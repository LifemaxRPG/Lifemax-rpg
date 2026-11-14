package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/*
 * The street's own life, with no drawing in it: what the sky and the far city do at any moment. Everything is
 * a function of the seconds the tab has been showing and a seed, so a moment is the same every time it is asked
 * for (a test, a screenshot) and nothing has to be remembered between frames. Nothing here makes an object per
 * frame: a pass through the sky is written into a [Pass] the painter keeps.
 *
 * Positions are shares: x of the view's width (a little past either edge while something enters or leaves), y of
 * the sky's band, 0 under the heading to 1 at the horizon.
 */

/** Something passing through the sky this moment, written by a schedule. */
class Pass {
    /** Whether it is in the sky now. */
    var on = false
    var x = 0f
    var y = 0f

    /** 1 flying right, -1 left. */
    var dir = 1f

    /** How far through its pass, 0 to 1. */
    var t = 0f

    /** Which pass this is (a different one each period), for its own small differences. */
    var pass = 0
}

/** A star: across and down the sky (0 to 1), its radius in dp, its brightness, its own twinkle. */
data class Star(
    val x: Float,
    val y: Float,
    val r: Float,
    val alpha: Float,
    val phase: Float,
    val speed: Float,
)

/** The shapes a cloud takes. */
enum class CloudKind {
    CUMULUS,
    STRATUS,
    PUFF,
}

/**
 * A cloud of the day's sky: where it starts across the view ([x0], a share of the width), which
 * [lane] of the sky it drifts in ([y], a share of the sky's band; every cloud has a lane of its
 * own, so none sits on another and all stay clear of the far towers), its [scale], how far it is
 * ([depth], 0 far to 1 near: the near and the big drift faster), its drift ([speed], shares of the
 * width a second) and its [lobes]: four numbers each (across, up as a negative, half width, half
 * height) in dp at scale 1, with [reach] its half width and [rise] its height in dp at scale 1.
 */
class Cloud(
    val kind: CloudKind,
    val lane: Int,
    val x0: Float,
    val y: Float,
    val scale: Float,
    val depth: Float,
    val speed: Float,
    val lobes: FloatArray,
    val reach: Float,
    val rise: Float,
)

/** One building of a far skyline: from [x], [w] wide and [h] tall, in dp. */
data class Building(val x: Float, val w: Float, val h: Float)

object Ambient {
    /** The far city repeats every this many dp, so the street can be scrolled as far as it goes. */
    const val SKYLINE_SPAN = 960f

    /** How fast people in the windows move: steps a second. */
    const val PEOPLE_FPS = 12f

    const val SHOOT_PERIOD = 37f
    const val SHOOT_SECONDS = 1.1f
    const val PLANE_PERIOD = 110f
    const val PLANE_SECONDS = 34f
    const val BIRD_PERIOD = 70f
    const val BIRD_SECONDS = 19f
    const val BLIMP_PERIOD = 210f
    const val BLIMP_SECONDS = 110f
    const val CAR_PERIOD = 13f
    const val CAR_SECONDS = 8f

    /** The birds of a flock. */
    const val FLOCK = 5

    /** A number from 0 up to but not 1 for ([a], [b], [c]), the same every time. */
    fun hash(a: Int, b: Int = 0, c: Int = 0): Float {
        var h = a * 374761393 + b * 668265263 + c * -1028477379
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h.toLong() and 0xFFFFFFL).toFloat() / 16777216f
    }

    /** [count] stars, the same every time for [seed]: each twinkles at its own pace. */
    fun stars(count: Int, seed: Int = 7): List<Star> =
        List(count) { i ->
            val big = hash(seed, i, 9) < .14f
            Star(
                hash(seed, i, 1),
                hash(seed, i, 2) * .92f,
                if (big) 1.3f else .7f + hash(seed, i, 3) * .3f,
                .35f + hash(seed, i, 4) * .6f,
                hash(seed, i, 5) * 2f * PI.toFloat(),
                .7f + hash(seed, i, 6) * 1.9f,
            )
        }

    /** How bright [star] is at [seconds]: between .4 and 1 of its own brightness. */
    fun twinkle(star: Star, seconds: Float): Float =
        .7f + .3f * sin(seconds * star.speed + star.phase)

    /**
     * A row of buildings from [from] to [to], the same for the same [seed]: each 12 to 31 wide and
     * between [lo] and [hi] tall.
     */
    fun skyline(seed: Int, lo: Int, hi: Int, from: Float, to: Float): List<Building> {
        var r = seed
        var x = from
        val out = ArrayList<Building>()
        while (x < to) {
            r = (r * 73 + 19) % 997
            val w = 12 + r % 20
            r = (r * 73 + 19) % 997
            out += Building(x, w.toFloat(), (lo + r % (hi - lo)).toFloat())
            x += w
        }
        return out
    }

    /** Where a skyline building starting at [x] is drawn while the row has slid by [slide]. */
    fun wrap(x: Float, slide: Float): Float {
        val v = (x - slide) % SKYLINE_SPAN
        return if (v < -SKYLINE_SPAN / 2f) v + SKYLINE_SPAN else v
    }

    /**
     * Whether window [w] of far building [b] has its light on at [seconds]: each switches on or off
     * on its own slow clock (half a minute to a minute and a half), and about [share] of them are
     * on at any time.
     */
    fun farWindowLit(b: Int, w: Int, seconds: Float, share: Float): Boolean {
        val period = 30f + 60f * hash(b, w, 11)
        val k = floor((seconds + period * hash(b, w, 12)) / period).toInt()
        return hash(b * 31 + w, k, 13) < share
    }

    /**
     * An aircraft-warning light at [seconds]: on for a moment every 1.6 s, each its own [index] out
     * of step with the others. 0 off to 1 on, with a short fade.
     */
    fun warning(index: Int, seconds: Float): Float {
        val period = 1.6f
        val at = ((seconds + index * .53f) % period + period) % period
        return when {
            at < .08f -> at / .08f
            at < .3f -> 1f
            at < .42f -> 1f - (at - .3f) / .12f
            else -> 0f
        }
    }

    /**
     * A pass of [period] seconds' schedule: in some periods (a [chance] of them) something crosses
     * for [seconds] at a time of its own within the period. Writes [out]; true while it is up.
     */
    private fun pass(
        now: Float,
        seed: Int,
        salt: Int,
        period: Float,
        seconds: Float,
        chance: Float,
        out: Pass,
    ): Boolean {
        out.on = false
        if (now <= 0f) return false
        val k = floor(now / period).toInt()
        out.pass = k
        if (hash(seed, k, salt) >= chance) return false
        val start = hash(seed, k, salt + 1) * (period - seconds)
        val t = (now - k * period - start) / seconds
        if (t < 0f || t >= 1f) return false
        out.on = true
        out.t = t
        out.dir = if (hash(seed, k, salt + 2) < .5f) -1f else 1f
        return true
    }

    /** Across the view at [t] of a pass, going [dir]: from just past one edge to past the other. */
    private fun across(t: Float, dir: Float): Float =
        if (dir > 0f) -.12f + 1.24f * t else 1.12f - 1.24f * t

    /** A shooting star, now and then on a clear night: a short streak, high in the sky. */
    fun shootingStar(seconds: Float, seed: Int, out: Pass): Boolean {
        if (!pass(seconds, seed, 20, SHOOT_PERIOD, SHOOT_SECONDS, .5f, out)) return false
        val k = out.pass
        out.x = .18f + .64f * hash(seed, k, 23) + out.dir * .26f * out.t
        out.y = .04f + .3f * hash(seed, k, 24) + .14f * out.t
        return true
    }

    /** An airplane crossing slowly, climbing a little: about every other minute. */
    fun plane(seconds: Float, seed: Int, out: Pass): Boolean {
        if (!pass(seconds, seed, 30, PLANE_PERIOD, PLANE_SECONDS, .6f, out)) return false
        out.x = across(out.t, out.dir)
        out.y = .1f + .28f * hash(seed, out.pass, 33) - .05f * out.t
        return true
    }

    /** A flock of birds on a gentle arc: by day and at dusk. */
    fun birds(seconds: Float, seed: Int, out: Pass): Boolean {
        if (!pass(seconds, seed, 40, BIRD_PERIOD, BIRD_SECONDS, .6f, out)) return false
        out.x = across(out.t, out.dir)
        out.y = .42f + .28f * hash(seed, out.pass, 43) - .1f * sin(out.t * PI.toFloat())
        return true
    }

    /**
     * Bird [i] of a flock flying [dir]: how far behind the leader (in dp, across and down) it flies
     * in the V. The leader is bird 0; the others take turns on either arm.
     */
    fun birdX(i: Int, dir: Float): Float = -dir * ((i + 1) / 2) * 13f

    fun birdY(i: Int): Float = ((i + 1) / 2) * 7f * if (i % 2 == 0) 1f else -.15f

    /** How far bird [i]'s wings are raised at [seconds], -1 down to 1 up: each its own beat. */
    fun wing(i: Int, seconds: Float): Float = sin(seconds * 8.5f + i * 1.7f)

    /** A blimp drifting across on a bright day, rarely. */
    fun blimp(seconds: Float, seed: Int, out: Pass): Boolean {
        if (!pass(seconds, seed, 50, BLIMP_PERIOD, BLIMP_SECONDS, .4f, out)) return false
        out.x = across(out.t, out.dir)
        out.y = .3f + .2f * hash(seed, out.pass, 53)
        return true
    }

    /** A car's lights along the far road, going one way or the other. */
    fun car(seconds: Float, seed: Int, out: Pass): Boolean {
        if (!pass(seconds, seed, 60, CAR_PERIOD, CAR_SECONDS, .8f, out)) return false
        out.x = across(out.t, out.dir)
        out.y = 1f
        return true
    }

    /**
     * Where cloud [i] (of a few) is across the view at [seconds]: they drift right at their own
     * slow speeds and come round again. At 0 seconds they stand where they always stand.
     */
    fun cloudX(i: Int, seconds: Float, seed: Int): Float {
        val start = .12f + i * .41f + hash(seed, i, 70) * .1f
        val speed = .0035f + .004f * hash(seed, i, 71)
        val span = 1.5f
        return ((start + seconds * speed) % span + span) % span - .25f
    }

    /**
     * The share of the sky's band each cloud lane drifts at, high to low: the lowest stays well
     * above the far towers.
     */
    val CLOUD_LANES = floatArrayOf(.06f, .19f, .32f, .45f, .58f)

    /**
     * The order the lanes are filled in when there are fewer clouds (night): spread, never bunched.
     */
    private val LANE_ORDER = intArrayOf(1, 3, 0, 4, 2)

    private val CLOUD_KINDS =
        arrayOf(
            CloudKind.CUMULUS,
            CloudKind.STRATUS,
            CloudKind.CUMULUS,
            CloudKind.PUFF,
            CloudKind.PUFF,
        )

    /** The most clouds a sky holds. */
    const val CLOUDS = 5

    /**
     * The sky's [CLOUDS] clouds, the same for the same [seed]: cumulus and stratus first, the small
     * puffs last, so a sky with fewer (night) keeps the first ones. Each has a lane of its own.
     */
    fun clouds(seed: Int): List<Cloud> =
        List(CLOUDS) { i ->
            val kind = CLOUD_KINDS[i]
            val lane = LANE_ORDER[i]
            val size =
                when (kind) {
                    CloudKind.CUMULUS -> .8f + .45f * hash(seed, i, 72)
                    CloudKind.STRATUS -> .9f + .4f * hash(seed, i, 72)
                    CloudKind.PUFF -> .6f + .3f * hash(seed, i, 72)
                }
            val big = ((size - .6f) / .7f).coerceIn(0f, 1f)
            val high = 1f - CLOUD_LANES[lane]
            val depth = (.6f * big + .4f * high).coerceIn(0f, 1f)
            Cloud(
                kind,
                lane,
                .08f + i * .37f + hash(seed, i, 70) * .12f,
                CLOUD_LANES[lane],
                size,
                depth,
                .0025f + .0065f * depth,
                cloudLobes(kind, seed, i),
                when (kind) {
                    CloudKind.CUMULUS -> 50f
                    CloudKind.STRATUS -> 82f
                    CloudKind.PUFF -> 30f
                },
                when (kind) {
                    CloudKind.CUMULUS -> 46f
                    CloudKind.STRATUS -> 14f
                    CloudKind.PUFF -> 24f
                },
            )
        }

    private fun cloudLobes(kind: CloudKind, seed: Int, i: Int): FloatArray {
        fun j(k: Int) = hash(seed, i * 31 + k, 74) - .5f
        val out = ArrayList<Float>()
        fun lobe(x: Float, y: Float, r: Float, flat: Float) {
            out += x
            out += y
            out += r
            out += r * flat
        }
        when (kind) {
            CloudKind.CUMULUS -> {
                // a flat-bottomed row of lobes, a heap of them on it, one crown
                for (k in 0 until 4) lobe(
                    -30f + k * 20f + j(k) * 4f,
                    -7f + j(k + 4) * 2f,
                    13f + j(k + 8) * 4f,
                    .8f,
                )
                for (k in 0 until 3) lobe(
                    -18f + k * 17f + j(k + 12) * 4f,
                    -17f + j(k + 16) * 5f,
                    14f + j(k + 20) * 4f,
                    .9f,
                )
                lobe(-3f + j(24) * 10f, -27f + j(25) * 3f, 14f + j(26) * 3f, .92f)
            }
            CloudKind.STRATUS -> {
                for (k in 0 until 6) lobe(
                    -52f + k * 20f + j(k) * 5f,
                    -4f + j(k + 6) * 3f,
                    20f + j(k + 12) * 6f,
                    .22f,
                )
                lobe(-12f + j(20) * 20f, -8f, 24f, .2f)
            }
            CloudKind.PUFF -> {
                for (k in 0 until 3) lobe(
                    -14f + k * 14f + j(k) * 3f,
                    -6f + j(k + 3) * 3f,
                    8f + j(k + 6) * 3f,
                    .85f,
                )
                lobe(-2f + j(9) * 6f, -13f, 9f + j(10) * 2f, .9f)
            }
        }
        return out.toFloatArray()
    }

    /**
     * Where [cloud] is across the view at [seconds] (a share of the width, a little off either
     * edge).
     */
    fun cloudAt(cloud: Cloud, seconds: Float): Float {
        val span = 1.5f
        return ((cloud.x0 + seconds * cloud.speed) % span + span) % span - .25f
    }

    /** A tree's lean at [seconds], in degrees: a slow sway with a gust in it. */
    fun sway(seed: Int, seconds: Float): Float =
        1.4f * sin(seconds * .9f + seed) + .5f * sin(seconds * 2.3f + seed * 1.7f)

    /** A flag's ripple at [seconds], -1 to 1. */
    fun flag(seconds: Float): Float = sin(seconds * 4.2f)

    /**
     * The step the people in the windows are on at [seconds]: they move [PEOPLE_FPS] times a
     * second.
     */
    fun step(seconds: Float): Int = floor(seconds * PEOPLE_FPS).toInt()

    /**
     * A failing light at [seconds]: for a floor missed once ([sputter] false) a flicker that is on
     * most of the time and drops out for a blink; missed twice, a sputter, off as often as on.
     */
    fun flicker(seed: Int, seconds: Float, sputter: Boolean): Boolean {
        val k = floor(seconds * (if (sputter) 9f else 14f)).toInt()
        // a run of good moments, then a stumble
        val burst = hash(seed, k / 10, 80) < (if (sputter) .7f else .35f)
        return !burst || hash(seed, k, 81) < (if (sputter) .42f else .72f)
    }
}
