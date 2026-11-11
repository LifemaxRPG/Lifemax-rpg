package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** Where a load found its state (the page's `report.source`). */
enum class LoadSource(val pageName: String) {
    MAIN(StoreKeys.MAIN),
    BACKUP(StoreKeys.BACKUP),
    NEW("NEW"),
    MEMORY_ONLY("MEMORY_ONLY"),
}

/**
 * The page's load report (`engine.js:2286`): where the state came from, whether that was the
 * recovery copy, the first quarantine key named, the version migrated from (0, or `-0` when the
 * stored text said `-0`), whether the storage works, the last reason a parsed text was refused, and
 * the storage error when it does not work.
 */
data class LoadReport(
    val source: LoadSource,
    val recovered: Boolean,
    val quarantined: String?,
    val migratedFrom: Double?,
    val available: Boolean,
    val invalid: String? = null,
    val error: String? = null,
)

/** What a load gave. */
sealed interface LoadOutcome {
    /** The state the page would hold, as it holds it, and its report. */
    data class Loaded(val state: JsonValue, val report: LoadReport) : LoadOutcome

    /** The page's load throws here (a prototype state whose `work` is not a list, LS01). */
    data class Threw(val name: String, val message: String) : LoadOutcome

    /**
     * The text under [key] nests deeper than native reads ([JsonReader.MAX_DEPTH]) though the page
     * would read it (LIFE_DIRECTOR_STORE_SPEC.md DV-1). Native stops here and writes nothing: it
     * never quarantines, nor loads past, a text the page could read.
     */
    data class BeyondNative(val key: String) : LoadOutcome
}

/** Why a save was not done. */
enum class SaveRefusal {
    UNAVAILABLE,
    INVALID,
    STORAGE_FULL,
    STORAGE_ERROR,
    NOT_VERIFIED,
}

/** What a save gave; [NotSaved.message] is the page's `error` text, word for word. */
sealed interface SaveOutcome {
    /** Written and read back the same; [units] is the text's length in UTF-16 units (`bytes`). */
    data class Saved(val units: Int) : SaveOutcome

    data class NotSaved(val reason: SaveRefusal, val message: String) : SaveOutcome
}

/** What reading a backup text gave (`importJson`). */
sealed interface ImportOutcome {
    data class Imported(val state: JsonValue) : ImportOutcome

    /** Not JSON (the page throws a SyntaxError, whose words are V8's; these are native's). */
    data class NotJson(val offset: Int, val reason: String) : ImportOutcome

    /** The state in it is refused; [message] is the page's (`Backup is not valid: ...`). */
    data class Invalid(val message: String) : ImportOutcome

    /** The page's `migrate` throws on it (LS01); [message] is V8's. */
    data class Threw(val name: String, val message: String) : ImportOutcome
}

/**
 * The page's Life Director store (`LifeDirectorStore.create(storage)`, `engine.js:2282-2338`) over
 * any [KeyValueStorage], rule for rule (LIFE_DIRECTOR_STORE_SPEC.md LST-2 to LST-23). Making one
 * probes the storage once (LST-2). It never throws; every outcome is a typed result.
 *
 * [encode] writes a state's text: the page's own `JSON.stringify` ([PageJson.text]) by default, so
 * native writes exactly what the page would; [DirectorStoreCodec.encode]'s preserving writer can be
 * given instead.
 */
class DirectorStore(
    private val storage: KeyValueStorage,
    private val encode: (JsonValue) -> String = PageJson::text,
) {
    /** False when the probe failed: every save is then refused and every load is MEMORY_ONLY. */
    val available: Boolean
    private var lastError: StorageError? = null

    init {
        val failed =
            storage.write(StoreKeys.PROBE, StoreKeys.PROBE_VALUE) ?: storage.remove(StoreKeys.PROBE)
        lastError = failed
        available = failed == null
    }

    /** `load(opts)`: the main key, else the recovery copy, else a new state (LST-3 to LST-9). */
    fun load(options: StoreOptions?): LoadOutcome {
        if (!available)
            return LoadOutcome.Loaded(
                StoreSchema.defaultState(options),
                LoadReport(LoadSource.MEMORY_ONLY, false, null, null, false, error = "$lastError"),
            )
        var quarantined: String? = null
        var invalid: String? = null
        for ((key, source) in
            listOf(StoreKeys.MAIN to LoadSource.MAIN, StoreKeys.BACKUP to LoadSource.BACKUP)) {
            val raw =
                when (val read = storage.read(key)) {
                    is StorageRead.Failed -> {
                        lastError = read.error
                        continue
                    }
                    StorageRead.Missing -> continue
                    is StorageRead.Found -> read.text
                }
            val parsed =
                when (val json = JsonReader.read(raw)) {
                    is JsonRead.Parsed -> json.value
                    is JsonRead.Malformed -> {
                        if (isTooDeep(json)) return LoadOutcome.BeyondNative(key)
                        val q = StoreKeys.quarantineKey(key)
                        storage.write(q, raw)
                        quarantined = quarantined ?: q
                        continue
                    }
                }
            val before =
                if (JsValues.truthy(parsed)) JsValues.member(parsed, "schemaVersion") else parsed
            val state =
                when (val m = StoreSchema.migrate(parsed)) {
                    is Migration.Threw -> return LoadOutcome.Threw(m.name, m.message)
                    is Migration.Done -> m.value
                }
            val reason = StoreSchema.validate(state)
            if (reason != null || state == null) {
                invalid = reason
                continue
            }
            val migratedFrom =
                when {
                    JsValues.isNumber(before, StoreKeys.SCHEMA_VERSION) -> null
                    JsValues.isNullish(before) -> 0.0
                    else -> (before as JsonNumber).value
                }
            return LoadOutcome.Loaded(
                state,
                LoadReport(
                    source,
                    source == LoadSource.BACKUP,
                    quarantined,
                    migratedFrom,
                    true,
                    invalid,
                ),
            )
        }
        return LoadOutcome.Loaded(
            StoreSchema.defaultState(options),
            LoadReport(LoadSource.NEW, false, quarantined, null, true, invalid),
        )
    }

    /**
     * `save(state)` (LST-16 to LST-20): refused when the storage does not work or the state is
     * invalid; else the main key's previous text goes to the recovery copy when it differs, the new
     * text is written and read back. A failed step leaves the steps before it done.
     */
    fun save(state: JsonValue?): SaveOutcome {
        if (!available) return SaveOutcome.NotSaved(SaveRefusal.UNAVAILABLE, StoreWords.UNAVAILABLE)
        StoreSchema.validate(state)?.let {
            return SaveOutcome.NotSaved(SaveRefusal.INVALID, StoreWords.INVALID_PREFIX + it)
        }
        val text = encode(state!!)
        when (val previous = storage.read(StoreKeys.MAIN)) {
            is StorageRead.Failed -> return failed(previous.error)
            is StorageRead.Found ->
                if (previous.text != text)
                    storage.write(StoreKeys.BACKUP, previous.text)?.let {
                        return failed(it)
                    }
            StorageRead.Missing -> Unit
        }
        storage.write(StoreKeys.MAIN, text)?.let {
            return failed(it)
        }
        return when (val back = storage.read(StoreKeys.MAIN)) {
            is StorageRead.Failed -> failed(back.error)
            is StorageRead.Found ->
                if (back.text == text) SaveOutcome.Saved(text.length) else notVerified()
            StorageRead.Missing -> notVerified()
        }
    }

    /** `clear(opts)`: a durable new state, saved as any other (LST-21). */
    fun clear(options: StoreOptions?): SaveOutcome = save(StoreSchema.defaultState(options))

    /**
     * `exportJson(state)`: the backup text (LST-22); a null state leaves the member out, as the
     * page does.
     */
    fun exportBackup(state: JsonValue?): String = BackupText.write(state)

    /** `importJson(text)`: the state a backup text holds, migrated and checked (LST-23). */
    fun importBackup(text: String): ImportOutcome = BackupText.read(text)

    private fun failed(error: StorageError): SaveOutcome.NotSaved {
        lastError = error
        return if (error.isQuota)
            SaveOutcome.NotSaved(SaveRefusal.STORAGE_FULL, StoreWords.STORAGE_FULL)
        else SaveOutcome.NotSaved(SaveRefusal.STORAGE_ERROR, error.message.ifEmpty { "$error" })
    }

    private fun notVerified() =
        SaveOutcome.NotSaved(SaveRefusal.NOT_VERIFIED, StoreWords.NOT_VERIFIED)

    companion object {
        /** The reader refused a text only for its depth ([JsonReader]'s reason for that). */
        fun isTooDeep(read: JsonRead.Malformed): Boolean = read.reason.startsWith(TOO_DEEP)

        private const val TOO_DEEP = "nested deeper than"
    }
}

/** The backup text format (`exportJson`, `importJson`; LST-22, LST-23). */
object BackupText {
    /** `JSON.stringify({ format, version, state }, null, 1)`. */
    fun write(state: JsonValue?): String {
        val members =
            linkedMapOf<String, JsonValue>(
                "format" to JsonString(StoreKeys.BACKUP_FORMAT),
                "version" to JsonNumber(StoreKeys.SCHEMA_VERSION),
            )
        if (state != null) members["state"] = state
        return PageJson.indented(JsonObject(members))
    }

    /** The state in a backup text, or the text itself read as a state when it is not a backup. */
    fun read(text: String): ImportOutcome {
        val o =
            when (val json = JsonReader.read(text)) {
                is JsonRead.Parsed -> json.value
                is JsonRead.Malformed -> return ImportOutcome.NotJson(json.offset, json.reason)
            }
        val isBackup =
            JsValues.truthy(o) &&
                JsValues.member(o, "format") == JsonString(StoreKeys.BACKUP_FORMAT)
        val inner = if (isBackup) JsValues.member(o, "state") else o
        val state =
            when (val m = StoreSchema.migrate(inner)) {
                is Migration.Threw -> return ImportOutcome.Threw(m.name, m.message)
                is Migration.Done -> m.value
            }
        StoreSchema.validate(state)?.let {
            return ImportOutcome.Invalid(StoreWords.BACKUP_INVALID_PREFIX + it)
        }
        return ImportOutcome.Imported(state!!)
    }
}
