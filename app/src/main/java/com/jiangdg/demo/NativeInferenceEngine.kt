package com.jiangdg.demo

import java.nio.ByteBuffer

object NativeInferenceEngine {
    init {
        System.loadLibrary("nativelib")
    }

    external fun preprocessNV21(
        nv21Data: ByteArray,
        width: Int,
        height: Int,
        outTensorBuffer: ByteBuffer,
        targetWidth: Int,
        targetHeight: Int,
        isQuantized: Boolean,
        scale: Float,
        zeroPoint: Int
    )

    external fun nativeNMS(
        outputBuffer: ByteBuffer,
        numAnchors: Int,
        numElements: Int,
        scoreThreshold: Float,
        iouThreshold: Float,
        outDetections: FloatArray,
        maxDetections: Int
    ): Int
}
