package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/** Where a stored session is kept in the saved plan. */
enum class SessionList(val prefix: String) {
    /** `schedule.scheduledInstances`. */
    SCHEDULED("s"),

    /** `schedule.historyInstances`. */
    HISTORY("h"),
}

/**
 * A session of the saved plan exactly as stored ([value], read only), with where it is kept
 * ([list], [index]). The getters read the members the read models use the way the page does:
 * epochs as numbers (`Number(value)`), the lifecycle state as stored.
 */
class StoredSession(val list: SessionList, val index: Int, val value: JsonValue) {
    /** `s<k>` or `h<k>`: how the golden cases and a shadow comparison name the session. */
    val ref: String
        get() = list.prefix + index

    val startEpoch: Double
        get() = number(member(value, "startEpoch"))

    val endEpoch: Double
        get() = number(member(value, "endEpoch"))

    val lifecycleState: JsonValue?
        get() = member(value, "lifecycleState")

    val instanceId: JsonValue?
        get() = member(value, "instanceId")

    val title: JsonValue?
        get() = member(value, "title")

    val tag: JsonValue?
        get() = member(value, "tag")

    val actualMinutes: JsonValue?
        get() = member(value, "actualMinutes")

    /** `lifecycleState === 'PLANNED'`. */
    val isPlanned: Boolean
        get() = lifecycleState == PLANNED

    /** RM-10 `sesState`. */
    val sessionState: String
        get() = sessionState(lifecycleState)

    companion object {
        private val PLANNED = JsonString("PLANNED")

        /**
         * RM-10 `sesState(st)` (logic.js:573): `done` when the state's text contains `COMPLETED`
         * but not `PARTIAL`, `part` when it contains `PARTIAL`, `plan` for exactly `PLANNED`, else
         * `off`.
         */
        fun sessionState(state: JsonValue?): String {
            val t = text(state)
            return when {
                "COMPLETED" in t && "PARTIAL" !in t -> "done"
                "PARTIAL" in t -> "part"
                state == PLANNED -> "plan"
                else -> "off"
            }
        }
    }
}

/**
 * One block of a day as `dayBlocks` makes it (RM-6 to RM-9): a span of the day with what fills it.
 * [code], [kind], [title] and [refId] are the stored segment's values as the page copies them
 * (null where the page's member holds undefined and so is left out of its JSON); a session's block
 * has [session] and the codes `TASK`, free time `FREE`. [mealStartEpoch] is set on a meal (the
 * segment's own start, before any clipping) and [clipped] on every block taken from a segment.
 * Blocks are compared by identity where the page compares objects (RM-12).
 */
class DayBlock(
    val startEpoch: Double,
    val endEpoch: Double,
    val code: JsonValue?,
    val kind: JsonValue?,
    val title: JsonValue?,
    val refId: JsonValue? = null,
    val mealStartEpoch: Double? = null,
    val clipped: Boolean? = null,
    val session: StoredSession? = null,
) {
    /** A copy over [startEpoch] to [endEpoch] (the page's `Object.assign({}, b, {...})`). */
    fun span(startEpoch: Double, endEpoch: Double): DayBlock =
        DayBlock(startEpoch, endEpoch, code, kind, title, refId, mealStartEpoch, clipped, session)

    /** RM-10 `blockClass` (logic.js:572) with the page's `KIND_CLASS`. */
    fun blockClass(words: ReadModelWords): String =
        if (session != null) "task"
        else
            words.kindClass[text(kind)]
                ?: if (code == JsonString("RECOVERY_PROTECTED")) "rest" else "free"

    /** `!(b.inst && sesState(b.inst.lifecycleState) === 'off')` (RM-12's `live`). */
    val isLive: Boolean
        get() = session == null || session.sessionState != "off"
}
