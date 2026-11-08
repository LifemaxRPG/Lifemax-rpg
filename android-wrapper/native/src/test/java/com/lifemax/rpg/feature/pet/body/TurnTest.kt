package com.lifemax.rpg.feature.pet.body

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Turning round step by step with the lab's `turn` row ([Turn]): the steps in order (side with the
 * head turned, three-quarter, front, the other three-quarter, the side facing the new way), the
 * pace by how the cat was moving, planted feet, and that every way of asking, interrupting or
 * cutting a turn ends with the cat facing one way and never stuck in the row.
 */
class TurnTest {
    private val dt = 1f / 120f

    /** What is on screen at one tick. */
    private data class Shot(
        val clip: String,
        val frame: Int,
        val flip: Boolean,
        val x: Float,
        val scaleX: Float,
    )

    private fun shot(b: CatBody) =
        Shot(b.pose.clipName, b.pose.frame, b.pose.flip, b.pose.x, b.pose.scaleX)

    /** Ticks of [b] for [seconds], one shot per tick. */
    private fun film(b: CatBody, seconds: Float): List<Shot> =
        List((seconds * 120).toInt()) {
            b.step(dt)
            shot(b)
        }

    /** The turn row's (frame, flip) steps in the order they were shown, repeats collapsed. */
    private fun turnSteps(shots: List<Shot>): List<Pair<Int, Boolean>> {
        val out = ArrayList<Pair<Int, Boolean>>()
        for (s in shots) if (s.clip == "turn") {
            val step = s.frame to s.flip
            if (out.lastOrNull() != step) out.add(step)
        }
        return out
    }

    private fun seconds(shots: List<Shot>) = shots.count { it.clip == "turn" } * dt

    private val forward = listOf(0 to false, 1 to false, 2 to false, 3 to false, 0 to true)

    @Test
    fun aStandingCatTurnsOnTheSpotStepByStep() {
        val body = CatBody(seed = 3)
        body.replacePlan(1, listOf(PlanStep.Face(-1)))
        val shots = film(body, 1f)
        assertEquals(forward, turnSteps(shots))
        assertEquals(Tuning.Turn.STILL_S, seconds(shots), 2 * dt)
        assertTrue("the feet stayed put", shots.all { it.x == 92f })
        assertTrue("the body is never narrowed", shots.all { it.scaleX > 0.9f })
        assertEquals(-1, body.pose.facing)
        assertEquals("idle_sit", body.pose.clipName)
        assertTrue(body.isIdle)
    }

    @Test
    fun aWalkingCatStopsTurnsAndWalksOn() {
        val body = CatBody(seed = 3, startX = 60f)
        body.replacePlan(1, listOf(PlanStep.Walk(300f)))
        film(body, 2f)
        val x0 = body.pose.x
        body.replacePlan(2, listOf(PlanStep.Walk(40f)))
        val shots = film(body, 5f)
        assertEquals(forward, turnSteps(shots))
        assertEquals(Tuning.Turn.WALK_S, seconds(shots), 2 * dt)
        val first = shots.indexOfFirst { it.clip == "turn" }
        val last = shots.indexOfLast { it.clip == "turn" }
        assertTrue("walked on before stopping", shots.take(first).any { it.clip == "walk" })
        val slid = shots[first].x - x0
        // the walk ends its stride (the plan changes on a footfall), then brakes hard
        assertTrue("the stop took $slid px", slid in 0.5f..MAX_STOP_PX)
        assertTrue(
            "the paws are planted while the row plays",
            (first..last).all { shots[it].x == shots[first].x },
        )
        assertTrue("walked off after the turn", shots.drop(last).any { it.clip == "walk" })
        assertEquals(40f, body.pose.x, 0.001f)
        assertEquals(-1, body.pose.facing)
    }

    @Test
    fun aGallopingCatSkidsThenTurnsQuickly() {
        val body = CatBody(seed = 3, startX = 0f)
        body.replacePlan(1, listOf(PlanStep.Walk(320f, run = true)))
        film(body, 2.2f)
        assertEquals("run", body.pose.clipName)
        body.replacePlan(2, listOf(PlanStep.Walk(0f, run = true)))
        var skid = 0f
        val shots =
            List(120 * 3) {
                body.step(dt)
                skid = maxOf(skid, body.pose.skid)
                shot(body)
            }
        assertTrue("the gallop skidded", skid > 0.5f)
        assertEquals(forward, turnSteps(shots))
        assertEquals(Tuning.Turn.RUN_S, seconds(shots), 2 * dt)
        assertEquals(-1, body.pose.facing)
    }

    @Test
    fun turningBackBeforeTheFlipRunsTheStepsBackwards() {
        val body = CatBody(seed = 3)
        body.replacePlan(1, listOf(PlanStep.Face(-1)))
        val first = film(body, 0.15f)
        body.replacePlan(2, listOf(PlanStep.Face(1)))
        val rest = film(body, 1f)
        val steps = turnSteps(first + rest)
        assertFalse("never flipped: $steps", steps.any { it.second })
        assertEquals(0, steps.last().first)
        assertEquals(1, body.pose.facing)
        assertEquals("idle_sit", body.pose.clipName)
        assertTrue(body.isIdle)
    }

    @Test
    fun turningBackDuringTheLastStepFinishesThenTurnsAgain() {
        val body = CatBody(seed = 3)
        body.replacePlan(1, listOf(PlanStep.Face(-1)))
        val first = film(body, Tuning.Turn.STILL_S * 0.9f)
        assertTrue("in the last step", first.last().clip == "turn" && first.last().flip)
        body.replacePlan(2, listOf(PlanStep.Face(1)))
        val rest = film(body, 1.5f)
        val steps = turnSteps(first + rest)
        assertEquals(forward + listOf(1 to true, 2 to true, 3 to true, 0 to false), steps)
        assertEquals(1, body.pose.facing)
        assertTrue(body.isIdle)
    }

    @Test
    fun aWalkCutBackBeforeTheFeetArePlantedCancelsTheTurn() {
        val body = CatBody(seed = 3, startX = 60f)
        body.replacePlan(1, listOf(PlanStep.Walk(300f)))
        film(body, 2f)
        body.replacePlan(2, listOf(PlanStep.Walk(40f)))
        body.step(dt)
        body.step(dt)
        body.replacePlan(3, listOf(PlanStep.Walk(300f)))
        val shots = film(body, 2f)
        assertTrue("no turn shown", shots.none { it.clip == "turn" })
        assertTrue("never flipped", shots.none { it.flip })
        assertTrue("walks on", shots.last().x > shots.first().x)
    }

    @Test
    fun aCatOnAPerchTurnsWithoutMovingOffIt() {
        val body = CatBody(seed = 4)
        body.placeAt(158f, 0f, SurfaceId.TOP, 1)
        val y = body.pose.y
        body.replacePlan(1, listOf(PlanStep.Face(-1), PlanStep.Walk(150f), PlanStep.Face(1)))
        val shots = film(body, 3f)
        assertEquals(
            forward + listOf(1 to true, 2 to true, 3 to true, 0 to false),
            turnSteps(shots),
        )
        assertTrue("stayed on the top", shots.all { it.x in 150f..158f })
        assertEquals(SurfaceId.TOP, body.pose.surface)
        assertEquals(y, body.pose.y, 0.001f)
        assertEquals(1, body.pose.facing)
        assertTrue(body.isIdle)
    }

    @Test
    fun aCarriedToyStaysInTheMouthThroughTheTurn() {
        val case = ToyScenes.carryTurn()
        val scene = case.scene
        scene.plan(*case.plan.toTypedArray())
        var turned = false
        var held = true
        scene.run(case.seconds) {
            if (it.body.pose.clipName == "turn") {
                turned = true
                held = held && it.body.heldToy != null
            }
        }
        assertTrue("the carry turned round with the row", turned)
        assertTrue("the toy stayed in the mouth while turning", held)
        assertEquals(-1, scene.body.pose.facing)
    }

    @Test
    fun twoCatsMeetingFaceEachOther() {
        val a = CatBody(seed = 5, startX = 40f)
        val b = CatBody(seed = 6, startX = 160f)
        a.replacePlan(1, listOf(PlanStep.Walk(130f), PlanStep.Face(1)))
        b.replacePlan(1, listOf(PlanStep.Walk(110f), PlanStep.Walk(150f), PlanStep.Face(-1)))
        val sa = ArrayList<Shot>()
        val sb = ArrayList<Shot>()
        repeat(120 * 8) {
            a.step(dt)
            b.step(dt)
            sa.add(shot(a))
            sb.add(shot(b))
        }
        assertEquals(1, a.pose.facing)
        assertEquals(-1, b.pose.facing)
        assertTrue(a.isIdle && b.isIdle)
        assertEquals(
            "b turned three times",
            3,
            turnSteps(sb).count { it == (2 to false) || it == (2 to true) },
        )
        assertTrue("a never needed to turn", sa.none { it.clip == "turn" })
    }

    @Test
    fun placingTheCatMidTurnSnapsItsFacing() {
        val body = CatBody(seed = 3)
        body.replacePlan(1, listOf(PlanStep.Face(-1)))
        film(body, 0.1f)
        assertEquals("turn", body.pose.clipName)
        body.placeAt(50f, World.ART_Z, SurfaceId.FLOOR, -1)
        body.step(dt)
        assertEquals("idle_sit", body.pose.clipName)
        assertEquals(-1, body.pose.facing)
        assertTrue(body.pose.flip)
        assertTrue(film(body, 1f).none { it.clip == "turn" })
    }

    @Test
    fun aHopTheOtherWayTurnsFirst() {
        val body = CatBody(seed = 3, startX = 60f)
        body.replacePlan(1, listOf(PlanStep.Hop(SurfaceId.POUF, -34f)))
        val shots = film(body, 4f)
        assertEquals(forward, turnSteps(shots).take(5))
        assertEquals(SurfaceId.POUF, body.pose.surface)
        assertTrue(body.isIdle)
    }

    @Test
    fun aLeapStillPlaysItsOwnTurn() {
        val body = CatBody(seed = 3, startX = 120f)
        val route = body.routeTo(SurfaceId.COUCH, -100f)
        assertTrue("a route", route.isNotEmpty())
        body.replacePlan(1, route)
        val shots = film(body, 12f)
        assertTrue("turned", shots.any { it.clip == "turn" })
        assertEquals(SurfaceId.COUCH, body.pose.surface)
    }

    @Test
    fun randomOrdersNeverLeaveTheCatStuckInTheRow() {
        for (seed in 1..40) {
            val rng = Rng(seed)
            val body = CatBody(seed = seed, startX = 100f)
            var plan = 1
            repeat(30) {
                val steps =
                    when (rng.range(0f, 4f).toInt()) {
                        0 -> listOf(PlanStep.Face(if (rng.range(0f, 1f) < 0.5f) 1 else -1))
                        1 -> listOf(PlanStep.Walk(rng.range(20f, 200f)))
                        2 -> listOf(PlanStep.Walk(rng.range(20f, 200f), run = true))
                        else -> listOf(PlanStep.Act("idle_sit", 0.3f))
                    }
                body.replacePlan(plan++, steps)
                repeat(rng.range(1f, 60f).toInt()) { body.step(dt) }
            }
            val want = if (rng.range(0f, 1f) < 0.5f) 1 else -1
            body.replacePlan(plan, listOf(PlanStep.Face(want)))
            val shots = film(body, 3f)
            assertTrue("seed $seed ended idle", body.isIdle)
            assertEquals("seed $seed facing", want, body.pose.facing)
            assertTrue("seed $seed left the row", shots.takeLast(60).none { it.clip == "turn" })
            assertEquals("seed $seed sprite matches facing", want < 0, body.pose.flip)
        }
    }

    @Test
    fun theTurnItselfResolvesEveryRequestOrder() {
        val turn = Turn()
        turn.request(-1)
        assertTrue(turn.active && turn.planting)
        turn.update(dt, moving = true, gait = Gait.WALK)
        assertTrue("waits for the feet", turn.planting)
        turn.update(dt, moving = false, gait = Gait.STAND)
        assertTrue(turn.showing && turn.plantedNow)
        turn.request(-1)
        turn.request(1)
        turn.request(-1)
        var ticks = 0
        while (turn.active && ticks < 1000) {
            turn.update(dt, moving = false, gait = Gait.STAND)
            ticks++
        }
        assertFalse(turn.active)
        assertEquals(-1, turn.dir)
        turn.request(1)
        turn.update(dt, moving = true, gait = Gait.RUN)
        ticks = 0
        while (turn.planting && ticks < 1000) {
            turn.update(dt, moving = true, gait = Gait.RUN)
            ticks++
        }
        assertEquals(
            "a cat that never stops still turns",
            Tuning.Turn.PLANT_MAX_S,
            ticks * dt,
            2 * dt,
        )
        turn.snapTo(-1)
        assertFalse(turn.active)
        assertEquals(-1, turn.dir)
        assertEquals(-1, turn.target)
    }

    @Test
    fun theStepSharesFillTheTurn() {
        assertEquals(1f, Tuning.Turn.STEP_SHARES.sum(), 1e-5f)
        for (s in listOf(Tuning.Turn.STILL_S, Tuning.Turn.WALK_S, Tuning.Turn.RUN_S)) assertTrue(
            "a turn takes 0.25 to 0.4 s: $s",
            s in 0.25f..0.4f,
        )
        assertTrue(abs(Tuning.Turn.STEP_SHARES.first() - Tuning.Turn.STEP_SHARES.last()) < 0.05f)
    }

    private companion object {
        /** Half a walk cycle to the next footfall (about 11 px) and the planted stop. */
        const val MAX_STOP_PX = 14f
    }
}
