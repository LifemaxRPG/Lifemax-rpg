package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.journal.JournalBodyFixtures
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.presentation.body.BlankDay
import com.lifemax.rpg.feature.journal.presentation.body.Timeline
import com.lifemax.rpg.feature.journal.presentation.body.WorkoutNotesCard
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * A day's timeline on its own: times, glowing nodes, icon cards with pills, notes and plan notes;
 * the workout notes card; the blank state.
 */
class JournalTimelineScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private fun body(day: DayEntry, light: Boolean = false) =
        paparazzi.shot(light) {
            day.blank?.let { BlankDay(it) }
            if (day.timeline.isNotEmpty()) Timeline(day.timeline)
            day.workoutNotes?.let { WorkoutNotesCard(it) }
        }

    /** Today: one plan session with its notes and plan notes. */
    @Test fun planSessionDark() = body(JournalBodyFixtures.today)

    @Test fun planSessionLight() = body(JournalBodyFixtures.today, light = true)

    /** A busy day: four activities, one with a skill. */
    @Test fun busyDayDark() = body(JournalBodyFixtures.pastDay)

    @Test fun busyDayLight() = body(JournalBodyFixtures.pastDay, light = true)

    /** Four activities and the workout notes card under them. */
    @Test fun workoutNotesDark() = body(JournalBodyFixtures.workoutDay)

    @Test fun blankDark() = body(JournalBodyFixtures.blankDay)

    @Test fun blankLight() = body(JournalBodyFixtures.blankDay, light = true)

    @Test
    fun largeText() {
        paparazzi.largeText()
        body(JournalBodyFixtures.pastDay)
    }
}
