package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.lifemax.rpg.feature.plan.domain.Celebration
import com.lifemax.rpg.feature.plan.domain.PagePoint
import com.lifemax.rpg.feature.plan.domain.XpGain
import com.lifemax.rpg.feature.plan.presentation.PlanFeedback
import com.lifemax.rpg.feature.plan.presentation.components.PlanFeedbackLayer
import org.junit.Rule
import org.junit.Test

/**
 * The XP celebration as Workout plan plays it after a tick, frozen at moments of its run: the ring
 * and burst at the tapped box, the orbs on their way to the logo, their arrival, and the reduced
 * motion burst. Each frame starts the clock at its moment, so the image is that moment's.
 */
class XpCelebrationScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.001)

    private fun frame(
        seconds: Float,
        awards: List<Celebration> = single,
        reduced: Boolean = false,
    ) =
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Backdrop)) {
                PlanFeedbackLayer(
                    PlanFeedback(7, "", awards),
                    Offset(700f, 1500f),
                    PagePoint(32f, 32.5f),
                    0f,
                    reduced,
                    onShown = {},
                    startAt = seconds,
                )
            }
        }

    @Test fun start() = frame(0f)

    @Test fun burst() = frame(0.25f)

    @Test fun flight() = frame(0.65f)

    @Test fun arrival() = frame(1.1f)

    @Test fun twoAwards() = frame(0.4f, awards = double)

    @Test fun reducedMotion() = frame(0.4f, reduced = true)

    private companion object {
        val Backdrop = Color(0xFF0B1020)
        val single = listOf(Celebration(40, listOf(XpGain("strength", "STR", 28, 0xFFFF8A50))))
        val double =
            listOf(
                Celebration(
                    40,
                    listOf(
                        XpGain("strength", "STR", 28, 0xFFFF8A50),
                        XpGain("endurance", "END", 12, 0xFF4FD1C5),
                    ),
                ),
                Celebration(15, listOf(XpGain("discipline", "DIS", 15, 0xFFB28DFF))),
            )
    }
}
