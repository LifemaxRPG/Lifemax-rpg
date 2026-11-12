package com.lifemax.rpg.feature.pet.brain

import java.util.Locale
import org.junit.Test

/** Prints the roaming numbers for every time of day and state (the tuning is judged by them). */
class BehaviourReportTest {
    private val times =
        listOf(
            9.0 to "morning 09:00",
            14.0 to "afternoon 14:00",
            19.0 to "evening 19:00",
            1.0 to "night 01:00",
        )

    @Test
    fun report() {
        for (day in 0..2) {
            val t = BehaviourMeter.record(day = day)
            for ((h, label) in times) {
                println("== day $day $label")
                for (c in 0..1) println("   " + t.stats(c, h, h + 20.0 / 60).line())
            }
            println("== day $day awake daytime 08:00-21:00, every cat")
            for (c in 0..1) println("   " + t.stats(c, 8.0, 21.0).line())
            println("== day $day whole day")
            for (c in 0..1) println("   " + t.stats(c, 0.0, 24.0).line())
            for (c in 0..1) println("   ${t.names[c]} acts: " + actsOf(t, c))
        }
        val needs =
            listOf(
                BehaviourMeter.Needs(energy = 0.25, name = "tired"),
                BehaviourMeter.Needs(energy = 0.9, name = "rested"),
                BehaviourMeter.Needs(hunger = 3, name = "hungry"),
                BehaviourMeter.Needs(trust = 0.9, rel = 0.9, name = "close bond"),
                BehaviourMeter.Needs(trust = 0.2, rel = 0.15, name = "distant bond"),
            )
        for (n in needs) for ((h, label) in times) {
            println("== ${n.name} $label")
            for (c in BehaviourMeter.window(h, n)) println("   " + c.line())
        }
    }

    private fun actsOf(t: BehaviourMeter.Trace, c: Int): String =
        t.activityNames
            .map { it to t.seconds(c, it) / 3600 }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .joinToString(", ") { it.first + String.format(Locale.ROOT, " %.1fh", it.second) }
}

/** The quick look: three days, the awake daytime and the whole day, for tuning runs. */
class BehaviourDaysReport {
    @Test
    fun days() {
        for (day in 0..1) {
            val t = BehaviourMeter.record(day = day)
            println("== day $day awake daytime 08:00-21:00")
            for (c in 0..1) println("   " + t.stats(c, 8.0, 21.0).line())
            for (c in 0..1) {
                val acts =
                    t.activityNames
                        .map { it to t.seconds(c, it) / 3600 }
                        .filter { it.second >= 0.09 }
                        .sortedByDescending { it.second }
                        .joinToString(", ") {
                            it.first + String.format(Locale.ROOT, " %.1fh", it.second)
                        }
                println("   ${t.names[c]} acts: " + acts)
            }
        }
    }
}

/** The numbers RoamingTest's thresholds come from, printed for the old and the new brain alike. */
class BehaviourBeforeAfter {
    @Test
    fun numbers() {
        val days = (0..1).map { BehaviourMeter.record(day = it, startHour = 7.0, hours = 14.0) }
        for (cat in 0..1) {
            val name = days[0].names[cat]
            val active = days.map { it.stats(cat, 8.0, 21.0).activeShare }.average()
            val sleep =
                days.map { it.stats(cat, 8.0, 21.0).share(BehaviourMeter.Posture.SLEEP) }.average()
            fun windows(minutes: Int) =
                days.flatMap { t ->
                    (0 until 13 * 60 / minutes)
                        .map { 8.0 + it * minutes / 60.0 }
                        .map { t.stats(cat, it, it + minutes / 60.0) }
                        .filter { it.awake >= it.total * 0.9 }
                }
            val w10 = windows(10)
            val w20 = windows(20)
            println(
                String.format(
                    Locale.ROOT,
                    "%s: active %.0f%% of awake, daytime asleep %.0f%%, places/10min %.1f (%d windows), longestStill max %.0fs mean %.0fs, plays %.0fs",
                    name,
                    active * 100,
                    sleep * 100,
                    w10.sumOf { it.places.size } / w10.size.toDouble(),
                    w10.size,
                    w20.maxOfOrNull { it.longestStillS } ?: 0.0,
                    w20.sumOf { it.longestStillS } / w20.size,
                    days.sumOf { it.seconds(cat, "zoomies") + it.seconds(cat, "solo play") },
                )
            )
        }
    }
}
