package com.lifemax.rpg.app.shell.bar

import org.json.JSONArray
import org.json.JSONObject

/**
 * One tab of the bar: the page's [key] for it, the id its tap answers to, and where it sits (dp).
 */
internal class BarTab(
    val key: String,
    val id: String,
    val left: Float,
    val right: Float,
    val top: Float = 0f,
    val bottom: Float = 0f,
)

/**
 * What the page says about scrubbing the bar in each bar scene (`scrub`, contract v1): the tabs in
 * the order the bar shows them (hidden tabs are not there), which one is selected, the owner's
 * scrub speed and haptics switch, and the look of the selected pill so the bar can show it on the
 * tab under the finger before the page has switched ([pillColor], [pillRadius]); [surface] is the
 * bar's own colour, which also fills the strip under it down to the screen's bottom edge. A scene
 * without it (an older page) leaves the bar a plain scroller.
 */
internal class BarScrubScene(
    val tabs: List<BarTab>,
    val active: String,
    val speed: ScrubSpeed,
    val haptics: Boolean,
    val pillColor: Int,
    val pillRadius: Float,
    val surface: Int,
) {
    /** The tab the page is on; -1 when the page is on a view the bar has no tab for. */
    val activeIndex: Int
        get() = tabs.indexOfFirst { it.key == active }

    /** A bar of fewer than two tabs has nothing to scrub through. */
    val scrubbable: Boolean
        get() = tabs.size >= 2

    companion object {
        /**
         * The scene's `scrub` object, with the positions taken from the bar layer's [hits] (each
         * tab's button, by id). Null when the page sent none or it is unusable.
         */
        fun parse(scrub: JSONObject?, hits: JSONArray?): BarScrubScene? {
            if (scrub == null || hits == null) return null
            val boxes = HashMap<String, JSONObject>()
            for (i in 0 until hits.length()) {
                val hit = hits.optJSONObject(i) ?: continue
                val id = hit.optString("id")
                if (id.isNotBlank()) boxes[id] = hit
            }
            val listed = scrub.optJSONArray("tabs") ?: return null
            val tabs = ArrayList<BarTab>()
            for (i in 0 until listed.length()) {
                val tab = listed.optJSONObject(i) ?: continue
                val box = boxes[tab.optString("id")] ?: continue
                val left = box.optDouble("x").toFloat()
                val top = box.optDouble("y").toFloat()
                tabs +=
                    BarTab(
                        tab.optString("key"),
                        tab.optString("id"),
                        left,
                        left + box.optDouble("w").toFloat(),
                        top,
                        top + box.optDouble("h").toFloat(),
                    )
            }
            val pill = scrub.optJSONObject("pill")
            return BarScrubScene(
                tabs,
                scrub.optString("active"),
                ScrubSpeed.of(scrub.optString("speed")),
                scrub.optBoolean("haptics", true),
                pill?.optLong("color")?.toInt() ?: 0,
                pill?.optDouble("radius", 10.0)?.toFloat() ?: 10f,
                scrub.optLong("surface").toInt(),
            )
        }
    }
}
