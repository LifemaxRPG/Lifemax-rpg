package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.DwellReport
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * Where one cat spent its time on one local day: seconds in the page's places (PET_SPOTS) and with
 * the page's toys (PET_TOYS), which the page turns into the cat's favourite place and toy (the
 * `dwell` command, work package C1). Counting allocates nothing: the ids are fixed, so each place
 * and toy has a slot. A report is made when [seconds] of dwell have gathered, when the day ends and
 * when the host asks ([flush]); whole seconds, never more than a day.
 */
internal class Dwell(private val cat: String) {
    private val spots = DoubleArray(SPOT_IDS.size)
    private val toys = DoubleArray(TOY_IDS.size)
    private var gathered = 0.0
    private var day = ""

    /**
     * [seconds] more on [dayKey] (a local date, "2026-11-12") at page place [spot] and with page
     * toy [toy] (either may be null: not at a known place, not with a toy). Answers the report of
     * the day just ended when [dayKey] is a new day, else of the stretch just filled, else null.
     */
    fun add(
        dayKey: String,
        spot: String?,
        toy: String?,
        seconds: Double,
        epochMs: Long,
    ): DwellReport? {
        var out: DwellReport? = null
        if (dayKey != day) {
            out = take(epochMs)
            day = dayKey
        }
        val s = if (spot == null) -1 else SPOT_IDS.indexOf(spot)
        val t = if (toy == null) -1 else TOY_IDS.indexOf(toy)
        if (s >= 0) spots[s] += seconds
        if (t >= 0) toys[t] += seconds
        if (s >= 0 || t >= 0) gathered += seconds
        if (out == null && gathered >= REPORT_EVERY_S) out = take(epochMs)
        return out
    }

    /** What has gathered so far, as a report (null when it comes to no whole second). */
    fun take(epochMs: Long): DwellReport? {
        if (day.isEmpty() || gathered <= 0.0) return null
        val sp = LinkedHashMap<String, Int>()
        val ty = LinkedHashMap<String, Int>()
        for (i in SPOT_IDS.indices) put(sp, SPOT_IDS[i], spots[i])
        for (i in TOY_IDS.indices) put(ty, TOY_IDS[i], toys[i])
        spots.fill(0.0)
        toys.fill(0.0)
        gathered = 0.0
        if (sp.isEmpty() && ty.isEmpty()) return null
        return DwellReport(OpIds.of(cat, "dwell", epochMs), cat, day, sp, ty)
    }

    private fun put(into: MutableMap<String, Int>, id: String, seconds: Double) {
        val whole = Math.round(seconds).toInt().coerceIn(0, DwellReport.DAY_SECONDS)
        if (whole > 0) into[id] = whole
    }

    companion object {
        /** A report once this many seconds have gathered (ten minutes). */
        const val REPORT_EVERY_S = 600.0

        val SPOT_IDS: List<String> = WorldContract.SPOTS.sorted()
        val TOY_IDS: List<String> = WorldContract.TOYS.sorted()
    }
}
