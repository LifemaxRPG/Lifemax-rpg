package com.lifemax.rpg.app.shell.swipe

import org.json.JSONArray
import org.json.JSONObject

/** Bars and states as the page projects them (protocol v1). */
object SwipeFixtures {
    data class Entry(
        val key: String,
        val view: String,
        val label: String,
        val kind: String,
        val source: Boolean = true,
    )

    /** The forged default bar with every native switch on (Life Director last, after Money). */
    val defaultBar =
        listOf(
            Entry("dashboard", "dashboard", "Overview", "web"),
            Entry("character", "character", "Character", "native"),
            Entry("food", "meals", "Food", "native"),
            Entry("pet", "pet", "Cat", "web"),
            Entry("log", "log", "Log", "native"),
            Entry("quests", "quests", "Quests", "web"),
            Entry("plan", "plan", "Workout plan", "native"),
            Entry("library", "media", "Library", "native"),
            Entry("money", "money", "Money", "stop"),
            Entry("life-director", "life-director", "Life Director", "native", source = false),
        )

    /** The owner's optional reorder: five native tabs side by side. */
    val nativeRow =
        listOf("character", "food", "log", "plan", "library").map { key ->
            defaultBar.first { it.key == key }
        } + defaultBar.filter { it.key !in setOf("character", "food", "log", "plan", "library") }

    fun swipeJson(
        view: String,
        bar: List<Entry> = defaultBar,
        on: Boolean = true,
        motion: Boolean = true,
        haptics: Any = "normal",
        tune: JSONObject? = null,
    ): JSONObject =
        JSONObject()
            .put("v", 1)
            .put("on", on)
            .put("motion", motion)
            .put("haptics", haptics)
            .put("view", view)
            .put("active", bar.firstOrNull { it.view == view }?.key ?: "")
            .put(
                "tabs",
                JSONArray(
                    bar.map {
                        JSONObject()
                            .put("key", it.key)
                            .put("view", it.view)
                            .put("label", it.label)
                            .put("icon", it.key)
                            .put("kind", it.kind)
                            .apply { if (!it.source) put("source", false) }
                    }
                ),
            )
            .put(
                "colors",
                JSONObject()
                    .put("bg", 0xFF101820L)
                    .put("text", 0xFFF2F2F2L)
                    .put("muted", 0xFF8A93A0L),
            )
            .put("tune", tune ?: JSONObject.NULL)

    fun state(
        view: String,
        bar: List<Entry> = defaultBar,
        on: Boolean = true,
        motion: Boolean = true,
        haptics: Any = "normal",
        sequence: Int = 1,
        tune: JSONObject? = null,
    ): SwipeState = SwipeState.parse(swipeJson(view, bar, on, motion, haptics, tune), sequence)!!
}
