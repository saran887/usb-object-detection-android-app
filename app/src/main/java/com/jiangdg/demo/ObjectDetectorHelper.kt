package com.jiangdg.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

// Data class for detection results
data class DetectionResult(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)

class ObjectDetectorHelper(
    private val context: Context,
    private val threshold: Float = 0.45f
) {
    private var interpreter: Interpreter? = null
    private val labels: List<String>
    private val inputSize = 320 // SSD MobileNet V3 input size
    private val numDetections = 100 // Max detections per frame (model outputs 100)
    private val NMS_IOU_THRESHOLD = 0.45f  // IoU threshold for Non-Maximum Suppression

    // Pre-allocated reusable buffers to avoid GC pressure
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3).apply {
        order(ByteOrder.nativeOrder())
    }
    private val pixelBuffer = IntArray(inputSize * inputSize)
    private val outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } }
    private val outputClasses = Array(1) { FloatArray(numDetections) }
    private val outputScores = Array(1) { FloatArray(numDetections) }
    private val numDetectionsOutput = FloatArray(1)
    private val outputMap = HashMap<Int, Any>(4).apply {
        put(0, outputLocations)
        put(1, outputClasses)
        put(2, outputScores)
        put(3, numDetectionsOutput)
    }

    init {
        try {
            // Load the TFLite model
            val modelBuffer = loadModelFile("model.tflite")
            val options = Interpreter.Options()

            // Use 4 CPU threads with XNNPACK for fast inference
            options.setNumThreads(4)
            options.setUseXNNPACK(true)
            Log.i(TAG, "Using 4 CPU threads + XNNPACK")

            interpreter = Interpreter(modelBuffer, options)
            
            // Load labels from assets
            labels = try {
                context.assets.open("labels.txt").bufferedReader().use { 
                    it.readLines().filter { line -> line.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load labels.txt: ${e.message}")
                try {
                    context.assets.open("labelmap.txt").bufferedReader().use { 
                        it.readLines().filter { line -> line.isNotBlank() }
                    }
                } catch (e2: Exception) {
                    Log.w(TAG, "Could not load labelmap.txt, using defaults")
                    listOf("person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light")
                }
            }
            
            Log.i(TAG, "✅ Model initialized: model.tflite")
            Log.i(TAG, "Labels: ${labels.size}, Input: ${inputSize}x${inputSize}, Threshold: $threshold")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize model: ${e.message}", e)
            throw e
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        return try {
            if (interpreter == null) {
                Log.w(TAG, "Interpreter not initialized, skipping detection")
                return emptyList()
            }

            val startTime = System.nanoTime()
            
            // Resize only if needed
            val resizedBitmap = if (bitmap.width == inputSize && bitmap.height == inputSize) {
                bitmap
            } else {
                Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, false) // bilinear=false is faster
            }

            // Fill pre-allocated input buffer
            fillInputBuffer(resizedBitmap)
            if (resizedBitmap !== bitmap) resizedBitmap.recycle()
            
            // Run inference using pre-allocated output buffers
            try {
                interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Inference error: ${e.message}")
                return emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected inference error: ${e.message}")
                return emptyList()
            }
            
            // Parse results with NMS
            val rawResults = mutableListOf<DetectionResult>()
            val numDetected = numDetectionsOutput[0].toInt().coerceAtMost(numDetections)
            
            for (i in 0 until numDetected) {
                val score = outputScores[0][i]
                if (score >= threshold) {
                    val classIndex = outputClasses[0][i].toInt()
                    val label = if (classIndex in labels.indices) labels[classIndex] else "Unknown"
                    if (label == "???" || label == "Unknown") continue
                    
                    val loc = outputLocations[0][i]
                    val boundingBox = RectF(
                        loc[1] * inputSize, // xmin
                        loc[0] * inputSize, // ymin
                        loc[3] * inputSize, // xmax
                        loc[2] * inputSize  // ymax
                    )
                    
                    // Validate bounding box
                    if (boundingBox.width() > 2f && boundingBox.height() > 2f &&
                        boundingBox.left >= 0f && boundingBox.top >= 0f) {
                        rawResults.add(DetectionResult(label, score, boundingBox))
                    }
                }
            }
            
            // Apply Non-Maximum Suppression to remove duplicate/overlapping boxes
            val results = applyNMS(rawResults)

            val inferenceTimeMs = (System.nanoTime() - startTime) / 1_000_000.0
            if (results.isNotEmpty()) {
                Log.d(TAG, "Detected ${results.size} objects in ${String.format("%.1f", inferenceTimeMs)}ms: ${results.joinToString { "${it.label}(${(it.confidence*100).toInt()}%)" }}")
            }
            
            results
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Detection failed: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fill the pre-allocated input ByteBuffer from a bitmap.
     * Avoids allocating a new ByteBuffer each frame.
     */
    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(pixelBuffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = pixelBuffer[pixel++]
                inputBuffer.put((value shr 16 and 0xFF).toByte()) // R
                inputBuffer.put((value shr 8 and 0xFF).toByte())  // G
                inputBuffer.put((value and 0xFF).toByte())         // B
            }
        }
    }

    /**
     * Non-Maximum Suppression: removes overlapping boxes for the same class,
     * keeping only the highest-confidence detection per region.
     */
    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.size <= 1) return detections
        
        // Group by class label
        val grouped = detections.groupBy { it.label }
        val kept = mutableListOf<DetectionResult>()
        
        for ((_, classDetections) in grouped) {
            // Sort by confidence descending
            val sorted = classDetections.sortedByDescending { it.confidence }.toMutableList()
            
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                kept.add(best)
                
                // Remove all detections that overlap too much with the best one
                sorted.removeAll { other ->
                    computeIoU(best.boundingBox, other.boundingBox) > NMS_IOU_THRESHOLD
                }
            }
        }
        
        // Return sorted by confidence, top results first
        return kept.sortedByDescending { it.confidence }
    }

    /**
     * Compute Intersection over Union between two bounding boxes.
     */
    private fun computeIoU(a: RectF, b: RectF): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)
        
        val interArea = maxOf(0f, interRight - interLeft) * maxOf(0f, interBottom - interTop)
        if (interArea == 0f) return 0f
        
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val unionArea = areaA + areaB - interArea
        
        return if (unionArea > 0f) interArea / unionArea else 0f
    }
    
    @Synchronized
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i(TAG, "🔄 Model resources released")
        } catch (e: Exception) {
            Log.w(TAG, "Warning during model cleanup: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
    }
}
