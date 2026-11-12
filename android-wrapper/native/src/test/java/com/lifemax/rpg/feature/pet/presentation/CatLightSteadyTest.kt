package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.LightTint
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.light.CatTarget
import com.lifemax.rpg.feature.pet.light.HouseLight
import com.lifemax.rpg.feature.pet.light.Weather
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Cause
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Look
import com.lifemax.rpg.feature.pet.presentation.SteadinessProbe.Renderer
import com.lifemax.rpg.feature.pet.room.HouseMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Lighting must never shimmer (docs/specs/CAT_LIGHTING_PLAN.md section 7): a cat's light key is
 * held ([LightHold]), a resting cat in any light draws byte-identical frames between the moments
 * something is meant to move, for every coat, at card and room scale, and a cat hovering on a
 * patch's edge changes key at most once. With no light the drawing is exactly the steady drawing
 * that was there before lighting.
 */
class CatLightSteadyTest {
    @Before
    fun coreRooms() {
        HouseMap.reset()
    }

    @Test
    fun theFirstLightShowsAtOnceAndAHeldLightNeverChanges() {
        val hold = LightHold()
        val key = hold.follow(2.0, LightTint.SUN, false, 0.0)
        assertEquals(CatLightKey.pack(2, LightTint.SUN), key)
        for (i in 1..600) assertEquals(key, hold.follow(2.0, LightTint.SUN, false, i / 120.0))
        assertEquals(0, hold.changes)
    }

    @Test
    fun aNewLightWaitsAndTwoChangesAreApart() {
        val hold = LightHold()
        hold.follow(0.0, LightTint.NONE, false, 0.0)
        var t = 0.0
        val dt = 1 / 120.0
        var changedAt = -1.0
        while (t < 3.0) {
            t += dt
            val k = hold.follow(2.0, LightTint.SUN, false, t)
            if (k != CatLightKey.NEUTRAL && changedAt < 0) changedAt = t
        }
        assertTrue("waited ${changedAt}s", changedAt >= LightHoldTuning.MIN_GAP_S)
        assertEquals(CatLightKey.pack(2, LightTint.SUN), hold.key)
        assertTrue(hold.changes <= 2)
    }

    @Test
    fun aCatHoveringOnAPatchEdgeChangesKeyAtMostOnceAndNeverBack() {
        // 0.3 px either side of the edge of the living room's noon patch, for 8 seconds
        val light = HouseLight().also { it.update(12.0, Weather.CLEAR) }
        val edge = edgeOfPatch(light)
        val target = CatTarget()
        val hold = LightHold()
        val keys = ArrayList<Int>()
        val dt = 1 / 120.0
        for (i in 0 until 8 * 120) {
            val x = edge + (if (i % 2 == 0) 0.3f else -0.3f)
            light.target(x, World.FLOOR_Y_ART, "floor", false, target)
            keys.add(hold.follow(target.halfSteps.toDouble(), target.tint, target.flatten, i * dt))
        }
        assertTrue("at most one change: ${hold.changes}", hold.changes <= 1)
        for (i in 2 until keys.size) {
            assertFalse("a-b-a at $i", keys[i] == keys[i - 2] && keys[i] != keys[i - 1])
        }
    }

    @Test
    fun theStagesLightingIsNeutralWhileOffAndStartsAfreshWhenOn() {
        var on = false
        val lighting = CatLighting({ Weather.CLEAR }, { on })
        val pose = CatPose()
        pose.x = -80f
        lighting.begin(12.0)
        assertEquals(CatLightKey.NEUTRAL, lighting.keyFor(pose, 0.0))
        assertEquals("Cat light off", lighting.readout())
        on = true
        lighting.begin(12.0)
        assertEquals(CatLightKey.pack(2, LightTint.SUN), lighting.keyFor(pose, 0.1))
        assertTrue(lighting.readout(), lighting.readout().endsWith("cats +1 sun (patch)"))
        on = false
        lighting.begin(12.0)
        assertEquals(CatLightKey.NEUTRAL, lighting.keyFor(pose, 0.2))
        assertEquals(CatLightKey.NEUTRAL, SpriteSteady.of(pose).light.key)
    }

    @Test
    fun aTurnChangesOnlyTheSideAndAtOnce() {
        val hold = LightHold()
        hold.follow(2.0, LightTint.SUN, false, 0.0)
        val turned = hold.follow(2.0, LightTint.SUN, true, 1 / 120.0)
        assertEquals(CatLightKey.pack(2, LightTint.SUN, flatten = true), turned)
    }

    @Test
    fun withNoLightTheSteadyDrawingIsTheOneBeforeLighting() {
        for (row in listOf("idle_sit", "sleep")) {
            val plain =
                SteadinessScenes.act(SteadinessScenes.ginger, row, Renderer.STEADY, CARD, 3f)
            val neutral =
                SteadinessScenes.act(SteadinessScenes.ginger, row, Renderer.STEADY, CARD, 3f) { _, _
                    ->
                    CatLightKey.NEUTRAL
                }
            assertEquals(plain.changed, neutral.changed)
            assertEquals(plain.causes, neutral.causes)
            assertEquals(plain.lastRaster.px.toList(), neutral.lastRaster.px.toList())
        }
    }

    @Test
    fun aRestingCatInAnyLightDrawsIdenticalFramesBetweenMotionEvents() {
        val scenes =
            listOf(
                Scene("noon patch", 12.0, -80f, "floor"),
                Scene("moon patch", 23.0, -50f, "floor"),
                Scene("beside the lamp", 23.0, -168f, "floor"),
                Scene("in a box", 12.0, 200f, "box"),
                Scene("night, no light", 2.0, 250f, "floor"),
                Scene("sunny sill", 12.0, -100f, "lsill"),
            )
        for (look in looks) for (scene in scenes) for (s in listOf(CARD, ROOM)) {
            val row = if (scene.surface == "box") "loaf" else "idle_sit"
            val light = HouseLight().also { it.update(scene.hour, Weather.CLEAR) }
            val hold = LightHold()
            val target = CatTarget()
            val run =
                SteadinessScenes.act(look, row, Renderer.STEADY, s, REST_S) { pose, t ->
                    light.target(scene.x, World.FLOOR_Y_ART, scene.surface, pose.flip, target)
                    hold.follow(target.halfSteps.toDouble(), target.tint, target.flatten, t)
                }
            val label = "${look.label} ${scene.name} x$s"
            val summary = SteadinessProbe.Summary(run)
            assertEquals("$label unexplained steps", 0, summary.unexplained)
            assertEquals("$label flicker", 0L, summary.flicker)
            assertTrue(
                "$label was lit",
                scene.name.startsWith("night") || run.lights.any { it != 0 },
            )
            assertTrue("$label light steps", run.causes.count { it == Cause.LIGHT } <= 1)
            val still = run.changed.count { it == 0 }
            assertTrue(
                "$label: $still of ${run.changed.size} still",
                still > run.changed.size * 0.8,
            )
        }
    }

    @Test
    fun aCatWalkingThroughThePatchBrightensAndReturnsWithoutFlicker() {
        val light = HouseLight().also { it.update(12.0, Weather.CLEAR) }
        val hold = LightHold()
        val target = CatTarget()
        // the scene walks from x 60 to 300 and back; map it onto the living room across its patch
        val run =
            SteadinessScenes.move(
                SteadinessScenes.ginger,
                SteadinessScenes.moveRows.first().second,
                Renderer.STEADY,
                CARD,
                12f,
            ) { pose, t ->
                val x = -190f + (pose.x - 60f) * 0.6f
                light.target(x, World.FLOOR_Y_ART, "floor", pose.flip, target)
                hold.follow(target.halfSteps.toDouble(), target.tint, target.flatten, t)
            }
        val lit = run.lights.count { CatLightKey.halfSteps(it) > 0 }
        assertTrue("lit for $lit frames", lit > 0)
        assertTrue("and out of it again", run.lights.count { CatLightKey.halfSteps(it) == 0 } > 0)
        assertEquals("unexplained", 0, SteadinessProbe.Summary(run).unexplained)
        val lights = run.lights
        for (i in 2 until lights.size) {
            assertFalse(
                "light a-b-a at $i",
                lights[i] == lights[i - 2] && lights[i] != lights[i - 1],
            )
        }
    }

    private class Scene(val name: String, val hour: Double, val x: Float, val surface: String)

    /** The left edge of the living room's patch at the cats' floor row. */
    private fun edgeOfPatch(light: HouseLight): Float {
        var x = -140f
        while (light.patchWindow(x, World.FLOOR_Y_ART) == Int.MIN_VALUE) x += 0.25f
        return x
    }

    private companion object {
        const val CARD = SteadinessScenes.CARD_SCALE
        const val ROOM = SteadinessScenes.ROOM_SCALE
        const val REST_S = 4f

        val looks: List<Look> by lazy {
            SteadinessScenes.looks +
                listOf(
                    Look("black adult", "black", -1, RigMorph.NEUTRAL),
                    Look("gray adult", "gray", -1, RigMorph(ear = 1.1, tail = 0.9)),
                )
        }
    }
}
