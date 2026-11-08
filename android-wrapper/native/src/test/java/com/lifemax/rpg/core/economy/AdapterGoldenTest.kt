package com.lifemax.rpg.core.economy

import com.lifemax.rpg.core.adapters.DeviceClock
import com.lifemax.rpg.core.adapters.HealthReads
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The economy golden families `health`, `quest`, `zone` and `time` replay through the adapter
 * layer: [GoldenRun] gives the pure code only what `ContextFactory` builds from the recorded save,
 * clock, zone and Health Connect answers (every other family does too). The mutation checks make
 * the adapters wrong on purpose and expect the replay to notice.
 */
class AdapterGoldenTest {
    private fun family(name: String) =
        EconomyGoldenTest.cases.filter { (it["id"] as String).startsWith("$name/") }

    private fun failing(families: List<String>, mutate: (GoldenRun) -> Unit = {}): List<String> =
        families
            .flatMap(::family)
            .filter { EconomyGoldenTest.differences(it, mutate).isNotEmpty() }
            .map { it["id"] as String }

    @Test
    fun theFamiliesAreAllThere() {
        assertEquals(63, family("health").size)
        assertEquals(60, family("quest").size)
        assertEquals(7, family("zone").size)
        assertEquals(11, family("time").size)
    }

    @Test
    fun healthQuestZoneAndTimeCasesReproduceThroughTheAdapters() {
        val failed = failing(listOf("health", "quest", "zone", "time"))
        assertTrue("these cases differ through the adapters: $failed", failed.isEmpty())
    }

    @Test
    fun healthReadsThatLoseTheStepsAreNoticed() {
        val lost =
            failing(listOf("health")) { run ->
                run.wrapReads = { inner ->
                    object : HealthReads by inner {
                        override fun steps(from: Long, to: Long) =
                            emptyList<HealthConnect.StepDay>()
                    }
                }
            }
        assertTrue("only ${lost.size} health cases noticed: $lost", lost.size >= 3)
    }

    @Test
    fun healthReadsThatLoseTheSleepAreNoticed() {
        val lost =
            failing(listOf("health")) { run ->
                run.wrapReads = { inner ->
                    object : HealthReads by inner {
                        override fun sleep(from: Long, to: Long) =
                            emptyList<HealthConnect.SleepSession>()
                    }
                }
            }
        assertTrue("only ${lost.size} health cases noticed: $lost", lost.isNotEmpty())
    }

    @Test
    fun aDeviceStuckInUtcIsNoticed() {
        val lost =
            failing(listOf("zone", "time", "health", "quest")) { run ->
                run.wrapDevice = { inner ->
                    object : DeviceClock {
                        override fun nowMillis() = inner.nowMillis()

                        override fun zone(): ZoneId = ZoneOffset.UTC
                    }
                }
            }
        assertTrue("only ${lost.size} cases noticed: $lost", lost.size >= 3)
    }

    @Test
    fun aDeviceAnHourFastIsNoticed() {
        val lost =
            failing(listOf("time", "health", "quest")) { run ->
                run.wrapDevice = { inner ->
                    object : DeviceClock {
                        override fun nowMillis() = inner.nowMillis() + 3_600_000L

                        override fun zone(): ZoneId = inner.zone()
                    }
                }
            }
        assertTrue("only ${lost.size} cases noticed: $lost", lost.size >= 3)
    }
}
