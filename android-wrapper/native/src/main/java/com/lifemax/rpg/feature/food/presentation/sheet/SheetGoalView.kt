package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.core.designsystem.form.LocalPickerFieldLook
import com.lifemax.rpg.core.designsystem.form.PickerFieldLook
import com.lifemax.rpg.core.designsystem.form.StepperField
import com.lifemax.rpg.feature.food.domain.GoalSum
import com.lifemax.rpg.feature.food.domain.SheetGoal
import com.lifemax.rpg.feature.food.presentation.BlueAccent
import com.lifemax.rpg.feature.food.presentation.FoodContext

/** How long a typed target waits before the page is told (it answers the line under the boxes). */
private const val GOAL_WAIT_MS = 150L

/**
 * The daily targets, drawn with the form kit in Food's palette: the calories with the page's step,
 * the page's presets, the three macros, the line under them and water. The boxes are the player's
 * own while typing and go to the page a moment later; the page answers the line ("Your macros add
 * up to …") and a preset's grams, which come back into the boxes. Save sends the boxes as they are,
 * so one typed a moment ago is not lost, and the page saves them as its own Save does.
 */
@Composable
internal fun SheetGoalView(
    c: FoodContext,
    goal: SheetGoal,
    actions: FoodSheetController,
    modifier: Modifier,
) {
    val cal = rememberSyncedBox("cal", goal.cal, actions)
    val macros = goal.fields.map { it to rememberSyncedBox(it.key, it.value, actions) }
    val water = rememberSyncedBox("water", goal.water, actions)
    CompositionLocalProvider(
        LocalFormKitLook provides foodFormLook(c),
        LocalPickerFieldLook provides foodPickerLook(c),
    ) {
        val save = {
            val boxes = mutableMapOf<String, Any>("cal" to cal.value, "water" to water.value)
            macros.forEach { (f, text) -> boxes[f.key] = text.value }
            actions.write("sheetGoalSave", "goal", boxes)
        }
        SheetScaffold(modifier, foot = { SheetPrimaryButton(goal.save, save) }) {
            StepperField(
                label = goal.calLabel,
                value = cal.value.toIntOrNull() ?: goal.calMin,
                onChange = { cal.value = it.toString() },
                min = goal.calMin,
                step = goal.calStep,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                goal.presets.forEach { p ->
                    Box(Modifier.weight(1f)) {
                        SheetChip(
                            c,
                            p.label,
                            false,
                            { actions.send("sheetGoalPreset", mapOf("preset" to p.id)) },
                            sub = p.sub,
                            choice = false,
                            fill = true,
                        )
                    }
                }
            }
            // the page's grid of two columns
            macros.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { (f, text) ->
                        FormTextField(
                            f.label,
                            text.value,
                            { text.value = it },
                            Modifier.weight(1f),
                            number = true,
                            maxLength = 6,
                        )
                    }
                    if (pair.size == 1) Box(Modifier.weight(1f))
                }
            }
            SumLine(c, goal.sum)
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FormTextField(
                    goal.waterLabel,
                    water.value,
                    { water.value = it },
                    Modifier.weight(1f),
                    number = true,
                    maxLength = 6,
                )
                Text(
                    goal.waterUnit,
                    color = c.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
    }
}

/** A target's box: typed here, told to the page after a moment, and filled by a preset. */
@Composable
private fun rememberSyncedBox(
    key: String,
    page: String,
    actions: FoodSheetController,
): MutableState<String> =
    rememberSynced(page, GOAL_WAIT_MS, ::sameNumber) {
        actions.send("sheetGoalField", mapOf("key" to key, "value" to it))
    }

/** The page's line under the macros, its total in bold and an overshoot in the warning colour. */
@Composable
private fun SumLine(c: FoodContext, s: GoalSum) {
    val line = buildAnnotatedString {
        append(s.lead)
        withStyle(SpanStyle(color = c.fg, fontWeight = FontWeight.Bold)) { append(s.strong) }
        append(s.sep)
        if (s.bad) withStyle(SpanStyle(color = warnColor(c))) { append(s.note) } else append(s.note)
    }
    Text(
        line,
        color = c.muted,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** The kit's stepper in Food's palette, dark or light. */
private fun foodPickerLook(c: FoodContext) =
    PickerFieldLook(
        label = c.muted,
        ink = c.fg,
        fill = c.sunken,
        border = c.panelBorder,
        accent = BlueAccent,
        onAccent = Color.White,
        button = c.selectedPanel,
        panel = c.cardTop,
        scrim = Color.Black.copy(alpha = .55f),
        muted = c.subtle,
    )
