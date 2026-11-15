package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Building
import com.lifemax.rpg.feature.habits.domain.Pass
import com.lifemax.rpg.feature.habits.domain.SkyPhase
import com.lifemax.rpg.feature.habits.domain.Star
import com.lifemax.rpg.feature.habits.domain.skyTone
import com.lifemax.rpg.feature.habits.domain.starsShown
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Everything behind the street, and alive: the sky of the hour with its twinkling stars (the bright
 * ones with cross-shaped glints, and now and then a shooting one), its sun (a corona and slow rays)
 * or cratered moon, its soft volumetric clouds (lit tops, shaded undersides, graded by the hour);
 * an airplane with a contrail, birds, a blimp; three rows of far city, hazier at their feet, that
 * slide at their own speeds as the street scrolls (depth), with windows of several kinds going on
 * and off at night, antennas, and red warning lights on the tallest; the glow over the horizon; a
 * car on the far road (a painted vehicle with its lights). Every moving thing is a function of
 * [seconds] and the [seed] ([Ambient]); at 0 seconds (motion reduced, or the tab hidden) it is a
 * still picture with nothing passing. Nothing is made per frame: the passes, paths, strokes and
 * brushes are kept here, drawn under transforms (shapes are in dp, brushes in the shape's own
 * units).
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

    /**
     * Where the latest frame's clouds are, for the shadows they throw on the street: the middle and
     * the width of each in px, and how solid it is (a near cloud more than a far one).
     */
    val shadeX = FloatArray(Ambient.CLOUDS)
    val shadeW = FloatArray(Ambient.CLOUDS)
    val shadeA = FloatArray(Ambient.CLOUDS)
    var shadeN = 0
        private set

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

    /**
     * How much of the horizon's air each skyline row is seen through, at its top and at its foot.
     */
    private val HAZE_TOP = floatArrayOf(.34f, .2f, .08f)
    private val HAZE_FOOT = floatArrayOf(.7f, .58f, .46f)
    private val path = Path()

    // ---- shapes made once (in dp, in their own local units)

    private val seamStroke = Stroke(.45f, cap = StrokeCap.Round)

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

    /** The blimp's envelope, nose to the right, 54 dp long: one smooth teardrop. */
    private val envelopePath =
        Path().apply {
            moveTo(-27f, .4f)
            cubicTo(-27f, -6.5f, -15f, -10f, 1f, -10f)
            cubicTo(16f, -10f, 27.5f, -6f, 27.5f, 0f)
            cubicTo(27.5f, 6f, 16f, 10f, 1f, 10f)
            cubicTo(-15f, 10f, -27f, 6.8f, -27f, .4f)
            close()
        }

    /** The crease round the blimp's middle, a very faint line. */
    private val equator =
        Path().apply {
            moveTo(-25f, 1.4f)
            quadraticBezierTo(0f, 3.6f, 26f, 1.4f)
        }

    /** The blimp's panel seams, ribs across the envelope. */
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
            // a fuselage with a rounded nose and a tapering tail
            moveTo(9.6f, 0f)
            cubicTo(8.2f, -1.3f, 6f, -1.5f, 3f, -1.45f)
            lineTo(-7f, -.95f)
            quadraticBezierTo(-9.2f, -.55f, -9.2f, 0f)
            quadraticBezierTo(-9.2f, .55f, -7f, .95f)
            lineTo(3f, 1.45f)
            cubicTo(6f, 1.5f, 8.2f, 1.3f, 9.6f, 0f)
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
    private val contrailHalo =
        Path().apply {
            moveTo(-9f, -2f)
            lineTo(-210f, -13f)
            lineTo(-210f, 13f)
            lineTo(-9f, 2f)
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
    private val tintShade = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private val tintMid = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private val tintLit = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private var discSun: Brush? = null
    private var discMoon: Brush? = null
    private var envelope: Brush? = null
    private var airGlow: Brush? = null
    private var trailSoft: Brush? = null
    private var trailCore: Brush? = null
    private var trailHalo: Brush? = null
    private var blimpSpec: Brush? = null
    private var blimpBounce: Brush? = null
    private var planeBody: Brush? = null

    // ---- brushes that follow the horizon, the orb and the density
    private var skyBrush: Brush? = null
    private var glowBrush: Brush? = null
    private var haloBrush: Brush? = null
    private var haloWide: Brush? = null
    private val rowBrush = arrayOfNulls<Brush>(3)
    private var brushKey: Any? = null
    private var brushH = 0f
    private var brushOrb = Offset.Zero
    private var brushD = 0f

    /**
     * [horizon] is the y of the horizon in pixels, [top] the y under the heading's band, [orb]
     * where the sun or the moon stands, [ceiling] the y above which the clouds hang (the tallest
     * roof in view), [shift] how far along the street the view stands (street units), [seconds] the
     * running time (0: a still picture).
     */
    fun DrawScope.backdrop(
        hour: Float,
        sky: SkyColors,
        horizon: Float,
        top: Float,
        orb: Offset,
        ceiling: Float,
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
        clouds(sky, w, top, horizon, ceiling, orb.x > w / 2f, shift, seconds)
        if (tone > 1.6f && Ambient.blimp(seconds, seed, pass))
            blimp(pass, sky, w, top, band, seconds)
        if (Ambient.plane(seconds, seed, pass)) plane(pass, sky, w, top, band, seconds, tone)
        for (r in rows.indices) row(r, sky, horizon, shift * drift[r], seconds)
        if (tone >= .7f && Ambient.birds(seconds, seed, pass)) birds(pass, w, top, band, seconds)
        // the glow over the horizon: the city's light at night, haze by day
        drawRect(glowBrush!!, Offset(0f, horizon - 60f * d), Size(w, 60f * d))
        drawRect(sky.ground[0], Offset(0f, horizon), Size(w, size.height - horizon))
        drawLine(sky.line, Offset(0f, horizon), Offset(w, horizon), d)
        if (Ambient.car(seconds, seed, pass)) car(pass, sky, w, horizon, seconds)
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
        // four stops, the two middle ones blended in, so the gradient has no visible band in it
        skyBrush =
            Brush.verticalGradient(
                0f to sky.sky[0],
                .32f to lerp(sky.sky[0], sky.sky[1], .72f),
                .66f to lerp(sky.sky[1], sky.sky[2], .3f),
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
        // the sun's wide glow: the whole sky near it warms, very faintly
        haloWide =
            Brush.radialGradient(
                0f to sky.halo.copy(alpha = sky.halo.alpha * .42f),
                .45f to sky.halo.copy(alpha = sky.halo.alpha * .13f),
                1f to Color.Transparent,
                center = orb,
                radius = (if (sky.phase == SkyPhase.DAY) 230f else 130f) * density,
            )
        for (r in 0 until 3) {
            // atmospheric perspective: the further the row, the hazier and bluer it is, and every
            // one fades into the horizon's air at its feet
            val top = lerp(sky.far[r], sky.sky[2], HAZE_TOP[r])
            rowBrush[r] =
                Brush.verticalGradient(
                    0f to top,
                    .5f to lerp(sky.far[r], sky.sky[2], HAZE_TOP[r] + .22f),
                    1f to lerp(sky.far[r], sky.sky[2], HAZE_FOOT[r]),
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
        // each cloud's three tints: the hour's, thinned into the horizon's haze the further it is
        for (i in clouds.indices) {
            val c = clouds[i]
            val haze = c.haze * .55f
            val horizon = sky.sky[2]
            fun tint(col: Color, k: Float) =
                ColorFilter.tint(lerp(col, horizon, (haze * k).coerceIn(0f, 1f)), BlendMode.SrcIn)
            tintShade[i] = tint(sky.cloudShade, 1f)
            tintMid[i] = tint(sky.cloudBody, .8f)
            tintLit[i] = tint(sky.cloudLit, .55f)
        }
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
                .3f to lerp(sky.cloudLit, sky.cloudBody, .55f),
                .62f to sky.cloudBody,
                1f to lerp(sky.cloudBody, sky.cloudShade, .8f),
                startY = -10f,
                endY = 10f,
            )
        // a soft specular along the top, and the sky's light bounced up under the belly
        blimpSpec =
            Brush.radialGradient(
                0f to Color.White.copy(alpha = .8f),
                .55f to Color.White.copy(alpha = .22f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = 1f,
            )
        blimpBounce =
            Brush.radialGradient(
                0f to lerp(sky.sky[2], sky.cloudLit, .5f).copy(alpha = .55f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = 1f,
            )
        planeBody =
            Brush.linearGradient(
                0f to Color.White,
                .55f to lerp(SceneInk.PlaneDay, sky.cloudLit, .35f),
                1f to lerp(SceneInk.PlaneDay, sky.cloudShade, .45f),
                start = Offset(9f, -1.5f),
                end = Offset(-9f, 1.5f),
            )
        trailHalo =
            Brush.linearGradient(
                0f to sky.cloudLit.copy(alpha = .16f),
                .5f to sky.cloudLit.copy(alpha = .06f),
                1f to sky.cloudLit.copy(alpha = 0f),
                start = Offset(-9f, 0f),
                end = Offset(-210f, 0f),
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
        drawCircle(haloWide!!, (if (sky.phase == SkyPhase.DAY) 230f else 130f) * d, orb)
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

    // ---- the meteor and the far road: tints for their baked sprites ([RoadSkySprites]), made once

    private val meteorCore = ColorFilter.tint(Color.White, BlendMode.SrcIn)
    private val meteorCoreFire = ColorFilter.tint(Color(0xFFFFEBC2), BlendMode.SrcIn)
    private val meteorHalo = ColorFilter.tint(Color(0xFFA9C2FF), BlendMode.SrcIn)
    private val meteorHaloFire = ColorFilter.tint(Color(0xFF96F0BE), BlendMode.SrcIn)
    private val meteorHead = ColorFilter.tint(Color(0xFFEAF0FF), BlendMode.SrcIn)
    private val meteorHeadFire = ColorFilter.tint(Color(0xFFFFD39A), BlendMode.SrcIn)
    private val sparkColor = Color(0xFFFFF6DC)
    private val sparkTint = ColorFilter.tint(sparkColor, BlendMode.SrcIn)
    private val headlightTint = ColorFilter.tint(SceneInk.Headlight, BlendMode.SrcIn)
    private val taillightTint = ColorFilter.tint(SceneInk.Taillight, BlendMode.SrcIn)

    /** The haze of distance over a vehicle by day: the far row's colour, a little, per sky. */
    private var hazeKey: Any? = null
    private var hazeTint: ColorFilter? = null

    /**
     * A shooting star as a meteor: a bright head with a soft bloom that is brightest in the middle
     * of its run, a streak behind it (a hot core in a cooler, wider halo, slightly curved, baked in
     * [RoadSkySprites.streak] and drawn turned along the way it flies), a few sparkles shed behind
     * the head that drift off the line and fade, and, after the head has gone out, its train
     * lingering a moment where it went. Each pass has its own length, brightness and slope; rarely
     * one is a fireball, longer and brighter, with green and orange in it ([Ambient.fireball]).
     */
    private fun DrawScope.shootingStar(p: Pass, w: Float, top: Float, band: Float, alpha: Float) {
        val d = density
        val t = p.t
        val k = p.pass
        val hx = p.x * w
        val hy = top + p.y * band
        // the head's way across the sky, in px for a whole pass
        val vx = p.dir * Ambient.METEOR_RUN * w
        val vy = Ambient.meteorSlope(seed, k) * band
        val angle = (atan2(vy, vx) * 180f / PI).toFloat()
        val size = Ambient.meteorSize(seed, k)
        val fire = Ambient.fireball(seed, k)
        val bright = Ambient.meteorBright(seed, k) * alpha
        val train = Ambient.meteorTrain(t) * bright
        val head = Ambient.meteorHead(t) * bright
        val len = (40f + 36f * size) * d * Ambient.meteorReach(t)
        val streak = SceneSprites.streak
        val sp = streak.sprite
        val g = RoadSkySprites.GLOW.toFloat()
        val glow = SceneSprites.glow
        if (train > 0f) {
            // along the streak, px per sprite px; across, mirrored for a meteor flying left so the
            // curve still hangs down
            val sx = len / (sp.headX - 10f)
            val sy =
                d * (.55f + .45f * size) / RoadSkySprites.PX_PER_DP * (if (p.dir < 0f) -1f else 1f)
            withTransform({
                translate(hx, hy)
                rotate(angle, Offset.Zero)
                scale(sx, sy, Offset.Zero)
            }) {
                val at = Offset(-sp.headX, -sp.h * .42f)
                drawImage(
                    streak.halo,
                    at,
                    train * .95f,
                    colorFilter = if (fire) meteorHaloFire else meteorHalo,
                )
                drawImage(
                    streak.core,
                    at,
                    train,
                    colorFilter = if (fire) meteorCoreFire else meteorCore,
                )
            }
        }
        if (head > 0f) {
            val r = (5f + 3.5f * size) * d
            val tint = if (fire) meteorHeadFire else meteorHead
            withTransform({
                translate(hx, hy)
                scale(2f * r / g, 2f * r / g, Offset.Zero)
            }) {
                drawImage(glow, Offset(-g / 2f, -g / 2f), head * .85f, colorFilter = tint)
            }
            withTransform({
                translate(hx, hy)
                scale(.9f * r / g, .9f * r / g, Offset.Zero)
            }) {
                drawImage(glow, Offset(-g / 2f, -g / 2f), head, colorFilter = tint)
            }
            drawCircle(SceneInk.Strobe, (1f + .45f * size) * d, Offset(hx, hy), head)
        }
        // the sparkles: each shed where the head was when it was born, drifting off the line
        val tc = min(t, Ambient.METEOR_HEAD_END)
        val vl = sqrt(vx * vx + vy * vy)
        val nx = -vy / vl
        val ny = vx / vl
        for (i in 0 until Ambient.SPARKS) {
            val b = Ambient.sparkBirth(seed, k, i)
            val a = Ambient.sparkAlpha(b, t) * bright
            if (a <= 0f) continue
            val age = (t - b) / Ambient.SPARK_LIFE
            val off = Ambient.sparkSide(seed, k, i) * (1.5f + 5f * age) * d
            val sx = hx - vx * (tc - b) + nx * off
            val sy = hy - vy * (tc - b) + ny * off + age * age * 7f * d
            withTransform({
                translate(sx, sy)
                scale(4.5f * d / g, 4.5f * d / g, Offset.Zero)
            }) {
                drawImage(glow, Offset(-g / 2f, -g / 2f), a * .55f, colorFilter = sparkTint)
            }
            drawCircle(sparkColor, (.3f + .4f * (1f - age)) * d, Offset(sx, sy), a)
        }
    }

    /**
     * Clouds: painted sprites ([CloudSprites]), each drawn as three tinted layers (its body in the
     * shade of the hour, the part the light finds, and its brightest rim), so a cloud costs three
     * image draws. They hang in lanes of the sky above [ceiling] (the roof of the tallest building
     * in view, with the "add a floor" ghost on it), so none can sit behind the hint; the near and
     * big ones drift faster (parallax) and the street's scrolling nudges them; each breathes a very
     * little. The further a cloud (the lower its lane), the smaller, bluer and fainter. The light
     * comes from the sun's side: [mirror] draws the sprites flipped for a sun on the right.
     */
    private fun DrawScope.clouds(
        sky: SkyColors,
        w: Float,
        top: Float,
        horizon: Float,
        ceiling: Float,
        mirror: Boolean,
        shift: Float,
        seconds: Float,
    ) {
        val d = density
        val night = sky.dark > .8f
        val count = if (night) 3 else if (sky.dark > .3f) 4 else Ambient.CLOUDS
        val strength = if (sky.phase == SkyPhase.DAY) 1f else if (night) .8f else .94f
        val room = CloudLayout.room(top, horizon, ceiling, d)
        shadeN = 0
        if (room <= 0f) return
        val pool = SceneSprites.clouds
        for (i in 0 until count) {
            val c = clouds[i]
            val images = pool[CloudSprites.of(c.kind)[c.variant]]
            val sp = images.sprite
            val s = d * c.scale / CloudSprites.PX_PER_DP
            val x = Ambient.cloudAt(c, seconds) * w - shift * .03f * c.depth * d
            val base = CloudLayout.base(c.y, top, horizon, ceiling, d)
            // breathing: the sky never quite stands still, a few percent over half a minute
            val breathe = 1f + .04f * sin(seconds * .21f + i * 2.1f)
            val a = (strength * breathe * room * (1f - .28f * c.haze)).coerceIn(0f, 1f)
            shadeX[i] = x
            shadeW[i] = sp.w * s
            shadeA[i] = a * c.depth
            shadeN = i + 1
            val sx = if (mirror) -s else s
            withTransform({
                translate(x, base)
                scale(sx, s, Offset.Zero)
            }) {
                val at = Offset(-sp.w / 2f, -sp.h * .86f)
                drawImage(images.shade, at, a, colorFilter = tintShade[i])
                drawImage(images.mid, at, a, colorFilter = tintMid[i])
                drawImage(images.lit, at, a, colorFilter = tintLit[i])
            }
        }
    }

    /**
     * An airship, bobbing as it drifts: a smooth envelope shaded top to belly, a soft specular, the
     * sky's light bounced under it, faint panel seams, a gondola, tail fins. Small and unhurried.
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
            // a soft shadow of the envelope on the gondola and the lower fin, then the fins, so the
            // envelope overlaps their roots; the lower one in shadow
            drawPath(finSide, sky.cloudShade, alpha = .9f)
            drawPath(finBottom, sky.cloudShade)
            drawPath(finTop, lerp(sky.cloudBody, sky.cloudShade, .3f))
            // the gondola hangs beneath: two struts, a rounded cabin, a lit window strip
            drawLine(sky.cloudShade, Offset(-3f, 8.8f), Offset(-3.4f, 10.2f), .7f, alpha = .8f)
            drawLine(sky.cloudShade, Offset(5f, 9f), Offset(5.4f, 10.2f), .7f, alpha = .8f)
            drawRoundRect(
                lerp(SceneInk.Plane, SceneInk.BlimpShade, .55f * (1f - dark)),
                Offset(-6.8f, 9.8f),
                Size(15.6f, 5.2f),
                androidx.compose.ui.geometry.CornerRadius(2.4f, 2.4f),
            )
            drawRoundRect(
                Color.White,
                Offset(-6.2f, 10f),
                Size(14.4f, .8f),
                androidx.compose.ui.geometry.CornerRadius(.4f, .4f),
                alpha = .35f - .25f * dark,
            )
            val lit = lerp(Color(0xFFBFE3FF), SceneInk.WindowLit, dark)
            drawRoundRect(
                lit,
                Offset(-5f, 11.1f),
                Size(12f, 1.8f),
                androidx.compose.ui.geometry.CornerRadius(.7f, .7f),
                alpha = .92f,
            )
            if (dark > .3f)
                drawCircle(SceneInk.WindowLit, 5.5f, Offset(1f, 12f), alpha = .16f * dark)
            // the envelope: gradient, then, inside it, seams, equator, specular and bounce light
            drawPath(envelopePath, envelope!!)
            clipPath(envelopePath) {
                drawPath(bellyBand, SceneInk.BlimpBand, alpha = .7f - .3f * dark)
                drawPath(seams, sky.cloudShade, alpha = .26f, style = seamStroke)
                drawPath(equator, sky.cloudShade, alpha = .22f, style = seamStroke)
                translate(-5f, -6.6f) {
                    scale(16f, 2.8f, Offset.Zero) {
                        drawCircle(blimpSpec!!, 1f, Offset.Zero, alpha = .85f - .7f * dark)
                    }
                }
                translate(0f, 9.2f) {
                    scale(23f, 4f, Offset.Zero) {
                        drawCircle(blimpBounce!!, 1f, Offset.Zero, alpha = .9f)
                    }
                }
            }
            // the propeller, a faint disc at the tail of the cabin
            val blade = abs(cos(seconds * 37f)) * 2.8f
            drawOval(Color.White, Offset(-9.6f, 12.4f - 2.8f), Size(1.4f, 5.6f), alpha = .18f)
            drawLine(
                SceneInk.Plane,
                Offset(-8.9f, 12.4f - blade),
                Offset(-8.9f, 12.4f + blade),
                .6f,
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

    /**
     * The airplane: by day a gleaming silhouette (a smooth fuselage, swept wings) with a contrail
     * that is a soft tube fading out behind it, at night its lights.
     */
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
                // a contrail behind it: a wide faint halo, a body, a bright core, ever thinner
                drawPath(contrailHalo, trailHalo!!)
                drawPath(contrailSoft, trailSoft!!)
                drawPath(contrailCore, trailCore!!)
                withTransform({ translate(.4f, .8f) }) {
                    drawPath(airplane, SceneInk.Shadow, alpha = .22f)
                }
                drawPath(airplane, planeBody!!)
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

    /**
     * A flock in a V: each bird a gull, two tapering wings that bend at the wrist, the downstroke a
     * little slower than the upstroke, every bird on its own beat.
     */
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
                val beat = Ambient.wing(i, seconds)
                // the wings rise quickly and fall slowly: the beat is skewed
                val flap = (beat + .35f * beat * beat * beat).coerceIn(-1f, 1f)
                path.rewind()
                for (k in 0 until 2) {
                    val s = 1f - 2f * k
                    path.moveTo(x, y - .2f)
                    // the leading edge out to the tip
                    path.cubicTo(
                        x + s * 2.4f,
                        y - 1.5f - flap * 1.3f,
                        x + s * 4.6f,
                        y - 1.3f - flap * 3.3f,
                        x + s * 7.2f,
                        y + .3f - flap * 4.6f,
                    )
                    // and the trailing edge back
                    path.cubicTo(
                        x + s * 4.4f,
                        y - .2f - flap * 2.2f,
                        x + s * 2.3f,
                        y + .7f - flap * .8f,
                        x,
                        y + 1.1f,
                    )
                    path.close()
                }
                drawPath(path, SceneInk.Bird, alpha = .92f)
                drawCircle(SceneInk.Bird, 1f, Offset(x, y + .2f))
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

    /**
     * A vehicle on the far road (one of the painted pool, [RoadSkySprites.VEHICLES], a different
     * one each pass, mirrored for the other way; now and then a second follows it): by day its own
     * colour under a little of the horizon's haze; at dusk and night a dark body, a headlight (a
     * hot core in a soft bloom) throwing a beam forward and down the road and a pool of light on
     * it, a red tail-light with its glow and a faint red on the road. It rides with a very small
     * bob.
     */
    private fun DrawScope.car(p: Pass, sky: SkyColors, w: Float, horizon: Float, seconds: Float) {
        val d = density
        if (hazeKey !== sky) {
            hazeTint = ColorFilter.tint(sky.far[2].copy(alpha = .35f), BlendMode.SrcAtop)
            hazeKey = sky
        }
        val pool = SceneSprites.vehicles
        val dark = sky.dark
        val lights = Px.smooth(.3f, .8f, dark)
        val roadY = horizon + 2.6f * d
        val follow = Ambient.carFollow(seed, p.pass)
        val s = d / RoadSkySprites.CAR_PX_PER_DP
        val g = RoadSkySprites.GLOW.toFloat()
        val glow = SceneSprites.glow
        for (k in 0 until 2) {
            if (k == 1 && follow <= 0f) break
            val v = pool[Ambient.vehicle(seed, p.pass, k, pool.size)]
            val sp = v.sprite
            val x = (p.x - p.dir * follow * k) * w
            val bob = if (seconds > 0f) sin(seconds * 9f + k * 2.3f + p.pass) * .14f * d else 0f
            withTransform({
                translate(x, roadY + bob)
                scale(s * p.dir, s, Offset.Zero)
            }) {
                // in the sprite's pixels, the origin under the vehicle's middle on the road
                val ax = -sp.w / 2f
                val ay = -sp.roadY
                val fx = ax + sp.frontX
                val bx = ax + sp.backX
                val ly = ay + sp.lightY
                if (lights > 0f) {
                    // the beam thrown forward and a little down the road, and its pool on it
                    val bs = 124f / RoadSkySprites.BEAM_W
                    withTransform({
                        translate(fx - 1f, ly + 1f)
                        rotate(5f, Offset.Zero)
                        scale(bs, bs * .75f, Offset.Zero)
                    }) {
                        drawImage(
                            SceneSprites.beam,
                            Offset(-6f, -RoadSkySprites.BEAM_H / 2f),
                            .62f * lights,
                            colorFilter = headlightTint,
                        )
                    }
                    withTransform({
                        translate(fx + 24f, 1.5f)
                        scale(64f / g, 9f / g, Offset.Zero)
                    }) {
                        drawImage(
                            glow,
                            Offset(-g / 2f, -g / 2f),
                            .38f * lights,
                            colorFilter = headlightTint,
                        )
                    }
                    // the tail-light's red on the road
                    withTransform({
                        translate(bx + 2f, 1.5f)
                        scale(40f / g, 7f / g, Offset.Zero)
                    }) {
                        drawImage(
                            glow,
                            Offset(-g / 2f, -g / 2f),
                            .3f * lights,
                            colorFilter = taillightTint,
                        )
                    }
                }
                val at = Offset(ax, ay)
                if (dark < 1f) drawImage(v.day, at, colorFilter = hazeTint)
                if (dark > 0f) drawImage(v.night, at, dark)
                if (lights > 0f) {
                    // the headlight: a wide soft bloom and a hot core; the tail-light small and red
                    withTransform({
                        translate(fx, ly)
                        scale(42f / g, 42f / g, Offset.Zero)
                    }) {
                        drawImage(
                            glow,
                            Offset(-g / 2f, -g / 2f),
                            .8f * lights,
                            colorFilter = headlightTint,
                        )
                    }
                    withTransform({
                        translate(fx, ly)
                        scale(14f / g, 14f / g, Offset.Zero)
                    }) {
                        drawImage(
                            glow,
                            Offset(-g / 2f, -g / 2f),
                            lights,
                            colorFilter = headlightTint,
                        )
                    }
                    drawCircle(SceneInk.Strobe, 1.5f, Offset(fx, ly), lights)
                    withTransform({
                        translate(bx, ly)
                        scale(22f / g, 22f / g, Offset.Zero)
                    }) {
                        drawImage(
                            glow,
                            Offset(-g / 2f, -g / 2f),
                            .7f * lights,
                            colorFilter = taillightTint,
                        )
                    }
                    drawCircle(SceneInk.Taillight, 1.4f, Offset(bx, ly), lights)
                }
            }
        }
    }
}
