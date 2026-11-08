package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.EconomySave

/**
 * A stored save as decoded: the whole JSON as stored (every field, tracked or not), its text when
 * it was read from one, and what the economy model wrote for it then. Encoding writes a changed
 * [EconomySave] over this, changing only what the economy changed.
 */
class SaveDocument
internal constructor(
    /** The stored save, every key included. */
    val stored: JsonObject,
    /** The text it was read from, or null when it was given as JSON. */
    val text: String?,
    /** How its tracked fields were read; encoding reads its own output back the same way. */
    val reading: SaveReading,
    /** The tracked fields as the model wrote them at decoding (the merge's `before`). */
    internal val written: JsonObject,
)

/** What decoding a stored save gave. */
sealed interface SaveDecoding {
    /** The economy's view of the save, and the document to encode changes against. */
    data class Decoded(val save: EconomySave, val document: SaveDocument) : SaveDecoding

    /** The save cannot be used; it is untouched, and [refusal] says why. */
    data class Refused(val refusal: SaveRefusal) : SaveDecoding
}

/** What encoding a changed save gave. */
sealed interface SaveEncoding {
    /**
     * The text to store, and the document it is (to encode the next change against). When the
     * economy changed nothing, [text] is the stored text itself, byte for byte.
     */
    data class Encoded(val text: String, val document: SaveDocument) : SaveEncoding

    /** Nothing may be written; the stored save stays as it was, and [refusal] says why. */
    data class Refused(val refusal: SaveRefusal) : SaveEncoding
}

/**
 * The one place that turns the page's stored save (`lifemax_rpg_prototype_v1`) into an
 * [EconomySave] and back (ECONOMY_SPEC section 2, SAVE-1, SAVE-5).
 *
 * Guarantees:
 * - Nothing native does not model is lost or altered: every key outside [SaveFields.TRACKED], every
 *   nested key outside [SaveFields.PARTIAL], every record's own extra keys, key presence and
 *   absence, `null`, number text (`1`, `1.0`, `-0`, `1e400`) and string content come back as
 *   stored. Only values the economy changed are rewritten, through [JsonMerge].
 * - `encode(decode(x), unchanged)` is `x`, byte for byte, and decoding is a pure function of its
 *   input, so decoding twice gives equal results and encoding the same change twice gives the same
 *   text.
 * - No exception escapes: a save that cannot be read safely, or a change that cannot be written
 *   faithfully, is a typed [SaveRefusal], and the caller keeps the stored save as it is.
 * - Before handing back a text, [encode] reads it back and checks that it holds exactly the economy
 *   it was given, and every untracked top-level field exactly as stored; otherwise it refuses
 *   ([SaveRefusalReason.NOT_FAITHFUL]).
 *
 * Under [SaveReading.STRICT] a NaN or infinite number the economy introduced is refused; under
 * [SaveReading.AS_PAGE] it is written as `null`, as `JSON.stringify` does.
 */
object EconomySaveCodec {
    /** Decodes the stored save text. */
    fun decode(text: String, reading: SaveReading = SaveReading.STRICT): SaveDecoding =
        guarded({ SaveDecoding.Refused(it) }) {
            when (val read = JsonReader.read(text)) {
                is JsonRead.Malformed ->
                    decodingRefused(
                        SaveRefusalReason.NOT_JSON,
                        SaveProblem("", "", "JSON", "${read.reason} at ${read.offset}"),
                    )
                is JsonRead.Parsed -> decodeValue(read.value, text, reading)
            }
        }

    /** Decodes a stored save already parsed as JSON. */
    fun decode(json: JsonValue, reading: SaveReading = SaveReading.STRICT): SaveDecoding =
        guarded({ SaveDecoding.Refused(it) }) { decodeValue(json, null, reading) }

    /** Writes [save], a changed version of the economy [document] was decoded into. */
    fun encode(document: SaveDocument, save: EconomySave): SaveEncoding =
        guarded({ SaveEncoding.Refused(it) }) { encodeChecked(document, save) }

    /**
     * The stored save with [save]'s changes merged in, exactly as [encode] lays them before it
     * writes the text and reads it back: nothing is written, nothing is checked, and every value
     * native did not change is the very [SaveDocument.stored] instance it was. For a comparison
     * that needs the outcome quickly (the economy shadow); a writer must use [encode]. Null when
     * the merge fails.
     */
    fun merged(document: SaveDocument, save: EconomySave): JsonObject? =
        try {
            JsonMerge().merge(document.stored, document.written, EconomySaveWriter.write(save), "")
                as? JsonObject
        } catch (_: RuntimeException) {
            null
        } catch (_: StackOverflowError) {
            null
        }

    private fun decodeValue(json: JsonValue, text: String?, reading: SaveReading): SaveDecoding {
        if (json !is JsonObject)
            return decodingRefused(
                SaveRefusalReason.NOT_AN_OBJECT,
                SaveProblem("", "", "object", json.kind.name),
            )
        val reads = PageReads(reading)
        val save = EconomySaveReader(reads).save(json)
        if (reads.problems.isNotEmpty())
            return SaveDecoding.Refused(SaveRefusal(SaveRefusalReason.WRONG_TYPE, reads.problems))
        val document = SaveDocument(json, text, reading, EconomySaveWriter.write(save))
        return SaveDecoding.Decoded(save, document)
    }

    private fun encodeChecked(document: SaveDocument, save: EconomySave): SaveEncoding {
        val after = EconomySaveWriter.write(save)
        if (after == document.written && document.text != null)
            return SaveEncoding.Encoded(document.text, document)
        val merge = JsonMerge()
        val merged = merge.merge(document.stored, document.written, after, "") as JsonObject
        if (merge.nonFinite.isNotEmpty() && document.reading == SaveReading.STRICT)
            return encodingRefused(
                SaveRefusalReason.NON_FINITE_NUMBER,
                merge.nonFinite.map { SaveProblem(fieldOf(it), "", "finite number", "NaN") },
            )
        val text = JsonWriter.write(merged)
        val reread =
            when (val again = decode(text, document.reading)) {
                is SaveDecoding.Decoded -> again
                is SaveDecoding.Refused -> return SaveEncoding.Refused(again.refusal)
            }
        val unfaithful = unfaithful(document, after, reread, merge.nonFinite.toSet())
        if (unfaithful.isNotEmpty())
            return encodingRefused(SaveRefusalReason.NOT_FAITHFUL, *unfaithful.toTypedArray())
        return SaveEncoding.Encoded(text, reread.document)
    }

    /**
     * What reading the written text back shows wrong: an economy value that differs from what was
     * to be written (except where a NaN became null, which only [SaveReading.AS_PAGE] allows), or
     * an untracked top-level field that is missing, added or different.
     */
    private fun unfaithful(
        document: SaveDocument,
        after: JsonObject,
        reread: SaveDecoding.Decoded,
        nulled: Set<String>,
    ): List<SaveProblem> {
        val economy = mutableListOf<String>()
        differences(reread.document.written, after, "", economy)
        val problems =
            economy
                .filterNot { it in nulled }
                .map { SaveProblem(fieldOf(it), "", "the value written", "a different value") }
        val stored = document.stored.members
        val written = reread.document.stored.members
        val untracked = (stored.keys + written.keys).filterNot { it in SaveFields.TRACKED }
        return problems +
            untracked
                .filterNot { stored[it] == written[it] }
                .map { SaveProblem(it, "", "kept as stored", "changed") }
    }

    /** Every path where [a] and [b] differ, as [JsonMerge] names paths. */
    private fun differences(a: JsonValue?, b: JsonValue?, path: String, out: MutableList<String>) {
        when {
            a == b -> Unit
            a is JsonObject && b is JsonObject ->
                (a.members.keys + b.members.keys).forEach {
                    differences(a[it], b[it], "$path.$it", out)
                }
            a is JsonArray && b is JsonArray && a.items.size == b.items.size ->
                a.items.indices.forEach { differences(a.items[it], b.items[it], "$path[$it]", out) }
            a is JsonNumber && b is JsonNumber && a.value == b.value -> Unit // -0 is written 0
            else -> out += path
        }
    }

    /** The tracked field a merge path starts with (`.activities[3].xp` is `activities`). */
    private fun fieldOf(path: String): String =
        path.removePrefix(".").takeWhile { it != '.' && it != '[' }

    private fun decodingRefused(reason: SaveRefusalReason, vararg problems: SaveProblem) =
        SaveDecoding.Refused(SaveRefusal(reason, problems.toList()))

    private fun encodingRefused(reason: SaveRefusalReason, vararg problems: SaveProblem) =
        SaveEncoding.Refused(SaveRefusal(reason, problems.toList()))

    private fun encodingRefused(reason: SaveRefusalReason, problems: List<SaveProblem>) =
        SaveEncoding.Refused(SaveRefusal(reason, problems))

    /**
     * Runs [work]; an unexpected exception becomes an [SaveRefusalReason.INTERNAL] refusal naming
     * only its class (its message could quote the save).
     */
    private inline fun <T> guarded(refuse: (SaveRefusal) -> T, work: () -> T): T =
        try {
            work()
        } catch (e: RuntimeException) {
            refuse(internal(e))
        } catch (e: StackOverflowError) {
            refuse(internal(e))
        }

    private fun internal(e: Throwable) =
        SaveRefusal(SaveRefusalReason.INTERNAL, listOf(SaveProblem("", "", "", e.javaClass.name)))
}
