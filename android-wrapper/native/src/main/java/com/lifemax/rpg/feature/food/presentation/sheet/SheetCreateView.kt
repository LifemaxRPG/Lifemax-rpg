package com.lifemax.rpg.feature.food.presentation.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.food.domain.CreateField
import com.lifemax.rpg.feature.food.domain.SheetCreate
import com.lifemax.rpg.feature.food.presentation.FoodContext

/**
 * Create food: the page's boxes for a label's values (one serving), drawn with the form kit in
 * Food's palette, the page's hint and its button. What is typed stays here and goes to the page
 * with Save, as the page reads its own boxes on Save; the page makes the food (per 100 g, its
 * calories from the macros when none are given) or refuses in its own words, and the form stays
 * filled in after a refusal.
 */
@Composable
internal fun SheetCreateView(
    c: FoodContext,
    form: SheetCreate,
    actions: FoodSheetController,
    modifier: Modifier,
) {
    val values = remember {
        mutableStateMapOf<String, String>().apply { form.fields.forEach { put(it.key, it.value) } }
    }
    CompositionLocalProvider(LocalFormKitLook provides foodFormLook(c)) {
        val save = {
            actions.write(
                "sheetCreateSave",
                "create",
                form.fields.associate { it.key to (values[it.key] ?: "") },
            )
        }
        SheetScaffold(modifier, foot = { SheetPrimaryButton(form.save, save) }) {
            createRows(form.fields).forEach { row ->
                if (row.size == 1) {
                    CreateBox(c, row[0], values, Modifier.fillMaxWidth())
                } else {
                    // the page's grid of two columns (.fv2-grid)
                    row.chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            pair.forEach { CreateBox(c, it, values, Modifier.weight(1f)) }
                            if (pair.size == 1) Column(Modifier.weight(1f)) {}
                        }
                    }
                }
            }
            Text(form.hint, color = c.muted, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

/** Fields of one group next to each other make a grid; a field with no group is a row alone. */
internal fun createRows(fields: List<CreateField>): List<List<CreateField>> {
    val out = mutableListOf<MutableList<CreateField>>()
    for (f in fields) {
        val last = out.lastOrNull()
        if (f.group != null && last != null && last.first().group == f.group) last += f
        else out += mutableListOf(f)
    }
    return out
}

@Composable
private fun CreateBox(
    c: FoodContext,
    f: CreateField,
    values: SnapshotStateMap<String, String>,
    modifier: Modifier,
) {
    val label = if (f.small != null) "${f.label} ${f.small}" else f.label
    Row(
        modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FormTextField(
            label,
            values[f.key] ?: "",
            { values[f.key] = it },
            Modifier.weight(1f),
            placeholder = f.hint,
            maxLength = f.maxLength,
            decimal = f.decimal,
        )
        f.unit?.let {
            Text(
                it,
                color = c.muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
    }
}
