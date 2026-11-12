package com.lifemax.rpg.feature.pet.domain

import kotlinx.coroutines.flow.StateFlow

interface PetRepository {
    val state: StateFlow<PetProjection?>
}

/**
 * The page's answer to a command: [ok] only when its save stored the change (or nothing needed
 * saving), in its own words in [message]. A refused feed says [why] (`vacation` or `full`, with
 * [inHours] to the next meal), and [reaction] tells the room what the cat does about it: `blink`
 * (blink back) or `bowl` (go to the bowl and wash). The answer to a stroke may carry [levelUp]: the
 * trust level the cat just reached (0 when it did not level up).
 */
data class PetReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val why: String,
    val inHours: Double,
    val reaction: String,
    val levelUp: Int = 0,
)

/** The commands the page answers; each returns the request id its reply will carry. */
interface PetCommands {
    /** Feed the cat (or whoever is hungry of two); [op] makes a repeat record at most once. */
    fun feed(op: String): String

    fun blink(): String

    fun vacation(on: Boolean): String

    fun rename(name: String): String

    /** Show the card of cat [index] (0 or 1) of a house of two (`pet.panels.v1`). */
    fun focus(index: Int): String = ""

    /** Unlock toy [id] early with Sparks, by the Rewards shop's own rule (`pet.panels.v1`). */
    fun buyToy(id: String): String = ""

    /** Open ([open]) or close the full genome table, which the page sends only while open. */
    fun expandGenome(open: Boolean): String = ""

    /**
     * A touch ended (`touch` / `stroke`): the page applies its petting rules and counts a pet. [op]
     * makes a repeat count once.
     */
    fun stroke(op: String, stroke: PetStroke): String = ""

    /**
     * One of the cattery screens' commands (`pet.cattery.v1`). [op] is the operation id of a
     * command that changes things ([CatteryCommand.changes]); a repeat with the same id records
     * once.
     */
    fun cattery(op: String, command: CatteryCommand): String = ""

    /** The cat purrs at [level] (0 to 1), or stops with 0 (`touch` / `purr`): the page's sound. */
    fun purr(cat: Int, level: Float): String = ""
}

/** For a view model that never sends (tests of the read-only parts): every call answers no id. */
object NoPetCommands : PetCommands {
    override fun feed(op: String) = ""

    override fun blink() = ""

    override fun vacation(on: Boolean) = ""

    override fun rename(name: String) = ""

    override fun focus(index: Int) = ""

    override fun buyToy(id: String) = ""

    override fun expandGenome(open: Boolean) = ""
}
