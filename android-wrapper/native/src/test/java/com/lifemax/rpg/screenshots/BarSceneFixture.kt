package com.lifemax.rpg.screenshots

import org.json.JSONArray
import org.json.JSONObject

/**
 * A bottom-bar scene as the page makes it (50-native-overview.js: the capture of `.sidebar`, with
 * the page's own sizes at 412 px wide: a 64 px bar, a 1 px hairline on top, tabs 53 px tall with a
 * 20 px icon over a 10 px label, the selected tab's rounded pill), for the bar's pictures. The
 * icons are outlined shapes standing in for the page's drawn ones; every other number is the
 * page's. Colours are the page's dark and light tokens.
 */
internal class BarSceneFixture(
    val names: List<String>,
    val active: Int,
    val light: Boolean = false,
    /** the label size in px: 10, or 13 where the phone's text size is raised to 130 % */
    val font: Float = 10f,
) {
    val bg = if (light) 0xFFEEF1F6 else 0xFF0C0E12
    val surface = if (light) 0xFFF6F8FB else 0xFF0E1117
    private val hairline = if (light) 0xFFCFD6E3 else 0xFF242A35
    private val muted = if (light) 0xFF5C6B82 else 0xFF8B93A3
    private val accent = if (light) 0xFF2F4BB8 else 0xFF9BB4FF
    private val pill = if (light) 0x183B5BDBL else 0x227C9CFFL

    val height = 64f
    private val tabTop = 7f
    private val tabHeight = 53f

    /** Each tab's width: its label plus the padding, at least 60 and at most 88 (the page's). */
    private fun widthOf(name: String) = (name.length * font * .56f + 8f).coerceIn(60f, 88f)

    val tabs: List<Triple<Float, Float, String>> =
        names
            .runningFold(6f) { x, name -> x + widthOf(name) + 2f }
            .zip(names)
            .map { (x, name) -> Triple(x, widthOf(name), name) }

    /** the strip's width: the tabs, the gaps and the 6 px padding either side */
    val width = tabs.last().let { it.first + it.second + 6f }

    private fun box(
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        color: Long,
        radius: Float = 0f,
        border: Long = 0,
        borderWidth: Float = 0f,
        topOnly: Boolean = false,
    ): JSONObject {
        val edge = { width: Float -> JSONObject().put("width", width).put("color", border) }
        val sides =
            if (topOnly) listOf(edge(borderWidth), edge(0f), edge(0f), edge(0f))
            else List(4) { edge(borderWidth) }
        return JSONObject()
            .put("kind", "box")
            .put("x", x)
            .put("y", y)
            .put("w", w)
            .put("h", h)
            .put("color", color)
            .put("border", border)
            .put("borderWidth", borderWidth)
            .put("borders", JSONArray(sides))
            .put("radius", radius)
            .put("opacity", 1)
            .put("gradients", JSONArray())
            .put("shadows", JSONArray())
    }

    private fun label(x: Float, y: Float, w: Float, text: String, color: Long, weight: Int) =
        JSONObject()
            .put("kind", "text")
            .put("x", x)
            .put("y", y)
            .put("w", w)
            .put("h", font * 1.2f)
            .put("text", text)
            .put("color", color)
            .put("font", font)
            .put("weight", weight)
            .put("spacing", 0)
            .put("mono", false)
            .put("italic", false)
            .put("opacity", 1)

    /** The bar's layer, as `capture([sidebar], true)` returns it. */
    fun layer(): JSONObject {
        val nodes = JSONArray()
        // the bar itself: its colour and the one hairline on its top edge
        nodes.put(
            box(0f, 0f, width, height, surface, border = hairline, borderWidth = 1f, topOnly = true)
        )
        val hits = JSONArray()
        tabs.forEachIndexed { i, (x, w, name) ->
            val on = i == active
            if (on) nodes.put(box(x, tabTop, w, tabHeight, pill, radius = 12f))
            val ink = if (on) accent else muted
            // the icon: a 16 px outline, centred in its 20 px box
            val shape = if (i % 3 == 1) 8f else 4f
            nodes.put(box(x + w / 2 - 8f, tabTop + 8f, 16f, 16f, 0, shape, ink, 1.6f))
            val textWidth = name.length * font * .56f
            val labelTop = tabTop + 6f + 20f + 3f
            nodes.put(
                label(x + (w - textWidth) / 2, labelTop, textWidth, name, ink, if (on) 650 else 550)
            )
            hits.put(
                JSONObject()
                    .put("id", "o${i + 1}")
                    .put("x", x)
                    .put("y", tabTop)
                    .put("w", w)
                    .put("h", tabHeight)
                    .put("label", name)
                    .put("button", true)
            )
        }
        return JSONObject()
            .put("width", width)
            .put("height", height)
            .put("nodes", nodes)
            .put("hits", hits)
    }

    /** The scene's `scrub`, as the page sends it. */
    fun scrub(speed: String = "fast"): JSONObject =
        JSONObject()
            .put("v", 1)
            .put(
                "tabs",
                JSONArray(
                    names.mapIndexed { i, name ->
                        JSONObject()
                            .put("key", name.lowercase().replace(' ', '-'))
                            .put("id", "o${i + 1}")
                    }
                ),
            )
            .put("active", names[active].lowercase().replace(' ', '-'))
            .put("speed", speed)
            .put("haptics", true)
            .put("pill", JSONObject().put("color", pill).put("radius", 12))
            .put("surface", surface)

    companion object {
        /** the bar of an owner with the default tabs: Quests at the very end */
        val DEFAULT =
            listOf(
                "Overview",
                "Character",
                "Food",
                "Cat",
                "Log activity",
                "Workout plan",
                "Library",
                "Money",
                "Life Director",
                "Quests",
            )

        /** and with every extra: more tabs than the strip's width can ever show */
        val MANY = DEFAULT.dropLast(1) + listOf("Journal", "Recipes", "Media", "Settings", "Quests")
    }
}
