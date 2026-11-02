package com.lifemax.rpg.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import app.cash.paparazzi.SnapshotHandler
import java.lang.reflect.Proxy

/**
 * What TalkBack is given for a screen: the merged semantics tree Compose builds for it, read in the
 * order it is traversed, with what a screen reader says (the text or description, the role, the
 * state), what it can do (click, custom actions) and the touch area it is given in dp. Paparazzi
 * draws one frame and this module has no Compose test rule, so the tree is read straight from the
 * view's semantics owner once the frame is laid out.
 */
data class A11yNode(
    val depth: Int,
    val text: String,
    val role: Role?,
    val heading: Boolean,
    val selected: Boolean?,
    val toggle: ToggleableState?,
    val state: String?,
    val live: Boolean,
    val clickable: Boolean,
    val clickLabel: String?,
    val actions: List<String>,
    val expands: String?,
    val widthDp: Float,
    val heightDp: Float,
    val topDp: Float,
) {
    /** One readable line, for a failure message and the dump. */
    override fun toString() = buildString {
        append("  ".repeat(depth))
        append('"').append(text).append('"')
        role?.let { append(" role=").append(it) }
        if (heading) append(" heading")
        selected?.let { append(" selected=").append(it) }
        toggle?.let { append(" toggle=").append(it) }
        state?.let { append(" state=\"").append(it).append('"') }
        if (live) append(" live")
        if (clickable) append(" click").append(clickLabel?.let { "(\"$it\")" } ?: "")
        if (actions.isNotEmpty()) append(" actions=").append(actions)
        expands?.let { append(" ").append(it) }
        append(" ${"%.0f".format(widthDp)}x${"%.0f".format(heightDp)}@${"%.0f".format(topDp)}")
    }
}

/**
 * The frames nobody keeps: the probe only reads the semantics, it writes no reference image. The
 * frame handler is a proxy, because its method takes a java.awt image this module's classpath (the
 * Android one) does not have.
 */
object NoImages : SnapshotHandler {
    override fun newFrameHandler(
        snapshot: app.cash.paparazzi.Snapshot,
        frameCount: Int,
        fps: Int,
    ): SnapshotHandler.FrameHandler =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(SnapshotHandler.FrameHandler::class.java),
        ) { _, _, _ ->
            null
        } as SnapshotHandler.FrameHandler

    override fun close() {}
}

/** Reads the tree of whatever [content] draws; the last read (after the last layout) is kept. */
class SemanticsProbe {
    var nodes: List<A11yNode> = emptyList()
        private set

    @Composable
    fun Watch(content: @Composable () -> Unit) {
        // the view Compose hosts the content in; its semantics owner is not public API
        val view = LocalView.current
        val semantics = view.javaClass.getMethod("getSemanticsOwner")
        val density = LocalDensity.current.density
        androidx.compose.foundation.layout.Box(
            Modifier.onGloballyPositioned {
                nodes = buildList {
                    walk(
                        (semantics.invoke(view) as SemanticsOwner).rootSemanticsNode,
                        0,
                        density,
                        this,
                    )
                }
            }
        ) {
            content()
        }
    }

    private fun walk(n: SemanticsNode, depth: Int, density: Float, out: MutableList<A11yNode>) {
        val c = n.config
        val description = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        val words = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        // both are kept: a node with a description and words is read by TalkBack from both
        val said = listOfNotNull(words, description?.let { "[$it]" }).joinToString(" ")
        val actions = c.getOrNull(SemanticsActions.CustomActions)?.map { it.label }.orEmpty()
        val clickable = c.contains(SemanticsActions.OnClick)
        val b = n.touchBoundsInRoot
        val speaks =
            said.isNotEmpty() ||
                clickable ||
                c.contains(SemanticsActions.Expand) ||
                c.contains(SemanticsActions.Collapse) ||
                actions.isNotEmpty() ||
                c.contains(SemanticsProperties.Heading)
        if (speaks && !c.contains(SemanticsProperties.InvisibleToUser))
            out +=
                A11yNode(
                    depth,
                    said,
                    c.getOrNull(SemanticsProperties.Role),
                    c.contains(SemanticsProperties.Heading),
                    c.getOrNull(SemanticsProperties.Selected),
                    c.getOrNull(SemanticsProperties.ToggleableState),
                    c.getOrNull(SemanticsProperties.StateDescription),
                    c.contains(SemanticsProperties.LiveRegion),
                    clickable,
                    c.getOrNull(SemanticsActions.OnClick)?.label,
                    actions,
                    when {
                        c.contains(SemanticsActions.Collapse) -> "collapse"
                        c.contains(SemanticsActions.Expand) -> "expand"
                        else -> null
                    },
                    b.width / density,
                    b.height / density,
                    b.top / density,
                )
        n.children.forEach { walk(it, depth + (if (speaks) 1 else 0), density, out) }
    }
}
