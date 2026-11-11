package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.PageZone
import com.lifemax.rpg.core.lifedirector.PlanHorizon
import com.lifemax.rpg.core.lifedirector.PlanPreferences
import com.lifemax.rpg.core.lifedirector.WallClock
import com.lifemax.rpg.core.lifedirector.ZoneNames
import com.lifemax.rpg.core.lifedirector.host.Js
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.list
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.same
import com.lifemax.rpg.core.lifedirector.host.Js.text
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Why the app would need to run at an instant (TM-80 to TM-88). [background] kinds change the saved
 * state when the page's code runs at or after them; the others are the page's foreground rhythm
 * (its polling interval, the dial's day, the vibration before a session), listed so a native screen
 * can keep the same rhythm.
 */
enum class WakeKind(val background: Boolean) {
    /**
     * TM-80: the running timer reaches its target; the check finishes it with the plan's minutes.
     */
    TIMER_END(true),

    /** TM-81: the first planned session to count as missed (its end plus the grace, strictly). */
    MISS(true),

    /** TM-84: light mode ends (`prefsLightUntil`, strictly after); the intensity comes back. */
    LIGHT_END(true),

    /** TM-82: a new local week starts (Monday 00:00 in the plan's zone): a weekly re-plan. */
    WEEK_ROLLOVER(true),

    /** TM-83: under two days of plan remain (strictly): a re-plan. */
    HORIZON_LOW(true),

    /** TM-88: a re-plan is due already (no plan, or the plan's zone is not the zone now). */
    REPLAN_NOW(true),

    /** TM-85 (Read): the local midnight that ends today's dial; the page ticks there. */
    DAY_END(false),

    /** TM-86 (Read): the page's next 5-minute tick, from when Life Director was mounted. */
    TICK(false),

    /** TM-87 (Read): 5 minutes before the next planned session, the page's vibration. */
    SESSION_ALERT(false),
}

/** One wake: its kind, the instant (epoch milliseconds) and the session it is for, if any. */
data class Wake(val kind: WakeKind, val atMs: Double, val sessionId: String? = null)

/** Every wake from a state at [nowMs], earliest first. */
data class WakePlan(val nowMs: Double, val wakes: List<Wake>) {
    /** The wakes due already (at or before now). */
    val due: List<Wake>
        get() = wakes.filter { it.atMs <= nowMs }

    /** The earliest wake that changes the saved state, or null. */
    val nextBackground: Wake?
        get() = wakes.firstOrNull { it.kind.background }

    /** The earliest wake of [kind], or null. */
    fun of(kind: WakeKind): Wake? = wakes.firstOrNull { it.kind == kind }
}

/**
 * The pure "what should wake the app" calculator (TM-80 to TM-88): given Life Director's state
 * (`ST` as JSON) and the wall clock, the instants at which the page's own code (the once-a-second
 * timer check, and the tick's misses and rollover) would change something, computed from the same
 * conditions the page tests, to the millisecond. The page tests each condition only when it runs
 * (the timer every second, the tick every 5 minutes, at the dial's midnight and on returning to the
 * app, and only while Life Director is on screen, D4), so its own change comes at the first run at
 * or after these instants. Host sync (activities logged in LifeMax) has no instant and is not here.
 * Nothing throws: a zone the page cannot read leaves out the wakes that need it.
 */
object Wakes {
    private const val SECOND_MS = 1000.0
    private const val MINUTE_S = 60.0
    private const val DAY_S = 86_400.0
    private const val LOW_HORIZON_DAYS = 2
    private const val DAYS_IN_WEEK = 7
    private const val TICK_MS = 300_000.0
    private const val ALERT_S = 300.0

    /**
     * The wakes of [st] at [nowMs]. [deviceZone] is the zone `tz()` falls back to; [tickAnchorMs],
     * when known, is when the page's 5-minute interval started.
     */
    fun of(
        st: JsonValue?,
        nowMs: Double,
        deviceZone: String,
        tickAnchorMs: Double? = null,
    ): WakePlan {
        val s = Js.of(st)
        val out = ArrayList<Wake>()
        if (!truthy(s)) return WakePlan(nowMs, out)
        val zoneName =
            at(at(s, "availability"), "timezone").let { if (truthy(it)) text(it) else deviceZone }
        val zone = ZoneNames.resolve(zoneName)
        val nowS = floor(nowMs / SECOND_MS)
        val schedule = at(s, "schedule").takeIf(::truthy)
        timerEnd(s, nowMs)?.let(out::add)
        if (schedule != null) miss(s, schedule)?.let(out::add)
        if (truthy(at(s, "setupComplete"))) {
            val light = at(s, "prefsLightUntil")
            if (truthy(light)) out += Wake(WakeKind.LIGHT_END, (floor(num(light)) + 1) * SECOND_MS)
            when {
                schedule == null -> out += Wake(WakeKind.REPLAN_NOW, nowMs)
                !same(at(schedule, "timezone"), zoneName) -> out += Wake(WakeKind.REPLAN_NOW, nowMs)
                else -> {
                    if (zone != null) out += week(s, nowS, nowMs, zone)
                    val low = num(at(schedule, "horizonEndEpoch")) - LOW_HORIZON_DAYS * DAY_S
                    if (nowS > low) out += Wake(WakeKind.HORIZON_LOW, nowMs)
                    else if (!low.isNaN())
                        out += Wake(WakeKind.HORIZON_LOW, (floor(low) + 1) * SECOND_MS)
                }
            }
        }
        if (schedule != null && zone != null)
            out += Wake(WakeKind.DAY_END, PlanHorizon.nextDayStart(nowS, zone) * SECOND_MS)
        if (tickAnchorMs != null && tickAnchorMs.isFinite())
            out += Wake(WakeKind.TICK, nextTick(tickAnchorMs, nowMs))
        if (schedule != null) alert(schedule, nowS, nowMs)?.let(out::add)
        val sorted =
            out.filter { !it.atMs.isNaN() }.sortedWith(compareBy({ it.atMs }, { it.kind.ordinal }))
        return WakePlan(nowMs, sorted)
    }

    /**
     * TM-80: `focusTick` finishes when `focusMs(f) >= target`, that is at `f.start + (f.pausedMs ||
     * 0) + target` while not paused; the target is `f.plannedMs`, else the length of the session of
     *    that id (planned or history). No target, or paused: none.
     */
    private fun timerEnd(s: Any?, nowMs: Double): Wake? {
        val f = at(at(s, "ui"), "focus")
        if (!truthy(f) || truthy(at(f, "paused"))) return null
        val id = at(f, "id")
        val sched = at(s, "schedule")
        val inst =
            if (!truthy(sched)) null
            else
                list(at(sched, "scheduledInstances")).firstOrNull { same(at(it, "instanceId"), id) }
                    ?: list(at(sched, "historyInstances")).firstOrNull {
                        same(at(it, "instanceId"), id)
                    }
        val plannedMs = at(f, "plannedMs")
        val target =
            when {
                truthy(plannedMs) -> num(plannedMs)
                inst != null -> TimerCommands.sessionMs(inst)
                else -> return null
            }
        if (!truthy(target)) return null
        val pausedMs = at(f, "pausedMs").let { if (truthy(it)) num(it) else 0.0 }
        val due = num(at(f, "start")) + pausedMs + target
        return Wake(WakeKind.TIMER_END, due, text(id))
    }

    /**
     * TM-81: `detectMisses` (`engine.js:2048`) takes a `PLANNED` session once `endEpoch + grace *
     * 60 < nowEpoch()`; the grace is the normalised preference (60 by default). The earliest
     * session.
     */
    private fun miss(s: Any?, schedule: Any?): Wake? {
        val grace = PlanPreferences.of(Js.json(at(s, "prefs"))).members["missGraceMinutes"]
        val g = num(Js.of(grace)).takeIf(::truthy) ?: DEFAULT_GRACE
        return list(at(schedule, "scheduledInstances"))
            .filter { at(it, "lifecycleState") == "PLANNED" }
            .map { i ->
                val edge = num(at(i, "endEpoch")) + g * MINUTE_S
                Wake(WakeKind.MISS, (floor(edge) + 1) * SECOND_MS, text(at(i, "instanceId")))
            }
            .filter { !it.atMs.isNaN() }
            .minByOrNull { it.atMs }
    }

    /**
     * TM-82: `maybeRollover` re-plans when `lastGeneratedWeekKey !== weekKey(now)`: now when the
     * key differs already, else at the next local Monday's midnight.
     */
    private fun week(s: Any?, nowS: Double, nowMs: Double, zone: PageZone): Wake {
        if (!same(at(s, "lastGeneratedWeekKey"), PlanHorizon.weekKey(nowS, zone)))
            return Wake(WakeKind.WEEK_ROLLOVER, nowMs)
        val next = CalendarMath.plusDays(PlanHorizon.monday(nowS, zone), DAYS_IN_WEEK)
        val at = next?.let { WallClock.instantOf(it, 0, zone) } ?: Double.NaN
        return Wake(WakeKind.WEEK_ROLLOVER, at * SECOND_MS)
    }

    /** TM-86: the page's `setInterval(tick, 300000)` from [anchorMs]: the next run after now. */
    fun nextTick(anchorMs: Double, nowMs: Double): Double {
        val k = maxOf(1.0, ceil((nowMs - anchorMs) / TICK_MS))
        val t = anchorMs + k * TICK_MS
        return if (t <= nowMs) t + TICK_MS else t
    }

    /** TM-87: five minutes before the next planned session that has not started. */
    private fun alert(schedule: Any?, nowS: Double, nowMs: Double): Wake? =
        list(at(schedule, "scheduledInstances"))
            .filter { at(it, "lifecycleState") == "PLANNED" && num(at(it, "startEpoch")) > nowS }
            .minByOrNull { num(at(it, "startEpoch")) }
            ?.let {
                val at = maxOf(nowMs, (num(at(it, "startEpoch")) - ALERT_S) * SECOND_MS)
                Wake(WakeKind.SESSION_ALERT, at, text(at(it, "instanceId")))
            }

    private const val DEFAULT_GRACE = 60.0
}
