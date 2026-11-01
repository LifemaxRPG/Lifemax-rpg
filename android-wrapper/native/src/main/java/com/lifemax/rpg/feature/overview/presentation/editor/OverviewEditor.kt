package com.lifemax.rpg.feature.overview.presentation.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.core.designsystem.gesture.noTabSwipe
import com.lifemax.rpg.core.designsystem.mix
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import com.lifemax.rpg.feature.overview.domain.EditorRow
import com.lifemax.rpg.feature.overview.domain.EditorWords

/**
 * The most rows the editor draws: every tile the page has (OV_TILES with Rewards, Apps, Health).
 */
const val EDITOR_MAX_ROWS = 11

/**
 * The Overview's layout editor drawn natively (phase 2), as the page's #ovEdit: the head with Done,
 * a row per tile in [rows]' order (at most [EDITOR_MAX_ROWS]), and Reset.
 *
 * A row is dragged by its grip from the touch, or from anywhere on it after a long press; the grip
 * is never a tab swipe. The rows it passes glide aside (260 ms, the page's ease) and the dropped
 * row settles in 220 ms. The editor opens in 280 ms with its rows 30 ms apart. Under
 * [reducedMotion] nothing moves. TalkBack moves a row with its actions (up, down, to the top, to
 * the bottom) and hides or shows it. Back closes the editor while [visible].
 *
 * The order is the caller's: [onReorder] gets each new order, from a drop or a TalkBack move, and
 * the caller shows it at once (in [rows], before the next frame) and puts the page's order back if
 * the page refused it; the rows glide to whatever order [rows] has. [onReset] asks for the default,
 * [onToggle] hides or shows a tile, [onDone] closes. [buzz] plays the page's buzz for each moment.
 * [autoScroll] scrolls the list around the editor by the px it is given while a lifted row is near
 * the window's edge, and answers how far it went.
 */
@Composable
fun OverviewEditor(
    rows: List<EditorRow>,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit = {},
    buzz: (EditorBuzz) -> Unit = {},
    words: EditorWords = EditorWords(),
    reducedMotion: Boolean = false,
    visible: Boolean = true,
    animateOpen: Boolean = true,
    autoScroll: (Float) -> Float = { 0f },
    preview: DragPreview? = null,
) {
    val c = LocalPageColors.current
    val shown = rows.take(EDITOR_MAX_ROWS)
    val ids = shown.map { it.id }
    val byId = shown.associateBy { it.id }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val buzzNow by rememberUpdatedState(buzz)
    val reorder by rememberUpdatedState(onReorder)
    val state = remember {
        ReorderState(ids, scope, reducedMotion, 8 * density, { buzzNow(it) }, preview)
    }
    // after a drop too: an order the caller did not take glides back to the one it has
    LaunchedEffect(ids, state.drops) { state.show(ids) }

    val open = remember { Animatable(if (animateOpen && !reducedMotion) 0f else OPEN_END) }
    LaunchedEffect(Unit) {
        buzzNow(EditorBuzz.Open)
        open.animateTo(OPEN_END, tween(OPEN_END.toInt(), easing = LinearEasing))
    }
    BackHandler(enabled = visible) { onDone() }

    val window = LocalView.current
    val drop = { state.drop { reorder(it) } }
    fun move(delta: Float, at: LayoutCoordinates?, finger: Offset) {
        state.drag(delta)
        val y = at?.takeIf { it.isAttached }?.localToWindow(finger)?.y ?: return
        val step = autoScrollStep(y, window.height.toFloat(), density)
        if (step != 0f) state.drag(autoScroll(step))
    }

    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .graphicsLayer {
                val k = EditorEase.transform((open.value / OPEN_MS).coerceIn(0f, 1f))
                alpha = k
                translationY = (1 - k) * 8.dp.toPx()
            }
            .clip(EditShape)
            .background(c.panel)
            .border(1.dp, c.accent.mix(0.35f, c.line), EditShape)
            .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    words.title,
                    Modifier.semantics { heading() },
                    color = c.text,
                    fontSize = 16.38.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.57.sp,
                )
                Text(
                    words.hint,
                    Modifier.padding(top = 3.dp),
                    color = c.muted,
                    fontSize = 12.5.sp,
                    lineHeight = 17.5.sp,
                )
            }
            PageButton(words.done, words.done, onDone, Modifier.widthIn(min = 84.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.order.forEachIndexed { index, id ->
                val row = byId[id] ?: return@forEachIndexed
                key(id) {
                    val place = remember { arrayOfNulls<LayoutCoordinates>(1) }
                    val lifted = state.raised == id
                    EditorRowView(
                        row,
                        words,
                        lift = if (lifted) state.lift.value else 0f,
                        dimmed = state.dragging != null && !lifted,
                        onToggle = {
                            onToggle(id)
                            buzzNow(EditorBuzz.Toggle)
                        },
                        grip =
                            Modifier.noTabSwipe().pointerInput(id) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    state.start(id)
                                    drag(down.id) {
                                        move(it.positionChange().y, place[0], it.position)
                                        it.consume()
                                    }
                                    drop()
                                }
                            },
                        modifier =
                            Modifier.zIndex(if (lifted) 1f else 0f)
                                .onSizeChanged { state.measured(id, it.height.toFloat()) }
                                .onGloballyPositioned { place[0] = it }
                                .graphicsLayer {
                                    val k =
                                        EditorEase.transform(
                                            ((open.value - index * ROW_GAP_MS) / ROW_MS).coerceIn(
                                                0f,
                                                1f,
                                            )
                                        )
                                    translationY = state.offset(id) + (1 - k) * 8.dp.toPx()
                                    alpha = k
                                    val s = 1 + 0.03f * (if (lifted) state.lift.value else 0f)
                                    scaleX = s
                                    scaleY = s
                                }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { state.start(id) },
                                        onDragEnd = drop,
                                        onDragCancel = drop,
                                        onDrag = { change, amount ->
                                            change.consume()
                                            move(amount.y, place[0], change.position)
                                        },
                                    )
                                }
                                .semantics(mergeDescendants = true) {
                                    customActions =
                                        talkBackActions(row, state.order, words) {
                                            reorder(it)
                                            buzzNow(EditorBuzz.Move)
                                        } +
                                            toggleAction(row, words) {
                                                onToggle(id)
                                                buzzNow(EditorBuzz.Toggle)
                                            }
                                },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 12.dp), Alignment.Center) {
            Text(
                words.reset,
                Modifier.clip(RoundedCornerShape(8.dp))
                    .tapTarget(Role.Button) {
                        onReset()
                        buzzNow(EditorBuzz.Reset)
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                color = c.muted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight(650),
            )
        }
    }
}

/**
 * Row [row]'s TalkBack moves in [order]: only those it can make (no "Move up" on the first row).
 * Each hands the new order to [moved].
 */
internal fun talkBackActions(
    row: EditorRow,
    order: List<String>,
    words: EditorWords,
    moved: (List<String>) -> Unit,
): List<CustomAccessibilityAction> =
    listOf(
            EditorMove.Up to words.moveUp,
            EditorMove.Down to words.moveDown,
            EditorMove.Top to words.moveTop,
            EditorMove.Bottom to words.moveBottom,
        )
        .mapNotNull { (move, label) ->
            move.apply(order, row.id)?.let { next ->
                CustomAccessibilityAction(label) {
                    moved(next)
                    true
                }
            }
        }

/** The eye's own action on the row, "Hide <name>" or "Show <name>", as its label says. */
private fun toggleAction(row: EditorRow, words: EditorWords, toggle: () -> Unit) =
    listOf(
        CustomAccessibilityAction((if (row.hidden) words.show else words.hide) + row.name) {
            toggle()
            true
        }
    )

private val EditShape = RoundedCornerShape(20.dp)

/** The head's 280 ms, and each row's 300 ms starting 30 ms after the one above (.ov-edit-in). */
private const val OPEN_MS = 280f
private const val ROW_MS = 300f
private const val ROW_GAP_MS = 30f
private const val OPEN_END = ROW_MS + ROW_GAP_MS * (EDITOR_MAX_ROWS - 1)
