package com.lunara.app.data.remote.innertube

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
     * [ANDROID_VR] leads because it is the only widely available identity that still answers
     * with plain, ready-to-stream `url` formats: no `signatureCipher`, no PO-token gate, no
     * SABR. The other three are fallbacks for the tracks it refuses. WEB_REMIX is deliberately
     * absent - it only answers with cipher-protected formats (no plain URL), so it could never
     * feed ExoPlayer directly.
     *
     * Putting a gated client first (as earlier builds did) meant every play wasted a round
     * trip on a response whose formats all have to be dropped, which is what made tracks take
     * seconds to start.
     */
    val STREAM_CLIENTS = listOf(ANDROID_VR, IOS, TVHTML5, ANDROID)

    /**
     * User agent used for every `*.googlevideo.com` request, both the readiness probe and the
     * actual playback fetch.
     *
     * It is the user agent of [ANDROID_VR] - the identity that mints almost every URL - so the
     * bytes request always matches the client the URL was signed for, and a probe can never
     * succeed where the player would have been rejected.
     */
    val STREAM_USER_AGENT = ANDROID_VR.userAgent
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
}
