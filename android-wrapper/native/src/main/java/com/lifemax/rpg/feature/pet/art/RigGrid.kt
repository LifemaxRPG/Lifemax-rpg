package com.lifemax.rpg.feature.pet.art

/**
 * The grid a cat's secondary motion is snapped to before a frame is composed, so that the same few
 * poses come round again and the composed frames can be cached. The springs move smoothly; the art
 * moves in steps small enough to read as smooth at the room's pixel size and few enough to cache:
 *
 * | what                 | step      | range                            |
 * |----------------------|-----------|----------------------------------|
 * | each ear             | 4 degrees | +/- 44 degrees (11 steps)        |
 * | ears flat            | quarters  | 0 to 1 (4 steps)                 |
 * | tail turn            | 3 degrees | +/- 24 degrees (8 steps)         |
 * | tail curl at the tip | 4 degrees | +/- 40 degrees (10 steps)        |
 * | whisker length       | 10 %      | 60 % to 140 % (4 steps each way) |
 *
 * Within the range one pose costs no more than a cache lookup once it has been composed; a pose
 * that is the middle of the grid on every axis is the art as drawn ([neutral]) and needs no
 * composing at all. Ear angles of the page (+/- 36 degrees for airplane ears) lie on the grid.
 */
class RigGrid {
    var earLeft = 0
        private set

    var earRight = 0
        private set

    var flat = 0
        private set

    var tail = 0
        private set

    var curl = 0
        private set

    var whisker = 0
        private set

    /** True when every axis is at the middle: the art as drawn. */
    val neutral: Boolean
        get() = earLeft == 0 && earRight == 0 && flat == 0 && tail == 0 && curl == 0 && whisker == 0

    /**
     * Snap a pose to the grid. Angles are degrees (positive turns clockwise on the screen), [flat]
     * is 0..1, [whiskerScale] a factor for the whisker length (1 as drawn). Values past the range
     * stop at its edge.
     */
    fun set(
        earLeftDeg: Double,
        earRightDeg: Double,
        flat: Double,
        tailDeg: Double,
        curlDeg: Double,
        whiskerScale: Double,
    ) {
        earLeft = steps(earLeftDeg / EAR_STEP_DEG, EAR_RANGE)
        earRight = steps(earRightDeg / EAR_STEP_DEG, EAR_RANGE)
        this.flat = steps(flat * FLAT_STEPS, FLAT_STEPS).coerceAtLeast(0)
        tail = steps(tailDeg / TAIL_STEP_DEG, TAIL_RANGE)
        curl = steps(curlDeg / CURL_STEP_DEG, CURL_RANGE)
        whisker = steps((whiskerScale - 1.0) / WHISKER_STEP, WHISKER_RANGE)
    }

    /** Back to the middle of every axis. */
    fun clear() = set(0.0, 0.0, 0.0, 0.0, 0.0, 1.0)

    /** The composer's pose for the grid point, written into [out]. */
    fun toPose(out: RigPose) {
        out.earLeft = earLeft * EAR_STEP_DEG
        out.earRight = earRight * EAR_STEP_DEG
        out.flat = flat.toDouble() / FLAT_STEPS
        out.tail = tail * TAIL_STEP_DEG
        out.tailCurl = curl * CURL_STEP_DEG
        out.whisker = 1.0 + whisker * WHISKER_STEP
    }

    /** The grid point as a number below [POINTS]: a cache key part. */
    fun key(): Long {
        var k = (earLeft + EAR_RANGE).toLong()
        k = k * EAR_SPAN + (earRight + EAR_RANGE)
        k = k * FLAT_SPAN + flat
        k = k * TAIL_SPAN + (tail + TAIL_RANGE)
        k = k * CURL_SPAN + (curl + CURL_RANGE)
        k = k * WHISKER_SPAN + (whisker + WHISKER_RANGE)
        return k
    }

    companion object {
        const val EAR_STEP_DEG = 4.0
        const val TAIL_STEP_DEG = 3.0
        const val CURL_STEP_DEG = 4.0
        const val WHISKER_STEP = 0.1
        const val FLAT_STEPS = 4

        const val EAR_RANGE = 11
        const val TAIL_RANGE = 8
        const val CURL_RANGE = 10
        const val WHISKER_RANGE = 4

        private const val EAR_SPAN = 2 * EAR_RANGE + 1
        private const val FLAT_SPAN = FLAT_STEPS + 1
        private const val TAIL_SPAN = 2 * TAIL_RANGE + 1
        private const val CURL_SPAN = 2 * CURL_RANGE + 1
        private const val WHISKER_SPAN = 2 * WHISKER_RANGE + 1

        /** Number of distinct keys: the grid has this many points. */
        const val POINTS: Long =
            1L * EAR_SPAN * EAR_SPAN * FLAT_SPAN * TAIL_SPAN * CURL_SPAN * WHISKER_SPAN

        /** Round half away from zero and clamp to +/- [range]. */
        private fun steps(v: Double, range: Int): Int {
            if (v.isNaN()) return 0
            val r = if (v >= 0) Math.floor(v + 0.5) else -Math.floor(-v + 0.5)
            return r.coerceIn(-range.toDouble(), range.toDouble()).toInt()
        }
    }
}
