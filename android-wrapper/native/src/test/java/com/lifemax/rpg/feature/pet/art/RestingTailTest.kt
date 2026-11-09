package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clips
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tail of a resting cat (sitting, loafing, asleep) as native draws it: every frame of every
 * resting row, for the four shipped coats at every growth stage and two genetic coats, after
 * [RestingFloor] has mended the sheet, and through the rig at every tail and curl step the body can
 * ask for. Measured against the row's own frames, which are the art's ground truth:
 * - floor: no frame reaches below the pixel row most of the row's frames end on (a sunk tail is a
 *   lump under the cat that is there in one frame and gone in the next);
 * - bulge: the drawn area of the band lying on the floor (the [RestingFloor.BAND_ROWS] rows up to
 *   the floor) is at most [BULGE_PIXELS] more than the row's median frame;
 * - closed outline: the lowest drawn pixel of every column of the band is the outline colour;
 * - rig: a resting row's tail is drawn, not rigged, so no tail or curl step changes its pixels.
 *
 * The shipped art breaks the first two in four frames (a stage-2 kitten's `idle_sit` and
 * `idle_sit_w`, stage 4 `sleep`, adult `sleep_w`); [theArtReallyHadTheLump] keeps this test honest.
 */
class RestingTailTest {
    private val art = RigTestSupport.art
    private val stages = listOf(0, 1, 2, 3, 4, 5, Growth.ADULT)
    private val resting = Clips.all.filter { it.isHoldable && !it.depthSheet }

    private fun anims(stage: Int): List<Anim> =
        resting.map { art.growth.resolve(stage, it.name, art.anims)!! }.distinctBy { it.row }

    private fun mended(sheet: IndexSheet, stage: Int) =
        RestingFloor.mend(sheet, art.anims, anims(stage))

    /** The measurements of one row of [sheet]; the failures are added to [failures]. */
    private fun check(label: String, sheet: IndexSheet, anim: Anim, failures: MutableList<String>) {
        val fw = art.anims.frameWidth
        val fh = art.anims.frameHeight
        fun index(f: Int, x: Int, y: Int) =
            sheet.indices[(anim.row * fh + y) * sheet.width + f * fw + x]
        val lows =
            IntArray(anim.frames) { f ->
                (fh - 1 downTo 0).firstOrNull { y -> (0 until fw).any { index(f, it, y) != 0 } }
                    ?: -1
            }
        val floor = lows.groupBy { it }.maxBy { it.value.size }.key
        val top = floor - RestingFloor.BAND_ROWS + 1
        val areas =
            IntArray(anim.frames) { f ->
                var n = 0
                for (y in top until fh) for (x in 0 until fw) if (index(f, x, y) != 0) n++
                n
            }
        val median = areas.sorted()[anim.frames / 2]
        for (f in 0 until anim.frames) {
            val where = "$label ${anim.name}#$f"
            if (lows[f] > floor) failures += "$where reaches row ${lows[f]} below the floor $floor"
            if (areas[f] > median + BULGE_PIXELS) {
                failures += "$where band of ${areas[f]} px, the row's median is $median"
            }
            for (x in 0 until fw) {
                val y = (fh - 1 downTo top).firstOrNull { index(f, x, it) != 0 } ?: continue
                if (index(f, x, y) != OUTLINE_INDEX) failures += "$where open outline at ($x,$y)"
            }
        }
    }

    @Test
    fun everyRestingFrameOfEveryShippedCoatSitsOnItsFloor() {
        val failures = ArrayList<String>()
        var rows = 0
        for (coat in art.legacyCoats) for (stage in stages) {
            val sheet = mended(art.sheet(coat, SheetKind.of(stage, false))!!, stage)
            for (anim in anims(stage)) {
                check("$coat stage $stage", sheet, anim, failures)
                rows++
            }
        }
        assertEquals("4 coats x 7 stages x 7 resting rows", 4 * 7 * 7, rows)
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun geneticCoatsSitOnTheirFloorToo() {
        val failures = ArrayList<String>()
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
                val sheet = mended(genetic.sheet(key, SheetKind.of(stage, false))!!, stage)
                for (anim in anims(stage)) check("$label stage $stage", sheet, anim, failures)
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun theArtReallyHadTheLump() {
        val failures = ArrayList<String>()
        for ((stage, name) in SUNK) {
            val anim = art.growth.resolve(stage, name, art.anims)!!
            val raw = art.sheet("black", SheetKind.of(stage, false))!!
            assertEquals(
                "$name at stage $stage",
                listOf(2),
                RestingFloor.sunkFrames(raw, art.anims, anim),
            )
            check("raw", raw, anim, failures)
            assertEquals(
                emptyList<Int>(),
                RestingFloor.sunkFrames(mended(raw, stage), art.anims, anim),
            )
        }
        assertTrue("the unmended art fails both measures", failures.size >= 2 * SUNK.size)
    }

    @Test
    fun theMendChangesOnlyTheSunkFramesBand() {
        val raw = art.sheet("tuxedo", SheetKind.of(2, false))!!
        val sheet = mended(raw, 2)
        val idle = art.anims["idle_sit"]!!
        val frame2 = art.anims.frameRect(idle, 2)
        val changed = raw.indices.indices.filter { raw.indices[it] != sheet.indices[it] }
        assertTrue("something was mended", changed.isNotEmpty())
        val rowOf = { i: Int -> i / sheet.width }
        val colOf = { i: Int -> i % sheet.width }
        for (i in changed) {
            val inIdleOrIdleW =
                rowOf(i) / art.anims.frameHeight in listOf(idle.row, art.anims["idle_sit_w"]!!.row)
            assertTrue("pixel $i outside a resting row", inIdleOrIdleW)
            assertTrue("pixel $i outside frame 2", colOf(i) / frame2.width == 2)
            assertTrue("pixel $i above the band", rowOf(i) % frame2.height >= 41)
        }
        // a sheet with nothing to mend is not copied
        val adult = art.sheet("tuxedo", SheetKind.of(Growth.ADULT, false))!!
        val loaf = listOf(art.anims["loaf"]!!)
        assertTrue(RestingFloor.mend(adult, art.anims, loaf) === adult)
    }

    @Test
    fun noTailOrCurlStepChangesARestingFrame() {
        val composer = RigCompose(RigFrames.FRAME_WIDTH, RigFrames.FRAME_HEIGHT)
        val cells = composer.canvasWidth * composer.canvasHeight
        val still = IntArray(cells)
        val moved = IntArray(cells)
        val pose = RigPose()
        val steps =
            (-RigGrid.TAIL_RANGE..RigGrid.TAIL_RANGE).map { it to 0 } +
                (-RigGrid.CURL_RANGE..RigGrid.CURL_RANGE).map { 0 to it } +
                listOf(
                    RigGrid.TAIL_RANGE to RigGrid.CURL_RANGE,
                    -RigGrid.TAIL_RANGE to -RigGrid.CURL_RANGE,
                    RigGrid.TAIL_RANGE to -RigGrid.CURL_RANGE,
                    -RigGrid.TAIL_RANGE to RigGrid.CURL_RANGE,
                )
        val morphs =
            listOf(
                RigMorph(whisker = 1.2),
                RigMorph(tail = 0.6, body = 1.1),
                RigMorph(tail = 1.4, leg = 1.2, bob = true),
            )
        var composed = 0
        for (coat in listOf("black", "ginger")) for (stage in listOf(2, 4, Growth.ADULT)) {
            val raw = art.sheet(coat, SheetKind.of(stage, false))!!
            val sheet = mended(raw, stage)
            val rig = RigTestSupport.library.kind(SheetKind.of(stage, false).key)!!
            val palette = art.palette(coat)!!
            for (anim in anims(stage)) {
                val index = rig.indexOf(anim.name)
                if (index < 0) continue
                for (f in 0 until anim.frames) {
                    val frame = rig.frame(index, f)
                    val src = Recolour.region(sheet, palette, art.anims.frameRect(anim, f)).pixels
                    for (morph in morphs) {
                        composer.compose(src, frame, morph, RigPose(), still)
                        for ((tail, curl) in steps) {
                            pose.tail = tail * RigGrid.TAIL_STEP_DEG
                            pose.tailCurl = curl * RigGrid.CURL_STEP_DEG
                            composer.compose(src, frame, morph, pose, moved)
                            composed++
                            assertTrue(
                                "$coat stage $stage ${anim.name}#$f tail $tail curl $curl",
                                still.contentEquals(moved),
                            )
                        }
                    }
                }
            }
        }
        assertTrue("composed $composed", composed > 10_000)
    }

    private companion object {
        /** A frame's floor band may carry this many pixels more than the row's median frame. */
        const val BULGE_PIXELS = 4

        /** Palette slot of the outline colour ([RigCompose.OUTLINE]). */
        const val OUTLINE_INDEX = 10

        /** The rows whose frame 2 the shipped art sinks through the floor. */
        val SUNK =
            listOf(2 to "idle_sit", 2 to "idle_sit_w", 4 to "sleep", Growth.ADULT to "sleep_w")
    }
}
