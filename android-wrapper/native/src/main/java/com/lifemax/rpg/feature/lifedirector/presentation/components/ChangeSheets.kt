package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.lifedirector.presentation.ChangeConfirm
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * The page's question before a change that cannot be taken back (remove a goal, reset), in its
 * words, with Keep.
 */
@Composable
fun BoxScope.ConfirmChangeSheet(
    c: ChangeConfirm,
    bottomInset: Dp,
    onConfirm: () -> Unit,
    onKeep: () -> Unit,
) =
    DirectorSheet(c.title, bottomInset, onKeep) {
        SheetTitle(c.title)
        SheetMuted(c.text, 14.4f)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectorButton(c.button, ButtonLook.PRIMARY, onConfirm)
            DirectorButton(KEEP, ButtonLook.GHOST, onKeep)
        }
    }

/** Restore backup: the page's words, a field to paste the backup into, and Restore. */
@Composable
fun BoxScope.RestoreSheet(
    title: String,
    text: String,
    button: String,
    draft: String,
    bottomInset: Dp,
    onDraft: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
) =
    DirectorSheet(title, bottomInset, onClose) {
        SheetTitle(title)
        SheetMuted(text, 13.76f)
        val shape = RoundedCornerShape(12.dp)
        BasicTextField(
            draft,
            onDraft,
            Modifier.fillMaxWidth()
                .heightIn(min = 140.dp)
                .padding(vertical = 10.dp)
                .clip(shape)
                .background(androidx.compose.ui.graphics.Color(0xFF0B1019))
                .border(1.dp, P.line2, shape)
                .padding(12.dp)
                .semantics { contentDescription = title },
            textStyle =
                DirectorText.copy(
                    color = P.ink,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
            cursorBrush = SolidColor(P.sky),
        )
        DirectorButton(button, ButtonLook.PRIMARY, { if (draft.isNotBlank()) onRestore() })
    }

private const val KEEP = "Keep"
