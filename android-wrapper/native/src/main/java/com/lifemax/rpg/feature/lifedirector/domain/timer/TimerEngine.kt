package com.lifemax.rpg.feature.lifedirector.domain.timer

/**
 * Pure timer rules. This first migration slice is NOT a persistence owner. Callers must commit a
 * transition before displaying it or delivering its completion; no clock or reward side effects
 * occur here. Epoch milliseconds deliberately preserve the legacy focus timer's clock semantics.
 */
object TimerEngine {
    data class Timer(
        val activityId: String,
        val generation: Long,
        val plannedMs: Long,
        val pausedAt: Long? = null,
        val pausedMs: Long = 0,
    ) {
        init {
            require(activityId.isNotBlank())
            require(generation in 1 until MAX_TIME)
            require(plannedMs in 1..MAX_TIME)
            require(pausedMs in 0..MAX_TIME)
            require(pausedAt == null || pausedAt in 1..MAX_TIME)
        }

        fun elapsedMs(nowMs: Long): Long =
            ((pausedAt ?: nowMs) - generation - pausedMs).coerceAtLeast(0)

        fun due(nowMs: Long): Boolean = pausedAt == null && elapsedMs(nowMs) >= plannedMs
    }

    /** Eligibility comes from the schedule owner; this engine never guesses it from a title. */
    data class Target(val activityId: String, val plannedMs: Long, val available: Boolean = true)

    data class Key(val activityId: String, val generation: Long) {
        fun matches(timer: Timer?): Boolean =
            timer != null && activityId == timer.activityId && generation == timer.generation
    }

    sealed interface Command {
        data class Start(val target: Target) : Command

        data class Replace(val expected: Key, val target: Target) : Command

        data class Pause(val expected: Key) : Command

        data class Resume(val expected: Key) : Command

        data class Reset(val expected: Key) : Command

        data class Stop(val expected: Key) : Command

        data class Finish(val expected: Key, val automatic: Boolean = false) : Command
    }

    enum class Status {
        CHANGED,
        UNCHANGED,
        STALE_TIMER,
        TIMER_EXISTS,
        INVALID_TARGET,
    }

    /** A completion proposal, never an XP award. Persist it in an outbox before delivery. */
    data class Completion(val timer: Key, val actualStartEpoch: Long, val minutes: Long)

    data class Transition(
        val timer: Timer?,
        val status: Status,
        val completion: Completion? = null,
    )

    // Bounds keep imported values and arithmetic within JavaScript's exact integer range.
    const val MAX_TIME = 8_640_000_000_000_000L

    fun reduce(current: Timer?, command: Command, nowMs: Long): Transition {
        require(nowMs in 1 until MAX_TIME)
        fun unchanged(status: Status = Status.UNCHANGED) = Transition(current, status)
        fun changed(timer: Timer?, completion: Completion? = null) =
            Transition(timer, Status.CHANGED, completion)
        fun valid(target: Target) =
            target.available && target.activityId.isNotBlank() && target.plannedMs in 1..MAX_TIME
        fun start(target: Target, at: Long) = Timer(target.activityId, at, target.plannedMs)

        when (command) {
            is Command.Start -> {
                if (!valid(command.target)) return unchanged(Status.INVALID_TARGET)
                if (current?.activityId == command.target.activityId) return unchanged()
                if (current != null) return unchanged(Status.TIMER_EXISTS)
                return changed(start(command.target, nowMs))
            }
            is Command.Replace -> {
                if (!valid(command.target)) return unchanged(Status.INVALID_TARGET)
                if (!command.expected.matches(current)) return unchanged(Status.STALE_TIMER)
                if (current!!.activityId == command.target.activityId) return unchanged()
                return changed(start(command.target, maxOf(nowMs, current.generation + 1)))
            }
            else -> Unit
        }
        val expected =
            when (command) {
                is Command.Pause -> command.expected
                is Command.Resume -> command.expected
                is Command.Reset -> command.expected
                is Command.Stop -> command.expected
                is Command.Finish -> command.expected
                else -> error("Start and Replace handled above")
            }
        if (!expected.matches(current)) return unchanged(Status.STALE_TIMER)
        val timer = requireNotNull(current)
        return when (command) {
            is Command.Pause ->
                if (timer.pausedAt != null) unchanged() else changed(timer.copy(pausedAt = nowMs))
            is Command.Resume -> {
                val pausedAt = timer.pausedAt ?: return unchanged()
                // A backwards wall-clock jump must not create negative accumulated pause time.
                val pause = (nowMs - pausedAt).coerceAtLeast(0)
                changed(
                    timer.copy(
                        pausedAt = null,
                        pausedMs = (timer.pausedMs + pause).coerceAtMost(MAX_TIME),
                    )
                )
            }
            is Command.Reset -> {
                val at = maxOf(nowMs, timer.generation + 1)
                changed(timer.copy(generation = at, pausedAt = at, pausedMs = 0))
            }
            is Command.Stop -> changed(null)
            is Command.Finish -> {
                if (command.automatic && !timer.due(nowMs)) return unchanged()
                val duration = if (command.automatic) timer.plannedMs else timer.elapsedMs(nowMs)
                val minutes = ((duration + 30_000) / 60_000).coerceAtLeast(1)
                changed(null, Completion(expected, timer.generation / 1000, minutes))
            }
            else -> error("Start and Replace handled above")
        }
    }
}
