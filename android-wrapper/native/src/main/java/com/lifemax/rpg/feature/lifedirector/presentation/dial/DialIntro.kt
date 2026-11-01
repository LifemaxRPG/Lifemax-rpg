package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.presentation.components.animatorScale
import kotlin.math.max

/*
 * The glance's arrival: the web's ld-dial-in (life-director/style.css), which the page plays on the
 * Overview's dial when it arrives (glance(el, {intro}) in logic.js), as values at [t] ms from its
 * start, so a screenshot can stop it anywhere and a test can check any frame. Each part fades in
 * with "backwards" fill (hidden during its delay, then shown for good):
 *   the outer ring          .5 s from 0,                         var(--ease)
 *   the words on the rings  .5 s from .2 s,                      var(--ease)
 *   each session (i-th)     .45 s from .25 s + i x 70 ms,        var(--ease)
 *   the hand                .4 s from .5 s,                      CSS "ease"
 * Meals, downtime, the face, the part of today gone and the middle are not part of it: they show
 * at once, as on the page. With reduced motion the page plays none of it (play() returns early),
 * and neither does the screen: there is no run, and the dial is drawn as it ends.
 */
internal object DialIntro {
    private val ease = CubicBezierEasing(.22f, 1f, .36f, 1f)
    private val cssEase = CubicBezierEasing(.25f, .1f, .25f, 1f)

    fun outer(t: Float) = BuildTimeline.held(t, 0f, 500f, ease)

    fun words(t: Float) = BuildTimeline.held(t, 200f, 500f, ease)

    /** The [i]-th session of the day (the web's --i: sessions only, in the dial's order). */
    fun session(t: Float, i: Int) = BuildTimeline.held(t, 250f + 70f * i, 450f, ease)

    fun hand(t: Float) = BuildTimeline.held(t, 500f, 400f, cssEase)

    /** When the last part has finished, for a day with [sessions] sessions. */
    fun end(sessions: Int) = max(900f, 250f + 70f * (sessions - 1).coerceAtLeast(0) + 450f)

    /** Each session arc's place in the --i count; null for any other arc. */
    fun order(arcs: List<DialArc>): Map<DialArc, Int> {
        var i = 0
        return buildMap { arcs.forEach { if (it.style == ArcStyle.SESSION) put(it, i++) } }
    }
}

/**
 * An arrival being played: how far into it the clock is ([ms], read while drawing, so a frame
 * redraws the dial without recomposing). A screenshot builds one on a fixed clock to stop it at a
 * given moment.
 */
@Stable
class DialIntroRun(private val clock: State<Long>) {
    val ms: Float
        get() = clock.value.toFloat()
}

/**
 * Which arrivals have had their turn: [take] is true the first time a key is offered. The Overview
 * keeps one for as long as its screen lives, so a glance scrolled out of the list and back does not
 * play again; a glance on its own keeps one for as long as it is composed.
 */
class DialIntroLedger {
    private var last = Long.MIN_VALUE

    fun take(key: Long): Boolean = (key != last).also { last = key }
}

/**
 * Plays the arrival once for each new [key] (the Overview's `arrival` counter) while [play] (the
 * glance showing, motion allowed): a key already played, or one that rises while hidden, stays
 * still, as the page plays it only on an arrival it shows. Android's "Remove animations" draws the
 * end state; another animator scale stretches the timeline. Null while nothing plays.
 */
@Composable
fun rememberDialIntro(
    key: Long,
    play: Boolean,
    sessions: Int,
    ledger: DialIntroLedger = remember { DialIntroLedger() },
): DialIntroRun? {
    val context = LocalContext.current
    var run by remember { mutableStateOf<DialIntroRun?>(null) }
    LaunchedEffect(key, play) {
        run = null
        // an arrival not seen is used up too, so it does not play later
        if (!ledger.take(key) || !play) return@LaunchedEffect
        val scale = animatorScale(context)
        if (scale <= 0f) return@LaunchedEffect
        val clock = mutableLongStateOf(0L)
        run = DialIntroRun(clock)
        val start = withFrameMillis { it }
        val end = DialIntro.end(sessions)
        while (true) {
            val t = (withFrameMillis { it } - start) / scale
            clock.longValue = t.toLong()
            if (t >= end) break
        }
        run = null
    }
    return run
}

/** The plan's rings at [t] ms into the arrival: the outer ring, the words, then each session. */
internal fun DrawScope.drawIntro(plan: DialPlan, order: Map<DialArc, Int>, t: Float) {
    faded(DialIntro.outer(t)) { for (art in plan.outer) art.draw(this) }
    faded(DialIntro.words(t)) { plan.words.draw(this) }
    for (art in plan.inner) {
        val i = order[art.arc]
        if (i == null) art.draw(this) else faded(DialIntro.session(t, i)) { art.draw(this) }
    }
}
