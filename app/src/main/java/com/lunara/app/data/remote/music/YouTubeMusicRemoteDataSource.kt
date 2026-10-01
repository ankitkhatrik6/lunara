package com.lunara.app.data.remote.music

import android.net.Uri
import android.util.Log
import com.lunara.app.core.utils.isPlayableAudioFile
import com.lunara.app.data.remote.innertube.AudioStream
import com.lunara.app.data.remote.innertube.InnerTubeApi
import com.lunara.app.data.remote.innertube.InnerTubeClient
import com.lunara.app.data.remote.innertube.InnerTubeClients
import com.lunara.app.data.remote.innertube.InnerTubeParams
import com.lunara.app.data.remote.innertube.InnerTubeParser
import com.lunara.app.data.remote.innertube.VideoInfo
import com.lunara.app.domain.model.Album
import com.lunara.app.domain.model.Artist
import com.lunara.app.domain.model.PlayableMedia
import com.lunara.app.domain.model.RadioPage
import com.lunara.app.domain.model.Song
import com.lunara.app.player.StreamSizes
import com.lunara.app.player.StreamUserAgents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube Music catalogue over the InnerTube API.
 *
 * Mirrors Blazify's flow (from `blazify/innertube` + `YouTube.kt`):
 *  - catalogue/search via `WEB_REMIX` (search/browse endpoints)
 *  - playable audio via the `player` endpoint, failing over between the client identities in
 *    [InnerTubeClients.STREAM_CLIENTS] and keeping the best audio-only adaptive format of each -
 *    exactly how Blazify's PlayerConnection opens a track for ExoPlayer/Media3.
 *
 * Two things make the stream part work in practice, and both are decided by *measurement* rather
 * than by trusting an identity: every URL is probed with the user agent of the client that minted
 * it, and each identity is judged by a range request deep inside the file (see [probeVerdict]).
 * An identity that only serves the beginning of its streams is remembered and demoted, so the
 * track is played from one that serves whole files.
 *
 * No third-party proxies, no Piped instances, no placeholder tracks.
 */
@Singleton
class YouTubeMusicRemoteDataSource @Inject constructor(
    private val api: InnerTubeApi,
    private val okHttpClient: OkHttpClient
) {
    /** Cached `signatureTimestamp` of the current player build (fetched lazily). */
    @Volatile
    private var signatureTimestamp: Int? = null

    private suspend fun signatureTimestampFor(client: InnerTubeClient): Int? {
        if (!client.useSignatureTimestamp) return null
        signatureTimestamp?.let { return it }
        return api.fetchSignatureTimestamp()?.also { signatureTimestamp = it }
    }

    // ---------- search ----------

    suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.SONGS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseSongs(root)
    }

    suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.ARTISTS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseItems(root).mapNotNull { item ->
            val browseId = item.browseId ?: return@mapNotNull null
            Artist(
                id = browseId,
                name = item.title,
                imageUrl = item.artworkUrl,
                monthlyListeners = null,
            )
        }
    }

    suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val root = api.search(InnerTubeClients.WEB_REMIX, query, InnerTubeParams.ALBUMS)
            ?: return@withContext emptyList()
        InnerTubeParser.parseItems(root).mapNotNull { item ->
            val browseId = item.browseId ?: item.playlistId ?: return@mapNotNull null
            Album(
                id = browseId,
                title = item.title,
                artistName = item.subtitle?.substringBefore("•")?.trim().orEmpty(),
                artworkUrl = item.artworkUrl,
                releaseYear = null,
                songCount = 0,
            )
        }
    }

    // ---------- album / playlist track listing ----------

    /**
     * Ordered track list for an album (MPRE browse id) or a shared album
     * playlist (OLAK id, e.g. Whole Lotta Red). Same `browse` call Blazify
     * makes before queueing an album for playback.
     */
    suspend fun getAlbumTracks(albumOrPlaylistId: String): List<Song> = withContext(Dispatchers.IO) {
        val id = albumOrPlaylistId.trim()
        val root = when {
            id.startsWith("OLAK") || id.startsWith("VL") || id.startsWith("PL") -> {
                val browseId = if (id.startsWith("VL")) id else "VL$id"
                api.browse(InnerTubeClients.WEB_REMIX, browseId = browseId)
                    ?: api.browse(InnerTubeClients.WEB_REMIX, browseId = id)
            }
            else -> api.browse(InnerTubeClients.WEB_REMIX, browseId = id)
        } ?: return@withContext emptyList()
        InnerTubeParser.parseSongs(root)
    }

    // ---------- song radio ("up next") ----------

    /**
     * The mix YouTube Music builds around [song]: the list the official app shows under
     * "Up next" the moment that track starts, which is what Lunara keeps playing once the
     * tracks the user actually asked for have run out.
     *
     * The seed is `RDAMVM<videoId>`, YouTube's own "radio for this track" playlist id, so the
     * recommendations are the real ones the service serves - not a local "sounds similar"
     * guess. [continuation] carries the token of a previous page and returns the next batch of
     * the same mix.
     */
    suspend fun upNext(song: Song, continuation: String? = null): RadioPage =
        withContext(Dispatchers.IO) {
            val root = if (continuation.isNullOrBlank()) {
                val videoId = radioVideoIdFor(song) ?: return@withContext RadioPage()
                api.next(
                    client = InnerTubeClients.WEB_REMIX,
                    videoId = videoId,
                    playlistId = InnerTubeParams.SONG_RADIO_PREFIX + videoId
                )
            } else {
                api.next(client = InnerTubeClients.WEB_REMIX, continuation = continuation)
            }
            val response = root ?: return@withContext RadioPage()
            InnerTubeParser.parseRadio(response)
        }

    /**
     * The video id a radio can be seeded with: the track's own id when it *is* a YouTube Music
     * track, otherwise the best search hit for its title and artist - so a track from the
     * device's own library still gets a real mix.
     */
    private suspend fun radioVideoIdFor(song: Song): String? {
        if (song.id.startsWith(YOUTUBE_ID_PREFIX)) {
            return song.id.removePrefix(YOUTUBE_ID_PREFIX).takeIf { it.isNotBlank() }
        }
        val query = listOf(song.title, song.artistName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (query.isBlank()) return null
        return searchSongs(query)
            .firstNotNullOfOrNull { hit -> hit.id.removePrefix(YOUTUBE_ID_PREFIX).takeIf { it.isNotBlank() } }
    }

    // ---------- single song / stream resolution ----------

    suspend fun getSong(id: String): Song? = withContext(Dispatchers.IO) {
        val videoId = id.removePrefix("yt_")
        var info: VideoInfo? = null
        for (client in InnerTubeClients.STREAM_CLIENTS) {
            val root = api.player(client, videoId, signatureTimestamp = signatureTimestampFor(client))
                ?: continue
            info = InnerTubeParser.parseVideoInfo(root)
            if (info != null) break
        }
        val details = info ?: return@withContext null
        Song(
            id = "yt_${details.videoId}",
            title = details.title ?: "Unknown Track",
            artistName = details.author ?: "Unknown Artist",
            albumName = null,
            artworkUrl = details.artworkUrl,
            durationMs = details.durationMs,
            streamUrl = null,
        )
    }

    suspend fun resolveAudioStreamUrl(videoId: String): String? = resolveAudioStream(videoId)?.url

    /** A playable audio source: a direct https stream or an HLS master playlist. */
    data class ResolvedStream(
        val url: String,
        val contentLength: Long = 0L,
        /** Full mime type from the InnerTube format, e.g. `audio/mp4; codecs="mp4a.40.2"`. */
        val mimeType: String? = null,
        val isHls: Boolean = false,
        /** Ready alternatives for the same track, best first. */
        val fallbackUrls: List<String> = emptyList()
    )

    /** One audio URL plus the metadata needed to hand it to ExoPlayer. */
    private data class Candidate(
        val url: String,
        val mimeType: String?,
        val contentLength: Long,
        val isHls: Boolean
    )

    private data class CachedStream(val stream: ResolvedStream, val resolvedAtMs: Long)

    /** Keeps a track's URLs for a few minutes so repeat plays start instantly. */
    private val streamCache = ConcurrentHashMap<String, CachedStream>()

    /** Rotates the identity a resolve starts with, so a retry never repeats the failed one. */
    private val rotation = AtomicInteger()

    /**
     * Identities that recently handed out URLs YouTube only serves the *beginning* of.
     *
     * This is the failure that made playback die mid track with a source error: the first range
     * request is answered `206` and a few megabytes later every further range is answered `403`.
     * Remembering which identities behaved that way puts them last next time, so a track starts
     * on a client that is actually known to serve whole files.
     */
    private val prefixOnlyClients = ConcurrentHashMap.newKeySet<String>()

    /**
     * The identity that last produced a stream the CDN served to the end.
     *
     * Being right once is worth remembering: a resolve costs one `player` round trip per identity
     * plus a probe, so starting on the identity that worked last time is often the difference
     * between an instant play and a few seconds of silence.
     */
    @Volatile
    private var lastGoodClientKey: String? = null

    /**
     * Resolves the audio of a track, failing over between the client identities.
     *
     * Every client's formats are collected instead of stopping at the first usable one, so the
     * player has spare URLs to fall back on. Each candidate is probed with the user agent of the
     * identity that minted it, and the *deep* probe - a range about 60% into the file - decides
     * whether a URL streams to the end or only serves its first megabytes before answering
     * `403`. That distinction is the fix for playback that started fine and then died mid track.
     *
     * An HLS playlist wins whenever a client offers one (it is not subject to the byte range
     * gate). A track is still handed over when nothing probes clean, because some CDN edges
     * refuse probes while serving ExoPlayer - that last resort is what keeps a track playable at
     * all on those edges.
     */
    suspend fun resolveAudioStream(
        videoId: String,
        forceRefresh: Boolean = false,
        allowHls: Boolean = true
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val cleanId = videoId.removePrefix("yt_")

        // The cache may hold a playlist URL from an ordinary resolve, and a caller that asked for
        // progressive audio only cannot use one, so its cache is skipped entirely.
        if (!forceRefresh && allowHls) {
            streamCache[cleanId]
                ?.takeIf { System.currentTimeMillis() - it.resolvedAtMs < STREAM_CACHE_TTL_MS }
                ?.let { return@withContext it.stream }
        }

        val fullyServable = LinkedHashSet<Candidate>()
        val unverified = LinkedHashSet<Candidate>()
        val prefixOnly = LinkedHashSet<Candidate>()
        val rejected = LinkedHashSet<Candidate>()

        var deepProbesLeft = MAX_DEEP_PROBED_CLIENTS

        for (client in orderedClients()) {
            val root = api.player(
                client = client,
                videoId = cleanId,
                signatureTimestamp = signatureTimestampFor(client)
            ) ?: continue

            var verdict: ProbeVerdict? = null

            // Downloads ask for progressive audio only: saving a playlist would produce a file that
            // ExoPlayer rejects as "unsupported audio format" instead of music.
            val manifest = if (allowHls) InnerTubeParser.parseHlsManifestUrl(root) else null
            if (manifest != null) {
                val candidate = Candidate(manifest, "application/x-mpegURL", 0L, isHls = true)
                StreamUserAgents.remember(candidate.url, client.userAgent)
                // A playlist is an index file, not media bytes: it is never subject to the byte
                // range gate that limits the direct formats, and media3 plays HLS natively. So an
                // offered playlist wins outright.
                if (probe(manifest, client.userAgent, range = null).code in 200..299) {
                    fullyServable.add(candidate)
                    lastGoodClientKey = keyOf(client)
                    Log.d(LOG_TAG, "${client.clientName}: HLS playlist offered, preferring it")
                    break
                }
                unverified.add(candidate)
            }

            val formats = InnerTubeParser.parseAudioStreams(root)
                .filter { it.url.startsWith("http") }
                // m4a (mp4) first: ExoPlayer's MP4 extractor handles seeks on these far
                // better than the webm/opus progressive streams, then highest bitrate.
                .sortedWith(
                    compareByDescending<AudioStream> { it.mimeType.contains("mp4") }
                        .thenByDescending { it.bitrate }
                )
                .take(MAX_CANDIDATES_PER_CLIENT)

            if (formats.isEmpty()) {
                Log.d(LOG_TAG, "${client.clientName}: no plain audio URL in the player response")
                continue
            }

            for (format in formats) {
                val candidate = Candidate(
                    url = format.url,
                    mimeType = format.mimeType,
                    contentLength = format.contentLength,
                    isHls = false
                )
                // A URL is only served to the identity that minted it, so record the agent.
                StreamUserAgents.remember(candidate.url, client.userAgent)

                // The identity's behaviour is judged once, by its best format: probing every
                // format would multiply the round trips a play takes to start.
                val clientVerdict = verdict ?: if (deepProbesLeft > 0) {
                    deepProbesLeft--
                    probeVerdict(format.url, format.contentLength, client.userAgent)
                } else if (probe(format.url, client.userAgent, FIRST_RANGE).code in 200..299) {
                    // Out of deep-probe budget: the cheap first range at least tells us it serves.
                    ProbeVerdict.UNVERIFIED
                } else {
                    ProbeVerdict.REJECTED
                }
                verdict = clientVerdict

                when (clientVerdict) {
                    ProbeVerdict.FULLY_SERVABLE -> {
                        prefixOnlyClients.remove(keyOf(client))
                        lastGoodClientKey = keyOf(client)
                        fullyServable.add(candidate)
                    }
                    ProbeVerdict.UNVERIFIED -> unverified.add(candidate)
                    ProbeVerdict.PREFIX_ONLY -> {
                        prefixOnlyClients.add(keyOf(client))
                        if (lastGoodClientKey == keyOf(client)) lastGoodClientKey = null
                        prefixOnly.add(candidate)
                    }
                    ProbeVerdict.REJECTED -> rejected.add(candidate)
                }
            }

            Log.d(
                LOG_TAG,
                "resolve $cleanId: ${client.clientName}/${client.clientVersion} -> $verdict " +
                    "(${formats.size} formats)"
            )

            if (fullyServable.size >= PREFERRED_FULLY_SERVABLE) break
        }

        // The order is the whole point. Identities whose URLs serve the complete file come
        // first, then the unmeasured ones, then those known to cut out mid track, and last the
        // ones that refused the probe outright - a CDN edge can refuse a probe and still serve
        // ExoPlayer, so they are kept rather than thrown away.
        val ordered = (fullyServable + unverified + prefixOnly + rejected).distinctBy { it.url }
        val best = ordered.firstOrNull() ?: return@withContext null

        // Remember every candidate's size. The player closes its byte ranges with it, and the
        // fallback URLs are swapped in mid-track without another resolve, so all of them have
        // to be known up front.
        ordered.forEach { StreamSizes.remember(it.url, it.contentLength) }

        ResolvedStream(
            url = best.url,
            contentLength = best.contentLength,
            mimeType = best.mimeType,
            isHls = best.isHls,
            fallbackUrls = ordered.drop(1).map { it.url }
        ).also {
            // The cache is read with a TTL but never evicted, so a long session would keep every
            // signed URL of every track it played alive; dropping the lot is harmless (it only
            // costs one extra resolve).
            if (streamCache.size >= MAX_CACHED_TRACKS) streamCache.clear()
            streamCache[cleanId] = CachedStream(it, System.currentTimeMillis())
        }
    }

    /**
     * How much of a stream URL YouTube is currently willing to serve.
     *
     * The distinction between the first two cases matters more than anything else here. A URL
     * whose signature YouTube will not honour to the end answers `206` to the *first* range - so
     * a first-range-only probe calls it healthy - and then `403` to every range after that,
     * which is exactly how playback used to start fine and die mid track with a source error.
     */
    private enum class ProbeVerdict { FULLY_SERVABLE, UNVERIFIED, PREFIX_ONLY, REJECTED }

    /** One probe: the HTTP status plus the total size the server reported, if any. */
    private data class ProbeResponse(val code: Int, val totalBytes: Long)

    /**
     * Measures [url] with the user agent of the identity that minted it - the agent the player
     * will send - by asking for a range deep inside the file.
     *
     * The deep range is what separates a URL that streams to the end from one that dies after
     * the intro. When the player response already declared the file size, that range is also the
     * *only* request needed: a `206` for bytes around 60% of the file proves the signature covers
     * the whole thing, and a `403` proves it does not. Asking for the opening bytes first - as
     * this used to - only adds a round trip to every play.
     *
     * An HLS playlist never reaches here; playlists are index files and are not range gated.
     */
    private fun probeVerdict(url: String, declaredSize: Long, userAgent: String): ProbeVerdict {
        if (declaredSize > DEEP_PROBE_MIN_BYTES) {
            val offset = declaredSize * DEEP_PROBE_POSITION_PERCENT / 100L
            return when (probe(url, userAgent, "bytes=$offset-${offset + PROBE_BYTES - 1}").code) {
                in 200..299 -> ProbeVerdict.FULLY_SERVABLE
                401, 403 -> ProbeVerdict.PREFIX_ONLY
                // No usable answer: measure the long way round rather than judging an identity on
                // one request that may simply have been dropped.
                else -> openingRangeVerdict(url, declaredSize, userAgent)
            }
        }
        return openingRangeVerdict(url, declaredSize, userAgent)
    }

    /**
     * The measured path for a URL whose size is unknown: ask for the opening bytes, then decide
     * whether the reported size makes a mid-file check worthwhile.
     */
    private fun openingRangeVerdict(
        url: String,
        declaredSize: Long,
        userAgent: String
    ): ProbeVerdict {
        val first = probe(url, userAgent, FIRST_RANGE)
        if (first.code !in 200..299) return ProbeVerdict.REJECTED

        val size = if (first.totalBytes > 0L) first.totalBytes else declaredSize
        if (size <= DEEP_PROBE_MIN_BYTES) return ProbeVerdict.FULLY_SERVABLE

        val offset = size * DEEP_PROBE_POSITION_PERCENT / 100L
        return when (probe(url, userAgent, "bytes=$offset-${offset + PROBE_BYTES - 1}").code) {
            in 200..299 -> ProbeVerdict.FULLY_SERVABLE
            401, 403 -> ProbeVerdict.PREFIX_ONLY
            else -> ProbeVerdict.UNVERIFIED
        }
    }

    /**
     * Sends one request for [url] and reports its status. [range] is a raw `Range` header value;
     * `null` asks without one, which is how a playlist has to be requested.
     */
    private fun probe(url: String, userAgent: String, range: String?): ProbeResponse = try {
        val builder = Request.Builder()
            .url(url)
            // A URL is bound to the identity that minted it. Probing with a foreign agent tests
            // something the player would never send, and reports a failure that cannot happen.
            .header("User-Agent", userAgent)
        range?.let { builder.header("Range", it) }
        okHttpClient.newCall(builder.build()).execute().use { response ->
            ProbeResponse(
                code = response.code,
                totalBytes = response.header("Content-Range")
                    ?.substringAfterLast('/', "")
                    ?.trim()
                    ?.toLongOrNull()
                    ?: -1L
            )
        }
    } catch (e: Exception) {
        ProbeResponse(code = -1, totalBytes = -1L)
    }

    /**
     * The identities to try, in order, for one resolve.
     *
     * The list is rotated by one on every call, so a retry - which is always triggered by a real
     * playback failure - never starts on the identity that just failed. Identities known to serve
     * only a prefix of their streams are pushed to the back, which is what makes the second
     * attempt at a track land on one that streams properly.
     */
    private fun orderedClients(): List<InnerTubeClient> {
        val clients = InnerTubeClients.STREAM_CLIENTS
        val offset = rotation.getAndIncrement().mod(clients.size)
        val rotated = clients.drop(offset) + clients.take(offset)
        val preferred = lastGoodClientKey
        return rotated
            .sortedBy { if (prefixOnlyClients.contains(keyOf(it))) 1 else 0 }
            // `sortedBy` is stable, so this only lifts the last winning identity to the front and
            // leaves everything else in the order decided above.
            .sortedBy { if (preferred != null && keyOf(it) == preferred) 0 else 1 }
    }

    /** Identifies one client identity, version included (two builds can share a name). */
    private fun keyOf(client: InnerTubeClient): String =
        "${client.clientName}/${client.clientVersion}"

    /**
     * Opens [song] for the player.
     *
     * Only two things are trusted as-is: a `content://` URI and a `file://` URI that really
     * exists. Everything else is resolved again, because a stored [Song.streamUrl] is a
     * *signed* URL that YouTube invalidates within minutes. Handing a dead one to ExoPlayer
     * produced a "source error" that could never recover: the retry path asked this same
     * method for a URL again and got the same expired signature back.
     *
     * Re-resolving is cheap - [resolveAudioStream] serves repeat plays from its own cache while
     * the URL is still valid - so freshness costs nothing in the common case.
     *
     * [forceRefresh] additionally bypasses that cache, used when the player reports that the
     * URL it was handed has just stopped working.
     *
     * [allowHls] is `false` for downloads, which must store progressive audio and never a
     * playlist.
     */
    suspend fun resolvePlayableMedia(
        song: Song,
        forceRefresh: Boolean = false,
        allowHls: Boolean = true
    ): PlayableMedia = withContext(Dispatchers.IO) {
        song.localUri?.takeIf { it.isUsableLocalUri() }?.let {
            return@withContext PlayableMedia(song, it, isLocal = true)
        }

        // Non-YouTube sources (imported media) have no resolver behind them, so their stored
        // URL is all there is. YouTube Music tracks are always resolved fresh.
        if (!forceRefresh && !song.id.startsWith(YOUTUBE_ID_PREFIX)) {
            song.streamUrl?.takeIf { it.isNotBlank() }?.let {
                return@withContext PlayableMedia(song, it, isLocal = false)
            }
        }

        val stream = resolveAudioStream(song.id, forceRefresh = forceRefresh, allowHls = allowHls)
            ?: throw IllegalStateException(
                "YouTube did not return a playable stream for \"${song.title}\""
            )
        PlayableMedia(
            song.copy(streamUrl = stream.url, localUri = null),
            stream.url,
            isLocal = false,
            mimeType = stream.mimeType,
            isHls = stream.isHls,
            contentLength = stream.contentLength,
            fallbackUris = stream.fallbackUrls
        )
    }

    /**
     * `true` when the receiver is a local URI that ExoPlayer can actually open.
     *
     * A downloaded track whose file has been deleted (app data cleared, storage trimmed) must
     * fall through to streaming instead of being handed over as a dead `file://` URI, which
     * ExoPlayer reports as a "source error" that never recovers. The same goes for a saved file
     * that turns out to be a playlist or a stub - see [isPlayableAudioFile].
     */
    private fun String.isUsableLocalUri(): Boolean = isPlayableAudioFile(this)

    private companion object {
        /** How long a resolved stream stays reusable before it is resolved again. */
        const val STREAM_CACHE_TTL_MS = 10 * 60 * 1000L

        /** Tracks kept in [streamCache]; beyond this the cache is dropped wholesale. */
        const val MAX_CACHED_TRACKS = 128

        /** Lunara stores every YouTube Music track under a `yt_<videoId>` id. */
        const val YOUTUBE_ID_PREFIX = "yt_"

        /** How many formats are probed per client identity. */
        const val MAX_CANDIDATES_PER_CLIENT = 4

        /**
         * Stop asking further clients once this many complete-file candidates are known.
         *
         * Deliberately low: every client costs a `player` round trip plus probes, so insisting on
         * more delayed the first note. Two is enough to fail over, and the remaining formats of
         * the clients already asked still join the list.
         */
        const val PREFERRED_FULLY_SERVABLE = 2

        /**
         * How many identities may be measured with the deep (mid-file) probe per resolve.
         *
         * The deep probe is what finds a URL that streams to the end, but it is one extra
         * request per identity, and identities that need it most are the slow ones. Three is the
         * balance: enough to discover a good identity within the normal play-start budget, and
         * what is learned is kept in [prefixOnlyClients] for the next resolve.
         */
        const val MAX_DEEP_PROBED_CLIENTS = 3

        /** Opening range used for the cheap "does this URL serve at all" check. */
        const val FIRST_RANGE = "bytes=0-1023"

        /** Size of the deep probe's range. */
        const val PROBE_BYTES = 1024L

        /** Files up to this size are covered by the opening range alone - no deep probe needed. */
        const val DEEP_PROBE_MIN_BYTES = 512L * 1024L

        /**
         * Where inside the file the deep probe looks, as a percentage of its size. Around two
         * thirds is past the region a token-less URL is still served from.
         */
        const val DEEP_PROBE_POSITION_PERCENT = 60L

        /** Tag for the resolve diagnostics printed to logcat. */
        const val LOG_TAG = "LunaraStream"
    }
}

