package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * What the page's art tables and recoloured pixels were recorded as
 * (`tools/gen_pet_art_fixtures.js`).
 */
object PetArtFixtures {
    val coats = listOf("ginger", "gray", "black", "tuxedo")

    /** The kitten stage whose adult-art sheet is recorded for each coat. */
    val sampledStage = mapOf("ginger" to 0, "gray" to 2, "black" to 4, "tuxedo" to 5)

    val web: JSONObject by lazy { JSONObject(String(bytes("web-tables.json"), Charsets.UTF_8)) }

    /** The recorded RGBA pixels of fixture [name] (without .png), as ARGB. */
    fun image(name: String): ArgbImage {
        val png = PngImage.decode(bytes("$name.png"))
        check(png.channels == 4) { "$name is not RGBA" }
        val d = png.data
        val pixels =
            IntArray(png.width * png.height) {
                val i = it * 4
                ((d[i + 3].toInt() and 255) shl 24) or
                    ((d[i].toInt() and 255) shl 16) or
                    ((d[i + 1].toInt() and 255) shl 8) or
                    (d[i + 2].toInt() and 255)
            }
        return ArgbImage(png.width, png.height, pixels)
    }

    private fun bytes(name: String): ByteArray =
        PetArtFixtures::class.java.getResourceAsStream("/pet-art/$name")!!.use { it.readBytes() }
}

/** A message naming the first differing pixel, or null if [actual] equals [expected] exactly. */
fun firstDifference(label: String, expected: ArgbImage, actual: ArgbImage): String? {
    if (expected.width != actual.width || expected.height != actual.height) {
        return "$label: size ${actual.width}x${actual.height}, expected ${expected.width}x${expected.height}"
    }
    var bad = 0
    var first = -1
    for (i in expected.pixels.indices) {
        if (expected.pixels[i] != actual.pixels[i]) {
            if (first < 0) first = i
            bad++
        }
    }
    if (bad == 0) return null
    val x = first % expected.width
    val y = first / expected.width
    return "$label: $bad of ${expected.pixels.size} pixels differ; first at ($x,$y): " +
        "expected %08x, got %08x".format(expected.pixels[first], actual.pixels[first])
}

/** A [CoatSource] that answers from maps, and counts what it was asked, for tests. */
class FakeCoatSource(
    private val sheets: Map<Pair<String, SheetKind>, ByteArray> = emptyMap(),
    private val palettes: Map<String, CatPalette> = emptyMap(),
) : CoatSource {
    val sheetAsks = mutableListOf<Pair<String, SheetKind>>()

    override fun sheet(coat: String, kind: SheetKind): ByteArray? {
        sheetAsks += coat to kind
        return sheets[coat to kind]
    }

    override fun palette(coat: String): CatPalette? = palettes[coat]
}
