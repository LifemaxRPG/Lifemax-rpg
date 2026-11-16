package com.lifemax.rpg.feature.pet.room

/**
 * What a revealed face of a solid piece looks like: the piece's authored side and top textures
 * (docs/specs/CAT_ROOM_CONTINUOUS_DEPTH.md, "Authored faces"). [SolidFaces] asks the skin for every
 * pixel of a side or a top it bakes, with the pixel's place on that face ([FaceSpot]: its row and
 * column on the sprite, its depth, how far it is behind the column's front and before its back);
 * the skin answers with a colour of the piece's own palette and how many ramp steps lighter or
 * darker it is ([FaceTex]), or leaves the pixel to the plain rules ([FaceTex.KEEP]: the colour of
 * the edge it is extruded from). The room's light is added by [SolidFaces] on top of what the skin
 * answers (a top one step lighter, a left side one darker, a right side two, a crevice one more),
 * so a skin paints a surface as it is, not as it is lit.
 *
 * A skin runs only while the faces are baked (once per art, on the background thread); it may
 * allocate and need not be quick.
 */
internal fun interface FaceSkin {
    /** The texture of the face pixel at [spot]: a [FaceTex] code. */
    fun paint(spot: FaceSpot): Int

    companion object {
        /** No authored texture: every face is the plain rules' (the edge's colour, re-shaded). */
        val PLAIN = FaceSkin { FaceTex.KEEP }
    }
}

/**
 * A skin's answer, packed in an Int: a palette index of the piece and a number of ramp steps (a
 * texture's own light and dark: a seam, a grain line, an edge catching the light), or one of the
 * two special answers.
 */
internal object FaceTex {
    /** Leave the pixel to the plain rules. */
    const val KEEP = -1

    /** The piece's outline colour (its ink), unlit. */
    const val INK = -2

    private const val BIAS = 64
    private const val EXACT = 1 shl 30

    /** Palette index [index], [steps] ramp steps lighter (negative: darker). */
    fun of(index: Int, steps: Int = 0): Int = ((steps + BIAS) shl 16) or index

    /**
     * Exactly palette index [index], the room's light not added: a colour the skin chose for the
     * side it is on (where a ramp would step into another material's colour).
     */
    fun exact(index: Int): Int = EXACT or (BIAS shl 16) or index

    /** Whether a code is [exact]. */
    fun isExact(code: Int): Boolean = code >= 0 && code and EXACT != 0

    /** The palette index of a code made by [of]. */
    fun index(code: Int): Int = code and 0xFFFF

    /** The ramp steps of a code made by [of]. */
    fun steps(code: Int): Int = ((code ushr 16) and 0xFF) - BIAS
}

/**
 * One pixel of a revealed face as a skin sees it, reused for every pixel of a piece while its faces
 * are baked. Coordinates are the sprite's (x right, y down); depth is in floor rows behind the
 * piece's front plane.
 */
internal class FaceSpot(val model: VolumeModel) {
    /** The pixel's index in the sprite, its column and row. */
    var i = 0
        private set

    val x: Int
        get() = i % model.width

    val y: Int
        get() = i / model.width

    /** The depth strip ([SolidFaces]) the pixel is baked into. */
    var j = 0
        private set

    /** Which face: [Face.LEFT], [Face.RIGHT] or [Face.TOP]. */
    var face: Byte = Face.NONE
        private set

    /**
     * Whether the face is a slope: a small step of the surface the front shows (a rounded top, a
     * round post), painted as that surface; the room's light is not added to it.
     */
    var slope = false
        private set

    /** What the face is made of: the painted colour, or the first one inward past the outline. */
    var material = 0
        private set

    internal fun set(i: Int, j: Int, face: Byte, slope: Boolean) {
        this.i = i
        this.j = j
        this.face = face
        this.slope = slope
        material = VolumeShade.material(model, i, face)
    }

    val side: Boolean
        get() = face == Face.LEFT || face == Face.RIGHT

    val top: Boolean
        get() = face == Face.TOP

    /** The depth of the middle of the pixel's strip. */
    val depth: Float
        get() = FaceView.depthOf(j) + 0.5f / FaceTuning.SLICES_PER_ROW

    /** Where the pixel's column starts and ends. */
    val front: Float
        get() = model.front[i]

    val back: Float
        get() = model.back[i]

    /** How many strips behind the strip of the column's front (1: the first strip of a face). */
    val behind: Int
        get() = j - FaceView.sliceOf(front)

    /** How many strips before the column's last strip (0: the last, at its back). */
    val before: Int
        get() = FaceView.slices(back) - 1 - j

    /** How far along its column the pixel is: 0 at the front, 1 at the back. */
    val along: Float
        get() = ((depth - front) / (back - front)).coerceIn(0f, 1f)

    /**
     * Which of [n] equal bands of its column's depth the pixel is in (0 at the front). A texture
     * changes along the depth only band by band, never strip by strip: a side is its depth drawn a
     * few device pixels wide, and a feature one strip wide would come and go as the camera turns. A
     * short column has fewer bands (each at least three strips deep), numbered as [n]'s would be.
     */
    fun band(n: Int): Int {
        val strips = FaceView.slices(back) - FaceView.sliceOf(front)
        val m = minOf(n, maxOf(1, strips / 3))
        return (along * m).toInt().coerceAtMost(m - 1) * n / m
    }

    /**
     * Whether nothing of the piece lies past the pixel on the way the face looks (left of a left
     * face on its row, above a top in its column): the outside of the piece, not a face inside it.
     */
    val outer: Boolean
        get() {
            val dx =
                when (face) {
                    Face.LEFT -> -1
                    Face.RIGHT -> 1
                    else -> 0
                }
            val dy = if (face == Face.TOP) -1 else 0
            var px = x + dx
            var py = y + dy
            while (px in 0 until model.width && py in 0 until model.height) {
                if (model.solid(py * model.width + px)) return false
                px += dx
                py += dy
            }
            return true
        }

    /** Whether the column at ([px], [py]) reaches the pixel's strip. */
    fun reaches(px: Int, py: Int): Boolean {
        if (px !in 0 until model.width || py !in 0 until model.height) return false
        val k = py * model.width + px
        return model.solid(k) &&
            model.front[k] < FaceView.depthOf(j + 1) &&
            model.back[k] > FaceView.depthOf(j)
    }

    /** How many rows of the piece lie above the pixel at its depth (0: the face's top edge). */
    val fromTop: Int
        get() {
            var n = 0
            while (reaches(x, y - n - 1)) n++
            return n
        }

    /** How many rows of the piece lie below the pixel at its depth (0: the face's bottom edge). */
    val fromBottom: Int
        get() {
            var n = 0
            while (reaches(x, y + n + 1)) n++
            return n
        }

    /**
     * What the face is made of past any outline: the painted colour, or the first one inward past
     * the art's ink and the piece's own darkest outline colour (a few pixels at most).
     */
    val body: Int
        get() {
            val palette = model.palette
            val dx =
                when (face) {
                    Face.LEFT -> 1
                    Face.RIGHT -> -1
                    else -> 0
                }
            val dy = if (face == Face.TOP) 1 else 0
            for (k in 0..VolumeTuning.MATERIAL_REACH) {
                val c = at(x + k * dx, y + k * dy)
                if (c < 0) break
                if (c != palette.ink && palette.colours[c] != RampTuning.ART_INK) return c
            }
            return material
        }

    /** Whether the front's topmost pixel in the pixel's column is the art's ink. */
    val topInked: Boolean
        get() {
            var top = y
            while (at(x, top - 1) >= 0) top--
            return model.colours[top * model.width + x] or OPAQUE == RampTuning.ART_INK
        }

    /** The palette indices of those of [argbs] the piece is painted with. */
    fun coloursOf(argbs: IntArray): Set<Int> =
        argbs.map { model.palette.indexOf(it) }.filter { it >= 0 }.toSet()

    /** Whether the pixel's material is the piece's outline colour (an outline row, extruded). */
    val inked: Boolean
        get() = model.palette.colours[material] == RampTuning.ART_INK

    /** Whether the piece is painted with the art's outline colour. */
    val outlined: Boolean
        get() = model.palette.ink.let { model.palette.colours[it] == RampTuning.ART_INK }

    /** The palette index painted at ([px], [py]) of the sprite (-1 where it is clear). */
    fun at(px: Int, py: Int): Int =
        if (px in 0 until model.width && py in 0 until model.height) {
            model.index[py * model.width + px]
        } else -1

    /** The palette index of [argb], or the pixel's own material if the piece has no such colour. */
    fun colour(argb: Int): Int = model.palette.indexOf(argb).let { if (it < 0) material else it }

    /** A fixed pseudo-random number 0 to 255 for the given numbers (a texture's noise). */
    fun noise(a: Int, b: Int, c: Int = 0): Int {
        var h = a * 374761393 + b * 668265263 + c * 1274126177
        h = (h xor (h ushr 13)) * 1103515245
        return (h xor (h ushr 16)) and 255
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
    }
}
