package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.same
import com.lifemax.rpg.core.lifedirector.PageValues.truthy

/** A warning about now (RM-13): `late` or `ending`, with its seconds. */
data class Kick(val kind: String, val seconds: Double)

/** RM-13: the next planned session starts within `SOON` ([seconds] from now). */
data class Soon(val block: DayBlock, val seconds: Double) {
    val kind: String
        get() = "soon"
}

/**
 * RM-12, RM-13 `nowState(now)`: the block now on ([current]; a live session before anything else),
 * the next block worth naming ([next]), the next planned session ([nextSession]), the warning about
 * now ([kick]) and about what is coming ([soon]), whether a planned session is on now
 * ([isSession]) and whether the page's timer runs on it ([running]). The blocks are the timeline's
 * own ([LiveNow.timeline] made for this state); the page holds `isSes` and `running` as null,
 * undefined or false for "no", and every reader tests them for truth.
 */
data class NowState(
    val current: DayBlock?,
    val next: DayBlock?,
    val nextSession: DayBlock?,
    val kick: Kick?,
    val soon: Soon?,
    val isSession: Boolean,
    val running: Boolean,
) {
    /** `alert: kick || soon`: which of the two warnings the page raises (`kick`, `soon`), or none. */
    val alert: String?
        get() = if (kick != null) "kick" else if (soon != null) "soon" else null
}

/** RM-11 to RM-13 (logic.js:1056-1135). */
internal object LiveNow {
    private const val JOIN = 60.0
    private const val NEXT_SLACK = 60.0

    /** RM-11 `liveTimeline(now)`: the first two days' blocks in one list, split pieces joined. */
    fun timeline(m: DirectorReadModels): List<DayBlock> {
        val s = m.schedule
        if (!truthy(s)) return emptyList()
        val all = ArrayList<DayBlock>()
        for (d in m.horizonDays().take(2)) all += DayBlocks.of(m, s!!, d)
        val sorted = all.sortedWith { a, b -> sign(a.startEpoch - b.startEpoch) }
        val out = ArrayList<DayBlock>()
        for (b in sorted) {
            val p = out.lastOrNull()
            if (
                p != null &&
                    p.session == null &&
                    b.session == null &&
                    same(p.code, b.code) &&
                    same(p.title, b.title) &&
                    b.startEpoch <= p.endEpoch + JOIN
            ) {
                out[out.size - 1] = p.span(p.startEpoch, maxOf(p.endEpoch, b.endEpoch))
                continue
            }
            out += b
        }
        return out
    }

    /** RM-12, RM-13 `nowState(now)`. */
    fun nowState(m: DirectorReadModels): NowState {
        val now = m.nowEpoch
        val words = m.words
        val tl = timeline(m)
        val on = { b: DayBlock -> b.startEpoch <= now && b.endEpoch > now }
        val cur =
            tl.firstOrNull { it.session != null && it.isLive && on(it) }
                ?: tl.firstOrNull { it.session == null && on(it) }
        val after = cur?.endEpoch ?: now
        val next =
            tl.firstOrNull {
                it !== cur &&
                    it.startEpoch >= after - NEXT_SLACK &&
                    it.blockClass(words) != "free" &&
                    it.isLive &&
                    !(it.session != null && it.session.sessionState == "done")
            }
        val nextTask =
            tl.firstOrNull {
                it.session != null && it !== cur && it.startEpoch > now && it.session.isPlanned
            }
        val isSes = cur?.session?.isPlanned == true
        val f = m.focus()
        val running =
            truthy(f) &&
                !truthy(member(f, "paused")) &&
                cur?.session != null &&
                same(member(f, "id"), cur.session.instanceId)
        val kick =
            if (isSes && !running && !truthy(f) && now - cur!!.startEpoch >= words.lateSeconds)
                Kick("late", now - cur.startEpoch)
            else if (
                cur != null &&
                    cur.endEpoch - now <= words.endingSeconds &&
                    next != null &&
                    cur.blockClass(words) != "free"
            )
                Kick("ending", cur.endEpoch - now)
            else null
        val soon =
            if (nextTask != null && nextTask.startEpoch - now <= words.soonSeconds)
                Soon(nextTask, nextTask.startEpoch - now)
            else null
        return NowState(cur, next, nextTask, kick, soon, isSes, running)
    }

    private fun sign(d: Double): Int = if (d > 0) 1 else if (d < 0) -1 else 0
}
