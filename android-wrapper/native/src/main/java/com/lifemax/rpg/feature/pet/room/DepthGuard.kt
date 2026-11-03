package com.lifemax.rpg.feature.pet.room

/**
 * The numbers of the performance guard: when a phone cannot keep up with the 2.5D room it steps
 * down MEDIUM, SUBTLE, off (the classic flat room), and it steps back up only after a long calm.
 * All costs are shares of the display's frame budget (16.7 ms at 60 Hz, 8.3 ms at 120 Hz), so the
 * same numbers serve every refresh rate.
 */
internal object GuardTuning {
    /** The frames averaged: a moving average over this many. 1.5 s at 60 Hz. */
    const val WINDOW = 90

    /**
     * Frames ignored after the guard starts or resumes: the first frames make the room's pictures.
     */
    const val WARMUP_FRAMES = 45

    /** Step down when the average cost is above this share of a frame's budget. */
    const val DOWN_ABOVE = 0.6f

    /** A step up needs the average below this share, and below it for [CALM_MS]. */
    const val UP_BELOW = 0.3f

    /** How long the room must have been calm to step up one level: two minutes. */
    const val CALM_MS = 120_000L

    /** After any change nothing changes again for this long, so the new level is judged fairly. */
    const val HOLD_MS = 4_000L

    /** A step up that has to be undone within this long doubles the calm needed for the next. */
    const val PROBATION_MS = 20_000L

    /** The calm needed never grows past this many times [CALM_MS]. */
    const val MAX_BACKOFF = 8

    /** One frame never counts for more than this many budgets (a stall is not a trend). */
    const val MAX_SAMPLE_BUDGETS = 6f

    /** The budget when the display's refresh rate is not known: 60 Hz. */
    const val DEFAULT_BUDGET_MS = 1000f / 60f
}

/**
 * Keeps the 2.5D room's depth within what the phone can draw. It is fed the cost of each frame
 * ([record]: the milliseconds the room took on the main thread, and how long the frame lasted) and
 * answers with the [level] to draw at: [DepthLevel.MEDIUM] (the owner's choice), then
 * [DepthLevel.SUBTLE], then null (the classic flat room).
 * - **Down.** A frame's cost is the larger of its own work and how far the frame ran past the
 *   budget. When the average of the last [GuardTuning.WINDOW] frames is above
 *   [GuardTuning.DOWN_ABOVE] of the budget, the level drops one step.
 * - **Up.** Only when the average has stayed below [GuardTuning.UP_BELOW] for [GuardTuning.CALM_MS]
 *   without a break. A step up is on probation for [GuardTuning.PROBATION_MS]: if it has to be
 *   undone then, the next calm needed doubles (up to [GuardTuning.MAX_BACKOFF] times), so a phone
 *   on the edge settles instead of flickering.
 * - **Between.** A change clears the window, ignores the next [GuardTuning.WARMUP_FRAMES] and holds
 *   for [GuardTuning.HOLD_MS]; the gap between the two thresholds is the hysteresis.
 *
 * Pure arithmetic on a few numbers: the clock is given ([clock], milliseconds, any origin), so a
 * test drives it with a fake one. Nothing is logged and nothing is kept past this object.
 *
 * @param best the highest level it may rise to.
 * @param clock the time in milliseconds.
 */
internal class DepthGuard(
    private val best: DepthLevel = DepthLevel.MEDIUM,
    private val clock: () -> Long,
) {
    /** The level to draw at; null means the classic flat room. */
    var level: DepthLevel? = best
        private set

    /** How many times the level has changed (for tests and the owner's curiosity, never logged). */
    var changes = 0
        private set

    private val costs = FloatArray(GuardTuning.WINDOW)
    private var filled = 0
    private var next = 0
    private var sum = 0f
    private var skip = GuardTuning.WARMUP_FRAMES
    private var heldUntil = Long.MIN_VALUE
    private var calmSince = NOT_CALM
    private var probationUntil = Long.MIN_VALUE
    private var backoff = 1

    /** The average cost over the window, as a share of the budget; 0 before it is full. */
    val load: Float
        get() = if (filled < costs.size) 0f else sum / costs.size

    /**
     * One frame: it took [workMs] of the main thread's time and lasted [intervalMs] since the last
     * one, on a display whose frame budget is [budgetMs]. Returns true if the level changed.
     */
    fun record(
        workMs: Float,
        intervalMs: Float,
        budgetMs: Float = GuardTuning.DEFAULT_BUDGET_MS,
    ): Boolean {
        if (skip > 0) {
            skip--
            return false
        }
        val over = intervalMs - budgetMs
        val cost = maxOf(workMs, over).coerceAtLeast(0f) / budgetMs
        add(cost.coerceAtMost(GuardTuning.MAX_SAMPLE_BUDGETS))
        if (filled < costs.size) return false
        val now = clock()
        val average = sum / costs.size
        if (average >= GuardTuning.UP_BELOW) calmSince = NOT_CALM
        else if (calmSince == NOT_CALM) calmSince = now
        if (probationUntil != Long.MIN_VALUE && now >= probationUntil) {
            probationUntil = Long.MIN_VALUE // the step up held: the next one is not on trial
            backoff = 1
        }
        if (now < heldUntil) return false
        if (average > GuardTuning.DOWN_ABOVE) return stepDown(now)
        val calm = calmSince != NOT_CALM && now - calmSince >= GuardTuning.CALM_MS * backoff
        return calm && stepUp(now)
    }

    /** The app came back to the front (or the room to the screen): start the averages afresh. */
    fun resume() {
        clearWindow()
        skip = GuardTuning.WARMUP_FRAMES
        calmSince = NOT_CALM
    }

    private fun stepDown(now: Long): Boolean {
        val lower =
            when (level) {
                DepthLevel.RICH -> DepthLevel.MEDIUM
                DepthLevel.MEDIUM -> DepthLevel.SUBTLE
                DepthLevel.SUBTLE -> null
                null -> return false
            }
        if (now < probationUntil) backoff = minOf(backoff * 2, GuardTuning.MAX_BACKOFF)
        probationUntil = Long.MIN_VALUE
        return change(lower, now)
    }

    private fun stepUp(now: Long): Boolean {
        val higher =
            when (level) {
                null -> DepthLevel.SUBTLE
                DepthLevel.SUBTLE -> DepthLevel.MEDIUM
                DepthLevel.MEDIUM -> DepthLevel.RICH
                DepthLevel.RICH -> return false
            }
        if (higher.ordinal > best.ordinal) return false
        probationUntil = now + GuardTuning.PROBATION_MS
        return change(higher, now)
    }

    private fun change(to: DepthLevel?, now: Long): Boolean {
        level = to
        changes++
        heldUntil = now + GuardTuning.HOLD_MS
        calmSince = NOT_CALM
        clearWindow()
        skip = GuardTuning.WARMUP_FRAMES
        return true
    }

    private fun add(cost: Float) {
        if (filled == costs.size) sum -= costs[next] else filled++
        costs[next] = cost
        sum += cost
        next = (next + 1) % costs.size
    }

    private fun clearWindow() {
        filled = 0
        next = 0
        sum = 0f
    }

    private companion object {
        const val NOT_CALM = Long.MIN_VALUE
    }
}
