package com.lifemax.rpg.feature.pet.art

import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The strict decoder of the page's answer to `coat`: anything off is refused, not guessed. */
class CoatReplyTest {
    private val key = CoatFixtures.coats.getValue("calico")

    private fun good() = CoatFixtures.reply("calico", "depth")

    private fun refuses(label: String, reply: JSONObject, kind: SheetKind = SheetKind.Depth) {
        try {
            CoatReply.decode(reply, key, kind)
            fail("$label was taken")
        } catch (e: IllegalArgumentException) {
            // refused: the message never carries the pixels
            assertTrue(label, (e.message ?: "").length < 120)
        }
    }

    /** The reply's PNG with a byte of the header changed. */
    private fun withPng(edit: (ByteArray) -> Unit): JSONObject {
        val png = Base64.getDecoder().decode(good().getString("png"))
        edit(png)
        return good().put("png", Base64.getEncoder().encodeToString(png))
    }

    @Test
    fun theRecordedReplyIsTaken() {
        val r = CoatReply.decode(good(), key, SheetKind.Depth)
        val sheet = SheetDecoder.decode(r.png, CoatReply.MAX_PIXELS)
        assertEquals(432, sheet.width)
        assertEquals(192, sheet.height)
        assertTrue(r.palette.indices.size in 15..30)
        assertEquals(0xFF1A101C.toInt(), r.palette.argb(10)) // the fixed outline
    }

    @Test
    fun aRefusalIsRecognisedOnlyWhenWellFormed() {
        val no = { m: Any? ->
            JSONObject()
                .put("command", "coat")
                .put("requestId", "r1")
                .put("ok", false)
                .put("message", m)
        }
        assertTrue(CoatReply.refused(no("That sheet is not one the room has.")))
        assertFalse(CoatReply.refused(no("")))
        assertFalse(CoatReply.refused(no("x".repeat(400))))
        assertFalse(CoatReply.refused(no("fine").put("png", "AAAA")))
        assertFalse(CoatReply.refused(good()))
        assertTrue(CoatReply.isCoat(good()))
        assertFalse(CoatReply.isCoat(JSONObject().put("command", "tip")))
    }

    @Test
    fun theEnvelopeIsExact() {
        refuses("unknown field", good().put("debug", true))
        refuses("a refusal", good().put("ok", false))
        refuses("no ok", good().also { it.remove("ok") })
        refuses("ok as text", good().put("ok", "true"))
        refuses("other coat", good().put("coat", "dna_red_0_n_0_0_n_0_3"))
        refuses("coat as number", good().put("coat", 3))
        refuses("other kind", good().put("kind", "adult"))
        refuses("asked for another kind", good(), SheetKind.Adult)
        refuses("no png", good().also { it.remove("png") })
        refuses("no palette", good().also { it.remove("palette") })
        refuses("png as object", good().put("png", JSONObject()))
    }

    @Test
    fun theSheetMustBeAnEightBitGreyPngOfASaneSize() {
        refuses("empty", good().put("png", ""))
        refuses("not base64", good().put("png", "***"))
        refuses("too long", good().put("png", "A".repeat(CoatReply.MAX_BASE64 + 4)))
        refuses("not a png", good().put("png", Base64.getEncoder().encodeToString(ByteArray(60))))
        refuses("too short", good().put("png", Base64.getEncoder().encodeToString(ByteArray(10))))
        refuses("rgba", withPng { it[25] = 6 })
        refuses("16 bit", withPng { it[24] = 16 })
        refuses("interlaced", withPng { it[28] = 1 })
        refuses("zero wide", withPng { for (i in 16..19) it[i] = 0 })
        refuses("huge", withPng { it[16] = 0x7f })
        refuses(
            "a million by a million",
            withPng {
                it[17] = 0x0f
                it[21] = 0x0f
            },
        )
    }

    @Test
    fun aPaletteIsIndexOneToTwoFiftyFiveAndSixHexDigits() {
        fun p(vararg e: Pair<String, Any?>) = good().put("palette", JSONObject(mapOf(*e)))
        refuses("empty", p())
        refuses("index 0", p("0" to "ffffff"))
        refuses("index 256", p("256" to "ffffff"))
        refuses("negative", p("-1" to "ffffff"))
        refuses("not a number", p("a" to "ffffff"))
        refuses("leading zero", p("010" to "ffffff"))
        refuses("four digits", p("1000" to "ffffff"))
        refuses("hash", p("10" to "#ffffff"))
        refuses("upper case", p("10" to "FFFFFF"))
        refuses("short", p("10" to "fff"))
        refuses("number", p("10" to 255))
        refuses("null", p("10" to JSONObject.NULL))
        val many = JSONObject()
        for (i in 1..CoatReply.MAX_PALETTE + 1) many.put("$i", "ffffff")
        refuses("too many", good().put("palette", many))
        // a good small palette is taken, and index 0 stays clear
        val r = CoatReply.decode(p("10" to "1a101c", "20" to "ffffff"), key, SheetKind.Depth)
        assertEquals(0xFFFFFFFF.toInt(), r.palette.argb(20))
        assertEquals(CatPalette.CLEAR, r.palette.argb(0))
        assertEquals(CatPalette.CLEAR, r.palette.argb(30))
    }

    @Test
    fun aSmallFileCannotClaimOrExpandToAHugeImage() {
        // 100 x 100 declared, data for far more: the inflated size is capped by the header's own
        val r = CoatReply.decode(good(), key, SheetKind.Depth)
        try {
            SheetDecoder.decode(r.png, 100L) // the real 432 x 192 sheet against a 100 pixel cap
            fail("an oversize sheet was decoded")
        } catch (e: IllegalArgumentException) {
            // refused before anything was inflated
        }
    }
}
