package com.lifemax.rpg.app.shell

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.view.doOnPreDraw
import com.lifemax.rpg.app.shell.swipe.TabScreenView
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Card-to-tab reveal, scoped to an explicit Overview navigation. Main thread only. */
internal object OverviewLaunch {
    private var pending: Cover? = null

    fun begin(
        source: TabScreenView,
        destination: String,
        top: Float,
        bottom: Float,
        reduced: Boolean,
    ) {
        cancel()
        if (reduced || destination == "dashboard" || source.width == 0 || source.height == 0) return
        if (Build.VERSION.SDK_INT >= 26 && !ValueAnimator.areAnimatorsEnabled()) return
        val origin = source.takeNavigationOrigin() ?: return
        val root = source.parent as? ViewGroup ?: return
        val factor = min(1f, 720f / source.width)
        val image =
            try {
                Bitmap.createBitmap(
                    max(1, (source.width * factor).toInt()),
                    max(1, (source.height * factor).toInt()),
                    Bitmap.Config.ARGB_8888,
                )
            } catch (_: OutOfMemoryError) {
                return
            }
        try {
            val canvas = Canvas(image)
            canvas.scale(factor, factor)
            source.draw(canvas)
        } catch (_: Exception) {
            image.recycle()
            return
        }
        val density = source.resources.displayMetrics.density
        val cover =
            Cover(source, image, destination, origin.x, origin.y, top * density, bottom * density)
        pending = cover
        root.addView(cover, ViewGroup.LayoutParams(-1, -1))
        // Classic web destinations have no Compose open callback. Give their navigation
        // time to draw, then reveal through the same cover rather than flashing the page.
        cover.postDelayed(cover.fallback, 500)
        cover.postDelayed(cover.expiry, 1800)
    }

    fun arrived(screen: View, destination: String) {
        val cover = pending ?: return
        if (cover.destination != destination) {
            cancel()
            return
        }
        screen.doOnPreDraw { screen.postOnAnimation { if (pending === cover) cover.start() } }
        cover.bringToFront()
    }

    fun cancel() {
        pending?.finish()
    }

    private class Cover(
        source: View,
        private val image: Bitmap,
        val destination: String,
        private val originX: Float,
        private val originY: Float,
        private val contentTop: Float,
        private val contentBottom: Float,
    ) : View(source.context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private val mask = Path()
        private val bounds = RectF()
        private var progress = 0f
        private var revealAnimator: ValueAnimator? = null
        private var finished = false
        val fallback = Runnable { if (pending === this) start() }
        val expiry = Runnable { finish() }

        init {
            isClickable = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        fun start() {
            if (finished || revealAnimator != null || !isAttachedToWindow) return
            removeCallbacks(fallback)
            revealAnimator =
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 560
                    interpolator = PathInterpolator(.22f, .8f, .15f, 1f)
                    addUpdateListener {
                        progress = it.animatedValue as Float
                        invalidate()
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator) {
                                finish()
                            }
                        }
                    )
                    start()
                }
        }

        @Suppress("DEPRECATION")
        override fun onDraw(canvas: Canvas) {
            val save = canvas.save()
            canvas.clipRect(
                0f,
                contentTop,
                width.toFloat(),
                (height - contentBottom).coerceAtLeast(contentTop),
            )
            val reach =
                max(
                    max(
                        hypot(originX, originY - contentTop),
                        hypot(width - originX, originY - contentTop),
                    ),
                    max(
                        hypot(originX, height - contentBottom - originY),
                        hypot(width - originX, height - contentBottom - originY),
                    ),
                )
            if (progress > 0f) {
                mask.reset()
                mask.addCircle(
                    originX,
                    originY.coerceIn(contentTop, max(contentTop, height - contentBottom)),
                    reach * progress,
                    Path.Direction.CW,
                )
                canvas.clipPath(mask, Region.Op.DIFFERENCE)
            }
            paint.alpha = (255 * (1f - progress * .35f)).toInt()
            bounds.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(image, null, bounds, paint)
            canvas.restoreToCount(save)
        }

        fun finish() {
            if (finished) return
            finished = true
            removeCallbacks(fallback)
            removeCallbacks(expiry)
            revealAnimator?.cancel()
            (parent as? ViewGroup)?.removeView(this)
            if (pending === this) pending = null
            // Let the detached display list release its bitmap; recycling here could race the
            // render thread.
        }

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            finish()
        }
    }
}
