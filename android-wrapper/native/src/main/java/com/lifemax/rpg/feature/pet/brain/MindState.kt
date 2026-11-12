package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.BlobPos
import com.lifemax.rpg.feature.pet.world.Drives as SavedDrives
import com.lifemax.rpg.feature.pet.world.Mood as SavedMood
import com.lifemax.rpg.feature.pet.world.QuirkCounters
import com.lifemax.rpg.feature.pet.world.Trust
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * One cat's mind as the world blob keeps it (plan 2.3) and back. What the brain owns is written
 * from the mind: where the cat stands, its activity, drives, mood, trust, its memory of places and
 * the day's schedule. What a later phase owns (quirks and their counters, the visiting rhythm, the
 * first moments, the voice, the cheers seen) is carried through unchanged from the state restored,
 * so nothing the page or an import put there is lost.
 */
internal object MindState {
    /** Cat [m]'s state now; [kept] is what was restored for it (null for a new cat). */
    fun export(m: CatMind, kept: BlobCat?, epochMs: Long, dayKey: String): BlobCat {
        val exec = m.exec
        val trust = kept?.trust ?: Trust(DEFAULT_THREAD, m.trust, DEFAULT_THREAD, DEFAULT_THREAD)
        return BlobCat(
            pos =
                BlobPos(
                    exec.surface.key,
                    exec.x
                        .toDouble()
                        .coerceIn(WorldContract.HOUSE_X_MIN, WorldContract.HOUSE_X_MAX),
                    exec.z.toDouble().coerceIn(0.0, WorldContract.Z_MAX),
                    exec.facing,
                ),
            act = actKey(m.runner.activity.id),
            actAt = (epochMs - (m.runner.elapsedS * MS).toLong()).coerceAtLeast(0L),
            drives = SavedDrives.of(Drives.ALL.map { round3(m.drives[it]) }),
            mood = SavedMood(round3(m.mood.valence), round3(m.mood.arousal)),
            trust = trust.copy(tru = round3(m.trust.coerceIn(0.0, 1.0))),
            spots = places(m),
            innate = kept?.innate.orEmpty(),
            counters = kept?.counters ?: QuirkCounters(0, 0, 0, 0),
            rhythm = kept?.rhythm ?: List(WorldContract.HOURS) { 0 },
            fx = m.fx.toList(),
            rng = kept?.rng?.takeIf { kept.day == dayKey } ?: streams(m.profile.id, dayKey),
            day = dayKey,
            wake = round3(m.schedule.wake),
            bed = round3(m.schedule.bed),
            voice = kept?.voice ?: 1.0,
            cheerSeen = kept?.cheerSeen.orEmpty(),
        )
    }

    /**
     * Puts [saved] into cat [m]: drives, mood, trust and places, and the cat where it stood (a
     * declared cut), holding no place yet. Its activity is taken up afterwards, once every cat of
     * the house is back where it stood ([CatMind.resume] with [activityOf] the saved word), so
     * neither takes a place the other one is on.
     */
    fun restore(m: CatMind, saved: BlobCat) {
        val levels = saved.drives.toList()
        for (d in Drives.ALL) m.drives[d] = levels[d.ordinal]
        m.mood.set(saved.mood.v, saved.mood.a)
        m.trust = saved.trust.tru
        m.book.seedLikes(m.slot, saved.spots)
        m.fx.clear()
        m.fx.addAll(saved.fx)
        m.book.release(m.slot)
        put(m, saved.pos)
    }

    /** Puts cat [m] at [pos] at once, on its surface (a floor depth only on the floor). */
    fun put(m: CatMind, pos: BlobPos) {
        val id = SurfaceId.entries.firstOrNull { it.key == pos.s } ?: SurfaceId.FLOOR
        val floor = id == SurfaceId.FLOOR
        val x = if (floor) World.clampHouse(pos.x.toFloat()) else World.clampTo(id, pos.x.toFloat())
        val z = if (floor) pos.z.toFloat() else World.surface(id).z
        m.exec.place(id, x, z, pos.dir)
    }

    /** The blob's word for an activity: its id with underscores for spaces. */
    fun actKey(id: String): String = id.replace(' ', '_')

    /** The activity id a blob's word names. */
    fun activityOf(key: String): String = key.replace('_', ' ')

    /** The cat's best liked places, by the page's spot ids, 0..100, at most the blob's six. */
    private fun places(m: CatMind): Map<String, Int> {
        val best = HashMap<String, Int>()
        for (spot in Spots.all) {
            val id = spot.pageId ?: continue
            if (id !in WorldContract.SPOTS) continue
            val v = Math.round(m.book.like(m.slot, spot) * PERCENT).toInt().coerceIn(0, MAX_LIKE)
            if (v > (best[id] ?: -1)) best[id] = v
        }
        return best.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(WorldContract.MAX_BLOB_SPOTS)
            .associate { it.key to it.value }
    }

    /** Fresh random streams for a day, as an import seeds them. */
    private fun streams(id: String, day: String): List<Int> =
        STREAMS.map { Rng.hash("$id|$day|$it") }

    private fun round3(v: Double): Double = Math.round(v * THOUSAND) / THOUSAND

    private val STREAMS = listOf("choice", "idle", "social")
    private const val MS = 1000.0
    private const val THOUSAND = 1000.0
    private const val PERCENT = 100.0
    private const val MAX_LIKE = 100
    private const val DEFAULT_THREAD = 0.4
}
