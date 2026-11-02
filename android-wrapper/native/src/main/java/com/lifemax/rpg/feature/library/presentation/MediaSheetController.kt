package com.lifemax.rpg.feature.library.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifemax.rpg.feature.library.domain.FilterOption
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaAskKind
import com.lifemax.rpg.feature.library.domain.MediaCommands
import com.lifemax.rpg.feature.library.domain.MediaField
import com.lifemax.rpg.feature.library.domain.MediaFields
import com.lifemax.rpg.feature.library.domain.MediaForm
import com.lifemax.rpg.feature.library.domain.MediaItem
import com.lifemax.rpg.feature.library.domain.MediaProjection
import com.lifemax.rpg.feature.library.domain.MediaReply
import com.lifemax.rpg.feature.library.domain.MediaStatus
import java.util.UUID

/**
 * The Media sheets (plan phase 3), while the page says the screen draws them ([MediaForm]): the
 * add/edit sheet with its draft, the finished tick shown at once and faded until the page answers,
 * and the two confirms. Writes go to the page as commands and the screen stays.
 *
 * The add carries an operation id per (command, target) that is kept until the page's terminal
 * answer: ok (also the unchanged ok a repeat earns), or a refusal of a field. A reply lost because
 * the screen hid is not terminal: the screen that opens again stops waiting, keeps the sheet and
 * its draft, and Save sends the same payload with the same id, which the page recognises (the id is
 * stored on the item in the same save) instead of adding a second item. A save that failed keeps
 * the id too: the page took the add back, so the same id lands it once on the next try. Status,
 * remove and remove audio are sets and ids, safe to repeat.
 *
 * Nothing is "saved" here: a sheet closes only on the page's ok, which means the save stored it,
 * and a refusal is shown in the page's own words, under its field when the page names one.
 */
class MediaSheetController(
    private val commands: MediaCommands,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    /** The add/edit sheet on show, or null. */
    var sheet by mutableStateOf<MediaSheetUi?>(null)
        private set

    /** The confirm on show, or null. */
    var ask by mutableStateOf<MediaAskUi?>(null)
        private set

    /** Ticks waiting for the page's answer: the state asked for, by item id. */
    var ticks by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    private var form: MediaForm? = null
    private var items: List<MediaItem> = emptyList()
    private var session: String? = null
    private val ops = HashMap<Pair<String, String>, String>()
    private val requests = HashMap<String, Pending>()

    private sealed interface Pending {
        data class Upsert(val key: Pair<String, String>) : Pending

        data class Tick(val id: String) : Pending

        data class Removal(val id: String, val kind: MediaAskKind) : Pending
    }

    /** Whether the screen draws the sheets: the page said so in its latest projection. */
    val native: Boolean
        get() = form != null

    /** The operation id an upsert of [target] ("new" for an add) carries now, if one is kept. */
    fun operation(target: String): String? = ops[UPSERT to target]

    /** A new projection: the form and the items the sheets work on. */
    fun projected(p: MediaProjection) {
        if (p.session != session) {
            // another page: its items, its ids; nothing of the old one is waited for or kept
            session = p.session
            ops.clear()
            requests.clear()
            sheet = null
            ask = null
            ticks = emptyMap()
        }
        form = p.form
        items = p.items
        if (p.form == null) {
            // the page draws its own form again: nothing of the screen's stays over the pane
            sheet = null
            ask = null
            ticks = emptyMap()
            return
        }
        val ids = items.map { it.id }.toSet()
        // an item removed meanwhile (on another path) takes its sheet, its confirm and its tick
        sheet?.id?.let { if (it !in ids) sheet = null }
        ask?.id?.let { if (it !in ids) ask = null }
        if (ticks.keys.any { it !in ids }) ticks = ticks.filterKeys { it in ids }
    }

    /**
     * The screen opened again: it stops waiting for replies it may never get (a hidden screen takes
     * none), keeps the sheet's draft and the operation ids for the retry, and drops what it can
     * simply ask again (a confirm) or shows from the projection (a tick).
     */
    fun opened() {
        requests.clear()
        sheet = sheet?.copy(pending = false)
        ask = null
        ticks = emptyMap()
    }

    /**
     * A tap the sheets answer on the screen: true when handled here, false when it goes to the page
     * (the page draws its own form, or the tap is play or attach).
     */
    fun handle(action: MediaAction): Boolean {
        if (!native) return false
        when (action) {
            MediaAction.Add -> add()
            is MediaAction.Edit -> item(action.id)?.let(::edit)
            is MediaAction.SetFinished -> item(action.id)?.let { tick(it, action.finished) }
            is MediaAction.Remove -> item(action.id)?.let { ask(MediaAskKind.REMOVE, it) }
            is MediaAction.RemoveAudio ->
                item(action.id)?.let { ask(MediaAskKind.REMOVE_AUDIO, it) }
            else -> return false
        }
        return true
    }

    fun add() {
        val f = form ?: return
        sheet = MediaSheetUi(null, f.emptyDraft(), typeOptions(f, ""))
    }

    fun edit(item: MediaItem) {
        val f = form ?: return
        sheet = MediaSheetUi(item.id, f.draftOf(item), typeOptions(f, item.type))
    }

    fun change(field: MediaField, value: String) {
        val s = sheet ?: return
        if (s.pending) return
        sheet = s.copy(draft = s.draft.with(field, value), errors = s.errors - field, error = "")
    }

    /** Closes the sheet; its draft is gone, as the page's Cancel resets its form. */
    fun cancel() {
        if (sheet?.pending == true) return
        sheet = null
    }

    /** Sends the sheet to the page, unless the page's own words for an empty title apply. */
    fun save() {
        val f = form ?: return
        val s = sheet ?: return
        if (s.pending) return
        val required = f.title.required
        if (required != null && s.draft.title.isBlank()) {
            sheet = s.copy(errors = mapOf(MediaField.TITLE to required), error = "")
            return
        }
        val key = UPSERT to (s.id ?: NEW)
        val op = ops.getOrPut(key, newId)
        val request = commands.upsert(op, s.id, s.draft)
        requests[request] = Pending.Upsert(key)
        sheet = s.copy(pending = true, errors = emptyMap(), error = "")
    }

    /** The tick: shown at once, faded, until the page answers; one at a time per item. */
    fun tick(item: MediaItem, finished: Boolean) {
        if (item.id in ticks) return
        ticks = ticks + (item.id to finished)
        val status = if (finished) MediaStatus.FINISHED else MediaStatus.PROGRESS
        requests[commands.setStatus(item.id, status)] = Pending.Tick(item.id)
    }

    fun ask(kind: MediaAskKind, item: MediaItem) {
        val f = form ?: return
        val words = f.ask(kind)
        ask = MediaAskUi(kind, item.id, words.about(item.title), words.confirm, f.askCancel)
    }

    fun dismissAsk() {
        ask = null
    }

    /** The confirm's button: the page removes the item or its audio, and says so in its toast. */
    fun confirmAsk() {
        val a = ask ?: return
        ask = null
        if (Pending.Removal(a.id, a.kind) in requests.values) return
        val request =
            when (a.kind) {
                MediaAskKind.REMOVE -> commands.remove(a.id)
                MediaAskKind.REMOVE_AUDIO -> commands.removeAudio(a.id)
            }
        requests[request] = Pending.Removal(a.id, a.kind)
    }

    /**
     * The page's answer to one of these commands. The page's toast, which comes in the same
     * projection, says what happened; a refusal of the sheet is drawn on the sheet instead.
     */
    fun onReply(reply: MediaReply) {
        when (val p = requests.remove(reply.requestId) ?: return) {
            is Pending.Upsert -> upserted(p, reply)
            is Pending.Tick -> ticks = ticks - p.id
            is Pending.Removal -> Unit
        }
    }

    private fun upserted(p: Pending.Upsert, reply: MediaReply) {
        val field = MediaField.of(reply.field)
        if (reply.ok || field != null) ops.remove(p.key)
        val s = sheet?.takeIf { it.pending && UPSERT to (it.id ?: NEW) == p.key } ?: return
        sheet =
            when {
                reply.ok -> null
                field != null -> s.copy(pending = false, errors = mapOf(field to reply.message))
                else -> s.copy(pending = false, error = reply.message.ifBlank { NOTHING_CHANGED })
            }
    }

    private fun item(id: String) = items.firstOrNull { it.id == id }

    private companion object {
        const val UPSERT = "upsert"
        const val NEW = "new"
        const val NOTHING_CHANGED = "Nothing was changed."
    }
}

/**
 * The page's types, and an old item's stored type the page no longer offers ([kept]) in front, so
 * an edit keeps it rather than blanking it.
 */
internal fun typeOptions(form: MediaForm, kept: String): List<FilterOption> =
    if (kept.isEmpty() || form.type.options.any { it.id == kept }) form.type.options
    else listOf(FilterOption(kept, kept)) + form.type.options

/**
 * The add/edit sheet: the item it edits ([id], null for an add), what is typed, the page's words
 * for each refused field and for a refusal of the whole sheet ([error]), and whether the page is
 * answering.
 */
data class MediaSheetUi(
    val id: String?,
    val draft: MediaFields,
    val typeOptions: List<FilterOption>,
    val errors: Map<MediaField, String> = emptyMap(),
    val error: String = "",
    val pending: Boolean = false,
)

/** A confirm on show: its question about the item, its button and Cancel. */
data class MediaAskUi(
    val kind: MediaAskKind,
    val id: String,
    val text: String,
    val confirm: String,
    val cancel: String,
)
