package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hops: anticipation, launch stretch, apex hang, landing squash, and one code for every jump. */
class JumpTest {
    private val dt = 1f / 120f

    private class Flight(
        val clips: List<String>,
        val peakElevation: Float,
        val minScaleYAfterLanding: Float,
        val maxScaleYAtLaunch: Float,
        val launchTick: Int,
        val landTick: Int,
        val final: CatPose,
    )

    private fun hop(surface: SurfaceId, x: Float, fromX: Float = 92f): Flight {
        val body = CatBody(seed = 2, startX = fromX)
        body.replacePlan(1, listOf(PlanStep.Hop(surface, x)))
        val clips = ArrayList<String>()
        var peak = 0f
        var minAfter = 2f
        var maxLaunch = 0f
        var launch = -1
        var land = -1
        for (tick in 1..(120 * 4)) {
            body.step(dt)
            val p = body.pose
            if (clips.isEmpty() || clips.last() != p.clipName) clips.add(p.clipName)
            peak = maxOf(peak, p.elevation)
            if (p.clipName == "leap" && launch < 0) launch = tick
            if (launch >= 0 && land < 0 && p.clipName != "leap") land = tick
            if (launch >= 0 && land < 0) maxLaunch = maxOf(maxLaunch, p.scaleY)
            if (land >= 0 && tick < land + 25) minAfter = minOf(minAfter, p.scaleY)
        }
        return Flight(clips, peak, minAfter, maxLaunch, launch, land, body.pose)
    }

    @Test
    fun aHopCrouchesLeapsAndLands() {
        val f = hop(SurfaceId.SILL, 60f, fromX = 54f)
        assertEquals(listOf("crouch", "leap", "crouch", "idle_sit"), f.clips)
        assertEquals(SurfaceId.SILL, f.final.surface)
        assertEquals(60f, f.final.x, 0.001f)
        assertEquals(World.surface(SurfaceId.SILL).y, f.final.y, 0.001f)
    }

    @Test
    fun theCatStretchesAtLaunchAndSquashesOnLanding() {
        val f = hop(SurfaceId.SILL, 60f, fromX = 54f)
        assertTrue("no launch stretch: ${f.maxScaleYAtLaunch}", f.maxScaleYAtLaunch > 1.05f)
        assertTrue("no landing squash: ${f.minScaleYAfterLanding}", f.minScaleYAfterLanding < 0.9f)
        assertEquals("settled", 1f, f.final.scaleY, 0.01f)
    }

    @Test
    fun heightAndDurationComeFromTheDistance() {
        val near = hop(SurfaceId.FLOOR, 110f)
        val far = hop(SurfaceId.FLOOR, 260f)
        assertTrue(
            "longer hop must take longer",
            (far.landTick - far.launchTick) > (near.landTick - near.launchTick),
        )
        val webFar = Tuning.Jump.BASE_S + Tuning.Jump.PER_DX_S * (260f - 92f)
        assertEquals(webFar, (far.landTick - far.launchTick) * dt, 0.03f)
        val up = hop(SurfaceId.SILL, 60f, fromX = 54f)
        assertTrue(
            "a climb should arc above its landing: ${up.peakElevation}",
            up.peakElevation > 10f,
        )
    }

    @Test
    fun theCatHangsNearTheApex() {
        val body = CatBody(seed = 2, startX = 92f)
        body.replacePlan(1, listOf(PlanStep.Hop(SurfaceId.FLOOR, 200f)))
        val heights = ArrayList<Float>()
        for (tick in 1..(120 * 3)) {
            body.step(dt)
            if (body.pose.clipName == "leap") heights.add(body.pose.elevation)
        }
        val peak = heights.max()
        val near = heights.count { it > peak * 0.9f }
        // A plain parabola spends 0.32 of its time above 90 percent of its height; the hang adds to
        // it.
        assertTrue("no hang: $near of ${heights.size}", near.toFloat() / heights.size > 0.4f)
    }

    @Test
    fun theSameCodeServesEveryHop() {
        val spots =
            listOf(
                SurfaceId.BED to 36f,
                SurfaceId.SILL to 60f,
                SurfaceId.COUCH to -100f,
                SurfaceId.POUF to -34f,
                SurfaceId.MID to 150f,
                SurfaceId.TOP to 154f,
            )
        for ((surface, x) in spots) {
            val f = hop(surface, x)
            assertEquals("hop to $surface", surface, f.final.surface)
            assertEquals("hop to $surface", World.surface(surface).y, f.final.y, 0.001f)
            assertTrue("hop to $surface crouched first", "crouch" in f.clips && "leap" in f.clips)
        }
    }

    @Test
    fun theCatFacesTheLandingSpotBeforeCrouching() {
        val body = CatBody(seed = 2, startX = 92f)
        body.replacePlan(1, listOf(PlanStep.Hop(SurfaceId.FLOOR, 10f)))
        var flippedBeforeLeap = false
        for (tick in 1..(120 * 2)) {
            body.step(dt)
            if (body.pose.clipName == "crouch" && body.pose.flip) flippedBeforeLeap = true
        }
        assertTrue(flippedBeforeLeap)
    }

    @Test
    fun theShadowStaysOnTheGroundAndShrinksInTheAir() {
        val body = CatBody(seed = 2, startX = 92f)
        body.replacePlan(1, listOf(PlanStep.Hop(SurfaceId.FLOOR, 200f)))
        val rest = body.pose.shadowScale
        var smallest = rest
        var faintest = body.pose.shadowAlpha
        for (tick in 1..(120 * 2)) {
            body.step(dt)
            assertEquals(
                "shadow follows the ground",
                body.pose.shadowY,
                World.floorY(body.pose.z),
                0.01f,
            )
            smallest = minOf(smallest, body.pose.shadowScale)
            faintest = minOf(faintest, body.pose.shadowAlpha)
        }
        assertTrue("shadow did not shrink", smallest < rest)
        assertTrue("shadow did not fade", faintest < Tuning.Shadow.ALPHA)
    }
}
