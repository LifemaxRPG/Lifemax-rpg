package com.lifemax.rpg.feature.habits.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * The crew: five friends (GU1–GU5, [StreetCameos]) who now and then walk the whole street together as a loose group,
 * messing about as they go. Like the rest of the street's life it is a function of the seconds the tab has been
 * showing and a seed, so a moment is the same every time it is asked for, and nothing is made per frame: the
 * painter keeps one [Crew] and this refills it.
 *
 * Each friend has a place in the group (how far ahead of its middle, how far out on the pavement), drifting a little
 * on their own. Now and then the group does something, one short scripted beat at a time ([Beat]): one shoves
 * another, who stumbles a step sideways, catches himself and shoves back or laughs it off; the one in front turns
 * and walks backwards telling a story with his arms going while the others laugh; a shoulder bump; the one at the
 * back drops behind over his phone and jogs to catch up. Between beats they chat and glance at each other. A beat
 * moves people by easing their place in the group, and each one's steps follow the ground they cover (backwards
 * while they face the others), so the feet never slide and the walk cycle carries straight through. With motion
 * reduced they just walk.
 */

/** One of the crew this moment, written by [StreetCrew.at]. */
class CrewMember {
    /** Their look's index ([StreetLooks.of]): [StreetLooks.CREW_AT] and on. */
    var look = 0

    /** Where their feet are along the street, and how far out on the pavement. */
    var x = 0f
    var lane = 0f

    /** Which way they face on the screen, -1 (left) to 1 (right): through 0 as they turn round. */
    var face = 1f

    /** The rig's scale of their stride ([StreetLife.pace]). */
    var size = 1f

    /**
     * The walk's frame (an even one: the crew's walks are baked at every other), the one beside it
     * and how much of that one shows.
     */
    var frame = 0
    var next = 0
    var mix = 0f

    /** How far they lean, degrees towards where they face (pitched by a shove, into a jog). */
    var lean = 0f

    /** What their hands and head are doing. */
    val act = Gesture()

    /** Their offset along the walk from the group's middle, and across the pavement. */
    internal var along = 0f
    internal var across = 0f
}

/** The crew on the street this moment, [StreetCrew.at] fills it. */
class Crew {
    var on = false

    /** Which of the crew's slots this walk is ([StreetCrew.start]). */
    var slot = 0

    /** 1 walking towards the street's far end (right), -1 towards its start. */
    var dir = 1f

    val members = Array(StreetCrew.SIZE) { CrewMember() }

    /** The members back to front (the wall side first): the order to draw them in. */
    val order = IntArray(StreetCrew.SIZE) { it }

    /** Where the group begins and ends along the street, a little room included. */
    var lo = 0f
    var hi = 0f

    /** The nearest the kerb any of them may walk, this moment ([StreetCrew.lanes]). */
    var top = 0f

    /** How far they have closed up across the pavement for someone passing, 0 to 1. */
    var squeeze = 0f

    // ---- the walk's plan: worked out once per walk, not every frame
    internal var planK = Int.MIN_VALUE
    internal var planSeed = 0
    internal var planLength = 0f
    internal var speed = 0f
    internal var duration = 0f

    /** Each member's place in the group ([StreetCrew.SEAT_ALONG]), and where their steps start. */
    internal val seat = IntArray(StreetCrew.SIZE)
    internal val from = FloatArray(StreetCrew.SIZE)

    internal var beats = 0
    internal val kind = IntArray(StreetCrew.MOST_BEATS)
    internal val at = FloatArray(StreetCrew.MOST_BEATS)
    internal val dur = FloatArray(StreetCrew.MOST_BEATS)
    internal val a = IntArray(StreetCrew.MOST_BEATS)
    internal val b = IntArray(StreetCrew.MOST_BEATS)
    internal val vary = FloatArray(StreetCrew.MOST_BEATS)

    /** For a story: the teller's ground at the turn round, and how much he covers backwards. */
    internal val turnPos = FloatArray(StreetCrew.MOST_BEATS)
    internal val backward = FloatArray(StreetCrew.MOST_BEATS)

    // scratch for [StreetCrew.place]
    internal var oAlong = 0f
    internal var oAcross = 0f
    internal var oFace = 1f
    internal var oLean = 0f
}

object StreetCrew {
    /** How many they are. */
    const val SIZE = 5

    /**
     * The crew may set out about every this many seconds (within [JITTER] of the period's start),
     * this share of the times; never in the first period, so not in the first few minutes the tab
     * shows. A long street lengthens the period so that one walk is over before the next.
     */
    const val PERIOD = 150f
    const val JITTER = 40f
    const val CHANCE = .6f

    /** Their pictures are made this many seconds before they come ([due]). */
    const val LEAD = 40f

    /** Their pace, of the slowest one's own: friends strolling together. */
    const val TEMPO = .86f

    /**
     * How far out on the pavement the group's wall side walks, and how much further out it spreads.
     */
    const val LANE = 7.3f
    const val SPREAD = 4f

    /** How much of their spread they give up when someone passes. */
    const val SQUEEZE = .4f

    /** The room someone passing keeps beyond the group's kerb side. */
    const val ROOM = 2.2f

    /** The nearest the wall anyone walks ([StreetLife]'s). */
    private const val LANE_MIN = 7f

    /** How far behind the street's start the group's middle sets out, and past its end it goes. */
    private const val FRONT = 14f
    private const val REAR = 30f

    /** The most beats planned on one walk. */
    const val MOST_BEATS = 24

    /** The beats ([Crew.kind]). */
    const val SHOVE = 0
    const val STORY = 1
    const val BUMP = 2
    const val LAG = 3
    private const val KINDS = 4

    /**
     * The places in the group, front to back: how far ahead of its middle and how far out from its
     * wall side. Neighbours along the walk are a body or so apart and at different depths, so the
     * group reads as five people, not a queue.
     */
    val SEAT_ALONG = floatArrayOf(12.5f, 4f, -4.5f, -13f, -21.5f)
    val SEAT_ACROSS = floatArrayOf(.4f, 2.6f, .9f, 2.8f, 1.4f)

    /** How close a shover comes up behind the one he shoves, and a bumper beside the bumped. */
    private const val SHOVE_GAP = 7.4f
    private const val BUMP_GAP = 2.6f

    /**
     * How far ahead of the next one a storyteller walks backwards, and how far the laggard drops
     * back.
     */
    private const val STORY_AHEAD = 15f
    private const val LAG_BACK = 11f

    private fun hash(seed: Int, k: Int, salt: Int) = Ambient.hash(seed * 7 + 20, k, salt)

    /** Whether the crew walks in slot [k]. */
    fun happens(seed: Int, k: Int): Boolean = k >= 1 && hash(seed, k, 300) < CHANCE

    /** How fast the group walks, street units a second. */
    fun speed(): Float {
        var v = Float.MAX_VALUE
        for (i in 0 until SIZE) v = min(v, StreetLife.speed(StreetLooks.CREW_AT + i))
        return v * TEMPO
    }

    /** How long a walk of the street from [from] to [to] takes them, seconds. */
    fun duration(from: Float, to: Float): Float =
        (StreetLife.length(from, to) + FRONT + REAR) / speed()

    /** The crew's period on a street from [from] to [to]: long enough for one walk at a time. */
    fun period(from: Float, to: Float): Float = max(PERIOD, duration(from, to) + JITTER + 10f)

    /** When the crew of slot [k] sets out, seconds. */
    fun start(seed: Int, k: Int, period: Float): Float = k * period + hash(seed, k, 301) * JITTER

    /** Which way slot [k] walks. */
    fun dir(seed: Int, k: Int): Float = if (hash(seed, k, 302) < .5f) -1f else 1f

    /**
     * How many seconds until the crew next sets out at [seconds] (0 while they walk), or more than
     * [LEAD] when they are not coming soon: their pictures are wanted only then.
     */
    fun due(seconds: Float, seed: Int, from: Float, to: Float): Float {
        if (seconds <= 0f) return Float.MAX_VALUE
        val p = period(from, to)
        val d = duration(from, to)
        val k0 = floor(seconds / p).toInt()
        var best = Float.MAX_VALUE
        for (k in k0 - 1..k0 + 1) {
            if (!happens(seed, k)) continue
            val s = start(seed, k, p)
            if (seconds >= s && seconds <= s + d) return 0f
            if (s > seconds) best = min(best, s - seconds)
        }
        return best
    }

    /**
     * Fills [out] with the crew at [seconds] on a street from [from] to [to]: where each one is,
     * their frames, which way they face and what they do ([goof] false: they just walk). Their
     * lanes are set apart by [lanes], once the others on the pavement are known. False (and [out]
     * off) when they are not on the street.
     */
    fun at(
        seconds: Float,
        seed: Int,
        from: Float,
        to: Float,
        out: Crew,
        goof: Boolean = true,
    ): Boolean {
        out.on = false
        if (seconds <= 0f) return false
        val p = period(from, to)
        val k = floor(seconds / p).toInt()
        var slot = Int.MIN_VALUE
        // a walk is over before the next period's start, but it may run on into the next period
        for (j in k downTo k - 1) {
            if (!happens(seed, j)) continue
            val t = seconds - start(seed, j, p)
            if (t >= 0f && t <= duration(from, to)) {
                slot = j
                break
            }
        }
        if (slot == Int.MIN_VALUE) return false
        val t = seconds - start(seed, slot, p)
        plan(seed, slot, from, to, out)
        val dir = dir(seed, slot)
        val ground = out.speed * t - FRONT
        out.dir = dir
        out.slot = slot
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in 0 until SIZE) {
            val m = out.members[i]
            m.look = StreetLooks.CREW_AT + i
            m.size = StreetLife.pace(m.look)
            place(out, i, t, goof)
            m.along = out.oAlong
            m.across = out.oAcross
            m.face = dir * out.oFace
            m.lean = out.oLean
            val travel = ground + out.oAlong
            m.x =
                if (dir > 0f) from - StreetLife.MARGIN + travel else to + StreetLife.MARGIN - travel
            // the ground their steps cover, in the way they face: backwards while a storyteller
            // faces
            // the others, so his walk runs backwards too
            val walked = walked(out, i, t, travel, goof)
            val phase = StreetGait.phase(walked, m.size, out.from[i])
            m.frame = Gait.nearest(phase, FRAMES) * STEP
            m.next = Gait.neighbour(phase, FRAMES) * STEP
            m.mix = abs(Gait.offset(phase, FRAMES))
            acts(out, i, t, goof, m.act)
            lo = min(lo, m.x)
            hi = max(hi, m.x)
        }
        out.lo = lo - 4f
        out.hi = hi + 4f
        out.on = true
        lanes(out, 0f)
        return true
    }

    /** The crew's walks are baked at every other frame of the walk: this many, and the step. */
    const val FRAMES = StreetGait.FRAMES / 2
    const val STEP = StreetGait.FRAMES / FRAMES

    /**
     * Sets [c]'s lanes for someone passing as near as [squeeze] (0 nobody, 1 alongside): they close
     * up towards the wall; also the kerb side they keep to ([Crew.top]) and the order to draw them
     * in.
     */
    fun lanes(c: Crew, squeeze: Float) {
        c.squeeze = squeeze.coerceIn(0f, 1f)
        val k = 1f - SQUEEZE * c.squeeze
        c.top = LANE + SPREAD * k
        for (i in 0 until SIZE) {
            val m = c.members[i]
            m.lane = (LANE + m.across * k).coerceIn(LANE_MIN, c.top)
        }
        // back to front: insertion sort, no allocation
        for (i in 0 until SIZE) c.order[i] = i
        for (i in 1 until SIZE) {
            val v = c.order[i]
            var j = i - 1
            while (j >= 0 && c.members[c.order[j]].lane > c.members[v].lane) {
                c.order[j + 1] = c.order[j]
                j--
            }
            c.order[j + 1] = v
        }
    }

    /**
     * The lane someone passing alongside the crew keeps to at least: beyond the group's kerb side
     * closed up as far as it goes, with [ROOM] between.
     */
    fun passLane(): Float = LANE + SPREAD * (1f - SQUEEZE) + ROOM

    // ---------------------------------------------------------------- the plan

    private fun plan(seed: Int, k: Int, from: Float, to: Float, c: Crew) {
        val length = StreetLife.length(from, to)
        if (c.planK == k && c.planSeed == seed && c.planLength == length) return
        c.planK = k
        c.planSeed = seed
        c.planLength = length
        c.speed = speed()
        c.duration = duration(from, to)
        // who walks where in the group: a shuffle of the places
        for (i in 0 until SIZE) c.seat[i] = i
        for (i in SIZE - 1 downTo 1) {
            val j = (hash(seed, k, 310 + i) * (i + 1)).toInt().coerceIn(0, i)
            val s = c.seat[i]
            c.seat[i] = c.seat[j]
            c.seat[j] = s
        }
        for (i in 0 until SIZE) c.from[i] = Gait.PASSING + hash(seed, k, 320 + i)
        // the beats, one after another with a breather between, none twice running
        c.beats = 0
        var t = 4f + 4f * hash(seed, k, 330)
        var last = -1
        var n = 0
        while (c.beats < MOST_BEATS) {
            val h = hash(seed, k, 340 + n * 8)
            var kind = (h * KINDS).toInt().coerceIn(0, KINDS - 1)
            if (kind == last)
                kind = (kind + 1 + (hash(seed, k, 341 + n * 8) * (KINDS - 1)).toInt()) % KINDS
            val v = hash(seed, k, 342 + n * 8)
            val r = hash(seed, k, 343 + n * 8)
            val d =
                when (kind) {
                    SHOVE -> if (v < .5f) 4.8f else 5.9f
                    STORY -> 8.6f + 1.6f * r
                    BUMP -> 3.9f
                    else -> 8.2f
                }
            // the whole beat on the street, the group's middle well inside it
            if (t + d > c.duration - 8f) break
            val j = c.beats
            c.kind[j] = kind
            c.at[j] = t
            c.dur[j] = d
            c.vary[j] = v
            when (kind) {
                SHOVE -> {
                    // someone shoves the one just ahead of him
                    val s = 1 + (r * (SIZE - 1)).toInt().coerceIn(0, SIZE - 2)
                    c.a[j] = who(c, s)
                    c.b[j] = who(c, s - 1)
                }
                STORY -> {
                    // the one in front tells it
                    c.a[j] = who(c, 0)
                    c.b[j] = -1
                }
                BUMP -> {
                    // the one behind bumps the one just ahead
                    val s = (r * (SIZE - 1)).toInt().coerceIn(0, SIZE - 2)
                    c.a[j] = who(c, s + 1)
                    c.b[j] = who(c, s)
                }
                else -> {
                    // the one at the back drops behind; the one ahead of him waves him on
                    c.a[j] = who(c, SIZE - 1)
                    c.b[j] = who(c, SIZE - 2)
                }
            }
            c.beats = j + 1
            if (kind == STORY) {
                val s = c.a[j]
                val t1 = t + TURN_OUT
                val t2 = t + d - TURN_BACK
                place(c, s, t1, true)
                val p1 = c.speed * t1 + c.oAlong
                place(c, s, t2, true)
                val p2 = c.speed * t2 + c.oAlong
                c.turnPos[j] = p1
                c.backward[j] = p2 - p1
            }
            last = kind
            t += d + 2.5f + 4.5f * hash(seed, k, 344 + n * 8)
            n++
        }
    }

    /** The member in place [s] of the group. */
    private fun who(c: Crew, s: Int): Int {
        for (i in 0 until SIZE) if (c.seat[i] == s) return i
        return 0
    }

    /**
     * When a storyteller is half way round to face the others, and half way back, into his beat.
     */
    private const val TURN_OUT = 1.6f
    private const val TURN_BACK = 2f

    // ---------------------------------------------------------------- where each one is

    private fun smooth(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }

    private fun rise(u: Float, a: Float, d: Float): Float = smooth((u - a) / d)

    /** Up from [a] over [up], down to nothing at [b] over [down]. */
    private fun pulse(u: Float, a: Float, b: Float, up: Float, down: Float): Float =
        rise(u, a, up) * (1f - rise(u, b - down, down))

    /**
     * Member [i] of [c] at [t] seconds into the walk, into [c]'s scratch: their offset along the
     * walk and across, which way they face (1 the way they go, -1 back) and how far they lean.
     */
    internal fun place(c: Crew, i: Int, t: Float, goof: Boolean) {
        val s = c.seat[i]
        var along = SEAT_ALONG[s]
        var across = SEAT_ACROSS[s]
        var face = 1f
        var lean = 0f
        // drifting a little in their place, on their own: two slow waves each
        val h = Ambient.hash(c.planSeed * 13 + i, c.planK, 360)
        val wa =
            1.1f * sin(2f * PI.toFloat() * t / (6.5f + 3f * h) + 6f * h) +
                .5f * sin(2f * PI.toFloat() * t / (2.9f + h) + 2f * h)
        val wc = .22f * sin(2f * PI.toFloat() * t / (5.3f + 2f * h) + 4f * h)
        var calm = 0f
        if (goof) {
            val j = beatAt(c, t)
            if (j >= 0) {
                val u = t - c.at[j]
                val d = c.dur[j]
                val a = c.a[j]
                val b = c.b[j]
                val sa = SEAT_ALONG[c.seat[a]]
                val ca = SEAT_ACROSS[c.seat[a]]
                val sb = if (b >= 0) SEAT_ALONG[c.seat[b]] else 0f
                val cb = if (b >= 0) SEAT_ACROSS[c.seat[b]] else 0f
                // everything eases home over a beat's last second and a half, and nobody drifts
                // much while it goes on (those in it not at all)
                val home = 1f - rise(u, d - 1.4f, 1.4f)
                val back = c.vary[j] >= .5f
                calm = .7f * pulse(u, 0f, d, .8f, 1.2f)
                when (c.kind[j]) {
                    SHOVE -> {
                        if (i == a) {
                            calm = max(calm, pulse(u, 0f, d, .6f, 1.2f))
                            val up = rise(u, 0f, 1f) * home
                            along += (sb - SHOVE_GAP - sa) * up
                            across += (cb - .9f - ca) * up
                            lean += 5f * pulse(u, .8f, 1.6f, .2f, .4f)
                            if (back) {
                                // shoved back: a stagger back and out, leaning back
                                along -= 1.9f * rise(u, 3.3f, .3f) * home
                                across -= .5f * pulse(u, 3.3f, 4.6f, .25f, .8f)
                                lean -= 6f * pulse(u, 3.3f, 4.2f, .15f, .5f)
                            }
                        } else if (i == b) {
                            calm = max(calm, pulse(u, 0f, d, .6f, 1.2f))
                            // pitched forward and a step out sideways (away from the one ahead of
                            // him), caught
                            val lurch = 2f * rise(u, 1.05f, .35f)
                            along +=
                                if (back)
                                    (lurch * (1f - rise(u, 2.3f, .8f)) - .4f * rise(u, 2.3f, .8f)) *
                                        home
                                else lurch * home
                            val ahead = c.seat[b] - 1
                            val side = if (ahead >= 0 && SEAT_ACROSS[ahead] > cb) -1f else 1f
                            across += side * 1.3f * pulse(u, 1.05f, 2.7f, .3f, .9f)
                            lean += 9f * pulse(u, 1.05f, 1.9f, .18f, .5f)
                        }
                    }
                    STORY -> {
                        if (i == a) {
                            // up ahead of the next one, round to face them, backwards, round again
                            var front = -Float.MAX_VALUE
                            for (o in 0 until SIZE) if (o != a)
                                front = max(front, SEAT_ALONG[c.seat[o]])
                            val go = pulse(u, 0f, d, 1.7f, 1.9f)
                            calm = max(calm, go)
                            along += (front + STORY_AHEAD - sa) * go
                            across += (1.6f - ca) * go
                            val turn =
                                rise(u, TURN_OUT - .25f, .5f) - rise(u, d - TURN_BACK - .25f, .5f)
                            face = cos(PI.toFloat() * turn)
                        }
                    }
                    BUMP -> {
                        val side = if (cb >= ca) 1f else -1f
                        if (i == a) {
                            calm = max(calm, pulse(u, 0f, d, .5f, 1.2f))
                            val up = rise(u, 0f, 1.05f) * home
                            along += (sb - BUMP_GAP - sa) * up
                            across +=
                                (cb - side * 1.05f - ca) * up +
                                    side * .5f * pulse(u, .9f, 1.45f, .25f, .35f)
                        } else if (i == b) {
                            calm = max(calm, pulse(u, 0f, d, .5f, 1.2f))
                            across += side * 1.25f * pulse(u, 1.05f, 2.5f, .3f, .9f)
                            along += .7f * pulse(u, 1.05f, 2.6f, .25f, .9f)
                            lean += 4f * pulse(u, 1.05f, 1.8f, .15f, .5f)
                        }
                    }
                    LAG -> {
                        if (i == a) {
                            // drops back over his phone, then jogs to catch up
                            along -= LAG_BACK * rise(u, .2f, 3.2f) * (1f - rise(u, 3.7f, 2.3f))
                            lean += 7f * pulse(u, 3.6f, 6.2f, .4f, .6f)
                        }
                    }
                }
            }
        }
        c.oAlong = along + wa * (1f - calm)
        c.oAcross = across + wc * (1f - calm)
        c.oFace = face
        c.oLean = lean
    }

    /** The beat going on at [t], or -1. */
    private fun beatAt(c: Crew, t: Float): Int {
        for (j in 0 until c.beats) {
            if (t < c.at[j]) return -1
            if (t < c.at[j] + c.dur[j]) return j
        }
        return -1
    }

    /**
     * How far member [i] has walked the way they face at [t], [travel] being the ground they have
     * covered: less twice what a storyteller covered backwards.
     */
    private fun walked(c: Crew, i: Int, t: Float, travel: Float, goof: Boolean): Float {
        if (!goof) return travel
        var s = travel
        for (j in 0 until c.beats) {
            if (c.kind[j] != STORY || c.a[j] != i) continue
            val t1 = c.at[j] + TURN_OUT
            if (t <= t1) break
            val pos = travel + FRONT
            s -= 2f * if (t >= c.at[j] + c.dur[j] - TURN_BACK) c.backward[j] else pos - c.turnPos[j]
        }
        return s
    }

    // ---------------------------------------------------------------- what they do

    /** Writes into [g] what member [i] of [c] is doing [t] seconds into the walk. */
    private fun acts(c: Crew, i: Int, t: Float, goof: Boolean, g: Gesture) {
        g.clear()
        if (!goof) return
        val j = beatAt(c, t)
        if (j >= 0 && beatAct(c, j, i, t - c.at[j], g)) return
        chatter(c, i, t, g)
    }

    /** Sets [g] to [act] at [w], [x] seconds into it. */
    private fun set(g: Gesture, act: Act, w: Float, x: Float, vary: Float): Boolean {
        if (w <= .001f) return false
        g.act = act
        g.w = w
        g.t = x.coerceAtLeast(0f)
        g.dur = 3f
        g.vary = vary
        return true
    }

    /** What member [i] does [u] seconds into beat [j], if anything. */
    private fun beatAct(c: Crew, j: Int, i: Int, u: Float, g: Gesture): Boolean {
        val d = c.dur[j]
        val a = c.a[j]
        val b = c.b[j]
        val back = c.vary[j] >= .5f
        // the others' laugh: a little after it starts, each in their own time
        val others = (i * 37 + j * 11) % 7 / 7f
        when (c.kind[j]) {
            SHOVE ->
                return if (i == a) {
                    set(g, Act.SHOVE, pulse(u, .8f, 1.6f, .25f, .4f), u - .8f, .5f) ||
                        if (back)
                            set(g, Act.LAUGH, pulse(u, 1.75f, 3.2f, .3f, .5f), u - 1.75f, .7f) ||
                                set(g, Act.CATCH, pulse(u, 3.3f, 4.3f, .3f, .5f), u - 3.3f, .4f) ||
                                set(g, Act.LAUGH, pulse(u, 4.4f, d - .2f, .3f, .7f), u - 4.4f, .8f)
                        else set(g, Act.LAUGH, pulse(u, 1.75f, d - .2f, .3f, .7f), u - 1.75f, .7f)
                } else if (i == b) {
                    set(g, Act.CATCH, pulse(u, 1.05f, 2.3f, .3f, .5f), u - 1.1f, .6f) ||
                        if (back)
                            set(g, Act.BACKHAND, pulse(u, 2.8f, 3.7f, .45f, .35f), u - 2.8f, .5f) ||
                                set(
                                    g,
                                    Act.LAUGH,
                                    pulse(u, 3.85f, d - .2f, .3f, .7f),
                                    u - 3.85f,
                                    .9f,
                                )
                        else set(g, Act.LAUGH, pulse(u, 2.45f, d - .2f, .35f, .8f), u - 2.45f, .9f)
                } else
                    set(
                        g,
                        Act.LAUGH,
                        .75f * pulse(u, 1.5f + .6f * others, d - .3f, .4f, .8f),
                        u - 1.5f,
                        others,
                    )
            STORY ->
                return if (i == a)
                    set(g, Act.STORY, pulse(u, 1.7f, d - 2.3f, .5f, .5f), u - 1.7f, c.vary[j]) ||
                        set(g, Act.LAUGH, pulse(u, d - 2.1f, d, .3f, .6f), u - d + 2.1f, .6f)
                else
                    set(
                        g,
                        Act.LAUGH,
                        pulse(u, 2.4f + .9f * others, d - .6f, .5f, .8f),
                        u - 2.4f,
                        others,
                    )
            BUMP ->
                return if (i == a)
                    set(g, Act.LAUGH, pulse(u, 1.35f, d - .2f, .3f, .7f), u - 1.35f, .75f)
                else if (i == b) {
                    // turned to the bumper: away from us when he is on the wall side
                    val toward = if (SEAT_ACROSS[c.seat[a]] < SEAT_ACROSS[c.seat[b]]) .1f else .9f
                    set(g, Act.CATCH, .55f * pulse(u, 1.1f, 2f, .3f, .45f), u - 1.1f, .3f) ||
                        set(g, Act.LAUGH, pulse(u, 2.1f, d - .2f, .3f, .7f), u - 2.1f, toward)
                } else false
            else ->
                return if (i == a)
                    set(g, Act.PHONE, pulse(u, .3f, 3.3f, .5f, .45f), u - .3f, .5f) ||
                        set(g, Act.JOG, pulse(u, 3.55f, 6.2f, .3f, .6f), u - 3.55f, .5f) ||
                        set(g, Act.LAUGH, pulse(u, 6.35f, d - .1f, .3f, .6f), u - 6.35f, .6f)
                else if (i == b) set(g, Act.WAVE, pulse(u, 3.8f, 5.4f, .45f, .5f), u - 3.8f, .4f)
                else false
        }
    }

    /**
     * Between beats: a word to the one beside them, a glance round, a hand to the hair, a scratch,
     * now and then, each in their own time; none from just before a beat to just after it.
     */
    private fun chatter(c: Crew, i: Int, t: Float, g: Gesture) {
        val base = c.planSeed * 131 + 977 + i * 17
        var start = 1.5f + 4f * Ambient.hash(base, c.planK, 370)
        var j = 0
        var n = 0
        while (n < 64 && start <= t) {
            val h = Ambient.hash(base, c.planK, 371 + n * 3)
            val act =
                when {
                    h < .5f -> Act.TALK
                    h < .8f -> Act.GLANCE
                    h < .9f -> Act.HAIR
                    else -> Act.SCRATCH
                }
            val r = Ambient.hash(base, c.planK, 372 + n * 3)
            val dur = if (act == Act.TALK) 2f + 1.6f * r else StreetActs.duration(act, r)
            // clear of the beats
            while (j < c.beats && c.at[j] + c.dur[j] + .4f < start) j++
            if (j < c.beats && start + dur > c.at[j] - 1f) {
                start = c.at[j] + c.dur[j] + .5f
                n++
                continue
            }
            if (t < start + dur) {
                val x = t - start
                g.act = act
                g.t = x
                g.dur = dur
                g.vary = Ambient.hash(base, c.planK, 373 + n * 3)
                g.w = StreetActs.envelope(x, dur, StreetActs.blend(act))
                return
            }
            start += dur + 2.5f + 5f * Ambient.hash(base, c.planK, 374 + n * 3)
            n++
        }
    }
}
