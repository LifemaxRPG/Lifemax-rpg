package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.feature.journal.domain.JournalBlank
import com.lifemax.rpg.feature.journal.domain.PlanNote
import com.lifemax.rpg.feature.journal.domain.TimelineItem
import com.lifemax.rpg.feature.journal.domain.WorkoutNotes

/*
 * What was logged on a day, in time order (04-runtime.js renderJournalDay, .jx-timeline): the time,
 * a glowing node on the line, and a card with the pixel icon, the name, the attribute pill, the
 * minutes and skill, the XP, the notes and the plan session's notes. Then the workout notes card,
 * or the blank state for a day with nothing logged.
 */

/** The unit under an item's XP (.jx-xpv small), which the page writes into its markup. */
private const val XP_UNIT = "XP"

/** The timeline: the faint line behind the nodes, 61 dp in, and an item per activity. */
@Composable
internal fun Timeline(items: List<TimelineItem>) {
    Column(
        Modifier.padding(top = 18.dp).fillMaxWidth().drawBehind {
            val x = 61.dp.toPx()
            val top = 10.dp.toPx()
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(JournalInk.white(0x1C), JournalInk.white(0x08)),
                    startY = top,
                    endY = size.height - top,
                ),
                Offset(x, top),
                Size(2.dp.toPx(), size.height - 2 * top),
                CornerRadius(2.dp.toPx()),
            )
        }
    ) {
        items.forEach { TimelineRow(it) }
    }
}

/** An item: the time (48 dp, right aligned), the node (26 dp) and the card. */
@Composable
private fun TimelineRow(item: TimelineItem) {
    val c = LocalJournalBodyColors.current
    val tint = argb(item.color)
    Row(Modifier.padding(bottom = 10.dp).semantics(mergeDescendants = true) {}) {
        Text(
            item.time,
            Modifier.width(48.dp).padding(top = 12.dp),
            color = c.muted2,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 16.5.sp,
            textAlign = TextAlign.End,
        )
        Box(Modifier.width(26.dp), contentAlignment = Alignment.TopCenter) {
            Box(
                Modifier.padding(top = 14.dp)
                    .size(12.dp)
                    .glow(tint, 16.dp, 6.dp)
                    .ring(c.tint(tint, 0.22f), 4.dp, 6.dp)
                    .clip(CircleShape)
                    .background(tint)
            )
        }
        ActivityCard(Modifier.weight(1f), edge = tint) {
            CardTop(item.icon, item.name, item.xp) { Sub(item) }
            item.notes?.let { Notes(it) }
            item.plan?.let { PlanNotes(it) }
        }
    }
}

/**
 * A card (.jx-card): the panel fading down, its soft border and small shadow; a timeline item's
 * card has the activity's colour as a 3 dp left edge.
 */
@Composable
private fun ActivityCard(
    modifier: Modifier = Modifier,
    edge: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .journalShadow(c, 16.dp, small = true)
            .clip(shape)
            .background(cssLinearGradient(180f, 0f to c.panel2, 1f to c.panel))
            .then(
                if (edge != null) Modifier.leftEdgeBorder(c.lineSoft, edge, 3.dp, 16.dp)
                else Modifier.border(1.dp, c.lineSoft, shape)
            )
            .padding(
                start = if (edge != null) 16.dp else 14.dp,
                end = 14.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        content = content,
    )
}

/** The card's top: the icon box, the name with what [sub] adds under it, and the XP. */
@Composable
private fun CardTop(
    icon: String,
    name: String,
    xp: String? = null,
    sub: @Composable () -> Unit = {},
) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(11.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(36.dp)
                .clip(shape)
                .background(c.iconBox)
                .border(1.dp, c.iconBoxBorder, shape),
            contentAlignment = Alignment.Center,
        ) {
            PixelIcon(icon, c.accent, Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = c.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.21).sp,
                lineHeight = 21.sp,
            )
            sub()
        }
        if (xp != null) Xp(xp)
    }
}

/** The pill, minutes and skill under the name (.jx-sub). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Sub(item: TimelineItem) {
    FlowRow(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val middle = Modifier.align(Alignment.CenterVertically)
        item.attr?.let { Pill(it.text, argb(it.color), middle) }
        item.minutes?.let { SubText(it, middle) }
        item.skill?.let { SubText(it, middle) }
    }
}

@Composable
private fun SubText(text: String, modifier: Modifier) {
    val c = LocalJournalBodyColors.current
    Text(text, modifier, color = c.muted2, fontSize = 11.5.sp, lineHeight = 17.25.sp)
}

/** The attribute pill (.jx-pill), in the attribute's colour. */
@Composable
private fun Pill(text: String, color: Color, modifier: Modifier) {
    val c = LocalJournalBodyColors.current
    Text(
        text,
        modifier
            .clip(CircleShape)
            .background(c.tint(color, 0.15f))
            .border(1.dp, c.tint(color, 0.28f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 2.dp),
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 15.75.sp,
    )
}

/** The XP (.jx-xpv): green, with the unit under it. */
@Composable
private fun Xp(xp: String) {
    val c = LocalJournalBodyColors.current
    Column(horizontalAlignment = Alignment.End) {
        Text(
            xp,
            color = c.good,
            fontSize = 17.sp,
            fontWeight = FontWeight(800),
            letterSpacing = (-0.51).sp,
            lineHeight = 17.sp,
        )
        Text(
            XP_UNIT,
            Modifier.padding(top = 2.dp),
            color = c.muted,
            fontSize = 9.sp,
            letterSpacing = 0.72.sp,
            lineHeight = 9.sp,
        )
    }
}

/** An activity's notes (.jx-card .j-notes) on a darker ground, as written. */
@Composable
private fun Notes(text: String) {
    val c = LocalJournalBodyColors.current
    Text(
        text,
        Modifier.padding(top = 9.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(JournalInk.black(0x26))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        color = c.textDim,
        fontSize = 13.sp,
        lineHeight = 18.85.sp,
    )
}

/** The plan session's notes (.j-plan-logs): the exercise in bold, then " · weight — note". */
@Composable
private fun PlanNotes(notes: List<PlanNote>) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier.padding(top = 8.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(c.bgElev)
            .border(1.dp, c.lineSoft, shape)
            .padding(horizontal = 11.dp, vertical = 9.dp)
    ) {
        notes.forEach { n ->
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.SemiBold)) {
                        append(n.label)
                    }
                    if (n.weight.isNotEmpty()) append(" · " + n.weight)
                    if (n.note.isNotEmpty()) append(" — " + n.note)
                },
                Modifier.padding(vertical = 3.dp),
                color = c.textDim,
                fontSize = 12.5.sp,
                lineHeight = 18.75.sp,
            )
        }
    }
}

/** The workout notes of a day with no plan session logged (.jx-card.jx-plan). */
@Composable
internal fun WorkoutNotesCard(notes: WorkoutNotes, modifier: Modifier = Modifier) {
    ActivityCard(modifier.fillMaxWidth()) {
        CardTop("dumbbell", notes.title)
        PlanNotes(notes.notes)
    }
}

/** A day with nothing logged (.jx-empty): the ✎ box, the title and the page's sentence. */
@Composable
internal fun BlankDay(blank: JournalBlank) {
    val c = LocalJournalBodyColors.current
    Column(
        Modifier.padding(top = 20.dp, bottom = 6.dp)
            .fillMaxWidth()
            .dashedBorder(c.line, 18.dp)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 17.dp, vertical = 27.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.padding(bottom = 6.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.accentDim),
            contentAlignment = Alignment.Center,
        ) {
            Text(blank.icon, color = c.accentStrong, fontSize = 20.sp, lineHeight = 30.sp)
        }
        Text(
            blank.title,
            color = c.text,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.75.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            blank.body,
            color = c.muted,
            fontSize = 13.sp,
            lineHeight = 19.5.sp,
            textAlign = TextAlign.Center,
        )
    }
}
