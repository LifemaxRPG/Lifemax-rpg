package com.lifemax.rpg.core.lifedirector.timer

import com.lifemax.rpg.core.lifedirector.host.Js
import com.lifemax.rpg.core.lifedirector.host.Js.at
import com.lifemax.rpg.core.lifedirector.host.Js.num
import com.lifemax.rpg.core.lifedirector.host.Js.same
import com.lifemax.rpg.core.lifedirector.host.Js.truthy
import com.lifemax.rpg.core.save.JsonValue
import kotlin.math.abs

/**
 * One reading of the device's two clocks (TM-73): the wall clock the page reads (`Date.now()`,
 * `System.currentTimeMillis()`), which the owner or the network can set, and a monotonic clock that
 * only moves forward while the device runs (`SystemClock.elapsedRealtime()`, counting deep sleep),
 * with the boot it belongs to ([boot], for example `Settings.Global.BOOT_COUNT`): a monotonic
 * reading means nothing in another boot.
 */
data class ClockReading(val wallMs: Double, val monoMs: Double, val boot: Long)

/**
 * The monotonic record a native timer would keep beside the page's `ST.ui.focus` (TM-73): the boot,
 * the monotonic start, when it was paused (null: running) and the total paused time, all by the
 * monotonic clock. The page's record stays the authority for what is saved; this one only measures.
 */
data class MonoFocus(
    val boot: Long,
    val startMono: Double,
    val pausedAtMono: Double?,
    val pausedTotalMs: Double,
)

/**
 * The elapsed time of a timer by both clocks (TM-73): [pageMs], the page's `focusMs` (wall clock),
 * and [monoMs] by the monotonic record (null when there is none, or it is from another boot).
 */
data class FocusElapsed(val pageMs: Double, val monoMs: Double?) {
    /** How far the wall clock moved apart from the monotonic one since the start (ms). */
    val clockShiftMs: Double?
        get() = monoMs?.let { pageMs - it }

    /** Whether the wall clock was changed by more than [toleranceMs] while the timer ran. */
    fun clockChanged(toleranceMs: Double = DEFAULT_TOLERANCE_MS): Boolean =
        clockShiftMs?.let { abs(it) > toleranceMs } ?: false

    /** The elapsed time to trust: the monotonic one when there is one, else the page's. */
    val trustedMs: Double
        get() = monoMs ?: pageMs

    companion object {
        /** Two seconds: more than a network time correction usually moves the clock. */
        const val DEFAULT_TOLERANCE_MS = 2000.0
    }
}

/**
 * The timer on a monotonic clock as well as the page's wall clock (TM-73, decision D7). Pure: every
 * reading is a parameter. [follow] keeps a [MonoFocus] in step with the page's timer by comparing
 * the timer before and after a command (a new start, a pause, a resume, a stop), so the page's
 * state machine ([TimerCommands]) stays the only one; [elapsed] and [endAtWallMs] then answer by
 * both clocks.
 */
object FocusClock {
    /** A record started at [r], running (or paused there when [paused]). */
    fun begin(r: ClockReading, paused: Boolean = false): MonoFocus =
        MonoFocus(r.boot, r.monoMs, if (paused) r.monoMs else null, 0.0)

    /**
     * The record after a command took the page's timer from [before] to [after] (`ST.ui.focus`, as
     * JSON) at [r]: none when the timer is gone; a new record when its id or start changed (a
     * start, a replacement, a reset, which leaves it paused); paused or resumed when only that
     * changed; otherwise [record] as it was. A record from another boot is replaced by a fresh one,
     * which then measures only from [r].
     */
    fun follow(
        record: MonoFocus?,
        before: JsonValue?,
        after: JsonValue?,
        r: ClockReading,
    ): MonoFocus? {
        val b = Js.of(before)
        val a = Js.of(after)
        if (!truthy(a)) return null
        val paused = truthy(at(a, "paused"))
        val fresh =
            !truthy(b) ||
                !same(at(a, "id"), at(b, "id")) ||
                !same(at(a, "start"), at(b, "start")) ||
                record == null ||
                record.boot != r.boot
        if (fresh) return begin(r, paused)
        val wasPaused = truthy(at(b, "paused"))
        return when {
            paused && !wasPaused -> record!!.copy(pausedAtMono = r.monoMs)
            !paused && wasPaused ->
                record!!.copy(
                    pausedAtMono = null,
                    pausedTotalMs =
                        record.pausedTotalMs + (r.monoMs - (record.pausedAtMono ?: r.monoMs)),
                )
            else -> record
        }
    }

    /** The elapsed time of the page's timer [focus] by both clocks at [r]. */
    fun elapsed(focus: JsonValue?, record: MonoFocus?, r: ClockReading): FocusElapsed {
        val f = Js.of(focus)
        val page = TimerCommands.focusMs(f, r.wallMs)
        val mono =
            record
                ?.takeIf { it.boot == r.boot }
                ?.let {
                    maxOf(0.0, (it.pausedAtMono ?: r.monoMs) - it.startMono - it.pausedTotalMs)
                }
        return FocusElapsed(page, mono)
    }

    /**
     * The wall-clock instant (ms) at which the timer reaches [targetMs] by the trusted clock, for
     * an alarm: now plus what is left; null while paused. By the page's clock alone this is
     * [Wakes]' `TIMER_END`; the two differ by the clock shift.
     */
    fun endAtWallMs(
        focus: JsonValue?,
        record: MonoFocus?,
        targetMs: Double,
        r: ClockReading,
    ): Double? {
        val f = Js.of(focus)
        if (!truthy(f) || truthy(at(f, "paused"))) return null
        val e = elapsed(focus, record, r)
        return r.wallMs + maxOf(0.0, targetMs - e.trustedMs)
    }

    /** The page's target for [focus] (`plannedMs`, when set): null when it has none. */
    fun plannedMs(focus: JsonValue?): Double? =
        at(Js.of(focus), "plannedMs").takeIf(::truthy)?.let(::num)
}
