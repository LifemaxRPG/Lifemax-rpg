package com.lifemax.rpg.core.designsystem.feedback

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The page's buzzes for a native screen: the phone's vibrator, played the way the page's
 * `haptic(p)` plays them. [timings] alternate on and off in milliseconds, starting on, as
 * navigator.vibrate takes them; [amplitude] (1 to 255) is the owner's strength, or null for the
 * vibrator's default (a WebView's navigator.vibrate).
 */
fun interface PageBuzz {
    fun buzz(timings: LongArray, amplitude: Int?)
}

/** The phone's vibrator; a screenshot or a test provides none, or one that records. */
val LocalPageBuzz = staticCompositionLocalOf<PageBuzz?> { null }

/**
 * The owner's haptic strength as the page projects it (`haptics`: HAP_LEVELS at the chosen level,
 * and HAP_MAX), so a native buzz is as long and as strong as the page's own. The screen gets none
 * when haptics are off, and then plays nothing.
 */
@Immutable
data class HapticScale(
    val dm: Double,
    val add: Double,
    val amp: Int,
    val min: Long,
    val max: Long = 260,
) {
    /**
     * One on-time, as the page's hapScale turns it: stretched by [dm] and [add], rounded as JS does
     * (half up), never under [min] and never over [max].
     */
    fun on(ms: Long): Long = Math.round(ms * dm + add).coerceAtLeast(min).coerceAtMost(max)

    /** A pattern, as hapScale turns it: the on-times scaled, the pauses between them kept. */
    fun pattern(vararg timings: Long): LongArray =
        LongArray(timings.size) { i -> if (i % 2 == 0) on(timings[i]) else timings[i] }
}

/**
 * Plays [timings] as the page's `haptic(timings)` would at [scale]; nothing when haptics are off
 * (no scale).
 */
fun PageBuzz.play(scale: HapticScale?, vararg timings: Long) {
    if (scale == null || timings.isEmpty()) return
    buzz(scale.pattern(*timings), scale.amp)
}

/**
 * The waveform NavHaptics' `pattern` sends the vibrator for [timings] (NavHapticsPlugin.java): a
 * zero wait first, at most 24 parts each held to 0..600 ms, and [amplitude] (held to 1..255) on
 * every on-part; no amplitudes without one.
 */
fun pageWaveform(timings: LongArray, amplitude: Int?): Pair<LongArray, IntArray?> {
    val parts = timings.take(MAX_PARTS).map { it.coerceIn(0, MAX_PART_MS) }
    val wave = longArrayOf(0) + parts
    val amp = amplitude?.coerceIn(1, 255) ?: return wave to null
    return wave to IntArray(wave.size) { i -> if (i % 2 == 1) amp else 0 }
}

private const val MAX_PARTS = 24
private const val MAX_PART_MS = 600L

/** Buzzes the phone's vibrator as the page's NavHaptics plugin does. */
class VibratorPageBuzz(context: Context) : PageBuzz {
    private val app = context.applicationContext
    private val vibrator: Vibrator? by lazy {
        runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    app.getSystemService(VibratorManager::class.java)?.defaultVibrator
                else @Suppress("DEPRECATION") app.getSystemService(Vibrator::class.java)
            }
            .getOrNull()
    }

    override fun buzz(timings: LongArray, amplitude: Int?) {
        val v = vibrator ?: return
        if (timings.isEmpty()) return
        val (wave, amps) = pageWaveform(timings, amplitude)
        // no vibrator, or no permission: no buzz, as the page's try/catch has it
        runCatching {
            if (!v.hasVibrator()) return
            when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ->
                    @Suppress("DEPRECATION") v.vibrate(wave, -1)
                amps == null -> v.vibrate(VibrationEffect.createWaveform(wave, -1))
                else -> v.vibrate(VibrationEffect.createWaveform(wave, amps, -1))
            }
        }
    }
}
