package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId

/** What the house tells each of its cats: the owner, the day, how many cats live here. */
interface House {
    /** A view of the room is on screen. */
    val ownerPresent: Boolean

    /** Today is one of the owner's rest days (plan 1.9). */
    val restDay: Boolean

    /** One or two. */
    val catCount: Int
}

/**
 * One cat's mind (plan 1.1 `CatMind`): its [drives], [mood], [persona] and [schedule], the reflexes
 * for the page's cues (a meal served, the owner arriving while it is hungry) and its [runner],
 * driving the [exec]utor (the live body, or the fast one). It thinks once per think quantum
 * ([think]); between quanta only the body moves. A quantum allocates nothing unless the cat picks a
 * new activity.
 */
class CatMind(
    val profile: CatProfile,
    val exec: Executor,
    override val book: SpotBook,
    private val house: House,
    registry: ActivityRegistry,
    routes: HashMap<Long, FloatArray> = HashMap(),
) : MindView {
    override val slot = profile.slot
    override val drives = Drives()
    override val persona = Persona(profile)
    override val schedule = Schedule()
    val mood = Mood()
    val runner = ActivityRunner(this, exec, registry, routes)

    /** The live body (null in fast mode). */
    val body: CatBody?
        get() = (exec as? BodyExecutor)?.body

    private var rng = Rng(Rng.hash(profile.id))
    private var day = Long.MIN_VALUE
    private val eat = registry.find(Catalogue.EAT)!!
    private val beg = registry.find(Beg.id)
    private val begIndex = beg?.let { registry.indexOf(it) } ?: -1
    private var fedPending = false
    private var ownerCame = false
    private var sitterDue = false
    private var sitterMeals = 0

    /** Simulated ms since the world began, as of the last quantum. */
    var nowMs = 0L
        private set

    override var hour = 0.0
        private set

    override var trust = profile.trust
    override val ownerPresent: Boolean
        get() = house.ownerPresent

    override val sitterMeal: Boolean
        get() = persona.vacation && sitterDue

    override val surface: SurfaceId
        get() = exec.surface

    override val x: Float
        get() = exec.x

    override val here: Spot?
        get() = Spots.at(exec.surface, exec.x)

    override val valence: Double
        get() = mood.valence

    override val arousal: Double
        get() = mood.arousal

    init {
        book.seedLikes(slot, profile.likes)
        for (d in Drives.ALL) drives[d] = BrainTuning.Care.START_DRIVES[d.ordinal]
        exec.weak = persona.weak
    }

    /**
     * Puts the cat at its first activity (the world's start); with [pick] false only the day is set
     * up, for a world whose state is restored next (the catch-up's copy).
     */
    fun start(nowMs: Long, hour: Double, localDay: Long, dayKey: String, pick: Boolean = true) {
        this.nowMs = nowMs
        this.hour = hour
        newDay(localDay, dayKey)
        if (pick) runner.startAt(runner.pick(rng), rng)
    }

    /**
     * Takes up activity [id] (or a fresh pick) where the cat now stands; see
     * [ActivityRunner.resume].
     */
    fun resume(id: String?) = runner.resume(id, rng)

    /** One 1/120 s sub-step: the body moves. */
    fun subStep() = exec.subStep()

    /** One think quantum at simulated [nowMs], local [hour], on local day [localDay]. */
    fun think(nowMs: Long, hour: Double, localDay: Long, dayKey: () -> String) {
        this.nowMs = nowMs
        this.hour = hour
        if (localDay != day) newDay(localDay, dayKey())
        exec.think()
        book.standing(slot, exec.surface, exec.x)
        if (exec.weak != persona.weak) exec.weak = persona.weak
        sitter()
        integrate()
        mood.quantum(persona, runner.activity, drives[Drive.ENERGY], schedule.peak(hour))
        reflexes()
        runner.quantum(rng)
        exec.affect(mood.ear, mood.tail, mood.valence, mood.arousal)
    }

    /** Plays activity [id] at [spot] for [seconds] now (scenes in tests, a dev overlay). */
    internal fun force(id: String, spot: Spot?, seconds: Double) {
        runner.force(requireNotNull(registryFind(id)) { "no activity $id" }, spot, seconds, rng)
    }

    private val registryFind: (String) -> Activity? = registry::find

    /** The page served this cat a meal (its `fed` cue): it goes to eat at once. */
    fun fed() {
        fedPending = true
        persona.hungerStage = 0
    }

    /** The owner opened a view of the room. */
    fun ownerArrived() {
        ownerCame = true
    }

    /** The page's hunger stage and vacation for this cat. */
    fun hunger(stage: Int, vacation: Boolean) {
        persona.hungerStage = stage
        persona.vacation = vacation
    }

    /** Called by the runner when [a] begins: the mood answers the moment. */
    internal fun began(a: Activity) {
        val t = BrainTuning.Mood
        when (a.id) {
            Eat.id -> {
                mood.bump(t.EAT_VALENCE, t.EAT_AROUSAL)
                if (sitterDue) {
                    sitterDue = false
                    sitterMeals++
                }
            }
            Beg.id -> mood.bump(t.BEG_VALENCE, 0.0)
            ParkourVisit.id -> mood.bump(0.0, t.LAND_AROUSAL)
        }
    }

    private fun newDay(localDay: Long, dayKey: String) {
        day = localDay
        schedule.plan(profile.id, dayKey, persona.lazy, house.restDay)
        rng = Rng(Rng.hash("${profile.id}|$dayKey|choice"))
        sitterMeals = 0
    }

    private fun sitter() {
        val meals = BrainTuning.Care.SITTER_MEAL_H
        if (persona.vacation && sitterMeals < meals.size && hour >= meals[sitterMeals])
            sitterDue = true
    }

    private fun reflexes() {
        if (fedPending) {
            fedPending = false
            if (runner.activity !== eat) runner.interrupt(eat, Spots.bowl(slot), "fed", rng)
            return
        }
        if (!ownerCame) return
        ownerCame = false
        val b = beg ?: return
        val a = runner.activity
        val deep = a.asleep && runner.atPlaceS > BrainTuning.Care.DEEP_SLEEP_S
        if (b.eligible(this) && a !== b && a !== eat && !deep && !runner.cooling(begIndex))
            runner.interrupt(b, Spots.bowl(slot), b.reason(this), rng)
    }

    /** One quantum of every drive under the activity being played (plan 1.3). */
    private fun integrate() {
        val t = BrainTuning.Drives
        val a = runner.activity
        val asleep = a.asleep
        val soft = here?.has(Spots.SOFT) == true
        val restDay = if (house.restDay) BrainTuning.Schedule.REST_DAY_DRAIN else 1.0
        if (asleep) {
            drives.relax(
                Drive.ENERGY,
                1.0,
                t.ENERGY_SLEEP_TAU / (if (soft) t.SOFT_SLEEP_GAIN else 1.0),
            )
        } else {
            drives.relax(
                Drive.ENERGY,
                0.0,
                t.ENERGY_AWAKE_TAU / (persona.energyDrain * a.exertion * restDay),
            )
        }
        for (i in 1 until Drives.COUNT) {
            val d = Drives.ALL[i]
            val sat = a.satisfies(d)
            if (sat > 0.0) {
                drives.relax(d, 0.0, sat)
                continue
            }
            var tau = t.RISE_TAU[i] / gain(d) * (if (asleep) t.ASLEEP_SLOWDOWN else 1.0)
            if (d == Drive.SOCIAL && house.ownerPresent) tau /= t.OWNER_SOCIAL_GAIN
            val target = if (d == Drive.COMPANY && house.catCount < 2) 0.0 else 1.0
            drives.relax(d, target, tau)
        }
        if (persona.weak) drives.cap(Drive.ENERGY, t.WEAK_ENERGY_CAP)
    }

    private fun gain(d: Drive): Double =
        when (d) {
            Drive.PLAY -> persona.playGain
            Drive.CURIOSITY -> persona.curiosityGain
            Drive.SOCIAL -> persona.socialGain
            Drive.COMPANY -> persona.socialGain
            Drive.COMFORT -> COMFORT_BASE + COMFORT_TIRED * (1.0 - drives[Drive.ENERGY])
            Drive.TERRITORY -> persona.territoryGain
            else -> 1.0
        }

    private companion object {
        /** Comfort rises with tiredness: gain 0.6 rested .. 1.4 exhausted. */
        const val COMFORT_BASE = 0.6
        const val COMFORT_TIRED = 0.8
    }
}
