package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.PetArtResources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The capability maths: ballistics, the limits, run-ups, growth, hunger, morphs. */
class JumpCapabilityTest {
    private val adult = JumpCapability.ADULT
    private val g = Tuning.Parkour.GRAVITY

    @Test
    fun anArcIsTrueBallistics() {
        val arc = adult.arc(rise = 30f, dx = 20f, runUp = 0f)!!
        // up to the apex and down to the 30 px higher landing take exactly the flight time
        assertEquals(Ballistics.flightTime(arc.apex, 30f, g), arc.flight, 1e-4f)
        // the feet are 30 px up at touchdown: lift(T) equals the rise
        assertEquals(30f, Ballistics.lift(arc.launchSpeed, arc.flight, g), 0.05f)
        // the sideways speed covers the distance in the flight time
        assertEquals(20f, arc.vx * arc.flight, 1e-3f)
        // the apex is reached at v0 / g and is v0^2 / 2g
        assertEquals(arc.apex, arc.launchSpeed * arc.launchSpeed / (2f * g), 0.01f)
        assertEquals(arc.apex, Ballistics.lift(arc.launchSpeed, arc.launchSpeed / g, g), 0.01f)
    }

    @Test
    fun aHighJumpHangsLongerThanAHopAndADropFallsFasterThanItRose() {
        val hop = adult.arc(4f, 14f, 0f)!!
        val high = adult.arc(58f, 14f, 0f)!!
        assertTrue(high.flight > hop.flight)
        val drop = adult.arc(-50f, 14f, 0f)!!
        assertTrue(drop.fallSpeed > drop.launchSpeed)
    }

    @Test
    fun theMostAStandingCatCanRiseIsItsApexLessTheClearance() {
        val limit = adult.apexStand - Tuning.Parkour.CLEARANCE
        assertNotNull(adult.arc(limit - 0.1f, 6f, 0f))
        assertNull(adult.arc(limit + 0.5f, 6f, 0f))
        assertEquals(limit, adult.maxRise(0f), 1e-4f)
    }

    @Test
    fun aRunningStartAddsHeightAndReach() {
        val stand = adult.maxRise(0f)
        val run = adult.maxRise(Tuning.Parkour.RUN_UP_MAX_PX)
        assertTrue(run > stand)
        assertEquals(Tuning.Parkour.APEX_RUN_BONUS, run - stand, 1e-3f)
        // a gap too wide from rest is possible with a run-up
        assertNull(adult.arc(40f, 52f, 0f))
        assertNotNull(adult.arc(40f, 52f, Tuning.Parkour.RUN_UP_MAX_PX))
        val needed = adult.runUpFor(40f, 52f, 100f)
        assertTrue(needed >= Tuning.Parkour.RUN_UP_MIN_PX && needed <= Tuning.Parkour.RUN_UP_MAX_PX)
        assertNotNull(adult.arc(40f, 52f, needed + 0.01f))
        // and none when there is no room to run
        assertEquals(-1f, adult.runUpFor(40f, 52f, 4f), 0f)
    }

    @Test
    fun noRunUpIsAskedForWhenTheCatCanDoItStanding() {
        assertEquals(0f, adult.runUpFor(23f, 20f, 100f), 0f)
    }

    @Test
    fun aRunUpGainsSpeedFromRestAtTheRunAcceleration() {
        val v = adult.runSpeed(10f)
        assertEquals(Math.sqrt(2.0 * Tuning.Parkour.RUN_ACCEL * 10.0).toFloat(), v, 1e-3f)
        assertEquals(Tuning.Parkour.RUN_SPEED, adult.runSpeed(1000f), 0f)
        assertEquals(0f, adult.runSpeed(0f), 0f)
    }

    @Test
    fun aDropIsSafeUpToTheMaxAndNotBeyond() {
        assertNotNull(adult.arc(-adult.maxDrop + 1f, 10f, 0f))
        assertNull(adult.arc(-adult.maxDrop - 1f, 10f, 0f))
        // from the fridge: the counter (60) but not the floor (101)
        assertNotNull(adult.arc(-60f, 20f, 0f))
        assertNull(adult.arc(-101f, 20f, 0f))
    }

    @Test
    fun noFlightIsLongerThanTheReachLimit() {
        assertNull(adult.arc(0f, adult.reachMax + 1f, Tuning.Parkour.RUN_UP_MAX_PX))
        assertNotNull(adult.arc(0f, adult.reachMax - 1f, Tuning.Parkour.RUN_UP_MAX_PX))
    }

    @Test
    fun effortIsTheShareOfTheCatsBest() {
        assertTrue(adult.arc(4f, 14f, 0f)!!.effort < 0.3f)
        assertTrue(adult.arc(adult.maxRise(0f) - 0.2f, 6f, 0f)!!.effort > 0.9f)
    }

    @Test
    fun kittensJumpLowerInProportionToTheirGrowthScale() {
        val growth = PetArtResources.load().growth
        val scales = Tuning.Parkour.STAGE_SCALE
        assertEquals(6, scales.size)
        var last = 0f
        for (stage in 0..5) {
            // the table repeats growth.json; the web's sx
            assertEquals(growth.scale(stage).toFloat(), scales[stage], 1e-4f)
            val cap = JumpCapability.forStage(stage)
            assertEquals(Tuning.Parkour.APEX_STAND * scales[stage], cap.apexStand, 1e-3f)
            assertTrue("stage $stage jumps higher than ${stage - 1}", cap.apexStand > last)
            last = cap.apexStand
            assertTrue(cap.apexStand < adult.apexStand)
            assertTrue(cap.vxStand < adult.vxStand)
            assertTrue(cap.maxDrop < adult.maxDrop)
            assertEquals(scales[stage], cap.sizeScale, 1e-6f)
        }
        // a grown cat, or any stage outside 0..5
        assertEquals(adult.apexStand, JumpCapability.forStage(-1).apexStand, 0f)
        assertEquals(adult.apexStand, JumpCapability.forStage(6).apexStand, 0f)
    }

    @Test
    fun aWeakCatJumpsLittleAndDoesNotRunUp() {
        val weak = JumpCapability.of(1f, weak = true)
        assertEquals(adult.apexStand * Tuning.Parkour.WEAK_SHARE, weak.apexStand, 1e-3f)
        assertEquals(adult.maxDrop * Tuning.Parkour.WEAK_SHARE, weak.maxDrop, 1e-3f)
        assertEquals(0f, weak.runCarry, 0f)
        assertEquals(0f, weak.runSpeed(30f), 0f)
        assertEquals(weak.apexStand, weak.apexLimit(30f), 1e-4f)
        assertTrue(weak.weak)
        // the counter (41 up) is out of reach for a hungry cat, a sofa seat (23) is not
        assertNull(weak.arc(41f, 20f, 0f))
        assertNotNull(weak.arc(23f, 20f, 0f))
    }

    @Test
    fun theRigsMorphsScaleHeightAndReach() {
        val longLegs = JumpCapability.of(1f, legLength = 1.3f)
        val shortLegs = JumpCapability.of(1f, legLength = 0.8f)
        assertTrue(longLegs.apexStand > adult.apexStand)
        assertTrue(shortLegs.apexStand < adult.apexStand)
        val longBody = JumpCapability.of(1f, bodyLength = 1.3f)
        assertTrue(longBody.reachMax > adult.reachMax)
        // 1.0 is neutral, and a wild morph is held to the clamp
        assertEquals(
            adult.apexStand,
            JumpCapability.of(1f, bodyLength = 1f, legLength = 1f).apexStand,
            0f,
        )
        val wild = JumpCapability.of(1f, legLength = 9f)
        assertEquals(
            JumpCapability.of(1f, legLength = Tuning.Parkour.MORPH_MAX).apexStand,
            wild.apexStand,
            0f,
        )
    }

    @Test
    fun tirednessAndMoodAreSmallHooks() {
        val tired = JumpCapability.of(1f, fatigue = 1f)
        assertEquals(adult.apexStand * (1f - Tuning.Parkour.FATIGUE_LOSS), tired.apexStand, 1e-3f)
        val happy = JumpCapability.of(1f, mood = 1f)
        assertTrue(happy.apexStand > adult.apexStand)
        assertTrue(happy.apexStand < adult.apexStand * 1.1f)
    }

    @Test
    fun theRoomsRungsSitWhereTheNumbersWereMeantToPutThem() {
        // the geometry the capability was tuned against (feet rows; floor 132 at the art depth)
        fun rise(id: SurfaceId) = World.floorY(World.ART_Z) - World.surface(id).y
        val standing = adult.maxRise(0f)
        val best = adult.maxRise(Tuning.Parkour.RUN_UP_MAX_PX)
        assertTrue(rise(SurfaceId.SILL) <= standing)
        assertTrue(rise(SurfaceId.COUNTER) < standing)
        assertTrue("tree top out of reach from the floor", rise(SurfaceId.TOP) > best)
        assertTrue("fridge top out of reach from the floor", rise(SurfaceId.FRIDGE) > best)
        // counter to fridge is within the cat's best but near the top of it
        val counterToFridge = World.surface(SurfaceId.COUNTER).y - World.surface(SurfaceId.FRIDGE).y
        assertTrue(counterToFridge <= best)
        assertTrue(counterToFridge > standing * 0.85f)
    }
}
