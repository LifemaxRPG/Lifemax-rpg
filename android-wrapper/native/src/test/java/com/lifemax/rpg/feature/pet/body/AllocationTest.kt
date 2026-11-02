package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Steady-state stepping must not allocate. The design guarantees it (no lambdas, boxing, ranges
 * objects or string building in the step path; everything is refilled in place), and this test
 * checks it by counting the bytes the thread allocates across thousands of steps.
 */
class AllocationTest {
    /**
     * The Android unit-test classpath has no java.lang.management, so the HotSpot thread bean is
     * reached by reflection (the test JVM is a plain JVM and has it).
     */
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
    fun steppingAllocatesNothingInSteadyState() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())

        val body = CatBody(seed = 9)
        // A long plan that exercises walking, running, turning, hops, acts and idle, queued up
        // front
        // (building plans is allocation, and belongs to the caller).
        val steps = ArrayList<PlanStep>()
        repeat(6) {
            steps.add(PlanStep.Walk(200f))
            steps.add(PlanStep.Face(-1))
            steps.add(PlanStep.Walk(60f, run = true))
            steps.addAll(body.routeTo(SurfaceId.TOP, 154f))
            steps.add(PlanStep.Act("groom", 1f))
            steps.add(PlanStep.Hop(SurfaceId.FLOOR, 120f))
            steps.add(PlanStep.Act("loaf", 1f))
        }
        body.replacePlan(1, steps)
        body.react(ReactionKind.PURR, 5f)
        body.lookAt(150f, 60f)
        val dt = 1f / 60f
        repeat(2_000) { body.step(dt) } // warm up: class loading, JIT, first-use paths

        val before = allocatedBytes()
        repeat(20_000) {
            body.step(dt)
            body.pose.events.count
        }
        val used = allocatedBytes() - before
        assertTrue("allocated $used bytes over 20000 steps", used <= TOLERANCE_BYTES)
    }

    private companion object {
        /**
         * The counter itself and the loop may account for a few objects; a leak would be megabytes.
         */
        const val TOLERANCE_BYTES = 4_096L
    }
}
