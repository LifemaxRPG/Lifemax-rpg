package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Building
import com.lifemax.rpg.feature.habits.domain.CloudKind
import com.lifemax.rpg.feature.habits.domain.MoonPhase
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
 * ones with cross-shaped glints, and now and then a shooting one), its sun (a painted disc, a
 * corona of streamers, slow rays, a wide glow) or moon (its seas and ray craters, in the phase of
 * the [day], with earthshine on the dark side), its soft volumetric clouds (lit tops, shaded
 * undersides, graded by the hour); an airplane with a contrail, birds, a blimp; three rows of far
 * city, hazier at their feet, that slide at their own speeds as the street scrolls (depth), with
 * windows of several kinds going on and off at night, antennas, and red warning lights on the
 * tallest; the glow over the horizon; a car on the far road (a painted vehicle with its lights).
 * Every moving thing is a function of [seconds] and the [seed] ([Ambient]); at 0 seconds (motion
 * reduced, or the tab hidden) it is a still picture with nothing passing. Nothing is made per
 * frame: the passes, paths, strokes and brushes are kept here, drawn under transforms (shapes are
 * in dp, brushes in the shape's own units).
 */
internal class Backdrop(private val seed: Int, day: String = "") {
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

    /**
     * The blimp's advertising panel, in dp on the envelope's side (curved with the hull), to clip
     * the light that sweeps along it at night.
     */
    private val bandPath =
        Path().apply {
            val mid = (AirSprites.BAND_L + AirSprites.BAND_R) / 2f
            moveTo(AirSprites.BAND_L + .4f, AirSprites.BAND_T + .3f)
            quadraticBezierTo(
                mid,
                AirSprites.BAND_T + 2.1f,
                AirSprites.BAND_R - .4f,
                AirSprites.BAND_T + .3f,
            )
            lineTo(AirSprites.BAND_R - .4f, AirSprites.BAND_B - .3f)
            quadraticBezierTo(
                mid,
                AirSprites.BAND_B + 1.5f,
                AirSprites.BAND_L + .4f,
                AirSprites.BAND_B - .3f,
            )
            close()
        }

    /** The light that sweeps along the blimp's panel: a soft spot, a unit circle. */
    private val sheen =
        Brush.radialGradient(
            0f to Color.White.copy(alpha = .5f),
            .5f to Color.White.copy(alpha = .16f),
            1f to Color.Transparent,
            center = Offset.Zero,
            radius = 1f,
        )

    /** A bird's outline, written by [BirdShape] and read into [path] each frame. */
    private val birdCmd = FloatArray(BirdShape.CAPACITY)

    // ---- brushes made once per sky (units of their own: unit circles, dp for the airships)

    private var skyKey: Any? = null
    private val tintShade = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private val tintMid = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private val tintLit = arrayOfNulls<ColorFilter>(Ambient.CLOUDS)
    private var airGlow: Brush? = null

    // the tints of the hour for the plane's, the contrail's and the blimp's layers, and how
    // strongly each is drawn
    private val planeTint = arrayOfNulls<ColorFilter>(AirSprites.LAYERS)
    private val planeAlpha = FloatArray(AirSprites.LAYERS)
    private val blimpTint = arrayOfNulls<ColorFilter>(AirSprites.LAYERS)
    private val blimpAlpha = FloatArray(AirSprites.LAYERS)
    private val trailTint = arrayOfNulls<ColorFilter>(AirSprites.TRAIL_LAYERS)
    private val trailAlpha = FloatArray(AirSprites.TRAIL_LAYERS)

    // the birds' ink by how far they are, and the warm rim the low sun gives them
    private val birdInk = Array(3) { SceneInk.Bird }
    private var birdRim = Color.Transparent
    private var birdRimAlpha = 0f

    // ---- the sun and the moon: sprites ([SceneSprites]), tinted once per sky
    private val moonStep = MoonPhase.step(MoonPhase.fraction(day))
    private val moonLight = sqrt(MoonPhase.illumination(MoonPhase.fraction(day)))
    private var orbKey: Any? = null
    private var tintHalo: ColorFilter? = null
    private var tintRays: ColorFilter? = null
    private var tintRim: ColorFilter? = null
    private var tintCore: ColorFilter? = null
    private var tintLimb: ColorFilter? = null
    private var tintMaria: ColorFilter? = null
    private var tintHigh: ColorFilter? = null
    private var tintNight: ColorFilter? = null

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
        if (tone >= .7f && Ambient.birds(seconds, seed, pass))
            birds(pass, sky, w, top, band, seconds)
        // the glow over the horizon: the city's light at night, haze by day
        drawRect(glowBrush!!, Offset(0f, horizon - 60f * d), Size(w, 60f * d))
        drawRect(sky.ground[0], Offset(0f, horizon), Size(w, size.height - horizon))
        drawLine(sky.line, Offset(0f, horizon), Offset(w, horizon), d)
        weather(sky, w, horizon, shift, seconds)
        if (Ambient.car(seconds, seed, pass)) car(pass, sky, w, horizon, seconds)
    }

    // ---- the weather over the sky and the far city
    // ([com.lifemax.rpg.feature.habits.domain.StreetWeather])

    /** How much of the sky is one cloud, and how thick the morning mist is: set by the stage. */
    var cloudiness = 0f
    var mist = 0f

    private var veilKey: Any? = null
    private var veilH = 0f
    private var veilBrush: Brush? = null
    private var veilTint: ColorFilter? = null
    private var fogTint: ColorFilter? = null

    /**
     * An overcast sky is one cloud: a grey veil over everything above the horizon (the sun a pale
     * disc through it, the far city and the horizon's glow flattened), darker overhead and paler at
     * the horizon, with a few low grey stratus dragging across the far city's tops. A misty morning
     * is a bright band of fog over the far city's feet and the lower sky, in two layers drifting
     * apart, which the far rows fade into; the stage lets it clear by mid-morning.
     */
    private fun DrawScope.weather(
        sky: SkyColors,
        w: Float,
        horizon: Float,
        shift: Float,
        seconds: Float,
    ) {
        val cloud = cloudiness
        val fog = mist
        if (cloud < .01f && fog < .01f) return
        val d = density
        if (veilKey !== sky || veilH != horizon || veilBrush == null) {
            // by day the veil is the grey of a rain sky; at night it is the sky's own dark
            val grey = lerp(Color(0xFF9CA3AF), lerp(sky.sky[1], sky.sky[0], .5f), sky.dark * .85f)
            veilBrush =
                Brush.verticalGradient(
                    0f to lerp(grey, Color.Black, .28f),
                    .55f to grey,
                    1f to lerp(grey, Color.White, .12f * (1f - sky.dark)),
                    startY = 0f,
                    endY = horizon,
                )
            veilTint = ColorFilter.tint(lerp(grey, Color.Black, .18f), BlendMode.SrcIn)
            fogTint = ColorFilter.tint(lerp(sky.sky[2], Color.White, .55f), BlendMode.SrcIn)
            veilKey = sky
            veilH = horizon
        }
        if (cloud >= .01f) {
            drawRect(veilBrush!!, Offset.Zero, Size(w, horizon + d), alpha = .82f * cloud)
            // the low ragged ceiling: stratus over the far city, slow, wide, grey
            val pool = SceneSprites.clouds
            val which = CloudSprites.of(CloudKind.STRATUS)
            val span = w * 1.5f
            for (i in 0 until 3) {
                val images = pool[which[i % which.size]]
                val sp = images.sprite
                val s = d * 1.5f / CloudSprites.PX_PER_DP
                val travel = seconds * (3f + i) * d - shift * .02f * d + i * span * .37f
                val x = ((travel % span) + span) % span - span * .2f
                val base = horizon - (96f - i * 14f) * d
                withTransform({
                    translate(x, base)
                    scale(if (i == 1) -s else s, s * .8f, Offset.Zero)
                }) {
                    val at = Offset(-sp.w / 2f, -sp.h * .86f)
                    drawImage(images.shade, at, .5f * cloud, colorFilter = veilTint)
                }
            }
        }
        if (fog >= .01f) {
            val brush = SceneSprites.weather.fog
            val px = d / WeatherSprites.PX_PER_UNIT
            for (layer in 0 until 2) {
                val s = px * (if (layer == 0) 1.5f else 2.4f)
                val tall = WeatherSprites.FOG_H * s
                val drift = seconds * (if (layer == 0) 2.5f else -1.5f) * d - shift * .04f * d
                val a = fog * (if (layer == 0) .85f else .4f)
                withTransform({
                    translate(drift, horizon - tall * .78f)
                    scale(s, s, Offset.Zero)
                }) {
                    // the band, and (the brush holds its last row) on down over the ground's strip
                    val x0 = -drift / s - WeatherSprites.FOG_W
                    drawRect(
                        brush,
                        Offset(x0, 0f),
                        Size(w / s - x0 + WeatherSprites.FOG_W, WeatherSprites.FOG_H + 60f * d / s),
                        alpha = a,
                        colorFilter = fogTint,
                    )
                }
            }
        }
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
        airTints(sky)
        airGlow =
            Brush.radialGradient(
                listOf(sky.cloudLit.copy(alpha = .4f), Color.Transparent),
                Offset.Zero,
                1f,
            )
        skyKey = sky
    }

    /**
     * What the hour does to the things of the air. By day the plane is bright metal, lit from the
     * upper left, its belly taking the sky's light; at dusk the belly warms with the horizon; at
     * night it is a silhouette a shade darker than the sky with its windows lit. The blimp the
     * same, and its panel goes from a painted band to a lit one. The birds are dark against the
     * day, warm-rimmed at dusk; the far ones thin into the air.
     */
    private fun airTints(sky: SkyColors) {
        fun tint(c: Color) = ColorFilter.tint(c, BlendMode.SrcIn)
        val dark = sky.dark
        val dusk = Px.smooth(.2f, .7f, dark)
        val horizonGlow = sky.glow.copy(alpha = 1f)
        run {
            val night = Px.smooth(.6f, 1f, dark)
            val litWindows = Px.smooth(.3f, .75f, dark)
            planeTint[AirSprites.SHADE] =
                tint(
                    lerp(
                        lerp(sky.cloudShade, SceneInk.Plane, .3f),
                        lerp(sky.far[0], Color.Black, .55f),
                        night,
                    )
                )
            planeAlpha[AirSprites.SHADE] = 1f
            planeTint[AirSprites.LIT] = tint(sky.cloudLit)
            planeAlpha[AirSprites.LIT] = 1f - .72f * night
            planeTint[AirSprites.GLINT] = tint(Color.White)
            planeAlpha[AirSprites.GLINT] = 1f - .75f * night
            planeTint[AirSprites.BOUNCE] = tint(lerp(sky.sky[2], horizonGlow, .35f * dusk))
            planeAlpha[AirSprites.BOUNCE] = .62f - .3f * night
            planeTint[AirSprites.GLOW] = tint(lerp(SceneInk.Plane, SceneInk.WindowLit, litWindows))
            planeAlpha[AirSprites.GLOW] = .7f + .25f * litWindows
        }
        trailTint[AirSprites.TRAIL_BODY] = tint(lerp(sky.cloudBody, sky.sky[2], .25f))
        trailAlpha[AirSprites.TRAIL_BODY] = .55f
        trailTint[AirSprites.TRAIL_CORE] = tint(sky.cloudLit)
        trailAlpha[AirSprites.TRAIL_CORE] = .9f
        run {
            val night = Px.smooth(.5f, 1f, dark)
            val lit = Px.smooth(.35f, .8f, dark)
            blimpTint[AirSprites.SHADE] =
                tint(
                    lerp(
                        lerp(sky.cloudShade, SceneInk.BlimpShade, .25f),
                        lerp(sky.cloudShade, sky.far[0], .5f),
                        night,
                    )
                )
            blimpAlpha[AirSprites.SHADE] = 1f
            blimpTint[AirSprites.LIT] = tint(sky.cloudLit)
            blimpAlpha[AirSprites.LIT] = 1f - .62f * night
            blimpTint[AirSprites.GLINT] = tint(Color.White)
            blimpAlpha[AirSprites.GLINT] = 1f - .8f * night
            blimpTint[AirSprites.BOUNCE] = tint(lerp(sky.sky[2], horizonGlow, .4f * dusk))
            blimpAlpha[AirSprites.BOUNCE] = .7f - .35f * night
            blimpTint[AirSprites.GLOW] = tint(lerp(SceneInk.BlimpBand, SceneInk.WindowLit, lit))
            blimpAlpha[AirSprites.GLOW] = .5f + .45f * lit
        }
        val duskInk = lerp(SceneInk.Bird, SceneInk.BirdDusk, dusk)
        for (tier in 0 until 3) {
            val fade = 1f - .22f * tier
            birdInk[tier] = lerp(duskInk, sky.sky[1], (1f - fade) * .6f)
        }
        birdRim = sky.halo.copy(alpha = 1f)
        birdRimAlpha = Px.smooth(.15f, .5f, dark) * Px.smooth(.95f, .6f, dark)
    }

    /**
     * The sun or the moon, from sprites painted once ([SunMoonSprites]) and tinted with the hour's
     * colours. The sun: its wide glow and corona, soft rays turning slowly (still in a still
     * picture), the disc with its limb darkening and thin bright limb; at dusk larger, deeper
     * orange and flatter, in a warmer, wider halo. The moon: its halo (as strong as the phase is
     * bright), its seas and highlands, and the dark of the [moonStep] over them, with earthshine
     * through it.
     */
    private fun DrawScope.heavenlyBody(sky: SkyColors, orb: Offset, seconds: Float) {
        orbTints(sky)
        val d = density
        val haloA = sky.halo.alpha
        if (sky.phase == SkyPhase.NIGHT) {
            val m = SceneSprites.moon
            val shade = SceneSprites.moonShade(moonStep)
            translate(orb.x, orb.y) {
                orbLayer(m.body, m.sprite.body, d, 1f, 1f, tintMaria, BlendMode.SrcOver)
                orbLayer(m.high, m.sprite.high, d, 1f, 1f, tintHigh, BlendMode.SrcOver)
                orbLayer(shade.image, shade.layer, d, 1f, .92f, tintNight, BlendMode.SrcOver)
                // the halo last, added over the sky round the disc (it is clear of the disc)
                orbLayer(
                    m.halo,
                    m.sprite.halo,
                    d,
                    1f,
                    haloA * 1.6f * moonLight,
                    tintHalo,
                    BlendMode.Plus,
                )
            }
        } else {
            val day = sky.phase == SkyPhase.DAY
            val s = SceneSprites.sun
            val grow = if (day) 1f else 1.16f
            translate(orb.x, orb.y) {
                orbLayer(
                    s.glow,
                    s.sprite.glow,
                    d,
                    1f,
                    haloA * (if (day) .8f else 1.1f),
                    tintHalo,
                    BlendMode.Plus,
                )
                orbLayer(
                    s.corona,
                    s.sprite.corona,
                    d,
                    grow,
                    haloA * 2.2f,
                    tintHalo,
                    BlendMode.SrcOver,
                )
                rotate(seconds * 2.5f, Offset.Zero) {
                    orbLayer(
                        s.rays,
                        s.sprite.rays,
                        d,
                        grow,
                        if (day) .55f else .5f,
                        tintRays,
                        BlendMode.SrcOver,
                    )
                }
                orbLayer(s.disc, s.sprite.disc, d, grow, 1f, tintRim, BlendMode.SrcOver)
                orbLayer(
                    s.core,
                    s.sprite.core,
                    d,
                    grow,
                    if (day) 1f else .55f,
                    tintCore,
                    BlendMode.SrcOver,
                )
                orbLayer(
                    s.limb,
                    s.sprite.limb,
                    d,
                    grow,
                    if (day) .8f else .3f,
                    tintLimb,
                    BlendMode.SrcOver,
                )
            }
        }
    }

    /** One layer of the sun or the moon, centred on the origin, [grow] times its size in dp. */
    private inline fun DrawScope.orbLayer(
        image: ImageBitmap,
        l: SkyLayer,
        d: Float,
        grow: Float,
        alpha: Float,
        tint: ColorFilter?,
        blend: BlendMode,
    ) {
        val s = d / l.pxPerDp * grow
        scale(s, s, Offset.Zero) {
            drawImage(
                image,
                Offset(-l.size / 2f, -l.size / 2f),
                alpha = alpha.coerceIn(0f, 1f),
                colorFilter = tint,
                blendMode = blend,
            )
        }
    }

    /** The tints of the sun's and the moon's layers for this sky (kept until the sky changes). */
    private fun orbTints(sky: SkyColors) {
        if (orbKey === sky) return
        val day = sky.phase == SkyPhase.DAY
        fun tint(c: Color) = ColorFilter.tint(c.copy(alpha = 1f), BlendMode.SrcIn)
        tintHalo = tint(sky.halo)
        tintRays = tint(lerp(sky.halo, Color.White, .3f))
        // the disc: white-gold by day, deep orange at dusk, brighter in the middle than at the rim
        tintRim = tint(lerp(sky.orb, Color(0xFFFF8A2A), if (day) .35f else .8f))
        tintCore = tint(lerp(sky.orb, Color.White, if (day) .85f else .3f))
        tintLimb = tint(if (day) Color.White else Color(0xFFFFF0C8))
        // the moon: bluish-grey seas under pale highlands; the night side the sky's own colour
        tintMaria = tint(lerp(sky.orb, Color(0xFF3E4A74), .72f))
        tintHigh = tint(lerp(sky.orb, Color.White, .55f))
        tintNight = tint(lerp(sky.sky[1], sky.sky[0], .5f))
        orbKey = sky
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
     * An airship, bobbing as it drifts: a painted sprite (an envelope shaded as the fabric cylinder
     * it is, with a gleam along its top and the sky's light under its belly, fins, a gondola) drawn
     * in the tints of the hour, its advertising panel lit at night with a light sweeping along it,
     * a propeller and running lights. Small and unhurried.
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
        val images = SceneSprites.blimp
        val sp = images.sprite
        withTransform({
            translate(p.x * w, top + p.y * band + bob * d)
            rotate(pitch, Offset.Zero)
            scale(d * p.dir * .95f, d * .95f, Offset.Zero)
        }) {
            if (dark > .3f)
                scale(46f, 20f, Offset.Zero) {
                    drawCircle(airGlow!!, 1f, Offset(0f, .1f), alpha = (dark - .3f) * 1.1f)
                }
            withTransform({ scale(1f / sp.ppd, 1f / sp.ppd, Offset.Zero) }) {
                val at = Offset(-sp.ox, -sp.oy)
                for (l in 0 until AirSprites.LAYERS) {
                    val a = blimpAlpha[l]
                    if (a > 0f) drawImage(images.layers[l], at, a, colorFilter = blimpTint[l])
                }
            }
            if (dark > .3f) {
                // the light that sweeps along the lit panel
                val k = (dark - .3f) / .7f
                val span = AirSprites.BAND_R - AirSprites.BAND_L + 16f
                val sx =
                    AirSprites.BAND_L - 8f + ((seconds * 4.2f + p.pass * 7f) % span + span) % span
                clipPath(bandPath) {
                    translate(sx, (AirSprites.BAND_T + AirSprites.BAND_B) / 2f + .9f) {
                        scale(7f, 3.2f, Offset.Zero) {
                            drawCircle(sheen, 1f, Offset.Zero, alpha = .55f * k)
                        }
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
                val k = (dark - .3f) / .7f
                drawCircle(SceneInk.Warning, 3.6f, Offset(-28.6f, -14.4f), alpha = .25f * k)
                drawCircle(SceneInk.Warning, 1.1f, Offset(-28.6f, -14.4f), alpha = k)
                drawCircle(SceneInk.NavGreen, 3f, Offset(24.5f, 3.2f), alpha = .22f * k)
                drawCircle(SceneInk.NavGreen, .95f, Offset(24.5f, 3.2f), alpha = k)
                val strobe = Ambient.warning(11, seconds * 1.3f)
                if (strobe > 0f)
                    drawCircle(SceneInk.Strobe, 1.4f, Offset(-27.6f, 13.6f), alpha = strobe)
            }
        }
    }

    /**
     * The airplane: a painted airliner seen from below (a shaded fuselage with a gleam along it and
     * a row of windows, swept wings with engines under them, a fin), in the tints of the hour; by
     * day with a contrail behind it, two cores of exhaust merging into one soft torn trail; at
     * night a silhouette with its windows lit, red and green at the wingtips, a red beacon under
     * its belly and a white strobe at its tail.
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
        val images = SceneSprites.plane
        val sp = images.sprite
        withTransform({
            translate(x, y)
            scale(d * p.dir, d, Offset.Zero)
        }) {
            if (tone > 1.3f) {
                val trail = SceneSprites.contrail
                val ts = trail.sprite
                withTransform({
                    translate(AirSprites.TRAIL_X, AirSprites.TRAIL_Y)
                    scale(1f / ts.ppd, 1f / ts.ppd, Offset.Zero)
                }) {
                    val at = Offset(-ts.ox, -ts.oy)
                    for (l in 0 until AirSprites.TRAIL_LAYERS) drawImage(
                        trail.layers[l],
                        at,
                        trailAlpha[l],
                        colorFilter = trailTint[l],
                    )
                }
            }
            withTransform({ scale(1f / sp.ppd, 1f / sp.ppd, Offset.Zero) }) {
                val at = Offset(-sp.ox, -sp.oy)
                for (l in 0 until AirSprites.LAYERS) {
                    val a = planeAlpha[l]
                    if (a > 0f) drawImage(images.layers[l], at, a, colorFilter = planeTint[l])
                }
            }
            val dark = sky.dark
            if (dark > .3f) {
                val k = (dark - .3f) / .7f
                // red at the near wingtip, green where the far one peeks over the back, each
                // with a little glow round it
                val near = Offset(AirSprites.TIP_NEAR_X, AirSprites.TIP_NEAR_Y)
                val far = Offset(AirSprites.TIP_FAR_X, AirSprites.TIP_FAR_Y)
                drawCircle(SceneInk.Warning, 2f, near, alpha = .3f * k)
                drawCircle(SceneInk.Warning, .55f, near, alpha = k)
                drawCircle(SceneInk.NavGreen, 1.6f, far, alpha = .22f * k)
                drawCircle(SceneInk.NavGreen, .45f, far, alpha = .9f * k)
                // the beacon under the belly, a slow red pulse with a faint glow
                val beacon = Ambient.warning(3, seconds * .8f)
                val belly = Offset(AirSprites.BEACON_X, AirSprites.BEACON_Y)
                drawCircle(SceneInk.Warning, 2.4f, belly, alpha = (.06f + .16f * beacon) * k)
                drawCircle(SceneInk.Warning, .45f, belly, alpha = (.3f + .6f * beacon) * k)
                // and the white strobe at the tail
                val strobe = Ambient.warning(7, seconds * 1.4f)
                if (strobe > 0f) {
                    val tail = Offset(AirSprites.TAIL_X, AirSprites.TAIL_Y)
                    drawCircle(SceneInk.Strobe, 3.4f, tail, alpha = .2f * strobe * k)
                    drawCircle(SceneInk.Strobe, .65f, tail, alpha = strobe)
                }
            }
        }
    }

    /**
     * A flock in a V, in three sizes (the far ones smaller, fainter, slower on the wing): each bird
     * a body with a head and a fanned tail and two wings that bend at the wrist, the hand curling
     * on the downstroke; every bird on its own beat and rising and falling on its own path, one now
     * and then gliding; the whole V drifting a little. Dark against the day's sky, warm-rimmed at
     * dusk.
     */
    private fun DrawScope.birds(
        p: Pass,
        sky: SkyColors,
        w: Float,
        top: Float,
        band: Float,
        seconds: Float,
    ) {
        val d = density
        val x0 = p.x * w + Ambient.flockDriftX(seconds) * d
        val y0 = top + p.y * band + Ambient.flockDriftY(seconds) * d
        withTransform({
            translate(x0, y0)
            scale(d, d, Offset.Zero)
        }) {
            for (i in 0 until Ambient.FLOCK) {
                val size = Ambient.birdSize(i)
                val fade = Ambient.birdFade(i)
                val x = Ambient.birdX(i, p.dir)
                val y = Ambient.birdY(i) + Ambient.birdBob(i, seconds)
                val n = BirdShape.build(x, y, Ambient.flap(i, seconds), size, p.dir, birdCmd)
                path.rewind()
                var k = 0
                while (k < n) {
                    when (birdCmd[k]) {
                        BirdShape.MOVE -> {
                            path.moveTo(birdCmd[k + 1], birdCmd[k + 2])
                            k += 3
                        }
                        BirdShape.LINE -> {
                            path.lineTo(birdCmd[k + 1], birdCmd[k + 2])
                            k += 3
                        }
                        BirdShape.CUBIC -> {
                            path.cubicTo(
                                birdCmd[k + 1],
                                birdCmd[k + 2],
                                birdCmd[k + 3],
                                birdCmd[k + 4],
                                birdCmd[k + 5],
                                birdCmd[k + 6],
                            )
                            k += 7
                        }
                        else -> {
                            path.close()
                            k += 1
                        }
                    }
                }
                if (birdRimAlpha > 0f)
                    translate(-.35f * size, -.4f * size) {
                        drawPath(path, birdRim, alpha = birdRimAlpha * .8f * fade)
                    }
                drawPath(path, birdInk[Ambient.birdTier(i)], alpha = .5f + .5f * fade)
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
