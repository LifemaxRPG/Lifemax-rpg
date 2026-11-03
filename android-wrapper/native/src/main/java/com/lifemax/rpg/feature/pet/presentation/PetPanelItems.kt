package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.pet.domain.PetProjection

/*
 * Where the Cat tab's panels (`pet.panels.v1`) go in the screen's list, in the page's order: the
 * Together card straight under the cat's card, and the collapsible cards (likes, inner life,
 * genetics, toys) after Care and settings. The screen adds these with one call each; a part the
 * page did not send (an older page, or a house with one cat) adds nothing.
 */

/** The Together card with the switcher, which the page shows right under the cat's card. */
internal fun LazyListScope.petTogetherItem(p: PetProjection, ui: PetUi, actions: PetActions) {
    val panels = p.panels ?: return
    val pair = panels.pair ?: return
    item("together") {
        PetSlot { PetTogetherCard(pair, panels.words, ui.waiting == "focus", actions.focus) }
    }
}

/** The collapsible cards that follow Care and settings. */
internal fun LazyListScope.petPanelItems(p: PetProjection, ui: PetUi, actions: PetActions) {
    val panels = p.panels ?: return
    panels.likes?.let { item("likes") { PetSlot { PetLikesCard(it, panels.words) } } }
    panels.inner?.let { item("inner") { PetSlot { PetInnerCard(it, panels.words) } } }
    panels.dna?.let {
        item("dna") { PetSlot { PetDnaCard(it, panels.words, actions.expandGenome) } }
    }
    panels.toys?.let {
        item("toys") {
            PetSlot { PetToysCard(it, panels.words, ui.waiting == "buyToy", actions.buyToy) }
        }
    }
}

/**
 * The gutter and gap a card had from the list's content padding, now that the list pads only its
 * end.
 */
@androidx.compose.runtime.Composable
private fun PetSlot(content: @androidx.compose.runtime.Composable () -> Unit) {
    Box(Modifier.padding(12.dp, 14.dp, 12.dp, 0.dp)) { content() }
}
