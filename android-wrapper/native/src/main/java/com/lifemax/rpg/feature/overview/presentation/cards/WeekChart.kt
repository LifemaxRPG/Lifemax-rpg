package com.lifemax.rpg.feature.overview.presentation.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.feature.overview.domain.WeekBar
import com.lifemax.rpg.feature.overview.domain.WeekPace
import com.lifemax.rpg.feature.overview.presentation.cards.CardsBGeometry as G

/**
 * `.ov-chart`: seven days of XP stacked by attribute, under the dashed pace line. The days are one
 * selectable group for TalkBack, each read by the page's aria-label; [onPick] is a day's tap.
 *
 * Each day's tap area spans its share of the row, the 7 px gap included (52 dp at the phone's
 * width, where the bar itself is 45), so the bars sit exactly where the page's do.
 */
@Composable
internal fun WeekChart(
    bars: List<WeekBar>,
    pace: WeekPace,
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // the day's letter's line at the reader's text size: 15 dp at the page's
    val labelLine = with(LocalDensity.current) { (10.sp * 1.5f).toDp().value }
    val column = G.column(labelLine)
    Box(modifier.fillMaxWidth().padding(top = 2.dp).height(G.CHART.dp)) {
        Row(
            Modifier.fillMaxSize()
                .layout { m, cs ->
                    // half the 7 px gap reaches past each end, so the outer bars meet the edges
                    val half = (BarGap / 2).roundToPx()
                    val p = m.measure(cs.copy(maxWidth = cs.maxWidth + 2 * half, minWidth = 0))
                    layout(cs.maxWidth, p.height) { p.place(-half, 0) }
                }
                .selectableGroup()
        ) {
            bars.forEachIndexed { i, b ->
                Day(b, i == selected, column, { onPick(i) }, Modifier.weight(1f))
            }
        }
        PaceLine(pace, G.paceBottom(pace.frac, labelLine))
    }
}

@Composable
private fun Day(
    b: WeekBar,
    picked: Boolean,
    column: Float,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val c = LocalPageColors.current
    Box(
        modifier
            .fillMaxHeight()
            .selectable(picked, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = b.a11y }
            .padding(horizontal = BarGap / 2)
    ) {
        Column(
            Modifier.fillMaxSize()
                .clip(DayShape)
                .background(if (picked) c.text.copy(alpha = .08f) else Color.Transparent)
                .padding(top = G.BAR_PAD_TOP.dp, bottom = G.BAR_PAD_BOTTOM.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(G.BAR_GAP.dp, Alignment.Bottom),
        ) {
            Stack(b, column, Modifier.weight(1f).fillMaxWidth())
            Text(
                b.label,
                Modifier.clearAndSetSemantics {},
                color =
                    when {
                        picked -> c.text
                        b.today -> c.gold
                        else -> c.muted
                    },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                lineHeight = 15.sp,
                // "Today" may spill past a narrow bar at large text, as the page's would
                softWrap = false,
                overflow = TextOverflow.Visible,
            )
        }
    }
}

/**
 * `.ov-bs`: the day's segments from the bottom up, each its attribute's colour fading to 73 %
 * (`bb`) with a faint dark top edge, in a column rounded 8 px above and 4 px below.
 */
@Composable
private fun Stack(b: WeekBar, column: Float, modifier: Modifier) {
    val c = LocalPageColors.current
    Column(modifier.clip(StackShape), verticalArrangement = Arrangement.Bottom) {
        if (b.segs.isEmpty()) {
            Box(
                Modifier.fillMaxWidth()
                    .height(G.EMPTY_DAY.dp.coerceAtMost(column.dp))
                    .clip(EmptyShape)
                    .background(c.line)
                    .drawBehind { drawRect(SegEdge, size = size.copy(height = 1.dp.toPx())) }
            )
            return@Column
        }
        val heights = G.segments(b.segs.map { it.frac }, column)
        // a Column stacks downwards: the last segment (the top one) first
        b.segs.indices.reversed().forEach { i ->
            val color = Color(b.segs[i].color.toInt())
            Box(
                Modifier.fillMaxWidth()
                    .height(heights[i].dp)
                    .background(Brush.verticalGradient(listOf(color, color.copy(alpha = SegFade))))
                    .drawBehind { drawRect(SegEdge, size = size.copy(height = 1.dp.toPx())) }
            )
        }
    }
}

/**
 * `.ov-pace`: a 1.5 px dashed line [bottom] dp above the chart's foot, over the bars, with its
 * label at the right end resting on it on a patch of the card's colour.
 */
@Composable
private fun PaceLine(pace: WeekPace, bottom: Float) {
    val c = LocalPageColors.current
    val ink = c.text.copy(alpha = .34f)
    val y = (G.CHART - bottom).dp
    Box(Modifier.fillMaxSize().clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxWidth().height(1.5.dp).offset(y = y)) {
            val w = 1.5.dp.toPx()
            drawLine(
                ink,
                Offset(0f, w / 2),
                Offset(size.width, w / 2),
                strokeWidth = w,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(Dash.toPx(), DashGap.toPx())),
            )
        }
        Text(
            pace.value,
            Modifier.align(Alignment.TopEnd)
                .offset(y = y - 17.dp)
                .background(c.panel)
                .padding(horizontal = 3.dp),
            color = c.muted,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.57.sp,
            lineHeight = 14.25.sp,
        )
    }
}

/** The colour a segment fades to at its foot (`<colour>bb`) and its top edge (`#0003`). */
private const val SegFade = 0xBB / 255f
private val SegEdge = Color(0x33000000)
private val BarGap = 7.dp
private val Dash = 2.7.dp
private val DashGap = 2.5.dp
private val DayShape = RoundedCornerShape(10.dp)
private val StackShape = RoundedCornerShape(8.dp, 8.dp, 4.dp, 4.dp)
private val EmptyShape = RoundedCornerShape(3.dp)
