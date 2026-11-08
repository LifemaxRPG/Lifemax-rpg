package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The developer parkour demo end to end, through the real host, house bodies and brain against the
 * page's books (FakePage): the button's tap (`CatWorldHost.demoParkour`) sends one awake cat on a
 * parkour tour of at least two jumps that ends cleanly in bounded time, and the page hears nothing
 * of it (no command, no game counted, no care counter moved).
 */
class DemoParkourEndToEndTest {
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
            }
        val time = FakeTimeSource(0L, WorldFixtures.NOW + HOUR) // 13:00
        return HouseWatch(page, time).also { it.open() }
    }

    @Test
    fun aTapSendsACatOnATourOfSeveralJumpsThatEndsCleanlyAndTellsThePageNothing() {
        val w = watch()
        w.minutes(2)
        for (m in w.world.minds) {
            m.drives[com.lifemax.rpg.feature.pet.brain.Drive.ENERGY] = RESTED
        }
        val sentBefore = w.port.sent.size
        val pageBefore = w.page.cats.map { Triple(it.playsToday, it.petsToday, it.toyCredit.size) }
        val answer = w.host.demoParkour()
        assertNotNull(answer)
        if (!answer!!.played) {
            // a cat that happens to be asleep or eating at 13:00 says so; make both ready and ask
            for (m in w.world.minds) m.force("loaf", null, LOAF_S)
        }
        val a = if (answer.played) answer else w.host.demoParkour()!!
        assertTrue("a cat goes: ${a.message}", a.played)
        val slot = a.slot
        val body = w.host.house!!.bodies[slot]
        var surface = body.pose.surface
        var jumps = 0
        var frames = 0
        var over = false
        while (frames++ < MAX_FRAMES) {
            if (frames % HouseWatch.PROJECT_EVERY.toInt() == 0) w.project()
            w.frame()
            if (body.pose.surface != surface) {
                jumps++
                surface = body.pose.surface
            }
            if (!w.world.minds[slot].touring) {
                over = true
                break
            }
        }
        assertTrue("at least two jumps (saw $jumps in $frames frames)", jumps >= 2)
        assertTrue("the tour ended in bounded time", over)
        assertEquals("no watchdog had to step in", 0, w.world.minds[slot].runner.watchdogs)
        // a few seconds more: nothing is stuck, the cat goes on with its day
        repeat(SETTLE_FRAMES) {
            if (it % HouseWatch.PROJECT_EVERY.toInt() == 0) w.project()
            w.frame()
        }
        assertFalse(w.world.minds[slot].touring)
        assertTrue(
            "the page was sent nothing",
            w.port.sent.size == sentBefore ||
                w.port.sent.none { it.first == "play" || it.first == "demo" },
        )
        assertEquals(
            "no care counter or game moved",
            pageBefore,
            w.page.cats.map { Triple(it.playsToday, it.petsToday, it.toyCredit.size) },
        )
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val RESTED = 0.9
        const val LOAF_S = 600.0
        const val MAX_FRAMES = 7_200 // six minutes of 50 ms frames
        const val SETTLE_FRAMES = 200
    }
}
