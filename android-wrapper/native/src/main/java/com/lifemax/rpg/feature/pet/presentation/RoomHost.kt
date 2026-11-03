package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lifemax.rpg.feature.pet.room.RoomStyle

/**
 * Where the room stands: its art and the first cat's sprites are still loading ([Loading]), the art
 * could not be loaded for good ([Unavailable], so the caller draws its still fallback), or the
 * house is ready to draw ([Ready]).
 */
internal sealed interface RoomState {
    data object Loading : RoomState

    data object Unavailable : RoomState

    class Ready(val stage: HouseStage) : RoomState
}

/**
 * The room for [looks], made once for the same looks: the art loaded off the main thread, a
 * [HouseLife] seeded by each cat's name and coat in a [HouseStage] that is given each cat's sprites
 * as they arrive. It is [RoomState.Loading] until the first cat's sprites are here (the caller
 * shows its still picture meanwhile); a later cat whose sprites do not come is simply not drawn.
 * With a [prepared] stage (screenshots and tests) nothing is loaded and the room is ready at once;
 * with [wanted] false and none prepared, nothing is loaded either and the room stays loading.
 *
 * [style] is how the room is drawn ([RoomStyle]): the classic flat room by default. This is where
 * the 2.5D room gets switched on for a screen (`style = RoomStyle.DEPTH25`, or `DEPTH25_STILL` for
 * reduced motion); no screen does yet.
 */
@Composable
internal fun rememberRoomState(
    looks: List<HouseCatLook>,
    prepared: HouseStage? = null,
    wanted: Boolean = true,
    style: RoomStyle = RoomStyle.CLASSIC,
): RoomState {
    val load = if (prepared == null && wanted) rememberRoomLoad().value else null
    val kit = load?.kit
    val stage = if (kit != null) rememberHouseStage(kit, looks, style) else null
    return when {
        prepared != null -> RoomState.Ready(prepared)
        stage != null -> RoomState.Ready(stage)
        load != null && kit == null -> RoomState.Unavailable
        else -> RoomState.Loading
    }
}

/**
 * The live room: the house with its three rooms and the light of the hour, with the cats living in
 * it, drawn at whole device pixels per room pixel (see [HouseStage]). The Overview's cat card and
 * the Cat tab both draw it here. It moves only while [running] and the app is in front, and holds a
 * still pose with [still]. While the room is loading nothing is drawn, and when its art could not
 * be loaded [unavailable] is.
 */
@Composable
internal fun RoomHost(
    state: RoomState,
    running: Boolean,
    still: Boolean,
    modifier: Modifier = Modifier,
    unavailable: @Composable () -> Unit = {},
) {
    when (state) {
        is RoomState.Ready -> HouseLiveView(state.stage, running, still, modifier)
        RoomState.Unavailable -> unavailable()
        RoomState.Loading -> Unit
    }
}
