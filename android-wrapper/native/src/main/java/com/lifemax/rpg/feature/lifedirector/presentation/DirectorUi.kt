package com.lifemax.rpg.feature.lifedirector.presentation

import com.lifemax.rpg.feature.lifedirector.domain.DialConfirm
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.DialStop
import com.lifemax.rpg.feature.lifedirector.domain.DragWords
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DragGeometry

/* The screen's state that is not the page's projection: what the view model holds between
projections (a pending request, a held handle, an open sheet, a draft). */

/** A short message over the screen; [serial] tells a repeat of the same words apart. */
data class DirectorToast(val serial: Int, val text: String)

/**
 * A handle a finger holds ([held]), or has let go on stop [stop] while the page answers. It keeps
 * the handle, stops and words it was taken with, so an update cannot move it.
 */
data class DialHold(
    val handle: DialHandle,
    val stops: List<DialStop>,
    val words: DragWords,
    val stop: Int,
    val held: Boolean,
) {
    /** What the centre says with the handle here. */
    val preview
        get() = DragGeometry.preview(handle, stops, stop, words)

    /** The arc it spans while held, or null when that is too short or it is let go. */
    val span
        get() = if (held) DragGeometry.span(handle, stops, stop) else null
}

/**
 * A change the page is asked about: the handle's [key] and where it went. [confirm] is the page's
 * answer, shown until Keep or Change it; [applying] once Change it has gone to the page.
 */
data class DialAsk(
    val key: String,
    val place: DialPlace,
    val confirm: DialConfirm? = null,
    val applying: Boolean = false,
)

/** A change that asks first: the page's question, its button, and the change it then sends. */
data class ChangeConfirm(
    val title: String,
    val text: String,
    val button: String,
    val name: String,
    val args: Map<String, Any>,
)

/** A guide being shown: which, and its step. */
data class GuideRun(val key: String, val step: Int)

data class DirectorUi(
    val pendingDay: Int? = null,
    /** the section asked for, shown as chosen until the page answers */
    val pendingSection: String? = null,
    val guide: GuideRun? = null,
    /** the change sent to the page and not answered yet */
    val pendingChange: String? = null,
    val confirm: ChangeConfirm? = null,
    val restoring: Boolean = false,
    val restoreDraft: String = "",
    /** the backup the page answered with, for the screen to put on the clipboard */
    val copyText: String? = null,
    val earlierOpen: Boolean = false,
    val toast: DirectorToast? = null,
    val hold: DialHold? = null,
    val ask: DialAsk? = null,
    /** Today's sheet the screen asked for (phase 4A), shown once the page sends its model */
    val sheet: OpenSheet? = null,
    /** the minutes typed in a session sheet's field */
    val minutesDraft: MinutesDraft? = null,
    /** Today's write sent to the page and not answered yet ("done", "timer.start", ...) */
    val pendingToday: String? = null,
    /** My time's form sheets (phase 4B): drafts, the page's words, the write in flight */
    val forms: FormUi = FormUi(),
)
