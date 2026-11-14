package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lifemax.rpg.feature.habits.domain.Building
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.Star
import com.lifemax.rpg.feature.habits.domain.skyline
import com.lifemax.rpg.feature.habits.domain.stars
import kotlin.math.sin

/**
 * Everything behind the towers: the sky of the hour with its stars, its sun or moon and its clouds,
 * two skylines that slide at different speeds as the towers move (depth), the haze at the horizon
 * and the ground. The sun, the moon and the clouds keep out of the heading's band.
 */
internal class Backdrop {
    private val starField: List<Star> = stars(54)
    private val far: List<Building> = skyline(5, 26, 64, -160f, 760f)
    private val near: List<Building> = skyline(23, 12, 36, -160f, 760f)

    /**
     * [horizon] is the y of the horizon in pixels, [top] the y under the heading's band, [orb]
     * where the sun or the moon stands, [shift] how far the towers have travelled sideways (in
     * towers), [seconds] the running time (0 when nothing moves).
     */
    fun DrawScope.backdrop(
        phase: SkyPhase,
        sky: SkyColors,
        horizon: Float,
        top: Float,
        orb: Offset,
        shift: Float,
        seconds: Float,
    ) {
        val d = density
        val at = (horizon / size.height).coerceIn(.05f, 1f)
        drawRect(
            Brush.verticalGradient(
                0f to sky.sky[0],
                at * .6f to sky.sky[1],
                at to sky.sky[2],
                1f to sky.sky[2],
            )
        )
        if (phase != SkyPhase.DAY) {
            val dim = if (phase == SkyPhase.NIGHT) 1f else .45f
            starField.forEachIndexed { i, s ->
                val twinkle =
                    if (s.twinkles && seconds > 0f) .55f + .45f * sin(seconds * 2.1f + i) else 1f
                drawCircle(
                    SceneInk.Star,
                    s.r * d,
                    Offset(s.x * size.width, s.y * horizon * .92f),
                    s.alpha * dim * twinkle,
                )
            }
        }
        val halo = (if (phase == SkyPhase.DAY) 40f else 50f) * d
        drawCircle(Brush.radialGradient(listOf(sky.halo, Color.Transparent), orb, halo), halo, orb)
        if (phase == SkyPhase.NIGHT) {
            drawCircle(sky.orb, 10f * d, orb)
            // the dark of the moon: a disc of sky over one side leaves a crescent
            drawCircle(sky.sky[1], 9f * d, orb + Offset(5f * d, -3f * d))
        } else drawCircle(sky.orb, 13f * d, orb)
        if (phase == SkyPhase.DAY) {
            // at rest one stands in the lane left of the tower in front, the other right of it
            val sky0 = top + 34f * d
            cloud(118f, sky0, 1f, .85f, seconds, 3.2f)
            cloud(
                size.width / d + 46f,
                sky0 + (horizon - sky0).coerceAtLeast(0f) * .4f,
                .7f,
                .7f,
                seconds,
                2.1f,
            )
        }
        row(far, sky.far, horizon, -shift * 9f, null)
        row(
            near,
            sky.near,
            horizon,
            -shift * 20f,
            if (phase == SkyPhase.DAY) null else SceneInk.CityLight,
        )
        val ground = horizon
        drawRect(
            Brush.verticalGradient(listOf(Color.Transparent, sky.haze), ground - 36f * d, ground),
            Offset(0f, ground - 36f * d),
            Size(size.width, 36f * d),
        )
        drawRect(
            Brush.verticalGradient(
                listOf(sky.ground[0], sky.ground[1]),
                ground,
                ground + (size.height - horizon) * .5f,
            ),
            Offset(0f, ground),
            Size(size.width, size.height - ground + 1f),
        )
        drawLine(sky.line, Offset(0f, ground), Offset(size.width, ground), 1f * d)
    }

    private fun DrawScope.row(
        buildings: List<Building>,
        color: Color,
        base: Float,
        slide: Float,
        lights: Color?,
    ) {
        val d = density
        buildings.forEachIndexed { i, b ->
            val x = (b.x + slide) * d
            if (x > size.width || x + b.w * d < 0f) return@forEachIndexed
            drawRect(color, Offset(x, base - b.h * d), Size(b.w * d + 1f, b.h * d + 60f * d))
            if (lights != null && i % 3 == 0)
                drawRect(
                    lights,
                    Offset(x + b.w * d * .3f, base - b.h * d * .6f),
                    Size(1.8f * d, 1.8f * d),
                    alpha = .75f,
                )
        }
    }

    private fun DrawScope.cloud(
        x0: Float,
        y: Float,
        scale: Float,
        alpha: Float,
        seconds: Float,
        speed: Float,
    ) {
        val d = density * scale
        val span = size.width + 160f * density
        val x = ((x0 * density + seconds * speed * density) % span) - 80f * density
        drawOval(SceneInk.Cloud, Offset(x - 34f * d, y - 11f * d), Size(68f * d, 22f * d), alpha)
        drawOval(SceneInk.Cloud, Offset(x - 30f * d, y - 19f * d), Size(32f * d, 22f * d), alpha)
        drawOval(SceneInk.Cloud, Offset(x - 10f * d, y - 25f * d), Size(40f * d, 28f * d), alpha)
    }
}
