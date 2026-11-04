package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lifemax.rpg.core.designsystem.feedback.LocalPageBuzz
import com.lifemax.rpg.feature.pet.domain.PetProjection

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
                noticeShown = vm::noticeShown,
                openRoom = vm::openRoom,
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
    val touchable = p.touch != null
    val touch = if (touchable) RoomTouch(onBowl = vm::feed) else null
    PetScreen(p, PetUi(vm.waiting, vm.notice, vm.toyTray), actions, topInsetDp, bottomInsetDp) {
        modifier ->
        PetRoom(p, room, visible && !vm.fullRoom, modifier, touch, still)
    }
    if (touchable && vm.fullRoom && room is RoomState.Ready) {
        PetFullRoom(
            p,
            room,
            running = visible,
            still = still,
            onBowl = vm::feed,
            onClose = vm::closeRoom,
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
