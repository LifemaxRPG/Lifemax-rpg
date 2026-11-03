package com.lifemax.rpg.feature.pet.art

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Kotlin rig composer against the web's `petRigCompose`, pixel for pixel: the recorded set
 * (`tools/gen_pet_rig_fixtures.js`) covers every adult animation, the four legacy coats, a kitten
 * stage and the depth rows, in five poses (neutral, ears flat, ears swivelled, the tail turned both
 * ways) and four sets of proportions. Beside it, properties the web composer has and native must
 * keep: a cat of plain proportions in no pose is the sheet frame untouched, and the native
 * additions (a blend of flat ears, a bend of the tail, a whisker factor) agree with the web's
 * values at their ends.
 */
class RigComposeFidelityTest {
    private val composer = RigCompose(RigFrames.FRAME_WIDTH, RigFrames.FRAME_HEIGHT)
    private val cells = composer.canvasWidth * composer.canvasHeight

    private fun compose(src: IntArray, frame: RigFrame, morph: RigMorph, pose: RigPose) =
        IntArray(cells).also { composer.compose(src, frame, morph, pose, it) }

    private fun describe(expected: IntArray, actual: IntArray, label: String): String? {
        var bad = 0
        var first = -1
        for (i in expected.indices) {
            if (expected[i] != actual[i]) {
                if (first < 0) first = i
                bad++
            }
        }
        if (bad == 0) return null
        val w = composer.canvasWidth
        return "$label: $bad pixels differ; first at (${first % w},${first / w}): expected " +
            "%08x, got %08x".format(expected[first], actual[first])
    }

    @Test
    fun theRecordedWebRendersAreReproducedExactly() {
        val cases = RigTestSupport.web
        assertTrue("fixtures not loaded", cases.size > 400)
        val failures = ArrayList<String>()
        for (case in cases) {
            val (src, frame) = RigTestSupport.source(case)
            describe(case.picture, compose(src, frame, case.morph, case.pose), case.toString())
                ?.let { failures.add(it + " pose=${case.pose.earLeft}/${case.pose.tail}") }
        }
        assertTrue(
            "${failures.size} of ${cases.size} differ:\n" + failures.take(12).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    @Test
    fun theSampleReallyMovesThings() {
        // guards the fixtures: a pose that changed nothing would prove nothing
        val moved =
            RigTestSupport.web
                .filter { it.kind == "adult" && it.coat == "ginger" && it.anim == "idle_sit" }
                .map { it.picture.contentHashCode() }
                .toSet()
        assertTrue("only ${moved.size} different pictures of idle_sit", moved.size >= 6)
    }

    @Test
    fun plainProportionsAndNoPoseDrawTheSheetFrameUntouched() {
        val art = RigTestSupport.art
        val plain = RigMorph.NEUTRAL
        val pose = RigPose()
        var checked = 0
        val kinds = listOf(-1) + (0..5)
        for (stage in kinds) for (depth in listOf(false, true)) {
            val kind = SheetKind.of(stage, depth)
            val rig = RigTestSupport.library.kind(kind.key)!!
            for (anim in (if (depth) art.anims.depth else art.anims.adult)) {
                val index = rig.indexOf(anim.name)
                for (f in 0 until anim.frames) {
                    val src =
                        Recolour.region(
                                art.sheet("ginger", kind)!!,
                                art.palette("ginger")!!,
                                art.anims.frameRect(anim, f),
                            )
                            .pixels
                    val got = compose(src, rig.frame(index, f), plain, pose)
                    val w = composer.canvasWidth
                    val want = IntArray(cells)
                    for (y in 0 until RigFrames.FRAME_HEIGHT) for (x in
                        0 until RigFrames.FRAME_WIDTH) {
                        want[(y + RigCompose.MARGIN_TOP) * w + x + RigCompose.MARGIN_LEFT] =
                            src[y * RigFrames.FRAME_WIDTH + x]
                    }
                    describe(want, got, "${kind.key} ${anim.name}#$f")?.let {
                        throw AssertionError(it)
                    }
                    checked++
                }
            }
        }
        assertEquals(1204, checked)
    }

    private fun idle(): Pair<IntArray, RigFrame> {
        val art = RigTestSupport.art
        val anim = art.anims["idle_sit"]!!
        val src = art.frame("ginger", Growth.ADULT, "idle_sit", 0)!!.pixels
        val rig = RigTestSupport.library.kind("adult")!!
        return src to rig.frame(rig.indexOf(anim.name), 0)
    }

    private fun walk(): Pair<IntArray, RigFrame> {
        val art = RigTestSupport.art
        val src = art.frame("ginger", Growth.ADULT, "walk", 0)!!.pixels
        val rig = RigTestSupport.library.kind("adult")!!
        return src to rig.frame(rig.indexOf("walk"), 0)
    }

    @Test
    fun aBlendOfFlatEarsMeetsTheWebAtBothEnds() {
        val (src, frame) = idle()
        val m = RigMorph(ear = 1.6, earWidth = 1.2)
        val web = RigPose().apply { flat = 1.0 }
        val none = RigPose()
        assertArrayEquals(
            compose(src, frame, m, web),
            compose(src, frame, m, RigPose().apply { flat = 1.0 }),
        )
        val half = compose(src, frame, m, RigPose().apply { flat = 0.5 })
        assertFalse(half.contentEquals(compose(src, frame, m, web)))
        assertFalse(half.contentEquals(compose(src, frame, m, none)))
        // out-of-range values stop at the ends
        assertArrayEquals(
            compose(src, frame, m, web),
            compose(src, frame, m, RigPose().apply { flat = 3.0 }),
        )
    }

    @Test
    fun aTailBendOfZeroIsTheWebsRigidTurnAndABendChangesTheTip() {
        val (src, frame) = walk()
        assertTrue("walk#0 has no tail part", frame.tail != null)
        val m = RigMorph(tail = 1.4)
        val rigid = compose(src, frame, m, RigPose().apply { tail = 7.0 })
        assertArrayEquals(
            rigid,
            compose(
                src,
                frame,
                m,
                RigPose().apply {
                    tail = 7.0
                    tailCurl = 0.0
                },
            ),
        )
        val bent =
            compose(
                src,
                frame,
                m,
                RigPose().apply {
                    tail = 7.0
                    tailCurl = 24.0
                },
            )
        assertFalse(bent.contentEquals(rigid))
        val bentOther =
            compose(
                src,
                frame,
                m,
                RigPose().apply {
                    tail = 7.0
                    tailCurl = -24.0
                },
            )
        assertFalse(bent.contentEquals(bentOther))
        // the same number of tail pixels give or take the resampling: it is still a tail
        val count = { p: IntArray -> p.count { it ushr 24 != 0 } }
        assertTrue(Math.abs(count(bent) - count(rigid)) < count(rigid) / 4)
    }

    @Test
    fun aWhiskerFactorIsTheSameAsLongerWhiskersInTheMorph() {
        val (src, frame) = idle()
        val viaPose =
            compose(src, frame, RigMorph(whisker = 1.2), RigPose().apply { whisker = 1.5 })
        val viaMorph = compose(src, frame, RigMorph(whisker = 1.8), RigPose())
        assertArrayEquals(viaMorph, viaPose)
        val shorter = compose(src, frame, RigMorph.NEUTRAL, RigPose().apply { whisker = 0.6 })
        val plain = compose(src, frame, RigMorph.NEUTRAL, RigPose())
        assertTrue(shorter.count { it ushr 24 != 0 } < plain.count { it ushr 24 != 0 })
    }

    @Test
    fun theMorphIsNeutralOnlyWithinTheWebsTolerance() {
        assertTrue(RigMorph.NEUTRAL.neutral)
        assertTrue(RigMorph(ear = 1.03, tail = 0.97).neutral)
        assertFalse(RigMorph(ear = 1.05).neutral)
        assertFalse(RigMorph(bob = true).neutral)
    }
}
