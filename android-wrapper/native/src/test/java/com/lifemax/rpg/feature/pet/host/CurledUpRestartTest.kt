package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Social
import com.lifemax.rpg.feature.pet.brain.Spots
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two cats curled up together on the sofa in the real host (the world restored from the page's
 * stored blob, then handed the house's bodies with `bind`) go on lying together: after a cold
 * start, and after the app was closed a few minutes and the gap caught up.
 */
class CurledUpRestartTest {
    private fun watch(): HouseWatch {
        val page =
            FakePage(WorldFixtures.NOW).also { p ->
                p.adopt(
                    FakePage.Cat(
                        born = WorldFixtures.NOW - 40 * DAY,
                        name = "Ted",
                        lastFed = WorldFixtures.NOW - HOUR,
                    )
                )
                p.adopt(
                    FakePage.Cat(
                        born = WorldFixtures.NOW - 35 * DAY,
                        name = "Lily",
                        lastFed = WorldFixtures.NOW - HOUR,
                    )
                )
                p.pair!!.rel = REL
                p.pair!!.met = true
            }
        return HouseWatch(page, FakeTimeSource(0L, WorldFixtures.NOW + HOUR)).also { it.open() }
    }

    private fun curled(w: HouseWatch): Boolean =
        w.world.sharing() && (0..1).all { w.world.now(it).activity == "curl up" }

    @Test
    fun twoCatsCurledUpStayTogetherThroughAColdStartAndACatchUp() {
        var w = watch()
        w.minutes(1)
        assertTrue(w.world.social.force(Social.NAP, 0, Spots.COUCH_L))
        var m = 0
        while (!curled(w) && m++ < SETTLE_MIN) w.minutes(1)
        assertTrue("they curled up on the sofa", curled(w))
        w.minutes(2) // the world is stored with both in the nap

        w = w.restart()
        w.open()
        w.frame()
        assertTrue("after a cold start: ${w.world.now(0)} | ${w.world.now(1)}", curled(w))
        w.minutes(1)
        assertTrue("still together a minute later", curled(w))
        assertEquals(0, w.world.minds.sumOf { it.runner.watchdogs })

        w.close()
        w.away(GAP_MIN)
        w.open()
        w.frame()
        assertTrue("after a caught-up gap: ${w.world.now(0)} | ${w.world.now(1)}", curled(w))
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val REL = 0.8
        const val SETTLE_MIN = 6
        const val GAP_MIN = 6
    }
}
