package com.zek.mictsplus.translate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View

/**
 * Full-screen pass-through overlay that renders translated text blocks
 * directly on top of their detected on-screen bounding boxes.
 */
class LiveTranslateOverlayView(context: Context) : View(context) {

    private var blocks: List<TranslatedBlock> = emptyList()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(215, 18, 22, 28)
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(90, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
    }

    fun updateBlocks(newBlocks: List<TranslatedBlock>) {
        blocks = newBlocks
        invalidate()
    }

    fun clear() {
        blocks = emptyList()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val pad = 6f * density
        val radius = 8f * density

        for (block in blocks) {
            val r = block.rect
            if (r.width() < 16 || r.height() < 12) continue

            val boxWidth = r.width().coerceAtLeast((80 * density).toInt())
            // Dynamically size text to fit comfortably inside the original block height
            val estimatedSize = (r.height() * 0.42f).coerceIn(11f * density, 19f * density)
            textPaint.textSize = estimatedSize

            val layout = StaticLayout.Builder
                .obtain(block.translatedText, 0, block.translatedText.length, textPaint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build()

            val drawHeight = maxOf(r.height().toFloat(), layout.height.toFloat())
            val rectF = RectF(
                (r.left - pad).coerceAtLeast(0f),
                (r.top - pad).coerceAtLeast(0f),
                (r.left + boxWidth + pad).coerceAtMost(width.toFloat()),
                (r.top + drawHeight + pad).coerceAtMost(height.toFloat())
            )

            canvas.drawRoundRect(rectF, radius, radius, bgPaint)
            canvas.drawRoundRect(rectF, radius, radius, borderPaint)

            canvas.save()
            canvas.translate(rectF.left + pad, rectF.top + pad * 0.6f)
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
