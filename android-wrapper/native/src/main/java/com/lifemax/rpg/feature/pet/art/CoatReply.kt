package com.lifemax.rpg.feature.pet.art

import java.util.Base64
import org.json.JSONObject

/**
 * What the page answers to `coat {key, kind}` (overview cards, `overview.cat.coat.v1`): one sheet
 * of a genetic coat, as the grey-index PNG the room draws it from, and the coat's palette. Decoded
 * strictly: a reply with an unknown field, the wrong coat or kind, a PNG that is not an 8-bit grey
 * sheet of a sane size, or a palette with anything but index 1..255 and six hex digits is a
 * refusal, never a guess. Nothing of the pixels is ever put in a message.
 */
class CoatReply private constructor(val png: ByteArray, val palette: CatPalette) {

    companion object {
        /** The largest base64 text taken for a sheet (the web's own limit, 600,000 characters). */
        const val MAX_BASE64 = 600_000

        /** The most pixels a sheet may have (the adult sheet is 576 x 1344 = 774,144). */
        const val MAX_PIXELS = 1_500_000L

        /** The most entries a palette may have (a coat uses about 21). */
        const val MAX_PALETTE = 64

        private val OK_KEYS = setOf("command", "requestId", "ok", "coat", "kind", "png", "palette")
        private val NO_KEYS = setOf("command", "requestId", "ok", "message")
        private val HEX = Regex("[0-9a-f]{6}")
        private val INDEX = Regex("[1-9][0-9]{0,2}")
        private const val SIGNATURE = "\u0089PNG\r\n\u001a\n"

        /** True if [reply] is the page's answer to a `coat` command (whatever it says). */
        fun isCoat(reply: JSONObject): Boolean = reply.optString("command") == "coat"

        /** True if [reply] says the page refused (a well-formed refusal). */
        fun refused(reply: JSONObject): Boolean =
            reply.opt("ok") == false &&
                reply.keys().asSequence().all { it in NO_KEYS } &&
                (reply.opt("message") as? String)?.length in 1..MAX_MESSAGE

        /**
         * The sheet [reply] carries for [coat] and [kind], or throws [IllegalArgumentException]
         * (any other failure to read it is thrown as one too).
         */
        fun decode(reply: JSONObject, coat: String, kind: SheetKind): CoatReply =
            try {
                read(reply, coat, kind)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("malformed coat reply")
            }

        private fun read(reply: JSONObject, coat: String, kind: SheetKind): CoatReply {
            val extra = reply.keys().asSequence().filter { it !in OK_KEYS }.toList()
            require(extra.isEmpty()) { "unknown coat reply fields $extra" }
            require(reply.opt("ok") == true) { "the page refused the sheet" }
            require(reply.get("coat") == coat) { "a reply for another coat" }
            require(reply.get("kind") == kind.key) { "a reply for another sheet" }
            val text = reply.get("png")
            require(text is String && text.length in 1..MAX_BASE64) { "the sheet text is not sane" }
            val png = Base64.getDecoder().decode(text)
            header(png)
            return CoatReply(png, palette(reply.getJSONObject("palette")))
        }

        /** The PNG's own header: 8-bit grey, not interlaced, a sane size. */
        private fun header(png: ByteArray) {
            require(png.size > 33 && String(png, 0, 8, Charsets.ISO_8859_1) == SIGNATURE) {
                "the sheet is not a PNG"
            }
            require(String(png, 12, 4, Charsets.ISO_8859_1) == "IHDR") { "the PNG has no header" }
            val w = int(png, 16)
            val h = int(png, 20)
            require(w in 1..MAX_SIDE && h in 1..MAX_SIDE && w.toLong() * h <= MAX_PIXELS) {
                "the sheet is ${w}x$h"
            }
            require(png[24].toInt() == 8 && png[25].toInt() == 0 && png[28].toInt() == 0) {
                "the sheet is not an 8-bit grey PNG"
            }
        }

        private fun palette(p: JSONObject): CatPalette {
            require(p.length() in 1..MAX_PALETTE) { "a palette has 1 to $MAX_PALETTE colours" }
            val colours = HashMap<Int, Int>()
            for (key in p.keys()) {
                require(INDEX.matches(key)) { "palette index" }
                val index = key.toInt()
                require(index in 1..255) { "palette index out of range" }
                val hex = p.get(key)
                require(hex is String && HEX.matches(hex)) { "palette colour" }
                colours[index] = hex.toInt(16)
            }
            return CatPalette.of(colours)
        }

        private fun int(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and 255) shl 24) or
                ((b[at + 1].toInt() and 255) shl 16) or
                ((b[at + 2].toInt() and 255) shl 8) or
                (b[at + 3].toInt() and 255)

        private const val MAX_SIDE = 4096
        private const val MAX_MESSAGE = 300
    }
}
