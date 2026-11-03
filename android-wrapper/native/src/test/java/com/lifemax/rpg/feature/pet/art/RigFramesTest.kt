package com.lifemax.rpg.feature.pet.art

import com.lifemax.rpg.feature.pet.body.Clips
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.presentation.RigMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The rig's frame cache and what drives it: hits return the same picture and compose nothing, a
 * full cache re-uses its pictures, and, with the body's springs driving the pose, steady state
 * allocates nothing (the thread's allocated bytes are counted across thousands of frames).
 */
class RigFramesTest {
    private val clip = Clips.all.first { it.name == "idle_sit" }.id
    private val walkClip = Clips.all.first { it.name == "walk" }.id
    private val extreme = RigMorph(2.2, 1.5, 1.8, 1.4, 1.5, 1.8)
    private val grid = RigGrid()

    @Test
    fun aPlainCatInNoPoseDrawsTheSheet() {
        val frames = RigFrames(4)
        val plain = RigTestSupport.set("ginger", -1, RigMorph.NEUTRAL)
        grid.clear()
        assertEquals(-1, frames.slotFor(plain, clip, 0, grid))
        assertEquals(0L, frames.composed)
        // proportions or a pose both need a composed picture
        grid.set(8.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        assertTrue(frames.slotFor(plain, clip, 0, grid) >= 0)
        val long = RigTestSupport.set("ginger", -1, extreme, id = 2)
        grid.clear()
        assertTrue(frames.slotFor(long, clip, 0, grid) >= 0)
    }

    @Test
    fun theSamePoseIsComposedOnceAndFoundAgain() {
        val frames = RigFrames(8)
        val set = RigTestSupport.set("ginger", -1, extreme)
        grid.set(-12.0, 8.0, 0.0, 6.0, 0.0, 1.0)
        val first = frames.slotFor(set, clip, 3, grid)
        assertTrue(frames.wasComposed)
        val again = frames.slotFor(set, clip, 3, grid)
        assertEquals(first, again)
        assertEquals(false, frames.wasComposed)
        assertEquals(1L, frames.composed)
        // the frame number wraps like the art's
        assertEquals(first, frames.slotFor(set, clip, 3 + set.frames(clip), grid))
        assertEquals(1L, frames.composed)
    }

    @Test
    fun anotherCatAnotherFrameOrAnotherPoseIsAnotherPicture() {
        val frames = RigFrames(16)
        val a = RigTestSupport.set("ginger", -1, extreme, id = 1)
        val b = RigTestSupport.set("gray", -1, extreme, id = 2)
        grid.set(8.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val slots =
            setOf(
                frames.slotFor(a, clip, 0, grid),
                frames.slotFor(b, clip, 0, grid),
                frames.slotFor(a, clip, 1, grid),
                frames.slotFor(a, walkClip, 0, grid),
            )
        grid.set(12.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val other = frames.slotFor(a, clip, 0, grid)
        assertEquals(4, slots.size)
        assertTrue(other !in slots)
        // the same art in another coat is recoloured, so the pixels differ
        grid.set(8.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        val ginger = frames.pixels(frames.slotFor(a, clip, 0, grid)).copyOf()
        assertNotEquals(ginger.toList(), frames.pixels(frames.slotFor(b, clip, 0, grid)).toList())
    }

    @Test
    fun aFullCacheLetsTheStalestPictureGo() {
        val frames = RigFrames(3)
        val set = RigTestSupport.set("ginger", -1, extreme)
        fun pose(step: Int) = grid.set(step * 4.0, 0.0, 0.0, 0.0, 0.0, 1.0)
        for (s in 1..3) {
            pose(s)
            frames.slotFor(set, clip, 0, grid)
        }
        pose(1) // 1 is the most recent now; 2 is the stalest
        frames.slotFor(set, clip, 0, grid)
        pose(4) // evicts 2
        frames.slotFor(set, clip, 0, grid)
        assertEquals(4L, frames.composed)
        pose(1)
        frames.slotFor(set, clip, 0, grid)
        pose(3)
        frames.slotFor(set, clip, 0, grid)
        assertEquals(4L, frames.composed)
        pose(2)
        frames.slotFor(set, clip, 0, grid)
        assertEquals(5L, frames.composed)
    }

    // ------------------------------------------------------------------ allocation

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

    @Test
    fun cachedPosesCostNothing() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val frames = RigFrames(96)
        val set = RigTestSupport.set("ginger", -1, extreme)
        fun sweep() {
            for (s in -4..4) for (f in 0 until set.frames(clip)) {
                grid.set(s * 4.0, -s * 4.0, 0.5, s * 3.0, s * 4.0, 1.0)
                frames.slotFor(set, clip, f, grid)
            }
        }
        sweep() // composes; recolours the art frames; fills the cache
        val composed = frames.composed
        val before = allocatedBytes()
        repeat(2_000) { sweep() }
        val used = allocatedBytes() - before
        assertEquals("nothing is composed twice", composed, frames.composed)
        assertTrue("allocated $used bytes over 2000 sweeps", used <= TOLERANCE_BYTES)
    }

    @Test
    fun composingIntoAFullCacheCostsNothingEither() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        val frames = RigFrames(4)
        val set = RigTestSupport.set("ginger", -1, extreme)
        fun sweep() {
            for (s in -6..6) for (f in 0 until set.frames(clip)) {
                grid.set(s * 4.0, s * 4.0, 0.0, s * 3.0, -s * 4.0, 1.0)
                frames.slotFor(set, clip, f, grid)
            }
        }
        repeat(60) { sweep() } // warm up: the JIT has to be done with the composer
        val before = allocatedBytes()
        val composed = frames.composed
        repeat(40) { sweep() } // every lookup misses: 4 slots, 104 pictures per sweep
        val used = allocatedBytes() - before
        assertTrue("it did compose", frames.composed - composed > 1_000)
        assertTrue("allocated $used bytes composing", used <= TOLERANCE_BYTES)
    }

    @Test
    fun twoCatsLivingTheirLifeDriveTheRigWithoutAllocating() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        // a cache small enough to be full after the warm-up: its pictures are made as it fills
        val frames = RigFrames(6)
        val sets =
            listOf(
                RigTestSupport.set("ginger", -1, extreme, id = 1),
                RigTestSupport.set("gray", -1, RigMorph(0.6, 0.8, 0.5, 0.7, 0.8, 0.6), id = 2),
            )
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        for ((i, body) in house.bodies.withIndex()) {
            val steps = ArrayList<PlanStep>()
            repeat(80) {
                steps.add(PlanStep.Walk(if (i == 0) -120f else 300f))
                steps.add(PlanStep.Act("groom", 1f))
                steps.add(PlanStep.Walk(if (i == 0) 20f else 190f))
                steps.add(PlanStep.Act("loaf", 1f))
            }
            body.replacePlan(1, steps)
        }
        var slots = 0
        // art and rig frames are made the first time a cat shows them; do it all up front
        for (set in sets) for (c in Clips.all) if (set.has(c.id)) {
            for (f in 0 until set.frames(c.id)) {
                set.source(c.id, f)
                set.rigFrame(c.id, f)
            }
        }
        fun frame() {
            house.step(1f / 60f)
            for (i in 0..1) {
                val pose = house.bodies[i].pose
                RigMotion.read(pose, kitten = false, motion = true, grid = grid)
                if (frames.slotFor(sets[i], pose.clip, pose.frame, grid) >= 0) slots++
            }
        }
        repeat(20_000) { frame() } // warm up: class loading, JIT, every art frame recoloured once
        // a loaded machine compiles late: go on until a stretch of frames costs next to nothing
        var quiet = 0
        for (round in 1..100) {
            val at = allocatedBytes()
            repeat(2_000) { frame() }
            quiet = if (allocatedBytes() - at <= TOLERANCE_BYTES / 8) quiet + 1 else 0
            if (quiet == 3) break
        }
        val before = allocatedBytes()
        repeat(20_000) { frame() }
        val used = allocatedBytes() - before
        assertTrue("allocated $used bytes over 20000 frames", used <= TOLERANCE_BYTES)
        assertTrue("the rig was never asked", slots > 30_000)
        assertTrue("the ears never moved", frames.composed > 20)
    }

    private companion object {
        const val TOLERANCE_BYTES = 4_096L
    }
}
