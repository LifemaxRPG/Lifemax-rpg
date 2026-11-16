package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** The numbers of the furniture's continuous depth (docs/specs/CAT_ROOM_CONTINUOUS_DEPTH.md). */
internal object FaceTuning {
    /**
     * Depth strips per floor row. Strip `j` is drawn `j / SLICES_PER_ROW * vx` across, so two
     * neighbouring strips are `vx / 2` apart: at most 0.18 art pixels (RICH, the camera turned all
     * the way), about one device pixel on the phone. The depth maps hold eighths of a row; two
     * strips a row keep their half-row steps (the sofa's seat front, the shelves).
     */
    const val SLICES_PER_ROW = 2

    /** The outline at the far edge of a revealed face is at most this wide (art pixels). */
    const val INK_WIDTH = 1f

    /** A strip's pixels are kept in runs this many rows tall, each cropped to its own pixels. */
    const val RUN_ROWS = 12

    /** Within a run's rows, pixels further apart than this many clear columns are cut apart. */
    const val RUN_GAP = 12

    /** The packed pictures are this wide. */
    const val WIDTH = 512
}

/**
 * The continuous camera of one piece, as pure arithmetic: an oblique projection in which a point
 * `d` floor rows behind the piece's front plane is drawn `d * vx` pixels across and `d * vy` down,
 * for the piece's own yaw as it is this frame (-1 to 1; nothing is quantised to steps). So a side
 * face of a piece `D` rows deep is drawn `D * |vx|` wide ([sideWidth]): 0 at a yaw of 0, growing
 * with the yaw continuously and monotonically, and its top `D * |vy|` tall.
 */
internal object FaceView {
    /** Pixels across per floor row of depth for [yaw] (-1 to 1) at [level]. */
    fun vx(yaw: Float, level: DepthLevel): Float = -yaw * level.depthGain

    /** Pixels down per floor row of depth for [yaw] at [level] (negative: the tops show). */
    fun vy(yaw: Float, level: DepthLevel): Float = -level.pitchAt(yaw)

    /** The depth (floor rows behind the front plane) strip [j] is drawn at. */
    fun depthOf(j: Int): Float = j / FaceTuning.SLICES_PER_ROW.toFloat()

    /** The strip a front at [depth] rows lies in. */
    fun sliceOf(depth: Float): Int = floor(depth * FaceTuning.SLICES_PER_ROW).toInt()

    /** How many strips a piece [depth] rows deep has. */
    fun slices(depth: Float): Int = ceil(depth * FaceTuning.SLICES_PER_ROW).toInt().coerceAtLeast(1)

    /** How many bands of a floor row (the order a cat is put into) a piece [depth] deep has. */
    fun bands(depth: Float): Int =
        (slices(depth) + FaceTuning.SLICES_PER_ROW - 1) / FaceTuning.SLICES_PER_ROW

    /** Which of the two shadings a view [vx] uses: 0 shows left sides, 1 right sides. */
    fun side(vx: Float): Int = if (vx > 0f) 1 else 0

    /**
     * How much of a face revealed [reveal] pixels wide is the outline: none while it is a pixel or
     * less (a sliver of side reads as a side, not as a thicker outline), then growing with it to
     * [FaceTuning.INK_WIDTH]; continuous, so the outline never pops.
     */
    fun inkWidth(reveal: Float): Float = (reveal - 1f).coerceIn(0f, FaceTuning.INK_WIDTH)

    /** How many pixels a floor row of depth moves for the view ([vx], [vy]): the larger one. */
    fun perRow(vx: Float, vy: Float): Float = maxOf(abs(vx), abs(vy))

    /** How wide the side face of a piece [depth] rows deep is drawn for the view [vx]. */
    fun sideWidth(depth: Float, vx: Float): Float = depth * abs(vx)
}

/**
 * The faces of one solid piece, baked once and drawn every frame at the camera's live angle: the
 * furniture's continuous 2.5D (docs/specs/CAT_ROOM_CONTINUOUS_DEPTH.md), the way the Habits street
 * draws its buildings (faces baked once, only where they are drawn changes with the camera).
 *
 * A piece is a [VolumeModel]: a column of solid behind every painted pixel. Its faces are baked as
 * depth strips, [FaceTuning.SLICES_PER_ROW] to a floor row: strip `j` holds what the piece shows at
 * that depth, its painted fronts (the columns that start there, untouched, so at rest the piece is
 * its sprite exactly) and the rim of its cross-section there, which is where its side and top faces
 * pass through that depth, coloured once by the room's rules ([VolumeTuning]: lighter on top,
 * darker on the sides, one more step in a crevice, the painted colour where it is a small step of
 * one surface, a slope). Each frame strip `j` is drawn at its depth times the view's offsets
 * ([FaceView]), back to front: the side face of a piece `D` rows deep spans exactly `D * |vx|`
 * pixels, a continuous function of the camera, and grows a device pixel at a time on the phone.
 * Nothing is rendered per frame and no step is cached.
 *
 * A strip keeps only its rim: a pixel that the next nearer strip covers whatever the view (its
 * column and its neighbours reach that strip too) is left out, so drawing the strips gives exactly
 * the picture the whole cross-sections would, at a fraction of the pixels. The rim is cut into runs
 * ([FaceTuning.RUN_ROWS], [FaceTuning.RUN_GAP]), each cropped to its own pixels.
 *
 * Which side shows depends on the direction of the view, so there are two shadings, one for left
 * sides and one for right ([FaceView.side]); they agree at rest. A piece painted with the art's
 * outline colour outlines the far edge of a revealed face: each shading has an inked copy (its
 * faces in the outline colour), drawn instead for a strip within [FaceView.inkWidth] of the back of
 * its columns. That width grows from nothing once a face is more than a pixel wide, so the outline
 * comes in continuously and at rest there is none.
 *
 * Everything is packed into one [image] (a block per shading, the same layout in each). Baking
 * allocates (a few milliseconds a piece, once); drawing allocates nothing.
 *
 * The sides and tops are the piece's own authored surfaces ([FaceSkin], [FaceSkins]): a skin says
 * what each face pixel is made of (a wooden side panel with its grain and edge banding, a fabric
 * arm with its seam, the pages on top of a book), and the room's light is added to it here.
 *
 * @param backed the piece stands against the wall (its sides darken where they meet it).
 * @param skin the piece's side and top textures.
 */
internal class SolidFaces(
    private val model: VolumeModel,
    private val backed: Boolean,
    private val skin: FaceSkin = FaceSkin.PLAIN,
) {
    /** How many depth strips the piece has. */
    val slices = FaceView.slices(model.depth)

    /** How many bands of one floor row of depth (the order a cat is put into) it has. */
    val bands = FaceView.bands(model.depth)

    /**
     * Whether the piece is painted with the art's outline colour, so its new edges are outlined.
     */
    val inked = model.palette.colours[model.palette.ink] == RampTuning.ART_INK

    /** How many shadings: left and right sides, and their inked copies for an outlined piece. */
    private val shadings = if (inked) 4 else 2

    /** The packed pictures. */
    val image: ArgbImage

    /** A canvas version for [image] (a canvas keeps its bitmap while this stays the same). */
    val version = VERSIONS.getAndIncrement()

    /** How many runs (draws) the strips have in all, in one shading. */
    val runs: Int

    /** Strip `j`'s runs are `first[j]` until `first[j + 1]`. */
    private val first = IntArray(slices + 1)

    /** Each run's rectangle in the first shading's block and its place from the sprite's corner. */
    private val sx: IntArray
    private val sy: IntArray
    val w: IntArray
    val h: IntArray
    val dx: IntArray
    val dy: IntArray

    /** How tall one shading's block of [image] is. */
    private val block: Int

    /** How many pixels of one shading are painted (memory, tests). */
    val pixels: Int

    /** The deepest back of the columns in each strip: where its faces end. */
    private val sliceBack = FloatArray(slices)

    /** The face pixel [skin] is asked about (used while baking). */
    private val spot = FaceSpot(model)

    init {
        val wm = model.width
        val hm = model.height
        val keep = BooleanArray(wm * hm)
        val rect = ArrayList<IntArray>() // x, y, w, h of each run, strip by strip
        val cut = ArrayList<Array<IntArray>>() // each run's pixels, per shading
        for (j in 0 until slices) {
            first[j] = rect.size
            for (i in 0 until wm * hm) {
                keep[i] = inSlice(i, j) && !covered(i, j)
                if (inSlice(i, j)) sliceBack[j] = maxOf(sliceBack[j], model.back[i])
            }
            for (top in 0 until hm step FaceTuning.RUN_ROWS) {
                val bottom = minOf(top + FaceTuning.RUN_ROWS, hm)
                var x = 0
                while (x < wm) {
                    if (!column(keep, x, top, bottom)) {
                        x++
                        continue
                    }
                    var end = x // the last kept column of this run
                    var probe = x + 1
                    while (probe < wm && probe - end <= FaceTuning.RUN_GAP + 1) {
                        if (column(keep, probe, top, bottom)) end = probe
                        probe++
                    }
                    var y0 = bottom
                    var y1 = top - 1
                    for (y in top until bottom) for (cx in x..end) {
                        if (keep[y * wm + cx]) {
                            y0 = minOf(y0, y)
                            y1 = maxOf(y1, y)
                        }
                    }
                    val rw = end - x + 1
                    val rh = y1 - y0 + 1
                    rect.add(intArrayOf(x, y0, rw, rh))
                    cut.add(
                        Array(shadings) { s ->
                            IntArray(rw * rh) { k ->
                                val i = (y0 + k / rw) * wm + x + k % rw
                                if (keep[i]) colourOf(i, j, s % 2, s >= 2) else 0
                            }
                        }
                    )
                    x = end + 1
                }
            }
        }
        first[slices] = rect.size
        runs = rect.size
        sx = IntArray(runs)
        sy = IntArray(runs)
        w = IntArray(runs) { rect[it][2] }
        h = IntArray(runs) { rect[it][3] }
        dx = IntArray(runs) { rect[it][0] }
        dy = IntArray(runs) { rect[it][1] }
        // shelf-pack the runs into one block, tallest first (little waste); every shading's block
        // has the same layout
        var cx = 0
        var cy = 0
        var shelf = 0
        for (e in (0 until runs).sortedWith(compareBy({ -h[it] }, { -w[it] }))) {
            if (cx + w[e] > FaceTuning.WIDTH) {
                cx = 0
                cy += shelf + 1
                shelf = 0
            }
            sx[e] = cx
            sy[e] = cy
            cx += w[e] + 1
            shelf = maxOf(shelf, h[e])
        }
        block = cy + shelf + 1
        val out = IntArray(FaceTuning.WIDTH * block * shadings)
        var painted = 0
        for (e in 0 until runs) for (s in 0 until shadings) {
            val c = cut[e][s]
            for (y in 0 until h[e]) {
                System.arraycopy(
                    c,
                    y * w[e],
                    out,
                    (s * block + sy[e] + y) * FaceTuning.WIDTH + sx[e],
                    w[e],
                )
            }
            if (s == 0) for (p in c) if (p ushr 24 != 0) painted++
        }
        image = ArgbImage(FaceTuning.WIDTH, block * shadings, out)
        pixels = painted
    }

    /** Whether any pixel of column [x] on rows [top] until [bottom] is kept. */
    private fun column(keep: BooleanArray, x: Int, top: Int, bottom: Int): Boolean {
        for (y in top until bottom) if (keep[y * model.width + x]) return true
        return false
    }

    /** Whether column [i] (a pixel index, or -1 outside) reaches into strip [j]. */
    internal fun inSlice(i: Int, j: Int): Boolean {
        if (i < 0 || !model.solid(i)) return false
        return model.front[i] < FaceView.depthOf(j + 1) && model.back[i] > FaceView.depthOf(j)
    }

    /**
     * Whether pixel [i] of strip [j] is always covered by strip `j - 1`, drawn just after it and
     * less than a pixel nearer whatever the view (at most 0.18 across, 0.11 down: the tops show,
     * never the undersides, so the nearer strip is moved down): the column and its neighbours on
     * either side, on its row and the row above, reach that strip too.
     */
    private fun covered(i: Int, j: Int): Boolean {
        if (j == 0) return false
        val x = i % model.width
        val y = i / model.width
        for (ny in y - 1..y) for (nx in x - 1..x + 1) if (!inSlice(at(nx, ny), j - 1)) return false
        return true
    }

    private fun at(x: Int, y: Int): Int =
        if (x in 0 until model.width && y in 0 until model.height) y * model.width + x else -1

    /**
     * What pixel [i] of strip [j] shows in [side]'s shading (0: nothing); with [inkFaces] a face
     * (not a front or a slope) is the outline colour.
     */
    internal fun colourOf(i: Int, j: Int, side: Int, inkFaces: Boolean): Int {
        if (!inSlice(i, j)) return 0
        if (FaceView.sliceOf(model.front[i]) >= j) return model.colours[i]
        val x = i % model.width
        val y = i / model.width
        val beside = at(if (side == 0) x - 1 else x + 1, y)
        val above = at(x, y - 1)
        val sideFace = if (side == 0) Face.LEFT else Face.RIGHT
        // the face a ray entering this column at this depth meets, and the column it came from
        val face: Byte
        val from: Int
        when {
            !inSlice(beside, j) -> {
                face = sideFace
                from = beside
            }
            !inSlice(above, j) -> {
                face = Face.TOP
                from = above
            }
            else -> {
                face = sideFace
                from = -1
            }
        }
        val slope =
            from >= 0 &&
                model.solid(from) &&
                model.front[i] - model.front[from] <= VolumeTuning.SLOPE_STEP
        val palette = model.palette
        spot.set(i, j, face, slope)
        val tex = skin.paint(spot)
        if (tex == FaceTex.INK) return palette.colours[palette.ink]
        // one surface: the slope keeps the painted colour (or the skin's, unlit)
        if (slope) {
            return when {
                tex == FaceTex.KEEP -> model.colours[i]
                FaceTex.isExact(tex) -> palette.colours[FaceTex.index(tex)]
                else -> palette.colours[palette.step(FaceTex.index(tex), FaceTex.steps(tex))]
            }
        }
        var steps =
            when (face) {
                Face.TOP -> VolumeTuning.TOP_STEP
                Face.LEFT -> VolumeTuning.LEFT_STEP
                else -> VolumeTuning.RIGHT_STEP
            }
        val back = model.back[i]
        val deeperBehind = backed || back < model.depth - VolumeTuning.CREVICE_REACH
        if (deeperBehind && FaceView.depthOf(j) >= back - VolumeTuning.CREVICE_REACH) {
            steps += VolumeTuning.CREVICE_STEP
        }
        if (inkFaces) return palette.colours[palette.ink]
        if (FaceTex.isExact(tex)) return palette.colours[FaceTex.index(tex)]
        if (tex == FaceTex.KEEP) {
            return palette.colours[palette.step(VolumeShade.material(model, i, face), steps)]
        }
        // the light first, then the texture's own steps (so a texture still shows on a dark side)
        return palette.colours[palette.step(FaceTex.index(tex), steps, FaceTex.steps(tex))]
    }

    /**
     * Draw band [k] of the piece (its strips `2k + 1` and `2k`, back to front) through [c] in
     * [slot], the piece's front plane at house position ([x], [y]), seen with the view ([vx], [vy])
     * ([FaceView]): each strip moved by its depth times the view, inked when it lies within the
     * outline's width of its back. Nothing is allocated.
     */
    fun paintBand(c: RoomCanvas, slot: Int, k: Int, x: Float, y: Float, vx: Float, vy: Float) {
        val last = minOf(slices, (k + 1) * FaceTuning.SLICES_PER_ROW) - 1
        for (j in last downTo k * FaceTuning.SLICES_PER_ROW) {
            val ox = x + FaceView.depthOf(j) * vx
            val oy = y + FaceView.depthOf(j) * vy
            val by = shadingOf(j, vx, vy) * block
            for (e in first[j] until first[j + 1]) {
                c.stack(image, slot, version, sx[e], by + sy[e], w[e], h[e], ox + dx[e], oy + dy[e])
            }
        }
    }

    /**
     * Which shading strip [j] is drawn in for the view ([vx], [vy]): [side]'s, or its inked copy (2
     * more) when the strip lies within the outline's width of its back.
     */
    internal fun shadingOf(j: Int, vx: Float, vy: Float): Int {
        val perRow = FaceView.perRow(vx, vy)
        var shading = FaceView.side(vx)
        if (inked && perRow > 0f) {
            val width = FaceView.inkWidth(sliceBack[j] * perRow)
            if (width > 0f && FaceView.depthOf(j) > sliceBack[j] - width / perRow) shading += 2
        }
        return shading
    }

    /** Strip [j]'s runs (tests). */
    fun runsOf(j: Int): IntRange = first[j] until first[j + 1]

    /** Bytes of [image] (memory, tests). */
    val bytes: Long
        get() = 4L * image.width * image.height

    private companion object {
        val VERSIONS = AtomicInteger(1)
    }
}
