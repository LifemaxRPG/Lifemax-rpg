package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.directorstore.StoreGolden.obj
import com.lifemax.rpg.core.directorstore.StoreGolden.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Runs a store golden case natively ([DirectorStore] over a [ScriptedStorage]) and writes its
 * answer as the recorder writes the page's (tools/gen_ld_store_golden.js, file comment).
 */
object StoreReplay {
    val ADAPTERS =
        setOf(
            "load",
            "save",
            "clear",
            "export",
            "import",
            "validate",
            "migrate",
            "defaultState",
            "sequence",
        )

    /** The case's answer natively; [StoreGolden.BeyondNative] when native cannot read its input. */
    fun run(c: StoreGolden.Case, refs: StoreGolden.Refs): JsonValue {
        val spec = c.args["storage"] as? JsonObject
        val quota = if (c.family == "browser") MemoryStorage.CHROMIUM_QUOTA_UNITS else null
        val storage = ScriptedStorage(spec ?: JsonObject.EMPTY, refs, quota)
        val out = LinkedHashMap<String, JsonValue>()
        out.putAll(answer(c, refs, storage).members)
        if (spec != null) {
            out["ops"] = JsonArray(storage.log)
            out["after"] = storage.after()
        }
        return JsonObject(out)
    }

    private fun answer(
        c: StoreGolden.Case,
        refs: StoreGolden.Refs,
        storage: ScriptedStorage,
    ): JsonObject {
        val a = c.args
        return when (c.fn) {
            "load" -> load(DirectorStore(storage), options(a), refs, withAvailable = true).first
            "save" ->
                obj("result" to result(DirectorStore(storage).save(refs.stateArg(a["state"]!!))))
            "clear" -> obj("result" to result(DirectorStore(storage).clear(options(a))))
            "export" -> obj("text" to refs.ref(BackupText.write(refs.stateArg(a["state"]!!))))
            "import" -> imported(BackupText.read(refs.text(a["text"]!!)), refs)
            "validate" ->
                obj(
                    "reason" to
                        (StoreSchema.validate(refs.stateArg(a["state"]!!))?.let(::JsonString)
                            ?: JsonNull)
                )
            "migrate" ->
                when (val m = StoreSchema.migrate(refs.stateArg(a["state"]!!))) {
                    is Migration.Done -> obj("state" to refs.state(m.value))
                    is Migration.Threw -> threw(m.name, m.message)
                }
            "defaultState" -> obj("state" to refs.state(StoreSchema.defaultState(options(a))))
            "sequence" -> obj("steps" to JsonArray(sequence(a, refs, storage)))
            else -> error("no adapter for ${c.fn}")
        }
    }

    private fun sequence(
        a: JsonObject,
        refs: StoreGolden.Refs,
        storage: ScriptedStorage,
    ): List<JsonValue> {
        var store = DirectorStore(storage)
        var current: JsonValue? = null
        return (a["steps"] as JsonArray).items.map { item ->
            val s = item as JsonObject
            when (str(s["do"])) {
                "create" -> {
                    store = DirectorStore(storage)
                    obj("available" to JsonBool.of(store.available))
                }
                "load" -> {
                    val (o, state) = load(store, options(s), refs, withAvailable = false)
                    if (state != null) current = state
                    o
                }
                "save" -> obj("result" to result(store.save(current)))
                "edit" -> {
                    val target = current
                    if (target is JsonObject || target is JsonArray) {
                        val set = (s["set"] as JsonArray).items
                        @Suppress("UNCHECKED_CAST")
                        if (target is JsonObject)
                            (target.members as MutableMap<String, JsonValue>)[str(set[0])] =
                                refs.uncanon(set[1])
                    }
                    JsonObject.EMPTY
                }
                "clear" -> obj("result" to result(store.clear(options(s))))
                // the recorder's current state starts as null (not undefined), so its export holds
                // "state": null
                "export" -> obj("text" to refs.ref(store.exportBackup(current ?: JsonNull)))
                "import" ->
                    when (val i = store.importBackup(refs.text(s["text"]!!))) {
                        is ImportOutcome.Imported -> {
                            val saved = store.save(i.state)
                            if (saved is SaveOutcome.Saved) current = i.state
                            obj("saved" to result(saved))
                        }
                        else -> obj("refused" to (imported(i, refs)["threw"]!!))
                    }
                "storage" -> {
                    storage.configure(s["faults"], s["quota"], s["items"])
                    JsonObject.EMPTY
                }
                else -> error("unknown step")
            }
        }
    }

    /** The load's answer, and the state it gave (null when it threw). */
    private fun load(
        store: DirectorStore,
        options: StoreOptions?,
        refs: StoreGolden.Refs,
        withAvailable: Boolean,
    ): Pair<JsonObject, JsonValue?> =
        when (val r = store.load(options)) {
            is LoadOutcome.Loaded -> {
                val m = linkedMapOf("state" to refs.state(r.state), "report" to report(r.report))
                if (withAvailable) m["available"] = JsonBool.of(store.available)
                JsonObject(m) to r.state
            }
            is LoadOutcome.Threw -> threw(r.name, r.message) to null
            is LoadOutcome.BeyondNative -> throw StoreGolden.BeyondNative(r.key)
        }

    fun report(r: LoadReport): JsonValue {
        val m =
            linkedMapOf(
                "source" to JsonString(r.source.pageName),
                "recovered" to JsonBool.of(r.recovered),
                "quarantined" to (r.quarantined?.let(::JsonString) ?: JsonNull),
                "migratedFrom" to (r.migratedFrom?.let(StoreGolden::canonNumber) ?: JsonNull),
                "available" to JsonBool.of(r.available),
            )
        r.invalid?.let { m["invalid"] = JsonString(it) }
        r.error?.let { m["error"] = JsonString(it) }
        return JsonObject(m)
    }

    fun result(r: SaveOutcome): JsonValue =
        when (r) {
            is SaveOutcome.Saved ->
                obj("ok" to JsonBool.TRUE, "bytes" to JsonNumber(r.units.toDouble()))
            is SaveOutcome.NotSaved -> obj("ok" to JsonBool.FALSE, "error" to JsonString(r.message))
        }

    private fun imported(i: ImportOutcome, refs: StoreGolden.Refs): JsonObject =
        when (i) {
            is ImportOutcome.Imported -> obj("state" to refs.state(i.state))
            is ImportOutcome.NotJson -> obj("threw" to obj("name" to JsonString("SyntaxError")))
            is ImportOutcome.Invalid -> threw("Error", i.message)
            is ImportOutcome.Threw -> threw(i.name, i.message)
        }

    private fun threw(name: String, message: String): JsonObject =
        obj("threw" to obj("name" to JsonString(name), "message" to JsonString(message)))

    private fun options(a: JsonObject): StoreOptions? =
        (a["opts"] as? JsonObject)?.let {
            StoreOptions(it["playerId"], it["timezone"], it["nowEpoch"])
        }
}
