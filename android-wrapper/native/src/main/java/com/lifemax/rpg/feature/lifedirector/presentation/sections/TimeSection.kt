package com.lifemax.rpg.feature.lifedirector.presentation.sections

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.form.CheckField
import com.lifemax.rpg.core.designsystem.form.FormKitLook
import com.lifemax.rpg.core.designsystem.form.LocalPickerFieldLook
import com.lifemax.rpg.core.designsystem.form.TimeField
import com.lifemax.rpg.core.designsystem.form.pickers
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.BuildCard
import com.lifemax.rpg.feature.lifedirector.domain.CommitmentDot
import com.lifemax.rpg.feature.lifedirector.domain.Commitments
import com.lifemax.rpg.feature.lifedirector.domain.RoutineRow
import com.lifemax.rpg.feature.lifedirector.domain.Routines
import com.lifemax.rpg.feature.lifedirector.domain.SetupStep
import com.lifemax.rpg.feature.lifedirector.domain.SetupStepState
import com.lifemax.rpg.feature.lifedirector.domain.TimeGoals
import com.lifemax.rpg.feature.lifedirector.domain.TimeSleep
import com.lifemax.rpg.feature.lifedirector.presentation.SleepDraft
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDialWords
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniSleepDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MinuteSpan

/*
 * The page's My time (renderSetup in logic.js, the "my time" rules of style.css): set-up's steps, the lede, the sleep card with
 * its mini dial, the commitments, the daily routines, goals and Build my week (or Re-plan now). Every word is the page's.
 */

/** What My time can ask for; each write is the page's own `do` command (TimeController). */
class TimeActions(
    val sleep: (bed: Int, wake: Int) -> Unit = { _, _ -> },
    val weekend: (Boolean) -> Unit = {},
    val weekendTimes: (bed: Int, wake: Int) -> Unit = { _, _ -> },
    val saveSleep: () -> Unit = {},
    /** a commitment's sheet; "" for a new one */
    val rule: (id: String) -> Unit = {},
    /** − (-1) or + (1) on a routine */
    val routine: (key: String, by: Int) -> Unit = { _, _ -> },
    val meals: (on: Boolean) -> Unit = {},
    val addGoal: () -> Unit = {},
    val build: () -> Unit = {},
)

/**
 * .ld-steps: the four set-up steps side by side, numbered; done ones ticked, the current one lit.
 */
@Composable
fun SetupSteps(steps: List<SetupStep>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        steps.forEachIndexed { k, s ->
            val shape = RoundedCornerShape(11.dp)
            val (fill, edge, ink, number) =
                when (s.state) {
                    SetupStepState.CUR ->
                        listOf(P.free.copy(alpha = .1f), P.free.copy(alpha = .45f), P.ink, P.sky)
                    SetupStepState.OK ->
                        listOf(P.restCard, P.task.copy(alpha = .3f), P.mint, P.task)
                    SetupStepState.TODO -> listOf(P.restCard, P.line, P.ink3, P.ink3)
                }
            Column(
                Modifier.weight(1f)
                    .clip(shape)
                    .background(fill, shape)
                    .border(1.dp, edge, shape)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (s.state == SetupStepState.OK) "✓" else "${k + 1}",
                    Modifier.clearAndSetSemantics {},
                    style = NumText.copy(letterSpacing = 0.em),
                    color = number,
                    fontSize = 14.4.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    s.label,
                    style = DirectorText,
                    color = ink,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight(650),
                    maxLines = 1,
                )
            }
        }
    }
}

/** .ld-lede: the line under the steps, in the second ink. */
@Composable
fun TimeLede(text: String) =
    Text(
        text,
        Modifier.padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 12.dp),
        style = DirectorText,
        color = P.ink2,
        fontSize = 14.7.sp,
    )

/** The page's .ld-card: a faint white wash, a hairline edge, 18 round; the cards 12 apart. */
@Composable
fun TimeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(P.cardTop, P.cardBottom)), shape)
            .border(1.dp, P.line, shape)
            .padding(16.dp)
    ) {
        content()
    }
}

/** .ld-card h4: the card's icon in the third ink, then its title. */
@Composable
fun TimeCardTitle(title: String, icon: String?) {
    Row(
        Modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { PixelIcon(it, P.ink3, Modifier.size(14.dp)) }
        Text(
            title,
            style = DirectorText,
            color = P.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight(750),
            letterSpacing = (-.02).em,
        )
    }
}

/**
 * The sleep card: the note, the mini dial over the times shown ([shown]: the draft, or the page's),
 * the two weeknight fields, the weekend switch and its fields, and Save sleep. [h12] is the page's
 * clock style for a time picked here.
 */
@Composable
fun SleepCard(
    s: TimeSleep,
    shown: SleepDraft,
    h12: Boolean,
    pending: Boolean,
    reducedMotion: Boolean,
    a: TimeActions,
    dialModifier: Modifier = Modifier,
) = TimeCard {
    CompositionLocalProvider(
        LocalPickerFieldLook provides FormKitLook().pickers(),
        LocalTextStyle provides DirectorText,
    ) {
        Column {
            TimeCardTitle(s.title, "moon")
            Muted(s.note)
            MiniSleepDial(
                miniDial(s),
                shown.bed,
                shown.wake,
                s.face.radii,
                s.face.hourWords,
                a.sleep,
                dialModifier.padding(top = 6.dp, bottom = 4.dp),
                enabled = !pending,
                reducedMotion = reducedMotion,
            )
            // a time the page wrote is shown in its words; one picked here in the page's style
            fun said(m: Int, saved: Int, text: String) = text.takeIf { m == saved }
            Row(
                Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimeField(
                    s.labels.bed,
                    shown.bed,
                    { a.sleep(it, shown.wake) },
                    Modifier.weight(1f),
                    enabled = !pending,
                    h12 = h12,
                    text = said(shown.bed, s.bed, s.bedText) ?: stopText(s, shown.bed),
                )
                TimeField(
                    s.labels.wake,
                    shown.wake,
                    { a.sleep(shown.bed, it) },
                    Modifier.weight(1f),
                    enabled = !pending,
                    h12 = h12,
                    text = said(shown.wake, s.wake, s.wakeText) ?: stopText(s, shown.wake),
                )
            }
            CheckField(
                s.labels.weekend,
                shown.weekendOn,
                a.weekend,
                Modifier.padding(vertical = 2.dp),
                enabled = !pending,
            )
            if (shown.weekendOn)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeField(
                        s.labels.weekendBed,
                        shown.weekendBed,
                        { a.weekendTimes(it, shown.weekendWake) },
                        Modifier.weight(1f),
                        enabled = !pending,
                        h12 = h12,
                        text = stopText(s, shown.weekendBed),
                    )
                    TimeField(
                        s.labels.weekendWake,
                        shown.weekendWake,
                        { a.weekendTimes(shown.weekendBed, it) },
                        Modifier.weight(1f),
                        enabled = !pending,
                        h12 = h12,
                        text = stopText(s, shown.weekendWake),
                    )
                }
            DirectorButton(
                s.save.label,
                if (s.save.primary) ButtonLook.PRIMARY else ButtonLook.PLAIN,
                { if (!pending) a.saveSleep() },
                Modifier.padding(top = if (shown.weekendOn) 10.dp else 0.dp)
                    .alpha(if (pending) .6f else 1f),
            )
        }
    }
}

/**
 * The page's words for a time on one of the dial's stops; null off the stops (the field writes it).
 */
private fun stopText(s: TimeSleep, m: Int): String? =
    s.clock.getOrNull(m / s.step)?.takeIf { m % s.step == 0 }

/** The mini dial's model from the page's sleep card. */
internal fun miniDial(s: TimeSleep) =
    MiniDial(
        bed = s.bed,
        wake = s.wake,
        bedText = s.bedText,
        wakeText = s.wakeText,
        length = s.length,
        step = s.step,
        context = s.context.map { MinuteSpan(it.from, it.to) },
        clock = s.clock,
        lengths = s.lengths,
        words =
            MiniDialWords(s.labels.centre, s.labels.bedHandle, s.labels.wakeHandle, s.labels.group),
    )

/** The commitments: a row each (its sheet on a tap), or the empty words; then Add a commitment. */
@Composable
fun CommitmentsCard(c: Commitments, pending: Boolean, a: TimeActions) = TimeCard {
    Column {
        TimeCardTitle(c.title, "briefcase")
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            if (c.rows.isEmpty())
                Muted(c.empty, Modifier.padding(vertical = 11.dp, horizontal = 4.dp))
            c.rows.forEachIndexed { i, r ->
                Row(
                    Modifier.fillMaxWidth()
                        .clickable(enabled = !pending, role = Role.Button) { a.rule(r.id) }
                        .drawBehind {
                            if (i < c.rows.lastIndex)
                                drawLine(
                                    P.line,
                                    androidx.compose.ui.geometry.Offset(0f, size.height - .5f),
                                    androidx.compose.ui.geometry.Offset(
                                        size.width,
                                        size.height - .5f,
                                    ),
                                    1.dp.toPx(),
                                )
                        }
                        .padding(horizontal = 4.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RuleDot(if (r.dot == CommitmentDot.BLOCK) P.block else P.work)
                    Column(Modifier.weight(1f)) {
                        Text(
                            r.title,
                            style = DirectorText,
                            color = P.ink,
                            fontSize = 15.2.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(r.line, style = DirectorText, color = P.ink3, fontSize = 12.5.sp)
                    }
                    Text(
                        "›",
                        Modifier.clearAndSetSemantics {},
                        style = DirectorText,
                        color = P.ink3,
                        fontSize = 20.8.sp,
                    )
                }
            }
        }
        DirectorButton(c.add, ButtonLook.PLAIN, { if (!pending) a.rule("") })
    }
}

/** .ld-rdot: a 10 dp rounded square in the commitment's colour, glowing 8 px. */
@Composable
private fun RuleDot(color: Color) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier.size(10.dp)
            .drawBehind {
                val glow = glowPaint(color, 8.dp.toPx())
                drawIntoCanvas {
                    it.drawRoundRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        3.dp.toPx(),
                        3.dp.toPx(),
                        glow,
                    )
                }
            }
            .background(color, shape)
    )
}

/**
 * The daily routines: a stepper each with the page's words per stop, then meals with its switch.
 */
@Composable
fun RoutinesCard(r: Routines, shown: (RoutineRow) -> Int, pending: Boolean, a: TimeActions) =
    TimeCard {
        Column {
            TimeCardTitle(r.title, "smile")
            Muted(r.text)
            r.rows.forEach { StepRow(it, shown(it), pending, a) }
            StepRow(r.meals, shown(r.meals), pending, a, last = true)
            Muted(r.note, Modifier.padding(top = 6.dp))
        }
    }

/**
 * .ld-step-row: the label and hint, then − , the minutes in the page's words, + (and the meals
 * switch).
 */
@Composable
private fun StepRow(
    row: RoutineRow,
    value: Int,
    pending: Boolean,
    a: TimeActions,
    last: Boolean = false,
) {
    val on = row.on != false
    Row(
        Modifier.fillMaxWidth()
            .drawBehind {
                drawLine(
                    P.line,
                    androidx.compose.ui.geometry.Offset(0f, size.height - .5f),
                    androidx.compose.ui.geometry.Offset(size.width, size.height - .5f),
                    1.dp.toPx(),
                )
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                row.label,
                style = DirectorText,
                color = P.ink,
                fontSize = 14.7.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(row.hint, style = DirectorText, color = P.ink3, fontSize = 12.2.sp)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val said = if (on) row.textAt(value) else row.text
            if (on)
                StepButton("−", row.less, pending || value <= row.min) { a.routine(row.key, -1) }
            Text(
                said,
                Modifier.widthIn(min = 54.dp).semantics {
                    contentDescription = row.label + ", " + said
                },
                style = DirectorText.copy(fontFamily = FontFamily.Monospace),
                color = P.ink,
                fontSize = 13.6.sp,
                fontWeight = FontWeight(650),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            if (on) StepButton("+", row.more, pending || value >= row.max) { a.routine(row.key, 1) }
            row.toggle?.let { t ->
                DirectorButton(t, ButtonLook.GHOST, { if (!pending) a.meals(!on) }, small = true)
            }
        }
    }
}

/** A stepper's small − or + (.ld-stepper .ld-btn), named for TalkBack in the page's words. */
@Composable
private fun StepButton(sign: String, said: String, off: Boolean, onClick: () -> Unit) =
    DirectorButton(
        sign,
        ButtonLook.PLAIN,
        { if (!off) onClick() },
        Modifier.widthIn(min = 36.dp).alpha(if (off) .45f else 1f).semantics {
            contentDescription = said
        },
        small = true,
    )

/** Goals: a chip each (or the empty words), Add a goal and, once there are some, Manage goals. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimeGoalsCard(g: TimeGoals, pending: Boolean, a: TimeActions, manage: () -> Unit) = TimeCard {
    Column {
        TimeCardTitle(g.title, "target")
        if (g.chips.isEmpty()) Muted(g.empty)
        else
            FlowRow(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                g.chips.forEach {
                    Text(
                        it,
                        Modifier.background(P.chipFill, RoundedCornerShape(50))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        style = DirectorText,
                        color = P.ink2,
                        fontSize = 13.1.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectorButton(
                g.add.label,
                if (g.add.primary) ButtonLook.PRIMARY else ButtonLook.PLAIN,
                { if (!pending) a.addGoal() },
            )
            g.manage?.let { DirectorButton(it, ButtonLook.GHOST, manage) }
        }
    }
}

/**
 * Build my week (.ld-build): its title, the time zone line and the wide button, dimmed until sleep
 * is saved.
 */
@Composable
fun BuildWeekCard(b: BuildCard, pending: Boolean, a: TimeActions, modifier: Modifier = Modifier) =
    TimeCard(modifier) {
        Column {
            TimeCardTitle(b.title, null)
            Muted(b.text)
            // the page leaves it pressable while dimmed: it then says why ("Save your sleep times
            // first")
            DirectorButton(
                b.label,
                ButtonLook.PRIMARY,
                { if (!pending) a.build() },
                Modifier.padding(top = 6.dp).fillMaxWidth().alpha(if (b.enabled) 1f else .45f),
            )
        }
    }

/** Re-plan now, once set up (.ld-row.ld-foot): a quiet button under the cards. */
@Composable
fun ReplanRow(label: String, pending: Boolean, a: TimeActions, modifier: Modifier = Modifier) =
    Box(modifier.padding(top = 6.dp, bottom = 4.dp)) {
        DirectorButton(label, ButtonLook.GHOST, { if (!pending) a.build() })
    }
