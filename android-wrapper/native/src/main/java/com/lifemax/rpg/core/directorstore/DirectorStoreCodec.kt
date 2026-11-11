package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonKind
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.sameStoredValue

/** How strictly a stored state is read. */
enum class StoreReading {
    /** The page's checks, then the JSON kinds of every field native will own ([OwnedFields]). */
    STRICT,

    /** Only the page's checks: what the page would accept. For replay and shadow comparison. */
    AS_PAGE,
}

/** Why a stored text was not read, or a state not written. Names a field, never a value. */
sealed interface StoreRefusal {
    data class NotJson(val offset: Int, val reason: String) : StoreRefusal

    /** Deeper than native reads, though the page would read it (DV-1). */
    data object TooDeep : StoreRefusal

    /** The page's `migrate` throws on it (LS01). */
    data class PageWouldThrow(val name: String) : StoreRefusal

    /** The page's `validate` refuses it; [reason] is the page's. */
    data class Invalid(val reason: String) : StoreRefusal

    /** An owned field holds a JSON kind the page never writes there. */
    data class WrongType(val path: String, val found: JsonKind) : StoreRefusal

    /** A NaN or infinity the page would write as `null`, changing the value (at [path]). */
    data class NonFinite(val path: String) : StoreRefusal

    /** The written text does not read back as the state that was meant ([what] differs). */
    data class NotFaithful(val what: String) : StoreRefusal
}

/** A stored state read: the [document] (the page's in-memory state), the [text] it came from. */
class DecodedState(val document: JsonObject, val text: String, val migratedFrom: Double?) {
    /** The owned fields native reads directly; everything else stays in [document] untouched. */
    val playerId: String?
        get() = (document["playerId"] as? JsonString)?.value

    val timezone: String?
        get() = ((document["availability"] as? JsonObject)?.get("timezone") as? JsonString)?.value

    val setupComplete: Boolean
        get() = (document["setupComplete"] as? JsonBool)?.value ?: false

    val updatedEpoch: Double?
        get() = (document["updatedEpoch"] as? JsonNumber)?.value
}

sealed interface StoreDecode {
    data class Decoded(val state: DecodedState) : StoreDecode

    data class Refused(val refusal: StoreRefusal) : StoreDecode
}

sealed interface StoreEncode {
    /** [text] is what to store; [document] is that text read back. */
    data class Encoded(val text: String, val document: JsonObject) : StoreEncode

    data class Refused(val refusal: StoreRefusal) : StoreEncode
}

/**
 * The JSON kinds the page writes in each field native will own (LIFE_DIRECTOR_STORE_SPEC.md
 * LST-27): `defaultState`'s fields, the ones logic.js adds (`hostSync`, `fixes`, `lastRejected`,
 * `prefsLightUntil`, `prefsLightRestore`) and `migratedFrom`. A field may be missing; `null` is
 * accepted where a number is, because `JSON.stringify(NaN)` writes it. The contents of `schedule`,
 * `ledger`, `goals` and the rest belong to the engine packages and are carried as they are.
 */
object OwnedFields {
    private val NUMBER = setOf(JsonKind.NUMBER, JsonKind.NULL)
    private val STRING = setOf(JsonKind.STRING)
    private val OBJECT = setOf(JsonKind.OBJECT)
    private val ARRAY = setOf(JsonKind.ARRAY)
    private val OBJECT_OR_NULL = setOf(JsonKind.OBJECT, JsonKind.NULL)
    private val STRING_OR_NULL = setOf(JsonKind.STRING, JsonKind.NULL)

    val TOP: Map<String, Set<JsonKind>> =
        linkedMapOf(
            "schemaVersion" to NUMBER,
            "playerId" to STRING,
            "createdEpoch" to NUMBER,
            "updatedEpoch" to NUMBER,
            "setupComplete" to setOf(JsonKind.BOOLEAN),
            "availability" to OBJECT,
            "fixedCommitments" to ARRAY,
            "goals" to ARRAY,
            "prefs" to OBJECT,
            "schedule" to OBJECT_OR_NULL,
            "ledger" to OBJECT_OR_NULL,
            "changeLog" to ARRAY,
            "energyCheckin" to OBJECT_OR_NULL,
            "questOverlay" to OBJECT,
            "labQuests" to ARRAY,
            "bridge" to OBJECT,
            "lastGeneratedWeekKey" to STRING_OR_NULL,
            "ui" to OBJECT,
            "hostSync" to OBJECT,
            "fixes" to OBJECT,
            "migratedFrom" to NUMBER,
            "lastRejected" to OBJECT_OR_NULL,
            "prefsLightUntil" to NUMBER,
            "prefsLightRestore" to STRING_OR_NULL,
        )

    val AVAILABILITY: Map<String, Set<JsonKind>> =
        linkedMapOf(
            "timezone" to STRING,
            "sleepRequirementMinutes" to NUMBER,
            "rules" to ARRAY,
            "blackouts" to ARRAY,
        )

    /** The first owned field holding a kind outside its set, as a refusal, or null. */
    fun check(state: JsonObject): StoreRefusal.WrongType? {
        for ((field, kinds) in TOP) {
            val value = state[field] ?: continue
            if (value.kind !in kinds) return StoreRefusal.WrongType(field, value.kind)
        }
        val availability = state["availability"] as? JsonObject ?: return null
        for ((field, kinds) in AVAILABILITY) {
            val value = availability[field] ?: continue
            if (value.kind !in kinds)
                return StoreRefusal.WrongType("availability.$field", value.kind)
        }
        return null
    }
}

/**
 * The native codec for `lifemax_director_state_v1` (WP-LD7), on core/save's preserving JSON: decode
 * reads a stored text as the page would (`JSON.parse`, `migrate`, `validate`) and, by default, also
 * checks the owned fields' kinds; encode writes a state with every number's own text kept and reads
 * its own output back before handing it out. Never throws.
 */
object DirectorStoreCodec {
    fun decode(text: String, reading: StoreReading = StoreReading.STRICT): StoreDecode {
        val parsed =
            when (val json = JsonReader.read(text)) {
                is JsonRead.Parsed -> json.value
                is JsonRead.Malformed ->
                    return StoreDecode.Refused(
                        if (DirectorStore.isTooDeep(json)) StoreRefusal.TooDeep
                        else StoreRefusal.NotJson(json.offset, json.reason)
                    )
            }
        val before =
            if (JsValues.truthy(parsed)) JsValues.member(parsed, "schemaVersion") else parsed
        val state =
            when (val m = StoreSchema.migrate(parsed)) {
                is Migration.Threw ->
                    return StoreDecode.Refused(StoreRefusal.PageWouldThrow(m.name))
                is Migration.Done -> m.value
            }
        StoreSchema.validate(state)?.let {
            return StoreDecode.Refused(StoreRefusal.Invalid(it))
        }
        if (state !is JsonObject) return StoreDecode.Refused(StoreRefusal.Invalid(NOT_A_RECORD))
        if (reading == StoreReading.STRICT)
            OwnedFields.check(state)?.let {
                return StoreDecode.Refused(it)
            }
        val migratedFrom =
            when {
                JsValues.isNumber(before, StoreKeys.SCHEMA_VERSION) -> null
                JsValues.isNullish(before) -> 0.0
                else -> (before as JsonNumber).value
            }
        return StoreDecode.Decoded(DecodedState(state, text, migratedFrom))
    }

    /**
     * The text for [document]. When it is the [stored] state unchanged, the stored text itself;
     * otherwise the page's layout with every number's own text, refused when it holds a NaN or
     * infinity, when the page or the owned-field check would refuse it, or when the text does not
     * read back as the same state with the same number texts.
     */
    fun encode(document: JsonValue, stored: DecodedState? = null): StoreEncode {
        val text = PageJson.preserving(document)
        if (stored != null && text == PageJson.preserving(stored.document))
            return StoreEncode.Encoded(stored.text, stored.document)
        nonFinite(document, "")?.let {
            return StoreEncode.Refused(StoreRefusal.NonFinite(it))
        }
        StoreSchema.validate(document)?.let {
            return StoreEncode.Refused(StoreRefusal.Invalid(it))
        }
        if (document !is JsonObject) return StoreEncode.Refused(StoreRefusal.Invalid(NOT_A_RECORD))
        OwnedFields.check(document)?.let {
            return StoreEncode.Refused(it)
        }
        val back =
            when (val read = decode(text)) {
                is StoreDecode.Decoded -> read.state.document
                is StoreDecode.Refused ->
                    return StoreEncode.Refused(StoreRefusal.NotFaithful(READ_BACK))
            }
        if (!back.sameStoredValue(document))
            return StoreEncode.Refused(StoreRefusal.NotFaithful(VALUES))
        if (PageJson.preserving(back) != text)
            return StoreEncode.Refused(StoreRefusal.NotFaithful(TEXTS))
        return StoreEncode.Encoded(text, back)
    }

    /** The path of the first NaN or infinity in [value] (`a.b[2]`), or null. */
    private fun nonFinite(value: JsonValue, path: String): String? =
        when (value) {
            is JsonNumber -> if (value.value.isFinite()) null else path.ifEmpty { "." }
            is JsonObject ->
                value.members.entries.firstNotNullOfOrNull { (k, v) ->
                    nonFinite(v, if (path.isEmpty()) k else "$path.$k")
                }
            is JsonArray ->
                value.items.withIndex().firstNotNullOfOrNull { (i, v) -> nonFinite(v, "$path[$i]") }
            else -> null
        }

    private const val NOT_A_RECORD = "a list, not an object"
    private const val READ_BACK = "the text is not read back"
    private const val VALUES = "values"
    private const val TEXTS = "number texts"
}
