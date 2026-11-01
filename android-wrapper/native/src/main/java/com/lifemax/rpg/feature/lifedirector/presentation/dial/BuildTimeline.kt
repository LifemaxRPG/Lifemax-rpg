package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import com.lifemax.rpg.feature.lifedirector.domain.BuildItem
import com.lifemax.rpg.feature.lifedirector.domain.BuildWords
import com.lifemax.rpg.feature.lifedirector.domain.DialBuild
import com.lifemax.rpg.feature.lifedirector.domain.FlyKind
import kotlin.math.max
import kotlin.math.min

/*
 * The build as runBuild plays it on the page (logic.js), as values at [t] ms from its start: each
 * Web Animation's keyframes, offsets, easing, delay and fill, number for number. The page's plan
 * (buildPlan) says what moves and when; this only says how each piece looks at a moment, so a
 * screenshot can stop the build anywhere and a test can check any frame.
 *
 * Web Animations ease the whole iteration and run straight between keyframes; "fill: both" holds
 * the first keyframe during the delay and the last after the end, "fill: none" shows nothing
 * outside the active time.
 */
internal object BuildTimeline {
    private val easeOut = CubicBezierEasing(0f, 0f, .58f, 1f)
    private val launch = CubicBezierEasing(.2f, .75f, .25f, 1f)
    private val orbit = CubicBezierEasing(.45f, 0f, .2f, 1f)
    private val burst = CubicBezierEasing(.1f, .6f, .3f, 1f)
    private val sparkFly = CubicBezierEasing(.15f, .7f, .3f, 1f)
    private val breakUp = CubicBezierEasing(.5f, 0f, .9f, .4f)
    private val swing = CubicBezierEasing(.3f, .8f, .3f, 1f)
    private val settle = CubicBezierEasing(.2f, .8f, .2f, 1f)
    private val spring = CubicBezierEasing(.34f, 1.45f, .64f, 1f)

    /** The page's tick count: a tick every quarter hour. */
    const val TICKS = 96

    /** How long after its end a build is cleared away. */
    const val CLEAR_MS = 20

    /** Progress through an animation with "fill: both": 0 before [delay], 1 after it ends. */
    fun held(t: Float, delay: Float, duration: Float, easing: Easing = LinearEasing): Float =
        easing.transform(((t - delay) / duration).coerceIn(0f, 1f))

    /** Progress through an animation with "fill: none": null outside its active time. */
    fun active(t: Float, delay: Float, duration: Float, easing: Easing = LinearEasing): Float? =
        if (t < delay || t > delay + duration) null else easing.transform((t - delay) / duration)

    /** The value at progress [p] of [frames] (offset to value), straight between them. */
    fun keyframes(p: Float, vararg frames: Pair<Float, Float>): Float {
        if (p <= frames.first().first) return frames.first().second
        for (k in 1 until frames.size) {
            val (o1, v1) = frames[k]
            if (p <= o1) {
                val (o0, v0) = frames[k - 1]
                return if (o1 <= o0) v1 else v0 + (v1 - v0) * (p - o0) / (o1 - o0)
            }
        }
        return frames.last().second
    }

    private fun sweepMs(b: DialBuild) = b.sweep?.ms?.toFloat() ?: 0f

    // Phase A: the sweep (a full build only)

    /** The arm's angle, once round in the sweep's time. */
    fun armAngle(b: DialBuild, t: Float): Float = 360f * held(t, 0f, sweepMs(b))

    /** The arm's opacity: in by 6 %, out after 90 % of the sweep and 160 ms more. */
    fun armAlpha(b: DialBuild, t: Float): Float =
        if (!b.full) 0f
        else keyframes(held(t, 0f, sweepMs(b) + 160), 0f to 0f, .06f to 1f, .9f to 1f, 1f to 0f)

    /**
     * How much of a band from angle [a0] to [a1] (its times, before the dial's gaps) the arm has
     * drawn: from when the arm reaches it, for the time the arm takes over it, at least 60 ms.
     */
    fun bandDrawn(b: DialBuild, t: Float, a0: Float, a1: Float): Float {
        if (!b.full) return 1f
        val sweep = sweepMs(b)
        return held(t, a0 / 360f * sweep, max(60f, (a1 - a0) / 360f * sweep))
    }

    /** How brightly tick [k] still flashes (1: all #e0f2fe), fading back over 520 ms. */
    fun tickFlash(b: DialBuild, t: Float, k: Int): Float {
        if (!b.full) return 0f
        val p = active(t, k.toFloat() / TICKS * sweepMs(b), 520f, easeOut) ?: return 0f
        return 1f - p
    }

    /** A kept session, meal, appointment or downtime starting at [a0]: shown as the arm passes. */
    fun revealed(b: DialBuild, t: Float, a0: Float): Float =
        if (!b.full) 1f else held(t, a0 / 360f * sweepMs(b), 260f, easeOut)

    /** The words, the past, the current ring and the hand: in after the lock (a full build). */
    fun faceShown(b: DialBuild, t: Float): Float =
        if (!b.full) 1f else held(t, b.lock.at + 80f, 420f, easeOut)

    // Phase B: the sessions fly in

    /** Sessions landed by [t]: the counter. */
    fun landed(b: DialBuild, t: Float): Int = b.items.count { it.land <= t }

    /** How [item] is placed at [t]: turned about the dial's centre, scaled, faded and lit. */
    fun flight(b: DialBuild, item: BuildItem, t: Float): Flight {
        val bright =
            keyframes(
                held(t, item.at.toFloat(), item.ms + 380f, easeOut),
                0f to 2.6f,
                .6f to 2.6f,
                1f to 1f,
            )
        if (item.kind == FlyKind.NEW) {
            val p = held(t, item.at.toFloat(), item.ms.toFloat(), launch)
            return Flight(
                rotation = keyframes(p, 0f to -55f, .68f to 5f, 1f to 0f),
                scale = keyframes(p, 0f to .1f, .68f to 1.08f, 1f to 1f),
                alpha = keyframes(p, 0f to 0f, .68f to 1f, 1f to 1f),
                brightness = bright,
            )
        }
        val p = held(t, item.at.toFloat(), item.ms.toFloat(), orbit)
        val sign = if (item.from > 0) 1f else -1f
        return Flight(
            rotation =
                keyframes(p, 0f to item.from, .12f to item.from, .82f to -sign * 2.5f, 1f to 0f),
            scale = 1f,
            alpha = keyframes(p, 0f to if (b.full) 0f else .55f, .12f to .7f, .82f to 1f, 1f to 1f),
            brightness = bright,
        )
    }

    /** A moved session's dotted trail from where it was to where it goes, or null. */
    fun trail(item: BuildItem, t: Float): Trail? {
        if (item.kind != FlyKind.MOVED) return null
        val lo = min(item.a0, item.a0 + item.from)
        val hi = max(item.a1, item.a1 + item.from)
        if (hi - lo >= 359f) return null
        val alpha =
            keyframes(held(t, item.at.toFloat(), item.ms + 200f), 0f to 0f, .25f to .75f, 1f to 0f)
        return if (alpha <= 0f) null else Trail(lo.coerceAtLeast(0f), hi.coerceAtMost(360f), alpha)
    }

    /** The touchdown ripple's scale and opacity, while it runs. */
    fun ripple(item: BuildItem, t: Float): Pair<Float, Float>? {
        val p = active(t, item.land.toFloat(), 620f, burst) ?: return null
        return keyframes(p, 0f to .3f, 1f to 3.4f) to keyframes(p, 0f to .95f, 1f to 0f)
    }

    /** How far the touchdown sparks have flown (0 to 1) and their opacity, while they fly. */
    fun sparks(item: BuildItem, t: Float): Pair<Float, Float>? {
        val p = active(t, item.land.toFloat(), 480f, sparkFly) ?: return null
        return p to keyframes(p, 0f to 0f, .08f to 1f, 1f to 0f)
    }

    /** The counter's tick at the last touchdown: its scale and how green it still is. */
    fun counterTick(b: DialBuild, t: Float): Pair<Float, Float> {
        val last = b.items.filter { it.land <= t }.maxOfOrNull { it.land } ?: return 1f to 0f
        val p = active(t, last.toFloat(), 260f, spring) ?: return 1f to 0f
        return keyframes(p, 0f to 1.28f, 1f to 1f) to (1f - p).coerceIn(0f, 1f)
    }

    /** A dropped session breaking up: opacity, scale, turn and stroke width. */
    fun ghost(b: DialBuild, t: Float, strokeWidth: Float): Ghost {
        val p = held(t, b.start.at.toFloat(), 640f, breakUp)
        return Ghost(
            alpha = keyframes(p, 0f to 0f, .2f to .9f, 1f to 0f),
            scale = keyframes(p, 0f to 1f, .2f to 1f, 1f to 1.2f),
            rotation = keyframes(p, 0f to 0f, .2f to 0f, 1f to 7f),
            strokeWidth = strokeWidth + (2f - strokeWidth) * p,
        )
    }

    /** A session that left the plan flashes once. */
    fun flipBrightness(b: DialBuild, t: Float): Float =
        keyframes(held(t, b.start.at.toFloat(), 700f), 0f to 1f, .3f to 2.2f, 1f to 1f)

    // Phase C: lock in

    /** The shockwave's scale and opacity, while it runs. */
    fun wave(b: DialBuild, t: Float): Pair<Float, Float>? {
        val p = active(t, b.lock.at.toFloat(), 760f, burst) ?: return null
        return keyframes(p, 0f to .45f, 1f to 1.16f) to keyframes(p, 0f to .85f, 1f to 0f)
    }

    /** The whole dial's small kick at the lock. */
    fun dialScale(b: DialBuild, t: Float): Float {
        val p = active(t, b.lock.at.toFloat(), 440f, easeOut) ?: return 1f
        return keyframes(p, 0f to 1f, .3f to 1.025f, 1f to 1f)
    }

    /** The hand of a full build swings from midnight past [angle] and settles. */
    fun handAngle(b: DialBuild, t: Float, angle: Float): Float =
        if (!b.full) angle
        else
            keyframes(
                held(t, b.lock.at + 80f, 760f, swing),
                0f to 0f,
                .8f to angle + 4f,
                1f to angle,
            )

    /** Handle [k] of a full build pops in after the lock. */
    fun handleScale(b: DialBuild, t: Float, k: Int): Float =
        if (!b.full) 1f
        else
            keyframes(
                held(t, b.lock.at + 200f + k * 70f, 420f, easeOut),
                0f to 0f,
                .65f to 1.3f,
                1f to 1f,
            )

    /** The read-out over the centre: in, held, and out with a small growth before the end. */
    fun hud(b: DialBuild, t: Float): Pair<Float, Float> {
        val end = b.end.toFloat().coerceAtLeast(1f)
        val o1 = max(.1f, (end - 560f) / end)
        val o2 = max(o1 + .01f, (end - 330f) / end)
        val p = held(t, 0f, end)
        val inAt = min(.05f, o1 / 2)
        return keyframes(p, 0f to 0f, inAt to 1f, o1 to 1f, o2 to 0f, 1f to 0f) to
            keyframes(p, 0f to .9f, inAt to 1f, o1 to 1f, o2 to 1.08f, 1f to 1.08f)
    }

    /** The live centre, hidden through the build and back over its last 320 ms. */
    fun centre(b: DialBuild, t: Float): Pair<Float, Float> {
        val p = held(t, b.end - 320f, 320f, settle)
        return p to .9f + .1f * p
    }

    /**
     * The read-out's words at [t]: the sweep's hour under the arm, a repair's intro, then the start
     * with the sessions landed so far, then the lock.
     */
    fun words(b: DialBuild, t: Float): HudWords {
        val sweep = b.sweep
        return when {
            t >= b.lock.at -> HudWords(b.lock, lock = true)
            t >= b.start.at -> {
                val n = landed(b, t)
                HudWords(b.start.kick, if (n > 0) "$n" else b.start.big, b.start.sub, false)
            }
            sweep != null -> {
                val angle = armAngle(b, t)
                val step = sweep.readout.lastOrNull { it.a <= angle } ?: sweep.readout.first()
                HudWords(sweep.kick, step.hour, step.what, false)
            }
            else -> HudWords(b.intro ?: b.start, lock = false)
        }
    }

    /** Whether [t] is past the build's end, when everything is cleared. */
    fun over(b: DialBuild, t: Float) = t >= b.end + CLEAR_MS
}

/** A flying session: turned [rotation] degrees about the dial's centre and scaled about it. */
internal data class Flight(
    val rotation: Float,
    val scale: Float,
    val alpha: Float,
    val brightness: Float,
)

internal data class Trail(val a0: Float, val a1: Float, val alpha: Float)

internal data class Ghost(
    val alpha: Float,
    val scale: Float,
    val rotation: Float,
    val strokeWidth: Float,
)

/** The read-out's three lines; [lock] colours them as locked in. */
internal data class HudWords(
    val kick: String,
    val big: String,
    val sub: String,
    val lock: Boolean,
) {
    constructor(w: BuildWords, lock: Boolean) : this(w.kick, w.big, w.sub, lock)
}
