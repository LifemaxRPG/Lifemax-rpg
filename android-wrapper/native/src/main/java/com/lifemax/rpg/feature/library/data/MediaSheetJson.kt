package com.lifemax.rpg.feature.library.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.library.domain.FilterOption
import com.lifemax.rpg.feature.library.domain.MediaAsk
import com.lifemax.rpg.feature.library.domain.MediaChoiceField
import com.lifemax.rpg.feature.library.domain.MediaFields
import com.lifemax.rpg.feature.library.domain.MediaForm
import com.lifemax.rpg.feature.library.domain.MediaReply
import com.lifemax.rpg.feature.library.domain.MediaTextField
import org.json.JSONObject

/**
 * The Media sheets' part of the projection (50-native-library.js form()). A page that predates the
 * sheets sends no edit at all, and its form is the page's own (null). Otherwise edit is "native"
 * with the form, or "page" with form null; anything else is malformed, as is a form with a key
 * missing or of the wrong type.
 */
fun JSONObject.mediaForm(): MediaForm? {
    if (!has("edit")) return null
    return when (val edit = getString("edit")) {
        "page" -> {
            require(has("form") && isNull("form")) { "A page-edited pane carries no form" }
            null
        }
        "native" -> form(getJSONObject("form"))
        else -> throw IllegalArgumentException("Unknown edit $edit")
    }
}

private fun form(f: JSONObject): MediaForm {
    val heading = f.getJSONObject("heading")
    val fields = f.getJSONObject("fields")
    val defaults = f.getJSONObject("defaults")
    val confirms = f.getJSONObject("confirms")
    val linksJson = f.getJSONObject("links")
    val links = linksJson.keys().asSequence().associateWith { linksJson.getString(it) }
    val type = choice(fields.getJSONObject("type"))
    val status = choice(fields.getJSONObject("status"))
    val form =
        MediaForm(
            heading.getString("add"),
            heading.getString("edit"),
            f.getString("save"),
            f.getString("cancel"),
            text(fields.getJSONObject("title")),
            type,
            text(fields.getJSONObject("creator")),
            status,
            text(fields.getJSONObject("link")),
            text(fields.getJSONObject("notes")),
            defaults.getString("type"),
            defaults.getString("status"),
            links,
            ask(confirms.getJSONObject("remove")),
            ask(confirms.getJSONObject("removeAudio")),
            confirms.getString("cancel"),
        )
    require(type.options.any { it.id == form.defaultType }) { "The default type is no option" }
    require(status.options.any { it.id == form.defaultStatus }) {
        "The default status is no option"
    }
    require(form.title.required != null) { "The title has no words for empty" }
    return form
}

private fun text(t: JSONObject): MediaTextField {
    val required =
        if (!t.has("required")) null
        else t.getString("required").also { require(it.isNotEmpty()) { "Empty required words" } }
    val max = t.getInt("maxLength")
    require(max > 0) { "A box without its length" }
    return MediaTextField(t.getString("label"), t.getString("placeholder"), max, required)
}

private fun choice(c: JSONObject): MediaChoiceField {
    val options =
        c.getJSONArray("options").objects().map {
            FilterOption(it.getString("id"), it.getString("label"))
        }
    require(options.isNotEmpty()) { "A select without options" }
    return MediaChoiceField(c.getString("label"), options)
}

private fun ask(a: JSONObject): MediaAsk {
    val ask = MediaAsk(a.getString("text"), a.getString("confirm"))
    require("{title}" in ask.text) { "A confirm that does not name the item" }
    return ask
}

/** The Media commands the screen sends; a reply to any other is not the sheets'. */
val MEDIA_COMMANDS = setOf("upsert", "setStatus", "remove", "removeAudio")

/**
 * The page's reply to a Media command, or null for another command's. A reply is decoded strictly:
 * one without its ok, changed or message is malformed.
 */
fun JSONObject.toMediaReply(): MediaReply? {
    val command = optString("command")
    if (command !in MEDIA_COMMANDS) return null
    return MediaReply(
        command,
        getString("requestId"),
        getBoolean("ok"),
        getBoolean("changed"),
        getString("message"),
        if (command == "upsert") getString("id") else "",
        if (command == "upsert") getString("field") else "",
    )
}

/** An upsert's payload: an add carries no id. */
fun upsertPayload(op: String, id: String?, fields: MediaFields): JSONObject =
    JSONObject()
        .put("op", op)
        .apply { if (id != null) put("id", id) }
        .put(
            "fields",
            JSONObject()
                .put("title", fields.title)
                .put("type", fields.type)
                .put("creator", fields.creator)
                .put("status", fields.status)
                .put("link", fields.link)
                .put("notes", fields.notes),
        )

fun statusPayload(id: String, status: String): JSONObject =
    JSONObject().put("id", id).put("status", status)

fun idPayload(id: String): JSONObject = JSONObject().put("id", id)
