package com.jiangdg.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
    
    // Status display views
    private lateinit var cameraCountText: TextView
    private lateinit var permissionStatusText: TextView
    
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
    
    // USB permission handling
    private val pendingCameras = mutableListOf<MultiCameraClient.ICamera>()
    private var permissionRequestInProgress = false
    private var pendingPermissionCount = 0
    
    // USB permission broadcast receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action?.startsWith("com.jiangdg.ausbc.USB_PERMISSION_") == true) {
                synchronized(this@DemoMultiCameraFragment) {
                    val deviceId = action.substringAfter("USB_PERMISSION_").toIntOrNull()
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                    
                    if (device != null) {
                        Log.i("DemoMultiCameraFragment", "📋 USB PERMISSION RESPONSE RECEIVED")
                        Log.i("DemoMultiCameraFragment", "Device: ${device.deviceName}")
                        Log.i("DemoMultiCameraFragment", "Device ID: ${device.deviceId}")
                        Log.i("DemoMultiCameraFragment", "Device Path: ${device.deviceName}")
                        Log.i("DemoMultiCameraFragment", "Permission Result: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
                        Log.i("DemoMultiCameraFragment", "Intent Action: $action")
                        
                        ToastUtils.show("USB Permission ${if (granted) "GRANTED" else "DENIED"} for ${device.deviceName}")
                        
                        pendingPermissionCount--
                        Log.i("DemoMultiCameraFragment", "Remaining permission requests: $pendingPermissionCount")
                        
                        if (pendingPermissionCount <= 0) {
                            Log.i("DemoMultiCameraFragment", "🎉 ALL USB PERMISSIONS PROCESSED")
                            Log.i("DemoMultiCameraFragment", "Pending cameras to process: ${pendingCameras.size}")
                            
                            permissionRequestInProgress = false
                            ToastUtils.show("All USB permission requests completed")
                            
                            // Update display to show completion
                            updateCameraCountDisplay()
                            
                            // Process any pending cameras after all permissions are handled
                            processPendingCameras()
                        } else {
                            Log.i("DemoMultiCameraFragment", "⏳ Waiting for more permission responses...")
                            // Update display to show pending count changed
                            updateCameraCountDisplay()
                        }
                    }
                }
            }
        }
    }

    override fun generateCamera(ctx: Context, device: UsbDevice): MultiCameraClient.ICamera {
        return CameraUVC(ctx, device)
    }
    
    private fun requestUsbPermissionForAllCameras() {
        if (permissionRequestInProgress) return
        
        permissionRequestInProgress = true
        ToastUtils.show("Requesting USB permissions for all cameras...")
        
        // Update display to show permission request in progress
        updateCameraCountDisplay()
        
        // Get USB manager to request permissions
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as android.hardware.usb.UsbManager
        val deviceList = usbManager.deviceList
        
        Log.i("DemoMultiCameraFragment", "🔐 USB PERMISSION REQUEST PROCESS")
        Log.i("DemoMultiCameraFragment", "Total USB devices found: ${deviceList.size}")
        
        var permissionCount = 0
        for ((_, device) in deviceList) {
            val hasPermission = usbManager.hasPermission(device)
            Log.i("DemoMultiCameraFragment", "Device: ${device.deviceName} (ID:${device.deviceId}) - Permission: ${if (hasPermission) "GRANTED" else "NEEDED"}")
            
            if (!hasPermission) {
                // Create permission intent for each device
                val permissionIntent = android.app.PendingIntent.getBroadcast(
                    requireContext(),
                    device.deviceId, // Use device ID as request code
                    android.content.Intent("com.jiangdg.ausbc.USB_PERMISSION_${device.deviceId}"),
                    android.app.PendingIntent.FLAG_IMMUTABLE
                )
                
                usbManager.requestPermission(device, permissionIntent)
                permissionCount++
                
                Log.w("DemoMultiCameraFragment", "🔒 REQUESTING USB PERMISSION")
                Log.w("DemoMultiCameraFragment", "Device Name: ${device.deviceName}")
                Log.w("DemoMultiCameraFragment", "Device ID: ${device.deviceId}")
                Log.w("DemoMultiCameraFragment", "Device Path: ${device.deviceName}")
                Log.w("DemoMultiCameraFragment", "Intent Action: com.jiangdg.ausbc.USB_PERMISSION_${device.deviceId}")
            }
        }
        
        if (permissionCount == 0) {
            ToastUtils.show("All USB cameras already have permission")
            permissionRequestInProgress = false
            processPendingCameras()
            updateCameraCountDisplay()
        } else {
            pendingPermissionCount = permissionCount
            ToastUtils.show("Requesting permission for $permissionCount USB devices")
            // The permission receiver will handle processing pending cameras when all permissions are complete
        }
    }
    
    private fun processPendingCameras() {
        if (pendingCameras.isEmpty()) return
        
        ToastUtils.show("Processing ${pendingCameras.size} pending cameras...")
        
        // Process all pending cameras
        val camerasToProcess = pendingCameras.toList()
        pendingCameras.clear()
        
        // Update display to show no more pending cameras
        updateCameraCountDisplay()
        
        for (camera in camerasToProcess) {
            // Re-trigger connection process for each camera with fragment lifecycle check
            mainHandler.postDelayed({
                // Check if fragment is still alive before processing camera
                if (isAdded && view != null) {
                    onCameraConnected(camera)
                }
            }, 500)
        }
    }
    
    private fun getCameraSpecificRequest(device: UsbDevice): CameraRequest {
        val deviceName = device.deviceName.lowercase()
        val vendorId = device.vendorId
        val productId = device.productId
        
        Log.d("CameraConfig", "Configuring camera: $deviceName VID:$vendorId PID:$productId")
        
        // Common 480p camera configurations
        val common480pConfigs = listOf(
            Triple(720, 480, "720x480p"),
            Triple(854, 480, "854x480p"),
            Triple(800, 480, "800x480p"),
            Triple(640, 480, "640x480p"),
            Triple(480, 480, "480x480p")
        )
        
        // Standard VGA configurations  
        val standardConfigs = listOf(
            Triple(640, 480, "640x480 VGA"),
            Triple(320, 240, "320x240 QVGA"),
            Triple(176, 144, "176x144 QCIF")
        )
        
        // Choose configuration based on device characteristics
        val configsToTry = when {
            // If device name suggests it's a 480p camera
            deviceName.contains("480") || deviceName.contains("hd") -> {
                common480pConfigs + standardConfigs
            }
            // For webcams and generic USB cameras
            deviceName.contains("webcam") || deviceName.contains("usb") -> {
                standardConfigs + common480pConfigs
            }
            // Default: try 480p configs first, then standard
            else -> common480pConfigs + standardConfigs
        }
        
        // Try MJPEG format first
        for ((width, height, description) in configsToTry) {
            try {
                Log.d("CameraConfig", "Trying MJPEG $description for device $deviceName")
                return CameraRequest.Builder()
                    .setPreviewWidth(width)
                    .setPreviewHeight(height)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(true)
                    .create()
            } catch (e: Exception) {
                Log.d("CameraConfig", "MJPEG $description failed for $deviceName: ${e.message}")
                continue
            }
        }
        
        // Try YUYV format if MJPEG fails
        for ((width, height, description) in configsToTry) {
            try {
                Log.d("CameraConfig", "Trying YUYV $description for device $deviceName")
                return CameraRequest.Builder()
                    .setPreviewWidth(width)
                    .setPreviewHeight(height)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(true)
                    .create()
            } catch (e: Exception) {
                Log.d("CameraConfig", "YUYV $description failed for $deviceName: ${e.message}")
                continue
            }
        }
        
        // Last resort: let camera choose its own resolution
        return try {
            Log.d("CameraConfig", "Using default resolution for $deviceName")
            CameraRequest.Builder()
                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                .setAspectRatioShow(false)
                .setRawPreviewData(true)
                .create()
        } catch (e: Exception) {
            Log.e("CameraConfig", "All configurations failed for $deviceName: ${e.message}")
            throw e
        }
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val deviceId = device.deviceId
        
        Log.i("DemoMultiCameraFragment", "═══════════════════════════════════════")
        Log.i("DemoMultiCameraFragment", "USB CAMERA CONNECTION ATTEMPT")
        Log.i("DemoMultiCameraFragment", "Device Name: $deviceName")
        Log.i("DemoMultiCameraFragment", "Device ID: $deviceId")
        Log.i("DemoMultiCameraFragment", "Vendor ID: ${device.vendorId}")
        Log.i("DemoMultiCameraFragment", "Product ID: ${device.productId}")
        Log.i("DemoMultiCameraFragment", "Device Path: ${device.deviceName}")
        
        // Check if we have USB permission for this device
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val hasPermission = usbManager.hasPermission(device)
        
        Log.i("DemoMultiCameraFragment", "USB Permission Status: ${if (hasPermission) "GRANTED" else "DENIED"}")
        
        if (!hasPermission) {
            Log.w("DemoMultiCameraFragment", "❌ USB PERMISSION REQUIRED")
            Log.w("DemoMultiCameraFragment", "User has not given permission to access device $deviceName")
            Log.w("DemoMultiCameraFragment", "Device path: ${device.deviceName}")
            Log.w("DemoMultiCameraFragment", "Adding camera to pending queue for permission request")
            
            ToastUtils.show("PENDING: Camera $deviceName needs USB permission")
            // Add to pending cameras and request permissions for all devices
            if (!pendingCameras.contains(camera)) {
                pendingCameras.add(camera)
                Log.i("DemoMultiCameraFragment", "Camera added to pending queue. Total pending: ${pendingCameras.size}")
                // Update display to show pending camera added
                updateCameraCountDisplay()
            }
            requestUsbPermissionForAllCameras()
            return // Don't proceed until permission is granted
        }
        
        Log.i("DemoMultiCameraFragment", "✅ USB PERMISSION GRANTED - Proceeding with connection")
        
        // Safely get serial number - handle permission issues
        val serialNumber = try {
            val serial = device.serialNumber ?: "unknown"
            Log.i("DemoMultiCameraFragment", "✅ Serial Number Access: SUCCESS - Serial: $serial")
            serial
        } catch (e: SecurityException) {
            Log.w("DemoMultiCameraFragment", "❌ SERIAL NUMBER ACCESS DENIED")
            Log.w("DemoMultiCameraFragment", "SecurityException: ${e.message}")
            Log.w("DemoMultiCameraFragment", "Device: $deviceName (${device.deviceName})")
            Log.w("DemoMultiCameraFragment", "This indicates USB permission issue for serial number access")
            "permission_required"
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ SERIAL NUMBER ACCESS ERROR")
            Log.e("DemoMultiCameraFragment", "Exception: ${e.message}")
            Log.e("DemoMultiCameraFragment", "Device: $deviceName")
            Log.e("DemoMultiCameraFragment", "Stack trace:", e)
            "error"
        }
        
        val vendorId = device.vendorId
        val productId = device.productId
        
        Log.i("DemoMultiCameraFragment", "📹 CAMERA CONNECTION DETAILS:")  
        Log.i("DemoMultiCameraFragment", "Device ID: $deviceId")
        Log.i("DemoMultiCameraFragment", "Serial Number: $serialNumber")
        Log.i("DemoMultiCameraFragment", "Vendor ID: $vendorId")
        Log.i("DemoMultiCameraFragment", "Product ID: $productId")
        
        ToastUtils.show("CONNECTED: Camera $deviceName ready")
        ToastUtils.show("Unique ID: $deviceId Serial: $serialNumber VID:$vendorId PID:$productId")
        
        // Check for duplicate cameras (same device connected twice)
        val isDuplicate = connectedCameras.any { existingCamera ->
            val existingDevice = existingCamera.getUsbDevice()
            val existingSerialNumber = try {
                existingDevice.serialNumber ?: "unknown"
            } catch (e: Exception) {
                "error"
            }
            existingDevice.deviceId == deviceId && 
            existingSerialNumber == serialNumber &&
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
        
        // Update camera count display
        updateCameraCountDisplay()
        
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
                updateCameraCountDisplay() // Update display after removing camera
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
            // Use device-specific configuration instead of generic one
            val device = camera.getUsbDevice()
            val request = getCameraSpecificRequest(device)
            ToastUtils.show("REQUEST: Camera-specific config created for camera $cameraIndex")
            
            // Ensure texture view is ready and check if fragment is still attached
            if (isAdded && view != null) {
                correctTextureView.post {
                    try {
                        // Double check fragment is still alive before opening camera
                        if (isAdded && view != null) {
                            camera.openCamera(correctTextureView, request)
                            ToastUtils.show("OPEN SUCCESS: Camera $cameraIndex opened - waiting for preview")
                        }
                    } catch (e: Exception) {
                        ToastUtils.show("OPEN FAILED: Camera $cameraIndex - ${e.message}")
                        e.printStackTrace()
                    }
                }
                
                // Add preview data callback AFTER camera is opened - use main handler safely
                mainHandler.postDelayed({
                    // Check if fragment is still alive before adding callback
                    if (isAdded && view != null && camera is CameraUVC) {
                        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                            override fun onPreviewData(
                                data: ByteArray?,
                                width: Int,
                                height: Int,
                                format: IPreviewDataCallBack.DataFormat
                            ) {
                                // Check if fragment is still alive before processing
                                if (isAdded && view != null && data != null) {
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
            }
            
        } catch (e: Exception) {
            ToastUtils.show("SETUP FAILED: Camera $cameraIndex - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun updateCameraCountDisplay() {
        if (!::cameraCountText.isInitialized || !::permissionStatusText.isInitialized) return
        
        mainHandler.post {
            // Check if fragment is still alive before updating UI
            if (isAdded && view != null) {
                val connectedCount = connectedCameras.size
                val pendingCount = pendingCameras.size
                
                cameraCountText.text = "Cameras Connected: $connectedCount/4"
                
                when {
                    permissionRequestInProgress -> {
                        permissionStatusText.text = "Requesting Permissions..."
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                    }
                    pendingCount > 0 -> {
                        permissionStatusText.text = "Pending: $pendingCount"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                    }
                    connectedCount == 0 -> {
                        permissionStatusText.text = "No Cameras"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    }
                    else -> {
                        permissionStatusText.text = "Ready"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    }
                }
            }
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
            
            // Initialize status display views
            cameraCountText = mViewBinding.root.findViewById(R.id.camera_count_text)
            permissionStatusText = mViewBinding.root.findViewById(R.id.permission_status_text)
            
            // Initialize camera count display
            updateCameraCountDisplay()
            
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
    
    override fun onResume() {
        super.onResume()
        // Register USB permission receiver
        val intentFilter = IntentFilter()
        // Register for all possible USB permission actions with device IDs
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = usbManager.deviceList
        for (device in deviceList.values) {
            intentFilter.addAction("com.jiangdg.ausbc.USB_PERMISSION_${device.deviceId}")
        }
        
        try {
            requireContext().registerReceiver(usbPermissionReceiver, intentFilter)
            Log.d("USBPermission", "USB permission receiver registered")
        } catch (e: Exception) {
            Log.e("USBPermission", "Failed to register USB permission receiver: ${e.message}")
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister USB permission receiver
        try {
            requireContext().unregisterReceiver(usbPermissionReceiver)
            Log.d("USBPermission", "USB permission receiver unregistered")
        } catch (e: Exception) {
            Log.e("USBPermission", "Failed to unregister USB permission receiver: ${e.message}")
        }
    }
    
    override fun onDestroyView() {
        // Clear any pending handlers to prevent dead thread errors
        mainHandler.removeCallbacksAndMessages(null)
        
        // Clear pending cameras
        pendingCameras.clear()
        permissionRequestInProgress = false
        pendingPermissionCount = 0
        
        // Cleanup cameras
        connectedCameras.clear()
        mCamera1 = null
        mCamera2 = null
        mCamera3 = null
        mCamera4 = null
        
        ttsHelper.shutdown()
        super.onDestroyView()
    }

    override fun getRootView(inflater: LayoutInflater, container: ViewGroup?): View {
        mViewBinding = FragmentMultiCameraBinding.inflate(inflater, container, false)
        return mViewBinding.root
    }

    private fun getCameraRequest(): CameraRequest {
        // Generic fallback method - use the camera-specific method instead
        Log.w("CameraConfig", "Using generic camera request - consider using getCameraSpecificRequest instead")
        
        // Try common resolutions that work with most cameras including 480p
        val resolutionConfigs = listOf(
            // 480p variations (most likely to work with 480p cameras)
            Triple(720, 480, "480p 720x480"),
            Triple(854, 480, "WVGA 854x480"), 
            Triple(800, 480, "WVGA 800x480"),
            Triple(640, 480, "VGA 640x480"),
            Triple(480, 480, "Square 480x480"),
            // Standard lower resolutions
            Triple(352, 288, "CIF 352x288"),
            Triple(320, 240, "QVGA 320x240"),
            Triple(176, 144, "QCIF 176x144")
        )
        
        // Try MJPEG format first (better compression, higher frame rates)
        for ((width, height, description) in resolutionConfigs) {
            try {
                Log.d("CameraConfig", "Trying generic MJPEG $description")
                return CameraRequest.Builder()
                    .setPreviewWidth(width)
                    .setPreviewHeight(height)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(true)
                    .create()
            } catch (e: Exception) {
                Log.d("CameraConfig", "Generic MJPEG $description failed: ${e.message}")
                continue
            }
        }
        
        // Try YUYV format if MJPEG fails
        for ((width, height, description) in resolutionConfigs) {
            try {
                Log.d("CameraConfig", "Trying generic YUYV $description")
                return CameraRequest.Builder()
                    .setPreviewWidth(width)
                    .setPreviewHeight(height)
                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                    .setAspectRatioShow(false)
                    .setRawPreviewData(true)
                    .create()
            } catch (e: Exception) {
                Log.d("CameraConfig", "Generic YUYV $description failed: ${e.message}")
                continue
            }
        }
        
        // Last resort: let camera choose its own resolution
        return try {
            Log.d("CameraConfig", "Using camera's default resolution")
            CameraRequest.Builder()
                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                .setAspectRatioShow(false)
                .setRawPreviewData(true)
                .create()
        } catch (e: Exception) {
            Log.e("CameraConfig", "All generic camera configurations failed: ${e.message}")
            throw e
        }
    }

    override fun onCameraAttached(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val vendorId = device.vendorId
        val productId = device.productId
        val deviceId = device.deviceId
        
        // Safely get serial number - handle permission issues
        val serialNumber = try {
            device.serialNumber ?: "unknown"
        } catch (e: SecurityException) {
            Log.w("DemoMultiCameraFragment", "Cannot access USB device serial number: ${e.message}")
            "permission_required"
        } catch (e: Exception) {
            Log.w("DemoMultiCameraFragment", "Error getting serial number: ${e.message}")
            "error"
        }
        
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
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        
        Log.i("DemoMultiCameraFragment", "📤 CAMERA DISCONNECTION")
        Log.i("DemoMultiCameraFragment", "Device Name: $deviceName")
        Log.i("DemoMultiCameraFragment", "Device ID: ${device.deviceId}")
        Log.i("DemoMultiCameraFragment", "Connected cameras before removal: ${connectedCameras.size}")
        
        connectedCameras.remove(camera)
        camera.closeCamera()
        
        // Clear camera reference
        when (camera) {
            mCamera1 -> {
                mCamera1 = null
                Log.i("DemoMultiCameraFragment", "Camera 1 reference cleared")
            }
            mCamera2 -> {
                mCamera2 = null
                Log.i("DemoMultiCameraFragment", "Camera 2 reference cleared")
            }
            mCamera3 -> {
                mCamera3 = null
                Log.i("DemoMultiCameraFragment", "Camera 3 reference cleared")
            }
            mCamera4 -> {
                mCamera4 = null
                Log.i("DemoMultiCameraFragment", "Camera 4 reference cleared")
            }
        }
        
        Log.i("DemoMultiCameraFragment", "Connected cameras after removal: ${connectedCameras.size}")
        ToastUtils.show("Camera disconnected: $deviceName")
        
        // Update camera count display
        updateCameraCountDisplay()
    }

    protected override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        ToastUtils.show("Camera detached: ${camera.getUsbDevice().deviceName}")
    }
}
