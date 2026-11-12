package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.room.CameraDirector

/**
 * The Cat tab for the plugin: the screen over the view model, with the live room. [onAction] hands
 * a tap to the page ([PetHandOver]); everything else is a command the view model sends. The room
 * moves only while the screen is [visible] (a screen behind the page runs nothing, A24) and holds a
 * still pose when the phone asks for no animations.
 *
 * With a page that said `pet.touch.v1` the cats answer a finger on the room, tapping the bowl
 * feeds, and the room opens full screen on the screen's own ([PetFullRoom]); the inline room then
 * stands still while the full-screen one runs, so there is one clock at a time.
 */
@Composable
fun PetRoute(
    vm: PetViewModel,
    topInsetDp: Float,
    bottomInsetDp: Float,
    onAction: (String, String) -> Unit,
    visible: Boolean,
) {
    val p = vm.pet ?: return
    LaunchedEffect(visible) { if (!visible) vm.hidden() }
    // no cat at home (pet.empty.v1): the first cat's adoption or the memorial, with no room
    if (p.empty != null) {
        EmptyHome(vm, p, topInsetDp, bottomInsetDp)
        return
    }
    val actions =
        remember(vm) {
            PetActions(
                feed = vm::feed,
                blink = vm::blink,
                vacation = vm::vacation,
                rename = vm::rename,
                focus = vm::focus,
                buyToy = vm::buyToy,
                expandGenome = vm::expandGenome,
                handOver = { onAction(it, "") },
                play = { if (!vm.playPressed()) onAction(PetHandOver.PLAY, "") },
                playToy = vm::playToy,
                demo = vm::demoPressed,
                noticeShown = vm::noticeShown,
                openRoom = vm::openRoom,
                catteryOpen = vm::openCattery,
            )
        }
    val looks =
        remember(p.cats) {
            p.cats.map { HouseCatLook(it.name, it.coat, it.stage, it.weak, it.rig) }
        }
    val still = rememberAnimationsOff()
    val room = rememberRoomState(looks, reducedMotion = still)
    val buzz = LocalPageBuzz.current
    if (room is RoomState.Ready) {
        val stage = room.stage
        // the owner's care reaches the cats in the room as it arrives
        LaunchedEffect(vm.reactionVersion, room) { vm.drainReactions().forEach { stage.react(it) } }
        SideEffect { stage.touchSink = PetTouchSink(vm, buzz) { vm.pet?.touch?.haptics } }
        // the camera frames the cat the card shows (the page's focus, one source of truth)
        val framed = CatSelection.cameraCat(p)
        LaunchedEffect(stage, framed) { stage.select(framed) }
        DisposableEffect(stage) { onDispose { stage.select(CameraDirector.NONE) } }
        // how each cat takes touch is the page's: its trust, its trait, its saturation now
        LaunchedEffect(p.touch, stage) {
            p.touch?.profiles?.forEachIndexed { i, t ->
                stage.petting.profile(
                    i,
                    t.level,
                    t.satMul.toFloat(),
                    t.trust.toFloat(),
                    t.sat.toFloat(),
                )
            }
        }
    }
    val screen = vm.catteryScreen
    val catteryActions =
        remember(vm) {
            CatteryActions(
                back = vm::closeCattery,
                send = vm::cattery,
                noticeShown = vm::noticeShown,
            )
        }
    val depthNote: (() -> String)? = if (p.demo != null) LiveDepth::readout else null
    val touchable = p.touch != null
    // a finger on a cat chooses it: the page's own focus command, so the card follows with the
    // camera
    val onCat: (Int) -> Unit = { i -> vm.pet?.let { CatSelection.tapped(it, i) }?.let(vm::focus) }
    val touch = if (touchable) RoomTouch(onBowl = vm::feed, onCat = onCat) else null
    val ui = PetUi(vm.waiting, vm.notice, vm.toyTray, depthNote)
    if (screen != null && p.cattery != null) {
        // a cattery screen covers the Cat tab: its room is not composed, so its clock stops
        CatteryHost(p, screen, ui, catteryActions, topInsetDp, bottomInsetDp)
    } else {
        PetScreen(p, ui, actions, topInsetDp, bottomInsetDp) { modifier ->
            PetRoom(p, room, visible && !vm.fullRoom, modifier, touch, still)
        }
    }
    if (touchable && vm.fullRoom && room is RoomState.Ready) {
        PetFullRoom(
            p,
            room,
            running = visible,
            still = still,
            onBowl = vm::feed,
            onClose = vm::closeRoom,
            onCat = onCat,
        )
    }
}

/**
 * The room in its frame: live while [visible], described for TalkBack, empty if its art is lost.
 * With [touch] the cats answer a finger on it.
 */
@Composable
internal fun PetRoom(
    p: PetProjection,
    room: RoomState,
    visible: Boolean,
    modifier: Modifier,
    touch: RoomTouch? = null,
    still: Boolean = rememberAnimationsOff(),
) {
    val names = p.cats.joinToString(" and ") { it.name }
    RoomHost(
        room,
        running = visible,
        still = still,
        modifier = modifier.semantics { contentDescription = "$names in the room" },
        touch = touch,
    )
}

/** What a reaction does to the cat it names. */
internal fun HouseStage.react(reaction: PetReaction) {
    when (reaction) {
        is PetReaction.Fed -> feed(reaction.cat)
        is PetReaction.Bowl -> visitBowl(reaction.cat)
        is PetReaction.Blink -> slowBlink(reaction.cat)
        is PetReaction.LevelUp -> petting.levelUp(reaction.cat)
    }
}

/**
 * The Cat tab with no cat at home: the page's intro (a first cat to adopt) or its memorial (a cat
 * has passed, and the door can open again) as the cattery's Adoption and Memorial screens, with no
 * room behind them. Back has nowhere to go (the tab is all there is), so the system Back leaves the
 * tab as it does from any other tab; the screen asks the page for its detail at once.
 */
@Composable
private fun EmptyHome(vm: PetViewModel, p: PetProjection, topInsetDp: Float, bottomInsetDp: Float) {
    val screen = p.empty ?: return
    val actions =
        remember(vm) {
            CatteryActions(back = {}, send = vm::cattery, noticeShown = vm::noticeShown)
        }
    LaunchedEffect(vm.catteryScreen == null, screen) { vm.openEmpty(screen) }
    val ui = PetUi(vm.waiting, vm.notice, vm.toyTray, null)
    CatteryHost(p, vm.catteryScreen ?: screen, ui, actions, topInsetDp, bottomInsetDp, root = true)
}
