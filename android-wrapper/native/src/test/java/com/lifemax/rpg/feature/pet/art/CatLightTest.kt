package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clips
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WP-L3 of docs/specs/CAT_LIGHTING_PLAN.md at the art level: the light key, the lit tables (whole
 * tones, the dither's half tone, the wash, the flattening and the virtual tones past the ramp), the
 * lit rows the app draws and the rig's lit frames. The neutral light is today's [Recolour], pixel
 * for pixel, on every path the app takes.
 */
class CatLightTest {
    private val art = ShadeTestSupport.art

    @Test
    fun theNeutralTableIsThePaletteOnEveryShippedAndFixtureCoat() {
        for ((label, palette) in ShadeTestSupport.palettes) {
            val look = CoatLook.of(palette)
            val table = LitRecolour.table(palette, CatLight.NEUTRAL, look)
            for (i in 0 until 256) assertEquals("$label $i", palette.argb(i), table[i])
            val key = LitRecolour.table(palette, CatLightKey.light(CatLightKey.NEUTRAL, look), look)
            assertArrayEquals(label, table, key)
        }
    }

    @Test
    fun everySheetLitByTheNeutralKeyIsTodaysPicture() {
        for (c in ShadeTestSupport.all) {
            val rect = FrameRect(0, 0, c.sheet.width, c.sheet.height)
            val look = CoatLook.of(c.palette)
            assertArrayEquals(
                c.label,
                Recolour.sheet(c.sheet, c.palette).pixels,
                LitRecolour.region(c.sheet, c.palette, rect, CatLightKey.light(0, look), look)
                    .pixels,
            )
        }
    }

    @Test
    fun theNightLiftLeavesAnOrdinaryCoatAsItIs() {
        // the night tint only touches a low-contrast coat; on any other the key draws today's art
        val key = CatLightKey.pack(0, LightTint.NIGHT)
        for (coat in listOf("ginger", "gray")) {
            val palette = art.palette(coat)!!
            val look = CoatLook.of(palette)
            assertTrue(coat, CatLightKey.light(key, look).neutral)
        }
        // black and tuxedo share the low-contrast black coat ramp
        for (coat in listOf("black", "tuxedo")) {
            assertFalse(coat, CatLightKey.light(key, CoatLook.of(art.palette(coat)!!)).neutral)
        }
    }

    @Test
    fun aKeyRoundTripsAndNeutralIsZero() {
        assertEquals(0, CatLightKey.pack(0, LightTint.NONE, false))
        for (h in CatLightKey.MIN_HALF_STEPS..CatLightKey.MAX_HALF_STEPS) for (t in
            LightTint.entries) for (f in listOf(false, true)) {
            val key = CatLightKey.pack(h, t, f)
            assertTrue(key in 0 until CatLightKey.COUNT)
            assertEquals(h, CatLightKey.halfSteps(key))
            assertEquals(t, CatLightKey.tint(key))
            assertEquals(f, CatLightKey.flatten(key))
            if (key != 0) assertEquals(key, CatLightKey.withFlatten(key, f))
        }
        assertEquals(
            CatLightKey.MAX_HALF_STEPS,
            CatLightKey.halfSteps(CatLightKey.pack(9, LightTint.SUN)),
        )
        assertEquals("+1.5 sun flat", CatLightKey.label(CatLightKey.pack(3, LightTint.SUN, true)))
        assertEquals("-1", CatLightKey.label(CatLightKey.pack(-2, LightTint.NONE)))
    }

    @Test
    fun aHalfStepIsACheckerOnTheFramesOwnGrid() {
        val palette = art.palette("ginger")!!
        val sheet = IndexSheet(4, 2, IntArray(8) { 30 })
        val lit =
            LitRecolour.region(sheet, palette, FrameRect(0, 0, 4, 2), CatLight(half = 1)).pixels
        for (y in 0 until 2) for (x in 0 until 4) {
            val expected = if ((x + y) % 2 == 0) palette.argb(40) else palette.argb(30)
            assertEquals("$x,$y", expected, lit[y * 4 + x])
        }
        // a frame rect starts on an even pixel, so every frame's checker is the same
        for (anim in art.anims.adult + art.anims.depth) for (f in 0 until anim.frames) {
            val r = art.anims.frameRect(anim, f)
            assertEquals("${anim.name}#$f", 0, (r.x + r.y) % 2)
        }
    }

    @Test
    fun aDarkCoatTakesASheenNotAShadowFromItsOutline() {
        val black = art.palette("black")!!
        val look = CoatLook.of(black)
        assertEquals(1, look.downGain)
        val sheet = IndexSheet(2, 1, intArrayOf(20, 50))
        val rect = FrameRect(0, 0, 2, 1)
        val down =
            LitRecolour.region(sheet, black, rect, CatLight(shadeOffset = -1, virtual = true))
        // the black outline is lighter than the coat's darkest: no virtual tone toward it
        assertEquals(black.argb(20), down.pixels[0])
        val up =
            LitRecolour.region(
                sheet,
                black,
                rect,
                CatLight(shadeOffset = 1, virtual = true, wash = 0),
            )
        val top = black.argb(50)
        assertTrue(CoatLook.luminanceOf(up.pixels[1]) > CoatLook.luminanceOf(top))
    }

    @Test
    fun anOrdinaryCoatGoesATonePastItsDarkestTowardTheOutline() {
        val ginger = art.palette("ginger")!!
        val look = CoatLook.of(ginger)
        assertTrue(CoatLook.luminanceOf(ginger.argb(10)) < look.luminance(ShadeFamily.COAT, 0))
        val out =
            LitRecolour.region(
                IndexSheet(1, 1, intArrayOf(20)),
                ginger,
                FrameRect(0, 0, 1, 1),
                CatLight(shadeOffset = -1, virtual = true),
            )
        assertTrue(CoatLook.luminanceOf(out.pixels[0]) < look.luminance(ShadeFamily.COAT, 0))
        assertTrue(CoatLook.luminanceOf(out.pixels[0]) > CoatLook.luminanceOf(ginger.argb(10)))
    }

    @Test
    fun flatteningPlaysDownTheBakedLightAndKeepsTheMiddle() {
        val gray = art.palette("gray")!!
        val sheet = IndexSheet(4, 1, intArrayOf(20, 30, 40, 50))
        val out =
            LitRecolour.region(sheet, gray, FrameRect(0, 0, 4, 1), CatLight(flatten = true)).pixels
        assertArrayEquals(
            intArrayOf(gray.argb(30), gray.argb(30), gray.argb(40), gray.argb(40)),
            out,
        )
    }

    @Test
    fun aWashMovesColoursTowardTheLightAndLeavesClearAlone() {
        val gray = art.palette("gray")!!
        val sheet = IndexSheet(3, 1, intArrayOf(0, 40, 150))
        val sun = LightTint.SUN
        val out =
            LitRecolour.region(
                    sheet,
                    gray,
                    FrameRect(0, 0, 3, 1),
                    CatLight(wash = (sun.wash shl 24) or sun.rgb),
                )
                .pixels
        assertEquals(0, out[0])
        for (i in 1..2) {
            val src = gray.argb(sheet.indices[i])
            assertNotEquals(src, out[i])
            val r0 = (src shr 16) and 255
            val r1 = (out[i] shr 16) and 255
            assertTrue("red moves toward the sun's", r1 >= r0)
            assertEquals(0xFF, out[i] ushr 24)
        }
    }

    @Test
    fun litRowsAreTheLitSheetRowForRowAndAreMadeOnce() {
        val palette = art.palette("tuxedo")!!
        val side = art.sheet("tuxedo", SheetKind.Adult)!!
        val depth = art.sheet("tuxedo", SheetKind.Depth)!!
        val h = art.anims.frameHeight
        var now = 0L
        val rows = LitRows(side, depth, palette, h, capacity = 3) { now++ }
        val key = CatLightKey.pack(3, LightTint.LAMP, flatten = true)
        val look = CoatLook.of(palette)
        for (anim in listOf(art.anims.adult[0], art.anims.depth[0])) {
            val top = anim.row * h
            val slot = rows.slotFor(anim.depth, top, key)
            assertTrue(rows.wasBaked)
            val sheet = if (anim.depth) depth else side
            val expected =
                LitRecolour.region(
                    sheet,
                    palette,
                    FrameRect(0, top, sheet.width, h),
                    CatLightKey.light(key, look),
                )
            val got = rows.pixels(slot)
            for (y in 0 until h) for (x in 0 until rows.width) {
                val e = if (x < sheet.width) expected.pixels[y * sheet.width + x] else 0
                assertEquals("${anim.name} $x,$y", e, got[y * rows.width + x])
            }
            assertEquals(slot, rows.slotFor(anim.depth, top, key))
            assertFalse("a kept row is not made again", rows.wasBaked)
        }
        assertEquals(2L, rows.bakes)
        assertEquals(-1, LitRows(side, null, palette, h).slotFor(true, 0, key))
        assertTrue(rows.residentBytes <= 3L * rows.width * h * 4)
    }

    @Test
    fun aRowBakeIsCheap() {
        val palette = art.palette("ginger")!!
        val side = art.sheet("ginger", SheetKind.Adult)!!
        val h = art.anims.frameHeight
        val rows = LitRows(side, null, palette, h, capacity = 4)
        // warm the tables and the JIT, then time a fresh row per key
        repeat(3) { rows.slotFor(false, 0, CatLightKey.pack(1, LightTint.SUN)) }
        var worst = 0L
        for (k in 1..20) {
            val key = CatLightKey.pack(k % 7 - 3, LightTint.entries[k % 6], k % 2 == 0)
            if (key == 0) continue
            val anim = art.anims.adult[k % art.anims.adult.size]
            rows.slotFor(false, anim.row * h, key)
            if (rows.wasBaked) worst = maxOf(worst, rows.lastBakeNanos)
        }
        println("REPORT lit row bake: worst ${worst / 1000} us for ${side.width}x$h")
        assertTrue("a row took ${worst / 1_000_000.0} ms", worst < 50_000_000L)
    }

    @Test
    fun theRigWithTheNeutralKeyIsTheRigAsBefore() {
        val morph = RigMorph(ear = 1.15, tail = 1.2, body = 1.06)
        val grids =
            listOf(
                RigGrid(),
                RigGrid().apply { setSteps(2, -1, 0, 3, 0, 1) },
                RigGrid().apply { setSteps(-4, 4, 2, -6, 5, -2) },
            )
        var compared = 0
        for (coat in PetArtFixtures.coats) for (stage in listOf(Growth.ADULT, 2)) {
            val set = RigTestSupport.set(coat, stage, morph)
            val old = RigFrames()
            val now = RigFrames()
            for (clip in 0 until set.clipCount) {
                if (!set.has(clip)) continue
                for (f in 0 until set.frames(clip)) for (grid in grids) {
                    val a = old.slotFor(set, clip, f, grid)
                    val b = now.slotFor(set, clip, f, grid, CatLightKey.NEUTRAL)
                    assertEquals(a, b)
                    if (a >= 0) {
                        assertArrayEquals("$coat $stage $clip#$f", old.pixels(a), now.pixels(b))
                        compared++
                    }
                }
            }
        }
        assertTrue("compared $compared", compared > 1000)
    }

    @Test
    fun aLitRigFrameIsComposedFromTheLitArtAndKeptApart() {
        val set = RigTestSupport.set("ginger", Growth.ADULT, RigMorph(ear = 1.1))
        val clip = Clips.IDLE_SIT.id
        val grid = RigGrid().apply { setSteps(2, -1, 0, 3, 0, 1) }
        val frames = RigFrames()
        val plain = frames.slotFor(set, clip, 0, grid)
        val plainPixels = frames.pixels(plain).copyOf()
        val key = CatLightKey.pack(2, LightTint.SUN)
        val lit = frames.slotFor(set, clip, 0, grid, key)
        assertNotEquals(plain, lit)
        assertTrue(frames.wasComposed)
        assertFalse(plainPixels.contentEquals(frames.pixels(lit)))
        assertArrayEquals("the plain slot is untouched", plainPixels, frames.pixels(plain))
        assertEquals(lit, frames.slotFor(set, clip, 0, grid, key))
        assertFalse(frames.wasComposed)
        assertArrayEquals(set.source(clip, 0), set.source(clip, 0, CatLightKey.NEUTRAL))
        // the most distant key still makes a distinct cache key (no overflow)
        val last = CatLightKey.pack(CatLightKey.MAX_HALF_STEPS, LightTint.entries.last(), true)
        val far = frames.slotFor(set, clip, 0, grid, last)
        assertTrue(far >= 0)
    }
}
