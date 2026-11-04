package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Stepping a cat that picks up, carries, tosses, bats, chases and kicks toys must not allocate in
 * steady state (the plan is built up front; building plans is the caller's allocation).
 */
class ToyAllocationTest {
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
    fun toyPlayAllocatesNothingInSteadyState() {
        assumeTrue("thread allocation counting needs a HotSpot JVM", supported())
        for (kind in listOf(ToyKind.MOUSE, ToyKind.YARN, ToyKind.BALL_A)) {
            val scene = ToyScene(kind, toyX = 60f, catX = 20f)
            val steps = ArrayList<PlanStep>()
            repeat(8) {
                steps.add(PlanStep.PickUp(0))
                steps.add(PlanStep.Carry(90f))
                steps.add(PlanStep.Toss(-1, 0.6f))
                steps.add(PlanStep.Chase(0, 3f))
                steps.add(PlanStep.Bat(0, 0.4f))
                steps.add(PlanStep.Chase(0, 3f))
                steps.add(PlanStep.Kick(0, 0.5f, 1f))
                steps.add(PlanStep.Chase(0, 3f))
                steps.add(PlanStep.Pounce(0))
                steps.add(PlanStep.Chase(0, 3f))
            }
            scene.body.replacePlan(1, steps)
            repeat(3_000) { scene.step() }
            val before = allocatedBytes()
            repeat(20_000) {
                scene.step()
                scene.body.pose.events.count
            }
            val used = allocatedBytes() - before
            assertTrue("$kind allocated $used bytes over 20000 steps", used <= TOLERANCE_BYTES)
        }
    }

    private companion object {
        const val TOLERANCE_BYTES = 4_096L
    }
}
