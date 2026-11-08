package com.lifemax.rpg.core.economy

import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.EconomySaveReader
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.PageReads
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveDocument
import com.lifemax.rpg.core.save.SaveEncoding
import com.lifemax.rpg.core.save.SaveFields
import com.lifemax.rpg.core.save.SaveReading

/**
 * The golden cases' view of the production codec ([EconomySaveCodec]): recorded states are plain
 * values (maps, lists, doubles), so this only converts between those and [JsonValue]. The cases
 * include deliberately malformed saves, which the page reads by its own rules, so they are decoded
 * with [SaveReading.AS_PAGE]; `SaveCodecGoldenTest` checks which of them [SaveReading.STRICT]
 * refuses.
 */
internal object SaveCodec {
    /** A field that is not in the save. */
    data object Absent

    val TRACKED: List<String> = SaveFields.TRACKED

    fun document(raw: Map<String, Any?>): SaveDocument =
        when (val d = EconomySaveCodec.decode(json(raw), SaveReading.AS_PAGE)) {
            is SaveDecoding.Decoded -> d.document
            is SaveDecoding.Refused -> error("the page's reading refused a save: ${d.refusal}")
        }

    fun decode(raw: Map<String, Any?>): EconomySave =
        when (val d = EconomySaveCodec.decode(json(raw), SaveReading.AS_PAGE)) {
            is SaveDecoding.Decoded -> d.save
            is SaveDecoding.Refused -> error("the page's reading refused a save: ${d.refusal}")
        }

    /** Every tracked field of [save] written over [document], [Absent] where the save lacks it. */
    fun encode(save: EconomySave, document: SaveDocument): Map<String, Any?> {
        val stored =
            when (val e = EconomySaveCodec.encode(document, save)) {
                is SaveEncoding.Encoded -> e.document.stored
                is SaveEncoding.Refused -> error("encoding refused: ${e.refusal}")
            }
        return TRACKED.associateWith { key ->
            stored[key]?.let(::plain) ?: Absent.takeIf { key !in stored }
        }
    }

    /** A record given as a call argument, read as the page reads a stored one. */
    fun activity(raw: Map<String, Any?>): Activity =
        EconomySaveReader(PageReads(SaveReading.AS_PAGE))
            .activity((json(raw) as JsonObject).members, PageReads.At("activities"))

    fun json(plain: Any?): JsonValue =
        when (plain) {
            null -> JsonNull
            is String -> JsonString(plain)
            is Boolean -> JsonBool.of(plain)
            is Number -> JsonNumber(plain.toDouble())
            is Map<*, *> -> JsonObject(plain.entries.associate { (k, v) -> k as String to json(v) })
            is List<*> -> JsonArray(plain.map(::json))
            else -> error("not a JSON value: ${plain.javaClass}")
        }

    fun plain(json: JsonValue): Any? =
        when (json) {
            JsonNull -> null
            is JsonString -> json.value
            is JsonBool -> json.value
            is JsonNumber -> json.value
            is JsonObject -> json.members.mapValuesTo(linkedMapOf()) { plain(it.value) }
            is JsonArray -> json.items.map(::plain)
        }
}
