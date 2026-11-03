package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.ReactionKind
import com.lifemax.rpg.feature.pet.host.CatWorldHost
import com.lifemax.rpg.feature.pet.room.RoomStyle
import java.lang.ref.WeakReference

/**
 * The cat world of the process as a screen draws it: the [host], its one [house] of bodies, and the
 * [generation] of that house (a new one means the world was rebuilt and a screen makes its stage
 * anew).
 */
internal class WorldBinding(val host: CatWorldHost, val house: HouseLife, val generation: Int)

/**
 * The world for [looks], or null when the screen keeps its own autopilot room: the host has no
 * world (an older page, no block, no brain) or its cats are not the ones this screen shows. Follows
 * the host, so a world that starts or is retired later swaps the screen's room in place.
 */
@Composable
internal fun rememberWorld(
    looks: List<HouseCatLook>,
    host: CatWorldHost = CatWorldHost.shared,
): WorldBinding? {
    var generation by remember(host) { mutableIntStateOf(host.generation) }
    DisposableEffect(host) {
        val listener = Runnable { generation = host.generation }
        host.addListener(listener)
        generation = host.generation // it may have changed between composing and now
        onDispose { host.removeListener(listener) }
    }
    val house = host.house
    return if (host.active && house != null && host.cats.map { it.name } == looks.map { it.name }) {
        WorldBinding(host, house, generation)
    } else null
}

/**
 * The one stage that shows the world's house, shared by every screen that draws it, so the Overview
 * card and the Cat tab have the same cats in the same places under the same camera. Held weakly: a
 * stage nobody composes any more is let go (with the room's pictures), and the next screen makes
 * one over the same house, which is where the cats are anyway.
 */
internal object SharedStages {
    private var held: WeakReference<HouseStage>? = null

    @Synchronized
    fun stageFor(
        kit: RoomKit,
        world: WorldBinding,
        style: RoomStyle,
        fedHouse: Boolean,
    ): HouseStage {
        val stage =
            held?.get()?.takeIf { it.kit === kit && it.house === world.house }
                ?: HouseStage(kit, world.house, style).also {
                    it.world = world.host
                    it.toys = world.host.toys
                    it.asyncWarm = true
                    held = WeakReference(it)
                }
        stage.setFedHouse(fedHouse)
        return stage
    }
}

/**
 * The owner's slow blink at cat [cat] of a house the native brain moves: the blink back only (a
 * sleeping cat is left alone), never a plan of the autopilot's over the brain's. True if it
 * blinked.
 */
internal fun HouseLife.blinkBack(cat: Int): Boolean {
    if (cat !in 0 until count || isAsleep(cat)) return false
    bodies[cat].react(ReactionKind.SLOW_BLINK)
    return true
}
