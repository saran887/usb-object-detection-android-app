package com.jiangdg.demo.manager

import android.content.Context
import android.util.Log
import com.jiangdg.demo.DetectionResult
import com.jiangdg.demo.ObjectDetectorHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class DetectionEngine(
    context: Context,
    private val onResult: (List<DetectionResult>) -> Unit
) {
    private val detector = ObjectDetectorHelper(context)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Conflated channel guarantees we only process the newest frame and drop older ones
    private val frameChannel = Channel<FrameData>(Channel.CONFLATED)
    private val isRunning = AtomicBoolean(false)

    // Reusable byte array buffer pool to achieve zero allocations
    private val bufferPool = ArrayDeque<ByteArray>()
    private val poolSize = 3

    class FrameData(val data: ByteArray, val width: Int, val height: Int)

    init {
        for (i in 0 until poolSize) {
            // Allocate arrays for VGA resolution (640x480 * 1.5 NV21 bytes)
            bufferPool.add(ByteArray(640 * 480 * 3 / 2))
        }
    }

    @Synchronized
    fun getBuffer(): ByteArray {
        return if (bufferPool.isNotEmpty()) bufferPool.removeFirst() else ByteArray(640 * 480 * 3 / 2)
    }

    @Synchronized
    fun recycleBuffer(buffer: ByteArray) {
        if (bufferPool.size < poolSize) {
            bufferPool.addLast(buffer)
        }
    }

    fun start() {
        if (isRunning.getAndSet(true)) return
        scope.launch {
            for (frame in frameChannel) {
                try {
                    val results = detector.detect(frame.data, frame.width, frame.height)
                    onResult(results)
                } catch (e: Exception) {
                    Log.e("DetectionEngine", "Inference exception: ${e.message}")
                } finally {
                    recycleBuffer(frame.data)
                }
            }
        }
    }

    fun feedFrame(data: ByteArray, width: Int, height: Int) {
        val pooledBuffer = getBuffer()
        val bytesToCopy = minOf(data.size, pooledBuffer.size)
        System.arraycopy(data, 0, pooledBuffer, 0, bytesToCopy)
        
        // Non-blocking offer to conflate queue and drop old frames
        frameChannel.offer(FrameData(pooledBuffer, width, height))
    }

    fun stop() {
        isRunning.set(false)
        frameChannel.close()
        detector.close()
    }
}
