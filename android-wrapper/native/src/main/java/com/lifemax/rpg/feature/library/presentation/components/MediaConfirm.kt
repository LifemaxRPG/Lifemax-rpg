package com.lifemax.rpg.feature.library.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.feature.library.presentation.MediaAskUi

/**
 * One of the page's confirms (Remove, Remove audio) drawn over the pane: the page's question about
 * the item, word for word, then Cancel and the confirming button named as the row's button is. A
 * tap on the dimmed pane or Back is Cancel, as the page's confirm dismissed is.
 */
@Composable
internal fun BoxScope.MediaConfirm(
    ask: MediaAskUi,
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalLibraryColors.current
    BackHandler(enabled = visible, onBack = onDismiss)
    Box(
        Modifier.matchParentSize()
            .background(Scrim)
            .clearAndSetSemantics {}
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
    )
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.align(Alignment.Center)
            .padding(horizontal = 28.dp)
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.line, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = ask.confirm }
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(ask.text, color = c.text, fontSize = 15.sp, lineHeight = 22.sp)
        Row(
            Modifier.align(Alignment.End),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LibraryButton(ask.cancel, onDismiss)
            LibraryButton(ask.confirm, onConfirm, primary = true)
        }
    }
}
