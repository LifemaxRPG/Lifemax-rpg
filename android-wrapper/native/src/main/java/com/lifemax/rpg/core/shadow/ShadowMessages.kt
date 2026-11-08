package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/**
 * One changed record of a record key: its index in the page's array after the change and its whole
 * value (2.3, `rec`).
 */
data class RecordChange(val index: Int, val value: JsonValue)

/** The record delta of one array key: the array's new length and the records that changed. */
data class RecordDelta(val length: Int, val changes: List<RecordChange>)

/**
 * A change of the shadow keys (2.2, 2.3): [set] replaces whole keys, [del] removes keys, [rec]
 * changes records of the keys in [ShadowKeys.REC_KEYS]. A key appears in at most one of them.
 */
data class ShadowDelta(
    val set: Map<String, JsonValue>,
    val del: List<String>,
    val rec: Map<String, RecordDelta>,
) {
    /** Whether the delta changes nothing. */
    val isEmpty: Boolean
        get() = set.isEmpty() && del.isEmpty() && rec.isEmpty()

    companion object {
        /** A delta that changes nothing. */
        val NONE = ShadowDelta(emptyMap(), emptyList(), emptyMap())
    }
}

/** What the page read from outside during the action (2.3, `env`); decoded per kind from 6b on. */
data class ShadowEnv(
    /** Each `writeSave()` outcome, in order: whether it stored. */
    val saves: List<Boolean>,
    /**
     * The journal coach's own plain writes during the action (`jcWrite`, its redraw's day memory,
     * outside the economy: A-06), sent apart from [saves] by a page that knows
     * `economy.shadow.quests.v1`; whether each stored.
     */
    val journal: List<Boolean> = emptyList(),
    /** The other parts as sent (`plan`, `health`, `confirm`, `prompt`, `dom`), not yet read. */
    val rest: JsonObject,
)

/**
 * One outermost page action (ECONOMY_SHADOW_PLAN 1.1, 2.3). The owner's words in [args], [env], the
 * deltas, [returns] and [toasts] live in memory only and are never written or logged.
 */
data class ShadowEnvelope(
    val sid: String,
    val epoch: Long,
    val seq: Long,
    /** The `seq` the page believes native's mirror is at; the envelope applies only on it. */
    val base: Long,
    val kind: String,
    val args: JsonArray,
    val t0: Long,
    val t1: Long,
    val zone: String,
    /** The page's UTC offset at [t0], in minutes east of UTC. */
    val offsetMinutes: Int,
    val locale: String,
    val env: ShadowEnv,
    val before: ShadowDelta,
    val after: ShadowDelta,
    val returns: JsonValue,
    val toasts: List<String>,
    /** The page's main-thread time for its own part of the action, in milliseconds. */
    val costMs: Double,
    val flags: Set<String>,
)

/** One slice of a base (2.3): the parts joined in order are `{"keys":{...}}`. */
data class ShadowBasePart(
    val sid: String,
    val epoch: Long,
    val seq: Long,
    val part: Int,
    val parts: Int,
    val text: String,
)

/** What the page says about itself in `configure` (2.3). */
data class ShadowPageInfo(
    val build: String,
    val schema: Int,
    val fingerprint: String,
    val faults: Int,
)

/** The owner's switch and the page's session, from `configure`. */
data class ShadowConfig(val enabled: Boolean, val sid: String, val page: ShadowPageInfo)

/** The envelope flags the page may set (2.3). */
object ShadowFlags {
    const val INTERLEAVED = "interleaved"
    const val THREW = "threw"
    const val READ_ONLY = "readOnly"
    const val NESTED_ASYNC = "nested-async"

    /** Every flag a v1 envelope may carry. */
    val ALL: Set<String> = setOf(INTERLEAVED, THREW, READ_ONLY, NESTED_ASYNC)
}
