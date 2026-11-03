package com.lifemax.rpg.feature.pet.room

/**
 * The order the 2.5D room draws its things in, back to front: the furniture cut out of the
 * backdrops ([LayerPiece]s), the furniture the room draws from its atlas (bed, bowls, box, pouf,
 * cushion), and the cats between them.
 * - Everything that is not a cat sorts by its depth `z` (stable: things at the same depth keep
 *   their list order). A flat piece, a rug, is at -1: under everything.
 * - A cat is drawn after everything behind it: after the last thing whose depth is at most the
 *   cat's depth minus [CAT_AFTER], the same rule the classic room uses, so a cat is in front of a
 *   piece when it is deeper than the row of the piece's lowest pixel, and behind it when it is not
 *   (it walks behind the sofa, and the sofa's arm hides it).
 * - A cat that stands on a surface a piece provides (on the sofa, on the cat tree's middle
 *   platform) is drawn right after the last piece that provides it, whatever its depth is (it is in
 *   front of its own sofa, and inside the tree: in front of the tree's trunk and cubby and
 *   platform, behind the platform above it). Which surface a cat is on comes from
 *   [CatLayer.surface].
 * - Two cats at the same depth go by the row of their feet; the one farther back goes first.
 *
 * The answer is an array of codes ([PIECE], [FURNITURE], [CAT] plus an index) in [order], valid for
 * the [size] first entries; the stack reuses its arrays, so asking again allocates nothing.
 *
 * @param pieces the layer pieces, in manifest order.
 * @param furnitureZ the depth of each piece of atlas furniture, in the order the scene lists them.
 */
internal class LayerStack(
    private val pieces: List<LayerPiece>,
    private val furnitureZ: FloatArray,
) {
    private val statics = IntArray(pieces.size + furnitureZ.size)
    private val anchor = IntArray(MAX_CATS)
    private val catOrder = IntArray(MAX_CATS)
    private val done = BooleanArray(MAX_CATS)

    /** The codes in drawing order. */
    val order = IntArray(statics.size + MAX_CATS)

    /** How many entries of [order] are valid. */
    var size = 0
        private set

    init {
        for (i in statics.indices) statics[i] =
            if (i < pieces.size) PIECE + i else FURNITURE + i - pieces.size
        // insertion sort by depth: stable, and there are a dozen of them
        for (i in 1 until statics.size) {
            val v = statics[i]
            var j = i - 1
            while (j >= 0 && zOf(statics[j]) > zOf(v)) {
                statics[j + 1] = statics[j]
                j--
            }
            statics[j + 1] = v
        }
    }

    private fun zOf(code: Int): Float =
        if (code < FURNITURE) pieces[code - PIECE].z else furnitureZ[code - FURNITURE]

    /** Work out the drawing order for [cats]. */
    fun build(cats: CatLayer) {
        val n = cats.count.coerceAtMost(MAX_CATS)
        for (i in 0 until n) {
            catOrder[i] = i
            anchor[i] = anchorOf(cats.surface(i))
        }
        if (n == 2 && behind(cats, 1, 0)) {
            catOrder[0] = 1
            catOrder[1] = 0
        }
        size = 0
        done.fill(false)
        for (code in statics) {
            val z = zOf(code)
            for (k in 0 until n) {
                val cat = catOrder[k]
                if (!done[cat] && anchor[cat] < 0 && cats.depth(cat) + CAT_AFTER <= z) {
                    done[cat] = true
                    order[size++] = CAT + cat
                }
            }
            order[size++] = code
            if (code < FURNITURE) {
                for (k in 0 until n) {
                    val cat = catOrder[k]
                    if (!done[cat] && anchor[cat] == code - PIECE) {
                        done[cat] = true
                        order[size++] = CAT + cat
                    }
                }
            }
        }
        // the cats deeper than everything come last
        for (k in 0 until n) if (!done[catOrder[k]]) order[size++] = CAT + catOrder[k]
    }

    /** The index of the last piece that provides [surface], or -1 for none (or for the floor). */
    private fun anchorOf(surface: String?): Int {
        if (surface == null || surface == FLOOR) return -1
        var i = pieces.size - 1
        while (i >= 0) {
            if (surface in pieces[i].surfaces) return i
            i--
        }
        return -1
    }

    private fun behind(cats: CatLayer, a: Int, b: Int): Boolean {
        val za = cats.depth(a)
        val zb = cats.depth(b)
        return za < zb || (za == zb && cats.feet(a) < cats.feet(b))
    }

    companion object {
        /** The code of piece `i` is `PIECE + i`. */
        const val PIECE = 0

        /** The code of atlas furniture `k` is `FURNITURE + k`. */
        const val FURNITURE = 1000

        /** The code of cat `i` is `CAT + i`. */
        const val CAT = 2000

        /** The most cats a room has. */
        const val MAX_CATS = 2

        /** A cat sorts just after whatever stands at its own depth (the classic room's rule). */
        const val CAT_AFTER = 0.01f

        private const val FLOOR = "floor"
    }
}
