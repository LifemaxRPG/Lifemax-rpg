package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.SurfaceId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mix of a cat's days by character (plan 4.1 "sanity bounds"), over several seeds (the cat's
 * world id and the date) and days, in fast mode (the same selector, drives and schedule as live;
 * the scripts are timed steps): an adult sleeps 12..16 h, a kitten 14..18 h; a lazy cat sleeps more
 * than a spontaneous one, a playful one gets about more, a shy one keeps more to hidden and low
 * places; an outgoing cat watches a window and visits a high place every day; besides resting no
 * awake activity takes more than 35 % of the awake time; a cat on vacation never begs and eats the
 * sitter's meals.
 */
class PersonaMixTest {
    /**
     * Hours per activity per simulated day, and the share of awake time in hidden or low places.
     */
    private class Mix(val days: Int) {
        val hours = HashMap<String, Double>()
        var hiddenOrLow = 0.0
        var awake = 0.0
        val perDay = ArrayList<MutableSet<String>>()

        fun h(a: String) = (hours[a] ?: 0.0) / days

        override fun toString() =
            hours.entries
                .sortedByDescending { it.value }
                .joinToString(", ") {
                    it.key + " " + String.format(Locale.ROOT, "%.1f", it.value / days)
                } + String.format(Locale.ROOT, " | low %.2f", hiddenOrLow / awake)
    }

    private fun mix(
        persona: Map<String, Double>,
        seeds: Int = 4,
        days: Int = 3,
        stage: Int = -1,
        acts: (Int) -> List<OwnerAct> = { CatDays.owner(hours = 24 * days) },
        vacation: Boolean = false,
    ): Mix {
        val mix = Mix(seeds * days)
        for (seed in 0 until seeds) {
            val cat =
                CatDays.cat("Cat", persona, id = "cseed$seed", stage = stage)
                    .copy(vacation = vacation)
            val start = CatDays.MIDNIGHT + seed * 7 * 24 * CatDays.HOUR
            var last: Doing? = null
            CatDays.run(
                listOf(cat),
                dailyActs(acts(seed), days),
                hours = 24.0 * days,
                start = start,
                fidelity = CatWorld.Fidelity.FAST,
            ) { world, step, think ->
                if (!think) return@run
                val d = world.now(0)
                if (d !== last) {
                    last = d
                    val day = (step / (SAMPLE_STEPS * 60 * 24)).toInt()
                    while (mix.perDay.size <= seed * days + day) mix.perDay += HashSet<String>()
                    mix.perDay[seed * days + day] += d.activity
                }
                if (step % SAMPLE_STEPS != 0L) return@run
                mix.hours[d.activity] = (mix.hours[d.activity] ?: 0.0) + 1.0 / 60
                if (d.activity != "sleep") {
                    mix.awake += 1.0
                    val m = world.minds[0]
                    val here = m.here
                    val low =
                        m.surface == SurfaceId.FLOOR ||
                            here?.low == true ||
                            here?.has(Spots.HIDDEN) == true
                    if (low) mix.hiddenOrLow += 1.0
                }
            }
        }
        return mix
    }

    /** The owner's day repeated for every day of the run (meals and visits each day). */
    private fun dailyActs(oneDay: List<OwnerAct>, days: Int): List<OwnerAct> {
        val day = 24 * CatDays.HOUR
        val first = oneDay.filter { it.atMs < day }
        return (0 until days).flatMap { k -> first.map { OwnerAct(it.atMs + k * day, it.kind) } }
    }

    private val lazy by lazy { mix(CatDays.LAZY) }
    private val playful by lazy { mix(CatDays.PLAYFUL) }
    private val shy by lazy { mix(CatDays.SHY) }

    @Test
    fun adultsSleepTwelveToSixteenHoursAndTheLazyOnesMore() {
        for ((name, m) in listOf("lazy" to lazy, "playful" to playful, "shy" to shy)) {
            println("$name: $m")
            assertTrue("$name sleeps ${m.h("sleep")} h", m.h("sleep") in 12.0..16.0)
        }
        assertTrue(
            "lazy ${lazy.h("sleep")} vs playful ${playful.h("sleep")}",
            lazy.h("sleep") >= playful.h("sleep") + 1.5,
        )
    }

    @Test
    fun aPlayfulCatGetsAboutMoreAndAShyOneKeepsLow() {
        fun about(m: Mix) = m.h("wander") + m.h("window watch") + m.h("parkour visit")
        assertTrue(
            "playful ${about(playful)} vs lazy ${about(lazy)}",
            about(playful) > about(lazy) * 1.3,
        )
        val shyLow = shy.hiddenOrLow / shy.awake
        val playfulLow = playful.hiddenOrLow / playful.awake
        assertTrue("shy $shyLow vs playful $playfulLow", shyLow > playfulLow + 0.1)
    }

    @Test
    fun anOutgoingCatWatchesAWindowAndClimbsEveryDay() {
        for ((i, day) in playful.perDay.withIndex()) {
            assertTrue("day $i: $day", "window watch" in day && "parkour visit" in day)
        }
    }

    @Test
    fun noAwakeActivityButRestTakesOverTheDay() {
        for (m in listOf(lazy, playful, shy)) {
            val awake = m.awake / 60 / m.days
            for ((a, h) in m.hours) {
                if (a == "sleep") continue
                val share = h / m.days / awake
                val bound = if (a == "loaf") LOAF_SHARE else ACTIVITY_SHARE
                assertTrue(
                    "$a takes ${(share * 100).toInt()} % of the awake time: $m",
                    share <= bound,
                )
            }
        }
    }

    @Test
    fun aKittenSleepsMoreInShorterBouts() {
        val kitten = mix(CatDays.PLAYFUL, stage = 1)
        println("kitten: $kitten")
        assertTrue("kitten sleeps ${kitten.h("sleep")} h", kitten.h("sleep") in 14.0..18.0)
    }

    @Test
    fun onVacationNobodyBegsAndTheSitterFeeds() {
        val m =
            mix(
                CatDays.AVERAGE,
                seeds = 2,
                days = 2,
                acts = {
                    listOf(OwnerAct(0, "hunger:0:3")) +
                        CatDays.owner(meals = emptyList()).filter { !it.kind.startsWith("hunger") }
                },
                vacation = true,
            )
        assertEquals(0.0, m.h("beg"), 0.0)
        assertTrue("ate every day: ${m.perDay}", m.perDay.all { "eat" in it })
    }

    private companion object {
        /** A sample a minute: 60 s of 120 sub-steps. */
        const val SAMPLE_STEPS = 60L * 120

        /**
         * Plan 4.1's 35 % for any awake activity; resting is the floor of a CB1 day (deviation).
         */
        const val ACTIVITY_SHARE = 0.35
        const val LOAF_SHARE = 0.8
    }
}
