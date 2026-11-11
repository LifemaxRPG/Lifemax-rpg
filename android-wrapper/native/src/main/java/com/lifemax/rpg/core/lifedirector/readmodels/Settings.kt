package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.lifedirector.PlanPreferences
import com.lifemax.rpg.core.lifedirector.readmodels.Js.get
import com.lifemax.rpg.core.lifedirector.readmodels.Js.n
import com.lifemax.rpg.core.lifedirector.readmodels.Js.obj
import com.lifemax.rpg.core.lifedirector.readmodels.Js.s
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter

/** A slider (`range` in `nativeSettings`, RM-26): its stops' labels in the page's words. */
data class SettingRange(
    val key: String,
    val label: String,
    val hint: String,
    val min: Double,
    val max: Double,
    val step: Double,
    /** The preference as `normalizePreferences` gives it (null: absent). */
    val value: JsonValue?,
    val stops: List<String>,
) {
    fun json(): JsonValue =
        obj(
            "key" to s(key),
            "label" to s(label),
            "hint" to s(hint),
            "min" to n(min),
            "max" to n(max),
            "step" to n(step),
            "value" to value,
            "stops" to Js.list(stops.map(::s)),
        )
}

/** A segmented choice: its title, the value chosen and the options. */
data class SettingChoice(val title: String, val value: JsonValue?, val options: List<Choice>)

/** A span of text, bold or not (the page's `<b>`). */
data class TextSpan(val text: String, val bold: Boolean)

/** `nativeSettings()` (RM-26, RM-27). */
data class SettingsPanel(
    val intensity: SettingChoice,
    val intensityNote: String,
    val ranges: List<SettingRange>,
    val restTitle: String,
    val restNote: String,
    /** Each day (Sunday first): its two-letter label and whether it is a rest day. */
    val restDays: List<Pair<String, Boolean>>,
    val horizon: SettingChoice,
    val misses: SettingChoice,
    val missText: List<TextSpan>,
    val grace: SettingRange,
    val guide: JsonValue,
    val data: JsonValue,
    val storageUnavailable: Boolean,
    val diagnosticsTitle: String,
    val diagnosticsLine: String,
    val labLabel: String,
    val labOn: Boolean,
) {
    fun json(): JsonValue {
        fun choice(c: SettingChoice, vararg more: Pair<String, JsonValue?>) =
            obj(
                "title" to s(c.title),
                "value" to c.value,
                "options" to
                    Js.list(c.options.map { obj("value" to it.value, "label" to s(it.label)) }),
                *more,
            )
        val data = LinkedHashMap((data as JsonObject).members)
        data["storageUnavailable"] = Js.b(storageUnavailable)
        return obj(
            "intensity" to choice(intensity, "note" to s(intensityNote)),
            "ranges" to Js.list(ranges.map { it.json() }),
            "restDays" to
                obj(
                    "title" to s(restTitle),
                    "note" to s(restNote),
                    "days" to
                        Js.list(
                            restDays.mapIndexed { k, (label, on) ->
                                obj("day" to n(k), "label" to s(label), "on" to Js.b(on))
                            }
                        ),
                ),
            "horizon" to choice(horizon),
            "misses" to
                choice(
                    misses,
                    "text" to
                        Js.list(missText.map { obj("t" to s(it.text), "b" to Js.b(it.bold)) }),
                    "grace" to grace.json(),
                ),
            "guide" to guide,
            "data" to JsonObject(data),
            "diagnostics" to obj("title" to s(diagnosticsTitle), "line" to s(diagnosticsLine)),
            "lab" to obj("label" to s(labLabel), "on" to Js.b(labOn)),
        )
    }
}

/** RM-26, RM-27: `nativeSettings` and `diagnosticsLine` (logic.js:3637-3659, 1683-1689). */
internal object Settings {
    private const val MEMORY_ONLY = "MEMORY_ONLY"
    private const val SHORT_DAY = 2

    fun panel(p: DirectorPanels): SettingsPanel {
        val w = p.words
        val st = p.state
        val prefs = PlanPreferences.of(get(st, "prefs"))
        val ledger = get(st, "ledger")
        fun t(k: String) = (w.settingsText(k) as JsonString).value
        fun range(r: RangeSpec, label: (Double) -> String): SettingRange {
            val stops = ArrayList<String>()
            var v = r.low
            while (v <= r.high) {
                stops += label(v)
                v += r.step
            }
            return SettingRange(r.key, r.label, r.hint ?: "", r.low, r.high, r.step, prefs[r.key], stops)
        }
        val rest = prefs["preferredRestDays"]
        val misses =
            SettingChoice(t("misses"), prefs["consequenceMode"], w.missOptions)
        val missText =
            listOf(
                TextSpan(w.missText + w.phrase("nativeSettings", "restTokens"), false),
                TextSpan(text(get(ledger, "restTokens")), true),
                TextSpan(w.phrase("nativeSettings", "restTail"), false),
            )
        val grace = range(w.grace) { text(n(it)) + w.phrase("graceLabel", "minuteMark") }
        return SettingsPanel(
            intensity = SettingChoice(t("intensity"), prefs["intensityLevel"], w.intensityOptions),
            intensityNote = w.intensityNote,
            ranges = w.prefRanges.map { range(it, p::mins) },
            restTitle = t("restDays"),
            restNote = t("restNote"),
            restDays = w.dayNames.mapIndexed { k, d -> d.take(SHORT_DAY) to includes(rest, k) },
            horizon =
                SettingChoice(
                    t("horizon"),
                    s(text(prefs["planningHorizonDays"])),
                    w.horizonOptions.map { Choice(s(text(it.value)), it.label) },
                ),
            misses = misses,
            missText = missText,
            grace = grace,
            guide = w.settingsText("guide"),
            data = w.settingsText("data"),
            storageUnavailable = p.loadSource == MEMORY_ONLY,
            diagnosticsTitle = t("diagnostics"),
            diagnosticsLine = diagnosticsLine(p, ledger),
            labLabel = t("lab"),
            labOn = truthy(get(get(st, "ui"), "lab")),
        )
    }

    /** `p.preferredRestDays.includes(k)`: a list's item, or a text's characters. */
    private fun includes(days: JsonValue?, k: Int): Boolean =
        when (days) {
            is JsonArray -> Js.includes(days.items, n(k))
            is JsonString -> k.toString() in days.value
            else -> throw PageTypeError("preferredRestDays.includes is not a function")
        }

    /** RM-27 `diagnosticsLine()`: engine, plan, events, host and the last rejected plan. */
    private fun diagnosticsLine(p: DirectorPanels, ledger: JsonValue?): String {
        fun ph(k: String) = p.words.phrase("diagnosticsLine", k)
        val st = p.state
        val s = get(st, "schedule")
        val plan =
            if (truthy(s))
                text(get(s, "status")) +
                    ph("sep") +
                    text(get(s, "computeTimeMs")) +
                    ph("ms") +
                    text(get(get(get(s, "validation"), "qualityMetrics"), "sqi")) +
                    ph("sep") +
                    text(get(get(s, "scheduledInstances"), "length")) +
                    ph("planned")
            else ph("noPlan")
        val events = text(get(get(ledger, "order"), "length"))
        val cap = p.host.capabilities()
        val host =
            if (cap.connected) ph("connected")
            else ph("missing") + cap.missing.joinToString(ph("comma"))
        val rejected = get(st, "lastRejected")
        val tail =
            if (truthy(rejected))
                ph("rejected") + (get(rejected, "violations")?.let(JsonWriter::write) ?: "undefined")
            else ""
        return ph("engine") + p.words.engineVersion + ph("sep") + plan + ph("events") + events +
            ph("host") + host + tail
    }
}
