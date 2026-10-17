/**
 * Ankit Khatri KC (C) 2026
 * Licensed under GPL-3.0
 */

package com.lunara.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.lunara.app.R
import com.lunara.app.ui.utils.resize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Draws the picture a share produces: an Instagram-story-sized card, the way Spotify wraps a song
 * before it goes out. The artwork fills the frame — a big cover up top, the same art blown up
 * behind a dark veil — with the title, the artist, the Lunara icon and a "Listen on Lunara"
 * sign-off. No QR, no link text: the link already travels with the picture when it is sent.
 *
 * Everything is drawn onto a plain [Canvas] rather than composed, because the result has to exist
 * as a PNG on disk that another app (Instagram, WhatsApp) can pick up - there is no view to
 * screenshot. The dimensions are the story ratio, 1080x1920, fixed so the output is identical on
 * every phone.
 */
object ShareStoryImage {

    private const val WIDTH = 1080
    private const val HEIGHT = 1920

    // Lunara's palette, matching the app theme. Used as the backdrop when the artwork has no
    // image to show (a blank cover, or a failed load) so the card is never a grey void.
    private const val FALLBACK_TOP = 0xFF1ED760.toInt()
    private const val FALLBACK_BOTTOM = 0xFF0A2A16.toInt()
    private const val INK = 0xFFFFFFFF.toInt()
    private const val INK_SOFT = 0xE6FFFFFF.toInt()

    // One loader for the whole app, built once. A fresh ImageLoader per share spins up its own
    // thread pool and memory cache every time, which is wasteful and can stall the UI while it
    // warms up — the "Lunara isn't responding" hitch when opening the share sheet.
    @Volatile
    private var cachedLoader: ImageLoader? = null

    private fun imageLoader(context: Context): ImageLoader =
        cachedLoader ?: synchronized(this) {
            cachedLoader ?: ImageLoader(context).also { cachedLoader = it }
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun create(
        context: Context,
        coverArtUrl: String?,
        title: String,
        artist: String,
        link: String,
        kindLabel: String,
    ): Bitmap =
        withContext(Dispatchers.Default) {
            val bitmap = createBitmap(WIDTH, HEIGHT)
            val canvas = Canvas(bitmap)

            var cover: Bitmap? = null
            if (!coverArtUrl.isNullOrBlank()) {
                cover =
                    runCatching {
                        // The raw thumbnail is ~60px, which is where the blur came from. Ask the
                        // server for the large copy (see resize()) so the card is drawn sharp.
                        val sharpUrl = coverArtUrl.resize(width = 1080, height = 1080)
                        val request =
                            ImageRequest
                                .Builder(context)
                                .data(sharpUrl)
                                .size(1080)
                                .allowHardware(false)
                                .build()
                        imageLoader(context).execute(request).image?.toBitmap()?.let { scaleToCover(it, 1080, 1080) }
                    }.getOrNull()
            }

            drawBackdrop(canvas, cover)
            drawCover(canvas, cover)
            drawText(canvas, title, artist, kindLabel)
            drawFooter(canvas, context)
            cover?.recycle()

            bitmap
        }

    /** The backdrop: the artwork blown up behind a dark veil, or Lunara green on failure. */
    private fun drawBackdrop(
        canvas: Canvas,
        cover: Bitmap?,
    ) {
        val paint = Paint()
        if (cover != null) {
            val full = scaleToCover(cover, WIDTH, HEIGHT)
            canvas.drawBitmap(full, null, RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), paint)
            if (full !== cover) full.recycle()
            paint.shader =
                LinearGradient(
                    0f, 0f, 0f, HEIGHT.toFloat(),
                    0x99000000.toInt(), 0xE6000000.toInt(),
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        } else {
            paint.shader =
                LinearGradient(
                    0f, 0f, 0f, HEIGHT.toFloat(),
                    FALLBACK_TOP, FALLBACK_BOTTOM,
                    Shader.TileMode.CLAMP,
                )
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), paint)
        }
    }

    /**
     * Scales [src] so it covers [width]x[height], cropping the overflow the way a centerCrop
     * ImageView does. Needed because the artwork arrives at its own size while the backdrop
     * needs the full card.
     */
    private fun scaleToCover(
        src: Bitmap,
        width: Int,
        height: Int,
    ): Bitmap {
        val scale = maxOf(width / src.width.toFloat(), height / src.height.toFloat())
        val scaled =
            Bitmap.createScaledBitmap(
                src,
                (src.width * scale).roundToInt().coerceAtLeast(1),
                (src.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        val x = ((scaled.width - width) / 2).coerceAtLeast(0)
        val y = ((scaled.height - height) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, x, y, width.coerceAtMost(scaled.width), height.coerceAtMost(scaled.height))
        if (scaled !== src && scaled !== cropped) scaled.recycle()
        return cropped
    }

    /** The artwork itself: a big rounded square, high up the card. */
    private fun drawCover(
        canvas: Canvas,
        cover: Bitmap?,
    ) {
        val size = WIDTH * 0.78f
        val left = (WIDTH - size) / 2f
        val top = HEIGHT * 0.10f
        val rect = RectF(left, top, left + size, top + size)
        // A dark bed, so a failed load still reads as a deliberate panel rather than a hole.
        canvas.drawRoundRect(rect, 56f, 56f, Paint().apply { color = 0x40000000.toInt() })
        if (cover != null) {
            val paint = Paint().apply { isAntiAlias = true }
            canvas.withClip(Path().apply { addRoundRect(rect, 56f, 56f, Path.Direction.CW) }) {
                drawBitmap(cover, null, rect, paint)
            }
        }
    }

    /**
     * The pill (SONG, ALBUM, PLAYLIST, ARTIST), the title, and the artist, centred under the
     * artwork. Sized for a story viewed full-screen: readable without squinting.
     */
    private fun drawText(
        canvas: Canvas,
        title: String,
        artist: String,
        kindLabel: String,
    ) {
        val center = WIDTH / 2f
        var y = HEIGHT * 0.10f + WIDTH * 0.78f + 72f

        val pillPaint =
            TextPaint().apply {
                color = INK
                textSize = 40f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.22f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
        canvas.drawText(kindLabel.uppercase(), center, y, pillPaint)
        y += 104f

        val titlePaint =
            TextPaint().apply {
                color = INK
                textSize = 76f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
        y = drawCentred(canvas, title.ifBlank { "Unknown" }, titlePaint, y, maxLines = 3)

        if (artist.isNotBlank()) {
            val artistPaint =
                TextPaint().apply {
                    color = INK_SOFT
                    textSize = 52f
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
            y += 24f
            drawCentred(canvas, artist, artistPaint, y, maxLines = 2)
        }
    }

    /**
     * [text] wrapped to the card's width and drawn centred, up to [maxLines] with an ellipsis.
     * Returns the baseline below the last line drawn, for whatever comes next.
     */
    private fun drawCentred(
        canvas: Canvas,
        text: String,
        paint: TextPaint,
        firstBaseline: Float,
        maxLines: Int,
    ): Float {
        // Wrap by words to the card's usable width, then draw each line with a single centred
        // drawText at the card's centre. StaticLayout was tried here and shifted the block to the
        // left: its textAlign handling plus a translation offset double-counted the centring.
        val center = WIDTH / 2f
        val maxWidth = WIDTH * 0.86f
        val lines = wrapWords(text, paint, maxWidth).take(maxLines)
        var y = firstBaseline
        val lineStep = paint.textSize * 1.18f
        for (line in lines) {
            canvas.drawText(line, center, y, paint)
            y += lineStep
        }
        return y
    }

    /** [text] broken into lines that each fit [maxWidth], at this paint's size. */
    private fun wrapWords(
        text: String,
        paint: TextPaint,
        maxWidth: Float,
    ): List<String> {
        val words = text.split(' ')
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isEmpty() || paint.measureText(candidate) <= maxWidth) {
                current.clear().append(candidate)
            } else {
                lines.add(current.toString())
                current.clear().append(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }

    /**
     * The foot of the card: the Lunara icon with the wordmark and "Listen on Lunara". No QR,
     * no link text — the link already travels with the picture when it is sent.
     */
    private fun drawFooter(
        canvas: Canvas,
        context: Context,
    ) {
        val center = WIDTH / 2f
        val baseLine = HEIGHT * 0.885f

        val icon =
            runCatching { ContextCompat.getDrawable(context, R.drawable.small_icon)?.toBitmap(224, 224) }.getOrNull()
        val iconSize = 168f
        if (icon != null) {
            canvas.drawBitmap(
                icon, null,
                RectF(center - iconSize / 2f, baseLine - iconSize, center + iconSize / 2f, baseLine),
                Paint().apply { isAntiAlias = true },
            )
        }

        val namePaint =
            TextPaint().apply {
                color = INK
                textSize = 64f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.04f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
        canvas.drawText(context.getString(R.string.app_name), center, baseLine + 84f, namePaint)

        val tagPaint =
            TextPaint().apply {
                color = INK_SOFT
                textSize = 40f
                textAlign = Paint.Align.CENTER
                isAntiAlias = true
            }
        canvas.drawText("Listen on Lunara", center, baseLine + 148f, tagPaint)
    }
}
