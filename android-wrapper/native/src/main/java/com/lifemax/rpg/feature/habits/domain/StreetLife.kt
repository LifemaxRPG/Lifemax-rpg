package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin

/*
 * The life of the pavement and the front road, with no drawing in it: now and then someone walks the whole street
 * from one end to the other (a plain walker, someone with a dog that stops once to sniff, a cyclist, two people
 * side by side), and now and then a car passes on the near road. Like [Ambient], everything is a function of the
 * seconds the tab has been showing and a seed, so a moment is the same every time it is asked for, and nothing
 * is made per frame: the painter keeps the [Passer]s and the [CarPass] this fills in. A walker's steps follow
 * the ground they cover ([StreetGait.phase]), so nobody skates, whatever their size or pace.
 */

/** Who is passing. */
enum class Stroller {
    WALKER,
    DOG_WALKER,
    CYCLIST,
    PAIR,
}

/** Someone on the pavement this moment, written by [StreetLife.passers]. */
class Passer {
    var on = false
    var kind = Stroller.WALKER

    /**
     * Where their feet are along the street, and how far out on the pavement (y, 0 at the wall).
     */
    var x = 0f
    var lane = StreetLife.LANE

    /** 1 walking towards the street's far end (right), -1 towards its start. */
    var dir = 1f
    var look = 0

    /** The rig's scale for this walker ([StreetLife.SIZE] times their height factor). */
    var size = 1f

    /** How far they have walked, in street units. */
    var travel = 0f

    /** The walk's frame, the one beside it and how much of that one shows. */
    var frame = 0
    var next = 0
    var mix = 0f

    /**
     * How settled into standing they are, 0 walking to 1 standing still (a dog walker's pause): the
     * baked settle frames show it.
     */
    var still = 0f

    /** A companion walking beside them (a [Stroller.PAIR]): a little behind and nearer the kerb. */
    var look2 = 0
    var size2 = 1f
    var frame2 = 0
    var next2 = 0
    var mix2 = 0f

    /**
     * The dog ahead of a [Stroller.DOG_WALKER]: where it is, its trot frames, how much it sniffs.
     */
    var dogX = 0f
    var dogFrame = 0
    var dogNext = 0
    var dogMix = 0f
    var sniff = 0f

    /** A [Stroller.CYCLIST]'s cranks and wheels, in radians. */
    var crank = 0f
    var wheel = 0f
}

/** The car on the near road this moment, written by [StreetLife.car]. */
class CarPass {
    var on = false

    /** The middle of the car along the street. */
    var x = 0f

    /** 1 driving right, -1 left. */
    var dir = 1f

    /** Which body and which paint. */
    var body = 0
    var paint = 0

    /** Its speed in street units a second, and its wheels' angle in radians. */
    var speed = 0f
    var wheel = 0f

    /** How far through the pass, 0 to 1. */
    var t = 0f
}

object StreetLife {
    /** The street walkers are drawn this many times the rig's size: a person at street scale. */
    const val SIZE = 1.14f

    /** How far out on the pavement they walk (y, the wall at 0 and the kerb at 16). */
    const val LANE = 10f

    /** Someone sets out about every this many seconds (within [JITTER] of the period's start). */
    const val PERIOD = 55f
    const val JITTER = 15f
    const val CHANCE = .9f

    /** How far beyond the street's ends they appear and vanish. */
    const val MARGIN = 40f

    /** The most passers kept at once. */
    const val MOST = 5

    /** A cyclist's speed next to a walker's. */
    const val CYCLIST = 2.4f

    /** How far ahead of its walker a dog trots, how big it is, and how long it stops to sniff. */
    const val DOG_LEAD = 12f
    const val DOG_SIZE = .9f
    const val SNIFF = 1.8f

    /**
     * Settling to a stand and setting off again take this long, and easing to a stop and from it.
     */
    const val SETTLE = .35f
    const val STOP = .55f
    const val START = .3f

    /** How far behind and how much nearer the kerb a pair's companion walks. */
    const val SIDE_BACK = 4.5f
    const val SIDE_OUT = 3f

    /** The near road: a car about every this many seconds, at this speed. */
    const val CAR_PERIOD = 70f
    const val CAR_JITTER = 20f
    const val CAR_CHANCE = .85f
    const val CAR_SPEED = 95f
    const val CAR_MARGIN = 90f
    const val CAR_BODIES = 3
    const val CAR_PAINTS = 5

    /** How many slots back a passer can still be walking: a long street takes a few. */
    private const val LOOKBACK = 8

    /** The rig's scale for [look]: everyone is about as tall as the street's people are. */
    fun pace(look: Int): Float = SIZE * StreetLooks.ALL[look].height

    /** How fast [look] walks, in street units a second. */
    fun speed(look: Int): Float = pace(look) * Gait.SPEED

    fun kind(seed: Int, k: Int): Stroller {
        val h = Ambient.hash(seed, k, 201)
        return when {
            h < .5f -> Stroller.WALKER
            h < .7f -> Stroller.DOG_WALKER
            h < .85f -> Stroller.CYCLIST
            else -> Stroller.PAIR
        }
    }

    fun look(seed: Int, k: Int): Int =
        (Ambient.hash(seed, k, 202) * StreetLooks.COUNT).toInt().coerceIn(0, StreetLooks.COUNT - 1)

    fun dir(seed: Int, k: Int): Float = if (Ambient.hash(seed, k, 203) < .5f) -1f else 1f

    /** When the passer of slot [k] sets out, in seconds. */
    fun start(seed: Int, k: Int): Float = k * PERIOD + Ambient.hash(seed, k, 204) * JITTER

    fun happens(seed: Int, k: Int): Boolean = k >= 0 && Ambient.hash(seed, k, 200) < CHANCE

    /** How far along the street from [from] to [to] (margins included) a walk goes. */
    fun length(from: Float, to: Float): Float = to - from + 2f * MARGIN

    /**
     * Everyone on the pavement at [seconds] on a street from [from] to [to], written into [out]
     * (the nearest the kerb last); how many there are. At rest ([seconds] 0) nobody.
     */
    fun passers(seconds: Float, seed: Int, from: Float, to: Float, out: Array<Passer>): Int {
        var n = 0
        for (p in out) p.on = false
        if (seconds <= 0f) return 0
        val k0 = floor(seconds / PERIOD).toInt()
        var k = k0
        while (k > k0 - LOOKBACK && n < out.size && n < MOST) {
            if (happens(seed, k)) {
                val t = seconds - start(seed, k)
                if (t >= 0f && fill(seed, k, t, from, to, out[n])) n++
            }
            k--
        }
        return n
    }

    /**
     * Fills [p] with the passer of slot [k], [t] seconds into their walk; false once it is over.
     */
    fun fill(seed: Int, k: Int, t: Float, from: Float, to: Float, p: Passer): Boolean {
        p.on = false
        p.still = 0f
        p.sniff = 0f
        val kind = kind(seed, k)
        val look = look(seed, k)
        val dir = dir(seed, k)
        val size = pace(look)
        val length = length(from, to)
        val v = speed(look) * if (kind == Stroller.CYCLIST) CYCLIST else 1f
        var travel: Float
        when (kind) {
            Stroller.DOG_WALKER -> {
                // they stop once, on the passing pose (a whole number of strides along), settle to
                // a stand while the dog sniffs, and set off again
                val stride = Gait.STRIDE * size
                val at = round(length * (.3f + .4f * Ambient.hash(seed, k, 205)) / stride) * stride
                val tc = max(0f, (at - v * STOP / 2f) / v)
                val tStop = tc + STOP
                val tHold = tStop + SNIFF
                val tGo = tHold + START
                if (t < tStop) {
                    travel = gone(t, v, 0f, tc, STOP)
                    if (t > tStop - SETTLE) p.still = (t - (tStop - SETTLE)) / SETTLE
                } else if (t < tHold) {
                    travel = at
                    p.still = 1f
                } else {
                    travel = at + gone(t - tHold, v, START, 1e9f, 0f)
                    p.still = (1f - (t - tHold) / SETTLE).coerceIn(0f, 1f)
                }
                p.sniff = p.still
            }
            else -> travel = v * t
        }
        if (travel > length) return false
        p.on = true
        p.kind = kind
        p.look = look
        p.dir = dir
        p.size = size
        // on foot the ground covered is lived, not metered: the steps drift and hesitate a little
        // ([Gait.liveTravel]); the same live distance places them and picks the frame, so the feet
        // never slide. A cyclist rolls steadily, and the dog walker keeps the metered pace so the
        // stop lands exactly on the passing pose.
        val live =
            if (kind == Stroller.CYCLIST || kind == Stroller.DOG_WALKER) travel
            else Gait.liveTravel(travel, size, seed * 31 + k)
        p.travel = live
        p.lane = LANE - 2f + 4f * Ambient.hash(seed, k, 206)
        p.x = if (dir > 0f) from - MARGIN + live else to + MARGIN - live
        walk(StreetGait.phase(live, size), p)
        when (kind) {
            Stroller.DOG_WALKER -> {
                p.dogX = p.x + dir * DOG_LEAD
                val dp = live / (StreetGait.DOG_STRIDE * DOG_SIZE)
                val phase = dp - floor(dp)
                p.dogFrame = Gait.nearest(phase, StreetGait.DOG_FRAMES)
                p.dogNext = Gait.neighbour(phase, StreetGait.DOG_FRAMES)
                p.dogMix = abs(Gait.offset(phase, StreetGait.DOG_FRAMES))
            }
            Stroller.CYCLIST -> {
                p.crank = StreetGait.crank(travel)
                p.wheel = StreetGait.wheel(travel)
            }
            Stroller.PAIR -> {
                p.look2 =
                    (look + 1 + (Ambient.hash(seed, k, 207) * (StreetLooks.COUNT - 1)).toInt()) %
                        StreetLooks.COUNT
                p.size2 = pace(p.look2)
                val phase =
                    StreetGait.phase(travel, p.size2, Gait.PASSING + Ambient.hash(seed, k, 208))
                p.frame2 = Gait.nearest(phase, StreetGait.FRAMES)
                p.next2 = Gait.neighbour(phase, StreetGait.FRAMES)
                p.mix2 = abs(Gait.offset(phase, StreetGait.FRAMES))
            }
            else -> {}
        }
        return true
    }

    private fun walk(phase: Float, p: Passer) {
        p.frame = Gait.nearest(phase, StreetGait.FRAMES)
        p.next = Gait.neighbour(phase, StreetGait.FRAMES)
        p.mix = abs(Gait.offset(phase, StreetGait.FRAMES))
    }

    /**
     * How far a walk of cruising speed [v] has gone [t] seconds in: [up] seconds to come up to
     * pace, [tc] at pace, [down] to stop (as [Visit] walks).
     */
    fun gone(t: Float, v: Float, up: Float, tc: Float, down: Float): Float {
        val pi = PI.toFloat()
        if (up > 0f && t < up) return v * (t / 2f - up / (2f * pi) * sin(pi * t / up))
        val a = v * up / 2f
        if (t < up + tc || down <= 0f) return a + v * (t - up)
        val tau = min(t - up - tc, down)
        return a + v * tc + v * (tau / 2f + down / (2f * pi) * sin(pi * tau / down))
    }

    // ---- the near road

    fun carHappens(seed: Int, k: Int): Boolean = k >= 0 && Ambient.hash(seed, k, 220) < CAR_CHANCE

    fun carStart(seed: Int, k: Int): Float =
        k * CAR_PERIOD + Ambient.hash(seed, k, 221) * CAR_JITTER

    fun carDir(seed: Int, k: Int): Float = if (Ambient.hash(seed, k, 222) < .5f) -1f else 1f

    /** The car on the near road at [seconds], driving the street from end to end; false if none. */
    fun car(seconds: Float, seed: Int, from: Float, to: Float, into: CarPass): Boolean {
        into.on = false
        if (seconds <= 0f) return false
        val length = to - from + 2f * CAR_MARGIN
        val k0 = floor(seconds / CAR_PERIOD).toInt()
        var k = k0
        while (k > k0 - 3) {
            if (carHappens(seed, k)) {
                val t = seconds - carStart(seed, k)
                val speed = CAR_SPEED * (.9f + .25f * Ambient.hash(seed, k, 223))
                val travel = speed * t
                if (t >= 0f && travel <= length) {
                    into.on = true
                    into.dir = carDir(seed, k)
                    into.x =
                        if (into.dir > 0f) from - CAR_MARGIN + travel else to + CAR_MARGIN - travel
                    into.body =
                        (Ambient.hash(seed, k, 224) * CAR_BODIES)
                            .toInt()
                            .coerceIn(0, CAR_BODIES - 1)
                    into.paint =
                        (Ambient.hash(seed, k, 225) * CAR_PAINTS)
                            .toInt()
                            .coerceIn(0, CAR_PAINTS - 1)
                    into.speed = speed
                    into.wheel = travel / CAR_WHEEL
                    into.t = travel / length
                    return true
                }
            }
            k--
        }
        return false
    }

    /** The near car's wheel radius, in street units. */
    const val CAR_WHEEL = 4.2f
}
