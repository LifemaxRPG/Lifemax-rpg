package com.lifemax.rpg.feature.pet.art

/**
 * Pixels as 0xAARRGGBB, straight alpha (a clear pixel is 0), `width * height` of them, row by row.
 */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(pixels.size == width * height) {
            "an image of ${width}x$height needs ${width * height} pixels"
        }
    }

    /** The pixel at ([x], [y]). */
    operator fun get(x: Int, y: Int): Int = pixels[y * width + x]
}

/**
 * Turns palette indices into colours exactly as the web's `petInit` does: index 0 is clear, an
 * index the coat has a colour for becomes that colour at full opacity, any other index is clear.
 * Colours are not blended or smoothed.
 */
object Recolour {

    /** The whole [sheet] in [palette]. */
    fun sheet(sheet: IndexSheet, palette: CatPalette): ArgbImage =
        region(sheet, palette, FrameRect(0, 0, sheet.width, sheet.height))

    /**
     * The part of [sheet] inside [rect] in [palette], e.g. one frame from [AnimTable.frameRect].
     * Throws [IllegalArgumentException] if [rect] reaches outside the sheet.
     */
    fun region(sheet: IndexSheet, palette: CatPalette, rect: FrameRect): ArgbImage {
        require(
            rect.x >= 0 &&
                rect.y >= 0 &&
                rect.x + rect.width <= sheet.width &&
                rect.y + rect.height <= sheet.height
        ) {
            "$rect is outside the ${sheet.width}x${sheet.height} sheet"
        }
        val lookup = IntArray(256) { palette.argb(it) }
        val out = IntArray(rect.width * rect.height)
        for (y in 0 until rect.height) {
            val from = (rect.y + y) * sheet.width + rect.x
            for (x in 0 until rect.width) {
                val index = sheet.indices[from + x]
                out[y * rect.width + x] = if (index in 0..255) lookup[index] else CatPalette.CLEAR
            }
        }
        return ArgbImage(rect.width, rect.height, out)
    }
}
