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
        strokeWidth = 8f
    }
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 56f
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }
    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#DD000000")
        style = Paint.Style.FILL
    }
    private var results: List<DetectionResult> = emptyList()
    private var imageWidth: Int = 320
    private var imageHeight: Int = 320

    fun setResults(results: List<DetectionResult>) {
        this.results = results
        invalidate()
    }

    fun setImageSize(width: Int, height: Int) {
        imageWidth = width
        imageHeight = height
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        
        // Calculate scale factors to map from model input size to view size
        val scaleX = width.toFloat() / imageWidth
        val scaleY = height.toFloat() / imageHeight
        
        for (result in results) {
            // Scale bounding box from model coordinates to view coordinates
            val scaledBox = RectF(
                result.boundingBox.left * scaleX,
                result.boundingBox.top * scaleY,
                result.boundingBox.right * scaleX,
                result.boundingBox.bottom * scaleY
            )
            
            // Draw bounding box
            canvas.drawRect(scaledBox, boxPaint)
            
            // Draw label with background
            val label = result.label
            val textBounds = Rect()
            textPaint.getTextBounds(label, 0, label.length, textBounds)
            
            val textX = scaledBox.left
            val textY = scaledBox.top - 12
            val backgroundRect = RectF(
                textX,
                textY - textBounds.height() - 8,
                textX + textBounds.width() + 16,
                textY + 4
            )
            
            canvas.drawRect(backgroundRect, backgroundPaint)
            canvas.drawText(label, textX + 8, textY, textPaint)
        }
    }
}
