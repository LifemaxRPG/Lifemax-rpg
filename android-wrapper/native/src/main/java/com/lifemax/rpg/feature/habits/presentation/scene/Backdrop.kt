package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Building
import com.lifemax.rpg.feature.habits.domain.CloudKind
import com.lifemax.rpg.feature.habits.domain.Pass
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.Star
import com.lifemax.rpg.feature.habits.domain.skyTone
import com.lifemax.rpg.feature.habits.domain.starsShown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Everything behind the street, and alive: the sky of the hour with its twinkling stars (the bright
 * ones with cross-shaped glints, and now and then a shooting one), its sun (a corona and slow rays)
 * or cratered moon, its soft volumetric clouds (lit tops, shaded undersides, graded by the hour);
 * an airplane with a contrail, birds, a blimp; three rows of far city, hazier at their feet, that
 * slide at their own speeds as the street scrolls (depth), with windows of several kinds going on
 * and off at night, antennas, and red warning lights on the tallest; the glow over the horizon; a
 * car on the far road. Every moving thing is a function of [seconds] and the [seed] ([Ambient]); at
 * 0 seconds (motion reduced, or the tab hidden) it is a still picture with nothing passing. Nothing
 * is made per frame: the passes, paths, strokes and brushes are kept here, drawn under transforms
 * (shapes are in dp, brushes in the shape's own units).
 */
internal class Backdrop(private val seed: Int) {
    private val starField: Array<Star> = Ambient.stars(72, seed).toTypedArray()
    private val rows: Array<Array<Building>> =
        arrayOf(
            Ambient.skyline(5, 34, 84, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
            Ambient.skyline(23, 22, 58, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
            Ambient.skyline(41, 12, 38, 0f, Ambient.SKYLINE_SPAN).toTypedArray(),
        )
    private val clouds = Ambient.clouds(seed).toTypedArray()

    /** How far each row slides for a street unit scrolled: the furthest least. */
    private val drift = floatArrayOf(.05f, .1f, .18f)

    /**
     * Each far building's few windows: (across, down) shares of it and a kind, three numbers each.
     */
    private val windows: Array<Array<FloatArray>> =
        Array(rows.size) { r ->
            Array(rows[r].size) { i ->
                val b = rows[r][i]
                val n = (b.h / 9f).toInt().coerceIn(1, 7)
                FloatArray(n * 3) { k ->
                    val w = k / 3
                    when (k % 3) {
                        0 -> .18f + .6f * Ambient.hash(r * 1000 + i, w * 2, 3)
                        1 -> .12f + .8f * Ambient.hash(r * 1000 + i, w * 2 + 1, 3)
                        else -> Ambient.hash(r * 1000 + i, w, 5)
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

    /**
     * The height of an antenna on a far building (dp), 0 for none: the warned ones, and some more.
     */
    private val antennas: Array<FloatArray> =
        Array(rows.size) { r ->
            FloatArray(rows[r].size) { i ->
                if (r == 2) 0f
                else if (warned[r][i]) 6f + 4f * Ambient.hash(r, i, 6)
                else if (Ambient.hash(r, i, 7) < .14f) 4f + 4f * Ambient.hash(r, i, 8) else 0f
            }
        }

    private val pass = Pass()
    private val path = Path()

    // ---- shapes made once (in dp, in their own local units)

    private val wing = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val seamStroke = Stroke(.45f, cap = StrokeCap.Round)
    private val rodStroke = Stroke(.9f, cap = StrokeCap.Round)

    /** The sun's rays: twelve thin wedges round a disc of radius 1, from 1.3 out to 2.7. */
    private val rays =
        Path().apply {
            for (k in 0 until 12) {
                val a = k * (2f * PI.toFloat() / 12f)
                val w = .085f
                val len = if (k % 2 == 0) 2.7f else 2.1f
                moveTo(cos(a - w) * 1.3f, sin(a - w) * 1.3f)
                lineTo(cos(a) * len, sin(a) * len)
                lineTo(cos(a + w) * 1.3f, sin(a + w) * 1.3f)
                close()
            }
        }

    /** The blimp, nose to the right, 54 dp long: envelope seams, belly band, fins, gondola. */
    private val seams =
        Path().apply {
            for (k in 0 until 7) {
                val x = -21f + k * 7f
                val h = 9.5f * kotlin.math.sqrt((1f - (x / 27f) * (x / 27f)).coerceAtLeast(0f))
                moveTo(x, -h)
                quadraticBezierTo(x + x / 27f * 3.2f, 0f, x, h)
            }
        }
    private val bellyBand =
        Path().apply {
            moveTo(-24.5f, 2.4f)
            quadraticBezierTo(0f, 5.6f, 24.5f, 2.4f)
            lineTo(24f, 4.3f)
            quadraticBezierTo(0f, 7.7f, -24f, 4.3f)
            close()
        }
    private val finTop =
        Path().apply {
            moveTo(-17f, -8f)
            lineTo(-27.5f, -16f)
            lineTo(-30f, -14.2f)
            lineTo(-24.5f, -4f)
            close()
        }
    private val finBottom =
        Path().apply {
            moveTo(-17f, 8f)
            lineTo(-27f, 15f)
            lineTo(-29.5f, 13.4f)
            lineTo(-24.5f, 4f)
            close()
        }
    private val finSide =
        Path().apply {
            moveTo(-18f, 0f)
            lineTo(-29f, -3.2f)
            lineTo(-29f, 3.2f)
            close()
        }

    /**
     * The airplane, nose to the right, about 20 dp long, seen from below: fuselage, wings, tail.
     */
    private val airplane =
        Path().apply {
            moveTo(9f, 0f)
            lineTo(5.5f, -1.3f)
            lineTo(-7f, -1f)
            lineTo(-8.5f, 0f)
            lineTo(-7f, 1f)
            lineTo(5.5f, 1.3f)
            close()
            for (side in intArrayOf(-1, 1)) {
                moveTo(2f, side * 1f)
                lineTo(-3.2f, side * 10.5f)
                lineTo(-5.8f, side * 10.5f)
                lineTo(-3.6f, side * 1f)
                close()
                moveTo(-6f, side * 1f)
                lineTo(-9f, side * 4.3f)
                lineTo(-10.4f, side * 4.3f)
                lineTo(-8.6f, side * 1f)
                close()
            }
        }
    private val contrailSoft =
        Path().apply {
            moveTo(-9f, -1f)
            lineTo(-190f, -6f)
            lineTo(-190f, 6f)
            lineTo(-9f, 1f)
            close()
        }
    private val contrailCore =
        Path().apply {
            moveTo(-9f, -.55f)
            lineTo(-130f, -1.9f)
            lineTo(-130f, 1.9f)
            lineTo(-9f, .55f)
            close()
        }

    // ---- brushes made once per sky (units of their own: unit circles, dp for the airships)

    private var skyKey: Any? = null
    private var cloudLobe: Brush? = null
    private var cloudBase: Brush? = null
    private var cloudHaze: Brush? = null
    private var discSun: Brush? = null
    private var discMoon: Brush? = null
    private var envelope: Brush? = null
    private var airGlow: Brush? = null
    private var trailSoft: Brush? = null
    private var trailCore: Brush? = null

    // ---- brushes that follow the horizon, the orb and the density
    private var skyBrush: Brush? = null
    private var glowBrush: Brush? = null
    private var haloBrush: Brush? = null
    private val rowBrush = arrayOfNulls<Brush>(3)
    private var brushKey: Any? = null
    private var brushH = 0f
    private var brushOrb = Offset.Zero
    private var brushD = 0f

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
        unitBrushes(sky)
        drawRect(skyBrush!!)
        val tone = skyTone(hour)
        val starAlpha = starsShown(tone)
        val band = (horizon - top).coerceAtLeast(40f * d)
        if (starAlpha > .01f) {
            for (i in starField.indices) {
                val s = starField[i]
                val at = Offset(s.x * w, s.y * horizon * .9f)
                val a = s.alpha * starAlpha * Ambient.twinkle(s, seconds)
                drawCircle(SceneInk.Star, s.r * d, at, a)
                if (s.r >= 1.3f) {
                    // the bright ones: a cross-shaped glint
                    val arm = (3.4f + s.r * 2.2f) * d
                    drawLine(
                        SceneInk.Star,
                        Offset(at.x - arm, at.y),
                        Offset(at.x + arm, at.y),
                        .8f * d,
                        cap = StrokeCap.Round,
                        alpha = a * .6f,
                    )
                    drawLine(
                        SceneInk.Star,
                        Offset(at.x, at.y - arm),
                        Offset(at.x, at.y + arm),
                        .8f * d,
                        cap = StrokeCap.Round,
                        alpha = a * .6f,
                    )
                }
            }
            if (starAlpha > .6f && Ambient.shootingStar(seconds, seed, pass))
                shootingStar(pass, w, top, band, starAlpha)
        }
        heavenlyBody(sky, orb, seconds)
        clouds(sky, w, top, band, shift, seconds)
        if (tone > 1.6f && Ambient.blimp(seconds, seed, pass))
            blimp(pass, sky, w, top, band, seconds)
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
        if (
            brushKey === sky &&
                brushH == horizon &&
                brushOrb == orb &&
                brushD == density &&
                skyBrush != null
        )
            return
        skyBrush =
            Brush.verticalGradient(
                0f to sky.sky[0],
                .6f to sky.sky[1],
                1f to sky.sky[2],
                startY = 0f,
                endY = horizon,
            )
        glowBrush =
            Brush.verticalGradient(
                listOf(Color.Transparent, sky.glow),
                startY = horizon - 60f * density,
                endY = horizon,
            )
        // the sun's corona is wide and warm, the moon's halo small and pale
        val reach = (if (sky.phase == SkyPhase.DAY) 74f else 52f) * density
        haloBrush =
            Brush.radialGradient(
                0f to sky.halo,
                .28f to sky.halo.copy(alpha = sky.halo.alpha * .55f),
                1f to Color.Transparent,
                center = orb,
                radius = reach,
            )
        for (r in 0 until 3) {
            // the far city is hazier at its feet, where the air is thickest
            rowBrush[r] =
                Brush.verticalGradient(
                    listOf(sky.far[r], lerp(sky.far[r], sky.sky[2], .38f)),
                    startY = horizon - 90f * density,
                    endY = horizon,
                )
        }
        brushKey = sky
        brushH = horizon
        brushOrb = orb
        brushD = density
    }

    private fun unitBrushes(sky: SkyColors) {
        if (skyKey === sky) return
        // a cloud lobe: lit at the upper left, shaded and ever fainter to the lower right
        cloudLobe =
            Brush.radialGradient(
                0f to sky.cloudLit.copy(alpha = .97f),
                .4f to sky.cloudBody.copy(alpha = .95f),
                .78f to sky.cloudShade.copy(alpha = .78f),
                1f to sky.cloudShade.copy(alpha = 0f),
                center = Offset(-.18f, -.3f),
                radius = 1.25f,
            )
        cloudBase =
            Brush.radialGradient(
                0f to sky.cloudShade.copy(alpha = .55f),
                .6f to sky.cloudShade.copy(alpha = .3f),
                1f to sky.cloudShade.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f,
            )
        cloudHaze =
            Brush.radialGradient(
                0f to sky.cloudBody.copy(alpha = .3f),
                .55f to sky.cloudBody.copy(alpha = .16f),
                1f to sky.cloudBody.copy(alpha = 0f),
                center = Offset.Zero,
                radius = 1f,
            )
        discSun =
            Brush.radialGradient(
                0f to Color.White,
                .6f to sky.orb,
                1f to lerp(sky.orb, Color(0xFFFFB347), .45f),
                center = Offset(-.15f, -.15f),
                radius = 1.15f,
            )
        discMoon =
            Brush.radialGradient(
                0f to Color.White,
                .65f to sky.orb,
                1f to lerp(sky.orb, Color(0xFF8C9BD0), .5f),
                center = Offset(-.3f, -.3f),
                radius = 1.4f,
            )
        envelope =
            Brush.verticalGradient(
                0f to sky.cloudLit,
                .45f to sky.cloudBody,
                1f to sky.cloudShade,
                startY = -9.5f,
                endY = 9.5f,
            )
        airGlow =
            Brush.radialGradient(
                listOf(sky.cloudLit.copy(alpha = .4f), Color.Transparent),
                Offset.Zero,
                1f,
            )
        trailSoft =
            Brush.linearGradient(
                0f to sky.cloudLit.copy(alpha = .5f),
                .45f to sky.cloudLit.copy(alpha = .22f),
                1f to sky.cloudLit.copy(alpha = 0f),
                start = Offset(-9f, 0f),
                end = Offset(-190f, 0f),
            )
        trailCore =
            Brush.linearGradient(
                0f to sky.cloudLit.copy(alpha = .9f),
                .6f to sky.cloudLit.copy(alpha = .35f),
                1f to sky.cloudLit.copy(alpha = 0f),
                start = Offset(-9f, 0f),
                end = Offset(-130f, 0f),
            )
        skyKey = sky
    }

    /** The sun (a corona, slow rays, a lit disc) or the moon (a cratered disc, a crescent). */
    private fun DrawScope.heavenlyBody(sky: SkyColors, orb: Offset, seconds: Float) {
        val d = density
        drawCircle(haloBrush!!, (if (sky.phase == SkyPhase.DAY) 74f else 52f) * d, orb)
        if (sky.phase == SkyPhase.NIGHT) {
            translate(orb.x, orb.y) {
                scale(11f * d, 11f * d, Offset.Zero) {
                    drawCircle(discMoon!!, 1f, Offset.Zero)
                    // maria and craters
                    drawCircle(SceneInk.Crater, .3f, Offset(-.28f, -.2f), alpha = .3f)
                    drawCircle(SceneInk.Crater, .2f, Offset(.25f, .1f), alpha = .26f)
                    drawCircle(SceneInk.Crater, .13f, Offset(-.1f, .45f), alpha = .3f)
                    drawCircle(SceneInk.Crater, .1f, Offset(.4f, -.35f), alpha = .24f)
                }
            }
            // the dark of the moon: a disc of sky over one side, a little of it showing through
            drawCircle(sky.sky[1], 9.6f * d, orb + Offset(5.2f * d, -3f * d), alpha = .93f)
        } else {
            translate(orb.x, orb.y) {
                // rays turn slowly (still in a still picture), then the disc
                rotate(seconds * 2.5f, Offset.Zero) {
                    scale(13f * d, 13f * d, Offset.Zero) {
                        drawPath(
                            rays,
                            SceneInk.Rays,
                            alpha = if (sky.phase == SkyPhase.DAY) .2f else .14f,
                        )
                    }
                }
                scale(13f * d, 13f * d, Offset.Zero) { drawCircle(discSun!!, 1f, Offset.Zero) }
            }
        }
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

    /**
     * Soft volumetric clouds: each a faint haze, a darker base and overlapping lobes, every lobe
     * lit at its upper left and shaded and fainter below. The big and near drift faster (parallax),
     * and the street's scrolling nudges them a little.
     */
    private fun DrawScope.clouds(
        sky: SkyColors,
        w: Float,
        top: Float,
        band: Float,
        shift: Float,
        seconds: Float,
    ) {
        val d = density
        val night = sky.dark > .8f
        val count = if (night) 3 else if (sky.dark > .3f) 4 else Ambient.CLOUDS
        val strength = if (sky.phase == SkyPhase.DAY) .96f else if (night) .72f else .9f
        for (i in 0 until count) {
            val c = clouds[i]
            val s = d * c.scale
            val x = Ambient.cloudAt(c, seconds) * w - shift * .03f * c.depth * d
            val y = top + band * c.y + c.rise * .5f * s
            val a = strength * (.72f + .28f * c.depth)
            // the haze round it, and the shaded base under it
            translate(x, y) {
                translate(0f, -c.rise * .5f * s) {
                    scale(c.reach * 1.25f * s, (c.rise * .5f + 9f) * s, Offset.Zero) {
                        drawCircle(cloudHaze!!, 1f, Offset.Zero, alpha = a)
                    }
                }
                scale(
                    c.reach * .95f * s,
                    (if (c.kind == CloudKind.STRATUS) 4f else 8f) * s,
                    Offset.Zero,
                ) {
                    translate(0f, -.1f) { drawCircle(cloudBase!!, 1f, Offset.Zero, alpha = a) }
                }
                var k = 0
                val lobes = c.lobes
                while (k < lobes.size) {
                    translate(lobes[k] * s, lobes[k + 1] * s) {
                        scale(lobes[k + 2] * s, lobes[k + 3] * s, Offset.Zero) {
                            drawCircle(cloudLobe!!, 1f, Offset.Zero, alpha = a)
                        }
                    }
                    k += 4
                }
            }
        }
    }

    /**
     * An airship, bobbing as it drifts: an envelope with seams and a highlight, fins, a gondola.
     */
    private fun DrawScope.blimp(
        p: Pass,
        sky: SkyColors,
        w: Float,
        top: Float,
        band: Float,
        seconds: Float,
    ) {
        val d = density
        val bob = sin(seconds * .9f + p.pass) * 1.6f
        val pitch = sin(seconds * .6f + p.pass * .7f) * 1.2f * p.dir
        val dark = sky.dark
        withTransform({
            translate(p.x * w, top + p.y * band + bob * d)
            rotate(pitch, Offset.Zero)
            scale(d * p.dir * .95f, d * .95f, Offset.Zero)
        }) {
            if (dark > .3f)
                scale(46f, 20f, Offset.Zero) {
                    drawCircle(airGlow!!, 1f, Offset(0f, .1f), alpha = (dark - .3f) * 1.1f)
                }
            // fins first, so the envelope overlaps their roots; the lower one in shadow
            drawPath(finSide, sky.cloudShade)
            drawPath(finBottom, sky.cloudShade)
            drawPath(finTop, lerp(sky.cloudBody, sky.cloudShade, .35f))
            // the envelope, a specular highlight along its top, the belly band, the panel seams
            drawOval(envelope!!, Offset(-27f, -9.5f), Size(54f, 19f))
            drawPath(bellyBand, SceneInk.BlimpBand, alpha = .78f - .3f * dark)
            drawPath(seams, sky.cloudShade, alpha = .4f, style = seamStroke)
            drawOval(Color.White, Offset(-17f, -8.2f), Size(26f, 3.2f), alpha = .6f - .5f * dark)
            // the gondola under it: a lit cabin window, a propeller blurring at its tail
            drawLine(sky.cloudShade, Offset(-3f, 8.6f), Offset(-3.5f, 10f), .8f)
            drawLine(sky.cloudShade, Offset(5f, 8.6f), Offset(5.5f, 10f), .8f)
            drawRoundRect(
                lerp(SceneInk.Plane, SceneInk.BlimpShade, .5f * (1f - dark)),
                Offset(-6.5f, 9.6f),
                Size(15f, 5.2f),
                androidx.compose.ui.geometry.CornerRadius(1.6f, 1.6f),
            )
            val lit = lerp(Color(0xFFBFE3FF), SceneInk.WindowLit, dark)
            drawRect(lit, Offset(-4.6f, 10.7f), Size(3.2f, 2f))
            drawRect(lit, Offset(-.2f, 10.7f), Size(3.2f, 2f), alpha = .85f)
            if (dark > .3f)
                drawCircle(SceneInk.WindowLit, 5f, Offset(-3f, 11.7f), alpha = .18f * dark)
            val blade = abs(cos(seconds * 37f)) * 3.1f
            drawOval(Color.White, Offset(-9f, 12.2f - 3.1f), Size(1.6f, 6.2f), alpha = .22f)
            drawLine(
                SceneInk.Plane,
                Offset(-8.2f, 12.2f - blade),
                Offset(-8.2f, 12.2f + blade),
                .7f,
            )
            if (dark > .3f) {
                // running lights: red at the top fin, green at the nose, a white strobe underneath
                drawCircle(SceneInk.Warning, 3.6f, Offset(-28.6f, -14.4f), alpha = .25f * dark)
                drawCircle(SceneInk.Warning, 1.1f, Offset(-28.6f, -14.4f), alpha = dark)
                drawCircle(SceneInk.NavGreen, 3f, Offset(24.5f, 3.2f), alpha = .22f * dark)
                drawCircle(SceneInk.NavGreen, .95f, Offset(24.5f, 3.2f), alpha = dark)
                val strobe = Ambient.warning(11, seconds * 1.3f)
                if (strobe > 0f)
                    drawCircle(SceneInk.Strobe, 1.4f, Offset(-27.6f, 13.6f), alpha = strobe)
            }
        }
    }

    /** The airplane: by day with swept wings and a contrail, at night its lights. */
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
        withTransform({
            translate(x, y)
            scale(d * p.dir, d, Offset.Zero)
        }) {
            if (tone > 1.3f) {
                // a contrail behind it, widening and fading the further back it goes
                drawPath(contrailSoft, trailSoft!!)
                drawPath(contrailCore, trailCore!!)
                withTransform({ translate(.5f, .9f) }) {
                    drawPath(airplane, SceneInk.Shadow, alpha = .3f)
                }
                drawPath(airplane, lerp(SceneInk.PlaneDay, sky.cloudLit, .35f))
            } else {
                // at night only its lights: red and green at the wingtips, a white strobe
                drawPath(airplane, sky.far[0])
                drawCircle(SceneInk.Warning, 1.4f, Offset(-4.4f, -10.4f))
                drawCircle(SceneInk.NavGreen, 1.4f, Offset(-4.4f, 10.4f))
                val strobe = Ambient.warning(7, seconds * 1.4f)
                if (strobe > 0f) {
                    drawCircle(SceneInk.Strobe, 4f, Offset(-8.5f, 0f), alpha = .2f * strobe)
                    drawCircle(SceneInk.Strobe, 1.5f, Offset(-8.5f, 0f), alpha = strobe)
                }
            }
        }
    }

    /** A flock in a V: each bird's gull-shaped wings beating at their own beat. */
    private fun DrawScope.birds(p: Pass, w: Float, top: Float, band: Float, seconds: Float) {
        val d = density
        val x0 = p.x * w
        val y0 = top + p.y * band
        withTransform({
            translate(x0, y0)
            scale(d, d, Offset.Zero)
        }) {
            for (i in 0 until Ambient.FLOCK) {
                val x = Ambient.birdX(i, p.dir)
                val y = Ambient.birdY(i)
                val flap = Ambient.wing(i, seconds)
                // two arcs meeting at the body; the tips rise and fall with the beat
                path.rewind()
                path.moveTo(x - 5.2f, y - flap * 3.4f + 1f)
                path.quadraticBezierTo(x - 2.4f, y - 2.4f - flap * 1.6f, x, y)
                path.quadraticBezierTo(
                    x + 2.4f,
                    y - 2.4f - flap * 1.6f,
                    x + 5.2f,
                    y - flap * 3.4f + 1f,
                )
                drawPath(path, SceneInk.Bird, style = wing)
            }
        }
    }

    private fun DrawScope.row(r: Int, sky: SkyColors, base: Float, slide: Float, seconds: Float) {
        val d = density
        val w = size.width / d
        val brush = rowBrush[r]!!
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
                    drawRect(brush, Offset(px, top), Size(b.w * d + 1f, b.h * d + 1f))
                    val antenna = antennas[r][i]
                    if (antenna > 0f)
                        drawLine(
                            sky.far[r],
                            Offset(px + b.w * d * .6f, top),
                            Offset(px + b.w * d * .6f, top - antenna * d),
                            1f * d,
                        )
                    if (lights && r > 0) {
                        val win = windows[r][i]
                        for (k in 0 until win.size / 3) {
                            if (!Ambient.farWindowLit(r * 1000 + i, k, seconds, share)) continue
                            val kind = win[k * 3 + 2]
                            // warm, pale or cool light; a few wider
                            val tint =
                                if (kind < .6f) SceneInk.FarLight
                                else if (kind < .85f) SceneInk.FarLightPale
                                else SceneInk.FarLightCool
                            val wide = if (kind > .45f && kind < .7f) 2.6f else 1.7f
                            drawRect(
                                tint,
                                Offset(px + win[k * 3] * b.w * d, top + win[k * 3 + 1] * b.h * d),
                                Size(wide * d, 1.7f * d),
                                alpha = .5f + .3f * sky.dark,
                            )
                        }
                    }
                    if (warned[r][i] && sky.dark > .3f) {
                        val on = if (seconds > 0f) Ambient.warning(r * 100 + i, seconds) else .7f
                        if (on > 0f) {
                            val at = Offset(px + b.w * d * .6f, top - antenna * d - 1.5f * d)
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
