package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.room.RoomLight

/**
 * One cat's day (plan 1.6), drawn afresh at each local midnight from the day seed
 * `hash("<catId>|<dayKey>|sched")`, so it depends on the cat's world id and the date and never on
 * its name: a wake hour 6.0..7.4 (later for a lazy cat), a bed hour 22.0..23.5, a dusk peak, and
 * two to four nap windows weighted to the early afternoon. The schedule never chooses an activity:
 * it gives factors the selector multiplies in ([sleepFactor], [activeFactor], [sunny], [daylight],
 * [lamps]). Nothing here allocates except [plan].
 */
class Schedule {
    private val s = BrainTuning.Schedule

    /** Hours of the current day (local, with fraction). */
    var wake = 7.0
        private set

    var bed = 22.5
        private set

    var dusk = 18.5
        private set

    private val napFrom = DoubleArray(s.NAPS_MAX * 2)
    private val napTo = DoubleArray(s.NAPS_MAX * 2)
    private var naps = 0

    /** How many nap windows today (doubled on a rest day). */
    val napCount: Int
        get() = naps

    /** Draws the day [dayKey] for cat [catId] with laziness [lazy]; [restDay] doubles the naps. */
    fun plan(catId: String, dayKey: String, lazy: Double, restDay: Boolean) {
        val rng = Rng(Rng.hash("$catId|$dayKey|sched"))
        wake = rng.range(s.WAKE_LO_H.toFloat(), s.WAKE_HI_H.toFloat()) + s.WAKE_LAZY_H * lazy
        bed = rng.range(s.BED_LO_H.toFloat(), s.BED_HI_H.toFloat()).toDouble()
        dusk = rng.range(s.DUSK_LO_H.toFloat(), s.DUSK_HI_H.toFloat()).toDouble()
        val count = s.NAPS_MIN + (rng.nextFloat() * (s.NAPS_MAX - s.NAPS_MIN + 1)).toInt()
        naps = 0
        repeat(if (restDay) count * 2 else count) {
            val start = weightedStart(rng) + rng.range(-HALF_HOUR, HALF_HOUR)
            val len = rng.range(s.NAP_LEN_LO_H.toFloat(), s.NAP_LEN_HI_H.toFloat())
            napFrom[naps] = start
            napTo[naps] = start + len
            naps++
        }
    }

    private fun weightedStart(rng: Rng): Double {
        var total = 0.0
        for (w in s.NAP_WEIGHTS) total += w
        var roll = rng.nextFloat() * total
        for (i in s.NAP_STARTS.indices) {
            roll -= s.NAP_WEIGHTS[i]
            if (roll < 0) return s.NAP_STARTS[i]
        }
        return s.NAP_STARTS.last()
    }

    /** Bed hours: from [bed] to [wake] the next morning. */
    fun inBed(hour: Double): Boolean = hour >= bed || hour < wake

    /** Inside one of today's nap windows. */
    fun inNap(hour: Double): Boolean {
        for (i in 0 until naps) if (hour >= napFrom[i] && hour < napTo[i]) return true
        return false
    }

    /** 0..1: how near the dawn (just after waking) or dusk activity peak [hour] is. */
    fun peak(hour: Double): Double {
        val dawn = Curves.bell(hour, wake + s.DAWN_HALF_H, s.DAWN_HALF_H)
        val evening = Curves.bell(hour, dusk, s.DUSK_HALF_H)
        return maxOf(dawn, evening)
    }

    /** The schedule's factor on sleep: ×3 in bed hours, ×1.8 in a nap window, less at a peak. */
    fun sleepFactor(hour: Double): Double {
        val base =
            when {
                inBed(hour) -> s.SLEEP_IN_BED
                inNap(hour) -> s.SLEEP_IN_NAP
                else -> 1.0
            }
        return base * Curves.lerp(1.0, s.SLEEP_AT_PEAK, peak(hour)) * lamps(hour)
    }

    /** The schedule's factor on getting about (wander, parkour): up at the peaks, down at night. */
    fun activeFactor(hour: Double): Double {
        val night = if (inBed(hour)) s.ACTIVE_IN_BED else 1.0
        return night * Curves.lerp(1.0, s.ACTIVE_AT_PEAK, peak(hour))
    }

    /** A sunny spot's factor: ×1.8 from 10 to 16 h. */
    fun sunny(hour: Double): Double =
        if (hour >= s.SUNNY_FROM_H && hour < s.SUNNY_UNTIL_H) s.SUNNY_GAIN else 1.0

    /** Something to see out of the window: 1 by day, less in the dark, more at the peaks. */
    fun daylight(hour: Double): Double {
        val dark = if (RoomLight.isNight(hour)) s.WINDOW_DARK else 1.0
        return dark * Curves.lerp(1.0, s.ACTIVE_AT_PEAK, peak(hour))
    }

    /** Lamps on (the room's evening light): cats settle, ×1..1.25 on sleep. */
    fun lamps(hour: Double): Double =
        Curves.lerp(1.0, s.LAMPS_SETTLE, RoomLight.lampAlpha(RoomLight.tint(hour)) / LAMP_MAX)

    /** Hours from [hour] until the next wake (for a night's sleep). */
    fun hoursToWake(hour: Double): Double = if (hour < wake) wake - hour else HOURS - hour + wake

    private companion object {
        const val HALF_HOUR = 0.5f
        const val LAMP_MAX = 0.32
        const val HOURS = 24.0
    }
}
