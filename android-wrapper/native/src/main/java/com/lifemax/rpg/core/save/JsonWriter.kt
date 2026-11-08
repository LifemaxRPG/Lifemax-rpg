package com.lifemax.rpg.core.save

/**
 * Writes JSON text as the page's `JSON.stringify(value)` does (no indentation), so a save native
 * writes reads back on the page exactly as the page's own would.
 * - Strings: `"` and `\` escaped, `\b \f \n \r \t` by name, other units below U+0020 as `\u00xx`,
 *   lone surrogates as `\udxxx` (well-formed `JSON.stringify`), everything else as it is.
 * - Numbers read from a text keep that text ([JsonNumber.lexeme]). A computed number is written as
 *   JavaScript's `Number.prototype.toString` gives it ([jsNumberText]); `-0` is written `0`, and
 *   NaN and the infinities `null`, as `JSON.stringify` does.
 * - Object members in their order.
 */
object JsonWriter {
    fun write(value: JsonValue): String = StringBuilder().also { append(it, value) }.toString()

    private fun append(out: StringBuilder, value: JsonValue) {
        when (value) {
            is JsonObject -> {
                out.append('{')
                var first = true
                for ((key, member) in value.members) {
                    if (!first) out.append(',')
                    first = false
                    appendString(out, key)
                    out.append(':')
                    append(out, member)
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                value.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            is JsonString -> appendString(out, value.value)
            is JsonNumber -> out.append(value.lexeme ?: jsNumberText(value.value))
            is JsonBool -> out.append(if (value.value) "true" else "false")
            JsonNull -> out.append("null")
        }
    }

    private fun appendString(out: StringBuilder, s: String) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append(unitEscape(c))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    out.append(c).append(s[i + 1])
                    i++
                }
                c.isSurrogate() -> out.append(unitEscape(c))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }

    private fun unitEscape(c: Char): String = "\\u" + c.code.toString(16).padStart(4, '0')
}

/**
 * `Number.prototype.toString()` for a double (ECMA-262 Number::toString, radix 10), and what
 * `JSON.stringify` writes for it: NaN and the infinities are `null`, `-0` is `0`.
 *
 * The digits are the shortest that read back as the same double, the closest such when there is a
 * choice. They come from `Double.toString`, which gives the shortest digits on Java 19 and later
 * except that it may give two digits where one would do (`4.9E-324` for `5e-324`); the one-digit
 * candidates are tried first, so the result is JavaScript's. Then the layout: plain digits up to 21
 * integer digits, `0.000ddd` down to 1e-7, else `d.ddde+n` / `d.ddde-n`.
 */
fun jsNumberText(value: Double): String {
    if (!value.isFinite()) return "null"
    if (value == 0.0) return "0"
    val negative = value < 0
    val (digits, pointAt) = shortestDigits(Math.abs(value))
    val k = digits.length
    val n = pointAt
    val body =
        when {
            n in k..21 -> digits + "0".repeat(n - k)
            n in 1..21 -> digits.substring(0, n) + "." + digits.substring(n)
            n in -5..0 -> "0." + "0".repeat(-n) + digits
            else -> {
                val e = n - 1
                val exponent = if (e >= 0) "e+$e" else "e$e"
                if (k == 1) digits + exponent else digits[0] + "." + digits.substring(1) + exponent
            }
        }
    return if (negative) "-$body" else body
}

/**
 * The shortest decimal digits `d1..dk` (no leading or trailing zeros) and the point position `n`
 * such that `0.d1..dk × 10^n` reads back as [positive].
 */
private fun shortestDigits(positive: Double): Pair<String, Int> {
    val (javaDigits, javaPoint) = decimalOf(positive.toString())
    if (javaDigits.length == 2)
        oneDigit(positive, javaDigits, javaPoint)?.let {
            return it
        }
    return javaDigits to javaPoint
}

/** Splits Java's `1.234E-5` or `123.45` into digits without zeros at either end and `n`. */
private fun decimalOf(text: String): Pair<String, Int> {
    val e = text.indexOfFirst { it == 'E' }
    val mantissa = if (e < 0) text else text.substring(0, e)
    val exponent = if (e < 0) 0 else text.substring(e + 1).toInt()
    val dot = mantissa.indexOf('.')
    val intPart = if (dot < 0) mantissa else mantissa.substring(0, dot)
    val fraction = if (dot < 0) "" else mantissa.substring(dot + 1)
    val all = intPart + fraction
    val leading = all.indexOfFirst { it != '0' }
    val trimmed = all.substring(leading).trimEnd('0')
    return trimmed to (intPart.length - leading + exponent)
}

/**
 * When two digits came back, a single digit may also read back as the same double (Java keeps a
 * second digit when it is closer); JavaScript takes the shortest, so try both one-digit neighbours
 * and keep the closer one that reads back, the even one on a tie. The distances are compared
 * exactly (BigDecimal); this only chooses between two texts, it never computes a value.
 */
private fun oneDigit(value: Double, two: String, point: Int): Pair<String, Int>? {
    val first = two[0] - '0'
    val candidates =
        listOf(first, first + 1).mapNotNull { d ->
            if (d == 10) ("1" to point + 1) else if (d == 0) null else (d.toString() to point)
        }
    val exact = java.math.BigDecimal(value)
    val reading = candidates.filter { (d, n) -> "${d}E${n - 1}".toDouble() == value }
    return reading.minWithOrNull(
        compareBy<Pair<String, Int>> { (d, n) ->
                java.math.BigDecimal("${d}E${n - 1}").subtract(exact).abs()
            }
            .thenBy { (d, _) -> (d[0] - '0') % 2 }
    )
}
