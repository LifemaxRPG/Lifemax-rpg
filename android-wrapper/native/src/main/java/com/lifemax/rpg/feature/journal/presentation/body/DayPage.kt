package com.lifemax.rpg.feature.journal.presentation.body

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.cssBlurRadius
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.JournalEmpty
import com.lifemax.rpg.feature.journal.domain.JournalEntry
import com.lifemax.rpg.feature.journal.domain.ReviewEntry

/**
 * The taps the Journal's body makes (§3.4, Phase 1): `select {key}` comes from the tiles; these
 * come from the entry. [onWrite] is `write {key}` (the reflection box or a review's answer box),
 * [onSwap] is `swap {d, from}`, [onChip] a welcome chip's claim, catch-up or hand-over.
 */
internal class JournalBodyCallbacks(
    val onWrite: (key: String) -> Unit = {},
    val onSwap: (day: String, from: String) -> Unit = { _, _ -> },
    val onChip: (ChipTap) -> Unit = {},
)

/**
 * The entry (#view-history .journal-entry): the selected day or review on its card, or the empty
 * Journal's message when there are no days at all.
 */
@Composable
internal fun JournalEntryPage(
    entry: JournalEntry?,
    empty: JournalEmpty?,
    callbacks: JournalBodyCallbacks,
    modifier: Modifier = Modifier,
) {
    JournalEntryCard(modifier.fillMaxWidth().heightIn(min = 280.dp)) {
        when {
            entry is DayEntry -> DayPage(entry, callbacks)
            entry is ReviewEntry -> ReviewPage(entry, callbacks.onWrite)
            empty != null -> EmptyJournal(empty)
        }
    }
}

/**
 * A day's page: the head and counts, the coach's cards (or the care note in their place), the
 * reflection box, then the timeline, the workout notes or the blank state. The page's vertical
 * margins meet as CSS margins do (the larger of two wins), so the gaps are worked out here.
 */
@Composable
internal fun ColumnScope.DayPage(day: DayEntry, callbacks: JournalBodyCallbacks) {
    // each child of the card arrives in turn (EntryArrival.kt); `at` is the next one's place
    var at = 1
    Arrive(at++) {
        BigHead(day.head.number, 58.sp, day.head.weekday, day.head.month) {
            DayXp(day.head.xp, day.head.xpLabel)
        }
    }
    Arrive(at++) { Meta(day.meta) }
    // the meta's 4 dp under it, then each card's margin (welcome and insight 14, care 12) and the
    // box's 18 above it
    var below: Dp = 4.dp
    fun gap(above: Dp) = max(below, above)
    if (day.care != null) {
        Spacer(Modifier.height(gap(12.dp)))
        Arrive(at++) { CareNote(day.care) }
        below = 12.dp
    } else {
        day.welcome?.let {
            Spacer(Modifier.height(gap(14.dp)))
            Arrive(at++) { WelcomeCard(it, callbacks.onChip) }
            below = 14.dp
        }
        day.insight?.let {
            Spacer(Modifier.height(gap(14.dp)))
            Arrive(at++) { InsightCard(it) }
            below = 14.dp
        }
    }
    Spacer(Modifier.height(gap(18.dp)))
    Arrive(at++) { ReflectionBox(day.key, day.reflect, callbacks.onWrite, callbacks.onSwap) }
    when {
        day.blank != null -> Arrive(at) { BlankDay(day.blank) }
        else -> {
            if (day.timeline.isNotEmpty()) Arrive(at++) { Timeline(day.timeline) }
            // after the last item's own 10 dp, or the empty timeline's 18
            day.workoutNotes?.let {
                Arrive(at) {
                    WorkoutNotesCard(
                        it,
                        Modifier.padding(top = if (day.timeline.isEmpty()) 18.dp else 0.dp),
                    )
                }
            }
        }
    }
}

/**
 * The head (.jx-dayhead): the gradient number ([size] 58 for a day, 46 for a review's badge), the
 * name and its second line, and what [end] adds on the right.
 */
@Composable
internal fun BigHead(
    number: String,
    size: TextUnit,
    title: String,
    line: String,
    end: @Composable () -> Unit = {},
) {
    val c = LocalJournalBodyColors.current
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            number,
            style =
                TextStyle(
                    brush = c.bigNumber,
                    fontSize = size,
                    fontWeight = FontWeight(850),
                    letterSpacing = size * -0.06f,
                    lineHeight = size * 0.9f,
                ),
            maxLines = 1,
            softWrap = false,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = c.text,
                fontSize = 19.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-0.57).sp,
                lineHeight = 23.sp,
            )
            Text(line, color = c.muted, fontSize = 12.5.sp, lineHeight = 18.75.sp)
        }
        end()
    }
}

/** The day's XP total (.jx-dayxp): green with its glow, and its label under it. */
@Composable
private fun DayXp(xp: String, label: String) {
    val c = LocalJournalBodyColors.current
    val glow = with(LocalDensity.current) { cssBlurRadius(22.dp.toPx()) }
    Column(horizontalAlignment = Alignment.End) {
        Text(
            xp,
            style =
                TextStyle(
                    color = c.good,
                    fontSize = 26.sp,
                    fontWeight = FontWeight(800),
                    letterSpacing = (-1.04).sp,
                    lineHeight = 26.sp,
                    shadow = Shadow(JournalInk.XpGlow, blurRadius = glow),
                ),
        )
        Text(
            label.uppercase(),
            color = c.muted,
            fontSize = 10.sp,
            letterSpacing = 0.6.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.End,
        )
    }
}

/** The counts under the head (.jx-meta): small pills. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Meta(meta: List<String>) {
    val c = LocalJournalBodyColors.current
    FlowRow(
        Modifier.padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        meta.forEach {
            Text(
                it,
                Modifier.clip(CircleShape)
                    .background(JournalInk.white(0x0A))
                    .border(1.dp, c.lineSoft, CircleShape)
                    .padding(horizontal = 11.dp, vertical = 4.dp),
                color = c.muted,
                fontSize = 11.5.sp,
                lineHeight = 17.25.sp,
            )
        }
    }
}

/** The empty Journal (.empty): the ◷, the title in bold and the page's sentence, centred. */
@Composable
private fun ColumnScope.EmptyJournal(empty: JournalEmpty) {
    val c = LocalJournalBodyColors.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 36.dp).semantics(
            mergeDescendants = true
        ) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            empty.icon,
            Modifier.padding(bottom = 8.dp),
            color = c.muted.copy(alpha = 0.7f),
            fontSize = 28.sp,
            lineHeight = 42.sp,
        )
        Text(
            empty.title,
            color = c.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            empty.body,
            color = c.muted,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            textAlign = TextAlign.Center,
        )
    }
}
