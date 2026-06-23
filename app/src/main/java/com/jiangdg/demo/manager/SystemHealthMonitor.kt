package com.jiangdg.demo.manager

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

class SystemHealthMonitor(private val recoveryAction: () -> Unit) {
    private val cameraHeartbeat = AtomicLong(System.currentTimeMillis())
    private val inferenceHeartbeat = AtomicLong(System.currentTimeMillis())
    private var isMonitoring = false
    private val timeoutLimit = 4000L // 4 seconds timeout limit

    private val monitorThread = Thread {
        while (isMonitoring) {
            val now = System.currentTimeMillis()
            if (now - cameraHeartbeat.get() > timeoutLimit || now - inferenceHeartbeat.get() > timeoutLimit) {
                Log.e("SystemHealthMonitor", "🚨 Thread Heartbeat Timeout registered! Executing soft-recovery routine...")
                try {
                    recoveryAction()
                } catch (e: Exception) {
                    Log.e("SystemHealthMonitor", "Error executing recoveryAction: ${e.message}")
                }
                resetHeartbeats()
            }
            try {
                Thread.sleep(2000)
            } catch (e: InterruptedException) {
                break
            }
        }
    }

    fun start() {
        isMonitoring = true
        resetHeartbeats()
        if (!monitorThread.isAlive) {
            monitorThread.start()
        }
    }

    fun updateCameraHeartbeat() {
        cameraHeartbeat.set(System.currentTimeMillis())
    }

    fun updateInferenceHeartbeat() {
        inferenceHeartbeat.set(System.currentTimeMillis())
    }

    fun resetHeartbeats() {
        val now = System.currentTimeMillis()
        cameraHeartbeat.set(now)
        inferenceHeartbeat.set(now)
    }

    fun stop() {
        isMonitoring = false
        try {
            monitorThread.interrupt()
        } catch (e: Exception) {}
    }
}
