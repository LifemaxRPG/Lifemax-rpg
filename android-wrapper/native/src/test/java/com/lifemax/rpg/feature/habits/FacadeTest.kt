package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.BuildingSpec
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.RuinWindow
import com.lifemax.rpg.feature.habits.domain.floorWindows
import com.lifemax.rpg.feature.habits.domain.ruinWindow
import com.lifemax.rpg.feature.habits.presentation.scene.BuildingDetail
import com.lifemax.rpg.feature.habits.presentation.scene.DetailInk
import com.lifemax.rpg.feature.habits.presentation.scene.FacadeSprites
import com.lifemax.rpg.feature.habits.presentation.scene.FloorExtra
import com.lifemax.rpg.feature.habits.presentation.scene.Shapes
import com.lifemax.rpg.feature.habits.presentation.scene.WallTile
import com.lifemax.rpg.feature.habits.presentation.scene.facade
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The facades: each material's baked wall tile (the same every time, opaque, seamless, the
 * palette's colour on average, inside the budget) and the architecture worked out from the
 * building's and the floor's seeds (the same every time, sparse, and inside the floor's front).
 */
class FacadeTest {
    private val tiles: Map<HabitMaterial, WallTile> by lazy {
        HabitMaterial.entries.associateWith { FacadeSprites.tile(it) }
    }

    private fun channel(c: Int, shift: Int) = (c ushr shift) and 255

    @Test
    fun tilesAreTheSameEveryTimeOpaqueAndTheirStatedSize() {
        for (m in HabitMaterial.entries) {
            val a = tiles.getValue(m)
            val b = FacadeSprites.tile(m)
            assertArrayEquals(a.pixels, b.pixels)
            val (uw, uh) = FacadeSprites.tileUnits(m)
            assertEquals((uw * FacadeSprites.PPU).toInt(), a.w)
            assertEquals((uh * FacadeSprites.PPU).toInt(), a.h)
            assertEquals(a.w * a.h, a.pixels.size)
            assertTrue("$m opaque", a.pixels.all { (it ushr 24) == 255 })
        }
        // and five different walls
        val all = tiles.values.toList()
        for (i in all.indices) for (j in i + 1 until all.size) assertTrue(
            !all[i].pixels.contentEquals(all[j].pixels)
        )
    }

    @Test
    fun tilesRepeatWithoutASeam() {
        for (m in HabitMaterial.entries) {
            val t = tiles.getValue(m)
            // the step from the last column to the first is no bigger than the steps inside
            fun diff(x0: Int, x1: Int): Float {
                var sum = 0f
                for (y in 0 until t.h) {
                    val a = t.pixels[y * t.w + x0]
                    val b = t.pixels[y * t.w + x1]
                    for (s in intArrayOf(0, 8, 16)) sum += abs(channel(a, s) - channel(b, s))
                }
                return sum / t.h
            }
            fun diffRow(y0: Int, y1: Int): Float {
                var sum = 0f
                for (x in 0 until t.w) {
                    val a = t.pixels[y0 * t.w + x]
                    val b = t.pixels[y1 * t.w + x]
                    for (s in intArrayOf(0, 8, 16)) sum += abs(channel(a, s) - channel(b, s))
                }
                return sum / t.w
            }
            var inside = 0f
            for (x in 0 until t.w - 1) inside = maxOf(inside, diff(x, x + 1))
            var insideRows = 0f
            for (y in 0 until t.h - 1) insideRows = maxOf(insideRows, diffRow(y, y + 1))
            // (a panel's seam is a hard edge by design: the wrap is allowed to match it)
            assertTrue(
                "$m seam across ${diff(t.w - 1, 0)} vs $inside",
                diff(t.w - 1, 0) <= inside * 1.05f + 2f,
            )
            assertTrue(
                "$m seam down ${diffRow(t.h - 1, 0)} vs $insideRows",
                diffRow(t.h - 1, 0) <= insideRows * 1.05f + 2f,
            )
        }
    }

    @Test
    fun tilesKeepThePalettesWallColourOnAverage() {
        for (m in HabitMaterial.entries) {
            val t = tiles.getValue(m)
            val f = facade(m)
            var r = 0L
            var g = 0L
            var b = 0L
            for (c in t.pixels) {
                r += channel(c, 16)
                g += channel(c, 8)
                b += channel(c, 0)
            }
            val n = t.pixels.size.toFloat()
            val old =
                floatArrayOf(
                    (f.wallTop.red + f.wallBottom.red) / 2f,
                    (f.wallTop.green + f.wallBottom.green) / 2f,
                    (f.wallTop.blue + f.wallBottom.blue) / 2f,
                )
            val mean = floatArrayOf(r / n / 255f, g / n / 255f, b / n / 255f)
            for (i in 0..2) assertTrue(
                "$m channel $i ${mean[i]} vs ${old[i]}",
                abs(mean[i] - old[i]) < .11f,
            )
            // and it is a surface, not a flat colour
            var varied = 0
            for (c in t.pixels) if (abs(channel(c, 8) - mean[1] * 255f) > 6f) varied++
            assertTrue("$m varied $varied", varied > t.pixels.size / 20)
        }
    }

    @Test
    fun theWholePoolIsInsideItsBudget() {
        val bytes = tiles.values.sumOf { it.pixels.size * 4 }
        assertEquals(
            bytes + 2 * FacadeSprites.cloth().let { it.w * it.h * 4 },
            FacadeSprites.bytes(),
        )
        assertTrue("$bytes", FacadeSprites.bytes() < 1_500_000)
        val cloth = FacadeSprites.cloth()
        assertTrue(cloth.shade.all { (it and 0xFFFFFF) == 0 })
        assertTrue(cloth.light.all { (it and 0xFFFFFF) == 0xFFFFFF })
        assertTrue(cloth.shade.any { (it ushr 24) > 20 } && cloth.light.any { (it ushr 24) > 20 })
    }

    @Test
    fun choicesAreTheSameEveryTimeAndSparse() {
        var noPipe = 0
        var left = 0
        var stacks = 0
        val n = 400
        for (seed in 0 until n) {
            val a = BuildingDetail.choices(seed * 7919 + 13)
            val b = BuildingDetail.choices(seed * 7919 + 13)
            assertEquals(a.pipeSide, b.pipeSide)
            assertEquals(a.roof.dish, b.roof.dish)
            if (a.pipeSide == 0) noPipe++
            if (a.pipeSide < 0) left++
            if (a.roof.stack) stacks++
            assertTrue(abs(a.pipeX) < BuildingSpec.W / 2f)
        }
        assertTrue("$noPipe without a pipe", noPipe in n / 20..n / 3)
        assertTrue("$left on the left", left in n / 5..n * 3 / 5)
        assertTrue("$stacks stacks", stacks in n / 3..n * 3 / 4)
        var ac = 0
        var balcony = 0
        for (seed in 0 until n) {
            val e = BuildingDetail.extra(seed * 31 + 5, HabitMaterial.STONE, 4, 6)
            assertEquals(e, BuildingDetail.extra(seed * 31 + 5, HabitMaterial.STONE, 4, 6))
            if (e == FloorExtra.AIR_CON) ac++
            if (e == FloorExtra.BALCONY) balcony++
            // nothing stands in front of a window of a floor whose every window lights up
            assertEquals(FloorExtra.NONE, BuildingDetail.extra(seed, HabitMaterial.CLAY, 6, 6))
            assertEquals(FloorExtra.NONE, BuildingDetail.extra(seed, HabitMaterial.GLASS, 2, 6))
        }
        assertTrue("$ac air conditioners", ac in n / 25..n / 3)
        assertTrue("$balcony balconies", balcony in n / 25..n / 3)
    }

    private fun windows(h: Float, ruin: Boolean): FloatArray {
        val spots = floorWindows(h, ruin)
        val out = FloatArray(spots.size * 4)
        for ((k, s) in spots.withIndex()) {
            out[k * 4] = s.x
            out[k * 4 + 1] = s.y
            out[k * 4 + 2] = s.w
            out[k * 4 + 3] = s.h
        }
        return out
    }

    @Test
    fun aFloorsArchitectureStaysInsideItsFrontAndKeepsItsSills() {
        val half = BuildingSpec.W / 2f
        for (m in HabitMaterial.entries) for (h in BuildingSpec.HEIGHTS) for (pipe in
            floatArrayOf(Float.NaN, half - 4.5f, -(half - 4.5f))) for (seed in 0 until 12) {
            val ruin = m == HabitMaterial.FADE
            val height = if (ruin) BuildingSpec.RUIN else h
            val win = windows(height, ruin)
            val count = win.size / 4
            val top = -200f
            val bottom = top + height
            val wall = Shapes()
            val front = Shapes()
            val ruins = Array(count) { ruinWindow("k$seed", seed % 3, 3, it) }
            BuildingDetail.floor(
                m,
                ruin,
                top,
                bottom,
                win,
                count,
                lit = 2 + seed % 4,
                hall = 1,
                seed = seed * 101,
                storey = seed % 3,
                pipeX = pipe,
                ruins = if (ruin) ruins else null,
                wall = wall,
                front = front,
            )
            for (s in listOf(wall, front)) {
                val b = s.bounds() ?: continue
                // the string course reaches a unit past each edge, nothing more; nothing above the
                // floor's top or below its foot
                assertTrue("$m $h left ${b[0]}", b[0] >= -half - 1f - 1e-3f)
                assertTrue("$m $h right ${b[2]}", b[2] <= half + 1f + 1e-3f)
                assertTrue("$m $h top ${b[1]}", b[1] >= top - 1e-3f)
                assertTrue("$m $h bottom ${b[3]}", b[3] <= bottom + 1e-3f)
            }
            // a sill under every window, where the snow lands (its top at the pane's foot)
            val trim = wall.rects(DetailInk.TRIM)
            for (k in 0 until count) {
                val x = win[k * 4]
                val yb = top + win[k * 4 + 1] + win[k * 4 + 3]
                var sill = false
                for (i in 0 until trim.size / 4) if (
                    abs(trim[i * 4 + 1] - yb) < 1e-3f &&
                        trim[i * 4] < x &&
                        trim[i * 4] + trim[i * 4 + 2] > x + win[k * 4 + 2]
                )
                    sill = true
                assertTrue("$m window $k sill", sill)
            }
            // the string course is there, along the whole top
            var course = false
            for (i in 0 until trim.size / 4) if (
                abs(trim[i * 4 + 1] - top) < 1e-3f && trim[i * 4 + 2] >= BuildingSpec.W
            )
                course = true
            assertTrue(course)
            if (!pipe.isNaN() && m != HabitMaterial.GLASS && !ruin)
                assertTrue(wall.rects(DetailInk.IRON).size > 0)
            if (ruin) {
                // tags only on boarded windows
                for (p in front.lines(DetailInk.SPRAY)) {
                    val x = p[0]
                    val k =
                        (0 until count).first {
                            x >= win[it * 4] - 2f && x <= win[it * 4] + win[it * 4 + 2] + 2f
                        }
                    assertEquals(RuinWindow.BOARDED, ruins[k])
                }
            } else if (front.bounds() != null) {
                // what stands in front of a window stands in the last to light
                val k = count - 1
                val b = front.bounds()!!
                assertTrue(b[0] >= win[k * 4] - 3f - 1e-3f)
                assertTrue(b[2] <= win[k * 4] + win[k * 4 + 2] + 3f + 1e-3f)
            }
        }
    }

    @Test
    fun theCorniceAndTheLobbyStayInTheirBands() {
        val half = BuildingSpec.W / 2f
        for (m in HabitMaterial.entries) {
            val s = Shapes()
            BuildingDetail.cornice(m, -300f, s)
            val b = s.bounds()
            assertNotNull(b)
            assertTrue(b!![0] >= -half - 3f - 1e-3f && b[2] <= half + 3f + 1e-3f)
            assertTrue(b[1] >= -300f - BuildingSpec.CORNICE - 1e-3f && b[3] <= -300f + 1e-3f)
        }
        for (pipe in floatArrayOf(Float.NaN, half - 4.5f)) {
            val s = Shapes()
            BuildingDetail.lobby(-BuildingSpec.LOBBY_H, pipe, s)
            val b = s.bounds()!!
            assertTrue(b[0] >= -half - 1e-3f && b[2] <= half + 1e-3f)
            assertTrue(b[1] >= -BuildingSpec.LOBBY_H - 1e-3f && b[3] <= 1e-3f)
            assertEquals(!pipe.isNaN(), s.rects(DetailInk.IRON).size > 4)
        }
    }
}
