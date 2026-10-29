package com.lifemax.rpg

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import kotlin.math.roundToInt
import org.json.JSONObject

/**
 * Small native controls over the SAME resumed, hardware-accelerated WebView. The body remains live
 * DOM/canvas: no copies, screenshots or page-sized bitmap. Empty space in this container passes
 * touches through to the original WebView.
 */
internal class LiveOverviewShell(
    activity: Activity,
    private val web: WebView,
    private val chromeOnly: Boolean = false,
    activate: (String) -> Unit,
) {
    private val parent = activity.findViewById<ViewGroup>(android.R.id.content)
    private val frame =
        FrameLayout(activity).apply {
            tag = if (chromeOnly) "native-shared-chrome" else "overview-live-shell"
            importantForAccessibility =
                if (chromeOnly) View.IMPORTANT_FOR_ACCESSIBILITY_YES
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    private val header = OverviewSurface(activity) { id, _ -> activate(id) }
    private val bar = OverviewSurface(activity) { id, _ -> activate(id) }
    private val nav = HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled = false }
    private val scroll =
        ViewTreeObserver.OnScrollChangedListener { header.translationY = -web.scrollY.toFloat() }
    var sequence = 0
        private set

    private var first = true

    init {
        // Bound software layers to two small control strips. The WebView body
        // stays accelerated and owns native scrolling/fling and live animation.
        header.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        bar.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        // Preserve original header gestures and trusted browser activation. A
        // drag beginning over the header must still scroll the live WebView.
        if (!chromeOnly)
            header.setOnTouchListener { _, event ->
                val from = IntArray(2)
                val to = IntArray(2)
                header.getLocationOnScreen(from)
                web.getLocationOnScreen(to)
                val copy = MotionEvent.obtain(event)
                copy.offsetLocation((from[0] - to[0]).toFloat(), (from[1] - to[1]).toFloat())
                web.dispatchTouchEvent(copy)
                copy.recycle()
                true
            }
        nav.addView(bar)
        frame.addView(header, FrameLayout.LayoutParams(-1, 1))
        frame.addView(nav, FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM))
        parent.addView(frame, FrameLayout.LayoutParams(-1, -1))
        if (!chromeOnly) web.viewTreeObserver.addOnScrollChangedListener(scroll)
    }

    fun show(scene: JSONObject) {
        sequence = scene.optInt("sequence")
        val width = scene.getDouble("width").toFloat()
        val scale = (web.width / width).takeIf { it > 0 } ?: web.resources.displayMetrics.density
        val h = scene.getJSONObject("header")
        val n = scene.getJSONObject("nav")
        header.applyScene(
            OverviewLayer(h),
            width,
            h.getDouble("height").toFloat(),
            Color.TRANSPARENT,
        )
        header.layoutParams =
            FrameLayout.LayoutParams(
                -1,
                (h.getDouble("height") * scale).roundToInt().coerceAtLeast(1),
            )
        bar.applyScene(
            OverviewLayer(n),
            n.getDouble("width").toFloat(),
            n.getDouble("height").toFloat(),
            scene.optLong("background").toInt(),
            true,
        )
        nav.layoutParams =
            FrameLayout.LayoutParams(
                -1,
                (n.getDouble("height") * web.resources.displayMetrics.density)
                    .roundToInt()
                    .coerceAtLeast(1),
                Gravity.BOTTOM,
            )
        header.translationY = if (chromeOnly) 0f else -web.scrollY.toFloat()
        if (first) {
            first = false
            nav.post {
                nav.scrollTo(
                    (scene.optDouble("navScroll") * web.resources.displayMetrics.density)
                        .roundToInt(),
                    0,
                )
            }
        }
    }

    fun contentInsetsDp(): Pair<Float, Float> {
        val density = web.resources.displayMetrics.density
        return Pair(header.layoutParams.height / density, nav.layoutParams.height / density)
    }

    fun close() {
        if (!chromeOnly && web.viewTreeObserver.isAlive)
            web.viewTreeObserver.removeOnScrollChangedListener(scroll)
        parent.removeView(frame)
    }
}
