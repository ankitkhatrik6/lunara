package com.lunara.app.data.remote.innertube

import android.net.Uri
import java.util.Locale

/**
 * YouTube Music client identities used when talking to the InnerTube endpoints.
 *
 * Each entry mirrors a real YouTube app build: the client id in the `X-YouTube-Client-Name`
 * header, the matching `X-YouTube-Client-Version`, the user agent the app sends and the
 * optional device context fields. Streaming endpoints are picky about this combination, so
 * several identities are kept to fail over between them.
 */
data class InnerTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    /** InnerTube host this identity belongs to (`www` for app/TV builds, `music` for web). */
    val origin: String = InnerTubeClients.WWW_ORIGIN,
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: Int? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val isEmbedded: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    /** `contentCheckOk` / `racyCheckOk` playback flags, required by the TV clients. */
    val needsPlaybackFlags: Boolean = false
)

object InnerTubeClients {
    const val WWW_ORIGIN = "https://www.youtube.com"
    const val MUSIC_ORIGIN = "https://music.youtube.com"

    /** Catalogue (search/browse) always goes through the YouTube Music web app. */
    const val ORIGIN = MUSIC_ORIGIN

    private const val USER_AGENT_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** Catalogue client: search, albums, playlists, artists. No login required. */
    val WEB_REMIX = InnerTubeClient(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260707.12.00",
        clientId = "67",
        userAgent = USER_AGENT_WEB,
        origin = MUSIC_ORIGIN,
        useSignatureTimestamp = true
    )

    /**
     * iOS build. Still answers with plain `url` formats (no cipher, no SABR) which is what
     * ExoPlayer can stream directly.
     */
    val IOS = InnerTubeClient(
        clientName = "IOS",
        clientVersion = "21.03.1",
        clientId = "5",
        userAgent = "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        osName = "iOS",
        osVersion = "18.2.22C152",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2"
    )

    /** Audio friendly app client (Oculus/Quest build - exempt from PO-token gating). */
    val ANDROID_VR = InnerTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.43.32",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
        osName = "Android",
        osVersion = "12",
        androidSdkVersion = 32,
        deviceMake = "Oculus",
        deviceModel = "Quest 3"
    )

    /**
     * Classic Android app build. Answers with plain-`url` audio adaptive formats; kept
     * ahead of IOS so its fresher version is tried first.
     */
    val ANDROID = InnerTubeClient(
        clientName = "ANDROID",
        clientVersion = "20.10.38",
        clientId = "3",
        userAgent = "com.google.android.youtube/20.10.38 " +
            "(Linux; U; Android 11; en_US; Build/; Cronet/)",
        osName = "Android",
        osVersion = "11",
        androidSdkVersion = 30
    )

    /**
     * Newer Oculus build of the same VR app. YouTube started gating the streaming endpoints per
     * app version, so an identity that answers with plain URLs today can start answering with
     * SABR-only ones tomorrow; keeping a second pinned version means one of them normally still
     * hands out a ready-to-stream format.
     */
    val ANDROID_VR_1_61 = InnerTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
        osName = "Android",
        osVersion = "12",
        androidSdkVersion = 32,
        deviceMake = "Oculus",
        deviceModel = "Quest 3"
    )

    /**
     * visionOS build (Apple Vision Pro). One of the identities yt-dlp still lists as free of the
     * GVS PO-token policy, so it is the most promising alternative when the Android/Oculus
     * identities get gated.
     */
    val VISIONOS = InnerTubeClient(
        clientName = "VISIONOS",
        clientVersion = "1.02",
        clientId = "101",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_5) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/18.5 Safari/605.1.15",
        osName = "visionOS",
        osVersion = "2.5.21O556o",
        deviceMake = "Apple",
        deviceModel = "RealityDevice17,1"
    )

    /**
     * Player embedded on third party pages. Also free of the PO-token policy, so it is the
     * second web identity worth trying - it only ever answers for videos that allow embedding,
     * which is most music.
     */
    val WEB_EMBEDDED_PLAYER = InnerTubeClient(
        clientName = "WEB_EMBEDDED_PLAYER",
        clientVersion = "2.20260708.00.00",
        clientId = "56",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36",
        isEmbedded = true,
        useSignatureTimestamp = true
    )

    /**
     * Age-gate/consent flags required by the TV clients; mirrors
     * `TVHTML5Client.playbackContext` in yt-dlp.
     */
    val TVHTML5 = InnerTubeClient(
        clientName = "TVHTML5",
        clientVersion = "7.20250319.01.00",
        clientId = "7",
        userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
            "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
        needsPlaybackFlags = true
    )

    /**
     * Order used when resolving a playable audio URL, best first.
     *
     * [ANDROID_VR] and [IOS] lead because they are the identities that still answer with plain,
     * ready-to-stream `url` formats: no `signatureCipher`, no SABR-only streaming response.
     * Everything after them is a fallback for the tracks they refuse - in particular
     * [VISIONOS] and [WEB_EMBEDDED_PLAYER], the identities yt-dlp still lists as exempt from
     * YouTube's PO-token policy. WEB_REMIX is deliberately absent: it only answers with
     * cipher-protected formats (no plain URL), so it could never feed ExoPlayer directly.
     *
     * The order is only a starting point. Which identity really works is decided per track by
     * probing the URLs it returns (see `YouTubeMusicRemoteDataSource.resolveAudioStream`), and
     * the list is rotated between attempts so a retry never begins with the identity that just
     * failed.
     */
    val STREAM_CLIENTS = listOf(
        ANDROID_VR,
        IOS,
        ANDROID,
        ANDROID_VR_1_61,
        VISIONOS,
        WEB_EMBEDDED_PLAYER,
        TVHTML5
    )

    /**
     * User agent for every `&c=<client>` tag that can appear in a stream URL.
     *
     * `distinctBy` matters: several identities share a client name (the two Oculus builds both
     * report `ANDROID_VR`), and the first declared one is the version this app prefers.
     */
    private val USER_AGENTS_BY_TAG: Map<String, String> =
        STREAM_CLIENTS.distinctBy { it.clientName }
            .associate { it.clientName.uppercase(Locale.ROOT) to it.userAgent } +
            mapOf(
                // Alias YouTube uses for the classic TV/embedded identities, plus the web
                // catalogue client that never mints stream URLs itself.
                "TVHTML5_SIMPLY_EMBEDDED_PLAYER" to TVHTML5.userAgent,
                "WEB" to USER_AGENT_WEB,
                "MWEB" to USER_AGENT_WEB,
                "WEB_REMIX" to USER_AGENT_WEB
            )

    /** Fallback user agent for a stream URL that carries no usable `&c=` tag. */
    val STREAM_USER_AGENT = ANDROID_VR.userAgent

    /**
     * Client name of the identity that minted [uri], read from YouTube's own `&c=` tag, or
     * `null` when the URL does not carry one.
     */
    fun streamTagOf(uri: Uri?): String? =
        uri?.let { runCatching { it.getQueryParameter("c") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)

    /**
     * Best effort user agent for [uri], derived from the client tag it carries.
     *
     * YouTube binds a stream URL to the client that asked for it and answers `403` to other user
     * agents - the URLs carry the client name in `&c=` for exactly that reason. Pinning a single
     * fixed user agent (as earlier builds did) therefore doomed every URL minted by any other
     * identity, which is why the user agent is always derived from the URL that is fetched. The
     * exact user agent of the minting client is remembered per URL in `StreamUserAgents`, this
     * lookup is the fallback for URLs this process did not resolve itself.
     */
    fun playbackUserAgentFor(uri: Uri?): String {
        val tag = streamTagOf(uri) ?: return STREAM_USER_AGENT
        return USER_AGENTS_BY_TAG[tag] ?: STREAM_USER_AGENT
    }
}

object InnerTubeParams {
    /**
     * Protobuf search filters used by the YouTube Music web app. They limit a search to a
     * single result kind, which keeps the parsing predictable.
     *
     * Note: these constants hold the *raw* base64 bytes (`=` padding included). Ktor's
     * `put("params", ...)` writes them into the JSON body verbatim, so they must NOT be
     * URL-encoded here - `%3D%3D` would arrive at YouTube as a literal filter YouTube does
     * not recognise, and search would come back with zero song rows (empty catalogue).
     */
    const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
    const val ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA=="
    const val ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA=="

    /** Browse ids of the built-in YouTube Music pages. */
    const val NEW_RELEASES = "FEmusic_new_releases_albums"
    const val CHARTS = "FEmusic_charts"

    /**
     * The signed-out home page. Thin on purpose - YouTube fills it with the listener's own history
     * once there is an account, and serves "New releases" and a regional shelf without one.
     */
    const val HOME = "FEmusic_home"

    /** The discovery page: new albums and singles, the moods and genres chooser, new videos. */
    const val EXPLORE = "FEmusic_explore"

    /**
     * The page every mood and genre opens in. It takes the `params` a moods and genres row
     * carries - "Chill", "Commute" and the rest share this one browse id, and the params are the
     * only thing that tells them apart.
     */
    const val MOODS_AND_GENRES = "FEmusic_moods_and_genres_category"

    /**
     * Playlist id of the mix YouTube Music builds *for a single track*, i.e. the song radio:
     * `RDAMVM` + the seed's video id. Handing this to the `next` endpoint is what fills the
     * "Up next" list of the official app with similar songs, and it is what Lunara appends
     * to the queue once the tracks the user actually asked for have run out.
     */
    const val SONG_RADIO_PREFIX = "RDAMVM"
}
