package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.DemandTables
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** An option of a choice or days field: its value (text, or a day's index) and label. */
data class FieldOption(val value: JsonValue, val label: String)

/**
 * One field of a form (`formField`, RM-31): what every kind has, and what its kind adds: `time` the
 * page's text for its value; `text` a length limit and placeholder; `number` limits, step and
 * placeholder; `check` the fields it reveals and hides; `choice` and `days` their options. [value]
 * is as the page holds it (a stored value, null: undefined and so left out).
 */
data class FormField(
    val key: String,
    val kind: String,
    val label: String,
    val value: JsonValue?,
    val required: Boolean = false,
    val group: Int? = null,
    val timeText: String? = null,
    val maxLength: Int? = null,
    val placeholder: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val step: Double = 1.0,
    val options: List<FieldOption>? = null,
    val reveals: List<String> = emptyList(),
    val hides: List<String> = emptyList(),
) {
    fun json(): JsonValue {
        val base =
            mutableListOf<Pair<String, JsonValue?>>(
                "key" to s(key),
                "kind" to s(kind),
                "label" to s(label),
                "value" to value,
                "required" to Js.b(required),
                "group" to (group?.let(::n) ?: JsonNull),
            )
        when (kind) {
            "time" -> base += "text" to s(timeText!!)
            "text" -> {
                base += "maxLength" to (maxLength?.let(::n) ?: JsonNull)
                base += "placeholder" to s(placeholder)
            }
            "number" -> {
                base += "min" to (min?.let(::n) ?: JsonNull)
                base += "max" to (max?.let(::n) ?: JsonNull)
                base += "step" to n(step)
                base += "placeholder" to s(placeholder)
            }
            "check" -> {
                base += "reveals" to Js.list(reveals.map(::s))
                base += "hides" to Js.list(hides.map(::s))
            }
        }
        options?.let { o ->
            base += "options" to Js.list(o.map { obj("value" to it.value, "label" to s(it.label)) })
        }
        return obj(*base.toTypedArray())
    }
}

/** A button of a sheet: its label and the command it sends. */
data class SheetCommand(val label: String, val command: String)

/** A form's optional part (`fold`): its summary, note and fields. */
data class FormFold(val summary: String, val note: String, val fields: List<FormField>)

/** A goal kind on the grid (`goalTypeModel`): its archetype, icon, title and templates. */
data class GoalKind(val arch: String, val icon: String, val title: String, val sub: String)

/** What `nativeForm` answers (RM-31 to RM-33). */
sealed interface FormSheet {
    fun json(): JsonValue

    /**
     * A commitment or goal form. [id] as the page holds it: a rule's stored id (null: undefined,
     * left out), [JsonNull] for a new commitment, the goal kind or `at:` id of a goal form.
     */
    data class Form(
        val kind: String,
        val id: JsonValue?,
        val title: String,
        val text: String,
        val fields: List<FormField>,
        val fold: FormFold?,
        val note: String,
        val save: SheetCommand,
        val cancel: String,
        val remove: SheetCommand?,
    ) : FormSheet {
        override fun json(): JsonValue =
            obj(
                "kind" to s(kind),
                "id" to id,
                "title" to s(title),
                "text" to s(text),
                "fields" to Js.list(fields.map { it.json() }),
                "fold" to
                    (fold?.let {
                        obj(
                            "summary" to s(it.summary),
                            "note" to s(it.note),
                            "fields" to Js.list(it.fields.map { f -> f.json() }),
                        )
                    } ?: JsonNull),
                "note" to s(note),
                "save" to command(save),
                "cancel" to s(cancel),
                "remove" to (remove?.let(::command) ?: JsonNull),
            )

        private fun command(c: SheetCommand) = obj("label" to s(c.label), "command" to s(c.command))
    }

    /** The goal-kind grid. */
    data class GoalTypes(val title: String, val text: String, val options: List<GoalKind>) :
        FormSheet {
        override fun json(): JsonValue =
            obj(
                "kind" to s("goalType"),
                "title" to s(title),
                "text" to s(text),
                "options" to
                    Js.list(
                        options.map {
                            obj(
                                "arch" to s(it.arch),
                                "icon" to s(it.icon),
                                "title" to s(it.title),
                                "sub" to s(it.sub),
                            )
                        }
                    ),
            )
    }

    /** A commitment removed since the screen asked. */
    data class Gone(val kind: String, val id: String) : FormSheet {
        override fun json(): JsonValue =
            obj("kind" to s(kind), "id" to s(id), "gone" to com.lifemax.rpg.core.save.JsonBool.TRUE)
    }
}

/** RM-31 to RM-33: `ruleForm`, `goalTypeModel`, `goalFormModel`, `activityAtForm`, `nativeForm`. */
internal object Forms {
    private val SLEEP = JsonString("SLEEP")
    private val PREFERRED = JsonString("PREFERRED")
    private val WORK = JsonString("WORK")
    private val LIGHT = JsonString("LIGHT")
    private const val CUSTOM = "CUSTOM"
    private const val AT = "at:"
    private const val DAY = 1440.0
    private const val SHORT_DAY = 2
    private const val UNWIND = 30.0
    private const val ROTATION = 4.0
    private val PRIORITY_ORDER = listOf(5, 4, 3, 2, 1)

    /** `nativeSheet(kind, id)` for the form kinds; null for a goal kind the page does not know. */
    fun sheet(p: DirectorPanels, kind: String, id: String): FormSheet? {
        if (kind == "goalForm" && id.startsWith(AT)) return activityAt(p, id)
        if (kind == "goalType") return goalTypes(p)
        if (kind == "goalForm")
            return if (DemandTables.archetypes.containsKey(id) || id == CUSTOM) goalForm(p, id)
            else null
        if (id.isEmpty()) return rule(p, null)
        val rules = Js.items(get(get(p.state, "availability"), "rules"), "rules")
        val r =
            rules.firstOrNull {
                get(it, "id") == s(id) && get(it, "kind") != SLEEP && get(it, "kind") != PREFERRED
            }
        return if (r != null) rule(p, r) else FormSheet.Gone(kind, id)
    }

    private fun choices(list: List<Choice>): List<FieldOption> =
        list.map { FieldOption(s(text(it.value)), it.label) }

    /** RM-31 `ruleForm(r)`: the commitment sheet, new (no [stored]) or an edit. */
    private fun rule(p: DirectorPanels, stored: JsonValue?): FormSheet.Form {
        val w = p.words
        fun ph(k: String) = w.phrase("ruleForm", k)
        val edit = stored != null
        val r = stored ?: w.newRule
        val kind = get(r, "kind")
        val decompress = get(r, "decompressMin")
        val unwind =
            if (PageValues.present(decompress)) decompress
            else n(if (kind == WORK && get(r, "load") != LIGHT) UNWIND else 0.0)
        val rot = get(r, "rotation")
        val rotating = truthy(rot)
        val on = if (rotating) Js.length(get(rot, "activeDays")) else ROTATION
        val off =
            if (rotating) number(get(rot, "cycleLength")) - Js.length(get(rot, "activeDays"))
            else ROTATION
        val days =
            if (rotating) JsonArray.EMPTY
            else
                get(r, "days").let {
                    when {
                        !truthy(it) -> JsonArray.EMPTY
                        it is JsonArray -> JsonArray(it.items)
                        it is JsonString -> it
                        else -> throw PageTypeError("days.slice is not a function")
                    }
                }
        val anchor =
            if (rotating) get(rot, "anchorDate")
            else s(p.clock.dateKey(p.models.nowEpoch, p.zone()))
        val start = get(r, "startMin")
        val end = number(Js.add(start, get(r, "durationMin"))) % DAY
        val commute = get(r, "commuteMin").let { if (truthy(it)) it else n(0.0) }
        val load = get(r, "load").let { if (truthy(it)) it else s(LOAD_DEFAULT) }
        val fields =
            listOf(
                FormField("title", "text", ph("name"), get(r, "title"), maxLength = TITLE_MAX),
                FormField("kind", "choice", ph("type"), kind, options = choices(w.ruleKinds)),
                FormField(
                    "rotating",
                    "check",
                    ph("rotating"),
                    Js.b(rotating),
                    reveals = listOf("on", "off", "anchor"),
                    hides = listOf("days"),
                ),
                FormField(
                    "days",
                    "days",
                    ph("days"),
                    days,
                    required = true,
                    options = w.dayNames.mapIndexed { k, d -> FieldOption(n(k), d.take(SHORT_DAY)) },
                ),
                FormField(
                    "on",
                    "number",
                    ph("daysOn"),
                    n(on),
                    group = 0,
                    min = 1.0,
                    max = MOST_DAYS,
                ),
                FormField(
                    "off",
                    "number",
                    ph("daysOff"),
                    n(off),
                    group = 0,
                    min = 1.0,
                    max = MOST_DAYS,
                ),
                FormField("anchor", "date", ph("firstDay"), anchor, required = true, group = 0),
                time(p, "start", ph("starts"), start, 1),
                time(p, "end", ph("ends"), n(end), 1),
                FormField(
                    "commute",
                    "number",
                    ph("travel"),
                    commute,
                    group = 2,
                    min = 0.0,
                    max = TRAVEL_MAX,
                    step = STEP,
                ),
                FormField(
                    "unwind",
                    "number",
                    ph("unwind"),
                    unwind,
                    group = 2,
                    min = 0.0,
                    max = UNWIND_MAX,
                    step = STEP,
                ),
                FormField("load", "choice", ph("tiring"), load, options = choices(w.ruleLoads)),
            )
        return FormSheet.Form(
            kind = "rule",
            id = if (edit) get(r, "id") else JsonNull,
            title = if (edit) ph("editPrefix") + text(get(r, "title")) else ph("addTitle"),
            text = "",
            fields = fields,
            fold = null,
            note = ph("note"),
            save = SheetCommand(if (edit) ph("save") else ph("add"), "ruleSave"),
            cancel = ph("cancel"),
            remove = if (edit) SheetCommand(ph("remove"), "ruleRemove") else null,
        )
    }

    /** A time field: its value and the page's text for it (`fHm`). */
    private fun time(p: DirectorPanels, key: String, label: String, value: JsonValue?, group: Int) =
        FormField(
            key,
            "time",
            label,
            value,
            required = true,
            group = group,
            timeText = p.clock.hm(number(value)),
        )

    /** RM-32 `goalTypeModel()`: the engine's archetypes, then your own. */
    private fun goalTypes(p: DirectorPanels): FormSheet.GoalTypes {
        fun ph(k: String) = p.words.phrase("goalTypeModel", k)
        val kinds =
            DemandTables.archetypes.map { (arch, a) ->
                GoalKind(
                    arch,
                    tagIcon(p, a.templates[0].tag),
                    a.title,
                    a.templates.joinToString(ph("comma")) { text(it["title"]) },
                )
            } + GoalKind(CUSTOM, ph("customIcon"), ph("customTitle"), ph("customSub"))
        return FormSheet.GoalTypes(ph("title"), ph("text"), kinds)
    }

    /** `tagIcon(t)`: the icon of the first `TAG_ICON` entry the tag's text starts with. */
    fun tagIcon(p: DirectorPanels, tag: JsonValue?): String {
        val t = text(tag)
        return p.words.tagIcons.firstOrNull { t.startsWith(it.first) }?.second
            ?: p.words.phrase("tagIcon", "fallback")
    }

    /** RM-32 `goalFormModel(arch)`: an archetype's form, or your own activity's (CUSTOM). */
    private fun goalForm(p: DirectorPanels, arch: String): FormSheet.Form {
        val w = p.words
        fun ph(k: String) = w.phrase("goalFormModel", k)
        val a = DemandTables.archetypes[arch]
        val volume = a != null && a.templates.any { truthy(it["volumeDriver"]) }
        val fields = ArrayList<FormField>()
        fields +=
            FormField(
                "title",
                "text",
                ph("name"),
                s(a?.title ?: ""),
                required = true,
                maxLength = NAME_MAX,
                placeholder = ph("namePlaceholder"),
            )
        if (arch == LANGUAGE)
            fields +=
                FormField(
                    "subject",
                    "text",
                    ph("language"),
                    s(ph("languageValue")),
                    maxLength = SUBJECT_MAX,
                )
        if (a == null)
            fields +=
                listOf(
                    FormField(
                        "tag",
                        "choice",
                        ph("activity"),
                        w.customTags[0].value,
                        options = choices(w.customTags),
                    ),
                    FormField(
                        "minutes",
                        "number",
                        ph("minutes"),
                        n(MINUTES),
                        group = 0,
                        min = MINUTES_MIN,
                        max = MINUTES_MAX,
                        step = MINUTES_STEP,
                    ),
                    FormField(
                        "perWeek",
                        "number",
                        ph("perWeek"),
                        n(PER_WEEK),
                        group = 0,
                        min = 1.0,
                        max = MOST_DAYS,
                    ),
                    FormField(
                        "physical",
                        "number",
                        ph("physical"),
                        n(EFFORT),
                        group = 1,
                        min = 1.0,
                        max = EFFORT_MAX,
                    ),
                    FormField(
                        "mental",
                        "number",
                        ph("mental"),
                        n(EFFORT),
                        group = 1,
                        min = 1.0,
                        max = EFFORT_MAX,
                    ),
                )
        fields +=
            FormField(
                "priority",
                "choice",
                ph("priority"),
                s(DEFAULT_PRIORITY),
                group = 2,
                options = PRIORITY_ORDER.map { FieldOption(s(it.toString()), w.priorities[it]) },
            )
        fields +=
            FormField(
                "level",
                "choice",
                ph("experience"),
                s(NOVICE),
                group = 2,
                options = choices(w.goalLevels),
            )
        val fold =
            if (!volume) null
            else
                FormFold(
                    ph("foldSummary"),
                    ph("foldNote"),
                    listOf(
                        FormField(
                            "units",
                            "number",
                            ph("total"),
                            JsonNull,
                            group = 3,
                            placeholder = ph("totalPlaceholder"),
                        ),
                        FormField(
                            "unit",
                            "text",
                            ph("unit"),
                            s(""),
                            group = 3,
                            placeholder = ph("unitPlaceholder"),
                        ),
                        FormField(
                            "rate",
                            "number",
                            ph("rate"),
                            JsonNull,
                            group = 4,
                            placeholder = ph("ratePlaceholder"),
                        ),
                        FormField("deadline", "date", ph("by"), s(""), group = 4),
                    ),
                )
        return FormSheet.Form(
            kind = "goalForm",
            id = s(if (a != null) arch else CUSTOM),
            title = a?.title ?: ph("customTitle"),
            text = "",
            fields = fields,
            fold = fold,
            note = "",
            save = SheetCommand(ph("save"), "goalAdd"),
            cancel = ph("cancel"),
            remove = null,
        )
    }

    /** RM-33 `activityAtForm(id)`: one activity at the time held (`at:<epoch>`), else null. */
    private fun activityAt(p: DirectorPanels, id: String): FormSheet.Form? {
        val w = p.words
        fun ph(k: String) = w.phrase("activityAtForm", k)
        val epoch = Js.number(id.substring(AT.length))
        if (!Js.safeInteger(epoch) || epoch <= 0) return null
        val zone = p.zone()
        val local = com.lifemax.rpg.core.lifedirector.WallClock.localParts(epoch, zone)
        return FormSheet.Form(
            kind = "goalForm",
            id = s(id),
            title = ph("title"),
            text = ph("text"),
            fields =
                listOf(
                    FormField(
                        "title",
                        "text",
                        ph("name"),
                        s(""),
                        required = true,
                        maxLength = ACTIVITY_MAX,
                    ),
                    FormField(
                        "tag",
                        "choice",
                        ph("activity"),
                        w.customTags[0].value,
                        options = choices(w.customTags),
                    ),
                    FormField(
                        "date",
                        "date",
                        ph("date"),
                        s(p.clock.dateKey(epoch, zone)),
                        required = true,
                    ),
                    FormField(
                        "start",
                        "time",
                        ph("start"),
                        n(local.minuteOfDay.toDouble()),
                        required = true,
                        timeText = p.clock.hm(local.minuteOfDay.toDouble()),
                    ),
                    FormField(
                        "minutes",
                        "number",
                        ph("duration"),
                        n(MINUTES),
                        min = MINUTES_MIN,
                        max = MINUTES_MAX,
                        step = MINUTES_STEP,
                    ),
                ),
            fold = null,
            note = ph("note"),
            save = SheetCommand(ph("save"), "activityAtAdd"),
            cancel = ph("cancel"),
            remove = null,
        )
    }

    private const val LOAD_DEFAULT = "MIXED_MODERATE"
    private const val LANGUAGE = "SKILL_LANGUAGE"
    private const val DEFAULT_PRIORITY = "3"
    private const val NOVICE = "NOVICE"
    private const val TITLE_MAX = 40
    private const val NAME_MAX = 50
    private const val SUBJECT_MAX = 20
    private const val ACTIVITY_MAX = 80
    private const val MOST_DAYS = 14.0
    private const val TRAVEL_MAX = 240.0
    private const val UNWIND_MAX = 120.0
    private const val STEP = 5.0
    private const val MINUTES = 30.0
    private const val MINUTES_MIN = 15.0
    private const val MINUTES_MAX = 180.0
    private const val MINUTES_STEP = 15.0
    private const val PER_WEEK = 3.0
    private const val EFFORT = 2.0
    private const val EFFORT_MAX = 5.0
}
