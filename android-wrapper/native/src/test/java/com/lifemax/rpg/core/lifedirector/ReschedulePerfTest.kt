package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.member
import com.lifemax.rpg.core.lifedirector.PageValues.number
import com.lifemax.rpg.core.lifedirector.ScheduleJson.num
import com.lifemax.rpg.core.lifedirector.ScheduleJson.obj
import com.lifemax.rpg.core.lifedirector.ScheduleJson.str
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonValue
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long native rescheduling (WP-LD5b) takes on the largest plan the tests know: the four-week
 * plan of [SchedulePerfTest] (every archetype at the hardcore intensity, its repair spending the
 * whole budget), then a missed urgent session, a sick spell of two days over its start and a fresh
 * plan of a changed availability (TIME_CHANGED, which plans the whole input again). Each is timed
 * as the median of several runs and printed; the bound only catches a blow-up.
 */
class ReschedulePerfTest {
    @Test
    fun reschedulingAFourWeekPlanTakesReasonableTime() {
        val read = JsonReader.read(SchedulePerfTest.INPUT)
        check(read is JsonRead.Parsed)
        val input = read.value
        val plan = PlanSchedule.generate(input)
        val now = number(member(input, "currentTimeEpoch"))
        val urgent =
            (member(plan, "scheduledInstances") as JsonArray).items.first {
                number(member(it, "tier")) >= URGENT && number(member(it, "startEpoch")) > now
            }
        val triggers =
            linkedMapOf(
                "missed" to
                    obj {
                        this["type"] = str("MISSED")
                        this["instanceId"] = member(urgent, "instanceId")
                    },
                "sick" to
                    obj {
                        this["type"] = str("SICK")
                        this["commitment"] = obj {
                            this["id"] = str("s")
                            this["title"] = str("Sick")
                            this["startEpoch"] = num(now)
                            this["endEpoch"] = num(now + SICK_SECONDS)
                        }
                    },
                "time" to obj { this["type"] = str("TIME_CHANGED") },
            )
        for ((name, trigger) in triggers) {
            val request: JsonValue = obj {
                this["engineInput"] = input
                this["currentOutput"] = plan
                this["trigger"] = trigger
            }
            repeat(WARM_UP) { Reschedule.run(request) }
            val times = LongArray(RUNS)
            var result: RescheduleResult? = null
            for (i in 0 until RUNS) {
                val t0 = System.nanoTime()
                result = Reschedule.run(request)
                times[i] = System.nanoTime() - t0
            }
            times.sort()
            val ms = times[RUNS / 2] / NANOS_PER_MS
            val sessions =
                (member(result!!.updatedSchedule, "scheduledInstances") as JsonArray).items.size
            println(
                "reschedule perf: 28 days, $name, $sessions sessions, median $ms ms over $RUNS runs"
            )
            assertTrue("$name took $ms ms", ms < LIMIT_MS)
        }
    }

    companion object {
        private const val URGENT = 3.0
        private const val SICK_SECONDS = 2 * 86400.0
        private const val WARM_UP = 2
        private const val RUNS = 5
        private const val NANOS_PER_MS = 1_000_000L
        private const val LIMIT_MS = 60_000L
    }
}
