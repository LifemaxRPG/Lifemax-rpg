package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.CycleRig
import com.lifemax.rpg.feature.habits.domain.DogRig
import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.Gait
import com.lifemax.rpg.feature.habits.domain.StreetGait
import com.lifemax.rpg.feature.habits.domain.StreetLife
import com.lifemax.rpg.feature.habits.domain.StreetLooks
import com.lifemax.rpg.feature.habits.domain.StreetRig
import com.lifemax.rpg.feature.habits.presentation.scene.StreetSprites
import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreetSpritesTest {
    @Test
    fun theStreetWalkersStandAboutTwentySevenUnitsTallAndTheirFramesAreTheSameEveryTime() {
        for (look in 0 until StreetLooks.COUNT) {
            val h = StreetRig.height(look) * StreetLife.SIZE
            assertTrue("look $look is $h", h in 26f..30f)
            val a = StreetSprites.walker(look, 5).sprite
            val b = StreetSprites.walker(look, 5).sprite
            assertArrayEquals(a.pixels, b.pixels)
            assertEquals(a.ox, b.ox, 0f)
            // the picture stands on its feet: the origin is at its bottom, within its width
            assertTrue(a.h - a.oy < 4f && a.h - a.oy > -1f)
            assertTrue(a.ox > 0f && a.ox < a.w)
            assertTrue(
                "height ${a.h}",
                a.h in (24f * StreetSprites.PPU).toInt()..(31f * StreetSprites.PPU).toInt(),
            )
            assertTrue("width ${a.w}", a.w < 22f * StreetSprites.PPU)
        }
    }

    @Test
    fun thePlantedFootStaysWhereItLandsThroughTheStance() {
        // in the flat part of the near foot's stance the foot's point on the ground, with the
        // body's travel added, does not move
        for (look in 0 until StreetLooks.COUNT) {
            var ref = Float.NaN
            for (f in 6..11) {
                val j = StreetRig.figure(look, f / StreetGait.FRAMES.toFloat()).joints
                val world =
                    j.footNX + Gait.STRIDE * StreetLooks.ALL[look].height * f / StreetGait.FRAMES
                if (ref.isNaN()) ref = world
                assertEquals("look $look frame $f", ref, world, .12f)
                assertTrue(j.footNH < .3f)
            }
            // and the swinging foot clears the ground
            val swing = StreetRig.figure(look, 24 / StreetGait.FRAMES.toFloat()).joints
            assertTrue(swing.footNH > 1.2f)
        }
    }

    @Test
    fun theWalkIsAClosedLoopWithNoJumpBetweenFramesAndSettlesToStanding() {
        for (look in 0 until StreetLooks.COUNT) {
            var last = StreetRig.frame(look, StreetGait.FRAMES - 1).shapes
            for (f in 0 until StreetGait.FRAMES) {
                val cur = StreetRig.frame(look, f).shapes
                val a = FigBuilder.bounds(last)
                val b = FigBuilder.bounds(cur)
                for (i in 0 until 4) assertTrue(
                    "look $look frame $f edge $i",
                    abs(a[i] - b[i]) < 2.2f,
                )
                last = cur
            }
            val stand = StreetRig.frame(look, StreetRig.TOTAL_FRAMES - 1).joints
            assertTrue(abs(stand.footNX - stand.footFX) < 2.5f)
            assertTrue(stand.footNH < .3f && stand.footFH < .3f)
        }
        assertArrayEquals(StreetRig.STAND, StreetRig.SETTLE.last(), 1e-6f)
    }

    @Test
    fun theDogAndTheBicycleAreSmallAndDeterministicAndStandOnTheGround() {
        for (f in 0 until DogRig.TOTAL_FRAMES) {
            val b = FigBuilder.bounds(DogRig.frame(f))
            assertEquals(0f, b[3], 1e-3f)
            assertTrue("dog height ${-b[1]}", -b[1] in 5f..9f)
            assertTrue(b[2] - b[0] < 13f)
            assertArrayEquals(StreetSprites.dog(f).pixels, StreetSprites.dog(f).pixels)
        }
        for (f in 0 until StreetGait.CRANK_FRAMES) {
            val b = FigBuilder.bounds(CycleRig.frame(StreetSprites.RIDER, f))
            assertTrue("rider height ${-b[1]}", -b[1] in 20f..30f)
            assertTrue(b[2] - b[0] < 32f)
        }
        assertEquals(
            StreetGait.CRANK_TRAVEL,
            2f * Math.PI.toFloat() * StreetGait.WHEEL_R * 2.6f,
            1e-4f,
        )
    }

    @Test
    fun theNearCarsArePaintedWithLitLensesAtNightAndNothingOnTheBorder() {
        for (body in 0 until StreetLife.CAR_BODIES) {
            val c = StreetSprites.car(body, body)
            assertTrue(c.length in 55f..72f)
            assertTrue(c.wheelX0 < c.wheelX1)
            for (layer in listOf(c.day, c.night, c.lights)) {
                for (x in 0 until c.w) {
                    assertEquals(0, layer[x] ushr 24)
                    assertEquals(0, layer[(c.h - 1) * c.w + x] ushr 24)
                }
                for (y in 0 until c.h) {
                    assertEquals(0, layer[y * c.w] ushr 24)
                    assertEquals(0, layer[y * c.w + c.w - 1] ushr 24)
                }
            }
            // the head lamp's pixel is brighter by night than by day, the body darker
            val lampI = (c.lampY.toInt()) * c.w + (c.frontX - 2f * c.ppu).toInt()
            fun lum(p: Int) = (p shr 16 and 255) + (p shr 8 and 255) + (p and 255)
            assertTrue(lum(c.night[lampI]) > lum(c.day[lampI]))
            val bodyI = (c.groundY - 5f * c.ppu).toInt() * c.w + c.w / 2
            assertTrue(lum(c.night[bodyI]) < lum(c.day[bodyI]))
            assertArrayEquals(c.day, StreetSprites.car(body, body).day)
        }
    }

    @Test
    fun theStreetSetFitsItsBudget() {
        val walkers = StreetSprites.walkerBytes()
        assertTrue("walkers are $walkers bytes", walkers < 3 * 1024 * 1024)
        assertTrue(StreetSprites.bytes() < 6 * 1024 * 1024)
    }
}
