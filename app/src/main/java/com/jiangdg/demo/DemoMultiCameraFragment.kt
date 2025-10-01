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
    private lateinit var activeCameraText: TextView
    private lateinit var noCameraIndicator: TextView
    
    // Camera container views for full screen display
    private lateinit var camera1Container: View
    private lateinit var camera2Container: View
    private lateinit var camera3Container: View
    private lateinit var camera4Container: View
    
    // Camera selection buttons
    private lateinit var camera1Button: android.widget.Button
    private lateinit var camera2Button: android.widget.Button
    private lateinit var camera3Button: android.widget.Button
    private lateinit var camera4Button: android.widget.Button
    
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
    
    // Connection state tracking to prevent duplicate logging
    private val connectingDevices = mutableSetOf<Int>() // Track device IDs currently being connected
    private val establishedDevices = mutableSetOf<Int>() // Track device IDs that are fully established
    
    // Frame tracking for preview status logging
    private val frameCounters = mutableMapOf<Int, Int>() // Track frame count per camera
    private val lastFrameTime = mutableMapOf<Int, Long>() // Track last frame time per camera
    
    // Bandwidth monitoring
    private val frameBytesReceived = mutableMapOf<Int, Long>() // Track total bytes per camera
    private val bandwidthStartTime = mutableMapOf<Int, Long>() // Track bandwidth measurement start time
    private val lastBandwidthReport = mutableMapOf<Int, Long>() // Track last bandwidth report time
    
    // Automatic camera cycling system - switches cameras every 5 seconds
    private var cameraCyclingEnabled = true // Enable automatic cycling every 5 seconds
    private var currentActiveCameraIndex = -1 // Index of currently active camera (-1 = none)
    private var cameraSwitchInterval = 5000L // Switch every 5 seconds
    private val activeCameras = mutableListOf<Int>() // List of available camera indices
    private var cameraCyclingRunnable: Runnable? = null
    
    // Sequential camera setup management
    private val workingCameras = mutableSetOf<Int>() // Track cameras that are receiving frames
    private var lastWorkingCameraTime = System.currentTimeMillis()
    
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
                        
                        // Log only: USB Permission ${if (granted) "GRANTED" else "DENIED"} for ${device.deviceName}
                        
                        pendingPermissionCount--
                        Log.i("DemoMultiCameraFragment", "Remaining permission requests: $pendingPermissionCount")
                        
                        if (pendingPermissionCount <= 0) {
                            Log.i("DemoMultiCameraFragment", "🎉 ALL USB PERMISSIONS PROCESSED")
                            Log.i("DemoMultiCameraFragment", "Pending cameras to process: ${pendingCameras.size}")
                            
                            permissionRequestInProgress = false
                            // ToastUtils.show("All USB permission requests completed") // Removed for clean UI
                            
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
        // ToastUtils.show("Requesting USB permissions for all cameras...") // Removed for clean UI
        
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
            // ToastUtils.show("All USB cameras already have permission") // Removed for clean UI
            permissionRequestInProgress = false
            processPendingCameras()
            updateCameraCountDisplay()
        } else {
            pendingPermissionCount = permissionCount
            // ToastUtils.show("Requesting permission for $permissionCount USB devices") // Removed for clean UI
            // The permission receiver will handle processing pending cameras when all permissions are complete
        }
    }
    
    private fun processPendingCameras() {
        if (pendingCameras.isEmpty()) return
        
        // ToastUtils.show("Processing ${pendingCameras.size} pending cameras...") // Removed for clean UI
        
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
    
    // Track failed devices to prevent infinite restart loops
    private val failedDevices = mutableSetOf<Int>()
    private val deviceRestartAttempts = mutableMapOf<Int, Int>()
    private val maxRestartAttempts = 3
    
    private fun getCameraSpecificRequest(device: UsbDevice): CameraRequest {
        val deviceName = device.deviceName.lowercase()
        val vendorId = device.vendorId
        val productId = device.productId
        val deviceId = device.deviceId
        
        Log.d("CameraConfig", "Configuring camera: $deviceName VID:$vendorId PID:$productId")
        
        // Check if this device has failed too many times
        val restartAttempts = deviceRestartAttempts.getOrDefault(deviceId, 0)
        if (restartAttempts >= maxRestartAttempts) {
            Log.w("DemoMultiCameraFragment", "⚠️ DEVICE $deviceId EXCEEDED MAX RESTART ATTEMPTS ($restartAttempts)")
            failedDevices.add(deviceId)
        }
        
        // Device 1004 specific configurations - this device needs very specific settings
        val device1004Configs = listOf(
            // Ultra-conservative resolutions for Device 1004 to force low bandwidth
            Triple(32, 24, "32x24 Micro-Nano"),   // Even smaller than nano - for extreme cases
            Triple(48, 36, "48x36 Ultra-Nano"),   // Slightly larger micro-nano
            Triple(64, 48, "64x48 Nano"),         // Nano resolution - extreme bandwidth conservation
            Triple(80, 60, "80x60 Tiny"),         // Very tiny resolution
            Triple(96, 72, "96x72 Ultra-Micro"), // Ultra minimal
            Triple(128, 96, "128x96 Micro"),      // Micro resolution
            Triple(160, 120, "160x120 QQVGA"),    // Very small
            Triple(176, 144, "176x144 QCIF"),     // Minimal resolution fallback
            Triple(240, 180, "240x180 Custom")    // Custom small size as last resort
        )
        
        // Device 1003 works well with standard configs
        val device1003Configs = listOf(
            Triple(320, 240, "320x240 QVGA"),     // Standard QVGA
            Triple(176, 144, "176x144 QCIF"),     // QCIF fallback
            Triple(352, 288, "352x288 CIF"),      // CIF
            Triple(640, 480, "640x480 VGA")       // VGA if bandwidth allows
        )
        
        // Ultra-low bandwidth configurations for multi-camera scenarios
        val ultraLowBandwidthConfigs = listOf(
            Triple(64, 48, "64x48 Nano"),          // Nano resolution for extreme bandwidth conservation
            Triple(96, 72, "96x72 Ultra-Micro"),   // Ultra minimal bandwidth
            Triple(128, 96, "128x96 Micro")        // Absolute minimum bandwidth fallback
        )
        
        // In camera cycling mode, treat each camera as single-camera mode since only one is active at a time
        // This gives each camera full bandwidth during its active period
        val isMultiCameraMode = if (cameraCyclingEnabled) {
            Log.i("DemoMultiCameraFragment", "🔄 CYCLING MODE: Treating as single-camera for full bandwidth")
            false // Each camera gets full bandwidth in cycling mode
        } else {
            connectedCameras.size > 1 || establishedDevices.size > 1
        }
        
        Log.i("DemoMultiCameraFragment", "🎛️ BANDWIDTH MANAGEMENT")
        Log.i("DemoMultiCameraFragment", "Connected cameras: ${connectedCameras.size}")
        Log.i("DemoMultiCameraFragment", "Established devices: ${establishedDevices.size}")
        Log.i("DemoMultiCameraFragment", "Camera cycling enabled: $cameraCyclingEnabled")
        Log.i("DemoMultiCameraFragment", "Effective multi-camera mode: $isMultiCameraMode")
        Log.i("DemoMultiCameraFragment", "Device restart attempts: $restartAttempts")
        // Cleaned up log message
        // ToastUtils.show("CYCLING: Device $deviceId gets full bandwidth in cycling mode") // Removed for clean UI
        
        // Choose configuration based on device characteristics and bandwidth requirements
        val configsToTry = when {
            // Device 1004 (Vendor: 13468, Product: 1041) - problematic device needs special handling
            deviceName.contains("001/004") || vendorId == 13468 -> {
                if (isMultiCameraMode) {
                    Log.i("DemoMultiCameraFragment", "🔧 Device 1004 MULTI-CAMERA: Ultra-conservative mode")
                    // In multi-camera mode, use ultra-low bandwidth for Device 1004 too
                    ultraLowBandwidthConfigs
                } else {
                    Log.i("DemoMultiCameraFragment", "🔧 Device 1004 SINGLE-CAMERA: Specialized configs")
                    // Single camera mode can use specialized configs
                    device1004Configs
                }
            }
            // Device 1003 (Vendor: 3141, Product: 25771) - working device
            deviceName.contains("001/003") || vendorId == 3141 -> {
                if (isMultiCameraMode) {
                    Log.i("DemoMultiCameraFragment", "🔧 Device 1003 MULTI-CAMERA: Ultra-conservative mode")
                    // Use only the smallest resolutions for multi-camera bandwidth sharing
                    ultraLowBandwidthConfigs
                } else {
                    Log.i("DemoMultiCameraFragment", "🔧 Device 1003 SINGLE-CAMERA: Standard mode")
                    device1003Configs
                }
            }
            // Default: Ultra-conservative for unknown devices in multi-camera mode
            else -> {
                if (isMultiCameraMode) {
                    Log.i("DemoMultiCameraFragment", "🔧 Generic MULTI-CAMERA: Ultra-conservative mode")
                    ultraLowBandwidthConfigs
                } else {
                    Log.i("DemoMultiCameraFragment", "🔧 Generic SINGLE-CAMERA: Conservative mode")
                    device1003Configs
                }
            }
        }
        
        Log.i("DemoMultiCameraFragment", "🎛️ DEVICE-SPECIFIC CONFIG for $deviceName")
        Log.i("DemoMultiCameraFragment", "Configurations to try: ${configsToTry.map { "${it.first}x${it.second}" }}")
        // Cleaned up log message
        // ToastUtils.show("CONFIG: Device $deviceName trying resolutions: ${configsToTry.map { "${it.first}x${it.second}" }.joinToString(", ") // Removed for clean UI}")
        
        // For Device 1004, try multiple format combinations as it's very picky
        val formatsToTry = if (vendorId == 13468 || deviceName.contains("001/004")) {
            Log.i("DemoMultiCameraFragment", "🔧 Device 1004: Trying multiple formats for compatibility")
            // Cleaned up log message
            // ToastUtils.show("FORMAT: Device 1004 trying all format combinations") // Removed for clean UI
            // Try both formats - Device 1004 is very particular about format+resolution combinations
            listOf(CameraRequest.PreviewFormat.FORMAT_YUYV, CameraRequest.PreviewFormat.FORMAT_MJPEG)
        } else {
            // For Device 1003 in multi-camera mode, prefer YUYV for lower bandwidth
            if (isMultiCameraMode) {
                Log.i("DemoMultiCameraFragment", "🔧 Device 1003 MULTI-CAMERA: Using YUYV for bandwidth")
                // Cleaned up log message
                // ToastUtils.show("FORMAT: Device 1003 using YUYV for bandwidth conservation") // Removed for clean UI
                listOf(CameraRequest.PreviewFormat.FORMAT_YUYV, CameraRequest.PreviewFormat.FORMAT_MJPEG)
            } else {
                // Cleaned up log message
                // ToastUtils.show("FORMAT: Device 1003 using MJPEG priority for single-camera") // Removed for clean UI
                listOf(CameraRequest.PreviewFormat.FORMAT_MJPEG, CameraRequest.PreviewFormat.FORMAT_YUYV)
            }
        }
        
        // Try each format with each configuration
        for (format in formatsToTry) {
            val formatName = if (format == CameraRequest.PreviewFormat.FORMAT_MJPEG) "MJPEG" else "YUYV"
            for ((width, height, description) in configsToTry) {
                try {
                    Log.i("DemoMultiCameraFragment", "🔧 Trying $formatName $description for device $deviceName")
                    
                    val request = CameraRequest.Builder()
                        .setPreviewWidth(width)
                        .setPreviewHeight(height)
                        .setPreviewFormat(format)
                        .setRenderMode(CameraRequest.RenderMode.NORMAL)
                        .setAspectRatioShow(false)
                        .setRawPreviewData(true)
                        .create()
                    
                    // Log bandwidth optimization strategy
                    if (isMultiCameraMode) {
                        Log.i("DemoMultiCameraFragment", "🔧 MULTI-CAMERA: Using micro resolution ${width}x${height} for bandwidth optimization")
                    } else {
                        Log.i("DemoMultiCameraFragment", "🔧 SINGLE-CAMERA: Standard resolution ${width}x${height}")
                    }
                    Log.i("DemoMultiCameraFragment", "✅ SUCCESS: $formatName $description selected for $deviceName")
                    // Cleaned up log message
                    // ToastUtils.show("SUCCESS: $deviceName configured for $formatName $description") // Removed for clean UI
                    return request
                } catch (e: Exception) {
                    Log.w("DemoMultiCameraFragment", "❌ $formatName $description failed for $deviceName: ${e.message}")
                    continue
                }
            }
        }
        
        // Last resort: try ultra-minimal configurations and auto-detect for Device 1004
        Log.w("DemoMultiCameraFragment", "⚠️ All specific configurations failed for $deviceName, trying ultra-minimal fallbacks")
        
        // For Device 1004, try some extreme fallback resolutions
        if (vendorId == 13468 || deviceName.contains("001/004")) {
            val emergencyConfigs = listOf(
                Triple(16, 12, "16x12 Emergency-Micro"),    // Extremely tiny
                Triple(24, 18, "24x18 Emergency-Tiny"),     // Emergency tiny
                Triple(40, 30, "40x30 Emergency-Small")     // Emergency small
            )
            
            Log.i("DemoMultiCameraFragment", "🚨 EMERGENCY CONFIGS for Device 1004")
            // Cleaned up log message
            // ToastUtils.show("EMERGENCY: Trying ultra-minimal resolutions for Device 1004") // Removed for clean UI
            
            for (format in listOf(CameraRequest.PreviewFormat.FORMAT_YUYV, CameraRequest.PreviewFormat.FORMAT_MJPEG)) {
                val formatName = if (format == CameraRequest.PreviewFormat.FORMAT_MJPEG) "MJPEG" else "YUYV"
                for ((width, height, description) in emergencyConfigs) {
                    try {
                        Log.i("DemoMultiCameraFragment", "🚨 EMERGENCY: Trying $formatName $description")
                        
                        val request = CameraRequest.Builder()
                            .setPreviewWidth(width)
                            .setPreviewHeight(height)
                            .setPreviewFormat(format)
                            .setRenderMode(CameraRequest.RenderMode.NORMAL)
                            .setAspectRatioShow(false)
                            .setRawPreviewData(true)
                            .create()
                        
                        Log.i("DemoMultiCameraFragment", "✅ EMERGENCY SUCCESS: $formatName $description for Device 1004")
                        // Cleaned up log message
                        // ToastUtils.show("EMERGENCY SUCCESS: Device 1004 $formatName $description") // Removed for clean UI
                        return request
                    } catch (e: Exception) {
                        Log.w("DemoMultiCameraFragment", "❌ EMERGENCY FAILED: $formatName $description - ${e.message}")
                        continue
                    }
                }
            }
        }
        
        // Final fallback: let camera choose its own resolution
        val defaultFormat = if (vendorId == 13468) {
            Log.i("DemoMultiCameraFragment", "🔧 Device 1004: Using YUYV auto-detect")
            CameraRequest.PreviewFormat.FORMAT_YUYV
        } else {
            CameraRequest.PreviewFormat.FORMAT_MJPEG
        }
        
        return try {
            Log.i("DemoMultiCameraFragment", "🔧 AUTO-DETECT: Letting camera choose resolution")
            // Cleaned up log message
            // ToastUtils.show("AUTO-DETECT: Camera choosing own resolution") // Removed for clean UI
            
            val request = CameraRequest.Builder()
                .setPreviewFormat(defaultFormat)
                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                .setAspectRatioShow(false)
                .setRawPreviewData(true)
                .create()
            Log.i("DemoMultiCameraFragment", "✅ SUCCESS: Auto-detect ${if (defaultFormat == CameraRequest.PreviewFormat.FORMAT_YUYV) "YUYV" else "MJPEG"} configuration for $deviceName")
            request
        } catch (e: Exception) {
            Log.e("CameraConfig", "❌ COMPLETE FAILURE: All configurations failed for $deviceName: ${e.message}")
            // Cleaned up log message
            // ToastUtils.show("COMPLETE FAILURE: Device $deviceName not compatible") // Removed for clean UI
            failedDevices.add(deviceId)
            throw e
        }
    }

    override fun onCameraConnected(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val deviceId = device.deviceId
        
        // Check if this device has failed too many times - reject it immediately
        if (failedDevices.contains(deviceId)) {
            Log.w("DemoMultiCameraFragment", "❌ DEVICE BLACKLISTED - Device $deviceId has failed too many times, ignoring connection")
            // ToastUtils.show("BLACKLISTED: Device $deviceId failed previously - ignoring") // Removed for clean UI
            return
        }
        
        // Check if this device is already established - if so, skip duplicate processing
        if (establishedDevices.contains(deviceId)) {
            Log.d("DemoMultiCameraFragment", "🔄 DUPLICATE CONNECTION ATTEMPT - Device $deviceId already established, ignoring")
            return
        }
        
        // Check if this device is currently being connected - reduce logging for repeated attempts
        val isFirstAttempt = !connectingDevices.contains(deviceId)
        if (isFirstAttempt) {
            connectingDevices.add(deviceId)
            Log.i("DemoMultiCameraFragment", "═══════════════════════════════════════")
            Log.i("DemoMultiCameraFragment", "🔌 NEW USB CAMERA CONNECTION ATTEMPT")
            Log.i("DemoMultiCameraFragment", "Device Name: $deviceName")
            Log.i("DemoMultiCameraFragment", "Device ID: $deviceId")
            Log.i("DemoMultiCameraFragment", "Vendor ID: ${device.vendorId}")
            Log.i("DemoMultiCameraFragment", "Product ID: ${device.productId}")
            Log.i("DemoMultiCameraFragment", "Device Path: ${device.deviceName}")
        } else {
            Log.d("DemoMultiCameraFragment", "🔄 RETRY CONNECTION - Device $deviceId (${device.deviceName})")
        }
        
        // Check if we have USB permission for this device
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val hasPermission = usbManager.hasPermission(device)
        
        if (isFirstAttempt) {
            Log.i("DemoMultiCameraFragment", "USB Permission Status: ${if (hasPermission) "GRANTED" else "DENIED"}")
        }
        
        if (!hasPermission) {
            if (isFirstAttempt) {
                Log.w("DemoMultiCameraFragment", "❌ USB PERMISSION REQUIRED")
                Log.w("DemoMultiCameraFragment", "User has not given permission to access device $deviceName")
                Log.w("DemoMultiCameraFragment", "Device path: ${device.deviceName}")
                Log.w("DemoMultiCameraFragment", "Adding camera to pending queue for permission request")
                
                // ToastUtils.show("PENDING: Camera $deviceName needs USB permission") // Removed for clean UI
                // Add to pending cameras and request permissions for all devices
                if (!pendingCameras.contains(camera)) {
                    pendingCameras.add(camera)
                    Log.i("DemoMultiCameraFragment", "Camera added to pending queue. Total pending: ${pendingCameras.size}")
                    // Update display to show pending camera added
                    updateCameraCountDisplay()
                }
                requestUsbPermissionForAllCameras()
            }
            return // Don't proceed until permission is granted
        }
        
        if (isFirstAttempt) {
            Log.i("DemoMultiCameraFragment", "✅ USB PERMISSION GRANTED - Proceeding with connection")
        }
        
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
        
        // Cleaned up log message
        // Cleaned up log message
        // ToastUtils.show("CONNECTED: Camera $deviceName ready") // Removed for clean UI
        // ToastUtils.show("Unique ID: $deviceId Serial: $serialNumber VID:$vendorId PID:$productId") // Removed for clean UI
        
        // Check if this device is already established - this is the primary duplicate check
        if (establishedDevices.contains(deviceId)) {
            Log.d("DemoMultiCameraFragment", "🔄 DEVICE ALREADY ESTABLISHED - Device ID $deviceId already connected, ignoring duplicate")
            return
        }
        
        // Secondary check: Look for duplicate cameras in connected list (same device connected twice)
        val isDuplicate = connectedCameras.any { existingCamera ->
            val existingDevice = existingCamera.getUsbDevice()
            existingDevice.deviceId == deviceId
        }
        
        if (isDuplicate) {
            Log.w("DemoMultiCameraFragment", "⚠️ DUPLICATE DEVICE IN CONNECTED LIST - Device ID $deviceId found in connectedCameras, ignoring")
            // ToastUtils.show("DUPLICATE: This camera is already connected - ignoring") // Removed for clean UI
            // Remove from connecting devices since we're not proceeding
            connectingDevices.remove(deviceId)
            return
        }
        
        // Add to connected cameras list and mark as established
        connectedCameras.add(camera)
        establishedDevices.add(deviceId)
        connectingDevices.remove(deviceId) // Remove from connecting since now established
        
        val cameraSlot = connectedCameras.size
        Log.i("DemoMultiCameraFragment", "✅ CAMERA SUCCESSFULLY ESTABLISHED")
        Log.i("DemoMultiCameraFragment", "Camera slot: $cameraSlot of 4")
        Log.i("DemoMultiCameraFragment", "Total connected cameras: ${connectedCameras.size}")
        Log.i("DemoMultiCameraFragment", "Established devices: ${establishedDevices.size}")
        
        // ToastUtils.show("ADDED: Camera $cameraSlot of 4 - Total connected: ${connectedCameras.size}") // Removed for clean UI
        
        // Update camera count display
        updateCameraCountDisplay()
        
        // With camera cycling, we setup all cameras but only activate one at a time
        when (cameraSlot) {
            1 -> {
                // ToastUtils.show("MANUAL MODE: $deviceName -> Camera 1 button ready") // Removed for clean UI
                Log.i("DemoMultiCameraFragment", "📹 CAMERA CYCLING MODE - Setting up Camera 1")
                setupCameraForManualSelection(camera, 1)
                activeCameras.add(1)
                
                // Auto-activate Camera 1 as default when it connects (with safety checks)
                Log.i("DemoMultiCameraFragment", "🎯 AUTO-SELECT: Activating Camera 1 as default")
                mainHandler.postDelayed({
                    try {
                        if (isAdded && view != null && !isDetached && currentActiveCameraIndex == -1) {
                            // Check if Camera 1 is not blacklisted before auto-selecting
                            if (!isDeviceBlacklisted(1)) {
                                selectCamera(1)
                            } else {
                                Log.w("DemoMultiCameraFragment", "⚠️ Camera 1 is blacklisted - skipping auto-select")
                                // Try to select an alternative working camera
                                switchToAlternativeCamera(1)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DemoMultiCameraFragment", "❌ Error during Camera 1 auto-select: ${e.message}")
                    }
                }, 2000) // Wait 2 seconds for camera to be ready (increased from 1s)
            }
            2 -> {
                // ToastUtils.show("CYCLING MODE: $deviceName -> Camera 2 button ready") // Removed for clean UI
                Log.i("DemoMultiCameraFragment", "🔄 CAMERA CYCLING MODE - Setting up Camera 2")
                setupCameraForManualSelection(camera, 2)
                activeCameras.add(2)
                
                // Start automatic cycling when we have 2+ cameras
                Log.i("DemoMultiCameraFragment", "🎬 STARTING 5-SECOND AUTO-CYCLING with ${activeCameras.size} cameras")
                mainHandler.postDelayed({
                    if (isAdded && view != null && activeCameras.size >= 2) {
                        startCameraCycling()
                    }
                }, 2000) // Wait 2 seconds for cameras to stabilize
            }
            3 -> {
                // ToastUtils.show("CYCLING MODE: $deviceName -> Camera 3 button ready") // Removed for clean UI
                Log.i("DemoMultiCameraFragment", "🔄 CAMERA CYCLING MODE - Setting up Camera 3")
                setupCameraForManualSelection(camera, 3)
                activeCameras.add(3)
                
                // Restart cycling with 3 cameras for better variety
                Log.i("DemoMultiCameraFragment", "🎬 RESTARTING 5-SECOND AUTO-CYCLING with ${activeCameras.size} cameras")
                stopCameraCycling()
                mainHandler.postDelayed({
                    if (isAdded && view != null && activeCameras.size >= 2) {
                        startCameraCycling()
                    }
                }, 1000)
            }
            4 -> {
                // ToastUtils.show("CYCLING MODE: $deviceName -> Camera 4 button ready") // Removed for clean UI
                Log.i("DemoMultiCameraFragment", "🔄 CAMERA CYCLING MODE - Setting up Camera 4")
                setupCameraForManualSelection(camera, 4)
                activeCameras.add(4)
                
                // Restart cycling with all 4 cameras
                Log.i("DemoMultiCameraFragment", "🎬 RESTARTING 5-SECOND AUTO-CYCLING with ALL ${activeCameras.size} cameras")
                stopCameraCycling()
                mainHandler.postDelayed({
                    if (isAdded && view != null && activeCameras.size >= 2) {
                        startCameraCycling()
                    }
                }, 1000)
            }
            else -> {
                // ToastUtils.show("LIMIT: Maximum 4 cameras supported. Camera $deviceName ignored.") // Removed for clean UI
                connectedCameras.remove(camera) // Remove from list since we can't use it
                updateCameraCountDisplay() // Update display after removing camera
                return
            }
        }
    }
    
    private fun setupCameraForManualSelection(camera: MultiCameraClient.ICamera, cameraIndex: Int) {
        Log.i("DemoMultiCameraFragment", "� SETTING UP CAMERA $cameraIndex FOR MANUAL SELECTION")
        
        // Set camera reference
        when (cameraIndex) {
            1 -> mCamera1 = camera
            2 -> mCamera2 = camera
            3 -> mCamera3 = camera
            4 -> mCamera4 = camera
        }
        
        // Set the camera state callback
        camera.setCameraStateCallBack(this)
        
        Log.i("DemoMultiCameraFragment", "✅ Camera $cameraIndex ready for manual selection")
        // Cleaned up log message
        // ToastUtils.show("READY: Camera $cameraIndex - Click button to view") // Removed for clean UI
        
        // Update button states to reflect available cameras
        updateCameraButtonStates(currentActiveCameraIndex)
    }
    
    private fun activateCamera(cameraIndex: Int) {
        Log.i("DemoMultiCameraFragment", "🎯 ACTIVATING CAMERA $cameraIndex for Full Screen Cycling")
        
        // First, deactivate all cameras
        deactivateAllCameras()
        
        // Get the camera, texture view, and container for this index
        val camera = when (cameraIndex) {
            1 -> mCamera1
            2 -> mCamera2
            3 -> mCamera3
            4 -> mCamera4
            else -> null
        }
        
        val textureView = when (cameraIndex) {
            1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
            2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
            3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
            4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
            else -> null
        }
        
        val cameraContainer = when (cameraIndex) {
            1 -> camera1Container
            2 -> camera2Container
            3 -> camera3Container
            4 -> camera4Container
            else -> null
        }
        
        if (camera == null || textureView == null || cameraContainer == null) {
            Log.w("DemoMultiCameraFragment", "❌ Cannot activate Camera $cameraIndex - components missing")
            return
        }
        
        currentActiveCameraIndex = cameraIndex
        
        // Make this camera container visible for full screen display
        mainHandler.post {
            if (isAdded && view != null) {
                // Hide the "no camera" indicator
                noCameraIndicator.visibility = View.GONE
                
                // Show only the active camera container in full screen
                cameraContainer.visibility = View.VISIBLE
                cameraContainer.bringToFront()
                
                // Update UI status
                activeCameraText.text = "📹 Active: Camera $cameraIndex"
                activeCameraText.setTextColor(resources.getColor(android.R.color.holo_green_light, null))
                
                Log.i("DemoMultiCameraFragment", "👁️ Camera $cameraIndex Container set to FULL SCREEN")
                // Cleaned up log message
                // ToastUtils.show("📹 NOW SHOWING: Camera $cameraIndex (Full Screen) // Removed for clean UI")
            }
        }
        
        try {
            // Get camera-specific configuration
            val device = camera.getUsbDevice()
            val request = getCameraSpecificRequest(device)
            
            Log.i("DemoMultiCameraFragment", "📹 OPENING Camera $cameraIndex for full screen display")
            
            if (isAdded && view != null && !isDetached) {
                textureView.post {
                    try {
                        if (isAdded && view != null && !isDetached && activity != null) {
                            camera.openCamera(textureView, request)
                            Log.i("DemoMultiCameraFragment", "✅ CAMERA $cameraIndex OPENED for cycling")
                        }
                    } catch (e: Exception) {
                        Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex OPENING FAILED: ${e.message}", e)
                        // ToastUtils.show("FAILED: Camera $cameraIndex - ${e.message}") // Removed for clean UI
                    }
                }
                
                // Add preview callback after a delay
                mainHandler.postDelayed({
                    if (isAdded && view != null && camera is CameraUVC) {
                        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                            override fun onPreviewData(
                                data: ByteArray?,
                                width: Int,
                                height: Int,
                                format: IPreviewDataCallBack.DataFormat
                            ) {
                                if (isAdded && view != null && data != null) {
                                    // Track frame count and timing
                                    val currentTime = System.currentTimeMillis()
                                    val frameCount = frameCounters.getOrDefault(cameraIndex, 0) + 1
                                    frameCounters[cameraIndex] = frameCount
                                    lastFrameTime[cameraIndex] = currentTime
                                    
                                                    // Log first frame
                                    if (frameCount == 1) {
                                        Log.i("DemoMultiCameraFragment", "🎬 FIRST FRAME: Camera $cameraIndex (${width}x${height}) - FULL SCREEN MODE")
                                        // Cleaned up log message
                                        // ToastUtils.show("🎥 LIVE: Camera $cameraIndex (${width}x${height}) // Removed for clean UI - FULL SCREEN")
                                        
                                        // Show cycling info
                                        mainHandler.postDelayed({
                                            if (isAdded && view != null) {
                                                // Cleaned up log message
                                                // ToastUtils.show("🔄 Cycling every ${cameraSwitchInterval/1000}s between ${activeCameras.size} cameras") // Removed for clean UI
                                            }
                                        }, 1000)
                                    }
                                    
                                    // Process frame for object detection (single camera - no bandwidth issues)
                                    when (format) {
                                        IPreviewDataCallBack.DataFormat.NV21 -> {
                                            processFrame(data, width, height, cameraIndex)
                                        }
                                        IPreviewDataCallBack.DataFormat.RGBA -> {
                                            // Convert RGBA to NV21 if needed
                                        }
                                        else -> {
                                            Log.d("DemoMultiCameraFragment", "Unsupported format: $format for Camera $cameraIndex")
                                        }
                                    }
                                }
                            }
                        })
                        Log.i("DemoMultiCameraFragment", "📹 PREVIEW CALLBACK ADDED for Camera $cameraIndex")
                    }
                }, 2000)
            }
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex ACTIVATION FAILED: ${e.message}", e)
            // ToastUtils.show("ACTIVATION FAILED: Camera $cameraIndex - ${e.message}") // Removed for clean UI
        }
    }
    
    private fun deactivateAllCameras() {
        Log.i("DemoMultiCameraFragment", "🔄 DEACTIVATING ALL CAMERAS for cycling")
        
        currentActiveCameraIndex = -1
        
        // Hide all camera containers
        mainHandler.post {
            if (isAdded && view != null) {
                camera1Container.visibility = View.GONE
                camera2Container.visibility = View.GONE
                camera3Container.visibility = View.GONE
                camera4Container.visibility = View.GONE
                
                // Show the "no camera" indicator
                noCameraIndicator.visibility = View.VISIBLE
                
                // Update UI status
                activeCameraText.text = "📹 Active: None"
                activeCameraText.setTextColor(resources.getColor(android.R.color.holo_orange_light, null))
                
                Log.i("DemoMultiCameraFragment", "👁️ All camera containers hidden")
            }
        }
        
        for (cameraIndex in 1..4) {
            val camera = when (cameraIndex) {
                1 -> mCamera1
                2 -> mCamera2
                3 -> mCamera3
                4 -> mCamera4
                else -> null
            }
            
            if (camera != null) {
                try {
                    camera.closeCamera()
                    Log.d("DemoMultiCameraFragment", "📹 Camera $cameraIndex closed")
                } catch (e: Exception) {
                    Log.w("DemoMultiCameraFragment", "❌ Error closing Camera $cameraIndex: ${e.message}")
                }
            }
        }
    }
    
    private fun startCameraCycling() {
        // Comprehensive safety checks
        if (!isAdded || view == null || isDetached || isRemoving) {
            Log.w("DemoMultiCameraFragment", "⚠️ Fragment not in valid state for cycling - skipping")
            return
        }
        
        if (!cameraCyclingEnabled || activeCameras.isEmpty()) {
            Log.w("DemoMultiCameraFragment", "⚠️ Cycling disabled or no active cameras - skipping")
            return
        }
        
        // Filter out blacklisted devices from cycling
        val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
        if (workingCameras.isEmpty()) {
            Log.w("DemoMultiCameraFragment", "⚠️ No working cameras available for cycling")
            return
        }
        
        if (workingCameras.size < 2) {
            Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working camera - cycling not needed")
            return
        }
        
        Log.i("DemoMultiCameraFragment", "🔄 STARTING CAMERA CYCLING")
        Log.i("DemoMultiCameraFragment", "Working cameras: ${workingCameras.joinToString(", ")}")
        Log.i("DemoMultiCameraFragment", "Blacklisted: ${failedDevices.joinToString(", ")}")
        Log.i("DemoMultiCameraFragment", "Switch interval: ${cameraSwitchInterval/1000} seconds")
        // Cleaned up log message
        // ToastUtils.show("CYCLING STARTED: ${activeCameras.size} cameras, switching every ${cameraSwitchInterval/1000}s") // Removed for clean UI
        
        // Show cycling instructions
        mainHandler.postDelayed({
            if (isAdded && view != null) {
                // Cleaned up log message
                // ToastUtils.show("CYCLING MODE: Each camera shows for ${cameraSwitchInterval/1000}s in full screen") // Removed for clean UI
            }
        }, 2000)
        
        // Stop any existing cycling
        stopCameraCycling()
        
        // Reset to first camera
        currentActiveCameraIndex = 0
        
        // Create cycling runnable with enhanced safety checks
        cameraCyclingRunnable = object : Runnable {
            override fun run() {
                try {
                    // Comprehensive safety checks
                    if (!isAdded || view == null || isDetached || isRemoving) {
                        Log.w("DemoMultiCameraFragment", "⚠️ Fragment invalid during cycling - stopping")
                        stopCameraCycling()
                        return
                    }
                    
                    if (!cameraCyclingEnabled) {
                        Log.i("DemoMultiCameraFragment", "🔄 Cycling disabled - stopping")
                        return
                    }
                    
                    // Get working cameras (filter blacklisted)
                    val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
                    if (workingCameras.isEmpty()) {
                        Log.w("DemoMultiCameraFragment", "⚠️ No working cameras for cycling - stopping")
                        stopCameraCycling()
                        return
                    }
                    
                    if (workingCameras.size < 2) {
                        Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working camera - stopping cycling")
                        stopCameraCycling()
                        return
                    }
                    
                    // Adjust current index if needed (in case camera was blacklisted)
                    if (currentActiveCameraIndex >= workingCameras.size) {
                        currentActiveCameraIndex = 0
                    }
                    
                    // Get current camera index
                    val cameraIndex = workingCameras[currentActiveCameraIndex]
                    
                    Log.i("DemoMultiCameraFragment", "🔄 CYCLING TO Camera $cameraIndex (${currentActiveCameraIndex + 1}/${workingCameras.size})")
                    
                    // Activate the current camera (with safety check)
                    if (!isDeviceBlacklisted(cameraIndex)) {
                        activateCamera(cameraIndex)
                    } else {
                        Log.w("DemoMultiCameraFragment", "⚠️ Camera $cameraIndex became blacklisted - skipping")
                    }
                    
                    // Move to next camera
                    currentActiveCameraIndex = (currentActiveCameraIndex + 1) % workingCameras.size
                    
                    // Schedule next switch with safety check
                    if (isAdded && view != null && !isDetached && !isRemoving && cameraCyclingEnabled) {
                        mainHandler.postDelayed(this, cameraSwitchInterval)
                    }
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error during camera cycling: ${e.message}")
                    // Try to recover by stopping cycling
                    try {
                        stopCameraCycling()
                    } catch (stopError: Exception) {
                        Log.e("DemoMultiCameraFragment", "❌ Error stopping cycling: ${stopError.message}")
                    }
                }
            }
        }
        
        // Start cycling immediately
        cameraCyclingRunnable?.let { mainHandler.post(it) }
    }
    
    private fun stopCameraCycling() {
        try {
            Log.i("DemoMultiCameraFragment", "🛑 STOPPING CAMERA CYCLING")
            cameraCyclingRunnable?.let { 
                try {
                    mainHandler.removeCallbacks(it)
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error removing cycling callbacks: ${e.message}")
                }
            }
            cameraCyclingRunnable = null
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ Error stopping camera cycling: ${e.message}")
        }
    }
    
    private fun setupCamera(camera: MultiCameraClient.ICamera, textureView: AspectRatioTextureView?, cameraIndex: Int) {
        // ToastUtils.show("SETUP: Starting setup for Camera $cameraIndex") // Removed for clean UI
        
        // Validate USB connection before setup
        val device = camera.getUsbDevice()
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        
        // Check if USB device is still valid and has permission
        if (!usbManager.hasPermission(device)) {
            Log.e("DemoMultiCameraFragment", "❌ USB PERMISSION LOST for Camera $cameraIndex")
            Log.e("DemoMultiCameraFragment", "Device: ${device.deviceName}")
            // ToastUtils.show("ERROR: USB permission lost for Camera $cameraIndex") // Removed for clean UI
            return
        }
        
        // Verify device is still connected
        val deviceList = usbManager.deviceList
        val isDeviceConnected = deviceList.values.any { it.deviceId == device.deviceId }
        
        if (!isDeviceConnected) {
            Log.e("DemoMultiCameraFragment", "❌ USB DEVICE DISCONNECTED for Camera $cameraIndex")
            Log.e("DemoMultiCameraFragment", "Device ID: ${device.deviceId}")
            Log.e("DemoMultiCameraFragment", "Device was physically disconnected")
            // ToastUtils.show("ERROR: Camera $cameraIndex physically disconnected") // Removed for clean UI
            
            // Remove from connected cameras
            connectedCameras.remove(camera)
            establishedDevices.remove(device.deviceId)
            updateCameraCountDisplay()
            return
        }
        
        Log.i("DemoMultiCameraFragment", "✅ USB CONNECTION VALID for Camera $cameraIndex")
        Log.i("DemoMultiCameraFragment", "Device: ${device.deviceName} (ID: ${device.deviceId})")
        
        // Get the correct texture view reference from the binding
        val correctTextureView = when (cameraIndex) {
            1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
            2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
            3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
            4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
            else -> null
        }
        
        if (correctTextureView == null) {
            // ToastUtils.show("ERROR: TextureView $cameraIndex not found! Retrying...") // Removed for clean UI
            Handler(Looper.getMainLooper()).postDelayed({
                setupCamera(camera, null, cameraIndex)
            }, 1000)
            return
        }
        
        // ToastUtils.show("SUCCESS: TextureView $cameraIndex found, proceeding...") // Removed for clean UI
        
        // Set camera reference
        when (cameraIndex) {
            1 -> mCamera1 = camera
            2 -> mCamera2 = camera
            3 -> mCamera3 = camera
            4 -> mCamera4 = camera
        }
        
        // ToastUtils.show("OPENING: Camera $cameraIndex - attempting to open") // Removed for clean UI
        camera.setCameraStateCallBack(this)
        
        try {
            // Use device-specific configuration instead of generic one
            val device = camera.getUsbDevice()
            val request = getCameraSpecificRequest(device)
            // ToastUtils.show("REQUEST: Camera-specific config created for camera $cameraIndex") // Removed for clean UI
            
            // Ensure texture view is ready and check if fragment is still attached
            if (isAdded && view != null && !isDetached) {
                correctTextureView.post {
                    try {
                        // Triple check fragment is still alive and not detached before opening camera
                        if (isAdded && view != null && !isDetached && activity != null) {
                            Log.i("DemoMultiCameraFragment", "📹 OPENING CAMERA $cameraIndex")
                            Log.i("DemoMultiCameraFragment", "TextureView ready: ${correctTextureView.isAvailable}")
                            Log.i("DemoMultiCameraFragment", "Fragment state: added=$isAdded, detached=$isDetached")
                            
                            camera.openCamera(correctTextureView, request)
                            // ToastUtils.show("OPEN SUCCESS: Camera $cameraIndex opened - waiting for preview") // Removed for clean UI
                            
                            Log.i("DemoMultiCameraFragment", "✅ CAMERA $cameraIndex OPENING INITIATED")
                        } else {
                            Log.w("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex OPENING SKIPPED - Fragment not in valid state")
                        }
                    } catch (e: Exception) {
                        Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex OPENING FAILED: ${e.message}", e)
                        // ToastUtils.show("OPEN FAILED: Camera $cameraIndex - ${e.message}") // Removed for clean UI
                    }
                }
                
                // Add preview data callback AFTER camera is opened - use main handler safely
                mainHandler.postDelayed({
                    // Check if fragment is still alive before adding callback
                    if (isAdded && view != null && !isDetached && activity != null && camera is CameraUVC) {
                        Log.i("DemoMultiCameraFragment", "🔧 ADDING PREVIEW CALLBACK for Camera $cameraIndex")
                        Log.i("DemoMultiCameraFragment", "Camera Type: ${camera.javaClass.simpleName}")
                        Log.i("DemoMultiCameraFragment", "Camera Device: ${camera.getUsbDevice().deviceName}")
                        
                        camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                            override fun onPreviewData(
                                data: ByteArray?,
                                width: Int,
                                height: Int,
                                format: IPreviewDataCallBack.DataFormat
                            ) {
                                // Check if fragment is still alive before processing
                                if (isAdded && view != null && data != null) {
                                    // Track frame count and timing for preview status
                                    val currentTime = System.currentTimeMillis()
                                    val frameCount = frameCounters.getOrDefault(cameraIndex, 0) + 1
                                    frameCounters[cameraIndex] = frameCount
                                    lastFrameTime[cameraIndex] = currentTime
                                    
                                    // Track bandwidth usage
                                    val totalBytes = frameBytesReceived.getOrDefault(cameraIndex, 0L) + data.size
                                    frameBytesReceived[cameraIndex] = totalBytes
                                    
                                    // Initialize bandwidth tracking start time on first frame
                                    if (frameCount == 1) {
                                        bandwidthStartTime[cameraIndex] = currentTime
                                        lastBandwidthReport[cameraIndex] = currentTime
                                    }
                                    
                                    // Log first frame received
                                    if (frameCount == 1) {
                                        Log.i("DemoMultiCameraFragment", "🎬 FIRST FRAME RECEIVED - Camera $cameraIndex")
                                        Log.i("DemoMultiCameraFragment", "Resolution: ${width}x${height}")
                                        Log.i("DemoMultiCameraFragment", "Format: $format")
                                        Log.i("DemoMultiCameraFragment", "Data Size: ${data.size} bytes")
                                        Log.i("DemoMultiCameraFragment", "✅ PREVIEW IS WORKING for Camera $cameraIndex")
                                        // Cleaned up log message
                                        // ToastUtils.show("PREVIEW ACTIVE: Camera $cameraIndex (${width}x${height}) // Removed for clean UI")
                                        
                                        // Check if camera is ignoring our resolution request
                                        val device = when (cameraIndex) {
                                            1 -> mCamera1?.getUsbDevice()
                                            2 -> mCamera2?.getUsbDevice()
                                            3 -> mCamera3?.getUsbDevice()
                                            4 -> mCamera4?.getUsbDevice()
                                            else -> null
                                        }
                                        
                                        if (device != null) {
                                            val isDevice1004 = device.vendorId == 13468 || device.deviceName.contains("001/004")
                                            val isMultiCameraMode = connectedCameras.size > 1
                                            
                                            // For Device 1004 in multi-camera mode, we expect very small resolutions
                                            if (isDevice1004 && isMultiCameraMode && (width > 200 || height > 200)) {
                                                Log.e("DemoMultiCameraFragment", "❌ RESOLUTION VIOLATION - Device 1004 using ${width}x${height}")
                                                Log.e("DemoMultiCameraFragment", "Expected: <200x200 for multi-camera bandwidth conservation")
                                                Log.e("DemoMultiCameraFragment", "This may cause USB bandwidth exhaustion and Camera 2 failure")
                                                // Cleaned up log message
                                                // ToastUtils.show("RESOLUTION VIOLATION: Device 1004 using ${width}x${height} instead of nano") // Removed for clean UI
                                                
                                                // Try alternative resolution enforcement approaches
                                                tryAlternativeResolutionEnforcement(cameraIndex, device, width, height)
                                            }
                                            
                                            // For any device in multi-camera mode with excessive resolution
                                            if (isMultiCameraMode && (width > 320 || height > 240)) {
                                                Log.w("DemoMultiCameraFragment", "⚠️ HIGH RESOLUTION WARNING - Camera $cameraIndex using ${width}x${height}")
                                                Log.w("DemoMultiCameraFragment", "This may consume excessive USB bandwidth in multi-camera mode")
                                                // Cleaned up log message
                                                // ToastUtils.show("HIGH BANDWIDTH: Camera $cameraIndex using ${width}x${height}") // Removed for clean UI
                                            }
                                        }
                                        
                                        // In cycling mode, bandwidth conservation is automatic since only one camera is active
                                        Log.i("DemoMultiCameraFragment", "� CYCLING MODE: Full bandwidth available for Camera $cameraIndex")
                                    }
                                    
                                    // Log every 100th frame for ongoing status
                                    if (frameCount % 100 == 0) {
                                        Log.d("DemoMultiCameraFragment", "📹 PREVIEW STATUS: Camera $cameraIndex - Frame $frameCount (${width}x${height})")
                                    }
                                    
                                    // Report bandwidth usage every 5 seconds
                                    val lastReport = lastBandwidthReport[cameraIndex] ?: currentTime
                                    if (currentTime - lastReport >= 5000) { // Every 5 seconds
                                        reportBandwidthUsage(cameraIndex, currentTime)
                                        lastBandwidthReport[cameraIndex] = currentTime
                                    }
                                    
                                    when (format) {
                                        IPreviewDataCallBack.DataFormat.NV21 -> {
                                            processFrame(data, width, height, cameraIndex)
                                        }
                                        IPreviewDataCallBack.DataFormat.RGBA -> {
                                            processFrame(data, width, height, cameraIndex)
                                        }
                                        else -> {
                                            // Try to process anyway for unknown formats
                                            if (frameCount == 1) {
                                                Log.w("DemoMultiCameraFragment", "⚠️ UNKNOWN FORMAT: Camera $cameraIndex using format $format")
                                            }
                                            processFrame(data, width, height, cameraIndex)
                                        }
                                    }
                                } else {
                                    Log.w("DemoMultiCameraFragment", "❌ PREVIEW DATA DROPPED: Camera $cameraIndex - Fragment not ready or data null")
                                }
                            }
                        })
                        // ToastUtils.show("CALLBACK: Preview callback added for Camera $cameraIndex") // Removed for clean UI
                        Log.i("DemoMultiCameraFragment", "📹 PREVIEW CALLBACK ADDED for Camera $cameraIndex")
                        
                        // Start monitoring for frames after callback is added
                        startFrameMonitoring(cameraIndex)
                    } else {
                        Log.w("DemoMultiCameraFragment", "❌ PREVIEW CALLBACK SKIPPED for Camera $cameraIndex - Fragment not in valid state")
                    }
                }, 3000) // Wait 3 seconds after camera opens before adding callback to reduce USB load
            } else {
                Log.w("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex SETUP SKIPPED - Fragment not ready (added=$isAdded, view=${view != null}, detached=$isDetached)")
            }
            
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex SETUP FAILED: ${e.message}", e)
            // ToastUtils.show("SETUP FAILED: Camera $cameraIndex - ${e.message}") // Removed for clean UI
        }
    }
    
    private fun startFrameMonitoring(cameraIndex: Int) {
        // Monitor for first frame within 5 seconds
        mainHandler.postDelayed({
            if (isAdded && view != null) {
                val frameCount = frameCounters[cameraIndex] ?: 0
                if (frameCount == 0) {
                    Log.w("DemoMultiCameraFragment", "⚠️ FRAME TIMEOUT: Camera $cameraIndex - No frames received after 5 seconds")
                    Log.w("DemoMultiCameraFragment", "Attempting to restart Camera $cameraIndex preview")
                    
                    // Try to restart the preview
                    val camera = when (cameraIndex) {
                        1 -> mCamera1
                        2 -> mCamera2
                        3 -> mCamera3
                        4 -> mCamera4
                        else -> null
                    }
                    
                    if (camera != null) {
                        Log.i("DemoMultiCameraFragment", "🔄 RESTARTING PREVIEW for Camera $cameraIndex")
                        restartCameraPreview(camera, cameraIndex)
                    }
                } else {
                    Log.i("DemoMultiCameraFragment", "✅ FRAME MONITORING OK: Camera $cameraIndex received $frameCount frames")
                }
            }
        }, 5000)
    }
    
    private fun restartCameraPreview(camera: MultiCameraClient.ICamera, cameraIndex: Int) {
        try {
            Log.i("DemoMultiCameraFragment", "🔄 RESTARTING CAMERA $cameraIndex PREVIEW")
            
            // Get the correct texture view reference from the binding (not stored references)
            val textureView = when (cameraIndex) {
                1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
                2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
                3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
                4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
                else -> null
            }
            
            if (textureView != null && camera is CameraUVC) {
                // Try with a different configuration - use smaller resolution for problematic cameras
                val device = camera.getUsbDevice()
                val deviceName = device.deviceName.lowercase()
                
                Log.i("DemoMultiCameraFragment", "🔄 TRYING SMALLER RESOLUTION for Camera $cameraIndex")
                
                // For restart, try even more conservative settings
                val restartRequest = try {
                    when {
                        deviceName.contains("001/003") -> {
                            // Device 1003 - try smaller resolution
                            Log.i("DemoMultiCameraFragment", "🔧 Device 1003 restart - trying 320x240")
                            CameraRequest.Builder()
                                .setPreviewWidth(320)
                                .setPreviewHeight(240)
                                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                                .setAspectRatioShow(false)
                                .setRawPreviewData(true)
                                .create()
                        }
                        else -> {
                            // For other devices, try minimal resolution
                            Log.i("DemoMultiCameraFragment", "� Generic restart - trying 176x144")
                            CameraRequest.Builder()
                                .setPreviewWidth(176)
                                .setPreviewHeight(144)
                                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_MJPEG)
                                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                                .setAspectRatioShow(false)
                                .setRawPreviewData(true)
                                .create()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("DemoMultiCameraFragment", "❌ Restart config failed: ${e.message}, trying YUYV")
                    CameraRequest.Builder()
                        .setPreviewWidth(176)
                        .setPreviewHeight(144)
                        .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                        .setRenderMode(CameraRequest.RenderMode.NORMAL)
                        .setAspectRatioShow(false)
                        .setRawPreviewData(true)
                        .create()
                }
                
                Log.i("DemoMultiCameraFragment", "🔄 CLOSING Camera $cameraIndex for restart")
                
                // Close camera but DON'T remove from connected list - we want to keep the slot
                camera.closeCamera()
                
                mainHandler.postDelayed({
                    if (isAdded && view != null) {
                        Log.i("DemoMultiCameraFragment", "🔄 REOPENING Camera $cameraIndex with conservative config")
                        
                        // Reopen camera with the same texture view and camera index
                        camera.openCamera(textureView, restartRequest)
                        
                        // Re-add the preview callback after restart
                        mainHandler.postDelayed({
                            if (isAdded && view != null && camera is CameraUVC) {
                                Log.i("DemoMultiCameraFragment", "� RE-ADDING PREVIEW CALLBACK for restarted Camera $cameraIndex")
                                
                                camera.addPreviewDataCallBack(object : IPreviewDataCallBack {
                                    override fun onPreviewData(
                                        data: ByteArray?,
                                        width: Int,
                                        height: Int,
                                        format: IPreviewDataCallBack.DataFormat
                                    ) {
                                        if (isAdded && view != null && data != null) {
                                            // Track frame count and timing
                                            val currentTime = System.currentTimeMillis()
                                            val frameCount = frameCounters.getOrDefault(cameraIndex, 0) + 1
                                            frameCounters[cameraIndex] = frameCount
                                            lastFrameTime[cameraIndex] = currentTime
                                            
                                            // Log restart success
                                            if (frameCount == 1) {
                                                Log.i("DemoMultiCameraFragment", "🎉 RESTART SUCCESS - Camera $cameraIndex first frame")
                                                Log.i("DemoMultiCameraFragment", "Restart Resolution: ${width}x${height}")
                                                // ToastUtils.show("RESTART SUCCESS: Camera $cameraIndex (${width}x${height}) // Removed for clean UI")
                                            }
                                            
                                            processFrame(data, width, height, cameraIndex)
                                        }
                                    }
                                })
                                
                                Log.i("DemoMultiCameraFragment", "✅ PREVIEW CALLBACK RE-ADDED for restarted Camera $cameraIndex")
                            }
                        }, 2000)
                        
                        Log.i("DemoMultiCameraFragment", "🔄 Camera $cameraIndex restart initiated with conservative settings")
                    }
                }, 1000)
            } else {
                Log.w("DemoMultiCameraFragment", "❌ RESTART SKIPPED: Camera $cameraIndex - TextureView not found or invalid camera type")
            }
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ PREVIEW RESTART FAILED for Camera $cameraIndex: ${e.message}")
        }
    }
    
    private fun checkUsbConnectionHealth() {
        if (!isAdded || view == null) return
        
        val usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        val currentDevices = usbManager.deviceList
        
        Log.i("DemoMultiCameraFragment", "🩺 USB CONNECTION HEALTH CHECK")
        Log.i("DemoMultiCameraFragment", "Connected cameras: ${connectedCameras.size}")
        Log.i("DemoMultiCameraFragment", "USB devices found: ${currentDevices.size}")
        
        // Check each connected camera
        val cameraIterator = connectedCameras.iterator()
        while (cameraIterator.hasNext()) {
            val camera = cameraIterator.next()
            val device = camera.getUsbDevice()
            val deviceId = device.deviceId
            
            // Check if device still exists in USB device list
            val stillConnected = currentDevices.values.any { it.deviceId == deviceId }
            val hasPermission = usbManager.hasPermission(device)
            
            if (!stillConnected) {
                Log.w("DemoMultiCameraFragment", "⚠️ USB DEVICE LOST: Device $deviceId (${device.deviceName})")
                // ToastUtils.show("USB LOST: Device $deviceId disconnected") // Removed for clean UI
                
                // Remove from all tracking
                cameraIterator.remove()
                establishedDevices.remove(deviceId)
                connectingDevices.remove(deviceId)
                
                // Clear camera reference
                when (camera) {
                    mCamera1 -> { mCamera1 = null; frameCounters.remove(1); lastFrameTime.remove(1) }
                    mCamera2 -> { mCamera2 = null; frameCounters.remove(2); lastFrameTime.remove(2) }
                    mCamera3 -> { mCamera3 = null; frameCounters.remove(3); lastFrameTime.remove(3) }
                    mCamera4 -> { mCamera4 = null; frameCounters.remove(4); lastFrameTime.remove(4) }
                }
            } else if (!hasPermission) {
                Log.w("DemoMultiCameraFragment", "⚠️ USB PERMISSION LOST: Device $deviceId (${device.deviceName})")
                // ToastUtils.show("PERMISSION LOST: Device $deviceId needs re-authorization") // Removed for clean UI
            } else {
                Log.d("DemoMultiCameraFragment", "✅ USB OK: Device $deviceId (${device.deviceName})")
            }
        }
        
        updateCameraCountDisplay()
    }
    
    private fun waitForCameraStability(cameraIndex: Int, onStable: () -> Unit) {
        val maxWaitTime = 30000L // Maximum 30 seconds wait
        val checkInterval = 2000L // Check every 2 seconds
        val startTime = System.currentTimeMillis()
        
        val stabilityChecker = object : Runnable {
            override fun run() {
                if (!isAdded || view == null) return
                
                // First check USB connection health
                checkUsbConnectionHealth()
                
                val currentTime = System.currentTimeMillis()
                val elapsedTime = currentTime - startTime
                
                // Check if camera is working (receiving frames)
                val frameCount = frameCounters[cameraIndex] ?: 0
                val lastFrame = lastFrameTime[cameraIndex] ?: 0
                val isReceivingFrames = frameCount > 10 && (currentTime - lastFrame) < 3000
                
                Log.i("DemoMultiCameraFragment", "🔍 STABILITY CHECK - Camera $cameraIndex")
                Log.i("DemoMultiCameraFragment", "Frames: $frameCount, Last frame: ${(currentTime - lastFrame)/1000}s ago")
                Log.i("DemoMultiCameraFragment", "Receiving frames: $isReceivingFrames")
                
                when {
                    isReceivingFrames -> {
                        Log.i("DemoMultiCameraFragment", "✅ CAMERA $cameraIndex STABLE - Proceeding with next camera")
                        workingCameras.add(cameraIndex)
                        // ToastUtils.show("STABLE: Camera $cameraIndex is working well") // Removed for clean UI
                        onStable()
                    }
                    elapsedTime > maxWaitTime -> {
                        Log.w("DemoMultiCameraFragment", "⏱️ STABILITY TIMEOUT - Camera $cameraIndex after ${maxWaitTime/1000}s")
                        // ToastUtils.show("TIMEOUT: Camera $cameraIndex - proceeding anyway") // Removed for clean UI
                        onStable() // Proceed anyway after timeout
                    }
                    else -> {
                        Log.d("DemoMultiCameraFragment", "⏳ WAITING FOR STABILITY - Camera $cameraIndex (${elapsedTime/1000}s elapsed)")
                        mainHandler.postDelayed(this, checkInterval)
                    }
                }
            }
        }
        
        // Start checking after a short delay to allow camera to initialize
        mainHandler.postDelayed(stabilityChecker, 3000)
    }
    
    private fun startStalledCameraMonitoring() {
        // Check for stalled cameras every 10 seconds
        val stalledCheckRunnable = object : Runnable {
            override fun run() {
                if (isAdded && view != null) {
                    checkForStalledCameras()
                    mainHandler.postDelayed(this, 10000) // Check again in 10 seconds
                }
            }
        }
        mainHandler.postDelayed(stalledCheckRunnable, 10000) // Start checking after 10 seconds
        
        // Also start USB connection health monitoring
        val usbHealthCheckRunnable = object : Runnable {
            override fun run() {
                if (isAdded && view != null) {
                    checkUsbConnectionHealth()
                    mainHandler.postDelayed(this, 15000) // Check USB health every 15 seconds
                }
            }
        }
        mainHandler.postDelayed(usbHealthCheckRunnable, 5000) // Start USB health checking after 5 seconds
    }
    
    private fun checkForStalledCameras() {
        if (!isAdded || view == null) return
        
        val currentTime = System.currentTimeMillis()
        for (cameraIndex in 1..4) {
            val lastFrame = lastFrameTime[cameraIndex]
            val frameCount = frameCounters[cameraIndex] ?: 0
            
            // Only check cameras that should be connected
            val isConnected = when (cameraIndex) {
                1 -> mCamera1 != null
                2 -> mCamera2 != null
                3 -> mCamera3 != null
                4 -> mCamera4 != null
                else -> false
            }
            
            if (isConnected) {
                if (frameCount == 0) {
                    // No frames received at all for connected camera
                    Log.w("DemoMultiCameraFragment", "⚠️ NO FRAMES: Camera $cameraIndex is connected but not receiving preview frames")
                    Log.w("DemoMultiCameraFragment", "This may indicate a preview initialization issue")
                } else if (lastFrame != null && (currentTime - lastFrame) > 5000) {
                    // No frames in last 5 seconds for previously working camera
                    Log.w("DemoMultiCameraFragment", "⚠️ STALLED PREVIEW: Camera $cameraIndex stopped receiving frames")
                    Log.w("DemoMultiCameraFragment", "Last frame: ${(currentTime - lastFrame)/1000}s ago, Total frames: $frameCount")
                    // ToastUtils.show("WARNING: Camera $cameraIndex preview may be stalled") // Removed for clean UI
                } else if (frameCount > 0) {
                    // Camera is working fine
                    Log.d("DemoMultiCameraFragment", "✅ PREVIEW OK: Camera $cameraIndex - $frameCount frames, last ${(currentTime - (lastFrame ?: currentTime))/1000}s ago")
                }
            }
        }
    }

    private fun setupCameraSelectionButtons() {
        camera1Button.setOnClickListener {
            // Temporarily stop cycling and show selected camera
            stopCameraCycling()
            selectCamera(1)
            // Resume cycling after 10 seconds (with comprehensive safety checks)
            mainHandler.postDelayed({
                try {
                    if (isAdded && view != null && !isDetached && !isRemoving && cameraCyclingEnabled) {
                        val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
                        if (workingCameras.size >= 2) {
                            Log.i("DemoMultiCameraFragment", "🔄 Auto-resuming cycling after manual override")
                            startCameraCycling()
                        } else {
                            Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working cameras - not resuming cycling")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error auto-resuming cycling: ${e.message}")
                }
            }, 10000)
        }
        
        camera2Button.setOnClickListener {
            stopCameraCycling()
            selectCamera(2)
            mainHandler.postDelayed({
                try {
                    if (isAdded && view != null && !isDetached && !isRemoving && cameraCyclingEnabled) {
                        val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
                        if (workingCameras.size >= 2) {
                            Log.i("DemoMultiCameraFragment", "🔄 Auto-resuming cycling after manual override")
                            startCameraCycling()
                        } else {
                            Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working cameras - not resuming cycling")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error auto-resuming cycling: ${e.message}")
                }
            }, 10000)
        }
        
        camera3Button.setOnClickListener {
            stopCameraCycling()
            selectCamera(3)
            mainHandler.postDelayed({
                try {
                    if (isAdded && view != null && !isDetached && !isRemoving && cameraCyclingEnabled) {
                        val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
                        if (workingCameras.size >= 2) {
                            Log.i("DemoMultiCameraFragment", "🔄 Auto-resuming cycling after manual override")
                            startCameraCycling()
                        } else {
                            Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working cameras - not resuming cycling")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error auto-resuming cycling: ${e.message}")
                }
            }, 10000)
        }
        
        camera4Button.setOnClickListener {
            stopCameraCycling()
            selectCamera(4)
            mainHandler.postDelayed({
                try {
                    if (isAdded && view != null && !isDetached && !isRemoving && cameraCyclingEnabled) {
                        val workingCameras = activeCameras.filter { !isDeviceBlacklisted(it) }
                        if (workingCameras.size >= 2) {
                            Log.i("DemoMultiCameraFragment", "🔄 Auto-resuming cycling after manual override")
                            startCameraCycling()
                        } else {
                            Log.i("DemoMultiCameraFragment", "📷 Only ${workingCameras.size} working cameras - not resuming cycling")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error auto-resuming cycling: ${e.message}")
                }
            }, 10000)
        }
        
        Log.i("DemoMultiCameraFragment", "📱 Camera selection buttons initialized - Auto-cycling every 5s")
        Log.i("DemoMultiCameraFragment", "👆 Click any button to pause cycling for 10 seconds")
    }
    
    private fun selectCamera(cameraIndex: Int) {
        val cyclingStatus = if (cameraCyclingEnabled && activeCameras.size >= 2) "CYCLING PAUSED" else "MANUAL SELECTION"
        Log.i("DemoMultiCameraFragment", "🎯 $cyclingStatus: Camera $cameraIndex")
        
        val camera = when (cameraIndex) {
            1 -> mCamera1
            2 -> mCamera2
            3 -> mCamera3
            4 -> mCamera4
            else -> null
        }
        
        if (camera == null) {
            Log.w("DemoMultiCameraFragment", "❌ Camera $cameraIndex not available")
            return
        }
        
        // Check if device is blacklisted
        if (isDeviceBlacklisted(cameraIndex)) {
            Log.w("DemoMultiCameraFragment", "❌ Camera $cameraIndex is blacklisted (failed device)")
            return
        }
        
        // Activate the selected camera
        activateCamera(cameraIndex)
        
        // Update button states
        updateCameraButtonStates(cameraIndex)
        
        if (cameraCyclingEnabled && activeCameras.size >= 2) {
            Log.i("DemoMultiCameraFragment", "✅ CYCLING PAUSED: Camera $cameraIndex selected - Auto-resume in 10s")
        } else {
            Log.i("DemoMultiCameraFragment", "✅ MANUAL SELECTION: Successfully switched to Camera $cameraIndex")
        }
    }
    
    private fun updateCameraButtonStates(activeCameraIndex: Int) {
        mainHandler.post {
            if (isAdded && view != null) {
                // Reset all buttons to inactive state
                camera1Button.setBackgroundColor(android.graphics.Color.parseColor("#555555"))
                camera2Button.setBackgroundColor(android.graphics.Color.parseColor("#555555"))
                camera3Button.setBackgroundColor(android.graphics.Color.parseColor("#555555"))
                camera4Button.setBackgroundColor(android.graphics.Color.parseColor("#555555"))
                
                // Highlight the active camera button
                val activeButton = when (activeCameraIndex) {
                    1 -> camera1Button
                    2 -> camera2Button
                    3 -> camera3Button
                    4 -> camera4Button
                    else -> null
                }
                
                activeButton?.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
                
                // Update button enabled states based on camera availability and blacklist status
                camera1Button.isEnabled = mCamera1 != null && !isDeviceBlacklisted(1)
                camera2Button.isEnabled = mCamera2 != null && !isDeviceBlacklisted(2)
                camera3Button.isEnabled = mCamera3 != null && !isDeviceBlacklisted(3)
                camera4Button.isEnabled = mCamera4 != null && !isDeviceBlacklisted(4)
                
                // Visually indicate blacklisted cameras
                if (mCamera1 != null && isDeviceBlacklisted(1)) {
                    camera1Button.setBackgroundColor(android.graphics.Color.parseColor("#CC0000")) // Red for failed
                    camera1Button.text = "CAM 1\n(FAILED)"
                } else if (camera1Button.text != "CAM 1") {
                    camera1Button.text = "CAM 1"
                }
            }
        }
    }
    
    private fun switchToAlternativeCamera(failedCameraIndex: Int) {
        try {
            // Add comprehensive safety checks
            if (!isAdded || view == null || isDetached || isRemoving) {
                Log.w("DemoMultiCameraFragment", "⚠️ ALTERNATIVE CAMERA SWITCH CANCELLED - Fragment not ready")
                return
            }
            
            Log.i("DemoMultiCameraFragment", "🔄 SWITCHING FROM FAILED Camera $failedCameraIndex to alternative")
            
            // Find the next available working camera
            val availableCameras = listOf(
                2 to mCamera2,
                3 to mCamera3,
                4 to mCamera4,
                1 to mCamera1
            ).filter { (index, camera) -> 
                index != failedCameraIndex && camera != null && !isDeviceBlacklisted(index)
            }
            
            if (availableCameras.isNotEmpty()) {
                val (alternativeIndex, _) = availableCameras.first()
                Log.i("DemoMultiCameraFragment", "✅ FOUND ALTERNATIVE: Switching to Camera $alternativeIndex")
                
                mainHandler.postDelayed({
                    try {
                        if (isAdded && view != null && !isDetached && !isRemoving) {
                            selectCamera(alternativeIndex)
                        } else {
                            Log.w("DemoMultiCameraFragment", "⚠️ ALTERNATIVE SWITCH CANCELLED - Fragment state changed")
                        }
                    } catch (e: Exception) {
                        Log.e("DemoMultiCameraFragment", "❌ Error during alternative camera selection: ${e.message}")
                    }
                }, 1000)
            } else {
                Log.w("DemoMultiCameraFragment", "⚠️ NO ALTERNATIVE CAMERAS available")
                try {
                    deactivateAllCameras()
                } catch (e: Exception) {
                    Log.e("DemoMultiCameraFragment", "❌ Error deactivating cameras: ${e.message}")
                }
            }
            
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ CRITICAL ERROR in switchToAlternativeCamera: ${e.message}")
            Log.e("DemoMultiCameraFragment", "Exception type: ${e.javaClass.simpleName}")
        }
    }
    
    private fun isDeviceBlacklisted(cameraIndex: Int): Boolean {
        val camera = when (cameraIndex) {
            1 -> mCamera1
            2 -> mCamera2
            3 -> mCamera3
            4 -> mCamera4
            else -> return true
        }
        
        return camera?.let { 
            failedDevices.contains(it.getUsbDevice().deviceId) 
        } ?: true
    }

    private fun updateCameraCountDisplay() {
        if (!::cameraCountText.isInitialized || !::permissionStatusText.isInitialized || 
            !::activeCameraText.isInitialized) return
        
        mainHandler.post {
            // Check if fragment is still alive before updating UI
            if (isAdded && view != null) {
                val connectedCount = connectedCameras.size
                val pendingCount = pendingCameras.size
                
                // Update camera count with cycling mode indication
                val modeText = if (cameraCyclingEnabled && activeCameras.size >= 2) {
                    "🔄 Auto-Cycling (5s): $connectedCount/4 cameras"
                } else {
                    "📱 Manual Mode: $connectedCount/4 cameras"
                }
                cameraCountText.text = modeText
                
                // Update active camera display
                if (currentActiveCameraIndex > 0) {
                    activeCameraText.text = "📹 Active: Camera $currentActiveCameraIndex"
                    activeCameraText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                } else {
                    activeCameraText.text = "📹 Active: None"
                    activeCameraText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                }
                
                // Update permission status
                when {
                    permissionRequestInProgress -> {
                        permissionStatusText.text = "Requesting permissions..."
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                    }
                    pendingCount > 0 -> {
                        permissionStatusText.text = "Pending: $pendingCount cameras"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#FF9800"))
                    }
                    connectedCount == 0 -> {
                        permissionStatusText.text = "No cameras connected"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#F44336"))
                    }
                    connectedCount > 0 -> {
                        permissionStatusText.text = "Ready - Click a camera button to switch"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    }
                    else -> {
                        permissionStatusText.text = "Ready for manual selection"
                        permissionStatusText.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
                    }
                }
                
                // Update camera button states
                updateCameraButtonStates(currentActiveCameraIndex)
            }
        }
    }
    
    private fun reportBandwidthUsage(cameraIndex: Int, currentTime: Long) {
        val totalBytes = frameBytesReceived[cameraIndex] ?: 0L
        val startTime = bandwidthStartTime[cameraIndex] ?: currentTime
        val frameCount = frameCounters[cameraIndex] ?: 0
        
        if (startTime != currentTime && totalBytes > 0) {
            val elapsedSeconds = (currentTime - startTime) / 1000.0
            val avgBytesPerSecond = totalBytes / elapsedSeconds
            val avgKbps = (avgBytesPerSecond * 8) / 1024 // Convert to Kbps
            val avgMbps = avgKbps / 1024 // Convert to Mbps
            val avgFps = frameCount / elapsedSeconds
            
            Log.i("DemoMultiCameraFragment", "📊 BANDWIDTH USAGE - Camera $cameraIndex")
            Log.i("DemoMultiCameraFragment", "Total Data: ${String.format("%.2f", totalBytes / 1024.0 / 1024.0)} MB")
            Log.i("DemoMultiCameraFragment", "Elapsed Time: ${String.format("%.1f", elapsedSeconds)}s")
            Log.i("DemoMultiCameraFragment", "Average Bandwidth: ${String.format("%.2f", avgKbps)} Kbps (${String.format("%.3f", avgMbps)} Mbps)")
            Log.i("DemoMultiCameraFragment", "Average FPS: ${String.format("%.1f", avgFps)}")
            Log.i("DemoMultiCameraFragment", "Total Frames: $frameCount")
            Log.i("DemoMultiCameraFragment", "Average Frame Size: ${String.format("%.1f", totalBytes.toDouble() / frameCount / 1024)} KB")
            
            // Calculate total bandwidth across all cameras
            var totalBandwidth = 0.0
            var activeCameras = 0
            for (camIndex in 1..4) {
                val camBytes = frameBytesReceived[camIndex] ?: 0L
                val camStartTime = bandwidthStartTime[camIndex] ?: 0L
                if (camBytes > 0 && camStartTime > 0) {
                    val camElapsed = (currentTime - camStartTime) / 1000.0
                    val camBandwidth = (camBytes / camElapsed * 8) / 1024 // Kbps
                    totalBandwidth += camBandwidth
                    activeCameras++
                }
            }
            
            if (activeCameras > 1) {
                Log.i("DemoMultiCameraFragment", "🌐 TOTAL USB BANDWIDTH USAGE")
                Log.i("DemoMultiCameraFragment", "Combined Bandwidth: ${String.format("%.2f", totalBandwidth)} Kbps (${String.format("%.3f", totalBandwidth / 1024)} Mbps)")
                Log.i("DemoMultiCameraFragment", "Active Cameras: $activeCameras")
                Log.i("DemoMultiCameraFragment", "Average per Camera: ${String.format("%.2f", totalBandwidth / activeCameras)} Kbps")
                
                // USB 2.0 theoretical limit is ~480 Mbps, practical limit is ~35-40 MB/s = ~280-320 Mbps
                val usbUtilization = (totalBandwidth / 1024) / 280.0 * 100 // Percentage of practical USB 2.0 limit
                Log.i("DemoMultiCameraFragment", "USB 2.0 Utilization: ${String.format("%.1f", usbUtilization)}% of practical limit (~280 Mbps)")
                
                if (usbUtilization > 80) {
                    Log.w("DemoMultiCameraFragment", "⚠️ HIGH USB BANDWIDTH USAGE - May cause frame drops or connection issues")
                } else if (usbUtilization > 60) {
                    Log.w("DemoMultiCameraFragment", "⚠️ MODERATE USB BANDWIDTH USAGE - Monitor for stability")
                }
            }
            
            Log.i("DemoMultiCameraFragment", "═══════════════════════════════════════")
        }
    }

    private var lastProcessTime = mutableMapOf<Int, Long>()
    
    private fun processFrame(data: ByteArray, width: Int, height: Int, cameraIndex: Int) {
        // Skip frame processing if executor is busy to prevent blocking
        if (executor.isShutdown || executor.isTerminated) return
        
        // Aggressive throttle for multi-camera mode to conserve bandwidth
        val currentTime = System.currentTimeMillis()
        val lastTime = lastProcessTime[cameraIndex] ?: 0
        
        // In cycling mode, only one camera is active at a time - no bandwidth conflicts!
        val throttleInterval = if (cameraCyclingEnabled) {
            // Much higher frame rate possible since only one camera is running
            33L  // ~30 FPS - full bandwidth available for single active camera
        } else {
            // Fallback to conservative throttling for non-cycling mode
            val isMultiCameraActive = connectedCameras.size > 1
            when {
                isMultiCameraActive -> 500L  // 2 FPS for multi-camera
                else -> 100L  // 10 FPS for single camera
            }
        }
        
        if (currentTime - lastTime < throttleInterval) return // Skip frames based on mode
        lastProcessTime[cameraIndex] = currentTime
        
        executor.execute {
            try {
                // Convert YUV data to Bitmap (similar to textureView.bitmap in MainActivity)
                val bitmap = yuvToBitmap(data, width, height)
                
                // Get appropriate detector and overlay for this camera
                val (detector, overlayView) = when (cameraIndex) {
                    1 -> objectDetector1 to overlayView1
                    2 -> objectDetector2 to overlayView2
                    3 -> objectDetector3 to overlayView3
                    4 -> objectDetector4 to overlayView4
                    else -> return@execute
                }
                
                // Process frame similar to MainActivity approach:
                // 1. Create TensorImage from bitmap (like MainActivity does with textureView.bitmap)
                // 2. Apply preprocessing with ImageProcessor (resize to 300x300)  
                // 3. Run model inference through detector.detect()
                // 4. Extract results and filter by confidence threshold
                val results = detector.detect(bitmap)
                
                // Log detection results for debugging (similar to MainActivity drawing logic)
                if (results.isNotEmpty() && cameraIndex == currentActiveCameraIndex) {
                    val topResult = results[0]
                    Log.d("ObjectDetection", "📱 Camera $cameraIndex: Detected ${topResult.label} (${String.format("%.2f", topResult.confidence)})")
                }
                
                // Update UI on main thread
                mainHandler.post {
                    try {
                        overlayView.setResults(results)
                        
                        // Speak the first detected label with TTS (only for currently active camera)
                        // Similar to MainActivity's TTS logic: if (label != lastSpoken)
                        if (cameraIndex == currentActiveCameraIndex && results.isNotEmpty()) {
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
                    // ToastUtils.show("Frame processing error for Camera $cameraIndex: ${e.message}") // Removed for clean UI
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
        val device = self.getUsbDevice()
        val deviceName = device.deviceName
        val deviceId = device.deviceId
        val cameraIndex = when (self) {
            mCamera1 -> 1
            mCamera2 -> 2
            mCamera3 -> 3
            mCamera4 -> 4
            else -> 0
        }
        
        Log.i("DemoMultiCameraFragment", "═══════════════════════════════════════")
        Log.i("DemoMultiCameraFragment", "🎬 CAMERA STATE CHANGE")
        Log.i("DemoMultiCameraFragment", "Camera Index: $cameraIndex")
        Log.i("DemoMultiCameraFragment", "Device Name: $deviceName")
        Log.i("DemoMultiCameraFragment", "Device ID: $deviceId")
        Log.i("DemoMultiCameraFragment", "State: $code")
        Log.i("DemoMultiCameraFragment", "Message: ${msg ?: "none"}")
        
        // Cleaned up log message
        // ToastUtils.show("STATE CHANGE: Camera $cameraIndex ($deviceName) // Removed for clean UI -> $code")
        
        when (code) {
            ICameraStateCallBack.State.ERROR -> {
                // Check if device is already blacklisted - if so, ignore all further errors
                if (failedDevices.contains(deviceId)) {
                    Log.w("DemoMultiCameraFragment", "⚠️ IGNORING ERROR from blacklisted Device $deviceId")
                    Log.w("DemoMultiCameraFragment", "This device has been marked as incompatible - no further processing")
                    return
                }
                
                Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex ERROR")
                Log.e("DemoMultiCameraFragment", "Device: $deviceName")
                Log.e("DemoMultiCameraFragment", "Error Message: ${msg ?: "unknown error"}")
                Log.e("DemoMultiCameraFragment", "Preview Status: FAILED - Camera cannot display video")
                
                // Track restart attempts to prevent infinite loops
                val currentAttempts = deviceRestartAttempts.getOrDefault(deviceId, 0)
                deviceRestartAttempts[deviceId] = currentAttempts + 1
                
                Log.w("DemoMultiCameraFragment", "📊 RESTART TRACKING: Device $deviceId attempt ${currentAttempts + 1}/$maxRestartAttempts")
                
                // Check for specific error types
                val errorMessage = msg ?: "unknown error"
                when {
                    errorMessage.contains("Usb control block can not be null", ignoreCase = true) -> {
                        Log.e("DemoMultiCameraFragment", "🔌 USB CONNECTION ERROR - USB control block is null")
                        Log.e("DemoMultiCameraFragment", "This indicates USB connection failure or power issues")
                        Log.e("DemoMultiCameraFragment", "Possible causes:")
                        Log.e("DemoMultiCameraFragment", "1. USB cable disconnected or loose")
                        Log.e("DemoMultiCameraFragment", "2. Insufficient USB power")
                        Log.e("DemoMultiCameraFragment", "3. USB hub issues")
                        Log.e("DemoMultiCameraFragment", "4. Driver/permission problems")
                        
                        // ToastUtils.show("USB ERROR: Camera $cameraIndex - USB connection failed") // Removed for clean UI
                        // ToastUtils.show("CHECK: USB cable, power, and connections") // Removed for clean UI
                        
                        // Don't attempt restart for USB connection errors - they need physical intervention
                        Log.w("DemoMultiCameraFragment", "⚠️ SKIPPING AUTO-RESTART for USB connection error")
                        // ToastUtils.show("MANUAL: Please check USB connections and reconnect camera") // Removed for clean UI
                        failedDevices.add(deviceId)
                        
                        // Remove from connected cameras as it's not usable
                        connectedCameras.remove(self)
                        establishedDevices.remove(deviceId)
                        connectingDevices.remove(deviceId)
                        
                        // Clear camera reference
                        when (cameraIndex) {
                            1 -> mCamera1 = null
                            2 -> mCamera2 = null
                            3 -> mCamera3 = null
                            4 -> mCamera4 = null
                        }
                        
                        updateCameraCountDisplay()
                        return
                    }
                    errorMessage.contains("unsupported", ignoreCase = true) -> {
                        Log.e("DemoMultiCameraFragment", "📷 UNSUPPORTED PREVIEW SIZE ERROR")
                        Log.e("DemoMultiCameraFragment", "Current resolution likely not supported by Device $deviceId")
                        Log.e("DemoMultiCameraFragment", "Will try alternative resolution on restart")
                        // Cleaned up log message
                        // ToastUtils.show("UNSUPPORTED SIZE: Device $deviceId needs different resolution") // Removed for clean UI
                        
                        // For Device 1004, provide specific feedback
                        if (device.vendorId == 13468) {
                            Log.w("DemoMultiCameraFragment", "🔧 Device 1004 RESOLUTION REJECTED - Will try emergency configs")
                            // Cleaned up log message
                            // ToastUtils.show("Device 1004: Resolution rejected - trying emergency mode") // Removed for clean UI
                        }
                        
                        // Check if we should attempt restart
                        if (currentAttempts >= maxRestartAttempts) {
                            Log.e("DemoMultiCameraFragment", "❌ MAXIMUM RESTART ATTEMPTS REACHED for Device $deviceId")
                            Log.e("DemoMultiCameraFragment", "This camera appears to be incompatible with our configurations")
                            Log.e("DemoMultiCameraFragment", "🔄 BLACKLISTING Device $deviceId - will be ignored from now on")
                            // ToastUtils.show("FAILED: Camera $cameraIndex incompatible - stopping attempts") // Removed for clean UI
                            failedDevices.add(deviceId)
                            
                            // If this was the active camera, try to switch to another working camera
                            if (cameraIndex == currentActiveCameraIndex) {
                                switchToAlternativeCamera(cameraIndex)
                            }
                            return
                        } else if (failedDevices.contains(deviceId)) {
                            Log.w("DemoMultiCameraFragment", "⚠️ Device $deviceId already marked as failed - skipping restart")
                            return
                        }
                        
                        // ToastUtils.show("UNSUPPORTED: Camera $cameraIndex - trying emergency configurations") // Removed for clean UI
                    }
                    errorMessage.contains("result=-2", ignoreCase = true) -> {
                        Log.e("DemoMultiCameraFragment", "🔧 NATIVE CONNECTION ERROR - Result code -2")
                        Log.e("DemoMultiCameraFragment", "Hardware-level USB connection failure")
                        Log.e("DemoMultiCameraFragment", "USB interface may be busy, locked, or in use by another process")
                        // Cleaned up log message
                        // ToastUtils.show("NATIVE ERROR: USB interface busy/locked - trying recovery") // Removed for clean UI
                        
                        // Check if we should attempt restart
                        if (currentAttempts >= maxRestartAttempts) {
                            Log.e("DemoMultiCameraFragment", "❌ MAXIMUM RESTART ATTEMPTS REACHED for Device $deviceId")
                            Log.e("DemoMultiCameraFragment", "🔄 BLACKLISTING Device $deviceId - native connection issues")
                            // ToastUtils.show("FAILED: Camera $cameraIndex - native connection issues, stopping attempts") // Removed for clean UI
                            failedDevices.add(deviceId)
                            
                            // If this was the active camera, try to switch to another working camera
                            if (cameraIndex == currentActiveCameraIndex) {
                                switchToAlternativeCamera(cameraIndex)
                            }
                            return
                        } else if (failedDevices.contains(deviceId)) {
                            Log.w("DemoMultiCameraFragment", "⚠️ Device $deviceId already marked as failed - skipping restart")
                            return
                        }
                        
                        // For native connection errors, try USB reset approach
                        tryUsbResetApproach(cameraIndex, deviceId)
                    }
                    errorMessage.contains("result=-99", ignoreCase = true) -> {
                        Log.e("DemoMultiCameraFragment", "🔧 CAMERA OPENING ERROR - Result code -99")
                        Log.e("DemoMultiCameraFragment", "This typically indicates hardware/driver issues")
                        // Cleaned up log message
                        // ToastUtils.show("DRIVER ERROR: Hardware/driver compatibility issue") // Removed for clean UI
                        
                        // Check if we should attempt restart
                        if (currentAttempts >= maxRestartAttempts) {
                            Log.e("DemoMultiCameraFragment", "❌ MAXIMUM RESTART ATTEMPTS REACHED for Device $deviceId")
                            Log.e("DemoMultiCameraFragment", "🔄 BLACKLISTING Device $deviceId - hardware/driver issues")
                            // ToastUtils.show("FAILED: Camera $cameraIndex - hardware issues, stopping attempts") // Removed for clean UI
                            failedDevices.add(deviceId)
                            
                            // If this was the active camera, try to switch to another working camera
                            if (cameraIndex == currentActiveCameraIndex) {
                                // Add safety check to prevent crashes during early initialization
                                try {
                                    if (isAdded && view != null && !isDetached) {
                                        switchToAlternativeCamera(cameraIndex)
                                    } else {
                                        Log.w("DemoMultiCameraFragment", "⚠️ Fragment not ready for camera switching - skipping")
                                    }
                                } catch (e: Exception) {
                                    Log.e("DemoMultiCameraFragment", "❌ Error during camera switch: ${e.message}")
                                }
                            }
                            return
                        } else if (failedDevices.contains(deviceId)) {
                            Log.w("DemoMultiCameraFragment", "⚠️ Device $deviceId already marked as failed - skipping restart")
                            return
                        }
                        
                        // ToastUtils.show("HARDWARE ERROR: Camera $cameraIndex - attempting restart") // Removed for clean UI
                    }
                    else -> {
                        // Extract any error code from the message for better diagnostics
                        val errorCodePattern = Regex("result=(-?\\d+)")
                        val errorCodeMatch = errorCodePattern.find(errorMessage)
                        val errorCode = errorCodeMatch?.groupValues?.get(1) ?: "unknown"
                        
                        Log.e("DemoMultiCameraFragment", "🔧 UNKNOWN CAMERA ERROR")
                        Log.e("DemoMultiCameraFragment", "Error code: $errorCode")
                        Log.e("DemoMultiCameraFragment", "Full message: $errorMessage")
                        // Cleaned up log message
                        // ToastUtils.show("UNKNOWN ERROR: Code $errorCode for Camera $cameraIndex") // Removed for clean UI
                        
                        if (currentAttempts >= maxRestartAttempts || failedDevices.contains(deviceId)) {
                            Log.w("DemoMultiCameraFragment", "⚠️ Too many restart attempts or device failed - skipping restart")
                            failedDevices.add(deviceId)
                            return
                        }
                    }
                }
                
                // Only attempt restart if device is not in failed state and hasn't exceeded max attempts
                if (!failedDevices.contains(deviceId) && currentAttempts < maxRestartAttempts) {
                    val delay = when {
                        errorMessage.contains("result=-2", ignoreCase = true) -> 7000L   // Longest delay for native USB errors
                        errorMessage.contains("result=-99", ignoreCase = true) -> 5000L  // Longer delay for hardware errors
                        errorMessage.contains("unsupported", ignoreCase = true) -> 3000L  // Medium delay for config errors
                        else -> 2000L  // Shorter delay for other errors
                    }
                    
                    Log.i("DemoMultiCameraFragment", "🔄 SCHEDULING RESTART ${currentAttempts + 1}/$maxRestartAttempts for Device $deviceId in ${delay/1000}s")
                    
                    // Add comprehensive safety checks to prevent crashes
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            // Multiple safety checks to prevent crashes during restart
                            if (!isAdded || view == null || isDetached || isRemoving) {
                                Log.w("DemoMultiCameraFragment", "⚠️ RESTART CANCELLED - Fragment lifecycle issue")
                                Log.w("DemoMultiCameraFragment", "isAdded: $isAdded, view: ${view != null}, isDetached: $isDetached, isRemoving: $isRemoving")
                                return@postDelayed
                            }
                            
                            if (failedDevices.contains(deviceId)) {
                                Log.w("DemoMultiCameraFragment", "⚠️ RESTART CANCELLED - Device $deviceId already blacklisted")
                                return@postDelayed
                            }
                            
                            // Check if activity is still alive
                            if (activity == null || requireActivity().isFinishing || requireActivity().isDestroyed) {
                                Log.w("DemoMultiCameraFragment", "⚠️ RESTART CANCELLED - Activity lifecycle issue")
                                return@postDelayed
                            }
                            
                            Log.i("DemoMultiCameraFragment", "🔄 ATTEMPTING CAMERA $cameraIndex RESTART (safety checks passed)")
                            restartCamera(self)
                            
                        } catch (e: Exception) {
                            Log.e("DemoMultiCameraFragment", "❌ CAMERA $cameraIndex RESTART FAILED: ${e.message}")
                            Log.e("DemoMultiCameraFragment", "Exception type: ${e.javaClass.simpleName}")
                            Log.e("DemoMultiCameraFragment", "Adding device $deviceId to failed list to prevent further attempts")
                            failedDevices.add(deviceId)
                        }
                    }, delay)
                } else {
                    Log.w("DemoMultiCameraFragment", "⚠️ RESTART SKIPPED: Device $deviceId failed=${failedDevices.contains(deviceId)}, attempts=$currentAttempts/$maxRestartAttempts")
                    // ToastUtils.show("RESTART SKIPPED: Camera $cameraIndex - too many attempts or device failed") // Removed for clean UI
                }
            }
            ICameraStateCallBack.State.OPENED -> {
                Log.i("DemoMultiCameraFragment", "✅ CAMERA $cameraIndex OPENED SUCCESSFULLY")
                Log.i("DemoMultiCameraFragment", "Device: $deviceName")
                Log.i("DemoMultiCameraFragment", "Preview Status: READY - Video should be visible now")
                Log.i("DemoMultiCameraFragment", "TextureView Slot: Camera $cameraIndex")
                
                // ToastUtils.show("CAMERA OPENED: Camera $cameraIndex - Video should appear now!") // Removed for clean UI
                // ToastUtils.show("SUCCESS: Preview started for Camera $cameraIndex") // Removed for clean UI
                
                // Check if preview is actually showing after a delay
                Handler(Looper.getMainLooper()).postDelayed({
                    checkPreviewStatus(self, cameraIndex)
                }, 3000)
            }
            ICameraStateCallBack.State.CLOSED -> {
                Log.i("DemoMultiCameraFragment", "📤 CAMERA $cameraIndex CLOSED")
                Log.i("DemoMultiCameraFragment", "Device: $deviceName")
                Log.i("DemoMultiCameraFragment", "Preview Status: STOPPED - Video feed ended")
                
                // ToastUtils.show("CAMERA CLOSED: Camera $cameraIndex") // Removed for clean UI
                // ToastUtils.show("STOPPED: Preview ended for Camera $cameraIndex") // Removed for clean UI
                
                // Remove from connected cameras
                connectedCameras.remove(self)
                updateCameraCountDisplay()
            }
        }
        
        Log.i("DemoMultiCameraFragment", "═══════════════════════════════════════")
    }
    
    private fun checkPreviewStatus(camera: MultiCameraClient.ICamera, cameraIndex: Int) {
        if (!isAdded || view == null) return
        
        try {
            val device = camera.getUsbDevice()
            val textureView = when (cameraIndex) {
                1 -> mTextureView1
                2 -> mTextureView2
                3 -> mTextureView3
                4 -> mTextureView4
                else -> null
            }
            
            Log.i("DemoMultiCameraFragment", "🔍 PREVIEW STATUS CHECK - Camera $cameraIndex")
            Log.i("DemoMultiCameraFragment", "Device: ${device.deviceName}")
            Log.i("DemoMultiCameraFragment", "TextureView Available: ${textureView?.isAvailable}")
            Log.i("DemoMultiCameraFragment", "TextureView Size: ${textureView?.width}x${textureView?.height}")
            
            if (textureView?.isAvailable == true && textureView.width > 0 && textureView.height > 0) {
                Log.i("DemoMultiCameraFragment", "✅ PREVIEW STATUS: Camera $cameraIndex is displaying video")
                Log.i("DemoMultiCameraFragment", "Resolution: ${textureView.width}x${textureView.height}")
                // Preview is working correctly
            } else {
                Log.w("DemoMultiCameraFragment", "⚠️ PREVIEW STATUS: Camera $cameraIndex may not be displaying video")
                Log.w("DemoMultiCameraFragment", "TextureView issues detected")
                // Preview may have issues
            }
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ PREVIEW STATUS CHECK FAILED: Camera $cameraIndex - ${e.message}")
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
                else -> {
                    Log.w("DemoMultiCameraFragment", "❌ RESTART FAILED: Camera not found in slot references")
                    return
                }
            }
            
            Log.i("DemoMultiCameraFragment", "🔄 RESTARTING CAMERA $cameraIndex (from error state)")
            
            // Don't remove from connected cameras list - keep the slot assignment
            // Use the specialized restart preview method instead of full setup
            restartCameraPreview(camera, cameraIndex)
            
        } catch (e: Exception) {
            Log.e("DemoMultiCameraFragment", "❌ RESTART FAILED: ${e.message}")
            // ToastUtils.show("Restart failed: ${e.message}") // Removed for clean UI
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
            activeCameraText = mViewBinding.root.findViewById(R.id.active_camera_text)
            noCameraIndicator = mViewBinding.root.findViewById(R.id.no_camera_indicator)
            
            // Initialize camera container views for full screen display
            camera1Container = mViewBinding.root.findViewById(R.id.camera_1_container)
            camera2Container = mViewBinding.root.findViewById(R.id.camera_2_container)
            camera3Container = mViewBinding.root.findViewById(R.id.camera_3_container)
            camera4Container = mViewBinding.root.findViewById(R.id.camera_4_container)
            
            // Initialize camera selection buttons
            camera1Button = mViewBinding.root.findViewById(R.id.camera_1_button)
            camera2Button = mViewBinding.root.findViewById(R.id.camera_2_button)
            camera3Button = mViewBinding.root.findViewById(R.id.camera_3_button)
            camera4Button = mViewBinding.root.findViewById(R.id.camera_4_button)
            
            // Set up camera selection button click listeners
            setupCameraSelectionButtons()
            
            // Initialize camera count display
            updateCameraCountDisplay()
            
            // Initialize ObjectDetectors for 4 cameras
            objectDetector1 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector2 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector3 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            objectDetector4 = ObjectDetectorHelper(requireContext(), threshold = 0.5f)
            
            // Initialize TTS helper
            ttsHelper = TTSHelper(requireContext())
            
            // Start periodic stalled camera checking
            startStalledCameraMonitoring()
            
            // ToastUtils.show("All camera views initialized successfully") // Removed for clean UI
        } catch (e: Exception) {
            // ToastUtils.show("Error initializing views: ${e.message}") // Removed for clean UI
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
    
    private fun resetFailedDevices() {
        Log.i("DemoMultiCameraFragment", "🔄 RESET FAILED DEVICES")
        Log.i("DemoMultiCameraFragment", "Previously failed devices: ${failedDevices.size}")
        Log.i("DemoMultiCameraFragment", "Failed device IDs: ${failedDevices.joinToString()}")
        
        failedDevices.clear()
        deviceRestartAttempts.clear()
        
        // ToastUtils.show("RESET: All device failure states cleared") // Removed for clean UI
        Log.i("DemoMultiCameraFragment", "✅ All device failure states cleared - devices can be tried again")
    }
    
    private fun tryAlternativeResolutionEnforcement(cameraIndex: Int, device: UsbDevice, actualWidth: Int, actualHeight: Int) {
        Log.w("DemoMultiCameraFragment", "🔧 ALTERNATIVE RESOLUTION ENFORCEMENT for Camera $cameraIndex")
        Log.w("DemoMultiCameraFragment", "Device ${device.deviceName} delivered ${actualWidth}x${actualHeight}")
        Log.w("DemoMultiCameraFragment", "Trying multiple enforcement strategies...")
        
        val deviceId = device.deviceId
        val attemptCount = deviceRestartAttempts.getOrDefault(deviceId, 0)
        
        // Strategy 1: Try different format combinations with nano resolutions
        val alternativeConfigs = listOf(
            // Try different format/resolution combinations
            Triple(32, 24, CameraRequest.PreviewFormat.FORMAT_YUYV),   // Ultra-nano YUYV
            Triple(48, 32, CameraRequest.PreviewFormat.FORMAT_YUYV),   // Micro YUYV
            Triple(64, 48, CameraRequest.PreviewFormat.FORMAT_MJPEG),  // Nano MJPEG
            Triple(96, 72, CameraRequest.PreviewFormat.FORMAT_YUYV),   // Small YUYV
            Triple(128, 96, CameraRequest.PreviewFormat.FORMAT_MJPEG), // Tiny MJPEG
            Triple(160, 120, CameraRequest.PreviewFormat.FORMAT_YUYV)  // Last resort YUYV
        )
        
        if (attemptCount < alternativeConfigs.size) {
            val (width, height, format) = alternativeConfigs[attemptCount]
            val formatName = if (format == CameraRequest.PreviewFormat.FORMAT_YUYV) "YUYV" else "MJPEG"
            
            Log.i("DemoMultiCameraFragment", "🔄 ENFORCEMENT ATTEMPT ${attemptCount + 1}: ${width}x${height} $formatName")
            // Cleaned up log message
            // ToastUtils.show("ENFORCEMENT: Trying ${width}x${height} $formatName for Device ${device.deviceName}") // Removed for clean UI
            
            // Schedule restart with alternative configuration
            mainHandler.postDelayed({
                if (isAdded && view != null) {
                    restartCameraWithSpecificConfig(cameraIndex, width, height, format)
                }
            }, 2000)
            
        } else {
            // All alternatives exhausted - blacklist device
            Log.e("DemoMultiCameraFragment", "❌ RESOLUTION ENFORCEMENT FAILED - All alternatives exhausted")
            Log.e("DemoMultiCameraFragment", "Device ${device.deviceName} cannot be controlled - blacklisting")
            // Cleaned up log message
            // ToastUtils.show("ENFORCEMENT FAILED: Device ${device.deviceName} uncontrollable - blacklisted") // Removed for clean UI
            
            failedDevices.add(deviceId)
            deviceRestartAttempts[deviceId] = maxRestartAttempts // Mark as maxed out
        }
    }
    
    private fun tryUsbResetApproach(cameraIndex: Int, deviceId: Int) {
        Log.w("DemoMultiCameraFragment", "🔧 USB RESET APPROACH for Camera $cameraIndex")
        Log.w("DemoMultiCameraFragment", "Attempting to reset USB connection for Device $deviceId")
        // Cleaned up log message
        // ToastUtils.show("USB RESET: Attempting USB interface reset for Device $deviceId") // Removed for clean UI
        
        val camera = when (cameraIndex) {
            1 -> mCamera1
            2 -> mCamera2
            3 -> mCamera3
            4 -> mCamera4
            else -> null
        }
        
        if (camera != null) {
            try {
                // Step 1: Close camera completely
                Log.i("DemoMultiCameraFragment", "🔄 STEP 1: Closing camera for USB reset")
                camera.closeCamera()
                
                // Step 2: Wait longer for USB interface to reset
                mainHandler.postDelayed({
                    if (isAdded && view != null) {
                        Log.i("DemoMultiCameraFragment", "🔄 STEP 2: Attempting camera reconnection after USB reset")
                        
                        // Step 3: Try opening with most conservative settings
                        val textureView = when (cameraIndex) {
                            1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
                            2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
                            3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
                            4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
                            else -> null
                        }
                        
                        if (textureView != null) {
                            try {
                                Log.i("DemoMultiCameraFragment", "🔧 REOPENING with ULTRA-CONSERVATIVE config after USB reset")
                                
                                // Use the most basic configuration possible
                                val resetRequest = CameraRequest.Builder()
                                    .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV) // Most compatible format
                                    .setRenderMode(CameraRequest.RenderMode.NORMAL)
                                    .setAspectRatioShow(false)
                                    .setRawPreviewData(true)
                                    .create() // Let camera choose its own resolution
                                
                                camera.openCamera(textureView, resetRequest)
                                Log.i("DemoMultiCameraFragment", "✅ USB RESET APPROACH: Camera reopened with default settings")
                                
                            } catch (e: Exception) {
                                Log.e("DemoMultiCameraFragment", "❌ USB RESET APPROACH FAILED: ${e.message}")
                                // Cleaned up log message
                                // ToastUtils.show("USB RESET FAILED: ${e.message}") // Removed for clean UI
                                
                                // Mark device as failed after USB reset failure
                                failedDevices.add(deviceId)
                            }
                        }
                    }
                }, 5000) // Wait 5 seconds for USB interface to fully reset
                
            } catch (e: Exception) {
                Log.e("DemoMultiCameraFragment", "❌ USB RESET APPROACH EXCEPTION: ${e.message}")
                failedDevices.add(deviceId)
            }
        }
    }
    
    private fun restartCameraWithSpecificConfig(cameraIndex: Int, width: Int, height: Int, format: CameraRequest.PreviewFormat) {
        val camera = when (cameraIndex) {
            1 -> mCamera1
            2 -> mCamera2
            3 -> mCamera3
            4 -> mCamera4
            else -> null
        }
        
        val textureView = when (cameraIndex) {
            1 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
            2 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_2)
            3 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_3)
            4 -> mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_4)
            else -> null
        }
        
        if (camera != null && textureView != null) {
            val device = camera.getUsbDevice()
            val deviceId = device.deviceId
            
            Log.i("DemoMultiCameraFragment", "🔄 RESTARTING Camera $cameraIndex with ${width}x${height}")
            
            try {
                // Increment restart attempt counter
                deviceRestartAttempts[deviceId] = deviceRestartAttempts.getOrDefault(deviceId, 0) + 1
                
                // Close current camera
                camera.closeCamera()
                
                // Wait then reopen with specific configuration
                mainHandler.postDelayed({
                    if (isAdded && view != null) {
                        try {
                            val formatName = if (format == CameraRequest.PreviewFormat.FORMAT_YUYV) "YUYV" else "MJPEG"
                            Log.i("DemoMultiCameraFragment", "🔧 REOPENING Camera $cameraIndex with FORCED ${width}x${height} $formatName")
                            
                            val specificRequest = CameraRequest.Builder()
                                .setPreviewWidth(width)
                                .setPreviewHeight(height)
                                .setPreviewFormat(format)
                                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                                .setAspectRatioShow(false)
                                .setRawPreviewData(true)
                                .create()
                            
                            camera.openCamera(textureView, specificRequest)
                            
                        } catch (e: Exception) {
                            Log.e("DemoMultiCameraFragment", "❌ SPECIFIC CONFIG RESTART FAILED: ${e.message}")
                        }
                    }
                }, 1500)
                
            } catch (e: Exception) {
                Log.e("DemoMultiCameraFragment", "❌ CAMERA RESTART WITH CONFIG FAILED: ${e.message}")
            }
        }
    }
    
    private fun forceUltraLowBandwidthMode() {
        Log.w("DemoMultiCameraFragment", "🚨 FORCING ULTRA-LOW BANDWIDTH MODE")
        Log.w("DemoMultiCameraFragment", "Device 1004 is consuming too much bandwidth")
        Log.w("DemoMultiCameraFragment", "Attempting to restart with nano resolutions")
        
        // In cycling mode, bandwidth conservation is automatic
        
        // Try to restart Device 1004 with ultra-conservative settings
        val camera1 = mCamera1
        if (camera1 != null) {
            val device = camera1.getUsbDevice()
            if (device.vendorId == 13468) {
                Log.i("DemoMultiCameraFragment", "🔄 RESTARTING Device 1004 with NANO resolutions")
                
                // Close current camera
                try {
                    camera1.closeCamera()
                } catch (e: Exception) {
                    Log.w("DemoMultiCameraFragment", "Error closing camera: ${e.message}")
                }
                
                // Wait then reopen with nano resolution
                mainHandler.postDelayed({
                    if (isAdded && view != null) {
                        try {
                            // Force nano resolution for Device 1004
                            val nanoRequest = CameraRequest.Builder()
                                .setPreviewWidth(64)
                                .setPreviewHeight(48)
                                .setPreviewFormat(CameraRequest.PreviewFormat.FORMAT_YUYV)
                                .setRenderMode(CameraRequest.RenderMode.NORMAL)
                                .setAspectRatioShow(false)
                                .setRawPreviewData(true)
                                .create()
                                
                            val textureView1 = mViewBinding.root.findViewById<AspectRatioTextureView>(R.id.texture_view_1)
                            if (textureView1 != null) {
                                Log.i("DemoMultiCameraFragment", "🔧 REOPENING Device 1004 with FORCED 64x48 NANO resolution")
                                camera1.openCamera(textureView1, nanoRequest)
                            }
                        } catch (e: Exception) {
                            Log.e("DemoMultiCameraFragment", "❌ Failed to restart Device 1004: ${e.message}")
                        }
                    }
                }, 2000)
            }
        }
    }
    
    override fun onDestroyView() {
        // Stop camera cycling
        stopCameraCycling()
        
        // Clear any pending handlers to prevent dead thread errors
        mainHandler.removeCallbacksAndMessages(null)
        
        // Clear pending cameras
        pendingCameras.clear()
        permissionRequestInProgress = false
        pendingPermissionCount = 0
        
        // Clear failure tracking
        failedDevices.clear()
        deviceRestartAttempts.clear()
        
        // Clear cycling data
        activeCameras.clear()
        currentActiveCameraIndex = 0
        
        // Cleanup cameras
        connectedCameras.clear()
        mCamera1 = null
        mCamera2 = null
        mCamera3 = null
        mCamera4 = null
        
        // Cleanup TensorFlow Lite models (like MainActivity onDestroy)
        try {
            if (::objectDetector1.isInitialized) objectDetector1.close()
            if (::objectDetector2.isInitialized) objectDetector2.close()
            if (::objectDetector3.isInitialized) objectDetector3.close()
            if (::objectDetector4.isInitialized) objectDetector4.close()
            Log.i("DemoMultiCameraFragment", "🧹 All TensorFlow Lite models cleaned up")
        } catch (e: Exception) {
            Log.w("DemoMultiCameraFragment", "Warning during model cleanup: ${e.message}")
        }
        
        ttsHelper.shutdown()
        executor.shutdown()
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
        
        // ToastUtils.show("USB Camera attached: $deviceName") // Removed for clean UI
        // ToastUtils.show("Details: VID:$vendorId PID:$productId ID:$deviceId Serial:$serialNumber") // Removed for clean UI
        
        // Check if this might be through a USB hub
        val hubInfo = if (deviceName.contains("hub", ignoreCase = true) || 
                         serialNumber.contains("hub", ignoreCase = true)) {
            "via USB HUB"
        } else {
            "direct connection"
        }
        // ToastUtils.show("Connection type: $hubInfo") // Removed for clean UI
        
        // Don't auto-connect here - let the framework handle it
        // The onCameraConnected method will be called when ready
    }

    protected override fun onCameraDisConnected(camera: MultiCameraClient.ICamera) {
        val device = camera.getUsbDevice()
        val deviceName = device.deviceName
        val deviceId = device.deviceId
        
        Log.i("DemoMultiCameraFragment", "📤 CAMERA DISCONNECTION")
        Log.i("DemoMultiCameraFragment", "Device Name: $deviceName")
        Log.i("DemoMultiCameraFragment", "Device ID: $deviceId")
        Log.i("DemoMultiCameraFragment", "Connected cameras before removal: ${connectedCameras.size}")
        
        // Find camera index before cleanup
        val cameraIndex = when (camera) {
            mCamera1 -> 1
            mCamera2 -> 2
            mCamera3 -> 3
            mCamera4 -> 4
            else -> 0
        }
        
        // Check if this is a restart-related disconnection (temporary)
        val isRestartDisconnection = cameraIndex > 0 && establishedDevices.contains(deviceId)
        
        if (isRestartDisconnection) {
            Log.i("DemoMultiCameraFragment", "🔄 RESTART-RELATED DISCONNECTION - Camera $cameraIndex")
            Log.i("DemoMultiCameraFragment", "Keeping camera slot and connection state for restart")
            
            // Don't remove from established devices or connected cameras during restart
            // Just clear frame tracking temporarily
            frameCounters.remove(cameraIndex)
            lastFrameTime.remove(cameraIndex)
            Log.i("DemoMultiCameraFragment", "🧹 TEMPORARY FRAME TRACKING CLEARED for Camera $cameraIndex restart")
            
            // ToastUtils.show("RESTART: Camera $cameraIndex disconnected for restart") // Removed for clean UI
            return
        }
        
        // This is a real disconnection - full cleanup
        Log.i("DemoMultiCameraFragment", "❌ REAL DISCONNECTION - Camera $cameraIndex")
        
        // Clean up connection state tracking
        connectingDevices.remove(deviceId)
        establishedDevices.remove(deviceId)
        
        // Clean up frame tracking
        if (cameraIndex > 0) {
            frameCounters.remove(cameraIndex)
            lastFrameTime.remove(cameraIndex)
            Log.i("DemoMultiCameraFragment", "🧹 FRAME TRACKING CLEARED for Camera $cameraIndex")
        }
        
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
        Log.i("DemoMultiCameraFragment", "Connection state cleaned - Connecting: ${connectingDevices.size}, Established: ${establishedDevices.size}")
        // ToastUtils.show("Camera disconnected: $deviceName") // Removed for clean UI
        
        // Update camera count display
        updateCameraCountDisplay()
    }

    protected override fun onCameraDetached(camera: MultiCameraClient.ICamera) {
        // ToastUtils.show("Camera detached: ${camera.getUsbDevice() // Removed for clean UI.deviceName}")
    }
}
