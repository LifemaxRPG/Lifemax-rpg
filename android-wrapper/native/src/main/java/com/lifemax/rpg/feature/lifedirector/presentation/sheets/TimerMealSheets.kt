package com.lifemax.rpg.feature.lifedirector.presentation.sheets

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.core.designsystem.components.PixelIcon
import com.lifemax.rpg.core.designsystem.glowPaint
import com.lifemax.rpg.feature.lifedirector.domain.Chip
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind
import com.lifemax.rpg.feature.lifedirector.domain.GuideSpan
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.MealSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.TimerSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.answers
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorUi
import com.lifemax.rpg.feature.lifedirector.presentation.components.ButtonLook
import com.lifemax.rpg.feature.lifedirector.presentation.components.ChipRow
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorButton
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorSheet
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorText
import com.lifemax.rpg.feature.lifedirector.presentation.components.clockText
import com.lifemax.rpg.feature.lifedirector.presentation.components.rememberTickingClock

/** What the timer and meal sheets can ask for. */
class TimerMealActions(
    val close: () -> Unit = {},
    val pause: () -> Unit = {},
    val finish: () -> Unit = {},
    val stop: () -> Unit = {},
    val openMeal: () -> Unit = {},
    val reset: () -> Unit = {},
)

/**
 * The timer or meal sheet the screen asked for, once the page has sent its model. [animate]: the
 * screen is visible (the clock ticks only then); [reducedMotion] moves the ring at once.
 */
@Composable
fun BoxScope.TimerMealSheet(
    p: LifeDirectorProjection,
    ui: DirectorUi,
    bottomInset: Dp,
    actions: TimerMealActions,
    animate: Boolean,
) {
    val open = ui.sheet ?: return
    when (val m = p.sheet?.takeIf { it.answers(open) }) {
        is TimerSheetModel ->
            TimerSheet(m, ui.pendingToday, bottomInset, actions, animate, !p.reducedMotion)
        is MealSheetModel -> MealSheet(m, bottomInset, actions)
        else -> {}
    }
}

/**
 * The page's focus sheet (focusSheet): what the timer is doing, the session, the 220-unit ring with
 * the time run so far and the planned length in it, the XP logging now would earn, then Finish and
 * log beside Pause or Resume, Stop without logging, and the note. The time is derived from the
 * page's times at each tick of the wall clock, never counted, and ticks only while [ticking] and
 * the timer runs. The ring fills as the time runs, in a second's linear sweep as the page's does,
 * at once when [glide] is false (reduced motion). [pending] is the write in flight.
 */
@Composable
fun BoxScope.TimerSheet(
    t: TimerSheetModel,
    pending: String?,
    bottomInset: Dp,
    actions: TimerMealActions,
    ticking: Boolean,
    glide: Boolean = true,
) =
    DirectorSheet(t.title, bottomInset, actions.close) {
        val now by rememberTickingClock(ticking && !t.paused)
        val elapsed = clockText(t.timer.elapsedMs(now))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                t.label,
                style = DirectorText,
                color = P.good,
                fontSize = 11.52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                t.title,
                Modifier.padding(horizontal = 30.dp).padding(top = 4.dp).semantics { heading() },
                style = DirectorText,
                fontSize = 20.8.sp,
                fontWeight = FontWeight(750),
                letterSpacing = (-.03).em,
                lineHeight = 1.2.em,
                textAlign = TextAlign.Center,
            )
            val fill by
                animateFloatAsState(
                    t.progress(now),
                    if (glide && ticking) tween(1000, easing = LinearEasing) else snap(),
                    label = "ring",
                )
            Box(
                Modifier.padding(top = 16.dp, bottom = 8.dp)
                    .fillMaxWidth(.7f)
                    .widthIn(max = 230.dp)
                    .aspectRatio(1f)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$elapsed, ${t.planned}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Ring(fill, t.paused)
                Column(
                    Modifier.clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        elapsed,
                        style = DirectorText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 38.4.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-.04).em,
                        lineHeight = 1.em,
                    )
                    Text(
                        t.planned,
                        Modifier.padding(top = 6.dp),
                        style = DirectorText,
                        color = P.ink3,
                        fontSize = 12.8.sp,
                    )
                }
            }
            TimerResetButton(pending == null, actions.reset)
            t.xp?.let { Muted(it, 13.76f) }
            val wait = Modifier.alpha(if (pending != null) .45f else 1f)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DirectorButton(t.finish, ButtonLook.PRIMARY, actions.finish, wait)
                DirectorButton(t.pause, ButtonLook.PLAIN, actions.pause, wait)
            }
            DirectorButton(
                t.stop,
                ButtonLook.GHOST,
                actions.stop,
                wait.padding(top = 10.dp),
                small = true,
            )
            Muted(listOf(GuideSpan(t.note, false)), 12.48f)
        }
    }

/**
 * .ld-fring: the track, then the time run so far from the top, clockwise, in the session's green
 * with its glow, or in amber without one while paused (.ld-focus.paused).
 */
@Composable
private fun Ring(fill: Float, paused: Boolean) =
    Canvas(Modifier.fillMaxSize()) {
        val u = size.width / VIEW
        val r = RADIUS * u
        val w = STROKE * u
        drawCircle(TRACK, r, style = Stroke(w))
        if (fill <= 0f) return@Canvas
        val box = Offset(center.x - r, center.y - r)
        val arc = Size(2 * r, 2 * r)
        if (!paused)
            drawIntoCanvas {
                val glow =
                    glowPaint(P.task.copy(alpha = .6f), 8f * u).apply {
                        style = PaintingStyle.Stroke
                        strokeWidth = w
                        strokeCap = StrokeCap.Round
                    }
                it.drawArc(
                    box.x,
                    box.y,
                    box.x + arc.width,
                    box.y + arc.height,
                    -90f,
                    360f * fill,
                    false,
                    glow,
                )
            }
        drawArc(
            if (paused) P.work else P.task,
            -90f,
            360f * fill,
            false,
            box,
            arc,
            style = Stroke(w, cap = StrokeCap.Round),
        )
    }

/**
 * The page's meal sheet (mealPreview): the meal's rail and icon, the recipe or "Your meal plan",
 * when, the chips, and the button that opens the meal menu.
 */
@Composable
fun BoxScope.MealSheet(m: MealSheetModel, bottomInset: Dp, actions: TimerMealActions) =
    DirectorSheet(m.title, bottomInset, actions.close) {
        MealHead(m)
        // the page draws a meal's chips all alike, as plain .ld-chip (the goal chip's look)
        ChipRow(m.chips.map { Chip(ChipKind.GOAL, it.text) }, Modifier.padding(top = 6.dp))
        DirectorButton(
            m.open,
            ButtonLook.PRIMARY,
            actions.openMeal,
            Modifier.padding(top = 16.dp).fillMaxWidth(),
        )
    }

/** .ld-sh-head.ld-c-meal: a 4 dp rail glowing in the meal's colour beside the title and when. */
@Composable
private fun MealHead(m: MealSheetModel) {
    val shape = RoundedCornerShape(4.dp)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier.width(4.dp)
                .fillMaxHeight()
                .boxShadow(P.meal, 10.dp, shape)
                .background(P.meal, shape)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Row(
                Modifier.padding(end = 40.dp).semantics(mergeDescendants = true) { heading() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PixelIcon("meal", P.ink, Modifier.size(16.dp))
                Text(
                    m.title,
                    style = DirectorText,
                    fontSize = 20.8.sp,
                    fontWeight = FontWeight(750),
                    letterSpacing = (-.03).em,
                    lineHeight = 1.2.em,
                )
            }
            Text(
                m.`when`,
                Modifier.padding(top = 5.dp),
                style = DirectorText,
                color = P.ink2,
                fontSize = 14.08.sp,
            )
        }
    }
}

/** A centred .ld-muted paragraph with its bold spans. */
@Composable
private fun Muted(spans: List<GuideSpan>, size: Float) =
    Text(
        buildAnnotatedString {
            spans.forEach {
                if (it.b) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it.t) }
                else append(it.t)
            }
        },
        Modifier.padding(vertical = 5.dp),
        style = DirectorText,
        color = P.ink3,
        fontSize = size.sp,
        lineHeight = 1.5.em,
        textAlign = TextAlign.Center,
    )

/** The page's ring: a 220-unit box, a radius of 92 and a 12-unit stroke. */
private const val VIEW = 220f
private const val RADIUS = 92f
private const val STROKE = 12f
private val TRACK = Color.White.copy(alpha = .06f)

/** Small reset glyph with a full touch target and an explicit accessibility label. */
@Composable
internal fun TimerResetButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp).semantics { contentDescription = "Reset timer" },
    ) {
        Text("↺", color = P.ink3, fontSize = 22.sp)
    }
}
