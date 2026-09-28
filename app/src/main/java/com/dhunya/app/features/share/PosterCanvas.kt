package com.dhunya.app.features.share

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.dhunya.app.ui.theme.DhunyaAccent
import com.dhunya.app.ui.theme.DhunyaBackground
import kotlin.math.roundToInt

/** Pure drawing helpers kept apart so the generator stays reviewable. */
internal object PosterCanvas {

    private val background = DhunyaBackground
    private val accent = DhunyaAccent
    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val SOFT_WHITE = 0xE6FFFFFF.toInt()
    private const val FAINT_WHITE = 0x99FFFFFF.toInt()
    private const val DIM_WHITE = 0x59FFFFFF.toInt()
    private const val SCRIM_TOP = 0x66000000
    private const val SCRIM_BOTTOM = 0xDD000000.toInt()

    fun drawBackground(canvas: Canvas, artwork: android.graphics.Bitmap?) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = background.toArgb()
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)

        if (artwork != null && !artwork.isRecycled) {
            val scale = maxOf(
                canvas.width / artwork.width.toFloat(),
                canvas.height / artwork.height.toFloat()
            )
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    (canvas.width - artwork.width * scale) / 2f,
                    (canvas.height - artwork.height * scale) / 2f
                )
            }
            val wash = Paint().apply { alpha = 110 }
            canvas.drawBitmap(artwork, matrix, wash)
        }

        val scrim = Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, canvas.height.toFloat(),
                intArrayOf(SCRIM_TOP, SCRIM_BOTTOM),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), scrim)
    }

    /** Returns the y position where the title block should start. */
    fun drawBadge(canvas: Canvas): Float {
        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.toArgb()
            textSize = 30f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.24f
        }
        val badgeTop = 118f
        canvas.drawText("NOW PLAYING", 84f, badgeTop + 30f, badgePaint)
        return badgeTop + 78f
    }

    /** Returns the y position where the cover should start. */
    fun drawTitleBlock(canvas: Canvas, title: String, artist: String, top: Float): Float {
        val bound = canvas.width - 2 * 84f
        val safeTitle = ellipsizeSingle(title)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = 74f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleLayout = StaticLayout.Builder
            .obtain(safeTitle, 0, safeTitle.length, titlePaint, bound.roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(canvas.width / 2f - titleLayout.width / 2f, top)
        titleLayout.draw(canvas)
        canvas.restore()

        val safeArtist = ellipsizeSingle(artist.ifBlank { "Unknown Artist" })
        val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SOFT_WHITE
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val artistLayout = StaticLayout.Builder
            .obtain(safeArtist, 0, safeArtist.length, artistPaint, bound.roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        val artistTop = top + titleLayout.height + 18f
        canvas.save()
        canvas.translate(canvas.width / 2f - artistLayout.width / 2f, artistTop)
        artistLayout.draw(canvas)
        canvas.restore()
        return artistTop + artistLayout.height + 56f
    }

    /** Returns the y position where the quote block should start. */
    fun drawCover(canvas: Canvas, artwork: android.graphics.Bitmap?, top: Float): Float {
        val size = 640f
        val left = (canvas.width - size) / 2f
        val rect = RectF(left, top, left + size, top + size)
        val clip = Path().apply { addRoundRect(rect, 44f, 44f, Path.Direction.CW) }
        canvas.save()
        canvas.clipPath(clip)
        if (artwork != null && !artwork.isRecycled) {
            val src = RectF(0f, 0f, artwork.width.toFloat(), artwork.height.toFloat())
            canvas.drawBitmap(artwork, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
        } else {
            val placeholder = Paint().apply { color = 0xFF23262B.toInt() }
            canvas.drawRect(rect, placeholder)
            val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DIM_WHITE
                textSize = 200f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("\u266A", left + size / 2f, top + size / 2f + 70f, note)
        }
        canvas.restore()
        return top + size + 64f
    }

    fun drawQuote(canvas: Canvas, quote: LyricQuote?, top: Float) {
        if (quote == null) return
        val bound = canvas.width - 2 * 96f
        var cursor = top

        val neighbourPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FAINT_WHITE
            textSize = 40f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        }
        (quote.lead + quote.tail).take(3).forEach { line ->
            val layout = centered(line, neighbourPaint, bound)
            if (cursor + layout.height > canvas.height - 260f) return
            drawCentered(canvas, layout, cursor)
            cursor += layout.height + 14f
        }

        val currentPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = WHITE
            textSize = 46f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
        }
        val current = centered("\"${quote.current}\"", currentPaint, bound)
        if (cursor + current.height <= canvas.height - 240f) {
            drawCentered(canvas, current, cursor)
        }
    }

    fun drawFooter(canvas: Canvas) {
        val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.toArgb()
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.3f
        }
        canvas.drawText("DHUNYA", canvas.width / 2f, canvas.height - 150f, brandPaint)

        val tagPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FAINT_WHITE
            textSize = 29f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            "The music you love, shared",
            canvas.width / 2f,
            canvas.height - 104f,
            tagPaint
        )
    }

    private fun ellipsizeSingle(text: String): String =
        text.trim().replace(Regex("\\s+"), " ").ifBlank { "Unknown" }

    private fun centered(text: String, paint: TextPaint, maxWidth: Float): StaticLayout =
        StaticLayout.Builder
            .obtain(text, 0, text.length, paint, maxWidth.roundToInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()

    private fun drawCentered(canvas: Canvas, layout: StaticLayout, top: Float) {
        canvas.save()
        canvas.translate(canvas.width / 2f - layout.width / 2f, top)
        layout.draw(canvas)
        canvas.restore()
    }
}
