package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.PetCattery
import com.lifemax.rpg.feature.pet.domain.PetProjection

/**
 * The Cat tab's way into the cattery screens (`pet.cattery.v1`): one card with a row for the
 * Cattery (how many cats wait there), the Breeding lab, "A second cat" while one may be adopted
 * (the page's words and its "Meet") and In memory once a cat has passed. A page that does not send
 * the block (an older page) shows nothing.
 */
internal fun LazyListScope.petCatteryItem(p: PetProjection, actions: PetActions) {
    val cattery = p.cattery ?: return
    item("cattery") {
        Box(Modifier.padding(12.dp, 14.dp, 12.dp, 0.dp)) {
            CatteryEntryCard(cattery, actions.catteryOpen)
        }
    }
}

@Composable
internal fun CatteryEntryCard(
    cattery: PetCattery,
    open: (CatteryScreen) -> Unit,
    modifier: Modifier = Modifier,
) {
    val w = cattery.words
    val e = cattery.entry
    PageCard(modifier.fillMaxWidth(), padding = 4.dp) {
        EntryRow(w.cattery, "${e.cats} / ${e.max}", "${w.cattery}, ${e.cats} of ${e.max}") {
            open(CatteryScreen.CATTERY)
        }
        HorizontalDivider(color = LocalPageColors.current.lineSoft)
        EntryRow(w.lab, "", w.lab) { open(CatteryScreen.LAB) }
        if (e.canAdopt) {
            HorizontalDivider(color = LocalPageColors.current.lineSoft)
            EntryRow(w.adoptTitle, w.adoptButton, "${w.adoptTitle}. ${w.adoptHint}", w.adoptHint) {
                open(CatteryScreen.ADOPT)
            }
        }
        if (e.memorial > 0) {
            HorizontalDivider(color = LocalPageColors.current.lineSoft)
            EntryRow(w.memorial, "${e.memorial}", "${w.memorial}, ${e.memorial}") {
                open(CatteryScreen.MEMORIAL)
            }
        }
    }
}

@Composable
private fun EntryRow(
    title: String,
    trailing: String,
    spoken: String,
    hint: String = "",
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth()
            .semantics { contentDescription = spoken }
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            if (hint.isNotEmpty()) PetNote(hint, Modifier.padding(top = 2.dp), size = 12.sp)
        }
        if (trailing.isNotEmpty()) Text(trailing, color = c.muted, fontSize = 13.sp)
        Text("→", color = c.muted, fontSize = 18.sp)
    }
}
