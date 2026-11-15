package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * How people walk. One cycle is two steps: the classic key poses (contact, down, passing, up, then the same on the
 * other leg) are worked out, not guessed, from where the feet are: the planted foot is fixed on the ground while
 * the body moves over it at a steady speed, the swinging foot is lifted over the floor and brought forward, and the
 * pelvis rides at the height the two legs allow. That makes the stride, the speed and the knees agree, so a walker
 * drawn at [Gait.SPEED] has no foot that slides.
 *
 * Everything in a walk is a function of the DISTANCE walked: the phase of the cycle is distance over [Gait.STRIDE],
 * so a walker that starts, stops or is drawn smaller takes shorter or quicker steps and still never skates. Pure
 * arithmetic with no Compose and no Android.
 */

object Gait {
    /** Frames baked for one cycle of a walk. */
    const val FRAMES = 16

    /** A walk's cycle (two steps) takes this long, in seconds: 110 steps a minute. */
    const val CYCLE = 1.09f

    /** How far one step goes, in units. */
    const val STEP = 7.0f

    /** How far a whole cycle goes: the foot that landed lands again. */
    const val STRIDE = 2f * STEP

    /** The speed of a walk at the rig's own size, in units a second. */
    const val SPEED = STRIDE / CYCLE

    /**
     * The phase of the passing pose (legs together under the body): where a walk starts and stops.
     */
    const val PASSING = .25f

    /**
     * The jog on the treadmill: 16 frames to a cycle of this many seconds (about 165 steps a
     * minute).
     */
    const val JOG_FRAMES = 16
    const val JOG_CYCLE = .73f

    /**
     * The extra frames of the turn that takes a walker from the passing pose in profile round to
     * facing the street (standing): four on the way and the standing pose itself, so that the
     * activity that takes over (looking out) begins on exactly the picture the turn ends on.
     */
    const val TURN_FRAMES = 5

    /**
     * The poses between [from] and [to] (not including [from]), [count] of them, the last being
     * [to]: eased, so that a turn starts and ends gently.
     */
    fun between(from: FloatArray, to: FloatArray, count: Int): List<FloatArray> =
        List(count) { i ->
            val s = (i + 1f) / count
            val e = s * s * (3f - 2f * s)
            FloatArray(K.SIZE) { k -> from[k] + (to[k] - from[k]) * e }
        }

    /** The phase [distance] along a walk is at, for a walker drawn [size] times the rig. */
    fun phase(distance: Float, size: Float, from: Float = PASSING): Float {
        val p = from + distance / (STRIDE * size)
        return p - floor(p)
    }

    /** The baked frame nearest to [phase] (0 to 1, wrapping) of a loop of [frames]. */
    fun nearest(phase: Float, frames: Int): Int {
        val i = floor((phase - floor(phase)) * frames + .5f).toInt()
        return if (i >= frames) i - frames else i
    }

    /**
     * How far [phase] is from the frame [nearest] picks, in frames (-.5 up to .5): the sign says
     * which side the next nearest frame is on.
     */
    fun offset(phase: Float, frames: Int): Float {
        val t = (phase - floor(phase)) * frames
        return t - floor(t + .5f)
    }

    /** The frame beside [nearest] that [offset] leans towards. */
    fun neighbour(phase: Float, frames: Int): Int {
        val n = nearest(phase, frames)
        val o = offset(phase, frames)
        val i = if (o >= 0f) n + 1 else n - 1
        return if (i >= frames) 0 else if (i < 0) frames - 1 else i
    }

    // ------------------------------------------------------------------------------ the walk

    /** The legs of the standard figure, as [FigBuilder] draws them. */
    private const val THIGH = 5.9
    private const val SHIN = 5.7
    private const val LEG = THIGH + SHIN

    /**
     * Where the foot sits: from the ankle, the heel and the ball, so that the sole can be found.
     */
    private fun heelX(t: Double) = .3 * sin(t) - .25 * cos(t)

    private fun toeX(t: Double) = .38 * sin(t) + 2.1 * cos(t)

    private fun heelBottom(t: Double) = .3 * cos(t) + .25 * sin(t) + .62

    private fun toeBottom(t: Double) = .38 * cos(t) - 2.1 * sin(t) + .47

    /** How far below the ankle the lowest part of a foot toes-up by [t] radians is. */
    private fun sole(t: Double) = max(heelBottom(t), toeBottom(t))

    private fun rad(d: Double) = d * PI / 180.0

    private fun smooth(x: Double): Double {
        val c = x.coerceIn(0.0, 1.0)
        return .5 - .5 * cos(PI * c)
    }

    // when in a foot's own cycle (0 at its heel strike) the heel is down, flat, heel-off, toe-off
    private const val U_FLAT = .13
    private const val U_HEEL_OFF = .42
    private const val U_TOE_OFF = .62

    /** The foot's tilt from the floor, in degrees, toes up, through its own cycle [u]. */
    private const val HEEL_STRIKE = 17.0
    private const val TOE_OFF = -42.0

    private fun tilt(u: Double): Double =
        when {
            u < U_FLAT -> HEEL_STRIKE * (.5 + .5 * cos(PI * u / U_FLAT))
            u < U_HEEL_OFF -> 0.0
            u < U_TOE_OFF -> TOE_OFF * smooth((u - U_HEEL_OFF) / (U_TOE_OFF - U_HEEL_OFF))
            else -> TOE_OFF + (HEEL_STRIKE - TOE_OFF) * smooth((u - U_TOE_OFF) / (1.0 - U_TOE_OFF))
        }

    /** The heel's world x at the heel strike: where it lands, ahead of the pelvis. */
    private const val LAND = 4.2

    private val FLAT = LAND - heelX(0.0)

    /**
     * The ankle's x in the world from the heel strike on (the pelvis is at STRIDE * u), stance
     * only.
     */
    private fun ankleWorld(u: Double): Double =
        when {
            u < U_FLAT -> LAND - heelX(rad(tilt(u)))
            u < U_HEEL_OFF -> FLAT
            else -> FLAT + toeX(0.0) - toeX(rad(tilt(u)))
        }

    private fun stance(u: Double) = ankleWorld(u) - Gait.STRIDE * u

    /** The clearance of the swinging foot over the floor, at [s] (0 to 1) of the swing. */
    private fun lift(s: Double): Double {
        val a = sin(PI * s)
        return 1.45 * a * a * (1.0 - .35 * s)
    }

    /** The ankle's x, from the pelvis, in the foot's own cycle [u]. */
    private fun ankleX(u: Double): Double {
        if (u < U_TOE_OFF) return stance(u)
        val d = 1.0 - U_TOE_OFF
        val e = 1e-4
        val x0 = stance(U_TOE_OFF)
        val m0 = (stance(U_TOE_OFF) - stance(U_TOE_OFF - e)) / e * d
        val x1 = stance(0.0)
        val m1 = (stance(e) - stance(0.0)) / e * d
        val s = (u - U_TOE_OFF) / d
        val s2 = s * s
        val s3 = s2 * s
        return (2 * s3 - 3 * s2 + 1) * x0 +
            (s3 - 2 * s2 + s) * m0 +
            (-2 * s3 + 3 * s2) * x1 +
            (s3 - s2) * m1
    }

    /** The ankle's height over the floor in the foot's own cycle [u]. */
    private fun ankleHeight(u: Double): Double {
        val t = rad(tilt(u))
        val extra = if (u < U_TOE_OFF) 0.0 else lift((u - U_TOE_OFF) / (1.0 - U_TOE_OFF))
        return sole(t) + extra
    }

    /**
     * The pelvis's height over the floor at [phi]: as high as the two legs let it be with the knee
     * as straight as the walk wants it (a little bent, more so on the down pose), so that it is
     * lowest at the contacts and the down poses, and highest on the way up.
     */
    private fun pelvisHeight(phi: Double): Double {
        val dip = .5 + .5 * cos(4 * PI * (phi - .125))
        val flex = rad(KNEE_STRAIGHT + KNEE_DOWN * dip * dip)
        val d = LEG * cos(flex / 2)
        val un = phi
        val uf = (phi + .5) % 1.0
        val hip = hipX(phi)
        return min(reach(un, hip, d), reach(uf, -hip, d))
    }

    /**
     * How high a hip at [hip] from the pelvis's middle sits over an ankle in the cycle [u], the leg
     * [d] long.
     */
    private fun reach(u: Double, hip: Double, d: Double): Double =
        ankleHeight(u) + sqrt(max(0.0, d * d - sq(ankleX(u) - hip)))

    /** The near hip's x from the pelvis's middle: the pelvis turns a little with each step. */
    private fun hipX(phi: Double): Double = HIP_SWING * cos(2 * PI * phi)

    private const val HIP_SWING = .5
    private const val SHOULDER_SWING = .7
    private const val BASE_HIP = 1.5 - 1.1 * .94
    private const val BASE_SHOULDER = 2.9 - 2.4 * .94

    private fun sq(x: Double) = x * x

    /**
     * The knee's bend at its straightest and how much more it gives on the down pose, in degrees.
     */
    private const val KNEE_STRAIGHT = 11.0
    private const val KNEE_DOWN = 9.0

    /**
     * One leg's angles worked out so the ankle is at ([x], [y]) from the hip: thigh, knee, shin,
     * all in degrees.
     */
    private fun leg(x: Double, y: Double, out: DoubleArray) {
        val d0 = sqrt(x * x + y * y)
        val d = min(d0, LEG * .9985)
        val g =
            acos(((THIGH * THIGH + SHIN * SHIN - d * d) / (2 * THIGH * SHIN)).coerceIn(-1.0, 1.0))
        val knee = PI - g
        val a = acos(((THIGH * THIGH + d * d - SHIN * SHIN) / (2 * THIGH * d)).coerceIn(-1.0, 1.0))
        val dir = atan2(x, y)
        val thigh = dir + a
        out[0] = thigh * 180 / PI
        out[1] = knee * 180 / PI
        out[2] = (thigh - knee) * 180 / PI
    }

    /** The walk's pose at [phi] of the cycle (0 = the near heel strikes). */
    fun walkPose(phi: Float): FloatArray {
        val f = phi.toDouble() - floor(phi.toDouble())
        val h = pelvisHeight(f)
        val un = f
        val uf = (f + .5) % 1.0
        val ln = DoubleArray(3)
        val lf = DoubleArray(3)
        val hip = hipX(f)
        leg(ankleX(un) - hip, h - ankleHeight(un), ln)
        leg(ankleX(uf) + hip, h - ankleHeight(uf), lf)
        val ann = tilt(un) - ln[2]
        val anf = tilt(uf) - lf[2]
        val c = cos(2 * PI * (f + .02))
        val arm = 25.0
        val an = -arm * c
        val af = arm * c
        val en = 13.0 + 17.0 * (an + arm) / (2 * arm)
        val ef = 13.0 + 17.0 * (af + arm) / (2 * arm)
        val lean = 3.5 + .8 * cos(4 * PI * (f - .125))
        return pose(
            turn = .94f,
            lean = lean.toFloat(),
            head = (-lean * .7).toFloat(),
            an = an.toFloat(),
            en = en.toFloat(),
            af = af.toFloat(),
            ef = ef.toFloat(),
            ln = ln[0].toFloat(),
            kn = ln[1].toFloat(),
            lf = lf[0].toFloat(),
            kf = lf[1].toFloat(),
            ann = ann.toFloat(),
            anf = anf.toFloat(),
            twist = (hip - BASE_HIP).toFloat(),
            shx = (-SHOULDER_SWING * cos(2 * PI * f) - BASE_SHOULDER).toFloat(),
        )
    }

    // ------------------------------------------------------------------------------ the jog

    /** A periodic curve through [t] (one value at each of its equal steps round a cycle) at [u]. */
    private fun round(t: DoubleArray, u: Double): Double {
        val n = t.size
        val x = (u - floor(u)) * n
        val i = floor(x).toInt()
        val f = x - i
        val a = t[(i - 1 + n) % n]
        val b = t[i % n]
        val c = t[(i + 1) % n]
        val d = t[(i + 2) % n]
        return .5 *
            (2 * b +
                (-a + c) * f +
                (2 * a - 5 * b + 4 * c - d) * f * f +
                (-a + 3 * b - 3 * c + d) * f * f * f)
    }

    /**
     * The knee's bend and the foot's tilt of a running leg, 16 steps round its own cycle (0 = the
     * foot lands).
     */
    private val JOG_KNEE =
        doubleArrayOf(
            16.0,
            30.0,
            40.0,
            38.0,
            32.0,
            28.0,
            34.0,
            58.0,
            84.0,
            104.0,
            108.0,
            100.0,
            82.0,
            62.0,
            40.0,
            24.0,
        )
    private val JOG_TILT =
        doubleArrayOf(
            6.0,
            2.0,
            -2.0,
            -8.0,
            -16.0,
            -28.0,
            -38.0,
            -30.0,
            -18.0,
            -8.0,
            -4.0,
            0.0,
            4.0,
            8.0,
            10.0,
            8.0,
        )

    private fun jogThigh(u: Double) = 15.0 + 27.0 * cos(2 * PI * (u - .85))

    /** The jog's pose at [phi] of the cycle (0 = the near foot lands). */
    fun jogPose(phi: Float): FloatArray {
        val f = phi.toDouble() - floor(phi.toDouble())
        val uf = (f + .5) % 1.0
        val ln = jogThigh(f)
        val lf = jogThigh(uf)
        val kn = round(JOG_KNEE, f)
        val kf = round(JOG_KNEE, uf)
        val ann = round(JOG_TILT, f) - (ln - kn)
        val anf = round(JOG_TILT, uf) - (lf - kf)
        // in flight (after one foot leaves and before the other lands) the whole body is off the
        // floor
        var lift = 0.0
        for (s in doubleArrayOf(.36, .86)) {
            val q = (f - s) / .14
            if (q in 0.0..1.0) lift = 1.15 * sin(PI * q) * sin(PI * q)
        }
        val c = cos(2 * PI * (f - .03))
        val an = -36.0 * c
        val af = 36.0 * c
        val sw = cos(2 * PI * f)
        return pose(
            turn = .92f,
            lean = 9f,
            head = -3f,
            an = an.toFloat(),
            en = (84.0 + 10.0 * (an + 36) / 72).toFloat(),
            af = af.toFloat(),
            ef = (84.0 + 10.0 * (af + 36) / 72).toFloat(),
            ln = ln.toFloat(),
            kn = kn.toFloat(),
            lf = lf.toFloat(),
            kf = kf.toFloat(),
            lift = lift.toFloat(),
            ann = ann.toFloat(),
            anf = anf.toFloat(),
            twist = (.5 * sw - BASE_HIP * 0).toFloat(),
            shx = (-.7 * sw).toFloat(),
        )
    }

    /** The jog's key poses: eight, round to the first. */
    val JOG_KEYS: List<FloatArray> = List(9) { jogPose((it % 8) / 8f) }

    /**
     * The eight key poses of a walk (contact, down, passing, up, and again on the other leg) and
     * back to the first.
     */
    val WALK_KEYS: List<FloatArray> = List(9) { walkPose((it % 8) / 8f) }
}
