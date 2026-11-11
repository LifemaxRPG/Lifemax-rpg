package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowRedactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** SH-13, SH-31, SH-32: paths in the page's normal form, redacted, last bits told apart. */
class JsonPathsTest {
    private val paths = JsonPaths(ShadowRedactor(ByteArray(ShadowLimits.SALT_BYTES) { 2 }))

    @Test
    fun thePagesNormalFormIsNotADifference() {
        assertEquals(
            emptyList<PathDifference>(),
            paths.diff(
                JsonArray(listOf(JsonNull, JsonNumber(0.0))),
                JsonArray(listOf(JsonNumber(Double.NaN), JsonNumber(-0.0))),
                "x",
            ),
        )
        assertEquals(
            emptyList<PathDifference>(),
            paths.diff(JsonNull, JsonNumber(Double.POSITIVE_INFINITY), "x"),
        )
    }

    @Test
    fun aListOfAnotherLengthIsNamed() {
        val d =
            paths.diff(
                JsonArray(listOf(JsonNumber(1.0))),
                JsonArray(listOf(JsonNumber(1.0), JsonNumber(2.0))),
                "x",
            )
        assertEquals(listOf(PathDifference("x[#]", false)), d)
    }

    @Test
    fun onlyALastBitIsALastBit() {
        assertEquals(
            listOf(PathDifference("x", true)),
            paths.diff(JsonNumber(1.5), JsonNumber(Math.nextUp(1.5)), "x"),
        )
        assertEquals(
            listOf(PathDifference("x", false)),
            paths.diff(JsonNumber(1.5), JsonNumber(1.5 + 1e-9), "x"),
        )
    }

    @Test
    fun aNameThatIsNotASchemaWordIsHashed() {
        val d =
            paths.diff(
                JsonObject(mapOf("g-muscle" to JsonString("a"), "startEpoch" to JsonNumber(1.0))),
                JsonObject(mapOf("g-muscle" to JsonString("b"), "startEpoch" to JsonNumber(2.0))),
                "x",
            )
        assertEquals("x.startEpoch", d[1].path)
        assertTrue(d[0].path, d[0].path.startsWith("x.*#") && !d[0].path.contains("muscle"))
    }
}
