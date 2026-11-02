package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetImpact
import com.lifemax.rpg.feature.food.domain.SheetPortion
import com.lifemax.rpg.feature.food.presentation.FoodContext
import com.lifemax.rpg.feature.food.presentation.fmtNum

private const val GRAMS_WAIT_MS = 250L

/**
 * The portion view: the food, its numbers at the chosen grams, the amount controls, the meal and
 * what it adds to the day, then Log. The same view changes a logged entry (the page's edit view,
 * [SheetPortion.edit]): no star, and Remove, Log today (for another day's entry) and Save.
 */
@Composable
internal fun SheetPortionView(
    c: FoodContext,
    sheet: FoodSheet,
    p: SheetPortion,
    actions: FoodSheetController,
    modifier: Modifier,
) {
    val edit = p.edit
    SheetScaffold(modifier, foot = { PortionFoot(c, p, actions) }) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.name,
                    color = c.fg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                if (p.sub.isNotEmpty()) Text(p.sub, color = c.muted, fontSize = 12.sp)
            }
            if (edit == null) Star(c, p, sheet.words.fav, actions)
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(fmtNum(p.kcal), color = c.fg, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                sheet.words.kcal,
                color = c.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        GramsStepper(c, sheet, p.grams, actions)
        ServingButtons(c, p, actions)
        SheetSlotPills(c, sheet.slots, sheet.slot) {
            actions.send("sheetSlot", mapOf("slot" to it))
        }
        p.impact.forEach { ImpactBar(c, it) }
    }
}

/** Log; or, for an entry, Remove, Log today (another day's entry only) and Save. */
@Composable
private fun PortionFoot(c: FoodContext, p: SheetPortion, actions: FoodSheetController) {
    val edit = p.edit
    if (edit == null) {
        SheetPrimaryButton(p.logLabel, { actions.write("sheetLog", p.name) })
    } else {
        val ref = mapOf<String, Any>("id" to edit.id)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RowScopeGhost(
                c,
                edit.remove,
                { actions.write("sheetEditDelete", edit.id, ref) },
                Modifier,
                ink = badColor(c),
            )
            edit.again?.let { again ->
                RowScopeGhost(
                    c,
                    again,
                    { actions.write("sheetEditAgain", edit.id, ref) },
                    Modifier.weight(1f),
                )
            }
            SheetPrimaryButton(
                p.logLabel,
                { actions.write("sheetEditSave", edit.id, ref) },
                Modifier.weight(2f),
            )
        }
    }
}

/**
 * The page's favourite star: a toggle TalkBack names with the page's word and reads as on or off.
 */
@Composable
private fun Star(c: FoodContext, p: SheetPortion, label: String, actions: FoodSheetController) {
    Box(
        Modifier.size(48.dp)
            .toggleable(value = p.favorite, role = Role.Checkbox) {
                actions.write("sheetFav", p.name)
            }
            .semantics { contentDescription = label },
        Alignment.Center,
    ) {
        Text(
            if (p.favorite) "★" else "☆",
            color = if (p.favorite) Color(0xFFFFC15E) else c.muted,
            fontSize = 26.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * Minus, the grams box and plus. The box is the player's own while typing; the page's number comes
 * back after a step, a serving or a multiple (a different number from the one typed), never over
 * the point of a "1." being typed.
 */
@Composable
private fun GramsStepper(
    c: FoodContext,
    sheet: FoodSheet,
    grams: Double,
    actions: FoodSheetController,
) {
    var text by
        rememberSynced(gramsText(grams), GRAMS_WAIT_MS, ::sameNumber) { typed ->
            typed
                .toDoubleOrNull()
                ?.takeIf { it > 0 }
                ?.let { actions.send("sheetGrams", mapOf("grams" to it)) }
        }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepButton(c, "−", sheet.words.less) { actions.send("sheetStep", mapOf("dir" to -1)) }
        SheetTextField(
            c,
            text,
            { text = it },
            sheet.words.grams,
            Modifier.weight(1f),
            numeric = true,
        )
        Text(sheet.words.grams, color = c.muted, fontSize = 14.sp)
        StepButton(c, "+", sheet.words.more) { actions.send("sheetStep", mapOf("dir" to 1)) }
    }
}

private fun gramsText(g: Double): String =
    if (g % 1.0 == 0.0) g.toLong().toString() else g.toString()

@Composable
private fun StepButton(c: FoodContext, glyph: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(48.dp)
            .clip(shape)
            .background(c.sunken)
            .semantics {
                role = Role.Button
                contentDescription = label
            }
            .clickable(onClick = onClick),
        Alignment.Center,
    ) {
        Text(
            glyph,
            color = c.fg,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** The serving buttons, then the multiples; each sends the page its grams or factor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServingButtons(c: FoodContext, p: SheetPortion, actions: FoodSheetController) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        p.servings.forEach { s ->
            SheetChip(
                c,
                s.label,
                s.selected,
                { actions.send("sheetServing", mapOf("grams" to s.grams)) },
                sub = if (s.showsGrams) "${gramsText(s.grams)} g" else null,
            )
        }
        p.multiples.forEach { m ->
            SheetChip(
                c,
                m.label,
                false,
                { actions.send("sheetMult", mapOf("x" to m.factor)) },
                choice = false,
            )
        }
    }
}

/** One macro's bar: the part of the day's target already eaten, then what this amount adds. */
@Composable
private fun ImpactBar(c: FoodContext, i: SheetImpact) {
    val color = Color(i.color)
    Column(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(i.name, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(i.label, color = c.fg, fontSize = 12.sp)
        }
        val track = RoundedCornerShape(50)
        Row(Modifier.fillMaxWidth().height(8.dp).clip(track).background(c.trackColor)) {
            if (i.before > 0f)
                Box(Modifier.weight(i.before).height(8.dp).background(color.copy(alpha = 0.45f)))
            if (i.add > 0f) Box(Modifier.weight(i.add).height(8.dp).background(color))
            val rest = 1f - i.before - i.add
            if (rest > 0f) Box(Modifier.weight(rest).height(8.dp))
        }
    }
}
