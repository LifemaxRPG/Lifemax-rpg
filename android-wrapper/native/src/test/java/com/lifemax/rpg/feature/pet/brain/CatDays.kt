package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock

/**
 * Test support for the brain: the cats of the day viewer's cases (three personas, a kitten, a weak
 * hungry cat, a house of two), an owner's day (views opened, meals served) with the page's hunger
 * stages in between, and [run], which drives a [CatWorld] on a fake clock exactly as the viewer
 * does and hands every sub-step to a probe.
 */
object CatDays {
    /** 30 Oct 2026 00:00 UTC. */
    const val MIDNIGHT = 1_793_318_400_000L
    const val HOUR = 3_600_000L
    const val MINUTE = 60_000L
    private const val NANOS_PER_MS = 1_000_000L

    val LAZY = mapOf("sk" to 0.3, "ou" to 0.15, "dm" to 0.3, "sp" to 0.15, "fr" to 0.6, "vo" to 0.3)
    val PLAYFUL =
        mapOf("sk" to 0.15, "ou" to 0.85, "dm" to 0.55, "sp" to 0.85, "fr" to 0.7, "vo" to 0.6)
    val SHY = mapOf("sk" to 0.9, "ou" to 0.2, "dm" to 0.2, "sp" to 0.4, "fr" to 0.4, "vo" to 0.2)
    val AVERAGE =
        mapOf("sk" to 0.5, "ou" to 0.5, "dm" to 0.5, "sp" to 0.5, "fr" to 0.5, "vo" to 0.5)

    /** A cat with world id [id] (it seeds the cat), named [name]. */
    fun cat(
        name: String,
        persona: Map<String, Double>,
        id: String = "c" + name.lowercase(),
        stage: Int = -1,
        hunger: Int = 0,
        slot: Int = 0,
    ) =
        CatProfile(
            id = id,
            name = name,
            slot = slot,
            persona = persona,
            stage = stage,
            hungerStage = hunger,
        )

    /**
     * The page's hunger stage after [hours] without a meal (petStageOf, 15-runtime.js): 0 under 8
     * h, 1 to 14, 2 to 26, 3 to 50, 4 to 120, then 5.
     */
    private val STAGE_FROM_H = doubleArrayOf(8.0, 14.0, 26.0, 50.0, 120.0)

    /**
     * An owner's day: views opened at [visits] (hour from, hour to), meals at [meals] (hours), the
     * cats last fed [lastFedH] hours before the day began; the page's hunger stage changes come as
     * `hunger` acts for each of [cats] cats, as the page's projection would bring them.
     */
    fun owner(
        cats: Int = 1,
        meals: List<Double> = listOf(8.0, 18.25),
        visits: List<Pair<Double, Double>> = NORMAL_VISITS,
        lastFedH: Double = 5.75,
        hours: Int = 24,
    ): List<OwnerAct> {
        val acts = ArrayList<OwnerAct>()
        for ((from, to) in visits) {
            acts += OwnerAct(ms(from), "open")
            acts += OwnerAct(ms(to), "close")
        }
        for (h in meals) acts += OwnerAct(ms(h), "feed")
        // the page's stage between meals
        val feeds = listOf(-lastFedH) + meals
        for (slot in 0 until cats) {
            acts += OwnerAct(0, "hunger:$slot:${stageAt(lastFedH)}")
            for ((i, fed) in feeds.withIndex()) {
                val until = feeds.getOrNull(i + 1) ?: hours.toDouble()
                for ((stage, after) in STAGE_FROM_H.withIndex()) {
                    val at = fed + after
                    if (at > maxOf(fed, 0.0) && at < until)
                        acts += OwnerAct(ms(at), "hunger:$slot:${stage + 1}")
                }
            }
        }
        return acts.sortedBy { it.atMs }
    }

    private fun stageAt(hours: Double): Int = STAGE_FROM_H.count { hours >= it }

    private fun ms(h: Double) = (h * HOUR).toLong()

    val NORMAL_VISITS = listOf(7.75 to 8.25, 12.5 to 12.7, 18.0 to 19.0, 21.5 to 21.75)

    /** One of the day viewer's cases. */
    class Case(val name: String, val cats: List<CatProfile>, val acts: List<OwnerAct>)

    val CASES: List<Case> by lazy {
        listOf(
            Case("lazy", listOf(cat("Ted", LAZY)), owner()),
            Case("playful", listOf(cat("Pip", PLAYFUL)), owner()),
            Case("shy", listOf(cat("Mia", SHY)), owner()),
            Case("kitten", listOf(cat("Bean", PLAYFUL, stage = 2)), owner()),
            // last fed 30 h before the day: very hungry (stage 3) all morning, weak at 20:00 unless
            // fed; the owner looks in but serves the meal only in the evening
            Case(
                "weak",
                listOf(cat("Sol", AVERAGE, hunger = 3)),
                owner(meals = listOf(19.0), lastFedH = 30.0),
            ),
            Case(
                "two cats",
                listOf(
                    cat("Ted", PLAYFUL, id = "cmu9rkw00"),
                    cat("Lily", SHY, id = "cmv2cdxc0", slot = 1),
                ),
                owner(cats = 2),
            ),
        )
    }

    /** The case called [name]. */
    fun case(name: String): Case = CASES.first { it.name == name }

    /**
     * Runs [cats] for [hours] from [start] at host frames of [frameMs] (or [frames], a sequence of
     * frame lengths in ms that repeats: jitter, a stall), feeding [acts] on their sub-steps as the
     * viewer does, and calls [probe] after every sub-step. Answers the world.
     */
    fun run(
        cats: List<CatProfile>,
        acts: List<OwnerAct> = emptyList(),
        hours: Double = 24.0,
        start: Long = MIDNIGHT,
        frameMs: Long = 16,
        frames: LongArray? = null,
        fidelity: CatWorld.Fidelity = CatWorld.Fidelity.LIVE,
        setup: (CatWorld) -> Unit = {},
        probe: (CatWorld, Long, Boolean) -> Unit = { _, _, _ -> },
    ): CatWorld {
        val src = FakeTimeSource(mono = 0, epoch = start)
        val clock = WorldClock(src)
        val world = CatWorld(clock, cats, fidelity)
        setup(world)
        var pending = acts.sortedBy { it.atMs }
        var next = 0
        val listener =
            WorldClock.SubStepListener { step, think ->
                val now = clock.simMs
                while (next < pending.size && pending[next].atMs <= now) world.owner(
                    pending[next++]
                )
                world.step(step, think)
                probe(world, step, think)
            }
        val end = (hours * HOUR).toLong() * NANOS_PER_MS
        var mono = 0L
        var f = 0
        while (mono < end) {
            val len = frames?.let { it[f++ % it.size] } ?: frameMs
            mono = minOf(end, mono + len * NANOS_PER_MS)
            clock.advanceTo(mono, Int.MAX_VALUE, listener)
        }
        return world
    }

    /** The day viewer's day for [case]. */
    fun viewerDay(case: Case, hours: Int = 24, frameMs: Long = 16): CatDayViewer.Day =
        CatDayViewer.simulate(MIDNIGHT, hours = hours, frameMs = frameMs, acts = case.acts) {
            CatWorld(it, case.cats)
        }
}
