package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.room.FloorLight
import com.lifemax.rpg.feature.pet.room.HouseMap
import com.lifemax.rpg.feature.pet.room.LightTuning
import com.lifemax.rpg.feature.pet.room.RoomLayout
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The whole-pixel window light (docs/specs/CAT_LIGHTING_PLAN.md WP-L4 and section 7): the classic
 * quads' shape within a pixel, every pixel either lit or not, still while the light is still, and a
 * day that slides it one column at a time with nothing flicking back.
 */
class FloorMaskTest {
    private val width = RoomLayout.HOUSE_WIDTH
    private val height = FloorMask.HEIGHT

    @Before
    fun coreRooms() {
        HouseMap.reset()
    }

    private fun mask(
        hour: Double,
        weather: Weather = Weather.CLEAR,
        dither: Boolean = false,
    ): IntArray {
        val light = HouseLight()
        light.update(hour, weather)
        return IntArray(width * height).also { FloorMask.paint(light, it, width, dither) }
    }

    @Test
    fun theSolidMaskIsEveryPixelWhoseCentreIsInAPane() {
        for (hour in listOf(8.0, 12.0, 16.9, 22.0, 3.0)) {
            val light = HouseLight().also { it.update(hour, Weather.CLEAR) }
            val out = IntArray(width * height)
            FloorMask.paint(light, out, width, dither = false)
            val colour = if (hour in 7.5..17.5) FloorMask.DAY else FloorMask.NIGHT
            var lit = 0
            for (row in 0 until height) for (x in 0 until width) {
                val inside = FloorMask.inQuad(light, x, row + FloorMask.TOP)
                assertEquals("$hour $x,$row", if (inside) colour else 0, out[row * width + x])
                if (inside) lit++
            }
            assertTrue("$hour lit $lit", lit > 1000)
        }
        assertTrue("dusk has no patch", mask(18.75).all { it == 0 })
    }

    @Test
    fun theMaskIsTheClassicQuadWithinAPixel() {
        // the classic quad slides by a float; the mask by whole pixels: at most a pixel apart
        for (minute in (8 * 60) until (17 * 60) step 7) {
            val hour = minute / 60.0
            val light = HouseLight().also { it.update(hour, Weather.CLEAR) }
            val drift = FloorLight.drift(hour)
            assertTrue(kotlin.math.abs(light.driftPx - drift) <= 0.5f + 0.25f)
            val out = IntArray(width * height)
            FloorMask.paint(light, out, width, dither = false)
            for (row in 0 until height) for (x in 0 until width) {
                if (out[row * width + x] == 0) continue
                assertTrue("$hour $x,$row", nearQuad(x, row + FloorMask.TOP, drift))
            }
        }
    }

    @Test
    fun aStillLightIsByteIdenticalFrameToFrame() {
        val light = HouseLight()
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        light.update(13.2, Weather.CLEAR)
        FloorMask.paint(light, a, width, dither = true)
        val version = light.version
        repeat(60) {
            light.update(13.2 + it * 0.0001, Weather.CLEAR)
            FloorMask.paint(light, b, width, dither = true)
            assertArrayEquals(a, b)
        }
        assertEquals(version, light.version)
    }

    @Test
    fun aDaySweepNeverFlicksAPixelBackAndChangesLittlePerStep() {
        val light = HouseLight()
        val frames = ArrayDeque<IntArray>()
        var worst = 0
        for (minute in 0 until 24 * 60) {
            light.update(minute / 60.0, Weather.CLEAR)
            val now = IntArray(width * height)
            FloorMask.paint(light, now, width, dither = true)
            val prev = frames.lastOrNull()
            val older = if (frames.size >= 2) frames[frames.size - 2] else null
            if (prev != null) {
                var changed = 0
                for (i in now.indices) {
                    if (now[i] == prev[i]) continue
                    changed++
                    if (older != null)
                        assertTrue(
                            "a-b-a at minute $minute",
                            now[i] != older[i] || now[i] == prev[i],
                        )
                }
                val phase = minute == 330 || minute == 450 || minute == 1050 || minute == 1170
                if (!phase) worst = maxOf(worst, changed)
            }
            frames.addLast(now)
            if (frames.size > 2) frames.removeFirst()
        }
        // one pixel column of the slide is at most one pixel per pane row edge
        assertTrue("worst step changed $worst pixels", worst <= 2 * 2 * height * 2 * 2)
    }

    @Test
    fun ditherIsAnchoredToTheHouseGridAndOvercastIsAChecker() {
        val clear = mask(12.0, dither = true)
        val grey = mask(12.0, Weather.OVERCAST, dither = true)
        assertTrue(grey.count { it != 0 } < clear.count { it != 0 })
        for (row in 0 until height) for (x in 0 until width) {
            if (grey[row * width + x] != 0) assertEquals(0, (x + row + FloorMask.TOP) and 1)
        }
        assertTrue("rain has no patch", mask(12.0, Weather.RAIN, dither = true).all { it == 0 })
    }

    /** Whether pixel ([x], [row]) is within a pixel of the classic quad drawn at [drift]. */
    private fun nearQuad(x: Int, row: Int, drift: Float): Boolean {
        val l = LightTuning.LIGHT_LEFT
        val r = LightTuning.LIGHT_RIGHT
        val y = row + 0.5f
        val lean =
            LightTuning.LIGHT_LEAN * (y - LightTuning.LIGHT_TOP) /
                (LightTuning.LIGHT_BOTTOM - LightTuning.LIGHT_TOP)
        return HouseMap.current.floorLights.any { w ->
            val px = x + 0.5f - (w - RoomLayout.HOUSE_X0 + drift) - lean
            px >= l - 1f && px < r + 1f
        }
    }
}
