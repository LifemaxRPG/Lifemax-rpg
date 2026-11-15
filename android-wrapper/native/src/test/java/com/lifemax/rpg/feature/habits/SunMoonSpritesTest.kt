package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.MoonPhase
import com.lifemax.rpg.feature.habits.presentation.scene.SkyLayer
import com.lifemax.rpg.feature.habits.presentation.scene.SunMoonSprites
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sun and the moon are painted once by pure Kotlin: the same pixels every time, white at every
 * alpha so the hour tints them, nothing on the sprite's border, the sun brighter in its middle than
 * at its rim, the moon's seas darker than its highlands, its phase right on known dates and its
 * terminator on the right side while waxing, and the whole inside its memory budget.
 */
class SunMoonSpritesTest {
    private val sun by lazy { SunMoonSprites.sun() }
    private val moon by lazy { SunMoonSprites.moon() }

    private fun sunLayers() = listOf(sun.disc, sun.core, sun.limb, sun.rays, sun.corona, sun.glow)

    private fun moonLayers() = listOf(moon.body, moon.high, moon.halo)

    private fun alpha(l: SkyLayer, x: Int, y: Int) = l.pixels[y * l.size + x] ushr 24

    /** The alpha at ([dx], [dy]) dp from the middle. */
    private fun at(l: SkyLayer, dx: Float, dy: Float): Int {
        val mid = (l.size - 1) / 2f
        return alpha(l, (mid + dx * l.pxPerDp + .5f).toInt(), (mid + dy * l.pxPerDp + .5f).toInt())
    }

    @Test
    fun spritesAreTheSameEveryTime() {
        val again = SunMoonSprites.sun()
        assertArrayEquals(sun.disc.pixels, again.disc.pixels)
        assertArrayEquals(sun.corona.pixels, again.corona.pixels)
        assertArrayEquals(sun.rays.pixels, again.rays.pixels)
        assertArrayEquals(moon.high.pixels, SunMoonSprites.moon().high.pixels)
        assertArrayEquals(SunMoonSprites.moonShade(5).pixels, SunMoonSprites.moonShade(5).pixels)
    }

    @Test
    fun layersAreWhiteTintableAndClearOfTheBorder() {
        val all =
            sunLayers() +
                moonLayers() +
                listOf(SunMoonSprites.moonShade(0), SunMoonSprites.moonShade(8))
        for (l in all) {
            assertEquals(l.size * l.size, l.pixels.size)
            assertTrue(l.pixels.all { (it ushr 24) == 0 || (it and 0xFFFFFF) == 0xFFFFFF })
            assertTrue(l.pixels.any { (it ushr 24) > 120 })
            for (i in 0 until l.size) {
                assertEquals(0, alpha(l, i, 0))
                assertEquals(0, alpha(l, i, l.size - 1))
                assertEquals(0, alpha(l, 0, i))
                assertEquals(0, alpha(l, l.size - 1, i))
            }
        }
    }

    @Test
    fun sunHasLimbDarkeningAFeatheredDiscAndSoftRays() {
        val r = SunMoonSprites.SUN_R
        // the disc is solid inside and gone just outside, with a soft edge between
        assertEquals(255, at(sun.disc, 0f, 0f))
        assertEquals(255, at(sun.disc, r * .9f, 0f))
        assertEquals(0, at(sun.disc, r + 1.5f, 0f))
        val edge = at(sun.disc, r, 0f)
        assertTrue("edge $edge", edge in 40..220)
        // brighter in the middle than near the rim
        val centre = at(sun.core, 0f, 0f)
        val rim = at(sun.core, r * .92f, 0f)
        assertTrue("centre $centre rim $rim", centre > 220 && rim < centre * .7f)
        // the rays and the corona thin out with distance, and never step hard
        assertTrue(at(sun.corona, r + 2f, 0f) > at(sun.corona, r + 20f, 0f))
        assertTrue(at(sun.corona, r + 20f, 0f) > at(sun.corona, r + 50f, 0f))
        for (l in listOf(sun.rays, sun.corona, sun.glow)) {
            var steepest = 0
            for (y in 0 until l.size) for (x in 0 until l.size - 1) steepest =
                maxOf(steepest, abs(alpha(l, x, y) - alpha(l, x + 1, y)))
            assertTrue("steepest $steepest", steepest < 90)
        }
    }

    @Test
    fun moonHasSeasHighlandsAndBrightCraters() {
        val r = SunMoonSprites.MOON_R
        assertEquals(255, at(moon.body, 0f, 0f))
        assertEquals(0, at(moon.body, r + 1.5f, 0f))
        var low = 255
        var high = 0
        for (y in 0 until moon.high.size) for (x in 0 until moon.high.size) {
            val mid = (moon.high.size - 1) / 2f
            val dx = (x - mid) / moon.high.pxPerDp
            val dy = (y - mid) / moon.high.pxPerDp
            if (dx * dx + dy * dy < r * r * .6f) {
                low = minOf(low, alpha(moon.high, x, y))
                high = maxOf(high, alpha(moon.high, x, y))
            }
        }
        // the seas show the body through the highlands; the craters are the brightest
        assertTrue("seas $low", low < 120)
        assertTrue("craters $high", high > 235)
        // Mare Imbrium (upper left) is a sea, the south is highland
        assertTrue(at(moon.high, -.3f * r, -.36f * r) < at(moon.high, .3f * r, .55f * r))
    }

    @Test
    fun moonPhaseIsRightOnKnownDates() {
        // full moon 2026-03-03 (11:38 UTC) and 2026-01-03; new moon 2026-03-19 and 2026-11-09
        assertEquals(.5f, MoonPhase.fraction("2026-03-03"), .035f)
        assertEquals(.5f, MoonPhase.fraction("2026-01-03"), .035f)
        fun fromNew(day: String) = MoonPhase.fraction(day).let { minOf(it, 1f - it) }
        assertTrue(fromNew("2026-03-19") < .035f)
        assertTrue(fromNew("2026-11-09") < .035f)
        assertTrue(fromNew("2000-01-06") < .035f)
        // a week after new the moon is waxing towards first quarter; a week after full, waning
        assertTrue(MoonPhase.fraction("2026-03-26") in 0.2f..0.3f)
        assertTrue(MoonPhase.fraction("2026-03-10") in 0.7f..0.8f)
        assertTrue(MoonPhase.fraction("2026-03-27") > MoonPhase.fraction("2026-03-26"))
        assertEquals(0f, MoonPhase.illumination(0f), 1e-6f)
        assertEquals(1f, MoonPhase.illumination(.5f), 1e-6f)
        assertEquals(.5f, MoonPhase.illumination(.25f), 1e-6f)
        assertEquals(16, MoonPhase.step(.5f))
        assertEquals(0, MoonPhase.step(.99f))
        assertEquals(8, MoonPhase.step(.25f))
        // a day that will not parse is a full moon, not a crash
        assertEquals(.5f, MoonPhase.fraction(""), 0f)
    }

    @Test
    fun terminatorIsOnTheRightSideWhileWaxingAndGoneAtFull() {
        val r = SunMoonSprites.MOON_R
        val first = SunMoonSprites.moonShade(MoonPhase.STEPS / 4)
        // first quarter: the left half dark, the right half lit
        assertTrue(at(first, -.5f * r, 0f) > 230)
        assertTrue(at(first, .5f * r, 0f) < 10)
        assertTrue(at(first, -.5f * r, .5f * r) > 230)
        val last = SunMoonSprites.moonShade(MoonPhase.STEPS * 3 / 4)
        assertTrue(at(last, .5f * r, 0f) > 230)
        assertTrue(at(last, -.5f * r, 0f) < 10)
        // a waxing crescent is lit on the right edge only
        val crescent = SunMoonSprites.moonShade(MoonPhase.STEPS / 8)
        assertTrue(at(crescent, .9f * r, 0f) < 40)
        assertTrue(at(crescent, 0f, 0f) > 200)
        // full: nothing dark, limb included; new: all dark
        val full = SunMoonSprites.moonShade(MoonPhase.STEPS / 2)
        assertTrue(full.pixels.all { (it ushr 24) < 8 })
        val new = SunMoonSprites.moonShade(0)
        assertTrue(at(new, 0f, 0f) > 250 && at(new, .9f * r, 0f) > 250)
        // the terminator is soft: no hard step across it, anywhere inside the disc
        var steepest = 0
        val mid = (first.size - 1) / 2f
        val inside = (r * .9f * first.pxPerDp).let { it * it }
        for (y in 0 until first.size) for (x in 0 until first.size - 1) {
            if ((x - mid) * (x - mid) + (y - mid) * (y - mid) > inside) continue
            steepest = maxOf(steepest, abs(alpha(first, x, y) - alpha(first, x + 1, y)))
        }
        assertTrue("steepest $steepest", steepest < 120)
    }

    @Test
    fun poolIsInsideItsBudget() {
        val shade = SunMoonSprites.moonShade(0)
        val bytes = sun.bytes() + moon.bytes() + shade.bytes
        assertEquals(bytes, SunMoonSprites.bytes())
        assertTrue("sun and moon are $bytes bytes", bytes < 640 * 1024)
        // the layers cover what they are drawn at: the sun's rays reach three radii, the glow wide
        assertTrue(sun.disc.dp / 2f > SunMoonSprites.SUN_R + 2f)
        assertTrue(sun.rays.dp / 2f >= SunMoonSprites.SUN_R * 3f)
        assertTrue(sun.glow.dp > 300f)
        assertTrue(moon.body.dp / 2f > SunMoonSprites.MOON_R + 2f)
    }
}
