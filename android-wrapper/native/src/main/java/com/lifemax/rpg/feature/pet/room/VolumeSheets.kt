package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil

/** The numbers behind the cached pictures of the solid pieces. */
internal object SheetTuning {
    /** The sheets are this wide; bands are packed in shelves. */
    const val WIDTH = 512

    /** The most camera steps on each side of rest (a canvas keeps a picture per step). */
    const val MAX_STEPS_PER_SIDE = 7

    /** A new step is taken only when the sway is this far (share of a step) past the boundary. */
    const val HYSTERESIS = 0.15f

    /** Bands are whole floor rows of depth; a piece is never deeper than this. */
    const val MAX_BANDS = 32

    /**
     * A band is kept as strips this many rows tall, each cropped to its own pixels, so a band that
     * is a thin side or a scattering of books does not keep the whole piece's box.
     */
    const val STRIP = 12
}

/**
 * The pictures of every solid piece for one camera step, each cut into bands of one floor row of
 * depth (so a cat can be drawn between the bands nearer and farther than it: a z-buffer at the
 * resolution of a floor row) and packed into one [image].
 *
 * A band is kept as strips of a few rows ([SheetTuning.STRIP]), each cropped to its own pixels.
 * Entry `b` is the `w[b]` x `h[b]` rectangle at ([sx], [sy]) of the image, drawn with its top left
 * ([dx], [dy]) pixels from the top left of piece [piece]'s sprite; it holds pixels whose depth is
 * band [band] of that piece and sorts at [key], the floor row it stands on (nearer is larger). A
 * piece's entries are contiguous, from [first] to [end] (they never overlap, so their order among
 * themselves does not matter). [order] is the drawing order of the entries and the atlas furniture
 * without the cats: [FURNITURE] plus an index for the furniture, the entry otherwise.
 */
internal class VolumeStep(
    val image: ArgbImage,
    val count: Int,
    val piece: IntArray,
    val band: IntArray,
    val sx: IntArray,
    val sy: IntArray,
    val w: IntArray,
    val h: IntArray,
    val dx: IntArray,
    val dy: IntArray,
    val key: FloatArray,
    val first: IntArray,
    val end: IntArray,
    val order: IntArray,
) {
    companion object {
        /** The code of atlas furniture `k` in [order] is `FURNITURE + k`. */
        const val FURNITURE = 1000
    }
}

/**
 * The solid pieces of the house and their pictures for every camera step: the camera's sway is cut
 * into `2 * stepsPerSide + 1` steps, and each step's pictures are rendered once ([VolumeRaster]),
 * cut into bands and cached ([VolumeStep]), lazily or ahead with [warmOne]; a canvas keeps one
 * bitmap per step (its slot), so a frame draws cached pictures and renders nothing.
 *
 * Step `i` is the sway `(i - stepsPerSide) / stepsPerSide`: the view `vx = -sway * depthGain` (the
 * back lags the front, so the side toward the camera shows) and `vy = -pitchAt(sway)` (the camera a
 * little higher at either end of its swing, so the tops show). The middle step is the camera at
 * rest, where every piece is its painted sprite.
 *
 * @param pieces the solid pieces, in manifest order (each has a volume).
 * @param furnitureKey the floor row each piece of atlas furniture sorts at, in the scene's order.
 * @param stepsPerSide steps on each side of rest; 0 for a room that never sways.
 */
internal class VolumeSheets(
    val pieces: List<LayerPiece>,
    private val furnitureKey: FloatArray,
    private val level: DepthLevel,
    val stepsPerSide: Int,
) {
    private val steps = arrayOfNulls<VolumeStep>(2 * stepsPerSide + 1)
    private val frame =
        VolumeFrame(
            pieces.maxOf {
                val m = it.volume!!.model
                VolumeRaster.size(m, level.depthGain, level.pitch)
            }
        )

    /** The most entries (band strips plus furniture) a step's order can have. */
    val capacity: Int =
        furnitureKey.size +
            pieces.sumOf {
                val m = it.volume!!.model
                val h = m.height + ceil(m.depth * level.pitch).toInt() + 1
                (h + SheetTuning.STRIP - 1) / SheetTuning.STRIP * SheetTuning.MAX_BANDS
            }

    /** The first canvas version of this set's sheets; step `i` is `version + i`. */
    val version = VERSIONS.getAndAdd(VERSION_SPAN)

    /** How many steps there are. */
    val count: Int
        get() = steps.size

    /** The step of rest. */
    val rest: Int
        get() = stepsPerSide

    /** The sway of step [i]. */
    fun swayOf(i: Int): Float =
        if (stepsPerSide == 0) 0f else (i - stepsPerSide).toFloat() / stepsPerSide

    /**
     * The step for [sway], given the step now shown, [current]: the nearest, but held until the
     * sway is a little past the boundary so a camera at rest on a boundary does not flicker.
     */
    fun indexOf(sway: Float, current: Int): Int {
        if (stepsPerSide == 0) return 0
        val at = sway * stepsPerSide + stepsPerSide
        if (current in steps.indices && abs(at - current) < 0.5f + SheetTuning.HYSTERESIS) {
            return current
        }
        return Math.round(at).coerceIn(0, steps.size - 1)
    }

    /** Whether step [i]'s pictures are made. */
    fun ready(i: Int): Boolean = steps[i] != null

    /** The pictures of step [i], made now if they are not yet. */
    fun step(i: Int): VolumeStep =
        steps[i]
            ?: build(i).also {
                steps[i] = it
                made++
            }

    /** Make the pictures of one more step, nearest rest first; false when all are made. */
    fun warmOne(): Boolean {
        if (made == steps.size) return false
        for (k in 0..stepsPerSide) {
            for (side in 0..1) {
                val i = if (side == 0) stepsPerSide - k else stepsPerSide + k
                if (steps[i] == null) {
                    step(i)
                    return true
                }
            }
        }
        return false
    }

    /** How many steps' pictures are made. */
    private var made = 0

    /** Render, band and pack step [i]: a few milliseconds, once. */
    private fun build(i: Int): VolumeStep {
        val sway = swayOf(i)
        val vx = -sway * level.depthGain
        val vy = -level.pitchAt(sway)
        val bands = ArrayList<Band>()
        val first = IntArray(pieces.size)
        val end = IntArray(pieces.size)
        for ((p, piece) in pieces.withIndex()) {
            val volume = piece.volume!!
            VolumeRaster.render(volume.model, vx, vy, volume.wall, frame)
            first[p] = bands.size
            for (top in 0 until frame.height step SheetTuning.STRIP) {
                val bottom = minOf(top + SheetTuning.STRIP, frame.height)
                bound(frame, top, bottom)
                for (b in SheetTuning.MAX_BANDS - 1 downTo 0) {
                    if (x1[b] < 0) continue
                    val cut = cut(frame, b, x0[b], y0[b], x1[b], y1[b])
                    bands.add(Band(p, b, volume.sortFront - (b + 0.5f), cut))
                }
            }
            end[p] = bands.size
        }
        return pack(bands, first, end)
    }

    private class Band(val piece: Int, val band: Int, val key: Float, val pixels: Cut)

    private class Cut(val x: Int, val y: Int, val w: Int, val h: Int, val argb: IntArray)

    private val x0 = IntArray(SheetTuning.MAX_BANDS)
    private val y0 = IntArray(SheetTuning.MAX_BANDS)
    private val x1 = IntArray(SheetTuning.MAX_BANDS)
    private val y1 = IntArray(SheetTuning.MAX_BANDS)

    /**
     * The box of every band's pixels on rows [top] until [bottom] (x1 -1 for none), in one pass.
     */
    private fun bound(frame: VolumeFrame, top: Int, bottom: Int) {
        x0.fill(Int.MAX_VALUE)
        y0.fill(Int.MAX_VALUE)
        x1.fill(-1)
        y1.fill(-1)
        for (y in top until bottom) for (x in 0 until frame.width) {
            val at = y * frame.width + x
            if (frame.colour[at] == 0) continue
            val b = bandOf(frame, at)
            x0[b] = minOf(x0[b], x)
            y0[b] = minOf(y0[b], y)
            x1[b] = maxOf(x1[b], x)
            y1[b] = maxOf(y1[b], y)
        }
    }

    /** The pixels of [frame] in band [b] inside the box ([x0], [y0])..([x1], [y1]). */
    private fun cut(frame: VolumeFrame, b: Int, x0: Int, y0: Int, x1: Int, y1: Int): Cut {
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val argb = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val at = (y0 + y) * frame.width + x0 + x
            if (inBand(frame, at, b)) argb[y * w + x] = frame.colour[at]
        }
        return Cut(frame.x0 + x0, frame.y0 + y0, w, h, argb)
    }

    private fun bandOf(frame: VolumeFrame, at: Int): Int =
        VolumeRaster.band(frame.depth[at]).coerceIn(0, SheetTuning.MAX_BANDS - 1)

    private fun inBand(frame: VolumeFrame, at: Int, b: Int): Boolean =
        frame.colour[at] != 0 && bandOf(frame, at) == b

    /** Shelf-pack the bands into one image and work out the drawing order. */
    private fun pack(bands: List<Band>, first: IntArray, end: IntArray): VolumeStep {
        val n = bands.size
        val sx = IntArray(n)
        val sy = IntArray(n)
        var cx = 0
        var cy = 0
        var shelf = 0
        for ((k, band) in bands.withIndex()) {
            val c = band.pixels
            if (cx + c.w > SheetTuning.WIDTH) {
                cx = 0
                cy += shelf + 1
                shelf = 0
            }
            sx[k] = cx
            sy[k] = cy
            cx += c.w + 1
            shelf = maxOf(shelf, c.h)
        }
        val height = maxOf(1, cy + shelf)
        val image = IntArray(SheetTuning.WIDTH * height)
        for ((k, band) in bands.withIndex()) {
            val c = band.pixels
            for (y in 0 until c.h) {
                System.arraycopy(
                    c.argb,
                    y * c.w,
                    image,
                    (sy[k] + y) * SheetTuning.WIDTH + sx[k],
                    c.w,
                )
            }
        }
        val order = (0 until n + furnitureKey.size).sortedWith(orderOf(bands)).toIntArray()
        return VolumeStep(
            image = ArgbImage(SheetTuning.WIDTH, height, image),
            count = n,
            piece = IntArray(n) { bands[it].piece },
            band = IntArray(n) { bands[it].band },
            sx = sx,
            sy = sy,
            w = IntArray(n) { bands[it].pixels.w },
            h = IntArray(n) { bands[it].pixels.h },
            dx = IntArray(n) { bands[it].pixels.x },
            dy = IntArray(n) { bands[it].pixels.y },
            key = FloatArray(n) { bands[it].key },
            first = first,
            end = end,
            order = IntArray(order.size) { k -> codeOf(order[k], n) },
        )
    }

    /**
     * Back to front by the floor row each thing stands on; at the same row the manifest's order (so
     * the room at rest is drawn exactly as painted), and the atlas furniture after the pieces.
     */
    private fun orderOf(bands: List<Band>): Comparator<Int> {
        val n = bands.size
        fun key(i: Int) = if (i < n) bands[i].key else furnitureKey[i - n]
        return compareBy<Int>({ key(it) }, { if (it < n) 0 else 1 }, { it })
    }

    private fun codeOf(i: Int, n: Int) = if (i < n) i else VolumeStep.FURNITURE + i - n

    companion object {
        private val VERSIONS = AtomicInteger(1)
        private const val VERSION_SPAN = 2 * SheetTuning.MAX_STEPS_PER_SIDE + 1

        /** Steps on each side of rest for [level], so a step moves the deepest back one pixel. */
        fun stepsFor(pieces: List<LayerPiece>, level: DepthLevel): Int {
            val deepest = pieces.maxOfOrNull { it.volume!!.model.depth } ?: 0f
            val reach = ceil((deepest * level.depthGain).toDouble()).toInt()
            return reach.coerceIn(1, SheetTuning.MAX_STEPS_PER_SIDE)
        }
    }
}
