package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin recolour against the pixels the web page itself holds after `petInit` / `petLoadStage`
 * / `petDepthStageSheet` (recorded by `tools/gen_pet_art_fixtures.js`). Pixel-identical, ARGB int
 * for ARGB int.
 */
class PetArtFidelityTest {
    private val art = PetArtResources.load()
    private val coats = PetArtFixtures.coats

    private fun recoloured(coat: String, kind: SheetKind): ArgbImage =
        Recolour.sheet(art.sheet(coat, kind)!!, art.palette(coat)!!)

    private fun assertSame(label: String, expected: ArgbImage, actual: ArgbImage) {
        val message = firstDifference(label, expected, actual)
        assertTrue(message ?: "", message == null)
    }

    @Test
    fun everyFrameOfEveryAdultRowIsIdenticalForAllFourCoats() {
        for (coat in coats) assertSame(
            "adult $coat",
            PetArtFixtures.image("adult-$coat"),
            recoloured(coat, SheetKind.Adult),
        )
    }

    @Test
    fun theDepthRowsAreIdenticalForAllFourCoats() {
        for (coat in coats) assertSame(
            "depth $coat",
            PetArtFixtures.image("depth-$coat"),
            recoloured(coat, SheetKind.Depth),
        )
    }

    @Test
    fun sampledKittenStagesAreIdentical() {
        for ((coat, stage) in PetArtFixtures.sampledStage) {
            assertSame(
                "stage$stage $coat",
                PetArtFixtures.image("stage$stage-$coat"),
                recoloured(coat, SheetKind.Stage(stage)),
            )
        }
    }

    @Test
    fun everyDepthKittenStageIsIdenticalForAllFourCoats() {
        for (coat in coats) for (stage in 0..5) {
            assertSame(
                "dstage$stage $coat",
                PetArtFixtures.image("dstage$stage-$coat"),
                recoloured(coat, SheetKind.DepthStage(stage)),
            )
        }
    }

    @Test
    fun firstMiddleAndLastFrameOfEveryRowCutOutByTheTableMatchTheWeb() {
        for (coat in coats) {
            val web = PetArtFixtures.image("adult-$coat")
            for (anim in art.anims.adult) for (frame in
                listOf(0, anim.frames / 2, anim.frames - 1)) {
                val got = art.frame(coat, Growth.ADULT, anim.name, frame)!!
                val r = art.anims.frameRect(anim, frame)
                val want =
                    IntArray(r.width * r.height) { web[r.x + it % r.width, r.y + it / r.width] }
                assertSame("$coat ${anim.name}#$frame", ArgbImage(r.width, r.height, want), got)
            }
        }
    }

    @Test
    fun depthRowsComeFromTheDepthSheet() {
        val web = PetArtFixtures.image("depth-tuxedo")
        for (anim in art.anims.depth) for (frame in 0 until anim.frames) {
            val got = art.frame("tuxedo", Growth.ADULT, anim.name, frame)!!
            val r = art.anims.frameRect(anim, frame)
            val want = IntArray(r.width * r.height) { web[r.x + it % r.width, r.y + it / r.width] }
            assertSame("depth ${anim.name}#$frame", ArgbImage(r.width, r.height, want), got)
        }
    }

    @Test
    fun aKittenDrawingAnimationItHasNoFootRowForUsesIdleSit() {
        // 'scratch' has no entry in the kitten tables: the web draws the idle_sit row of the kitten
        // sheet
        val web = PetArtFixtures.image("stage2-gray")
        val idle = art.anims["idle_sit"]!!
        val r = art.anims.frameRect(idle, 3)
        val want = IntArray(r.width * r.height) { web[r.x + it % r.width, r.y + it / r.width] }
        assertSame(
            "gray kitten scratch",
            ArgbImage(r.width, r.height, want),
            art.frame("gray", 2, "scratch", 3)!!,
        )
    }

    @Test
    fun kittenDepthRowsComeFromTheDepthKittenSheet() {
        val web = PetArtFixtures.image("dstage4-black")
        val anim = art.anims["walk_back"]!!
        val r = art.anims.frameRect(anim, 5)
        val want = IntArray(r.width * r.height) { web[r.x + it % r.width, r.y + it / r.width] }
        assertSame(
            "black kitten walk_back",
            ArgbImage(r.width, r.height, want),
            art.frame("black", 4, "walk_back", 5)!!,
        )
    }

    @Test
    fun theRoomItemAtlasMatchesThePagesCanvas() {
        val web = PetArtFixtures.image("room-items")
        val ours = art.room.image
        assertEquals(web.width, ours.width)
        assertEquals(web.height, ours.height)
        for (i in web.pixels.indices) {
            val w = web.pixels[i]
            val o = ours.pixels[i]
            val alphaW = w ushr 24
            assertEquals("alpha at $i", alphaW, o ushr 24)
            // a canvas holds premultiplied colour, so a half-clear pixel may be off by a rounding
            // step; solid ones are exact
            if (alphaW == 255) assertEquals("pixel $i", w, o)
            else if (alphaW > 0)
                for (shift in intArrayOf(16, 8, 0)) {
                    assertTrue(
                        "colour at $i",
                        Math.abs(((w ushr shift) and 255) - ((o ushr shift) and 255)) <= 2,
                    )
                }
        }
    }

    @Test
    fun everyRoomSpriteIsCutOutWithTheWebsMeta() {
        val meta = PetArtFixtures.web.getJSONObject("roomMeta")
        assertEquals(meta.keys().asSequence().toSet(), art.room.names.toSet())
        for (name in art.room.names) {
            val a = meta.getJSONArray(name)
            val sprite = art.room.sprite(name)!!
            assertEquals(
                name,
                listOf(a.getInt(2), a.getInt(3)),
                listOf(sprite.width, sprite.height),
            )
            assertEquals(
                name,
                FrameRect(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3)),
                art.room.rect(name),
            )
            assertEquals(name, art.room.image[a.getInt(0), a.getInt(1)], sprite[0, 0])
        }
        assertNull(art.room.sprite("no_such_sprite"))
        assertNotNull(art.room.sprite("bed_back"))
    }
}
