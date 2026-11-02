package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
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

    private companion object {
        const val TOLERANCE_BYTES = 4_096L
    }
}
