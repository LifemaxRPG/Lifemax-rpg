package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.feature.lifedirector.domain.ChipSize
import com.lifemax.rpg.feature.lifedirector.domain.DragChip
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Everything drawn over the list while a session is lifted: the trash sliding up from the bottom
 * above the bars, the session as a small chip above the finger, and the list scrolling when the
 * finger nears its top or bottom edge. It takes no touches (the list's gesture has them). With
 * [reduced] motion nothing slides, springs or grows: the trash is there, the chip follows the
 * finger, and letting go is instant.
 */
@Composable
internal fun RemoveOverlay(
    drag: DragRemoveController,
    list: LazyListState,
    topInsetDp: Float,
    bottomInsetDp: Float,
    reduced: Boolean,
) {
    val density = LocalDensity.current
    val held by drag.held.collectAsState()
    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { drag.anchor.overlayAt(it) }
            .onSizeChanged {
                drag.layout(
                    it.width.toFloat(),
                    it.height.toFloat(),
                    with(density) { topInsetDp.dp.toPx() },
                    with(density) { bottomInsetDp.dp.toPx() },
                    density.density,
                )
            }
    ) {
        val h = held ?: return@Box
        TrashSlide(drag, h, reduced)
        FlyingChip(drag, list, h, reduced)
        if (h.phase == DragPhase.HELD) AutoScroll(drag, list)
    }
}

/**
 * The trash, in the place the model puts it, sliding up from below the screen while a session is
 * held.
 */
@Composable
private fun TrashSlide(drag: DragRemoveController, held: Held, reduced: Boolean) {
    val target by drag.target.collectAsState()
    val armed by drag.armed.collectAsState()
    val t = target ?: return
    val up = held.phase != DragPhase.RETURNING
    val shown = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(up, reduced) {
        val to = if (up) 1f else 0f
        if (reduced) shown.snapTo(to) else shown.animateTo(to, tween(SLIDE_MS))
    }
    val r = t.radius
    Box(
        Modifier.offset { IntOffset((t.centre.x - r).roundToInt(), (t.centre.y - r).roundToInt()) }
            .graphicsLayer {
                translationY = (1f - shown.value) * (r * 2 + SLIDE_FROM_DP.dp.toPx())
                alpha = shown.value
            }
    ) {
        RemoveTarget(
            armed = armed && held.phase == DragPhase.HELD,
            idleLabel = "Drop to remove",
            armedLabel = "Release to remove",
            description =
                if (armed) "Release to remove ${held.row.title} from today"
                else "Drop here to remove ${held.row.title} from today",
            reduced = reduced,
        )
    }
}

/**
 * The lifted session as a small chip ([LiftedChip]): just above the finger while held (a little
 * larger, with a shadow), into the trash, shrinking, when dropped there, and, when let go
 * elsewhere, back to its row's place with a spring, fading as it settles into the row that has
 * opened for it. A chip lifted from the dial grows from the size of its segment and shrinks back
 * into it.
 */
@Composable
private fun FlyingChip(
    drag: DragRemoveController,
    list: LazyListState,
    held: Held,
    reduced: Boolean,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val finger by drag.finger.collectAsState()
    val target by drag.target.collectAsState()
    val placed = remember(held.row.key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scale = remember(held.row.key) { Animatable(1f) }
    val shown = remember(held.row.key) { Animatable(1f) }
    var from by remember(held.row.key) { mutableStateOf(DragPhase.HELD) }
    val listWidth = list.layoutInfo.viewportSize.width.toFloat()
    val size = remember { mutableStateOf(ChipSize.Zero) }

    /** Where the chip is held for a finger at [at]: centred just above it. */
    fun heldAt(at: DragPoint) =
        DragChip.origin(at, size.value, listWidth, density.density).toOffset()

    LaunchedEffect(held.row.key) {
        view.announceForAccessibility(
            "${held.row.title} lifted. Drag it to the trash to remove it from today."
        )
    }
    LaunchedEffect(held.row.key, held.phase) {
        val here = heldAt(drag.finger.value)
        when (held.phase) {
            DragPhase.HELD -> {
                shown.snapTo(1f)
                if (reduced) scale.snapTo(1f)
                else {
                    // a chip lifted from the dial grows from the size of its segment
                    if (held.source == DragSource.DIAL) scale.snapTo(FROM_SEGMENT_SCALE)
                    scale.animateTo(LIFT_SCALE, spring(Spring.DampingRatioMediumBouncy))
                }
            }
            DragPhase.DROPPING -> {
                if (from == DragPhase.HELD) placed.snapTo(here)
                val to =
                    target?.let { DragChip.centredOn(it.centre, size.value).toOffset() } ?: here
                if (reduced) shown.snapTo(0f)
                else {
                    placed.animateTo(to, tween(DROP_MS))
                    scale.animateTo(.2f, tween(DROP_MS))
                    shown.animateTo(0f, tween(DROP_MS))
                }
            }
            DragPhase.RETURNING -> {
                if (from == DragPhase.HELD) placed.snapTo(here)
                shown.snapTo(1f)
                val home =
                    if (held.source == DragSource.DIAL)
                        drag.anchor.home(held.row.tap?.id.orEmpty())?.toPoint()
                    else slotMiddle(list, held.row, size.value.height, drag.finger.value, listWidth)
                comeHome(home, size.value, placed, scale, shown, reduced, held.source)
                drag.settled()
            }
        }
        from = held.phase
    }
    // the chip is not drawn until it has been measured, so it never shows at a wrong place
    val measured = size.value != ChipSize.Zero
    LiftedChip(
        held.row,
        Modifier.widthIn(max = with(density) { DragChip.maxWidth(listWidth).toDp() })
            .onSizeChanged { size.value = ChipSize(it.width.toFloat(), it.height.toFloat()) }
            .graphicsLayer {
                val at = if (held.phase == DragPhase.HELD) heldAt(finger) else placed.value
                translationX = at.x
                translationY = at.y
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (measured) shown.value else 0f
            },
    )
}

private fun DragPoint.toOffset() = Offset(x, y)

/**
 * A chip let go away from the trash goes back to [home], the middle of its row's slot or of its
 * dial segment (null when the dial is off the screen), and fades as it settles there with a spring.
 * A chip from the dial also shrinks to the size of its segment. With reduced motion it is simply
 * gone.
 */
private suspend fun comeHome(
    home: DragPoint?,
    chip: ChipSize,
    placed: Animatable<Offset, AnimationVector2D>,
    scale: Animatable<Float, AnimationVector1D>,
    shown: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    source: DragSource,
) {
    if (reduced || home == null) return shown.snapTo(0f)
    coroutineScope {
        launch {
            scale.animateTo(
                if (source == DragSource.DIAL) FROM_SEGMENT_SCALE else 1f,
                tween(SETTLE_MS),
            )
        }
        launch { shown.animateTo(0f, tween(SETTLE_MS)) }
        placed.animateTo(
            DragChip.centredOn(home, chip).toOffset(),
            spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
        )
    }
}

/**
 * The middle of the row's slot in the list now, or just off the edge the finger last was nearer (a
 * chip's [height] beyond it) when the row has scrolled out of sight.
 */
private fun slotMiddle(
    list: LazyListState,
    row: OrderRow,
    height: Float,
    finger: DragPoint,
    listWidth: Float,
): DragPoint {
    val info = list.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == row.key }
    val y =
        when {
            item != null -> item.offset + item.size / 2f
            finger.y < info.viewportSize.height / 2f -> -height
            else -> info.viewportSize.height + height
        }
    return DragPoint(listWidth / 2, y)
}

/**
 * Scrolls the list while the finger is in a band at its top or bottom, faster the nearer the edge.
 */
@Composable
private fun AutoScroll(drag: DragRemoveController, list: LazyListState) {
    LaunchedEffect(drag, list) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / NANOS
            last = now
            val speed = drag.scrollSpeed()
            if (speed != 0f) list.scrollBy(speed * seconds)
        }
    }
}

/** How much larger a lifted chip is than it is at rest. */
internal const val LIFT_SCALE = 1.04f

/** How big a chip is next to a dial segment it grows from or shrinks back into. */
private const val FROM_SEGMENT_SCALE = .5f
private const val SLIDE_MS = 200
private const val SLIDE_FROM_DP = 120
private const val DROP_MS = 170
private const val SETTLE_MS = 160
private const val NANOS = 1_000_000_000f
