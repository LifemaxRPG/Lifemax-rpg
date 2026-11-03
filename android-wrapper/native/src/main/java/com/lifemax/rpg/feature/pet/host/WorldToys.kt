package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.art.LabTable
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.MouthAnchors
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.ToyKind
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.world.BlobToy
import com.lifemax.rpg.feature.pet.world.WorldBlob
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldCat
import com.lifemax.rpg.feature.pet.world.WorldContract

/**
 * The toys of the world's house (CB4 part A's [ToyField], one for the house): laid out from the
 * stored world's toys where they were left, else (a world with none yet) the first of the cats'
 * unlocked toys lying on the rugs, as the page's room shows a toy or two about. Each cat gets the
 * field and its mouth anchors for its growth stage. Nothing here chooses to play: the brain does
 * that in a later phase; the bodies can already be given toy steps.
 */
internal object WorldToys {
    /** The field for the world of [block], from [blob]'s toys or the unlocked ones. */
    fun field(block: WorldBlock, blob: WorldBlob): ToyField {
        val field = ToyField()
        for (t in blob.toys.take(WorldContract.MAX_TOYS_ON_FLOOR)) {
            val kind = ToyKind.of(t.id) ?: continue
            val on = SurfaceId.entries.firstOrNull { it.key == t.s } ?: SurfaceId.FLOOR
            val floor = on == SurfaceId.FLOOR
            val x = if (floor) World.clampHouse(t.x.toFloat()) else World.clampTo(on, t.x.toFloat())
            field.add(kind, x, on, if (floor) t.z.toFloat() else World.surface(on).z)
        }
        if (field.count == 0) {
            val unlocked = block.cats.flatMap { it.toys }.distinct().mapNotNull(ToyKind::of)
            unlocked.take(LAY_X.size).forEachIndexed { i, kind ->
                field.add(kind, LAY_X[i], SurfaceId.FLOOR, LAY_Z[i])
            }
        }
        return field
    }

    /** Gives every cat of [house] the [field] and its mouth anchors for its stage in [cats]. */
    fun attach(house: HouseLife, field: ToyField, cats: List<WorldCat>) {
        val lab = lab
        for (i in 0 until house.count) {
            val stage = cats.getOrNull(i)?.stage ?: WorldContract.STAGE_GROWN
            house.bodies[i].attachToys(field, MouthAnchors.of(lab, stage))
        }
    }

    /** The toys as the blob keeps them: where each lies, and which cat (world id) holds it. */
    fun export(field: ToyField, house: HouseLife, cats: List<WorldCat>): List<BlobToy> {
        val out = ArrayList<BlobToy>(field.count)
        for (i in 0 until field.count.coerceAtMost(WorldContract.MAX_TOYS_ON_FLOOR)) {
            val t = field[i] ?: continue
            var by: String? = null
            for (k in 0 until house.count) {
                if (house.bodies[k].heldToy === t) by = cats.getOrNull(k)?.id
            }
            out +=
                BlobToy(
                    t.kind.key,
                    t.surface.key,
                    Math.round(t.x)
                        .toDouble()
                        .coerceIn(WorldContract.HOUSE_X_MIN, WorldContract.HOUSE_X_MAX),
                    Math.round(t.z).toDouble().coerceIn(0.0, WorldContract.Z_MAX),
                    by,
                )
        }
        return out
    }

    /** The art lab's mouth rows, read once; null (estimated anchors) if they cannot be read. */
    private val lab: LabTable? by lazy { runCatching { PetArtResources.loadLab() }.getOrNull() }

    /** Where the first toys lie in a world that has none yet: the bedroom rug, the living rug. */
    private val LAY_X = floatArrayOf(112f, -54f)
    private val LAY_Z = floatArrayOf(60f, 66f)
}
