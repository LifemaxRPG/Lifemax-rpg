package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanKeyTag
import com.lifemax.rpg.feature.plan.domain.PlanProjection
import com.lifemax.rpg.feature.plan.domain.PlanWeek
import kotlin.math.hypot

/**
 * The calendar card: the program's hero and sessions count, the eight weeks, the selected week's
 * days, the legend and the program's key.
 */
@Composable
internal fun PlanCalendarCard(
    p: PlanProjection,
    day: PlanDay,
    onWeek: (Int) -> Unit,
    onDay: (String) -> Unit,
) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(25.dp)
    val selectedWeek = p.weekOf(day)
    val week = p.weeks[selectedWeek]
    Column(
        Modifier.fillMaxWidth()
            .boxShadow(CardShadow, 65.dp, 25.dp, 24.dp)
            .clip(shape)
            .background(c.panel)
            .border(1.dp, c.calendarBorder, shape)
    ) {
        Hero(p)
        HorizontalDivider(color = c.lineSoft)
        Journey()
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, top = 5.dp, end = 16.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(p.weeks, key = { _, w -> w.number }) { i, w ->
                PlanWeekTile(w, active = i == selectedWeek) { onWeek(i) }
            }
        }
        HorizontalDivider(color = c.lineSoft)
        DailyMissions(week)
        // a new week's days start from its Monday, as the page draws them
        key(week.number) {
            LazyRow(
                contentPadding =
                    PaddingValues(start = 16.dp, top = 5.dp, end = 16.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                // a day the plan does not have (before it starts) is an empty slot, as on the page
                itemsIndexed(week.days, key = { i, d -> d?.date ?: "none-$i" }) { _, d ->
                    if (d == null) Spacer(Modifier)
                    else PlanDayTile(d, selected = d.date == day.date) { onDay(d.date) }
                }
            }
        }
        HorizontalDivider(color = c.lineSoft)
        LegendAndKey(p.key)
    }
}

@Composable
private fun Hero(p: PlanProjection) {
    val c = LocalPlanColors.current
    Column(
        Modifier.fillMaxWidth()
            .background(c.hero)
            .padding(start = 16.dp, top = 19.dp, end = 16.dp, bottom = 17.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "TRAINING PROTOCOL · 08 WEEKS",
                    color = c.accentStrong,
                    fontSize = 9.sp,
                    fontWeight = FontWeight(850),
                    letterSpacing = 1.71.sp,
                    lineHeight = 13.5.sp,
                )
                Text(
                    "Built by consistency.",
                    Modifier.padding(top = 8.dp).widthIn(max = 230.dp),
                    color = c.text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight(850),
                    letterSpacing = (-1.56).sp,
                    lineHeight = 25.92.sp,
                )
                Text(
                    "One session at a time. Every rep moves you forward.",
                    Modifier.padding(top = 7.dp).widthIn(max = 205.dp),
                    color = c.muted,
                    fontSize = 11.sp,
                    lineHeight = 16.5.sp,
                )
            }
            SessionsCount(p.sessionsDone, p.sessionsTotal)
        }
        Box(
            Modifier.padding(top = 18.dp)
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape)
                .background(c.line)
        ) {
            Box(
                Modifier.fillMaxWidth(p.sessionsDone.toFloat() / p.sessionsTotal.coerceAtLeast(1))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(c.cycle)
            )
        }
    }
}

/** The round "done/total SESSIONS" badge, lit from its upper left like the page's. */
@Composable
private fun SessionsCount(done: Int, total: Int) {
    val c = LocalPlanColors.current
    Column(
        Modifier.size(72.dp)
            .boxShadow(CountShadow, 22.dp, 36.dp, 8.dp)
            .drawWithCache {
                val light = Offset(size.width * 0.35f, size.height * 0.25f)
                val glow =
                    Brush.radialGradient(
                        0f to c.countGlow,
                        0.7f to c.bgElev,
                        center = light,
                        radius = hypot(size.width - light.x, size.height - light.y),
                    )
                val ring = Stroke(5.dp.toPx())
                val edge = Stroke(1.dp.toPx())
                onDrawBehind {
                    drawCircle(glow)
                    drawCircle(c.countRing, size.minDimension / 2 - 3.5.dp.toPx(), style = ring)
                    drawCircle(c.countBorder, size.minDimension / 2 - 0.5.dp.toPx(), style = edge)
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$done of $total sessions done"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Text(
            buildAnnotatedString {
                append("$done")
                withStyle(
                    SpanStyle(color = c.muted, fontSize = 12.sp, fontWeight = FontWeight(600))
                ) {
                    append("/$total")
                }
            },
            color = c.text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-1.14).sp,
            lineHeight = 19.sp,
        )
        Text(
            "SESSIONS",
            color = c.muted,
            fontSize = 8.sp,
            fontWeight = FontWeight(800),
            letterSpacing = 0.8.sp,
            lineHeight = 12.sp,
        )
    }
}

@Composable
private fun Journey() {
    val c = LocalPlanColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 17.dp, end = 16.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "THE JOURNEY",
            Modifier.semantics { heading() },
            color = c.textDim,
            fontSize = 9.sp,
            fontWeight = FontWeight(800),
            letterSpacing = 1.8.sp,
            lineHeight = 15.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Swipe to explore",
                color = c.accentStrong,
                fontSize = 11.sp,
                fontWeight = FontWeight(650),
                lineHeight = 16.5.sp,
            )
            Box(
                Modifier.padding(start = 4.dp).size(19.dp).clip(CircleShape).background(c.panel2),
                contentAlignment = Alignment.Center,
            ) {
                Text("↔", color = c.accentStrong, fontSize = 12.sp, lineHeight = 12.sp)
            }
        }
    }
}

@Composable
private fun DailyMissions(week: PlanWeek) {
    val c = LocalPlanColors.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, top = 18.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text(
                "Daily missions",
                Modifier.semantics { heading() },
                color = c.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.68).sp,
                lineHeight = 19.55.sp,
            )
            Text(
                buildAnnotatedString {
                    append(week.label)
                    withStyle(SpanStyle(color = c.muted2)) { append("  /  ") }
                    append(week.range)
                },
                Modifier.padding(top = 5.dp),
                color = c.accentStrong,
                fontSize = 10.sp,
                fontWeight = FontWeight(750),
                letterSpacing = 0.7.sp,
                lineHeight = 14.sp,
            )
        }
        Row(
            Modifier.padding(bottom = 0.5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(6.dp)
                    .boxShadow(c.accent, 9.dp, 3.dp)
                    .background(c.accent, CircleShape)
            )
            Text(
                "SWIPE",
                color = c.muted2,
                fontSize = 9.sp,
                fontWeight = FontWeight(850),
                letterSpacing = 1.62.sp,
                lineHeight = 13.5.sp,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendAndKey(key: List<PlanKeyTag>) {
    val c = LocalPlanColors.current
    Column(
        Modifier.fillMaxWidth()
            .background(c.bottomFill)
            .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = 19.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            listOf(
                    c.good to "Completed",
                    c.bad to "Missed",
                    c.gold to "Today",
                    c.line to "Upcoming",
                )
                .forEach { (color, label) ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(7.dp).background(color, CircleShape))
                        Text(label, color = c.muted, fontSize = 10.sp, lineHeight = 15.sp)
                    }
                }
        }
        FlowRow(
            Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            key.forEach { tag ->
                Row(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(c.panel2)
                        .border(1.dp, c.keyTagBorder, RoundedCornerShape(9.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (tag.icon.isNotEmpty()) PixelIcon(tag.icon, c.tagText, Modifier.size(13.dp))
                    Text(
                        tag.label,
                        color = c.tagText,
                        fontSize = 10.sp,
                        letterSpacing = 0.1.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}
