package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.domain.SheetQuick
import com.lifemax.rpg.feature.food.presentation.FoodContext

/**
 * Quick calories: a number and an optional name, logged to the chosen meal as a one-off food, in
 * the page's words. What is typed goes to the page as it is: the page refuses no calories in its
 * own words ("Enter the calories"), which come back as its message.
 */
@Composable
internal fun SheetQuickView(
    c: FoodContext,
    sheet: FoodSheet,
    q: SheetQuick,
    actions: FoodSheetController,
    modifier: Modifier,
) {
    var kcal by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    SheetScaffold(
        modifier,
        foot = {
            SheetPrimaryButton(
                q.save,
                { actions.write("sheetQuickSave", "quick", mapOf("kcal" to kcal, "name" to name)) },
            )
        },
    ) {
        SheetTextField(
            c,
            kcal,
            { kcal = it },
            q.kcalHint,
            Modifier.fillMaxWidth(),
            numeric = true,
            label = q.kcalLabel,
        )
        SheetTextField(
            c,
            name,
            { name = it.take(q.nameMax) },
            q.nameHint,
            Modifier.fillMaxWidth(),
            label = q.nameLabel,
            small = q.nameSmall,
        )
        SheetSlotPills(c, sheet.slots, sheet.slot) {
            actions.send("sheetSlot", mapOf("slot" to it))
        }
    }
}
