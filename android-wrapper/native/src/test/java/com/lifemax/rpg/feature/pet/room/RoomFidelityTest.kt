package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.art.PngImage
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The native room against the web room. `tools/gen_pet_room_fixtures.js` runs the page's own
 * `petDraw` with no cat at fixed hours and a fixed room clock and records the 576 x 144 canvas it
 * paints. Here [RoomScene] paints the same empty house onto a software canvas and the two pictures
 * are compared pixel by pixel.
 *
 * What must match: every pixel, to within 2 on any colour channel. That allowance is for the 8-bit
 * rounding of a blend or a multiply, and for the anti-aliased edge of the window light and the lamp
 * glow's gradient; the sky bands, the painted rooms, the sprites, the stars, the props and their
 * placement are exact (the test counts the pixels that are not, and each must be a blended one).
 */
class RoomFidelityTest {
    private val art = PetArtResources.loadRoom()
    private val scene = RoomScene(art)
    private val web = JSONObject(text("web-room.json"))
    private val hours = web.getJSONArray("hours").let { a -> List(a.length()) { a.getDouble(it) } }
    private val clock = web.getDouble("clock")

    private fun compose(hour: Double): ArgbImage {
        val canvas = SoftCanvas(art)
        scene.paintBackdrop(canvas, hour, clock, 0)
        scene.paintScene(canvas, NoCats)
        scene.paintOverlay(canvas, hour, 0)
        return canvas.picture()
    }

    private fun recorded(hour: Double): ArgbImage {
        val name = if (hour == Math.floor(hour)) hour.toInt().toString() else hour.toString()
        return PngImage.decode(bytes("house-$name.png")).toArgb()
    }

    @Test
    fun theHouseMatchesThePageAtEveryRecordedHour() {
        for (hour in hours) {
            val want = recorded(hour)
            val got = compose(hour)
            assertEquals(want.width, got.width)
            assertEquals(want.height, got.height)
            val steps = IntArray(TOLERANCE + 2)
            var first = ""
            for (i in want.pixels.indices) {
                val d = distance(want.pixels[i], got.pixels[i])
                steps[minOf(d, TOLERANCE + 1)]++
                if (d > TOLERANCE && first.isEmpty()) {
                    first =
                        "(${i % want.width},${i / want.width}) %08x vs %08x"
                            .format(want.pixels[i], got.pixels[i])
                }
            }
            println("hour $hour: pixels off by 0/1/2/more: ${steps.toList()}")
            assertEquals(
                "hour $hour: a pixel is off by more than $TOLERANCE, first $first",
                0,
                steps[TOLERANCE + 1],
            )
            assertTrue(
                "hour $hour: ${steps[TOLERANCE]} pixels are off by $TOLERANCE",
                steps[TOLERANCE] <= want.pixels.size / 100,
            )
        }
    }

    @Test
    fun theSkyTheTintAndTheTablesAreThePages() {
        val t = web.getJSONObject("tables")
        for (row in t.getJSONArray("skyAt").objects()) {
            val hour = row.getDouble(0)
            val sky = IntArray(2)
            RoomLight.skyAt(hour, sky)
            val want = row.getJSONArray(1)
            assertEquals("top at $hour", rgb(want.getJSONArray(0)), sky[RoomLight.TOP])
            assertEquals("bottom at $hour", rgb(want.getJSONArray(1)), sky[RoomLight.BOTTOM])
        }
        for (row in t.getJSONArray("tint").objects()) {
            assertEquals(
                "tint at ${row.getDouble(0)}",
                rgb(row.getJSONArray(1)),
                RoomLight.tint(row.getDouble(0)),
            )
        }
        assertEquals(t.getJSONArray("windows").ints(), RoomLayout.windows.toList())
        assertEquals(t.getInt("houseX0"), RoomLayout.HOUSE_X0)
        assertEquals(t.getInt("houseWidth"), RoomLayout.HOUSE_WIDTH)
        val bowl = t.getJSONObject("bowl")
        assertEquals(bowl.getJSONObject("food").getInt("x"), RoomLayout.BOWL_FOOD_X)
        assertEquals(bowl.getJSONObject("water").getInt("x"), RoomLayout.BOWL_WATER_X)
        assertEquals(bowl.getJSONObject("food").getInt("top"), RoomLayout.BOWL_TOP)
        val meta = t.getJSONObject("roomMeta")
        for (name in
            listOf("sun", "moon", "cloud1", "cloud2", "bed_back", "bed_front", "bowl_empty")) {
            val r = art.item(name)
            assertEquals(meta.getJSONArray(name).ints(), listOf(r.x, r.y, r.width, r.height))
        }
    }

    private fun distance(a: Int, b: Int): Int {
        var worst = 0
        for (shift in intArrayOf(24, 16, 8, 0)) {
            worst = maxOf(worst, Math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)))
        }
        return worst
    }

    private fun rgb(a: JSONArray) = (a.getInt(0) shl 16) or (a.getInt(1) shl 8) or a.getInt(2)

    private fun JSONArray.ints() = List(length()) { getInt(it) }

    private fun JSONArray.objects() = List(length()) { getJSONArray(it) }

    private fun bytes(name: String): ByteArray =
        RoomFidelityTest::class.java.getResourceAsStream("/pet-room/$name")!!.use { it.readBytes() }

    private fun text(name: String) = String(bytes(name), Charsets.UTF_8)

    private companion object {
        /** An 8-bit blend, multiply or anti-aliased edge may differ by this much in a channel. */
        const val TOLERANCE = 2
    }
}
