package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Look
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Run

/**
 * The scenes the steadiness probe plays, every one stepped at the screen's 120 frames a second and
 * drawn every frame: a cat acting one clip in place for some seconds, a cat walking or running back
 * and forth, and two cats living part of their day in the house.
 */
internal object SteadinessScenes {
    /** Device pixels per art pixel: the card on the phone, and its full-screen room. */
    const val CARD_SCALE = 5
    const val ROOM_SCALE = 9

    /** The resting rows: no body motion of their own, only the art's frames and the rig. */
    val restRows = listOf("idle_sit", "idle_sit_w", "loaf", "loaf_w", "sleep", "sleep_w")

    /** The moving rows: the plan that makes each one. */
    val moveRows: List<Pair<String, (CatBody) -> Unit>> =
        listOf(
            "walk" to
                { b ->
                    b.replacePlan(1, List(6) { PlanStep.Walk(if (it % 2 == 0) 300f else 60f) })
                },
            "run" to
                { b ->
                    b.replacePlan(
                        1,
                        List(6) { PlanStep.Walk(if (it % 2 == 0) 330f else 60f, run = true) },
                    )
                },
            "walk_front_back" to
                { b ->
                    b.replacePlan(
                        1,
                        List(6) { PlanStep.Walk(120f + 30f * it, if (it % 2 == 0) 90f else 30f) },
                    )
                },
        )

    val ginger = Look("ginger adult", "ginger", -1, RigMorph.NEUTRAL)
    val tuxedoKitten = Look("tuxedo kitten st2", "tuxedo", 2, RigMorph.NEUTRAL)
    val calico =
        Look(
            "calico (genetic) adult",
            CoatFixtures.coats.getValue("calico"),
            -1,
            RigMorph(ear = 1.15, earWidth = 1.1, tail = 1.2, leg = 1.08, body = 1.06, whisker = 1.1),
        )
    val silverKitten =
        Look(
            "silver (genetic) kitten st1",
            CoatFixtures.coats.getValue("silver"),
            1,
            RigMorph(ear = 0.9, tail = 0.8, body = 0.95),
        )

    /** The looks of the tables: plain and genetic coats, grown and kittens. */
    val looks = listOf(ginger, tuxedoKitten, calico, silverKitten)

    /** Seconds a scene settles before it is measured (the posture's own kick dies away). */
    const val SETTLE_S = 1.5f

    /** A cat acting [row] in place for [seconds], drawn by [renderer] at [s]. */
    fun act(
        look: Look,
        row: String,
        renderer: Renderer,
        s: Int,
        seconds: Float,
        seed: Int = 7,
    ): Run {
        val body = CatBody(seed = seed)
        body.replacePlan(1, listOf(PlanStep.Act(row, seconds + SETTLE_S + 1f)))
        return play(body, look, renderer, s, seconds, SETTLE_S)
    }

    /** A cat moving by [plan] for [seconds]. */
    fun move(look: Look, plan: (CatBody) -> Unit, renderer: Renderer, s: Int, seconds: Float): Run {
        val body = CatBody(seed = 5, startX = 60f)
        plan(body)
        return play(body, look, renderer, s, seconds, settle = 0.2f)
    }

    /** Two cats living [seconds] of their day in the house (the Overview card's room). */
    fun house(looks: List<Look>, renderer: Renderer, s: Int, seconds: Float): List<Run> {
        val life = HouseLife(listOf(HouseCat(11, false), HouseCat(23, false)))
        val dt = 1f / SteadinessProbe.HZ
        val runs = life.bodies.indices.map { Run(looks[it], renderer, s, it + 1) }
        repeat((seconds * SteadinessProbe.HZ).toInt()) {
            life.step(dt)
            for (i in life.bodies.indices) runs[i].frame(life.bodies[i].pose)
        }
        return runs
    }

    private fun play(
        body: CatBody,
        look: Look,
        renderer: Renderer,
        s: Int,
        seconds: Float,
        settle: Float,
    ): Run {
        val dt = 1f / SteadinessProbe.HZ
        repeat((settle * SteadinessProbe.HZ).toInt()) { body.step(dt) }
        val run = Run(look, renderer, s, 1)
        repeat((seconds * SteadinessProbe.HZ).toInt()) {
            body.step(dt)
            run.frame(body.pose)
        }
        return run
    }
}
