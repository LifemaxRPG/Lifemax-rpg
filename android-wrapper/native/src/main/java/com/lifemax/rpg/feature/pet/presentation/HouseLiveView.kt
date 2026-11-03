package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.body.Autopilot
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.room.RoomStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    val rig: RigMorph = RigMorph.NEUTRAL,
)

/** How long a room stays off the screen before its cached camera steps are let go of. */
private const val TRIM_AFTER_MS = 20_000L
private const val MS_PER_SECOND = 1000f

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
    val budgetMs = rememberFrameBudgetMs()
    SideEffect { stage.secondaryMotion = !still }
    // Memory: the 2.5D room's camera steps are made off the main thread while the room is shown,
    // and let go of once no view of the stage has been animating for a while, or at once when the
    // last view leaves the composition. Two views may share a stage (the inline room and the
    // full-screen room), so a view only lets go when the others are quiet too.
    val view = remember(stage) { HouseStage.View() }
    SideEffect { view.animating = animate }
    DisposableEffect(stage) {
        stage.attach(view)
        onDispose { stage.detach(view) }
    }
    LaunchedEffect(stage, animate) {
        if (animate) {
            withContext(Dispatchers.Default) { stage.warmDepth { !isActive } }
        } else {
            delay(TRIM_AFTER_MS)
            stage.trimWhenIdle()
        }
    }
    // the cat world of the process runs while any of its views animates (see CatWorldHost)
    DisposableEffect(stage, animate) {
        val world = stage.world
        val handle = if (animate && world != null) world.attach() else null
        onDispose { if (handle != null) world?.detach(handle) }
    }
    LaunchedEffect(stage, animate) {
        if (!animate) return@LaunchedEffect
        LiveDepth.resume()
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                stage.world?.advance(now)
                val dt = (now - last) / NANOS_PER_SECOND
                stage.step(dt)
                LiveDepth.record(stage.workMs, dt * MS_PER_SECOND, budgetMs)
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
 * whose sprites do not come is simply not drawn. The room is drawn in [style] (see [RoomStyle]); a
 * change of style is applied to the same stage in place. The 2.5D room's pictures are made on a
 * background thread first, and the room is not shown (null) until its rest picture is made, so the
 * first frames do not hitch.
 */
@Composable
internal fun rememberHouseStage(
    kit: RoomKit,
    looks: List<HouseCatLook>,
    style: RoomStyle = RoomStyle.CLASSIC,
): HouseStage? {
    // with a cat world (CatWorldHost) every screen draws the one house; else the room is its own
    val world = rememberWorld(looks)
    val stage =
        remember(kit, looks, world?.generation) {
            if (world != null) SharedStages.stageFor(kit, world, style, looks.none { it.weak })
            else {
                val cats = looks.map { HouseCat(Autopilot.seedFor(it.name, it.coat), it.weak) }
                HouseStage(kit, HouseLife(cats), style).also { st ->
                    st.asyncWarm = true
                    st.setFedHouse(looks.none { it.weak })
                }
            }
        }
    LaunchedEffect(stage, style) { stage.useStyle(style) }
    var warm by remember(stage) { mutableStateOf(stage.depthReady) }
    LaunchedEffect(stage, style) {
        withContext(Dispatchers.Default) { stage.prepareDepth() }
        warm = true
    }
    val sprites = looks.map { rememberCatSprites(it.coat, it.stage, it.rig) }
    SideEffect { sprites.forEachIndexed { i, s -> stage.setSprites(i, s) } }
    return if (sprites[0] != null && warm) stage else null
}
