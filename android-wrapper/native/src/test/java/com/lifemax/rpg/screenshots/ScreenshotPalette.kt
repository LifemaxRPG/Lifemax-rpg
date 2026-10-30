package com.lifemax.rpg.screenshots

import org.json.JSONObject

/**
 * The web app's two palettes, as the page projects them (opaque ARGB), so screenshots look like the
 * phone.
 */
object ScreenshotPalette {
    private fun argb(rgb: Long) = 0xFF000000L or rgb

    private val dark =
        mapOf(
            "bg" to 0x0C0E12L,
            "panel" to 0x161A22L,
            "panel2" to 0x1C212BL,
            "line" to 0x2C3340L,
            "text" to 0xEEF1F6L,
            "muted" to 0x8B93A3L,
            "accent" to 0x7C9CFFL,
            "good" to 0x3DD68CL,
        )
    private val light =
        mapOf(
            "bg" to 0xEEF1F6L,
            "panel" to 0xFFFFFFL,
            "panel2" to 0xF4F6FAL,
            "line" to 0xCFD6E3L,
            "text" to 0x121A2BL,
            "muted" to 0x5C6B82L,
            "accent" to 0x3B5BDBL,
            "good" to 0x0F9F6EL,
        )

    fun tokens(light: Boolean): JSONObject =
        JSONObject().also { json ->
            (if (light) this.light else dark).forEach { (name, rgb) -> json.put(name, argb(rgb)) }
        }

    fun tokenMap(light: Boolean): Map<String, Long> =
        (if (light) this.light else dark).mapValues { argb(it.value) }
}
