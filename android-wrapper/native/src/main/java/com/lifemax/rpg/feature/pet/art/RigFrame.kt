package com.lifemax.rpg.feature.pet.art

/**
 * A part of a frame that the rig can move: an ear or a whisker. [px] are the frame's pixel indices
 * (`y * width + x`) in the order the page lists them; the base and tip are in frame pixels, taken
 * at the centre of their pixel (the page's `+ 0.5`).
 */
class RigPart(
    val px: IntArray,
    val baseX: Double,
    val baseY: Double,
    val tipX: Double,
    val tipY: Double,
)

/**
 * The tail of a frame: its pixels and its centre line as a polyline of [count] vertices (frame
 * pixels, pixel centres). [cum] is the arc length from the root to each vertex.
 */
class RigTail(val px: IntArray, val x: DoubleArray, val y: DoubleArray, val cum: DoubleArray) {
    val count: Int
        get() = x.size
}

/**
 * Everything the rig knows about one frame of the art (`petRig.html`, see `tools/rig_pack.js`).
 *
 * @property foot the row of the paws
 * @property legFrom first row of the band that is stretched to lengthen or shorten the legs, or -1
 *   (stretching the legs only applies when [stretchLegs] says the animation allows it)
 * @property legTo the row after the band
 * @property torsoFrom first column of the band that is stretched to lengthen the body, or -1
 * @property torsoTo the column after the band
 * @property stretchLegs the animation's rows can be re-spaced (the page's `lg`)
 * @property stretchBody the animation's columns can be re-spaced (the page's `bd`)
 * @property headX the head anchor, or NaN: not used by the composer (the look-at phase pivots it)
 * @property headY see [headX]
 * @property ears the ears, left (back) ear first
 * @property whiskers the whiskers
 * @property tail the tail, or null when the animation does not treat it as a part
 */
class RigFrame(
    val foot: Int,
    val legFrom: Int,
    val legTo: Int,
    val torsoFrom: Int,
    val torsoTo: Int,
    val stretchLegs: Boolean,
    val stretchBody: Boolean,
    val headX: Double,
    val headY: Double,
    val ears: Array<RigPart>,
    val whiskers: Array<RigPart>,
    val tail: RigTail?,
)

/**
 * The proportions of one cat as multipliers of the drawn art (the page's `PET_MORPH`: ear length
 * and width, tail length, leg length, body length, whisker length). 1.0 is the art as drawn. The
 * page computes them from the cat's genes; [bob] marks a bobtail, whose tail is cut short.
 */
data class RigMorph(
    val ear: Double = 1.0,
    val earWidth: Double = 1.0,
    val tail: Double = 1.0,
    val leg: Double = 1.0,
    val body: Double = 1.0,
    val whisker: Double = 1.0,
    val bob: Boolean = false,
) {
    /**
     * True when the cat is the art as drawn, so the plain sheet can be shown (the page's
     * `petRigNeutral`: every multiplier within 0.04 of 1 and no bobtail).
     */
    val neutral: Boolean
        get() =
            !bob &&
                near(ear) &&
                near(earWidth) &&
                near(tail) &&
                near(leg) &&
                near(body) &&
                near(whisker)

    private fun near(v: Double) = Math.abs(v - 1.0) <= NEUTRAL_TOLERANCE

    companion object {
        /** The art as drawn. */
        val NEUTRAL = RigMorph()

        /** How far a multiplier may stray from 1 and still count as the art as drawn. */
        const val NEUTRAL_TOLERANCE = 0.04

        /** The smallest and largest multiplier a gene can produce (`PET_MORPH`). */
        const val MIN = 0.35
        const val MAX = 2.8
    }
}

/**
 * How the parts of a frame are moved: the composer's input. Angles are degrees, positive turns
 * clockwise on the screen (an ear tips toward the face, a tail rises over the back).
 *
 * @property earLeft turn of the left (back) ear about its base
 * @property earRight turn of the right (front) ear
 * @property flat how flat the ears lie, 0 upright to 1 airplane (the page's `flat`, which is 0 or
 *   1; native blends between)
 * @property tail turn of the whole tail about its root
 * @property tailCurl extra turn at the tip, growing along the tail from none at the root: the tail
 *   bends instead of swinging as one piece (0 is the page's rigid turn)
 * @property whisker a factor for the whisker length, 1 as drawn
 */
class RigPose {
    var earLeft = 0.0
    var earRight = 0.0
    var flat = 0.0
    var tail = 0.0
    var tailCurl = 0.0
    var whisker = 1.0

    /** True when nothing is moved. */
    val neutral: Boolean
        get() =
            earLeft == 0.0 &&
                earRight == 0.0 &&
                flat == 0.0 &&
                tail == 0.0 &&
                tailCurl == 0.0 &&
                whisker == 1.0

    /** Put every part back where the art has it. */
    fun reset() {
        earLeft = 0.0
        earRight = 0.0
        flat = 0.0
        tail = 0.0
        tailCurl = 0.0
        whisker = 1.0
    }
}
