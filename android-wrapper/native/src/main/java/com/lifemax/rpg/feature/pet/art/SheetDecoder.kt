package com.lifemax.rpg.feature.pet.art

/**
 * A sheet of palette indices: every pixel is a grey index 0..255, not yet a colour. [indices] holds
 * `width * height` values, row by row.
 */
class IndexSheet(val width: Int, val height: Int, val indices: IntArray) {
    init {
        require(indices.size == width * height) {
            "an index sheet of ${width}x$height needs ${width * height} pixels"
        }
    }
}

/**
 * Decodes the cat's sheets (8-bit grey PNGs whose pixel value is the palette index) into an
 * [IndexSheet].
 */
object SheetDecoder {

    /** Decodes [png], or throws [IllegalArgumentException] if it is not an 8-bit grey PNG. */
    fun decode(png: ByteArray): IndexSheet {
        val image = PngImage.decode(png)
        require(image.channels == 1) {
            "a cat sheet is an 8-bit grey PNG, not ${image.channels} channels"
        }
        return IndexSheet(
            image.width,
            image.height,
            IntArray(image.data.size) { image.data[it].toInt() and 255 },
        )
    }
}
