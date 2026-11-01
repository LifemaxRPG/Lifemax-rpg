package com.lifemax.rpg.feature.lifedirector.domain

/** Which end of its rule a handle moves. */
enum class HandleEnd {
    START,
    END,
}

/**
 * A handle on the dial's outer ring (the page's dialHandles): one end of a sleep, work, class or
 * appointment rule. [key] names it to the page and changes whenever the rule moves. [a] is where it
 * sits and [minute] its local minute. [label] is the centre's kick while it is held, [aria] what
 * TalkBack calls it. [lengths] and [ends] have one entry per stop: the length letting go there
 * gives the rule (what the confirmation asks about), or "" when that is too short (then no arc is
 * drawn); and the angle where the lit span from the stop ends, towards the rule's other end and
 * kept on the dial.
 */
data class DialHandle(
    val key: String,
    val rule: String,
    val end: HandleEnd,
    val kind: BlockKind,
    val a: Float,
    val minute: Int,
    val label: String,
    val aria: String,
    val lengths: List<String>,
    val ends: List<Float>,
)

/**
 * A place a handle can land, in dial order: minute [m] of the day, drawn at angle [a]; the finger
 * picks it from angle [from] up to the next stop's. [time] is what the centre shows.
 */
data class DialStop(val m: Int, val a: Float, val from: Float, val time: String)

/** The centre's words while a handle is held: a span too short, and how to apply. */
data class DragWords(val tooShort: String, val release: String)

/**
 * Where a handle was let go: on a stop, or a quarter hour later (1) or earlier (-1) by TalkBack.
 */
sealed interface DialPlace {
    data class Stop(val minute: Int) : DialPlace

    data class Step(val step: Int) : DialPlace
}

/**
 * The page's confirmation of a dial change (dialConfirm). With a [problem] it offers only [keep]
 * ("OK"); otherwise [body], [apply] ("Change it"), [keep] ("Keep 11:00 PM") and [note].
 */
data class DialConfirm(
    val title: String,
    val from: String,
    val to: String,
    val problem: String,
    val body: String,
    val note: String,
    val apply: String,
    val keep: String,
)

/**
 * The page's answer to a command, in NativeTabKit's envelope. [changed], [moved] and [confirm] are
 * the dial commands' own: whether the rule changes (or did), whether sessions moved, and what the
 * confirmation says.
 */
data class DirectorReply(
    val requestId: String,
    val command: String,
    val ok: Boolean,
    val message: String = "",
    val changed: Boolean = false,
    val moved: Boolean = false,
    val confirm: DialConfirm? = null,
    /** what a change answered with besides ok (exportBackup: the backup to copy) */
    val text: String = "",
)
