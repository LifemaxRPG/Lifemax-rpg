package com.lifemax.rpg.core.quests

import java.math.BigDecimal
import java.math.BigInteger

/**
 * The Daily Hand's hashing and random numbers (QUESTS_SPEC QST-32, QST-33), on 32-bit integers
 * exactly as the page computes them: Kotlin's `Int` multiplication keeps the low 32 bits of the
 * product (`Math.imul`), `ushr` is `>>>`, and the unsigned result is divided by 2^32 in binary64.
 */
object HandRandom {
    /** FNV-1a's offset basis, 2166136261. */
    const val FNV_OFFSET: Int = 0x811C9DC5.toInt()

    /** FNV-1a's 32-bit prime. */
    const val FNV_PRIME: Int = 16777619

    /** Mulberry32's state step. */
    const val MULBERRY_STEP: Int = 0x6D2B79F5

    /** 2^32: the unsigned 32-bit output becomes a double in [0, 1). */
    const val UINT32_RANGE: Double = 4294967296.0

    /**
     * `dhSeed(text)`: FNV-1a over the UTF-16 code units of [text] (a Kotlin `Char` is one unit, so
     * a lone surrogate counts as itself), as an unsigned 32-bit value.
     */
    fun seed(text: String): Long {
        var h = FNV_OFFSET
        for (unit in text) h = (h xor unit.code) * FNV_PRIME
        return h.toLong() and UINT32_MASK
    }

    /** `seed >>> 0` for any double, as JavaScript's ToUint32 does (NaN and infinities are 0). */
    fun toUint32(value: Double): Long {
        if (!value.isFinite()) return 0
        val whole = if (value < 0) Math.ceil(value) else Math.floor(value)
        return BigDecimal(whole).toBigInteger().mod(UINT32_MODULUS).toLong()
    }

    private val UINT32_MODULUS: BigInteger = BigInteger.ONE.shiftLeft(32)

    private const val UINT32_MASK = 0xFFFFFFFFL
}

/**
 * `dhRng(seed)`: Mulberry32. Each [next] gives the page's next value in [0, 1); [seed] is read as
 * an unsigned 32-bit value.
 */
class HandRng(seed: Long) {
    private var state: Int = seed.toInt()

    /** The next value, bit for bit what the page's generator returns. */
    fun next(): Double {
        state += HandRandom.MULBERRY_STEP
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        val out = t xor (t ushr 14)
        return (out.toLong() and 0xFFFFFFFFL).toDouble() / HandRandom.UINT32_RANGE
    }
}
