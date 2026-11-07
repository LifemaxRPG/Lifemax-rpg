package com.lifemax.rpg.feature.overview.presentation.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Local card positions only; the existing editor remains the layout save owner. */
internal class CardReorderState(
    initial: List<String>,
    private val list: LazyListState,
    private val scope: CoroutineScope,
) {
    var order by mutableStateOf(initial)
        private set

    var dragging by mutableStateOf<String?>(null)
        private set

    var raised by mutableStateOf<String?>(null)
        private set

    val lift = Animatable(0f)
    private val landing = Animatable(0f)
    private var landingStarted by mutableStateOf(false)
    private var top by mutableFloatStateOf(0f)
    private var grab by mutableFloatStateOf(0f)
    private var original = initial
    private var generation = 0

    private fun item(id: String) =
        list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "tile/$id" }

    fun sync(shown: List<String>) {
        if (dragging == null && raised == null) order = shown
    }

    fun start(id: String, fingerY: Float, reduced: Boolean): Boolean {
        if (raised != null) return false
        val measured = item(id) ?: return false
        original = order
        top = measured.offset.toFloat()
        grab = fingerY
        dragging = id
        raised = id
        landingStarted = false
        generation++
        scope.launch {
            if (reduced) lift.snapTo(1f) else lift.animateTo(1f, spring(stiffness = 600f))
        }
        return true
    }

    fun move(delta: Float): Boolean {
        top += delta
        return cross()
    }

    fun cross(): Boolean {
        val id = dragging ?: return false
        val own = item(id) ?: return false
        val visible =
            list.layoutInfo.visibleItemsInfo.filter {
                (it.key as? String)?.startsWith("tile/") == true
            }
        // Wait for a requested reorder to reach layout before considering another crossing.
        if (
            visible.map { (it.key as String).removePrefix("tile/") } !=
                order.filter { key -> visible.any { it.key == "tile/$key" } }
        )
            return false
        val centre = top + own.size / 2
        val other =
            visible
                .filter { it.key != own.key }
                .lastOrNull {
                    if (centre > own.offset + own.size / 2)
                        it.offset > own.offset && centre > it.offset + it.size / 2
                    else it.offset < own.offset && centre < it.offset + it.size / 2
                } ?: return false
        val from = order.indexOf(id)
        val to = order.indexOf((other.key as String).removePrefix("tile/"))
        if (from < 0 || to < 0 || from == to) return false
        order = moved(order, from, to)
        return true
    }

    fun offset(id: String): Float =
        when {
            id != raised -> 0f
            dragging != null || !landingStarted -> top - (item(id)?.offset ?: top.toInt())
            else -> landing.value
        }

    fun scrollStep(topEdge: Float, bottomEdge: Float, speed: Float): Float {
        if (dragging == null) return 0f
        val finger = top + grab - list.layoutInfo.viewportStartOffset
        return when {
            finger < topEdge -> -speed
            finger > bottomEdge -> speed
            else -> 0f
        }
    }

    fun end(cancelled: Boolean, reduced: Boolean, save: (List<String>) -> Unit) {
        val id = dragging ?: return
        dragging = null
        if (cancelled) order = original else if (order != original) save(order)
        val run = generation
        scope.launch {
            withFrameNanos {}
            withFrameNanos {}
            if (generation != run) return@launch
            landing.snapTo(top - (item(id)?.offset ?: top.toInt()))
            landingStarted = true
            launch {
                if (reduced) lift.snapTo(0f) else lift.animateTo(0f, spring(stiffness = 500f))
            }
            if (reduced) landing.snapTo(0f)
            else landing.animateTo(0f, spring(dampingRatio = .82f, stiffness = 400f))
            if (generation == run) {
                raised = null
                landingStarted = false
            }
        }
    }
}

/** Replace only visible slots; hidden/unavailable cards keep their saved slots. */
internal fun mergeCardOrder(full: List<String>, visible: List<String>): List<String> {
    require(visible.distinct().size == visible.size && visible.all { it in full })
    val shown = visible.toSet()
    val next = visible.iterator()
    return full.map { if (it in shown) next.next() else it }
}
