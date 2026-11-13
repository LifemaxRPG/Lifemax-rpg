package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/** Which face of a column a rendered pixel shows. */
internal object Face {
    const val NONE: Byte = 0

    /** The painted front: the sprite's own pixel. */
    const val FRONT: Byte = 1

    /** A side facing left (the light's side), facing right, a top, an underside. */
    const val LEFT: Byte = 2
    const val RIGHT: Byte = 3
    const val TOP: Byte = 4
    const val BOTTOM: Byte = 5

    /**
     * The small step between two neighbouring columns of one surface (the rows of a painted top,
     * the columns of a round trunk): part of that surface, so it keeps the painted colour.
     */
    const val SLOPE: Byte = 6
}

/** The light and the look of the faces the sway shows. */
internal object VolumeTuning {
    /** Ramp steps of each face from its material: the light comes from the windows, above left. */
    const val TOP_STEP = 1
    const val LEFT_STEP = -1
    const val RIGHT_STEP = -2
    const val BOTTOM_STEP = -2

    /** A side within this much of where its column meets the wall or a deeper part is shaded. */
    const val CREVICE_REACH = 1f
    const val CREVICE_STEP = -1

    /** A face pixel next to something at least this much deeper is on an edge: outlined. */
    const val EDGE_GAP = 1.5f

    /** How far into the piece a side looks for its material past the painted outline. */
    const val MATERIAL_REACH = 3

    /** Neighbouring columns whose fronts differ by at most this are one surface ([Face.SLOPE]). */
    const val SLOPE_STEP = 2f
}

/**
 * The rendered picture of one piece for one camera view, with what each pixel shows. [x0], [y0] is
 * the picture's top left relative to the sprite's (the sides can show left of and above it). Arrays
 * are sized once and reused; a pixel with no colour shows nothing of the piece.
 */
internal class VolumeFrame(capacity: Int) {
    var x0 = 0
    var y0 = 0
    var width = 0
    var height = 0

    /** The colour of each pixel, `0xAARRGGBB`; 0 where the piece does not cover it. */
    val colour = IntArray(capacity)

    /** How far behind the front plane each pixel's surface is, in floor rows. */
    val depth = FloatArray(capacity)

    /** Which face each pixel shows ([Face]). */
    val face = ByteArray(capacity)

    /** The model pixel (column) each pixel shows. */
    val cell = IntArray(capacity)

    /** Whether pixel [i] shows a face the camera at rest would not see. */
    fun revealed(i: Int): Boolean = face[i] > Face.FRONT
}

/**
 * Renders a [VolumeModel] for a camera view: an oblique projection in which a point [depth] rows
 * behind the front plane is drawn `depth * vx` pixels across and `depth * vy` down from where it
 * stands, so the front plane stays put, the back lags, and the sides and tops between them show.
 * Each picture pixel casts one ray back through the model's columns (a grid walk, exact, nearest
 * surface first: a z-buffer by construction) and shows the face it meets:
 * - a column's front: the painted pixel, untouched (so a view of (0, 0) is the sprite exactly);
 * - a side, top or underside: the colour of the material behind the painted outline there, stepped
 *   along the piece's own ramp by the face's light ([VolumeTuning]), one more step dark in a
 *   crevice (where the side meets the wall or a deeper part of the piece), and the piece's ink
 *   where the face ends at an edge, as the painted outline does.
 *
 * Pure arithmetic; nothing is allocated (the frame is the caller's).
 */
internal object VolumeRaster {
    /** The picture a view ([vx], [vy]) needs, `width * height`, for sizing a [VolumeFrame]. */
    fun size(model: VolumeModel, vx: Float, vy: Float): Int {
        val w = model.width + span(model.depth * vx)
        val h = model.height + span(model.depth * vy)
        return w * h
    }

    private fun span(reach: Float): Int = ceil(abs(reach).toDouble()).toInt() + 1

    /**
     * Render [model] seen with offsets ([vx], [vy]) pixels per floor row of depth into [out].
     * [backed]: the piece's back stands against the wall (its sides darken where they meet it).
     */
    fun render(model: VolumeModel, vx: Float, vy: Float, backed: Boolean, out: VolumeFrame) {
        cover(model, vx, vy, out)
        shade(model, backed, out)
    }

    /**
     * Which pixels [model] covers seen with offsets ([vx], [vy]), and at what depth, into [out]:
     * [render] without the shading of the faces (a covered pixel has a colour, the painted one).
     */
    fun cover(model: VolumeModel, vx: Float, vy: Float, out: VolumeFrame) {
        val rx = model.depth * vx
        val ry = model.depth * vy
        out.x0 = if (rx < 0f) -span(rx) else 0
        out.y0 = if (ry < 0f) -span(ry) else 0
        out.width = model.width + if (rx == 0f) 0 else span(rx)
        out.height = model.height + if (ry == 0f) 0 else span(ry)
        require(out.width * out.height <= out.colour.size) { "the frame is too small" }
        for (y in 0 until out.height) for (x in 0 until out.width) {
            cast(model, x + out.x0, y + out.y0, vx, vy, out, y * out.width + x)
        }
    }

    /**
     * One ray from the picture pixel at sprite position ([px], [py]): walk the model's columns in
     * the order the ray crosses them and stop at the first solid one it passes through.
     */
    private fun cast(
        model: VolumeModel,
        px: Int,
        py: Int,
        vx: Float,
        vy: Float,
        out: VolumeFrame,
        at: Int,
    ) {
        out.face[at] = Face.NONE
        out.colour[at] = 0
        // the ray: at depth d it is over the point (px + 0.5 - d vx, py + 0.5 - d vy)
        val ux = -vx
        val uy = -vy
        var cx = px
        var cy = py
        val stepX = if (ux > 0f) 1 else -1
        val stepY = if (uy > 0f) 1 else -1
        val deltaX = if (ux == 0f) Float.MAX_VALUE else 1f / abs(ux)
        val deltaY = if (uy == 0f) Float.MAX_VALUE else 1f / abs(uy)
        var nextX = if (ux == 0f) Float.MAX_VALUE else 0.5f * deltaX
        var nextY = if (uy == 0f) Float.MAX_VALUE else 0.5f * deltaY
        var dIn = 0f
        var entered = Face.FRONT
        val end = model.depth
        while (dIn < end) {
            val dOut = minOf(nextX, nextY, end)
            if (cx in 0 until model.width && cy in 0 until model.height) {
                val i = cy * model.width + cx
                if (model.solid(i)) {
                    val f = model.front[i]
                    if (f < dOut && model.back[i] > dIn) {
                        val front = f >= dIn
                        out.face[at] =
                            when {
                                front -> Face.FRONT
                                slope(model, cx, cy, entered, f) -> Face.SLOPE
                                else -> entered
                            }
                        out.depth[at] = if (front) f else dIn
                        out.cell[at] = i
                        out.colour[at] = model.colours[i]
                        return
                    }
                }
            } else if (leaving(cx, cy, stepX, stepY, ux, uy, model)) {
                return
            }
            if (nextX <= nextY) {
                cx += stepX
                dIn = nextX
                nextX += deltaX
                entered = if (stepX > 0) Face.LEFT else Face.RIGHT
            } else {
                cy += stepY
                dIn = nextY
                nextY += deltaY
                entered = if (stepY > 0) Face.TOP else Face.BOTTOM
            }
        }
    }

    /**
     * Whether the side of column ([cx], [cy]) the ray entered through ([entered]) is a small step
     * from the column it came from, which is solid and starts a little nearer: one surface.
     */
    private fun slope(model: VolumeModel, cx: Int, cy: Int, entered: Byte, front: Float): Boolean {
        val px =
            when (entered) {
                Face.LEFT -> cx - 1
                Face.RIGHT -> cx + 1
                else -> cx
            }
        val py =
            when (entered) {
                Face.TOP -> cy - 1
                Face.BOTTOM -> cy + 1
                else -> cy
            }
        if (px !in 0 until model.width || py !in 0 until model.height) return false
        val p = py * model.width + px
        return model.solid(p) && front - model.front[p] <= VolumeTuning.SLOPE_STEP
    }

    /** True when the ray is outside the model and moving away from it: it can meet nothing more. */
    private fun leaving(
        cx: Int,
        cy: Int,
        sx: Int,
        sy: Int,
        ux: Float,
        uy: Float,
        model: VolumeModel,
    ): Boolean {
        val goneX = (cx < 0 && (ux == 0f || sx < 0)) || (cx >= model.width && (ux == 0f || sx > 0))
        val goneY = (cy < 0 && (uy == 0f || sy < 0)) || (cy >= model.height && (uy == 0f || sy > 0))
        return goneX || goneY
    }

    /** Colour the faces the sway shows: material, light, crevices, then the edges. */
    private fun shade(model: VolumeModel, backed: Boolean, out: VolumeFrame) {
        val palette = model.palette
        val n = out.width * out.height
        for (at in 0 until n) {
            if (!out.revealed(at) || out.face[at] == Face.SLOPE) continue
            val cell = out.cell[at]
            var steps =
                when (out.face[at]) {
                    Face.TOP -> VolumeTuning.TOP_STEP
                    Face.LEFT -> VolumeTuning.LEFT_STEP
                    Face.RIGHT -> VolumeTuning.RIGHT_STEP
                    else -> VolumeTuning.BOTTOM_STEP
                }
            val back = model.back[cell]
            val deeperBehind = backed || back < model.depth - VolumeTuning.CREVICE_REACH
            if (deeperBehind && out.depth[at] >= back - VolumeTuning.CREVICE_REACH) {
                steps += VolumeTuning.CREVICE_STEP
            }
            out.colour[at] =
                palette.colours[palette.step(material(model, cell, out.face[at]), steps)]
        }
        // a piece painted without the art's outline (the cat tree, the lamp) gets none on its sides
        val ink = palette.colours[palette.ink]
        if (ink != RampTuning.ART_INK) return
        for (at in 0 until n) {
            if (out.revealed(at) && out.face[at] != Face.SLOPE && onEdge(out, at)) {
                out.colour[at] = ink
            }
        }
    }

    /**
     * The palette index of what a side of column [cell] is made of: the painted pixel there, or, if
     * that is the outline, the first pixel inward past it (a few pixels at most).
     */
    fun material(model: VolumeModel, cell: Int, face: Byte): Int {
        val palette = model.palette
        val dx =
            when (face) {
                Face.LEFT -> 1
                Face.RIGHT -> -1
                else -> 0
            }
        val dy =
            when (face) {
                Face.TOP -> 1
                Face.BOTTOM -> -1
                else -> 0
            }
        var x = cell % model.width
        var y = cell / model.width
        var index = model.index[cell]
        for (k in 0 until VolumeTuning.MATERIAL_REACH) {
            if (palette.colours[index] != RampTuning.ART_INK) return index
            x += dx
            y += dy
            if (x !in 0 until model.width || y !in 0 until model.height) break
            val next = model.index[y * model.width + x]
            if (next < 0) break
            index = next
        }
        return model.index[cell]
    }

    /**
     * Whether a shown face pixel is on an edge: beside nothing, or beside something clearly deeper,
     * with more of the shown face on its other side (a face one pixel wide keeps its colour, so a
     * sliver of side reads as a side and not as a thicker outline).
     */
    private fun onEdge(out: VolumeFrame, at: Int): Boolean {
        val x = at % out.width
        val y = at / out.width
        for (k in 0 until 4) {
            val dx = DX[k]
            val dy = DY[k]
            if (!open(out, x + dx, y + dy, out.depth[at], out.face[at])) continue
            val ix = x - dx
            val iy = y - dy
            if (ix in 0 until out.width && iy in 0 until out.height) {
                if (out.revealed(iy * out.width + ix)) return true
            }
        }
        return false
    }

    /**
     * Whether the pixel at ([x], [y]) is open beside a pixel of [face] at [depth]: nothing there,
     * or something clearly deeper that is not more of the same face (a side runs back in depth
     * across its own width, so its own pixels are never an edge to it).
     */
    private fun open(out: VolumeFrame, x: Int, y: Int, depth: Float, face: Byte): Boolean {
        if (x !in 0 until out.width || y !in 0 until out.height) return true
        val i = y * out.width + x
        if (out.face[i] == Face.NONE) return true
        if (out.face[i] == face || out.face[i] == Face.SLOPE) return false
        return out.depth[i] > depth + VolumeTuning.EDGE_GAP
    }

    private val DX = intArrayOf(-1, 1, 0, 0)
    private val DY = intArrayOf(0, 0, -1, 1)

    /** The band (whole floor rows of depth) a pixel of [depth] is drawn in. */
    fun band(depth: Float): Int = floor(depth).toInt()
}
