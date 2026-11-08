package com.lifemax.rpg.app.shell.bar

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView

/**
 * The bottom bar's strip: it scrolls sideways when the tabs do not fit, but the finger does not
 * scroll it. A slide along it is a scrub through the tabs ([BarScrubber]), which scrolls the strip
 * only as far as it takes to keep the tab under the finger in view. A tap goes to the tab's button
 * as before. [onOpen] is called with the id of a tab to open; [onHighlight] is told which tab to
 * show as selected ahead of the page (null: the page's own).
 */
internal class ScrubBar(
    context: Context,
    private val onOpen: (String) -> Unit,
    private val onHighlight: (BarTab?) -> Unit,
) : HorizontalScrollView(context), ScrubHost {
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val slots = HashMap<Int, Runnable>()
    private var scrollMotion: ValueAnimator? = null
    private var scrollTarget = Float.NaN
    private val scrubber = BarScrubber(this)

    /** The page's word on scrubbing; set with each bar scene. */
    var scene: BarScrubScene?
        get() = scrubber.scene
        set(value) {
            scrubber.scene = value
        }

    /** Whether a scrub is under way (the page's own scroll position is not followed meanwhile). */
    val scrubbing: Boolean
        get() = scrubber.scrubbing

    /** A new bar scene is up, with the page on [active]. */
    fun sceneArrived(active: String) = scrubber.sceneArrived(active)

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> scrubber.down(event.x / density, event.y / density)
            MotionEvent.ACTION_MOVE ->
                if (scrubber.move(event.x / density, event.y / density, event.eventTime)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> scrubber.up()
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE ->
                scrubber.move(event.x / density, event.y / density, event.eventTime)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> scrubber.up()
        }
        return true
    }

    /**
     * Size the strip for a bar [barPx] tall with [bleedPx] of its own colour below it: the bar sits
     * at the bottom of its frame, the tabs in the top [barPx] and the colour running on to the
     * frame's edge through the gesture area.
     */
    fun fit(barPx: Int, bleedPx: Int) {
        if (paddingBottom == bleedPx && (layoutParams?.height ?: -1) == barPx + bleedPx) return
        setPadding(0, 0, 0, bleedPx)
        layoutParams = FrameLayout.LayoutParams(-1, barPx + bleedPx, Gravity.BOTTOM)
    }

    // ------------------------------------------------------------------ the scrubber's bar

    override val viewport: Float
        get() = width / density

    override val scroll: Float
        get() = scrollX / density

    override val maxScroll: Float
        get() = ((getChildAt(0)?.width ?: 0) - width).coerceAtLeast(0) / density

    override fun scrollToDp(dp: Float) {
        if (dp == scrollTarget && scrollMotion?.isRunning == true) return
        scrollTarget = dp
        scrollMotion?.cancel()
        scrollMotion =
            ValueAnimator.ofInt(scrollX, (dp * density).toInt()).apply {
                duration = 110L
                interpolator = DecelerateInterpolator()
                addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
                start()
            }
    }

    override fun open(id: String) = onOpen(id)

    override fun buzz(kind: ScrubBuzz) {
        val strength = scene?.hapticStrength ?: 50
        if (strength <= 0) return
        val motor =
            if (Build.VERSION.SDK_INT >= 31) {
                (this.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                        as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                (this.context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
            }
        if (
            Build.VERSION.SDK_INT >= 26 &&
                motor?.hasVibrator() == true &&
                motor.hasAmplitudeControl()
        ) {
            val amplitude = (strength * 254 / 100 + 1).coerceIn(1, 255)
            val duration =
                when (kind) {
                    ScrubBuzz.Tick -> 7L
                    ScrubBuzz.Start -> 12L
                    ScrubBuzz.End -> 15L
                }
            motor.vibrate(VibrationEffect.createOneShot(duration, amplitude))
            return
        }
        val modern = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val constant =
            when (kind) {
                ScrubBuzz.Tick -> HapticFeedbackConstants.CLOCK_TICK
                ScrubBuzz.Start ->
                    if (modern) HapticFeedbackConstants.GESTURE_START
                    else HapticFeedbackConstants.LONG_PRESS
                ScrubBuzz.End ->
                    if (modern) HapticFeedbackConstants.GESTURE_END
                    else HapticFeedbackConstants.VIRTUAL_KEY
            }
        // the page's NavHaptics: system haptics that do not depend on the touch-feedback setting
        @Suppress("DEPRECATION")
        performHapticFeedback(constant, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    override fun highlight(tab: BarTab?) = onHighlight(tab)

    override fun after(slot: Int, delayMs: Long, action: () -> Unit) {
        cancel(slot)
        val run = Runnable {
            slots.remove(slot)
            action()
        }
        slots[slot] = run
        handler.postAtTime(run, SystemClock.uptimeMillis() + delayMs)
    }

    override fun cancel(slot: Int) {
        slots.remove(slot)?.let(handler::removeCallbacks)
    }

    override fun onDetachedFromWindow() {
        scrollMotion?.cancel()
        slots.values.forEach(handler::removeCallbacks)
        slots.clear()
        super.onDetachedFromWindow()
    }
}
