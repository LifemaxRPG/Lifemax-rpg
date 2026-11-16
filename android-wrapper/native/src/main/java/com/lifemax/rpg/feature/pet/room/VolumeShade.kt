package com.lifemax.rpg.feature.pet.room

/** Which face of a column a pixel of a piece's faces shows. */
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

/** The light and the look of the faces the camera's angle shows. */
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

/** What a revealed face of a piece is made of. */
internal object VolumeShade {
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
}
