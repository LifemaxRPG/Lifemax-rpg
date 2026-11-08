package com.lifemax.rpg.app.shell

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.FrameLayout
import com.lifemax.rpg.app.shell.bar.BarScrubScene
import com.lifemax.rpg.app.shell.bar.BarTab
import com.lifemax.rpg.app.shell.bar.ScrubBar
import com.lifemax.rpg.app.shell.bar.ScrubPill
import com.lifemax.rpg.app.shell.bar.ScrubPreview
import com.lifemax.rpg.app.shell.swipe.TabSwipe
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
        BarsFrame(activity).apply {
            tag = if (chromeOnly) "native-shared-chrome" else "overview-live-shell"
            importantForAccessibility =
                if (chromeOnly) View.IMPORTANT_FOR_ACCESSIBILITY_YES
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
    private val header =
        OverviewSurface(activity) { id, _ ->
            TabTiming.tap(id)
            activate(id)
        }
    private val bar =
        OverviewSurface(activity) { id, _ ->
            TabTiming.tap(id)
            activate(id)
        }
    private val preview = ScrubPreview(activity, web)
    private var pillMotion: ValueAnimator? = null
    private var drawnTab: BarTab? = null
    private val nav =
        ScrubBar(
            activity,
            onOpen = { id ->
                TabTiming.tap(id)
                activate(id)
            },
            onHighlight = ::showPill,
            onHold = { if (chromeOnly) ChromeHost.scrubHold(it) },
        )
    private val scroll =
        ViewTreeObserver.OnScrollChangedListener { header.translationY = -web.scrollY.toFloat() }
    var sequence = 0
        private set

    val root: View
        get() = frame

    private var first = true

    /**
     * NAV-5: the bottom bar goes while the keyboard is up, so it never sits over a field above the
     * keyboard
     */
    private val keyboard: (Boolean) -> Unit = { up ->
        nav.visibility = if (up) View.GONE else View.VISIBLE
        applyBleed()
    }

    private val bleedWatch =
        View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> applyBleed() }

    /**
     * The bottom bar's own height in px, without the strip of the same colour under it that reaches
     * the screen's bottom edge through the gesture area ([bleed]).
     */
    private var barPx = 1
    private var bleed = 0

    /** The tab a scrub shows as selected ahead of the page, by its key. */
    private var pillKey: String? = null

    /** The page's bar scroll last followed (px), and the glide toward it. */
    private var navScroll = 0
    private var glide: ValueAnimator? = null

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
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        frame.addView(header, FrameLayout.LayoutParams(-1, 1))
        frame.addView(nav, FrameLayout.LayoutParams(-1, 1, Gravity.BOTTOM))
        parent.addView(frame, FrameLayout.LayoutParams(-1, -1))
        // the bar's colour runs on under the gesture area to the very bottom of the screen
        parent.clipToPadding = false
        parent.addOnLayoutChangeListener(bleedWatch)
        applyBleed()
        if (!chromeOnly) web.viewTreeObserver.addOnScrollChangedListener(scroll)
        ImeState.listen(keyboard)
        keyboard(ImeState.up)
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
        barPx =
            (n.getDouble("height") * web.resources.displayMetrics.density)
                .roundToInt()
                .coerceAtLeast(1)
        preview.layoutParams =
            FrameLayout.LayoutParams(-1, -1).apply {
                topMargin = header.layoutParams.height
                bottomMargin = barPx + bleed
            }
        val scrub = BarScrubScene.parse(scene.optJSONObject("scrub"), n.optJSONArray("hits"))
        nav.scene = scrub
        nav.setBackgroundColor(
            scrub?.surface?.takeIf { Color.alpha(it) > 0 } ?: scene.optLong("background").toInt()
        )
        bar.selectedId = scrub?.tabs?.firstOrNull { it.key == scrub.active }?.id
        applyBleed()
        showPill(pillKey?.let { key -> scrub?.tabs?.firstOrNull { it.key == key } })
        nav.sceneArrived(scrub?.active.orEmpty())
        if (!chromeOnly) header.translationY = -web.scrollY.toFloat()
        follow(scene)
    }

    /**
     * The bar follows the page's: at once on the first scene; after that, while the page sends the
     * swipe state (the tab swipe switched on), it glides wherever the page's bar has moved since (a
     * tab changed, by a tap or a swipe), in 170 ms with a cubic ease-out as the page's own bar
     * does, at once with the page's motion off. A tab change alone is not enough: the destination's
     * scene is taken while the page's bar is still gliding, and the settled scene after it brings
     * where the bar ends.
     */
    private fun follow(scene: JSONObject) {
        val to = (scene.optDouble("navScroll") * web.resources.displayMetrics.density).roundToInt()
        val swipe = scene.optJSONObject("swipe")
        when {
            first -> nav.post { nav.scrollTo(to, 0) }
            nav.scrubbing -> Unit // the finger is moving the strip
            swipe == null || to == navScroll -> return
            swipe.optBoolean("motion", true) -> nav.post { glideTo(to) }
            else -> nav.post { nav.scrollTo(to, 0) }
        }
        first = false
        navScroll = to
    }

    private fun glideTo(to: Int) {
        glide?.cancel()
        glide =
            ValueAnimator.ofInt(nav.scrollX, to).apply {
                duration = GLIDE_MS
                interpolator = DecelerateInterpolator(1.5f)
                addUpdateListener { nav.scrollTo(it.animatedValue as Int, 0) }
                start()
            }
    }

    /**
     * A native screen scrolled [px] down from its top: the shared header goes up with it, as the
     * page's header scrolls with the page, until it is gone; it never floats over the content.
     */
    fun followScroll(px: Float) {
        if (!chromeOnly) return
        header.translationY = -px.coerceIn(0f, header.layoutParams.height.toFloat())
    }

    /** The bottom bar's height in px (the audio dock sits on top of it). */
    val navPx: Int
        get() = barPx

    fun contentInsetsDp(): Pair<Float, Float> {
        val density = web.resources.displayMetrics.density
        return Pair(header.layoutParams.height / density, barPx / density)
    }

    /**
     * The screen's gesture area below the content: the bar reaches down through it to the bottom
     * edge, with its tabs still above it, so the content's own insets (which stop at the gesture
     * area) stay as they are.
     */
    private fun applyBleed() {
        val inset = if (ImeState.up) 0 else parent.paddingBottom
        val lp = frame.layoutParams as FrameLayout.LayoutParams
        if (bleed != inset || lp.bottomMargin != -inset) {
            bleed = inset
            lp.bottomMargin = -inset
            frame.layoutParams = lp
        }
        nav.fit(barPx, inset)
    }

    /** A scrub shows the tab under the finger as selected before the page has switched to it. */
    private fun showPill(tab: BarTab?) {
        val previous = drawnTab
        val changed = pillKey != tab?.key
        pillKey = tab?.key
        preview.show(tab)
        if (changed) web.evaluateJavascript("window.lmaNativeBarScrubbing=" + (tab != null), null)
        fun draw(at: BarTab?) {
            val scrub = nav.scene
            drawnTab = at
            bar.scrubPill =
                if (at == null || scrub == null) null
                else
                    ScrubPill(
                        at,
                        scrub.tabs.firstOrNull { it.key == scrub.active },
                        scrub.pillColor,
                        scrub.pillRadius,
                    )
        }
        if (!changed && pillMotion?.isRunning == true) return
        pillMotion?.cancel()
        if (tab == null || previous == null || !changed) {
            draw(tab)
            return
        }
        pillMotion =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 110L
                interpolator = DecelerateInterpolator()
                addUpdateListener {
                    val t = it.animatedValue as Float
                    fun mix(a: Float, b: Float) = a + (b - a) * t
                    draw(
                        BarTab(
                            tab.key,
                            tab.id,
                            mix(previous.left, tab.left),
                            mix(previous.right, tab.right),
                            mix(previous.top, tab.top),
                            mix(previous.bottom, tab.bottom),
                        )
                    )
                }
                start()
            }
    }

    fun close() {
        pillMotion?.cancel()
        preview.show(null)
        web.evaluateJavascript("window.lmaNativeBarScrubbing=false", null)
        ImeState.forget(keyboard)
        parent.removeOnLayoutChangeListener(bleedWatch)
        glide?.cancel()
        if (!chromeOnly && web.viewTreeObserver.isAlive)
            web.viewTreeObserver.removeOnScrollChangedListener(scroll)
        parent.removeView(frame)
    }

    private companion object {
        /** The page's bar glide (lmaBarTo): 1 − (1 − t)³ over 170 ms. */
        const val GLIDE_MS = 170L
    }
}

/**
 * The frame that holds the bars, above every native screen. android.R.id.content splits touches, so
 * a finger here is never a pointer of the screen under it: the frame tells the tab swipe instead
 * (CONTRACT A34). A finger that lands during a swipe is a second finger ([TabSwipe.stray]); one
 * that cuts a drag short is swallowed to its end, so no tab of the bar acts mid-swipe. A finger
 * resting here counts at a screen's DOWN ([TabSwipe.touched]), so no swipe starts beside it.
 */
private class BarsFrame(context: Context) : FrameLayout(context) {
    private var swallowing = false

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) swallowing = TabSwipe.stray(event)
        val taken = swallowing || super.dispatchTouchEvent(event)
        when (action) {
            MotionEvent.ACTION_DOWN -> TabSwipe.touched(this, taken)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                TabSwipe.touched(this, false)
                swallowing = false
            }
        }
        return taken
    }

    override fun onDetachedFromWindow() {
        TabSwipe.touched(this, false)
        super.onDetachedFromWindow()
    }
}
