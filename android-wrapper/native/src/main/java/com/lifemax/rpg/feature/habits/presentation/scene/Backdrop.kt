package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Building
import com.lifemax.rpg.feature.habits.domain.Pass
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.Star
import com.lifemax.rpg.feature.habits.domain.skyTone
import com.lifemax.rpg.feature.habits.domain.starsShown

/**
 * Everything behind the street, and alive: the sky of the hour with its twinkling stars (and now
 * and then a shooting one), its sun or moon and its drifting clouds; an airplane, birds, a blimp;
 * three rows of far city that slide at their own speeds as the street scrolls (depth), with their
 * windows going on and off at night and red warning lights on the tallest; the glow over the
 * horizon; a car on the far road. Every moving thing is a function of [seconds] and the [seed]
 * ([Ambient]); at 0 seconds (motion reduced, or the tab hidden) it is a still picture with nothing
 * passing. Nothing is made per frame: the passes, paths and brushes are kept here.
 */
internal class Backdrop(private val seed: Int) {
    private val starField: Array<Star> = Ambient.stars(72, seed).toTypedArray()
    private val rows: Array<Array<Building>> =
        arrayOf(
            Ambient.skyline(5, 34, 84, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
            Ambient.skyline(23, 22, 58, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
            Ambient.skyline(41, 12, 38, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
        )

    /** How far each row slides for a street unit scrolled: the furthest least. */
    private val drift = floatArrayOf(.05f, .1f, .18f)

    /** Each far building's few windows, as (across, down) shares of it. */
    private val windows: Array<Array<FloatArray>> =
        Array(rows.size) { r ->
            Array(rows[r].size) { i ->
                val b = rows[r][i]
                val n = (b.h / 9f).toInt().coerceIn(1, 7)
                FloatArray(n * 2) { k ->
                    Ambient.hash(r * 1000 + i, k, 3).let { v ->
                        if (k % 2 == 0) .2f + .6f * v else .12f + .8f * v
                    }
                }
            }
        }

    /** The tallest of the furthest two rows carry the red lights. */
    private val warned: Array<BooleanArray> =
        Array(rows.size) { r ->
            val sorted =
                rows[r].indices.sortedByDescending { rows[r][it].h }.take(if (r < 2) 3 else 0)
            BooleanArray(rows[r].size) { it in sorted }
        }

    private val pass = Pass()
    private val path = Path()

    private var skyBrush: Brush? = null
    private var glowBrush: Brush? = null
    private var haloBrush: Brush? = null
    private var brushKey: Any? = null
    private var brushH = 0f
    private var brushOrb = Offset.Zero

    /**
     * [horizon] is the y of the horizon in pixels, [top] the y under the heading's band, [orb]
     * where the sun or the moon stands, [shift] how far along the street the view stands (street
     * units), [seconds] the running time (0: a still picture).
     */
    fun DrawScope.backdrop(
        hour: Float,
        sky: SkyColors,
        horizon: Float,
        top: Float,
        orb: Offset,
        shift: Float,
        seconds: Float,
    ) {
        val d = density
        val w = size.width
        brushes(sky, horizon, orb)
        drawRect(skyBrush!!)
        val tone = skyTone(hour)
        val starAlpha = starsShown(tone)
        val band = (horizon - top).coerceAtLeast(40f * d)
        if (starAlpha > .01f) {
            for (i in starField.indices) {
                val s = starField[i]
                drawCircle(
                    SceneInk.Star,
                    s.r * d,
                    Offset(s.x * w, s.y * horizon * .9f),
                    s.alpha * starAlpha * Ambient.twinkle(s, seconds),
                )
            }
            if (starAlpha > .6f && Ambient.shootingStar(seconds, seed, pass))
                shootingStar(pass, w, top, band, starAlpha)
        }
        drawCircle(haloBrush!!, (if (sky.phase == SkyPhase.DAY) 40f else 50f) * d, orb)
        if (sky.phase == SkyPhase.NIGHT) {
            drawCircle(sky.orb, 10f * d, orb)
            // the dark of the moon: a disc of sky over one side leaves a crescent
            drawCircle(sky.sky[1], 9f * d, orb + Offset(5f * d, -3f * d))
        } else drawCircle(sky.orb, 13f * d, orb)
        clouds(sky, w, top, band, seconds)
        if (tone > 1.6f && Ambient.blimp(seconds, seed, pass)) blimp(pass, w, top, band)
        if (Ambient.plane(seconds, seed, pass)) plane(pass, sky, w, top, band, seconds, tone)
        for (r in rows.indices) row(r, sky, horizon, shift * drift[r], seconds)
        if (tone >= .7f && Ambient.birds(seconds, seed, pass)) birds(pass, w, top, band, seconds)
        // the glow over the horizon: the city's light at night, haze by day
        drawRect(glowBrush!!, Offset(0f, horizon - 60f * d), Size(w, 60f * d))
        drawRect(sky.ground[0], Offset(0f, horizon), Size(w, size.height - horizon))
        drawLine(sky.line, Offset(0f, horizon), Offset(w, horizon), d)
        if (Ambient.car(seconds, seed, pass)) car(pass, sky, w, horizon)
    }

    private fun DrawScope.brushes(sky: SkyColors, horizon: Float, orb: Offset) {
        if (brushKey === sky && brushH == horizon && brushOrb == orb && skyBrush != null) return
        val at = horizon
        skyBrush =
            Brush.verticalGradient(
                0f to sky.sky[0],
                .6f to sky.sky[1],
                1f to sky.sky[2],
                startY = 0f,
                endY = at,
            )
        glowBrush =
            Brush.verticalGradient(
                listOf(Color.Transparent, sky.glow),
                startY = horizon - 60f * density,
                endY = horizon,
            )
        haloBrush =
            Brush.radialGradient(
                listOf(sky.halo, Color.Transparent),
                orb,
                (if (sky.phase == SkyPhase.DAY) 40f else 50f) * density,
            )
        brushKey = sky
        brushH = horizon
        brushOrb = orb
    }

    private fun DrawScope.shootingStar(p: Pass, w: Float, top: Float, band: Float, alpha: Float) {
        val d = density
        val x = p.x * w
        val y = top + p.y * band
        val fade = (if (p.t < .15f) p.t / .15f else 1f - (p.t - .15f) / .85f) * alpha
        // a bright head and a tail in three fading pieces, back along the way it came
        val dx = -p.dir * 22f * d
        val dy = -12f * d
        for (k in 0 until 3) {
            drawLine(
                SceneInk.Star,
                Offset(x + dx * k, y + dy * k),
                Offset(x + dx * (k + 1), y + dy * (k + 1)),
                (1.6f - k * .4f) * d,
                cap = StrokeCap.Round,
                alpha = fade * (1f - k * .3f),
            )
        }
        drawCircle(SceneInk.Strobe, 1.6f * d, Offset(x, y), fade)
    }

    private fun DrawScope.clouds(
        sky: SkyColors,
        w: Float,
        top: Float,
        band: Float,
        seconds: Float,
    ) {
        val d = density
        val night = sky.dark > .8f
        val count = if (night) 2 else 3
        for (i in 0 until count) {
            val x = Ambient.cloudX(i, seconds, seed) * w
            val y = top + band * (if (night) .1f + .14f * i else .1f + .2f * i)
            if (night) {
                // thin streaks that pass over the moon
                drawOval(sky.cloud, Offset(x - 50f * d, y - 3f * d), Size(100f * d, 6f * d), .45f)
                drawOval(sky.cloud, Offset(x - 22f * d, y - 6f * d), Size(60f * d, 7f * d), .35f)
            } else {
                val s = d * (1f - .2f * i)
                val a = if (sky.phase == SkyPhase.DAY) .9f - .12f * i else .7f
                drawOval(sky.cloud, Offset(x - 34f * s, y - 11f * s), Size(68f * s, 22f * s), a)
                drawOval(sky.cloud, Offset(x - 30f * s, y - 19f * s), Size(32f * s, 22f * s), a)
                drawOval(sky.cloud, Offset(x - 10f * s, y - 25f * s), Size(40f * s, 28f * s), a)
            }
        }
    }

    private fun DrawScope.blimp(p: Pass, w: Float, top: Float, band: Float) {
        val d = density
        val x = p.x * w
        val y = top + p.y * band
        drawOval(SceneInk.Blimp, Offset(x - 22f * d, y - 7f * d), Size(44f * d, 14f * d))
        drawOval(Color(0x22000000), Offset(x - 20f * d, y), Size(40f * d, 6f * d))
        drawRect(SceneInk.Plane, Offset(x - 4f * d, y + 6f * d), Size(8f * d, 3f * d))
        val tail = x - p.dir * 21f * d
        path.rewind()
        path.moveTo(tail, y)
        path.lineTo(tail - p.dir * 6f * d, y - 7f * d)
        path.lineTo(tail - p.dir * 6f * d, y + 7f * d)
        path.close()
        drawPath(path, SceneInk.Blimp)
    }

    private fun DrawScope.plane(
        p: Pass,
        sky: SkyColors,
        w: Float,
        top: Float,
        band: Float,
        seconds: Float,
        tone: Float,
    ) {
        val d = density
        val x = p.x * w
        val y = top + p.y * band
        val back = -p.dir
        if (tone > 1.3f) {
            // a contrail behind it, fading the further back it goes
            for (k in 0 until 4) drawLine(
                SceneInk.Contrail,
                Offset(x + back * (8f + 40f * k) * d, y + k * 1.2f * d),
                Offset(x + back * (8f + 40f * (k + 1)) * d, y + (k + 1) * 1.2f * d),
                (1.6f + k * .5f) * d,
                alpha = .55f - k * .12f,
            )
            drawLine(
                SceneInk.PlaneDay,
                Offset(x - 5f * d, y),
                Offset(x + 5f * d, y),
                2f * d,
                cap = StrokeCap.Round,
            )
            drawLine(
                SceneInk.PlaneDay,
                Offset(x, y),
                Offset(x + back * 2f * d, y + 3f * d),
                1.2f * d,
            )
        } else {
            // at night only its lights: red and green at the wingtips, a white strobe
            drawLine(sky.far[0], Offset(x - 5f * d, y), Offset(x + 5f * d, y), 1.6f * d)
            drawCircle(SceneInk.Warning, 1.3f * d, Offset(x - 4f * d * p.dir, y + 1f * d))
            drawCircle(SceneInk.NavGreen, 1.3f * d, Offset(x + 4f * d * p.dir, y + 1f * d))
            val strobe = Ambient.warning(7, seconds * 1.4f)
            if (strobe > 0f) drawCircle(SceneInk.Strobe, 1.6f * d, Offset(x, y), strobe)
        }
    }

    private fun DrawScope.birds(p: Pass, w: Float, top: Float, band: Float, seconds: Float) {
        val d = density
        val x0 = p.x * w
        val y0 = top + p.y * band
        for (i in 0 until Ambient.FLOCK) {
            val x = x0 + Ambient.birdX(i, p.dir) * d
            val y = y0 + Ambient.birdY(i) * d
            val flap = Ambient.wing(i, seconds) * 2.6f * d
            drawLine(
                SceneInk.Bird,
                Offset(x - 4f * d, y - flap),
                Offset(x, y),
                1.2f * d,
                cap = StrokeCap.Round,
            )
            drawLine(
                SceneInk.Bird,
                Offset(x, y),
                Offset(x + 4f * d, y - flap),
                1.2f * d,
                cap = StrokeCap.Round,
            )
        }
    }

    private fun DrawScope.row(r: Int, sky: SkyColors, base: Float, slide: Float, seconds: Float) {
        val d = density
        val w = size.width / d
        val color = sky.far[r]
        val buildings = rows[r]
        val lights = sky.dark > .3f
        val share = if (sky.dark > .8f) .42f else .25f
        for (i in buildings.indices) {
            val b = buildings[i]
            // the row repeats along the street; each building stands where its turn puts it
            var x = Ambient.wrap(b.x, slide)
            while (x < w + b.w) {
                if (x + b.w >= 0f) {
                    val px = x * d
                    val top = base - b.h * d
                    drawRect(color, Offset(px, top), Size(b.w * d + 1f, b.h * d + 1f))
                    if (lights && r > 0) {
                        val win = windows[r][i]
                        for (k in 0 until win.size / 2) {
                            if (!Ambient.farWindowLit(r * 1000 + i, k, seconds, share)) continue
                            drawRect(
                                SceneInk.FarLight,
                                Offset(px + win[k * 2] * b.w * d, top + win[k * 2 + 1] * b.h * d),
                                Size(1.7f * d, 1.7f * d),
                                alpha = .5f + .3f * sky.dark,
                            )
                        }
                    }
                    if (warned[r][i] && sky.dark > .3f) {
                        val on = if (seconds > 0f) Ambient.warning(r * 100 + i, seconds) else .7f
                        if (on > 0f) {
                            val at = Offset(px + b.w * d / 2f, top - 1.5f * d)
                            drawCircle(SceneInk.Warning, 4f * d, at, .18f * on)
                            drawCircle(SceneInk.Warning, 1.4f * d, at, on)
                        }
                    }
                }
                x += Ambient.SKYLINE_SPAN
            }
        }
    }

    private fun DrawScope.car(p: Pass, sky: SkyColors, w: Float, horizon: Float) {
        val d = density
        val x = p.x * w
        val y = horizon + 3f * d
        if (sky.dark > .3f) {
            val front = x + p.dir * 3f * d
            drawCircle(SceneInk.Headlight, 4f * d, Offset(front, y), .2f)
            drawCircle(SceneInk.Headlight, 1.2f * d, Offset(front, y))
            drawCircle(SceneInk.Taillight, 1f * d, Offset(x - p.dir * 3f * d, y))
        } else drawRect(sky.far[2], Offset(x - 4f * d, y - 2f * d), Size(8f * d, 3f * d))
    }
}
