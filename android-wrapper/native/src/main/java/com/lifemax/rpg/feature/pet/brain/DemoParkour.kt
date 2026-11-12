package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.ParkourPlanner
import com.lifemax.rpg.feature.pet.body.Rng

/**
 * DEVELOPER DEMO (owner request, shown on the Cat tab only while the page's developer mode is on):
 * one awake cat runs a tour of parkour visits now, instead of waiting for the odd one the selector
 * picks. It is the brain's own [ParkourVisit] played stop by stop through the runner (so the place
 * book, the cats-apart rules, the longest-activity watchdog and the way there are all the usual
 * ones), started like [OwnerPlay] by a reflex ([CatMind.startDemo]); only the first stop is
 * interrupted into, the next ones follow from [ActivityRunner]'s `following`.
 *
 * It has NO data effects: nothing is reported to the page (no `play`, no XP, no care counters, no
 * pair or likes writes); the cat's own drives and mood move as in any parkour visit, and what
 * normal activity persistence saves in the world blob is all that is saved.
 */
internal object DemoParkour {
    /** The explain ring's and the runner's reason for a demo stop. */
    const val REASON = "developer demo: parkour"

    /** Words shown when the demo cannot start ([PlayWords.NAME] stands for the cat's name). */
    const val HUNGRY = "${PlayWords.NAME} is too hungry for parkour. A meal first."
    const val EATING = "${PlayWords.NAME} is eating."
    const val ASLEEP = "${PlayWords.NAME} is asleep."
    const val PLAYING = "${PlayWords.NAME} is in the middle of a game."
    const val BUSY = "${PlayWords.NAME} is busy right now."
    const val TIRED = "${PlayWords.NAME} is too sleepy for parkour."
    const val RUNNING = "A parkour demo is already running."
    const val NO_PERCH = "${PlayWords.NAME} has no free perch to climb to."

    /** Why cat [m], now playing [activity], will not run the demo, or null when it will. */
    fun refusal(m: CatMind, activity: Activity): String? =
        when {
            m.persona.weak -> HUNGRY
            activity === Eat -> EATING
            activity.asleep -> ASLEEP
            activity === OwnerPlay -> PLAYING
            activity === MakeWay || activity === Beg -> BUSY
            m.drives[Drive.ENERGY] < BrainTuning.Play.TIRED_ENERGY -> TIRED
            else -> null
        }

    private val IDLE = setOf("loaf", "explore", "wander", "window watch", "groom", "knead")

    /** 0 for a cat doing nothing much (the demo's first choice), 1 for one doing something. */
    fun busyRank(activity: Activity): Int = if (activity.id in IDLE) 0 else 1

    /**
     * The tour for [m]: up to [BrainTuning.Demo.STOPS] distinct parkour tops free of the other cat,
     * each chosen as the one the planner needs the most jumps to reach from the one before (the
     * first from where the cat stands); empty when no perch can be reached.
     */
    fun tour(m: CatMind): List<Spot> {
        val cap = m.exec.capability
        val seed = Rng.hash(m.profile.id)
        var surface = m.exec.surface
        var x = m.exec.x
        val rest =
            Spots.all.filter {
                it.open && it.has(Spots.PARKOUR) && m.book.free(m.slot, it, it.x, 0)
            }
        val remaining = ArrayList(rest)
        val stops = ArrayList<Spot>()
        while (stops.size < BrainTuning.Demo.STOPS && remaining.isNotEmpty()) {
            var best: Spot? = null
            var bestJumps = 0
            for (s in remaining) {
                if (s.surface == surface) continue
                val r = ParkourPlanner.plan(cap, surface, x, s.surface, s.x, seed) ?: continue
                if (r.leaps.size > bestJumps) {
                    best = s
                    bestJumps = r.leaps.size
                }
            }
            val next = best ?: break
            stops += next
            remaining.remove(next)
            surface = next.surface
            x = next.x
        }
        return stops
    }
}
