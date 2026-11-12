package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The split path with a neutral light is today's [Recolour], pixel for pixel (no tolerance): every
 * shipped coat and fixture coat, every sheet, every frame of every clip, the sheets as the cat
 * draws them after the mends, and the rig's source frames. The lit path is not wired into the app;
 * this is its self-check.
 */
class LitRecolourIdentityTest {
    private val art = ShadeTestSupport.art

    private fun same(label: String, expected: ArgbImage, actual: ArgbImage) {
        val message = firstDifference(label, expected, actual)
        assertNull(message, message)
    }

    @Test
    fun everySheetIsIdenticalWhole() {
        for (c in ShadeTestSupport.all) {
            val split = ShadeSplit.split(c.sheet)
            val rect = FrameRect(0, 0, c.sheet.width, c.sheet.height)
            same(
                c.label,
                Recolour.sheet(c.sheet, c.palette),
                LitRecolour.region(split, c.palette, rect),
            )
        }
    }

    @Test
    fun everyFrameOfEveryClipIsIdentical() {
        var frames = 0
        for (c in ShadeTestSupport.shipped) {
            val split = ShadeSplit.split(c.sheet)
            val stage =
                when (val k = c.kind) {
                    is SheetKind.Stage -> k.index
                    is SheetKind.DepthStage -> k.index
                    else -> Growth.ADULT
                }
            val coat = c.label.substringBefore(' ')
            for (clip in Clips.all) {
                val anim = art.growth.resolve(stage, clip.name, art.anims)!!
                if (SheetKind.of(stage, anim.depth) != c.kind) continue
                for (f in 0 until anim.frames) {
                    val rect = art.anims.frameRect(anim, f)
                    same(
                        "$coat ${c.kind.key} ${clip.name}#$f",
                        Recolour.region(c.sheet, c.palette, rect),
                        LitRecolour.region(split, c.palette, rect),
                    )
                    frames++
                }
            }
        }
        assertTrue("only $frames frames compared", frames > 5_000)
    }

    @Test
    fun geneticFixtureFramesAreIdentical() {
        for (c in ShadeTestSupport.genetic) {
            val split = ShadeSplit.split(c.sheet)
            val anims = if (c.kind == SheetKind.Depth) art.anims.depth else art.anims.adult
            if (c.kind is SheetKind.Stage) continue
            for (anim in anims) for (f in 0 until anim.frames) {
                val rect = art.anims.frameRect(anim, f)
                same(
                    "${c.label} ${anim.name}#$f",
                    Recolour.region(c.sheet, c.palette, rect),
                    LitRecolour.region(split, c.palette, rect),
                )
            }
        }
    }

    @Test
    fun theSheetsAsTheCatDrawsThemAfterTheMendsAreIdentical() {
        val resting = Clips.all.filter { it.isHoldable && !it.depthSheet }
        val side = Clips.all.filter { !it.depthSheet }
        fun rows(stage: Int, clips: List<com.lifemax.rpg.feature.pet.body.Clip>) =
            clips.map { art.growth.resolve(stage, it.name, art.anims)!! }.distinctBy { it.row }
        for (coat in PetArtFixtures.coats) for (stage in listOf(0, 1, 2, 3, 4, 5, Growth.ADULT)) {
            val raw = art.sheet(coat, SheetKind.of(stage, false))!!
            val closed = OutlineGaps.close(raw, art.anims, rows(stage, side))
            val floored = RestingFloor.mend(closed, art.anims, rows(stage, resting))
            val sheet = RestingShade.freeze(floored, art.anims, rows(stage, resting))
            val palette = art.palette(coat)!!
            val split = ShadeSplit.split(sheet)
            assertEquals(sheet.indices.toList(), ShadeSplit.join(split).indices.toList())
            same(
                "mended $coat $stage",
                Recolour.sheet(sheet, palette),
                LitRecolour.region(split, palette, FrameRect(0, 0, sheet.width, sheet.height)),
            )
        }
    }

    @Test
    fun theRigSourceFramesAndTheComposedFramesAreIdentical() {
        val composer = RigCompose(RigFrames.FRAME_WIDTH, RigFrames.FRAME_HEIGHT)
        val cells = composer.canvasWidth * composer.canvasHeight
        val cases = RigTestSupport.web
        assertTrue(cases.size > 400)
        for (case in cases) {
            val (oldSource, frame) = RigTestSupport.source(case)
            val anim = art.growth.resolve(case.stage, case.anim, art.anims)!!
            val sheet = art.sheet(case.coat, SheetKind.of(case.stage, anim.depth))!!
            val palette = art.palette(case.coat)!!
            val newSource =
                LitRecolour.region(
                        ShadeSplit.split(sheet),
                        palette,
                        art.anims.frameRect(anim, case.frame),
                    )
                    .pixels
            assertEquals("$case source", oldSource.toList(), newSource.toList())
            val composed = IntArray(cells)
            composer.compose(newSource, frame, case.morph, case.pose, composed)
            assertEquals("$case composed", case.picture.toList(), composed.toList())
        }
    }

    @Test
    fun aRectOutsideTheSheetIsRefused() {
        val c = ShadeTestSupport.shipped.first()
        var refused = false
        try {
            LitRecolour.region(
                ShadeSplit.split(c.sheet),
                c.palette,
                FrameRect(0, 0, c.sheet.width + 1, 1),
            )
        } catch (e: IllegalArgumentException) {
            refused = true
        }
        assertTrue(refused)
    }
}
