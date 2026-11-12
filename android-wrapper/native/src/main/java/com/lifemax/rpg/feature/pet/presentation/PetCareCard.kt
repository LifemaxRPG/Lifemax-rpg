package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageCheck
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.components.toggleTarget
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.pet.domain.PetProjection

/**
 * `details.card.pet-more`: "Care and settings", which opens to the page's two paragraphs, the
 * Vacation mode switch and the cat's name. [open] is whether it shows its contents; it starts
 * closed, as the page's does. The switch and the rename ask the page ([onVacation], [onRename]) and
 * show what it answers: a switch waiting for the page is faded.
 */
@Composable
internal fun PetCareCard(
    p: PetProjection,
    waiting: String?,
    onVacation: (Boolean) -> Unit,
    onRename: (String) -> Unit,
    modifier: Modifier = Modifier,
    startOpen: Boolean = false,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(startOpen) }
    PageCard(modifier.fillMaxWidth(), padding = 15.dp) {
        Row(
            Modifier.fillMaxWidth()
                .semantics { stateDescription = if (open) "Open" else "Closed" }
                .tapTarget(Role.Button) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(p.words.careTitle, color = c.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(if (open) "▾" else "▸", color = c.muted, fontSize = 15.sp)
        }
        if (open) CareBody(p, waiting, onVacation, onRename)
    }
}

@Composable
private fun CareBody(
    p: PetProjection,
    waiting: String?,
    onVacation: (Boolean) -> Unit,
    onRename: (String) -> Unit,
) {
    val c = LocalPageColors.current
    val care = p.care
    Column(Modifier.padding(top = 4.dp).selectableGroup()) {
        Paragraph(care.how)
        Paragraph(care.bond)
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 6.dp)
                .toggleTarget(care.vacation, enabled = waiting == null, onToggle = onVacation),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    p.words.vacationTitle,
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                PetNote(care.vacationHint, size = 12.sp)
            }
            PageCheck(care.vacation, pending = waiting == "vacation")
        }
        CompositionLocalProvider(LocalFormKitLook provides remember(c) { petFormLook(c) }) {
            RenameField(p, waiting, onRename)
        }
    }
}

/** A paragraph of the care text: the page's bold lead-in ("How it works.") and the rest. */
@Composable
private fun Paragraph(text: String) {
    val c = LocalPageColors.current
    val lead = text.indexOf(". ") + 1
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(0, lead)) }
            append(text.substring(lead))
        },
        Modifier.padding(vertical = 5.dp),
        color = c.textDim,
        fontSize = 13.sp,
        lineHeight = 19.5.sp,
    )
}

@Composable
private fun RenameField(p: PetProjection, waiting: String?, onRename: (String) -> Unit) {
    var draft by remember(p.care.name) { mutableStateOf(p.care.name) }
    val ready = draft.trim().isNotEmpty() && draft.trim() != p.care.name
    Column(Modifier.padding(top = 12.dp)) {
        FormTextField(
            p.words.nameLabel,
            draft,
            { draft = it },
            Modifier.fillMaxWidth(),
            enabled = waiting == null,
            maxLength = p.care.nameMax,
        )
        if (ready)
            PageButton(
                p.words.rename,
                p.words.rename,
                { onRename(draft.trim()) },
                Modifier.padding(top = 10.dp),
            )
    }
}

/** The page's fields in the Cat tab's colours: its background as the well, its line as the edge. */
internal fun petFormLook(c: PageColors) =
    FormKitLook(
        ink = c.text,
        ink2 = c.muted,
        ink3 = c.muted2,
        well = c.field,
        edge = c.line,
        segEdge = c.line,
        focus = c.accent,
        focusRing = c.accentDim,
        error = c.bad,
        cursor = c.accent,
    )
