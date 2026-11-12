package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.form.FormTextField
import com.lifemax.rpg.core.designsystem.form.LocalFormKitLook
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.domain.CatPreview
import com.lifemax.rpg.feature.pet.domain.CatteryCat
import kotlin.math.floor

/*
 * The parts the cattery screens share: a cat's still picture (the live room's own art, so a genetic
 * coat is composed by the page and asked for as for the room), the cat card, and the dialogs that
 * stand in for the page's confirm() and prompt().
 */

private val PreviewShape = RoundedCornerShape(12.dp)
private val DialogShape = RoundedCornerShape(16.dp)
private val SexFemale = androidx.compose.ui.graphics.Color(0xFFF0718F)
private val SexMale = androidx.compose.ui.graphics.Color(0xFF5BA4E6)

/**
 * A cat sitting (or, for the memorial, asleep), still, from its coat, growth stage and proportions.
 */
@Composable
internal fun CatPreviewView(preview: CatPreview, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    val sprites = rememberCatSprites(preview.coat, preview.stage, preview.rig)
    val pose =
        remember(preview.sleep) {
            CatPose().apply { clip = (if (preview.sleep) Clips.SLEEP_W else Clips.IDLE_SIT).id }
        }
    Canvas(
        modifier
            .size(PREVIEW_W, PREVIEW_H)
            .clip(PreviewShape)
            .background(c.panel2)
            .clearAndSetSemantics {}
    ) {
        val s = sprites ?: return@Canvas
        val scale = floor(size.width / s.frameWidth).toInt().coerceAtLeast(1)
        drawSpriteAt(pose, s, scale, size.width / 2f, size.height - 4f * scale, motion = false)
    }
}

private val PREVIEW_W = 96.dp
private val PREVIEW_H = 72.dp

/** The sex mark of a cat card: a symbol the page uses, with its word for TalkBack. */
@Composable
internal fun SexMark(female: Boolean) {
    Text(
        if (female) "♀" else "♂",
        Modifier.clearAndSetSemantics {},
        color = if (female) SexFemale else SexMale,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** What TalkBack reads for a cat card: name, sex, the page's small line and the chips. */
internal fun catSpoken(cat: CatteryCat): String = buildString {
    append(cat.name).append(", ").append(if (cat.female) "female" else "male").append(". ")
    append(cat.sub).append(". ")
    append(cat.chips.joinToString(", ") { it.title + " " + it.label.substringAfterLast(' ') })
}

/**
 * A cat card (`.pet-bc`): its picture, name with the sex mark, the small line and the shape chips
 * (the goal's marked), then the [actions] the page's card has under them. The words are one
 * TalkBack element; the buttons stay their own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatteryCatCard(
    cat: CatteryCat,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val c = LocalPageColors.current
    val stack = panelLargeText()
    PageCard(modifier.fillMaxWidth(), padding = 12.dp) {
        val body: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Column(
                    Modifier.semantics(mergeDescendants = true) {
                        contentDescription = catSpoken(cat)
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SexMark(cat.female)
                        Text(
                            cat.name,
                            Modifier.padding(start = 6.dp).semantics { heading() },
                            color = c.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(cat.sub, color = c.muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                FlowRow(
                    Modifier.padding(top = 6.dp).clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    cat.chips.forEach {
                        PetChip(it.label, if (it.goal) CatPink else null, bold = it.goal)
                    }
                }
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CatPreviewView(cat.preview)
                body(Modifier)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CatPreviewView(cat.preview)
                body(Modifier.weight(1f))
            }
        }
        Column(Modifier.padding(top = 8.dp), content = { actions() })
    }
}

/** A row of the card's buttons: side by side, or stacked at large text. */
@Composable
internal fun CatteryButtons(content: @Composable () -> Unit) {
    if (panelLargeText()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** A card button: the page's secondary button, with its spoken label. */
@Composable
internal fun CatteryButton(
    text: String,
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val go = { if (enabled) onClick() }
    val m = Modifier.alpha(if (enabled) 1f else .5f)
    if (primary) PageButton(text, label, go, m)
    else PetSecondaryButton(text, label, go, m, small = true)
}

/**
 * The page's confirm(), as a dialog: [text] word for word, then Cancel and the confirming button.
 */
@Composable
internal fun CatteryConfirm(
    text: String,
    confirm: String,
    cancel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.widthIn(max = 420.dp)
                .fillMaxWidth()
                .clip(DialogShape)
                .background(c.panel)
                .border(1.dp, c.line, DialogShape)
                .semantics { paneTitle = text }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp)
            Row(
                Modifier.align(Alignment.End),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PetSecondaryButton(cancel, cancel, onDismiss)
                PageButton(confirm, confirm, onConfirm)
            }
        }
    }
}

/** The page's prompt() for a name, as a dialog with the field, the limit and Save and Cancel. */
@Composable
internal fun CatteryNameDialog(
    title: String,
    label: String,
    initial: String,
    max: Int,
    save: String,
    cancel: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalPageColors.current
    var draft by remember { mutableStateOf(initial) }
    val ready = draft.trim().isNotEmpty() && draft.trim() != initial
    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalFormKitLook provides remember(c) { petFormLook(c) }) {
            Column(
                Modifier.widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .clip(DialogShape)
                    .background(c.panel)
                    .border(1.dp, c.line, DialogShape)
                    .semantics { paneTitle = title }
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(title, color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                FormTextField(
                    label,
                    draft,
                    { draft = it },
                    Modifier.fillMaxWidth(),
                    maxLength = max,
                )
                Row(
                    Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PetSecondaryButton(cancel, cancel, onDismiss)
                    if (ready) PageButton(save, save, { onSave(draft.trim()) })
                }
            }
        }
    }
}

/** One choice of a [CatteryPicker]: its key and the words shown. */
internal data class Choice(val key: String, val label: String)

/**
 * A field that shows the chosen option and, tapped, lists them all in a dialog: a mother, a father,
 * a goal. The list is a radio group for TalkBack.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun CatteryPicker(
    label: String,
    choices: List<Choice>,
    selected: String,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalPageColors.current
    var open by remember { mutableStateOf(false) }
    val shown = choices.firstOrNull { it.key == selected }?.label.orEmpty()
    Column(modifier) {
        Text(label, color = c.muted, fontSize = 12.sp, fontWeight = FontWeight(650))
        Row(
            Modifier.fillMaxWidth()
                .padding(top = 4.dp)
                .clip(PreviewShape)
                .background(c.field)
                .border(1.dp, c.line, PreviewShape)
                .semantics { contentDescription = "$label, $shown" }
                .tapTarget(Role.DropdownList) { open = true }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown, Modifier.weight(1f), color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
            Text("▾", color = c.muted, fontSize = 15.sp)
        }
    }
    if (open)
        Dialog(onDismissRequest = { open = false }) {
            Column(
                Modifier.widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .clip(DialogShape)
                    .background(c.panel)
                    .border(1.dp, c.line, DialogShape)
                    .semantics { paneTitle = label }
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 20.dp, vertical = 6.dp).semantics { heading() },
                    color = c.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                LazyColumn(Modifier.selectableGroup()) {
                    items(choices, key = { it.key }) { ch ->
                        val on = ch.key == selected
                        Row(
                            Modifier.fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .selectable(
                                    selected = on,
                                    role = Role.RadioButton,
                                    onClick = {
                                        open = false
                                        if (!on) onPick(ch.key)
                                    },
                                )
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (on) "●" else "○",
                                Modifier.clearAndSetSemantics {},
                                color = if (on) c.accent else c.muted,
                                fontSize = 15.sp,
                            )
                            Text(ch.label, color = c.text, fontSize = 14.sp, lineHeight = 20.sp)
                        }
                    }
                }
            }
        }
}
