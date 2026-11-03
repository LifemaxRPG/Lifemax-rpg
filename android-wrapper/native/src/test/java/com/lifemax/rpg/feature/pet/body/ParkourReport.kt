package com.lifemax.rpg.feature.pet.body

import java.util.Locale

/**
 * Test support: the cats of the parkour tables, and the reachability table as text. The same text
 * is the documented table in PET_ENGINE_PLAN.md and a golden file, so a change to the capability
 * numbers or the room's geometry shows up as a readable diff.
 */
object ParkourReport {
    /** A cat for the tables. */
    class Kind(val name: String, val cap: JumpCapability)

    /** The cats: an adult, the six kitten stages and a weak adult. */
    val kinds: List<Kind> =
        listOf(Kind("adult", JumpCapability.ADULT)) +
            (0..5).map { Kind("kitten$it", JumpCapability.forStage(it)) } +
            Kind("weak adult", JumpCapability.of(1f, weak = true)) +
            Kind("weak kitten0", JumpCapability.forStage(0, weak = true))

    private val ids = SurfaceId.entries

    /**
     * One line per leap edge of [kind]: `from -> to` with kind, rise, take-off, landing, run-up.
     */
    fun edges(kind: Kind): List<String> {
        val out = ArrayList<String>()
        for (a in ids) for (b in ids) {
            if (a == b) continue
            val e = ParkourPlanner.edge(kind.cap, a, b) ?: continue
            out.add(
                String.format(
                    Locale.ROOT,
                    "%s -> %s  %s rise %.0f  off %.0f  on %.0f  run %.0f  apex %.0f  %.2fs  effort %.2f",
                    a.key,
                    b.key,
                    e.kind,
                    e.rise,
                    e.takeOffX,
                    e.landX,
                    e.runUp,
                    e.arc.apex,
                    e.arc.flight,
                    e.arc.effort,
                )
            )
        }
        return out
    }

    /** The routes up from the floor to the parkour goals, for every cat. */
    fun routes(kind: Kind): List<String> {
        val goals =
            listOf(
                SurfaceId.BED,
                SurfaceId.COUCH,
                SurfaceId.MID,
                SurfaceId.TOP,
                SurfaceId.SILL,
                SurfaceId.COUNTER,
                SurfaceId.FRIDGE,
                SurfaceId.BOOKCASE,
                SurfaceId.LSILL,
                SurfaceId.KSILL,
                SurfaceId.TABLE,
            )
        return goals.map { g ->
            val r = ParkourPlanner.plan(kind.cap, SurfaceId.FLOOR, 92f, g, World.surface(g).x0)
            val chain =
                if (r == null) "cannot"
                else
                    (listOf(SurfaceId.FLOOR) + r.leaps.map { it.to }).joinToString(" > ") { it.key }
            "floor -> ${g.key}: $chain"
        }
    }

    /** The whole table: every cat's cap, its leap edges and its routes. */
    fun text(): String {
        val sb = StringBuilder()
        for (k in kinds) {
            val c = k.cap
            sb.append(
                String.format(
                    Locale.ROOT,
                    "== %s: apex %.1f (+%.1f run), reach %.1f (+%.1f run), max drop %.1f, max rise %.1f\n",
                    k.name,
                    c.apexStand,
                    c.apexRunBonus,
                    c.vxStand,
                    c.runCarry * Tuning.Parkour.RUN_SPEED,
                    c.maxDrop,
                    c.maxRise(Tuning.Parkour.RUN_UP_MAX_PX),
                )
            )
            for (line in routes(k)) sb.append("  ").append(line).append('\n')
            for (line in edges(k)) sb.append("    ").append(line).append('\n')
        }
        return sb.toString()
    }
}
