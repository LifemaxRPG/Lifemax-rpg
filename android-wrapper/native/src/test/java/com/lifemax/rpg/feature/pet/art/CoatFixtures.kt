package com.lifemax.rpg.feature.pet.art

import org.json.JSONObject

/**
 * What the page answered to `coat {key, kind}` for four genetic coats, and the canvases the room
 * draws from the same sheets (`tools/gen_pet_coat_fixtures.js`): calico (a tortoiseshell with white
 * spotting), point (a colour-point), longwhite (a long-haired white-spotted red tabby) and silver
 * (a classic silver tabby), for the kinds `adult`, `depth` and `st1`.
 */
object CoatFixtures {
    val kinds =
        mapOf("adult" to SheetKind.Adult, "depth" to SheetKind.Depth, "st1" to SheetKind.Stage(1))

    /** Label to coat key. */
    val coats: Map<String, String> by lazy {
        val j = JSONObject(text("coats.json"))
        j.keys().asSequence().associateWith { j.getString(it) }
    }

    /** The page's reply for [label] and [kind] (a key of [kinds]), under request [id]. */
    fun reply(label: String, kind: String, id: String = "req-1"): JSONObject =
        JSONObject(text("$label-$kind.reply.json")).put("requestId", id)

    /** The room's own recoloured canvas for [label] and [kind], as ARGB. */
    fun room(label: String, kind: String): ArgbImage {
        val png = PngImage.decode(bytes("$label-$kind.png"))
        check(png.channels == 4)
        val d = png.data
        return ArgbImage(
            png.width,
            png.height,
            IntArray(png.width * png.height) {
                val i = it * 4
                ((d[i + 3].toInt() and 255) shl 24) or
                    ((d[i].toInt() and 255) shl 16) or
                    ((d[i + 1].toInt() and 255) shl 8) or
                    (d[i + 2].toInt() and 255)
            },
        )
    }

    private fun bytes(name: String): ByteArray =
        CoatFixtures::class.java.getResourceAsStream("/pet-coat/$name")!!.use { it.readBytes() }

    private fun text(name: String) = String(bytes(name), Charsets.UTF_8)
}

/**
 * A bridge that only records what was asked, so a test answers when it chooses (the page is async).
 */
class FakeCoatBridge {
    class Sent(val id: String, val coat: String, val kind: SheetKind)

    val sent = mutableListOf<Sent>()
    private var n = 0

    val requester =
        PageCoatSource.Requester { coat, kind -> "r${++n}".also { sent += Sent(it, coat, kind) } }

    fun last() = sent.last()
}
