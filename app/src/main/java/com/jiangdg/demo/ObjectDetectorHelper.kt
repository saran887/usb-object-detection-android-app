package com.jiangdg.demo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.task.vision.detector.ObjectDetector

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
    private val imageProcessor: ImageProcessor

    init {
        try {
            // Initialize image processor similar to MainActivity approach
            // Resize to 300x300 like MainActivity uses
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
                .build()
            
            // Create detector with options
            val options = ObjectDetector.ObjectDetectorOptions.builder()
                .setScoreThreshold(threshold)
                .setMaxResults(5)
                .build()
                
            detector = ObjectDetector.createFromFileAndOptions(
                context,
                "ssd_mobilenet_v1_1_metadata_1.tflite", // Same model as MainActivity
                options
            )
            
            // Load labels from assets (try both labelmap.txt and labels.txt like MainActivity)
            labels = try {
                FileUtil.loadLabels(context, "labels.txt") // MainActivity approach
            } catch (e: Exception) {
                try {
                    context.assets.open("labelmap.txt").bufferedReader().use { it.readLines() }
                } catch (e2: Exception) {
                    Log.w("ObjectDetectorHelper", "Could not load labels, using default")
                    listOf("person", "bicycle", "car", "motorcycle", "airplane") // Default labels
                }
            }
            
            Log.i("ObjectDetectorHelper", "✅ Model initialized successfully")
            Log.i("ObjectDetectorHelper", "Labels loaded: ${labels.size}")
            Log.i("ObjectDetectorHelper", "Confidence threshold: $threshold")
            
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "❌ Failed to initialize model: ${e.message}")
            throw e
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        return try {
            // Process frame exactly like MainActivity:
            // 1. Create TensorImage from bitmap
            var image = TensorImage.fromBitmap(bitmap)
            
            // 2. Apply preprocessing (resize to 300x300)
            image = imageProcessor.process(image)
            
            // 3. Run model detection (similar to model.process() in MainActivity)
            val detections = detector.detect(image)
            
            // 4. Convert results to our format
            detections.flatMap { detection ->
                detection.categories.map { category ->
                    val labelIdx = category.index
                    val label = if (labelIdx in labels.indices) labels[labelIdx] else category.label
                    
                    DetectionResult(
                        label = label,
                        confidence = category.score,
                        boundingBox = detection.boundingBox
                    )
                }
            }.filter { it.confidence >= threshold } // Apply threshold like MainActivity
            
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "❌ Detection failed: ${e.message}")
            emptyList()
        }
    }
    
    fun close() {
        try {
            detector.close()
            Log.i("ObjectDetectorHelper", "🔄 Model resources released")
        } catch (e: Exception) {
            Log.w("ObjectDetectorHelper", "Warning during model cleanup: ${e.message}")
        }
    }
}
