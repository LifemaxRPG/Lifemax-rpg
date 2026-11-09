package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.Feeding
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.CareFeed
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures

/**
 * The owner's Feed, end to end, as the Cat tab plays it: the page's feed command (FakePage's
 * petFeedClick: whoever is hungry is fed, each meal a `fed` cue), the projection after it (its
 * world block carries the cues) and, with [Serve.TAB], the Cat tab's own reaction to each cue
 * (`HouseStage.feed` → `CatWorldHost.ownerAct("feed:<slot>")`). [watch] then follows each fed cat
 * frame by frame on the house's own bodies: when it took up the meal, began to move, reached its
 * bowl on the floor, had eaten its time, and what it did next.
 */
internal object FeedWatch {
    /** How the meal reaches the world: the Cat tab's reaction too, or only the page's block. */
    enum class Serve {
        TAB,
        BLOCK,
    }

    /** One fed cat's meal, in seconds after the feed (-1: never). */
    data class Meal(
        val slot: Int,
        val tookUp: Double,
        val moved: Double,
        val reached: Double,
        val ate: Double,
        val next: String,
        val watchdogs: Int,
        val story: String,
    ) {
        /** Why this meal is not what the owner should see, or null when it is. */
        fun fault(startedAtBowl: Boolean): String? =
            when {
                tookUp !in 0.0..TOOK_UP_S -> "did not take up the meal"
                !startedAtBowl && moved !in 0.0..MOVED_S -> "did not get up and move"
                reached !in 0.0..REACHED_S -> "did not reach its bowl"
                ate < 0.0 -> "did not eat its meal"
                next.isEmpty() -> "did nothing after it ate"
                watchdogs > 0 -> "the watchdog stepped in"
                else -> null
            }
    }

    /** A house of two at [hour] of the fixture's day, fed 6 h ago, friends who have met. */
    fun house(hour: Int, kitten: Boolean): HouseWatch {
        val start = Math.floorDiv(WorldFixtures.NOW, DAY) * DAY + hour * HOUR
        val page =
            FakePage(start).also { p ->
                p.adopt(
                    FakePage.Cat(
                        born = start - 40 * DAY,
                        name = "Ted",
                        lastFed = start - FED_AGO,
                        stage = if (kitten) KITTEN else -1,
                    )
                )
                p.adopt(
                    FakePage.Cat(
                        born = start - 35 * DAY,
                        name = "Lily",
                        lastFed = start - FED_AGO,
                        stage = if (kitten) KITTEN + 1 else -1,
                    )
                )
                p.pair!!.rel = FRIENDS
                p.pair!!.met = true
            }
        return HouseWatch(page, FakeTimeSource(0L, start))
    }

    private var ops = 0

    /** The owner taps Feed now; the fed cats' meals as they play out (at most [MAX_S]). */
    fun feed(w: HouseWatch, serve: Serve = Serve.TAB): List<Pair<Meal, Boolean>> {
        for (c in w.page.cats) c.lastFed = minOf(c.lastFed, w.time.epoch - FED_AGO)
        val before = w.page.meals.size
        w.page.now = w.time.epoch
        val answer = w.page.send(CareFeed("feed-${++ops}"))
        check(answer.getBoolean("ok")) { "the page refused the meal: $answer" }
        val fed = w.page.meals.drop(before).map { it.first }
        w.project()
        if (serve == Serve.TAB) for (slot in fed) w.host.ownerAct("feed:$slot")
        return watch(w, fed)
    }

    /** Follows the cats [slots] from now, each meal with whether the cat began at its bowl. */
    fun watch(w: HouseWatch, slots: List<Int>): List<Pair<Meal, Boolean>> {
        val house = w.host.house!!
        val dogs = w.world.minds.map { it.runner.watchdogs }
        val from = slots.map { house.bodies[it].pose.let { p -> Triple(p.surface, p.x, p.z) } }
        val tookUp = DoubleArray(slots.size) { -1.0 }
        val moved = DoubleArray(slots.size) { -1.0 }
        val reached = DoubleArray(slots.size) { -1.0 }
        val ate = DoubleArray(slots.size) { -1.0 }
        val next = Array(slots.size) { "" }
        val story = Array(slots.size) { LinkedHashSet<String>() }
        var t = 0.0
        var frame = 0
        while (t < MAX_S && !(next.all { it.isNotEmpty() } && t > SETTLE_S)) {
            if (frame++ % PROJECT_EVERY == 0) w.project()
            w.frame(FRAME_MS)
            t += FRAME_MS / MS
            slots.forEachIndexed { k, s ->
                val m = w.world.minds[s]
                val p = house.bodies[s].pose
                val d = w.world.now(s)
                story[k] += "${d.activity} (${d.why})"
                val eating = d.activity == EAT
                if (tookUp[k] < 0 && eating) tookUp[k] = t
                val (s0, x0, z0) = from[k]
                val away = Math.abs(p.x - x0) > MOVE_PX || Math.abs(p.z - z0) > MOVE_PX
                if (moved[k] < 0 && (p.surface != s0 || away)) moved[k] = t
                val atBowl =
                    p.surface == SurfaceId.FLOOR && Math.abs(p.x - Feeding.bowlX(s)) < BOWL_PX
                if (reached[k] < 0 && eating && m.runner.atPlaceS > 0 && atBowl) reached[k] = t
                if (ate[k] < 0 && eating && atBowl && m.runner.atPlaceS >= Feeding.EAT_SECONDS)
                    ate[k] = t
                if (ate[k] >= 0 && next[k].isEmpty() && !eating) next[k] = d.activity
            }
        }
        return slots.mapIndexed { k, s ->
            val atBowl =
                from[k].first == SurfaceId.FLOOR &&
                    Math.abs(from[k].second - Feeding.bowlX(s)) < BOWL_PX
            Meal(
                s,
                tookUp[k],
                moved[k],
                reached[k],
                ate[k],
                next[k],
                w.world.minds[s].runner.watchdogs - dogs[s],
                story[k].joinToString(" > "),
            ) to atBowl
        }
    }

    /** The meals that went wrong, one line each (empty: every fed cat ate at its bowl). */
    fun faults(label: String, meals: List<Pair<Meal, Boolean>>): List<String> =
        meals.mapNotNull { (m, atBowl) ->
            m.fault(atBowl)?.let { "$label: cat ${m.slot} $it (${m.story})" }
        }

    const val HOUR = 3_600_000L
    const val DAY = 86_400_000L
    const val FRAME_MS = 50L
    private const val MS = 1000.0
    private const val FED_AGO = 6 * HOUR
    private const val KITTEN = 2
    private const val FRIENDS = 0.8
    private const val EAT = "eat"
    private const val PROJECT_EVERY = 100
    private const val MAX_S = 120.0
    private const val SETTLE_S = 20.0
    private const val MOVE_PX = 1f
    private const val BOWL_PX = 6f

    /** The bounds the owner's eye holds the cats to (seconds after the tap). */
    const val TOOK_UP_S = 3.0
    const val MOVED_S = 6.0
    const val REACHED_S = 90.0
}
