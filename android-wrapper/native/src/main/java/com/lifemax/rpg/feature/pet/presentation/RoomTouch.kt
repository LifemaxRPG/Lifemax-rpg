package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.lifemax.rpg.feature.pet.body.TouchHit

/**
 * How a room takes the owner's finger: the cats answer it (petting), a tap on the food bowl is
 * [onBowl] (it feeds), and [fullScreen] draws the room over the whole window (see [HouseStage]).
 */
@Immutable
internal class RoomTouch(
    val fullScreen: Boolean = false,
    val onBowl: () -> Unit = {},
    /** A finger landed on cat [it] (an index of the house): the owner chose it. */
    val onCat: (Int) -> Unit = {},
)

/**
 * Feeds one finger to [stage]: down, every move, up. The gesture is consumed, so a stroke across
 * the room is not a swipe to the next tab or a scroll; a finger that lands on the bowl and lifts on
 * it is a tap on the bowl. A touch the system takes away ends like a lift.
 */
internal fun Modifier.petTouch(
    stage: HouseStage,
    onBowl: () -> Unit,
    onCat: (Int) -> Unit = {},
): Modifier =
    pointerInput(stage) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            val hit = stage.pointerDown(down.position.x, down.position.y)
            val onBowlAtEnd = hit == TouchHit.BOWL
            if (hit >= 0) onCat(hit)
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (change.changedToUp()) break
                    if (change.positionChanged()) {
                        stage.pointerMove(change.position.x, change.position.y)
                        change.consume()
                    }
                }
            } finally {
                stage.pointerUp()
            }
            if (onBowlAtEnd) onBowl()
        }
    }
