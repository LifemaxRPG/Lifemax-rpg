package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pixel fidelity of a genetic coat, end to end as the phone does it: the page's answer to `coat`
 * (recorded by `tools/gen_pet_coat_fixtures.js`) goes through [PageCoatSource], [PetArt] and
 * [Recolour], and the result is compared, ARGB int for ARGB int, with the canvas the room itself
 * draws that coat from (the page's `rt.sheets`, `rt.dsheets`, `rt.gs` after `petDnaEnsureSheets`
 * and `petLoadStage`). Four coats cover the composer's branches: a tortoiseshell with white
 * spotting, a colour-point, a long-haired white-spotted tabby and a silver classic tabby.
 */
class CoatFidelityTest {
    private val labels = CoatFixtures.coats.keys.sorted()

    /** A source that has been asked for and answered every recorded sheet of [label]. */
    private fun art(label: String): PetArt {
        val bridge = FakeCoatBridge()
        val source = PageCoatSource().also { it.bind(bridge.requester) }
        val key = CoatFixtures.coats.getValue(label)
        for ((name, kind) in CoatFixtures.kinds) {
            source.sheet(key, kind)
            assertTrue(source.onReply(CoatFixtures.reply(label, name, bridge.last().id)))
        }
        return PetArtResources.load(source)
    }

    @Test
    fun fourCoatsAreFound() {
        assertEquals(listOf("calico", "longwhite", "point", "silver"), labels)
    }

    @Test
    fun everySheetOfEveryCoatRecoloursToTheRoomsOwnCanvas() {
        for (label in labels) {
            val art = art(label)
            val key = CoatFixtures.coats.getValue(label)
            for ((name, kind) in CoatFixtures.kinds) {
                val sheet = art.sheet(key, kind)
                assertNotNull("$label $name", sheet)
                val palette = art.palette(key)
                assertNotNull("$label palette", palette)
                val message =
                    firstDifference(
                        "$label $name",
                        CoatFixtures.room(label, name),
                        Recolour.sheet(sheet!!, palette!!),
                    )
                assertTrue(message ?: "", message == null)
            }
        }
    }

    @Test
    fun sampleFramesOfEveryCoatMatchTheRoomsCanvas() {
        // frames as the cat draws them: an adult walk and sit, a kitten's idle, the depth rows
        val samples =
            listOf(
                Triple("walk", Growth.ADULT, "adult"),
                Triple("idle_sit", Growth.ADULT, "adult"),
                Triple("groom", Growth.ADULT, "adult"),
                Triple("sleep", Growth.ADULT, "adult"),
                Triple("walk_front", Growth.ADULT, "depth"),
                Triple("walk_back", Growth.ADULT, "depth"),
                Triple("idle_sit", 1, "st1"),
            )
        for (label in labels) {
            val art = art(label)
            val key = CoatFixtures.coats.getValue(label)
            for ((anim, stage, sheetName) in samples) {
                val room = CoatFixtures.room(label, sheetName)
                val a = art.growth.resolve(stage, anim, art.anims)!!
                for (frame in 0 until a.frames) {
                    val rect = art.anims.frameRect(a, frame)
                    val want =
                        ArgbImage(
                            rect.width,
                            rect.height,
                            IntArray(rect.width * rect.height) {
                                room.pixels[
                                        (rect.y + it / rect.width) * room.width +
                                            rect.x +
                                            it % rect.width]
                            },
                        )
                    val got = art.frame(key, stage, anim, frame)
                    assertNotNull("$label $anim", got)
                    val message = firstDifference("$label $anim #$frame", want, got!!)
                    assertTrue(message ?: "", message == null)
                }
            }
        }
    }

    @Test
    fun theCoatsReallyDifferAndUseTheirBranchesColours() {
        val rooms = labels.associateWith { CoatFixtures.room(it, "adult").pixels }
        for (a in labels) for (b in labels) if (a < b)
            assertTrue("$a = $b", !rooms[a]!!.contentEquals(rooms[b]!!))
        fun palette(label: String) = art(label).palette(CoatFixtures.coats.getValue(label))!!
        // tortoiseshell patches (75, 85) and a colour-point's dark points (220..) have colours only
        // where the composer used them
        assertTrue(palette("calico").argb(75) != 0 && palette("calico").argb(85) != 0)
        assertTrue(palette("point").argb(220) != 0 && palette("point").argb(230) != 0)
        // the long-haired coat's sheet carries fringe pixels the plain tabby sheet does not (a
        // different index layout)
        val longSheet =
            art("longwhite").sheet(CoatFixtures.coats.getValue("longwhite"), SheetKind.Adult)!!
        assertTrue(longSheet.indices.any { it == 10 })
    }
}
