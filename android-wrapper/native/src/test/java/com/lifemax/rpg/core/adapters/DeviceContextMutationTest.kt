package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.adapters.DeviceContextReplay.Mutation
import com.lifemax.rpg.core.adapters.DeviceContextReplay.cases
import com.lifemax.rpg.core.adapters.DeviceContextReplay.differences
import com.lifemax.rpg.core.adapters.DeviceContextReplay.family
import com.lifemax.rpg.core.adapters.DeviceContextReplay.id
import com.lifemax.rpg.core.save.JsonObject
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mutation spot checks: each deliberately wrong adapter input must make the golden replay fail in
 * the family it should, so the replay is known to notice the mistake it guards against.
 */
class DeviceContextMutationTest {
    private fun failing(families: Set<String>, mutation: Mutation): List<String> =
        cases
            .filter { family(it) in families }
            .filter { differences(it, mutation).isNotEmpty() }
            .map(::id)

    private fun assertCaught(families: Set<String>, mutation: Mutation, least: Int = 1) {
        val caught = failing(families, mutation)
        assertTrue("only ${caught.size} cases noticed the mutation: $caught", caught.size >= least)
    }

    private fun shifted(clock: DeviceClock, millis: Long) =
        object : DeviceClock {
            override fun nowMillis() = clock.nowMillis() + millis

            override fun zone(): ZoneId = clock.zone()
        }

    private fun without(view: SaveView, vararg keys: String) =
        SaveView(view.economy, JsonObject(view.stored.members - keys.toSet()))

    @Test
    fun aClockInTheWrongZoneIsNoticed() =
        assertCaught(
            CLOCKS,
            Mutation(
                device = { c ->
                    object : DeviceClock {
                        override fun nowMillis() = c.nowMillis()

                        override fun zone(): ZoneId = ZoneOffset.UTC
                    }
                }
            ),
            least = 15,
        )

    @Test
    fun aClockAHourFastIsNoticed() =
        assertCaught(CLOCKS, Mutation(device = { shifted(it, HOUR) }), least = 8)

    @Test
    fun aClockADayFastIsNoticedEverywhere() =
        assertCaught(CLOCKS, Mutation(device = { shifted(it, 24 * HOUR) }), least = 30)

    @Test
    fun forgettingTheWaterLogIsNoticed() =
        assertCaught(FACTS, Mutation(save = { without(it, "waterLog") }), least = 10)

    @Test
    fun forgettingTheCatIsNoticed() =
        assertCaught(FACTS, Mutation(save = { without(it, "pet") }), least = 5)

    @Test
    fun forgettingTheLuckRollIsNoticed() =
        assertCaught(FACTS, Mutation(save = { without(it, "luck") }), least = 3)

    @Test
    fun aPlanThatIsNeverARestDayIsNoticed() =
        assertCaught(FACTS, Mutation(plan = { FakePlanFacts() }), least = 3)

    @Test
    fun aPlanWhoseEveryDayIsDoneIsNoticed() =
        assertCaught(
            FACTS,
            Mutation(
                plan = { p ->
                    object : PlanFacts by p {
                        override fun isDone(day: String) = true
                    }
                }
            ),
            least = 3,
        )

    private companion object {
        const val HOUR = 3_600_000L
        val CLOCKS = setOf("clock", "clockmove", "zonemove")
        val FACTS = setOf("facts")
    }
}
