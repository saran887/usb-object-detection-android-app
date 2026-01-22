package com.jiangdg.demo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
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
    private val threshold: Float = 0.5f
) {
    private var interpreter: Interpreter? = null
    private val labels: List<String>
    private val inputSize = 320 // SSD MobileNet V3 input size
    private val numDetections = 100 // Max detections per frame (model outputs 100)

    init {
        try {
            // Load the TFLite model
            val modelBuffer = loadModelFile("model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            
            // Load labels from assets
            labels = try {
                context.assets.open("labels.txt").bufferedReader().use { 
                    it.readLines().filter { line -> line.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w("ObjectDetectorHelper", "Could not load labels.txt: ${e.message}")
                try {
                    context.assets.open("labelmap.txt").bufferedReader().use { 
                        it.readLines().filter { line -> line.isNotBlank() }
                    }
                } catch (e2: Exception) {
                    Log.w("ObjectDetectorHelper", "Could not load labelmap.txt, using defaults")
                    listOf("person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light")
                }
            }
            
            Log.i("ObjectDetectorHelper", "✅ Model initialized successfully: model.tflite")
            Log.i("ObjectDetectorHelper", "Labels loaded: ${labels.size}")
            Log.i("ObjectDetectorHelper", "Input size: ${inputSize}x${inputSize}")
            Log.i("ObjectDetectorHelper", "Confidence threshold: $threshold")
            
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "❌ Failed to initialize model: ${e.message}", e)
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
            // Safety check for interpreter
            if (interpreter == null) {
                Log.w("ObjectDetectorHelper", "Interpreter not initialized, skipping detection")
                return emptyList()
            }
            
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
            val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)
            
            // Output arrays for SSD MobileNet
            val outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } }
            val outputClasses = Array(1) { FloatArray(numDetections) }
            val outputScores = Array(1) { FloatArray(numDetections) }
            val numDetectionsOutput = FloatArray(1)
            
            // Run inference with additional safety
            val outputs = mutableMapOf<Int, Any>()
            outputs[0] = outputLocations
            outputs[1] = outputClasses
            outputs[2] = outputScores
            outputs[3] = numDetectionsOutput
            
            try {
                interpreter?.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
            } catch (e: IllegalArgumentException) {
                Log.e("ObjectDetectorHelper", "TensorFlow inference error: ${e.message}")
                return emptyList()
            } catch (e: Exception) {
                Log.e("ObjectDetectorHelper", "Unexpected inference error: ${e.message}")
                return emptyList()
            }
            
            // Parse results
            val results = mutableListOf<DetectionResult>()
            val numDetected = numDetectionsOutput[0].toInt().coerceAtMost(numDetections)
            
            for (i in 0 until numDetected) {
                val score = outputScores[0][i]
                if (score >= threshold) {
                    val classIndex = outputClasses[0][i].toInt()
                    val label = if (classIndex in labels.indices) labels[classIndex] else "Unknown"
                    
                    // Convert from [ymin, xmin, ymax, xmax] normalized to RectF
                    val location = outputLocations[0][i]
                    val boundingBox = RectF(
                        location[1] * inputSize, // xmin
                        location[0] * inputSize, // ymin
                        location[3] * inputSize, // xmax
                        location[2] * inputSize  // ymax
                    )
                    
                    results.add(DetectionResult(label, score, boundingBox))
                }
            }
            
            if (results.isNotEmpty()) {
                Log.d("ObjectDetectorHelper", "Detected ${results.size} objects: ${results.joinToString { "${it.label}(${(it.confidence*100).toInt()}%)" }}")
            }
            
            results
            
        } catch (e: Exception) {
            Log.e("ObjectDetectorHelper", "❌ Detection failed: ${e.message}", e)
            emptyList()
        }
    }
    
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // Model expects uint8 input [1, 320, 320, 3]
        val byteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())
        
        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val value = intValues[pixel++]
                // Store as uint8 (0-255 range)
                byteBuffer.put((value shr 16 and 0xFF).toByte())
                byteBuffer.put((value shr 8 and 0xFF).toByte())
                byteBuffer.put((value and 0xFF).toByte())
            }
        }
        
        return byteBuffer
    }
    
    @Synchronized
    fun close() {
        try {
            interpreter?.close()
            interpreter = null
            Log.i("ObjectDetectorHelper", "🔄 Model resources released")
        } catch (e: Exception) {
            Log.w("ObjectDetectorHelper", "Warning during model cleanup: ${e.message}")
        }
    }
}
