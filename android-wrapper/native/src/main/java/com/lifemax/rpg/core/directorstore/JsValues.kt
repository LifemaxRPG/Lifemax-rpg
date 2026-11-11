package com.lifemax.rpg.core.directorstore

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.jsNumberText
import java.math.BigInteger

/**
 * The few JavaScript coercions the page's store applies to stored values, over [JsonValue]
 * (LIFE_DIRECTOR_STORE_SPEC.md LST-12, LST-14). JavaScript's `undefined` (a member that is not
 * there) is Kotlin `null` throughout; JSON `null` is [JsonNull].
 */
object JsValues {
    /** `obj.key` for the members the store reads: an object's own member, else `undefined`. */
    fun member(value: JsonValue?, key: String): JsonValue? = (value as? JsonObject)?.get(key)

    /** JavaScript truthiness: `undefined`, `null`, `false`, `0`, `-0`, `NaN` and `""` are false. */
    fun truthy(value: JsonValue?): Boolean =
        when (value) {
            null,
            JsonNull -> false
            is JsonBool -> value.value
            is JsonNumber -> !(value.value == 0.0 || value.value.isNaN())
            is JsonString -> value.value.isNotEmpty()
            is JsonArray,
            is JsonObject -> true
        }

    /** `value == null` (loose): `undefined` or `null`. */
    fun isNullish(value: JsonValue?): Boolean = value == null || value == JsonNull

    /** `value === n` for a number `n`: only a number of that value (`-0` equals `0`). */
    fun isNumber(value: JsonValue?, n: Double): Boolean = value is JsonNumber && value.value == n

    /**
     * ToString: `"undefined"`, `"null"`, `"true"`, the number as `Number.prototype.toString` writes
     * it (`NaN`, `Infinity`, `-0` as `"0"`), the string itself, an array's items joined by `,`
     * (`null` items empty), `"[object Object]"`.
     */
    fun text(value: JsonValue?): String =
        when (value) {
            null -> "undefined"
            JsonNull -> "null"
            is JsonBool -> if (value.value) "true" else "false"
            is JsonNumber -> numberText(value.value)
            is JsonString -> value.value
            is JsonArray -> value.items.joinToString(",") { if (it == JsonNull) "" else text(it) }
            is JsonObject -> OBJECT_TEXT
        }

    /**
     * `Number.prototype.toString()` for any double (`NaN`, `Infinity` and `-Infinity` included).
     */
    fun numberText(n: Double): String =
        when {
            n.isNaN() -> "NaN"
            n == Double.POSITIVE_INFINITY -> "Infinity"
            n == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> jsNumberText(n)
        }

    /**
     * ToNumber: `undefined` NaN, `null` 0, booleans 1 and 0, a string by StringToNumber, an array
     * or object through its text (ToPrimitive gives [text] for both).
     */
    fun number(value: JsonValue?): Double =
        when (value) {
            null -> Double.NaN
            JsonNull -> 0.0
            is JsonBool -> if (value.value) 1.0 else 0.0
            is JsonNumber -> value.value
            is JsonString -> stringNumber(value.value)
            is JsonArray,
            is JsonObject -> stringNumber(text(value))
        }

    /**
     * StringToNumber (ECMA-262 7.1.4.1.1): white space and line terminators trimmed; empty is 0;
     * `Infinity` with an optional sign; `0x`, `0o`, `0b` integers without a sign; a decimal literal
     * with optional sign, point and exponent (`5.`, `.5` and leading zeros allowed, no `_`);
     * anything else NaN. Values are correctly rounded, as in JavaScript.
     */
    fun stringNumber(raw: String): Double {
        val s = raw.trim(::isJsSpace)
        if (s.isEmpty()) return 0.0
        when (s) {
            "Infinity",
            "+Infinity" -> return Double.POSITIVE_INFINITY
            "-Infinity" -> return Double.NEGATIVE_INFINITY
        }
        if (s.length > 2 && s[0] == '0') {
            val radix =
                when (s[1]) {
                    'x',
                    'X' -> HEX
                    'o',
                    'O' -> OCTAL
                    'b',
                    'B' -> BINARY
                    else -> 0
                }
            if (radix != 0) {
                val digits = s.substring(2)
                return if (digits.all { Character.digit(it, radix) >= 0 && it.code < ASCII_END })
                    BigInteger(digits, radix).toDouble()
                else Double.NaN
            }
        }
        return if (DECIMAL.matches(s)) s.toDouble() else Double.NaN
    }

    /**
     * WhiteSpace and LineTerminator of ECMA-262 (Unicode Zs, TAB, VT, FF, ZWNBSP, LF, CR, LS, PS).
     */
    fun isJsSpace(c: Char): Boolean =
        c == '\t' ||
            c == '\u000B' ||
            c == '\u000C' ||
            c == ' ' ||
            c == '\u00A0' ||
            c == '\uFEFF' ||
            c == '\n' ||
            c == '\r' ||
            c == '\u2028' ||
            c == '\u2029' ||
            c == '\u1680' ||
            c in '\u2000'..'\u200A' ||
            c == '\u202F' ||
            c == '\u205F' ||
            c == '\u3000'

    /**
     * An array index: the canonical text of an integer from 0 to 2^32 - 2. An object's own keys
     * come in JavaScript in this order: array indexes ascending, then the other keys as they were
     * added (OrdinaryOwnPropertyKeys), which is the order `JSON.stringify` writes them in.
     */
    fun isArrayIndex(key: String): Boolean {
        if (key.isEmpty() || key.length > MAX_INDEX_DIGITS) return false
        if (key == "0") return true
        if (key[0] !in '1'..'9' || !key.all { it in '0'..'9' }) return false
        return key.toLong() <= MAX_ARRAY_INDEX
    }

    private const val OBJECT_TEXT = "[object Object]"
    private const val HEX = 16
    private const val OCTAL = 8
    private const val BINARY = 2
    private const val ASCII_END = 128
    private const val MAX_INDEX_DIGITS = 10
    private const val MAX_ARRAY_INDEX = 4294967294L
    private val DECIMAL = Regex("[+-]?([0-9]+\\.?[0-9]*|\\.[0-9]+)([eE][+-]?[0-9]+)?")
}
