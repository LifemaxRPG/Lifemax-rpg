package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.world.FedCue
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * The page's `fed` cues (the world block's `cue.fed`, plan 2.1) as the world plays them: each meal
 * the page served, by whichever route (the Cat tab's Feed, the page's own Cat view, the voice, a
 * deep link), sends its cat to its bowl once, whichever screen draws the world (the Overview card's
 * included) and however the world was just started (a restore, a rebuild). The Cat tab's own
 * reaction to the same meal ([CatWorldHost.ownerAct] `feed:<slot>`) may come too: a cat already
 * called to its meal is not called again.
 *
 * Meals play only while a view shows the world. When the first view comes, after none, or when the
 * page counts its meals from one again (it reloaded: a cue with an id already played but served at
 * another time), only a meal served moments ago ([HostTuning.FRESH_MEAL_MS]: the Cat tab opened for
 * it) plays, never an old one the page still lists.
 */
internal class WorldCues {
    private var played = 0L
    private var baselined = false
    private val ids = LongArray(KEPT)
    private val servedAt = LongArray(KEPT)
    private var kept = 0

    /** No view shows the world any more: the next one plays only fresh meals. */
    fun hidden() {
        baselined = false
    }

    /**
     * Plays the meals of [cues] not played yet, each with [feed] (its cat's slot): the block was
     * taken at [takenAt] (its cues' ages are from then), and it is [now] (epoch ms).
     */
    fun take(cues: List<FedCue>, takenAt: Long, now: Long, feed: (Int) -> Unit) {
        if (cues.any { it.id <= played && another(it.id, takenAt - it.agoMs) }) {
            played = 0L // a reloaded page counts its meals from one again
            kept = 0
            baselined = false
        }
        for (c in cues) {
            if (c.id <= played) continue
            played = c.id
            val at = takenAt - c.agoMs
            keep(c.id, at)
            if (baselined || now - at <= HostTuning.FRESH_MEAL_MS) feed(c.cat)
        }
        baselined = true
    }

    /** Whether meal [id], served at [at], is not the meal of that id this world played. */
    private fun another(id: Long, at: Long): Boolean {
        for (i in 0 until kept) if (ids[i] == id) return Math.abs(servedAt[i] - at) > SAME_MEAL_MS
        return false
    }

    private fun keep(id: Long, at: Long) {
        if (kept == KEPT) {
            System.arraycopy(ids, 1, ids, 0, KEPT - 1)
            System.arraycopy(servedAt, 1, servedAt, 0, KEPT - 1)
            kept--
        }
        ids[kept] = id
        servedAt[kept] = at
        kept++
    }

    private companion object {
        /** The meals remembered: as many as a block lists. */
        const val KEPT = WorldContract.MAX_FED_CUES

        /** Two sightings of one meal agree on when it was served to within this (ages are ms). */
        const val SAME_MEAL_MS = 2_000L
    }
}
