package com.lifemax.rpg.feature.pet.body

/** How the cat was moving when it decided to turn round: sets the turn's pace. */
internal enum class TurnPace {
    /** Standing or sitting: an unhurried turn on the spot. */
    STILL,

    /** Walking (or trotting toward or away from the camera): a quicker turn after a short stop. */
    WALK,

    /** Galloping: a skid to a stop (the body's skid lean and squash), then the quickest turn. */
    RUN,
}

/**
 * A turn round, step by step, with the lab's `turn` row (docs/specs/PET_ART_LAB.md section 5): the
 * side view with the head already turned to the camera (the head leads), the foreshortened
 * three-quarter view with the body dipped (the body follows), the front view, the three-quarter
 * view of the other side, and then the first frame again facing the new way (the head still
 * glancing at the camera as the body arrives) before the cat's own clip takes over. Every step is a
 * whole art frame placed on the same paws: nothing is scaled or slid, so the drawing changes only
 * when the frame does (docs/specs/PET_SPRITE_STEADINESS.md).
 *
 * The stages: [active] from the request on. A cat that is moving first plants its feet (the stride
 * keeps playing while it brakes, so the feet never slip; [Locomotion] brakes harder while a turn is
 * waiting); the row is shown ([showing]) once it stands. The way the sprite faces ([dir]) flips at
 * the start of the last step. Asked to turn back before that, the frames run backwards to the start
 * and the cat keeps its facing; asked during the last step, the turn ends first and the next
 * request starts a new one. Nothing here allocates.
 */
internal class Turn {
    /** The way the sprite is drawn: +1 right, -1 left. It flips at the start of the last step. */
    var dir = 1
        private set

    /** The way the cat faces once the turn is over (equal to [dir] when not turning). */
    var target = 1
        private set

    /** The pace of the turn in progress (or of the last one). */
    var pace = TurnPace.STILL
        private set

    /** The frame of [Clips.TURN] to draw while [showing]. */
    var frame = 0
        private set

    /** True on the tick the sprite flipped. */
    var flippedNow = false
        private set

    /** True on the tick the turn row came on screen (the feet are planted). */
    var plantedNow = false
        private set

    private var stage = Stage.NONE
    private var clock = 0f
    private var seconds = 0f
    private var planted = 0f
    private var backwards = false
    private var lastGait = Gait.STAND
    private var sinceEnd = Tuning.Turn.STRIDE_HOLD_S

    /** True from a request until the turn is over: the cat holds still for it. */
    val active: Boolean
        get() = stage != Stage.NONE

    /** True while the turn row is on screen in place of the cat's own clip. */
    val showing: Boolean
        get() = stage == Stage.SHOW

    /** The clip on screen: the turn row while [showing], else the cat's own [clip]. */
    fun shown(clip: Clip): Clip = if (showing) Clips.TURN else clip

    /** The frame on screen: the turn's while [showing], else the cat's own [frame]. */
    fun shownFrame(frame: Int): Int = if (showing) this.frame else frame

    /**
     * True while a stopped stride should be held rather than sat down: during a turn and for a
     * moment after it, while the cat sets off again.
     */
    val holdsStride: Boolean
        get() = active || sinceEnd < Tuning.Turn.STRIDE_HOLD_S

    /** True while the turn waits for the moving cat to stand. */
    val planting: Boolean
        get() = stage == Stage.PLANT

    /**
     * Turn toward [wanted]. Ignored when already facing that way, or already turning that way. When
     * the cat is turning the other way and has not flipped yet, it turns back; during the last step
     * it finishes first (callers ask again every tick, so the request is not lost).
     */
    fun request(wanted: Int) {
        if (stage == Stage.NONE) {
            if (wanted == dir) return
            target = wanted
            pace = paceOf(lastGait)
            stage = Stage.PLANT
            planted = 0f
            backwards = false
            return
        }
        if (wanted == target) {
            backwards = false
            return
        }
        when {
            stage == Stage.PLANT -> {
                target = dir
                end()
            }
            !flippedYet() -> backwards = true
        }
    }

    /** Face [wanted] at once (placing the cat, or a move that takes over the facing itself). */
    fun snapTo(wanted: Int) {
        dir = wanted
        target = wanted
        end()
    }

    /**
     * Advance one tick: [moving] is whether the cat has any speed left (from [Locomotion]: the row
     * waits until the paws are still, so they never slide under it), [gait] its stride, which sets
     * the pace of a turn asked for on the next tick.
     */
    fun update(dt: Float, moving: Boolean, gait: Gait) {
        flippedNow = false
        plantedNow = false
        when (stage) {
            Stage.NONE -> {
                lastGait = gait
                sinceEnd += dt
            }
            Stage.PLANT -> plantTick(dt, moving)
            Stage.SHOW -> showTick(dt)
        }
    }

    private fun plantTick(dt: Float, moving: Boolean) {
        planted += dt
        if (moving && planted < Tuning.Turn.PLANT_MAX_S) return
        stage = Stage.SHOW
        seconds = secondsOf(pace)
        clock = 0f
        frame = STEP_FRAMES[0]
        plantedNow = true
    }

    private fun showTick(dt: Float) {
        clock += if (backwards) -dt else dt
        if (backwards && clock <= 0f) {
            target = dir
            end()
            return
        }
        if (clock >= seconds) {
            end()
            return
        }
        val step = stepAt(clock / seconds)
        if (step == LAST_STEP && dir != target) {
            dir = target
            flippedNow = true
        }
        frame = STEP_FRAMES[step]
    }

    private fun flippedYet(): Boolean = stage == Stage.SHOW && dir == target

    private fun end() {
        if (stage == Stage.SHOW) sinceEnd = 0f
        stage = Stage.NONE
        clock = 0f
        backwards = false
        frame = 0
    }

    private enum class Stage {
        NONE,
        PLANT,
        SHOW,
    }

    private companion object {
        /** The frames of the `turn` row, step by step; the last is drawn facing the new way. */
        val STEP_FRAMES = intArrayOf(0, 1, 2, 3, 0)
        const val LAST_STEP = 4

        fun paceOf(gait: Gait): TurnPace =
            when (gait) {
                Gait.STAND -> TurnPace.STILL
                Gait.RUN -> TurnPace.RUN
                Gait.WALK,
                Gait.FRONT,
                Gait.BACK -> TurnPace.WALK
            }

        fun secondsOf(pace: TurnPace): Float =
            when (pace) {
                TurnPace.STILL -> Tuning.Turn.STILL_S
                TurnPace.WALK -> Tuning.Turn.WALK_S
                TurnPace.RUN -> Tuning.Turn.RUN_S
            }

        /** The step shown at [u] (0..1 of the turn), by the steps' shares of its time. */
        fun stepAt(u: Float): Int {
            var edge = 0f
            val shares = Tuning.Turn.STEP_SHARES
            for (i in shares.indices) {
                edge += shares[i]
                if (u < edge) return i
            }
            return shares.size - 1
        }
    }
}
