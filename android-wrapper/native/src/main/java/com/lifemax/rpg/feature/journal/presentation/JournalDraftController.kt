package com.lifemax.rpg.feature.journal.presentation

import com.lifemax.rpg.feature.journal.domain.Answer
import com.lifemax.rpg.feature.journal.domain.JournalCommand
import com.lifemax.rpg.feature.journal.domain.JournalDraft
import com.lifemax.rpg.feature.journal.domain.JournalReply
import com.lifemax.rpg.feature.journal.domain.Reflect
import com.lifemax.rpg.feature.journal.domain.isReviewKey

/**
 * The words being typed in a notebook field and how they reach the page (phase 2, ADR 0013).
 * - **Autosave.** [AUTOSAVE_MS] after the typing stops the words are sent with `final` false: the
 *   page stores them and pays nothing, however long they are.
 * - **The final write.** Leaving the field, the tab or the app ([leave]) sends them with `final`
 *   true, which may also pay the reward; the page pays it once a day, so a repeat is harmless. It
 *   is sent when the words are not yet the page's or a reward is still owed ([owes]), and once per
 *   text. Never while the owner is still typing.
 * - **One at a time.** A write in flight holds the next; what was typed meanwhile follows when its
 *   answer arrives (a pending final first).
 * - **A conflict.** The page holds neither the words' base nor the words (a catch-up added a
 *   sentence meanwhile): nothing was written. The owner's words stay, the base becomes what the
 *   page holds, and nothing is sent again by itself; the next autosave or leave replaces it. When
 *   the page holds the words of an earlier write whose answer was lost, it is no conflict: the base
 *   is moved and the write sent again.
 * - **A failed save.** The words stay as typed and the next trigger sends them again.
 * - **A lost answer** ([lostReply], when the screen opens again): the write in flight may have
 *   landed, so its words are remembered as [unconfirmed]; the page's own text equal to the field's
 *   settles it.
 *
 * Main thread only. [send] sends a command and answers its request id; [schedule] runs a block
 * after a delay and answers how to cancel it; [owes] says whether a final write could still pay for
 * these words; [changed] is told after every change of the draft.
 */
internal class JournalDraftController(
    private val send: (JournalCommand) -> String,
    private val schedule: (Long, () -> Unit) -> () -> Unit,
    private val owes: (key: String, text: String) -> Boolean,
    private val changed: () -> Unit,
) {
    /** The draft, or null when nothing is being kept. */
    var draft: JournalDraft? = null
        private set

    private class Sent(
        val id: String,
        val key: String,
        val text: String,
        val base: String,
        val final: Boolean,
    )

    private var inFlight: Sent? = null
    private var cancelTimer: (() -> Unit)? = null
    private var unconfirmed: String? = null
    private var finalFor: String? = null
    private var again = false
    private var pendingFinal = false

    /**
     * The owner typed [text] in the field of [key], whose words the page last said were [pageText].
     * The first typing starts the draft.
     */
    fun edit(key: String, text: String, pageText: String) {
        val d = draft?.takeIf { it.key == key }
        if (d != null && d.text == text) {
            draft = d.copy(typed = true)
            return
        }
        if (d == null) {
            drop()
            finalFor = null
        }
        draft = (d ?: JournalDraft(key, pageText, pageText)).copy(text = text, typed = true)
        if (finalFor != text) finalFor = null
        cancelTimer?.invoke()
        cancelTimer = schedule(AUTOSAVE_MS) { autosave() }
        changed()
    }

    /** The owner left the field, the tab or the app: the words go as a final write if need be. */
    fun leave() {
        cancelTimer?.invoke()
        cancelTimer = null
        val d = draft ?: return
        if (inFlight != null) {
            pendingFinal = true
            return
        }
        if (d.dirty || unconfirmed != null || (finalFor != d.text && owes(d.key, d.text)))
            transmit(final = true)
        else settle()
    }

    /**
     * The page's frame says [key] holds [pageText] now. Words the owner has not touched take it in
     * (a catch-up added a sentence); words that equal it are the page's. A draft for an entry the
     * page no longer lists is dropped when it is the page's.
     */
    fun page(key: String, pageText: String?) {
        val d = draft ?: return
        if (d.key != key) return
        if (pageText == null) return
        if (inFlight == null && d.text == pageText) {
            unconfirmed = null
            if (d.base != pageText) draft = d.copy(base = pageText)
            settle()
            return
        }
        val clean = d.text == d.base && unconfirmed == null && inFlight == null
        if (clean && pageText != d.base) {
            draft = d.copy(text = pageText, base = pageText, rev = d.rev + 1)
            changed()
        }
    }

    /** The page's answer to a command this controller sent; false when it was not one of ours. */
    fun onReply(reply: JournalReply): Boolean {
        val sent = inFlight?.takeIf { it.id == reply.requestId } ?: return false
        inFlight = null
        val d = draft
        when {
            d == null -> Unit
            d.key != sent.key -> followUp() // the write was for an entry left meanwhile
            reply.ok -> {
                unconfirmed = null
                if (sent.final) finalFor = sent.text
                draft = d.copy(base = sent.text)
                followUp()
            }
            reply.conflict -> {
                val stored = reply.stored
                if (stored != null && stored == unconfirmed) {
                    // the page holds the words of a write whose answer was lost: no conflict
                    unconfirmed = null
                    draft = d.copy(base = stored)
                    transmit(sent.final)
                } else {
                    // someone else's words are there: ours stay, and replace them when sent again
                    if (stored != null) draft = d.copy(base = stored)
                    pendingFinal = false
                    again = false
                }
            }
            else -> {
                // not stored (or not understood): the words stay, the next trigger sends them
                // again; a write the page did change in memory is the page's base now
                if (reply.changed) draft = d.copy(base = sent.text)
                unconfirmed = sent.text.takeIf { reply.changed }
                again = false
            }
        }
        changed()
        return true
    }

    /** The screen opened again: an answer to a write in flight will not come (A24). */
    fun lostReply() {
        val sent = inFlight ?: return
        inFlight = null
        unconfirmed = sent.text
        again = false
        pendingFinal = false
    }

    /** The screen stopped being drawn: the words the page certainly holds are let go (A35). */
    fun rested() {
        cancelTimer?.invoke()
        cancelTimer = null
        val d = draft ?: return
        if (d.text == d.base && unconfirmed == null && inFlight == null) drop()
    }

    private fun autosave() {
        cancelTimer = null
        val d = draft ?: return
        if (inFlight != null) {
            again = true
            return
        }
        if (d.dirty || unconfirmed != null) transmit(final = false)
    }

    private fun followUp() {
        val d = draft ?: return
        if (pendingFinal) {
            pendingFinal = false
            again = false
            leave()
        } else if (again) {
            again = false
            if (d.dirty) transmit(final = false)
        } else if (!d.dirty && cancelTimer == null) settle()
    }

    private fun transmit(final: Boolean) {
        val d = draft ?: return
        val command: JournalCommand =
            if (isReviewKey(d.key)) Answer(d.key, d.text, d.base, final)
            else Reflect(d.key, d.text, d.base, final)
        inFlight = Sent(send(command), d.key, d.text, d.base, final)
    }

    /**
     * The words are the page's and nothing is owed (no final write that could still pay): the draft
     * is let go, unless more is typed.
     */
    private fun settle() {
        val d = draft ?: return
        val owed = finalFor != d.text && owes(d.key, d.text)
        if (
            d.text == d.base &&
                unconfirmed == null &&
                inFlight == null &&
                cancelTimer == null &&
                !owed
        ) {
            drop()
            changed()
        }
    }

    private fun drop() {
        draft = null
        unconfirmed = null
        again = false
        pendingFinal = false
    }

    companion object {
        /** How long after the typing stops the words are saved without leaving the field. */
        const val AUTOSAVE_MS = 1_500L
    }
}
