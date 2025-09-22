package com.jiangdg.demo

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.task.vision.detector.ObjectDetector
import org.tensorflow.lite.support.image.TensorImage

// Data class for detection results
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: android.graphics.RectF
)

class ObjectDetectorHelper(
    context: Context,
    private val threshold: Float = 0.5f
) {
    private val detector: ObjectDetector
    private val labels: List<String>

    init {
        val options = ObjectDetector.ObjectDetectorOptions.builder()
            .setScoreThreshold(threshold)
            .setMaxResults(5)
            .build()
        detector = ObjectDetector.createFromFileAndOptions(
            context,
            "ssd_mobilenet_v1_1_metadata_1.tflite",
            options
        )
        // Load labels from assets/labelmap.txt
        labels = context.assets.open("labelmap.txt").bufferedReader().use { it.readLines() }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val results = detector.detect(TensorImage.fromBitmap(bitmap))
        return results.flatMap { detection ->
            detection.categories.map { category ->
                val labelIdx = category.index
                val label = if (labelIdx in labels.indices) labels[labelIdx] else category.label
                DetectionResult(
                    label = label,
                    confidence = category.score,
                    boundingBox = detection.boundingBox
                )
            }
        }
    }
}
