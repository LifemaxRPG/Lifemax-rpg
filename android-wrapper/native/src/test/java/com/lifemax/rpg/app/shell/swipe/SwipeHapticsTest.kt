package com.lifemax.rpg.app.shell.swipe

import android.view.HapticFeedbackConstants.CLOCK_TICK
import android.view.HapticFeedbackConstants.CONTEXT_CLICK
import android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
import android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
import android.view.HapticFeedbackConstants.REJECT
import com.lifemax.rpg.app.shell.swipe.HapticMoment.Fling
import com.lifemax.rpg.app.shell.swipe.HapticMoment.Latch
import com.lifemax.rpg.app.shell.swipe.HapticMoment.Refused
import com.lifemax.rpg.app.shell.swipe.HapticMoment.Unlatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The haptics table of plan §2, at each page level, tune style and API level. */
class SwipeHapticsTest {
    private fun buzz(
        moment: HapticMoment,
        level: HapticLevel = HapticLevel.Normal,
        style: HapticStyle = HapticStyle.Threshold,
        sdk: Int = 35,
    ) = SwipeHaptics.buzz(moment, level, style, sdk)

    private fun feedback(constant: Int) = SwipeBuzz.Feedback(constant)

    @Test
    fun normalPlaysTheGestureThresholdsOnApi34AndUp() {
        assertEquals(feedback(GESTURE_THRESHOLD_ACTIVATE), buzz(Latch, sdk = 34))
        assertEquals(feedback(GESTURE_THRESHOLD_ACTIVATE), buzz(Fling))
        assertEquals(feedback(GESTURE_THRESHOLD_DEACTIVATE), buzz(Unlatch))
        assertEquals(feedback(REJECT), buzz(Refused))
    }

    @Test
    fun belowApi34TheCatchTicksAndLettingGoIsSilent() {
        assertEquals(feedback(CLOCK_TICK), buzz(Latch, sdk = 33))
        assertEquals(feedback(CLOCK_TICK), buzz(Fling, sdk = 26))
        assertNull(buzz(Unlatch, sdk = 33))
    }

    @Test
    fun aRefusalRejectsFromApi30AtEveryLevelAndStyle() {
        for (level in HapticLevel.entries - HapticLevel.Off) for (style in HapticStyle.entries) {
            assertEquals(feedback(REJECT), buzz(Refused, level, style, sdk = 30))
            assertNull(buzz(Refused, level, style, sdk = 29))
        }
    }

    @Test
    fun lightTicksAtTheCatchOnly() {
        assertEquals(feedback(CLOCK_TICK), buzz(Latch, HapticLevel.Light))
        assertEquals(feedback(CLOCK_TICK), buzz(Fling, HapticLevel.Light, sdk = 26))
        assertNull(buzz(Unlatch, HapticLevel.Light))
    }

    @Test
    fun strongAndMaxClickHeavilyAtTheCatchFromApi29() {
        for (level in listOf(HapticLevel.Strong, HapticLevel.Max)) {
            assertEquals(SwipeBuzz.HeavyClick, buzz(Latch, level, sdk = 29))
            assertEquals(SwipeBuzz.HeavyClick, buzz(Fling, level))
            assertEquals(feedback(GESTURE_THRESHOLD_DEACTIVATE), buzz(Unlatch, level))
            assertEquals(feedback(CLOCK_TICK), buzz(Latch, level, sdk = 28))
        }
    }

    @Test
    fun tickAndClickPlayTheirKindAtTheCatchAtEveryLevel() {
        for (level in HapticLevel.entries - HapticLevel.Off) {
            assertEquals(feedback(CLOCK_TICK), buzz(Latch, level, HapticStyle.Tick))
            assertEquals(feedback(CONTEXT_CLICK), buzz(Fling, level, HapticStyle.Click, sdk = 26))
            assertNull(buzz(Unlatch, level, HapticStyle.Tick))
            assertNull(buzz(Unlatch, level, HapticStyle.Click))
        }
    }

    @Test
    fun theOffStyleAndThePageHapticsOffAreSilent() {
        for (moment in listOf(Latch, Unlatch, Fling)) assertNull(
            buzz(moment, style = HapticStyle.Off)
        )
        for (moment in HapticMoment.entries) assertNull(buzz(moment, HapticLevel.Off))
    }
}
