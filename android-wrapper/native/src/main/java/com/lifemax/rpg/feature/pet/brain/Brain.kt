package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.world.WorldBlock
import com.lifemax.rpg.feature.pet.world.WorldCommand

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

    /**
     * Fast-forwards a [gapMs] gap the app was not watching (plan 2.6): coarse, deterministic for
     * the same state and gap, at most 24 h (the host caps it) and under 5 ms. Answers where the
     * cats ended up and up to three lines of what they did; the default does nothing.
     */
    fun catchUp(gapMs: Long): CatchUp = CatchUp.NONE

    /** The brain's state for the world blob, or null for a brain with none to keep. */
    fun export(): BrainState? = null

    /** Takes back a state saved by [export] (the host calls it once, before the first step). */
    fun restore(state: BrainState) {}

    /** The page's newest world block (care caps, hunger stage, cues, context), by `seq`. */
    fun update(block: WorldBlock) {}

    /**
     * The commands the brain wants sent to the page since the last call (moments, social, dwell).
     * Answers a shared empty list when there are none, so asking every think quantum costs nothing.
     */
    fun takeReports(): List<WorldCommand> = emptyList()

    /**
     * Whether this brain moves the bodies itself ([bind] hands them over) rather than the autopilot
     * of the shipped house.
     */
    val drivesBodies: Boolean
        get() = false

    /** The house's bodies, once, when [drivesBodies]; the brain keeps them. */
    fun bind(bodies: List<CatBody>) {}

    /** The house's toys (the field the bodies were given), once, when [drivesBodies]. */
    fun bindToys(toys: ToyField) {}

    /**
     * The owner tossed the page's toy [key] into the room at world epoch [atMs] (the Cat tab's toy
     * tray): it lands in the field given to [bindToys], near the cats, and a cat that wants to
     * plays with it. Answers which, or why none would.
     */
    fun offerToy(key: String, atMs: Long): PlayAnswer = PlayAnswer(-1, PlayWords.NOT_HERE)
}

/**
 * What became of a toy the owner offered: cat [slot] went to play (-1: none did), and [message],
 * the short line that says why no cat would (empty when one plays).
 */
data class PlayAnswer(val slot: Int, val message: String) {
    /** True when a cat went to play. */
    val played: Boolean
        get() = slot >= 0
}

/**
 * One cat's current activity: its catalogue [activity] (plan 1.8: sleep, loaf, window...), the
 * [place] it happens at (a surface or a spot), the drive [level] that chose it (0..1) and the [why]
 * in a few words, which the explain ring and the day viewer print.
 */
data class Doing(val activity: String, val place: String, val level: Double, val why: String)

/** Something the owner does during a simulated day, at simulated [atMs]. */
data class OwnerAct(val atMs: Long, val kind: String)
