package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * The room's item sprites (bed, bowls, balls, mouse, feather, hearts, zzz, exclaim, note, bubble,
 * sparkle, sun, moon, clouds) packed into one RGBA atlas, with the web's meta `name ->
 * [x, y, w, h]` (`petAssets.room.meta`).
 *
 * Unlike the cat sheets these are real colours, not palette indices.
 */
class RoomAtlas(val image: ArgbImage, private val meta: Map<String, FrameRect>) {

    /** Every sprite name, alphabetically. */
    val names: List<String>
        get() = meta.keys.sorted()

    /** Where [name] sits in the atlas, or null. */
    fun rect(name: String): FrameRect? = meta[name]

    /** A copy of the pixels of [name], or null if the atlas has no such sprite. */
    fun sprite(name: String): ArgbImage? {
        val r = meta[name] ?: return null
        val out = IntArray(r.width * r.height)
        for (y in 0 until r.height) System.arraycopy(
            image.pixels,
            (r.y + y) * image.width + r.x,
            out,
            y * r.width,
            r.width,
        )
        return ArgbImage(r.width, r.height, out)
    }

    companion object {
        /** Builds the atlas from the RGBA PNG [png] and the text of `room.json`. */
        fun parse(png: ByteArray, metaJson: String): RoomAtlas {
            val decoded = PngImage.decode(png)
            require(decoded.channels == 4) { "the room atlas is an RGBA PNG" }
            val d = decoded.data
            val pixels =
                IntArray(decoded.width * decoded.height) {
                    val i = it * 4
                    ((d[i + 3].toInt() and 255) shl 24) or
                        ((d[i].toInt() and 255) shl 16) or
                        ((d[i + 1].toInt() and 255) shl 8) or
                        (d[i + 2].toInt() and 255)
                }
            val json = JSONObject(metaJson)
            val meta =
                json.keys().asSequence().associateWith {
                    val a = json.getJSONArray(it)
                    FrameRect(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3))
                }
            for ((name, r) in meta) {
                require(r.x + r.width <= decoded.width && r.y + r.height <= decoded.height) {
                    "$name lies outside the atlas"
                }
            }
            return RoomAtlas(ArgbImage(decoded.width, decoded.height, pixels), meta)
        }
    }
}
