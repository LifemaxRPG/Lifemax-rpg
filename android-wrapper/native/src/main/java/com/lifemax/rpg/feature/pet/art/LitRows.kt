package com.lifemax.rpg.feature.pet.art

/**
 * One cat's lit sheet rows (docs/specs/CAT_LIGHTING_PLAN.md section 5.2): a whole clip row of
 * frames, recoloured for one light key, made the first time it is asked for and kept in a small
 * least-recently-used store. A row of an adult sheet is 576 x 48 pixels (about 110 KB), so a cat in
 * a few lights costs a few hundred KB, not a 4.4 MB sheet per light. [CatLightKey.NEUTRAL] is never
 * made here: the app draws the sheet it has always drawn.
 *
 * The sheets are the index sheets as the cat draws them (after the mends), so a lit row is the
 * drawn row with other colours looked up, frame for frame: a resting cat's identical frames stay
 * identical under any light. Not thread safe: the screen draws from one thread.
 *
 * @param side the side view's index sheet as drawn
 * @param depth the depth view's index sheet, or null while it has not arrived
 * @param frameHeight the height of a row of frames
 */
class LitRows(
    private val side: IndexSheet,
    private val depth: IndexSheet?,
    private val palette: CatPalette,
    val frameHeight: Int,
    capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::nanoTime,
) {
    private val look = CoatLook.of(palette)

    /**
     * The width of every row picture: the wider sheet's (a narrower sheet's row is padded clear).
     */
    val width: Int = maxOf(side.width, depth?.width ?: 0)

    private val cache = RigCache(capacity, width * frameHeight)
    private val tables = arrayOfNulls<IntArray>(CatLightKey.COUNT * 2)

    /** How many rows were made since the start, and the time the latest took, for the readout. */
    var bakes = 0L
        private set

    var lastBakeNanos = 0L
        private set

    /** True if the latest [slotFor] made its row (so a bitmap of the slot is out of date). */
    var wasBaked = false
        private set

    /**
     * The slot of the row of frames whose top is sheet row [top] of the depth sheet ([onDepth]) or
     * the side sheet, lit by [key] (not [CatLightKey.NEUTRAL]); -1 if that sheet is not here.
     */
    fun slotFor(onDepth: Boolean, top: Int, key: Int): Int {
        wasBaked = false
        val sheet = (if (onDepth) depth else side) ?: return -1
        require(key in 1 until CatLightKey.COUNT) { "key $key" }
        if (top < 0 || top + frameHeight > sheet.height) return -1
        val cacheKey = (key.toLong() * ROW_SPAN + top) * 2 + (if (onDepth) 1 else 0)
        val hit = cache.find(cacheKey)
        if (hit >= 0) return hit
        val started = clock()
        val slot = cache.claim(cacheKey)
        val out = cache.pixels(slot)
        if (sheet.width < width) out.fill(CatPalette.CLEAR)
        val lo = table(key, 0)
        val hi = table(key, 1)
        LitRecolour.into(sheet, FrameRect(0, top, sheet.width, frameHeight), lo, hi, out, width)
        bakes++
        lastBakeNanos = clock() - started
        wasBaked = true
        return slot
    }

    /** The pixels of a slot [slotFor] returned: [width] x [frameHeight], row by row. */
    fun pixels(slot: Int): IntArray = cache.pixels(slot)

    /** How many rows are kept now. */
    val resident: Int
        get() = cache.size

    /** Bytes the kept rows take as ARGB. */
    val residentBytes: Long
        get() = resident.toLong() * width * frameHeight * BYTES_PER_PIXEL

    private fun table(key: Int, which: Int): IntArray {
        val i = key * 2 + which
        tables[i]?.let {
            return it
        }
        val light = CatLightKey.light(key, look)
        val made =
            if (which == 1 && light.half != 0) LitRecolour.table(palette, light, look, light.half)
            else if (which == 1) table(key, 0) else LitRecolour.table(palette, light, look)
        tables[i] = made
        return made
    }

    companion object {
        /** Rows kept per cat: a cat walking in and out of two lights uses a handful. */
        const val DEFAULT_CAPACITY = 12

        private const val ROW_SPAN = 1L shl 16
        private const val BYTES_PER_PIXEL = 4
    }
}
