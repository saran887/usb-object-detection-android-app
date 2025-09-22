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
    
    // Camera components for 4 cameras
    private var mCamera1: MultiCameraClient.ICamera? = null
    private var mCamera2: MultiCameraClient.ICamera? = null
    private var mCamera3: MultiCameraClient.ICamera? = null
    private var mCamera4: MultiCameraClient.ICamera? = null
    
    // TextureViews for each camera
    private var mTextureView1: AspectRatioTextureView? = null
    private var mTextureView2: AspectRatioTextureView? = null
    private var mTextureView3: AspectRatioTextureView? = null
    private var mTextureView4: AspectRatioTextureView? = null
    
    // Overlay views for each camera
    private lateinit var overlayView1: OverlayView
    private lateinit var overlayView2: OverlayView
    private lateinit var overlayView3: OverlayView
    private lateinit var overlayView4: OverlayView
    
    // Object detection helpers for each camera
    private lateinit var objectDetector1: ObjectDetectorHelper
    private lateinit var objectDetector2: ObjectDetectorHelper
    private lateinit var objectDetector3: ObjectDetectorHelper
    private lateinit var objectDetector4: ObjectDetectorHelper
    
    private lateinit var ttsHelper: TTSHelper
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    // Connected cameras list
    private val connectedCameras = mutableListOf<MultiCameraClient.ICamera>()

    override fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val deviceId = device.deviceId
        val serialNumber = device.serialNumber ?: "unknown"
        val vendorId = device.vendorId
        val productId = device.productId
        
        ToastUtils.show("CONNECTED: Camera $deviceName ready")
        ToastUtils.show("Unique ID: $deviceId Serial: $serialNumber VID:$vendorId PID:$productId")
        
        // Check for duplicate cameras (same device connected twice)
        val isDuplicate = connectedCameras.any { existingCamera ->
            val existingDevice = existingCamera.getUsbDevice()
            existingDevice.deviceId == deviceId && 
            existingDevice.serialNumber == serialNumber &&
            existingDevice.vendorId == vendorId &&
            existingDevice.productId == productId
        }
        
        if (isDuplicate) {
            ToastUtils.show("DUPLICATE: This camera is already connected - ignoring")
            return
        }
        
        // Add to connected cameras list
        connectedCameras.add(camera)
        val cameraSlot = connectedCameras.size
        ToastUtils.show("ADDED: Camera $cameraSlot of 4 - Total connected: ${connectedCameras.size}")
        
        when (cameraSlot) {
            1 -> {
                ToastUtils.show("ASSIGNING: $deviceName -> Camera View 1 (TOP-LEFT)")
                setupCamera(camera, mTextureView1, 1)
            }
            2 -> {
                ToastUtils.show("ASSIGNING: $deviceName -> Camera View 2 (TOP-RIGHT)")
                setupCamera(camera, mTextureView2, 2)
            }
            3 -> {
                ToastUtils.show("ASSIGNING: $deviceName -> Camera View 3 (BOTTOM-LEFT)")
                setupCamera(camera, mTextureView3, 3)
            }
            4 -> {
                ToastUtils.show("ASSIGNING: $deviceName -> Camera View 4 (BOTTOM-RIGHT)")
                setupCamera(camera, mTextureView4, 4)
            }
            else -> {
                ToastUtils.show("LIMIT: Maximum 4 cameras supported. Camera $deviceName ignored.")
                connectedCameras.remove(camera) // Remove from list since we can't use it
                return
            }
        }
    }
    
    private fun setupCamera(camera: MultiCameraClient.ICamera, textureView: AspectRatioTextureView?, cameraIndex: Int) {
        ToastUtils.show("SETUP: Starting setup for Camera $cameraIndex")
        
        // Get the correct texture view reference from the binding
        val correctTextureView = when (cameraIndex) {
            1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
            2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
            3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
            4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
            else -> null
        }
        
        if (correctTextureView == null) {
            ToastUtils.show("ERROR: TextureView $cameraIndex not found! Retrying...")
            Handler(Looper.getMainLooper()).postDelayed({
                setupCamera(camera, null, cameraIndex)
            }, 1000)
            return
        }
        
        ToastUtils.show("SUCCESS: TextureView $cameraIndex found, proceeding...")
        
        // Set camera reference
        when (cameraIndex) {
            1 -> mCamera1 = camera
            2 -> mCamera2 = camera
            3 -> mCamera3 = camera
            4 -> mCamera4 = camera
        }
        
        ToastUtils.show("OPENING: Camera $cameraIndex - attempting to open")
        camera.setCameraStateCallBack(this)
        
        try {
            val request = getCameraRequest()
            ToastUtils.show("REQUEST: Camera config created - opening camera $cameraIndex")
            
            // Ensure texture view is ready
            correctTextureView.post {
                try {
                    camera.openCamera(correctTextureView, request)
                    ToastUtils.show("OPEN SUCCESS: Camera $cameraIndex opened - waiting for preview")
                } catch (e: Exception) {
                    ToastUtils.show("OPEN FAILED: Camera $cameraIndex - ${e.message}")
                    e.printStackTrace()
                }
            }
            
            // Add preview data callback AFTER camera is opened
            Handler(Looper.getMainLooper()).postDelayed({
                if (camera is CameraUVC) {
                    camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                        override fun onPreviewData(
                            data: ByteArray?,
                            width: Int,
                            height: Int,
                            format: IPreviewDataCallBack.DataFormat
                        ) {
                            if (data != null) {
                                when (format) {
                                    IPreviewDataCallBack.DataFormat.NV21 -> {
                                        processFrame(data, width, height, cameraIndex)
                                    }
                                    IPreviewDataCallBack.DataFormat.RGBA -> {
                                        processFrame(data, width, height, cameraIndex)
                                    }
                                    else -> {
                                        // Try to process anyway for unknown formats
                                        processFrame(data, width, height, cameraIndex)
                                    }
                                }
                            }
                        }
                    })
                    ToastUtils.show("CALLBACK: Preview callback added for Camera $cameraIndex")
                }
            }, 2000) // Wait 2 seconds after camera opens before adding callback
            
        } catch (e: Exception) {
            ToastUtils.show("SETUP FAILED: Camera $cameraIndex - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun processFrame(data: ByteArray, width: Int, height: Int, cameraIndex: Int) {
        // Skip frame processing if executor is busy to prevent blocking
        if (executor.isShutdown || executor.isTerminated) return
        
        executor.execute {
            try {
                val bitmap = yuvToBitmap(data, width, height)
                
                // Get appropriate detector and overlay for this camera
                val (detector, overlayView) = when (cameraIndex) {
                    1 -> objectDetector1 to overlayView1
                    2 -> objectDetector2 to overlayView2
                    3 -> objectDetector3 to overlayView3
                    4 -> objectDetector4 to overlayView4
                    else -> return@execute
                }
                
                val results = detector.detect(bitmap)
                
                // Update UI on main thread
                mainHandler.post {
                    try {
                        overlayView.setResults(results)
                        
                        // Speak the first detected label with TTS (only for Camera 1 to avoid audio conflicts)
                        if (cameraIndex == 1 && results.isNotEmpty()) {
                            val label = results[0].label
                            val confidence = results[0].confidence
                            ttsHelper.speak(label, confidence)
                        }
                    } catch (e: Exception) {
                        // Silently continue if UI update fails
                    }
                }
            } catch (e: Exception) {
                // Log error but don't crash - continue processing frames
                mainHandler.post {
                    ToastUtils.show("Frame processing error for Camera $cameraIndex: ${e.message}")
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
        val deviceName = self.getUsbDevice().deviceName
        ToastUtils.show("STATE CHANGE: Camera $deviceName -> $code")
        when (code) {
            ICameraStateCallBack.State.ERROR -> {
                ToastUtils.show("CAMERA ERROR: ($deviceName) ${msg ?: "unknown error"}")
                // Try to restart camera after error
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        ToastUtils.show("RESTART: Attempting to restart camera $deviceName")
                        restartCamera(self)
                    } catch (e: Exception) {
                        ToastUtils.show("RESTART FAILED: ${e.message}")
                    }
                }, 2000)
            }
            ICameraStateCallBack.State.OPENED -> {
                ToastUtils.show("CAMERA OPENED: $deviceName - Video should appear now!")
            }
            ICameraStateCallBack.State.CLOSED -> {
                ToastUtils.show("CAMERA CLOSED: $deviceName")
                // Remove from connected cameras
                connectedCameras.remove(self)
            }
        }
    }
    
    private fun restartCamera(camera: MultiCameraClient.ICamera) {
        try {
            // Find which camera slot this is
            val cameraIndex = when (camera) {
                mCamera1 -> 1
                mCamera2 -> 2
                mCamera3 -> 3
                mCamera4 -> 4
                else -> return
            }
            
            // Close and reopen
            camera.closeCamera()
            Handler(Looper.getMainLooper()).postDelayed({
                setupCamera(camera, null, cameraIndex)
            }, 1000)
        } catch (e: Exception) {
            ToastUtils.show("Restart failed: ${e.message}")
        }
    }

    override fun initView() {
        super.initView()
        
        try {
            // Initialize TextureViews for 4 cameras
            mTextureView1 = mViewBinding.root.findViewById(R.id.texture_view_1)
            mTextureView2 = mViewBinding.root.findViewById(R.id.texture_view_2)
            mTextureView3 = mViewBinding.root.findViewById(R.id.texture_view_3)
            mTextureView4 = mViewBinding.root.findViewById(R.id.texture_view_4)
            
            // Initialize OverlayViews for 4 cameras
            overlayView1 = mViewBinding.root.findViewById(R.id.overlay_view_1)
            overlayView2 = mViewBinding.root.findViewById(R.id.overlay_view_2)
            overlayView3 = mViewBinding.root.findViewById(R.id.overlay_view_3)
            overlayView4 = mViewBinding.root.findViewById(R.id.overlay_view_4)
            
            // Initialize ObjectDetectors for 4 cameras
            objectDetector1 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector2 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector3 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector4 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            
            // Initialize TTS helper
            ttsHelper = TTSHelper(requireContext())
            
            ToastUtils.show("All camera views initialized successfully")
        } catch (e: Exception) {
            ToastUtils.show("Error initializing views: ${e.message}")
        }
    }
    
    override fun onDestroyView() {
        ttsHelper.shutdown()
        super.onDestroyView()
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentMultiCameraBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    private fun getCameraRequest(): CameraRequest {
        return try {
            val format = CameraRequest.PreviewFormat.FORMAT_MJPEG
            ToastUtils.show("Configuring MJPEG 640x480 for stable preview")
            CameraRequest.Builder()
                .setPreviewWidth(640)
                .setPreviewHeight(480)
                .setPreviewFormat(format)
                .setRenderMode(CameraRequest.RenderMode.NORMAL) // Use NORMAL instead of OPENGL
                .setAspectRatioShow(false) // Don't enforce aspect ratio
                .setRawPreviewData(true) // Enable raw data for object detection
                .create()
        } catch (e: Exception) {
            ToastUtils.show("MJPEG failed, trying YUYV 640x480")
            try {
                CameraRequest.Builder()
                    .setPreviewWidth(640)
                    .setPreviewHeight(480)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(true)
                    .create()
            } catch (e2: Exception) {
                ToastUtils.show("YUYV failed, trying basic NV21 320x240")
                try {
                    CameraRequest.Builder()
                        .setPreviewWidth(320)
                        .setPreviewHeight(240)
                        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                        .setRenderMode(CameraRequest.RenderMode.NORMAL)
                        .setAspectRatioShow(false)
                        .setRawPreviewData(true)
                        .create()
                } catch (e3: Exception) {
                    ToastUtils.show("All camera configs failed: ${e3.message}")
                    throw e3
                }
            }
        }
    }

    override fun onCameraAttached(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val vendorId = device.vendorId
        val productId = device.productId
        val deviceId = device.deviceId
        val serialNumber = device.serialNumber ?: "unknown"
        
        ToastUtils.show("USB Camera attached: $deviceName")
        ToastUtils.show("Details: VID:$vendorId PID:$productId ID:$deviceId Serial:$serialNumber")
        
        // Check if this might be through a USB hub
        val hubInfo = if (deviceName.contains("hub", ignoreCase = true) || 
                         serialNumber.contains("hub", ignoreCase = true)) {
            "via USB HUB"
        } else {
            "direct connection"
        }
        ToastUtils.show("Connection type: $hubInfo")
        
        // Don't auto-connect here - let the framework handle it
        // The onCameraConnected method will be called when ready
    }

    protected override fun onCameraDisConnected(camera: MultiCameraClient.ICamera) {
        connectedCameras.remove(camera)
        camera.closeCamera()
        
        // Clear camera reference
        when (camera) {
            mCamera1 -> mCamera1 = null
            mCamera2 -> mCamera2 = null
            mCamera3 -> mCamera3 = null
            mCamera4 -> mCamera4 = null
        }
        
        ToastUtils.show("Camera disconnected: ${camera.getUsbDevice().deviceName}")
    }

    protected override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        ToastUtils.show("Camera detached: ${camera.getUsbDevice().deviceName}")
    }
}
