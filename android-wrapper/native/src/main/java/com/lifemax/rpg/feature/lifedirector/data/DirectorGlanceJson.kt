package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.DirectorGlance
import com.lifemax.rpg.feature.lifedirector.domain.GlanceFoot
import com.lifemax.rpg.feature.lifedirector.domain.GlanceState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Life Director's glance as the page sends it (LifeDirector.native.glance() in logic.js; the
 * Overview cards' `tiles.plan`, plan §3.3), decoded strictly: a missing field, a field of the wrong
 * kind, or a state whose parts do not match it throws, so the Overview's decoder rejects the whole
 * projection and keeps its last good state.
 *
 * The entry point for the Overview: its decoder hands the `tiles.plan` object to [decode] (the page
 * sends null there without Life Director; the Overview then has no glance to draw). The dial and
 * its middle are Today's, decoded by Today's own decoders, so the glance draws exactly what Today
 * would.
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
        val dial = dial(readOnly(g.getJSONObject("dial")))
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

    /**
     * The glance's dial is Today's without what only Today has (handles, stops, drag words, a
     * build): those keys must be absent, and the copy Today's decoder reads gets them empty.
     */
    private fun readOnly(d: JSONObject): JSONObject {
        for (key in TODAY_ONLY) require(!d.has(key)) { "The glance's dial has no $key" }
        return JSONObject(d.toString())
            .put("handles", JSONArray())
            .put("stops", JSONArray())
            .put("dragWords", JSONObject().put("tooShort", "").put("release", ""))
            .put("build", JSONObject.NULL)
    }

    private val TODAY_ONLY = listOf("handles", "stops", "dragWords", "build")
}

private fun JSONObject.string(key: String): String =
    get(key).let { it as? String ?: throw IllegalArgumentException("$key is not text") }

/** Text or null; the key itself must be there. */
private fun JSONObject.stringOrNull(key: String): String? {
    require(has(key)) { "$key is missing" }
    return if (isNull(key)) null else string(key)
}
