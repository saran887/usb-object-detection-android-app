package com.jiangdg.demo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpokenLabel: String? = null
    private var lastSpokenTime: Long = 0L
    private val cooldownMillis: Long = 1500L  // 1.5s cooldown between same-label speech

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
            android.util.Log.i("TTSHelper", "TTS initialization started")
        } catch (e: Exception) {
            android.util.Log.e("TTSHelper", "Failed to initialize TTS: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                android.util.Log.e("TTSHelper", "Language not supported")
            } else {
                isReady = true
                // Set speech rate slightly faster for quicker announcements
                tts?.setSpeechRate(1.1f)
                android.util.Log.i("TTSHelper", "✅ TTS ready")
            }
        } else {
            android.util.Log.e("TTSHelper", "TTS initialization failed with status: $status")
        }
    }

    /**
     * Speak the detected label. Only requires label and confidence.
     * No position filtering — speaks for any detected object.
     */
    fun speak(label: String, confidence: Float) {
        try {
            if (!isReady) {
                android.util.Log.w("TTSHelper", "⚠️ TTS not ready yet")
                return
            }
            if (confidence < 0.45f) {
                return
            }

            val now = System.currentTimeMillis()
            // Speak if: different label OR cooldown expired
            val shouldSpeak = (label != lastSpokenLabel) || (now - lastSpokenTime > cooldownMillis)
            if (shouldSpeak) {
                val message = "$label under the vehicle"
                android.util.Log.i("TTSHelper", "🔊 Speaking: $message (${(confidence * 100).toInt()}%)")
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "detection")
                lastSpokenLabel = label
                lastSpokenTime = now
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSHelper", "❌ Error during speak: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
