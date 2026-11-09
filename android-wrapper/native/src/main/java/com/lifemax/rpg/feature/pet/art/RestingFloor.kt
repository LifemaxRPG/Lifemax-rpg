package com.lifemax.rpg.feature.pet.art

/**
 * Mends the resting rows of a sheet where one frame's drawing sinks through the floor.
 *
 * A sitting, loafing or sleeping cat rests on the same floor in every frame of its row: the paws
 * and the tail lying along them end on one pixel row. A few frames of the shipped art break that by
 * a pixel: the tail lying in front of a sitting stage-2 kitten drops one row in the middle of
 * `idle_sit` frame 2 (an eye-open frame, so it comes round every couple of seconds), and the tail
 * of `sleep` (stage 4) and adult `sleep_w` does the same in frame 2. On the screen the sunk pixels
 * read as a lump hanging under the cat, there in one frame and gone in the next, while the cat is
 * otherwise still.
 *
 * The mend keeps every other part of the frame (the head, ears and eyes move as drawn) and only
 * puts back the band that lies on the floor where it sank: in the columns where the frame reaches
 * below the floor that most of the row's frames share (and [MARGIN_COLUMNS] beside them), the
 * [BAND_ROWS] rows above the floor and everything below it are taken from the frame of the row that
 * rests on the floor and differs least there. A row whose frames do not mostly share one floor is
 * left alone, and a sheet with nothing to mend is returned as it is.
 *
 * The page draws the sheets unmended (this is a native drawing fix; the art files are unchanged),
 * so the art's fidelity tests read the sheets through [PetArt], before this.
 */
object RestingFloor {
    /** Rows above the floor that belong to the band lying on it (the tail's height). */
    const val BAND_ROWS = 5

    /** Columns beside the sunk ones that are mended too: the band's ends move with it. */
    const val MARGIN_COLUMNS = 1

    /**
     * [sheet] with the resting rows [anims] mended (see [RestingFloor]); [sheet] itself when no
     * frame of them sinks. Rows outside the sheet and depth rows are skipped.
     */
    fun mend(sheet: IndexSheet, table: AnimTable, anims: Collection<Anim>): IndexSheet {
        var out: IntArray? = null
        val seen = HashSet<Int>()
        for (anim in anims) {
            if (anim.depth || !seen.add(anim.row)) continue
            if ((anim.row + 1) * table.frameHeight > sheet.height) continue
            if (anim.frames * table.frameWidth > sheet.width) continue
            val pixels = out ?: sheet.indices
            val mended = mendRow(pixels, sheet.width, table, anim) ?: continue
            out = mended
        }
        return out?.let { IndexSheet(sheet.width, sheet.height, it) } ?: sheet
    }

    /**
     * The frames of [anim] whose lowest row lies below the floor most of them share, or an empty
     * list; for tests and for the mend.
     */
    fun sunkFrames(sheet: IndexSheet, table: AnimTable, anim: Anim): List<Int> {
        val lows = IntArray(anim.frames) { lowestRow(sheet.indices, sheet.width, table, anim, it) }
        val floor = sharedFloor(lows) ?: return emptyList()
        return lows.indices.filter { lows[it] > floor }
    }

    /** Mends one row in place of a copy of [pixels]; null if nothing in it sinks. */
    private fun mendRow(pixels: IntArray, width: Int, table: AnimTable, anim: Anim): IntArray? {
        val fw = table.frameWidth
        val fh = table.frameHeight
        val lows = IntArray(anim.frames) { lowestRow(pixels, width, table, anim, it) }
        val floor = sharedFloor(lows) ?: return null
        var out: IntArray? = null
        for (f in lows.indices) {
            if (lows[f] <= floor) continue
            val src = out ?: pixels
            // the columns that reach below the floor, and their neighbours
            var from = fw
            var to = -1
            for (y in floor + 1 until fh) for (x in 0 until fw) {
                if (src[at(width, table, anim, f, x, y)] != CLEAR) {
                    from = minOf(from, x)
                    to = maxOf(to, x)
                }
            }
            from = maxOf(0, from - MARGIN_COLUMNS)
            to = minOf(fw - 1, to + MARGIN_COLUMNS)
            val top = maxOf(0, floor - BAND_ROWS + 1)
            val donor = donorFor(src, width, table, anim, lows, floor, f, from, to, top)
            val mended = out ?: pixels.copyOf().also { out = it }
            for (y in top until fh) for (x in from..to) {
                mended[at(width, table, anim, f, x, y)] =
                    mended[at(width, table, anim, donor, x, y)]
            }
        }
        return out
    }

    /**
     * The frame resting on [floor] whose pixels in the mended area differ least from frame [f]'s;
     * of equals, the one closest before it in the row.
     */
    private fun donorFor(
        pixels: IntArray,
        width: Int,
        table: AnimTable,
        anim: Anim,
        lows: IntArray,
        floor: Int,
        f: Int,
        from: Int,
        to: Int,
        top: Int,
    ): Int {
        var best = -1
        var bestCost = Int.MAX_VALUE
        var bestGap = Int.MAX_VALUE
        for (g in lows.indices) {
            if (lows[g] != floor) continue
            var cost = 0
            for (y in top until table.frameHeight) for (x in from..to) {
                if (
                    pixels[at(width, table, anim, g, x, y)] !=
                        pixels[at(width, table, anim, f, x, y)]
                )
                    cost++
            }
            val gap = Math.floorMod(f - g, lows.size)
            if (cost < bestCost || (cost == bestCost && gap < bestGap)) {
                best = g
                bestCost = cost
                bestGap = gap
            }
        }
        return best
    }

    /** The floor more than half of the frames end on, or null if there is none. */
    private fun sharedFloor(lows: IntArray): Int? {
        val counts = lows.filter { it >= 0 }.groupingBy { it }.eachCount()
        val (floor, count) = counts.maxByOrNull { it.value } ?: return null
        return if (count * 2 > lows.size) floor else null
    }

    /** The lowest row of frame [f] with a drawn pixel, or -1 for an empty frame. */
    private fun lowestRow(pixels: IntArray, width: Int, table: AnimTable, anim: Anim, f: Int): Int {
        for (y in table.frameHeight - 1 downTo 0) {
            for (x in 0 until table.frameWidth) {
                if (pixels[at(width, table, anim, f, x, y)] != CLEAR) return y
            }
        }
        return -1
    }

    /** The sheet index of pixel ([x], [y]) of frame [f] of [anim] (as [AnimTable.frameRect]). */
    private fun at(width: Int, table: AnimTable, anim: Anim, f: Int, x: Int, y: Int): Int =
        (anim.row * table.frameHeight + y) * width + f * table.frameWidth + x

    /** The palette index of a clear pixel. */
    private const val CLEAR = 0
}
