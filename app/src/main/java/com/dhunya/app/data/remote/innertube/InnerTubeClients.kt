package com.dhunya.app.data.remote.innertube

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
    val osName: String? = null,
    val osVersion: String? = null,
    val androidSdkVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val isEmbedded: Boolean = false,
    val useSignatureTimestamp: Boolean = false
)

object InnerTubeClients {
    const val ORIGIN = "https://music.youtube.com"
    const val API_BASE = "$ORIGIN/youtubei/v1/"

    private const val USER_AGENT_WEB =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /** Catalogue client: search, albums, playlists, artists. No login required. */
    val WEB_REMIX = InnerTubeClient(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260213.01.00",
        clientId = "67",
        userAgent = USER_AGENT_WEB,
        useSignatureTimestamp = true
    )

    /** Audio friendly client. Returns direct audio URLs (no cipher) and non adaptive bitrate. */
    val ANDROID_VR = InnerTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.43.32",
        clientId = "28",
        userAgent = "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
            "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/107.0.5284.2)",
        osName = "Android",
        osVersion = "12",
        androidSdkVersion = "32",
        deviceMake = "Oculus",
        deviceModel = "Quest 3"
    )

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

    /** Embedded player: can still return streams for age restricted tracks. */
    val TVHTML5_EMBEDDED = InnerTubeClient(
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        clientVersion = "2.0",
        clientId = "85",
        userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.02) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/15.4 Safari/605.1.15",
        isEmbedded = true,
        useSignatureTimestamp = true
    )

    /** Order used when resolving a playable audio URL. */
    val STREAM_CLIENTS = listOf(ANDROID_VR, IOS, TVHTML5_EMBEDDED, WEB_REMIX)
}

object InnerTubeParams {
    /**
     * Protobuf search filters used by the YouTube Music web app. They limit a search to a
     * single result kind, which keeps the parsing predictable.
     */
    const val SONGS = "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D"
    const val ALBUMS = "EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D"
    const val ARTISTS = "EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D"

    /** Browse ids of the built-in YouTube Music pages. */
    const val NEW_RELEASES = "FEmusic_new_releases_albums"
    const val CHARTS = "FEmusic_charts"
}
