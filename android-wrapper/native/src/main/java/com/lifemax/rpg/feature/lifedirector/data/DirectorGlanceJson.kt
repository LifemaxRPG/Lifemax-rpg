package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.DirectorGlance
import com.lifemax.rpg.feature.lifedirector.domain.GlanceFoot
import com.lifemax.rpg.feature.lifedirector.domain.GlanceState
import org.json.JSONObject

/**
 * Life Director's glance as the page sends it (LifeDirector.native.glance() in logic.js; the
 * Overview cards' `tiles.plan`, plan §3.3), decoded strictly: a missing field, a field of the wrong
 * kind, or a state whose parts do not match it throws, so the Overview's decoder rejects the whole
 * projection and keeps its last good state.
 *
 * The entry point for the Overview: its decoder hands the `tiles.plan` object (when not null) to
 * [decode]. The dial and its middle are Today's, decoded by Today's own decoders, so the glance
 * draws exactly what Today would.
 */
object DirectorGlanceJson {
    fun decode(g: JSONObject): DirectorGlance {
        val state =
            when (val s = g.string("state")) {
                "setup" -> GlanceState.SETUP
                "none" -> GlanceState.NONE
                "day" -> GlanceState.DAY
                else -> throw IllegalArgumentException("Unknown glance state $s")
            }
        val day = state == GlanceState.DAY
        val intro = g.stringOrNull("intro")
        val button = g.stringOrNull("button")
        require((intro != null) != day) { "Only an empty glance has words instead of a dial" }
        require((button != null) == (state == GlanceState.SETUP)) { "Only set-up has a button" }
        for (part in listOf("dial", "core", "foot")) require(g.has(part) && g.isNull(part) != day) {
            "A glance's $part goes with its day"
        }
        if (!day)
            return DirectorGlance(
                state,
                g.string("title"),
                g.string("link"),
                intro,
                button,
                null,
                null,
                null,
                null,
            )
        val dial = dial(g.getJSONObject("dial"))
        require(dial.handles.isEmpty() && dial.build == null) {
            "The glance's dial has no handles and plays no build"
        }
        val core = g.getJSONObject("core")
        val foot = g.getJSONObject("foot")
        return DirectorGlance(
            state = state,
            title = g.string("title"),
            link = g.string("link"),
            intro = null,
            button = null,
            dial = dial,
            centre = centre(core),
            fullTitle = core.string("fullTitle").also { require(it.isNotBlank()) },
            foot = GlanceFoot(foot.string("text"), foot.string("done")),
        )
    }

    /** [decode] for the page's JSON text, as a fixture or a test holds it. */
    fun decode(text: String): DirectorGlance = decode(JSONObject(text))
}

private fun JSONObject.string(key: String): String =
    get(key).let { it as? String ?: throw IllegalArgumentException("$key is not text") }

/** Text or null; the key itself must be there. */
private fun JSONObject.stringOrNull(key: String): String? {
    require(has(key)) { "$key is missing" }
    return if (isNull(key)) null else string(key)
}
