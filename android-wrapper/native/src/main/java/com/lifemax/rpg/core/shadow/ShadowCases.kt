package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.save.sameStoredValue

/** A difference a case can be saved for, as the Settings card lists it (no value from the save). */
data class CaseRef(val id: String, val kind: String, val at: Long, val count: Int)

/**
 * The cases behind "Save case…" (decision O-4): for the latest [ShadowLimits.CASES_KEPT]
 * unexplained differences, the whole action as the page sent it and both sides' outcomes where they
 * differ, **unredacted** (the owner's activity names, notes and quest names included). Held in
 * memory only: never in the ring, never in the copied report, never logged; gone when the app
 * process ends or the owner clears the check. The only way out is [text], which the plugin writes
 * to a file the owner chose after a warning. Not thread-safe: the worker owns it.
 */
class ShadowCases {
    private class Case(val ref: CaseRef, val text: String)

    private val cases = LinkedHashMap<String, Case>()

    /** The differences a case is held for, newest first. */
    fun list(): List<CaseRef> = cases.values.map { it.ref }.reversed()

    /** The case of difference [id] as a file's text, or null when none is held for it. */
    fun text(id: String): String? = cases[id]?.text

    /** Forgets every case (Clear). */
    fun clear() = cases.clear()

    /**
     * Keeps the case of [entry] (an unexplained difference just recorded or repeated): the
     * envelope's [envelopeText], the page's [before] and [page] after-states and native's [run], at
     * the keys where the two after-states differ. A repeat replaces the older case of the same
     * entry.
     */
    fun keep(
        entry: MismatchEntry,
        envelopeText: String,
        before: Map<String, JsonValue>,
        page: Map<String, JsonValue>,
        run: Prediction?,
    ) {
        val text = write(entry, envelopeText, before, page, run)
        cases.remove(entry.id)
        cases[entry.id] = Case(CaseRef(entry.id, entry.kind, entry.last, entry.count), text)
        while (
            cases.size > ShadowLimits.CASES_KEPT ||
                (cases.size > 1 &&
                    cases.values.sumOf { it.text.length } > ShadowLimits.CASES_MAX_CHARS)
        ) cases.remove(cases.keys.first())
    }

    private fun write(
        entry: MismatchEntry,
        envelopeText: String,
        before: Map<String, JsonValue>,
        page: Map<String, JsonValue>,
        run: Prediction?,
    ): String {
        val native = run?.after
        val keys =
            ShadowDiff.KEYS.filter { k ->
                val p = page[k]
                val n = native?.get(k)
                when {
                    native == null -> p != null
                    p == null || n == null -> p != n
                    else -> p !== n && !p.sameStoredValue(n)
                }
            }
        fun side(state: Map<String, JsonValue>?, other: Map<String, JsonValue>?) =
            JsonObject(
                LinkedHashMap<String, JsonValue>().apply {
                    for (k in keys) state?.get(k)?.let { put(k, trimmed(it, other?.get(k))) }
                }
            )
        val envelope = (JsonReader.read(envelopeText) as? JsonRead.Parsed)?.value
        val out =
            obj(
                "v" to JsonNumber(1.0),
                "what" to JsonString(WHAT),
                "difference" to
                    obj(
                        "id" to JsonString(entry.id),
                        "kind" to JsonString(entry.kind),
                        "first" to JsonNumber(entry.first.toDouble()),
                        "last" to JsonNumber(entry.last.toDouble()),
                        "count" to JsonNumber(entry.count.toDouble()),
                        "pageBuild" to JsonString(entry.pageBuild),
                        "paths" to JsonArray(entry.paths.map { JsonString(it) }),
                        "allow" to JsonArray(entry.allow.map { JsonString(it) }),
                    ),
                "envelope" to (envelope ?: JsonString(envelopeText)),
                "keys" to JsonArray(keys.map { JsonString(it) }),
                "before" to side(before, page),
                "page" to side(page, native),
                "native" to
                    if (run == null) JsonString(NOT_RUN)
                    else
                        obj(
                            "state" to side(native, page),
                            "returns" to run.returns,
                            "toasts" to JsonArray(run.toasts.map { JsonString(it) }),
                            "saves" to JsonNumber(run.saves.toDouble()),
                            "encodeRefusal" to
                                (run.encodeRefusal?.let { JsonString(it) } ?: JsonNull),
                        ),
            )
        return JsonWriter.write(out)
    }

    /**
     * A long record list is kept as its length and the records that differ from [other] (at most
     * [ShadowLimits.CASE_MAX_ITEMS]); anything else whole.
     */
    private fun trimmed(value: JsonValue, other: JsonValue?): JsonValue {
        if (value !is JsonArray || value.items.size <= ShadowLimits.CASE_WHOLE_ITEMS) return value
        val theirs = (other as? JsonArray)?.items.orEmpty()
        val changed = LinkedHashMap<String, JsonValue>()
        for ((i, item) in value.items.withIndex()) {
            if (changed.size >= ShadowLimits.CASE_MAX_ITEMS) break
            val t = theirs.getOrNull(i)
            if (t == null || (t !== item && !t.sameStoredValue(item))) changed[i.toString()] = item
        }
        return obj(
            "length" to JsonNumber(value.items.size.toDouble()),
            "changed" to JsonObject(changed),
        )
    }

    private fun obj(vararg m: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*m))

    private companion object {
        const val WHAT =
            "LifeMax economy check: one difference, unredacted. It holds your own data from " +
                "that action. Share it only with someone you trust."
        const val NOT_RUN = "native did not finish the action"
    }
}
