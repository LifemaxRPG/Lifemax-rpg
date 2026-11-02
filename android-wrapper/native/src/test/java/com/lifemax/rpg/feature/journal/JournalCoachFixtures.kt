package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.DayEntry

/** The coach's states, from the page's own frames: the care note and the three welcomes. */
object JournalCoachFixtures {
    val care: DayEntry = GeneratedJournal.day("care")
    val welcome1: DayEntry = GeneratedJournal.day("welcome1")
    val welcome1Claimed: DayEntry = GeneratedJournal.day("welcome1-claimed")
    val welcome2: DayEntry = GeneratedJournal.day("welcome2")
    val welcome3: DayEntry = GeneratedJournal.day("welcome3")
}
