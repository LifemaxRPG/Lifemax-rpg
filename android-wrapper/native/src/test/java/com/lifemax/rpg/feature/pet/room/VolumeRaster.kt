package com.lifemax.rpg.feature.pet.room

import kotlin.math.abs
import kotlin.math.ceil

/**
 * The rendered picture of one piece for one camera view, with what each pixel shows. [x0], [y0] is
 * the picture's top left relative to the sprite's (the sides can show left of and above it).
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
 * The reference renderer of a [VolumeModel], test-only: the exact ray cast the room used to cache
 * per camera step before the furniture's depth became continuous ([SolidFaces]). The faces are
 * tested against it at the views it rendered. An oblique projection in which a point [depth] rows
 * behind the front plane is drawn `depth * vx` pixels across and `depth * vy` down; each picture
 * pixel casts one ray back through the model's columns (nearest surface first) and shows the face
 * it meets: a column's front (the painted pixel), or a side, top or underside shaded along the
 * piece's own ramp ([VolumeTuning]), darker in a crevice, and in the piece's ink at an edge.
 */
internal object VolumeRaster {
    /** The picture a view ([vx], [vy]) needs, `width * height`, for sizing a [VolumeFrame]. */
    fun size(model: VolumeModel, vx: Float, vy: Float): Int {
        val w = model.width + span(model.depth * vx)
        val h = model.height + span(model.depth * vy)
        return w * h
    }

    private fun span(reach: Float): Int = ceil(abs(reach).toDouble()).toInt() + 1

    /** Render [model] seen with offsets ([vx], [vy]) into [out]; [backed]: against the wall. */
    fun render(model: VolumeModel, vx: Float, vy: Float, backed: Boolean, out: VolumeFrame) {
        cover(model, vx, vy, out)
        shade(model, backed, out)
    }

    /** Which pixels [model] covers seen with offsets ([vx], [vy]), and at what depth. */
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
                palette.colours[
                        palette.step(VolumeShade.material(model, cell, out.face[at]), steps)]
        }
        val ink = palette.colours[palette.ink]
        if (ink != RampTuning.ART_INK) return
        for (at in 0 until n) {
            if (out.revealed(at) && out.face[at] != Face.SLOPE && onEdge(out, at)) {
                out.colour[at] = ink
            }
        }
    }

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

    private fun open(out: VolumeFrame, x: Int, y: Int, depth: Float, face: Byte): Boolean {
        if (x !in 0 until out.width || y !in 0 until out.height) return true
        val i = y * out.width + x
        if (out.face[i] == Face.NONE) return true
        if (out.face[i] == face || out.face[i] == Face.SLOPE) return false
        return out.depth[i] > depth + VolumeTuning.EDGE_GAP
    }

    private val DX = intArrayOf(-1, 1, 0, 0)
    private val DY = intArrayOf(0, 0, -1, 1)
}
