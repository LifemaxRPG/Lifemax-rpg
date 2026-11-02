package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.journal.domain.JournalCare
import com.lifemax.rpg.feature.journal.domain.JournalInsight
import com.lifemax.rpg.feature.journal.domain.JournalWelcome
import com.lifemax.rpg.feature.journal.domain.WelcomeChip

/*
 * The coach's cards above the reflection box (47-journal-coach.js jcTop, the Journal pane's
 * .jc-card, .jc-welcome and .jc-care): the insight with its pattern line, the welcome-back card
 * with its moon and chips, and the care note.
 */

/**
 * A coach card (.jc-card): the [color]'s 4 dp left edge, its tint fading across the card and its
 * low glow, the badge, the sentence and, when there is one, the pattern line. The review's coach
 * card is the same card in amber with no pattern.
 */
@Composable
internal fun CoachCard(
    badge: String,
    color: Color,
    text: String,
    pattern: String?,
    modifier: Modifier = Modifier,
) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .glow(color, 34.dp, 18.dp, spread = (-22).dp, dy = 14.dp, clipOut = false)
            .clip(shape)
            .background(c.panel2)
            .background(
                cssLinearGradient(135f, 0f to c.tint(color, 0.17f), 0.75f to Color.Transparent)
            )
            .leftEdgeBorder(color.mix(0.3f, c.line), color, 4.dp, 18.dp)
            .semantics(mergeDescendants = true) {}
            .padding(start = 18.dp, end = 17.dp, top = 15.dp, bottom = 15.dp)
    ) {
        CoachBadge(badge, color)
        Text(
            text,
            Modifier.padding(top = 8.dp),
            color = c.textDim,
            fontSize = 14.5.sp,
            lineHeight = 22.475.sp,
        )
        if (pattern != null) PatternLine(pattern, color)
    }
}

/** The insight card for a day. */
@Composable
internal fun InsightCard(insight: JournalInsight) =
    CoachCard(insight.label, argb(insight.color), insight.text, insight.pattern)

/** The badge on a coach card (.jc-badge): uppercase, on the card's colour, with its glow. */
@Composable
private fun CoachBadge(text: String, color: Color) {
    Text(
        text.uppercase(),
        Modifier.glow(color, 14.dp, 99.dp, spread = (-4).dp, dy = 4.dp)
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        color = JournalInk.OnBadge,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.735.sp,
        lineHeight = 15.75.sp,
    )
}

/** The pattern line (.jc-pat): a dot in the card's colour with its ring, then the sentence. */
@Composable
private fun PatternLine(text: String, color: Color) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.padding(top = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(JournalInk.black(0x22))
            .border(1.dp, JournalInk.white(0x14), shape)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            Modifier.padding(top = 5.dp)
                .size(8.dp)
                .ring(c.tint(color, 0.25f), 4.dp, 4.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text, color = c.muted, fontSize = 13.sp, lineHeight = 18.85.sp)
    }
}

/**
 * The welcome-back card (.jc-welcome): the amber glow in its top right corner over the panel, the
 * crescent moon, the head, the sentence and the chips. A claimed chip is faded and does nothing;
 * [onChip] gets what an open chip asks for.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WelcomeCard(
    welcome: JournalWelcome,
    editor: EntryEditor? = null,
    onChip: (ChipTap) -> Unit,
) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(cssLinearGradient(180f, 0f to c.panel2, 1f to c.panel))
            .drawBehind {
                // radial-gradient(120% 140% at 100% 0%, #f59e0b2e, transparent 60%)
                val rx = size.width * 1.2f
                val ry = size.height * 1.4f
                scale(1f, ry / rx, Offset(size.width, 0f)) {
                    drawCircle(
                        Brush.radialGradient(
                            0f to Color(0x2EF59E0B),
                            0.6f to Color.Transparent,
                            center = Offset(size.width, 0f),
                            radius = rx,
                        ),
                        radius = rx,
                        center = Offset(size.width, 0f),
                    )
                }
            }
            .border(1.dp, Color(0x55F59E0B), shape)
    ) {
        Moon(c.panel, Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 12.dp))
        Column(Modifier.padding(start = 17.dp, end = 17.dp, top = 19.dp, bottom = 17.dp)) {
            Text(
                welcome.head,
                Modifier.padding(end = 64.dp),
                color = c.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(800),
                letterSpacing = (-0.57).sp,
                lineHeight = 28.5.sp,
            )
            Text(
                welcome.body,
                Modifier.padding(top = 6.dp, bottom = 10.dp, end = 10.dp),
                color = c.muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                welcome.chips.forEach { chip -> Chip(chip) { chipTap(chip)?.let(onChip) } }
            }
            // phase 2: the catch-up's sentence is typed here, under the chips
            if (editor != null && editor.catchUp.open) CatchUpPanel(editor)
        }
    }
}

/** A welcome chip (.jc-chip): a pill with a faint white fill; faded once [WelcomeChip.done]. */
@Composable
private fun Chip(chip: WelcomeChip, onClick: () -> Unit) {
    val c = LocalJournalBodyColors.current
    Text(
        chip.label,
        Modifier.alpha(if (chip.done) 0.5f else 1f)
            .clip(CircleShape)
            .background(
                cssLinearGradient(180f, 0f to JournalInk.white(0x18), 1f to JournalInk.white(0x08))
            )
            .border(1.dp, JournalInk.white(0x2E), CircleShape)
            .then(
                if (chip.done) Modifier.semantics { disabled() }
                else Modifier.clickable(role = Role.Button, onClick = onClick)
            )
            .padding(horizontal = 15.dp, vertical = 9.dp),
        color = c.text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 19.5.sp,
    )
}

/**
 * The page's crescent moon (a 64-unit svg at 54 dp): an amber disc lit from its top left, covered
 * by a disc of the panel's colour, with the amber drop shadow under both.
 */
@Composable
private fun Moon(cover: Color, modifier: Modifier) {
    // the svg's opacity .95 is on each colour: a faded layer would clip the shadow to the box
    Canvas(modifier.size(54.dp)) {
        val u = size.width / 64f
        val disc = Offset(32 * u, 32 * u)
        val bite = Offset(43 * u, 26 * u)
        val both =
            Path().apply {
                op(
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(disc, 22 * u)) },
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(bite, 19 * u)) },
                    PathOperation.Union,
                )
            }
        translate(top = 6.dp.toPx()) {
            drawIntoCanvas { it.drawPath(both, glowPaint(Color(0x66F59E0B).fade(), 16.dp.toPx())) }
        }
        drawCircle(
            Brush.radialGradient(
                listOf(JournalInk.MoonLight.fade(), JournalInk.Amber.fade()),
                center = Offset(disc.x - 22 * u + 0.35f * 44 * u, disc.y - 22 * u + 0.3f * 44 * u),
                radius = 22 * u,
            ),
            radius = 22 * u,
            center = disc,
        )
        drawCircle(cover.fade(), radius = 19 * u, center = bite)
    }
}

private fun Color.fade() = copy(alpha = alpha * 0.95f)

/** The care note (.jc-care): the lead in bold, then the page's words, on a soft blue. */
@Composable
internal fun CareNote(care: JournalCare) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(18.dp)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(care.lead) }
            append(" ")
            append(care.text)
        },
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Color(0x1238BDF8))
            .border(1.dp, Color(0x6638BDF8), shape)
            .padding(15.dp),
        color = c.text,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    )
}
