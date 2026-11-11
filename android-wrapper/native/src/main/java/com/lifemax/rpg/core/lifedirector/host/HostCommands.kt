package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.lifedirector.EngineRefusal
import com.lifemax.rpg.core.lifedirector.PageRangeError
import com.lifemax.rpg.core.lifedirector.PageTypeError
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonValue

/** What a command is asked to do (HC-2); the page's names. */
sealed interface HostCommand {
    /** `Today[name](payload)` through `nativeTodayRun` (HC-10 to HC-35; not `timer`, WP-LD10's). */
    data class Today(val name: String, val payload: JsonValue?) : HostCommand

    /** `Do[name](payload)` through `nativeRun` (HC-50 to HC-66). */
    data class Do(val name: String, val payload: JsonValue?) : HostCommand

    /** `boot()` (HC-1). */
    data object Boot : HostCommand

    /** The tick's body (HC-79). */
    data object Tick : HostCommand

    /** `syncHost()` (HC-70 to HC-74). */
    data object SyncHost : HostCommand

    /** `processMisses()` (HC-75 to HC-77). */
    data object ProcessMisses : HostCommand

    /** `maybeRollover()` (HC-78). */
    data object MaybeRollover : HostCommand

    /** `repairEarlyDone()` with its once-only flag cleared first (HC-42). */
    data object RepairEarlyDone : HostCommand

    /** `applyDial(now)` holding the change [change] (`ui.pendingDial`; HC-80 to HC-82). */
    data class ApplyDial(val change: JsonValue?) : HostCommand

    /** `applyOutput(output, label)` (HC-40, HC-41). */
    data class ApplyOutput(val output: JsonValue, val label: JsonValue?) : HostCommand

    /** `finishInstance` of the planned session of that id (HC-20 to HC-26). */
    data class Finish(
        val instanceId: String,
        val minutes: Double,
        val options: HostCompletion.FinishOptions,
    ) : HostCommand

    /** `logToday` of the planned session of that id (HC-27). */
    data class LogToday(val instanceId: String, val minutes: Double, val op: String?) : HostCommand
}

/** A command's answer (HC-3). */
sealed interface HostReply {
    /** A Today or Do command's or the dial's answer. */
    data class Command(val reply: CommandReply) : HostReply

    /** A tick function's `changed`, or `applyOutput`'s acceptance. */
    data class Flag(val value: Boolean) : HostReply

    /**
     * `finishInstance`'s or `logToday`'s ledger answer (the engine's object), or null when refused.
     */
    data class Ledger(val recorded: JsonValue?) : HostReply

    /** `logToday`'s repeat (`{duplicate: true}`). */
    data object Duplicate : HostReply

    /** The session named is not on the plan (the recorder's `absent`). */
    data object Absent : HostReply

    /** No answer (boot, the tick). */
    data object None : HostReply

    /** The page would throw here: the error's name and message (HC-5). */
    data class Threw(val name: String, val message: String) : HostReply
}

/** The Skip's take-back as a value (HC-17). */
data class TakeBackState(
    val id: String,
    val text: String,
    val at: Double,
    val before: JsonValue,
    val left: String,
)

/**
 * The page's memory between commands as a value (HC-2): `ST` and the controller's variables
 * ([HostPage]).
 */
data class HostState(
    val st: JsonValue? = null,
    val banner: JsonValue? = null,
    val notice: Notice? = null,
    val tab: String = "today",
    val dayIdx: Double = 0.0,
    val pendingDial: JsonValue? = null,
    val takeBack: TakeBackState? = null,
    val takenBackOp: JsonValue = JsonNull,
    val lastSave: SaveOutcome = SaveOutcome.OK,
    val lastFinishRefusal: String? = null,
)

/** A command's three results (HC-2): the state after it, its answer, the host calls it made. */
data class HostOutcome(val state: HostState, val reply: HostReply, val calls: List<HostCall>)

/**
 * WP-LD9's entry point: Life Director's host commands as pure functions `(state, now, host,
 * command) -> (state', reply, hostCalls)` (HC-2). Nothing throws: where the page would throw, the
 * reply says so ([HostReply.Threw]) and the state is what the page would hold then. Not wired to a
 * screen; the page stays the only writer.
 */
object HostCommands {
    fun run(state: HostState, nowMs: Double, host: HostEffects, command: HostCommand): HostOutcome {
        val recording = RecordingHost(host)
        val p = page(state, recording, nowMs)
        val reply =
            try {
                execute(p, command)
            } catch (e: JsThrow) {
                HostReply.Threw(e.name, e.message ?: "")
            } catch (e: PageTypeError) {
                HostReply.Threw("TypeError", e.message ?: "")
            } catch (e: PageRangeError) {
                HostReply.Threw("RangeError", e.message ?: "")
            } catch (e: EngineRefusal) {
                HostReply.Threw("Error", e.message ?: "")
            } catch (e: RuntimeException) {
                HostReply.Threw("Error", e.message ?: "")
            } catch (e: StackOverflowError) {
                HostReply.Threw("RangeError", "Maximum call stack size exceeded")
            }
        return HostOutcome(snapshot(p), reply, recording.calls.toList())
    }

    private fun payload(v: JsonValue?): JsObj = Js.o(Js.of(v)) ?: JsObj()

    private fun planned(p: HostPage, id: String): JsObj? =
        Js.o(
            Js.list(p.schedule?.get("scheduledInstances")).firstOrNull {
                Js.at(it, "instanceId") == id
            }
        )

    private fun execute(p: HostPage, c: HostCommand): HostReply =
        when (c) {
            is HostCommand.Today ->
                HostReply.Command(TodayCommands.run(p, c.name, payload(c.payload)))
            is HostCommand.Do -> HostReply.Command(DoCommands.run(p, c.name, payload(c.payload)))
            HostCommand.Boot -> HostReply.None.also { p.boot() }
            HostCommand.Tick -> HostReply.None.also { HostTick.tick(p) }
            HostCommand.SyncHost -> HostReply.Flag(HostTick.syncHost(p))
            HostCommand.ProcessMisses -> HostReply.Flag(HostTick.processMisses(p))
            HostCommand.MaybeRollover -> HostReply.Flag(HostTick.maybeRollover(p))
            HostCommand.RepairEarlyDone -> {
                Js.o(p.s["fixes"])?.remove("earlyDone")
                HostReply.Flag(HostCompletion.repairEarlyDone(p))
            }
            is HostCommand.ApplyDial -> {
                val change = Js.o(Js.of(c.change))
                p.pendingDial = change
                HostReply.Command(TimeCommands.applyDial(p, change, p.now))
            }
            is HostCommand.ApplyOutput ->
                HostReply.Flag(p.applyOutput(Js.o(Js.of(c.output)) ?: JsObj(), Js.of(c.label)))
            is HostCommand.Finish -> {
                val inst = planned(p, c.instanceId)
                if (inst == null) HostReply.Absent
                else
                    HostReply.Ledger(
                        HostCompletion.finish(p, inst, c.minutes, c.options).recorded?.json()
                    )
            }
            is HostCommand.LogToday -> {
                val inst = planned(p, c.instanceId)
                when {
                    inst == null -> HostReply.Absent
                    else -> {
                        val r = HostCompletion.logToday(p, inst, c.minutes, c.op)
                        if (r.duplicate) HostReply.Duplicate
                        else HostReply.Ledger(r.recorded?.json())
                    }
                }
            }
        }

    private fun page(s: HostState, host: HostEffects, nowMs: Double): HostPage =
        HostPage(host, nowMs).apply {
            st = Js.o(Js.of(s.st))
            banner = Js.o(Js.of(s.banner))
            notice = s.notice
            tab = s.tab
            dayIdx = s.dayIdx
            pendingDial = Js.o(Js.of(s.pendingDial))
            takeBack =
                s.takeBack?.let {
                    TakeBack(it.id, it.text, it.at, Js.o(Js.of(it.before)) ?: JsObj(), it.left)
                }
            takenBackOp = Js.of(s.takenBackOp)
            lastSave = s.lastSave
            lastFinishRefusal = s.lastFinishRefusal
        }

    private fun snapshot(p: HostPage): HostState =
        HostState(
            st = Js.json(p.st),
            banner = Js.json(p.banner),
            notice = p.notice,
            tab = p.tab,
            dayIdx = p.dayIdx,
            pendingDial = Js.json(p.pendingDial),
            takeBack =
                p.takeBack?.let {
                    TakeBackState(it.id, it.text, it.at, Js.value(it.before), it.left)
                },
            takenBackOp = Js.value(p.takenBackOp),
            lastSave = p.lastSave,
            lastFinishRefusal = p.lastFinishRefusal,
        )

    /** A reply as the page's value (for the golden comparison and a screen's bridge). */
    fun json(r: HostReply): JsonValue =
        when (r) {
            is HostReply.Command -> r.reply.json()
            is HostReply.Flag -> JsonBool.of(r.value)
            is HostReply.Ledger -> r.recorded ?: JsonNull
            HostReply.Duplicate -> JsonObject(mapOf("duplicate" to JsonBool.TRUE))
            HostReply.Absent -> com.lifemax.rpg.core.save.JsonString("absent")
            HostReply.None -> JsonNull
            is HostReply.Threw ->
                JsonObject(
                    linkedMapOf(
                        "threw" to com.lifemax.rpg.core.save.JsonString(r.name),
                        "message" to com.lifemax.rpg.core.save.JsonString(r.message),
                    )
                )
        }
}
