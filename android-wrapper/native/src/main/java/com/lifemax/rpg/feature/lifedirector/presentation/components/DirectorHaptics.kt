package com.lifemax.rpg.feature.lifedirector.presentation.components

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import com.lifemax.rpg.core.designsystem.feedback.VibratorPageBuzz

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

/**
 * Buzzes the phone's vibrator at its default strength, as a WebView's navigator.vibrate does,
 * through the core vibrator every native screen shares ([VibratorPageBuzz]).
 */
class VibratorBuzz(context: Context) : DirectorBuzz {
    private val page = VibratorPageBuzz(context)

    override fun buzz(vararg timings: Long) = page.buzz(timings, null)
}
