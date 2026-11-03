package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.Rng
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobCat
import com.lifemax.rpg.feature.pet.world.BlobPos
import com.lifemax.rpg.feature.pet.world.BlobToy
import com.lifemax.rpg.feature.pet.world.Drives
import com.lifemax.rpg.feature.pet.world.LegacyPos
import com.lifemax.rpg.feature.pet.world.Mood
import com.lifemax.rpg.feature.pet.world.QuirkCounters
import com.lifemax.rpg.feature.pet.world.Trust
import com.lifemax.rpg.feature.pet.world.WorldBlob
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldContract
import kotlin.math.abs

/**
 * The start of a world (docs/specs/CAT_BRAIN_PLAN.md 2.4): a cat the page has no stored world for
 * is built from what the page sends as `legacy` (its web brain, read once; lossy but sensible), or
 * from the page's defaults when it has none. [merge] puts the stored cats and the imported ones
 * together into the one blob a brain is restored from.
 */
object WorldImport {
    /** Marks a world that began from the web brains ("legacy1") or from nothing ("fresh"). */
    private const val FROM_LEGACY = "legacy1"
    private const val FROM_FRESH = "fresh"

    /** A saved elevation within this many pixels of a surface's height means the cat was on it. */
    private const val ELEVATION_SLACK = 25.0

    /** Less than this many pixels up is the floor with a little jitter, not a surface. */
    private const val MIN_ELEVATION = 6

    private fun round3(v: Double) = Math.round(v * 1000.0) / 1000.0

    /** A saved place may be this far beyond a surface's ends and still be on it. */
    private const val EDGE_SLACK = 8.0

    /** One of the cat's three random streams, seeded as the plan says (1.2). */
    private val STREAMS = listOf("choice", "idle", "social")

    /**
     * The blob for the living cats of [block]: each from [stored] when it has them (unless the page
     * could not read that cat's entry, [rejected]), else imported from the page's `legacy`, else
     * built fresh. [day] and [hour] are the world clock's local day and hour, [nowMs] its epoch.
     */
    fun merge(
        block: WorldBlock,
        stored: WorldBlob?,
        rejected: Set<String>,
        day: String,
        hour: Double,
        nowMs: Long,
    ): WorldBlob {
        val cats = LinkedHashMap<String, BlobCat>()
        val lying = ArrayList<BlobToy>()
        var fromBrain = false
        for (cat in block.cats) {
            val kept = stored?.cats?.get(cat.id)?.takeIf { cat.id !in rejected }
            if (kept == null && cat.legacy?.src == "brain") {
                fromBrain = true
                cat.legacy.toys.forEach {
                    lying += BlobToy(it.id, SurfaceId.FLOOR.key, it.x.toDouble(), it.z, null)
                }
            }
            cats[cat.id] = kept ?: build(cat, day, hour, nowMs)
        }
        val began =
            if (stored != null) stored.imported else if (fromBrain) FROM_LEGACY else FROM_FRESH
        return WorldBlob(
            v = WorldContract.BLOB_VERSION,
            imported = if (fromBrain) FROM_LEGACY else began,
            cats = cats,
            pair = stored?.pair,
            toys =
                (stored?.toys.orEmpty().filter { it.by == null || it.by in cats } + lying).take(
                    WorldContract.MAX_TOYS_ON_FLOOR
                ),
            gift = stored?.gift?.takeIf { it.cat in cats },
            outbox = stored?.outbox.orEmpty(),
            away = stored?.away,
            at = stored?.at ?: 0L,
        )
    }

    /** One cat's world from its legacy block, or from the page's defaults when it has none. */
    fun build(cat: WorldCat, day: String, hour: Double, nowMs: Long): BlobCat {
        val old = cat.legacy
        val glass = old?.glass ?: 0
        val fresh = Fresh
        return BlobCat(
            pos = old?.pos?.let(::position) ?: scheduled(cat.slot, hour),
            act = "idle",
            actAt = nowMs,
            drives =
                Drives(
                    en = old?.energy ?: fresh.ENERGY,
                    pl = HostTuning.DRIVE_PLAY,
                    cu = HostTuning.DRIVE_CURIOSITY,
                    so =
                        round3(
                            HostTuning.DRIVE_SOCIAL +
                                HostTuning.DRIVE_SOCIAL_GLASS * glass / PERCENT
                        ),
                    co = HostTuning.DRIVE_COMPANY,
                    cm = HostTuning.DRIVE_COMFORT,
                    te = HostTuning.DRIVE_TERRITORY,
                    gr = HostTuning.DRIVE_GROOM,
                ),
            mood = old?.mood ?: fresh.MOOD,
            trust = old?.trust ?: fresh.TRUST,
            spots = old?.spots.orEmpty(),
            innate = old?.innate.orEmpty(),
            counters = old?.counters ?: QuirkCounters(0, 0, 0, 0),
            rhythm = old?.rhythm ?: List(WorldContract.HOURS) { fresh.RHYTHM },
            fx = old?.fx.orEmpty(),
            rng = STREAMS.map { Rng.hash("${cat.id}|$day|$it") },
            day = day,
            wake = HostTuning.WAKE_H,
            bed = HostTuning.BED_H,
            voice = old?.voice ?: 1.0,
            cheerSeen = emptyList(),
        )
    }

    private object Fresh {
        const val ENERGY = 0.5
        const val RHYTHM = 5
        val MOOD = Mood(0.2, -0.1)
        val TRUST = Trust(0.4, 0.4, 0.4, 0.7)
    }

    private const val PERCENT = 100.0

    /** Where a cat with no saved place stands at [hour]: in bed at night, else by the bowls. */
    fun scheduled(slot: Int, hour: Double): BlobPos {
        val night = hour >= HostTuning.BED_NIGHT_FROM_H || hour < HostTuning.BED_NIGHT_TO_H
        if (night) return BlobPos(SurfaceId.BED.key, BED_X, World.ART_Z.toDouble(), 1)
        val x = if (slot == 0) HostTuning.BOWL_X else HostTuning.BOWL_X_SECOND
        return BlobPos(SurfaceId.FLOOR.key, x, World.ART_Z.toDouble(), 1)
    }

    private const val BED_X = 36.0

    /**
     * The web room's saved place (house x, depth, elevation above the floor) as a place in the
     * body's house: the surface whose height best matches the elevation under the cat, else the
     * floor.
     */
    fun position(p: LegacyPos): BlobPos {
        val z = p.z.coerceIn(0.0, WorldContract.Z_MAX)
        val x = p.x.toDouble().coerceIn(World.HOUSE_MIN_X.toDouble(), World.HOUSE_MAX_X.toDouble())
        if (p.elev < MIN_ELEVATION) return BlobPos(SurfaceId.FLOOR.key, x, z, 1)
        val floorY = World.floorY(z.toFloat())
        val best =
            SurfaceId.entries
                .filter { it != SurfaceId.FLOOR }
                .map { World.surface(it) }
                .filter { x >= it.x0 - EDGE_SLACK && x <= it.x1 + EDGE_SLACK }
                .minByOrNull { abs(floorY - it.y - p.elev) }
        if (best == null || abs(floorY - best.y - p.elev) > ELEVATION_SLACK)
            return BlobPos(SurfaceId.FLOOR.key, x, z, 1)
        return BlobPos(
            best.id.key,
            x.coerceIn(best.x0.toDouble(), best.x1.toDouble()),
            best.z.toDouble(),
            1,
        )
    }
}
