package com.screentranslate.app.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class CameraTranslationOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val results = mutableListOf<Pair<RectF, String>>()

    private val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#CC1565C0")
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val bgPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#CC1565C0")
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 34f
        isAntiAlias = true
    }

    fun setResults(newResults: List<Pair<RectF, String>>) {
        results.clear()
        results.addAll(newResults)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((rect, text) in results) {
            canvas.drawRect(rect, boxPaint)
            val textWidth = textPaint.measureText(text)
            val labelHeight = 42f
            val bgRect = RectF(rect.left, rect.top - labelHeight, rect.left + textWidth + 12f, rect.top)
            canvas.drawRect(bgRect, bgPaint)
            canvas.drawText(text, rect.left + 6f, rect.top - 10f, textPaint)
        }
    }
}
