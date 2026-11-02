package com.lifemax.rpg.feature.journal.presentation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.app.shell.rememberImeUp
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageSegment
import com.lifemax.rpg.core.designsystem.components.PageSegments
import com.lifemax.rpg.core.designsystem.components.PageTipCard
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.feature.journal.domain.JournalAction
import com.lifemax.rpg.feature.journal.domain.JournalChrome
import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.presentation.body.EntryEditor
import com.lifemax.rpg.feature.journal.presentation.body.JournalBodyColors
import com.lifemax.rpg.feature.journal.presentation.body.LocalJournalBodyColors
import com.lifemax.rpg.feature.journal.presentation.components.JournalColors
import com.lifemax.rpg.feature.journal.presentation.components.JournalHero
import com.lifemax.rpg.feature.journal.presentation.components.LocalJournalColors
import com.lifemax.rpg.feature.journal.presentation.components.LockCardView

@Composable
fun JournalRoute(
    vm: JournalViewModel,
    visible: Boolean,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onPage: (JournalAction) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val actions = remember(vm, onPage) { JournalActions(vm, onPage) }
    JournalScreen(ui, actions, visible, topInsetDp, bottomInsetDp)
}

/** Everything the screen can ask for, so the screen itself holds no state. */
class JournalActions(
    val select: (String) -> Unit = {},
    val write: (String) -> Unit = {},
    val swap: (d: String, from: String) -> Unit = { _, _ -> },
    val unlock: () -> Unit = {},
    val backup: () -> Unit = {},
    val dismissTip: () -> Unit = {},
    val page: (JournalAction) -> Unit = {},
    val toastShown: (Long) -> Unit = {},
    /** Phase 2: the screen writes for itself (library.journal.write.v1). */
    val edit: (key: String, text: String) -> Unit = { _, _ -> },
    val leaveField: () -> Unit = {},
    val claim: (kind: String) -> Unit = {},
    val openCatchUp: () -> Unit = {},
    val catchUpSend: (String) -> Unit = {},
    val catchUpCancel: () -> Unit = {},
    val celebrated: (Long) -> Unit = {},
) {
    /** A hand-over goes to the page only while the view model allows one (one per opening). */
    constructor(
        vm: JournalViewModel,
        page: (JournalAction) -> Unit,
    ) : this(
        vm::select,
        vm::write,
        vm::swap,
        vm::unlock,
        vm::backup,
        vm::dismissTip,
        { if (vm.handOver(it)) page(it) },
        vm::toastShown,
        vm::edit,
        vm::leaveField,
        vm::claim,
        vm::openCatchUp,
        vm::sendCatchUp,
        vm::closeCatchUp,
        vm::celebrated,
    )
}

/**
 * The Journal as the page lays it out on a phone, in one of three states:
 * - **open:** the Media/Journal switch, the tip under it, the hero, then the body (Screen B);
 * - **locked:** the switch and the lock card, nothing else;
 * - **resting** (a screen not on show, or before the page's first frame reached it): the switch on
 *   the page background.
 *
 * The switch is core's PageSegments at the place native Media draws it, so it never moves across
 * Media↔Journal. A new state fades in over 200 ms (an unlock); the state it replaces goes at once,
 * so the Journal's words never linger under a card or at rest. A hidden screen ([visible] false)
 * draws no toast.
 */
@Composable
fun JournalScreen(
    ui: JournalUi,
    actions: JournalActions,
    visible: Boolean = true,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    list: LazyListState = rememberLazyListState(),
) {
    val frame = ui.frame ?: return
    JournalTheme(frame.chrome) {
        val c = LocalJournalColors.current
        val spot = remember { TapSpot() }
        // a screen that writes: the bottom bar is gone while the keyboard is up, so it keeps no
        // room
        val keyboard = rememberImeUp() && ui.write != null
        Box(Modifier.fillMaxSize().background(c.bg).pointerInput(spot) { recordTaps(spot) }) {
            val padding =
                PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = topInsetDp.dp + 16.dp,
                    bottom = (if (keyboard) 0f else bottomInsetDp).dp + 24.dp,
                )
            Crossfade(frame.kind, animationSpec = tween(CROSSFADE_MS), label = "journal") { kind ->
                when (kind) {
                    Kind.Open ->
                        (ui.frame as? JournalFrame.Open)?.let {
                            OpenJournalList(it, ui, actions, padding, list, keyboard)
                        }
                    Kind.Locked ->
                        (ui.frame as? JournalFrame.Locked)?.let {
                            LockedPage(it, ui, actions, padding)
                        }
                    Kind.Resting -> RestingPage(frame.chrome, actions, padding)
                }
            }
            val celebration = ui.celebration
            val open = frame as? JournalFrame.Open
            if (visible && celebration != null && open != null)
                JournalCelebrationLayer(
                    celebration,
                    spot,
                    open.journal.write?.celebrationTarget,
                    reduced = !open.journal.motion,
                    onFinished = actions.celebrated,
                )
            val toast = ui.toast
            if (visible && toast != null)
                ToastLine(
                    toast,
                    frame.chrome,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(start = 12.dp, end = 12.dp, bottom = bottomInsetDp.dp + 12.dp),
                    actions.toastShown,
                )
        }
    }
}

/** The open Journal: switch, tip, hero, and Screen B's body, in one list. */
@Composable
private fun OpenJournalList(
    frame: JournalFrame.Open,
    ui: JournalUi,
    actions: JournalActions,
    padding: PaddingValues,
    list: LazyListState,
    keyboardUp: Boolean,
) {
    val j = frame.journal
    val editor =
        j.write?.let {
            EntryEditor(
                ui.draft,
                it.catchUp,
                ui.catchUp,
                keyboardUp,
                actions.edit,
                actions.leaveField,
                actions.catchUpSend,
                actions.catchUpCancel,
            )
        }
    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = padding) {
        item("panes") { Switch(frame.chrome, actions) }
        j.tip?.let { tip ->
            item("tip") {
                PageTipCard(tip.icon, tip.title, tip.body, tip.dismiss, actions.dismissTip)
            }
        }
        item("hero") { JournalHero(j.hero, actions.backup) }
        JournalBodySlot(j, ui.selection, actions, editor)
    }
}

/** The switch and the lock card (from a withheld frame: nothing of the Journal is here). */
@Composable
private fun LockedPage(
    frame: JournalFrame.Locked,
    ui: JournalUi,
    actions: JournalActions,
    padding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
        Switch(frame.chrome, actions)
        LockCardView(frame.chrome.lock.card, frame.status, ui.unlocking, actions.unlock)
    }
}

/** A screen not on show: the switch alone, so a swipe that pulls it in shows no data. */
@Composable
private fun RestingPage(chrome: JournalChrome, actions: JournalActions, padding: PaddingValues) {
    Column(Modifier.fillMaxSize().padding(padding)) { Switch(chrome, actions) }
}

@Composable
private fun Switch(chrome: JournalChrome, actions: JournalActions) =
    PageSegments(chrome.panes.map { PageSegment(it.id, it.label, it.current) }) {
        actions.page(JournalAction.OpenView(it))
    }

/** The page's toast as it shows: it rises in, stays as long as the page's, fades away, done. */
@Composable
private fun ToastLine(
    toast: JournalToast,
    chrome: JournalChrome,
    modifier: Modifier,
    onShown: (Long) -> Unit,
) {
    val shown = remember(toast.id) { Animatable(0f) }
    LaunchedEffect(toast.id) {
        shown.playToast()
        onShown(toast.id)
    }
    PageToast(toast.text, chrome.tokens, chrome.light, modifier, shown = { shown.value })
}

/** The page's palette and type for the Journal, with the Journal's own colours. */
@Composable
internal fun JournalTheme(chrome: JournalChrome, content: @Composable () -> Unit) {
    val colors =
        remember(chrome.tokens, chrome.light) { JournalColors(chrome.tokens, chrome.light) }
    val body =
        remember(chrome.tokens, chrome.light) { JournalBodyColors(chrome.tokens, chrome.light) }
    PageTheme(colors) {
        CompositionLocalProvider(
            LocalJournalColors provides colors,
            LocalJournalBodyColors provides body,
            content = content,
        )
    }
}

private enum class Kind {
    Open,
    Locked,
    Resting,
}

private val JournalFrame.kind
    get() =
        when (this) {
            is JournalFrame.Open -> Kind.Open
            is JournalFrame.Locked -> Kind.Locked
            is JournalFrame.Resting -> Kind.Resting
        }

/** The unlock's crossfade from the card to the Journal (plan §2). */
private const val CROSSFADE_MS = 200
