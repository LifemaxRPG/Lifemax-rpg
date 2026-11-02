package com.lifemax.rpg.app.shell.swipe

import android.content.Context
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.AbstractComposeView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.lifemax.rpg.app.shell.ChromeHost

/**
 * A native tab's screen: a Compose host like ComposeView (which is final), that also decides
 * whether a gesture on it becomes a tab swipe (ADR 0011, who gets the gesture). [TabSwipe] moves it
 * and owns its visibility; the gesture is read here, before the screen's own content sees each
 * event:
 * - every event feeds the screen's [SwipeArbiter] first, in raw coordinates relative to the content
 *   root, since the screen itself moves under the finger;
 * - a child that takes the gesture (a scroller consuming movement, a `noTabSwipe` region) says so
 *   through [requestDisallowInterceptTouchEvent], and the arbiter lets the gesture go;
 * - once the arbiter claims, [onInterceptTouchEvent] takes the gesture from the children (they get
 *   ACTION_CANCEL) and [onTouchEvent] consumes the rest;
 * - while the screen moves with no finger on it (a settle, the wait for the page), a touch that
 *   starts on it is swallowed to its end;
 * - a touch that nothing on the open screen takes stops here ([onTouchEvent]), so it never reaches
 *   the WebView underneath, where the web swipe would start.
 */
internal class TabScreenView(context: Context, val pane: String) : AbstractComposeView(context) {
    private val content = mutableStateOf<(@Composable () -> Unit)?>(null)

    override var shouldCreateCompositionOnAttachedToWindow = false
        private set

    /** The screen's content, as ComposeView.setContent. */
    fun setContent(content: @Composable () -> Unit) {
        shouldCreateCompositionOnAttachedToWindow = true
        this.content.value = content
        if (isAttachedToWindow) createComposition()
    }

    /** How far the content is scrolled from its top, read from the scrolling it does (px). */
    private var scrolledPx = 0f
    private val scrollWatch =
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // a drag or fling toward the top that nothing could take: the content is at its top
                scrolledPx =
                    if (available.y > 0f && consumed.y >= 0f) 0f
                    else (scrolledPx - consumed.y).coerceAtLeast(0f)
                ChromeHost.scrolled(pane, scrolledPx)
                return Offset.Zero
            }
        }

    @Composable
    override fun Content() {
        Box(Modifier.nestedScroll(scrollWatch)) { content.value?.invoke() }
    }

    override fun getAccessibilityClassName(): CharSequence = javaClass.name

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val accessibility = context.getSystemService(AccessibilityManager::class.java)
    private var arbiter = SwipeArbiter(pane)
    private var velocity: VelocityTracker? = null
    private var swallowing = false

    /** The screen taking this gesture in this one's place (a chained swipe's catch), if any. */
    private var passTo: TabScreenView? = null

    /** The content root's place on the screen at the DOWN: raw coordinates are taken from it. */
    private var originX = 0f
    private var originY = 0f

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val ending = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL
        if (action == MotionEvent.ACTION_DOWN) {
            // a screen sliding beside the one a finger may catch passes the touch to it (A34)
            passTo = TabSwipe.catchHolder(pane)
            swallowing = passTo == null && TabSwipe.shields(pane)
        }
        passTo?.let { holder ->
            if (ending) passTo = null
            holder.dispatchTouchEvent(event)
            return true
        }
        if (swallowing) {
            TabSwipe.stray(event)
            if (ending) swallowing = false
            return true
        }
        follow(event)
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean = arbiter.consumes

    override fun requestDisallowInterceptTouchEvent(disallow: Boolean) {
        if (disallow) take(arbiter.childClaimed(), System.nanoTime())
        super.requestDisallowInterceptTouchEvent(disallow)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean =
        arbiter.consumes || (event.actionMasked == MotionEvent.ACTION_DOWN && TabSwipe.isOpen(pane))

    /** TabSwipe no longer follows this screen's gesture (a page open, `on:false`): let it go. */
    fun yieldGesture() {
        arbiter = arbiter.yieldGesture()
    }

    private fun follow(event: MotionEvent) {
        val now = event.eventTime * NANOS_PER_MILLI
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val root = parent as? View ?: this
                val at = IntArray(2).also(root::getLocationOnScreen)
                originX = at[0].toFloat()
                originY = at[1].toFloat()
                release()
                // a screen still sliding is caught where it stands; any other starts a lock
                val step =
                    if (TabSwipe.catching(pane))
                        arbiter.catchDown(x(event), facts(event, root), TabSwipe.tune)
                    else
                        arbiter.down(
                            x(event),
                            y(event),
                            facts(event, root),
                            TabSwipe.gate(pane),
                            TabSwipe.tune,
                        )
                if (step.input is SwipeInput.Arm || step.input is SwipeInput.Catch)
                    velocity = VelocityTracker.obtain().also { it.addRaw(event) }
                take(step, now)
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addRaw(event)
                take(arbiter.move(x(event), y(event)), now)
            }
            MotionEvent.ACTION_POINTER_DOWN -> take(arbiter.pointerDown(), now)
            MotionEvent.ACTION_UP -> {
                val tracker = velocity?.apply { addRaw(event) }
                tracker?.computeCurrentVelocity(MILLIS_PER_SECOND)
                take(arbiter.up(tracker?.xVelocity ?: 0f), now)
                release()
            }
            MotionEvent.ACTION_CANCEL -> {
                take(arbiter.cancel(), now)
                release()
            }
        }
    }

    private fun take(step: ArbiterStep, now: Long) {
        arbiter = step.arbiter
        step.input?.let { TabSwipe.gesture(it, now) }
    }

    private fun release() {
        velocity?.recycle()
        velocity = null
    }

    private fun x(event: MotionEvent) = event.rawX - originX

    private fun y(event: MotionEvent) = event.rawY - originY

    /** The platform's half of the gates at a DOWN. */
    private fun facts(event: MotionEvent, root: View): TouchFacts {
        val insets = ViewCompat.getRootWindowInsets(this)
        val back = insets?.getInsets(WindowInsetsCompat.Type.systemGestures())
        return TouchFacts(
            widthPx = root.width,
            density = resources.displayMetrics.density,
            touchSlopPx = touchSlop,
            pointers = TabSwipe.fingers(event.pointerCount),
            tool =
                when (event.getToolType(0)) {
                    MotionEvent.TOOL_TYPE_FINGER -> ToolKind.Finger
                    MotionEvent.TOOL_TYPE_STYLUS -> ToolKind.Stylus
                    MotionEvent.TOOL_TYPE_MOUSE -> ToolKind.Mouse
                    else -> ToolKind.Other
                },
            imeVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true,
            touchExploration = accessibility?.isTouchExplorationEnabled == true,
            windowFocused = hasWindowFocus(),
            gestureLeftPx = back?.left ?: 0,
            gestureRightPx = back?.right ?: 0,
        )
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
        const val MILLIS_PER_SECOND = 1000

        /** The finger's velocity in screen coordinates, past samples included. */
        fun VelocityTracker.addRaw(event: MotionEvent) {
            val raw = MotionEvent.obtain(event)
            raw.offsetLocation(event.rawX - event.x, event.rawY - event.y)
            addMovement(raw)
            raw.recycle()
        }
    }
}
