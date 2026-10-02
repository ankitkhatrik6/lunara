package com.lunara.app.data.remote.innertube

import com.lunara.app.domain.model.BrowseCard
import com.lunara.app.domain.model.BrowseCategory
import com.lunara.app.domain.model.BrowseShelf
import com.lunara.app.domain.model.RadioPage
import com.lunara.app.domain.model.Song
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** A track/album/artist row extracted from any YouTube Music shelf. */
data class InnerTubeItem(
    val title: String,
    val subtitle: String? = null,
    val videoId: String? = null,
    val browseId: String? = null,
    val playlistId: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long = 0L
)

/** One audio track returned by the player endpoint. */
data class AudioStream(
    val url: String,
    val mimeType: String,
    val bitrate: Int,
    val itag: Int,
    val contentLength: Long = 0L
)

/** Track metadata returned by the player endpoint. */
data class VideoInfo(
    val videoId: String,
    val title: String?,
    val author: String?,
    val durationMs: Long,
    val artworkUrl: String?
)

/**
 * Parses InnerTube responses.
 *
 * Instead of mirroring YouTube's deeply nested renderer tree with fixed data classes, this
 * walks the JSON and picks renderers out by name. That keeps working when YouTube moves a
 * shelf between `singleColumnBrowseResultsRenderer`, `twoColumnBrowseResultsRenderer`, a
 * continuation response or a tab it introduced this week.
 */
object InnerTubeParser {

    private const val SEPARATOR = "•"

    fun findObject(root: JsonElement, key: String): JsonObject? = findAllObjects(root, key).firstOrNull()

    fun findAllObjects(root: JsonElement, key: String): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        walk(root, key, found)
        return found
    }

    private fun walk(node: JsonElement, key: String, out: MutableList<JsonObject>) {
        when (node) {
            is JsonObject -> {
                (node[key] as? JsonObject)?.let { out.add(it) }
                node.values.forEach { walk(it, key, out) }
            }
            is JsonArray -> node.forEach { walk(it, key, out) }
            else -> Unit
        }
    }

    /** Every track/album/artist row of a search or browse response. */
    fun parseItems(root: JsonElement): List<InnerTubeItem> {
        val items = mutableListOf<InnerTubeItem>()
        findAllObjects(root, "musicResponsiveListItemRenderer").forEach { renderer ->
            parseResponsiveItem(renderer)?.let { items.add(it) }
        }
        findAllObjects(root, "musicTwoRowItemRenderer").forEach { renderer ->
            parseTwoRowItem(renderer)?.let { items.add(it) }
        }
        return items
    }

    /** Tracks of an album or playlist, taken from the shelf that holds them. */
    fun parseSongs(root: JsonElement): List<Song> {
        val scope = listOf(
            "musicPlaylistShelfRenderer",
            "musicShelfRenderer",
            "musicPlaylistShelfContinuation",
            "musicShelfContinuation"
        ).firstNotNullOfOrNull { findObject(root, it) } ?: root
        return findAllObjects(scope, "musicResponsiveListItemRenderer")
            .mapNotNull { parseResponsiveSong(it) }
    }

    /**
     * The shelves of a browse page, in the order YouTube Music draws them.
     *
     * Both shelf shapes are read: the horizontal carousels of a home or moods page
     * (`musicCarouselShelfRenderer`, whose heading sits in its header) and the vertical shelves
     * of a charts or artist page (`musicShelfRenderer`, which carries its own title). Rows are
     * filed by what they are rather than by which shelf they came from, because YouTube uses the
     * same row for a song, a playlist tile and an artist:
     *
     *  - a row that can be played immediately (a song, or a music video listed as a tile) becomes
     *    a [BrowseShelf.songs] entry,
     *  - a row that leads to a playlist, album or artist becomes a [BrowseShelf.cards] entry,
     *  - a row of the moods and genres chooser becomes a [BrowseShelf.categories] entry.
     *
     * Empty shelves are dropped here: the charts page answers with a country filter shelf that
     * holds no rows at all, and a heading over nothing is worse than no heading.
     */
    fun parseBrowseShelves(root: JsonElement): List<BrowseShelf> {
        val shelves = mutableListOf<BrowseShelf?>()
        findAllObjects(root, "musicCarouselShelfRenderer").forEach { shelf ->
            val title = shelf.obj("header")
                ?.obj("musicCarouselShelfBasicHeaderRenderer")
                ?.obj("title")
                ?.runsText()
            if (title.isVideoShelf()) return@forEach
            shelves += shelfOf(
                title = title,
                rows = shelf.arr("contents").objects()
            )
        }
        findAllObjects(root, "musicShelfRenderer").forEach { shelf ->
            val title = shelf.obj("title")?.runsText()
            if (title.isVideoShelf()) return@forEach
            shelves += shelfOf(
                title = title,
                rows = shelf.arr("contents").objects()
            )
        }
        return shelves.filterNotNull()
    }

    /** One shelf, filed by row kind; `null` when YouTube sent a heading with nothing under it. */
    private fun shelfOf(title: String?, rows: List<JsonObject>): BrowseShelf? {
        val songs = mutableListOf<Song>()
        val cards = mutableListOf<BrowseCard>()
        val categories = mutableListOf<BrowseCategory>()
        rows.forEach { row ->
            row.shelfSong()?.let { songs.add(it) }
                ?: row.shelfCard()?.let { cards.add(it) }
                ?: row.shelfCategory()?.let { categories.add(it) }
        }
        if (songs.isEmpty() && cards.isEmpty() && categories.isEmpty()) return null
        return BrowseShelf(
            title = title.orEmpty(),
            songs = songs,
            cards = cards,
            categories = categories
        )
    }

    /**
     * A row that plays straight away: a music song row. Two-row tiles are deliberately not songs:
     * they are albums, singles, playlists or artists, and a video tile must never enter the music
     * queue just because it has a YouTube watch id.
     */
    private fun JsonObject.shelfSong(): Song? {
        obj("musicResponsiveListItemRenderer")?.let { return parseResponsiveSong(it) }
        return null
    }

    /**
     * A row that leads somewhere: a playlist, an album or an artist.
     *
     * Each renderer is read by the parser that knows its shape. A tile keeps its byline ("Single •
     * John Rai") in `subtitle.runs`, while a list row keeps it in its second flex column - reading a
     * tile as a list row finds the title and loses the byline with it.
     */
    private fun JsonObject.shelfCard(): BrowseCard? {
        val item = obj("musicTwoRowItemRenderer")?.let { parseTwoRowItem(it) }
            ?: obj("musicResponsiveListItemRenderer")?.let { parseResponsiveItem(it) }
            ?: return null
        val browseId = item.browseId ?: item.playlistId ?: return null
        return BrowseCard(
            title = item.title,
            subtitle = item.subtitle.cleanByline(),
            browseId = browseId,
            artworkUrl = item.artworkUrl
        )
    }

    /** A row of the moods and genres chooser, which is a button rather than a shelf entry. */
    private fun JsonObject.shelfCategory(): BrowseCategory? {
        val button = obj("musicNavigationButtonRenderer") ?: return null
        val label = button.obj("buttonText")?.runsText()?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = button.obj("clickCommand")?.obj("browseEndpoint") ?: return null
        val browseId = endpoint.text("browseId") ?: return null
        return BrowseCategory(
            title = label,
            browseId = browseId,
            params = endpoint.text("params")
        )
    }

    /**
     * The queue behind a `next` response: YouTube Music's own recommendation list for the
     * track that seeded it - the "song radio", i.e. exactly what the official app starts
     * playing when the track you picked has finished.
     *
     * The rows live in `musicQueueRenderer.content.playlistPanelRenderer.contents`, one
     * `playlistPanelVideoRenderer` each; the mobile builds name the same row
     * `musicPlaylistPanelVideoRenderer`, so both are accepted. A *continuation* of the radio
     * (the next batch) arrives without that wrapper, which is why the lookup falls back to the
     * whole response.
     *
     * The seed track is part of the list YouTube returns; dropping it is the caller's job, so
     * this stays a faithful parse of the response.
     */
    fun parseRadio(root: JsonElement): RadioPage {
        // A continuation of a mix arrives without the `musicQueueRenderer` wrapper, and the
        // response itself is the last place the rows can be, so the whole thing is searched as a
        // fallback and the type stays `JsonElement`.
        val panel: JsonElement = findObject(root, "musicQueueRenderer")?.obj("content")
            ?.obj("playlistPanelRenderer")
            ?: findObject(root, "playlistPanelRenderer")
            ?: findObject(root, "playlistPanelContinuation")
            ?: root

        val songs = findAllObjects(panel, "playlistPanelVideoRenderer")
            .plus(findAllObjects(panel, "musicPlaylistPanelVideoRenderer"))
            .mapNotNull { parseRadioSong(it) }

        return RadioPage(
            songs = songs,
            continuation = radioContinuation(panel.asObj()),
            title = findObject(root, "musicQueueHeaderRenderer")?.obj("subtitle")?.runsText()
        )
    }

    /**
     * One row of the radio.
     *
     * Its byline is the artist followed by whatever YouTube advertises there - `1.8B views •
     * 19M likes` for a music video, `Album • 4M plays` for an audio track - so the artist is
     * read from `shortBylineText` (always just the artist) and the album only from a credit
     * that really is one.
     */
    private fun parseRadioSong(renderer: JsonObject): Song? {
        val videoId = renderer.text("videoId") ?: renderer.firstVideoId() ?: return null
        val title = renderer.obj("title")?.runsText()?.takeIf { it.isNotBlank() } ?: return null
        val credits = renderer.obj("longBylineText")?.arr("runs")?.objects()
            ?.mapNotNull { it.text("text")?.trim()?.takeIf { text -> text.isNotBlank() } }
            ?.filterNot { it == SEPARATOR || it.isCreditNoise() }
            .orEmpty()
        return Song(
            id = "yt_$videoId",
            title = title,
            artistName = renderer.obj("shortBylineText")?.runsText()
                ?: credits.firstOrNull()
                ?: "Unknown Artist",
            albumName = credits.getOrNull(1),
            artworkUrl = renderer.artworkUrl(),
            durationMs = renderer.obj("lengthText")?.runsText().toDurationMs()
        )
    }

    /** The token that asks for the next batch of a radio, whatever YouTube named it. */
    private fun radioContinuation(panel: JsonObject?): String? =
        panel.arr("continuations").objects().firstNotNullOfOrNull { entry ->
            entry.obj("nextRadioContinuationData")?.text("continuation")
                ?: entry.obj("nextContinuationData")?.text("continuation")
                ?: entry.obj("reloadContinuationData")?.text("continuation")
        }?.takeIf { it.isNotBlank() }

    /**
     * Every audio format of a `player` response that can be used as-is.
     *
     * Formats returned by the web clients are cipher protected (`signatureCipher` /
     * `cipher`) and carry no plain URL, so they are skipped; the app-only clients
     * (ANDROID_VR / IOS) answer with direct URLs. Audio only entries are preferred over
     * muxed video ones and the caller picks the highest bitrate.
     */
    fun parseAudioStreams(root: JsonElement): List<AudioStream> {
        val streamingData = findObject(root, "streamingData") ?: return emptyList()
        val formats = streamingData.arr("adaptiveFormats").objects() +
            streamingData.arr("formats").objects()
        val streams = formats.mapNotNull { format ->
            val url = format.text("url")?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val mimeType = format.text("mimeType").orEmpty()
                .takeIf { it.isNotBlank() }
                ?: mimeTypeForItag(
                    format.int("itag") ?: format.text("itag")?.toIntOrNull() ?: 0
                )
            AudioStream(
                url = url,
                mimeType = mimeType,
                bitrate = format.long("bitrate")?.coerceIn(0L, Int.MAX_VALUE.toLong())?.toInt() ?: 0,
                itag = format.int("itag")
                    ?: format.text("itag")?.toIntOrNull()
                    ?: 0,
                contentLength = format.text("contentLength")?.toLongOrNull()
                    ?: format.long("contentLength")
                    ?: 0L
            )
        }
        val audioOnly = streams.filter { it.mimeType.startsWith("audio") }
        return audioOnly.ifEmpty { streams }
    }

    /** Track metadata of a `player` response. */
    fun parseVideoInfo(root: JsonElement): VideoInfo? {
        val details = findObject(root, "videoDetails") ?: return null
        val videoId = details.text("videoId")?.takeIf { it.isNotBlank() } ?: return null
        return VideoInfo(
            videoId = videoId,
            title = details.text("title"),
            author = details.text("author"),
            durationMs = (details.long("lengthSeconds") ?: 0L) * 1000L,
            artworkUrl = details.obj("thumbnail")?.arr("thumbnails")?.objects()
                ?.mapNotNull { it.text("url") }
                ?.lastOrNull()
                ?.upscaledArtwork()
        )
    }

    /** `playabilityStatus.status` of a `player` response (`OK`, `LOGIN_REQUIRED`, ...). */
    fun playabilityStatus(root: JsonElement): String? =
        findObject(root, "playabilityStatus")?.text("status")

    /** Human readable `playabilityStatus.reason`, surfaced in playback error messages. */
    fun playabilityReason(root: JsonElement): String? =
        findObject(root, "playabilityStatus")?.text("reason")

    /**
     * HLS master playlist of a `player` response. YouTube serves HLS without the
     * "PO token" requirement that applies to the direct https formats, so it is preferred
     * whenever a client hands one out.
     */
    fun parseHlsManifestUrl(root: JsonElement): String? =
        findObject(root, "streamingData")?.let { streamingData ->
            streamingData.text("hlsUrl") ?: streamingData.text("hlsManifestUrl")
        }?.takeIf { it.startsWith("http") }

    /**
     * Fallback MIME type for the well known audio itags (139/140 = AAC, 249/250/251 =
     * Opus). The app clients sometimes omit `mimeType` on audio levels, and without it
     * the stream would be dropped by the audio only filter below or misdetected by the
     * player.
     */
    private fun mimeTypeForItag(itag: Int): String = when (itag) {
        139 -> "audio/mp4; codecs=\"mp4a.40.5\""
        140 -> "audio/mp4; codecs=\"mp4a.40.2\""
        249 -> "audio/webm; codecs=\"opus\""
        250 -> "audio/webm; codecs=\"opus\""
        251 -> "audio/webm; codecs=\"opus\""
        else -> ""
    }

    // ---------- row parsing ----------

    /** A song/album/artist row of a `musicResponsiveListItemRenderer`. */
    private fun parseResponsiveItem(renderer: JsonObject): InnerTubeItem? {
        val columns = renderer.arr("flexColumns").objects()
            .mapNotNull { it.obj("musicResponsiveListItemFlexColumnRenderer")?.obj("text")?.runsText() }
            .filter { it.isNotBlank() }
        val title = columns.firstOrNull()
            ?: renderer.obj("title")?.runsText()?.takeIf { it.isNotBlank() }
            ?: return null

        val subtitle = columns.drop(1).joinToString(SEPARATOR).ifBlank { null }
        val fixedColumns = renderer.arr("fixedColumns").objects()
            .mapNotNull { it.obj("musicResponsiveListItemFixedColumnRenderer")?.obj("text")?.runsText() }
        val durationText = fixedColumns.firstOrNull { it.trim().isDuration() }
            ?: subtitle?.split(SEPARATOR)?.firstOrNull { it.trim().isDuration() }

        return InnerTubeItem(
            title = title,
            subtitle = subtitle,
            videoId = renderer.firstVideoId(),
            browseId = renderer.firstBrowseId(),
            playlistId = renderer.firstPlaylistId(),
            artworkUrl = renderer.artworkUrl(),
            durationMs = durationText.toDurationMs()
        )
    }

    /** A card row (`musicTwoRowItemRenderer`) used for albums, playlists and artists. */
    private fun parseTwoRowItem(renderer: JsonObject): InnerTubeItem? {
        val title = renderer.obj("title")?.runsText()?.takeIf { it.isNotBlank() } ?: return null
        val endpoint = renderer.obj("navigationEndpoint")
        return InnerTubeItem(
            title = title,
            subtitle = renderer.obj("subtitle")?.runsText()?.ifBlank { null },
            videoId = endpoint?.obj("watchEndpoint")?.text("videoId"),
            browseId = endpoint?.obj("browseEndpoint")?.text("browseId"),
            playlistId = endpoint?.obj("watchPlaylistEndpoint")?.text("playlistId")
                ?: endpoint?.obj("browseEndpoint")?.text("browseId")?.toPlaylistId(),
            artworkUrl = renderer.artworkUrl()
        )
    }

    private fun parseResponsiveSong(renderer: JsonObject): Song? {
        val item = parseResponsiveItem(renderer) ?: return null
        val videoId = item.videoId ?: return null
        val credits = item.subtitle.subtitleParts()
        return Song(
            id = "yt_$videoId",
            title = item.title,
            artistName = credits.getOrNull(0) ?: "Unknown Artist",
            albumName = credits.getOrNull(1),
            artworkUrl = item.artworkUrl,
            durationMs = item.durationMs
        )
    }

    // ---------- primitives ----------

    private val kindLabels = setOf("song", "video", "album", "single", "ep", "playlist", "artist")
    private val yearPattern = Regex("^\\d{4}$")
    private val durationPattern = Regex("^\\d{1,2}:\\d{2}(:\\d{2})?$")

    /**
     * A play/like/view counter sitting where a credit would be (`1.8B views`, `19M likes`).
     * A music video's radio row puts one in the middle of its byline, and mistaking it for the
     * album name would file every recommendation under a nonsense album.
     */
    private val counterPattern =
        Regex("(?i)^[\\d.,]+\\s*[KMB]?\\s*(views?|likes?|plays?|subscribers?|videos?|songs?)$")

    private val videoShelfPattern =
        Regex("(?i)\\b(video|videos|music video|music videos|video charts)\\b")

    private fun String?.isVideoShelf(): Boolean = this?.let(videoShelfPattern::containsMatchIn) == true

    private fun String?.cleanByline(): String? = this?.split(SEPARATOR)
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() && !it.isCreditNoise() && it.lowercase() !in kindLabels }
        ?.joinToString(SEPARATOR)
        ?.takeIf { it.isNotBlank() }

    private fun String.isCreditNoise(): Boolean =
        isDuration() || yearPattern.matches(this) || counterPattern.matches(this)

    private fun String.isDuration(): Boolean = durationPattern.matches(this)

    private fun String?.toDurationMs(): Long {
        val parts = this?.trim()?.split(":")?.mapNotNull { it.toLongOrNull() } ?: return 0L
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    /** "Artist • Album • 3:25" -> [Artist, Album] ("Artist • 3:25" -> [Artist]). */
    private fun String?.subtitleParts(): List<String> {
        return this?.split(SEPARATOR)
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() && !it.isCreditNoise() && it.lowercase() !in kindLabels }
            .orEmpty()
    }

    /** Concatenates every text run of a `text`/`title`/`subtitle` object. */
    private fun JsonObject?.runsText(): String? = this?.arr("runs")?.objects()
        ?.joinToString("") { it.text("text").orEmpty() }
        ?.trim()
        ?.ifBlank { null }

    private fun JsonObject.firstVideoId(): String? =
        obj("playlistItemData")?.text("videoId")
            ?: obj("navigationEndpoint")?.obj("watchEndpoint")?.text("videoId")
            ?: findAllObjects(this, "watchEndpoint").firstNotNullOfOrNull { it.text("videoId") }

    private fun JsonObject.firstBrowseId(): String? =
        obj("navigationEndpoint")?.obj("browseEndpoint")?.text("browseId")
            ?: findAllObjects(this, "browseEndpoint").firstNotNullOfOrNull { it.text("browseId") }

    private fun JsonObject.firstPlaylistId(): String? =
        obj("navigationEndpoint")?.obj("watchPlaylistEndpoint")?.text("playlistId")
            ?: findAllObjects(this, "watchPlaylistEndpoint").firstNotNullOfOrNull { it.text("playlistId") }
            ?: firstBrowseId()?.toPlaylistId()

    /** Best (largest) thumbnail of a row, upscaled for phone width. */
    private fun JsonObject.artworkUrl(): String? {
        val renderer = findAllObjects(this, "musicThumbnailRenderer").firstOrNull()
            ?: findAllObjects(this, "thumbnailRenderer").firstOrNull()
        // A queue row of the song radio carries its thumbnail directly instead of wrapping it
        // in a renderer, so the row itself is the last place to look.
        val thumbnails = (renderer ?: this).obj("thumbnail")?.arr("thumbnails")?.objects().orEmpty()
        val sized = thumbnails.filter { it.int("width") != null }
        val best = if (sized.isNotEmpty()) {
            sized.maxByOrNull { (it.int("width") ?: 0) * (it.int("height") ?: 0) }
        } else {
            thumbnails.lastOrNull()
        }
        return best?.text("url")?.upscaledArtwork()
    }

    private fun String.toPlaylistId(): String? = when {
        startsWith("PL") || startsWith("OLAK") || startsWith("RD") || startsWith("VL") ->
            removePrefix("VL").takeIf { it.isNotBlank() }
        else -> null
    }

    /** YouTube returns thumbnails sized for the web client; ask for a phone sized one. */
    private fun String.upscaledArtwork(): String {
        val marker = indexOf("=w")
        return if (marker > 0) substring(0, marker) + "=w1000-h1000-l90-rj" else this
    }
}
