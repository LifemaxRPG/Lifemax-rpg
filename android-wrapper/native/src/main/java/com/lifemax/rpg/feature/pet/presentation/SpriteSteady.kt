package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.RigGrid
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.Clips
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.floor

/**
 * The numbers of the steady sprite (docs/specs/PET_SPRITE_STEADINESS.md): how the body's smooth
 * springs become whole steps of the drawing.
 *
 * Pixel art drawn without smoothing cannot be scaled or turned by a hair without some of its pixels
 * jumping to a neighbour's colour, so every value the drawing reads from the springs is held on a
 * grid of steps, and a step only changes when the value has gone [HOLD] of a step past the middle
 * between two steps: a spring that hovers on an edge keeps its step instead of flicking between the
 * two, and a resting cat draws the very same pixels frame after frame.
 */
internal object SteadyTuning {
    /** The body's turn about the paws is drawn in steps of this many degrees. */
    const val TURN_STEP_DEG = 1.0

    /** Squash and stretch are drawn in steps of this share of the body (about a pixel row). */
    const val SCALE_STEP = 1.0 / 32.0

    /** How far past the middle between two steps a value must go before the step changes. */
    const val HOLD = 0.25

    /**
     * Art rows from the paws to the top of a sitting cat (the shipped `idle_sit` reaches 45): the
     * breath's lever, so the top of the cat rises as far as the old whole-body breath scale took
     * it. A loaf or a sleeping cat reaches its own share of it
     * ([com.lifemax.rpg.feature.pet.body.Clip.height]).
     */
    const val BREATH_REACH_ROWS = 45.0

    /**
     * The share of the reach, from the paws, that stays planted while the cat breathes: the rows
     * above it rise and sink as one by whole device pixels, and the row at the cut stretches.
     */
    const val BREATH_CUT_SHARE = 0.35
}

/**
 * One value held on a grid of whole steps with hysteresis: the first value goes to its nearest step
 * (so a single drawing, a screenshot, is the plain rounding), and from then on the step only
 * changes when the value has gone [hold] of a step past the middle between the step it is on and
 * the next one, and then goes to the nearest step.
 */
internal class SteadyStep(private val hold: Double = SteadyTuning.HOLD) {
    /** The step the value is shown on. */
    var index = 0
        private set

    private var fresh = true

    /** Follow [steps] (the value divided by its step) and return the step it is shown on. */
    fun follow(steps: Double): Int {
        if (steps.isNaN()) return index
        if (fresh || abs(steps - index) > HALF + hold) index = nearest(steps)
        fresh = false
        return index
    }

    /** Forget the memory: the next value goes to its nearest step. */
    fun reset() {
        index = 0
        fresh = true
    }

    private companion object {
        const val HALF = 0.5

        /** Round half away from zero, as [RigGrid] does. */
        fun nearest(v: Double): Int = (if (v >= 0) floor(v + HALF) else -floor(-v + HALF)).toInt()
    }
}

/**
 * What of one cat's pose is drawn, held steady: its turn, squash and stretch ([turnDeg], [scaleX],
 * [scaleY]), how many device pixels its back has risen with the breath ([rise]) and the rig's grid
 * point for ears, tail and whiskers ([grid]), each on whole steps with hysteresis (see
 * [SteadyTuning]). Reading the same pose again changes nothing, so a cat drawn twice in a frame is
 * drawn the same both times. One per cat on screen ([of]); main thread only.
 */
internal class SpriteSteady {
    /** The rig's grid point to draw. */
    val grid = RigGrid()

    /** The body's turn in degrees, clockwise on the screen. */
    var turnDeg = 0f
        private set

    /** The body's horizontal scale, before mirroring. */
    var scaleX = 1f
        private set

    /** The body's vertical scale. */
    var scaleY = 1f
        private set

    /** Device pixels the rows above the breath's cut are lifted (negative: sunk). */
    var rise = 0
        private set

    private val turn = SteadyStep()
    private val width = SteadyStep()
    private val height = SteadyStep()
    private val breath = SteadyStep()
    private val rig = Array(RigMotion.AXES) { SteadyStep() }
    private val values = DoubleArray(RigMotion.AXES)

    /**
     * Read [pose], drawn at [s] device pixels to an art pixel: [kitten] ears and reduced [motion]
     * keep the rig's parts as drawn (as [RigMotion.read]); the body's own steps apply either way.
     */
    fun read(pose: CatPose, kitten: Boolean, motion: Boolean, s: Int) {
        turnDeg =
            (turn.follow(Math.toDegrees(pose.rotation.toDouble()) / SteadyTuning.TURN_STEP_DEG) *
                    SteadyTuning.TURN_STEP_DEG)
                .toFloat()
        scaleX = stepScale(width, pose.scaleX)
        scaleY = stepScale(height, pose.scaleY)
        rise = breath.follow((pose.breath - 1.0) * reach(pose.clip) * s)
        if (!motion) {
            for (axis in rig) axis.reset()
            grid.clear()
            return
        }
        RigMotion.values(pose, kitten, values)
        grid.setSteps(
            rig[RigMotion.EAR_LEFT].follow(values[RigMotion.EAR_LEFT]),
            rig[RigMotion.EAR_RIGHT].follow(values[RigMotion.EAR_RIGHT]),
            rig[RigMotion.FLAT].follow(values[RigMotion.FLAT]),
            rig[RigMotion.TAIL].follow(values[RigMotion.TAIL]),
            rig[RigMotion.CURL].follow(values[RigMotion.CURL]),
            rig[RigMotion.WHISKER].follow(values[RigMotion.WHISKER]),
        )
    }

    private fun stepScale(step: SteadyStep, scale: Float): Float =
        (1.0 + step.follow((scale - 1.0) / SteadyTuning.SCALE_STEP) * SteadyTuning.SCALE_STEP)
            .toFloat()

    companion object {
        private val all = WeakHashMap<CatPose, SpriteSteady>()

        /** The steady drawing of the cat whose body fills [pose] (made the first time). */
        fun of(pose: CatPose): SpriteSteady = all[pose] ?: SpriteSteady().also { all[pose] = it }

        /** Art rows from the paws to the top of the back in [clip]'s posture. */
        fun reach(clip: Int): Double = SteadyTuning.BREATH_REACH_ROWS * Clips.all[clip].height

        /** The picture row of the breath's cut for a clip whose paws are on picture row [feet]. */
        fun cutRow(clip: Int, feet: Int): Int =
            feet + 1 - Math.round(reach(clip) * SteadyTuning.BREATH_CUT_SHARE).toInt()
    }
}
