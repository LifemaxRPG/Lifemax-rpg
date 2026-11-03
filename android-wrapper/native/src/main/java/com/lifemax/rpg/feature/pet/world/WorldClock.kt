package com.lifemax.rpg.feature.pet.world

import java.time.LocalDate

/** Where time comes from: the monotonic clock frames are stamped with, and the wall clock. */
interface TimeSource {
    fun monoNanos(): Long

    fun epochMs(): Long
}

/** The real clocks (JVM only: no Android type). */
object SystemTimeSource : TimeSource {
    override fun monoNanos(): Long = System.nanoTime()

    override fun epochMs(): Long = System.currentTimeMillis()
}

/** A clock that moves only when told: day replays, tests and the day viewer. */
class FakeTimeSource(var mono: Long = 0L, var epoch: Long = 0L) : TimeSource {
    override fun monoNanos() = mono

    override fun epochMs() = epoch

    /** Moves both clocks on by [ms]. */
    fun advanceMs(ms: Long) {
        mono += ms * NANOS_PER_MS
        epoch += ms
    }
}

/**
 * The world's one simulated clock (plan 1.2). Simulated time is a whole number of sub-steps of
 * 1/[STEPS_PER_SECOND] s since [anchorTo] and advances only through [advanceTo], which is
 * idempotent per timestamp: two views that report the same frame time cannot double-step, an older
 * time does nothing, and the sub-steps due are counted exactly (integer arithmetic on nanoseconds),
 * so a host at 60 Hz, 120 Hz or jittered frames reaches the same sub-step at the same simulated
 * moment. Every [THINK_EVERY]th sub-step is a think quantum (0.25 s).
 *
 * Wall time comes only from here: [epochMs] is the anchor's wall time plus simulated time plus the
 * page's dev shift ([clockOffsetMs]); [zoneOffsetMin] makes local hours and days. Re-anchoring (on
 * resume) is the one place the wall clock is read again; it answers the gap a catch-up covers.
 */
class WorldClock(
    private val source: TimeSource,
    var zoneOffsetMin: Int = 0,
    var clockOffsetMs: Long = 0L,
) {
    private var anchorMono = source.monoNanos()
    private var anchorEpoch = source.epochMs()
    private var lastMono = anchorMono
    private var baseSteps = 0L

    /** Sub-steps run since the world began (never reset by a re-anchor). */
    var subSteps = 0L
        private set

    /**
     * Runs one sub-step; [step] is the sub-step's number, [think] whether it is a think quantum.
     */
    fun interface SubStepListener {
        fun onSubStep(step: Long, think: Boolean)
    }

    /**
     * Brings simulated time up to [monoNanos], calling [each] once per whole sub-step now due, at
     * most [maxSteps] of them (a longer stall is left to the caller's catch-up); answers how many
     * ran. A time at or before the last one runs nothing.
     */
    fun advanceTo(
        monoNanos: Long,
        maxSteps: Int = MAX_LIVE_STEPS,
        each: SubStepListener? = null,
    ): Int {
        if (monoNanos <= lastMono) return 0
        lastMono = monoNanos
        val due = baseSteps + (monoNanos - anchorMono) * STEPS_PER_SECOND / NANOS_PER_SECOND
        var ran = 0
        while (subSteps < due && ran < maxSteps) {
            subSteps++
            ran++
            each?.onSubStep(subSteps, subSteps % THINK_EVERY == 0L)
        }
        if (subSteps < due) {
            // the rest of a long stall is skipped: simulated time joins the frame clock from here,
            // and the wall clock it shows lags by what was skipped until the caller re-anchors
            anchorTo(monoNanos, epochMs() - clockOffsetMs)
        }
        return ran
    }

    /**
     * Joins the frame clock at [monoNanos] and the wall clock at [epochMs] from here on (resume),
     * and answers the wall-clock gap since the simulated clock's own now: what a catch-up covers.
     */
    fun anchorTo(monoNanos: Long = source.monoNanos(), epochMs: Long = source.epochMs()): Long {
        val gap = epochMs + clockOffsetMs - this.epochMs()
        anchorMono = monoNanos
        anchorEpoch = epochMs
        lastMono = monoNanos
        baseSteps = subSteps
        return gap
    }

    /** Simulated milliseconds since the world began. */
    val simMs: Long
        get() = subSteps * MS_PER_SECOND / STEPS_PER_SECOND

    /** The world's wall time now (epoch ms), the page's dev shift included. */
    fun epochMs(): Long =
        anchorEpoch + (subSteps - baseSteps) * MS_PER_SECOND / STEPS_PER_SECOND + clockOffsetMs

    private fun localMs(): Long = epochMs() + zoneOffsetMin * MS_PER_MINUTE

    /** The local hour of day, 0 until 24, with its fraction. */
    fun hourOfDay(): Double = Math.floorMod(localMs(), MS_PER_DAY) / MS_PER_HOUR.toDouble()

    /** The local day as the page writes it (YYYY-MM-DD). */
    fun dayKey(): String = LocalDate.ofEpochDay(Math.floorDiv(localMs(), MS_PER_DAY)).toString()

    companion object {
        const val STEPS_PER_SECOND = 120L
        const val THINK_EVERY = 30L

        /** A frame never runs more than two seconds of sub-steps; more is a stall for catch-up. */
        const val MAX_LIVE_STEPS = 240

        private const val NANOS_PER_SECOND = 1_000_000_000L
        private const val MS_PER_SECOND = 1000L
        private const val MS_PER_MINUTE = 60_000L
        private const val MS_PER_HOUR = 3_600_000L
        private const val MS_PER_DAY = 86_400_000L
    }
}

private const val NANOS_PER_MS = 1_000_000L
