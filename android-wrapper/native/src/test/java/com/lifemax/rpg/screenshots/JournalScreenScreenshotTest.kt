package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.journal.GeneratedJournal
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.presentation.JournalActions
import com.lifemax.rpg.feature.journal.presentation.JournalScreen
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel
import org.junit.Rule
import org.junit.Test

/**
 * The whole native Journal on a phone-shaped screen (411 dp by 890 dp), from the page's own frames,
 * as the owner meets it: the lock card, today's page with the strip and the day, and a week's
 * review, in both themes. These are the images to hold beside the page at 412 px.
 */
class JournalScreenScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 2400, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private fun screen(fixture: String, light: Boolean, item: Int = 0) {
        val repo =
            WebJournalRepository().apply {
                accept(
                    GeneratedJournal.json(fixture)
                        .put("light", light)
                        .put("tokens", ScreenshotPalette.tokens(light)),
                    true,
                )
            }
        val vm =
            JournalViewModel(repo, JournalCommands { _, _ -> }).apply {
                opened()
                onProjection()
            }
        paparazzi.snapshot {
            JournalScreen(vm.ui.value, JournalActions(), list = rememberLazyListState(item))
        }
    }

    @Test fun lockedDark() = screen("locked", light = false)

    @Test fun lockedLight() = screen("locked", light = true)

    @Test fun openTodayDark() = screen("today", light = false)

    @Test fun openTodayLight() = screen("today", light = true)

    /** Scrolled past the hero: the strip and the day's page, as a visit usually looks. */
    @Test fun openTodayBodyDark() = screen("today", light = false, item = BODY)

    @Test fun openTodayBodyLight() = screen("today", light = true, item = BODY)

    @Test fun weekReviewDark() = screen("week", light = false)

    @Test fun weekReviewLight() = screen("week", light = true)

    @Test fun weekReviewBodyDark() = screen("week", light = false, item = BODY)

    @Test fun weekReviewBodyLight() = screen("week", light = true, item = BODY)

    private companion object {
        /** The strip, after the switch and the hero. */
        const val BODY = 2
    }
}
