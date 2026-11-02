package com.lifemax.rpg.feature.journal.presentation

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.journal.domain.OpenJournal

/**
 * The Journal's body under the hero: the tile strip, then the selected day (its head, meta, care
 * note, welcome card, insight and pattern, reflection box, timeline, workout notes, empty states)
 * or review (bars or heat grid, grid, coach card, answer box), or the empty Journal's message.
 *
 * Screen B draws it. This is its slot: items of the screen's list, after the switch, the tip and
 * the hero, keyed so the list keeps its place across frames. [selection] is the highlighted key
 * (the tapped tile at once, then the page's); [actions] holds every tap the body can make (select,
 * write, swap, claim, catch-up, Life Director). The strip carries `Modifier.noTabSwipe()`. Until
 * Screen B lands it draws nothing.
 */
@Suppress("FunctionName", "UNUSED_PARAMETER")
internal fun LazyListScope.JournalBodySlot(
    journal: OpenJournal,
    selection: String?,
    actions: JournalActions,
) {
    item("body") { Spacer(Modifier.fillMaxWidth().height(0.dp).testTag("journal-body-slot")) }
}
