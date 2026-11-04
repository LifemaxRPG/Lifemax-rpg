package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.FakePage
import com.lifemax.rpg.feature.pet.world.FakeTimeSource
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Native Play end to end (pet.play.v1): the owner tosses a toy from the Cat tab's tray into the
 * shared room; the real brain sends a willing cat, which picks it up, carries it, tosses and chases
 * it and sets it down, with the house's own toy field and bodies; when the game ends the page hears
 * `play` and counts it by its own rules (FakePage plays the page's books). A cat fast asleep does
 * not stir, and the answer says so instead of pretending.
 */
class OwnerPlayTest {
    private fun watch(hour: Int): HouseWatch {
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
        val time = FakeTimeSource(0L, WorldFixtures.NOW + (hour - NOON) * HOUR)
        return HouseWatch(page, time).also { it.open() }
    }

    @Test
    fun aTossedToyIsPickedUpCarriedTossedAndTheGameIsCountedByThePage() {
        val w = watch(DAY_H)
        w.minutes(2)
        // both cats awake and rested: someone comes
        for (m in w.world.minds) {
            m.drives[com.lifemax.rpg.feature.pet.brain.Drive.ENERGY] = RESTED
        }
        val answer = w.host.offerToy("mouse")
        assertNotNull(answer)
        assertTrue("a cat comes: ${answer!!.message}", answer.played)
        assertEquals("", answer.message)
        val slot = answer.slot
        val toy = w.world.minds[slot].toy!!
        assertEquals(SurfaceId.FLOOR, toy.surface)
        val body = w.host.house!!.bodies[slot]
        assertEquals("play", w.world.now(slot).activity)
        var carried = false
        var moved = false
        val x0 = toy.x
        var frames = 0
        while (frames++ < MAX_GAME_FRAMES && w.world.now(slot).activity == "play") {
            if (frames % HouseWatch.PROJECT_EVERY.toInt() == 0) w.project()
            w.frame()
            if (body.heldToy === toy) carried = true
            if (Math.abs(toy.x - x0) > MOVED_PX) moved = true
        }
        assertTrue("the cat carried the toy in its mouth", carried)
        assertTrue("the toy went somewhere", moved)
        assertFalse("the game ended", w.world.now(slot).activity == "play")
        assertTrue("set down at the end", body.heldToy == null)
        // the report reaches the page (through the world's outbox) and the page counts it
        repeat(REPORT_FRAMES) {
            if (it % HouseWatch.PROJECT_EVERY.toInt() == 0) w.project()
            w.frame()
        }
        assertTrue(w.port.sent.any { it.first == "play" })
        val cat = w.page.cats[slot]
        assertEquals("one game counted", 1, cat.playsToday)
        assertEquals(1.0, cat.toyCredit["mouse"]!!, 0.0)
        // the toy lies in the room, in the saved world
        assertTrue(w.host.snapshotNow()!!.toys.any { it.id == "mouse" && it.by == null })
    }

    @Test
    fun aCatFastAsleepDoesNotStirAndSaysSo() {
        val w = watch(NIGHT_H)
        w.minutes(DEEP_MIN)
        assertTrue("both asleep at 2:30", w.world.minds.all { it.runner.activity.asleep })
        val answer = w.host.offerToy("ball_a")!!
        assertFalse(answer.played)
        assertTrue(answer.message, answer.message.contains("sleep"))
        assertTrue(answer.message, answer.message.startsWith("Ted"))
        // nothing is reported for a toy nobody came to
        w.minutes(2)
        assertTrue(w.port.sent.none { it.first == "play" })
        assertEquals(0, w.page.cats.sumOf { it.playsToday })
    }

    private companion object {
        const val HOUR = 3_600_000L
        const val DAY = 86_400_000L
        const val NOON = 12
        const val DAY_H = 13
        const val NIGHT_H = 2
        const val DEEP_MIN = 30
        const val RESTED = 0.9
        const val MAX_GAME_FRAMES = 12_000 // ten minutes of 50 ms frames
        const val REPORT_FRAMES = 400
        const val MOVED_PX = 10f
    }
}
