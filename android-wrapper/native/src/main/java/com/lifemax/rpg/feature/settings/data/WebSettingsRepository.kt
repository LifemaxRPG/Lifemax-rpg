package com.lifemax.rpg.feature.settings.data

import com.lifemax.rpg.core.data.PAGE_TOKENS
import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.data.pageTokens
import com.lifemax.rpg.feature.settings.domain.ButtonStyle
import com.lifemax.rpg.feature.settings.domain.ClassicRow
import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.FieldEdit
import com.lifemax.rpg.feature.settings.domain.FieldInput
import com.lifemax.rpg.feature.settings.domain.PageNotice
import com.lifemax.rpg.feature.settings.domain.SettingsCard
import com.lifemax.rpg.feature.settings.domain.SettingsCategory
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsHead
import com.lifemax.rpg.feature.settings.domain.SettingsOption
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsRepository
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Decodes the page's Settings projection (50-native-settings.js, settings.projection.v1). Malformed
 * data is rejected whole and the last good state kept: a missing or mistyped field (true or false,
 * or a number, sent as text included), a palette without every token, an unknown kind of row or
 * control, a control operated in a way its kind is not, two cards or two controls with one id (the
 * list's keys and the commands' targets), a selected category or a card's category the chips do not
 * have, a profile picture that is not a data URL. What is only the page's state of the moment is
 * drawn as it comes: a select whose options a renderer has not filled yet, a field row whose input
 * is hidden. An update older than the one held is ignored; a projection from another page session
 * is taken only on an explicit open.
 */
class WebSettingsRepository : SettingsRepository {
    private val mutable = MutableStateFlow<SettingsProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.whole("protocol") == 1L) { "Unsupported settings protocol" }
        val session = j.text("webSessionId")
        val sequence = j.whole("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return
        mutable.value = decode(j, session, sequence)
    }

    private fun decode(j: JSONObject, session: String, sequence: Long): SettingsProjection {
        val head = j.getJSONObject("head")
        val categories =
            j.getJSONArray("categories").objects().map {
                SettingsCategory(it.text("id"), it.text("name"))
            }
        val chips = categories.map { it.id }
        require(chips.isNotEmpty() && chips.none(String::isBlank) && chips.distinct() == chips) {
            "Each category chip needs an id of its own"
        }
        val category = j.text("category")
        require(category in chips) { "The selected category is not a chip" }
        val classic = j.getJSONObject("classic")
        val ids = mutableSetOf<String>()
        val cards = j.getJSONArray("cards").objects().map { card(it, chips, ids) }
        require(cards.map { it.id }.distinct().size == cards.size) { "Two cards share an id" }
        requireFormsComplete(cards)
        return SettingsProjection(
            session,
            sequence,
            j.getJSONObject("tokens").let { t ->
                // pageTokens (shared with the other tabs) takes numeric text; here only a number is
                PAGE_TOKENS.forEach { t.whole(it) }
                t.pageTokens()
            },
            j.flag("light"),
            SettingsHead(head.text("eyebrow"), head.text("title"), head.text("blurb")),
            categories,
            category,
            ClassicRow(classic.text("label"), classic.text("hint")),
            cards,
            if (j.isNull("notice")) null
            else
                j.getJSONObject("notice").let {
                    PageNotice(it.whole("id"), it.text("text")).also { n ->
                        require(n.id > 0) { "A notice id starts at 1" }
                    }
                },
        )
    }

    private fun card(c: JSONObject, chips: List<String>, ids: MutableSet<String>): SettingsCard {
        val id = c.text("id")
        require(id.isNotBlank()) { "A card needs an id" }
        val category = c.text("category")
        require(category.isEmpty() || category in chips) { "Card $id is on no chip" }
        val controls = { a: JSONArray -> a.objects().map { control(it, ids) } }
        val rows =
            c.getJSONArray("rows").objects().map { r ->
                when (val type = r.text("type")) {
                    "row" ->
                        SettingsRow.Line(
                            r.text("title"),
                            r.text("hint"),
                            r.flag("danger"),
                            controls(r.getJSONArray("controls")),
                        )
                    "note" ->
                        SettingsRow.Note(
                            r.text("text"),
                            r.flag("warn"),
                            controls(r.getJSONArray("controls")).map {
                                require(it is SettingsControl.Button) { "A note holds buttons" }
                                it
                            },
                        )
                    "text" -> SettingsRow.Text(r.text("text"))
                    "controls" ->
                        SettingsRow.Controls(
                            controls(r.getJSONArray("controls")).also {
                                require(it.isNotEmpty()) { "A row of controls is empty" }
                            }
                        )
                    "field" ->
                        SettingsRow.Field(
                            r.text("label"),
                            controls(r.getJSONArray("controls")).also { fields ->
                                require(
                                    fields.all {
                                        it is SettingsControl.Field || it is SettingsControl.Select
                                    }
                                ) {
                                    "A form field holds fields or a select"
                                }
                            },
                        )
                    "tab" -> tab(r, ids)
                    else -> throw IllegalArgumentException("Unknown kind of row $type")
                }
            }
        return SettingsCard(id, c.text("title"), c.text("intro"), category, rows)
    }

    private fun tab(r: JSONObject, ids: MutableSet<String>): SettingsRow.Tab {
        fun arrow(name: String): SettingsControl.Button? =
            if (r.isNull(name)) null
            else
                control(r.getJSONObject(name), ids).let {
                    require(it is SettingsControl.Button) { "A bar row's arrow is a button" }
                    it
                }
        val view = r.text("view")
        require(view.isNotBlank()) { "A bar row needs its view" }
        val show = control(r.getJSONObject("show"), ids)
        require(show is SettingsControl.Switch) { "A bar row's box is a switch" }
        return SettingsRow.Tab(
            view,
            r.text("label"),
            r.text("icon"),
            r.flag("visible"),
            arrow("earlier"),
            arrow("later"),
            show,
        )
    }

    private fun control(c: JSONObject, ids: MutableSet<String>): SettingsControl {
        val id = c.text("id")
        require(id.isNotBlank() && ids.add(id)) { "Each control needs an id of its own" }
        val enabled = c.flag("enabled")
        val op =
            when (val name = c.text("op")) {
                "toggle" -> ControlOp.Toggle
                "choose" -> ControlOp.Choose
                "press" -> ControlOp.Press
                "reveal" -> ControlOp.Reveal
                "edit" -> ControlOp.Edit
                "save" -> ControlOp.Save
                else -> throw IllegalArgumentException("Unknown op $name")
            }
        fun operated(vararg allowed: ControlOp) =
            require(op in allowed) { "Control $id cannot be operated by $op" }
        return when (val kind = c.text("kind")) {
            "switch" -> {
                operated(ControlOp.Toggle, ControlOp.Reveal)
                SettingsControl.Switch(id, enabled, op, c.text("label"), c.flag("on"))
            }
            "select" -> {
                operated(ControlOp.Choose, ControlOp.Reveal)
                SettingsControl.Select(
                    id,
                    enabled,
                    op,
                    c.text("label"),
                    c.text("value"),
                    options(c),
                )
            }
            "choice" -> {
                operated(ControlOp.Choose, ControlOp.Reveal)
                SettingsControl.Choice(
                    id,
                    enabled,
                    op,
                    c.text("label"),
                    c.text("value"),
                    options(c),
                )
            }
            "button" -> {
                operated(ControlOp.Press, ControlOp.Reveal, ControlOp.Save)
                val group = if (op == ControlOp.Save) c.text("group") else ""
                require(op != ControlOp.Save || group.isNotBlank()) {
                    "A Save button needs its form"
                }
                val style =
                    when (val name = c.text("style")) {
                        "primary" -> ButtonStyle.Primary
                        "secondary" -> ButtonStyle.Secondary
                        "danger" -> ButtonStyle.Danger
                        else -> throw IllegalArgumentException("Unknown button style $name")
                    }
                SettingsControl.Button(
                    id,
                    enabled,
                    op,
                    c.text("label"),
                    c.text("description"),
                    style,
                    c.flag("small"),
                    group,
                )
            }
            "field" -> {
                operated(ControlOp.Reveal, ControlOp.Edit)
                val input =
                    when (val name = c.text("input")) {
                        "text" -> FieldInput.Text
                        "number" -> FieldInput.Number
                        "time" -> FieldInput.Time
                        else -> throw IllegalArgumentException("Unknown field input $name")
                    }
                SettingsControl.Field(
                    id,
                    enabled,
                    op,
                    c.text("label"),
                    c.text("value"),
                    c.text("placeholder"),
                    input,
                    if (op == ControlOp.Edit) fieldEdit(c, id, input) else null,
                )
            }
            "avatar" -> {
                operated(ControlOp.Reveal)
                val image = c.text("image")
                require(image.isEmpty() || image.startsWith("data:image/")) {
                    "A profile picture is a data URL"
                }
                SettingsControl.Avatar(id, enabled, op, c.text("label"), image, c.text("initial"))
            }
            else -> throw IllegalArgumentException("Unknown kind of control $kind")
        }
    }

    /**
     * What the page says about a field the screen edits. A time is set at once (no form); a text or
     * number box is saved with its form, so it must name one. A limit the input does not have is
     * null, never missing.
     */
    private fun fieldEdit(c: JSONObject, id: String, input: FieldInput): FieldEdit {
        val group = c.text("group")
        require((input == FieldInput.Time) == group.isEmpty()) {
            "Field $id: a time is saved at once and a text or number with its form"
        }
        fun limit(name: String): Double? {
            val v = c.get(name)
            if (v == JSONObject.NULL) return null
            require(v is Number) { "$name is not a number or null" }
            return v.toDouble()
        }
        val maxLength = limit("maxLength")
        require(maxLength == null || (maxLength >= 0 && maxLength % 1.0 == 0.0)) {
            "maxLength is not a length"
        }
        return FieldEdit(group, limit("min"), limit("max"), limit("step"), maxLength?.toInt())
    }

    /**
     * Every form a Save button names has fields, and every form's fields have their Save button.
     */
    private fun requireFormsComplete(cards: List<SettingsCard>) {
        val controls =
            cards
                .flatMap { it.rows }
                .flatMap { row ->
                    when (row) {
                        is SettingsRow.Line -> row.controls
                        is SettingsRow.Field -> row.controls
                        is SettingsRow.Controls -> row.controls
                        is SettingsRow.Note -> row.controls
                        else -> emptyList()
                    }
                }
        val saved =
            controls
                .filterIsInstance<SettingsControl.Button>()
                .map { it.group }
                .filter(String::isNotEmpty)
        val held =
            controls
                .filterIsInstance<SettingsControl.Field>()
                .mapNotNull { it.edit?.group }
                .filter(String::isNotEmpty)
        require(saved.toSet() == held.toSet() && saved.distinct() == saved) {
            "Each form has its fields and one Save button"
        }
    }

    /**
     * A select's or a choice's options; a select a renderer fills later (Card art) has none yet.
     */
    private fun options(c: JSONObject): List<SettingsOption> =
        c.getJSONArray("options").objects().map {
            SettingsOption(it.text("value"), it.text("label"))
        }

    /**
     * A true-or-false field. org.json's getBoolean also takes the text "true", which the page never
     * sends: anything but a boolean is malformed.
     */
    private fun JSONObject.flag(name: String): Boolean {
        val value = get(name)
        require(value is Boolean) { "$name is not true or false" }
        return value
    }

    /**
     * A whole number. org.json's getInt and getLong also take numeric text ("1") and cut a fraction
     * off, neither of which the page sends: anything but an integer is malformed.
     */
    private fun JSONObject.whole(name: String): Long {
        val value = get(name)
        require(value is Int || value is Long) { "$name is not a whole number" }
        return (value as Number).toLong()
    }

    /** A string field; a number or anything else where a string belongs is malformed. */
    private fun JSONObject.text(name: String): String {
        val value = get(name)
        require(value is String) { "$name is not text" }
        return value
    }
}
