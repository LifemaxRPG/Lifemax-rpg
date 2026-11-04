package com.lifemax.rpg.feature.pet.host

/**
 * Every number of the world host in one place (docs/specs/CAT_BRAIN_PLAN.md 2.3, 2.6, 2.7): how
 * often the world is written to the page, how long the host waits for an answer, the gap a catch-up
 * covers and how it is bounded.
 */
object HostTuning {
    /** The world is written at most this often while it changes (plan 2.3: every 60 s). */
    const val WRITE_EVERY_MS = 60_000L

    /** After a moment or a pair event the write is urgent, but never closer than this. */
    const val URGENT_GAP_MS = 5_000L

    /** A write the page could not store (`unsaved`) is tried again after this. */
    const val RETRY_MS = 30_000L

    /** A refusal for size is answered by trimming and trying again after this. */
    const val TRIM_RETRY_MS = 2_000L

    /** No answer in this long: the command is sent again (every command is repeat-safe by op). */
    const val REPLY_TIMEOUT_MS = 20_000L

    /**
     * How often the world's state is compared with what was last written (cheap, not per frame).
     */
    const val CHANGE_CHECK_MS = 10_000L

    /** A command the page refused this many times (other than `unsaved`) is dropped. */
    const val MAX_ATTEMPTS = 5

    /** A gap shorter than this is simply the app being away for a moment: no catch-up. */
    const val CATCH_UP_MIN_MS = 5 * 60_000L

    /** A catch-up never covers more than one day (plan 2.6). */
    const val CATCH_UP_MAX_MS = 24 * 3_600_000L

    /** A frame never steps the bodies by more than this many seconds (a stall is a catch-up's). */
    const val MAX_STEP_S = 0.1f

    /** Lines of the "while you were away" story, and the longest one. */
    const val AWAY_LINES = 3
    const val AWAY_LINE_MAX = 160

    /** Trim levels a world over its size limit goes through, in order (plan 2.3). */
    const val TRIM_SPOTS_TO = 4
    const val TRIM_LEVEL_SPOTS = 1
    const val TRIM_LEVEL_AWAY = 2
    const val TRIM_LEVEL_TOYS = 3
    const val TRIM_LEVEL_LAST = 4
    const val TRIM_SPOTS_LAST = 2

    /** Where a cat with no saved place stands: the floor by the bowls, a slot apart. */
    const val BOWL_X = 87.0
    const val BOWL_X_SECOND = 121.0
    const val BED_NIGHT_FROM_H = 22.0
    const val BED_NIGHT_TO_H = 7.0

    /** A fresh world's schedule (hours), before the brain draws its own. */
    const val WAKE_H = 6.7
    const val BED_H = 22.75

    /** The drives of a cat that is new to the world (the brain adjusts them within minutes). */
    const val DRIVE_PLAY = 0.4
    const val DRIVE_CURIOSITY = 0.5
    const val DRIVE_SOCIAL = 0.3
    const val DRIVE_SOCIAL_GLASS = 0.2
    const val DRIVE_COMPANY = 0.3
    const val DRIVE_COMFORT = 0.3
    const val DRIVE_TERRITORY = 0.3
    const val DRIVE_GROOM = 0.2
}
