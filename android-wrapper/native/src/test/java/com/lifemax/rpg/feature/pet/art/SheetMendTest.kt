package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clip
import com.lifemax.rpg.feature.pet.body.Clips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two sheet mends of `tools/audit_cat_sprites.py`, over every shipped coat at every stage and
 * two genetic coats:
 * - [OutlineGaps]: no coat-shade pixel sits on the silhouette's edge next to the outline (the
 *   kittens' muzzle had one or two in nearly every frame);
 * - [RestingShade]: in a resting row no coat pixel changes shade between two frames whose 3x3
 *   neighbourhood is the same kind of pixel in every cell, and nothing but coat shades changed.
 *
 * The unmended art fails both, so the test cannot go blind.
 */
class SheetMendTest {
    private val art = RigTestSupport.art
    private val stages = listOf(0, 1, 2, 3, 4, 5, Growth.ADULT)
    private val fw = art.anims.frameWidth
    private val fh = art.anims.frameHeight
    private val resting = Clips.all.filter { it.isHoldable && !it.depthSheet }
    private val sideRows = Clips.all.filter { !it.depthSheet }

    private fun rows(stage: Int, clips: List<Clip>) =
        clips.map { art.growth.resolve(stage, it.name, art.anims)!! }.distinctBy { it.row }

    private fun coat(i: Int) = i in 20..80

    private fun kind(i: Int) =
        when {
            i == 0 -> 0
            i == 10 -> 1
            coat(i) -> 2
            else -> 3
        }

    private fun px(sheet: IndexSheet, anim: Anim, f: Int, x: Int, y: Int): Int =
        if (x < 0 || y < 0 || x >= fw || y >= fh) -1
        else sheet.indices[(anim.row * fh + y) * sheet.width + f * fw + x]

    /** Coat pixels on the edge next to the outline: the gaps. */
    private fun gaps(sheet: IndexSheet, anim: Anim): Int {
        var n = 0
        for (f in 0 until anim.frames) for (y in 0 until fh) for (x in 0 until fw) {
            if (!coat(px(sheet, anim, f, x, y))) continue
            val around =
                listOf(
                    px(sheet, anim, f, x, y - 1),
                    px(sheet, anim, f, x, y + 1),
                    px(sheet, anim, f, x - 1, y),
                    px(sheet, anim, f, x + 1, y),
                )
            if (around.any { it == 0 } && around.any { it == 10 }) n++
        }
        return n
    }

    private fun signature(sheet: IndexSheet, anim: Anim, f: Int, x: Int, y: Int): List<Int> =
        (-1..1).flatMap { dy ->
            (-1..1).map { dx ->
                px(sheet, anim, f, x + dx, y + dy).let { if (it < 0) 0 else kind(it) }
            }
        }

    /** Pixels whose shade differs between two frames that look the same around them. */
    private fun shimmer(sheet: IndexSheet, anim: Anim): Int {
        var n = 0
        for (y in 0 until fh) for (x in 0 until fw) {
            for (f in 0 until anim.frames) for (g in f + 1 until anim.frames) {
                val a = px(sheet, anim, f, x, y)
                val b = px(sheet, anim, g, x, y)
                if (
                    coat(a) &&
                        coat(b) &&
                        a != b &&
                        signature(sheet, anim, f, x, y) == signature(sheet, anim, g, x, y)
                )
                    n++
            }
        }
        return n
    }

    @Test
    fun shippedKittenAndAdultFacesHaveNoOutlineGapAfterTheMend() {
        var rawGaps = 0
        var rows = 0
        for (coat in art.legacyCoats) for (stage in stages) {
            val raw = art.sheet(coat, SheetKind.of(stage, false))!!
            val anims = rows(stage, sideRows)
            val closed = OutlineGaps.close(raw, art.anims, anims)
            for (anim in anims) {
                rawGaps += gaps(raw, anim)
                assertEquals("$coat stage $stage ${anim.name}", 0, gaps(closed, anim))
                rows++
            }
            // only coat pixels became outline, and closing twice changes nothing
            for (i in raw.indices.indices) {
                if (raw.indices[i] != closed.indices[i]) {
                    assertTrue(coat(raw.indices[i]) && closed.indices[i] == 10)
                }
            }
            assertSame(closed, OutlineGaps.close(closed, art.anims, anims))
        }
        assertTrue("rows $rows", rows > 4 * 7 * 30)
        assertTrue("the shipped art has $rawGaps gap pixels", rawGaps > 700)
    }

    @Test
    fun restingRowsHoldTheirCoatShadeWhereNothingMoves() {
        var raw = 0
        var rows = 0
        for (coat in art.legacyCoats) for (stage in stages) {
            val sheet = art.sheet(coat, SheetKind.of(stage, false))!!
            val anims = rows(stage, resting)
            val frozen = RestingShade.freeze(sheet, art.anims, anims)
            for (anim in anims) {
                raw += shimmer(sheet, anim)
                assertEquals("$coat stage $stage ${anim.name}", 0, shimmer(frozen, anim))
                rows++
            }
            for (i in sheet.indices.indices) {
                val a = sheet.indices[i]
                val b = frozen.indices[i]
                if (a != b) assertTrue("only coat shades change", coat(a) && coat(b))
            }
            assertSame(frozen, RestingShade.freeze(frozen, art.anims, anims))
        }
        assertEquals(4 * 7 * 7, rows)
        assertTrue("the shipped resting art shimmers in $raw pixel pairs", raw > 2000)
    }

    @Test
    fun theBlackAdultsSittingRowHadTheFlicker() {
        val sheet = art.sheet("black", SheetKind.of(Growth.ADULT, false))!!
        val idle = art.anims["idle_sit"]!!
        val changed = RestingShade.changes(sheet, art.anims, idle).size
        assertTrue("changed $changed", changed >= 40)
        val pairs = shimmer(sheet, idle)
        assertTrue("pairs $pairs", pairs >= 200)
        val frozen = RestingShade.freeze(sheet, art.anims, listOf(idle))
        assertEquals(0, shimmer(frozen, idle))
        // the blink still blinks: the eye pixels are untouched
        for (i in sheet.indices.indices) {
            if (!coat(sheet.indices[i])) assertEquals(sheet.indices[i], frozen.indices[i])
        }
    }

    @Test
    fun geneticCoatsAreMendedToo() {
        for (label in listOf("calico", "silver")) {
            val source = PageCoatSource()
            val bridge = FakeCoatBridge()
            source.bind(bridge.requester)
            val key = CoatFixtures.coats.getValue(label)
            for ((name, kind) in CoatFixtures.kinds) {
                source.sheet(key, kind)
                assertTrue(source.onReply(CoatFixtures.reply(label, name, bridge.last().id)))
            }
            val genetic = PetArtResources.load(source)
            for (stage in listOf(Growth.ADULT, 1)) {
                val raw = genetic.sheet(key, SheetKind.of(stage, false))!!
                val sheet =
                    RestingShade.freeze(
                        OutlineGaps.close(raw, art.anims, rows(stage, sideRows)),
                        art.anims,
                        rows(stage, resting),
                    )
                for (anim in rows(stage, resting)) {
                    assertEquals("$label $stage ${anim.name}", 0, shimmer(sheet, anim))
                }
                for (anim in rows(stage, sideRows)) assertEquals(0, gaps(sheet, anim))
            }
        }
    }
}
