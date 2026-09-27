package com.dhunya.app.data.remote.innertube

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin HTTP layer over the YouTube Music InnerTube API
 * (`https://music.youtube.com/youtubei/v1/...`).
 *
 * Only raw JSON is handled here - parsing lives in [InnerTubeParser] - which keeps the
 * transport resilient when YouTube reshapes its responses.
 */
@Singleton
class InnerTubeApi @Inject constructor(
    private val httpClient: HttpClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val language: String = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
    private val region: String = Locale.getDefault().country.ifBlank { "US" }
    private val timeZone: String = java.util.TimeZone.getDefault().id

    /**
     * `visitorData` handed out by YouTube on the first response. Replaying it on the
     * `player` calls makes the request look like a returning client instead of a fresh one,
     * which is what YouTube uses to decide whether to ask for a sign in.
     */
    @Volatile
    private var visitorData: String? = null

    suspend fun search(
        client: InnerTubeClient,
        query: String,
        params: String? = null,
        continuation: String? = null
    ): JsonObject? = post(
        client = client,
        endpoint = "search",
        body = buildJsonObject {
            put("context", context(client))
            put("query", query)
            params?.let { put("params", it) }
            continuation?.let { put("continuation", it) }
        },
        continuation = continuation
    )

    suspend fun browse(
        client: InnerTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null
    ): JsonObject? = post(
        client = client,
        endpoint = "browse",
        body = buildJsonObject {
            put("context", context(client))
            browseId?.let { put("browseId", it) }
            params?.let { put("params", it) }
            continuation?.let { put("continuation", it) }
        },
        continuation = continuation
    )

    suspend fun player(
        client: InnerTubeClient,
        videoId: String,
        playlistId: String? = null,
        signatureTimestamp: Int? = null
    ): JsonObject? = post(
        client = client,
        endpoint = "player",
        body = buildJsonObject {
            put("context", if (client.isEmbedded) embeddedContext(client, videoId) else context(client))
            put("videoId", videoId)
            playlistId?.let { put("playlistId", it) }
            if (client.needsPlaybackFlags) {
                // Age-gate/consent flags the TV clients demand; without them the
                // player endpoint answers UNPLAYABLE ("The page needs to be
                // reloaded") even for ordinary tracks.
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureVoice", false)
                    }
                }
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            }
            if (client.useSignatureTimestamp && signatureTimestamp != null) {
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("signatureTimestamp", signatureTimestamp)
                    }
                }
            }
        }
    )

    /**
     * `signatureTimestamp` of the current YouTube player build.
     *
     * Clients whose streams are cipher protected (WEB_REMIX, TV embedded) have to echo
     * this value inside `playbackContext` - without it YouTube answers with
     * `signatureCipher` entries that carry no playable URL. The number lives in the
     * player JavaScript bundle, so the iframe loader is resolved to its `base.js` and
     * scanned once. Returns null when the bundle cannot be reached (the app-only clients
     * do not need it at all).
     */
    suspend fun fetchSignatureTimestamp(): Int? {
        return try {
            val iframe = httpClient.get("$YOUTUBE_ORIGIN/iframe_api").bodyAsText()
                .replace("\\/", "/")
            val scriptPath = Regex("""/s/player/[A-Za-z0-9_\-]+/player_ias[^"']*?base\.js""")
                .find(iframe)?.value ?: return null
            val script = httpClient.get("$YOUTUBE_ORIGIN$scriptPath").bodyAsText()
            Regex("""signatureTimestamp\s*[:=]\s*(\d+)""")
                .find(script)?.groupValues?.get(1)?.toIntOrNull()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun context(client: InnerTubeClient): JsonObject = buildJsonObject {
        putJsonObject("client") {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", language)
            put("gl", region)
            put("timeZone", timeZone)
            put("utcOffsetMinutes", (java.util.TimeZone.getDefault().rawOffset / 60000))
            visitorData?.let { put("visitorData", it) }
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
        }
    }

    private fun embeddedContext(client: InnerTubeClient, videoId: String): JsonObject =
        JsonObject(
            context(client).toMutableMap().apply {
                put(
                    "thirdParty",
                    buildJsonObject { put("embedUrl", "https://www.youtube.com/watch?v=$videoId") }
                )
            }
        )

    private suspend fun post(
        client: InnerTubeClient,
        endpoint: String,
        body: JsonObject,
        continuation: String? = null
    ): JsonObject? {
        return try {
            val response = httpClient.post(client.origin + INNERTUBE_PATH + endpoint) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.UserAgent, client.userAgent)
                header("X-YouTube-Client-Name", client.clientId)
                header("X-YouTube-Client-Version", client.clientVersion)
                header("X-Origin", client.origin)
                header("Referer", "${client.origin}/")
                header("X-Goog-Api-Format-Version", "1")
                visitorData?.let { header("X-Goog-Visitor-Id", it) }
                parameter("prettyPrint", "false")
                if (continuation != null) {
                    parameter("continuation", continuation)
                    parameter("ctoken", continuation)
                }
                setBody(json.encodeToString(JsonObject.serializer(), body))
            }
            if (response.status.value !in 200..299) return null
            val text = response.bodyAsText()
            if (text.isBlank()) return null
            val root = json.parseToJsonElement(text) as? JsonObject
            rememberVisitorData(root)
            root
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Keeps the `visitorData` of the newest response so later calls can replay it. */
    private fun rememberVisitorData(root: JsonObject?) {
        val fromContext = root?.obj("responseContext")?.text("visitorData")
        val candidate = fromContext ?: root?.obj("responseContext")
            ?.obj("serviceTrackingParams")
            ?.arr("params")
            ?.let { params ->
                params.mapNotNull { it.asObj() }
                    .firstOrNull { it.text("key") == "visitor_data" }
                    ?.text("value")
            }
        if (!candidate.isNullOrBlank()) {
            visitorData = candidate
        }
    }

    private companion object {
        /** Web origin that serves the player JavaScript bundle. */
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"

        /** InnerTube API path appended to the client specific origin. */
        const val INNERTUBE_PATH = "/youtubei/v1/"
    }
}
