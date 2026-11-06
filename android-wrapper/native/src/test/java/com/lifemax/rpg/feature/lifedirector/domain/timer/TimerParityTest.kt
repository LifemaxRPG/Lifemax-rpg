package com.lifemax.rpg.feature.lifedirector.domain.timer

import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Command
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Key
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Target
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Timer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TimerParityTest {
    private fun JSONObject.timer(field: String): Timer? {
        val obj = optJSONObject(field) ?: return null
        return Timer(
            obj.getString("id"),
            obj.getLong("start"),
            obj.getLong("plannedMs"),
            if (obj.isNull("paused")) null else obj.getLong("paused"),
            obj.getLong("pausedMs"),
        )
    }

    @Test
    fun matchesActualJavascriptTransitions() {
        val json = javaClass.getResource("/fixtures/director-timer-rules.json")!!.readText()
        val rows = JSONArray(json)
        for (i in 0 until rows.length()) {
            val row = rows.getJSONObject(i)
            val before = row.timer("before")
            val id = row.getString("id")
            val key = Key(before?.activityId ?: id, before?.generation ?: 1)
            val command =
                when (row.getString("act")) {
                    "start" -> Command.Start(Target(id, 900_000))
                    "replace" -> Command.Replace(key, Target(id, 900_000))
                    "pause" -> Command.Pause(key)
                    "resume" -> Command.Resume(key)
                    "reset" -> Command.Reset(key)
                    "cancel" -> Command.Stop(key)
                    "finish" -> Command.Finish(key)
                    "autoFinish" -> Command.Finish(key, automatic = true)
                    else -> error("Unknown fixture command")
                }
            val result = TimerEngine.reduce(before, command, row.getLong("now"))
            assertEquals("transition $i", row.timer("after"), result.timer)
            val expected = row.optJSONObject("completion")
            if (expected == null) assertNull("transition $i", result.completion)
            else {
                val actual = requireNotNull(result.completion)
                assertEquals(expected.getString("activityId"), actual.timer.activityId)
                assertEquals(expected.getLong("minutes"), actual.minutes)
                assertEquals(expected.getLong("actualStartEpoch"), actual.actualStartEpoch)
            }
        }
    }
}
