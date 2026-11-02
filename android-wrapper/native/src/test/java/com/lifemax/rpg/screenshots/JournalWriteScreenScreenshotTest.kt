package com.lifemax.rpg.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.core.designsystem.feedback.PagePoint
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.journal.GeneratedJournal
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.domain.JournalCelebration
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.presentation.JournalActions
import com.lifemax.rpg.feature.journal.presentation.JournalCelebrationLayer
import com.lifemax.rpg.feature.journal.presentation.JournalScreen
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import com.lifemax.rpg.feature.journal.presentation.TapSpot
import org.junit.Rule
import org.junit.Test

/**
 * Phase 2 on the whole screen (411 dp by 890 dp, as JournalScreenScreenshotTest): the native
 * Journal of a screen that writes, with words in the draft and the counter counting them, and the
 * XP a final write paid flying from the owner's last touch to the top bar (core PageCelebration).
 */
class JournalWriteScreenScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun screen(fixture: String, light: Boolean, typed: String?, item: Int) {
        val repo =
            WebJournalRepository().apply {
                accept(
                    GeneratedJournal.json(fixture)
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
        val key = ((repo.state.value as JournalFrame.Open).journal.entry!!).key
        val vm =
            JournalViewModel(repo, JournalCommands { _, _ -> }, { _, _ -> {} }).apply {
                opened()
                onProjection()
                if (typed != null) edit(key, typed)
            }
        paparazzi.snapshot {
            JournalScreen(vm.ui.value, JournalActions(), list = rememberLazyListState(item))
        }
    }

    /** Words in today's draft: the box on the screen, scrolled past the hero. */
    @Test fun typingDark() = screen("native-today", false, LONG, BODY)

    @Test fun typingLight() = screen("native-today", true, LONG, BODY)

    /** The week's review, answered on the screen. */
    @Test fun reviewAnswerDark() = screen("native-week", false, "Lights out by eleven.", BODY)

    /** The XP flying from the owner's last touch (low on the screen) to the top bar's logo. */
    @Test fun celebrationFlight() = celebrate(0.6f)

    @Test fun celebrationBurst() = celebrate(0.25f)

    private fun celebrate(seconds: Float) =
        paparazzi.snapshot {
            Box(Modifier.fillMaxSize().background(Color(0xFF0C0E12))) {
                JournalCelebrationLayer(
                    JournalCelebration(
                        1,
                        listOf(XpAward(15, listOf(XpGain("wellbeing", "WLB", 15, 0xFF34D399L)))),
                    ),
                    TapSpot().apply { at = Offset(540f, 1500f) },
                    PagePoint(32f, 32.5f),
                    reduced = false,
                    onFinished = {},
                    startAt = seconds,
                )
            }
        }

    private companion object {
        const val BODY = 2
        const val LONG =
            "Reflecting on the whole day: the morning study block went well and I kept my phone away."
    }
}
