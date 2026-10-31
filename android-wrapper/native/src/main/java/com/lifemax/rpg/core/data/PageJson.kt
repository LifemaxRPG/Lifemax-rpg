package com.lifemax.rpg.core.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Decoding what every tab's projection shares, so each tab applies the same rules.
 */

/** The palette tokens NativeTabKit sends with every projection (TOKENS in 50-native-tabkit.js). */
val PAGE_TOKENS =
    listOf(
        "bg",
        "bg-elev",
        "panel",
        "panel2",
        "panel3",
        "text",
        "text-dim",
        "muted",
        "muted2",
        "accent",
        "accent-dim",
        "accent-strong",
        "line",
        "line-soft",
        "good",
        "gold",
        "bad",
    )

/**
 * The page's palette from a projection's tokens object, as ARGB: every key NativeTabKit sends. A
 * missing key or a value that is not a number is malformed (throws), so a screen is never drawn in
 * the fallback grey of a token the page stopped sending.
 */
fun JSONObject.pageTokens(): Map<String, Long> = PAGE_TOKENS.associateWith { getLong(it) }

/** The objects of an array; an element that is not an object is malformed (throws). */
fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
