package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.BlobGift
import com.lifemax.rpg.feature.pet.world.BlobPair
import com.lifemax.rpg.feature.pet.world.BlobToy
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldClock
import com.lifemax.rpg.feature.pet.world.WorldCommand

/**
 * The cats of one house and the brain that runs them (plan 1.1 `CatWorld`, the [Brain] the day
 * viewer and the screens drive): one [CatMind] per cat, the shared [book] of places, the owner's
 * presence, all stepped on the [clock]'s sub-steps. Every 30th sub-step is a think quantum; the
 * cats think in slot order, so the second sees the first's claims. Headless and deterministic: the
 * same cats, start time and owner acts replay the same day at any host frame rate.
 *
 * As the world host's brain (plan 2.7): [update] takes the page's care data (hunger, vacation, the
 * rest day), [export]/[restore] save and take back the minds through the world blob (MindState),
 * [catchUp] plays a gap in fast mode (CatchUpRun), and a live world drives the house's own bodies
 * once [bind] hands them over, so every screen draws the cats this brain moves.
 *
 * @param fidelity [Fidelity.LIVE] steps a real [CatBody] per cat; [Fidelity.FAST] plays the same
 *   scripts as timed steps (soak tests and the catch-up).
 * @param registry the activity catalogue; later phases add theirs.
 */
class CatWorld(
    private val clock: WorldClock,
    cats: List<CatProfile>,
    val fidelity: Fidelity = Fidelity.LIVE,
    private val registry: ActivityRegistry = Catalogue.core(),
    /**
     * Each cat's measured ways, by slot, to start from (the catch-up's copy shares the live ones).
     */
    routes: List<HashMap<Long, FloatArray>>? = null,
    /** False for a world whose state is [restore]d at once: its cats pick nothing first. */
    pickFirst: Boolean = true,
) : Brain, House {
    /** A live body per cat, or the fast executor. */
    enum class Fidelity {
        LIVE,
        FAST,
    }

    init {
        require(cats.size in 1..2) { "a house has one or two cats" }
    }

    /** Who has which place. */
    val book = SpotBook(cats.size)

    override var ownerPresent = false
        private set

    override var restDay = false
        private set

    override val catCount = cats.size

    /** The minds, by slot. */
    val minds: List<CatMind> =
        cats.mapIndexed { slot, p ->
            val profile = p.copy(slot = slot)
            CatMind(
                profile,
                executor(profile),
                book,
                this,
                registry,
                routes?.get(slot) ?: HashMap(),
            )
        }

    override val names: List<String> = cats.map { it.name }

    /** What the two cats do together (none for one cat). */
    internal val social = SocialDirector(minds, this, clock::epochMs)

    private var localDay = Long.MIN_VALUE
    private var dayKey = ""
    private val dayKeyOf = { dayKey }

    init {
        refreshDay()
        for (m in minds) m.start(clock.simMs, clock.hourOfDay(), localDay, dayKey, pickFirst)
    }

    override fun step(step: Long, think: Boolean) {
        for (i in minds.indices) minds[i].subStep()
        if (!think) return
        refreshDay()
        val now = clock.simMs
        val hour = clock.hourOfDay()
        for (i in minds.indices) minds[i].think(now, hour, localDay, dayKeyOf)
        social.think(hour)
        keepApart()
    }

    override fun now(slot: Int): Doing = minds[slot].runner.doing

    /** What [restore] took that this brain does not run yet (later phases): carried through. */
    private val kept = HashMap<String, BlobCat>()
    private var keptPair: BlobPair? = null
    private var keptToys: List<BlobToy> = emptyList()
    private var keptGift: BlobGift? = null

    override val drivesBodies: Boolean
        get() = fidelity == Fidelity.LIVE

    /**
     * Plays the house's [bodies] (by slot) from now on instead of this world's own: each is put
     * where its cat stands and the cat takes up what it was doing.
     */
    override fun bind(bodies: List<CatBody>) {
        if (fidelity != Fidelity.LIVE) return
        val bound = minds.filter { bodies.getOrNull(it.slot) != null }
        for (m in bound) (m.exec as BodyExecutor).adopt(bodies[m.slot])
        // two cats curled up together go on lying together on the new bodies
        social.rebinding()
        for (m in bound) m.resume(m.runner.activity.id, left = m.runner.left, at = m.runner.place)
        social.restored()
    }

    /**
     * The house's toys (the world host's field, which the bodies were given too): what an owner's
     * game is played with ([offerToy]).
     */
    override fun bindToys(toys: ToyField) {
        this.toys = toys
    }

    override var toys: ToyField? = null
        private set

    /**
     * The owner tossed the page's toy [key] into the room: it lands on the floor in front of the
     * cat that will play (an awake one first, else the first), which goes to it (notices it, picks
     * it up, carries and tosses it, chases, pounces on it and bats it, sets it down), unless every
     * cat would rather not: then the toy lies by the first cat and the answer says why, in a short
     * line, and nobody pretends. [atMs] (the world's epoch) names the game's report.
     */
    override fun offerToy(key: String, atMs: Long): PlayAnswer {
        val field = toys ?: return PlayAnswer(-1, PlayWords.NOT_HERE)
        val willing = minds.filter { it.refusesPlay() == null }
        val player = willing.firstOrNull { !it.runner.activity.asleep } ?: willing.firstOrNull()
        val near = player ?: minds.first()
        val toy = ToyDrop.near(field, key, near.exec) ?: return PlayAnswer(-1, PlayWords.NO_TOY)
        if (player == null) {
            val line = near.refusesPlay() ?: PlayWords.TIRED
            return PlayAnswer(-1, line.replace(PlayWords.NAME, near.profile.name))
        }
        player.play(toy, key, OpIds.of(player.profile.id, PLAY_COMMAND, atMs))
        return PlayAnswer(player.slot, "")
    }

    private var lastDemo = -1

    /**
     * DEVELOPER DEMO: the awake cat that is doing least (a loaf, a wander, a wash) runs a parkour
     * tour, the other one next time; none while a tour is running, and none that is eating, asleep,
     * in a game or too hungry or sleepy: the answer then says why, in one line ([DemoParkour]).
     */
    override fun demoParkour(): PlayAnswer {
        if (minds.any { it.touring }) return PlayAnswer(-1, DemoParkour.RUNNING)
        val willing = minds.filter { it.refusesDemo() == null }
        if (willing.isEmpty()) {
            val m = minds.first()
            val line = m.refusesDemo() ?: DemoParkour.BUSY
            return PlayAnswer(-1, line.replace(PlayWords.NAME, m.profile.name))
        }
        val order =
            compareBy<CatMind>(
                { DemoParkour.busyRank(it.runner.activity) },
                { if (it.slot == lastDemo) 1 else 0 },
            )
        val pick = willing.sortedWith(order).first()
        if (!pick.startDemo())
            return PlayAnswer(-1, DemoParkour.NO_PERCH.replace(PlayWords.NAME, pick.profile.name))
        lastDemo = pick.slot
        return PlayAnswer(pick.slot, "")
    }

    private val reports = ArrayList<WorldCommand>()

    /** Queues [c] for the page (an owner's game that ended). */
    override fun report(c: WorldCommand) {
        reports += c
    }

    override fun takeReports(): List<WorldCommand> {
        if (reports.isEmpty()) return emptyList()
        return ArrayList(reports).also { reports.clear() }
    }

    override fun mateX(slot: Int): Float? {
        val other = minds.firstOrNull { it.slot != slot }?.exec ?: return null
        return if (other.surface == SurfaceId.FLOOR) other.x else null
    }

    override fun crowded(slot: Int): Boolean {
        // a moment together (curled up, a nose touch) is the one time two cats may be this close
        if (social.sharing()) return false
        // two cats fed together walk the same way to their bowls and eat side by side
        if (minds.size > 1 && minds.all { goingToEat(it) }) return false
        val a = minds[slot].exec
        for (o in minds) {
            if (o.slot == slot) continue
            val b = o.exec
            if (a.surface != b.surface) continue
            if (Math.abs(a.x - b.x) >= BrainTuning.Apart.OVERLAP_PX) continue
            if (a.surface == SurfaceId.FLOOR && Math.abs(a.z - b.z) >= BrainTuning.Apart.OVERLAP_Z)
                continue
            return true
        }
        return false
    }

    override fun pairedOnRestore(slot: Int): Boolean = social.pairedOnRestore()

    /** True while the two cats share a moment that lets them be close (see [SocialDirector]). */
    fun sharing(): Boolean = social.sharing()

    /** The page's friendship of the two (tests and the day viewer set it without a block). */
    fun pair(info: PairInfo) = social.pair(info)

    override fun asideX(slot: Int): Float {
        val me = minds[slot].exec.x
        val other = minds.firstOrNull { it.slot != slot }?.exec?.x ?: return me
        val gap = BrainTuning.Apart.ASIDE_PX
        val lo = World.HOUSE_MIN_X + gap / 2
        val hi = World.HOUSE_MAX_X - gap / 2
        val left = other - gap
        val right = other + gap
        val dl = Math.abs(me - left)
        val dr = Math.abs(me - right)
        // on the very same point the first cat steps left, the second right
        val near = if (dl < dr || (dl == dr && slot == 0)) left else right
        val far = if (near == left) right else left
        return if (near in lo..hi) near else far.coerceIn(lo, hi)
    }

    /**
     * The overlap guard (plan 1.5, [BrainTuning.Apart]): two cats that stay on top of each other
     * for a few seconds are parted; the one awake (or the second) makes way, never one going to its
     * meal: two fed together walk the same way to bowls side by side and are not parted.
     */
    private fun keepApart() {
        if (minds.size < 2 || social.sharing() || !crowded(0)) {
            together = 0
            return
        }
        if (++together < BrainTuning.Apart.OVERLAP_QUANTA) return
        together = 0
        val a = minds[0]
        val b = minds[1]
        // a cat on its way to its meal (or eating) is never the one sent away from it
        val first =
            when {
                goingToEat(a) -> b
                goingToEat(b) -> a
                b.runner.activity.asleep && !a.runner.activity.asleep -> a
                else -> b
            }
        // one already making way that could not get clear: the other one goes instead
        val mover = if (first.runner.activity === MakeWay) (if (first === a) b else a) else first
        if (mover.runner.activity !== MakeWay && !goingToEat(mover)) mover.makeWay()
    }

    private var together = 0

    /** Cat [m] is eating, or on its way to its bowl to eat (or about to, once it lands). */
    private fun goingToEat(m: CatMind): Boolean =
        m.runner.activity === Eat || m.runner.nextReflex === Eat

    /** The page's newest block: each cat's hunger stage and vacation, and the rest day. */
    override fun update(block: WorldBlock) {
        for (c in block.cats) {
            val m = minds.firstOrNull { it.profile.id == c.id } ?: continue
            m.hunger(c.hunger.stage.coerceIn(0, MAX_HUNGER), c.hunger.vacation)
        }
        block.pair?.let { social.pair(PairInfo.of(it)) }
        restDay = block.context.restDay
    }

    override fun export(): BrainState {
        val epoch = clock.epochMs()
        val day = clock.dayKey()
        val cats = LinkedHashMap<String, BlobCat>()
        for (m in minds) cats[m.profile.id] = MindState.export(m, kept[m.profile.id], epoch, day)
        return BrainState(cats, social.export(keptPair), keptToys, keptGift)
    }

    /** Takes back [state]: each cat it has is put where it stood and takes up its activity. */
    override fun restore(state: BrainState) {
        // every cat is put back first, then each takes up what it did, so the second sees the
        // first where it really is (and neither holds a place from before the restore)
        val back = minds.filter { state.cats[it.profile.id] != null }
        for (m in back) {
            val saved = state.cats.getValue(m.profile.id)
            kept[m.profile.id] = saved
            MindState.restore(m, saved)
        }
        keptPair = state.pair
        social.restore(state.pair)
        social.restoring(minds.map { state.cats[it.profile.id]?.act })
        // each takes up what is left of it: the time since it began (actAt) is already spent
        val now = clock.epochMs()
        for (m in back) {
            val saved = state.cats.getValue(m.profile.id)
            val sinceS = (now - saved.actAt).coerceAtLeast(0L) / MS_PER_S
            m.resume(MindState.activityOf(saved.act), sinceS)
        }
        social.restored()
        keptToys = state.toys
        keptGift = state.gift
    }

    override fun catchUp(gapMs: Long): CatchUp = CatchUpRun.run(this, clock, gapMs)

    /** The same cats as they are now, in fast mode on [fastClock] (the catch-up's world). */
    internal fun fastCopy(fastClock: WorldClock): CatWorld {
        val profiles =
            minds.map {
                it.profile.copy(
                    hungerStage = it.persona.hungerStage,
                    vacation = it.persona.vacation,
                    trust = it.trust,
                )
            }
        val routes = minds.map { it.runner.routes }
        return CatWorld(fastClock, profiles, Fidelity.FAST, registry, routes, false).also {
            it.restDay = restDay
            it.social.pair(social.info)
        }
    }

    /**
     * The owner's acts (the day viewer's script, later the page's cues): `open` / `close` a view,
     * `feed` (every cat) or `feed:<slot>`, `hunger:<slot>:<stage>`, `vacation:on|off`,
     * `restday:on|off`. Anything else is ignored.
     */
    override fun owner(act: OwnerAct) {
        val parts = act.kind.split(':')
        when (parts[0]) {
            "open" -> {
                ownerPresent = true
                for (m in minds) m.ownerArrived()
            }
            "close" -> ownerPresent = false
            "feed" ->
                if (parts.size > 1) parts[1].toIntOrNull()?.let { minds.getOrNull(it)?.fed() }
                else for (m in minds) m.fed()
            "hunger" -> {
                val m = parts.getOrNull(1)?.toIntOrNull()?.let { minds.getOrNull(it) } ?: return
                val stage = parts.getOrNull(2)?.toIntOrNull() ?: return
                m.hunger(stage.coerceIn(0, MAX_HUNGER), m.persona.vacation)
            }
            "vacation" ->
                for (m in minds) m.hunger(m.persona.hungerStage, parts.getOrNull(1) == "on")
            "restday" -> restDay = parts.getOrNull(1) == "on"
        }
    }

    private fun refreshDay() {
        val local = clock.epochMs() + clock.zoneOffsetMin * MS_PER_MINUTE
        val d = Math.floorDiv(local, MS_PER_DAY)
        if (d != localDay) {
            localDay = d
            dayKey = clock.dayKey()
            social.newDay(dayKey)
        }
    }

    private fun executor(p: CatProfile): Executor {
        val persona = Persona(p)
        val seed = Rng.hash(p.id)
        return when (fidelity) {
            Fidelity.LIVE ->
                BodyExecutor(
                    CatBody(seed).also {
                        it.growthScale = persona.growthScale
                        it.bodyLength = p.bodyMorph.toFloat()
                        it.legLength = p.legMorph.toFloat()
                    }
                )
            Fidelity.FAST ->
                FastExecutor(persona.growthScale, p.bodyMorph.toFloat(), p.legMorph.toFloat(), seed)
        }
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_S = 1000.0
        const val MS_PER_DAY = 86_400_000L
        const val MAX_HUNGER = 5

        /** The page's command an owner's game is reported with. */
        const val PLAY_COMMAND = "play"
    }
}
