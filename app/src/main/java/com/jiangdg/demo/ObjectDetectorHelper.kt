package com.jiangdg.demo

import android.content.Context
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
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
    private val inputSize = 320
    private val numAnchors = 2100
    private val numElements = 84
    private val maxDetections = 20

    // Direct ByteBuffer matching the INT8 quantized input shape [1, 320, 320, 3] -> 320 * 320 * 3 bytes
    private val inputBuffer: ByteBuffer = ByteBuffer.allocateDirect(inputSize * inputSize * 3).apply {
        order(ByteOrder.nativeOrder())
    }

    // Direct ByteBuffer matching the output tensor shape [1, 84, 2100] -> Float (4 bytes)
    private val outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(1 * numElements * numAnchors * 4).apply {
        order(ByteOrder.nativeOrder())
    }

    private val detectionResultsArray = FloatArray(maxDetections * 6)

    // Quantization parameters (defaults to standard normalized values, dynamically initialized from model metadata)
    private var inputScale: Float = 1.0f / 255.0f
    private var inputZeroPoint: Int = -128

    init {
        try {
            val modelBuffer = loadModelFile("model.tflite")
            val options = Interpreter.Options().apply {
                try {
                    addDelegate(GpuDelegate())
                } catch (e: Exception) {
                    setNumThreads(4)
                }
            }

            interpreter = Interpreter(modelBuffer, options)
            
            // Extract quantization parameters if available
            try {
                val inputTensor = interpreter?.getInputTensor(0)
                inputTensor?.quantizationParams()?.let {
                    inputScale = it.scale
                    inputZeroPoint = it.zeroPoint
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not extract quantization parameters, using defaults: ${e.message}")
            }

            labels = try {
                context.assets.open("labels.txt").bufferedReader().use { 
                    it.readLines().filter { line -> line.isNotBlank() }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not load labels.txt: ${e.message}")
                emptyList()
            }
            Log.i(TAG, "✅ Optimized Model initialized: YOLOv8n INT8.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize model: ${e.message}", e)
            throw e
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun detect(nv21Frame: ByteArray, width: Int, height: Int): List<DetectionResult> {
        val interp = interpreter ?: return emptyList()

        // 1. Native preprocessor: bilinearly scales NV21 and quantizes directly to inputBuffer
        inputBuffer.rewind()
        NativeInferenceEngine.preprocessNV21(
            nv21Data = nv21Frame,
            width = width,
            height = height,
            outTensorBuffer = inputBuffer,
            targetWidth = inputSize,
            targetHeight = inputSize,
            isQuantized = true,
            scale = inputScale,
            zeroPoint = inputZeroPoint
        )

        // 2. Direct execution
        outputBuffer.rewind()
        try {
            interp.run(inputBuffer, outputBuffer)
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            return emptyList()
        }

        // 3. Native NMS
        outputBuffer.rewind()
        val count = NativeInferenceEngine.nativeNMS(
            outputBuffer = outputBuffer,
            numAnchors = numAnchors,
            numElements = numElements,
            scoreThreshold = threshold,
            iouThreshold = 0.45f,
            outDetections = detectionResultsArray,
            maxDetections = maxDetections
        )

        // 4. Map flat outputs back to structured DetectionResult object list
        val results = mutableListOf<DetectionResult>()
        for (i in 0 until count) {
            val idx = i * 6
            val classId = detectionResultsArray[idx].toInt()
            val score = detectionResultsArray[idx + 1]
            val x1 = detectionResultsArray[idx + 2]
            val y1 = detectionResultsArray[idx + 3]
            val x2 = detectionResultsArray[idx + 4]
            val y2 = detectionResultsArray[idx + 5]

            val label = if (classId in labels.indices) labels[classId] else "Unknown"
            val boundingBox = RectF(x1, y1, x2, y2)
            results.add(DetectionResult(label, score, boundingBox))
        }

        return results
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        private const val TAG = "ObjectDetectorHelper"
    }
}
