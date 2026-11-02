package com.lifemax.rpg.screenshots

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.journal.JournalBodyFixtures
import com.lifemax.rpg.feature.journal.JournalCoachFixtures
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.presentation.body.CareNote
import com.lifemax.rpg.feature.journal.presentation.body.InsightCard
import com.lifemax.rpg.feature.journal.presentation.body.WelcomeCard
import com.lifemax.rpg.screenshots.JournalBodyShots.largeText
import com.lifemax.rpg.screenshots.JournalBodyShots.shot
import org.junit.Rule
import org.junit.Test

/**
 * The coach's cards in their states: the welcome card at each tier (the third with its chip
 * claimed) above its insight, an insight with its pattern line, and the care note.
 */
class JournalCoachCardsScreenshotTest {
    @get:Rule val paparazzi = JournalBodyShots.paparazzi()

    private fun cards(day: DayEntry, light: Boolean = false) =
        paparazzi.shot(light) {
            day.care?.let { CareNote(it) }
            day.welcome?.let {
                WelcomeCard(it) {}
                Spacer(Modifier.height(14.dp))
            }
            day.insight?.let { InsightCard(it) }
        }

    @Test fun welcome1Dark() = cards(JournalCoachFixtures.welcome1)

    @Test fun welcome1Light() = cards(JournalCoachFixtures.welcome1, light = true)

    @Test fun welcome2Dark() = cards(JournalCoachFixtures.welcome2)

    @Test fun welcome2Light() = cards(JournalCoachFixtures.welcome2, light = true)

    @Test fun welcome3ClaimedDark() = cards(JournalCoachFixtures.welcome3Claimed)

    @Test fun insightDark() = cards(JournalBodyFixtures.today)

    @Test fun insightLight() = cards(JournalBodyFixtures.today, light = true)

    @Test fun careDark() = cards(JournalCoachFixtures.care)

    @Test fun careLight() = cards(JournalCoachFixtures.care, light = true)

    @Test
    fun largeTextWelcome() {
        paparazzi.largeText()
        cards(JournalCoachFixtures.welcome2)
    }
}
