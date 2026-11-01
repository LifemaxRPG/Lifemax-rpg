package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.lifedirector.domain.DialConfirm
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * The page's confirmation of a dial change (confirmDial in logic.js, the .ld-sheet panel): the
 * question, the time struck through and the new one, then either the problem and OK, or what the
 * change does with Change it, Keep and the small print. The dimmed screen around it, the close
 * button and Back all keep the time. It sits above the shared bottom bar ([bottomInset]), which a
 * native screen does not cover. A tap on the panel stays there without making the panel a button:
 * TalkBack reads its heading, words and buttons one by one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BoxScope.DialConfirmSheet(
    confirm: DialConfirm,
    bottomInset: Dp,
    onApply: () -> Unit,
    onKeep: () -> Unit,
) {
    Box(
        Modifier.matchParentSize()
            .background(P.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onKeep)
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
            .semantics { paneTitle = confirm.title }
    ) {
        Box(
            Modifier.align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .size(40.dp, 5.dp)
                .background(P.line2, RoundedCornerShape(5.dp))
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 40.dp, bottom = 26.dp)
        ) {
            Text(
                confirm.title,
                Modifier.padding(end = 40.dp).semantics { heading() },
                style = DirectorText,
                fontSize = 20.8.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-.03).em,
                lineHeight = 1.2.em,
            )
            Change(confirm.from, confirm.to)
            if (confirm.problem.isNotEmpty()) {
                Text(
                    confirm.problem,
                    Modifier.padding(vertical = 10.dp)
                        .fillMaxWidth()
                        .background(P.warnNote, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    style = DirectorText,
                    color = P.amber,
                    fontSize = 14.08.sp,
                )
                DirectorButton(confirm.keep, ButtonLook.PRIMARY, onKeep)
            } else {
                Muted(confirm.body, 13.76f)
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DirectorButton(confirm.apply, ButtonLook.PRIMARY, onApply)
                    DirectorButton(confirm.keep, ButtonLook.GHOST, onKeep)
                }
                Muted(confirm.note, 12.48f)
            }
        }
        Box(
            Modifier.align(Alignment.TopEnd)
                .padding(top = 11.dp, end = 5.dp)
                .clip(RoundedCornerShape(10.dp))
                .tapTarget(Role.Button, onClick = onKeep)
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

/**
 * The old time struck through, an arrow, the new time in bold (.ld-change). The strike and the
 * arrow are silent, so TalkBack hears "From 11:00 PM to 10:30 PM".
 */
@Composable
private fun Change(from: String, to: String) {
    Row(
        Modifier.padding(top = 14.dp, bottom = 6.dp).clearAndSetSemantics {
            contentDescription = "From $from to $to"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val mono = DirectorText.copy(fontFamily = FontFamily.Monospace, letterSpacing = (-.03).em)
        Text(
            from,
            style = mono,
            color = P.ink3,
            fontSize = 17.6.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = TextDecoration.LineThrough,
        )
        Text("→", style = mono, color = P.ink3, fontSize = 17.6.sp, fontWeight = FontWeight.Bold)
        Text(to, style = mono, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

/** A paragraph of .ld-muted text. */
@Composable
private fun Muted(text: String, size: Float) {
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
