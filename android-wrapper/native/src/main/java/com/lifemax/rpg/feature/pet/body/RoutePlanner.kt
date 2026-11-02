package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.body.SurfaceId.BED
import com.lifemax.rpg.feature.pet.body.SurfaceId.BOX
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUCH
import com.lifemax.rpg.feature.pet.body.SurfaceId.COUNTER
import com.lifemax.rpg.feature.pet.body.SurfaceId.FLOOR
import com.lifemax.rpg.feature.pet.body.SurfaceId.KSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.LSILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.MID
import com.lifemax.rpg.feature.pet.body.SurfaceId.SILL
import com.lifemax.rpg.feature.pet.body.SurfaceId.TOP

/**
 * Turns "go to this surface at this x" into the walk and hop steps that get there over the room's
 * surface graph. A faithful port of the web's `petRoute` (15-runtime.js), including the spots the
 * cat takes off from, so a plan from the page and a plan from here are the same plan.
 */
object RoutePlanner {
    private const val COUCH_RUN_UP = 20f
    private const val COUNTER_RUN_UP = -18f
    private const val STEP_OFF_RAISED = 16f
    private const val PROP_STEP_OFF = 6f
    private const val PROP_SIDE_SHARE = 0.6f
    private const val PROP_RUN_UP = 8f
    private const val BOX_SIDE_SHARE = 0.2f
    private const val BOX_RUN_UP = 2f
    private const val BED_RUN_UP_X = 50f
    private const val BED_X = 36f
    private const val SILL_RUN_UP_X = 54f
    private const val SILL_WALK_MIN = 38f
    private const val SILL_WALK_MAX = 70f
    private const val TREE_BASE_X = 140f
    private const val TREE_WALK_X = 138f
    private const val TREE_MID_X = 150f

    /**
     * The steps from standing on [from] at [fromX] to [dest] at [x]. Empty when the cat is already
     * on the bed.
     */
    fun route(from: SurfaceId, fromX: Float, dest: SurfaceId, x: Float): List<PlanStep> {
        val steps = ArrayList<PlanStep>(MAX_STEPS)
        if ((dest == LSILL || dest == KSILL) && from != dest) {
            return viaSill(steps, from, fromX, dest, x)
        }
        if (from == dest && dest != FLOOR) {
            if (dest != BED) steps.add(PlanStep.Walk(World.clampTo(dest, x)))
            return steps
        }
        Builder(steps, from, fromX).build(dest, x)
        return steps
    }

    private const val MAX_STEPS = 6

    private fun viaSill(
        steps: ArrayList<PlanStep>,
        from: SurfaceId,
        fromX: Float,
        dest: SurfaceId,
        x: Float,
    ): List<PlanStep> {
        val via = if (dest == LSILL) COUCH else COUNTER
        val landing = World.clampTo(dest, x)
        if (from != via) steps.addAll(route(from, fromX, via, World.clampTo(via, landing)))
        steps.add(PlanStep.Hop(dest, landing))
        return steps
    }

    /** One route being built: remembers where the cat is along the way (`cur` in the web). */
    private class Builder(val steps: ArrayList<PlanStep>, from: SurfaceId, val startX: Float) {
        var cur = from

        fun build(dest: SurfaceId, x: Float) {
            when {
                dest == FLOOR -> floor(x)
                World.surface(dest).prop -> prop(dest, x)
                dest == COUCH || dest == COUNTER -> furniture(dest, x)
                dest == BED -> bed()
                dest == SILL -> sill(x)
                dest == MID -> mid(x)
                dest == TOP -> top(x)
            }
        }

        /**
         * Hop down to the floor (if not on it), landing at [fx] or a step clear of what it left.
         */
        fun toFloor(fx: Float) {
            if (cur == FLOOR) return
            val land =
                when {
                    cur == COUCH || cur == COUNTER ->
                        startX + if (fx >= startX) STEP_OFF_RAISED else -STEP_OFF_RAISED
                    World.surface(cur).prop -> {
                        val p = World.surface(cur)
                        val clear = p.half * PROP_SIDE_SHARE + PROP_STEP_OFF
                        if (fx >= startX) p.x1 + clear else p.x0 - clear
                    }
                    else -> fx
                }
            steps.add(PlanStep.Hop(FLOOR, land))
            cur = FLOOR
        }

        fun floor(x: Float) {
            val dest = World.clampHouse(x)
            toFloor(dest)
            steps.add(PlanStep.Walk(dest))
        }

        fun prop(dest: SurfaceId, x: Float) {
            val s = World.surface(dest)
            val at = Mathx.clamp(x, s.x0, s.x1)
            val side = if (startX <= at) -1f else 1f
            val isBox = dest == BOX
            val run =
                if (isBox) s.half * BOX_SIDE_SHARE + BOX_RUN_UP
                else s.half * PROP_SIDE_SHARE + PROP_RUN_UP
            val approach = World.clampHouse((if (side < 0f) s.x0 else s.x1) + side * run)
            toFloor(approach)
            steps.add(PlanStep.Walk(approach, if (isBox) s.z + World.BOX_FRONT_Z else s.z))
            steps.add(PlanStep.Hop(dest, at))
        }

        fun furniture(dest: SurfaceId, x: Float) {
            val at = World.clampTo(dest, x)
            val approach = at + if (dest == COUCH) COUCH_RUN_UP else COUNTER_RUN_UP
            toFloor(approach)
            steps.add(PlanStep.Walk(approach))
            steps.add(PlanStep.Hop(dest, at))
        }

        fun bed() {
            toFloor(BED_RUN_UP_X)
            steps.add(PlanStep.Walk(BED_RUN_UP_X))
            steps.add(PlanStep.Hop(BED, BED_X))
        }

        fun sill(x: Float) {
            toFloor(SILL_RUN_UP_X)
            steps.add(PlanStep.Walk(Mathx.clamp(x, SILL_WALK_MIN, SILL_WALK_MAX)))
            steps.add(PlanStep.Hop(SILL, x))
        }

        fun mid(x: Float) {
            if (cur == TOP) {
                steps.add(PlanStep.Hop(MID, x))
                return
            }
            toFloor(TREE_BASE_X)
            steps.add(PlanStep.Walk(TREE_WALK_X))
            steps.add(PlanStep.Hop(MID, x))
        }

        fun top(x: Float) {
            if (cur != MID && cur != TOP) {
                toFloor(TREE_BASE_X)
                steps.add(PlanStep.Walk(TREE_WALK_X))
                steps.add(PlanStep.Hop(MID, TREE_MID_X))
                cur = MID
            }
            steps.add(PlanStep.Hop(TOP, x))
        }
    }
}
