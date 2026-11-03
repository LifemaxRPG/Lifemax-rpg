package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.lifemax.rpg.feature.pet.domain.PetProjection

/**
 * The Cat tab for the plugin: the screen over the view model, with the live room. [onAction] hands
 * a tap to the page ([PetHandOver]); everything else is a command the view model sends. The room
 * moves only while the screen is [visible] (a screen behind the page runs nothing, A24) and holds a
 * still pose when the phone asks for no animations.
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
                handOver = { onAction(it, "") },
                noticeShown = vm::noticeShown,
            )
        }
    val looks =
        remember(p.cats) { p.cats.map { HouseCatLook(it.name, it.coat, it.stage, it.weak) } }
    val room = rememberRoomState(looks)
    if (room is RoomState.Ready) {
        // the owner's care reaches the cats in the room as it arrives
        LaunchedEffect(vm.reactionVersion, room) {
            vm.drainReactions().forEach { room.stage.react(it) }
        }
    }
    PetScreen(p, PetUi(vm.waiting, vm.notice), actions, topInsetDp, bottomInsetDp) { modifier ->
        PetRoom(p, room, visible, modifier)
    }
}

/**
 * The room in its frame: live while [visible], described for TalkBack, empty if its art is lost.
 */
@Composable
internal fun PetRoom(p: PetProjection, room: RoomState, visible: Boolean, modifier: Modifier) {
    val names = p.cats.joinToString(" and ") { it.name }
    RoomHost(
        room,
        running = visible,
        still = rememberAnimationsOff(),
        modifier = modifier.semantics { contentDescription = "$names in the room" },
    )
}

/** What a reaction does to the cat it names. */
internal fun HouseStage.react(reaction: PetReaction) {
    when (reaction) {
        is PetReaction.Fed -> feed(reaction.cat)
        is PetReaction.Bowl -> visitBowl(reaction.cat)
        is PetReaction.Blink -> slowBlink(reaction.cat)
    }
}
