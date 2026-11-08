package com.lifemax.rpg.core.lifedirector

/**
 * LDT-0: the clock the Life Director reads. The page reads `Date.now()` (milliseconds) for timers
 * and `Math.floor(Date.now() / 1000)` (seconds, rounded down, also before 1970) for everything the
 * engine plans with. The clock is always a parameter; nothing here reads the system clock.
 */
fun interface DirectorClock {
    /** `Date.now()`: milliseconds since 1970-01-01T00:00:00Z. */
    fun nowMillis(): Long

    /** `nowEpoch()`: whole seconds since 1970, rounded down. */
    fun nowEpochSeconds(): Long = Math.floorDiv(nowMillis(), MILLIS_PER_SECOND)

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
