package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.CommandReply
import com.lifemax.rpg.core.lifedirector.host.HostCompletion
import com.lifemax.rpg.core.lifedirector.host.HostPage
import com.lifemax.rpg.core.lifedirector.host.Js
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.get
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.o
import com.lifemax.rpg.core.lifedirector.host.Js.obj
import com.lifemax.rpg.core.lifedirector.host.Js.same
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.lifedirector.host.JsObj
import com.lifemax.rpg.core.lifedirector.host.Replies.recorded
import com.lifemax.rpg.core.lifedirector.host.Replies.saved
import com.lifemax.rpg.core.save.JsonNull
import kotlin.math.floor

/**
 * `Today.timer` (`logic.js:3491-3552`) and the timer's once-a-second check `focusTick`
 * (`logic.js:1952-1959`) over the page's memory ([HostPage]), as the page runs them (TM-1 to TM-61,
 * LIFE_DIRECTOR_TIMER_SPEC.md). The running timer is `ST.ui.focus = {id, start, paused, pausedMs,
 * plannedMs}`, wall-clock milliseconds read from `Date.now()` ([HostPage.nowMs]); its values are
 * read with JavaScript's coercions, so a timer the page wrote oddly (a number id, no `plannedMs`, a
 * negative `pausedMs`) behaves as it does there.
 */
object TimerCommands {
    private const val MINUTE_MS = 60_000.0
    private const val SECOND_MS = 1000.0
    private const val SHORTEST_S = 60.0

    /** The page's refusals and notices, in its words. */
    const val NOT_YET = "You can start it on the day, or from 6 hours before."
    const val NOT_AVAILABLE = "This session is not available to start yet."
    const val ANOTHER = "Another timer is running. Finish or stop it first."
    const val CHANGED = "The timer changed. Please choose Start again."
    const val NOT_SAVED_NEW = "The new timer could not be saved. Your previous timer is unchanged."
    const val NOT_RESET = "Timer could not be reset. Please try again."
    const val NOT_RUNNING = "That timer is not running."
    const val ALREADY = "Already recorded."
    const val STOPPED = "Timer stopped. Nothing was logged."
    const val OFF_PLAN = "That session is no longer on the plan. Log it from the Log page."

    /** The acts `Today.timer` knows (TM-4). */
    val ACTS =
        listOf("start", "replace", "pause", "resume", "reset", "finish", "cancel", "autoFinish")

    /** `ST.ui.focus` (TM-1): the running timer, or JavaScript's null or undefined. */
    fun focusOf(p: HostPage): Any? = get(get(p.s, "ui"), "focus")

    private fun setFocus(p: HostPage, f: Any?) {
        o(get(p.s, "ui"))?.set("focus", f)
    }

    /**
     * TM-2 `focusMs(f)` (`logic.js:1929`): `max(0, (f.paused || Date.now()) - f.start - (f.pausedMs
     * || 0))`.
     */
    fun focusMs(f: Any?, nowMs: Double): Double {
        val paused = at(f, "paused")
        val upTo = if (truthy(paused)) num(paused) else nowMs
        val pausedMs = at(f, "pausedMs")
        return maxOf(
            0.0,
            upTo - num(at(f, "start")) - (if (truthy(pausedMs)) num(pausedMs) else 0.0),
        )
    }

    /** `plannedInst(id)`: the session of that id (as text) still on the plan. */
    private fun planned(p: HostPage, id: Any?): JsObj? =
        p.schedule?.let { s ->
            o(list(s["scheduledInstances"]).firstOrNull { same(at(it, "instanceId"), text(id)) })
        }

    /** `findInst(id)` (`logic.js:2307`): planned, then history, the id compared strictly. */
    private fun found(p: HostPage, id: Any?): JsObj? {
        val s = p.schedule ?: return null
        return o(list(s["scheduledInstances"]).firstOrNull { same(at(it, "instanceId"), id) })
            ?: o(list(s["historyInstances"]).firstOrNull { same(at(it, "instanceId"), id) })
    }

    /** TM-3 `Math.max(60, inst.endEpoch - inst.startEpoch) * 1000`: the session's length (ms). */
    fun sessionMs(inst: Any?): Double =
        maxOf(SHORTEST_S, num(at(inst, "endEpoch")) - num(at(inst, "startEpoch"))) * SECOND_MS

    /** TM-3 the timer's target: `f.plannedMs || <the session's length>`. */
    private fun target(f: Any?, inst: Any?): Double {
        val plannedMs = at(f, "plannedMs")
        return if (truthy(plannedMs)) num(plannedMs) else sessionMs(inst)
    }

    private fun newFocus(id: Any?, start: Double, inst: JsObj): JsObj =
        obj(
            "id" to id,
            "start" to start,
            "paused" to JsonNull,
            "pausedMs" to 0.0,
            "plannedMs" to sessionMs(inst),
        )

    /** TM-4 `Today.timer({act, id, start})`. */
    fun timer(p: HostPage, x: JsObj): CommandReply {
        val f = focusOf(p)
        val id = x["id"]
        val start = num(x["start"])
        val sameStart = truthy(f) && same(at(f, "start"), start)
        return when (val act = x["act"]) {
            "autoFinish" -> autoFinish(p, f, id, sameStart)
            "replace" -> replace(p, f, id, sameStart)
            "start" -> start(p, f, id)
            "reset" -> reset(p, f, id, sameStart)
            "pause" -> pause(p, f)
            "resume" -> resume(p, f)
            "finish",
            "cancel" -> finishOrCancel(p, f, id, sameStart, act == "cancel", null)
            else -> CommandReply.CANNOT
        }
    }

    /** TM-50 to TM-53: the automatic finish, with the planned minutes. */
    private fun autoFinish(p: HostPage, f: Any?, id: Any?, sameStart: Boolean): CommandReply {
        if (!sameStart || !same(at(f, "id"), id) || truthy(at(f, "paused"))) return recorded(p, "")
        val inst = planned(p, at(f, "id")) ?: return CommandReply.GONE
        val target = target(f, inst)
        if (focusMs(f, p.nowMs) < target) return recorded(p, "")
        val minutes = maxOf(1.0, Js.round(target / MINUTE_MS))
        return finishOrCancel(p, f, id, true, false, minutes)
    }

    /** TM-30 to TM-32 `replace`: the running timer gives way to a new one, by its start. */
    private fun replace(p: HostPage, f: Any?, id: Any?, sameStart: Boolean): CommandReply {
        val inst = planned(p, id)
        if (inst == null || inst["lifecycleState"] != "PLANNED") return CommandReply.GONE
        if (!HostCompletion.doable(p, inst, p.now)) return CommandReply.no(NOT_AVAILABLE)
        if (!sameStart)
            return if (truthy(f) && same(at(f, "id"), id)) recorded(p, "")
            else CommandReply.no(CHANGED)
        if (same(at(f, "id"), id)) return recorded(p, "")
        setFocus(p, newFocus(id, maxOf(p.nowMs, num(at(f, "start")) + 1), inst))
        if (!p.persist()) {
            setFocus(p, f)
            return CommandReply.no(NOT_SAVED_NEW)
        }
        return saved(p, "")
    }

    /** TM-10 to TM-13 `start`. */
    private fun start(p: HostPage, f: Any?, id: Any?): CommandReply {
        val inst = planned(p, id)
        if (inst == null || inst["lifecycleState"] != "PLANNED") return CommandReply.GONE
        if (!HostCompletion.doable(p, inst, p.now)) return CommandReply.no(NOT_YET)
        if (truthy(f) && same(at(f, "id"), id)) return recorded(p, "")
        if (truthy(f) && planned(p, at(f, "id")) != null) return CommandReply.no(ANOTHER)
        setFocus(p, newFocus(id, p.nowMs, inst))
        p.persist()
        return saved(p, "")
    }

    /** TM-25, TM-26 `reset`: back to zero, paused, by its id and start. */
    private fun reset(p: HostPage, f: Any?, id: Any?, sameStart: Boolean): CommandReply {
        if (!sameStart || !same(at(f, "id"), id)) return recorded(p, "")
        val at = maxOf(p.nowMs, num(at(f, "start")) + 1)
        setFocus(p, Js.assign(o(f), obj("start" to at, "paused" to at, "pausedMs" to 0.0)))
        if (!p.persist()) {
            setFocus(p, f)
            return CommandReply.no(NOT_RESET)
        }
        return saved(p, "")
    }

    /** TM-20 `pause`: any running timer, whatever id or start the request names. */
    private fun pause(p: HostPage, f: Any?): CommandReply {
        if (!truthy(f) || truthy(at(f, "paused"))) return recorded(p, "")
        o(f)?.set("paused", p.nowMs)
        p.persist()
        return saved(p, "")
    }

    /** TM-21, TM-22 `resume`: the pause is added to `pausedMs`, by the wall clock. */
    private fun resume(p: HostPage, f: Any?): CommandReply {
        if (!truthy(f) || !truthy(at(f, "paused"))) return recorded(p, "")
        val t = o(f) ?: return recorded(p, "")
        val before = t["pausedMs"]
        t["pausedMs"] = (if (truthy(before)) num(before) else 0.0) + p.nowMs - num(t["paused"])
        t["paused"] = JsonNull
        p.persist()
        return saved(p, "")
    }

    /**
     * TM-40 to TM-45 `finish` and `cancel` (and TM-51's automatic finish with [autoMinutes]): by
     * the timer's start; a finish logs the elapsed minutes from the timer's start.
     */
    private fun finishOrCancel(
        p: HostPage,
        f: Any?,
        id: Any?,
        sameStart: Boolean,
        cancel: Boolean,
        autoMinutes: Double?,
    ): CommandReply {
        if (!sameStart) {
            val done =
                !cancel &&
                    list(p.schedule?.get("historyInstances")).any {
                        same(at(it, "instanceId"), id) &&
                            text(at(it, "lifecycleState")).contains("COMPLETED")
                    }
            return when {
                done -> recorded(p, ALREADY)
                !truthy(f) -> recorded(p, "")
                else -> CommandReply.no(NOT_RUNNING)
            }
        }
        if (cancel) {
            setFocus(p, JsonNull)
            p.persist()
            p.say(STOPPED)
            return saved(p, "")
        }
        val inst = planned(p, at(f, "id"))
        val minutes = autoMinutes ?: maxOf(1.0, Js.round(focusMs(f, p.nowMs) / MINUTE_MS))
        setFocus(p, JsonNull)
        if (inst == null || inst["lifecycleState"] != "PLANNED") {
            p.persist()
            p.say(OFF_PLAN)
            return saved(p, "")
        }
        val r =
            HostCompletion.finish(
                p,
                inst,
                minutes,
                HostCompletion.FinishOptions(
                    startEpoch = floor(num(at(f, "start")) / SECOND_MS),
                    quietRefusal = true,
                ),
            )
        if (r.recorded == null && autoMinutes != null) {
            setFocus(p, f)
            p.persist()
        }
        return if (r.recorded != null) saved(p, "") else refusedFinish(p)
    }

    private fun refusedFinish(p: HostPage): CommandReply =
        if (p.lastFinishRefusal == "DUPLICATE_EVENT_ID") recorded(p, ALREADY)
        else
            CommandReply.no("Cannot complete this block (" + (p.lastFinishRefusal ?: "null") + ").")

    /**
     * TM-60, TM-61 `focusTick` (`logic.js:1952-1959`): with a timer running (not paused) and a
     * target (`f.plannedMs`, else the length of the session found by id among planned sessions and
     * history), once the elapsed time reaches it, `Today.timer({act: 'autoFinish', id: f.id, start:
     * f.start})`. The page then draws the clocks, which is not modelled.
     */
    fun focusTick(p: HostPage): FocusTick {
        val st = p.st ?: return FocusTick.Idle
        val f = at(at(st, "ui"), "focus")
        if (!truthy(f)) return FocusTick.Idle
        val ms = focusMs(f, p.nowMs)
        val inst = found(p, at(f, "id"))
        val plannedMs = at(f, "plannedMs")
        val target =
            when {
                truthy(plannedMs) -> num(plannedMs)
                inst != null -> sessionMs(inst)
                else -> Double.NaN
            }
        if (truthy(at(f, "paused")) || !truthy(target) || !(ms >= target))
            return FocusTick.Counting(ms, target.takeIf { truthy(it) })
        val reply =
            timer(p, obj("act" to "autoFinish", "id" to at(f, "id"), "start" to at(f, "start")))
        return FocusTick.AutoFinished(reply)
    }
}

/** What the once-a-second check did (TM-60). */
sealed interface FocusTick {
    /** No timer (or no state). */
    data object Idle : FocusTick

    /** A timer counting (or paused) at [elapsedMs] of [targetMs] (null: no target to reach). */
    data class Counting(val elapsedMs: Double, val targetMs: Double?) : FocusTick

    /** The target was reached: the automatic finish's answer (TM-50 to TM-53). */
    data class AutoFinished(val reply: CommandReply) : FocusTick
}
