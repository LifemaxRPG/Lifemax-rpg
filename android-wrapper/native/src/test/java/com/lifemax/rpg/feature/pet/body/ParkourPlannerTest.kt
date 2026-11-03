package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.body.SurfaceId.BED
import com.lifemax.rpg.feature.pet.body.SurfaceId.BOOKCASE
import com.lifemax.rpg.feature.pet.body.SurfaceId.BOX
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUCH
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUNTER
import com.lifemax.rpg.feature.pet.body.SurfaceId.CUSHION
import com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR
import com.lifemax.rpg.feature.pet.body.SurfaceId.FRIDGE
import com.lifemax.rpg.feature.pet.body.SurfaceId.KSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.LSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.MID
import com.lifemax.rpg.feature.pet.body.SurfaceId.POUF
import com.lifemax.rpg.feature.pet.body.SurfaceId.SILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.TABLE
import com.lifemax.rpg.feature.pet.body.SurfaceId.TOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The surface table (PET_AS_BUILT 4.1), the reachability graph built from geometry and capability,
 * and the planner: what a cat can reach, by which chain, and why.
 */
class ParkourPlannerTest {
    private val adult = JumpCapability.ADULT

    private fun chain(
        cap: JumpCapability,
        goal: SurfaceId,
        from: SurfaceId = FLOOR,
    ): List<SurfaceId>? =
        ParkourPlanner.plan(
                cap,
                from,
                World.surface(from).let { (it.x0 + it.x1) / 2f },
                goal,
                World.surface(goal).x0,
            )
            ?.leaps
            ?.map { it.to }

    // ---------------------------------------------------------------- the room

    @Test
    fun theSurfaceTableMatchesTheWebRoomAndTheNewSurfaces() {
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
        // the props are derived by the web from their depth (PET_PROPS); worked by hand
        check(POUF, 127f, -41f, -27f)
        check(CUSHION, 130f, -134f, -106f)
        check(BOX, 129f, 278f, 302f)
        assertEquals(45f, World.surface(POUF).z, 0f)
        assertEquals(66f, World.surface(CUSHION).z, 0f)
        assertEquals(42f, World.surface(BOX).z, 0f)
        // the parkour surfaces: the fridge top (piece row 30), the bookcase top (row 32), the side
        // table (row 104), read from the room layers (see Solids)
        check(FRIDGE, 31f, 214f, 238f)
        check(BOOKCASE, 33f, 108f, 136f)
        check(TABLE, 105f, -22f, -12f)
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
    fun theRoomsAreSplitWhereTheWebSplitsThem() {
        assertEquals(0, World.roomOf(-5f))
        assertEquals(1, World.roomOf(-4f))
        assertEquals(1, World.roomOf(195f))
        assertEquals(2, World.roomOf(196f))
    }

    // ---------------------------------------------------------------- the fridge rule

    @Test
    fun theFridgeTopIsReachedOnlyThroughTheCounter() {
        // the heights: floor to counter is within the cat's reach, counter to fridge too, and
        // floor to fridge is not
        assertNotNull(ParkourPlanner.edge(adult, FLOOR, COUNTER))
        assertNotNull(ParkourPlanner.edge(adult, COUNTER, FRIDGE))
        assertNull(ParkourPlanner.edge(adult, FLOOR, FRIDGE))
        assertEquals(listOf(COUNTER, FRIDGE), chain(adult, FRIDGE))
        // and nothing else leads to the fridge: the counter is the only way up
        for (a in SurfaceId.entries) {
            if (a == COUNTER || a == FRIDGE) continue
            assertNull("$a -> fridge", ParkourPlanner.edge(adult, a, FRIDGE))
        }
        // not even the kitchen's window sill, which is reached through the counter too
        assertEquals(listOf(COUNTER, KSILL), chain(adult, KSILL))
    }

    @Test
    fun theFridgeRuleHoldsForEveryKittenStageAndForWeakCats() {
        for (stage in 0..5) {
            val cap = JumpCapability.forStage(stage)
            assertNull("kitten $stage floor -> fridge", ParkourPlanner.edge(cap, FLOOR, FRIDGE))
            val viaCounter = chain(cap, FRIDGE)
            if (viaCounter != null) {
                assertEquals("kitten $stage", listOf(COUNTER, FRIDGE), viaCounter)
            }
        }
        // only the biggest kittens manage the last 60 px; the rest stay on the counter or below
        assertNull(chain(JumpCapability.forStage(0), FRIDGE))
        assertNull(chain(JumpCapability.forStage(4), FRIDGE))
        assertEquals(listOf(COUNTER, FRIDGE), chain(JumpCapability.forStage(5), FRIDGE))
        // a weak cat does not get onto the counter at all
        val weak = JumpCapability.of(1f, weak = true)
        assertNull(ParkourPlanner.edge(weak, FLOOR, COUNTER))
        assertNull(chain(weak, COUNTER))
        assertNull(chain(weak, FRIDGE))
        assertNull(chain(weak, SILL))
    }

    @Test
    fun theSmallestKittenReachesTheCounterOnlyThroughTheBox() {
        val k0 = JumpCapability.forStage(0)
        assertNull(ParkourPlanner.edge(k0, FLOOR, COUNTER))
        assertEquals(listOf(BOX, COUNTER), chain(k0, COUNTER))
        // the next stage jumps it directly
        assertEquals(listOf(COUNTER), chain(JumpCapability.forStage(1), COUNTER))
    }

    @Test
    fun theTreeIsFloorMidTopAndTheWindowSillsNeedTheirFurniture() {
        assertNull(ParkourPlanner.edge(adult, FLOOR, TOP))
        assertEquals(listOf(MID, TOP), chain(adult, TOP))
        assertEquals(listOf(MID), chain(adult, MID))
        // the living-room sill is behind the sofa: reached over it, not from the floor
        assertNull(ParkourPlanner.edge(adult, FLOOR, LSILL))
        assertEquals(listOf(COUCH, LSILL), chain(adult, LSILL))
        // the kitchen sill is above the counter
        assertNull(ParkourPlanner.edge(adult, FLOOR, KSILL))
        // the bedroom sill is a clear jump from the floor for a grown cat
        assertEquals(listOf(SILL), chain(adult, SILL))
        // the sill and the tree top both lead to the bookcase top, which the floor cannot reach
        assertNull(ParkourPlanner.edge(adult, FLOOR, BOOKCASE))
        assertNotNull(ParkourPlanner.edge(adult, TOP, BOOKCASE))
        assertNotNull(ParkourPlanner.edge(adult, SILL, BOOKCASE))
    }

    @Test
    fun noCatMakesAnUnsafeDropAndComingDownFollowsTheWayUp() {
        // fridge to floor is 101 px; bookcase to floor 99: neither is a safe drop
        assertNull(ParkourPlanner.edge(adult, FRIDGE, FLOOR))
        assertNull(ParkourPlanner.edge(adult, BOOKCASE, FLOOR))
        assertEquals(listOf(COUNTER, FLOOR), chain(adult, FLOOR, FRIDGE))
        for (cap in ParkourReport.kinds.map { it.cap }) {
            for (a in SurfaceId.entries) for (b in SurfaceId.entries) {
                val e = ParkourPlanner.edge(cap, a, b) ?: continue
                assertTrue("$a -> $b drop", e.rise >= -cap.maxDrop)
                assertTrue("$a -> $b reach", Math.abs(e.landX - e.takeOffX) <= cap.reachMax + 0.01f)
            }
        }
    }

    @Test
    fun noFlightCrossesAWall() {
        for (k in ParkourReport.kinds) for (a in SurfaceId.entries) for (b in SurfaceId.entries) {
            val e = ParkourPlanner.edge(k.cap, a, b) ?: continue
            assertEquals(
                "${k.name} $a -> $b crosses a wall",
                World.roomOf(e.takeOffX),
                World.roomOf(e.landX),
            )
        }
    }

    // ---------------------------------------------------------------- the planner

    @Test
    fun theFullReachabilityTableIsRecorded() {
        // The table in PET_ENGINE_PLAN.md, for every cat size: every edge the geometry and the
        // capability produce. A change to either shows up here as text. RECORD=1 regenerates.
        TraceSupport.checkGolden("parkour-reach", ParkourReport.text())
    }

    @Test
    fun theChainIsAsShortAsTheEdgesAllow() {
        // breadth first over the edges: the planner's chain is never longer
        for (k in ParkourReport.kinds) {
            val ids = SurfaceId.entries
            val dist = IntArray(ids.size) { -1 }
            dist[FLOOR.ordinal] = 0
            val queue = ArrayDeque(listOf(FLOOR))
            while (queue.isNotEmpty()) {
                val a = queue.removeFirst()
                for (b in ids) {
                    if (dist[b.ordinal] >= 0 || ParkourPlanner.edge(k.cap, a, b) == null) continue
                    dist[b.ordinal] = dist[a.ordinal] + 1
                    queue.addLast(b)
                }
            }
            for (goal in ids) {
                if (goal == FLOOR) continue
                val route = ParkourPlanner.plan(k.cap, FLOOR, 92f, goal, World.surface(goal).x0)
                if (dist[goal.ordinal] < 0) {
                    assertNull("${k.name} should not reach $goal", route)
                } else {
                    assertNotNull("${k.name} should reach $goal", route)
                    assertEquals(
                        "${k.name} -> $goal chain length",
                        dist[goal.ordinal],
                        route!!.chain,
                    )
                }
            }
        }
    }

    @Test
    fun aKittenTakesALongerChainThanAnAdult() {
        val kitten = JumpCapability.forStage(0)
        assertEquals(1, ParkourPlanner.plan(adult, FLOOR, 92f, SILL, 60f)!!.chain)
        assertEquals(4, ParkourPlanner.plan(kitten, FLOOR, 92f, SILL, 60f)!!.chain)
        assertEquals(listOf(MID, TOP, BOOKCASE, SILL), chain(kitten, SILL))
    }

    @Test
    fun aWeakCatAvoidsTheHighJumpsAnAdultTakes() {
        val weak = JumpCapability.of(1f, weak = true)
        val a = ParkourPlanner.plan(adult, FLOOR, 92f, BOOKCASE, 120f)!!
        val w = ParkourPlanner.plan(weak, FLOOR, 92f, BOOKCASE, 120f)!!
        assertEquals(listOf(SILL, BOOKCASE), a.leaps.map { it.to })
        assertEquals(listOf(MID, TOP, BOOKCASE), w.leaps.map { it.to })
        assertTrue(w.leaps.maxOf { it.rise } <= weak.maxRise(0f))
        assertTrue(w.leaps.maxOf { it.rise } < a.leaps.maxOf { it.rise })
    }

    @Test
    fun theCheaperOfTwoRoutesWins() {
        // from the couch to the tree top there is no way over the wall; from the sill the
        // bookcase is one leap, and the same route is the cheapest from anywhere on the floor
        val r = ParkourPlanner.plan(adult, SILL, 60f, BOOKCASE, 120f)!!
        assertEquals(listOf(BOOKCASE), r.leaps.map { it.to })
        assertTrue(r.cost > 0f)
        // an easier hop costs less than a hard one
        val easy = ParkourPlanner.plan(adult, FLOOR, 60f, BED, 36f)!!
        val hard = ParkourPlanner.plan(adult, FLOOR, 60f, SILL, 60f)!!
        assertTrue("easy ${easy.cost} against hard ${hard.cost}", easy.cost < hard.cost)
        // and a weak cat finds the same hops dearer than a strong one does
        val weak = JumpCapability.of(1f, weak = true)
        val strongTree = ParkourPlanner.plan(adult, FLOOR, 140f, TOP, 154f)!!
        val weakTree = ParkourPlanner.plan(weak, FLOOR, 140f, TOP, 154f)!!
        assertTrue(weakTree.cost > strongTree.cost)
    }

    @Test
    fun theSamePlanTwiceIsTheSamePlanAndTheSeedOnlyBreaksTies() {
        val first = ParkourPlanner.plan(adult, FLOOR, 92f, FRIDGE, 226f, seed = 7)!!
        val again = ParkourPlanner.plan(adult, FLOOR, 92f, FRIDGE, 226f, seed = 7)!!
        assertEquals(first.steps, again.steps)
        assertEquals(first.cost, again.cost, 0f)
        for (seed in 0..40) {
            val r = ParkourPlanner.plan(adult, FLOOR, 92f, FRIDGE, 226f, seed)!!
            assertEquals(first.leaps.map { it.to }, r.leaps.map { it.to })
            // the jitter is a hair of cost, never a different route
            assertEquals(first.cost, r.cost, Tuning.Parkour.TIE_BREAK * 10f)
        }
    }

    @Test
    fun theStepsAreWalksAndLeapsThatEndWhereAsked() {
        val steps = ParkourPlanner.route(adult, FLOOR, 92f, FRIDGE, 226f)
        assertEquals(PlanStep.Walk::class, steps[0]::class)
        val leaps = steps.filterIsInstance<PlanStep.Leap>()
        assertEquals(listOf(COUNTER, FRIDGE), leaps.map { it.surface })
        // the two leaps are back to back (the counter has no walk after the first landing): a chain
        val at = steps.indexOf(leaps[0])
        assertEquals(leaps[1], steps[at + 1])
        // the last step is the walk to the spot asked for on the fridge top
        assertEquals(PlanStep.Walk(226f), steps.last())
        // the first leap takes off in the counter's range and is aimed at the fridge
        assertTrue(leaps[1].takeOffX in World.surface(COUNTER).x0..World.surface(COUNTER).x1)
        assertTrue(leaps[1].x in World.surface(FRIDGE).x0..World.surface(FRIDGE).x1)
    }

    @Test
    fun aRunUpIsAWalkBackThenALeapFromTheMark() {
        val steps = ParkourPlanner.route(adult, FLOOR, 92f, FRIDGE, 226f)
        val leap = steps.filterIsInstance<PlanStep.Leap>()[1]
        val hop = ParkourPlanner.plan(adult, FLOOR, 92f, FRIDGE, 226f)!!.leaps[1]
        assertTrue(hop.runUp >= Tuning.Parkour.RUN_UP_MIN_PX)
        // the counter is jumped from at its left end, running leftward from further back
        assertEquals(leap.takeOffX, hop.takeOffX, 0f)
        assertTrue(leap.takeOffX + hop.runUp <= World.surface(COUNTER).x1)
    }

    @Test
    fun staysPutWhereThereIsNothingToDo() {
        assertEquals(emptyList<PlanStep>(), ParkourPlanner.route(adult, BED, 36f, BED, 36f))
        assertEquals(
            listOf<PlanStep>(PlanStep.Walk(200f)),
            ParkourPlanner.route(adult, FLOOR, 92f, FLOOR, 200f),
        )
        assertEquals(
            listOf<PlanStep>(PlanStep.Walk(346f)),
            ParkourPlanner.route(adult, FLOOR, 92f, FLOOR, 999f),
        )
        assertEquals(
            listOf<PlanStep>(PlanStep.Walk(76f)),
            ParkourPlanner.route(adult, SILL, 40f, SILL, 200f),
        )
        assertNull(ParkourPlanner.plan(JumpCapability.forStage(0), FLOOR, 92f, FRIDGE, 226f))
        assertEquals(
            emptyList<PlanStep>(),
            ParkourPlanner.route(JumpCapability.forStage(0), FLOOR, 92f, FRIDGE, 226f),
        )
    }

    @Test
    fun propsAreSteppedOnAtTheirOwnDepth() {
        val steps = ParkourPlanner.route(adult, FLOOR, 92f, BOX, 290f)
        val walk = steps[0] as PlanStep.Walk
        assertEquals(World.surface(BOX).z + World.BOX_FRONT_Z, walk.z!!, 0f)
        assertEquals(LeapKind.STEP, ParkourPlanner.edge(adult, FLOOR, BOX)!!.kind)
        assertEquals(LeapKind.STEP, ParkourPlanner.edge(adult, FLOOR, BED)!!.kind)
        assertEquals(LeapKind.LEAP, ParkourPlanner.edge(adult, FLOOR, COUNTER)!!.kind)
        assertEquals(LeapKind.DROP, ParkourPlanner.edge(adult, COUNTER, FLOOR)!!.kind)
    }

    // ---------------------------------------------------------------- the arcs

    @Test
    fun everyPlannedArcLandsOnItsTargetAndClearsTheFurniture() {
        val g = Tuning.Parkour.GRAVITY
        for (k in ParkourReport.kinds) for (a in SurfaceId.entries) for (b in SurfaceId.entries) {
            val e = ParkourPlanner.edge(k.cap, a, b) ?: continue
            val sa = World.surface(a)
            val sb = World.surface(b)
            val z = if (sa.prop) sa.z else if (sb.prop) sb.z else World.ART_Z
            val ya = if (a == FLOOR) World.floorY(z) else sa.y
            val yb = if (b == FLOOR) World.floorY(z) else sb.y
            // the arc starts and ends on the two surfaces
            assertEquals("${k.name} $a->$b rise", ya - yb, e.rise, 1e-3f)
            assertEquals(
                "${k.name} $a->$b lands",
                e.rise,
                Ballistics.lift(e.arc.launchSpeed, e.arc.flight, g),
                0.05f,
            )
            assertTrue(
                "${k.name} $a->$b off",
                e.takeOffX in minOf(sa.x0, sa.x1)..maxOf(sa.x0, sa.x1) || a == FLOOR,
            )
            assertTrue(
                "${k.name} $a->$b on",
                e.landX in minOf(sb.x0, sb.x1)..maxOf(sb.x0, sb.x1) || b == FLOOR,
            )
            // the apex clears the target by at least the clearance for a climb
            if (e.rise > Tuning.Parkour.STEP_MAX) {
                assertTrue(
                    "${k.name} $a->$b apex",
                    e.arc.apex >= e.rise + Tuning.Parkour.CLEARANCE - 1e-3f,
                )
            }
            // and no sample of the flight is inside furniture the leap is not part of
            val lowPair = (sa.prop || a == FLOOR) && (sb.prop || b == FLOOR)
            if (lowPair) continue
            for (i in 1 until 40) {
                val t = e.arc.flight * i / 40f
                val x = e.takeOffX + (e.landX - e.takeOffX) * t / e.arc.flight
                val y = ya - Ballistics.lift(e.arc.launchSpeed, t, g)
                for (s in Solids.all) {
                    if (!s.blocks(a, b)) continue
                    assertFalse(
                        "${k.name} $a->$b passes through ${s.name} at ($x, $y)",
                        s.contains(x, y, 0f),
                    )
                }
            }
        }
    }

    @Test
    fun theSofaIsWhatKeepsTheCatOffTheLivingRoomSillFromTheFloor() {
        // without the sofa solid a hard flight from the floor would do it; with it, none does
        assertNull(ParkourPlanner.edge(adult, FLOOR, LSILL))
        val sofa = Solids.all.first { it.name == "sofa" }
        assertTrue(sofa.blocks(FLOOR, LSILL))
        assertFalse(sofa.blocks(FLOOR, COUCH))
        assertFalse(sofa.blocks(COUCH, LSILL))
    }
}
