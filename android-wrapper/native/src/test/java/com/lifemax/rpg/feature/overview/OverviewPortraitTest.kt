package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.domain.CAT_PORTRAIT_HEIGHT
import com.lifemax.rpg.feature.overview.domain.CAT_PORTRAIT_WIDTH
import com.lifemax.rpg.feature.overview.domain.PORTRAIT_MAX_URL
import com.lifemax.rpg.feature.overview.domain.PortraitBounds
import com.lifemax.rpg.feature.overview.domain.catPortrait
import com.lifemax.rpg.feature.overview.domain.portraitBounds
import com.lifemax.rpg.feature.overview.domain.portraitPngBytes
import com.lifemax.rpg.feature.overview.domain.portraitScale
import java.nio.ByteBuffer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The cat portrait's model: the strict read of `tiles.cat.portrait` against the projection's
 * images, the PNG check before anything reaches the bitmap decoder, and the crisp pixel scale. The
 * fixture is the page's own portrait of a cat (tests/pet-portrait.test.js, RECORD=1).
 */
class OverviewPortraitTest {
    private val fixture =
        JSONObject(javaClass.getResource("/fixtures/overview-cat-portrait.json")!!.readText())
    private val cat = fixture.getJSONObject("cat")
    private val images = fixture.getJSONObject("images")

    @Test
    fun thePagesPortraitIsReadFromTheImageTable() {
        val portrait = cat.catPortrait(images)!!
        assertEquals(cat.getString("portrait"), portrait.key)
        assertEquals(images.getString(portrait.key), portrait.dataUrl)
    }

    @Test
    fun thePagesPortraitIsA192By128Png() {
        val bytes = portraitPngBytes(cat.catPortrait(images)!!.dataUrl)!!
        // the IHDR chunk: width and height straight after the 8-byte signature and the chunk header
        val header = ByteBuffer.wrap(bytes, 16, 8)
        assertEquals(CAT_PORTRAIT_WIDTH, header.int)
        assertEquals(CAT_PORTRAIT_HEIGHT, header.int)
    }

    @Test
    fun noPortraitYetIsNull() {
        assertNull(JSONObject(cat.toString()).put("portrait", JSONObject.NULL).catPortrait(images))
    }

    @Test(expected = IllegalArgumentException::class)
    fun aMissingPortraitFieldIsMalformed() {
        JSONObject(cat.toString()).apply { remove("portrait") }.catPortrait(images)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aKeyTheImagesDoNotCarryIsMalformed() {
        JSONObject(cat.toString()).put("portrait", "nope").catPortrait(images)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aKeyThatIsNotAStringIsMalformed() {
        JSONObject(cat.toString()).put("portrait", 7).catPortrait(images)
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
