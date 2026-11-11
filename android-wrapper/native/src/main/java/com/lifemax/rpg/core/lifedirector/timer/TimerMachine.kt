package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.CommandReply
import com.lifemax.rpg.core.lifedirector.host.HostCall
import com.lifemax.rpg.core.lifedirector.host.HostCommand
import com.lifemax.rpg.core.lifedirector.host.HostCommands
import com.lifemax.rpg.core.lifedirector.host.HostEffects
import com.lifemax.rpg.core.lifedirector.host.HostReply
import com.lifemax.rpg.core.lifedirector.host.HostState
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * What a screen (or a background job) asks of the timer (TM-1): `Today.timer`'s acts by the page's
 * names, with the session id and the timer's start (milliseconds) they name, and [Check], the
 * once-a-second check. Ids and starts are JSON values, as a screen sends them: the page compares
 * them strictly, so a number id is not its text (TM-14).
 */
sealed interface TimerCommand {
    /** Start the session's timer (TM-10). */
    data class Start(val id: JsonValue) : TimerCommand

    /** Stop the running timer, whose start is [start], and start [id]'s (TM-30). */
    data class Replace(val id: JsonValue, val start: JsonValue) : TimerCommand

    /** Pause the running timer (TM-20). */
    data object Pause : TimerCommand

    /** Resume it (TM-21). */
    data object Resume : TimerCommand

    /** Back to zero, paused (TM-25). */
    data class Reset(val id: JsonValue, val start: JsonValue) : TimerCommand

    /** Finish and log the elapsed minutes (TM-40). */
    data class Finish(val id: JsonValue, val start: JsonValue) : TimerCommand

    /** Stop without logging (TM-45). */
    data class Cancel(val id: JsonValue, val start: JsonValue) : TimerCommand

    /** Finish with the planned minutes once the target is reached (TM-50). */
    data class AutoFinish(val id: JsonValue, val start: JsonValue) : TimerCommand

    /** An act the page does not know (TM-4). */
    data class Other(val act: JsonValue?) : TimerCommand

    /** `focusTick`, the page's once-a-second check (TM-60). */
    data object Check : TimerCommand

    /** The page's payload `{act, id, start}`. */
    fun payload(): JsonObject {
        fun of(act: String, id: JsonValue? = null, start: JsonValue? = null): JsonObject {
            val m = LinkedHashMap<String, JsonValue>()
            m["act"] = JsonString(act)
            id?.let { m["id"] = it }
            start?.let { m["start"] = it }
            return JsonObject(m)
        }
        return when (this) {
            is Start -> of("start", id)
            is Replace -> of("replace", id, start)
            Pause -> of("pause")
            Resume -> of("resume")
            is Reset -> of("reset", id, start)
            is Finish -> of("finish", id, start)
            is Cancel -> of("cancel", id, start)
            is AutoFinish -> of("autoFinish", id, start)
            is Other -> JsonObject(act?.let { mapOf("act" to it) } ?: emptyMap())
            Check -> of("check")
        }
    }

    companion object {
        /** A start in milliseconds as a JSON value. */
        fun ms(v: Double): JsonValue = JsonNumber(v)

        /** An id as a JSON value. */
        fun id(v: String): JsonValue = JsonString(v)
    }
}

/** The timer's answer (TM-1): a command's reply, or what the check did. */
sealed interface TimerReply {
    /** `Today.timer`'s `{ok, changed, message}`. */
    data class Answer(val reply: CommandReply) : TimerReply

    /** What the once-a-second check did. */
    data class Checked(val tick: FocusTick) : TimerReply

    /** The page would throw here (a state without `ui`): the error's name and message. */
    data class Threw(val name: String, val message: String) : TimerReply
}

/** The timer's three results: the state after it, its answer, the host calls it made. */
data class TimerOutcome(val state: HostState, val reply: TimerReply, val calls: List<HostCall>)

/**
 * WP-LD10's entry point: the timer as a pure function `(state, now, host, command) -> (state',
 * reply, hostCalls)` over WP-LD9's state and host ([HostState], [HostEffects]); [nowMs] is the wall
 * clock the page reads (`Date.now()`). Nothing throws. Not wired to a screen; the page stays the
 * only writer.
 */
object TimerMachine {
    fun run(
        state: HostState,
        nowMs: Double,
        host: HostEffects,
        command: TimerCommand,
    ): TimerOutcome {
        if (command == TimerCommand.Check) {
            var tick: FocusTick = FocusTick.Idle
            val o =
                HostCommands.runOn(state, nowMs, host) { p -> tick = TimerCommands.focusTick(p) }
            return TimerOutcome(o.state, threw(o.reply) ?: TimerReply.Checked(tick), o.calls)
        }
        val o = HostCommands.run(state, nowMs, host, HostCommand.Today("timer", command.payload()))
        val reply =
            threw(o.reply)
                ?: TimerReply.Answer((o.reply as? HostReply.Command)?.reply ?: CommandReply.CANNOT)
        return TimerOutcome(o.state, reply, o.calls)
    }

    private fun threw(r: HostReply): TimerReply? =
        (r as? HostReply.Threw)?.let { TimerReply.Threw(it.name, it.message) }
}
