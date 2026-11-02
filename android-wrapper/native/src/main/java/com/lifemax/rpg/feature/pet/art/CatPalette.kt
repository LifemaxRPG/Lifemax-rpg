package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * The colours of one coat: a grey index (the pixel value in a sheet, 0..255) to an opaque colour.
 *
 * Index 0 is always clear, and so is any index the coat gives no colour to (the web draws both
 * clear). The web's indices are multiples of ten from 10 to 210: 10 outline, 20-50 coat bands,
 * 60-80 stripes, 90-110 white, 120-140 pink, 150 pupil, 160-170 iris, 180-210 details (see
 * `petInit`).
 */
class CatPalette private constructor(private val argb: IntArray) {

    /**
     * The opaque ARGB colour of [index], or 0 (clear) for index 0, an index with no colour, or one
     * outside 0..255.
     */
    fun argb(index: Int): Int = if (index in argb.indices) argb[index] else CLEAR

    /** The indices that have a colour, in ascending order. */
    val indices: List<Int>
        get() = argb.indices.filter { argb[it] != CLEAR }

    companion object {
        /** The ARGB value of a clear pixel. */
        const val CLEAR = 0

        /** A palette from index to 0xRRGGBB. An entry for index 0 is ignored: index 0 is clear. */
        fun of(rgbByIndex: Map<Int, Int>): CatPalette {
            val argb = IntArray(256)
            for ((index, rgb) in rgbByIndex) {
                require(index in 0..255) { "palette index $index is not 0..255" }
                if (index != 0) argb[index] = (0xFF shl 24) or (rgb and 0xFFFFFF)
            }
            return CatPalette(argb)
        }

        /**
         * Reads `palettes.json` ({"coat": {"10": "1a101c", ...}}): the palette of every coat in the
         * file.
         */
        fun parseAll(text: String): Map<String, CatPalette> {
            val json = JSONObject(text)
            return json.keys().asSequence().associateWith { coat ->
                parse(json.getJSONObject(coat))
            }
        }

        /** One coat's {"index": "rrggbb"} object. */
        fun parse(coat: JSONObject): CatPalette =
            of(coat.keys().asSequence().associate { it.toInt() to coat.getString(it).toInt(16) })
    }
}
