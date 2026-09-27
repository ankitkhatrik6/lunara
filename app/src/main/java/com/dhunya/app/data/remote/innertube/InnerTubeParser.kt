package com.dhunya.app.data.remote.innertube

import com.dhunya.app.domain.model.Song
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
    val itag: Int
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
            AudioStream(
                url = url,
                mimeType = format.text("mimeType").orEmpty(),
                bitrate = format.int("bitrate") ?: 0,
                itag = format.int("itag") ?: 0
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

    private fun String.isDuration(): Boolean = durationPattern.matches(this)

    private fun String?.toDurationMs(): Long {
        val parts = this?.trim()?.split(":")?.mapNotNull { it.toLongOrNull() } ?: return 0L
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    /** "Artist • Album • 2020" -> ["Artist", "Album"] (labels, years and durations dropped). */
    private fun String?.subtitleParts(): List<String> = this
        ?.split(SEPARATOR)
        ?.map { it.trim() }
        ?.filter { part ->
            part.isNotEmpty() &&
                !part.isDuration() &&
                !yearPattern.matches(part) &&
                part.lowercase() !in kindLabels
        }
        .orEmpty()

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
        val thumbnails = renderer?.obj("thumbnail")?.arr("thumbnails")?.objects().orEmpty()
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
        return if (marker > 0) substring(0, marker) + "=w544-h544-l90-rj" else this
    }
}
