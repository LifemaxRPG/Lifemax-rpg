package com.lifemax.rpg.feature.lifedirector.presentation.drag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.boxShadow
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.presentation.components.DirectorPalette as P
import com.lifemax.rpg.feature.lifedirector.presentation.components.OrderRowView
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Everything drawn over the list while a session is lifted: the trash sliding up from the bottom
 * above the bars, the session as a card under the finger, and the list scrolling when the finger
 * nears its top or bottom edge. It takes no touches (the list's gesture has them). With [reduced]
 * motion nothing slides, springs or grows: the trash is there, the card follows the finger, and
 * letting go is instant.
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
        FlyingCard(drag, list, h, reduced)
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
 * The lifted session as a card: under the finger while held (a little larger, with a shadow); into
 * the trash, shrinking, when dropped there; and back to its place with a spring when let go
 * elsewhere, placed it settles into the row that has opened for it.
 */
@Composable
private fun FlyingCard(
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
    val grab = held.grab
    val inset = with(density) { LIST_SIDE.toPx() }
    val size = remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(held.row.key) {
        view.announceForAccessibility(
            "${held.row.title} lifted. Drag it to the trash to remove it from today."
        )
    }
    LaunchedEffect(held.row.key, held.phase) {
        val here = Offset(drag.finger.value.x - grab.x, drag.finger.value.y - grab.y)
        when (held.phase) {
            DragPhase.HELD -> {
                shown.snapTo(1f)
                if (reduced) scale.snapTo(1f)
                else {
                    // a card lifted from the dial grows from the size of its segment
                    if (held.source == DragSource.DIAL) scale.snapTo(FROM_SEGMENT_SCALE)
                    scale.animateTo(LIFT_SCALE, spring(Spring.DampingRatioMediumBouncy))
                }
            }
            DragPhase.DROPPING -> {
                if (from == DragPhase.HELD) placed.snapTo(here)
                val t = target
                val to =
                    if (t == null) here
                    else
                        Offset(
                            t.centre.x - size.value.width / 2f,
                            t.centre.y - size.value.height / 2f,
                        )
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
                if (held.source == DragSource.DIAL)
                    intoSegment(
                        drag.anchor.home(held.row.tap?.id.orEmpty()),
                        size.value,
                        placed,
                        scale,
                        shown,
                        reduced,
                    )
                else {
                    val slot =
                        Offset(inset, slotTop(list, held.row, size.value.height, drag.finger.value))
                    if (reduced) {
                        placed.snapTo(slot)
                        scale.snapTo(1f)
                    } else {
                        scale.animateTo(1f, tween(SETTLE_MS))
                        placed.animateTo(
                            slot,
                            spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
                        )
                    }
                }
                drag.settled()
            }
        }
        from = held.phase
    }
    Box(
        Modifier.width(with(density) { (list.layoutInfo.viewportSize.width - 2 * inset).toDp() })
            .onSizeChanged { size.value = it }
            .graphicsLayer {
                val at =
                    if (held.phase == DragPhase.HELD) Offset(finger.x - grab.x, finger.y - grab.y)
                    else placed.value
                translationX = at.x
                translationY = at.y
                scaleX = scale.value
                scaleY = scale.value
                alpha = shown.value
            }
    ) {
        LiftedCard(held.row)
    }
}

/**
 * A card lifted from the dial goes back into its segment: it shrinks and fades as it settles there
 * with a spring, [home] being the segment's place on the screen. With reduced motion, or with the
 * dial gone from the screen, it is simply gone.
 */
private suspend fun intoSegment(
    home: Offset?,
    card: IntSize,
    placed: Animatable<Offset, AnimationVector2D>,
    scale: Animatable<Float, AnimationVector1D>,
    shown: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
) {
    if (reduced || home == null) return shown.snapTo(0f)
    coroutineScope {
        launch { scale.animateTo(FROM_SEGMENT_SCALE, tween(SETTLE_MS)) }
        launch { shown.animateTo(0f, tween(SETTLE_MS)) }
        placed.animateTo(
            Offset(home.x - card.width / 2f, home.y - card.height / 2f),
            spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMedium),
        )
    }
}

/**
 * The session as a card: its row on the panel's colour with an edge and a shadow, nothing to tap.
 */
@Composable
internal fun LiftedCard(row: OrderRow, modifier: Modifier = Modifier) {
    Box(
        modifier
            .boxShadow(Color.Black.copy(alpha = .6f), 26.dp, CARD_SHAPE, dy = 10.dp)
            .background(P.bg0, CARD_SHAPE)
            .border(1.dp, P.line2, CARD_SHAPE)
            .padding(horizontal = 14.dp)
    ) {
        OrderRowView(row.copy(tap = null), {})
    }
}

/** Where the row's slot is in the list now, or just off the edge the finger last was nearer. */
private fun slotTop(list: LazyListState, row: OrderRow, height: Int, finger: DragPoint): Float {
    val info = list.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.key == row.key }
    return when {
        item != null -> item.offset.toFloat()
        finger.y < info.viewportSize.height / 2f -> -height.toFloat()
        else -> info.viewportSize.height.toFloat()
    }
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

private val CARD_SHAPE = RoundedCornerShape(16.dp)
/** How much larger a lifted card is than the row it was. */
internal const val LIFT_SCALE = 1.04f

/** How small a card is next to a dial segment it grows from or shrinks back into. */
private const val FROM_SEGMENT_SCALE = .3f
private const val SLIDE_MS = 200
private const val SLIDE_FROM_DP = 120
private const val DROP_MS = 170
private const val SETTLE_MS = 160
private const val NANOS = 1_000_000_000f
