package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import com.lifemax.rpg.feature.journal.JournalBodyFixtures
import com.lifemax.rpg.feature.journal.presentation.body.JournalTiles
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * The Journal's tile strip from the page's own list (JournalBodyFixtures): today, a weekly review,
 * a quiet pill and older days, with today or a review selected, and scrolled along.
 */
class JournalTilesScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private fun snap(selected: String?, light: Boolean = false, first: Int = 0) =
        paparazzi.shot(light) {
            JournalTiles(
                JournalBodyFixtures.tiles,
                selected,
                {},
                state = rememberLazyListState(first),
            )
        }

    @Test fun todayDark() = snap("2026-10-28")

    @Test fun todayLight() = snap("2026-10-28", light = true)

    /** The week's review selected: amber border and glow, no lift. */
    @Test fun reviewDark() = snap("W:2026-W43")

    @Test fun reviewLight() = snap("W:2026-W43", light = true)

    /** Scrolled to the quiet pill: an older day with a ✎ selected beside the next review. */
    @Test fun olderDark() = snap("2026-10-17", first = 4)

    @Test fun olderLight() = snap("2026-10-17", light = true, first = 4)

    /** Nothing selected yet (the list before its first answer). */
    @Test fun noneDark() = snap(null)

    @Test
    fun largeText() {
        paparazzi.largeText()
        snap("2026-10-28")
    }
}
