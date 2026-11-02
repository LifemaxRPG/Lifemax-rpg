package com.lifemax.rpg.feature.journal.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.pageButtonShadow
import com.lifemax.rpg.feature.journal.domain.LockCard
import com.lifemax.rpg.feature.journal.domain.LockStatus

/**
 * The lock card (.jl-card), drawn from a withheld frame, which holds nothing of the Journal: the
 * lock, the page's heading and sentence, Unlock, and the error line under it. Unlock waits (faded,
 * as the page's disabled .btn) while the system prompt is up or [unlocking] is in flight; the error
 * line keeps its height when empty, so the card never jumps when an error comes or goes.
 */
@Composable
internal fun LockCardView(
    card: LockCard,
    status: LockStatus,
    unlocking: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalJournalColors.current
    val waiting = status.prompting || unlocking
    // .jl-overlay: 18 px from the view's sides (6 dp inside the screen's 12), 34 px under its top
    Box(
        modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, top = 36.dp),
        Alignment.TopCenter,
    ) {
        Column(
            Modifier.widthIn(max = 340.dp)
                .fillMaxWidth()
                .then(with(c) { Modifier.journalShadow(CardShape) })
                .clip(CardShape)
                .background(c.panelFade)
                .background(c.lockGlow)
                .border(1.dp, c.line, CardShape)
                .padding(start = 23.dp, end = 23.dp, top = 29.dp, bottom = 25.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LockIcon()
            Text(
                card.title,
                Modifier.padding(top = 14.dp, bottom = 6.dp).semantics { heading() },
                color = c.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                lineHeight = 25.sp,
                textAlign = TextAlign.Center,
            )
            Text(
                card.body,
                Modifier.padding(bottom = 18.dp),
                color = c.muted,
                fontSize = 13.5.sp,
                lineHeight = 20.25.sp,
                textAlign = TextAlign.Center,
            )
            UnlockButton(card.unlock, enabled = !waiting, onUnlock)
            Text(
                status.error,
                Modifier.padding(top = 10.dp).heightIn(min = 18.dp).semantics {
                    liveRegion = LiveRegionMode.Polite
                },
                color = c.bad,
                fontSize = 12.5.sp,
                lineHeight = 18.75.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** The lock in its tile (.jl-ico): the page's padlock, on the accent's dim fill, with its glow. */
@Composable
private fun LockIcon() {
    val c = LocalJournalColors.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.size(64.dp)
            .boxShadow(c.accent, 30.dp, shape, dy = 10.dp, spread = (-10).dp, clipOutBox = true)
            .clip(shape)
            .background(c.accentDim)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(30.dp)) {
            // the page's SVG, a 24 unit view box drawn at 30 px, stroke 1.8
            val u = size.width / 24f
            val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            drawRoundRect(
                c.accentStrong,
                Offset(5 * u, 11 * u),
                Size(14 * u, 9 * u),
                CornerRadius(2.5f * u),
                style = stroke,
            )
            val shackle =
                Path().apply {
                    moveTo(8 * u, 11 * u)
                    lineTo(8 * u, 8 * u)
                    cubicTo(8 * u, 5.79f * u, 9.79f * u, 4 * u, 12 * u, 4 * u)
                    cubicTo(14.21f * u, 4 * u, 16 * u, 5.79f * u, 16 * u, 8 * u)
                    lineTo(16 * u, 11 * u)
                }
            drawPath(shackle, c.accentStrong, style = stroke)
            drawCircle(c.accentStrong, 1.2f * u, Offset(12 * u, 15.5f * u))
            drawCircle(c.accentStrong, 1.2f * u, Offset(12 * u, 15.5f * u), style = stroke)
        }
    }
}

/**
 * The card's Unlock (.btn): the accent button with its light top edge, faded and deaf while it
 * waits. It keeps the page's drawn height; Compose widens its touch area to 48 dp.
 */
@Composable
private fun UnlockButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalJournalColors.current
    val shape = RoundedCornerShape(8.dp)
    val edge = if (c.light) ButtonEdgeLight else ButtonEdgeDark
    Box(
        // the 48 dp touch area lies around the drawn 41 dp button, waiting or not, so it never
        // moves
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = text }
            .alpha(if (enabled) 1f else 0.45f)
            .pageButtonShadow(shape, c.light)
            .clip(shape)
            .background(c.accent)
            .drawBehind { drawRect(edge, size = Size(size.width, 1.dp.toPx())) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = c.onAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            lineHeight = 21.sp,
            softWrap = false,
        )
    }
}

private val CardShape = RoundedCornerShape(26.dp)
private val ButtonEdgeDark = androidx.compose.ui.graphics.Color(0x22FFFFFF)
private val ButtonEdgeLight = androidx.compose.ui.graphics.Color(0x44FFFFFF)
