package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.lazy.LazyListScope
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
    item("together") { PetTogetherCard(pair, panels.words, ui.waiting == "focus", actions.focus) }
}

/** The collapsible cards that follow Care and settings. */
internal fun LazyListScope.petPanelItems(p: PetProjection, ui: PetUi, actions: PetActions) {
    val panels = p.panels ?: return
    panels.likes?.let { item("likes") { PetLikesCard(it, panels.words) } }
    panels.inner?.let { item("inner") { PetInnerCard(it, panels.words) } }
    panels.dna?.let { item("dna") { PetDnaCard(it, panels.words, actions.expandGenome) } }
    panels.toys?.let {
        item("toys") { PetToysCard(it, panels.words, ui.waiting == "buyToy", actions.buyToy) }
    }
}
