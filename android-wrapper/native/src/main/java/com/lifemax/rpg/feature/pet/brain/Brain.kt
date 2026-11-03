package com.lifemax.rpg.feature.pet.brain

/**
 * The native cat brain as the tools outside it see it (docs/specs/CAT_BRAIN_PLAN.md 1.1): a world
 * of one or two cats driven by a [com.lifemax.rpg.feature.pet.world.WorldClock], asked what each
 * cat is doing now and why. The day viewer and the golden day traces drive any implementation
 * through this; CB0 ships only a stand-in (tests), CB1 the real brain. This package is headless:
 * pure Kotlin, no Android or Compose type (HeadlessCheckTest fails the build otherwise).
 */
interface Brain {
    /** The cats' names, by slot (the order the page lists them). */
    val names: List<String>

    /** Brings the world up to simulated sub-step [step] (called once per sub-step, in order). */
    fun step(step: Long, think: Boolean)

    /** What cat [slot] is doing now. */
    fun now(slot: Int): Doing

    /**
     * The owner's acts in a simulated day (open the view, feed, pet...), which a brain may answer.
     */
    fun owner(act: OwnerAct) {}
}

/**
 * One cat's current activity: its catalogue [activity] (plan 1.8: sleep, loaf, window...), the
 * [place] it happens at (a surface or a spot), the drive [level] that chose it (0..1) and the [why]
 * in a few words, which the explain ring and the day viewer print.
 */
data class Doing(val activity: String, val place: String, val level: Double, val why: String)

/** Something the owner does during a simulated day, at simulated [atMs]. */
data class OwnerAct(val atMs: Long, val kind: String)
