package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Which solid pieces the developer's prototype draws as continuous 3D ([SolidStack]) instead of
 * from the cached camera steps (docs/specs/CAT_3D_ENGINE_PLAN.md). In memory only; [OFF] (every
 * piece from its steps, the room as it was) on every launch.
 */
internal enum class Solid3d(val label: String) {
    OFF("off"),

    /** The sofa and the two cushions on it (one group, so the cushions turn with the seat). */
    SOFA("sofa"),

    /** Every standing piece. */
    ALL("all");

    /** Whether piece [name] is drawn as a [SolidStack] in this mode. */
    fun covers(name: String): Boolean =
        when (this) {
            OFF -> false
            SOFA -> name.startsWith("sofa")
            ALL -> true
        }
}

/** The numbers of the continuous 3D prototype. */
internal object StackTuning {
    /**
     * Slices per floor row of depth. Each slice moves by `depth * vx`, so neighbouring slices are
     * `vx / SLICES_PER_ROW` apart: at MEDIUM and the live view angle about 0.08 art pixels, under
     * one device pixel at the phone's 5 to 9 device pixels per art pixel, so a side grows a device
     * pixel at a time instead of an art pixel at a time. The depth maps hold eighths of a row; two
     * slices a row keep the half-row steps (the sofa's seat front, the shelves).
     */
    const val SLICES_PER_ROW = 2

    /** The outline at the far edge of a revealed face is at most this wide (art pixels). */
    const val INK_WIDTH = 1f
}

/**
 * The continuous camera of one piece, as pure arithmetic: the same oblique projection as the cached
 * steps ([VolumeSheets]: a point `d` rows behind the front plane is drawn `d * vx` across and `d *
 * vy` down), but for the piece's own yaw as it is now, not the nearest of a few steps.
 */
internal object StackView {
    /** Pixels across per floor row of depth for [yaw] (-1 to 1) at [level]. */
    fun vx(yaw: Float, level: DepthLevel): Float = -yaw * level.depthGain

    /** Pixels down per floor row of depth for [yaw] at [level] (negative: the tops show). */
    fun vy(yaw: Float, level: DepthLevel): Float = -level.pitchAt(yaw)

    /** The depth (floor rows behind the front plane) slice [j] is drawn at. */
    fun depthOf(j: Int): Float = j / StackTuning.SLICES_PER_ROW.toFloat()

    /** The slice a front at [depth] rows lies in. */
    fun sliceOf(depth: Float): Int = floor(depth * StackTuning.SLICES_PER_ROW).toInt()

    /** Which of a stack's two shadings a view [vx] uses: 0 shows left sides, 1 right sides. */
    fun side(vx: Float): Int = if (vx > 0f) 1 else 0

    /**
     * How much of a face revealed [reveal] pixels wide is the outline: none while it is a pixel or
     * less (a sliver of side reads as a side, not as a thicker outline, as the raster has it), then
     * growing with it to [StackTuning.INK_WIDTH]; continuous, so the outline never pops.
     */
    fun inkWidth(reveal: Float): Float = (reveal - 1f).coerceIn(0f, StackTuning.INK_WIDTH)

    /** How many pixels a floor row of depth moves for the view ([vx], [vy]): the larger one. */
    fun perRow(vx: Float, vy: Float): Float = maxOf(abs(vx), abs(vy))

    /** Where slice [j] of a piece whose front plane is at ([x], [y]) is drawn, across. */
    fun sliceX(x: Float, j: Int, vx: Float): Float = x + depthOf(j) * vx

    /** Where slice [j] of a piece whose front plane is at ([x], [y]) is drawn, down. */
    fun sliceY(y: Float, j: Int, vy: Float): Float = y + depthOf(j) * vy
}

/**
 * One solid piece as a stack of thin slices, for drawing it in continuous 3D: the prototype route
 * of docs/specs/CAT_3D_ENGINE_PLAN.md ("sprite stacking"). The piece's columns ([VolumeModel]) are
 * cut into slices [StackTuning.SLICES_PER_ROW] to a floor row of depth; slice `j` holds every pixel
 * whose column reaches that depth. Drawn back to front, each moved by its own depth times the
 * view's offsets ([StackView]), the slices build the piece's sides and tops as the camera turns:
 * the offsets are continuous, so a side grows by a fraction of an art pixel as the camera moves
 * (drawn at the screen's own pixels) instead of a whole art pixel at a time from a cached step.
 *
 * What each slice pixel shows is decided once, when the stack is made, with the cached raster's own
 * rules ([VolumeRaster]): a column's front slice is the painted pixel, untouched (so at rest the
 * stack is the sprite exactly); a deeper pixel on the rim of its slice is the side or top it would
 * be, shaded along the piece's own ramp (lighter on top, darker on the sides, one more step in a
 * crevice), or the painted colour where it is a small step of one surface (a slope). Which side is
 * shown depends on the direction of the view, so a stack has two shadings, one for left sides and
 * one for right ([StackView.side]); they agree at rest, so nothing pops when the view crosses it. A
 * piece painted with the art's outline colour outlines the far edge of a revealed face, as the
 * raster does: each slice also has an inked copy (its faces in the outline colour, its fronts and
 * slopes as they are), drawn instead of it when the slice lies within [StackView.inkWidth] pixels
 * of the back of its columns. That width grows from nothing once a face is more than a pixel wide,
 * so the outline comes in continuously and at rest there is none.
 *
 * Everything is packed into one [image]; entry `(side, j)` is slice `j`'s rectangle in it and where
 * it is drawn from the sprite's top left. Making a stack allocates; drawing one allocates nothing.
 *
 * @param backed the piece stands against the wall (its sides darken where they meet it).
 */
internal class SolidStack(private val model: VolumeModel, private val backed: Boolean) {
    /** How many slices the piece has. */
    val slices = ceil(model.depth * StackTuning.SLICES_PER_ROW).toInt().coerceAtLeast(1)

    /** How many bands of one floor row of depth (the order a cat is put into) the piece has. */
    val bands = (slices + StackTuning.SLICES_PER_ROW - 1) / StackTuning.SLICES_PER_ROW

    /**
     * Whether the piece is painted with the art's outline colour, so its new edges are outlined.
     */
    val inked = model.palette.colours[model.palette.ink] == RampTuning.ART_INK

    /** The packed pictures: every slice in both shadings, plain and inked. */
    val image: ArgbImage

    /** A canvas version for [image] (a canvas keeps its bitmap while this stays the same). */
    val version = VERSIONS.getAndIncrement()

    private val entries = 4 * slices

    /** Each entry's rectangle in [image] (w 0: the entry is empty) and its place in the sprite. */
    val sx = IntArray(entries)
    val sy = IntArray(entries)
    val w = IntArray(entries)
    val h = IntArray(entries)
    val dx = IntArray(entries)
    val dy = IntArray(entries)

    /** The entry of slice [j] in [side]'s shading. */
    fun sliceEntry(side: Int, j: Int): Int = side * slices + j

    /** The entry of slice [j]'s inked copy in [side]'s shading. */
    fun inkedEntry(side: Int, j: Int): Int = (2 + side) * slices + j

    /** How many pixels of the picture are painted, across all entries (memory, tests). */
    val pixels: Int

    /** The deepest back of the columns in each slice: where its faces end. */
    private val sliceBack = FloatArray(slices)

    init {
        val cuts = arrayOfNulls<IntArray>(entries)
        val box = IntArray(4)
        val wm = model.width
        val hm = model.height
        val scratch = IntArray(wm * hm)
        for (side in 0..1) for (j in 0 until slices) {
            for (i in 0 until wm * hm) scratch[i] = colourOf(i, j, side, false)
            cuts[sliceEntry(side, j)] = crop(scratch, box, sliceEntry(side, j))
            if (!inked) continue
            for (i in 0 until wm * hm) scratch[i] = colourOf(i, j, side, true)
            cuts[inkedEntry(side, j)] = crop(scratch, box, inkedEntry(side, j))
        }
        for (j in 0 until slices) {
            for (i in 0 until wm * hm) {
                if (inSlice(i, j)) sliceBack[j] = maxOf(sliceBack[j], model.back[i])
            }
        }
        // shelf-pack the entries into one image, as the steps' sheets are packed
        var cx = 0
        var cy = 0
        var shelf = 0
        var painted = 0
        for (e in 0 until entries) {
            if (w[e] == 0) continue
            if (cx + w[e] > SheetTuning.WIDTH) {
                cx = 0
                cy += shelf + 1
                shelf = 0
            }
            sx[e] = cx
            sy[e] = cy
            cx += w[e] + 1
            shelf = maxOf(shelf, h[e])
        }
        val height = maxOf(1, cy + shelf)
        val out = IntArray(SheetTuning.WIDTH * height)
        for (e in 0 until entries) {
            val c = cuts[e] ?: continue
            for (y in 0 until h[e]) {
                System.arraycopy(c, y * w[e], out, (sy[e] + y) * SheetTuning.WIDTH + sx[e], w[e])
            }
            for (p in c) if (p ushr 24 != 0) painted++
        }
        image = ArgbImage(SheetTuning.WIDTH, height, out)
        pixels = painted
    }

    /** Whether column [i] (a pixel index, or -1 outside) reaches into slice [j]. */
    private fun inSlice(i: Int, j: Int): Boolean {
        if (i < 0 || !model.solid(i)) return false
        return model.front[i] < StackView.depthOf(j + 1) && model.back[i] > StackView.depthOf(j)
    }

    private fun at(x: Int, y: Int): Int =
        if (x in 0 until model.width && y in 0 until model.height) y * model.width + x else -1

    /**
     * What pixel [i] of slice [j] shows in [side]'s shading (0: nothing); with [inkFaces] a face
     * (not a front or a slope) is the outline colour.
     */
    private fun colourOf(i: Int, j: Int, side: Int, inkFaces: Boolean): Int {
        if (!inSlice(i, j)) return 0
        if (StackView.sliceOf(model.front[i]) >= j) return model.colours[i]
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
        if (from >= 0 && model.solid(from)) {
            if (model.front[i] - model.front[from] <= VolumeTuning.SLOPE_STEP) {
                return model.colours[i] // one surface: the slope keeps the painted colour
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
        if (deeperBehind && StackView.depthOf(j) >= back - VolumeTuning.CREVICE_REACH) {
            steps += VolumeTuning.CREVICE_STEP
        }
        val palette = model.palette
        if (inkFaces) return palette.colours[palette.ink]
        return palette.colours[palette.step(VolumeRaster.material(model, i, face), steps)]
    }

    /** Crop [pixels] (the sprite's size) to its painted box into entry [e]; null when empty. */
    private fun crop(pixels: IntArray, box: IntArray, e: Int): IntArray? {
        box[0] = Int.MAX_VALUE
        box[1] = Int.MAX_VALUE
        box[2] = -1
        box[3] = -1
        for (y in 0 until model.height) for (x in 0 until model.width) {
            if (pixels[y * model.width + x] == 0) continue
            box[0] = minOf(box[0], x)
            box[1] = minOf(box[1], y)
            box[2] = maxOf(box[2], x)
            box[3] = maxOf(box[3], y)
        }
        if (box[2] < 0) return null
        val cw = box[2] - box[0] + 1
        val ch = box[3] - box[1] + 1
        val out = IntArray(cw * ch)
        for (y in 0 until ch) {
            System.arraycopy(pixels, (box[1] + y) * model.width + box[0], out, y * cw, cw)
        }
        w[e] = cw
        h[e] = ch
        dx[e] = box[0]
        dy[e] = box[1]
        return out
    }

    /**
     * Draw band [k] of the piece through [c] in [slot], the piece's front plane at house position
     * ([x], [y]), seen with the view ([vx], [vy]) ([StackView]): its slices back to front, each
     * inked when it lies within the outline's width of its back. Nothing is allocated.
     */
    fun paintBand(c: RoomCanvas, slot: Int, k: Int, x: Float, y: Float, vx: Float, vy: Float) {
        val side = StackView.side(vx)
        val perRow = StackView.perRow(vx, vy)
        val last = minOf(slices, (k + 1) * StackTuning.SLICES_PER_ROW) - 1
        for (j in last downTo k * StackTuning.SLICES_PER_ROW) {
            var e = sliceEntry(side, j)
            if (inked && perRow > 0f) {
                val width = StackView.inkWidth(sliceBack[j] * perRow)
                if (width > 0f && StackView.depthOf(j) > sliceBack[j] - width / perRow) {
                    e = inkedEntry(side, j)
                }
            }
            if (w[e] == 0) continue
            draw(c, slot, e, StackView.sliceX(x, j, vx), StackView.sliceY(y, j, vy))
        }
    }

    private fun draw(c: RoomCanvas, slot: Int, e: Int, x: Float, y: Float) {
        c.stack(image, slot, version, sx[e], sy[e], w[e], h[e], x + dx[e], y + dy[e])
    }

    private companion object {
        val VERSIONS = AtomicInteger(1)
        const val EPSILON = 1e-4f
    }
}
