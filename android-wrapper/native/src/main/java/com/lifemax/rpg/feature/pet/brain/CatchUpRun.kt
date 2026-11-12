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
        // the world was frozen for the gap: what each cat was doing began that much earlier, so a
        // nap the app slept through is over in the story instead of starting anew
        val start =
            world.export().let { s ->
                s.copy(
                    cats =
                        s.cats.mapValues { (_, c) ->
                            c.copy(actAt = (c.actAt - gapMs).coerceAtLeast(0L))
                        }
                )
            }
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
        fast.flush()
        val end = fast.export()
        world.restore(end)
        // what the two did together in the story is told to the page as it happened
        fast.takeReports().forEach(world::report)
        world.social.adoptMet(fast.social) // a first meeting in the story is not offered again
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
        val shared = fast.social.events.map { it.kind }
        return CatchUp(places, tally.lines(world.names, PairLines.line(shared, world.names)))
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

        /**
         * The first cat's main doing, the second's, then what the two did together ([pair]), else
         * the first's next doing if it filled a share.
         */
        fun lines(names: List<String>, pair: String?): List<String> {
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
            if (pair != null) out += pair
            else if (next != null && share(0, next.second) >= CatchUpTuning.SECOND_LINE_SHARE)
                out += line(names[0], next.first, share(0, next.second))
            return out.take(CatchUpTuning.LINES)
        }

        private fun share(slot: Int, n: Int): Double =
            if (totals[slot] == 0) 0.0 else n.toDouble() / totals[slot]

        private fun line(name: String, what: Pair<String, String>, share: Double): String {
            val verb = VERBS[what.first] ?: what.first
            val where = where(what.second)
            val how = if (share >= CatchUpTuning.MOST_SHARE) " most of the time" else " for a while"
            return "$name $verb$where$how"
        }
    }

    /**
     * Where a doing happened, as the story says it: nothing for the floor, the words of the rooms
     * that open with the cats' bond (a sun patch is sat in, a sink is climbed), else "on the
     * place". A place of a room that is not open is never one a cat chose ([Spot.open]).
     */
    internal fun where(place: String): String =
        when {
            place in NOWHERE -> ""
            place.startsWith(BEHIND) -> " $place"
            else -> NEW_ROOMS[place] ?: " on the $place"
        }

    private val NEW_ROOMS =
        mapOf(
            "sun patch" to " in the sun patch",
            "sun bench" to " on the sunroom bench",
            "study shelf" to " up on the study shelf",
            "tub rim" to " on the rim of the tub",
            "sink" to " up on the sink",
        )

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
            "play" to "played",
            "explore" to "had a look round",
            "zoomies" to "raced about",
            "solo play" to "played",
            "make way" to "moved over",
            "greet" to "touched noses",
            "chase" to "played chase",
            "groom each other" to "groomed the other",
            "settle in" to "settled in",
            "curl up" to "napped curled up",
            "standoff" to "had a standoff",
            "shadow" to "followed the other about",
        )
}

/** The catch-up story's line for what two cats did together while nobody watched. */
internal object PairLines {
    /**
     * One line for the moments of [kinds] (the director's log, oldest first) between [names]: the
     * warmest of them (a nap together first, a hiss last), or null when they shared none.
     */
    fun line(kinds: List<String>, names: List<String>): String? {
        if (names.size < 2) return null
        val a = names[0]
        val b = names[1]
        val pick = ORDER.firstOrNull { it in kinds } ?: return null
        return when (pick) {
            Social.NAP -> "They napped together"
            Social.GROOM -> "$a and $b groomed each other"
            Social.CHASE -> "$a and $b played chase"
            Social.GREET -> "$a and $b touched noses"
            Social.STANDOFF -> "$a and $b had a standoff"
            else -> "$a and $b kept a polite distance"
        }
    }

    private val ORDER =
        listOf(Social.NAP, Social.GROOM, Social.CHASE, Social.GREET, Social.STANDOFF, Social.HISS)
}

/** How the offline catch-up is played and told. */
internal object CatchUpTuning {
    /**
     * The longest stretch played (the end of a longer gap; the host caps a gap at a day). Bounded
     * by its cost: the fast world plays about twenty minutes of two cats in 45 ms on a desktop JVM
     * (cats pick something new every half minute now, roaming: an hour was 135 ms), and the
     * catch-up runs on the frame that reopens the room.
     */
    const val SIM_MAX_MS = 1_200_000L

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
