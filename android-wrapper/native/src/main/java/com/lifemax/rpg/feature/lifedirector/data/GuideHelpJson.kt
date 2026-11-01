package com.lifemax.rpg.feature.lifedirector.data

import org.json.JSONObject

/*
 * The projection's `help` (phase 4B, the plan's guide.help): the guide the ? button plays, as the page's tourHelp picks it, sent only
 * when this screen draws that guide (Today's, Week's or set-up's). It is apart from `guide` because Goals and Settings have no guide
 * of their own: from there the screen switches to that guide's section and plays it. Absent (a page from before 4B) or null: the ?
 * is handed over. Strict like the rest: anything but a section's name throws, so the projection is refused.
 */
internal fun JSONObject.guideHelp(): String? {
    if (!has("help") || isNull("help")) return null
    val key = get("help") as? String ?: throw IllegalArgumentException("help is not text")
    require(key.isNotEmpty()) { "help names no guide" }
    return key
}
