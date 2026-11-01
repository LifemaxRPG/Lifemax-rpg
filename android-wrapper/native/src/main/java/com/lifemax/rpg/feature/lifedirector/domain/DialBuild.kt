package com.lifemax.rpg.feature.lifedirector.domain

/** How a session arrives in a build: launched from the centre, or orbiting from where it was. */
enum class FlyKind {
    NEW,
    MOVED,
}

/** The build's centre words at [at] ms: a kick, a big line and a sub line. */
data class BuildWords(val at: Int, val kick: String, val big: String, val sub: String)

/** The sweep's read-out from angle [a] on: the hour and what fills it. */
data class ReadoutStep(val a: Float, val hour: String, val what: String)

/** A full build's first phase: the arm turns once in [ms] while the read-out follows it. */
data class BuildSweep(val ms: Int, val kick: String, val readout: List<ReadoutStep>)

/**
 * A session that flies into place: from [at] for [ms] ms, touching down at [land]. [a0] and [a1]
 * are its angles before the dial's gaps; [from] is how many degrees back it was (moved only).
 */
data class BuildItem(
    val id: String,
    val kind: FlyKind,
    val a0: Float,
    val a1: Float,
    val from: Float,
    val at: Int,
    val ms: Int,
    val land: Int,
)

/** A session that left the plan and breaks up. */
data class BuildGhost(val a0: Float, val a1: Float)

/**
 * The page's build or repair as runBuild plays it (buildPlan), times in ms from its start. A [full]
 * build sweeps first; a repair shows [intro] until [start]. [keep] are the sessions that stay,
 * [flips] those that left the plan and flash, [ghosts] those dropped. Played once per [id].
 */
data class DialBuild(
    val id: Long,
    val full: Boolean,
    val sweep: BuildSweep?,
    val intro: BuildWords?,
    val start: BuildWords,
    val items: List<BuildItem>,
    val keep: List<String>,
    val flips: List<String>,
    val ghosts: List<BuildGhost>,
    val lock: BuildWords,
    val end: Int,
)
