package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.RouteLeap
import com.lifemax.rpg.feature.pet.body.SurfaceId

/**
 * Who has which place, and how much each cat likes each place (plan 1.5 SpotBook).
 *
 * Reservations. A cat [claim]s its activity's place at pick time, with the raised surfaces its
 * route lands on ([passes], a bit mask of [SurfaceId.ordinal]); it keeps the place it rests at
 * ([stay]) until it has physically left it. A place is [free] for a cat when no other cat claims or
 * stays on it, when no other cat's route lands on its surface and its own route lands on no surface
 * the other cat holds, and, on the floor, when it keeps [BrainTuning.Places.FLOOR_GAP] from the
 * other cat's floor places (the two bowls excepted: they eat side by side). So two cats never share
 * a single-occupancy perch, on the way or at the top.
 *
 * Memory. [like] starts from the page's likings (PET_SPOTS shares of the leader) and learns from
 * dwell time ([learn]); the selector multiplies it in.
 */
class SpotBook(private val cats: Int) {
    private val claimSpot = IntArray(cats) { NONE }
    private val claimX = FloatArray(cats)
    private val staySpot = IntArray(cats) { NONE }
    private val stayX = FloatArray(cats)
    private val passMask = IntArray(cats)
    private val visits = Array(cats) { IntArray(BrainTuning.Roam.REVISIT.size) { NONE } }
    private val likes = Array(cats) { DoubleArray(Spots.count) { BrainTuning.Places.DEFAULT_LIKE } }

    /** Seeds cat [slot]'s likings from the page's (0..100 by PET_SPOTS id). */
    fun seedLikes(slot: Int, page: Map<String, Int>) {
        for (s in Spots.all) {
            val v = s.pageId?.let { page[it] } ?: continue
            likes[slot][s.index] = (v / PERCENT).coerceIn(0.0, 1.0)
        }
    }

    /** How much cat [slot] likes [spot], 0..1. */
    fun like(slot: Int, spot: Spot): Double = likes[slot][spot.index]

    /** Cat [slot] spent [hours] at [spot]: the liking grows toward 1 by a small share. */
    fun learn(slot: Int, spot: Spot, hours: Double) {
        val share = (hours * BrainTuning.Places.DWELL_LEARN_PER_H).coerceIn(0.0, 1.0)
        likes[slot][spot.index] += (1.0 - likes[slot][spot.index]) * share
    }

    /**
     * Cat [slot] visited [spot] (an activity ended there): it is the newest of the cat's last few
     * places. The same place twice in a row counts once.
     */
    fun visited(slot: Int, spot: Spot) {
        val v = visits[slot]
        if (v[0] == spot.index) return
        var at = v.indexOf(spot.index)
        if (at < 0) at = v.size - 1
        for (k in at downTo 1) v[k] = v[k - 1]
        v[0] = spot.index
    }

    /**
     * How fresh [spot] is for cat [slot], 0..1: the newest of its last places counts the least (see
     * [BrainTuning.Roam.REVISIT]), a place not among them counts 1.
     */
    fun fresh(slot: Int, spot: Spot): Double {
        val k = visits[slot].indexOf(spot.index)
        return if (k < 0) 1.0 else BrainTuning.Roam.REVISIT[k]
    }

    /** The place cat [slot] is going to (or rests at), or null. */
    fun claimOf(slot: Int): Spot? = claimSpot[slot].let { if (it == NONE) null else Spots.all[it] }

    /** The place cat [slot] is still physically at, or null. */
    fun stayOf(slot: Int): Spot? = staySpot[slot].let { if (it == NONE) null else Spots.all[it] }

    /** True when [spot] (at [x]) is free for cat [slot] whose route there lands on [passes]. */
    fun free(slot: Int, spot: Spot, x: Float, passes: Int): Boolean {
        for (o in 0 until cats) {
            if (o == slot) continue
            if (!clear(spot, x, claimSpot[o], claimX[o])) return false
            if (!clear(spot, x, staySpot[o], stayX[o])) return false
            if (spot.single && (passMask[o] and bit(spot.surface)) != 0) return false
            if ((passes and held(o)) != 0) return false
        }
        return true
    }

    private fun clear(spot: Spot, x: Float, other: Int, otherX: Float): Boolean {
        if (other == NONE) return true
        val o = Spots.all[other]
        if (spot.single || o.single) return spot.index != other
        if (spot.has(Spots.BOWL) && o.has(Spots.BOWL)) return true
        return Math.abs(x - otherX) >= BrainTuning.Places.FLOOR_GAP
    }

    /** The single-occupancy surfaces cat [o] claims or stays on, as a bit mask. */
    private fun held(o: Int): Int {
        var m = 0
        if (claimSpot[o] != NONE && Spots.all[claimSpot[o]].single)
            m = m or bit(Spots.all[claimSpot[o]].surface)
        if (staySpot[o] != NONE && Spots.all[staySpot[o]].single)
            m = m or bit(Spots.all[staySpot[o]].surface)
        return m
    }

    /** Cat [slot] is going to [spot] at [x], its route landing on [passes]. */
    fun claim(slot: Int, spot: Spot, x: Float, passes: Int) {
        claimSpot[slot] = spot.index
        claimX[slot] = x
        passMask[slot] = passes
    }

    /** Cat [slot] got to its claimed place: it stays there, and its route is done. */
    fun arrived(slot: Int) {
        staySpot[slot] = claimSpot[slot]
        stayX[slot] = claimX[slot]
        passMask[slot] = 0
    }

    /**
     * Cat [slot] stands on [surface] at [x]: a stay it has left (another surface, or on the floor
     * further than the gap) is released.
     */
    fun standing(slot: Int, surface: SurfaceId, x: Float) {
        val s = staySpot[slot]
        if (s == NONE) return
        val spot = Spots.all[s]
        val gone =
            spot.surface != surface ||
                (surface == SurfaceId.FLOOR &&
                    Math.abs(stayX[slot] - x) > BrainTuning.Places.FLOOR_GAP)
        if (gone) staySpot[slot] = NONE
    }

    /** Cat [slot] holds and is going to nothing (it was moved: a restore, a catch-up). */
    fun release(slot: Int) {
        claimSpot[slot] = NONE
        staySpot[slot] = NONE
        passMask[slot] = 0
    }

    /** Places cat [slot] at [spot] at once (the world's start, a test). */
    fun place(slot: Int, spot: Spot, x: Float) {
        claim(slot, spot, x, 0)
        arrived(slot)
    }

    companion object {
        const val NONE = -1
        private const val PERCENT = 100.0

        /** The bit of [surface] in a passes mask. */
        fun bit(surface: SurfaceId): Int = 1 shl surface.ordinal

        /**
         * The raised surfaces a list of route [leaps] lands on on its way to [dest], as a mask (the
         * destination itself is the place's own business: the sofa's two ends are two places).
         */
        fun passesOf(leaps: List<RouteLeap>, dest: SurfaceId): Int {
            var m = 0
            for (i in leaps.indices) {
                val to = leaps[i].to
                if (to != SurfaceId.FLOOR && (to != dest || to == SurfaceId.COUCH)) m = m or bit(to)
            }
            return m
        }
    }
}
