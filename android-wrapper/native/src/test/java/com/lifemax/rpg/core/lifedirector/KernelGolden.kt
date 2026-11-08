package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter

/**
 * The kernel's golden cases, `ld-kernel-golden.json` (recorded from the page in Chromium; format in
 * `tools/gen_ld_kernel_golden.js`), read with the save codec's reader, and the JSON shapes the page
 * writes. `{"$num": "NaN"}` and its kin stand for the numbers JSON cannot hold; [decode] turns them
 * into the numbers, so a case compares with [JsonValue] equality (doubles bit for bit).
 */
object KernelGolden {
    const val FILE = "ld-kernel-golden.json"

    /** One recorded call: [fn] with [args] gave [out]. */
    class Case(
        val id: String,
        val family: String,
        val rule: String,
        val fn: String,
        val args: List<JsonValue>,
        val out: JsonValue,
    )

    private val file: JsonObject by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        read.value as JsonObject
    }

    /** The versions the cases were recorded with. */
    val header: JsonObject by lazy { file["header"] as JsonObject }

    /** Every case, in the file's order. */
    val cases: List<Case> by lazy {
        (file["cases"] as JsonArray).items.map {
            val c = it as JsonObject
            Case(
                text(c["id"]),
                text(c["family"]),
                text(c["rule"]),
                text(c["fn"]),
                (c["args"] as JsonArray).items,
                decode(c["out"]!!),
            )
        }
    }

    private val NUMBERS =
        mapOf(
            "NaN" to Double.NaN,
            "Infinity" to Double.POSITIVE_INFINITY,
            "-Infinity" to Double.NEGATIVE_INFINITY,
            "-0" to -0.0,
        )

    /** [value] with every `{"$num": ...}` replaced by its number. */
    fun decode(value: JsonValue): JsonValue =
        when (value) {
            is JsonObject ->
                (value["\$num"] as? JsonString)?.let { JsonNumber(NUMBERS.getValue(it.value)) }
                    ?: JsonObject(value.members.mapValues { decode(it.value) })
            is JsonArray -> JsonArray(value.items.map(::decode))
            else -> value
        }

    fun text(value: JsonValue?): String = (value as JsonString).value

    fun number(value: Double): JsonValue = JsonNumber(value)

    fun number(value: Int): JsonValue = JsonNumber(value.toDouble())

    fun string(value: String): JsonValue = JsonString(value)

    fun bool(value: Boolean): JsonValue = JsonBool.of(value)

    fun obj(vararg members: Pair<String, JsonValue>): JsonObject = JsonObject(linkedMapOf(*members))

    fun array(values: List<JsonValue>): JsonArray = JsonArray(values)

    fun threw(name: String): JsonObject = obj("threw" to string(name))

    /** Compact JSON for messages (NaN shows as null, as the writer writes it). */
    fun show(value: JsonValue): String = JsonWriter.write(value).take(SHOWN)

    private const val SHOWN = 400
}
