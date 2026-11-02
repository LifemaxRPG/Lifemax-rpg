package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.PageInk
import com.lifemax.rpg.core.designsystem.components.PageTextArea
import com.lifemax.rpg.core.designsystem.components.PageTextAreaLook
import com.lifemax.rpg.feature.journal.domain.CatchUpUi
import com.lifemax.rpg.feature.journal.domain.CatchUpWords
import com.lifemax.rpg.feature.journal.domain.JournalDraft

/*
 * Phase 2: the notebook fields that type for themselves (library.journal.write.v1). An entry with an
 * [EntryEditor] draws its reflection and answer boxes as fields, and its welcome card's catch-up as
 * a field under the chips; without one (the page's own field, phase 1) a tap hands over to the page.
 */

/**
 * What the body needs to let the owner write: the words being typed ([draft]), the catch-up's words
 * and state, whether the keyboard is up, and what each change asks of the view model. [onEdit] is
 * told every change of the text of the entry [key]; [onLeave] that the field lost the focus.
 */
internal class EntryEditor(
    val draft: JournalDraft?,
    val catchUpWords: CatchUpWords,
    val catchUp: CatchUpUi,
    val keyboardUp: Boolean,
    val onEdit: (key: String, text: String) -> Unit,
    val onLeave: () -> Unit,
    val onCatchUpSend: (String) -> Unit,
    val onCatchUpCancel: () -> Unit,
) {
    /** The draft's words for the entry [key], or null when the owner has not typed in it. */
    fun draftFor(key: String): JournalDraft? = draft?.takeIf { it.key == key }
}

/** The ruled field's colours in the Journal's palette (the same as the static field's). */
@Composable
private fun notebookLook(): PageTextAreaLook {
    val c = LocalJournalBodyColors.current
    return PageTextAreaLook(
        ink = c.text,
        placeholder = PageInk.Placeholder,
        rule = JournalInk.white(0x0B),
        well = c.textarea,
        edge = c.line,
        cursor = c.accent,
    )
}

/**
 * The reflection's or the answer's field. [text] is what it should show: the draft's words, else
 * the page's. The field keeps its own text value (the keyboard's composing text and the selection
 * are its own) and takes [text] in only when the page's words replace the owner's ([rev] rises) or
 * when it is not focused. Closing the keyboard leaves the field, so the final write goes then.
 */
@Composable
internal fun EditableNotebook(
    key: String,
    text: String,
    rev: Int,
    placeholder: String,
    max: Int,
    editor: EntryEditor,
) {
    var value by remember(key) { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    var focused by remember(key) { mutableStateOf(false) }
    LaunchedEffect(rev, text) {
        if (value.text != text && (!focused || rev != 0))
            value = TextFieldValue(text, TextRange(text.length))
    }
    // the keyboard closed while the field has the focus: the owner is done with it
    val focus = LocalFocusManager.current
    val up = editor.keyboardUp
    val wasUp = remember { booleanArrayOf(up) }
    LaunchedEffect(up) {
        if (wasUp[0] && !up && focused) focus.clearFocus()
        wasUp[0] = up
    }
    PageTextArea(
        value,
        { typed ->
            val changed = typed.text != value.text
            value = typed
            if (changed) editor.onEdit(key, typed.text)
        },
        placeholder,
        notebookLook(),
        maxLength = max,
        revealOn = up,
        description = placeholder,
        onFocusChange = {
            focused = it
            if (!it) editor.onLeave()
        },
    )
}

/**
 * The one-sentence catch-up under the welcome card's chips: the page's question, a field for the
 * sentence and the system prompt's two buttons. The page checks the sentence (12 characters at
 * least) and answers in its words, which the screen shows as its toast; the field stays until the
 * page has stored it. Its focus is taken when it opens, so the keyboard rises.
 */
@Composable
internal fun CatchUpPanel(editor: EntryEditor) {
    val c = LocalJournalBodyColors.current
    val words = editor.catchUpWords
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val request = remember { FocusRequester() }
    LaunchedEffect(Unit) { request.requestFocus() }
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(words.prompt, color = c.text, fontSize = 14.sp, lineHeight = 21.sp)
        PageTextArea(
            value,
            { value = it },
            "",
            notebookLook(),
            Modifier.padding(top = 8.dp),
            maxLength = words.max,
            minHeight = 84.dp,
            revealOn = editor.keyboardUp,
            description = words.prompt,
            focusRequester = request,
        )
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            CatchUpButton(words.cancel, false, !editor.catchUp.sending, editor.onCatchUpCancel)
            CatchUpButton(words.ok, true, !editor.catchUp.sending) {
                editor.onCatchUpSend(value.text)
            }
        }
    }
}

/** A button of the catch-up: the chip's pill, the accent's one for the sentence's OK. */
@Composable
private fun CatchUpButton(label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    PillButton(
        label,
        if (primary) c.accent else JournalInk.white(0x14),
        if (primary) PageInk.OnAccent else c.text,
        enabled,
        onClick,
    )
}

@Composable
private fun PillButton(
    label: String,
    fill: Color,
    ink: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        Modifier.alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
        color = ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.5.sp,
    )
}
