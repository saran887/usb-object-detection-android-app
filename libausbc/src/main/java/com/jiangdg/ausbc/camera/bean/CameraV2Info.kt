
package com.jiangdg.ausbc.camera.bean

import android.hardware.camera2.CameraCharacteristics


@kotlin.Deprecated("Deprecated since version 3.3.0")
data class CameraV2Info(override val cameraId: String) : CameraInfo(cameraId) {
    var cameraType: Int = 0
    var cameraCharacteristics: CameraCharacteristics? = null

    override fun toString(): String {
        return "CameraV2Info(cameraId='$cameraId', " +
                "cameraType=$cameraType, " +
                "cameraCharacteristics=$cameraCharacteristics)"
    }
}
