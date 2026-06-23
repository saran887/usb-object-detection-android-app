package com.jiangdg.demo.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.jiangdg.demo.R
import com.jiangdg.demo.manager.*

class VehicleSafetyService : Service(), CameraManager.CameraFrameCallback {

    inner class LocalBinder : Binder() {
        fun getService(): VehicleSafetyService = this@VehicleSafetyService
    }

    private val binder = LocalBinder()
    
    lateinit var cameraManager: CameraManager
    lateinit var detectionEngine: DetectionEngine
    lateinit var alertManager: AlertManager
    lateinit var watchdog: SystemHealthMonitor

    private val CHANNEL_ID = "VehicleSafetyServiceChannel"
    private var drivingState = DrivingState.FORWARD

    enum class DrivingState { FORWARD, REVERSE, LEFT, RIGHT }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, getNotification())

        alertManager = AlertManager(this)
        detectionEngine = DetectionEngine(this) { results ->
            watchdog.updateInferenceHeartbeat()
            if (results.isNotEmpty()) {
                val primaryResult = results[0]
                alertManager.postAlert("Warning: ${primaryResult.label} ahead", AlertManager.AlertLevel.CRITICAL)
            }
        }

        cameraManager = CameraManager(this, this)
        watchdog = SystemHealthMonitor {
            Log.e("VehicleSafetyService", "Watchdog triggered. Reinitializing modules...")
            cameraManager.release()
            cameraManager.initialize()
            watchdog.resetHeartbeats()
        }

        detectionEngine.start()
        cameraManager.initialize()
        watchdog.start()

        // Default: Start Front Camera (Index 1)
        cameraManager.switchActiveCamera(1)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // Camera Frame Callback
    override fun onFrameCaptured(cameraIndex: Int, data: ByteArray, width: Int, height: Int) {
        watchdog.updateCameraHeartbeat()
        detectionEngine.feedFrame(data, width, height)
    }

    fun updateDrivingState(state: DrivingState) {
        if (drivingState == state) return
        drivingState = state
        val targetIndex = when (state) {
            DrivingState.FORWARD -> 1
            DrivingState.REVERSE -> 2
            DrivingState.LEFT -> 3
            DrivingState.RIGHT -> 4
        }
        cameraManager.switchActiveCamera(targetIndex)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun getNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vehicle Safety System")
            .setContentText("Monitoring USB Cameras & Obstacles...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Vehicle Safety System Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        watchdog.stop()
        cameraManager.release()
        detectionEngine.stop()
        alertManager.shutdown()
        super.onDestroy()
    }
}
