package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.art.LightTint
import com.lifemax.rpg.feature.pet.room.HouseMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** `pet/light/lighting.json`: the shipped data and the strict decoder. */
class LightDataTest {
    private val shipped by lazy {
        String(
            LightDataTest::class.java.getResourceAsStream("/pet/light/lighting.json")!!.readBytes()
        )
    }

    @Test
    fun theShippedDataHasThePlansDefaults() {
        val d = LightData.shipped
        assertEquals(7.5, d.dayFrom, 0.0)
        assertEquals(17.5, d.dayUntil, 0.0)
        assertEquals(2, d.patchDay.halfSteps)
        assertEquals(LightTint.SUN, d.patchDay.tint)
        assertEquals(LightTint.MOON, d.patchNight.tint)
        assertEquals(setOf("sill", "lsill", "ksill"), d.sillSurfaces)
        assertEquals(1.0, d.sun.getValue(Weather.CLEAR), 0.0)
        assertEquals(0.35, d.sun.getValue(Weather.OVERCAST), 0.0)
        assertEquals(0.0, d.sun.getValue(Weather.RAIN), 0.0)
        val lamp = d.points.single()
        assertEquals("living", lamp.room)
        assertTrue(lamp.whenDark)
        // every room of the house has its bias, and they are all 0 for now
        assertEquals(HouseMap.everything.map { it.id }.toSet(), d.rooms.keys)
        assertTrue(d.rooms.values.all { it == 0 })
    }

    @Test
    fun anUnknownFieldIsRefused() {
        refused(shipped.replace("\"nightTint\"", "\"glow\": 1, \"nightTint\""))
        refused(shipped.replace("\"tint\": \"lamp\"", "\"tint\": \"lamp\", \"flicker\": \"none\""))
    }

    @Test
    fun badValuesAreRefused() {
        refused(shipped.replace("\"tint\": \"lamp\"", "\"tint\": \"disco\""))
        refused(shipped.replace("\"overcast\": {\"sun\": 0.35}", "\"overcast\": {\"sun\": 1.5}"))
        refused(shipped.replace("\"box\": -2", "\"box\": -9"))
        refused(shipped.replace("\"when\": \"dark\"", "\"when\": \"sometimes\""))
        refused(shipped.replace("\"version\": 1", "\"version\": 2"))
        refused(shipped.replace("\"rain\": {\"sun\": 0.0}", "\"snow\": {\"sun\": 0.0}"))
    }

    @Test
    fun theSeededWeatherIsTheSameEachDayAndMostlyClear() {
        val days = (0L until 3650L).map { Weather.seeded(20_000L + it) }
        assertEquals(days, (0L until 3650L).map { Weather.seeded(20_000L + it) })
        val clear = days.count { it == Weather.CLEAR }.toDouble() / days.size
        val rain = days.count { it == Weather.RAIN }.toDouble() / days.size
        assertTrue("clear $clear", clear in 0.6..0.8)
        assertTrue("rain $rain", rain in 0.05..0.15)
    }

    private fun refused(text: String) {
        try {
            LightData.parse(text)
            fail("accepted: $text")
        } catch (e: IllegalArgumentException) {
            // refused, as it must be
        } catch (e: org.json.JSONException) {
            // a missing field
        }
    }
}
