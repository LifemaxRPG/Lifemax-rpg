package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.world.WorldClock

/**
 * A trivial [Brain] so the day viewer runs before the real one exists (CB1 replaces it): each cat
 * follows a fixed timetable of the plan's activities by local hour, the second half an hour behind
 * the first, and eats when the owner feeds. It thinks only on think quanta, as the real one will.
 */
class StandInBrain(private val clock: WorldClock, override val names: List<String>) : Brain {
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

    override fun owner(act: OwnerAct) {
        if (act.kind == "feed") fedAt = act.atMs
    }

    private companion object {
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
