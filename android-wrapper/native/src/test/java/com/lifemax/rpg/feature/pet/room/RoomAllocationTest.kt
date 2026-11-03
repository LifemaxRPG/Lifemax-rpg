package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.Beat
import com.lifemax.rpg.feature.pet.body.Burst
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.Petting
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.TouchModel
import com.lifemax.rpg.feature.pet.body.TouchSink
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.presentation.TouchParticles
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * A frame of the live room costs no allocation: stepping two cats, the camera following them and
 * painting the whole room (the sky, the three rooms, the furniture with the cats between, the tint
 * and the lamp) onto a canvas that only counts. The bytes the thread allocates across thousands of
 * frames are counted; a per-frame object would add up to megabytes.
 */
class RoomAllocationTest {
    private val bean: Any? =
        runCatching {
                Class.forName("java.lang.management.ManagementFactory")
                    .getMethod("getThreadMXBean")
                    .invoke(null)
            }
            .getOrNull()

    private fun supported(): Boolean =
        runCatching {
                val type = Class.forName("com.sun.management.ThreadMXBean")
                type.isInstance(bean) &&
                    type.getMethod("isThreadAllocatedMemorySupported").invoke(bean) == true
            }
            .getOrDefault(false)

    private fun allocatedBytes(): Long {
        val type = Class.forName("com.sun.management.ThreadMXBean")
        val method = type.getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
        return method.invoke(bean, Thread.currentThread().id) as Long
    }

    /** A canvas that draws nothing and counts what it was asked for. */
    private class CountingCanvas : RoomCanvas {
        var calls = 0

        override fun clip(x: Int, y: Int, w: Int, h: Int) {
            calls++
        }

        override fun unclip() {
            calls++
        }

        override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
            calls++
        }

        override fun image(
            image: RoomImage,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Int,
            dy: Int,
            dw: Int,
            dh: Int,
        ) {
            calls++
        }

        override fun quad(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
            argb: Int,
        ) {
            calls++
        }

        override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) {
            calls++
        }

        override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) {
            calls++
        }
    }

    private class TwoCats(private val bodies: Array<CatBody>) : CatLayer {
        var drawn = 0

        override val count = 2

        override fun depth(i: Int) = bodies[i].pose.z

        override fun feet(i: Int) = bodies[i].pose.y

        override fun draw(i: Int) {
            drawn++
        }
    }

    @Test
    fun aFrameOfTheLiveRoomAllocatesNothing() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val scene = RoomScene(PetArtResources.loadRoom())
        val canvas = CountingCanvas()
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        val cats = TwoCats(house.bodies)
        val director = CameraDirector()
        // long plans for both cats, made up front (planning is allocation, and belongs elsewhere)
        for ((i, body) in house.bodies.withIndex()) {
            val steps = ArrayList<PlanStep>()
            repeat(40) {
                steps.add(PlanStep.Walk(if (i == 0) -120f else 300f))
                steps.add(PlanStep.Act("groom", 1f))
                steps.add(PlanStep.Walk(if (i == 0) 20f else 190f))
                steps.add(PlanStep.Act("loaf", 1f))
            }
            body.replacePlan(1, steps)
        }
        val dt = 1f / 60f
        var hour = 20.0 // the tint and the lamp are on
        fun frame() {
            house.step(dt)
            director.update(house.bodies[0], house.bodies[1], dt)
            scene.paintBackdrop(canvas, hour, 12.0, 6)
            scene.paintScene(canvas, cats)
            scene.paintOverlay(canvas, hour, 6)
        }
        repeat(2_000) { frame() } // warm up: class loading, JIT, the lamp's first picture

        val before = allocatedBytes()
        repeat(20_000) { frame() }
        val used = allocatedBytes() - before
        assertTrue("allocated $used bytes over 20000 frames", used <= TOLERANCE_BYTES)
        assertTrue("nothing was drawn", canvas.calls > 20_000 && cats.drawn >= 40_000)
        hour = 12.0
    }

    /**
     * The Cat tab's screen is the same room at a larger size, plus the owner's care: a meal sends a
     * cat to its bowl (and the bowl shows food), a slow blink is returned. Asking for either plans,
     * which allocates and belongs to the moment of the tap; the frames while the cats walk to their
     * bowls, eat, wash and blink back must cost nothing, the bowl full and the room at the Cat
     * screen's scale.
     */
    @Test
    fun aFrameOfTheCatScreenWithTheOwnersCareAllocatesNothing() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val scene = RoomScene(PetArtResources.loadRoom())
        val canvas = CountingCanvas()
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        val cats = TwoCats(house.bodies)
        val director = CameraDirector()
        house.bodies[0].placeAt(-100f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[1].placeAt(300f, World.ART_Z, SurfaceId.FLOOR, -1)
        val dt = 1f / 60f
        fun frame() {
            house.step(dt)
            director.update(house.bodies[0], house.bodies[1], dt)
            scene.foodBowlFull = true
            scene.paintBackdrop(canvas, 12.0, 12.0, 6)
            scene.paintScene(canvas, cats)
            scene.paintOverlay(canvas, 12.0, 6)
        }
        // warm up with a meal and a blink of their own: class loading, JIT, the first plans
        repeat(600) { frame() }
        house.serveMeal(0)
        house.serveMeal(1)
        house.slowBlink(0)
        repeat(MEAL_FRAMES) { frame() }
        house.bodies[0].placeAt(-100f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[1].placeAt(300f, World.ART_Z, SurfaceId.FLOOR, -1)
        house.serveMeal(0)
        house.serveMeal(1)
        house.slowBlink(0)
        val before = allocatedBytes()
        repeat(MEAL_FRAMES) { frame() }
        val used = allocatedBytes() - before
        assertTrue(
            "allocated $used bytes over $MEAL_FRAMES frames of a meal",
            used <= TOLERANCE_BYTES,
        )
        assertTrue("the cats did not eat", house.bodies.all { it.pose.x in 80f..130f })
    }

    /**
     * The owner's hand on a cat: the model steps, hearts and notes spawn and rise and are painted
     * over the cats, the cat purrs and its ears move. Putting the hand down plans (a reflex, at the
     * moment of the touch); the frames while it rests, and the room's pictures rising, cost
     * nothing.
     */
    @Test
    fun aFrameWithAHandOnTheCatAllocatesNothing() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val art = PetArtResources.loadRoom()
        val scene = RoomScene(art)
        val canvas = CountingCanvas()
        val particles = TouchParticles(art)
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        val cats = TwoCats(house.bodies)
        val director = CameraDirector()
        val petting =
            Petting(
                house,
                object : TouchSink {
                    override fun burst(kind: Burst, x: Float, y: Float) =
                        particles.spawn(kind, x, y)

                    override fun buzz(beat: Beat) = Unit

                    override fun purr(cat: Int, level: Float) = Unit

                    override fun stroke(cat: Int, outcome: TouchModel.Outcome) = Unit
                },
            )
        house.bodies[0].placeAt(-100f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[1].placeAt(300f, World.ART_Z, SurfaceId.FLOOR, -1)
        // a calm cat that trusts the hand, so it does not leave within the fifteen seconds
        petting.profile(0, 5, 0.5f, 1f, 0f)
        val dt = 1f / 60f
        fun frame() {
            house.step(dt)
            petting.step(dt)
            particles.step(dt)
            director.update(house.bodies[0], house.bodies[1], dt)
            scene.paintBackdrop(canvas, 12.0, 12.0, 6)
            scene.paintScene(canvas, cats)
            particles.paint(canvas)
            scene.paintOverlay(canvas, 12.0, 6)
        }
        repeat(600) { frame() }
        val pose = house.bodies[0].pose
        petting.down(pose.x, pose.y - 10f)
        repeat(300) { frame() } // warm up with the hand down: the purr, the first hearts
        val before = allocatedBytes()
        repeat(HOLD_FRAMES) { frame() }
        val used = allocatedBytes() - before
        assertTrue("allocated $used bytes over $HOLD_FRAMES frames", used <= TOLERANCE_BYTES)
        assertTrue("hearts were drawn", particles.count > 0 || canvas.calls > 0)
        assertTrue(petting.touching == 0)
    }

    private companion object {
        const val TOLERANCE_BYTES = 4_096L

        /** Twelve seconds of a hand resting on a cat. */
        const val HOLD_FRAMES = 720

        /** Fifteen seconds: the walk to the bowls, the meal and part of the wash. */
        const val MEAL_FRAMES = 900
    }
}
