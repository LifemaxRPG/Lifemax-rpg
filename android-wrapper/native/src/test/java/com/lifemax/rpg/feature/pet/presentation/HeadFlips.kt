package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Look
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Run

/**
 * The head-flip measurement (docs/specs/PET_SPRITE_STEADINESS.md, "A sitting cat's head"): a black
 * kitten sits on the cat tree's top slab while a second cat walks the floor below, the house runs
 * on a fixed seed for 30 s at 120 Hz, and every frame is drawn by the steadiness probe. A head flip
 * is a change of the art frame of the open-eyed sitting row (a blink, which comes back to the same
 * frame by design, is not one); an a-b-a flip goes back to a frame the head showed within the last
 * [ABA_S] seconds, so a head cycling through five poses revisits each of them every 1.4 s.
 */
internal object HeadFlips {
    const val SECONDS = 30
    const val ABA_S = 2.5f

    val blackKitten2 = Look("black kitten st2", "black", 2, RigMorph.NEUTRAL)
    val blackKitten3 = Look("black kitten st3", "black", 3, RigMorph.NEUTRAL)

    /** What one 30 s run measured. */
    class Result(val run: Run, val frames: List<Int>, val times: List<Float>) {
        /** Steps (index into [Run.changed]) where the open-eyed head frame changed. */
        val flips = ArrayList<Int>()

        /**
         * Of [flips], the ones that went back to a frame shown less than [ABA_S] seconds before.
         */
        val aba = ArrayList<Int>()

        /**
         * Seconds each head frame was held before it changed (the first, cut by the start, left
         * out).
         */
        val dwell = ArrayList<Float>()

        /** Steps the rig moved an ear, the tail or the whiskers a step. */
        val rigSteps = run.causes.count { it == Cause.RIG }

        init {
            var held = -1
            val left = HashMap<Int, Float>()
            var since = 0f
            for (i in frames.indices) {
                val f = frames[i]
                if (f < 0) continue
                if (held < 0) {
                    held = f
                    since = times[i]
                    continue
                }
                if (f == held) continue
                if (flips.isNotEmpty()) dwell.add(times[i] - since)
                flips.add(i)
                if (times[i] - (left[f] ?: -ABA_S) < ABA_S) aba.add(i)
                left[held] = times[i]
                held = f
                since = times[i]
            }
        }

        /** Coat pixels recoloured on the head-flip steps, device pixels. */
        fun coatOnFlips(): List<Int> = flips.filter { it < run.coat.size }.map { run.coat[it] }

        fun minDwell() = dwell.minOrNull() ?: Float.MAX_VALUE

        fun summary(label: String, s: Int): String {
            val coat = coatOnFlips()
            val art = coat.map { it / (s * s) }
            return "$label: flips ${flips.size}, a-b-a ${aba.size}, dwell min " +
                "%.2f s median %.2f s"
                    .format(minDwell(), dwell.sorted().getOrElse(dwell.size / 2) { 0f }) +
                ", coat px per flip (art px) min ${art.minOrNull()} max ${art.maxOrNull()} " +
                "total ${art.sum()} in $SECONDS s, rig steps $rigSteps"
        }
    }

    /**
     * [look]'s cat sits on the tree top for [SECONDS] s, house seed [seed], a second cat walking
     * the floor below; [each] sees both bodies after every step.
     */
    fun treeScene(
        look: Look,
        s: Int,
        seed: Int = 23,
        each: (CatBody, CatBody) -> Unit = { _, _ -> },
    ): Result {
        val life = HouseLife(listOf(HouseCat(seed, false), HouseCat(seed + 100, false)), false)
        val sitter = life.bodies[0]
        val walker = life.bodies[1]
        sitter.placeAt(TREE_TOP_X, World.ART_Z, SurfaceId.TOP, -1)
        walker.placeAt(40f, World.ART_Z, SurfaceId.FLOOR, 1)
        walker.replacePlan(1, List(8) { PlanStep.Walk(if (it % 2 == 0) 120f else 40f) })
        val run = Run(look, Renderer.STEADY, s, 1)
        val dt = 1f / SteadinessProbe.HZ
        val frames = ArrayList<Int>()
        val times = ArrayList<Float>()
        val settle = (SETTLE_S * SteadinessProbe.HZ).toInt()
        repeat(settle) { life.step(dt) }
        repeat(SECONDS * SteadinessProbe.HZ) {
            life.step(dt)
            val pose = sitter.pose
            run.frame(pose)
            val open = pose.clip == Clips.IDLE_SIT.id && pose.frame in Clips.IDLE_SIT.openFrames
            frames.add(if (open) pose.frame else -1)
            times.add(it * dt)
            each(sitter, walker)
        }
        // run.changed has one entry per frame but the first
        return Result(run, frames.drop(1), times.drop(1))
    }

    private const val TREE_TOP_X = 154f
    private const val SETTLE_S = 2f
}
