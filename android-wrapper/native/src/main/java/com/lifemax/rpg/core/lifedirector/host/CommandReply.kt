package com.lifemax.rpg.core.lifedirector.host

import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * A command's answer to the screen that asked (HC-3): [ok] only when the change (or the repeat) is
 * saved; [changed] whether anything changed (absent where the page leaves it out); the page's words
 * ([message]); for a Skip the take-back's words ([undo]); for a repair that said its refusal
 * already, [said]; a backup's [text]; for the dial whether sessions [moved].
 */
data class CommandReply(
    val ok: Boolean,
    val message: String,
    val changed: Boolean? = null,
    val undo: String? = null,
    val said: Boolean? = null,
    val text: String? = null,
    val moved: Boolean? = null,
) {
    /** The page's object. */
    fun json(): JsonObject {
        val m = LinkedHashMap<String, JsonValue>()
        m["ok"] = JsonBool.of(ok)
        if (changed != null) m["changed"] = JsonBool.of(changed)
        if (moved != null) m["moved"] = JsonBool.of(moved)
        if (said != null) m["said"] = JsonBool.of(said)
        m["message"] = JsonString(message)
        if (undo != null) m["undo"] = JsonString(undo)
        if (text != null) m["text"] = JsonString(text)
        return JsonObject(m)
    }

    companion object {
        /** `refuse(message)` (`logic.js:2328`): nothing changed, and why. */
        fun refuse(message: String) = CommandReply(false, message, changed = false)

        /** A refusal without `changed` (Today's and the goal commands' form). */
        fun no(message: String) = CommandReply(false, message)

        /** `That is no longer on the plan.` (`logic.js:3410`). */
        val GONE = no("That is no longer on the plan.")

        /** `Life Director cannot do that.` */
        val CANNOT = no("Life Director cannot do that.")
    }
}

/** Replies that depend on the last save (`logic.js:2325, 3409, 3411`). */
internal object Replies {
    /** `saved(message)`: ok when the last save went through, else its words. */
    fun saved(p: HostPage, message: String): CommandReply =
        if (p.lastSave.ok) CommandReply(true, message)
        else CommandReply(false, "Not saved: " + (p.lastSave.error ?: "undefined"))

    /** `recorded(message)`: a repeat; saves, and is ok when that save went through. */
    fun recorded(p: HostPage, message: String): CommandReply =
        CommandReply(p.persist(), message, changed = false)

    /** `applied()`: a change made; ok when it is saved. */
    fun applied(p: HostPage): CommandReply = saved(p, "").copy(changed = true)
}
