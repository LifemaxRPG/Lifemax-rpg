package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the owner's care does to the cats in the room: a fed cat goes to its bowl, eats and washes
 * (the page's feed), a full one only visits, a slow blink is returned unless the cat sleeps, and
 * the autopilot picks up again afterwards.
 */
class FeedingTest {
    private val ted = HouseCat(Autopilot.seedFor("Ted", "ginger"), false)
    private val lily = HouseCat(Autopilot.seedFor("Lily", "gray"), false)

    private fun house(autopilot: Boolean = false) =
        HouseLife(listOf(ted, lily), autopilot).also {
            it.bodies[0].placeAt(-100f, World.ART_Z, SurfaceId.FLOOR, 1)
            it.bodies[1].placeAt(300f, World.ART_Z, SurfaceId.FLOOR, -1)
        }

    /**
     * The clips cat [i] plays over [seconds], in order, each once, and where it stood at the first
     * eat.
     */
    private class Seen(val clips: List<String>, val eatX: Float, val eatFacing: Int)

    private fun watch(house: HouseLife, i: Int, seconds: Int): Seen {
        val clips = ArrayList<String>()
        var eatX = Float.NaN
        var facing = 0
        repeat(seconds * 60) {
            house.step(1f / 60)
            val p = house.bodies[i].pose
            if (clips.lastOrNull() != p.clipName) clips.add(p.clipName)
            if (p.clipName == "eat" && eatX.isNaN()) {
                eatX = p.x
                facing = p.facing
            }
        }
        return Seen(clips, eatX, facing)
    }

    @Test
    fun aFedCatWalksToTheBowlEatsAndWashes() {
        val house = house()
        house.serveMeal(0)
        val seen = watch(house, 0, 25)
        assertEquals(87f, seen.eatX, 0.5f)
        assertEquals(1, seen.eatFacing)
        assertTrue(seen.clips.indexOf("eat") > 0)
        assertTrue(seen.clips.indexOf("groom") > seen.clips.indexOf("eat"))
    }

    @Test
    fun theSecondCatEatsAtTheOtherBowlFacingTheOtherWay() {
        val house = house()
        house.serveMeal(1)
        val seen = watch(house, 1, 25)
        assertEquals(121f, seen.eatX, 0.5f)
        assertEquals(-1, seen.eatFacing)
    }

    @Test
    fun bothCatsFedTogetherEatSideBySide() {
        val house = house()
        house.serveMeal(0)
        house.serveMeal(1)
        repeat(25 * 60) { house.step(1f / 60) }
        // the meal and the wash are over: they stand where they ate, 34 px apart as the page's do
        assertEquals(87f, house.bodies[0].pose.x, 0.5f)
        assertEquals(121f, house.bodies[1].pose.x, 0.5f)
    }

    @Test
    fun aCatUpOnTheSofaComesDownToEat() {
        val house = house()
        house.bodies[0].placeAt(-110f, World.ART_Z, SurfaceId.COUCH, 1)
        house.serveMeal(0)
        val seen = watch(house, 0, 30)
        assertEquals(87f, seen.eatX, 0.5f)
        assertEquals(SurfaceId.FLOOR, house.bodies[0].pose.surface)
    }

    @Test
    fun aCatThatIsFullOnlyVisitsTheBowl() {
        val house = house()
        house.visitBowl(0)
        val seen = watch(house, 0, 20)
        assertTrue(seen.eatX.isNaN())
        assertTrue("groom" in seen.clips)
        assertEquals(87f, house.bodies[0].pose.x, 0.5f)
    }

    @Test
    fun aCatThatDoesNotExistIsIgnored() {
        val one = HouseLife(listOf(ted), autopilot = false)
        one.serveMeal(1)
        one.visitBowl(2)
        assertFalse(one.slowBlink(1))
    }

    @Test
    fun anAwakeCatBlinksBackAndASleepingOneIsLeftAlone() {
        val house = house()
        assertTrue(house.slowBlink(0))
        house.bodies[1].replacePlan(1, listOf(PlanStep.Act("sleep", 30f)))
        repeat(120) { house.step(1f / 60) }
        assertFalse(house.slowBlink(1))
    }

    @Test
    fun aCatDoingNothingLooksUpAtTheOwnerWhenItBlinks() {
        val house = house()
        repeat(60) { house.step(1f / 60) }
        house.slowBlink(0)
        val seen = watch(house, 0, 2)
        assertTrue("lookup" in seen.clips)
    }

    @Test
    fun theSameCareOnTheSameDayPlaysTheSame() {
        fun day(): List<String> {
            val h = house(autopilot = true)
            repeat(10 * 60) { h.step(1f / 60) }
            h.serveMeal(0)
            val out = ArrayList<String>()
            repeat(40 * 60) {
                h.step(1f / 60)
                if (it % 60 == 0)
                    out.add(h.bodies.joinToString("|") { b -> TraceSupport.fingerprint(b.pose) })
            }
            return out
        }
        assertEquals(day(), day())
    }

    @Test
    fun theAutopilotPicksUpAgainAfterTheMeal() {
        val house = house(autopilot = true)
        house.serveMeal(0)
        val before = house.plans(0)
        repeat(60 * 60) { house.step(1f / 60) }
        assertTrue("the cat did nothing of its own after the meal", house.plans(0) > before + 1)
    }
}
