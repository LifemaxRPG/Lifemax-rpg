package com.lifemax.rpg.app.shell.bar

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the page says about scrubbing in a bar scene, read as native reads it. */
class BarScrubSceneTest {
    private fun hit(id: String, x: Double) =
        JSONObject().put("id", id).put("x", x).put("y", 6.0).put("w", 64.0).put("h", 52.0)

    private val hits =
        JSONArray()
            .put(hit("o1", 6.0))
            .put(hit("o2", 72.0))
            .put(JSONObject().put("id", "").put("x", 0).put("w", 10).put("label", "text"))
            .put(hit("o3", 138.0))

    private fun scrub() =
        JSONObject()
            .put("v", 1)
            .put(
                "tabs",
                JSONArray()
                    .put(JSONObject().put("key", "dashboard").put("id", "o1"))
                    .put(JSONObject().put("key", "character").put("id", "o2"))
                    .put(JSONObject().put("key", "quests").put("id", "o3")),
            )
            .put("active", "character")
            .put("speed", "faster")
            .put("haptics", false)
            .put("pill", JSONObject().put("color", 0x337C9CFFL).put("radius", 12))
            .put("surface", 0xFF0E1117L)

    @Test
    fun theTabsComeInThePagesOrderWithTheirPlaces() {
        val scene = BarScrubScene.parse(scrub(), hits)!!
        assertEquals(listOf("dashboard", "character", "quests"), scene.tabs.map { it.key })
        assertEquals(listOf("o1", "o2", "o3"), scene.tabs.map { it.id })
        assertEquals(72f, scene.tabs[1].left, 0f)
        assertEquals(136f, scene.tabs[1].right, 0f)
        assertEquals(6f, scene.tabs[1].top, 0f)
        assertEquals(58f, scene.tabs[1].bottom, 0f)
    }

    @Test
    fun theSettingsAreRead() {
        val scene = BarScrubScene.parse(scrub(), hits)!!
        assertEquals(1, scene.activeIndex)
        assertEquals(ScrubSpeed.Faster, scene.speed)
        assertFalse(scene.haptics)
        assertEquals(0x337C9CFF, scene.pillColor)
        assertEquals(12f, scene.pillRadius, 0f)
        assertEquals(0xFF0E1117.toInt(), scene.surface)
        assertTrue(scene.scrubbable)
    }

    @Test
    fun aTabWithoutAButtonInTheSceneIsLeftOut() {
        val s = scrub()
        s.getJSONArray("tabs").put(JSONObject().put("key", "ghost").put("id", "o99"))
        assertEquals(3, BarScrubScene.parse(s, hits)!!.tabs.size)
    }

    @Test
    fun aPageOnAViewWithoutATabHasNoActiveIndex() {
        val s = scrub().put("active", "settings")
        assertEquals(-1, BarScrubScene.parse(s, hits)!!.activeIndex)
    }

    @Test
    fun aSceneWithoutScrubLeavesTheBarAPlainScroller() {
        assertNull(BarScrubScene.parse(null, hits))
        assertNull(BarScrubScene.parse(scrub(), null))
        assertNull(BarScrubScene.parse(JSONObject().put("v", 1), hits))
    }

    @Test
    fun aBarOfOneTabIsNotScrubbable() {
        val s =
            scrub()
                .put("tabs", JSONArray().put(JSONObject().put("key", "dashboard").put("id", "o1")))
        assertFalse(BarScrubScene.parse(s, hits)!!.scrubbable)
    }

    @Test
    fun speedAndHapticsDefaultTheWayThePageDoes() {
        val s =
            scrub().apply {
                remove("speed")
                remove("haptics")
            }
        val scene = BarScrubScene.parse(s, hits)!!
        assertEquals(ScrubSpeed.Fast, scene.speed)
        assertTrue(scene.haptics)
    }
}
