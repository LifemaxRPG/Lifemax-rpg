package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.ToyBody
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.world.MomentReport
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.PlayDone
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldContract

/** What the house tells each of its cats: the owner, the day, how many cats live here. */
interface House {
    /** A view of the room is on screen. */
    val ownerPresent: Boolean

    /** Today is one of the owner's rest days (plan 1.9). */
    val restDay: Boolean

    /** One or two. */
    val catCount: Int

    /** The house's toys, once the host has given them (null in a headless or fast world). */
    val toys: ToyField?

    /**
     * Where the other cat stands on the floor (null: none, or up on a perch), seen by cat [slot].
     */
    fun mateX(slot: Int): Float? = null

    /** Another cat stands on top of cat [slot] (see [BrainTuning.Apart]). */
    fun crowded(slot: Int): Boolean

    /** A floor x at least [BrainTuning.Apart.ASIDE_PX] from the other cat, near cat [slot]. */
    fun asideX(slot: Int): Float

    /** Queues [c] for the page. */
    fun report(c: WorldCommand)

    /** The world's clock as epoch ms (what a report is dated and named by); 0 where unknown. */
    fun epochMs(): Long = 0L

    /**
     * Cat [slot] is being restored lying curled up with the other cat (both saved in the nap, side
     * by side): it keeps its spot beside the other instead of making way.
     */
    fun pairedOnRestore(slot: Int): Boolean = false
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

    /**
     * The first moments already reported for this cat (the blob's `fx`, restored with the cat): a
     * first is reported once, ever.
     */
    internal val fx = LinkedHashSet<String>()

    /** Where the cat spent its time, for the page's favourite place and toy (`dwell`). */
    private val dwell = Dwell(profile.id)

    /** The page toy the cat is playing with alone now (null: none), for [dwell]. */
    private var soloToy: String? = null
    private var dayKey = ""
    private var ownerCame = false
    private var sitterDue = false
    private var sitterMeals = 0

    /** Simulated ms since the world began, as of the last quantum. */
    override var nowMs = 0L
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
    fun resume(id: String?, sinceS: Double = 0.0, left: Double = Double.NaN, at: Spot? = null) =
        runner.resume(id, rng, sinceS, left, at)

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
        tally()
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

    override val mateX: Float?
        get() = house.mateX(slot)

    /** Another cat stands on top of this one. */
    fun crowded(): Boolean = house.crowded(slot)

    /** Where to step aside to, on the floor, clear of the other cat. */
    fun asideX(): Float = house.asideX(slot)

    /** Gets off the other cat (the overlap guard): walks aside first. */
    fun makeWay() = runner.makeWay(rng)

    /** See [House.pairedOnRestore]. */
    fun pairedOnRestore(): Boolean = house.pairedOnRestore(slot)

    /** Plays its part [script] of the moment together [token] as [a] (the social director's). */
    internal fun playPart(a: Activity, script: Script, reason: String, token: Int) =
        runner.playPart(a, script, reason, token)

    /** The moment together was given up: the cat picks anew. */
    internal fun leavePair() = runner.leavePair(rng)

    override var toy: ToyBody? = null
        private set

    override var stillS = 0.0
        private set

    override var sinceMealS = Double.MAX_VALUE
        private set

    override val looseToy: ToyBody?
        get() = house.toys?.nearestPickable(exec.x, SurfaceId.FLOOR)

    private var playOp = ""

    /** The game being played (its toy and report op), from its start: a second toss ends it. */
    private var gameToy: ToyBody? = null
    private var gameOp = ""

    /**
     * Why this cat will not come and play now, in a short line with [PlayWords.NAME] for its name
     * (hungry, eating, fast asleep, too sleepy), or null when it will.
     */
    fun refusesPlay(): String? = OwnerPlay.refusal(this, runner.activity, runner.atPlaceS)

    /**
     * The owner offered [toy] (the page's toy [key]): the game starts now, reported with [op] when
     * it ends.
     */
    fun play(toy: ToyBody, key: String, op: String) {
        this.toy = toy
        playKey = key
        playOp = op
        runner.interrupt(OwnerPlay, null, OwnerPlay.REASON, rng)
    }

    private var playKey = ""
    private var gameKey = ""

    /** The perches still to visit on a developer parkour demo's tour ([DemoParkour]). */
    private val demoStops = ArrayDeque<Spot>()

    /** A demo stop is being played (or the tour has stops left): no second demo starts now. */
    var touring = false
        private set

    /** Why this cat will not run the developer parkour demo now, or null when it will. */
    fun refusesDemo(): String? = DemoParkour.refusal(this, runner.activity)

    /**
     * DEVELOPER DEMO: sends the cat on a parkour tour now (see [DemoParkour]); false when no perch
     * can be reached. Nothing is reported to the page.
     */
    fun startDemo(): Boolean {
        val stops = DemoParkour.tour(this)
        if (stops.isEmpty()) return false
        demoStops.clear()
        demoStops.addAll(stops.drop(1))
        touring = true
        runner.interrupt(ParkourVisit, stops[0], DemoParkour.REASON, rng, BrainTuning.Demo.STOP_S)
        return true
    }

    /** The next perch of the demo tour (the runner asks when a stop ends), or null. */
    internal fun nextDemoStop(): Spot? = demoStops.removeFirstOrNull()

    /** Called by the runner when [a] ends (by itself or cut short) after [atPlaceS] at it. */
    internal fun ended(a: Activity, atPlaceS: Double) {
        if (a !== OwnerPlay) return
        val t = gameToy ?: return
        val played = atPlaceS >= BrainTuning.Play.PLAYED_MIN_S
        if (gameOp.isNotEmpty()) house.report(PlayDone(gameOp, profile.id, gameKey, played))
        if (toy === t && playOp == gameOp) {
            toy = null
            playOp = ""
        }
        gameToy = null
        gameOp = ""
    }

    /** Called by the runner when [a] begins: the mood answers the moment. */
    internal fun began(a: Activity) {
        val t = BrainTuning.Mood
        // anything but a demo stop (a meal, a game, a pick of its own) ends the tour
        touring = a === ParkourVisit && runner.doing.why == DemoParkour.REASON
        if (!touring) demoStops.clear()
        when (a.id) {
            Eat.id -> {
                sinceMealS = 0.0
                mood.bump(t.EAT_VALENCE, t.EAT_AROUSAL)
                if (sitterDue) {
                    sitterDue = false
                    sitterMeals++
                }
            }
            Beg.id -> mood.bump(t.BEG_VALENCE, 0.0)
            OwnerPlay.id -> {
                gameToy = toy
                gameOp = playOp
                gameKey = playKey
                mood.bump(BrainTuning.Play.VALENCE, BrainTuning.Play.AROUSAL)
            }
            ParkourVisit.id -> mood.bump(0.0, t.LAND_AROUSAL)
            // the owner saw the zoomies (the web needs the room view open too)
            Zoomies.id -> if (house.ownerPresent) firstMoment("first_zoomies")
            SoloPlay.id ->
                soloToy =
                    looseToy
                        ?.takeIf { Math.abs(it.x - exec.x) <= BrainTuning.Roam.SOLO_TOY_REACH_PX }
                        ?.kind
                        ?.key
        }
    }

    private fun newDay(localDay: Long, dayKey: String) {
        day = localDay
        this.dayKey = dayKey
        schedule.plan(profile.id, dayKey, persona.lazy, house.restDay)
        rng = Rng(Rng.hash("${profile.id}|$dayKey|choice"))
        sitterMeals = 0
    }

    /** Roaming's clocks: how long the cat has been still, how long since its meal. */
    private fun tally() {
        val q = BrainTuning.Time.QUANTUM_S
        stillS = if (runner.activity.lively || exec.moving) 0.0 else stillS + q
        if (sinceMealS < Double.MAX_VALUE) sinceMealS += q
        val toy = if (runner.activity === SoloPlay) soloToy else null
        val report = dwell.add(dayKey, here?.pageId, toy, q, house.epochMs())
        if (report != null) house.report(report)
    }

    /** Hands what has gathered of the cat's time at places and toys to the page now. */
    fun flushDwell() {
        dwell.take(house.epochMs())?.let(house::report)
    }

    /**
     * The first moment [kind] (a [MomentReport.MOMENT_KINDS] name) happened to the cat while the
     * owner watched: reported once, ever; a returned blink and a respected boundary are kept as
     * `first_blink` and `first_respect`, the others by their own name.
     */
    internal fun firstMoment(kind: String) {
        val key = if (kind == "blink" || kind == "respect") "first_$kind" else kind
        require(key in WorldContract.FIRST_MOMENTS) { "no first moment $kind" }
        if (!fx.add(key)) return
        house.report(
            MomentReport(
                OpIds.firstMoment(profile.id, kind),
                profile.id,
                kind,
                house.epochMs(),
                null,
            )
        )
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
