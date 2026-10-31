package com.lifemax.rpg.app.shell.swipe

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SwipeTuneTest {
    private val default = SwipeTune.DEFAULT

    /** The numbers ADR 0011 records (the feel, in numbers). */
    @Test
    fun theDefaultsAreTheDecidedNumbers() {
        val decided =
            mapOf(
                "knee" to 0.18,
                "breakAt" to 0.45,
                "heavy" to 0.25,
                "hyst" to 0.06,
                "popDamping" to 0.65,
                "popStiffness" to 1200.0,
                "wallMax" to 0.08,
                "wallSlope" to 0.35,
                "flingDp" to 1200.0,
                "flingMin" to 0.12,
                "backFlingDp" to 600.0,
                "commitK" to 1000.0,
                "cancelK" to 800.0,
                "slopFactor" to 1.0,
                "lockRatio" to 1.3,
                "edgeDp" to 24.0,
            )
        val json = default.toJson()
        assertEquals(decided.keys + "haptic", json.keys().asSequence().toSet())
        // exactly, so a fixture of the page's LMA_TABSWIPE can be compared as it is
        for ((key, value) in decided) assertEquals(key, value, json.getDouble(key), 0.0)
        assertEquals("threshold", json.getString("haptic"))
        assertEquals(4000f, SwipeTune.MAX_STIFFNESS, 0f)
        assertEquals(0.45f, SwipeTune.SETTLE_CAP_SECONDS, 0f)
        assertEquals(default, default.clamped())
    }

    @Test
    fun theDefaultsAreThePagesOwn() {
        // the page's LMA_TABSWIPE (50-native-swipe.js), written by tools/gen_native_fixtures.js
        val page = JSONObject(javaClass.getResource("/fixtures/swipe-tune.json")!!.readText())
        val json = default.toJson()
        assertEquals(json.keys().asSequence().toSet(), page.keys().asSequence().toSet())
        for ((key, _) in SwipeTune.numbers) {
            assertEquals(key, json.getDouble(key), page.getDouble(key), 0.0)
        }
        assertEquals(json.getString("haptic"), page.getString("haptic"))
        assertEquals(default, SwipeTune.parse(page))
    }

    @Test
    fun everyKeyIsClampedToItsRange() {
        val ranges =
            mapOf(
                "knee" to (0.05f to 0.35f),
                "heavy" to (0.05f to 1f),
                "hyst" to (0f to 0.15f),
                "popDamping" to (0.4f to 1f),
                "popStiffness" to (400f to 3000f),
                "wallMax" to (0f to 0.15f),
                "wallSlope" to (0f to 0.6f),
                "flingDp" to (500f to 3000f),
                "flingMin" to (0.05f to 0.35f),
                "backFlingDp" to (200f to 2000f),
                "commitK" to (300f to 4000f),
                "cancelK" to (300f to 4000f),
                "slopFactor" to (0.75f to 2f),
                "lockRatio" to (1f to 3f),
                "edgeDp" to (16f to 48f),
            )
        val read = SwipeTune.numbers.toMap()
        for ((key, range) in ranges) {
            val low = SwipeTune.parse(JSONObject().put(key, -1e6))
            val high = SwipeTune.parse(JSONObject().put(key, 1e6))
            assertEquals("$key low", range.first, read.getValue(key)(low), 1e-6f)
            assertEquals("$key high", range.second, read.getValue(key)(high), 1e-6f)
        }
    }

    @Test
    fun theCatchIsClampedAfterTheKnee() {
        val late = SwipeTune.parse(JSONObject().put("knee", 0.35).put("breakAt", 0.3))
        assertEquals(0.45f, late.breakAt, 1e-6f)
        val early = SwipeTune.parse(JSONObject().put("knee", 0.01).put("breakAt", 0.1))
        assertEquals(0.05f, early.knee, 1e-6f)
        assertEquals(0.15f, early.breakAt, 1e-6f)
        assertEquals(0.7f, SwipeTune.parse(JSONObject().put("breakAt", 0.9)).breakAt, 1e-6f)
    }

    @Test
    fun anythingThatIsNotAFiniteNumberKeepsTheDefault() {
        val tune =
            SwipeTune.parse(
                JSONObject()
                    .put("knee", "0.3")
                    .put("heavy", JSONObject.NULL)
                    .put("flingDp", true)
                    .put("lockRatio", JSONObject())
            )
        assertEquals(default, tune)
        assertEquals(default.knee, SwipeTune(knee = Float.NaN).clamped().knee, 0f)
        assertEquals(
            default.edgeDp,
            SwipeTune(edgeDp = Float.POSITIVE_INFINITY).clamped().edgeDp,
            0f,
        )
    }

    @Test
    fun theHapticKindIsOneOfFour() {
        assertEquals(HapticStyle.Click, SwipeTune.parse(JSONObject().put("haptic", "click")).haptic)
        assertEquals(HapticStyle.Tick, SwipeTune.parse(JSONObject().put("haptic", "tick")).haptic)
        assertEquals(HapticStyle.Off, SwipeTune.parse(JSONObject().put("haptic", "off")).haptic)
        assertEquals(
            HapticStyle.Threshold,
            SwipeTune.parse(JSONObject().put("haptic", "boom")).haptic,
        )
        assertEquals(HapticStyle.Threshold, SwipeTune.parse(JSONObject().put("haptic", 3)).haptic)
    }

    @Test
    fun anOverrideChangesOnlyItsKey() {
        val tune = SwipeTune.parse(JSONObject().put("heavy", 0.4).put("flingDp", 1500))
        assertEquals(default.copy(heavy = 0.4f, flingDp = 1500f), tune)
    }

    @Test
    fun noOverridesIsTheDefault() {
        assertSame(default, SwipeTune.parse(null))
        assertEquals(default, SwipeTune.parse(JSONObject()))
        assertEquals(default, SwipeTune.parse(default.toJson()))
    }
}
