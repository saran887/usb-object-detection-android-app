package com.jiangdg.demo

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.base.MultiCameraFragment
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.utils.ToastUtils
import com.jiangdg.demo.databinding.FragmentMultiCameraBinding
import com.jiangdg.ausbc.widget.AspectRatioTextureView
import java.util.concurrent.Executors
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import android.graphics.Bitmap

/** Multi-road camera demo
 *
 * @author Created by jiangdg
 */
class DemoMultiCameraFragment : MultiCameraFragment(), ICameraStateCallBack {
    private lateinit var mViewBinding: FragmentMultiCameraBinding
    private var mTextureView: AspectRatioTextureView? = null
    private var mCamera: MultiCameraClient.ICamera? = null
    private lateinit var overlayView: OverlayView
    private lateinit var objectDetector: ObjectDetectorHelper
    private lateinit var ttsHelper: TTSHelper
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // For TTS label repeat suppression
    private var lastSpokenLabel: String? = null
    private var repeatFrameCount: Int = 0
    private val skipFrames = 7 // Skip TTS for 7 frames if label is repeated

    override fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        ToastUtils.show("onCameraConnected called")
        mCamera = camera
        val textureView = mTextureView
        if (textureView == null) {
            ToastUtils.show("TextureView not ready, retrying...")
            Handler(Looper.getMainLooper()).postDelayed({
                onCameraConnected(camera)
            }, 200)
            return
        }
        ToastUtils.show("Preview surface ready")
        camera.setCameraStateCallBack(this)
        val request = getCameraRequest()
        ToastUtils.show("Opening camera...")
        camera.openCamera(textureView, request)

        if (camera is CameraUVC) {
            camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                override fun onPreviewData(
                    data: ByteArray?,
                    width: Int,
                    height: Int,
                    format: IPreviewDataCallBack.DataFormat
                ) {
                    if (data != null && format == IPreviewDataCallBack.DataFormat.NV21) {
                        processFrame(data, width, height)
                    }
                }
            })
        }
    }

    private fun processFrame(data: ByteArray, width: Int, height: Int) {
        executor.execute {
            val bitmap = yuvToBitmap(data, width, height)
            val results = objectDetector.detect(bitmap)
            mainHandler.post {
                overlayView.setResults(results)
                // Speak the first detected label, if any, with repeat suppression
                if (results.isNotEmpty()) {
                    val label = results[0].label
                    if (label == lastSpokenLabel) {
                        repeatFrameCount++
                        if (repeatFrameCount > skipFrames) {
                            ttsHelper.speak(label)
                            repeatFrameCount = 0
                        }
                    } else {
                        ttsHelper.speak(label)
                        lastSpokenLabel = label
                        repeatFrameCount = 0
                    }
                } else {
                    lastSpokenLabel = null
                    repeatFrameCount = 0
                }
            }
        }
    }

    private fun yuvToBitmap(data: ByteArray, width: Int, height: Int): Bitmap {
        val yuvImage = android.graphics.YuvImage(data, android.graphics.ImageFormat.NV21, width, height, null)
        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 90, out)
        val yuv = out.toByteArray()
        return android.graphics.BitmapFactory.decodeByteArray(yuv, 0, yuv.size)
    }

    override fun onCameraState(
        self: MultiCameraClient.ICamera,
        code: ICameraStateCallBack.State,
        msg: String?
    ) {
        ToastUtils.show("onCameraState: $code, msg: $msg")
        when (code) {
            ICameraStateCallBack.State.ERROR -> {
                ToastUtils.show("Camera Error: ${msg ?: "unknown error"}")
            }
            ICameraStateCallBack.State.OPENED -> {
                ToastUtils.show("Camera Opened: ${self.getUsbDevice().deviceName}")
            }
            ICameraStateCallBack.State.CLOSED -> {
                ToastUtils.show("Camera Closed")
            }
        }
    }

    override fun initView() {
        super.initView()
        mTextureView = mViewBinding.root.findViewById(R.id.fullscreen_texture_view)
        overlayView = mViewBinding.root.findViewById(R.id.overlay_view)
        objectDetector = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
        ttsHelper = TTSHelper(requireContext())
    }
    override fun onDestroyView() {
    ttsHelper.shutdown()
    super.onDestroyView()
    }
    /**
     * Enumerate connected USB devices and trigger camera connection if found
     */
    private fun enumerateAndConnectCamera() {
        // Removed USB attach/detach logic as requested
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentMultiCameraBinding.inflate(inflater, container, false)
        mTextureView = mViewBinding.root.findViewById(R.id.fullscreen_texture_view)
        // overlayView will be initialized in initView
        return mViewBinding.root
    }

    private fun getCameraRequest(): CameraRequest {
        return try {
            val format = CameraRequest.PreviewFormat.FORMAT_MJPEG
            ToastUtils.show("Camera format: $format")
            CameraRequest.Builder()
                .setPreviewWidth(1280) // Use a 16:9 ratio for portrait screens
                .setPreviewHeight(720)
                .setPreviewFormat(format)
                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                .setAspectRatioShow(false) // Stretch to fill
                .setRawPreviewData(false)
                .create().also {
                    ToastUtils.show("Created camera config: ${it.previewWidth}x${it.previewHeight}")
                }
        } catch (e: Exception) {
            ToastUtils.show("MJPEG 1280x720 failed, trying YUYV 1280x720")
            try {
                CameraRequest.Builder()
                    .setPreviewWidth(1280)
                    .setPreviewHeight(720)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(false)
                    .create().also {
                        ToastUtils.show("Created fallback config: ${it.previewWidth}x${it.previewHeight}")
                    }
            } catch (e2: Exception) {
                ToastUtils.show("All configs failed: ${e2.message}")
                throw e2
            }
        }
    }

    override fun onCameraAttached(camera: MultiCameraClient.ICamera) {
        // No-op for single camera UI
    }

    protected override fun onCameraDisConnected(camera: MultiCameraClient.ICamera) {
        // For single camera UI, just close the camera
        camera.closeCamera()
    }

    protected override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        // No-op for single camera UI, or add cleanup logic if needed
    }
}
