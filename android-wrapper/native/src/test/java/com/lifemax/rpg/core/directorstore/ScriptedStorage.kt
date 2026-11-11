package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.directorstore.StoreGolden.obj
import com.lifemax.rpg.core.directorstore.StoreGolden.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The golden cases' storage (tools/ld_store_cases.js, module comment): the case's items, faults
 * that make the nth matching call fail, lossy writes, and Chromium's size cap, with every call
 * logged as the recorder logs it. The browser family uses it with Chromium's cap and nothing else,
 * which is how its cases check the model against the real localStorage.
 */
class ScriptedStorage(spec: JsonObject?, private val refs: StoreGolden.Refs, quota: Long? = null) :
    KeyValueStorage {
    private class Fault(val op: String, val key: String, val nth: Int, val error: JsonValue) {
        var seen = 0
    }

    private class Lossy(val key: String, val nth: Int, val keep: Int) {
        var seen = 0
    }

    val log = ArrayList<JsonValue>()
    private val items = LinkedHashMap<String, String>()
    private var faults = emptyList<Fault>()
    private var lossy = emptyList<Lossy>()
    private var quota = quota ?: Long.MAX_VALUE

    init {
        for (pair in (spec?.get("items") as? JsonArray)?.items.orEmpty()) {
            val (k, v) = (pair as JsonArray).items
            items[str(k)] = refs.text(v)
        }
        configure(spec?.get("faults"), spec?.get("quota"), null)
        lossy =
            (spec?.get("lossy") as? JsonArray)?.items.orEmpty().map {
                val l = it as JsonObject
                Lossy(str(l["key"]), int(l["nth"]), int(l["keep"]))
            }
    }

    /** A sequence's `storage` step: new faults, a new cap (JSON null: none), items changed. */
    fun configure(faultSpec: JsonValue?, quotaSpec: JsonValue?, itemsChange: JsonValue?) {
        if (faultSpec is JsonArray)
            faults =
                faultSpec.items.map {
                    val f = it as JsonObject
                    Fault(str(f["op"]), str(f["key"]), int(f["nth"]), f["error"]!!)
                }
        when (quotaSpec) {
            null -> Unit
            JsonNull -> quota = Long.MAX_VALUE
            else -> quota = (quotaSpec as JsonNumber).value.toLong()
        }
        for (pair in (itemsChange as? JsonArray)?.items.orEmpty()) {
            val (k, v) = (pair as JsonArray).items
            if (v == JsonNull) items.remove(str(k)) else items[str(k)] = refs.text(v)
        }
    }

    /** The items at the end, sorted by key, texts as the recorder writes them. */
    fun after(): JsonValue =
        JsonArray(
            items.entries
                .sortedBy { it.key }
                .map { (k, v) -> JsonArray(listOf(JsonString(k), refs.ref(v))) }
        )

    private fun fault(op: String, key: String): StorageError? {
        var error: StorageError? = null
        for (f in faults) {
            if (f.op != op || (f.key != ANY && f.key != key)) continue
            f.seen++
            if (error == null && (f.nth == 0 || f.seen == f.nth)) error = errorOf(f.error, key)
        }
        return error
    }

    private fun errorOf(spec: JsonValue, key: String): StorageError =
        if (spec is JsonString) StorageError.quotaExceeded(key)
        else StorageError(str((spec as JsonObject)["name"]), str(spec["message"]))

    private fun threw(e: StorageError): JsonValue = obj("threw" to JsonString(e.name))

    override fun read(key: String): StorageRead {
        fault("get", key)?.let {
            log += entry("get", key, threw(it))
            return StorageRead.Failed(it)
        }
        val v = items[key]
        log += entry("get", key, refs.ref(v))
        return v?.let { StorageRead.Found(it) } ?: StorageRead.Missing
    }

    override fun write(key: String, text: String): StorageError? {
        fault("set", key)?.let {
            log += entry("set", key, refs.ref(text), threw(it))
            return it
        }
        val used = items.entries.sumOf { (k, v) -> (k.length + v.length).toLong() }
        val replaced = items[key]?.let { key.length + it.length } ?: 0
        if (used - replaced + key.length + text.length > quota) {
            val q = StorageError.quotaExceeded(key)
            log += entry("set", key, refs.ref(text), threw(q))
            return q
        }
        var keep: Int? = null
        for (l in lossy) {
            if (l.key != ANY && l.key != key) continue
            l.seen++
            if (keep == null && (l.nth == 0 || l.seen == l.nth)) keep = l.keep
        }
        if (keep != null && keep < 0) {
            log += entry("set", key, refs.ref(text), JsonString("dropped"))
            return null
        }
        items[key] = if (keep == null) text else text.take(keep)
        log +=
            entry(
                "set",
                key,
                refs.ref(text),
                if (keep == null) JsonString("ok") else obj("kept" to JsonNumber(keep.toDouble())),
            )
        return null
    }

    override fun remove(key: String): StorageError? {
        fault("remove", key)?.let {
            log += entry("remove", key, threw(it))
            return it
        }
        items.remove(key)
        log += entry("remove", key)
        return null
    }

    private fun entry(op: String, key: String, vararg rest: JsonValue): JsonValue =
        JsonArray(listOf(JsonString(op), JsonString(key)) + rest)

    private fun int(v: JsonValue?): Int = (v as JsonNumber).value.toInt()

    private companion object {
        const val ANY = "*"
    }
}
