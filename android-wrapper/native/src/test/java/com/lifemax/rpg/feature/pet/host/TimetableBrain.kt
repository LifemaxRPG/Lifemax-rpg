package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.brain.Brain
import com.lifemax.rpg.feature.pet.brain.CatchPlace
import com.lifemax.rpg.feature.pet.brain.CatchUp
import com.lifemax.rpg.feature.pet.brain.Doing
import com.lifemax.rpg.feature.pet.brain.OwnerAct
import com.lifemax.rpg.feature.pet.world.WorldClock

/**
 * A trivial [Brain] for the host's tests (the stand-in CB0 shipped; the real brain is
 * [com.lifemax.rpg.feature.pet.brain.CatWorld]): each cat follows a fixed timetable of the plan's
 * activities by local hour, the second half an hour behind the first, and eats when the owner
 * feeds. Its catch-up is a pure function of the clock and the gap, so the host's catch-up and frame
 * tests can assert exact places and lines. It thinks only on think quanta.
 */
open class TimetableBrain(protected val clock: WorldClock, override val names: List<String>) :
    Brain {
    private val doing = Array(names.size) { Doing("sleep", "bed", 0.0, "bed hours") }
    private var fedAt = Long.MIN_VALUE

    override fun step(step: Long, think: Boolean) {
        if (!think) return
        for (slot in names.indices) {
            val hour = clock.hourOfDay() - slot * LAG_H
            val sinceMeal = clock.simMs - fedAt
            doing[slot] =
                if (sinceMeal in 0 until MEAL_MS) Doing("eat", "bowl", 1.0, "fed")
                else TIMETABLE.last { hour >= it.first || it === TIMETABLE.first() }.second
        }
    }

    override fun now(slot: Int) = doing[slot]

    /**
     * The stand-in's catch-up: where the timetable puts each cat when the gap ends (at most a day)
     * and a line for each quarter of the gap. A pure function of the clock and the gap.
     */
    override fun catchUp(gapMs: Long): CatchUp {
        val gap = gapMs.coerceIn(0L, MAX_GAP_MS)
        val startH = clock.hourOfDay()
        val places =
            names.indices.map { slot ->
                val d = at(startH + gap / HOUR_MS.toDouble() - slot * LAG_H)
                CatchPlace(slot, SURFACE[d.place] ?: d.place, null, null, 1, d.activity)
            }
        val lines =
            listOf(QUARTER_1, QUARTER_2, QUARTER_3).map { q ->
                val d = at(startH + gap * q / HOUR_MS.toDouble())
                "${names[0]} ${VERB[d.activity] ?: d.activity} on the ${d.place}"
            }
        return CatchUp(places, lines.distinct())
    }

    /** The timetable's row for local hour [h] (any number of hours, wrapped to a day). */
    private fun at(h: Double): Doing {
        val hour = ((h % 24.0) + 24.0) % 24.0
        return TIMETABLE.last { hour >= it.first || it === TIMETABLE.first() }.second
    }

    override fun owner(act: OwnerAct) {
        if (act.kind == "feed") fedAt = act.atMs
    }

    private companion object {
        const val HOUR_MS = 3_600_000L
        const val MAX_GAP_MS = 24 * HOUR_MS
        const val QUARTER_1 = 0.25
        const val QUARTER_2 = 0.5
        const val QUARTER_3 = 0.75
        val SURFACE = mapOf("tree" to "top")
        val VERB =
            mapOf(
                "sleep" to "slept",
                "loaf" to "loafed",
                "groom" to "washed",
                "window watch" to "watched the window",
            )
        const val LAG_H = 0.5
        const val MEAL_MS = 300_000L // a stand-in meal lasts long enough to show in a minute sample

        /** From each hour on, until the next (the first row also covers the hours before it). */
        val TIMETABLE =
            listOf(
                0.0 to Doing("sleep", "bed", 0.2, "bed hours"),
                7.0 to Doing("waking routine", "bed", 0.9, "wake hour"),
                7.1 to Doing("loaf", "couch", 0.5, "comfort"),
                10.0 to Doing("window watch", "lsill", 0.7, "daylight"),
                12.0 to Doing("groom", "couch", 0.6, "after a meal"),
                13.0 to Doing("sleep", "tree", 0.3, "afternoon nap"),
                15.75 to Doing("wander", "floor", 0.6, "curiosity"),
                17.0 to Doing("parkour visit", "fridge", 0.7, "territory"),
                19.0 to Doing("zoomies", "floor", 0.9, "dusk"),
                19.2 to Doing("loaf", "cushion", 0.5, "comfort"),
                22.0 to Doing("sleep", "bed", 0.2, "bed hours"),
            )
    }
}
