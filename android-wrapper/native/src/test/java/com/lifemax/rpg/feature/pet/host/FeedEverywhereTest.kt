package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Drive
import com.lifemax.rpg.feature.pet.brain.Social
import com.lifemax.rpg.feature.pet.brain.Spot
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.host.FeedWatch.feed
import com.lifemax.rpg.feature.pet.host.FeedWatch.house
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's "the two times I fed my cats, they didn't get up and move at all to go eat": the Feed
 * of the Cat tab, through the real host, house bodies and brain against the page's books
 * (FakePage), for both cats, grown and kittens, at night, by day and in the evening, from every
 * kind of thing a cat can be doing (asleep, resting, washing, at a window, wandering, up on a
 * perch, half way there, in a moment together, at a toy, on a parkour demo tour, after a cold start
 * and after a gap caught up): each fed cat takes up the meal at once, gets up and goes, reaches its
 * own bowl on the floor within [FeedWatch.REACHED_S], eats its time there and goes on with its day,
 * with no watchdog. Every failure of the sweep is listed at once.
 */
class FeedEverywhereTest {
    private val faults = ArrayList<String>()

    private fun check(label: String, w: HouseWatch) {
        faults += FeedWatch.faults(label, feed(w))
    }

    private fun each(body: (hour: Int, kitten: Boolean, tag: String) -> Unit) {
        for (kitten in listOf(false, true)) for (hour in HOURS) {
            body(hour, kitten, "${if (kitten) "kittens" else "grown"} at ${hour}h")
        }
    }

    private fun verdict() =
        assertTrue(
            "${faults.size} meals went wrong:\n${faults.joinToString("\n")}",
            faults.isEmpty(),
        )

    @Test
    fun aMealGetsBothCatsToTheirBowlsWhateverTheyAreDoing() {
        each { hour, kitten, tag ->
            for (m in NATURAL_MIN) {
                val w = house(hour, kitten).also { it.open() }
                w.minutes(m)
                check("$tag, $m min into the day", w)
            }
            for ((id, spot) in FORCED) for (arrived in listOf(true, false)) {
                val w = house(hour, kitten).also { it.open() }
                w.minutes(1)
                w.world.minds[0].force(id, spot, FORCE_S)
                w.world.minds[1].force(id, otherOf(spot), FORCE_S)
                if (arrived) settle(w) else repeat(MID_WALK_FRAMES) { w.frame(FeedWatch.FRAME_MS) }
                check(
                    "$tag, $id at ${spot?.id ?: "here"} ${if (arrived) "there" else "on the way"}",
                    w,
                )
            }
        }
        verdict()
    }

    @Test
    fun aMealEndsAMomentTogetherAndBothGoToEat() {
        each { hour, kitten, tag ->
            for ((kind, spot) in MOMENTS) for (afterS in MOMENT_AFTER_S) {
                val w = house(hour, kitten).also { it.open() }
                w.minutes(1)
                w.world.social.force(kind, 0, spot)
                seconds(w, afterS)
                check("$tag, $kind at ${spot?.id ?: "here"} after ${afterS}s", w)
            }
        }
        verdict()
    }

    @Test
    fun aMealReachesCatsAtAToyOnADemoTourAfterAColdStartAndAfterAGap() {
        each { hour, kitten, tag ->
            for (afterS in DEMO_AFTER_S) {
                val w = house(hour, kitten).also { it.open() }
                w.minutes(2)
                for (m in w.world.minds) {
                    m.drives[Drive.ENERGY] = RESTED
                    m.force("loaf", null, FORCE_S)
                }
                w.host.demoParkour()
                seconds(w, afterS)
                check("$tag, on a parkour demo tour after ${afterS}s", w)
            }
            for (afterS in TOY_AFTER_S) {
                val w = house(hour, kitten).also { it.open() }
                w.minutes(2)
                w.host.offerToy(TOY)
                seconds(w, afterS)
                check("$tag, at a toy after ${afterS}s", w)
            }
            var w = house(hour, kitten).also { it.open() }
            w.minutes(COLD_MIN)
            w = w.restart()
            w.open()
            check("$tag, right after a cold start", w)
            w = house(hour, kitten).also { it.open() }
            w.minutes(COLD_MIN)
            w.close()
            w.away(GAP_MIN)
            w.open()
            check("$tag, right after a ${GAP_MIN} min gap was caught up", w)
        }
        verdict()
    }

    private fun settle(w: HouseWatch) {
        var s = 0.0
        while (s < SETTLE_MAX_S && !w.world.minds.all { it.runner.atPlaceS > SETTLED_S }) {
            w.frame(FeedWatch.FRAME_MS)
            s += FeedWatch.FRAME_MS / MS
        }
    }

    private fun seconds(w: HouseWatch, s: Double) =
        repeat((s * MS / FeedWatch.FRAME_MS).toInt()) { w.frame(FeedWatch.FRAME_MS) }

    /** The other cat's place for a forced activity: somewhere else on the ground floor. */
    private fun otherOf(spot: Spot?): Spot =
        if (spot === Spots.LIVING_RUG) Spots.KITCHEN else Spots.LIVING_RUG

    private companion object {
        val HOURS = listOf(3, 13, 21)
        val NATURAL_MIN = listOf(1, 23)
        val FORCED =
            listOf(
                "sleep" to Spots.BED,
                "sleep" to Spots.COUCH_L,
                "sleep" to Spots.BOX,
                "loaf" to Spots.RUG,
                "loaf" to Spots.TREE_MID,
                "loaf" to Spots.COUNTER,
                "groom" to Spots.LIVING_RUG,
                "window watch" to Spots.SILL,
                "wander" to null,
                "parkour visit" to Spots.TREE_TOP,
                "parkour visit" to Spots.FRIDGE,
                "parkour visit" to Spots.BOOKCASE,
                "scratch" to Spots.SCRATCH_TREE,
                "knead" to Spots.CUSHION,
            )
        val MOMENTS: List<Pair<String, Spot?>> =
            listOf(
                Social.GREET to null,
                Social.CHASE to null,
                Social.GROOM to null,
                Social.NAP to Spots.COUCH_L,
                Social.STANDOFF to Spots.TREE_TOP,
                Social.SHADOW to null,
            )
        val MOMENT_AFTER_S = listOf(1.0, 6.0, 30.0)
        val DEMO_AFTER_S = listOf(1.0, 5.0, 15.0)
        val TOY_AFTER_S = listOf(1.0, 8.0)
        const val TOY = "mouse"
        const val FORCE_S = 300.0
        const val MID_WALK_FRAMES = 15
        const val SETTLE_MAX_S = 120.0
        const val SETTLED_S = 2.0
        const val RESTED = 0.9
        const val COLD_MIN = 9
        const val GAP_MIN = 90
        const val MS = 1000.0
    }
}
