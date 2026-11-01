package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.feedback.HeldCelebration
import com.lifemax.rpg.core.designsystem.feedback.PageCelebrations
import com.lifemax.rpg.core.designsystem.feedback.PagePoint
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import org.junit.Rule
import org.junit.Test

/**
 * The shared page celebration on its own, as the Overview cards draw what the page's fxSink hands
 * them: Native Log's held orbs from the bottom centre (the page's lastTap after a Log save) to the
 * logo, a health sync's award with no tap (from the middle), and the reduced-motion burst. On the
 * owner's 411 dp phone.
 */
class PageCelebrationScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2340, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun frame(held: HeldCelebration, seconds: Float, reduced: Boolean = false) =
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Backdrop)) {
                PageCelebrations(listOf(held), Logo, reduced, visible = true, startAt = seconds)
            }
        }

    @Test fun logBurst() = frame(log, 0.25f)

    @Test fun logFlight() = frame(log, 0.6f)

    @Test fun syncFlight() = frame(sync, 0.6f)

    @Test fun logReducedMotion() = frame(log, 0.4f, reduced = true)

    @Test
    fun hiddenDrawsNothing() =
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Backdrop)) {
                PageCelebrations(listOf(log), Logo, false, visible = false, startAt = 0.6f)
            }
        }

    private companion object {
        val Backdrop = Color(0xFF0C0E12)

        /** The top bar's logo, as the kit sends `celebrationTarget`. */
        val Logo = PagePoint(32f, 32.5f)

        val log =
            HeldCelebration(
                11,
                46,
                PagePoint(205.5f, 820f),
                listOf(
                    XpGain("strength", "STR", 30, 0xFFFF8A50),
                    XpGain("vitality", "VIT", 16, 0xFF3DD68C),
                ),
            )

        val sync = HeldCelebration(12, 24, null, listOf(XpGain("vitality", "VIT", 24, 0xFF3DD68C)))
    }
}
