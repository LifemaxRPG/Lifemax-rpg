package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.brain.CatchPlace
import com.lifemax.rpg.feature.pet.world.BlobPos
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * Where the house's bodies stand, as the world blob keeps it ([BlobPos]): putting a cat down where
 * the blob or a catch-up's story says, and reading back where it is, kept inside the house.
 */
internal object HousePlaces {
    /**
     * Puts cat [slot] of [h] down at the saved place [p] (a surface it does not know: the floor).
     */
    fun place(h: HouseLife, slot: Int, p: BlobPos) {
        if (slot !in 0 until h.count) return
        val id = SurfaceId.entries.firstOrNull { it.key == p.s } ?: SurfaceId.FLOOR
        val surface = World.surface(id)
        val z = if (id == SurfaceId.FLOOR) p.z.toFloat() else surface.z
        val x =
            if (id == SurfaceId.FLOOR) World.clampHouse(p.x.toFloat())
            else World.clampTo(id, p.x.toFloat())
        h.bodies[slot].placeAt(x, z, id, if (p.dir < 0) -1 else 1)
    }

    /** Puts a cat down where a catch-up's story ended ([p]; no x: the middle of its surface). */
    fun placeAt(h: HouseLife, p: CatchPlace) {
        val id = SurfaceId.entries.firstOrNull { it.key == p.surface } ?: SurfaceId.FLOOR
        val surface = World.surface(id)
        val x = p.x?.toFloat() ?: (surface.x0 + surface.x1) / 2f
        place(h, p.slot, BlobPos(id.key, x.toDouble(), p.z ?: surface.z.toDouble(), p.dir))
    }

    /** Where cat [slot] of [h] stands now, as the blob keeps it. */
    fun positionOf(h: HouseLife, slot: Int): BlobPos {
        val pose = h.bodies[slot].pose
        return BlobPos(
            pose.surface.key,
            pose.x.toDouble().coerceIn(WorldContract.HOUSE_X_MIN, WorldContract.HOUSE_X_MAX),
            pose.z.toDouble().coerceIn(0.0, WorldContract.Z_MAX),
            if (pose.facing < 0) -1 else 1,
        )
    }
}
