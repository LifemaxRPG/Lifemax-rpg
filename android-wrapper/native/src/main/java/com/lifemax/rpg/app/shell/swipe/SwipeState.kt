package com.lifemax.rpg.app.shell.swipe

import org.json.JSONObject

/** How the page would draw a tab right now (protocol `kind`). */
enum class TabKind(val wire: String) {
    /** A native screen that would open now. */
    Native("native"),
    /** The WebView draws it. Also anything the page names that this build does not know. */
    Web("web"),
    /** Never crossed: Money, which is another Activity. */
    Stop("stop");

    companion object {
        fun of(wire: String?): TabKind = entries.firstOrNull { it.wire == wire } ?: Web
    }
}

/** The page's haptics: off, or its level (`fxOpt().haptics ? hapLevelKey() : 'off'`). */
enum class HapticLevel(val wire: String) {
    Off("off"),
    Light("light"),
    Normal("normal"),
    Strong("strong"),
    Max("max");

    companion object {
        fun of(wire: String?): HapticLevel? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * One tab of the bottom bar, in the page's order. [key] is the bar's key ('food'), [view] the page
 * view it resolves to now ('meals' or 'recipes'), which is also the pane id of a native screen.
 * [source] is false for a tab that may not start a swipe (Life Director).
 */
data class SwipeTab(
    val key: String,
    val view: String,
    val label: String,
    val icon: String,
    val kind: TabKind,
    val source: Boolean = true,
)

/** The page's colours as ARGB, for the veil. */
data class SwipeColors(val bg: Int, val text: Int, val muted: Int)

/** What lies on one side of the open screen, and so what a drag toward it shows. */
sealed interface Neighbour {
    /** The tab this side leads to; null past either end of the bar. */
    val tab: SwipeTab?

    /** A native screen composed this session: the real screen slides in. */
    data class Pane(override val tab: SwipeTab) : Neighbour

    /**
     * A native tab not composed yet: the veil slides in with its icon and label, a commit is
     * allowed, and the page opens the screen cold.
     */
    data class Veil(override val tab: SwipeTab) : Neighbour

    /**
     * A web or stop tab, or an end of the bar: the screen gives a little over the label-less veil
     * and springs back. Phase 1 never commits into a wall.
     */
    data class Wall(override val tab: SwipeTab?) : Neighbour
}

/** Both sides of the [active] tab. */
data class Neighbours(val active: SwipeTab, val prev: Neighbour, val next: Neighbour) {
    /** dir +1 is the next tab (the finger moves left), −1 the previous one. */
    operator fun get(dir: Int): Neighbour = if (dir > 0) next else prev
}

/**
 * The page's projection for the swipe, from the `swipe` field of every bar scene (`navScene`,
 * Food's `chromeScene`). It rides the scene, so ChromeHost's sequence and stale rule apply: only a
 * scene that was drawn may be passed on. [sequence] is that scene's; a swipe sends it back.
 */
data class SwipeState(
    /** The page allows a swipe to start now (the beta switch, onboarding, no overlay...). */
    val on: Boolean,
    /** The page's motion is on; off, nothing follows the finger and settles are instant. */
    val motion: Boolean,
    val haptics: HapticLevel,
    /** The page's current view: the only pane that may start a swipe. */
    val view: String,
    /** The bar key of [view], or "" when the view is not on the bar. */
    val active: String,
    val tabs: List<SwipeTab>,
    val colors: SwipeColors?,
    val tune: SwipeTune,
    val sequence: Int,
) {
    /**
     * The sides of [pane], or null when this state cannot start a swipe from it: another view is
     * current, the active tab is not on the bar or resolves to another view, or it may not start a
     * swipe. A native neighbour is a [Neighbour.Pane] when its view is in [composed], otherwise a
     * [Neighbour.Veil]; anything else is a [Neighbour.Wall].
     */
    fun neighbours(pane: String, composed: Set<String>): Neighbours? {
        if (view != pane) return null
        val i = tabs.indexOfFirst { it.key == active }
        val here = tabs.getOrNull(i) ?: return null
        if (here.view != pane || !here.source) return null
        fun side(j: Int): Neighbour {
            val tab = tabs.getOrNull(j) ?: return Neighbour.Wall(null)
            return when {
                tab.kind != TabKind.Native -> Neighbour.Wall(tab)
                tab.view in composed -> Neighbour.Pane(tab)
                else -> Neighbour.Veil(tab)
            }
        }
        return Neighbours(here, side(i - 1), side(i + 1))
    }

    companion object {
        /**
         * The scene's `swipe` field, or null when it is missing, of another version, or has a tab
         * list that cannot be trusted (missing, empty, or any entry without a key or view: a
         * dropped entry would shift every neighbour). Unknown kinds count as web; a missing `on` is
         * off.
         */
        fun parse(swipe: JSONObject?, sequence: Int): SwipeState? {
            if (swipe == null || swipe.optInt("v") != 1) return null
            val list = swipe.optJSONArray("tabs") ?: return null
            val tabs = List(list.length()) { i -> list.optJSONObject(i)?.toTab() ?: return null }
            if (tabs.isEmpty()) return null
            return SwipeState(
                on = swipe.optBoolean("on", false),
                motion = swipe.optBoolean("motion", true),
                haptics =
                    when (val h = swipe.opt("haptics")) {
                        is String -> HapticLevel.of(h) ?: HapticLevel.Normal
                        is Boolean -> if (h) HapticLevel.Normal else HapticLevel.Off
                        else -> HapticLevel.Off
                    },
                view = swipe.text("view"),
                active = swipe.text("active"),
                tabs = tabs,
                colors = swipe.optJSONObject("colors")?.toColors(),
                tune = SwipeTune.parse(swipe.optJSONObject("tune")),
                sequence = sequence,
            )
        }

        private fun JSONObject.toTab(): SwipeTab? {
            val key =
                text("key").ifEmpty {
                    return null
                }
            val view =
                text("view").ifEmpty {
                    return null
                }
            return SwipeTab(
                key = key,
                view = view,
                label = text("label"),
                icon = text("icon"),
                kind = TabKind.of(text("kind")),
                source = optBoolean("source", true),
            )
        }

        private fun JSONObject.toColors(): SwipeColors? {
            fun argb(key: String): Int? = (opt(key) as? Number)?.toLong()?.toInt()
            return SwipeColors(
                argb("bg") ?: return null,
                argb("text") ?: return null,
                argb("muted") ?: return null,
            )
        }

        /** A string value, or "" (Android's optString would turn a JSON null into "null"). */
        private fun JSONObject.text(key: String): String = opt(key) as? String ?: ""
    }
}
