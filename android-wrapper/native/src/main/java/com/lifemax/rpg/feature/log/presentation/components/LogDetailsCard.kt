package com.lifemax.rpg.feature.log.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.components.SectionLabel
import com.lifemax.rpg.core.designsystem.components.SurfaceCard
import com.lifemax.rpg.feature.log.domain.LogDraft

/** The words: a name for a custom activity, the skill practised, and notes. */
@Composable
internal fun LogDetailsCard(
    draft: LogDraft,
    onCustomName: (String) -> Unit,
    onSkill: (String) -> Unit,
    onNotes: (String) -> Unit,
) {
    SurfaceCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (draft.type == "custom")
                Field("What did you do?", draft.customName, onCustomName, "Custom activity name")
            Field("Skill practised", draft.skill, onSkill, "Skill")
            Field(
                "Notes (optional)",
                draft.notes,
                onNotes,
                "What did you accomplish?",
                singleLine = false,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label)
        OutlinedTextField(
            value,
            onChange,
            Modifier.fillMaxWidth().heightIn(min = if (singleLine) 52.dp else 96.dp),
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
                ),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .5f),
                    focusedContainerColor = MaterialTheme.colorScheme.background,
                    unfocusedContainerColor = MaterialTheme.colorScheme.background,
                ),
        )
    }
}
