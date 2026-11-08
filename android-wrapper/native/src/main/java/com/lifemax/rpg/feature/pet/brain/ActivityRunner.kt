package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World

/**
 * Plays one activity at a time for a cat (plan 1.1 `ActivityRunner`): turns a [Choice] into a
 * [Script] (the planner's route to the place, then the activity's beats), hands it to the
 * [Executor], watches it to its end, and decides what follows: the activity's aftermath
 * ([Activity.next], the waking routine after sleep), a resumed activity a reflex had interrupted,
 * or a fresh pick. It is the only thing that starts a plan on the body. Every pick is written to
 * [explain].
 */
class ActivityRunner(
    private val m: CatMind,
    private val exec: Executor,
    private val registry: ActivityRegistry,
    /** The ways the cat has measured (see [Travel]); a fast copy of the cat reuses them. */
    internal val routes: HashMap<Long, FloatArray> = HashMap(),
) {
    private val selector = Selector(registry)
    private val travel = Travel(exec, routes)
    private val coolUntilMs = LongArray(registry.all.size) { Long.MIN_VALUE }
    private val recent = IntArray(BrainTuning.Select.NOVELTY.size) { -1 }
    private val loaf = requireNotNull(registry.find(Catalogue.LOAF)) { "the registry needs loaf" }

    /** The last picks, with their reasons and the candidates they beat. */
    val explain = ExplainRing()

    /** The activity being played, its place (null: where the cat was) and its planned seconds. */
    var activity: Activity = loaf
        private set

    var place: Spot? = null
        private set

    var seconds = 0.0
        private set

    /** Seconds spent at the place so far (the way there not counted). */
    var atPlaceS = 0.0
        private set

    /** Seconds since the activity began, the way there included. */
    var elapsedS = 0.0
        private set

    /** What the cat is doing now, as the [Brain] interface reports it. */
    var doing = Doing(activity.id, "here", 0.0, "start")
        private set

    /** Times the watchdog had to end a script that overran (never, in a healthy brain). */
    var watchdogs = 0
        private set

    /**
     * The moment together this cat is playing a part of ([SocialDirector]), or 0: set by
     * [playPart], cleared by anything else the cat begins.
     */
    var pairToken = 0
        internal set

    /** True while the cat sits waiting for a way the other cat holds. */
    val waitingForWay: Boolean
        get() = waiting != null

    private var arrived = false
    private var suspended: Activity? = null
    private var suspendedPlace: Spot? = null
    private var suspendedLeft = 0.0
    private var suspendedAtMs = 0L

    /** Begins the world: the cat is put down at its first activity's place (a declared cut). */
    fun startAt(choice: Choice, rng: Rng) {
        val spot = choice.spot
        if (spot != null) {
            exec.place(spot.surface, spot.x, spot.z)
            m.book.place(m.slot, spot, spot.x)
        }
        begin(choice, rng, cut = true)
    }

    /**
     * Takes the world up again where the cat now stands (a restored world, a catch-up's end, the
     * house's bodies handed over): [id] when the catalogue has it, at the place the cat is at, else
     * a fresh pick, with its way there walked from here. Never a cut: the cat is already placed.
     * The place is the cat's again only when no other cat holds it: a cat put back on a place the
     * other one has (both saved on the bed, say) does something else somewhere free, and a cat put
     * down on top of the other first makes way.
     */
    fun resume(
        id: String?,
        rng: Rng,
        sinceS: Double = 0.0,
        left: Double = Double.NaN,
        at: Spot? = null,
    ) {
        waiting = null
        suspended = null
        val here = m.here
        m.book.release(m.slot)
        // two cats restored curled up together share their spot (see SocialDirector.restored)
        val free = here == null || m.book.free(m.slot, here, exec.x, 0) || m.pairedOnRestore()
        if (here != null && free) m.book.place(m.slot, here, exec.x)
        // the later cat makes way (the first has taken up its own activity already)
        if (m.slot > 0 && m.crowded()) {
            makeWay(rng)
            return
        }
        // where it is taken up: the place it was going to (the bodies handed over mid-way), else
        // where it stands, when that suits it (a cat restored on its way to bed does not sleep by
        // the bowl: it picks anew)
        val place = at ?: here
        val a0 = id?.let(registry::find)
        // a moment together is taken up where the cat lies, never walked to a nearby spot
        if (a0 != null && Social.isPair(a0) && at == null)
            return resumeInPlace(a0, rng, sinceS, left, free)
        val a =
            id?.let(registry::find)?.takeIf {
                free &&
                    it.resumesAfterRestart &&
                    (it.placeFlags == 0 || (place != null && (place.flags and it.placeFlags) != 0))
            }
        if (a == null) return begin(pick(rng), rng)
        val rest = if (left.isNaN()) remaining(a, sinceS, rng) else left
        if (rest <= 0.0) {
            // what it was doing is over by now (a nap the app slept through): what follows it
            val next = a.next?.let(registry::find)
            return begin(next?.let { Choice(it, null, 0.0, "after ${a.id}") } ?: pick(rng), rng)
        }
        begin(Choice(a, place, 0.0, RESUME, rest), rng)
    }

    /** [a] (a moment together, curled up) taken up where the cat lies, or a fresh pick. */
    private fun resumeInPlace(a: Activity, rng: Rng, sinceS: Double, left: Double, free: Boolean) {
        if (!free || !a.resumesAfterRestart) return begin(pick(rng), rng)
        val rest = if (left.isNaN()) remaining(a, sinceS, rng) else left
        if (rest <= 0.0) {
            val next = a.next?.let(registry::find)
            return begin(next?.let { Choice(it, null, 0.0, "after ${a.id}") } ?: pick(rng), rng)
        }
        begin(Choice(a, null, 0.0, RESUME, rest), rng)
    }

    /**
     * How much of [a] is left for a cat that began it [sinceS] ago (the blob's `actAt`): a fresh
     * draw of its length less the time already spent, so a nap is never begun anew at every restart
     * or catch-up (the owner's cats "stuck in bed for hours"); NaN (the activity's own length) for
     * one that draws none; 0 or less when it is over.
     */
    private fun remaining(a: Activity, sinceS: Double, rng: Rng): Double {
        val d = a.duration(m, rng)
        if (d <= 0.0) return Double.NaN
        val rest = d - sinceS.coerceAtLeast(0.0)
        return if (sinceS > 0.0 && rest < BrainTuning.Select.RESUME_MIN_LEFT_S) 0.0 else rest
    }

    /** What is left of the activity being played (the house's bodies handed over mid-way). */
    val left: Double
        get() = (seconds - atPlaceS).coerceAtLeast(BrainTuning.Select.RESUME_MIN_LEFT_S)

    /**
     * The cat is on top of the other one: it gets up (properly, if it slept), walks at least
     * [BrainTuning.Apart.ASIDE_PX] from it and sits and washes a moment before it picks anew.
     */
    fun makeWay(rng: Rng) {
        val was = activity
        end(natural = false)
        waiting = null
        begin(Choice(MakeWay, null, 0.0, MAKES_WAY), rng, wakeFirst = was.asleep, lead = Lead.ASIDE)
    }

    /** One think quantum: progress, the end of the activity, and what follows. */
    fun quantum(rng: Rng) {
        val q = BrainTuning.Time.QUANTUM_S
        elapsedS += q
        if (!arrived && exec.atPlace) {
            arrived = true
            if (place != null) m.book.arrived(m.slot)
        }
        if (arrived) atPlaceS += q
        val overran =
            elapsedS > seconds + routeAllowance() + BrainTuning.Select.WATCHDOG_SLACK_S ||
                elapsedS > activity.maxMinutes * BrainTuning.Time.MINUTE + ROUTE_MAX_S
        if (exec.done || overran) {
            if (overran) watchdogs++
            val held = waiting
            if (held != null) {
                waiting = null
                begin(held, rng)
                return
            }
            end(natural = true)
            begin(following(rng), rng)
        }
    }

    /**
     * A reflex: [activity] at [spot] now, instead of what was playing; a resumable activity is kept
     * to be resumed after it (plan 1.5).
     */
    fun interrupt(activity: Activity, spot: Spot?, reason: String, rng: Rng) {
        val was = this.activity
        val left = seconds - atPlaceS
        if (was.resumable && left > BrainTuning.Select.RESUME_MIN_LEFT_S) {
            suspended = was
            suspendedPlace = place
            suspendedLeft = left
            suspendedAtMs = m.nowMs
        }
        val wake = was.asleep
        end(natural = false)
        begin(Choice(activity, spot, 0.0, reason), rng, wakeFirst = wake)
    }

    /**
     * Plays [activity] at [spot] now for [seconds] at the place, in place of what was playing (a
     * scene in a test, a dev overlay); the reason reads "forced".
     */
    internal fun force(activity: Activity, spot: Spot?, seconds: Double, rng: Rng) {
        end(natural = false)
        begin(Choice(activity, spot, 0.0, FORCED, seconds), rng)
    }

    /**
     * Plays [a], a part of the moment together [token], as [script] (the social director wrote it)
     * in place of what the cat was doing. An ordinary activity ends here and is not taken up again
     * after the moment; one part of the moment follows another without ending it (no cooldown, no
     * novelty, one explain line per moment).
     */
    internal fun playPart(a: Activity, script: Script, reason: String, token: Int) {
        val fresh = token != pairToken || !Social.isPair(activity)
        if (!Social.isPair(activity)) end(natural = false)
        waiting = null
        suspended = null
        activity = a
        place = null
        seconds = script.beatSeconds
        atPlaceS = 0.0
        elapsedS = 0.0
        arrived = false
        exec.start(script)
        val where = m.here?.id ?: if (exec.surface == SurfaceId.FLOOR) "floor" else exec.surface.key
        doing = Doing(a.id, where, m.drives[a.mainDrive], reason)
        if (fresh) {
            explain.add(Why(m.nowMs, a.id, where, reason, emptyList()))
            m.began(a)
        }
        pairToken = token
    }

    /**
     * The moment together ended before its time (the other cat was called away, a stage timed out):
     * a cat still in it gets up from it and picks anew.
     */
    internal fun leavePair(rng: Rng) {
        if (!Social.isPair(activity)) return
        end(natural = false)
        waiting = null
        begin(pick(rng), rng)
    }

    /** A fresh pick for the cat as it is now. */
    fun pick(rng: Rng): Choice = selector.pick(m, rng, travel, ::cooling, recent)

    /** True while the activity has not cooled down since it last ended. */
    fun cooling(index: Int): Boolean = m.nowMs < coolUntilMs[index]

    /** The time the way there took, or (still on the way) the most it may take. */
    private fun routeAllowance(): Double = if (arrived) elapsedS - atPlaceS else ROUTE_MAX_S

    private fun end(natural: Boolean) {
        val a = activity
        if (natural) a.after(m.drives)
        val i = registry.indexOf(a)
        coolUntilMs[i] = m.nowMs + (a.cooldownMin * BrainTuning.Time.MINUTE * MS).toLong()
        for (k in recent.size - 1 downTo 1) recent[k] = recent[k - 1]
        recent[0] = i
        val p = place ?: m.here
        if (p != null && arrived) m.book.learn(m.slot, p, atPlaceS / BrainTuning.Time.HOUR)
        m.ended(a, if (arrived) atPlaceS else 0.0)
    }

    /** [a]'s way stayed taken: it is not tried again for a while (at least its own cooldown). */
    private fun giveUp(a: Activity) {
        val i = registry.indexOf(a)
        val minutes = maxOf(a.cooldownMin, GIVE_UP_COOL_MIN)
        coolUntilMs[i] = m.nowMs + (minutes * BrainTuning.Time.MINUTE * MS).toLong()
    }

    /** A walk to a floor place at least [BrainTuning.Apart.ASIDE_PX] from the other cat. */
    private fun aside(script: Script) {
        val x = m.asideX()
        if (exec.surface == SurfaceId.FLOOR) script.approach(x, Occluders.visibleZ(x, exec.z))
        else down(script, x)
    }

    /**
     * A cat about to stay where it stands on the floor, behind furniture that would hide most of it
     * or under the cat tree's platform with no room for its head (it stopped there on its way),
     * first steps forward to where it can be seen ([Occluders]).
     */
    private fun outOfHiding(script: Script) {
        if (exec.surface != SurfaceId.FLOOR) return
        if (!Occluders.hidden(exec.x, exec.z) && !Occluders.clipped(exec.x, exec.z)) return
        script.approach(exec.x, Occluders.visibleZ(exec.x, exec.z))
    }

    /**
     * Where a cat up on a perch lands to play with the owner's toy: beside it on the side the cat
     * comes from, so it walks up to it and reaches down (landing on it, it could not).
     */
    private fun besideToy(): Float {
        val toy = m.toy ?: return exec.x
        val side = if (exec.x >= toy.x) 1 else -1
        return World.clampHouse(toy.x + side * BrainTuning.Play.APPROACH_PX)
    }

    /** The way down to the floor at [x] (nothing when the cat cannot get there from here). */
    private fun down(script: Script, x: Float) {
        val r = exec.route(SurfaceId.FLOOR, x) ?: return
        script.route(r.steps)
    }

    private fun following(rng: Rng): Choice {
        activity.next?.let { id ->
            registry.find(id)?.let {
                return Choice(it, null, 0.0, "after ${activity.id}")
            }
        }
        val s = suspended
        if (s != null) {
            suspended = null
            val fresh = m.nowMs - suspendedAtMs <= BrainTuning.Select.RESUME_MAX_S * MS
            val spot = suspendedPlace
            val free = spot == null || m.book.free(m.slot, spot, spot.x, 0)
            // a nap a meal broke into is taken up again only by a cat that still wants one (tired,
            // or bed hours): a rested cat fed by day gets up and about instead of going straight
            // back to bed
            if (fresh && free && s.eligible(m)) return Choice(s, spot, 0.0, RESUME, suspendedLeft)
        }
        return pick(rng)
    }

    /** An activity whose way is taken by the other cat, waited for; and how often it waited. */
    private var waiting: Choice? = null
    private var waits = 0

    /** What the runner walks before an activity's own way: nothing, aside, down to the floor. */
    private enum class Lead {
        NONE,
        ASIDE,
        FLOOR,
    }

    private fun begin(
        choice: Choice,
        rng: Rng,
        cut: Boolean = false,
        wakeFirst: Boolean = false,
        lead: Lead = if (choice.activity.onFloor) Lead.FLOOR else Lead.NONE,
    ) {
        val a = choice.activity
        var spot = choice.spot
        val script = Script()
        pairToken = 0
        if (wakeFirst) wakeBeats(script)
        when (lead) {
            Lead.ASIDE -> aside(script)
            Lead.FLOOR -> if (exec.surface != SurfaceId.FLOOR) down(script, besideToy())
            Lead.NONE -> if (spot == null) outOfHiding(script)
        }
        if (spot != null && !cut && !standsAt(spot)) {
            when (routeTo(spot, script)) {
                Way.NONE -> spot = null
                Way.TAKEN -> {
                    // gives up after a while: that activity cools down and another is picked (at
                    // worst a loaf in place, which never stays on top of the other cat: makeWay)
                    if (waits++ < MAX_WAITS) waitFor(choice, script)
                    else {
                        giveUp(a)
                        begin(pick(rng), rng)
                    }
                    return
                }
                Way.CLEAR -> Unit
            }
        }
        waits = 0
        val dur = if (choice.seconds.isNaN()) a.duration(m, rng) else choice.seconds
        a.script(m, spot ?: m.here, dur, rng, script)
        activity = a
        place = spot
        seconds = script.beatSeconds
        atPlaceS = 0.0
        elapsedS = 0.0
        arrived = false
        exec.start(script)
        val where =
            (spot ?: m.here)?.id
                ?: if (exec.surface == SurfaceId.FLOOR) "floor" else exec.surface.key
        doing = Doing(a.id, where, m.drives[a.mainDrive], choice.reason)
        val top =
            if (choice.score == 0.0) emptyList() else selector.lastTop.map { it.label to it.score }
        explain.add(Why(m.nowMs, a.id, where, choice.reason, top))
        m.began(a)
    }

    private fun standsAt(spot: Spot): Boolean =
        exec.surface == spot.surface &&
            Math.abs(exec.x - spot.x) < AT_SPOT_PX &&
            (!spot.onFloor || Math.abs(exec.z - spot.z) < AT_SPOT_PX)

    private enum class Way {
        CLEAR,
        NONE,
        TAKEN,
    }

    /**
     * The way to [spot] into [script] and the place claimed ([Way.CLEAR]); [Way.NONE] when the cat
     * cannot get there, [Way.TAKEN] when the other cat holds the place or a perch on the way (a
     * reflex or a resume, which the selector did not price, can meet that).
     */
    private fun routeTo(spot: Spot, script: Script): Way {
        if (exec.surface == SurfaceId.FLOOR && spot.onFloor) {
            if (!m.book.free(m.slot, spot, spot.x, 0)) return Way.TAKEN
            script.approach(spot.x, spot.z)
            m.book.claim(m.slot, spot, spot.x, 0)
            return Way.CLEAR
        }
        val r = exec.route(spot.surface, spot.x) ?: return Way.NONE
        val passes = SpotBook.passesOf(r.leaps, spot.surface)
        if (!m.book.free(m.slot, spot, spot.x, passes)) return Way.TAKEN
        script.route(r.steps)
        if (spot.onFloor) script.approach(spot.x, spot.z)
        m.book.claim(m.slot, spot, spot.x, passes)
        return Way.CLEAR
    }

    /** Sits a moment where it is and tries [choice] again (at most [MAX_WAITS] times). */
    private fun waitFor(choice: Choice, script: Script) {
        script.act(Rows.SIT, WAIT_S)
        waiting = choice
        place = null
        seconds = script.beatSeconds
        atPlaceS = 0.0
        elapsedS = 0.0
        arrived = false
        exec.start(script)
        doing = Doing(choice.activity.id, m.here?.id ?: exec.surface.key, doing.level, WAITS)
    }

    /** A cat woken by a reflex gets up properly first: a yawn, a stretch, a shake. */
    private fun wakeBeats(script: Script) = script.wake()

    private companion object {
        const val MS = 1000.0
        const val AT_SPOT_PX = 3f
        const val ROUTE_MAX_S = 120.0
        const val RESUME = "resume"
        const val FORCED = "forced"
        const val WAITS = "waits: the way is taken"
        const val WAIT_S = 5.0
        const val MAX_WAITS = 6
        const val MAKES_WAY = "makes way: the other cat is there"

        /** An activity given up on (its way stayed taken) is not tried again for this long. */
        const val GIVE_UP_COOL_MIN = 5.0
    }
}
