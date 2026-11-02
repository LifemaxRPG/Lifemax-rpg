package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.journal.JournalReviewFixtures
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyCallbacks
import com.lifemax.rpg.feature.journal.presentation.body.JournalEntryPage
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * The weekly and monthly reviews on the entry's card: the badge head, the week's bars or the
 * month's heat grid, the numbers, the coach's card and the answer box.
 */
class JournalReviewsScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private fun page(review: ReviewEntry, light: Boolean = false, scrolledDp: Int = 0) =
        paparazzi.shot(light, scrolledDp) { JournalEntryPage(review, null, JournalBodyCallbacks()) }

    @Test fun weekDark() = page(JournalReviewFixtures.week)

    @Test fun weekLight() = page(JournalReviewFixtures.week, light = true)

    /** The coach's card and the answer box. */
    @Test fun weekAnswerDark() = page(JournalReviewFixtures.week, scrolledDp = 330)

    /** Three days and four empty bars; answered and paid. */
    @Test fun weekPaidDark() = page(JournalReviewFixtures.weekPaid)

    @Test fun weekPaidAnswerDark() = page(JournalReviewFixtures.weekPaid, scrolledDp = 330)

    @Test fun monthDark() = page(JournalReviewFixtures.month)

    @Test fun monthLight() = page(JournalReviewFixtures.month, light = true)

    /** The numbers, three rows of two, and the coach's card. */
    @Test fun monthGridDark() = page(JournalReviewFixtures.month, scrolledDp = 360)

    @Test fun monthGridLight() = page(JournalReviewFixtures.month, light = true, scrolledDp = 360)

    @Test
    fun largeTextWeek() {
        paparazzi.largeText()
        page(JournalReviewFixtures.week)
    }

    @Test
    fun largeTextMonth() {
        paparazzi.largeText()
        page(JournalReviewFixtures.month, scrolledDp = 400)
    }
}
