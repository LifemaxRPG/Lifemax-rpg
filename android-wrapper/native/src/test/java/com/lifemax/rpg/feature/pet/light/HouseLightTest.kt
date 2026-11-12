package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.art.LightTint
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.room.HouseMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * What the house's light does to a cat (docs/specs/CAT_LIGHTING_PLAN.md section 4.3 b): a sun
 * patch, a moon patch, a sunny sill, the lamp at night, a tucked-away spot, the weather, and which
 * side a light is on.
 */
class HouseLightTest {
    private val floor = World.FLOOR_Y_ART

    @Before
    fun coreRooms() {
        HouseMap.reset()
    }

    private fun at(
        hour: Double,
        x: Float,
        surface: String = "floor",
        flip: Boolean = false,
        weather: Weather = Weather.CLEAR,
        y: Float = floor,
    ): CatTarget {
        val light = HouseLight()
        light.update(hour, weather)
        return CatTarget().also { light.target(x, y, surface, flip, it) }
    }

    @Test
    fun aCatInTheNoonPatchIsATonelighterAndWarm() {
        val t = at(12.0, LIVING_PATCH_X)
        assertEquals(2, t.halfSteps)
        assertEquals(LightTint.SUN, t.tint)
        assertEquals("patch", t.source)
        val out = at(12.0, LIVING_PATCH_X + 80f)
        assertEquals(0, out.halfSteps)
        assertEquals(LightTint.NONE, out.tint)
        // the bedroom has its own window patch; the kitchen's light does not reach its floor
        assertEquals(2, at(12.0, 60f).halfSteps)
        assertEquals(0, at(12.0, 300f).halfSteps)
    }

    @Test
    fun theMoonPatchIsHalfAToneAndCool() {
        val t = at(23.0, LIVING_PATCH_X + 30f)
        assertEquals(1, t.halfSteps)
        assertEquals(LightTint.MOON, t.tint)
    }

    @Test
    fun atDuskTheFloorHasNoPatchButTheSillGlowsOrange() {
        assertEquals(0, at(18.75, LIVING_PATCH_X).halfSteps)
        val sill = at(18.75, -100f, surface = "lsill")
        assertEquals(1, sill.halfSteps)
        assertEquals(LightTint.DUSK, sill.tint)
        assertEquals(LightTint.SUN, at(12.0, -100f, surface = "lsill").tint)
    }

    @Test
    fun theWeatherDimsTheSun() {
        val overcast = at(12.0, LIVING_PATCH_X, weather = Weather.OVERCAST)
        assertEquals(1, overcast.halfSteps)
        assertEquals("an overcast patch is grey", LightTint.NONE, overcast.tint)
        assertEquals(0, at(12.0, LIVING_PATCH_X, weather = Weather.RAIN).halfSteps)
        // a rainy night has no moon either, so a dark coat gets the night's own lift
        val rainyNight = at(23.0, LIVING_PATCH_X + 30f, weather = Weather.RAIN)
        assertEquals(0, rainyNight.halfSteps)
        assertEquals(LightTint.NIGHT, rainyNight.tint)
    }

    @Test
    fun theLampWarmsACatBesideItOnlyWhenTheRoomIsDark() {
        val night = at(23.0, LAMP_X + 4f)
        assertEquals(2, night.halfSteps)
        assertEquals(LightTint.LAMP, night.tint)
        assertEquals("floor-lamp", night.source)
        assertEquals(1, at(23.0, LAMP_X + 30f).halfSteps)
        assertEquals(0, at(23.0, LAMP_X + 60f).halfSteps)
        assertEquals(0, at(12.0, LAMP_X + 4f).halfSteps)
    }

    @Test
    fun aCatTuckedAwayIsATonedarker() {
        val box = at(12.0, 200f, surface = "box")
        assertEquals(-2, box.halfSteps)
        assertEquals("shade", box.source)
        assertEquals(-2, at(12.0, 200f, surface = "mid").halfSteps)
        assertEquals(0, at(12.0, 200f, surface = "couch").halfSteps)
    }

    @Test
    fun aDarkCoatGetsTheNightLiftOutOfAnyLight() {
        val t = at(2.0, 250f)
        assertEquals(0, t.halfSteps)
        assertEquals(LightTint.NIGHT, t.tint)
        assertEquals(LightTint.NONE, at(12.0, 250f).tint)
    }

    @Test
    fun aMirroredCatLitFromTheSideItWasNotDrawnLitFromIsFlattened() {
        // the living room's glass centre is at -98: a cat right of it has the light on its left
        val right = LIVING_PATCH_X + 20f
        assertFalse("facing right, lit from the left: as drawn", at(12.0, right).flatten)
        assertTrue("facing left, lit from the left", at(12.0, right, flip = true).flatten)
        val left = -105f
        assertTrue("facing right, lit from the right", at(12.0, left).flatten)
        assertFalse(
            "facing left, lit from the right: as drawn",
            at(12.0, left, flip = true).flatten,
        )
        assertFalse("a sill is lit from the front", at(12.0, -100f, surface = "lsill").flatten)
    }

    @Test
    fun thePatchSlidesInWholePixelsWithHysteresisAndTheVersionChangesRarely() {
        val light = HouseLight()
        val drifts = ArrayList<Int>()
        var versions = 0
        var lastVersion = -1
        for (minute in 0 until 24 * 60) {
            light.update(minute / 60.0, Weather.CLEAR)
            drifts.add(light.driftPx)
            if (light.version != lastVersion) versions++
            lastVersion = light.version
        }
        for (i in 1 until drifts.size) {
            assertTrue("a step of one pixel", kotlin.math.abs(drifts[i] - drifts[i - 1]) <= 1)
        }
        for (i in 2 until drifts.size) {
            assertFalse(
                "a-b-a at minute $i",
                drifts[i] == drifts[i - 2] && drifts[i] != drifts[i - 1],
            )
        }
        // 14 hours of 2.4 px, plus the day, dusk and night changes and the lamp's two steps
        assertTrue("$versions versions in a day", versions <= 34 + 8)
        // hovering on the half pixel holds the step
        val h = HouseLight()
        val edge = 12.0 + 0.5 / 2.4
        h.update(edge - 0.01, Weather.CLEAR)
        val first = h.driftPx
        repeat(50) { h.update(if (it % 2 == 0) edge + 0.02 else edge - 0.02, Weather.CLEAR) }
        assertEquals(first, h.driftPx)
    }

    private companion object {
        /** A floor x inside the living room's noon patch, and the lamp's x. */
        const val LIVING_PATCH_X = -80f
        const val LAMP_X = -172f
    }
}
