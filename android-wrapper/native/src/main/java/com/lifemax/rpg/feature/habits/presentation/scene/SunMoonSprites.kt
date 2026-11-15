package com.lifemax.rpg.feature.habits.presentation.scene

import com.lifemax.rpg.feature.habits.domain.MoonPhase
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * One square layer of the sun or the moon, white at every alpha so the hour tints it: [size] pixels
 * a side, [pxPerDp] of them to a dp, centred on the body's middle.
 */
internal class SkyLayer(val size: Int, val pxPerDp: Float, val pixels: IntArray) {
    /** What the layer comes to in dp. */
    val dp: Float
        get() = size / pxPerDp

    val bytes: Int
        get() = size * size * 4
}

/**
 * The sun, painted once: its [disc] (the whole of it, feathered), the [core] (limb darkening: the
 * middle is brighter than the rim), a thin bright [limb], soft long [rays] (turned slowly as
 * drawn), the [corona] (streamers of fine radial structure) and the wide [glow] the whole sky near
 * it takes, with a faint lens streak in it.
 */
internal class SunSprite(
    val disc: SkyLayer,
    val core: SkyLayer,
    val limb: SkyLayer,
    val rays: SkyLayer,
    val corona: SkyLayer,
    val glow: SkyLayer,
) {
    fun bytes(): Int = disc.bytes + core.bytes + limb.bytes + rays.bytes + corona.bytes + glow.bytes
}

/**
 * The moon, painted once: its [body] (the disc, the colour of the maria), the [high] highlands and
 * ray craters over it (its albedo, so the seas show as the body's colour through it), and its soft
 * [halo]. The dark of the moon is a layer of its own, one per phase: [SunMoonSprites.moonShade].
 */
internal class MoonSprite(val body: SkyLayer, val high: SkyLayer, val halo: SkyLayer) {
    fun bytes(): Int = body.bytes + high.bytes + halo.bytes
}

/**
 * The sun and moon sprite generator: fields of light and albedo written once into ARGB, with no
 * edge that is not feathered by the field itself. Pure Kotlin; [SceneSprites] turns the layers into
 * images and the hour's colours are given as tints when they are drawn.
 */
internal object SunMoonSprites {
    /** The sun's disc, in dp. */
    const val SUN_R = 13f

    /** The moon's disc, in dp. */
    const val MOON_R = 11f

    private const val SEED = 7919

    /**
     * Bytes the pool holds: the sun (three disc layers, the rays, the corona, the glow), the moon
     * (its body, highlands and halo) and one picture of its dark side.
     */
    fun bytes(): Int = (3 * 96 * 96 + 2 * 160 * 160 + 128 * 128 + 3 * 80 * 80 + 128 * 128) * 4

    // ---- the sun

    fun sun(): SunSprite {
        val r0 = SUN_R
        val disc = layer(96, 3f) { _, _, r, _ -> Px.smooth(r0 + .5f, r0 - .4f, r) }
        val core =
            layer(96, 3f) { x, y, r, _ ->
                val mu = sqrt(max(0f, 1f - (r / r0) * (r / r0)))
                // limb darkening, and the faintest granulation so the disc is painted, not filled
                val grain = (Noise.fbm(x * 1.3f, y * 1.3f, SEED + 1, 3) - .5f) * .07f
                Px.smooth(r0 + .2f, r0 - .7f, r) * Px.clamp01((.1f + .9f * mu) * (1f + grain))
            }
        val limb =
            layer(96, 3f) { _, _, r, _ ->
                val e = (r - (r0 - .55f)) / .75f
                Px.smooth(r0 + .4f, r0 - .3f, r) * exp(-e * e) * .55f
            }
        val rays =
            layer(160, 2f) { _, _, r, th ->
                val reach = 40f
                if (r >= reach) return@layer 0f
                val t = Px.clamp01((r - r0 * .9f) / (reach - r0 * .9f))
                val radial = (1f - t) * (1f - t) * (1f - t) * Px.smooth(r0 * .7f, r0 * 1.25f, r)
                val c = cos(th)
                val s = sin(th)
                // broad lobes, finer rays inside them, and a few long soft spikes
                val lobes = Noise.fbm(c * 2.5f + 11f, s * 2.5f + 7f, SEED + 2, 3)
                val fine = Noise.fbm(c * 7f + 3f + r * .02f, s * 7f + 1f, SEED + 3, 2)
                var spikes = 0f
                for (k in 0 until 9) {
                    val a =
                        k * (2f * PI.toFloat() / 9f) + .37f + Noise.value(k * 1.7f, 0f, SEED) * .5f
                    var da =
                        ((th - a) % (2f * PI.toFloat()) + 3f * PI.toFloat()) % (2f * PI.toFloat()) -
                            PI.toFloat()
                    da /= .07f + .04f * Noise.value(k * 3.1f, 1f, SEED)
                    val len = .55f + .45f * Noise.value(k * 2.3f, 2f, SEED)
                    spikes += exp(-da * da) * Px.smooth(len, len * .35f, t)
                }
                val along = .7f + .6f * Noise.fbm(c * 4f + r * .09f, s * 4f, SEED + 4, 2)
                val a =
                    radial *
                        (.12f +
                            Px.smooth(.4f, .78f, lobes) * .65f +
                            Px.smooth(.5f, .82f, fine) * .45f +
                            spikes * .55f) *
                        along
                Px.clamp01(a) * .8f
            }
        val corona =
            layer(160, 1.25f, blur = 2) { _, _, r, th ->
                val c = cos(th)
                val s = sin(th)
                val out = max(0f, r - r0)
                // the bright inner corona, then streamers reaching out, both with fine structure
                val inner = exp(-out / (r0 * .5f))
                val outer = exp(-out / (r0 * 1.7f))
                val coarse =
                    Px.smooth(.32f, .86f, Noise.fbm(c * 5f + out * .02f, s * 5f + 17f, SEED + 5, 3))
                val fine =
                    Px.smooth(
                        .48f,
                        .84f,
                        Noise.fbm(c * 14f + out * .03f, s * 14f + 5f, SEED + 6, 2),
                    )
                val a = inner * (.4f + .25f * coarse) + outer * (.06f + .38f * coarse + .3f * fine)
                // solid under the disc (where the angle, and so the structure, turns too fast)
                Px.mix(1f, Px.clamp01(a), Px.smooth(r0 * .5f, r0, r)) * Px.smooth(62f, 42f, r)
            }
        val glow =
            layer(128, .3f) { x, y, r, _ ->
                val wide = exp(-(r / 120f) * (r / 120f))
                val near = exp(-(r / 48f) * (r / 48f))
                // a faint anamorphic streak: the lens the scene is seen through
                val streak = exp(-(y / 13f) * (y / 13f)) * exp(-(x / 130f) * (x / 130f))
                Px.clamp01(wide * .5f + near * .5f + streak * .13f) * Px.smooth(205f, 150f, r)
            }
        return SunSprite(disc, core, limb, rays, corona, glow)
    }

    // ---- the moon

    private class Sea(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val k: Float)

    private class Crater(
        val cx: Float,
        val cy: Float,
        val r: Float,
        val rays: Int,
        val reach: Float,
    )

    /**
     * The seas of the near side, north up, in shares of the radius: Imbrium, Procellarum, Frigoris,
     * Serenitatis, Tranquillitatis, Crisium, Fecunditatis, Nectaris, Vaporum, Nubium, Humorum. They
     * run into one another where they do on the moon.
     */
    private val SEAS =
        listOf(
            Sea(-.3f, -.36f, .3f, .28f, 1f),
            Sea(-.62f, -.02f, .2f, .42f, .85f),
            Sea(-.1f, -.72f, .4f, .06f, .55f),
            Sea(.12f, -.42f, .19f, .18f, .95f),
            Sea(.38f, -.14f, .22f, .22f, .95f),
            Sea(.72f, -.3f, .1f, .13f, .85f),
            Sea(.55f, .2f, .13f, .2f, .8f),
            Sea(.36f, .3f, .1f, .1f, .7f),
            Sea(.02f, -.1f, .11f, .08f, .7f),
            Sea(-.3f, .32f, .18f, .14f, .8f),
            Sea(-.52f, .42f, .1f, .1f, .7f),
        )

    /** The bright ray craters: Tycho, Copernicus, Kepler, Aristarchus. */
    private val CRATERS =
        listOf(
            Crater(-.12f, .7f, .055f, 11, .62f),
            Crater(-.42f, .12f, .065f, 8, .34f),
            Crater(-.66f, .08f, .035f, 6, .2f),
            Crater(-.6f, -.28f, .03f, 0, 0f),
        )

    fun moon(): MoonSprite {
        val r0 = MOON_R
        val body = layer(80, 3f) { _, _, r, _ -> Px.smooth(r0 + .35f, r0 - .35f, r) }
        val high =
            layer(80, 3f, blur = 1) { x, y, r, _ ->
                // a hair wider than the body, so the limb is a soft light edge, not a dark ring
                // of the body's colour showing under the highlands' feather
                val edge = Px.smooth(r0 + .4f, r0 - .3f, r)
                if (edge <= 0f) return@layer 0f
                val u = x / r0
                val v = y / r0
                // the seas: irregular low-frequency dark, and the named ones, their shores warped
                val wx = u + (Noise.fbm(u * 2.1f + 3f, v * 2.1f, SEED + 10, 2) - .5f) * .16f
                val wy = v + (Noise.fbm(u * 2.1f, v * 2.1f + 5f, SEED + 11, 2) - .5f) * .16f
                // a field of the seas, then one shore through it, so neighbours run together and
                // the coast is irregular: a map, not a scatter of ovals
                var field = (Noise.fbm(u * 1.6f + 7f, v * 1.6f, SEED + 12, 3) - .5f) * .5f
                for (s in SEAS) {
                    val dx = (wx - s.cx) / s.rx
                    val dy = (wy - s.cy) / s.ry
                    field += Px.smooth(1.35f, .55f, sqrt(dx * dx + dy * dy)) * s.k
                }
                // the southern highlands stay bright
                field -= Px.smooth(.2f, .9f, v) * .5f
                val sea = Px.smooth(.3f, .62f, field)
                // mottling inside the seas and roughness on the highlands
                // the highlands sit under full white, so the craters and their rays can be brighter
                val mottle = (Noise.fbm(u * 5f, v * 5f, SEED + 13, 3) - .5f) * .26f
                val rough = (Noise.fbm(u * 11f, v * 11f, SEED + 14, 3) - .5f) * .2f
                var albedo = .8f - .6f * sea + mottle * (.4f + .6f * sea) + rough * (1f - .7f * sea)
                // a few small craters: a bright rim and a darker floor
                for (k in 0 until 26) {
                    val cx = (Noise.value(k * 1.3f, 9f, SEED) - .5f) * 1.7f
                    val cy = (Noise.value(k * 1.3f, 19f, SEED) - .5f) * 1.7f
                    if (cx * cx + cy * cy > .9f) continue
                    val cr = .025f + .045f * Noise.value(k * 1.3f, 29f, SEED)
                    val dd = sqrt((u - cx) * (u - cx) + (v - cy) * (v - cy))
                    if (dd > cr * 2f) continue
                    // rims no finer than a pixel and a half, so nothing sparkles
                    val e = (dd - cr) / max(cr * .35f, .045f)
                    albedo += exp(-e * e) * .14f - Px.smooth(cr, cr * .45f, dd) * .14f
                }
                // the ray craters: a bright floor, soft ejecta, rays fading out along their length
                for (c in CRATERS) {
                    val dx = u - c.cx
                    val dy = v - c.cy
                    val dd = sqrt(dx * dx + dy * dy)
                    albedo += Px.smooth(c.r + .03f, c.r - .03f, dd) * .5f
                    albedo += exp(-(dd / (c.r * 2.6f)) * (dd / (c.r * 2.6f))) * .3f
                    if (c.rays > 0 && dd > c.r * .8f && dd < c.reach) {
                        val th = atan2(dy, dx)
                        var ray = 0f
                        for (k in 0 until c.rays) {
                            val a =
                                k * (2f * PI.toFloat() / c.rays) +
                                    Noise.value(k * 2.2f, 3f + c.rays, SEED) * .6f
                            var da =
                                ((th - a) % (2f * PI.toFloat()) + 3f * PI.toFloat()) %
                                    (2f * PI.toFloat()) - PI.toFloat()
                            da /= .09f
                            val len =
                                c.reach * (.55f + .45f * Noise.value(k * 1.9f, 7f + c.rays, SEED))
                            ray += exp(-da * da) * Px.smooth(len, c.r, dd)
                        }
                        val flicker = .7f + .6f * Noise.fbm(dx * 9f, dy * 9f, SEED + 15, 2)
                        // (the rays start a little out from the rim, where they are wide enough)
                        albedo += Px.clamp01(ray) * .5f * flicker * Px.smooth(c.r, c.r * 3f, dd)
                    }
                }
                // a touch of limb darkening, so it reads as a sphere
                val mu = sqrt(max(0f, 1f - u * u - v * v))
                edge * Px.clamp01(albedo) * (.76f + .24f * mu)
            }
        // the halo is drawn over the moon (added), so it is clear of the disc: the dark side
        // stays dark, and the dark of the moon cuts no arc out of it
        val halo =
            layer(128, 1f) { _, _, r, _ ->
                val out = max(0f, r - r0)
                Px.clamp01(exp(-out / (r0 * .7f)) * .55f + exp(-(r / 34f) * (r / 34f)) * .45f) *
                    Px.smooth(r0 - .6f, r0 + .5f, r) *
                    Px.smooth(62f, 42f, r)
            }
        return MoonSprite(body, high, halo)
    }

    /**
     * The dark of the moon at [step] of [MoonPhase.STEPS] (0 new, a quarter of them on first
     * quarter, half on full): the night side as a soft terminator over the disc, lit on the right
     * while waxing. Drawn in the sky's colour with a little of the moon showing through it: the
     * earthshine.
     */
    fun moonShade(step: Int): SkyLayer {
        val r0 = MOON_R
        val p = step.toFloat() / MoonPhase.STEPS
        // the sun's longitude on the moon: 0 at full, 90 degrees (to the right) at first quarter
        val sunLon = (PI - p * 2.0 * PI).toFloat()
        val twoPi = 2f * PI.toFloat()
        // the long shadows of the ground as the light grazes it: none at full (nor new)
        val grazing = abs(sin(p * 2.0 * PI)).toFloat() * .2f
        return layer(80, 3f) { x, y, r, _ ->
            // over the whole of the body's and the highlands' feathered edges, so no rim of
            // either shows on the dark side
            val edge = Px.smooth(r0 + .8f, r0 + .45f, r)
            if (edge <= 0f) return@layer 0f
            val u = x / r0
            val v = y / r0
            val z = sqrt(max(0f, 1f - u * u - v * v))
            // the night side is the half of the sphere turned from the sun, by longitude, so the
            // limb of a full moon is as bright as its middle (as it is: the moon has no limb
            // darkening to speak of); soft at the terminator
            val lon = atan2(u, z)
            val away = abs(((lon - sunLon) % twoPi + 3f * PI.toFloat()) % twoPi - PI.toFloat())
            val lit = PI.toFloat() / 2f - away
            // (the softness lies on the night side, so the limb of a full moon is wholly lit)
            val dark = 1f - Px.smooth(-.24f, 0f, lit)
            val graze = Px.smooth(.5f, .04f, lit) * grazing
            edge * Px.clamp01(dark + graze * (1f - dark))
        }
    }

    // ---- the brush

    /**
     * A white layer whose alpha is [f] of the point's place from the middle (in dp: across, down,
     * the distance and the angle), softened by [blur] passes of a light blur (for fields with
     * structure finer than the layer's pixels), feathered to nothing at the border.
     */
    private inline fun layer(
        size: Int,
        pxPerDp: Float,
        blur: Int = 0,
        f: (x: Float, y: Float, r: Float, theta: Float) -> Float,
    ): SkyLayer {
        val field = FloatArray(size * size)
        val mid = (size - 1) / 2f
        for (j in 0 until size) for (i in 0 until size) {
            val x = (i - mid) / pxPerDp
            val y = (j - mid) / pxPerDp
            field[j * size + i] = Px.clamp01(f(x, y, sqrt(x * x + y * y), atan2(y, x)))
        }
        for (pass in 0 until blur) blur(field, size)
        val px = IntArray(size * size)
        for (j in 0 until size) for (i in 0 until size) {
            val border = min(min(i, size - 1 - i), min(j, size - 1 - j)).toFloat()
            val a = field[j * size + i] * Px.smooth(0f, 4f, border)
            if (a > 0f) px[j * size + i] = Px.argbF(a, 1f, 1f, 1f)
        }
        return SkyLayer(size, pxPerDp, px)
    }

    /** A light 3x3 blur of a square field. */
    private fun blur(d: FloatArray, w: Int) {
        val c = d.copyOf()
        for (y in 1 until w - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            d[i] =
                (c[i] * 4f +
                    c[i - 1] * 2f +
                    c[i + 1] * 2f +
                    c[i - w] * 2f +
                    c[i + w] * 2f +
                    c[i - w - 1] +
                    c[i - w + 1] +
                    c[i + w - 1] +
                    c[i + w + 1]) / 16f
        }
    }
}
