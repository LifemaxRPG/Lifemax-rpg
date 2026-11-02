package com.lifemax.rpg.feature.character.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.feature.character.domain.CharacterCommands
import com.lifemax.rpg.feature.character.domain.CharacterProjection
import com.lifemax.rpg.feature.character.domain.CharacterReply
import com.lifemax.rpg.feature.character.domain.LuckRoll
import java.util.UUID

/** A sheet the screen draws over Character, while the page speaks the actions. */
sealed interface CharacterSheet {
    /**
     * Daily Luck: [roll] once the page has rolled (null while it is asked to), [error] in the
     * page's words when it refused, [notice] its note on a roll that was already made.
     */
    data class Luck(
        val roll: LuckRoll?,
        val pending: Boolean,
        val error: String = "",
        val notice: String = "",
    ) : CharacterSheet

    /**
     * The benchmarks: [editing] is the index whose result is being changed (null while adding a new
     * one) with the draft [name] and [value]; [pending] while the page answers.
     */
    data class Benchmarks(
        val editing: Int? = null,
        val name: String = "",
        val value: String = "",
        val pending: Boolean = false,
        val error: String = "",
        val notice: String = "",
    ) : CharacterSheet
}

/** An XP celebration to draw: [id] counts per screen, so every award is a new burst. */
data class CharacterCelebration(val id: Long, val awards: List<XpAward>)

/**
 * What a tap on Character asks of the page once it speaks the actions: Luck (the page rolls and
 * saves, the sheet shows its answer), the benchmark sheet (add, change a result, remove) and "Log
 * <attribute> activity" (the page prefills its Log form and goes there). Without the actions
 * [handle] answers false and the tap is handed over to the web page as before.
 *
 * Writes carry an operation id per (command, target), kept until the page's terminal answer: ok
 * (also the unchanged ok of a repeat) or a refusal that does not leave the write possible. A reply
 * lost because the screen hid is not terminal: the screen that opens again stops waiting, keeps the
 * sheet and its draft, and the same tap sends the same id, which the page recognises. A refusal or
 * a failed save keeps the id too: the page took the write back, so the same id lands it once.
 *
 * Nothing is "saved" here: a sheet's draft goes away only on the page's ok, which means its save
 * stored it, and a refusal is shown in the page's own words.
 */
class CharacterActions(
    private val commands: CharacterCommands,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    var sheet by mutableStateOf<CharacterSheet?>(null)
        private set

    var celebration by mutableStateOf<CharacterCelebration?>(null)
        private set

    private var p: CharacterProjection? = null
    private var session: String? = null
    private var serial = 0L
    private var logging = false
    private val ops = HashMap<Pair<String, String>, String>()
    private val requests = HashMap<String, Pending>()

    private sealed interface Pending {
        data class Luck(val key: Pair<String, String>) : Pending

        data class Write(val key: Pair<String, String>) : Pending

        data object Log : Pending
    }

    /** The operation id a write to [target] would carry now (tests and diagnostics). */
    fun operation(command: String, target: String): String? = ops[command to target]

    /**
     * A new projection: today's roll settles a Luck sheet that waits, and a new page starts clean.
     */
    fun projected(next: CharacterProjection) {
        if (next.session != session) {
            session = next.session
            ops.clear()
            requests.clear()
            sheet = null
            celebration = null
            logging = false
        }
        p = next
        if (!next.actions) {
            sheet = null
            return
        }
        val roll = next.luckRoll
        if (roll != null) {
            ops.keys.removeAll { it.first == LUCK }
            (sheet as? CharacterSheet.Luck)
                ?.takeIf { it.roll == null }
                ?.let { sheet = CharacterSheet.Luck(roll, false, notice = it.notice) }
        }
    }

    /**
     * The screen opened again: it stops waiting for replies it may never get (a hidden screen takes
     * none) and keeps the sheet, its draft and the operation ids.
     */
    fun opened() {
        requests.clear()
        logging = false
        when (val s = sheet) {
            is CharacterSheet.Luck -> sheet = if (s.roll == null) s.copy(pending = false) else s
            is CharacterSheet.Benchmarks -> sheet = s.copy(pending = false)
            null -> Unit
        }
    }

    /**
     * A tap the actions answer on the screen: true when handled here, false when it goes to the
     * page.
     */
    fun handle(action: String, id: String): Boolean {
        val shown = p?.takeIf { it.actions } ?: return false
        when (action) {
            "luck" -> luck(shown)
            "benchmarks" -> sheet = CharacterSheet.Benchmarks()
            "log" -> log(id)
            else -> return false
        }
        return true
    }

    fun close() {
        sheet = null
    }

    // ---- Luck ----

    private fun luck(shown: CharacterProjection) {
        val roll = shown.luckRoll
        if (roll != null) {
            sheet = CharacterSheet.Luck(roll, false)
            return
        }
        sheet = CharacterSheet.Luck(null, true)
        rollNow(shown)
    }

    /** Ask again after a refusal: the same operation id. */
    fun retryLuck() {
        val shown = p ?: return
        val s = sheet as? CharacterSheet.Luck ?: return
        if (s.roll != null || s.pending) return
        sheet = CharacterSheet.Luck(null, true)
        rollNow(shown)
    }

    private fun rollNow(shown: CharacterProjection) {
        val key = LUCK to shown.today
        val op = ops.getOrPut(key, newId)
        requests[commands.luck(op, shown.today)] = Pending.Luck(key)
    }

    // ---- benchmarks ----

    fun edit(index: Int) {
        val b = p?.benchmarks?.getOrNull(index) ?: return
        val s = sheet as? CharacterSheet.Benchmarks ?: return
        if (s.pending) return
        sheet = CharacterSheet.Benchmarks(editing = index, name = b.name, value = b.value)
    }

    fun cancelEdit() {
        val s = sheet as? CharacterSheet.Benchmarks ?: return
        if (!s.pending) sheet = CharacterSheet.Benchmarks()
    }

    fun change(name: String? = null, value: String? = null) {
        val s = sheet as? CharacterSheet.Benchmarks ?: return
        if (s.pending) return
        sheet = s.copy(name = name ?: s.name, value = value ?: s.value, error = "", notice = "")
    }

    /** Save the draft: a new benchmark, or the changed result of the one being edited. */
    fun save() {
        val shown = p ?: return
        val s = sheet as? CharacterSheet.Benchmarks ?: return
        if (s.pending) return
        val index = s.editing
        if (index == null) {
            val key = ADD to s.name.trim()
            val op = ops.getOrPut(key, newId)
            sheet = s.copy(pending = true, error = "", notice = "")
            requests[commands.benchmarkAdd(op, s.name, s.value)] = Pending.Write(key)
        } else {
            val seen = shown.benchmarks.getOrNull(index) ?: return
            sheet = s.copy(pending = true, error = "", notice = "")
            requests[commands.benchmarkSet(index, seen.name, s.value)] =
                Pending.Write(SET to seen.name)
        }
    }

    fun remove(index: Int) {
        val seen = p?.benchmarks?.getOrNull(index) ?: return
        val s = sheet as? CharacterSheet.Benchmarks ?: return
        if (s.pending) return
        sheet = s.copy(pending = true, error = "", notice = "")
        requests[commands.benchmarkRemove(index, seen.name)] = Pending.Write(REMOVE to seen.name)
    }

    // ---- Log ----

    /** "Log <attribute> activity": the page prefills its form and goes to the Log tab; once. */
    private fun log(attribute: String) {
        if (logging) return
        logging = true
        requests[commands.log(attribute)] = Pending.Log
    }

    // ---- replies ----

    /** Whether [reply] answered one of these writes. */
    fun onReply(reply: CharacterReply): Boolean {
        val pending = requests.remove(reply.requestId) ?: return false
        val awards = reply.celebrations
        if (awards.isNotEmpty()) celebration = CharacterCelebration(++serial, awards)
        when (pending) {
            Pending.Log -> logging = false
            is Pending.Luck -> {
                val roll = reply.luck
                sheet =
                    when {
                        !reply.ok -> CharacterSheet.Luck(null, false, error = reply.message)
                        else -> {
                            ops.remove(pending.key)
                            // a roll the screen cannot read waits for the next projection, which
                            // has it
                            CharacterSheet.Luck(
                                roll,
                                roll == null,
                                notice = if (reply.changed) "" else reply.message,
                            )
                        }
                    }
            }
            is Pending.Write -> {
                val s = sheet as? CharacterSheet.Benchmarks ?: return true
                sheet =
                    if (reply.ok) {
                        ops.remove(pending.key)
                        CharacterSheet.Benchmarks(notice = if (reply.changed) "" else reply.message)
                    } else s.copy(pending = false, error = reply.message)
            }
        }
        return true
    }

    /** Celebration [id] has played to its end. */
    fun celebrated(id: Long) {
        if (celebration?.id == id) celebration = null
    }

    /** Behind the page nothing plays (A24): a celebration cut short is not replayed. */
    fun hidden() {
        celebration = null
    }

    private companion object {
        const val LUCK = "luck"
        const val ADD = "benchmarkAdd"
        const val SET = "benchmarkSet"
        const val REMOVE = "benchmarkRemove"
    }
}
