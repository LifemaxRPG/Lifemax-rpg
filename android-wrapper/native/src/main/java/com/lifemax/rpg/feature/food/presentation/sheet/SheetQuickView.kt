package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.food.domain.FoodSheet
import com.lifemax.rpg.feature.food.presentation.FoodContext

/** Quick calories: a number and an optional name, logged to the chosen meal as a one-off food. */
@Composable
internal fun SheetQuickView(
    c: FoodContext,
    sheet: FoodSheet,
    send: (String, Map<String, Any>) -> Unit,
) {
    var kcal by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val value = kcal.toDoubleOrNull()?.takeIf { it > 0 }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SheetTextField(c, kcal, { kcal = it }, "Calories", Modifier.fillMaxWidth(), numeric = true)
        SheetTextField(c, name, { name = it }, "Name (optional)", Modifier.fillMaxWidth())
        SheetSlotPills(c, sheet.slots, sheet.slot) { send("sheetSlot", mapOf("slot" to it)) }
        SheetPrimaryButton(
            if (value == null) "Enter calories" else "Add ${value.toLong()} kcal",
            {
                if (value != null)
                    send("sheetQuickSave", mapOf("kcal" to value, "name" to name.trim()))
            },
        )
    }
}
