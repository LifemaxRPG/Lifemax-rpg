package com.lifemax.rpg.core.lifedirector

import kotlin.math.ceil
import kotlin.math.floor

/**
 * LDT-16, LDT-17: the plan's time line cut into 15-minute slots. Slot 0 starts at [startEpoch] (the
 * first day's start); there are [count] slots up to [endEpoch]. [localMinute] is, for each slot,
 * the wall-clock minute of the day its start shows, and [dayIndex] the [PlanDay.index] it belongs
 * to.
 */
class SlotGrid(
    val startEpoch: Double,
    val endEpoch: Double,
    val count: Double,
    val localMinute: IntArray,
    val dayIndex: IntArray,
) {
    /** LDT-17 `slotOf`: the slot [epochSeconds] falls in (rounded down; negative before slot 0). */
    fun slotAt(epochSeconds: Double): Double = floor((epochSeconds - startEpoch) / SLOT_SECONDS)

    /** LDT-17 `slotCeil`: the first slot that starts at or after [epochSeconds]. */
    fun slotAtOrAfter(epochSeconds: Double): Double =
        ceil((epochSeconds - startEpoch) / SLOT_SECONDS)

    /** LDT-17 `epochOf`: the instant [slot] starts. */
    fun epochAt(slot: Double): Double = startEpoch + slot * SLOT_SECONDS

    companion object {
        /** LDT-1 `SLOT_SEC`: a slot is 900 seconds, 15 minutes. */
        const val SLOT_SECONDS: Double = 900.0

        /** LDT-1 `SLOTS_PER_HOUR`. */
        const val SLOTS_PER_HOUR: Int = 4

        private const val SLOT_MINUTES = 15

        /** A grid with only a start, for the slot arithmetic of LDT-17. */
        fun startingAt(startEpoch: Double): SlotGrid =
            SlotGrid(startEpoch, startEpoch, 0.0, IntArray(0), IntArray(0))

        /**
         * LDT-16 `createGrid`: the slots of [days] (from [PlanHorizon.days]) in [zone]. A day whose
         * offset is the same at its start and in its last second gives its slots the minutes 0, 15,
         * 30 ... in order; any other day (a clock change) gives each slot the wall clock of its
         * start, so a fold repeats an hour and a gap skips one.
         */
        fun of(days: List<PlanDay>, zone: PageZone): SlotGrid {
            val start = days.first().startEpoch
            val end = days.last().endEpoch
            val count = (end - start) / SLOT_SECONDS
            val minutes = IntArray(count.toInt())
            val owner = IntArray(count.toInt())
            for (day in days) {
                val first = ((day.startEpoch - start) / SLOT_SECONDS).toInt()
                val steady =
                    WallClock.offsetSeconds(day.startEpoch, zone) ==
                        WallClock.offsetSeconds(day.endEpoch - 1, zone)
                for (k in 0 until day.slots.toInt()) {
                    val slot = first + k
                    owner[slot] = day.index
                    minutes[slot] =
                        if (steady) k * SLOT_MINUTES
                        else WallClock.localParts(start + slot * SLOT_SECONDS, zone).minuteOfDay
                }
            }
            return SlotGrid(start, end, count, minutes, owner)
        }
    }
}
