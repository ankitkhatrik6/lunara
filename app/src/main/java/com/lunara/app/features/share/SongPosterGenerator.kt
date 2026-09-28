package com.lunara.app.features.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.lunara.app.core.result.Resource
import com.lunara.app.domain.model.Song
import com.lunara.app.domain.usecase.GetLyricsUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders the Instagram style "now playing" poster: blurred artwork backdrop, rounded cover,
 * track title / artist, a quoted section of [lyrics] around the moment the song was shared
 * (synced lines win when they exist), and the Lunara branding strip.
 *
 * Everything is drawn on a `Canvas` bitmap (no Compose capture), so it works identically on
 * every minSdk, and the output is a single 1080x1920 PNG ready to hand to the share sheet.
 */
@Singleton
class SongPosterGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient
) {

    suspend fun generate(
        song: Song,
        positionMs: Long,
        lyricsLoader: suspend () -> Resource<com.lunara.app.domain.model.Lyrics>
    ): File = withContext(Dispatchers.IO) {
        val lyrics = (lyricsLoader() as? Resource.Success)?.data
        val quote = LyricQuote.forPosition(lyrics, positionMs)

        val artwork = loadBitmap(song.artworkUrl)
        val bitmap = Bitmap.createBitmap(POSTER_W, POSTER_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        PosterCanvas.drawBackground(canvas, artwork)
        var cursorY = PosterCanvas.drawBadge(canvas)
        cursorY = PosterCanvas.drawTitleBlock(canvas, song.title, song.artistName, cursorY)
        cursorY = PosterCanvas.drawCover(canvas, artwork, cursorY)
        PosterCanvas.drawQuote(canvas, quote, cursorY)
        PosterCanvas.drawFooter(canvas)

        val outDir = File(context.cacheDir, "posters").apply { if (!exists()) mkdirs() }
        val outFile = File(outDir, "lunara_" + song.id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".png")
        FileOutputStream(outFile).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                error("Could not encode the share poster")
            }
            stream.flush()
        }
        outFile
    }

    private fun loadBitmap(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return try {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body ?: return null
                body.byteStream().use { BitmapFactory.decodeStream(it) }
            }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val POSTER_W = 1080
        const val POSTER_H = 1920
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"
    }
}
