package com.lifemax.rpg.feature.journal.domain

import com.lifemax.rpg.core.designsystem.feedback.PagePoint
import com.lifemax.rpg.core.designsystem.feedback.XpAward

/*
 * Phase 2, library.journal.write.v1 (ADR 0013): the screen types the owner's words itself and sends
 * them; the page stores them through its own owners and pays the reward as it always did, once. A
 * frame says so with `edit: "native"` and the `write` words below.
 */

/**
 * The one-sentence catch-up's words, the page's own prompt() (jcCatchUp) asked in the screen's
 * field: its question, the shortest sentence it takes, the longest, and the system prompt's two
 * buttons.
 */
data class CatchUpWords(
    val prompt: String,
    val min: Int,
    val max: Int,
    val ok: String,
    val cancel: String,
)

/** What a screen that writes needs from the page: the catch-up's words and where the XP flies. */
data class JournalWrite(val catchUp: CatchUpWords, val celebrationTarget: PagePoint?)

/**
 * The words in a notebook field that are not yet certainly the page's. [key] is a day
 * ("2026-10-28") or a review ("W:2026-W43"); [text] is what the field holds; [base] is what the
 * page is believed to hold, which the next write is checked against; [typed] says the owner has
 * typed (the counter then counts characters); [rev] rises when the field's text is replaced from
 * outside (the page's words taken in), so the field knows to show them.
 */
data class JournalDraft(
    val key: String,
    val text: String,
    val base: String,
    val typed: Boolean = false,
    val rev: Int = 0,
) {
    /** Whether the page may not hold these words yet. */
    val dirty: Boolean
        get() = text != base
}

/** The catch-up's field: shown under the welcome card, and waiting for the page's answer. */
data class CatchUpUi(val open: Boolean = false, val sending: Boolean = false)

/** Whether [key] names a review (W:… or M:…) rather than a day. */
fun isReviewKey(key: String): Boolean = key.length > 1 && key[1] == ':'

/** An XP celebration to draw, named by [id] so every award is a new burst. */
data class JournalCelebration(val id: Long, val awards: List<XpAward>)

/** The words of a day's reflection: autosave (`final` false) or the write that leaves the field. */
data class Reflect(val d: String, val text: String, val base: String, val final: Boolean) :
    JournalCommand {
    override val name = "reflect"
}

/** The words of a review's answer, the same way. */
data class Answer(val key: String, val text: String, val base: String, val final: Boolean) :
    JournalCommand {
    override val name = "answer"
}

/** A welcome chip that pays: "pet" or "fresh". */
data class ClaimChip(val kind: String) : JournalCommand {
    override val name = "claim"
}

/** The one-sentence catch-up, with its sentence. */
data class CatchUpSentence(val text: String) : JournalCommand {
    override val name = "catchUp"
}
