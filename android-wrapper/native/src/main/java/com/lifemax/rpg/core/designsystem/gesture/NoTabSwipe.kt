package com.lifemax.rpg.core.designsystem.gesture

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize

/**
 * A gesture that starts here is never a tab swipe: a text field (parity with the web, whose swipe
 * ignores INPUT and TEXTAREA) or a drag region that does not consume movement itself. On the down,
 * before anything else sees it, it tells the screen's View that a child has the gesture (the same
 * signal a scroller gives when it takes a drag).
 */
fun Modifier.noTabSwipe(): Modifier = this then NoTabSwipeElement

private data object NoTabSwipeElement : ModifierNodeElement<NoTabSwipeNode>() {
    override fun create() = NoTabSwipeNode()

    override fun update(node: NoTabSwipeNode) = Unit

    override fun InspectorInfo.inspectableProperties() {
        name = "noTabSwipe"
    }
}

private class NoTabSwipeNode :
    Modifier.Node(), PointerInputModifierNode, CompositionLocalConsumerModifierNode {
    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize,
    ) {
        if (
            pass == PointerEventPass.Initial &&
                pointerEvent.changes.any { it.changedToDownIgnoreConsumed() }
        )
            currentValueOf(LocalView).parent?.requestDisallowInterceptTouchEvent(true)
    }

    override fun onCancelPointerInput() = Unit
}
