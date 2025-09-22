
package com.jiangdg.ausbc.camera.bean


@kotlin.Deprecated("Deprecated since version 3.3.0")
open class CameraInfo(open val cameraId: String) {
    var cameraPreviewSizes: MutableList<PreviewSize>? = null
    var cameraVid: Int = 0
    var cameraPid: Int = 0
}
