package com.lifemax.rpg.feature.plan.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.feature.plan.domain.DayStatus
import com.lifemax.rpg.feature.plan.domain.PlanDay
import com.lifemax.rpg.feature.plan.domain.PlanWeek

/** A week of the journey: its number, a ✓ ● ○ mark, the sessions done and a meter. */
@Composable
internal fun PlanWeekTile(week: PlanWeek, active: Boolean, onClick: () -> Unit) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(16.dp)
    val done = if (week.total > 0) week.done.toFloat() / week.total else 0f
    Column(
        Modifier.width(101.dp)
            .heightIn(min = 126.dp)
            .then(
                if (active) Modifier.boxShadow(c.accent.copy(alpha = 0.15f), 18.dp, shape, 6.dp)
                else Modifier
            )
            // the page's progress line, just inside the bottom border and over the tile's
            // corners; only its right end is rounded
            .drawWithCache {
                val inset = 1.dp.toPx()
                val thick = 3.dp.toPx()
                val end = CornerRadius(thick / 2)
                val line =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                Rect(
                                    Offset(inset, size.height - inset - thick),
                                    Size((size.width - 2 * inset) * done, thick),
                                ),
                                topRight = end,
                                bottomRight = end,
                            )
                        )
                    }
                onDrawWithContent {
                    drawContent()
                    drawPath(line, if (active) c.accent else c.good, if (active) 1f else 0.8f)
                }
            }
            .clip(shape)
            .background(if (active) c.weekActive else c.weekIdle)
            .border(
                1.dp,
                when {
                    active -> c.accent
                    week.current -> c.gold
                    else -> c.lineSoft
                },
                shape,
            )
            .selectable(active, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription =
                    listOfNotNull(
                            "Week ${week.number.trimStart('0')}",
                            week.meta,
                            "this week".takeIf { week.current },
                            "complete".takeIf { week.complete },
                        )
                        .joinToString(", ")
            }
            .padding(start = 10.dp, top = 10.dp, end = 10.dp, bottom = 9.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                week.number,
                color = if (active) c.accentStrong else c.textDim,
                fontSize = 11.sp,
                fontWeight = FontWeight(850),
                letterSpacing = 0.66.sp,
                lineHeight = 11.sp,
            )
            WeekMark(week.complete, active)
        }
        Text(
            week.label,
            Modifier.padding(top = 10.dp),
            color = if (active) c.accentStrong else c.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight(850),
            letterSpacing = 0.66.sp,
            lineHeight = 16.5.sp,
            maxLines = 1,
        )
        Text(
            week.meta,
            Modifier.padding(top = 5.dp).heightIn(min = 25.dp),
            color = if (active) c.text else c.muted2,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.66.sp,
            lineHeight = 13.75.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(3.dp)) {
            Box(
                Modifier.fillMaxWidth(done)
                    .fillMaxHeight()
                    .background(c.good, RoundedCornerShape(9.dp))
            )
        }
    }
}

/** ✓ for a finished week, ● for the selected one, ○ otherwise. */
@Composable
private fun WeekMark(complete: Boolean, active: Boolean) {
    val c = LocalPlanColors.current
    Box(
        Modifier.size(19.dp)
            .then(
                if (active) Modifier.boxShadow(c.accent.copy(alpha = 0.38f), 15.dp, CircleShape)
                else Modifier
            )
            .background(if (active) c.accent else c.bgElev, CircleShape)
            .border(
                1.dp,
                when {
                    complete -> c.good
                    active -> c.accent
                    else -> c.line
                },
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                complete -> "✓"
                active -> "●"
                else -> "○"
            },
            color =
                when {
                    complete -> c.good
                    active -> SelectedWeekInk
                    else -> c.muted2
                },
            fontSize = 11.sp,
            fontWeight = FontWeight(750),
            lineHeight = 11.sp,
        )
    }
}

/** A day of the selected week: weekday, date, the session's letter or icon, its kind, ✓ or ×. */
@Composable
internal fun PlanDayTile(day: PlanDay, selected: Boolean, onClick: () -> Unit) {
    val c = LocalPlanColors.current
    val shape = RoundedCornerShape(17.dp)
    val goldDate = day.today && !selected
    val accent = if (selected) c.accentStrong else null
    Box(
        Modifier.offset(y = if (selected) (-1).dp else 0.dp)
            .width(72.dp)
            .heightIn(min = 140.dp)
            .alpha(if (day.rest) 0.68f else 1f)
            .then(
                if (selected) Modifier.boxShadow(c.accent.copy(alpha = 0.13f), 20.dp, shape, 8.dp)
                else Modifier
            )
            .clip(shape)
            .background(if (selected) c.daySelected else c.dayIdle)
            .border(
                1.dp,
                when {
                    goldDate -> c.gold
                    day.status == DayStatus.MISSED -> c.missedBorder
                    day.status == DayStatus.DONE -> c.doneBorder
                    selected -> c.accent
                    else -> c.lineSoft
                },
                shape,
            )
            .selectable(selected, role = Role.Tab, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = day.description
                stateDescription = day.statusWords()
            }
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 7.dp, top = 10.dp, end = 7.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                day.weekday,
                color = accent ?: c.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight(850),
                letterSpacing = 0.7.sp,
                lineHeight = 15.sp,
            )
            Text(
                day.dayOfMonth,
                Modifier.padding(top = 8.dp),
                color = if (goldDate) c.gold else c.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(if (goldDate) 800 else 850),
                letterSpacing = (-1.14).sp,
                lineHeight = 19.sp,
            )
            Box(
                Modifier.padding(top = 8.dp)
                    .size(38.dp, 34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) c.selectedIconFill else c.panel2)
                    .border(
                        1.dp,
                        if (selected) c.selectedIconBorder else c.lineSoft,
                        RoundedCornerShape(11.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val ink = accent ?: c.textDim
                if (day.glyph.isNotEmpty())
                    Text(
                        day.glyph,
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 14.sp,
                    )
                else PixelIcon(day.icon, ink, Modifier.size(18.dp))
            }
            Text(
                day.kindLabel,
                Modifier.padding(top = 15.dp).heightIn(min = 16.dp),
                color = accent ?: c.muted2,
                fontSize = 8.sp,
                fontWeight = FontWeight(850),
                letterSpacing = 0.48.sp,
                lineHeight = 8.sp,
                textAlign = TextAlign.Center,
            )
        }
        when (day.status) {
            DayStatus.DONE -> "✓" to c.good
            DayStatus.MISSED -> "×" to c.bad
            else -> null
        }?.let { (mark, color) ->
            Text(
                mark,
                Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 7.dp),
                color = color,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                lineHeight = 15.sp,
            )
        }
    }
}

/** The legend's word for a day's state. */
private fun PlanDay.statusWords(): String {
    val status =
        when (status) {
            DayStatus.DONE -> "Completed"
            DayStatus.MISSED -> "Missed"
            DayStatus.TODAY -> "Today"
            DayStatus.REST -> "Rest day"
            DayStatus.UPCOMING -> "Upcoming"
        }
    return if (today && status != "Today") "$status, today" else status
}
