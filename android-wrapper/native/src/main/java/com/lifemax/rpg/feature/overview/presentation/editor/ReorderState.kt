package com.lifemax.rpg.feature.overview.presentation.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lifemax.rpg.feature.overview.domain.EditorBuzz
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** The page's ease for the editor (cubic-bezier(.2,.8,.2,1), 03-features.css). */
internal val EditorEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/** A frozen drag, for screenshot tests: row [id] lifted over slot [to], [dy] px from its place. */
data class DragPreview(val id: String, val to: Int, val dy: Float)

/**
 * The editor's local drag state (plan §3.1: local UI state; the order itself is the page's). Rows
 * glide with an offset of their own: during a drag the passed rows stand aside by a step; after a
 * drop or any other change of order every row starts where it was drawn and glides to its new place
 * (the page's FLIP), in 260 ms, the dropped row settling in 220 ms. Under [reduced] motion rows
 * jump. Offsets are px; the step is read when drawing, from the heights the rows measured.
 */
@Stable
internal class ReorderState(
    initial: List<String>,
    private val scope: CoroutineScope,
    private val reduced: Boolean,
    private val gap: Float,
    private val buzz: (EditorBuzz) -> Unit,
    preview: DragPreview? = null,
) {
    var order by mutableStateOf(initial)
        private set

    private val heights = mutableStateMapOf<String, Float>()
    private val glides = HashMap<String, Animatable<Float, *>>()
    private val shifts = HashMap<String, Animatable<Float, *>>()

    /** The lifted row, or null. */
    var dragging by mutableStateOf(preview?.id)
        private set

    private var from = preview?.let { initial.indexOf(it.id) } ?: -1
    private var to by mutableStateOf(preview?.to ?: -1)
    private var dy by mutableFloatStateOf(preview?.dy ?: 0f)
    private var mids = emptyList<Float>()

    /** 1 while a row is lifted, easing to 0 as it settles: its scale and its shadow. */
    val lift = Animatable(if (preview != null) 1f else 0f)

    /** Counts drops, so the caller's order is looked at again after each one. */
    var drops by mutableIntStateOf(0)
        private set

    /** The row that is lifted or still settling, drawn above the others. */
    var raised by mutableStateOf(preview?.id)
        private set

    init {
        if (preview != null)
            initial.forEachIndexed { i, id ->
                shifts[id] = Animatable(shiftFor(i, from, preview.to).toFloat())
            }
    }

    private fun glide(id: String) = glides.getOrPut(id) { Animatable(0f) }

    private fun shift(id: String) = shifts.getOrPut(id) { Animatable(0f) }

    private fun height(id: String) = heights[id] ?: 0f

    fun measured(id: String, height: Float) {
        heights[id] = height
    }

    /** Where row [id] is drawn from its laid-out place, in px; read it when drawing. */
    fun offset(id: String): Float {
        val step = dragging?.let { height(it) + gap } ?: 0f
        val own = if (id == dragging) dy else 0f
        return glide(id).value + shift(id).value * step + own
    }

    /** A new order from outside (the page's answer, a reset, a TalkBack move): rows glide to it. */
    fun show(next: List<String>) {
        if (next == order || dragging != null) return
        val before = tops(order, ::height, gap)
        val after = tops(next, ::height, gap)
        order = next
        for (id in next) {
            val was = before[id] ?: continue
            val g = glide(id)
            val start = g.value + was - (after[id] ?: was)
            now {
                g.snapTo(start)
                g.animateTo(0f, motion(GLIDE_MS))
            }
        }
    }

    /** A finger lifted row [id]. */
    fun start(id: String) {
        if (dragging != null) return
        val i = order.indexOf(id)
        if (i < 0) return
        val top = tops(order, ::height, gap)
        mids = order.map { (top[it] ?: 0f) + height(it) / 2 }
        from = i
        to = i
        dy = 0f
        dragging = id
        raised = id
        now { lift.snapTo(1f) }
        buzz(EditorBuzz.Lift)
    }

    /** The finger moved [delta] px (the list scrolling under it counts as a move). */
    fun drag(delta: Float) {
        if (dragging == null) return
        dy += delta
        val slot = slotFor(mids, from, mids[from] + dy)
        if (slot == to) return
        to = slot
        buzz(EditorBuzz.Cross)
        order.forEachIndexed { i, id ->
            if (id != dragging) {
                val s = shift(id)
                now { s.animateTo(shiftFor(i, from, slot).toFloat(), motion(GLIDE_MS)) }
            }
        }
    }

    /**
     * The finger came up (or the gesture was cancelled): the row drops; [onMoved] the new order.
     */
    fun drop(onMoved: (List<String>) -> Unit) {
        val id = dragging ?: return
        val before = tops(order, ::height, gap)
        val next = moved(order, from, to)
        val after = tops(next, ::height, gap)
        // every row starts where it is drawn now, from its new place, and glides home
        for (row in order) {
            val start = (before[row] ?: 0f) + offset(row) - (after[row] ?: 0f)
            val g = glide(row)
            val s = shift(row)
            now {
                s.snapTo(0f)
                g.snapTo(start)
                g.animateTo(0f, motion(if (row == id) SETTLE_MS else GLIDE_MS))
            }
        }
        val changed = next != order
        drops++
        dy = 0f
        dragging = null
        order = next
        scope.launch {
            lift.animateTo(0f, motion(SETTLE_MS))
            if (dragging == null) raised = null
        }
        if (changed) {
            onMoved(next)
            buzz(EditorBuzz.Drop)
        }
    }

    private fun motion(ms: Int): AnimationSpec<Float> = tween(if (reduced) 0 else ms, 0, EditorEase)

    /** Runs [block] at once up to its first suspension, so a snap lands before the next frame. */
    private fun now(block: suspend CoroutineScope.() -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED, block = block)
    }

    private companion object {
        const val GLIDE_MS = 260
        const val SETTLE_MS = 220
    }
}
