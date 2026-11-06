package com.lifemax.rpg.feature.lifedirector.domain.timer

import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Command
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Key
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Status
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Target
import com.lifemax.rpg.feature.lifedirector.domain.timer.TimerEngine.Timer
import org.junit.Assert.*
import org.junit.Test

class TimerEngineTest {
    private val at = 1_800_000_000_000L
    private val timer = Timer("walk", at, 900_000)
    private val key = Key("walk", at)

    @Test
    fun startRequiresAnAvailableTargetAndNeverReplacesImplicitly() {
        assertEquals(
            Status.INVALID_TARGET,
            TimerEngine.reduce(null, Command.Start(Target("", 1)), at).status,
        )
        assertEquals(
            Status.INVALID_TARGET,
            TimerEngine.reduce(null, Command.Start(Target("walk", 1, false)), at).status,
        )
        assertEquals(
            timer,
            TimerEngine.reduce(null, Command.Start(Target("walk", 900_000)), at).timer,
        )
        assertEquals(
            Status.UNCHANGED,
            TimerEngine.reduce(timer, Command.Start(Target("walk", 900_000)), at + 1).status,
        )
        assertEquals(
            Status.TIMER_EXISTS,
            TimerEngine.reduce(timer, Command.Start(Target("read", 900_000)), at + 1).status,
        )
    }

    @Test
    fun pauseResumeExcludesPauseAndSurvivesRecreation() {
        val paused = TimerEngine.reduce(timer, Command.Pause(key), at + 60_000).timer!!
        assertEquals(60_000L, paused.elapsedMs(at + 600_000))
        // Reconstruct solely from persisted fields; no in-process ticker state is required.
        val restored =
            Timer(
                paused.activityId,
                paused.generation,
                paused.plannedMs,
                paused.pausedAt,
                paused.pausedMs,
            )
        val resumed = TimerEngine.reduce(restored, Command.Resume(key), at + 600_000).timer!!
        assertEquals(120_000L, resumed.elapsedMs(at + 660_000))
        assertEquals(
            Status.UNCHANGED,
            TimerEngine.reduce(resumed, Command.Resume(key), at + 660_000).status,
        )
        assertFalse(paused.due(at + 86_400_000))
    }

    @Test
    fun resetPausesAtZeroAndInvalidatesOldCommands() {
        val reset = TimerEngine.reduce(timer, Command.Reset(key), at).timer!!
        assertEquals(at + 1, reset.generation)
        assertEquals(0L, reset.elapsedMs(at + 500_000))
        assertEquals(reset.generation, reset.pausedAt)
        assertEquals(timer.plannedMs, reset.plannedMs)
        for (command in
            listOf(
                Command.Pause(key),
                Command.Resume(key),
                Command.Reset(key),
                Command.Stop(key),
                Command.Finish(key),
            )) {
            val result = TimerEngine.reduce(reset, command, at + 1)
            assertEquals(Status.STALE_TIMER, result.status)
            assertEquals(reset, result.timer)
            assertNull(result.completion)
        }
    }

    @Test
    fun replacementDiscardsElapsedTimeWithoutCreatingAReward() {
        for (old in listOf(timer, timer.copy(pausedAt = at + 20_000))) {
            val switched =
                TimerEngine.reduce(
                    old,
                    Command.Replace(key, Target("read", 1_800_000)),
                    at + 60_000,
                )
            assertEquals("read", switched.timer!!.activityId)
            assertEquals(0L, switched.timer.elapsedMs(at + 60_000))
            assertNull(switched.completion)
            assertEquals(
                Status.STALE_TIMER,
                TimerEngine.reduce(
                        switched.timer,
                        Command.Replace(key, Target("read", 1_800_000)),
                        at + 60_001,
                    )
                    .status,
            )
        }
    }

    @Test
    fun automaticCompletionCapsCreditAtPlanAfterLongBackgroundGap() {
        assertEquals(
            Status.UNCHANGED,
            TimerEngine.reduce(timer, Command.Finish(key, true), at + 899_999).status,
        )
        for (now in listOf(at + 900_000, at + 86_400_000)) {
            val done = TimerEngine.reduce(timer, Command.Finish(key, true), now)
            assertNull(done.timer)
            assertEquals(15L, done.completion!!.minutes)
            assertEquals(at / 1000, done.completion.actualStartEpoch)
            assertEquals(key, done.completion.timer)
        }
    }

    @Test
    fun manualCompletionMatchesJavascriptRoundingAndStopNeverLogs() {
        for ((ms, minutes) in listOf(0L to 1L, 89_999L to 1L, 90_000L to 2L, 180_000L to 3L)) {
            assertEquals(
                minutes,
                TimerEngine.reduce(timer, Command.Finish(key), at + ms).completion!!.minutes,
            )
        }
        val stopped = TimerEngine.reduce(timer, Command.Stop(key), at + 60_000)
        assertNull(stopped.timer)
        assertNull(stopped.completion)
        assertEquals(
            Status.STALE_TIMER,
            TimerEngine.reduce(null, Command.Finish(key), at + 60_001).status,
        )
    }

    @Test
    fun staleActivityCannotControlAnotherTimerEvenWithSameTimestamp() {
        val wrong = Key("other", at)
        assertEquals(Status.STALE_TIMER, TimerEngine.reduce(timer, Command.Pause(wrong), at).status)
        assertEquals(
            Status.STALE_TIMER,
            TimerEngine.reduce(timer, Command.Finish(wrong), at).status,
        )
    }

    @Test
    fun backwardsClockCannotProduceNegativeElapsedOrPause() {
        assertEquals(0L, timer.elapsedMs(at - 100))
        val paused = timer.copy(pausedAt = at + 10_000)
        val resumed = TimerEngine.reduce(paused, Command.Resume(key), at + 5_000).timer!!
        assertEquals(0L, resumed.pausedMs)
        assertEquals(5_000L, resumed.elapsedMs(at + 5_000))
    }

    @Test
    fun proposedChangesDoNotMutateTheInputBeforePersistence() {
        val result = TimerEngine.reduce(timer, Command.Pause(key), at + 10_000)
        assertNull(timer.pausedAt)
        assertNotNull(result.timer!!.pausedAt)
        // A storage adapter can discard a candidate on failed commit and retain this exact input.
        assertEquals(20_000L, timer.elapsedMs(at + 20_000))
    }
}
