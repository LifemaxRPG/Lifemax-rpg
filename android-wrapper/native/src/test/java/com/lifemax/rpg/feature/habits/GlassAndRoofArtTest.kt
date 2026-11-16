package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.presentation.scene.AwningArt
import com.lifemax.rpg.feature.habits.presentation.scene.GlassMap
import com.lifemax.rpg.feature.habits.presentation.scene.GlassSprites
import com.lifemax.rpg.feature.habits.presentation.scene.RoofArt
import com.lifemax.rpg.feature.habits.presentation.scene.skyColors
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The glass's reflections, the awning's canvas and the roof's furniture: baked the same every time,
 * inside their budget, and each doing what it is for.
 */
class GlassAndRoofArtTest {
    private fun ch(c: Int, shift: Int) = (c shr shift) and 255

    @Test
    fun theReflectionsAreBakedOnceTheSameSeamlessAndAsWeights() {
        val sky = GlassSprites.sky()
        assertArrayEquals(sky.pixels, GlassSprites.sky().pixels)
        assertEquals(GlassSprites.SKY_W * GlassSprites.SKY_H, sky.pixels.size)
        // opaque, and its weights never add up to more than the whole pixel
        assertTrue(sky.pixels.all { (it ushr 24) == 255 })
        assertTrue(sky.pixels.all { ch(it, 16) + ch(it, 8) + ch(it, 0) <= 257 })
        // clouds in it, the horizon's band low in it, clear sky at its top
        assertTrue(sky.pixels.count { ch(it, 16) > 80 } > sky.pixels.size / 50)
        val w = sky.w
        assertTrue(
            (0 until w).all {
                ch(sky.pixels[(sky.h - 1) * w + it], 0) > 120 ||
                    ch(sky.pixels[(sky.h - 1) * w + it], 16) > 40
            }
        )
        assertTrue(
            (0 until w).all {
                ch(sky.pixels[it], 16) + ch(sky.pixels[it], 8) + ch(sky.pixels[it], 0) < 30
            }
        )
        // round: the last column runs into the first no harder than any column into the next
        fun step(y: Int, x: Int) =
            abs(ch(sky.pixels[y * w + (x + 1) % w], 16) - ch(sky.pixels[y * w + x], 16))
        val seam = (0 until sky.h).maxOf { step(it, w - 1) }
        val inner = (0 until sky.h).maxOf { y -> (0 until w - 1).maxOf { step(y, it) } }
        assertTrue("seam $seam, inner $inner", seam <= inner)

        val land = GlassSprites.land()
        assertArrayEquals(land.pixels, GlassSprites.land().pixels)
        assertEquals(GlassSprites.LAND_W * GlassSprites.LAND_H, land.pixels.size)
        val lw = land.w
        // clear at its top (it is clamped above), the near bank solid at its foot
        assertTrue((0 until lw).all { land.pixels[it] == 0 })
        assertTrue((0 until lw).all { (land.pixels[(land.h - 1) * lw + it] ushr 24) > 240 })
        // and solid all along under the horizon
        assertTrue(
            (0 until lw).all { (land.pixels[(GlassSprites.HORIZON + 1) * lw + it] ushr 24) > 240 }
        )
        // lights for the night (windows, lamps, a clock), water, the haze on far hills
        assertTrue(land.pixels.count { ch(it, 8) > 150 && (it ushr 24) > 100 } > 40)
        assertTrue(land.pixels.count { ch(it, 0) > 150 && (it ushr 24) > 200 } > 2000)
        assertTrue(land.pixels.count { ch(it, 16) > 150 && (it ushr 24) > 200 } > 1000)
        // mostly sky: little of what is over the horizon is covered, and the horizon is open
        // (nothing on it) along long stretches between the landmarks
        var covered = 0f
        var open = 0
        for (x in 0 until lw) {
            var col = 0f
            for (y in 0 until GlassSprites.HORIZON) col += (land.pixels[y * lw + x] ushr 24) / 255f
            covered += col
            if (col < .5f) open++
        }
        assertTrue("$covered", covered < .25f * lw * GlassSprites.HORIZON)
        assertTrue("$open", open > lw / 5)
        // round: its last column runs into its first
        assertTrue(
            (0 until land.h).all {
                abs((land.pixels[it * lw + lw - 1] ushr 24) - (land.pixels[it * lw] ushr 24)) < 40
            }
        )

        for (k in 0 until GlassSprites.INTERIORS) {
            val r = GlassSprites.interior(k)
            assertTrue(r.pixels.all { (it ushr 24) == 255 })
        }
        val g = GlassSprites.glint()
        // the glint's rim is clear, so a clamped brush adds nothing past it
        for (i in 0 until g.w) {
            assertEquals(0, g.pixels[i])
            assertEquals(0, g.pixels[(g.h - 1) * g.w + i])
        }
        assertTrue((g.pixels[(g.h / 2) * g.w + g.w / 2] ushr 24) > 240)
    }

    @Test
    fun aFacadesPanesShowOneContinuousReflectionThatSlidesAsTheViewPans() {
        val a = FloatArray(4)
        val b = FloatArray(4)
        val land = GlassMap.landShift(1234f, 400f, 380f)
        val sky = GlassMap.skyShift(400f, 380f, 12f)
        // two neighbouring panes on one floor, 28 units apart across the facade: the mirrored
        // world they show is exactly that far apart, in the land and in the sky alike
        val left = -40f
        val spacing = 28f
        assertEquals(
            spacing,
            GlassMap.worldX(left + spacing, land) - GlassMap.worldX(left, land),
            0f,
        )
        GlassMap.place(left, 110f, land, sky, a)
        GlassMap.place(left + spacing, 110f, land, sky, b)
        fun turn(d: Float, w: Int) = ((d % w) + w) % w
        assertEquals(spacing / GlassMap.K_LAND, turn(b[0] - a[0], GlassSprites.LAND_W), 1e-3f)
        assertEquals(spacing / GlassMap.K_SKY_X, turn(b[2] - a[2], GlassSprites.SKY_W), 1e-3f)
        // and the same rows: no pane is raised or turned on its own
        assertEquals(a[1], b[1], 0f)
        assertEquals(a[3], b[3], 0f)
        // a pane a floor up meets the sheet a floor's height further up, and the horizon lies at
        // one height over the pavement on every facade
        GlassMap.place(left, 170f, land, sky, b)
        assertEquals(60f / GlassMap.K_LAND, a[1] - b[1], 1e-3f)
        assertEquals(60f / GlassMap.K_SKY_Y, a[3] - b[3], 1e-3f)
        GlassMap.place(0f, GlassMap.HORIZON_Z, 0f, 0f, b)
        assertEquals(GlassSprites.HORIZON.toFloat(), b[1], 1e-4f)
        // the view pans: the sheet slides against the facade, slower than the facade (the land is
        // far off) and the sky slower still
        val panned = GlassMap.landShift(1234f, 400f, 440f)
        assertEquals(-60f * GlassMap.LAND_SLIDE, panned - land, 1e-3f)
        GlassMap.place(left, 110f, panned, GlassMap.skyShift(400f, 440f, 12f), b)
        assertNotEquals(a[0], b[0], .5f)
        assertNotEquals(a[2], b[2], .5f)
        // the clouds drift with the clock, the land does not
        GlassMap.place(left, 110f, land, GlassMap.skyShift(400f, 380f, 22f), b)
        assertNotEquals(a[2], b[2], .5f)
        assertEquals(a[0], b[0], 0f)
        // neighbours differ only a hair in brightness
        val sheens = (0 until 200).map { GlassMap.sheen(it) }
        assertTrue(sheens.all { abs(it - 1f) <= .03f })
        // about half the panes show a room behind them, a few a blind pulled down
        val rooms = (0 until 400).count { GlassMap.interior(it) in 0 until GlassSprites.BLIND }
        val blinds = (0 until 400).count { GlassMap.interior(it) == GlassSprites.BLIND }
        assertTrue("$rooms", rooms in 140..220)
        assertTrue("$blinds", blinds in 10..50)
        // the glint sits toward the sun from the view's middle, higher for a higher sun
        assertTrue(GlassMap.glintX(200f, 0f) in 1f..199f)
        assertTrue(GlassMap.glintZ(.8f) > GlassMap.glintZ(.2f))
    }

    @Test
    fun theGlassTakesTheHoursColoursAndTheNightsLights() {
        val day = FloatArray(24)
        val night = FloatArray(24)
        GlassMap.colours(skyColors(12f), day)
        GlassMap.colours(skyColors(23f), night)
        // the sky overhead is blue by day and far darker at night
        assertTrue(day[11] > day[9] + .1f)
        assertTrue(night[9] + night[10] + night[11] < (day[9] + day[10] + day[11]) / 2f)
        // the windows and lamps out there are warm at night and stand out of the dark
        assertTrue(night[15] > night[17] + .2f)
        assertTrue(night[15] > night[21] + .3f)
        // the far hills moonlit, paler than the near dark; the water darker than by day
        assertTrue(night[12] + night[13] + night[14] > night[21] + night[22] + night[23] + .1f)
        assertTrue(night[18] + night[19] + night[20] < day[18] + day[19] + day[20] - .3f)
    }

    @Test
    fun theAwningIsCanvasOnAFrameColouredPerShop() {
        val c = AwningArt.canopy()
        val v = AwningArt.valance()
        assertArrayEquals(c.pixels, AwningArt.canopy().pixels)
        assertEquals(AwningArt.W * AwningArt.CANOPY_PX, c.pixels.size)
        // both stripes, cream and colour, in the cloth
        assertTrue(c.pixels.count { (it ushr 24) > 200 && ch(it, 0) > 100 } > c.pixels.size / 6)
        assertTrue(c.pixels.count { (it ushr 24) > 200 && ch(it, 16) > 100 } > c.pixels.size / 6)
        // the cloth sags between its arms and the hem is scalloped below it
        assertTrue(AwningArt.front(0f + 11.375f - 92f + 1f) > AwningArt.front(-92f + 1f) + .5f)
        val hems = (0 until 180).map { AwningArt.hem(-90f + it) }
        assertTrue(hems.maxOrNull()!! - hems.minOrNull()!! > 3f)
        assertTrue(v.pixels.count { (it ushr 24) > 200 } > v.pixels.size / 3)
        // the colour matrix gives the shop's colour for a fully coloured, unlit pixel
        val m = FloatArray(20)
        AwningArt.matrix(.2f, .5f, .6f, 1f, 1f, 1f, m)
        assertEquals(.2f * 255f, m[0] * 255f + m[4], .01f)
        assertEquals(.6f * 255f, m[10] * 255f + m[14], .01f)
        assertEquals(1f, m[18], 0f)
    }

    @Test
    fun theWaterTowerIsLitFromTheSunsSideAndTheWholePoolIsInsideItsBudget() {
        val sky = RoofArt.tank(0)
        val left = RoofArt.tank(-1)
        val right = RoofArt.tank(1)
        assertArrayEquals(sky.pixels, RoofArt.tank(0).pixels)
        fun bright(p: IntArray, w: Int, from: Int, to: Int): Long {
            var s = 0L
            for (k in p.indices) {
                val x = k % w
                if (x in from until to && (p[k] ushr 24) > 200) s += ch(p[k], 16) + ch(p[k], 8)
            }
            return s
        }
        val w = left.w
        assertTrue(bright(left.pixels, w, 0, w / 2) > bright(right.pixels, w, 0, w / 2))
        assertTrue(bright(right.pixels, w, w / 2, w) > bright(left.pixels, w, w / 2, w))
        // the same silhouette in all three (the painter mixes them)
        for (k in sky.pixels.indices) assertEquals(sky.pixels[k] ushr 30, left.pixels[k] ushr 30)
        for (p in
            listOf(RoofArt.hvac(), RoofArt.dish(), RoofArt.stack(), RoofArt.planter())) assertTrue(
            p.pixels.count { (it ushr 24) > 200 } > p.pixels.size / 8
        )
        val bytes = GlassSprites.bytes() + RoofArt.bytes() + AwningArt.bytes()
        assertTrue("$bytes", bytes < 900_000)
        assertEquals(
            3 * sky.w * sky.h * 4 +
                listOf(RoofArt.hvac(), RoofArt.dish(), RoofArt.stack(), RoofArt.planter()).sumOf {
                    it.w * it.h * 4
                },
            RoofArt.bytes(),
        )
    }
}
