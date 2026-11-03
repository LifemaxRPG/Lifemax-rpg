package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.body.SurfaceId.BED
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUNTER
import com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR
import com.lifemax.rpg.feature.pet.body.SurfaceId.FRIDGE
import com.lifemax.rpg.feature.pet.body.SurfaceId.TOP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leaps acted out by a real body: true ballistic arcs that land on the target, the jump rows in
 * order, the turn row when the cat must turn, a pause and a look before a hard jump, chains that
 * flow, equal poses at any frame rate, and recorded traces of the fridge chain.
 */
class ParkourFlightTest {
    private fun cat(seed: Int = 7, x: Float = 262f, scale: Float = 1f) =
        CatBody(seed = seed, startX = x).also { it.growthScale = scale }

    /** What a run saw: every distinct clip, in order, with the time it first showed. */
    private class Watch {
        val clips = ArrayList<String>()
        val times = ArrayList<Float>()
        val rotations = ArrayList<Float>()

        fun see(t: Float, p: CatPose) {
            if (clips.isEmpty() || clips.last() != p.clipName) {
                clips.add(p.clipName)
                times.add(t)
            }
            rotations.add(p.rotation)
        }
    }

    private fun run(body: CatBody, seconds: Float, hz: Int = 120, watch: Watch? = null) {
        val dt = 1f / hz
        for (frame in 1..Math.round(seconds * hz)) {
            body.step(dt)
            watch?.see(frame * dt, body.pose)
        }
    }

    // ---------------------------------------------------------------- landing and the arc

    @Test
    fun theCatLandsOnTheFridgeTopThroughTheCounterExactly() {
        val body = cat()
        val route = body.parkourRoute(FRIDGE, 226f)!!
        body.replacePlan(1, route.steps)
        val surfaces = ArrayList<SurfaceId>()
        for (i in 1..60 * 20) {
            body.step(1f / 60f)
            val s = body.pose.surface
            if (surfaces.isEmpty() || surfaces.last() != s) surfaces.add(s)
        }
        assertEquals(listOf(FLOOR, COUNTER, FRIDGE), surfaces)
        assertEquals(FRIDGE, body.pose.surface)
        assertEquals(World.surface(FRIDGE).y, body.pose.y, 0f)
        assertEquals(226f, body.pose.x, 0.1f)
        assertTrue(body.isIdle)
    }

    @Test
    fun aFlightFollowsTheBallisticArcOfItsPlan() {
        val body = cat(x = 300f)
        body.placeAt(300f, World.ART_Z, COUNTER)
        val leap = body.parkourRoute(FRIDGE, 226f)!!.leaps.single()
        body.replacePlan(1, body.routeTo(FRIDGE, 226f))
        val g = Tuning.Parkour.GRAVITY
        val dt = 1f / 120f
        val ground = World.surface(COUNTER).y
        var launch = -1
        var minY = Float.MAX_VALUE
        var landedAt = -1
        for (frame in 1..120 * 8) {
            body.step(dt)
            val p = body.pose
            if (launch < 0 && p.y < ground - 0.001f) launch = frame
            if (launch >= 0 && landedAt < 0) {
                minY = minOf(minY, p.y)
                if (p.surface == FRIDGE) landedAt = frame
                else {
                    val t = (frame - launch + 1) * dt
                    val want = ground - Ballistics.lift(leap.arc.launchSpeed, t, g)
                    assertEquals("y at ${t}s of the flight", want, p.y, 0.05f)
                }
            }
        }
        assertTrue(launch > 0 && landedAt > 0)
        // apex and flight time are the plan's, to a frame
        assertEquals(leap.arc.apex, ground - minY, 0.6f)
        assertEquals(leap.arc.flight, (landedAt - launch + 1) * dt, 2.5f * dt)
        assertEquals(World.surface(FRIDGE).y, body.pose.y, 0f)
    }

    @Test
    fun aDropFallsFasterThanItRoseAndSquashesOnLanding() {
        val body = cat()
        body.placeAt(236f, World.ART_Z, FRIDGE)
        body.replacePlan(1, body.routeTo(COUNTER, 300f))
        var maxSquash = 0f
        for (i in 1..60 * 6) {
            body.step(1f / 60f)
            maxSquash = maxOf(maxSquash, 1f - body.pose.scaleY)
        }
        assertEquals(COUNTER, body.pose.surface)
        assertTrue("landing squash $maxSquash", maxSquash > 0.1f)
    }

    // ---------------------------------------------------------------- the rows

    @Test
    fun aLeapPlaysTheJumpRowsInOrder() {
        val body = cat()
        body.replacePlan(1, body.routeTo(COUNTER, 300f))
        val w = Watch()
        run(body, 8f, watch = w)
        val rows = w.clips.filter { it.startsWith("jump_") }
        assertEquals(listOf("jump_up", "jump_air", "jump_land"), rows)
        assertTrue(w.times[w.clips.indexOf("jump_up")] < w.times[w.clips.indexOf("jump_air")])
    }

    @Test
    fun theCatTurnsWithTheTurnRowWhenItMustFaceTheTarget() {
        val body = cat()
        body.placeAt(290f, World.ART_Z, COUNTER, facing = 1)
        val w = Watch()
        body.replacePlan(1, body.routeTo(FRIDGE, 226f))
        var flipped = false
        val dt = 1f / 120f
        var facing = body.pose.facing
        for (i in 1..120 * 10) {
            body.step(dt)
            w.see(i * dt, body.pose)
            if (body.pose.facing != facing) {
                flipped = true
                // the flip happens while the turn row has just ended
                assertTrue(w.clips.last() == "turn" || w.clips.contains("turn"))
                facing = body.pose.facing
            }
        }
        assertTrue(flipped)
        assertTrue("never played the turn row: ${w.clips}", "turn" in w.clips)
        assertTrue(w.clips.indexOf("turn") < w.clips.indexOf("jump_air"))
        assertEquals(-1, body.pose.facing)
    }

    // ---------------------------------------------------------------- hesitation and measuring

    @Test
    fun aHardJumpIsMetWithAPauseAndAMeasuringWiggleAnEasyOneIsNot() {
        fun secondsBeforeLaunch(
            from: SurfaceId,
            x: Float,
            to: SurfaceId,
            at: Float,
        ): Pair<Float, Float> {
            val body = cat()
            body.placeAt(x, World.ART_Z, from, facing = if (at < x) -1 else 1)
            body.replacePlan(1, body.routeTo(to, at))
            val dt = 1f / 120f
            val ground = World.groundY(from, World.ART_Z)
            var wiggle = 0f
            for (i in 1..120 * 8) {
                body.step(dt)
                // on the ground the whole time before the push: how long, and how much it swayed
                if (body.pose.y < ground - 0.001f) return i * dt to wiggle
                wiggle = maxOf(wiggle, Math.abs(body.pose.rotation))
            }
            error("never left $from")
        }
        // counter to fridge: 60 up, 90 percent of the cat's best; floor to bed: a step
        val (hardPause, hardWiggle) = secondsBeforeLaunch(COUNTER, 298f, FRIDGE, 226f)
        val (easyPause, easyWiggle) = secondsBeforeLaunch(FLOOR, 36f, BED, 36f)
        assertTrue("pause $hardPause", hardPause > 0.8f)
        assertTrue("easy $easyPause", easyPause < 0.4f)
        assertTrue("wiggle $hardWiggle", hardWiggle >= Tuning.Parkour.MEASURE_ROTATION * 0.5f)
        assertTrue("easy wiggle $easyWiggle", easyWiggle < Tuning.Parkour.MEASURE_ROTATION * 0.5f)
    }

    @Test
    fun theCatLooksUpAtTheTargetBeforeABigJump() {
        val body = cat()
        body.placeAt(298f, World.ART_Z, COUNTER, facing = -1)
        body.replacePlan(1, body.routeTo(FRIDGE, 226f))
        var lookedUp = 0f
        for (i in 1..120 * 3) {
            body.step(1f / 120f)
            lookedUp = minOf(lookedUp, body.pose.lookY)
        }
        assertTrue("look y $lookedUp", lookedUp < -0.3f)
    }

    // ---------------------------------------------------------------- chains

    @Test
    fun theLandingOfOneLeapBlendsIntoTheNextWhenTheyAreBackToBack() {
        val route = cat().parkourRoute(FRIDGE, 226f)!!
        val first = route.steps.indexOfFirst { it is PlanStep.Leap }
        // the planner lands where the next run-up starts: the two leaps are neighbours
        assertTrue(route.steps[first + 1] is PlanStep.Leap)

        /** Seconds from the first touchdown to the second push, and the clips between. */
        fun gap(steps: List<PlanStep>): Pair<Float, List<String>> {
            val body = cat()
            body.replacePlan(1, steps)
            val dt = 1f / 120f
            var landed = -1f
            var launched = -1f
            var previous = body.pose.clipName
            var airs = 0
            val between = ArrayList<String>()
            for (i in 1..120 * 24) {
                body.step(dt)
                val name = body.pose.clipName
                if (name == "jump_land" && previous != "jump_land" && landed < 0f) landed = i * dt
                if (
                    landed >= 0f && launched < 0f && (between.isEmpty() || between.last() != name)
                ) {
                    between.add(name)
                }
                if (name == "jump_air" && previous != "jump_air") {
                    airs++
                    if (airs == 2) launched = i * dt
                }
                previous = name
            }
            return launched - landed to between
        }
        val (chained, clips) = gap(route.steps)
        val apart =
            gap(
                route.steps.toMutableList().also {
                    it.add(first + 1, PlanStep.Act("idle_sit", 0.4f))
                }
            )
        assertTrue("chained gap $chained", chained > 0f)
        assertTrue(
            "chained $chained should beat apart ${apart.first}",
            chained + 0.3f < apart.first,
        )
        // between the landing and the push: no stand-and-wait, just the turn, the look, the run-up
        assertTrue("clips between: $clips", clips.none { it == "idle_sit" })
        // and it is quick: a short landing, a pause (a hard jump), a crouch, at most a turn
        val p = Tuning.Parkour
        val pause =
            (p.HESITATE_BASE_S + p.HESITATE_PER_EFFORT_S * route.leaps.last().arc.effort) * 0.5f +
                p.MEASURE_S
        assertTrue(
            "gap $chained",
            chained < p.LAND_CHAIN_S + pause + p.CROUCH_CHAIN_S + Clips.TURN.seconds + 0.3f,
        )
    }

    @Test
    fun aKittenWithTheBoxChainReachesTheCounter() {
        val body = cat(scale = Tuning.Parkour.STAGE_SCALE[0])
        val route = body.parkourRoute(COUNTER, 300f)!!
        assertEquals(listOf(SurfaceId.BOX, COUNTER), route.leaps.map { it.to })
        body.replacePlan(1, route.steps)
        run(body, 16f)
        assertEquals(COUNTER, body.pose.surface)
        assertEquals(300f, body.pose.x, 0.1f)
    }

    @Test
    fun theSmallestKittenStaysWhereItIsWhenAskedForTheFridge() {
        val body = cat(scale = Tuning.Parkour.STAGE_SCALE[0])
        assertEquals(emptyList<PlanStep>(), body.routeTo(FRIDGE, 226f))
        run(body, 3f)
        assertEquals(FLOOR, body.pose.surface)
    }

    @Test
    fun aCatInTheAirFinishesItsLeapBeforeANewPlanTakesOver() {
        val body = cat()
        body.replacePlan(1, body.routeTo(TOP, 154f))
        val dt = 1f / 120f
        var swapped = false
        var landedBeforeSwap = false
        for (i in 1..120 * 12) {
            body.step(dt)
            if (!swapped && body.pose.clipName == "jump_air") {
                body.replacePlan(2, listOf(PlanStep.Walk(120f)))
                swapped = true
                landedBeforeSwap = false
            }
            if (swapped && body.pose.surface != FLOOR) landedBeforeSwap = true
        }
        assertTrue(swapped)
        assertTrue("the leap in the air was dropped", landedBeforeSwap)
    }

    // ---------------------------------------------------------------- time

    @Test
    fun equalPosesAtEqualTimesAtEveryFrameRate() {
        fun script(hz: Int): List<String> {
            val body = cat(seed = 5)
            body.replacePlan(1, body.routeTo(FRIDGE, 226f) + PlanStep.Act("groom", 1f))
            val out = ArrayList<String>()
            val dt = 1f / hz
            for (frame in 1..hz * 14) {
                body.step(dt)
                if (frame % (hz / 30) == 0) out.add(TraceSupport.fingerprint(body.pose))
            }
            return out
        }
        val reference = script(120)
        for (hz in listOf(30, 60, 240)) {
            val other = script(hz)
            assertEquals(reference.size, other.size)
            for (i in reference.indices) assertEquals(
                "at ${hz}Hz sample $i",
                reference[i],
                other[i],
            )
        }
    }

    // ---------------------------------------------------------------- recorded traces

    @Test
    fun floorToCounterToFridgeTrace() {
        val body = cat()
        body.replacePlan(1, body.routeTo(FRIDGE, 226f))
        TraceSupport.checkGolden("parkour-fridge-chain", TraceSupport.record(body, 16f))
    }

    @Test
    fun smallestKittenTakesTheBoxToTheCounterTrace() {
        val body = cat(scale = Tuning.Parkour.STAGE_SCALE[0])
        body.replacePlan(1, body.routeTo(COUNTER, 300f))
        TraceSupport.checkGolden("parkour-kitten-box-counter", TraceSupport.record(body, 14f))
    }

    @Test
    fun smallestKittenAskedForTheFridgeStaysTrace() {
        val body = cat(scale = Tuning.Parkour.STAGE_SCALE[0])
        body.replacePlan(1, body.routeTo(FRIDGE, 226f))
        TraceSupport.checkGolden(
            "parkour-kitten-stays",
            TraceSupport.record(body, 4f, sampleEveryFrames = 12),
        )
    }
}
