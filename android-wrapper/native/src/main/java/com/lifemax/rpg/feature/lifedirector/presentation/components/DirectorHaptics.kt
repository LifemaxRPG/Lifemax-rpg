package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Life Director's buzzes, as the page gives them with navigator.vibrate (logic.js: 6 ms as a held
 * handle passes a stop, 7 ms as a session lands, 10-40-18 or 12 ms at a build's lock). [timings]
 * alternate on and off in milliseconds, starting on, as navigator.vibrate takes them.
 */
fun interface DirectorBuzz {
    fun buzz(vararg timings: Long)
}

/** The phone's vibrator; a screenshot provides one that does nothing. */
val LocalDirectorBuzz = staticCompositionLocalOf<DirectorBuzz?> { null }

/** Buzzes the phone's vibrator at its default strength, as a WebView's navigator.vibrate does. */
class VibratorBuzz(context: Context) : DirectorBuzz {
    private val app = context.applicationContext
    private val vibrator: Vibrator? by lazy {
        runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
            }
            .getOrNull()
    }

    override fun buzz(vararg timings: Long) {
        val v = vibrator ?: return
        if (timings.isEmpty()) return
        val wave = longArrayOf(0) + timings
        // no vibrator, or no permission: no buzz, as the page's try/catch has it
        runCatching {
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                v.vibrate(VibrationEffect.createWaveform(wave, -1))
            else @Suppress("DEPRECATION") v.vibrate(wave, -1)
        }
    }
}
