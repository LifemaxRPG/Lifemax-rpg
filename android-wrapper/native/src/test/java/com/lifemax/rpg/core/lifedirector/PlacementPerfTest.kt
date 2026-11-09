package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How long the native construction (WP-LD4) takes on a large plan: four weeks, office hours with a
 * commute, every archetype as an advanced goal at the hardcore intensity, five quests (one daily, a
 * chain of two), a check-in. The time is printed; the bound only catches a blow-up, since a test
 * machine's speed varies (and a phone is several times slower than the JVM on a workstation).
 */
class PlacementPerfTest {
    @Test
    fun aFourWeekPlanIsBuiltInReasonableTime() {
        val read = JsonReader.read(INPUT)
        check(read is JsonRead.Parsed)
        val input = read.value
        repeat(WARM_UP) { PlanConstruction.construct(input) }
        val times = LongArray(RUNS)
        var placed = 0
        for (i in 0 until RUNS) {
            val t0 = System.nanoTime()
            val c = PlanConstruction.construct(input)
            times[i] = System.nanoTime() - t0
            placed = c.placements.size
        }
        times.sort()
        val ms = times[RUNS / 2] / NANOS_PER_MS
        println("placement perf: 28 days, $placed sessions placed, median ${ms} ms over $RUNS runs")
        assertTrue("the construction took $ms ms", ms < LIMIT_MS)
    }

    companion object {
        private const val WARM_UP = 2
        private const val RUNS = 5
        private const val NANOS_PER_MS = 1_000_000L
        private const val LIMIT_MS = 30_000L

        private val INPUT =
            """
            {"timezone":"Europe/London","currentTimeEpoch":1793001600,"horizonStartEpoch":1793001600,"horizonDays":28,
             "playerAvailability":{"timezone":"Europe/London","sleepRequirementMinutes":480,"rules":[
               {"id":"sleep","kind":"SLEEP","startMin":1380,"durationMin":480},
               {"id":"work","kind":"WORK","days":[1,2,3,4,5],"startMin":540,"durationMin":480,"commuteMin":30,"load":"MIXED_MODERATE"},
               {"id":"gym-class","kind":"CLASS","days":[2,4],"startMin":1110,"durationMin":60},
               {"id":"pref","kind":"PREFERRED","startMin":1080,"durationMin":120}]},
             "fixedCommitments":[{"id":"trip","title":"Trip","startEpoch":1794200400,"endEpoch":1794229200,"kind":"APPOINTMENT"}],
             "goals":[
               {"id":"g-1","title":"Lift","archetypeId":"FITNESS_HYPERTROPHY","priority":5,"level":"ADVANCED"},
               {"id":"g-2","title":"Spanish","archetypeId":"SKILL_LANGUAGE","subject":"spanish","priority":4,"level":"ADVANCED"},
               {"id":"g-3","title":"Read","archetypeId":"HABIT_READING","priority":3,"level":"ADVANCED","createdEpoch":1790000000,
                "target":{"units":3600,"completed":200,"unit":"pages","unitsPerHour":30,"deadlineEpoch":1800000000}},
               {"id":"g-4","title":"Money","archetypeId":"ADMIN_FINANCE","priority":3,"level":"ADVANCED"},
               {"id":"g-5","title":"Run","archetypeId":"FITNESS_CARDIO","priority":4,"level":"ADVANCED"},
               {"id":"g-6","title":"Exam","archetypeId":"STUDY_GENERAL","priority":5,"level":"ADVANCED"},
               {"id":"g-7","title":"Guitar","archetypeId":"CREATIVE_PRACTICE","priority":2,"level":"ADVANCED"},
               {"id":"g-8","title":"Calm","archetypeId":"MINDFULNESS","priority":2,"level":"ADVANCED"}],
             "activeQuests":[
               {"id":"q-1","title":"Lift 3","tier":"CORE","tag":"fitness.strength","requiredCount":3,"deadlineEpoch":1793606400},
               {"id":"q-2","title":"Daily vocab","tier":"CORE","tag":"language.spanish.vocab","requiredCount":1,"recurrence":"DAILY"},
               {"id":"q-3","title":"Tax","tier":"MANDATORY","tag":"admin.finance","requiredCount":2,"deadlineEpoch":1794000000},
               {"id":"q-4","title":"Chapter","tier":"CORE","tag":"study.deep","requiredCount":2,"prerequisiteQuestIds":["q-3"],"deadlineEpoch":1794800000},
               {"id":"q-5","title":"Jam","tier":"OPTIONAL","tag":"creative.practice","requiredMinutes":120,"deadlineEpoch":1795000000}],
             "existingSchedule":[],"completionHistory":[{"startEpoch":1792900000,"templateId":"g-1:strength","spacingKey":"g-1:strength"}],
             "energyProfile":{"checkin":{"epoch":1793008800,"level":"NORMAL"}},
             "playerPreferences":{"intensityLevel":"HARDCORE","maxDailyGoalMinutes":300,"preferredRestDays":[0]}}
            """
                .trimIndent()
    }
}
