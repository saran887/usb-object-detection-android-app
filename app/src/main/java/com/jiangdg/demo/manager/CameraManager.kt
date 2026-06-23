package com.jiangdg.demo.manager

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.TextureView
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.usb.USBMonitor

class CameraManager(
    private val context: Context,
    private val callback: CameraFrameCallback
) : ICameraStateCallBack {

    interface CameraFrameCallback {
        fun onFrameCaptured(cameraIndex: Int, data: ByteArray, width: Int, height: Int)
    }

    private var cameraClient: MultiCameraClient? = null
    private val cameras = HashMap<Int, MultiCameraClient.ICamera>()
    private var activeCameraIndex = -1

    fun initialize() {
        cameraClient = MultiCameraClient(context, object : IDeviceConnectCallBack {
            override fun onAttachDev(device: UsbDevice?) {
                device ?: return
                val camera = CameraUVC(context, device)
                val index = assignIndex(device)
                cameras[index] = camera
                camera.setCameraStateCallBack(this@CameraManager)
                cameraClient?.requestPermission(device)
            }

            override fun onDetachDec(device: UsbDevice?) {
                device ?: return
                val index = assignIndex(device)
                cameras.remove(index)?.apply {
                    closeCamera()
                }
            }

            override fun onConnectDev(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                device ?: return
                ctrlBlock ?: return
                val index = assignIndex(device)
                cameras[index]?.apply {
                    setUsbControlBlock(ctrlBlock)
                    if (index == activeCameraIndex) {
                        openCameraStream(index)
                    }
                }
            }

            override fun onDisConnectDec(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {}
            override fun onCancelDev(device: UsbDevice?) {}
        })
        cameraClient?.register()
    }

    private fun assignIndex(device: UsbDevice): Int {
        val name = device.deviceName.lowercase()
        return when {
            name.contains("front") || device.deviceId % 4 == 0 -> 1
            name.contains("rear") || device.deviceId % 4 == 1 -> 2
            name.contains("left") || device.deviceId % 4 == 2 -> 3
            else -> 4 // Right
        }
    }

    fun openCameraStream(index: Int, previewSurface: TextureView? = null) {
        val camera = cameras[index] ?: return
        val resolution = when (index) {
            1, 2 -> Pair(640, 480) // Front & Rear
            else -> Pair(320, 240) // Left & Right
        }

        val request = CameraRequest.Builder()
            .setPreviewWidth(resolution.first)
            .setPreviewHeight(resolution.second)
            .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
            .setRenderMode(CameraRequest.RenderMode.NORMAL)
            .setRawPreviewData(true)
            .create()

        try {
            camera.openCamera(previewSurface, request)
            camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                override fun onPreviewData(data: ByteArray?, width: Int, height: Int, format: IPreviewDataCallBack.DataFormat) {
                    if (data != null) {
                        callback.onFrameCaptured(index, data, width, height)
                    }
                }
            })
            activeCameraIndex = index
        } catch (e: Exception) {
            Log.e("CameraManager", "Failed to start camera $index: ${e.message}")
        }
    }

    fun stopCameraStream(index: Int) {
        cameras[index]?.closeCamera()
        if (activeCameraIndex == index) {
            activeCameraIndex = -1
        }
    }

    fun switchActiveCamera(newIndex: Int, previewSurface: TextureView? = null) {
        if (newIndex == activeCameraIndex) return
        if (activeCameraIndex != -1) {
            stopCameraStream(activeCameraIndex)
        }
        openCameraStream(newIndex, previewSurface)
    }

    override fun onCameraState(self: MultiCameraClient.ICamera, code: ICameraStateCallBack.State, msg: String?) {
        Log.d("CameraManager", "Camera State updated: ${self.getUsbDevice().deviceName} -> $code")
    }

    fun release() {
        cameras.values.forEach { it.closeCamera() }
        cameras.clear()
        cameraClient?.unRegister()
        cameraClient?.destroy()
        cameraClient = null
    }
}
