package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.CAT_PORTRAIT_HEIGHT
import com.lifemax.rpg.feature.overview.domain.CAT_PORTRAIT_WIDTH
import com.lifemax.rpg.feature.overview.domain.PORTRAIT_MAX_URL
import com.lifemax.rpg.feature.overview.domain.PortraitBounds
import com.lifemax.rpg.feature.overview.domain.portraitBounds
import com.lifemax.rpg.feature.overview.domain.portraitPngBytes
import com.lifemax.rpg.feature.overview.domain.portraitScale
import java.nio.ByteBuffer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The cat portrait: `tiles.cat.portrait` read through the projection's images (the page's own
 * projection of a cat, overview-cards-pet.json), the PNG check before anything reaches the bitmap
 * decoder, and the crisp pixel scale. overview-cat-portrait.json is the page's portrait proven
 * against the pet room (tests/pet-portrait.test.js, RECORD=1).
 */
class OverviewPortraitTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/$name.json")!!.readText())

    private val pet = fixture("overview-cards-pet")

    private fun decode(j: JSONObject) =
        WebOverviewRepository().apply { accept(j, true) }.state.value!!

    private fun cat(j: JSONObject) = j.getJSONObject("tiles").getJSONObject("cat")

    @Test
    fun thePagesPortraitIsReadFromTheImageTable() {
        val portrait = decode(pet).catPortrait!!
        assertEquals(cat(pet).getString("portrait"), portrait.key)
        assertEquals(pet.getJSONObject("images").getString(portrait.key), portrait.dataUrl)
    }

    @Test
    fun thePagesPortraitIsA192By128Png() {
        val proven = fixture("overview-cat-portrait")
        val key = proven.getJSONObject("cat").getString("portrait")
        val url = proven.getJSONObject("images").getString(key)
        for (dataUrl in listOf(url, decode(pet).catPortrait!!.dataUrl)) {
            val bytes = portraitPngBytes(dataUrl)!!
            // the IHDR chunk: width and height straight after the signature and the chunk header
            val header = ByteBuffer.wrap(bytes, 16, 8)
            assertEquals(CAT_PORTRAIT_WIDTH, header.int)
            assertEquals(CAT_PORTRAIT_HEIGHT, header.int)
        }
    }

    @Test
    fun noPortraitYetIsNull() {
        val j = JSONObject(pet.toString())
        cat(j).put("portrait", JSONObject.NULL)
        assertNull(decode(j).catPortrait)
    }

    @Test
    fun aMissingWrongOrUnknownPortraitIsMalformed() {
        val breaks: List<(JSONObject) -> Unit> =
            listOf(
                { it.remove("portrait") },
                { it.put("portrait", "i0") },
                { it.put("portrait", 7) },
            )
        for (broken in breaks) {
            val j = JSONObject(pet.toString())
            broken(cat(j))
            assertThrows(Exception::class.java) { decode(j) }
        }
    }

    @Test
    fun onlyABase64PngReachesTheDecoder() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2)
        val b64 = java.util.Base64.getEncoder().encodeToString(png)
        assertArrayEquals(png, portraitPngBytes("data:image/png;base64,$b64"))
        assertNull(portraitPngBytes("data:image/jpeg;base64,$b64"))
        assertNull(portraitPngBytes("https://example.com/cat.png"))
        assertNull(portraitPngBytes("data:image/png;base64,not base64!"))
        val notPng = java.util.Base64.getEncoder().encodeToString("GIF89a....".toByteArray())
        assertNull(portraitPngBytes("data:image/png;base64,$notPng"))
        assertNull(portraitPngBytes("data:image/png;base64," + "A".repeat(PORTRAIT_MAX_URL)))
    }

    @Test
    fun thePixelScaleIsWholeWhenThePortraitFits() {
        // 120 x 88 dp at 2.75 px per dp: 330 x 242 px holds the 192 x 128 portrait once, crisply
        assertEquals(1f, portraitScale(330f, 242f, 192, 128))
        assertEquals(2f, portraitScale(400f, 300f, 192, 128))
        assertEquals(3f, portraitScale(600f, 384f, 192, 128))
        // smaller than the image: the plain fit
        assertEquals(0.5f, portraitScale(96f, 100f, 192, 128))
        assertEquals(0f, portraitScale(0f, 100f, 192, 128))
    }

    @Test
    fun theCatsBoundsSkipOnlyClearPixels() {
        val px = IntArray(5 * 4)
        px[1 * 5 + 1] = 0x01000000 // barely there still counts (the shadow is translucent)
        px[2 * 5 + 3] = 0xFF112233.toInt()
        assertEquals(PortraitBounds(1, 1, 3, 2), portraitBounds(px, 5, 4))
        assertNull(portraitBounds(IntArray(20), 5, 4))
    }
}
