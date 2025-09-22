package com.jiangdg.demo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 48f
        style = Paint.Style.FILL
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }
    private var results: List<DetectionResult> = emptyList()

    fun setResults(results: List<DetectionResult>) {
        this.results = results
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (result in results) {
            canvas.drawRect(result.boundingBox, boxPaint)
            val label = "${result.label} ${(result.confidence * 100).toInt()}%"
            canvas.drawText(label, result.boundingBox.left, result.boundingBox.top - 10, textPaint)
        }
    }
}
