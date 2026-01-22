package com.jiangdg.demo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TTSHelper(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var lastSpokenLabel: String? = null
    private var lastSpokenTime: Long = 0L
    private val cooldownMillis: Long = 2000L  // Reduced from 3000 to 2000

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
                android.util.Log.i("TTSHelper", "✅ TTS ready and initialized successfully")
            }
        } else {
            android.util.Log.e("TTSHelper", "TTS initialization failed with status: $status")
        }
    }

    fun speak(label: String, confidence: Float, yPosition: Float = 0.5f) {
        try {
            val now = System.currentTimeMillis()
            if (!isReady) {
                android.util.Log.w("TTSHelper", "⚠️ TTS not ready yet")
                return
            }
            if (confidence < 0.5f) {
                android.util.Log.d("TTSHelper", "⏭️ Skipped: confidence too low ($label ${(confidence * 100).toInt()}%)")
                return
            }
            
            android.util.Log.d("TTSHelper", "🎯 Detection: $label at Y: ${(yPosition * 100).toInt()}%, confidence: ${(confidence * 100).toInt()}%")
            
            // Only speak for objects in bottom part of frame (underneath)
            // yPosition > 0.65 means bottom 35% of frame
            if (yPosition <= 0.65f) {
                android.util.Log.d("TTSHelper", "⏭️ Skipped: not in bottom area ($label Y: ${(yPosition * 100).toInt()}% needs >65%)")
                return  // Skip if not in bottom area
            }
            
            val shouldSpeak = (label != lastSpokenLabel) || (now - lastSpokenTime > cooldownMillis)
            if (shouldSpeak) {
                val confidencePercent = (confidence * 100).toInt()
                val message = "$label under the vehicle"
                
                android.util.Log.i("TTSHelper", "🔊 Speaking: $message ($confidencePercent%, Y: ${(yPosition * 100).toInt()}%)")
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "detection")
                lastSpokenLabel = label
                lastSpokenTime = now
            } else {
                android.util.Log.d("TTSHelper", "⏭️ Skipped: cooldown active ($label)")
            }
        } catch (e: Exception) {
            android.util.Log.e("TTSHelper", "❌ Error during speak: ${e.message}")
        }
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
