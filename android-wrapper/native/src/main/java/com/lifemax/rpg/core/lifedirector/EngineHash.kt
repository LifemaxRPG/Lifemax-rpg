package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.quests.HandRandom
import com.lifemax.rpg.core.quests.HandRng

/**
 * The engine's hash and the identities built from it (LDH-1 to LDH-7). All of it is 32-bit integer
 * arithmetic exactly as the page computes it (LDN-5): Kotlin `Int` multiplication keeps the low 32
 * bits of the product (`Math.imul`), `ushr` is `>>>`, and text is read one UTF-16 code unit at a
 * time, a lone surrogate counting as itself (LDN-6). These are idempotency keys: one wrong bit
 * gives a session a second event id, so a second award.
 */
object EngineHash {
    /** The 53-bit hash's first lane's start, `0xdeadbeef`. */
    const val LANE_ONE_BASIS: Int = 0xDEADBEEF.toInt()

    /** The second lane's start, `0x41c6ce57`. */
    const val LANE_TWO_BASIS: Int = 0x41C6CE57

    /** Per code unit, lane one's multiplier, 2654435761. */
    const val LANE_ONE_STEP: Int = 0x9E3779B1.toInt()

    /** Per code unit, lane two's multiplier, 1597334677. */
    const val LANE_TWO_STEP: Int = 0x5F356495

    /** The final mix's first multiplier, 2246822507. */
    const val MIX_ONE: Int = 0x85EBCA6B.toInt()

    /** The final mix's second multiplier, 3266489909. */
    const val MIX_TWO: Int = 0xC2B2AE35.toInt()

    /** The low 21 bits of lane two become the top of the 53-bit result. */
    private const val HIGH_BITS: Int = 0x1FFFFF
    private const val UINT32: Long = 0xFFFFFFFFL
    private const val ID_RADIX = 36

    /** LDH-3: each half of a stable id is 11 base-36 digits (2^53 needs 11). */
    const val HALF_ID_DIGITS: Int = 11

    /** LDH-3: the seed of a stable id's first half. */
    const val FIRST_HALF_SEED: Int = 1

    /** LDH-3: the seed of a stable id's second half. */
    const val SECOND_HALF_SEED: Int = 7

    /**
     * LDH-1, LDH-2 `cyrb53`: a 53-bit hash of [text] (0 to 2^53 - 1). [seed] is a 32-bit integer;
     * the page converts a number with ToInt32 first ([seedOf]).
     */
    fun hash53(text: String, seed: Int): Long {
        var one = LANE_ONE_BASIS xor seed
        var two = LANE_TWO_BASIS xor seed
        for (unit in text) {
            one = (one xor unit.code) * LANE_ONE_STEP
            two = (two xor unit.code) * LANE_TWO_STEP
        }
        one = ((one xor (one ushr 16)) * MIX_ONE) xor ((two xor (two ushr 13)) * MIX_TWO)
        two = ((two xor (two ushr 16)) * MIX_ONE) xor ((one xor (one ushr 13)) * MIX_TWO)
        return ((two and HIGH_BITS).toLong() shl 32) + (one.toLong() and UINT32)
    }

    /** LDN-5: a seed number as the hash reads it (ToInt32: whole part, modulo 2^32, signed). */
    fun seedOf(number: Double): Int = HandRandom.toUint32(number).toInt()

    /**
     * LDH-3 `stableId`: [prefix], `_`, then [text]'s hash with seed 1 and with seed 7, each in base
     * 36 (lower case) padded with zeros to 11 digits.
     */
    fun stableId(prefix: String, text: String): String =
        prefix + "_" + half(text, FIRST_HALF_SEED) + half(text, SECOND_HALF_SEED)

    private fun half(text: String, seed: Int): String =
        hash53(text, seed).toString(ID_RADIX).padStart(HALF_ID_DIGITS, '0')

    /**
     * LDH-5 `completionEventId`: the ledger's identity of one completion. With an [instanceId] (any
     * non-empty text) it is `stableId("ev", "DONE:<playerId>:<instanceId>")`, with `:rev<n>` added
     * for a non-zero [revision]; without one, it is the ad-hoc `stableId("ev",
     * "ADHOC:<playerId>:<clientEventId>")`, and an ad-hoc completion without a client event id
     * (null or empty) is refused.
     */
    fun completionEventId(
        playerId: String,
        instanceId: String?,
        clientEventId: String?,
        revision: Long?,
    ): String {
        if (!instanceId.isNullOrEmpty()) {
            val suffix = if (revision != null && revision != 0L) ":rev$revision" else ""
            return stableId("ev", "DONE:$playerId:$instanceId$suffix")
        }
        require(!clientEventId.isNullOrEmpty()) { "Ad-hoc completions need a clientEventId" }
        return stableId("ev", "ADHOC:$playerId:$clientEventId")
    }

    /**
     * LDH-7 `mulberry32`: the engine's seeded generator, the same Mulberry32 as the Daily Hand's
     * (QUESTS_SPEC QST-33), seeded with ToUint32 of [seed].
     */
    fun generator(seed: Double): HandRng = HandRng(HandRandom.toUint32(seed))
}
