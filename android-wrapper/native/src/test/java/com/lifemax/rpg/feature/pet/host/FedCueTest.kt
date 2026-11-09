package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.host.FeedWatch.Serve
import com.lifemax.rpg.feature.pet.host.FeedWatch.feed
import com.lifemax.rpg.feature.pet.host.FeedWatch.house
import com.lifemax.rpg.feature.pet.world.CareFeed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The owner's meals that never reached the bowl, one regression each:
 * - two cats fed together walk the same way to their bowls side by side; the overlap guard took
 *   that for one lying on the other and sent one, then the other, away from its meal;
 * - a cat fed in the air (half way up onto the sofa, a perch, the tree) had its way to the bowl
 *   planned from where it took off, and walked it on what it landed on: it ate on the sofa;
 * - a meal the page served while the Cat tab was not the screen showing the world (the Overview
 *   card, the page's own Cat view, the voice), or just before the room appeared, never reached the
 *   cats: only the Cat tab's reaction fed them; now the world plays the block's `fed` cues itself,
 *   once each, and not an old one when a view opens.
 */
class FedCueTest {
    private fun assertAte(label: String, meals: List<Pair<FeedWatch.Meal, Boolean>>) {
        val bad = FeedWatch.faults(label, meals)
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
    }

    @Test
    fun twoCatsFedTogetherWalkSideBySideToTheirBowlsAndBothEat() {
        for (kitten in listOf(true, false)) {
            val w = house(NIGHT, kitten).also { it.open() }
            w.minutes(1)
            w.world.minds[0].force("sleep", Spots.BEHIND_CUSHION, LONG_S)
            w.world.minds[1].force("sleep", Spots.COUCH_L, LONG_S)
            w.minutes(1)
            val meals = feed(w)
            assertEquals(2, meals.size)
            assertAte("kitten=$kitten", meals)
            for ((m, _) in meals) assertFalse(m.story, m.story.contains("make way"))
        }
    }

    @Test
    fun aCatFedInTheAirEatsAtItsBowlNotOnWhatItLandsOn() {
        for ((spot, kitten) in listOf(Spots.COUCH_R to false, Spots.TREE_MID to true)) {
            val w = house(DAY_H, kitten).also { it.open() }
            w.minutes(1)
            w.world.minds[1].force("loaf", Spots.KITCHEN, LONG_S)
            w.world.minds[0].force("loaf", Spots.RUG, LONG_S)
            var frames = 0
            while (w.world.minds[0].runner.atPlaceS < 1.0 && frames++ < MAX_FRAMES) w.frame(
                FeedWatch.FRAME_MS
            )
            w.world.minds[0].force("loaf", spot, LONG_S)
            val body = w.host.house!!.bodies[0]
            frames = 0
            while (!body.inFlight && frames++ < MAX_FRAMES) w.frame(FeedWatch.FRAME_MS)
            assertTrue("the cat jumps up to ${spot.id}", body.inFlight)
            val meals = feed(w).filter { it.first.slot == 0 }
            assertAte("fed in the air on the way to ${spot.id}", meals)
            assertEquals(SurfaceId.FLOOR, body.pose.surface)
        }
    }

    @Test
    fun aMealThePageServedReachesTheWorldWithoutTheCatTab() {
        // the Overview card shows the world; the meal came from the page's own Cat view
        val w = house(DAY_H, false).also { it.open() }
        w.minutes(2)
        assertAte("served by the page, seen on the Overview card", feed(w, Serve.BLOCK))
    }

    @Test
    fun aMealServedJustBeforeTheRoomOpensIsEatenAndAnOldOneIsNot() {
        val w = house(DAY_H, false).also { it.open() }
        w.minutes(2)
        w.close()
        // the meal is served while no room shows; the room opens a moment later: they go and eat
        w.page.now = w.time.epoch
        w.page.send(CareFeed("served-while-closed"))
        w.project()
        repeat(FRESH_FRAMES) { w.frame(FeedWatch.FRAME_MS) }
        w.open()
        assertAte("the room opened for the meal", FeedWatch.watch(w, listOf(0, 1)))

        // a meal served long before the room opens has been eaten: nobody walks to the bowl now
        w.minutes(2)
        w.close()
        for (c in w.page.cats) c.lastFed = w.time.epoch - 6 * FeedWatch.HOUR
        w.page.now = w.time.epoch
        w.page.send(CareFeed("served-long-ago"))
        w.project()
        w.away(OLD_MEAL_MIN)
        w.open()
        repeat(OLD_FRAMES) {
            w.frame(FeedWatch.FRAME_MS)
            for (s in 0..1) assertFalse("an old meal replayed", w.world.now(s).why == "fed")
        }
    }

    @Test
    fun aWorldStartedByTheMealsOwnBlockStillPlaysIt() {
        // a cold start: the page served the meal, and its block is the first the host sees
        val w = house(DAY_H, false)
        w.page.now = w.time.epoch
        w.page.send(CareFeed("first-block"))
        w.open()
        assertAte("the first block carried the meal", FeedWatch.watch(w, listOf(0, 1)))
    }

    @Test
    fun aReloadedPageCountsItsMealsFromOneAgainAndTheyStillPlay() {
        val w = house(DAY_H, false).also { it.open() }
        w.minutes(1)
        assertAte("before the reload", feed(w, Serve.BLOCK))
        w.minutes(1)
        w.page.reload()
        w.minutes(1)
        assertAte("after the reload", feed(w, Serve.BLOCK))
    }

    @Test
    fun theCatTabAndTheBlockTogetherServeOneMealNotTwo() {
        val w = house(DAY_H, false).also { it.open() }
        w.minutes(2)
        val meals = feed(w, Serve.TAB)
        assertAte("one tap", meals)
        repeat(AFTER_FRAMES) {
            w.frame(FeedWatch.FRAME_MS)
            for (s in 0..1) assertFalse("a second meal", w.world.now(s).activity == "eat")
        }
    }

    private companion object {
        const val NIGHT = 3
        const val DAY_H = 13
        const val LONG_S = 600.0
        const val MAX_FRAMES = 600
        const val FRESH_FRAMES = 20
        const val OLD_MEAL_MIN = 30
        const val OLD_FRAMES = 200
        const val AFTER_FRAMES = 1200
    }
}
