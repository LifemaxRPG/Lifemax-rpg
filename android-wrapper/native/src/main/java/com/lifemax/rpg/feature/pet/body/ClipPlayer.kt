package com.lifemax.rpg.feature.pet.body

import kotlin.math.floor

/**
 * Plays one [Clip] at a time. A clip always starts at an explicit phase (frame 0, or the phase of
 * the clip it replaces), never on a global clock. Looping clips wrap; one-shot clips hold their
 * last frame and report [finished].
 *
 * Clips that blink (the sitting rows) keep their eyes open, holding one open frame for
 * [Tuning.Blink.OPEN_HOLD_S] before the next (a sitting cat's head must not change many times a
 * second), and run a blink every few seconds, drawn from the cat's own random stream, like the
 * web's petBlinkFrame.
 */
internal class ClipPlayer(private val rng: Rng) {
    var clip: Clip = Clips.IDLE_SIT
        private set

    /** Position inside the clip, `0..1`. */
    var phase = 0f
        private set

    /** True once a one-shot clip has reached its end. */
    var finished = false
        private set

    /** True on the tick a foot landed (the phase crossed one of the clip's contact points). */
    var footfall = false
        private set

    /** True while a blink is playing. */
    val blinking: Boolean
        get() = blinkClock >= 0f

    /** Seconds since this clip started. */
    var elapsed = 0f
        private set

    private var blinkClock = NOT_BLINKING
    private var nextBlinkIn = 0f
    private var openClock = 0f

    init {
        scheduleBlink()
    }

    /** Start [next] at [startPhase]. */
    fun play(next: Clip, startPhase: Float = 0f) {
        clip = next
        phase = Mathx.clamp(startPhase, 0f, ALMOST_ONE)
        finished = false
        footfall = false
        elapsed = 0f
        blinkClock = NOT_BLINKING
        openClock = 0f
        scheduleBlink()
    }

    /** Jump to [p] inside the current clip (used for clips driven by a motion, like the leap). */
    fun seek(p: Float) {
        phase = Mathx.clamp(p, 0f, ALMOST_ONE)
    }

    /** Advance by [dt] seconds at [cyclesPerSecond] passes through the clip. */
    fun advance(dt: Float, cyclesPerSecond: Float) {
        val before = phase
        elapsed += dt
        phase += dt * cyclesPerSecond
        footfall = false
        if (clip.loop) {
            phase -= floor(phase)
            footfall = crossedContact(before, phase, cyclesPerSecond > 0f)
        } else if (phase >= ALMOST_ONE) {
            phase = ALMOST_ONE
            finished = true
        }
        if (clip.blinks) advanceBlink(dt)
    }

    /** Advance at the clip's own base rate, scaled by [rate]. */
    fun advanceAtBaseRate(dt: Float, rate: Float = 1f) = advance(dt, clip.fps * rate / clip.frames)

    /** The frame to draw. */
    val frame: Int
        get() = if (clip.blinks) blinkFrame() else plainFrame()

    /** How far through the current frame we are, `0..1`. */
    val framePhase: Float
        get() {
            val t = phase * clip.frames
            return t - floor(t)
        }

    private fun plainFrame(): Int = minOf(clip.frames - 1, (phase * clip.frames).toInt())

    private fun blinkFrame(): Int {
        if (blinkClock >= 0f)
            return clip.blinkFrames[
                    minOf(clip.blinkFrames.size - 1, (blinkClock * clip.fps).toInt())]
        val open = clip.openFrames
        return open[(openClock / Tuning.Blink.OPEN_HOLD_S).toInt() % open.size]
    }

    private fun advanceBlink(dt: Float) {
        if (blinkClock >= 0f) {
            blinkClock += dt
            if (blinkClock * clip.fps >= clip.blinkFrames.size) {
                blinkClock = NOT_BLINKING
                scheduleBlink()
            }
            return
        }
        openClock += dt
        nextBlinkIn -= dt
        if (nextBlinkIn <= 0f) blinkClock = 0f
    }

    private fun scheduleBlink() {
        nextBlinkIn = rng.range(Tuning.Blink.GAP_MIN_S, Tuning.Blink.GAP_MAX_S)
    }

    private fun crossedContact(before: Float, now: Float, forward: Boolean): Boolean {
        if (!forward) return false
        val wrapped = now < before
        for (i in clip.contacts.indices) {
            val c = clip.contactPhase(i)
            val hit = if (wrapped) c > before || c <= now else c > before && c <= now
            if (hit) return true
        }
        return false
    }

    private companion object {
        const val NOT_BLINKING = -1f
        const val ALMOST_ONE = 0.9999f
    }
}
