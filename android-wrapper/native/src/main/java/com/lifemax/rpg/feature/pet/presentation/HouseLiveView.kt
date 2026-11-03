package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.room.RoomStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the card knows of one cat of the house: its [name] and [coat] seed its habits (so it keeps
 * its character from one visit to the next), [stage] is its growth stage (-1 grown) and [weak] its
 * hunger.
 */
internal data class HouseCatLook(
    val name: String,
    val coat: String,
    val stage: Int,
    val weak: Boolean,
)

/** The outcome of loading the room's art: [kit] is null if it could not be loaded, for good. */
internal class RoomLoad(val kit: RoomKit?)

/**
 * The live room for a card: a [HouseStage] stepped once per display frame by the window's frame
 * clock and drawn by [HouseStage.draw].
 *
 * Time. One coroutine waits for each frame (`withFrameNanos`) and steps the house by the real time
 * since the last one, so the motion is the same at 60, 90 or 120 Hz. It runs only while [running]
 * and the window's lifecycle is at least resumed; otherwise no frame is requested at all, so a
 * hidden or stopped screen costs nothing, and the cats pick up where they were.
 *
 * Stillness. With [still] (the page's reduced motion, or the phone's "remove animations") the room
 * is drawn once, the cats sitting, and the clock never runs.
 *
 * Only the canvas redraws on a frame (the counter is read in the draw phase), so nothing recomposes
 * while the cats move.
 */
@Composable
internal fun HouseLiveView(
    stage: HouseStage,
    running: Boolean,
    still: Boolean,
    modifier: Modifier = Modifier,
    touch: RoomTouch? = null,
) {
    val resumed = rememberResumed()
    val frame = remember { mutableLongStateOf(0L) }
    val animate = running && resumed && !still
    LaunchedEffect(stage, animate) {
        if (!animate) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                stage.step(((now - last) / NANOS_PER_SECOND))
                last = now
                frame.longValue = now
            }
        }
    }
    val touchable = if (touch != null) Modifier.petTouch(stage, touch.onBowl) else Modifier
    val fullScreen = touch?.fullScreen == true
    Canvas(modifier.then(touchable)) {
        frame.longValue // redraw on every step of the clock
        stage.fullScreen = fullScreen // the room is drawn to fit this canvas, wherever it was last
        stage.draw(this)
    }
}

/** The room's art, loaded off the main thread: null while loading, then a [RoomLoad]. */
@Composable
internal fun rememberRoomLoad(): State<RoomLoad?> =
    produceState<RoomLoad?>(null) {
        value = RoomLoad(withContext(Dispatchers.Default) { CatArt.room() })
    }

/**
 * The house of the cats on a card, made once for the same [looks]: a [HouseLife] seeded by each
 * cat's name and coat, in a [HouseStage] that is given each cat's sprites as they arrive. Null
 * until the first cat's sprites are here (the card shows its still portrait meanwhile); a later cat
 * whose sprites do not come is simply not drawn. The room is drawn in [style]: the classic flat
 * room by default, or the 2.5D room (the integration point for CT9; see [RoomStyle]).
 */
@Composable
internal fun rememberHouseStage(
    kit: RoomKit,
    looks: List<HouseCatLook>,
    style: RoomStyle = RoomStyle.CLASSIC,
): HouseStage? {
    val stage =
        remember(kit, looks, style) {
            val cats = looks.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) }
            HouseStage(kit, HouseLife(cats), style).also { st ->
                st.setFedHouse(looks.none { it.weak })
            }
        }
    val sprites = looks.map { rememberCatSprites(it.coat, it.stage) }
    SideEffect { sprites.forEachIndexed { i, s -> stage.setSprites(i, s) } }
    return if (sprites[0] != null) stage else null
}
