package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * Life Director's bottom sheet (the page's .ld-sheet panel): the screen dimmed around it, a panel
 * up to 560 dp wide above the shared bottom bar ([bottomInset], which a native screen does not
 * cover), its grabber, the close button and its pane title. A tap on the dimmed screen or the close
 * button is [onDismiss]; a tap on the panel stays there without making the panel a button, so
 * TalkBack reads its heading, words and buttons one by one. [content] is laid out in the panel's
 * scrolling column.
 */
@Composable
fun BoxScope.DirectorSheet(
    title: String,
    bottomInset: Dp,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        Modifier.matchParentSize()
            .background(P.scrim)
            .clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            .clearAndSetSemantics {}
    )
    val shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Box(
        Modifier.align(Alignment.BottomCenter)
            .padding(bottom = bottomInset)
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .boxShadow(P.sheetShadow, 70.dp, shape, dy = (-24).dp)
            .clip(shape)
            .background(sheetFill(LocalDensity.current))
            .border(1.dp, P.line2, shape)
            .pointerInput(Unit) { detectTapGestures {} }
            .semantics { paneTitle = title }
    ) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(40.dp, 5.dp)
                .background(P.line2, RoundedCornerShape(5.dp))
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 26.dp),
            content = content,
        )
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 11.dp, end = 5.dp)
                .clip(RoundedCornerShape(10.dp))
                .tapTarget(Role.Button, onClick = onDismiss)
                .semantics { contentDescription = CLOSE },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "×",
                Modifier.clearAndSetSemantics {},
                style = DirectorText,
                color = P.ink3,
                fontSize = 20.sp,
                lineHeight = 1.em,
            )
        }
    }
}

/** A sheet's heading, clear of the close button. */
@Composable
fun SheetTitle(title: String) =
    Text(
        title,
        Modifier.padding(end = 40.dp).semantics { heading() },
        style = DirectorText,
        fontSize = 20.8.sp,
        fontWeight = FontWeight(750),
        letterSpacing = (-.03).em,
        lineHeight = 1.2.em,
    )

/** A paragraph of .ld-muted text. */
@Composable
fun SheetMuted(text: String, size: Float) {
    if (text.isEmpty()) return
    Text(
        text,
        Modifier.padding(vertical = 5.dp),
        style = DirectorText,
        color = P.ink3,
        fontSize = size.sp,
        lineHeight = 1.5.em,
    )
}

private const val CLOSE = "Close"

/** The panel's fill: #151b29 into #0e121b over its first 260 dp. */
private fun sheetFill(density: Density) =
    Brush.verticalGradient(
        listOf(P.sheetTop, P.sheetBottom),
        startY = 0f,
        endY = with(density) { 260.dp.toPx() },
    )
