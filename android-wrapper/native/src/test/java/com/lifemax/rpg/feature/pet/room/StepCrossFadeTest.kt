package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.PetArtResources
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cross-fade between two camera steps, in pixels: each solid piece drawn alone ([StepMix] and
 * the bands' alphas, as the room draws them) over a plain backdrop. At weight 0 it is the base step
 * exactly, near 1 the other step, half-way the two pictures half each, and a small change of weight
 * is a small change of picture: the sides fade instead of popping. Then in the room: a camera that
 * crosses a step changes the furniture a little each frame, and one that comes to rest draws the
 * old picture exactly.
 */
class StepCrossFadeTest {
    private val art = PetArtResources.loadRoom()
    private val depth = RoomScene(art, RoomStyle.live(DepthLevel.MEDIUM)).depth!!
    private val sheets = depth.sheets
    private val mix = StepMix(sheets, FloatArray(0))
    private val n = depth.pieces.size

    /** Piece [p] alone over grey: [base] with [over] faded in at [weight]. */
    private fun piece(p: Int, base: Int, over: Int, weight: Float): IntArray {
        mix.update(IntArray(n) { base }, IntArray(n) { over }, FloatArray(n) { weight })
        val c = SoftCanvas(art)
        c.fill(0, 0, c.width, c.height, GREY)
        val piece = depth.pieces[p]
        for (b in mix.first[p] until mix.end[p]) {
            val a = mix.alpha[b]
            if (a <= 0f) continue
            c.sheet(
                mix.image(b),
                0,
                0,
                mix.sx(b),
                mix.sy(b),
                mix.w(b),
                mix.h(b),
                piece.houseX + mix.dx(b),
                piece.y + mix.dy(b),
                a,
            )
        }
        return c.pixels
    }

    private fun channelGap(a: Int, b: Int): Int {
        var most = 0
        for (s in intArrayOf(0, 8, 16)) most =
            maxOf(most, abs(((a shr s) and 255) - ((b shr s) and 255)))
        return most
    }

    private fun maxGap(a: IntArray, b: IntArray): Int {
        var most = 0
        for (i in a.indices) most = maxOf(most, channelGap(a[i], b[i]))
        return most
    }

    /** Every pair of neighbouring steps, base the one nearer rest. */
    private fun pairs(): List<IntArray> {
        val r = sheets.rest
        return (0 until sheets.count)
            .filter { it != r }
            .map { i -> intArrayOf(if (i > r) i - 1 else i + 1, i) }
    }

    @Test
    fun atTheEndsTheFadeIsOneStepAndHalfWayItIsBothHalfEach() {
        var worstMid = 0
        var offMid = 0
        var pixels = 0
        for (p in 0 until n) for ((base, over) in pairs()) {
            val alone = piece(p, base, -1, 0f)
            val other = piece(p, over, -1, 0f)
            assertEquals(
                "weight 0 is the base exactly (${depth.pieces[p].name} $base)",
                alone.toList(),
                piece(p, base, over, 0f).toList(),
            )
            assertTrue(
                "weight 1 is the other step (${depth.pieces[p].name} $base to $over)",
                maxGap(other, piece(p, base, over, 0.9999f)) <= 1,
            )
            val half = piece(p, base, over, 0.5f)
            for (i in half.indices) {
                if (alone[i] == other[i]) continue
                pixels++
                val want = lerp(alone[i], other[i], 0.5f)
                val gap = channelGap(want, half[i])
                worstMid = maxOf(worstMid, gap)
                if (gap > 3) offMid++
            }
        }
        println("CROSSFADE pixels that differ between steps $pixels, off a true half-way $offMid")
        // the base is the step nearer rest, whose sides are the shallower: the faded-in step's
        // bands are drawn over it, never under, so the mix is the true half-way (0 of about 12,000
        // pixels off it when this was written); a rounding of the raster may cost a few
        assertTrue("off half-way: $offMid of $pixels", offMid * 1000 <= pixels)
    }

    @Test
    fun aSmallChangeOfWeightIsASmallChangeOfPicture() {
        for (p in 0 until n) for ((base, over) in pairs()) {
            var last = piece(p, base, over, 0f)
            for (k in 1..10) {
                val now = piece(p, base, over, k / 10f)
                val gap = maxGap(last, now)
                assertTrue("${depth.pieces[p].name} $base to $over at ${k / 10f}: $gap", gap <= 60)
                last = now
            }
        }
    }

    @Test
    fun aPanDrawsTheFurnitureBetweenStepsAndARestingCameraDrawsTheOldPicture() {
        val s = RoomScene(art, RoomStyle.live(DepthLevel.MEDIUM))
        val d = s.depth!!
        var blended = 0
        repeat(240) {
            d.follow(-80f + 360f * it / 240f)
            d.step(DT)
            paint(s)
            for (piece in d.pieces) {
                val at = d.positionOf(piece.name)
                if (at != Math.round(at).toFloat()) blended++
            }
        }
        assertTrue("pieces drawn between steps while the camera pans: $blended", blended > 100)
        repeat(600) {
            d.follow(280f)
            d.step(DT)
            paint(s)
        }
        // at rest every piece is one whole step, the one the room drew before the blend (held
        // with the same hysteresis), and the frames stay the same
        for (piece in d.pieces) {
            val at = d.positionOf(piece.name)
            assertEquals(
                "${piece.name} at rest on its step",
                d.stepOf(piece.name).toFloat(),
                at,
                0f,
            )
        }
        val still = paint(s)
        repeat(120) {
            d.follow(280f)
            d.step(DT)
            assertEquals(still.toList(), paint(s).toList())
        }
    }

    private fun paint(s: RoomScene): IntArray {
        val c = SoftCanvas(art)
        s.paintBackdrop(c, 12.0, 0.0, 0)
        s.paintScene(c, NoCats)
        return c.pixels
    }

    private companion object {
        const val GREY = 0xFF808080.toInt()
        const val DT = 1f / 60f

        fun lerp(a: Int, b: Int, t: Float): Int {
            var out = 0xFF000000.toInt()
            for (s in intArrayOf(0, 8, 16)) {
                val v = Math.round(((a shr s) and 255) * (1 - t) + ((b shr s) and 255) * t)
                out = out or (v shl s)
            }
            return out
        }
    }
}
