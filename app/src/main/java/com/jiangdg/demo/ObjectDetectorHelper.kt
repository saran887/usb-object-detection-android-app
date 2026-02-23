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
    private val inputSize = 320 // YOLOv8 input size
    private val numAnchors = 2100 // YOLOv8 320x320 anchors
    private val numElements = 84 // 4 (box) + 80 (classes)
    private val NMS_IOU_THRESHOLD = 0.45f

    // Pre-allocated reusable buffers to avoid GC pressure
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3 * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val pixelBuffer = IntArray(inputSize * inputSize)
    
    // Output shape for YOLOv8 is [1, 84, 2100]
    private val outputBuffer = Array(1) { Array(numElements) { FloatArray(numAnchors) } }

    init {
        try {
            val modelBuffer = loadModelFile("model.tflite")
            val options = Interpreter.Options()

            options.setNumThreads(4)

            interpreter = Interpreter(modelBuffer, options)
            
            labels = try {
                context.assets.open("labels.txt").bufferedReader().use { 
                    it.readLines().filter { line -> line.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load labels.txt: \${e.message}")
                emptyList()
            }
            Log.i(TAG, "✅ Model initialized: YOLOv8n. Labels: \${labels.size}")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize model: \${e.message}", e)
            throw e
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val interp = interpreter ?: return emptyList()

        val resizedBitmap = if (bitmap.width == inputSize && bitmap.height == inputSize) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true) 
        }

        fillInputBuffer(resizedBitmap)
        if (resizedBitmap !== bitmap) resizedBitmap.recycle()
        
        try {
            interp.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: \${e.message}")
            return emptyList()
        }
        
        val rawResults = mutableListOf<DetectionResult>()
        val output = outputBuffer[0]
        
        // Output format: [84, 2100]
        var maxOverallConf = 0f
        
        for (i in 0 until numAnchors) {
            var maxClassConf = 0f
            var maxClassId = -1
            
            // Find class with highest confidence
            for (c in 0 until 80) {
                val conf = output[c + 4][i]
                if (conf > maxClassConf) {
                    maxClassConf = conf
                    maxClassId = c
                }
            }
            
            if (maxClassConf > maxOverallConf) {
                maxOverallConf = maxClassConf
            }
            
            if (maxClassConf >= threshold) {
                // YOLOv8 bounding boxes can be normalized (0.0 - 1.0) or raw pixels (0 - 320)
                // We check if the values are normalized by seeing if width/height is <= 1.0
                val isNormalized = output[2][0] <= 1.0f && output[3][0] <= 1.0f
                
                var cx = output[0][i]
                var cy = output[1][i]
                var w = output[2][i]
                var h = output[3][i]
                
                if (isNormalized) {
                    cx *= inputSize
                    cy *= inputSize
                    w *= inputSize
                    h *= inputSize
                }
                
                // Reduce bounding box size slightly by 15% for a tighter fit around the object
                w *= 0.85f
                h *= 0.85f
                
                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f
                
                val boundingBox = RectF(left, top, right, bottom)
                val label = if (maxClassId in labels.indices) labels[maxClassId] else "Unknown"
                
                rawResults.add(DetectionResult(label, maxClassConf, boundingBox))
            }
        }
        
        Log.d(TAG, "Detection completed. Max confidence found: \$maxOverallConf. Filtered results: \${rawResults.size}")
        return applyNMS(rawResults)
    }

    private fun fillInputBuffer(bitmap: Bitmap) {
        inputBuffer.rewind()
        bitmap.getPixels(pixelBuffer, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // YOLOv8 expects Float32 input, normalized to 0.0 - 1.0
        for (pixel in pixelBuffer) {
            inputBuffer.putFloat(((pixel shr 16 and 0xFF) / 255f)) // R
            inputBuffer.putFloat(((pixel shr 8 and 0xFF) / 255f))  // G
            inputBuffer.putFloat(((pixel and 0xFF) / 255f))        // B
        }
    }

    private fun applyNMS(detections: List<DetectionResult>): List<DetectionResult> {
        if (detections.size <= 1) return detections
        
        val kept = mutableListOf<DetectionResult>()
        val grouped = detections.groupBy { it.label }
        
        for ((_, classDetections) in grouped) {
            val sorted = classDetections.sortedByDescending { it.confidence }.toMutableList()
            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                kept.add(best)
                sorted.removeAll { other ->
                    computeIoU(best.boundingBox, other.boundingBox) > NMS_IOU_THRESHOLD
                }
            }
        }
        return kept.sortedByDescending { it.confidence }
    }

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
        } catch (e: Exception) {}
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
    }
}
