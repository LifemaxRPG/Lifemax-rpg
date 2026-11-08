package com.lifemax.rpg.core.lifedirector

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A time zone as the page knows it (LDT-3): a tz database zone, whose offsets come from
 * `java.time`'s rules, or a fixed offset in seconds. A fixed offset is kept as a number because the
 * page accepts offsets up to 23:59 and `java.time.ZoneOffset` stops at 18:00.
 */
class PageZone private constructor(private val region: ZoneId?, private val fixedSeconds: Int) {
    /** The offset in seconds in force at [instant]. */
    fun offsetAt(instant: Instant): Int =
        region?.rules?.getOffset(instant)?.totalSeconds ?: fixedSeconds

    /**
     * The offsets (seconds) with which the wall clock [local] occurs: none in a gap, two in a fold.
     */
    fun offsetsOf(local: LocalDateTime): List<Int> =
        region?.rules?.getValidOffsets(local)?.map { it.totalSeconds } ?: listOf(fixedSeconds)

    /** The offset (seconds) before the gap that [local] falls in. */
    fun offsetBeforeGap(local: LocalDateTime): Int =
        requireNotNull(region?.rules?.getTransition(local)) { "not in a gap" }
            .offsetBefore
            .totalSeconds

    override fun toString(): String = region?.id ?: "fixed $fixedSeconds s"

    companion object {
        /** A tz database zone (or a `java.time` fixed offset). */
        fun of(zone: ZoneId): PageZone = PageZone(zone, 0)

        /** A fixed offset of [seconds] east of UTC. */
        fun fixed(seconds: Int): PageZone = PageZone(null, seconds)
    }
}
