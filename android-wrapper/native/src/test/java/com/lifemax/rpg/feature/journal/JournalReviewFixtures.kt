package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.ReviewEntry

/** The reviews, from the page's own frames. */
object JournalReviewFixtures {
    /** Week 43: seven days, answered and paid. */
    val week: ReviewEntry = GeneratedJournal.review("week")

    /** The same week before it was answered: an empty box and the unpaid counter. */
    val weekOpen: ReviewEntry =
        week.copy(answer = "", paid = false, counter = "Write 50+ characters for +30 XP")

    /** September, with its heat grid, not yet answered. */
    val month: ReviewEntry = GeneratedJournal.review("month")
}
