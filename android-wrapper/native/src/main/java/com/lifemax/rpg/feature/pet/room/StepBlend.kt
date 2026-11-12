package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import kotlin.math.floor

/** Whether a camera step's pictures are made (an interface, so asking boxes nothing). */
internal fun interface StepReady {
    fun ready(i: Int): Boolean
}

/** The numbers behind the blend between two camera steps. */
internal object BlendTuning {
    /**
     * How fast (camera steps per second) a blend that the camera's own motion does not explain is
     * closed: the settle onto a whole step once the camera rests, and the catch-up when a step a
     * background warmer was making is ready. Half a step takes a quarter of a second: a short fade,
     * never a jump.
     */
    const val SETTLE_STEPS_PER_S = 2f
}

/**
 * Where one solid piece is between its camera steps, as a continuous position: the owner's report
 * was that the furniture's sides came and went "like a slideshow" as the camera panned, because a
 * piece was drawn at the nearest of its pre-rendered steps and snapped to the next one. A piece is
 * now drawn as a cross-fade of the two steps on either side of the camera ([base] and [over], with
 * the weight [weight] on [over]), so a side that the next step shows fades in as the camera turns
 * towards it instead of appearing at once.
 *
 * The position, [shown], is in steps (`sway * stepsPerSide + stepsPerSide`, so step `i` is at `i`):
 * - **Moving.** While the camera moves it follows the camera exactly: the weight is the fraction of
 *   the way from one step to the next, continuous in the sway, 0 at a step and 1 at the next.
 * - **At rest.** When the camera holds still (the wanted position is bit-identical to the last
 *   frame's, as [DepthSway.shown] makes it) the position glides to the whole step the room drew
 *   before the blend existed (the nearest, held with [VolumeSheets.indexOf]'s hysteresis), at
 *   [BlendTuning.SETTLE_STEPS_PER_S], and stops on it exactly. A resting camera therefore draws one
 *   step's pictures alone, the same picture as before, and identical frames.
 * - **Not ready.** With a background warmer a step may not be made yet: the position never enters a
 *   step that is not ready, and when the step is made it moves on at the settle speed, so a step
 *   that becomes ready fades in rather than popping.
 * - **The first frame** (and the first after [reset]) starts on the whole step, as before.
 *
 * Which of the two steps is the base: the one nearer the camera's rest (sway 0). The nearer step's
 * sides are the shallower ones, so its bands are drawn first and the farther step's, which show
 * more of the piece, are faded in over them ([StepMix] has how the base is split so the cross-fade
 * is exact).
 *
 * Plain arithmetic on a few numbers; nothing is allocated.
 *
 * @param stepsPerSide the sheets' steps on each side of rest (0: the room never sways).
 */
internal class StepBlend(private val stepsPerSide: Int) {
    private val last = 2 * stepsPerSide

    /** The position drawn, in steps (0 to `2 * stepsPerSide`). */
    var shown = stepsPerSide.toFloat()
        private set

    /** The whole step the position rests on, held with hysteresis (as [VolumeSheets.indexOf]). */
    var settled = stepsPerSide
        private set

    private var lastWant = Float.NaN
    private var started = false

    /** The step drawn in full (alpha 1, but for its pixels the [over] step does not cover). */
    var base = stepsPerSide
        private set

    /** The step faded in over [base], or -1 when [base] is drawn alone. */
    var over = -1
        private set

    /** How much of [over] is drawn: above 0 and below 1 when there is one, else 0. */
    var weight = 0f
        private set

    /**
     * The whole step for the drawn position, held with hysteresis: the step the room drew before
     * the blend, and the one it comes to rest on (tests, tuning).
     */
    val nearest: Int
        get() = settled

    /** The sway of the drawn position (where the inside of the piece is drawn, for a cat on it). */
    val sway: Float
        get() = if (stepsPerSide == 0) 0f else (shown - stepsPerSide) / stepsPerSide

    /** Start again at the next [update] (the steps were let go of). */
    fun reset() {
        started = false
        lastWant = Float.NaN
        settled = stepsPerSide
        place(stepsPerSide.toFloat())
    }

    /**
     * One frame: the camera wants [sway] (-1 to 1) for this piece, [dt] seconds after the last
     * frame; [ready] says which steps' pictures are made.
     */
    fun update(sway: Float, dt: Float, ready: StepReady) {
        if (stepsPerSide == 0) {
            place(0f)
            return
        }
        val want =
            (sway.coerceIn(-1f, 1f) * stepsPerSide + stepsPerSide).coerceIn(0f, last.toFloat())
        if (!started) {
            started = true
            lastWant = want
            settled = settleOf(want, stepsPerSide)
            settled = nearestReady(settled, ready)
            place(settled.toFloat())
            return
        }
        val resting = want == lastWant
        val target = if (resting) settleOf(want, settled).toFloat() else want
        val limit = abs(want - lastWant) + BlendTuning.SETTLE_STEPS_PER_S * dt.coerceAtLeast(0f)
        lastWant = want
        // exactly onto the target when it is in reach, so a step is drawn alone, not at 0.9999
        var next =
            if (abs(target - shown) <= limit) target
            else shown + (target - shown).coerceIn(-limit, limit)
        next = withinReady(next, ready)
        settled = settleOf(next, settled)
        place(next)
    }

    /** [VolumeSheets.indexOf] on a position in steps. */
    private fun settleOf(at: Float, current: Int): Int {
        if (current in 0..last && abs(at - current) < 0.5f + SheetTuning.HYSTERESIS) return current
        return Math.round(at).coerceIn(0, last)
    }

    /** The ready step nearest [i] (itself when ready); [i] when none is (it is made on demand). */
    private fun nearestReady(i: Int, ready: StepReady): Int {
        if (ready.ready(i)) return i
        for (k in 1..last) {
            if (i - k >= 0 && ready.ready(i - k)) return i - k
            if (i + k <= last && ready.ready(i + k)) return i + k
        }
        return i
    }

    /**
     * [at] kept to the run of ready steps around the position drawn now: it never moves onto or
     * past a step that is not made yet.
     */
    private fun withinReady(at: Float, ready: StepReady): Float {
        val from = nearestReady(Math.round(shown).coerceIn(0, last), ready)
        if (!ready.ready(from)) return at // nothing is made: the frame makes what it needs
        var lo = from
        while (lo > 0 && ready.ready(lo - 1)) lo--
        var hi = from
        while (hi < last && ready.ready(hi + 1)) hi++
        return at.coerceIn(lo.toFloat(), hi.toFloat())
    }

    /** Draw at position [at]: the two steps around it and the weight of the farther one. */
    private fun place(at: Float) {
        shown = at
        val lo = floor(at).toInt().coerceIn(0, maxOf(0, last))
        val f = at - lo
        if (f <= 0f || lo >= last) {
            base = lo
            over = -1
            weight = 0f
            return
        }
        val hi = lo + 1
        if (abs(lo - stepsPerSide) <= abs(hi - stepsPerSide)) {
            base = lo
            over = hi
            weight = f
        } else {
            base = hi
            over = lo
            weight = 1f - f
        }
        if (weight <= 0f) {
            over = -1
            weight = 0f
        }
    }
}
