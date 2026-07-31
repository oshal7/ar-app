package com.arbounce.playground.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.arbounce.playground.R

/** Central place for bounce/launch sound effects and haptic impulses. */
class Feedback(context: Context) {

    private val appContext = context.applicationContext

    private val soundPool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()

    private val bounceId = soundPool.load(appContext, R.raw.bounce, 1)
    private val launchId = soundPool.load(appContext, R.raw.launch, 1)

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun bounce(impactSpeed: Float) {
        val volume = (0.25f + impactSpeed / 6f).coerceIn(0.2f, 1f)
        // Higher impact → slightly lower pitch for a "heavier" thud.
        val rate = (1.25f - (impactSpeed / 12f)).coerceIn(0.8f, 1.3f)
        soundPool.play(bounceId, volume, volume, 1, 0, rate)
        vibrate((6L + (impactSpeed * 4).toLong()).coerceIn(6L, 40L))
    }

    fun launch() {
        soundPool.play(launchId, 0.7f, 0.7f, 1, 0, 1f)
        vibrate(12L)
    }

    private fun vibrate(ms: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(ms)
        }
    }

    fun release() {
        soundPool.release()
    }
}
