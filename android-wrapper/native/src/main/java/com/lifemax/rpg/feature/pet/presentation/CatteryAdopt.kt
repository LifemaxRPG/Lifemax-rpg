package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.pet.domain.AdoptKit
import com.lifemax.rpg.feature.pet.domain.CatteryCommand
import com.lifemax.rpg.feature.pet.domain.CatteryDetail
import com.lifemax.rpg.feature.pet.domain.CatteryWords

private val KitShape = RoundedCornerShape(12.dp)

/**
 * Adoption (the page's intro for a first cat, "Another visitor" for a second): the page's words, a
 * litter of kittens to pick from, a name (empty: the page picks one) and the button that lets one
 * in. "Another litter" asks the page for a new roll; native shows the result.
 */
internal fun LazyListScope.adoptItems(
    d: CatteryDetail.Adopt,
    w: CatteryWords,
    busy: Boolean,
    actions: CatteryActions,
) {
    if (d.mode.isEmpty()) return
    item("adoptHead") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PageEyebrow(d.eyebrow)
            Text(
                d.title,
                color = LocalPageColors.current.text,
                fontSize = 20.sp,
                fontWeight = FontWeight(650),
                lineHeight = 26.sp,
            )
            PetNote(d.body, size = 13.sp)
        }
    }
    item("adoptKits") { AdoptForm(d, w, busy, actions) }
}

@Composable
private fun AdoptForm(
    d: CatteryDetail.Adopt,
    w: CatteryWords,
    busy: Boolean,
    actions: CatteryActions,
) {
    val c = LocalPageColors.current
    var picked by remember(d.kits) { mutableStateOf(d.pick) }
    var name by remember(d.kits) { mutableStateOf("") }
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PetNote(d.parents)
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            d.kits.forEach { kit ->
                KitTile(kit, kit.index == picked) {
                    picked = kit.index
                    actions.send(CatteryCommand.PickKit(kit.index))
                }
            }
        }
        CatteryButton(w.newLitter, w.newLitter, enabled = !busy) {
            actions.send(CatteryCommand.NewLitter)
        }
        CompositionLocalProvider(LocalFormKitLook provides remember(c) { petFormLook(c) }) {
            FormTextField(
                w.nameLabel,
                name,
                { name = it },
                Modifier.fillMaxWidth(),
                enabled = !busy,
                placeholder = d.placeholder,
                maxLength = d.nameMax,
            )
        }
        if (d.memory.isNotEmpty()) PetNote(d.memory)
        CatteryButtons {
            if (d.mode == "pair") CatteryButton(w.cancel, w.cancel) { actions.back() }
            CatteryButton(
                if (d.mode == "pair") w.letIn else w.adopt,
                if (d.mode == "pair") w.letIn else w.adopt,
                primary = true,
                enabled = !busy,
            ) {
                actions.send(CatteryCommand.Adopt(d.mode, picked, name.trim()))
            }
        }
    }
}

/**
 * One kitten of the litter: its picture, sex and coat, then the page's trait and temperament words.
 */
@Composable
private fun KitTile(kit: AdoptKit, on: Boolean, onPick: () -> Unit) {
    val c = LocalPageColors.current
    val spoken =
        (if (kit.female) "female " else "male ") + kit.coat + ", " + kit.trait + ", " + kit.persona
    Row(
        Modifier.fillMaxWidth()
            .clip(KitShape)
            .background(if (on) c.accentDim else c.panel2)
            .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.lineSoft, KitShape)
            .semantics { contentDescription = spoken }
            .selectable(selected = on, role = Role.RadioButton, onClick = onPick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CatPreviewView(kit.preview)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SexMark(kit.female)
                Text(
                    kit.coat,
                    Modifier.padding(start = 6.dp),
                    color = c.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            PetNote(kit.trait + " · " + kit.persona, size = 12.sp)
        }
    }
}

/**
 * In memory: every cat that has passed, with the page's sentence; and the door, when it can open
 * again.
 */
internal fun LazyListScope.memorialItems(
    d: CatteryDetail.Memorial,
    w: CatteryWords,
    busy: Boolean,
    send: (CatteryCommand) -> Unit,
) {
    if (d.entries.isEmpty()) item("none") { PetNote(w.none, size = 13.sp) }
    d.entries.forEachIndexed { i, e ->
        item("mem$i") {
            val c = LocalPageColors.current
            PageCard(Modifier.fillMaxWidth().padding(bottom = 10.dp), padding = 12.dp) {
                Row(
                    Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    e.preview?.let { CatPreviewView(it) }
                    Column(Modifier.weight(1f)) {
                        Text(e.name, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        if (e.coat.isNotEmpty()) PetNote(e.coat, size = 12.sp)
                        PetNote(
                            e.text,
                            Modifier.padding(top = 4.dp),
                            size = 13.sp,
                            color = c.textDim,
                        )
                    }
                }
            }
        }
    }
    if (d.canReopen)
        item("reopen") {
            CatteryButton(w.reopen, w.reopen, primary = true, enabled = !busy) {
                send(CatteryCommand.Reopen)
            }
        }
}
