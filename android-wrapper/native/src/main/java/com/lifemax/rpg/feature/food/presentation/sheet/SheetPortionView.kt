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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetImpact
import com.lifemax.rpg.feature.food.domain.SheetPortion
import com.lifemax.rpg.feature.food.presentation.FoodContext
import com.lifemax.rpg.feature.food.presentation.fmtNum
import kotlinx.coroutines.delay

private const val GRAMS_WAIT_MS = 250L

/**
 * The portion view: the food, its numbers at the chosen grams, the amount controls, the meal and
 * what it adds to the day.
 */
@Composable
internal fun SheetPortionView(
    c: FoodContext,
    sheet: FoodSheet,
    p: SheetPortion,
    send: (String, Map<String, Any>) -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
            Text(
                if (p.favorite) "★" else "☆",
                color = if (p.favorite) Color(0xFFFFC15E) else c.muted,
                fontSize = 26.sp,
                modifier =
                    Modifier.size(48.dp)
                        .semantics {
                            role = Role.Button
                            contentDescription =
                                if (p.favorite) "Remove from favorites" else "Add to favorites"
                        }
                        .clickable { send("sheetFav", emptyMap()) },
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(fmtNum(p.kcal), color = c.fg, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Text(
                "kcal",
                color = c.muted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        GramsStepper(c, p.grams, send)
        ServingButtons(c, p, send)
        SheetSlotPills(c, sheet.slots, sheet.slot) { send("sheetSlot", mapOf("slot" to it)) }
        p.impact.forEach { ImpactBar(c, it) }
        SheetPrimaryButton(p.logLabel, { send("sheetLog", emptyMap()) })
    }
}

/**
 * Minus, the grams box and plus. The box is the player's own while typing; the page's number comes
 * back after a step.
 */
@Composable
private fun GramsStepper(c: FoodContext, grams: Double, send: (String, Map<String, Any>) -> Unit) {
    val shown = gramsText(grams)
    var text by remember { mutableStateOf(shown) }
    var sent by remember { mutableStateOf(shown) }
    LaunchedEffect(shown) {
        if (shown != sent) {
            text = shown
            sent = shown
        }
    }
    LaunchedEffect(text) {
        val v = text.toDoubleOrNull()
        if (text != sent && v != null && v > 0) {
            delay(GRAMS_WAIT_MS)
            sent = text
            send("sheetGrams", mapOf("grams" to v))
        }
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StepButton(c, "−", "Less") { send("sheetStep", mapOf("dir" to -1)) }
        SheetTextField(c, text, { text = it }, "Grams", Modifier.weight(1f), numeric = true)
        Text("g", color = c.muted, fontSize = 16.sp)
        StepButton(c, "+", "More") { send("sheetStep", mapOf("dir" to 1)) }
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
        Text(glyph, color = c.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

/** The serving buttons, then the multiples; each sends the page its grams or factor. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServingButtons(
    c: FoodContext,
    p: SheetPortion,
    send: (String, Map<String, Any>) -> Unit,
) {
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
                { send("sheetServing", mapOf("grams" to s.grams)) },
                sub = if (s.showsGrams) "${gramsText(s.grams)} g" else null,
            )
        }
        p.multiples.forEach { m ->
            SheetChip(c, m.label, false, { send("sheetMult", mapOf("x" to m.factor)) })
        }
    }
}

/** One macro's bar: the part of the day's target already eaten, then what this amount adds. */
@Composable
private fun ImpactBar(c: FoodContext, i: SheetImpact) {
    val color = Color(i.color)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
