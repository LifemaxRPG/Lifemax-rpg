package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.journal.JournalBodyFixtures
import com.lifemax.rpg.feature.journal.JournalCoachFixtures
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyCallbacks
import com.lifemax.rpg.feature.journal.presentation.body.JournalEntryPage
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * A whole day's page on its card, from the page's data: the head and counts, the coach's cards, the
 * reflection box and the timeline, scrolled down in screenfuls; the empty Journal.
 */
class JournalDayPageScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private fun page(day: DayEntry?, light: Boolean = false, scrolledDp: Int = 0) =
        paparazzi.shot(light, scrolledDp) {
            JournalEntryPage(
                day,
                JournalBodyFixtures.empty.takeIf { day == null },
                JournalBodyCallbacks(),
            )
        }

    /** Today: the head, the counts, the insight with its pattern, the top of the box. */
    @Test fun todayDark() = page(JournalBodyFixtures.today)

    @Test fun todayLight() = page(JournalBodyFixtures.today, light = true)

    /** The question with its ↻, the ruled field, the meter and the counter, the first item. */
    @Test fun todayBoxDark() = page(JournalBodyFixtures.today, scrolledDp = 340)

    @Test fun todayBoxLight() = page(JournalBodyFixtures.today, light = true, scrolledDp = 340)

    /** Written and paid: the text on its lines, the full meter, the claimed counter in green. */
    @Test fun writtenDark() = page(JournalBodyFixtures.todayWritten, scrolledDp = 340)

    /** Welcome back with its chips above the insight. */
    @Test fun welcomeDark() = page(JournalCoachFixtures.welcome2)

    /** The care note in place of the coach; the box with its plain label. */
    @Test fun careDark() = page(JournalCoachFixtures.care)

    @Test fun careLight() = page(JournalCoachFixtures.care, light = true)

    /** A past day's timeline under its box. */
    @Test fun pastDayDark() = page(JournalBodyFixtures.pastDay, scrolledDp = 640)

    /** The blank state under a short, unpaid reflection. */
    @Test fun blankDark() = page(JournalBodyFixtures.blankDay, scrolledDp = 340)

    @Test fun emptyDark() = page(null)

    @Test fun emptyLight() = page(null, light = true)

    @Test
    fun largeTextTop() {
        paparazzi.largeText()
        page(JournalBodyFixtures.today)
    }

    @Test
    fun largeTextBox() {
        paparazzi.largeText()
        page(JournalBodyFixtures.today, scrolledDp = 700)
    }
}
