package com.lifemax.rpg.feature.plan.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.core.designsystem.form.LocalPickerFieldLook
import com.lifemax.rpg.core.designsystem.form.pickers
import com.lifemax.rpg.feature.plan.presentation.FieldEdit
import com.lifemax.rpg.feature.plan.presentation.FieldKind
import com.lifemax.rpg.feature.plan.presentation.PlanWriteActions

/**
 * The form kit in the plan's colours: the page's fields on this view are white in the light theme
 * and the page's dark well otherwise, with its accent for the focus.
 */
@Composable
internal fun PlanForms(content: @Composable () -> Unit) {
    val c = LocalPlanColors.current
    val look =
        remember(c) {
            FormKitLook(
                ink = c.text,
                ink2 = c.textDim,
                ink3 = c.muted,
                well = c.field,
                edge = c.line,
                focus = c.accent,
                focusRing = c.accent.copy(alpha = .2f),
                cursor = c.accentStrong,
                error = c.bad,
            )
        }
    CompositionLocalProvider(
        LocalFormKitLook provides look,
        LocalPickerFieldLook provides look.pickers(),
        content = content,
    )
}

/**
 * A bottom sheet over the dimmed screen, above the shared bottom bar ([bottomInset]), in the plan's
 * colours. A tap on the dimmed screen or Back is [onDismiss]; a tap on the panel stays there.
 */
@Composable
internal fun BoxScope.PlanSheet(
    title: String,
    bottomInset: Dp,
    onDismiss: () -> Unit,
    active: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalPlanColors.current
    // a hidden screen takes no Back button (A24)
    BackHandler(enabled = active, onBack = onDismiss)
    Box(
        Modifier.matchParentSize()
            .background(Color.Black.copy(alpha = .55f))
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .clearAndSetSemantics {}
    )
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    Box(
        Modifier.align(Alignment.BottomCenter)
            .padding(bottom = bottomInset)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgElev)
            .border(1.dp, c.line, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = title }
    ) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .size(40.dp, 5.dp)
                .background(c.line, RoundedCornerShape(5.dp))
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

@Composable
internal fun SheetHeading(text: String) {
    val c = LocalPlanColors.current
    Text(
        text,
        Modifier.semantics { heading() },
        color = c.text,
        fontSize = 18.sp,
        fontWeight = FontWeight(700),
        lineHeight = 24.sp,
    )
}

/** A secondary button as the plan's Leave plan is drawn, 48 dp tall for a thumb. */
@Composable
internal fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(c.secondaryButton)
            .border(1.dp, c.line, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
        )
    }
}

/**
 * A value or a session log, edited in a sheet with the form kit's text field; Save sends it to the
 * page.
 */
@Composable
internal fun BoxScope.PlanFieldSheet(
    edit: FieldEdit,
    busy: Boolean,
    logMax: Int,
    bottomInset: Dp,
    actions: PlanWriteActions,
    active: Boolean = true,
) {
    PlanSheet(edit.title, bottomInset, actions::cancelEdit, active) {
        PlanForms {
            SheetHeading(edit.title)
            FormTextField(
                edit.label,
                edit.draft,
                actions::draft,
                Modifier.fillMaxWidth(),
                placeholder = edit.placeholder,
                maxLength = if (edit.kind == FieldKind.LOG) logMax else null,
                error = edit.error,
                lines = if (edit.kind == FieldKind.LOG) 4 else 1,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecondaryAction("Cancel", actions::cancelEdit)
                PageButton(
                    "Save",
                    "Save " + edit.label,
                    { if (!busy) actions.saveEdit() },
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/** A question the page asks with confirm(), in its words, with the two answers. */
@Composable
internal fun BoxScope.PlanConfirmSheet(
    question: String,
    confirm: String,
    bottomInset: Dp,
    busy: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    active: Boolean = true,
) {
    val c = LocalPlanColors.current
    PlanSheet(question, bottomInset, onCancel, active) {
        Text(
            question,
            Modifier.semantics { heading() },
            color = c.text,
            fontSize = 16.sp,
            fontWeight = FontWeight(600),
            lineHeight = 23.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecondaryAction("Cancel", onCancel)
            PageButton(confirm, confirm, { if (!busy) onConfirm() }, Modifier.weight(1f))
        }
    }
}
