package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.BlobGift
import com.lifemax.rpg.feature.pet.world.BlobPair
import com.lifemax.rpg.feature.pet.world.BlobToy
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldClock

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
        for (m in minds) {
            val body = bodies.getOrNull(m.slot) ?: continue
            (m.exec as BodyExecutor).adopt(body)
            m.resume(m.runner.activity.id)
        }
    }

    /** The page's newest block: each cat's hunger stage and vacation, and the rest day. */
    override fun update(block: WorldBlock) {
        for (c in block.cats) {
            val m = minds.firstOrNull { it.profile.id == c.id } ?: continue
            m.hunger(c.hunger.stage.coerceIn(0, MAX_HUNGER), c.hunger.vacation)
        }
        restDay = block.context.restDay
    }

    override fun export(): BrainState {
        val epoch = clock.epochMs()
        val day = clock.dayKey()
        val cats = LinkedHashMap<String, BlobCat>()
        for (m in minds) cats[m.profile.id] = MindState.export(m, kept[m.profile.id], epoch, day)
        return BrainState(cats, keptPair, keptToys, keptGift)
    }

    /** Takes back [state]: each cat it has is put where it stood and takes up its activity. */
    override fun restore(state: BrainState) {
        for (m in minds) {
            val saved = state.cats[m.profile.id] ?: continue
            kept[m.profile.id] = saved
            MindState.restore(m, saved)
        }
        keptPair = state.pair
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
        const val MS_PER_DAY = 86_400_000L
        const val MAX_HUNGER = 5
    }
}
