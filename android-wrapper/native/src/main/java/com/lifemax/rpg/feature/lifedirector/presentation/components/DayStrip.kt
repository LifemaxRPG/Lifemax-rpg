package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.StripDay
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P

/**
 * The days of the plan, seven across, each with a dot per session (the web's stripHtml). Choosing
 * one asks the page for that day; [selected] is the day on screen or the one asked for.
 */
@Composable
fun DayStrip(days: List<StripDay>, selected: Int, onDay: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp)) {
        val chip = (maxWidth - 30.dp) / 7
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            days.forEachIndexed { i, day ->
                DayChip(day, i == selected, Modifier.width(chip)) { onDay(i) }
            }
        }
    }
}

@Composable
private fun DayChip(day: StripDay, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier
            .heightIn(min = 66.dp)
            .then(
                if (on)
                    Modifier.boxShadow(P.free.copy(alpha = .14f), 18.dp, shape)
                        .background(P.free.copy(alpha = .14f), shape)
                        .border(1.dp, P.free.copy(alpha = .42f), shape)
                else Modifier
            )
            .clip(shape)
            .semantics {
                selected = on
                contentDescription = day.description()
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(top = 7.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            day.label,
            style = DirectorText,
            color = if (on) P.sky else P.ink3,
            fontSize = 10.9.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
        )
        Text(
            "${day.date}",
            style = DirectorText,
            color = if (on) P.ink else P.ink2,
            fontSize = 17.9.sp,
            fontWeight = FontWeight(650),
            lineHeight = 19.7.sp,
        )
        Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            day.dots.forEach { state ->
                when (state) {
                    SessionState.DONE -> GlowDot(5.dp, P.task, 5.dp)
                    SessionState.OFF -> GlowDot(5.dp, P.block.copy(alpha = .6f), 0.dp)
                    else -> GlowDot(5.dp, P.task.copy(alpha = .45f), 0.dp)
                }
            }
        }
    }
}

/** "Today 28, 1 done, 2 planned, 1 skipped": the chip's words and what its dots say. */
private fun StripDay.description() =
    (listOf("$label $date") +
            dots.groupingBy { it.spoken() }.eachCount().map { (state, n) -> "$n $state" })
        .joinToString(", ")

/** A session's state as TalkBack says it. */
internal fun SessionState.spoken(): String =
    when (this) {
        SessionState.DONE -> "done"
        SessionState.PART -> "part done"
        SessionState.OFF -> "skipped"
        SessionState.PLAN,
        SessionState.NONE -> "planned"
    }
