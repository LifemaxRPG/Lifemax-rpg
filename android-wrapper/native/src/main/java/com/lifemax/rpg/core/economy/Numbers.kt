package com.lifemax.rpg.core.economy

import kotlin.math.abs

/*
 * Numbers follow ECONOMY_SPEC section 0.1: every amount is an IEEE-754 double (NUM-1), evaluated
 * in the order the spec writes it, so `x * 0.7` stays `x * 0.7`. A save can hold anything, so a
 * missing or malformed number arrives here as NaN (NUM-3) and the rules decide, case by case, what
 * NaN becomes.
 */

/** `round` (NUM-2): the nearest integer, ties toward positive infinity. */
internal fun jsRound(value: Double): Double =
    if (value.isNaN() || abs(value) >= 4.503599627370496E15) value else Math.round(value).toDouble()

/** `num(v) || 0` (NUM-3): NaN becomes 0. */
internal val Double.orZero: Double
    get() = if (isNaN()) 0.0 else this

/** `num(v) || fallback` (NUM-3): NaN and 0 become [fallback]. */
internal fun Double.or(fallback: Double): Double = if (isNaN() || this == 0.0) fallback else this

/** A double that holds a whole number, for messages: `24.0` reads `24`, as the page writes it. */
internal fun Double.text(): String =
    if (this == Math.rint(this) && abs(this) < 1e15) toLong().toString() else toString()

/** `num` of a text (NUM-3): blank is 0, anything that is not a numeric literal is NaN. */
fun jsNumber(text: String): Double {
    val s = text.jsTrim()
    if (s.isEmpty()) return 0.0
    DECIMAL.matchEntire(s)?.let {
        return s.toDouble()
    }
    INFINITY.matchEntire(s)?.let {
        return if (s.startsWith("-")) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
    }
    RADIX.matchEntire(s)?.let { match ->
        val (radix, digits) =
            when (match.groupValues[1].lowercase()) {
                "x" -> 16 to match.groupValues[2]
                "o" -> 8 to match.groupValues[2]
                else -> 2 to match.groupValues[2]
            }
        if (digits.any { Character.digit(it, radix) < 0 }) return Double.NaN
        return digits.toBigInteger(radix).toDouble()
    }
    return Double.NaN
}

/**
 * `String.prototype.trim()`: JavaScript's white space and line terminators, which are not Kotlin's
 * (no `U+001C`..`U+001F`, but `U+00A0` and `U+FEFF`).
 */
internal fun String.jsTrim(): String = trim { c ->
    c in '\u0009'..'\u000D' ||
        c == ' ' ||
        c == '\u00A0' ||
        c == '\u1680' ||
        c in '\u2000'..'\u200A' ||
        c == '\u2028' ||
        c == '\u2029' ||
        c == '\u202F' ||
        c == '\u205F' ||
        c == '\u3000' ||
        c == '\uFEFF'
}

private val DECIMAL = Regex("""[+-]?(\d+\.?\d*|\.\d+)([eE][+-]?\d+)?""")
private val INFINITY = Regex("""[+-]?Infinity""")
private val RADIX = Regex("""0([xXoObB])([0-9a-fA-F]+)""")
