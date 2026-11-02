package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalEmpty
import com.lifemax.rpg.feature.journal.domain.JournalListItem

/**
 * The Journal body's days and strip for the screenshots and tests: the page's own frames
 * (GeneratedJournal), a name for each situation the pictures show. Nothing here is typed by hand
 * except the one variant, [todayWritten], which is the same day with its reflection claimed.
 */
object JournalBodyFixtures {
    /** The strip of a lived-in six weeks: today, reviews, quiet pills and older days. */
    val tiles: List<JournalListItem> = GeneratedJournal.open("today").list.items

    /** Today: a plan session, the insight with its pattern, a written reflection that can pay. */
    val today: DayEntry = GeneratedJournal.day("today")

    /** Today after the reflection paid: the full meter and the claimed counter. */
    val todayWritten: DayEntry = today.copy(reflect = today.reflect.copy(awarded = true))

    /** A past day with several activities. */
    val pastDay: DayEntry = GeneratedJournal.day("pastday")

    /** A day with nothing logged, under a short reflection. */
    val blankDay: DayEntry = GeneratedJournal.day("blank")

    /** A day with workout notes but no plan session. */
    val workoutDay: DayEntry = GeneratedJournal.day("notes")

    /** The empty Journal's message. */
    val empty: JournalEmpty = GeneratedJournal.open("empty").empty!!
}
