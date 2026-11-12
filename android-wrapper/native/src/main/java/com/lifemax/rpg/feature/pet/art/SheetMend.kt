package com.lifemax.rpg.feature.pet.art

/**
 * Two small mends of the shipped side sheets, made as [com.lifemax.rpg.feature.pet.presentation]
 * loads them (like [RestingFloor]; the art files and the page are unchanged). Both come from
 * `tools/audit_cat_sprites.py` (see `docs/specs/PET_SPRITE_CONSISTENCY_AUDIT.md`).
 *
 * [OutlineGaps]: on the kitten sheets (stages 0 to 3) the front of the face has one or two pixels
 * of coat colour where the outline ring should be, in nearly every frame of every row.
 *
 * [RestingShade]: the shading of a still pose is dithered texture, and the art redraws some of it
 * from frame to frame while nothing around it moves: a coat pixel flips between two coat shades (10
 * to 28 pixels a step on the adult's sitting rows, 40 to 50 on a black coat being 19 L* apart) with
 * the same neighbours in both frames. On a resting cat that reads as the coat shimmering.
 */
object OutlineGaps {
    /**
     * [sheet] with every coat-shade pixel of the [anims] rows that sits on the silhouette's edge
     * next to the outline (a 4-neighbour inside the frame is clear and another is outline) drawn as
     * outline; [sheet] itself if there is none. Rows outside the sheet are skipped.
     */
    fun close(sheet: IndexSheet, table: AnimTable, anims: Collection<Anim>): IndexSheet {
        var out: IntArray? = null
        val seen = HashSet<Int>()
        val fw = table.frameWidth
        val fh = table.frameHeight
        for (anim in anims) {
            if (anim.depth || !seen.add(anim.row)) continue
            if ((anim.row + 1) * fh > sheet.height || anim.frames * fw > sheet.width) continue
            // a closed pixel can leave the next coat pixel along the edge open: go on until none is
            for (pass in 0 until MAX_PASSES) {
                val pixels = out ?: sheet.indices
                val open = ArrayList<Int>()
                for (f in 0 until anim.frames) for (y in 0 until fh) for (x in 0 until fw) {
                    val at = (anim.row * fh + y) * sheet.width + f * fw + x
                    if (isCoat(pixels[at]) && isOpen(pixels, sheet.width, at, x, y, fw, fh)) {
                        open.add(at)
                    }
                }
                if (open.isEmpty()) break
                val copy = out ?: sheet.indices.copyOf().also { out = it }
                for (at in open) copy[at] = OUTLINE
            }
        }
        return out?.let { IndexSheet(sheet.width, sheet.height, it) } ?: sheet
    }

    /** True if the pixel at [at] has a clear 4-neighbour and an outline one inside its frame. */
    private fun isOpen(
        p: IntArray,
        width: Int,
        at: Int,
        x: Int,
        y: Int,
        fw: Int,
        fh: Int,
    ): Boolean {
        var clear = false
        var outline = false
        for (d in 0 until 4) {
            val nx = x + DX[d]
            val ny = y + DY[d]
            if (nx < 0 || ny < 0 || nx >= fw || ny >= fh) continue
            val n = p[at + DY[d] * width + DX[d]]
            if (n == CLEAR) clear = true else if (n == OUTLINE) outline = true
        }
        return clear && outline
    }

    private const val MAX_PASSES = 4
    private val DX = intArrayOf(0, 0, -1, 1)
    private val DY = intArrayOf(-1, 1, 0, 0)
}

object RestingShade {
    /**
     * [sheet] with the rows [anims] frozen: in every frame of a row, a coat-shade pixel takes the
     * shade it has most often (the earliest frame's on a tie) among the frames where it is a coat
     * shade and its 3x3 neighbourhood has the same kind of pixel (clear, outline, coat, other) in
     * every cell. Where anything around it moved, blinked or opened, it is left as drawn. [sheet]
     * itself if nothing changes. Depth rows and rows outside the sheet are skipped.
     */
    fun freeze(sheet: IndexSheet, table: AnimTable, anims: Collection<Anim>): IndexSheet {
        var out: IntArray? = null
        val seen = HashSet<Int>()
        for (anim in anims) {
            if (anim.depth || !seen.add(anim.row)) continue
            if ((anim.row + 1) * table.frameHeight > sheet.height) continue
            if (anim.frames * table.frameWidth > sheet.width) continue
            val frozen = freezeRow(sheet, table, anim) ?: continue
            val pixels = out ?: sheet.indices.copyOf().also { out = it }
            for ((at, index) in frozen) pixels[at] = index
        }
        return out?.let { IndexSheet(sheet.width, sheet.height, it) } ?: sheet
    }

    /** The pixels of one row that differ from their frozen shade: sheet offset to new index. */
    fun changes(sheet: IndexSheet, table: AnimTable, anim: Anim): Map<Int, Int> =
        freezeRow(sheet, table, anim) ?: emptyMap()

    private fun freezeRow(sheet: IndexSheet, table: AnimTable, anim: Anim): Map<Int, Int>? {
        val fw = table.frameWidth
        val fh = table.frameHeight
        val n = anim.frames
        fun at(f: Int, x: Int, y: Int) = (anim.row * fh + y) * sheet.width + f * fw + x

        fun kind(index: Int) =
            when {
                index == CLEAR -> 0
                index == OUTLINE -> 1
                isCoat(index) -> 2
                else -> 3
            }

        fun signature(f: Int, x: Int, y: Int): Int {
            var s = 0
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                val k =
                    if (nx < 0 || ny < 0 || nx >= fw || ny >= fh) 0
                    else kind(sheet.indices[at(f, nx, ny)])
                s = s * 4 + k
            }
            return s
        }

        var out: HashMap<Int, Int>? = null
        val sigs = IntArray(n)
        for (y in 0 until fh) for (x in 0 until fw) {
            var coats = 0
            for (f in 0 until n) if (isCoat(sheet.indices[at(f, x, y)])) coats++
            if (coats < 2) continue
            for (f in 0 until n) sigs[f] = signature(f, x, y)
            for (f in 0 until n) {
                val own = sheet.indices[at(f, x, y)]
                if (!isCoat(own)) continue
                // the shades this pixel has in the frames that look the same around it
                val counts = IntArray(MAX_INDEX + 1)
                var best = own
                var bestCount = 0
                var bestFrame = n
                for (g in 0 until n) {
                    val v = sheet.indices[at(g, x, y)]
                    if (!isCoat(v) || sigs[g] != sigs[f]) continue
                    counts[v]++
                }
                for (g in 0 until n) {
                    val v = sheet.indices[at(g, x, y)]
                    if (!isCoat(v) || sigs[g] != sigs[f]) continue
                    if (counts[v] > bestCount || (counts[v] == bestCount && g < bestFrame)) {
                        best = v
                        bestCount = counts[v]
                        bestFrame = g
                    }
                }
                if (best != own) (out ?: HashMap<Int, Int>().also { out = it })[at(f, x, y)] = best
            }
        }
        return out
    }

    private const val MAX_INDEX = 255
}

private const val CLEAR = 0
private const val OUTLINE = 10

/** A coat shade: palette index 20 to 80 (the lit ramp 20-50 and the stripes 60-80). */
private fun isCoat(index: Int): Boolean = index in 20..80
