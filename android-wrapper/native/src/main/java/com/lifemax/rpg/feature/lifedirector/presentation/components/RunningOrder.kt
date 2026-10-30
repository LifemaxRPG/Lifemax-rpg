package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * "Rest of today" (or "Running order" for another day) and how many sessions it holds. No margins:
 * on the web, .ld-root h4 outranks .ld-sec's.
 */
@Composable
fun OrderHeading(isToday: Boolean, sessions: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            if (isToday) "Rest of today" else "Running order",
            Modifier.alignByBaseline().semantics { heading() },
            style = DirectorText,
            fontSize = 16.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-.02).em,
        )
        if (sessions > 0)
            Text(
                "$sessions session${if (sessions == 1) "" else "s"}",
                Modifier.alignByBaseline(),
                style = DirectorText,
                color = P.ink3,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
    }
}

/** The fold over the blocks already behind you today; [open] shows them below it. */
@Composable
fun EarlierFold(summary: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics { stateDescription = if (open) "Expanded" else "Collapsed" }
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Earlier today",
            style = DirectorText,
            color = P.ink2,
            fontSize = 13.8.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(summary, style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
    }
}

/** The card the earlier blocks sit in (.ld-earlier), cut to this item's [slice] of it. */
fun Modifier.earlierCard(slice: Slice) = cardSlice(slice, 14.dp, SolidColor(P.faintFill), P.line)

/**
 * One block of the running order (the web's roHtml): its time and length, a rail in its colour, its
 * title with state, quests, goal and XP, and a note. A session is a card; a block already gone is
 * dimmed. Tapping a session or a meal opens it on the page.
 */
@Composable
fun OrderRowView(row: OrderRow, onTap: (Tap) -> Unit, modifier: Modifier = Modifier) {
    val session = row.state != SessionState.NONE
    val shape = RoundedCornerShape(12.dp)
    val color = P.of(row.kind)
    val rail =
        when {
            row.state == SessionState.OFF -> P.block.copy(alpha = .5f)
            row.state == SessionState.DONE -> color.copy(alpha = .5f)
            session -> color
            row.kind == BlockKind.FREE -> P.free.copy(alpha = .3f * .75f)
            else -> color.copy(alpha = .75f)
        }
    val glowing = session && row.state != SessionState.DONE && row.state != SessionState.OFF
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = if (session) 4.dp else 0.dp)
            .then(if (session) Modifier.sessionCard(row.state, shape) else Modifier)
            .clip(shape)
            .semantics(mergeDescendants = true) { contentDescription = row.description() }
            .then(
                row.tap?.let { tap ->
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { onTap(tap) }
                } ?: Modifier
            )
            .alpha(if (row.past && !session) .55f else 1f)
            .padding(
                top = if (session) 6.dp else 5.dp,
                bottom = if (session) 6.dp else 5.dp,
                end = if (session) 10.dp else 0.dp,
            )
            .rail(rail, glowing),
        horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP),
    ) {
        Column(
            Modifier.width(TIME_COLUMN).padding(top = 6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                row.time,
                style = DirectorText,
                color = P.ink2,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.8.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.2.em,
            )
            Text(row.length, style = DirectorText, color = P.ink3, fontSize = 11.2.sp)
        }
        Spacer(Modifier.width(RAIL))
        Column(Modifier.weight(1f).padding(vertical = 5.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelIcon(row.icon, color.copy(alpha = .8f), Modifier.size(13.dp))
                Text(
                    row.title,
                    Modifier.weight(1f, fill = false),
                    style = DirectorText,
                    color =
                        when {
                            session || row.now -> P.ink
                            row.kind == BlockKind.FREE -> P.ink3
                            else -> P.ink2
                        },
                    fontSize = if (session) 16.sp else 14.4.sp,
                    fontWeight =
                        when {
                            session -> FontWeight.Bold
                            row.kind == BlockKind.FREE -> FontWeight.Medium
                            else -> FontWeight.SemiBold
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.stateLabel.isNotEmpty()) StatePill(row)
                if (row.now) Pill("Now", P.nowBlue, P.nowBlue.copy(alpha = .16f), weight = 750)
            }
            if (row.chips.isNotEmpty()) ChipRow(row.chips, Modifier.padding(top = 6.dp))
            if (row.sub.isNotEmpty())
                Text(
                    row.sub,
                    style = DirectorText,
                    color = P.ink3,
                    fontSize = 12.2.sp,
                    lineHeight = 1.45.em,
                )
        }
    }
}

@Composable
private fun StatePill(row: OrderRow) {
    val (color, fill) =
        when (row.state) {
            SessionState.DONE -> P.mint to P.task.copy(alpha = .16f)
            SessionState.PART -> P.amber to P.work.copy(alpha = .14f)
            SessionState.OFF -> P.rose to P.block.copy(alpha = .12f)
            else -> P.ink3 to P.pillFill
        }
    Pill(row.stateLabel, color, fill)
}

/** Something came up, or re-plan from now: both run on the page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DirectorFooter(onTap: (Tap) -> Unit) {
    // 10 dp above, not .ld-foot's 18: on the web, .ld-root .ld-row outranks it
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DirectorButton("Something came up", ButtonLook.PLAIN, { onTap(Tap("disrupt")) })
        DirectorButton("Re-plan from now", ButtonLook.GHOST, { onTap(Tap("replan")) })
    }
}

private fun OrderRow.description() =
    (listOf(time, length, title, stateLabel, if (now) "Now" else "") + chips.map { it.text } + sub)
        .filter { it.isNotEmpty() }
        .joinToString(", ")

private fun Modifier.sessionCard(state: SessionState, shape: RoundedCornerShape) =
    when (state) {
        SessionState.DONE -> background(P.faintFill, shape).border(1.dp, P.line, shape)
        SessionState.OFF ->
            background(P.block.copy(alpha = .04f), shape)
                .border(1.dp, P.block.copy(alpha = .16f), shape)
        else ->
            background(
                    Brush.horizontalGradient(
                        listOf(P.task.copy(alpha = .09f), P.task.copy(alpha = .025f))
                    ),
                    shape,
                )
                .border(1.dp, P.task.copy(alpha = .16f), shape)
    }

/** A row's columns: the time, then the rail in the space kept for it, then the block. */
private val TIME_COLUMN = 62.dp
private val COLUMN_GAP = 12.dp
private val RAIL = 4.dp

/**
 * The rail in the block's colour, in its space after the time column and as tall as the row; a live
 * session's glows.
 */
private fun Modifier.rail(color: Color, glowing: Boolean) = drawWithCache {
    val left = (TIME_COLUMN + COLUMN_GAP).toPx()
    val width = RAIL.toPx()
    val corner = CornerRadius(width)
    val glow = if (glowing) glowPaint(P.task.copy(alpha = .5f), 10.dp.toPx()) else null
    val railSize = Size(width, size.height.coerceAtLeast(22.dp.toPx()))
    onDrawBehind {
        glow?.let { paint ->
            drawIntoCanvas {
                it.drawRoundRect(left, 0f, left + width, railSize.height, width, width, paint)
            }
        }
        drawRoundRect(color, Offset(left, 0f), railSize, corner)
    }
}
