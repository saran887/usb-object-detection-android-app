package com.jiangdg.demo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpokenLabel: String? = null
    private var lastSpokenTime: Long = 0L
    private val cooldownMillis: Long = 3000L

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isReady = true
        }
    }

    fun speak(label: String, confidence: Float) {
        val now = System.currentTimeMillis()
        if (!isReady) return
        if (confidence <= 0.6f) return
        val shouldSpeak = (label != lastSpokenLabel) || (now - lastSpokenTime > cooldownMillis)
        if (shouldSpeak) {
            tts?.speak("$label Under the vehicle", TextToSpeech.QUEUE_FLUSH, null, null)
            lastSpokenLabel = label
            lastSpokenTime = now
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
