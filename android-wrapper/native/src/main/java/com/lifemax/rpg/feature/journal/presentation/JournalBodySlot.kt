package com.lifemax.rpg.feature.journal.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.OpenJournal
import com.lifemax.rpg.feature.journal.presentation.body.ARRIVAL_TOTAL_MS
import com.lifemax.rpg.feature.journal.presentation.body.ChipTap
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyCallbacks
import com.lifemax.rpg.feature.journal.presentation.body.JournalEntryPage
import com.lifemax.rpg.feature.journal.presentation.body.JournalTiles
import com.lifemax.rpg.feature.journal.presentation.body.LocalEntryClock
import com.lifemax.rpg.feature.journal.presentation.body.selectedIndex

/**
 * The Journal's body under the hero: the tile strip, then the selected day (its head, meta, care
 * note, welcome card, insight and pattern, reflection box, timeline, workout notes, empty states)
 * or review (bars or heat grid, grid, coach card, answer box), or the empty Journal's message.
 *
 * Items of the screen's list, after the switch, the tip and the hero, keyed so the list keeps its
 * place across frames. [selection] is the highlighted key (the tapped tile at once, then the
 * page's); [actions] holds every tap the body can make. The strip is never a tab swipe, and keeps
 * the selected tile in view; the entry plays the page's arrival when a new one is selected.
 */
@Suppress("FunctionName")
internal fun LazyListScope.JournalBodySlot(
    journal: OpenJournal,
    selection: String?,
    actions: JournalActions,
) {
    if (journal.list.items.isNotEmpty()) {
        item("tiles") {
            val strip = rememberLazyListState()
            val index = selectedIndex(journal.list.items, selection)
            // the first time the strip is placed, at once; after that, a tile that was out of view
            // slides in. A tap on a visible tile moves nothing.
            val placed = remember { booleanArrayOf(false) }
            LaunchedEffect(selection, journal.list.rev) {
                if (index < 0) return@LaunchedEffect
                if (!placed[0]) {
                    placed[0] = true
                    strip.scrollToItem(index)
                } else if (strip.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    strip.animateScrollToItem(index)
                }
            }
            JournalTiles(journal.list.items, selection, actions.select, state = strip)
        }
    }
    item("gap") { Spacer(Modifier.height(4.dp)) }
    item("entry") {
        val entry = journal.entry
        val callbacks =
            remember(actions) {
                JournalBodyCallbacks(
                    onWrite = actions.write,
                    onSwap = actions.swap,
                    onChip = { actions.page(it.toAction()) },
                )
            }
        // every new entry arrives, drawn at its start on the frame it is first seen; the first one
        // drawn does not, nor does one with the page's animations off
        val seen = remember { arrayOfNulls<String>(1) }
        val arrives = journal.motion && seen[0] != null && seen[0] != entry?.key
        val clock =
            remember(entry?.key) { Animatable(if (arrives) 0f else ARRIVAL_TOTAL_MS.toFloat()) }
        LaunchedEffect(entry?.key) {
            seen[0] = entry?.key
            if (arrives)
                clock.animateTo(
                    ARRIVAL_TOTAL_MS.toFloat(),
                    tween(ARRIVAL_TOTAL_MS, easing = LinearEasing),
                )
        }
        CompositionLocalProvider(LocalEntryClock provides { clock.value }) {
            JournalEntryPage(entry, journal.empty, callbacks)
        }
    }
}

/** What a welcome chip hands to the page (the kit closes the screen and the page runs it). */
private fun ChipTap.toAction(): JournalAction =
    when (this) {
        is ChipTap.Claim -> JournalAction.Claim(id)
        ChipTap.CatchUp -> JournalAction.CatchUp
        is ChipTap.Open -> JournalAction.OpenView(view)
    }
