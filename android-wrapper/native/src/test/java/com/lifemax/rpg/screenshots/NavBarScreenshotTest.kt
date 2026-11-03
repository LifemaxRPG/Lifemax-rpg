package com.lifemax.rpg.screenshots

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.app.shell.OverviewLayer
import com.lifemax.rpg.app.shell.OverviewSurface
import com.lifemax.rpg.app.shell.bar.BarScrubScene
import com.lifemax.rpg.app.shell.bar.ScrubBar
import com.lifemax.rpg.app.shell.bar.ScrubPill
import org.junit.Rule
import org.junit.Test

/**
 * The bottom bar as the phone draws it, at the bottom of a 411 dp phone with a 24 dp gesture area:
 * the bar's own colour runs flush to the very bottom edge through the gesture area (the handle
 * drawn on it as the system does), with the tab row above the handle. Normal, with a tab picked by
 * a scrub (the pill already on the tab under the finger), many tabs scrolled to the end where
 * Quests is, in the light theme, with the phone's text raised to 130 %, and with the 3-button bar's
 * 48 dp and none. The scenes are made by [BarSceneFixture] to the page's sizes.
 */
class NavBarScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(screenHeight = HEIGHT_PX, density = Density(420)),
            useDeviceResolution = true,
            maxPercentDifference = 0.001,
        )

    private val density = 420 / 160f

    private fun dp(v: Float) = (v * density).toInt()

    /** A stand-in for the screen above the bar: the page's colour with a few cards. */
    private class ScreenStandIn(context: Context, private val fixture: BarSceneFixture) :
        View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(fixture.bg.toInt())
            paint.color = if (fixture.light) 0xFFFFFFFF.toInt() else 0xFF161A22.toInt()
            val d = resources.displayMetrics.density
            for (k in 0 until 3) {
                val top = (12 + k * 78) * d
                canvas.drawRoundRect(
                    RectF(12 * d, top, width - 12 * d, top + 68 * d),
                    14 * d,
                    14 * d,
                    paint,
                )
            }
        }
    }

    /** The system's gesture handle over the gesture area. */
    private class GestureHandle(context: Context, private val light: Boolean) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            paint.color = if (light) 0xFF1A2330.toInt() else 0xFFE6E9F0.toInt()
            val r = height / 2f
            canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, paint)
        }
    }

    private fun shoot(
        fixture: BarSceneFixture,
        gestureDp: Float = 24f,
        scrollTo: Float = 0f,
        picking: Int = -1,
        label: String? = null,
    ) {
        val context = paparazzi.context
        val inset = dp(gestureDp)
        val barPx = dp(fixture.height)
        val surface = OverviewSurface(context) { _, _ -> }
        surface.applyScene(
            OverviewLayer(fixture.layer()),
            fixture.width,
            fixture.height,
            fixture.bg.toInt(),
            true,
        )
        val scrub = BarScrubScene.parse(fixture.scrub(), fixture.layer().getJSONArray("hits"))!!
        val nav = ScrubBar(context, {}, {})
        nav.addView(surface)
        nav.scene = scrub
        nav.setBackgroundColor(scrub.surface)
        nav.fit(barPx, inset)
        if (picking >= 0)
            surface.scrubPill =
                ScrubPill(
                    scrub.tabs[picking],
                    scrub.tabs[fixture.active],
                    scrub.pillColor,
                    scrub.pillRadius,
                )
        // the bars' frame reaches down through the gesture area, as the shell lays it out
        val frame = FrameLayout(context)
        frame.addView(nav)
        val content =
            FrameLayout(context).apply {
                setPadding(0, 0, 0, inset)
                clipToPadding = false
                addView(ScreenStandIn(context, fixture), FrameLayout.LayoutParams(-1, -1))
                addView(frame, FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = -inset })
            }
        val root = FrameLayout(context)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        if (gestureDp > 0)
            root.addView(
                GestureHandle(context, fixture.light),
                FrameLayout.LayoutParams(
                        dp(108f),
                        dp(4f),
                        Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    )
                    .apply { bottomMargin = dp(8f) },
            )
        if (scrollTo > 0) {
            val w = View.MeasureSpec.makeMeasureSpec(dp(411f), View.MeasureSpec.EXACTLY)
            val h = View.MeasureSpec.makeMeasureSpec(HEIGHT_PX, View.MeasureSpec.EXACTLY)
            root.measure(w, h)
            root.layout(0, 0, dp(411f), HEIGHT_PX)
            nav.scrollTo(dp(scrollTo), 0)
        }
        paparazzi.snapshot(root, label)
    }

    @Test fun normal() = shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 3))

    @Test
    fun scrubbingTowardsTheLibrary() =
        shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 3), scrollTo = 200f, picking = 6)

    @Test
    fun manyTabsScrolledToTheEnd() =
        shoot(BarSceneFixture(BarSceneFixture.MANY, active = 13), scrollTo = 2000f)

    @Test fun light() = shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 0, light = true))

    @Test
    fun lightScrubbing() =
        shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 0, light = true), picking = 2)

    @Test fun largeText() = shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 4, font = 13f))

    @Test
    fun withTheThreeButtonBar() =
        shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 1), gestureDp = 48f)

    @Test
    fun withNoGestureArea() =
        shoot(BarSceneFixture(BarSceneFixture.DEFAULT, active = 1), gestureDp = 0f)

    private companion object {
        /**
         * the picture's height in device pixels (taller than the phone is wide, or the device turns
         * landscape)
         */
        const val HEIGHT_PX = 1100
    }
}
