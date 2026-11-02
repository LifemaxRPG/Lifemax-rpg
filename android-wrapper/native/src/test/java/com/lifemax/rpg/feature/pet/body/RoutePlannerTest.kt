package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.body.SurfaceId.BED
import com.lifemax.rpg.feature.pet.body.SurfaceId.BOX
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUCH
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUNTER
import com.lifemax.rpg.feature.pet.body.SurfaceId.CUSHION
import com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR
import com.lifemax.rpg.feature.pet.body.SurfaceId.KSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.LSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.MID
import com.lifemax.rpg.feature.pet.body.SurfaceId.POUF
import com.lifemax.rpg.feature.pet.body.SurfaceId.SILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.TOP
import org.junit.Assert.assertEquals
import org.junit.Test

/** The surface table (PET_AS_BUILT 4.1) and the route planner (petRoute, 15-runtime.js). */
class RoutePlannerTest {
    private fun route(from: SurfaceId, fromX: Float, dest: SurfaceId, x: Float) =
        RoutePlanner.route(from, fromX, dest, x)

    @Test
    fun theSurfaceTableMatchesTheWebRoom() {
        fun check(id: SurfaceId, y: Float, x0: Float, x1: Float) {
            val s = World.surface(id)
            assertEquals("$id y", y, s.y, 0f)
            assertEquals("$id x0", x0, s.x0, 0f)
            assertEquals("$id x1", x1, s.x1, 0f)
        }
        check(FLOOR, 132f, -154f, 346f)
        check(BED, 128f, 36f, 36f)
        check(SILL, 74f, 38f, 76f)
        check(MID, 100f, 146f, 158f)
        check(TOP, 64f, 150f, 158f)
        check(COUCH, 109f, -136f, -60f)
        check(COUNTER, 91f, 286f, 340f)
        check(LSILL, 73f, -124f, -72f)
        check(KSILL, 74f, 304f, 338f)
        // The props are derived by the web from their depth (PET_PROPS); worked by hand.
        check(POUF, 127f, -41f, -27f)
        check(CUSHION, 130f, -134f, -106f)
        check(BOX, 129f, 278f, 302f)
        assertEquals(45f, World.surface(POUF).z, 0f)
        assertEquals(66f, World.surface(CUSHION).z, 0f)
        assertEquals(42f, World.surface(BOX).z, 0f)
    }

    @Test
    fun theDepthProjectionMatchesTheWebRoom() {
        assertEquals(113, World.depthScalePct(45f))
        assertEquals(100, World.depthScalePct(35f))
        assertEquals(132f, World.floorY(35f), 0.001f)
        assertEquals(94, World.depthScalePct(30f))
        assertEquals(146, World.depthScalePct(70f))
        assertEquals(185, World.depthScalePct(100f))
        assertEquals(70, World.walkSpeedPct(0f))
        assertEquals(130, World.walkSpeedPct(100f))
    }

    @Test
    fun floorToFloorIsOneWalk() {
        assertEquals(listOf<PlanStep>(PlanStep.Walk(200f)), route(FLOOR, 92f, FLOOR, 200f))
        assertEquals(listOf<PlanStep>(PlanStep.Walk(346f)), route(FLOOR, 92f, FLOOR, 999f))
    }

    @Test
    fun theTreeIsReachedThroughMidThenTop() {
        assertEquals(
            listOf(PlanStep.Walk(138f), PlanStep.Hop(MID, 150f), PlanStep.Hop(TOP, 154f)),
            route(FLOOR, 92f, TOP, 154f),
        )
        assertEquals(
            listOf(PlanStep.Walk(138f), PlanStep.Hop(MID, 150f)),
            route(FLOOR, 92f, MID, 150f),
        )
        assertEquals(listOf<PlanStep>(PlanStep.Hop(MID, 150f)), route(TOP, 154f, MID, 150f))
    }

    @Test
    fun climbingDownLandsOnTheFloorFirst() {
        assertEquals(
            listOf(PlanStep.Hop(FLOOR, 60f), PlanStep.Walk(60f)),
            route(SILL, 50f, FLOOR, 60f),
        )
    }

    @Test
    fun couchAndCounterAreJumpedOntoFromTheirApproachSide() {
        assertEquals(
            listOf(PlanStep.Walk(-80f), PlanStep.Hop(COUCH, -100f)),
            route(FLOOR, 0f, COUCH, -100f),
        )
        assertEquals(
            listOf(PlanStep.Walk(282f), PlanStep.Hop(COUNTER, 300f)),
            route(FLOOR, 200f, COUNTER, 300f),
        )
    }

    @Test
    fun theWindowSillsGoViaTheirFurniture() {
        assertEquals(
            listOf(PlanStep.Walk(-80f), PlanStep.Hop(COUCH, -100f), PlanStep.Hop(LSILL, -100f)),
            route(FLOOR, 0f, LSILL, -100f),
        )
        assertEquals(listOf<PlanStep>(PlanStep.Hop(KSILL, 320f)), route(COUNTER, 300f, KSILL, 320f))
    }

    @Test
    fun stayingOnASurfaceIsAWalkAlongItExceptTheBed() {
        assertEquals(listOf<PlanStep>(PlanStep.Walk(76f)), route(SILL, 40f, SILL, 200f))
        assertEquals(emptyList<PlanStep>(), route(BED, 36f, BED, 36f))
    }

    @Test
    fun bedAndSillAreJumpedFromTheirOwnRunUpSpot() {
        assertEquals(
            listOf(PlanStep.Walk(50f), PlanStep.Hop(BED, 36f)),
            route(FLOOR, 90f, BED, 36f),
        )
        assertEquals(
            listOf(PlanStep.Walk(60f), PlanStep.Hop(SILL, 60f)),
            route(FLOOR, 90f, SILL, 60f),
        )
    }

    @Test
    fun propsAreApproachedFromTheNearSideAndTheBoxFromInFront() {
        val pouf = World.surface(POUF)
        val toPouf = route(FLOOR, 40f, POUF, -34f)
        // Coming from the right (x 40 > -34): approach from the right-hand end.
        assertEquals(PlanStep.Walk(pouf.x1 + pouf.half * 0.6f + 8f, 45f), toPouf[0])
        assertEquals(PlanStep.Hop(POUF, -34f), toPouf[1])
        val box = World.surface(BOX)
        val toBox = route(FLOOR, 200f, BOX, 290f)
        assertEquals(
            PlanStep.Walk(box.x0 - (box.half * 0.2f + 2f), 42f + World.BOX_FRONT_Z),
            toBox[0],
        )
        assertEquals(PlanStep.Hop(BOX, 290f), toBox[1])
        val toCushion = route(FLOOR, 0f, CUSHION, -120f)
        assertEquals(2, toCushion.size)
    }

    @Test
    fun leavingAPropStepsOffClearOfIt() {
        val pouf = World.surface(POUF)
        val out = route(POUF, -34f, FLOOR, 50f)
        assertEquals(PlanStep.Hop(FLOOR, pouf.x1 + pouf.half * 0.6f + 6f), out[0])
        assertEquals(PlanStep.Walk(50f), out[1])
        val couch = route(COUCH, -100f, FLOOR, 50f)
        assertEquals(PlanStep.Hop(FLOOR, -84f), couch[0])
    }

    @Test
    fun theBodyCanRouteFromWhereItStands() {
        val body = CatBody(seed = 1, startX = 92f)
        assertEquals(route(FLOOR, 92f, TOP, 154f), body.routeTo(TOP, 154f))
    }
}
