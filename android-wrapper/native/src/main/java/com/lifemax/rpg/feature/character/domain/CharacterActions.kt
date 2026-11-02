package com.lifemax.rpg.feature.character.domain

import com.lifemax.rpg.core.designsystem.feedback.XpAward

/**
 * Today's Daily Luck as the page rolled it: the dice, the number, and the tier's own words and
 * colour (0xAARRGGBB).
 */
data class LuckRoll(
    val date: String,
    val dice: List<Int>,
    val score: String,
    val tier: String,
    val message: String,
    val color: Long,
)

/** What a benchmark's name and result may hold, as the page says (the screen hard-codes none). */
data class BenchmarkLimits(val name: Int, val value: Int)

/** The page's words for the Luck sheet. */
data class LuckWords(
    val heading: String,
    val rolling: String,
    val retry: String,
    val close: String,
)

/** The page's words for the benchmark sheet (the web form's own labels). */
data class BenchmarkWords(
    val heading: String,
    val nameLabel: String,
    val namePlaceholder: String,
    val valueLabel: String,
    val valuePlaceholder: String,
    val add: String,
    val edit: String,
    val save: String,
    val cancel: String,
    val remove: String,
    val close: String,
)

data class CharacterWords(val luck: LuckWords, val benchmarks: BenchmarkWords) {
    companion object {
        /** Before the page speaks the actions, none of these is drawn. */
        val NONE =
            CharacterWords(
                LuckWords("", "", "", ""),
                BenchmarkWords("", "", "", "", "", "", "", "", "", "", ""),
            )
    }
}

/** The page's answer to a command: `ok` only when its save succeeded, in its own words. */
data class CharacterReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val luck: LuckRoll?,
    val celebrations: List<XpAward>,
)

/** The commands the page answers; each returns the request id its reply will carry. */
interface CharacterCommands {
    fun luck(op: String, day: String): String

    fun benchmarkAdd(op: String, name: String, value: String): String

    fun benchmarkSet(index: Int, name: String, value: String): String

    fun benchmarkRemove(index: Int, name: String): String

    fun log(attribute: String): String
}

/** For a view model that never sends (tests of the read-only parts): every call answers no id. */
object NoCharacterCommands : CharacterCommands {
    override fun luck(op: String, day: String) = ""

    override fun benchmarkAdd(op: String, name: String, value: String) = ""

    override fun benchmarkSet(index: Int, name: String, value: String) = ""

    override fun benchmarkRemove(index: Int, name: String) = ""

    override fun log(attribute: String) = ""
}
