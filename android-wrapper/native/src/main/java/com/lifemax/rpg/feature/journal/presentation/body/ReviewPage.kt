package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.core.designsystem.cssLinearGradient
import com.lifemax.rpg.feature.journal.domain.HeatCell
import com.lifemax.rpg.feature.journal.domain.ReviewBar
import com.lifemax.rpg.feature.journal.domain.ReviewChart
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import com.lifemax.rpg.feature.journal.domain.ReviewGridItem

/**
 * A weekly or monthly review (47-journal-coach.js jcReviewHtml): the badge as the big number with
 * the headline and range, the chart (a bar a day for a week, a heat grid for a month), the numbers
 * two to a row, the coach's amber card and the answer box. [onWrite] is `write {key}`.
 */
@Composable
internal fun ColumnScope.ReviewPage(
    review: ReviewEntry,
    onWrite: (String) -> Unit,
    editor: EntryEditor? = null,
) {
    Arrive(1) { BigHead(review.head.badge, 46.sp, review.head.title, review.head.range) }
    Arrive(2) {
        when (val chart = review.chart) {
            is ReviewChart.Bars -> Bars(chart.bars)
            is ReviewChart.Heat -> Heat(chart)
        }
    }
    // the chart's bottom margin (8 or 6) meets the grid's 12, the grid's 12 the card's 14, and
    // the card's 14 the box's 18
    Arrive(3) { Grid(review.grid, Modifier.padding(top = 12.dp)) }
    Arrive(4) {
        CoachCard(
            review.coach.badge,
            JournalInk.Coach,
            review.coach.text,
            null,
            Modifier.padding(top = 14.dp),
        )
    }
    Spacer(Modifier.height(18.dp))
    Arrive(5) { ReviewAnswerBox(review, onWrite, editor) }
}

/**
 * The week's bars (.jx-bars): 136 dp tall, a column a day with its XP above and its letter below.
 */
@Composable
private fun Bars(bars: List<ReviewBar>) {
    Row(
        Modifier.padding(top = 18.dp)
            .fillMaxWidth()
            .height(136.dp)
            .padding(start = 4.dp, end = 4.dp, top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        bars.forEach { BarColumn(it, Modifier.weight(1f).fillMaxHeight()) }
    }
}

/**
 * One day's column: the value, the bar and the letter, bottom aligned 5 dp apart. The bar is its
 * percent of the column's height, but, as in the page's flex column, it gives way so the value and
 * the letter still fit, and never goes under 3 dp.
 */
@Composable
private fun BarColumn(bar: ReviewBar, modifier: Modifier) {
    val c = LocalJournalBodyColors.current
    val tint = argb(bar.color)
    val shape = RoundedCornerShape(8.dp, 8.dp, 4.dp, 4.dp)
    Layout(
        content = {
            Text(
                bar.value,
                color = c.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.sp,
            )
            Box(
                Modifier.glow(tint, 20.dp, 6.dp, spread = (-8).dp, dy = 8.dp)
                    .clip(shape)
                    .background(cssLinearGradient(180f, 0f to tint, 1f to c.tint(tint, 0.38f)))
            )
            Text(
                bar.label,
                color = if (bar.today) c.accentStrong else c.muted2,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 15.75.sp,
            )
        },
        modifier =
            modifier.clearAndSetSemantics {
                contentDescription = "${bar.label} ${bar.value}".trim()
            },
    ) { (value, column, label), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val v = value.measure(loose)
        val l = label.measure(loose)
        val h = constraints.maxHeight
        val gap = 5.dp.roundToPx()
        val room = h - v.height - l.height - 2 * gap
        val barH =
            minOf((h * bar.height.coerceIn(0, 100) / 100f).toInt(), room)
                .coerceAtLeast(3.dp.roundToPx())
        val b = column.measure(Constraints.fixed(constraints.maxWidth, barH))
        layout(constraints.maxWidth, h) {
            var y = h - l.height
            l.place((constraints.maxWidth - l.width) / 2, y)
            y -= gap + barH
            b.place(0, y)
            y -= gap + v.height
            v.place((constraints.maxWidth - v.width) / 2, y)
        }
    }
}

/** The month's heat grid (.jx-heat): the weekday letters, then the days in weeks of seven. */
@Composable
private fun Heat(heat: ReviewChart.Heat) {
    val c = LocalJournalBodyColors.current
    Column(
        Modifier.padding(top = 16.dp).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            heat.weekdays.forEach {
                Text(
                    it,
                    Modifier.weight(1f),
                    color = c.muted2,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        heatRows(heat.lead, heat.cells).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (i in 0 until 7) {
                    val cell = week.getOrNull(i)
                    if (cell == null) Spacer(Modifier.weight(1f).aspectRatio(1f))
                    else HeatSquare(cell, Modifier.weight(1f))
                }
            }
        }
    }
}

/** A day of the month (.jx-hc): its colour at its strength with a glow, or faint when quiet. */
@Composable
private fun HeatSquare(cell: HeatCell, modifier: Modifier) {
    val c = LocalJournalBodyColors.current
    val tint = argb(cell.color)
    val shape = RoundedCornerShape(10.dp)
    val shade = with(LocalDensity.current) { cssBlurRadius(3.dp.toPx()) to 1.dp.toPx() }
    Box(
        modifier
            .aspectRatio(1f)
            .then(if (cell.off) Modifier else Modifier.glow(tint, 14.dp, 10.dp, spread = (-6).dp))
            .clip(shape)
            .background(if (cell.off) JournalInk.white(0x0A) else c.tint(tint, cell.alpha))
            .clearAndSetSemantics { contentDescription = cell.label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            cell.day,
            style =
                TextStyle(
                    color = if (cell.off) c.muted2 else Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.5.sp,
                    shadow =
                        if (cell.off) null
                        else Shadow(Color(0x88000000), Offset(0f, shade.second), shade.first),
                ),
        )
    }
}

/** The numbers (.jc-grid), two to a row, each row as tall as its taller box. */
@Composable
private fun Grid(items: List<ReviewGridItem>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { pair ->
            Row(
                Modifier.height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { GridBox(it, Modifier.weight(1f).fillMaxHeight()) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GridBox(item: ReviewGridItem, modifier: Modifier) {
    val c = LocalJournalBodyColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(
                cssLinearGradient(180f, 0f to JournalInk.white(0x0A), 1f to JournalInk.white(0x04))
            )
            .border(1.dp, c.lineSoft, shape)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Text(
            item.label.uppercase(),
            Modifier.padding(bottom = 2.dp),
            color = c.muted,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            lineHeight = 15.sp,
        )
        Text(
            item.value,
            color = c.text,
            fontSize = 17.sp,
            fontWeight = FontWeight(800),
            letterSpacing = (-0.34).sp,
            lineHeight = 25.5.sp,
        )
    }
}
