package com.lifemax.rpg.feature.habits.domain

import kotlin.math.floor

/*
 * The street's wind, with no drawing in it: one model every swaying thing reads (the trees, the awnings, the flags,
 * the falling leaves and the rain, the steam), so they all move in the same air. A function of the time and of the
 * place along the street only, so a frame at a given time is the same frame every time (the screenshots), and
 * nothing is kept from one frame to the next.
 */

/**
 * The air over the street at one moment ([update]), read at places along it.
 *
 * Its push ([force], 0 calm to [MAX]) is a base that wanders slowly, gusts that build over a second
 * or two, hold and fade over a few more, and a little turbulence inside them. The whole field
 * travels down the street at [SPEED] (the wind blows towards a larger x), so a gust reaches what
 * stands further downwind a moment later. How windy the day is follows the weather ([level]):
 * breezy under a grey sky, blustery in the rain, near still in a mist.
 *
 * What hangs in it responds as a spring ([swing]): it bends over as a gust comes, overshoots a
 * little, rocks back as the gust lets go, settles, never a regular beat; its parts can trail it
 * ([lagged]) and flutter on their own ([flutter]). With motion off ([update] at 0 seconds) every
 * reading is 0: everything hangs still.
 *
 * Nothing is made per reading: one instance, with the one buffer [swing] fills, is kept by the
 * stage and read on the main thread.
 */
class Wind {
    /** The time the frame is drawn at, seconds; 0 or less with motion off. */
    var seconds = 0f
        private set

    /** How windy the weather is, 0 (dead calm) to 1 (a storm's rain). */
    var level = 0f
        private set

    /** Whether anything moves: false with motion off. */
    val moving: Boolean
        get() = seconds > 0f

    /** Sets the moment and the weather the frame is read at. */
    fun update(seconds: Float, weather: WeatherState) {
        this.seconds = seconds
        level = levelOf(weather)
    }

    /** The wind's push at [x] along the street now, 0 to [MAX] (0 with motion off). */
    fun force(x: Float): Float = if (!moving) 0f else push(local(x))

    /** The time the air at [x] was where the air at the street's origin is now (gusts travel). */
    fun local(x: Float): Float = seconds - x / SPEED

    /**
     * The push at local time [t] ([local]): the wandering base, the strongest gust then, and the
     * turbulence riding on both.
     */
    fun push(t: Float): Float {
        if (level <= 0f) return 0f
        val wander = .62f * smooth1(t / 29f, 11) + .38f * smooth1(t / 83f, 13)
        val base = level * (BASE_LOW + BASE_SPAN * wander)
        val gust = gust(t)
        val air = base + gust
        // eddies inside the moving air: a few tenths of a second to a couple of seconds across
        val eddy = (smooth1(t * 1.7f, 17) - .5f) * .55f + (smooth1(t * 4.1f, 19) - .5f) * .25f
        val f = air * (1f + TURBULENCE * eddy)
        return if (f < 0f) 0f else if (f > MAX) MAX else f
    }

    /**
     * The strongest gust blowing at local time [t] (slots of [SLOT] seconds, each may hold one).
     */
    fun gust(t: Float): Float {
        val k = floor(t / SLOT).toInt()
        var g = 0f
        for (s in k - 1..k) {
            val e = gustIn(s, t)
            if (e > g) g = e
        }
        return g
    }

    /**
     * The gusts of the last few slots read, by slot (a slot's gust is the same whatever the
     * weather, so they are kept): which slot each entry holds, its chance, start, rise, hold, fall
     * and strength. Read over and over by [swing] as it runs through the slots in turn.
     */
    private val slotOf = IntArray(CACHE) { Int.MIN_VALUE }
    private val slotData = FloatArray(CACHE * 6)

    /** The gust of slot [s] at local time [t], 0 where it has none or it has passed. */
    private fun gustIn(s: Int, t: Float): Float {
        val e = Math.floorMod(s, CACHE)
        val d = e * 6
        if (slotOf[e] != s) {
            slotOf[e] = s
            slotData[d] = Ambient.hash(s, 1, 701)
            slotData[d + 1] = s * SLOT + Ambient.hash(s, 3, 701) * SLOT * .6f
            slotData[d + 2] = 1.1f + 1.3f * Ambient.hash(s, 4, 701)
            slotData[d + 3] = .3f + 1.4f * Ambient.hash(s, 5, 701)
            slotData[d + 4] = 1.8f + 2.2f * Ambient.hash(s, 6, 701)
            slotData[d + 5] = GUST_LOW + GUST_SPAN * Ambient.hash(s, 2, 701)
        }
        // a calm day has fewer gusts; a stormy one nearly one a slot
        if (slotData[d] >= .38f + .5f * level) return 0f
        val u = t - slotData[d + 1]
        if (u <= 0f) return 0f
        val rise = slotData[d + 2]
        val hold = slotData[d + 3]
        val fall = slotData[d + 4]
        val env =
            when {
                u < rise -> smoother(u / rise)
                u < rise + hold -> 1f
                u < rise + hold + fall -> 1f - smoother((u - rise - hold) / fall)
                else -> return 0f
            }
        return env * level * slotData[d + 5]
    }

    /** The trail of the last [swing], oldest first: where it was at each step of the window. */
    private val trail = FloatArray(STEPS + 1)
    private var trailDt = WINDOW / STEPS

    /**
     * Where a spring hanging in the wind at [x] stands now: something with a natural sway of
     * [period] seconds and [damping] (0 rings for ever, 1 creeps), pushed by the wind there. It
     * leans as far as the push in a steady wind (the reading is in the push's units), runs past it
     * when a gust comes, swings back when it goes. Run over the last [WINDOW] seconds from rest
     * each time (so a frame never depends on the one before it), the steps keyed to the time, so it
     * moves smoothly from frame to frame. Leaves its trail for [lagged]. 0 with motion off.
     */
    fun swing(x: Float, period: Float, damping: Float): Float {
        if (!moving || level <= 0f) {
            java.util.Arrays.fill(trail, 0f)
            return 0f
        }
        val w = TWO_PI / period
        val k = w * w
        val c = 2f * damping * w
        val dt = WINDOW / STEPS
        trailDt = dt
        val now = local(x)
        var t = now - WINDOW
        var y = push(t)
        var v = 0f
        trail[0] = y
        for (i in 1..STEPS) {
            t += dt
            // semi-implicit Euler: steady for these stiffnesses at this step
            v += dt * (k * (push(t) - y) - c * v)
            y += dt * v
            trail[i] = y
        }
        return y
    }

    /**
     * Where the last [swing] stood [lag] seconds ago (0 to [WINDOW]): a part that follows what it
     * hangs from a moment late (a branch behind its trunk, a cluster behind its branch).
     */
    fun lagged(lag: Float): Float {
        val p = (STEPS - lag / trailDt).coerceIn(0f, STEPS.toFloat())
        val i = p.toInt().coerceAtMost(STEPS - 1)
        val f = p - i
        return trail[i] + (trail[i + 1] - trail[i]) * f
    }

    /**
     * A part's own flutter at [x], -1 to 1 times how hard the wind is there: smooth noise at about
     * [rate] a second, [seed] its own, so no two parts beat together. 0 with motion off.
     */
    fun flutter(x: Float, seed: Int, rate: Float): Float {
        if (!moving) return 0f
        val t = local(x)
        val f = push(t)
        val s = seed and 0xFFFF
        val n =
            (smooth1(t * rate + s * .37f, 23 + s) - .5f) * 1.4f +
                (smooth1(t * rate * 2.3f + s * .71f, 29 + s) - .5f) * .6f
        return (n * f).coerceIn(-MAX, MAX)
    }

    /**
     * How far the air at [x] carried something between [from] and [to] seconds (frame times), in
     * push-seconds: the push's integral there, by trapezoids on a grid fixed in time
     * ([DRIFT_STEP]), so it only ever grows as [to] goes on and moves smoothly from frame to frame.
     * 0 with motion off.
     */
    fun drift(x: Float, from: Float, to: Float): Float {
        if (!moving || to <= from) return 0f
        val off = x / SPEED
        val a = from - off
        val b = to - off
        var t = a
        var p = push(a)
        var sum = 0f
        var node = (floor(a / DRIFT_STEP) + 1f) * DRIFT_STEP
        while (node < b) {
            val q = push(node)
            sum += (node - t) * (p + q) * .5f
            t = node
            p = q
            node += DRIFT_STEP
        }
        return sum + (b - t) * (p + push(b)) * .5f
    }

    companion object {
        /** How fast a gust travels down the street, street units a second (a lot in ~1.7 s). */
        const val SPEED = 110f

        /** The strongest push. */
        const val MAX = 2f

        /** The day's breeze with nothing in the weather to stir it. */
        const val CALM = .25f

        /** Gusts come in slots this long, seconds; at most one a slot. */
        const val SLOT = 6.5f

        /** The springs are run over this many seconds, in this many steps. */
        const val WINDOW = 8f
        const val STEPS = 64

        /** How many slots' gusts are kept. */
        private const val CACHE = 8

        /** The grid [drift] sums the push on, seconds. */
        const val DRIFT_STEP = .25f

        /** The base's range as a share of the level, a gust's strength, the turbulence's share. */
        private const val BASE_LOW = .3f
        private const val BASE_SPAN = .4f
        private const val GUST_LOW = .45f
        private const val GUST_SPAN = .85f
        private const val TURBULENCE = .35f

        private const val TWO_PI = 6.2831855f

        /**
         * How windy [w] is, 0 to 1: a breeze on a clear day, more under a grey sky, most in the
         * rain, near still in a mist.
         */
        fun levelOf(w: WeatherState): Float {
            val l = (CALM + .25f * w.cloudiness + .65f * w.rain) * (1f - .85f * w.mist)
            return if (l < 0f) 0f else if (l > 1f) 1f else l
        }

        /** Smooth value noise along one axis, 0 to 1, a lattice of one. */
        fun smooth1(x: Float, seed: Int): Float {
            val i = floor(x)
            val f = x - i
            val n = i.toInt()
            val a = Ambient.hash(n, seed, 709)
            val b = Ambient.hash(n + 1, seed, 709)
            return a + (b - a) * smoother(f)
        }

        /** Perlin's smootherstep: 0 at 0, 1 at 1, flat at both ends to the second derivative. */
        fun smoother(x: Float): Float {
            val t = if (x < 0f) 0f else if (x > 1f) 1f else x
            return t * t * t * (t * (t * 6f - 15f) + 10f)
        }
    }
}
