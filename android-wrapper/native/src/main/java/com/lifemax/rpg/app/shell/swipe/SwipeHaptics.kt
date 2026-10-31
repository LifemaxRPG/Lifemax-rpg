package com.lifemax.rpg.app.shell.swipe

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

/** What the phone plays for a swipe haptic. */
sealed interface SwipeBuzz {
    /** `View.performHapticFeedback` with this constant. */
    data class Feedback(val constant: Int) : SwipeBuzz

    /** The predefined heavy click, as a touch vibration. */
    data object HeavyClick : SwipeBuzz
}

/**
 * The swipe's haptics (plan §2, haptics table), played on the source screen's View. Every one
 * honours the system's touch feedback setting: no FLAG_IGNORE_GLOBAL_SETTING, and the heavy click
 * vibrates as touch feedback. The model has already silenced what the page's haptics and the
 * `haptic` tune key turn off.
 */
object SwipeHaptics {
    fun play(view: View, haptic: SwipeEffect.Haptic) {
        when (val buzz = buzz(haptic.moment, haptic.level, haptic.style, Build.VERSION.SDK_INT)) {
            is SwipeBuzz.Feedback -> view.performHapticFeedback(buzz.constant)
            SwipeBuzz.HeavyClick -> heavyClick(view)
            null -> Unit
        }
    }

    /**
     * What [moment] plays at the page's [level], with the `haptic` tune key's [style], on API
     * [sdk]. A refusal plays REJECT (API 30+) whatever the style. The catch (a latch, or a fling
     * commit that never latched) plays by style: `tick` and `click` that kind at every level, so
     * the owner can compare them on the phone, `threshold` the plan's table at the page's level
     * ([table]). Letting go of the catch plays only in the table.
     */
    fun buzz(moment: HapticMoment, level: HapticLevel, style: HapticStyle, sdk: Int): SwipeBuzz? {
        if (level == HapticLevel.Off) return null
        if (moment == HapticMoment.Refused)
            return feedback(HapticFeedbackConstants.REJECT).takeIf { sdk >= Build.VERSION_CODES.R }
        val catch = moment != HapticMoment.Unlatch
        return when (style) {
            HapticStyle.Off -> null
            HapticStyle.Tick -> feedback(HapticFeedbackConstants.CLOCK_TICK).takeIf { catch }
            HapticStyle.Click -> feedback(HapticFeedbackConstants.CONTEXT_CLICK).takeIf { catch }
            HapticStyle.Threshold -> table(catch, level, sdk)
        }
    }

    /**
     * Light is a tick at the catch. Normal is GESTURE_THRESHOLD_ACTIVATE at the catch and
     * DEACTIVATE as it lets go on API 34+, a tick at the catch below. Strong and max are the heavy
     * click at the catch (API 29+) and let go as normal does.
     */
    private fun table(catch: Boolean, level: HapticLevel, sdk: Int): SwipeBuzz? {
        val threshold = sdk >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        return when {
            level == HapticLevel.Light ->
                feedback(HapticFeedbackConstants.CLOCK_TICK).takeIf { catch }
            !catch ->
                feedback(HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE).takeIf { threshold }
            level != HapticLevel.Normal && sdk >= Build.VERSION_CODES.Q -> SwipeBuzz.HeavyClick
            threshold -> feedback(HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE)
            else -> feedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun feedback(constant: Int) = SwipeBuzz.Feedback(constant)

    /** EFFECT_HEAVY_CLICK, played as touch feedback so the system's setting and strength apply. */
    private fun heavyClick(view: View) {
        val context = view.context.applicationContext
        // no vibrator, or no permission: no buzz
        runCatching {
            val vibrator =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
            if (vibrator == null || !vibrator.hasVibrator()) return
            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH),
                )
            else
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .build(),
                )
        }
    }
}
