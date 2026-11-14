package com.lifemax.rpg.feature.habits.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.habits.domain.HabitFx
import com.lifemax.rpg.feature.habits.domain.HabitsCommands
import com.lifemax.rpg.feature.habits.domain.HabitsReply
import com.lifemax.rpg.feature.habits.domain.HabitsRepository
import com.lifemax.rpg.feature.habits.domain.PrimaryAction
import com.lifemax.rpg.feature.habits.domain.primaryAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The sheets the screen draws: everything that needs typing or a second thought. */
enum class SheetKind {
    /** Who the owner is becoming. */
    WHO,
    NEW_STACK,

    /** A foundation's cue and rooftop reward; an empty one can be removed. */
    EDIT_STACK,
    NEW_HABIT,

    /** A block's dials: size, pairing, order, foundation, name, undo, retire. */
    TUNE,
    NAMES,
    PAIR,

    /** Which foundation the block stands on. */
    RESTACK,
    RETIRE,
}

/**
 * The sheet on show. [target] is the stack or block it is about ("" for none), [fields] what has
 * been typed, in the sheet's order, [fade] a new habit that is one to break. [error] says why the
 * last try was refused or could not be saved.
 */
data class HabitsSheet(
    val kind: SheetKind,
    val target: String = "",
    val fields: List<String> = emptyList(),
    val fade: Boolean = false,
    val error: String = "",
)

/** A message to show once (a write that failed, a block that turned to stone). */
data class HabitsToast(val id: Long, val text: String)

/** Something to play once: a block landing, a floor coming down. */
data class HabitsPlay(val id: Long, val fx: HabitFx)

/**
 * What the screen holds itself: the stack in [focus], the block [selected], the [city] view and how
 * many [weeks] back its time-lapse stands. [jump] rises whenever the focus moves by anything but
 * the owner's finger, so the towers travel there. [combo] counts the blocks laid in a row on this
 * visit to the stack.
 */
data class HabitsUi(
    val focus: String? = null,
    val selected: String? = null,
    val city: Boolean = false,
    val weeks: Int = 0,
    val jump: Long = 0,
    val sheet: HabitsSheet? = null,
    val toast: HabitsToast? = null,
    val play: HabitsPlay? = null,
    val combo: Int = 0,
)

/**
 * The screen's own state and what its taps ask for. The habits live in the repository: a tap runs a
 * command there, and the screen shows what the repository then says. A sheet closes only when its
 * change is stored; a refusal or a write that failed (the change is taken back) is said in plain
 * words, on the sheet that asked (its draft kept) or as a toast.
 */
class HabitsViewModel(repository: HabitsRepository, private val commands: HabitsCommands) :
    ViewModel() {
    val projection = repository.state
    private val mutable = MutableStateFlow(HabitsUi())
    val ui = mutable.asStateFlow()
    private var started = false
    private var serial = 0L

    /**
     * The data changed (or arrived): start on the first stack with a block to lay; let go of what
     * is gone.
     */
    fun onProjection() {
        val p = projection.value ?: return
        val first = (p.stacks.firstOrNull { it.next.isNotEmpty() } ?: p.stacks.firstOrNull())?.id
        if (!started) {
            started = true
            mutable.value = HabitsUi(focus = first)
            return
        }
        mutable.update { ui ->
            val focus = ui.focus?.takeIf { p.stack(it) != null }
            ui.copy(
                focus = focus ?: first,
                jump = if (focus == null && ui.focus != null) ui.jump + 1 else ui.jump,
                selected = ui.selected?.takeIf { p.block(it) != null },
                sheet =
                    ui.sheet?.takeIf {
                        when (it.kind) {
                            SheetKind.WHO,
                            SheetKind.NEW_STACK -> true
                            SheetKind.EDIT_STACK,
                            SheetKind.NEW_HABIT -> p.stack(it.target) != null
                            else -> p.block(it.target) != null
                        }
                    },
                weeks = ui.weeks.coerceIn(0, p.rules.weeks),
                city = ui.city && p.stacks.isNotEmpty(),
            )
        }
    }

    /**
     * The screen went behind the page: nothing plays there (A24), and a play cut short is not
     * replayed.
     */
    fun hidden() = mutable.update { it.copy(play = null) }

    // ---- the scene ----

    /** Bring [stackId] in front; [byFinger] when the owner's swipe already put the towers there. */
    fun focus(stackId: String, byFinger: Boolean = false) {
        if (projection.value?.stack(stackId) == null) return
        mutable.update {
            if (it.focus == stackId && !it.city) it
            else
                it.copy(
                    focus = stackId,
                    selected = null,
                    city = false,
                    weeks = 0,
                    combo = if (it.focus == stackId) it.combo else 0,
                    jump = if (byFinger) it.jump else it.jump + 1,
                )
        }
    }

    /** A tap on a block (or a tree): its dials open; a tap on the one selected puts them away. */
    fun select(blockId: String) {
        val stack = projection.value?.stackOf(blockId) ?: return
        mutable.update {
            if (it.selected == blockId) it.copy(selected = null, sheet = null)
            else
                it.copy(
                    selected = blockId,
                    focus = stack.id,
                    sheet = HabitsSheet(SheetKind.TUNE, blockId),
                )
        }
    }

    /** The big button. */
    fun primary() {
        val p = projection.value ?: return
        when (val action = primaryAction(p.stacks, mutable.value.focus)) {
            PrimaryAction.FirstFoundation -> newStack()
            is PrimaryAction.FirstHabit -> newHabit(action.stack.id)
            is PrimaryAction.Lay -> lay(action.block.id)
            is PrimaryAction.Knock -> lay(action.block.id)
            is PrimaryAction.Claim -> said(commands.claim(action.stack.id))
            is PrimaryAction.GoTo -> focus(action.stack.id)
            PrimaryAction.Complete -> Unit
        }
    }

    private fun lay(id: String) {
        val reply = commands.lay(id)
        said(reply)
        if (reply.ok && reply.changed)
            mutable.update {
                it.copy(
                    play = reply.fx?.let { fx -> HabitsPlay(++serial, fx) } ?: it.play,
                    combo = it.combo + 1,
                )
            }
        else if (!reply.ok) mutable.update { it.copy(combo = 0) }
    }

    /**
     * The rooftop reward: claimed when it can be, else its foundation's sheet, where it is named.
     */
    fun reward(stackId: String) {
        val stack = projection.value?.stack(stackId) ?: return
        if (stack.claimable) said(commands.claim(stackId)) else editStack(stackId)
    }

    /** The rough-day switch. */
    fun rough() {
        val p = projection.value ?: return
        said(commands.rough(!p.rough))
    }

    /** The city of every tower, or back to the one in front. */
    fun city(on: Boolean) {
        if (projection.value?.stacks.isNullOrEmpty()) return
        mutable.update { it.copy(city = on, weeks = 0, selected = null, sheet = null) }
    }

    /** The time-lapse: the city [weeks] weeks ago (0 is today). */
    fun lapse(weeks: Int) {
        val max = projection.value?.rules?.weeks ?: return
        mutable.update { if (it.city) it.copy(weeks = weeks.coerceIn(0, max)) else it }
    }

    // ---- sheets ----

    fun who() = show(HabitsSheet(SheetKind.WHO, fields = listOf(projection.value?.who.orEmpty())))

    fun newStack() = show(HabitsSheet(SheetKind.NEW_STACK, fields = listOf("")))

    fun editStack(stackId: String) {
        val s = projection.value?.stack(stackId) ?: return
        show(HabitsSheet(SheetKind.EDIT_STACK, stackId, listOf(s.cue, s.treat)))
    }

    fun newHabit(stackId: String) {
        if (projection.value?.stack(stackId) != null)
            show(HabitsSheet(SheetKind.NEW_HABIT, stackId, listOf("", "")))
    }

    /** From a block's dials: its names at each size. */
    fun names() = fromDials { b ->
        HabitsSheet(SheetKind.NAMES, b, projection.value?.block(b)?.names.orEmpty())
    }

    fun pair() = fromDials { b ->
        HabitsSheet(SheetKind.PAIR, b, listOf(projection.value?.block(b)?.pair.orEmpty()))
    }

    fun restack() = fromDials { b -> HabitsSheet(SheetKind.RESTACK, b) }

    fun retire() = fromDials { b -> HabitsSheet(SheetKind.RETIRE, b) }

    private fun fromDials(next: (String) -> HabitsSheet) {
        val s = mutable.value.sheet ?: return
        if (s.kind == SheetKind.TUNE) show(next(s.target))
    }

    private fun show(sheet: HabitsSheet) = mutable.update { it.copy(sheet = sheet) }

    /** Typing in field [index] of the sheet. */
    fun type(index: Int, text: String) =
        mutable.update { ui ->
            val s = ui.sheet ?: return@update ui
            if (index !in s.fields.indices) ui
            else
                ui.copy(
                    sheet =
                        s.copy(
                            fields = s.fields.toMutableList().also { it[index] = text },
                            error = "",
                        )
                )
        }

    /**
     * A new habit: one to build, or one to break (which counts clean days instead of a small size).
     */
    fun kind(fade: Boolean) =
        mutable.update { ui ->
            val s = ui.sheet ?: return@update ui
            if (s.kind != SheetKind.NEW_HABIT || s.fade == fade) ui
            else {
                val floors = projection.value?.limits?.floors?.toString().orEmpty()
                ui.copy(
                    sheet =
                        s.copy(
                            fade = fade,
                            fields = listOf(s.fields[0], if (fade) floors else ""),
                            error = "",
                        )
                )
            }
        }

    /** One of the starter cues for a new foundation: laid at once. */
    fun starter(cue: String) {
        val s = mutable.value.sheet ?: return
        if (s.kind != SheetKind.NEW_STACK) return
        mutable.update { it.copy(sheet = s.copy(fields = listOf(cue))) }
        submit()
    }

    /** The sheet's main button. */
    fun submit() {
        val s = mutable.value.sheet ?: return
        val f = s.fields
        when (s.kind) {
            SheetKind.WHO -> onSheet(commands.who(f[0])) { it.copy(sheet = null) }
            SheetKind.NEW_STACK ->
                onSheet(commands.addStack(f[0])) { reply ->
                    reply.copy(
                        sheet = null,
                        city = false,
                        weeks = 0,
                        selected = null,
                        focus = lastId.ifEmpty { reply.focus },
                        jump = reply.jump + 1,
                        combo = 0,
                    )
                }
            SheetKind.EDIT_STACK ->
                onSheet(commands.editStack(s.target, f[0], f[1])) { it.copy(sheet = null) }
            SheetKind.NEW_HABIT ->
                onSheet(
                    commands.addHabit(
                        s.target,
                        s.fade,
                        f[0],
                        if (s.fade) "" else f[1],
                        if (s.fade) f[1].trim().toIntOrNull() ?: 0 else 0,
                    )
                ) {
                    it.copy(sheet = null, selected = null)
                }
            SheetKind.NAMES ->
                onSheet(commands.names(s.target, f)) {
                    it.copy(sheet = HabitsSheet(SheetKind.TUNE, s.target))
                }
            SheetKind.PAIR ->
                onSheet(commands.pair(s.target, f[0])) {
                    it.copy(sheet = HabitsSheet(SheetKind.TUNE, s.target))
                }
            SheetKind.TUNE,
            SheetKind.RESTACK,
            SheetKind.RETIRE -> Unit
        }
    }

    /** The sheet's red button: remove an empty foundation, retire a habit. */
    fun danger() {
        val s = mutable.value.sheet ?: return
        when (s.kind) {
            SheetKind.EDIT_STACK ->
                onSheet(commands.removeStack(s.target)) { it.copy(sheet = null, selected = null) }
            SheetKind.RETIRE ->
                onSheet(commands.retire(s.target)) { it.copy(sheet = null, selected = null) }
            else -> Unit
        }
    }

    /** Stand the block of the sheet on foundation [stackId]: the towers follow it there. */
    fun restackTo(stackId: String) {
        val s = mutable.value.sheet ?: return
        if (s.kind != SheetKind.RESTACK) return
        if (projection.value?.stackOf(s.target)?.id == stackId) return back()
        onSheet(commands.restack(s.target, stackId)) {
            it.copy(sheet = null, selected = null, focus = stackId, jump = it.jump + 1)
        }
    }

    /** The dials of the block in the sheet: its own size (0 two minutes, 1 normal, 2 stretch). */
    fun size(size: Int) = dial { commands.size(it, size) }

    /** Earlier (-1) or later (1) in its stack. */
    fun move(dir: Int) = dial { commands.move(it, dir) }

    /** Take today's block back. */
    fun undo() {
        dial { commands.undo(it) }
        mutable.update { it.copy(combo = 0) }
    }

    private fun dial(run: (String) -> HabitsReply) {
        val s = mutable.value.sheet ?: return
        if (s.kind == SheetKind.TUNE) said(run(s.target))
    }

    /** Back from a sheet opened from a block's dials to the dials; any other sheet closes. */
    fun back() =
        mutable.update { ui ->
            val s = ui.sheet ?: return@update ui
            if (s.kind in FROM_DIALS) ui.copy(sheet = HabitsSheet(SheetKind.TUNE, s.target))
            else ui.copy(sheet = null, selected = null)
        }

    fun closeSheet() = mutable.update { it.copy(sheet = null, selected = null) }

    /** Toast [id] has been shown to its end. */
    fun toastShown(id: Long) =
        mutable.update { if (it.toast?.id == id) it.copy(toast = null) else it }

    /** Play [id] has run to its end. */
    fun played(id: Long) = mutable.update { if (it.play?.id == id) it.copy(play = null) else it }

    // ---- answers ----

    private var lastId = ""

    /** A command from the scene or a dial: whatever it has to say is a toast. */
    private fun said(reply: HabitsReply) {
        onProjection()
        if (reply.message.isNotEmpty() && (!reply.ok || reply.changed))
            mutable.update { it.copy(toast = HabitsToast(++serial, reply.message)) }
    }

    /**
     * A command from a sheet: stored, [then] happens; refused or not saved, the sheet stays and
     * says why.
     */
    private fun onSheet(reply: HabitsReply, then: (HabitsUi) -> HabitsUi) {
        onProjection()
        lastId = reply.id
        mutable.update { ui ->
            if (reply.ok) then(ui) else ui.copy(sheet = ui.sheet?.copy(error = reply.message))
        }
        if (reply.ok && reply.message.isNotEmpty() && reply.changed)
            mutable.update { it.copy(toast = HabitsToast(++serial, reply.message)) }
    }

    private companion object {
        val FROM_DIALS = setOf(SheetKind.NAMES, SheetKind.PAIR, SheetKind.RESTACK, SheetKind.RETIRE)
    }
}
