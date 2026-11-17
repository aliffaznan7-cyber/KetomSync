package com.example.data.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HapticFeedbackHelper(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun pulse(enabled: Boolean, strength: Float, durationMs: Int, multiplier: Float = 1.0f) {
        if (!enabled) return
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        val safeDuration = durationMs.coerceIn(1, 50).toLong()
        val rawAmplitude = (strength * multiplier * 255f).toInt().coerceIn(1, 255)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amplitude = if (vib.hasAmplitudeControl()) rawAmplitude else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(safeDuration, amplitude))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(safeDuration)
            }
        } catch (_: SecurityException) {
            // Vibrate permission guard
        }
    }
}
