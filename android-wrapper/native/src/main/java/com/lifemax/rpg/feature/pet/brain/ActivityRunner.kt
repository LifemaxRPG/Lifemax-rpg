package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId

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
     */
    fun resume(id: String?, rng: Rng) {
        waiting = null
        suspended = null
        val here = m.here
        m.book.release(m.slot)
        if (here != null) m.book.place(m.slot, here, exec.x)
        val a = id?.let(registry::find)
        begin(if (a != null) Choice(a, here, 0.0, RESUME) else pick(rng), rng)
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
        val overran = elapsedS > seconds + routeAllowance() + BrainTuning.Select.WATCHDOG_SLACK_S
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
            // a nap a meal broke into is taken up again even by a cat that would not start one now
            if (fresh && free && (s.eligible(m) || s.asleep))
                return Choice(s, spot, 0.0, RESUME, suspendedLeft)
        }
        return pick(rng)
    }

    /** An activity whose way is taken by the other cat, waited for; and how often it waited. */
    private var waiting: Choice? = null
    private var waits = 0

    private fun begin(choice: Choice, rng: Rng, cut: Boolean = false, wakeFirst: Boolean = false) {
        val a = choice.activity
        var spot = choice.spot
        val script = Script()
        if (wakeFirst) wakeBeats(script)
        if (spot != null && !cut && !standsAt(spot)) {
            when (routeTo(spot, script)) {
                Way.NONE -> spot = null
                Way.TAKEN -> {
                    // gives up after a while and rests where it is (in place: no way to be taken)
                    if (waits++ < MAX_WAITS) waitFor(choice, script)
                    else begin(Choice(loaf, null, 0.0, WAITS), rng)
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
    private fun wakeBeats(script: Script) {
        script.act(Rows.YAWN, Rows.once(Rows.YAWN))
        script.act(Rows.STRETCH, Rows.once(Rows.STRETCH))
        script.act(Rows.SHAKE, Rows.once(Rows.SHAKE))
    }

    private companion object {
        const val MS = 1000.0
        const val AT_SPOT_PX = 3f
        const val ROUTE_MAX_S = 120.0
        const val RESUME = "resume"
        const val FORCED = "forced"
        const val WAITS = "waits: the way is taken"
        const val WAIT_S = 5.0
        const val MAX_WAITS = 6
    }
}
