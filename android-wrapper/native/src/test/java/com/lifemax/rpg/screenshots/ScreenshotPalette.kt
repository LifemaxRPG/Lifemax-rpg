package com.lifemax.rpg.screenshots

import org.json.JSONObject

/**
 * The web app's two palettes, as the page projects them (ARGB), so screenshots look like the phone.
 */
object ScreenshotPalette {
    private val dark =
        mapOf(
            "bg" to 0xFF0C0E12L,
            "bg-elev" to 0xFF11141AL,
            "panel" to 0xFF161A22L,
            "panel2" to 0xFF1C212BL,
            "panel3" to 0xFF232933L,
            "text" to 0xFFEEF1F6L,
            "text-dim" to 0xFFC4CAD6L,
            "muted" to 0xFF8B93A3L,
            "muted2" to 0xFF6B7384L,
            "accent" to 0xFF7C9CFFL,
            "accent-dim" to 0x22809EFFL,
            "accent-strong" to 0xFF9BB4FFL,
            "line" to 0xFF2C3340L,
            "line-soft" to 0xFF242A35L,
            "good" to 0xFF3DD68CL,
            "gold" to 0xFFE8B84AL,
            "bad" to 0xFFF07178L,
        )
    private val light =
        mapOf(
            "bg" to 0xFFEEF1F6L,
            "bg-elev" to 0xFFFFFFFFL,
            "panel" to 0xFFFFFFFFL,
            "panel2" to 0xFFF4F6FAL,
            "panel3" to 0xFFE9EDF5L,
            "text" to 0xFF121A2BL,
            "text-dim" to 0xFF3A455CL,
            "muted" to 0xFF5C6B82L,
            "muted2" to 0xFF7A879CL,
            "accent" to 0xFF3B5BDBL,
            "accent-dim" to 0x184060DFL,
            "accent-strong" to 0xFF2F4BB8L,
            "line" to 0xFFCFD6E3L,
            "line-soft" to 0xFFE3E8F0L,
            "good" to 0xFF0F9F6EL,
            "gold" to 0xFFB8860BL,
            "bad" to 0xFFDC2626L,
        )

    fun tokens(light: Boolean): JSONObject = JSONObject(tokenMap(light))

    fun tokenMap(light: Boolean): Map<String, Long> = if (light) this.light else dark
}
