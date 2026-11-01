package com.lifemax.rpg.feature.overview.presentation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.lifemax.rpg.feature.lifedirector.presentation.components.animatorScale
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntro
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialIntroRun
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/*
 * The page's Overview arrival (ovReplay, 18-runtime.js, and its CSS), as values at [t] ms from its
 * start, so a screenshot can stop it anywhere and a test can check any frame:
 *   each card rises in      .4 s, 30 ms apart from the head (the n-th at n x 30 ms, the ninth on
 *                           at .24 s), opacity 0 and 10 px lower to its place (viewIn), ease-out
 *   today's ring arcs       1.15 s from -130 degrees and scale .92, faded in (ovRing), ease-out
 *   the week's bars         .8 s from scaleY .04 at the foot, opacity .2 (ovGrow), ease-out
 *   today's XP              counts up over 900 ms, cubic ease-out (1 - (1 - u)^3), rounded
 *   the XP and gem bars     fill from empty over 1.1 s (their width transition), ease-out
 *   Life Director's dial    its own arrival (DialIntro), session by session
 * ease-out is the page's --ease-out, cubic-bezier(.22, 1, .36, 1). With reduced motion the page
 * plays none of it, and neither does the screen (ArrivalController).
 */
internal object OverviewArrival {
    private val easeOut: Easing = CubicBezierEasing(.22f, 1f, .36f, 1f)

    private fun held(t: Float, delay: Float, duration: Float) =
        easeOut.transform(((t - delay) / duration).coerceIn(0f, 1f))

    /** The [index]-th row of the list (the head is 0): how far it has risen, 0 to 1. */
    fun card(t: Float, index: Int) = held(t, min(index, 8) * 30f, 400f)

    /** How far the card rows still sit below their place, in dp, at [rise] (from [card]). */
    fun cardDrop(rise: Float) = 10f * (1f - rise)

    /** Today's ring arcs, 0 to 1: rotation -130 x (1 - p) degrees, scale .92 + .08p, opacity p. */
    fun ring(t: Float) = held(t, 0f, 1150f)

    /** The week's bars, 0 to 1: scaleY .04 + .96p from the foot, opacity .2 + .8p. */
    fun bars(t: Float) = held(t, 0f, 800f)

    /** Today's XP as the count-up shows it [t] ms in. */
    fun count(to: Int, t: Float): Int {
        val u = (t / 900f).coerceIn(0f, 1f)
        return (to * (1f - (1f - u).pow(3))).roundToInt()
    }

    /** How much of its fill a bar shows, 0 to 1. */
    fun fill(t: Float) = held(t, 0f, 1100f)

    /** When everything has ended, for a dial of [sessions] sessions. */
    fun end(sessions: Int) = max(1150f, DialIntro.end(sessions))
}

/**
 * An arrival being played: how far into it the clock is ([ms]); the cards read it while drawing
 * where they can (a frame redraws without recomposing). [dial] drives the glance's DialIntro off
 * the same clock, so the whole Overview arrives together. A screenshot builds one with [at].
 */
@Stable
class OverviewArrivalRun(private val clock: State<Long>) {
    val ms: Float
        get() = clock.value.toFloat()

    val dial = DialIntroRun(clock)

    companion object {
        /** An arrival stopped at [ms] (screenshots). */
        fun at(ms: Long) = OverviewArrivalRun(mutableLongStateOf(ms))
    }
}

/** The arrival the cards are playing, or null when they are still. */
internal val LocalOverviewArrival = staticCompositionLocalOf<OverviewArrivalRun?> { null }

/**
 * Plays arrival [key] (0: none) once, on the frame clock: null while nothing plays. Android's
 * "Remove animations" draws the end at once; another animator scale stretches the timeline.
 * [onDone] runs when it has ended, or at once when it cannot play.
 */
@Composable
fun rememberOverviewArrival(key: Long, sessions: Int, onDone: (Long) -> Unit): OverviewArrivalRun? {
    val context = LocalContext.current
    val done by rememberUpdatedState(onDone)
    var run by remember { mutableStateOf<OverviewArrivalRun?>(null) }
    LaunchedEffect(key) {
        run = null
        if (key == 0L) return@LaunchedEffect
        val scale = animatorScale(context)
        if (scale > 0f) {
            val clock = mutableLongStateOf(0L)
            run = OverviewArrivalRun(clock)
            val start = withFrameMillis { it }
            val end = OverviewArrival.end(sessions)
            while (true) {
                val t = (withFrameMillis { it } - start) / scale
                clock.longValue = t.toLong()
                if (t >= end) break
            }
            run = null
        }
        done(key)
    }
    return run
}

/** The [index]-th row rising into place while an arrival plays; nothing at all otherwise. */
internal fun Modifier.arrivalRise(run: OverviewArrivalRun?, index: Int): Modifier =
    if (run == null) this
    else
        graphicsLayer {
            val rise = OverviewArrival.card(run.ms, index)
            alpha = rise
            translationY = OverviewArrival.cardDrop(rise) * density
        }
