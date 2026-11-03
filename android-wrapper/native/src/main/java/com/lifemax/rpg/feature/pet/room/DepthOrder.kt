package com.lifemax.rpg.feature.pet.room

/**
 * The order the 2.5D room draws its things in for one frame, back to front: the bands of the solid
 * pieces and the atlas furniture (a [VolumeStep]'s own order) with the cats put between them.
 * Everything stands on a floor row (its key; nearer is larger), so this is a z-buffer at the
 * resolution of a floor row between flat cats and solid furniture:
 * - a cat is drawn after everything whose key is not more than its own and before everything nearer
 *   ([CAT_AFTER]): it walks behind the sofa's arm and the arm hides it, it stands beside the tree
 *   and the near half of a platform's side covers it while the far half does not;
 * - a cat standing on a piece (on the sofa, inside the tree on its middle platform) is at the depth
 *   of that surface ([PieceVolume.surfaces]) and is drawn after every band of that piece, even the
 *   nearer ones: those are pulled back to just before the cat, since a cat on a seat is on top of
 *   the seat's front, not behind it. Other pieces still sort by their own depth, so the cushion
 *   standing in front of the seat stays in front of the cat and the platform above covers its ears;
 * - two cats go by how near they reach (a cat on a piece reaches its front), then lower first if
 *   both are on pieces (the cat on the middle platform, the top platform's front, the cat on top),
 *   else by the row of their feet; a cat is never drawn before the one ordered ahead of it.
 *
 * The answer is [order] (codes: a band number, [VolumeStep.FURNITURE] plus an index, or [CAT] plus
 * a cat) for its first [size] entries; the arrays are reused, so a frame allocates nothing.
 */
internal class DepthOrder(capacity: Int) {
    /** The codes in drawing order. */
    val order = IntArray(capacity + MAX_CATS)

    /** How many entries of [order] are valid. */
    var size = 0
        private set

    private val drawn = BooleanArray(capacity)
    private val catOrder = IntArray(MAX_CATS)
    private val catDone = BooleanArray(MAX_CATS)

    /**
     * Work out the order of [step] with [count] cats at floor rows [catKey] (feet rows [catFeet])
     * standing on the pieces [catAnchor] (an index into the step's pieces, or -1 for none), each
     * under the piece [catCover] (drawn wholly after the cat; -1 for none).
     */
    fun build(
        step: VolumeStep,
        furnitureKey: FloatArray,
        count: Int,
        catKey: FloatArray,
        catFeet: FloatArray,
        catAnchor: IntArray,
        catCover: IntArray = NO_COVER,
    ) {
        cover = catCover
        val n = count.coerceAtMost(MAX_CATS)
        for (i in 0 until n) {
            catOrder[i] = i
            val a = catAnchor[i]
            reach[i] = if (a >= 0) maxOf(catKey[i], step.key[step.end[a] - 1]) else catKey[i]
        }
        if (n == 2 && behind(1, 0, catFeet, catAnchor)) {
            catOrder[0] = 1
            catOrder[1] = 0
        }
        catDone.fill(false)
        drawn.fill(false, 0, step.count)
        size = 0
        for (code in step.order) {
            val key =
                if (code >= VolumeStep.FURNITURE) furnitureKey[code - VolumeStep.FURNITURE]
                else step.key[code]
            for (k in
                0 until n) { // in the cats' order: a cat never goes before the one ahead of it
                val cat = catOrder[k]
                if (catDone[cat]) continue
                if (key <= catKey[cat] + CAT_AFTER) break
                emitCat(step, cat, catKey, catAnchor)
            }
            if (code < VolumeStep.FURNITURE) {
                if (drawn[code] || waitsFor(step.piece[code], n)) continue
                drawn[code] = true
            }
            order[size++] = code
        }
        for (k in 0 until n) if (!catDone[catOrder[k]])
            emitCat(step, catOrder[k], catKey, catAnchor)
    }

    /** Cat [cat], after the nearer bands of the piece it stands on. */
    private fun emitCat(step: VolumeStep, cat: Int, catKey: FloatArray, catAnchor: IntArray) {
        val anchor = catAnchor[cat]
        if (anchor >= 0) {
            for (b in step.first[anchor] until step.end[anchor]) {
                if (!drawn[b] && step.key[b] > catKey[cat]) {
                    drawn[b] = true
                    order[size++] = b
                }
            }
        }
        catDone[cat] = true
        order[size++] = CAT + cat
        val over = cover[cat]
        if (over >= 0) {
            for (b in step.first[over] until step.end[over]) {
                if (!drawn[b]) {
                    drawn[b] = true
                    order[size++] = b
                }
            }
        }
    }

    /** Whether [piece] covers a cat not yet drawn, so its bands wait for that cat. */
    private fun waitsFor(piece: Int, n: Int): Boolean {
        for (k in 0 until n) if (!catDone[k] && cover[k] == piece) return true
        return false
    }

    private var cover = NO_COVER

    /**
     * Whether cat [a] goes before cat [b]: the one that reaches less near first (a cat on a piece
     * reaches the front of that piece, whose near bands are drawn just before it). At the same
     * reach two cats on pieces go lower first (the one up on the platform is drawn over the one on
     * the platform below, with the upper platform's front between them); two on the floor go by
     * their feet, the one farther back first.
     */
    private fun behind(a: Int, b: Int, feet: FloatArray, anchor: IntArray): Boolean {
        if (reach[a] != reach[b]) return reach[a] < reach[b]
        return if (anchor[a] >= 0 && anchor[b] >= 0) feet[a] > feet[b] else feet[a] < feet[b]
    }

    private val reach = FloatArray(MAX_CATS)

    companion object {
        /** The code of cat `i` is `CAT + i`. */
        const val CAT = 2000

        /** The most cats a room has. */
        const val MAX_CATS = 2

        /** A cat sorts just after whatever stands on its own row (the classic room's rule). */
        const val CAT_AFTER = 0.001f

        private val NO_COVER = intArrayOf(-1, -1)
    }
}
