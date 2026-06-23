package com.jiangdg.demo.manager

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale
import java.util.PriorityQueue

class AlertManager(context: Context) : TextToSpeech.OnInitListener {

    enum class AlertLevel { CRITICAL, WARNING, INFO }

    data class AlertItem(
        val message: String,
        val priority: AlertLevel,
        val timestamp: Long = System.currentTimeMillis()
    ) : Comparable<AlertItem> {
        override fun compareTo(other: AlertItem): Int {
            return this.priority.ordinal.compareTo(other.priority.ordinal) // Critical (0) first
        }
    }

    private var tts: TextToSpeech = TextToSpeech(context, this)
    private val alertQueue = PriorityQueue<AlertItem>()
    private var isPlaying = false
    private val alertCooldowns = HashMap<String, Long>()
    private val cooldownDuration = 4000L // 4 seconds cooldown per distinct target warning

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    fun postAlert(message: String, level: AlertLevel) {
        val now = System.currentTimeMillis()
        val lastSent = alertCooldowns.getOrDefault(message, 0L)
        if (now - lastSent < cooldownDuration) return // Skip repeat messages during cooldown
        
        alertCooldowns[message] = now
        synchronized(alertQueue) {
            alertQueue.add(AlertItem(message, level))
            processQueue()
        }
    }

    private fun processQueue() {
        if (isPlaying) return
        val item = synchronized(alertQueue) { alertQueue.poll() } ?: return
        
        isPlaying = true
        // Set higher speech rate for urgent warnings
        tts.setSpeechRate(if (item.priority == AlertLevel.CRITICAL) 1.4f else 1.0f)
        
        tts.speak(item.message, TextToSpeech.QUEUE_FLUSH, null, "AlertID")
        
        // Simple polling wait for TTS completion
        Thread {
            while (tts.isSpeaking) {
                Thread.sleep(100)
            }
            isPlaying = false
            processQueue()
        }.start()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
