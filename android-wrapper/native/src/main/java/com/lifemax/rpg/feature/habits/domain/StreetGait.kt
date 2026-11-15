package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * How the people on the pavement move, on top of [Gait]: the street walker is seen close up, so the walk gets the
 * small things a window figure is too small to show. The phase of the cycle still comes from the distance walked
 * ([phase]: one place to route to a walker's own gait signature later), so nobody skates; what is added here lags
 * that phase a little, as loose things do: the wrists trail the arms, the head bobs a moment after the pelvis,
 * and a coat hem, a ponytail or a bag swing behind the body. Also the dog's trot and the cyclist's pedalling.
 * Pure arithmetic, no allocation except the pose arrays made while baking.
 */
object StreetGait {
    /** Frames baked for one cycle of the street walk: twice the window walk's, for the close-up. */
    const val FRAMES = 32

    /**
     * The phase of the street walk [travel] units along, for a walker drawn [size] times the rig.
     */
    fun phase(travel: Float, size: Float, from: Float = Gait.PASSING): Float =
        Gait.phase(travel, size, from)

    /** How far behind the legs the wrists, the head and loose cloth lag, in cycles. */
    const val WRIST_LAG = .06f
    const val HEAD_LAG = .07f
    const val TRAIL_LAG = .09f

    /**
     * The walk's pose at [phi] with the street's refinements: the forearms trail the upper arms a
     * little and the head nods against the body's lean.
     */
    fun pose(phi: Float): FloatArray {
        val p = Gait.walkPose(phi)
        val f = phi - floor(phi)
        val lag = sin(2f * PI.toFloat() * (f - WRIST_LAG)) - sin(2f * PI.toFloat() * f)
        p[K.EN] += 7f * lag
        p[K.EF] -= 7f * lag
        p[K.HEAD] += 1.4f * nod(f)
        return p
    }

    /** The head's own nod, -1 to 1: twice a cycle, behind the pelvis's rise and fall. */
    fun nod(f: Float): Float = cos(4f * PI.toFloat() * (f - .125f - HEAD_LAG))

    /**
     * How much lower than the body carries it the head rides at [f], in units of height: the bob
     * reaches the head a moment after the pelvis.
     */
    fun headDrop(f: Float): Float =
        .011f *
            (cos(4f * PI.toFloat() * (f - .125f)) - cos(4f * PI.toFloat() * (f - .125f - HEAD_LAG)))

    /**
     * The swing of what hangs from the body once a cycle (a coat's hem, a handbag), -1 to 1, behind
     * the legs' swing: positive when the near leg has just swung forward.
     */
    fun trail(f: Float): Float = sin(2f * PI.toFloat() * (f - TRAIL_LAG))

    /**
     * The swing of what hangs from the head or the shoulders twice a cycle (a ponytail, a strap),
     * -1 to 1, behind the bob.
     */
    fun trail2(f: Float): Float = sin(4f * PI.toFloat() * (f - .125f - TRAIL_LAG))

    // ------------------------------------------------------------------------------ the dog

    /** Frames baked for the dog's trot, and its stride in units at the dog's own size. */
    const val DOG_FRAMES = 6
    const val DOG_STRIDE = 9f

    /**
     * The dog's leg angles at [f] of its trot (0 to 1): fore near, fore far, hind near, hind far.
     */
    fun dogLegs(f: Float, out: FloatArray) {
        val w = 2f * PI.toFloat() * f
        // a trot: diagonal pairs move together (near fore with far hind)
        out[0] = 28f * sin(w)
        out[1] = 28f * sin(w + PI.toFloat())
        out[2] = 26f * sin(w + PI.toFloat() + .3f)
        out[3] = 26f * sin(w + .3f)
    }

    /** The dog's body rise at [f] of its trot, units: it lifts a little twice a stride. */
    fun dogBob(f: Float): Float = .25f * (1f - cos(4f * PI.toFloat() * f))

    /** The tail's wag at [f], degrees from level. */
    fun dogTail(f: Float): Float = 18f * sin(4f * PI.toFloat() * f + 1f)

    // ------------------------------------------------------------------------------ the bicycle

    /** Frames baked for one turn of the cranks. */
    const val CRANK_FRAMES = 8

    /** The wheel's radius in units, and how far the bicycle goes for one turn of the cranks. */
    const val WHEEL_R = 4.6f
    const val CRANK_TRAVEL = 2f * PI.toFloat() * WHEEL_R * 2.6f

    /** The cranks' angle after [travel] units, radians (0: the near pedal at the front). */
    fun crank(travel: Float): Float {
        val t = travel / CRANK_TRAVEL
        return (t - floor(t)) * 2f * PI.toFloat()
    }

    /** The wheels' angle after [travel] units, radians. */
    fun wheel(travel: Float): Float = travel / WHEEL_R
}
