
package com.jiangdg.natives


object YUVUtils {
    init {
        System.loadLibrary("nativelib")
    }

    external fun yuv420spToNv21(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420sp(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420spWithMirror(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420p(data: ByteArray, width: Int, height: Int)
    external fun nv21ToYuv420pWithMirror(data: ByteArray, width: Int, height: Int)
    external fun nativeRotateNV21(data: ByteArray, width: Int, height: Int, degree: Int)
}