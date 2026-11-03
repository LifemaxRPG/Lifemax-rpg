package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.domain.PetNotice
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.domain.PetReply
import com.lifemax.rpg.feature.pet.domain.PetRepository
import java.util.UUID

/** What a cat in the room does because of something the owner or the page did. */
sealed interface PetReaction {
    /** The page served cat [cat] a meal: it walks to its bowl, eats and washes. */
    data class Fed(val cat: Int) : PetReaction

    /** The owner tried to feed a cat that is full: it goes to its bowl and washes. */
    data class Bowl(val cat: Int) : PetReaction

    /** The owner slow blinked: cat [cat] blinks back. */
    data class Blink(val cat: Int) : PetReaction
}

/**
 * The Cat tab's screen state: the page's projection, the command in flight, the message showing,
 * and the reactions the room is to play. Every number and word is the page's; what is the screen's
 * own is only what it is waiting for.
 * - Feed carries an operation id, kept until the page's answer is final (it landed, or it was
 *   refused), so a tap repeated after an answer that never arrived (the screen closed while the
 *   page was answering) records at most one meal.
 * - A command waits for its reply; opening the screen again stops the waiting (a hidden screen gets
 *   no reply), keeping the operation id.
 * - The page's message ([notice]) shows once per id, only while the screen shows; the screen's own
 *   (a reply's words) show the same way.
 * - Each meal the page served ([PetFedCue]) is played once. The first projection after the screen
 *   opens plays only a meal that has just happened (a deep link that opened the Cat tab), not an
 *   old one.
 */
class PetViewModel(
    private val repository: PetRepository,
    private val commands: PetCommands = NoPetCommands,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    var pet by mutableStateOf<PetProjection?>(null)
        private set

    var notice by mutableStateOf<PetNotice?>(null)
        private set

    /** The command the page is answering, or null. */
    var waiting by mutableStateOf<String?>(null)
        private set

    /** Raised each time reactions are queued, for the room to drain them. */
    var reactionVersion by mutableIntStateOf(0)
        private set

    private val reactions = ArrayDeque<PetReaction>()
    private var showing = false
    private var noticed: Pair<String, Long>? = null
    private var said = 0L
    private var feedOp: String? = null
    private var requestId = ""
    private var cueSession = ""
    private var playedCue = 0L
    private var cuesBaselined = false

    /** The screen opened (again): nothing it waited for will be answered now. */
    fun opened() {
        showing = true
        notice = null
        waiting = null
        cuesBaselined = false
        projected()
    }

    /** The screen went behind the page: it draws no message and plays no reaction. */
    fun hidden() {
        showing = false
        notice = null
        waiting = null
        reactions.clear()
    }

    /** The repository has taken a projection. */
    fun projected() {
        val p = repository.state.value ?: return
        pet = p
        queueMeals(p)
        p.notice?.let {
            val seen = p.session to it.id
            if (seen != noticed && showing) {
                noticed = seen
                notice = it
            }
        }
    }

    /** The reactions queued since the last call, oldest first. */
    fun drainReactions(): List<PetReaction> {
        if (reactions.isEmpty()) return emptyList()
        return reactions.toList().also { reactions.clear() }
    }

    fun feed() {
        if (waiting != null) return
        val op = feedOp ?: newId().also { feedOp = it }
        send(PET_FEED) { commands.feed(op) }
    }

    fun blink() {
        if (waiting != null) return
        send(PET_BLINK) { commands.blink() }
    }

    fun vacation(on: Boolean) {
        if (waiting != null) return
        send(PET_VACATION) { commands.vacation(on) }
    }

    fun rename(name: String) {
        if (waiting != null) return
        send(PET_RENAME) { commands.rename(name) }
    }

    /** The Together card's switcher: show cat [index]'s card. */
    fun focus(index: Int) {
        if (waiting != null) return
        send(PET_FOCUS) { commands.focus(index) }
    }

    /** Unlock a toy early with Sparks; the page's reply says whether it could. */
    fun buyToy(id: String) {
        if (waiting != null) return
        send(PET_BUY_TOY) { commands.buyToy(id) }
    }

    /** The genome table opened or closed: the page sends its rows only while it is open. */
    fun expandGenome(open: Boolean) {
        if (waiting != null) return
        send(PET_EXPAND) { commands.expandGenome(open) }
    }

    fun onReply(reply: PetReply) {
        if (reply.requestId != requestId) return
        waiting = null
        // a meal that landed or was refused is final; one the page could not store may be sent
        // again
        if (reply.command == PET_FEED && (reply.ok || reply.why.isNotEmpty())) feedOp = null
        if (reply.message.isNotEmpty()) say(reply.message)
        val cat = pet?.focus ?: 0
        when (reply.reaction) {
            "blink" -> queue(PetReaction.Blink(cat))
            "bowl" -> queue(PetReaction.Bowl(cat))
        }
    }

    /** The message has shown to the end. */
    fun noticeShown(shown: PetNotice) {
        if (notice == shown) notice = null
    }

    private fun send(command: String, request: () -> String) {
        waiting = command
        requestId = request()
        if (requestId.isEmpty()) waiting = null
    }

    private fun queueMeals(p: PetProjection) {
        if (p.session != cueSession) {
            cueSession = p.session
            playedCue = 0
            cuesBaselined = false
        }
        for (cue in p.fedCues) {
            if (cue.id <= playedCue) continue
            if (showing && (cuesBaselined || cue.ago <= FRESH_MEAL_MS))
                queue(PetReaction.Fed(cue.cat))
            playedCue = cue.id
        }
        cuesBaselined = true
    }

    private fun queue(reaction: PetReaction) {
        if (!showing) return
        reactions.addLast(reaction)
        reactionVersion++
    }

    private fun say(text: String) {
        notice = PetNotice(-(++said), text)
    }

    private companion object {
        const val PET_FEED = "feed"
        const val PET_BLINK = "blink"
        const val PET_VACATION = "vacation"
        const val PET_RENAME = "rename"
        const val PET_FOCUS = "focus"
        const val PET_BUY_TOY = "buyToy"
        const val PET_EXPAND = "expand"

        /** A meal this recent, found in the first projection, is the reason the owner is here. */
        const val FRESH_MEAL_MS = 4_000L
    }
}
