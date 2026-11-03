package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldClock

/**
 * The offline catch-up of plan 2.6: the same cats, minds and selector played in
 * [CatWorld.Fidelity.FAST] over the gap the app was not watching (at most
 * [CatchUpTuning.SIM_MAX_MS], its end), on a clock of their own that ends where the world's is now.
 * The live world then takes the end state (the cats are where the story left them, a declared cut)
 * and the story is told in up to three lines from where each cat spent most of the time. A pure
 * function of the world's state, its clock and the gap: the same reopen tells the same story.
 */
internal object CatchUpRun {
    fun run(world: CatWorld, clock: WorldClock, gapMs: Long): CatchUp {
        val simMs = gapMs.coerceIn(0L, CatchUpTuning.SIM_MAX_MS)
        if (simMs < CatchUpTuning.MIN_MS) return CatchUp.NONE
        val start = world.export()
        val time = FakeTimeSource(0L, clock.epochMs() - clock.clockOffsetMs - simMs)
        val fastClock = WorldClock(time, clock.zoneOffsetMin, clock.clockOffsetMs)
        val fast = world.fastCopy(fastClock)
        fast.restore(start)
        val tally = Tally(fast.names.size)
        var thinks = 0L
        // a fast world's sub-steps move nothing (its executors keep time by quanta): only the
        // think quanta are played
        fastClock.advanceTo(simMs * NANOS_PER_MS, Int.MAX_VALUE) { step, think ->
            if (!think) return@advanceTo
            fast.step(step, true)
            if (thinks++ % CatchUpTuning.SAMPLE_EVERY == 0L) {
                for (slot in fast.names.indices) tally.add(slot, fast.now(slot))
            }
        }
        val end = fast.export()
        world.restore(end)
        val places =
            world.minds.map { m ->
                CatchPlace(
                    m.slot,
                    m.exec.surface.key,
                    m.exec.x.toDouble(),
                    m.exec.z.toDouble(),
                    m.exec.facing,
                    m.runner.activity.id,
                )
            }
        return CatchUp(places, tally.lines(world.names))
    }

    /** Where each cat spent its samples: activity and place, counted. */
    private class Tally(cats: Int) {
        private val counts = Array(cats) { LinkedHashMap<Pair<String, String>, Int>() }
        private val totals = IntArray(cats)

        fun add(slot: Int, d: Doing) {
            val key = d.activity to d.place
            counts[slot][key] = (counts[slot][key] ?: 0) + 1
            totals[slot]++
        }

        /** The first cat's main doing, the second's, then the first's next if it filled a share. */
        fun lines(names: List<String>): List<String> {
            val ranked =
                counts.map { m ->
                    m.entries.sortedByDescending { it.value }.map { it.key to it.value }
                }
            val out = ArrayList<String>()
            for (slot in ranked.indices) {
                val top = ranked[slot].firstOrNull() ?: continue
                out += line(names[slot], top.first, share(slot, top.second))
            }
            val next = ranked.firstOrNull()?.getOrNull(1)
            if (next != null && share(0, next.second) >= CatchUpTuning.SECOND_LINE_SHARE)
                out += line(names[0], next.first, share(0, next.second))
            return out.take(CatchUpTuning.LINES)
        }

        private fun share(slot: Int, n: Int): Double =
            if (totals[slot] == 0) 0.0 else n.toDouble() / totals[slot]

        private fun line(name: String, what: Pair<String, String>, share: Double): String {
            val verb = VERBS[what.first] ?: what.first
            val place = what.second
            val where =
                when {
                    place in NOWHERE -> ""
                    place.startsWith(BEHIND) -> " $place"
                    else -> " on the $place"
                }
            val how = if (share >= CatchUpTuning.MOST_SHARE) " most of the time" else " for a while"
            return "$name $verb$where$how"
        }
    }

    private const val NANOS_PER_MS = 1_000_000L
    private const val BEHIND = "behind"
    private val NOWHERE = setOf("here", "floor")

    /** The past tense of each activity of the catalogue, for the story. */
    private val VERBS =
        mapOf(
            "sleep" to "slept",
            "waking routine" to "stretched",
            "loaf" to "lounged",
            "groom" to "washed",
            "knead" to "kneaded",
            "wander" to "wandered about",
            "window watch" to "watched the window",
            "parkour visit" to "climbed about",
            "scratch" to "scratched",
            "eat" to "ate",
            "beg" to "waited for food",
        )
}

/** How the offline catch-up is played and told. */
internal object CatchUpTuning {
    /**
     * The longest stretch played (the end of a longer gap; the host caps a gap at a day). Bounded
     * by its cost: the fast world plays about an hour of two cats in 40 ms on a desktop JVM, and
     * the catch-up runs on the frame that reopens the room.
     */
    const val SIM_MAX_MS = 3_600_000L

    /** A gap shorter than this is not played. */
    const val MIN_MS = 60_000L

    /** Think quanta between two samples of what the cats do (one a minute). */
    const val SAMPLE_EVERY = 240L

    /** A doing that filled this share of the time is "most of the time". */
    const val MOST_SHARE = 0.5

    /** The first cat's second doing is told when it filled this share. */
    const val SECOND_LINE_SHARE = 0.25

    /** Lines of the story at most. */
    const val LINES = 3
}
