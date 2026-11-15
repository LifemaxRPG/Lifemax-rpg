package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import com.lifemax.rpg.feature.habits.domain.BuildingLayout
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.CarPass
import com.lifemax.rpg.feature.habits.domain.CycleRig
import com.lifemax.rpg.feature.habits.domain.DogRig
import com.lifemax.rpg.feature.habits.domain.Furniture
import com.lifemax.rpg.feature.habits.domain.FurnitureKind
import com.lifemax.rpg.feature.habits.domain.KeyLight
import com.lifemax.rpg.feature.habits.domain.NearLight
import com.lifemax.rpg.feature.habits.domain.Passer
import com.lifemax.rpg.feature.habits.domain.PeopleLod
import com.lifemax.rpg.feature.habits.domain.Perspective
import com.lifemax.rpg.feature.habits.domain.SceneLight
import com.lifemax.rpg.feature.habits.domain.StreetGait
import com.lifemax.rpg.feature.habits.domain.StreetLayout
import com.lifemax.rpg.feature.habits.domain.StreetLife
import com.lifemax.rpg.feature.habits.domain.StreetRig
import com.lifemax.rpg.feature.habits.domain.Stroller
import kotlin.math.abs
import kotlin.math.max

/**
 * The street's life on the canvas: the people on the pavement ([StreetLife.passers]), their shadows
 * (from the key light, in the painter's shadow layer, and from the street lamps at night), the near
 * car on the front road and, at night, its headlights' sweep over the facades. Everything drawn is
 * a baked image ([SceneSprites]) under a transform or a path; the per-frame state lives in fields
 * the schedule fills ([update]), so nothing is made while drawing. Street units, the pavement line
 * at y = 0, the kerb at [BuildingPainter.PAVEMENT].
 */
internal class StreetLifePainter {
    /** This frame's passers and car, filled by [update]. */
    val passers = Array(StreetLife.MOST) { Passer() }
    var count = 0
    val car = CarPass()

    /** The key light and where it stands, as [BuildingPainter] has them: set by the stage. */
    var light: KeyLight? = null
    var lightX = 0f
    var lightSpan = 1000f
    var lightFrom = -1f

    /** The painter whose lamp posts and trees throw the headlights' shadows up the walls. */
    var painter: BuildingPainter? = null

    private val fall = Matrix()
    private val unit = 1f / StreetSprites.PPU
    private val carUnit = 1f / StreetSprites.CAR_PPU

    /** The shade a walker throws under a street lamp, and the warmth the lamp lends them. */
    private val lampShadeFilter = ColorFilter.tint(Color(0xFF0A0806), BlendMode.SrcIn)
    private val warmFilter = ColorFilter.tint(Color(0xFFFFB866), BlendMode.SrcIn)
    private val dayRimFilter = ColorFilter.tint(Color(0xFFF2F5FA), BlendMode.SrcIn)

    /**
     * A walker by day: the figures are baked warm and glowing for lamp-lit nights; in daylight a
     * person in the street is a cool, flat, dark shape with only a little modelling, so their tones
     * are taken to cool greys by brightness and the baked glow round them is cut away.
     */
    private val dayFigure =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    .105f,
                    .206f,
                    .039f,
                    0f,
                    20f,
                    .114f,
                    .224f,
                    .042f,
                    0f,
                    24f,
                    .135f,
                    .266f,
                    .05f,
                    0f,
                    32f,
                    0f,
                    0f,
                    0f,
                    1.7f,
                    -178f,
                )
            )
        )
    private val moonRimFilter = ColorFilter.tint(Color(0xFFC9D6FF), BlendMode.SrcIn)

    /** A foot's soft contact shadow: a unit disc fading out. */
    private val contact =
        Brush.radialGradient(
            listOf(Color(0x8C000000), Color(0x33000000), Color.Transparent),
            Offset.Zero,
            1f,
        )

    /**
     * The headlights' pool on the road and the wash on the facades: unit discs, drawn additively.
     */
    private val pool =
        Brush.radialGradient(
            listOf(Color(0xFFFFE6B4).copy(alpha = .6f), Color.Transparent),
            Offset.Zero,
            1f,
        )
    private val wash =
        Brush.radialGradient(
            listOf(
                Color(0xFFFFD9A0).copy(alpha = .55f),
                Color(0xFFFFD9A0).copy(alpha = .18f),
                Color.Transparent,
            ),
            Offset.Zero,
            1f,
        )
    private val tail =
        Brush.radialGradient(
            listOf(Color(0xFFFF4A30).copy(alpha = .55f), Color.Transparent),
            Offset.Zero,
            1f,
        )

    /** The headlights' beam along the road: a unit cone from its origin at 0, opening to x = 1. */
    private val beam =
        Path().apply {
            moveTo(0f, -.02f)
            lineTo(1f, -.22f)
            lineTo(1f, .22f)
            lineTo(0f, .04f)
            close()
        }
    private val beamBrush =
        Brush.horizontalGradient(
            0f to Color(0xFFFFE9C0).copy(alpha = .34f),
            .35f to Color(0xFFFFE9C0).copy(alpha = .1f),
            1f to Color.Transparent,
            startX = 0f,
            endX = 1f,
        )

    /** The layer the headlights' wash and the shadows in it are drawn in, added over the walls. */
    private val sweepPaint = Paint().apply { blendMode = BlendMode.Plus }
    private val lead = Path()
    private val leadStroke = Stroke(.45f, cap = StrokeCap.Round)
    private val wall = Matrix()

    /** Fills in who is on the street at [seconds]. */
    fun update(seconds: Float, seed: Int, street: StreetLayout) {
        count = StreetLife.passers(seconds, seed, street.start, street.end, passers)
        StreetLife.car(seconds, seed, street.start, street.end, car)
    }

    /** How much bigger someone [lane] out on the pavement is drawn (nearer the kerb, nearer us). */
    private fun perspective(lane: Float): Float = 1f + (lane - StreetLife.LANE) / 10f * .06f

    /** The road's surface under the near car's wheels. */
    private val roadY = BuildingPainter.PAVEMENT + 2.5f + 14f

    // ------------------------------------------------------------------ the figures

    /** A walker's frame and the one beside it at the current origin (the feet), [alpha] strong. */
    private fun DrawScope.walkerFrames(
        p: Passer,
        look: Int,
        frame: Int,
        next: Int,
        mix: Float,
        alpha: Float,
        filter: ColorFilter?,
    ) {
        val a = SceneSprites.walker(look, frame)
        if (p.still > .01f) {
            // settling to a stand: the settle frames over the walk's passing frame
            val q = p.still * StreetRig.SETTLE_FRAMES
            val i = q.toInt().coerceIn(0, StreetRig.SETTLE_FRAMES - 1)
            val t = q - i
            val from = if (i == 0) a else SceneSprites.walker(look, StreetGait.FRAMES + i - 1)
            val to = SceneSprites.walker(look, StreetGait.FRAMES + i)
            drawImage(from.image, Offset(from.dx, from.dy), alpha, colorFilter = filter)
            if (t > .03f) drawImage(to.image, Offset(to.dx, to.dy), alpha * t, colorFilter = filter)
            return
        }
        if (next != frame && mix > .03f) {
            val b = SceneSprites.walker(look, next)
            drawImage(b.image, Offset(b.dx, b.dy), alpha * mix, colorFilter = filter)
        }
        drawImage(a.image, Offset(a.dx, a.dy), alpha, colorFilter = filter)
    }

    private fun DrawScope.dogFrames(p: Passer, alpha: Float, filter: ColorFilter?) {
        if (p.sniff > .01f) {
            val s = SceneSprites.dog(DogRig.SNIFF_FRAME)
            val a = SceneSprites.dog(p.dogFrame)
            drawImage(a.image, Offset(a.dx, a.dy), alpha, colorFilter = filter)
            drawImage(s.image, Offset(s.dx, s.dy), alpha * p.sniff, colorFilter = filter)
            return
        }
        if (p.dogNext != p.dogFrame && p.dogMix > .03f) {
            val b = SceneSprites.dog(p.dogNext)
            drawImage(b.image, Offset(b.dx, b.dy), alpha * p.dogMix, colorFilter = filter)
        }
        val a = SceneSprites.dog(p.dogFrame)
        drawImage(a.image, Offset(a.dx, a.dy), alpha, colorFilter = filter)
    }

    /** The bicycle: its wheels turning, then the frame and the rider. */
    private fun DrawScope.cycleFrames(p: Passer, alpha: Float, filter: ColorFilter?) {
        val r = StreetGait.WHEEL_R * StreetLife.SIZE
        val half = CycleRig.WHEELBASE / 2f * StreetLife.SIZE
        val deg = -p.wheel * 180f / Math.PI.toFloat()
        cycleWheel(-half, r, deg, alpha, filter)
        cycleWheel(half, r, deg, alpha, filter)
        val f =
            (p.crank / (2f * Math.PI.toFloat()) * StreetGait.CRANK_FRAMES + .5f).toInt() %
                StreetGait.CRANK_FRAMES
        val a = SceneSprites.cycle(f)
        drawImage(a.image, Offset(a.dx, a.dy), alpha, colorFilter = filter)
    }

    private fun DrawScope.cycleWheel(
        dx: Float,
        r: Float,
        deg: Float,
        alpha: Float,
        filter: ColorFilter?,
    ) {
        val wheel = SceneSprites.cycleWheel
        withTransform({
            translate(dx * unit * StreetSprites.PPU, -r * unit * StreetSprites.PPU)
            rotate(deg, Offset.Zero)
        }) {
            drawImage(
                SceneSprites.cycleWheelImage,
                Offset(-wheel.ox, -wheel.oy),
                alpha,
                colorFilter = filter,
            )
        }
    }

    /**
     * Everyone of passer [p] at the current origin (their feet), in the sprite's own pixels: the
     * walker (and a companion, a dog on its lead, or a bicycle under them).
     */
    private fun DrawScope.figure(p: Passer, alpha: Float, filter: ColorFilter?) {
        when (p.kind) {
            Stroller.CYCLIST -> cycleFrames(p, alpha, filter)
            else -> walkerFrames(p, p.look, p.frame, p.next, p.mix, alpha, filter)
        }
    }

    /**
     * How [party] places a figure: flat on the pavement, fallen along the key light, or along a
     * lamp's.
     */
    private val PLAIN = 0
    private val KEY = 1
    private val LAMP = 2

    /** The matrix [party] places a figure with, and a lamp's lens for [LAMP]. */
    private val place = Matrix()
    private var lensX = 0f
    private var nudgeX = 0f
    private var nudgeY = 0f

    private fun placed(x: Float, y: Float, how: Int): Matrix {
        place.reset()
        place.translate(x + nudgeX, y + nudgeY)
        when (how) {
            KEY -> {
                val l = light ?: return place
                place *= fallMatrix(l, x)
            }
            LAMP -> {
                fall.reset()
                fall[1, 0] = -NearLight.lampLean(lensX, x)
                fall[1, 1] = -NearLight.lampReach(y * 2f)
                place *= fall
            }
        }
        return place
    }

    /**
     * The whole of passer [p], placed [how] about their feet: the figure and what goes with them (a
     * companion, a dog on its lead, the bicycle under them).
     */
    private fun DrawScope.party(
        p: Passer,
        persp: Float,
        alpha: Float,
        filter: ColorFilter?,
        how: Int,
    ) {
        val s = persp * unit
        withTransform({ transform(placed(p.x, p.lane, how)) }) {
            scale(p.dir * s, s, Offset.Zero) { figure(p, alpha, filter) }
        }
        when (p.kind) {
            Stroller.DOG_WALKER ->
                withTransform({ transform(placed(p.dogX, p.lane, how)) }) {
                    scale(p.dir * s, s, Offset.Zero) { dogFrames(p, alpha, filter) }
                }
            Stroller.PAIR -> {
                val s2 = perspective(p.lane + StreetLife.SIDE_OUT) * unit
                withTransform({
                    transform(
                        placed(
                            p.x - p.dir * StreetLife.SIDE_BACK,
                            p.lane + StreetLife.SIDE_OUT,
                            how,
                        )
                    )
                }) {
                    scale(p.dir * s2, s2, Offset.Zero) {
                        walkerFrames(p, p.look2, p.frame2, p.next2, p.mix2, alpha, filter)
                    }
                }
            }
            else -> {}
        }
    }

    // ------------------------------------------------------------------ shadows

    /**
     * The passers' and the car's shadows from the key light, in the painter's shadow layer: each
     * figure fallen along the light from its feet ([BuildingPainter.shadows] calls this).
     */
    fun DrawScope.keyShadows(light: KeyLight, filter: ColorFilter, from: Float, to: Float) {
        val lean = abs(light.lean) + SceneLight.SPLAY * 1.5f
        val near = lean * 30f + 40f
        for (i in 0 until count) {
            val p = passers[i]
            if (p.x + near < from || p.x - near > to) continue
            party(p, perspective(p.lane), 1f, filter, KEY)
        }
        if (car.on && car.x + 120f > from && car.x - 120f < to) {
            val c = SceneSprites.car(car.body, car.paint)
            withTransform({
                translate(car.x, roadY)
                transform(fallMatrix(light, car.x))
                scale(car.dir * carUnit, carUnit, Offset.Zero)
            }) {
                drawImage(c.day, Offset(-c.sprite.w / 2f, -c.sprite.groundY), colorFilter = filter)
            }
        }
    }

    private fun fallMatrix(light: KeyLight, x: Float): Matrix {
        fall.reset()
        fall[1, 0] = -SceneLight.lean(light, (x - lightX) / lightSpan)
        fall[1, 1] = -light.reach
        return fall
    }

    /**
     * At night, each passer's shadow from the street lamps near them: the lens is a point light, so
     * the shadow leans away from it (behind them as they come, in front once past), stretches with
     * the distance and fades with the lamp's pool. Drawn after the key light's shadows and before
     * anything that stands on the pavement.
     */
    fun DrawScope.lampShadows(dark: Float, lamps: List<Furniture>, from: Float, to: Float) {
        if (dark <= .2f || count == 0) return
        for (k in lamps.indices) {
            val f = lamps[k]
            if (f.kind != FurnitureKind.LAMP) continue
            val lensX = f.x + NearLight.LAMP_LENS
            if (
                lensX < from - NearLight.LAMP_REACH - 40f || lensX > to + NearLight.LAMP_REACH + 40f
            )
                continue
            for (i in 0 until count) {
                val p = passers[i]
                val strength = NearLight.lampFall(p.x - lensX)
                if (strength < .02f) continue
                this@StreetLifePainter.lensX = lensX
                party(p, perspective(p.lane), .6f * dark * strength, lampShadeFilter, LAMP)
            }
        }
    }

    // ------------------------------------------------------------------ the people themselves

    /**
     * The people on the pavement, behind the lamp posts and trees: each with a soft contact shadow
     * under either foot, a rim of light on the side the key light is, and the warmth of the nearest
     * street lamp at night. Nobody when the street is drawn small ([lod]).
     */
    fun DrawScope.people(
        sky: SkyColors,
        lamps: List<Furniture>,
        from: Float,
        to: Float,
        lod: PeopleLod,
    ) {
        if (count == 0 || lod == PeopleLod.NONE || lod == PeopleLod.DOT) return
        val key = light
        val dark = sky.dark
        for (i in 0 until count) {
            val p = passers[i]
            if (p.x + 60f < from || p.x - 60f > to) continue
            val persp = perspective(p.lane)
            // the nearest lamp's light on them
            var lamp = 0f
            var lampSide = 0f
            if (dark > .2f)
                for (k in lamps.indices) {
                    val f = lamps[k]
                    if (f.kind != FurnitureKind.LAMP) continue
                    val d = p.x - (f.x + NearLight.LAMP_LENS)
                    val s = NearLight.lampFall(d) * dark
                    if (s > lamp) {
                        lamp = s
                        lampSide = if (d > 0f) -1f else 1f
                    }
                }
            // the feet's contact shadows (the walker's; a bicycle and a dog have their own)
            if (p.kind != Stroller.CYCLIST) contacts(p, p.look, p.frame, p.x, p.lane, persp)
            if (p.kind == Stroller.PAIR)
                contacts(
                    p,
                    p.look2,
                    p.frame2,
                    p.x - p.dir * StreetLife.SIDE_BACK,
                    p.lane + StreetLife.SIDE_OUT,
                    perspective(p.lane + StreetLife.SIDE_OUT),
                )
            // how much of the day's look they take: all of it by day, none once it is dark
            val day = (1f - dark * 1.6f).coerceIn(0f, 1f)
            // the rim on the lit side: the figure once more, a sliver towards the light (by day a
            // faint cool edge, at night the lamp's warmth or the moon's blue)
            val rim =
                if (key != null)
                    key.strength * (1f - .5f * key.cool) * (1f - dark * .5f) * (1f - .55f * day)
                else 0f
            if (rim > .03f || lamp > .03f) {
                val side = if (lamp > rim) lampSide else lightFrom
                val filter =
                    if (lamp > rim) warmFilter
                    else if (key != null && key.cool > .5f) moonRimFilter else dayRimFilter
                nudgeX = side * .5f
                nudgeY = -.15f
                party(p, persp, max(rim, lamp) * .7f, filter, PLAIN)
                nudgeX = 0f
                nudgeY = 0f
            }
            if (day < .99f) party(p, persp, 1f - day, null, PLAIN)
            if (day > .01f) party(p, persp, day, dayFigure, PLAIN)
            // the lamp's warmth over them, added
            if (lamp > .03f) party(p, persp, lamp * .38f, warmFilter, PLAIN)
            if (p.kind == Stroller.DOG_WALKER) lead(p, persp)
        }
    }

    /**
     * The soft shadow under each of a walker's feet: darkest with the foot down, gone as it lifts.
     */
    private fun DrawScope.contacts(
        p: Passer,
        look: Int,
        frame: Int,
        x: Float,
        y: Float,
        persp: Float,
    ) {
        val j = SceneSprites.walker(look, frame).joints
        val s = StreetLife.SIZE * persp
        foot(x + p.dir * j.footNX * s, y, j.footNH * s)
        foot(x + p.dir * j.footFX * s, y, j.footFH * s)
    }

    private fun DrawScope.foot(x: Float, y: Float, lift: Float) {
        val a = (1f - lift / 2.4f).coerceIn(0f, 1f)
        if (a <= .02f) return
        val w = 2.4f + lift * .6f
        translate(x, y) {
            scale(w, 1.1f, Offset.Zero) {
                drawCircle(contact, 1f, Offset.Zero, alpha = a * a * .9f)
            }
        }
    }

    /** The dog's lead, from the walker's near hand to the dog's collar, sagging a little. */
    private fun DrawScope.lead(p: Passer, persp: Float) {
        val j = SceneSprites.walker(p.look, p.frame).joints
        val s = StreetLife.SIZE * persp
        val hx = p.x + p.dir * j.handNX * s
        val hy = p.lane + j.handNY * s
        val ds = StreetLife.DOG_SIZE * persp
        val cx = p.dogX + p.dir * 3.3f * ds
        val cy = p.lane - 7.2f * ds + (if (p.sniff > 0f) 2.5f * ds * p.sniff else 0f)
        lead.rewind()
        lead.moveTo(hx, hy)
        lead.quadraticBezierTo((hx + cx) / 2f, max(hy, cy) + 1.6f, cx, cy)
        drawPath(lead, Color(0xFF1C1612), style = leadStroke, alpha = .9f)
    }

    // ------------------------------------------------------------------ the near car

    /**
     * The headlights' sweep over the facades at night: a warm wash that slides along the building
     * fronts ahead of the car, and in it the shadows of the lamp posts and trees thrown up the
     * walls from the headlights (low, so they are tall and swing as the car passes). Drawn after
     * the buildings and before what stands in front of them.
     */
    fun DrawScope.sweep(
        sky: SkyColors,
        layouts: List<BuildingLayout>,
        street: StreetLayout,
        from: Float,
        to: Float,
        seconds: Float,
    ) {
        val dark = sky.dark
        if (dark <= .2f || !car.on || layouts.isEmpty()) return
        val c = SceneSprites.car(car.body, car.paint)
        val lampX = car.x + car.dir * (c.sprite.length / 2f - 1.5f)
        val spot = NearLight.sweepSpot(lampX, car.dir)
        if (spot + NearLight.SWEEP_REACH < from || spot - NearLight.SWEEP_REACH > to) return
        var tallest = 0f
        for (i in layouts.indices) tallest = max(tallest, layouts[i].height)
        val half = B.W / 2f
        drawIntoCanvas { it.saveLayer(Rect(from, -tallest - 10f, to, 0f), sweepPaint) }
        val p = painter
        for (i in layouts.indices) {
            val cx = street.centers[i]
            // the wash covers the front and whichever side face the building shows
            val shift = p?.shiftAt(cx) ?: B.DX
            val left = cx + Perspective.left(shift)
            val right = cx + Perspective.right(shift)
            if (right < from || left > to) continue
            val strength = NearLight.sweep(lampX, car.dir, cx)
            if (strength < .02f) continue
            val h = layouts[i].height
            clipRect(left, -h, right, 0f) {
                translate(spot, -6f) {
                    scale(NearLight.SWEEP_REACH * .8f, 70f, Offset.Zero) {
                        drawRect(
                            wash,
                            Offset(-1f, -1f),
                            Size(2f, 2f),
                            alpha = strength * dark * .9f,
                        )
                    }
                }
            }
        }
        // the shadows of the pavement's furniture up the walls: each scaled about the headlight,
        // on the wall of each building in view
        if (p != null) {
            val scale = NearLight.wallScale()
            wall.reset()
            wall.translate(lampX, -NearLight.HEADLIGHT)
            wall.scale(scale, scale)
            wall.translate(-lampX, NearLight.HEADLIGHT)
            for (i in layouts.indices) {
                val cx = street.centers[i]
                val shift = p.shiftAt(cx)
                val left = cx + Perspective.left(shift)
                val right = cx + Perspective.right(shift)
                if (right < from || left > to) continue
                if (NearLight.sweep(lampX, car.dir, cx) < .02f) continue
                clipRect(left, -layouts[i].height, right, 0f) {
                    withTransform({ transform(wall) }) {
                        for (k in street.furniture.indices) {
                            val f = street.furniture[k]
                            if (f.kind == FurnitureKind.BENCH) continue
                            val strength = NearLight.sweep(lampX, car.dir, f.x)
                            if (strength < .03f) continue
                            with(p) {
                                caster(f, seconds, Color.Black, BlendMode.DstOut, strength * dark)
                            }
                        }
                    }
                }
            }
        }
        drawIntoCanvas { it.restore() }
    }

    private fun DrawScope.carWheel(sp: CarSprite, wx: Float, deg: Float) {
        val wheel = SceneSprites.carWheel
        withTransform({
            translate((wx - sp.w / 2f) * carUnit, -(sp.groundY - sp.wheelY) * carUnit)
            rotate(deg, Offset.Zero)
            scale(carUnit, carUnit, Offset.Zero)
        }) {
            drawImage(SceneSprites.carWheelImage, Offset(-wheel.ox, -wheel.oy))
        }
    }

    /**
     * The near car on the front road: its shadow and pool of light on the road, the beam ahead of
     * it at night, its wheels turning, its body (by day, by night) and its lamps' bloom.
     */
    fun DrawScope.car(sky: SkyColors, from: Float, to: Float) {
        if (!car.on) return
        val c = SceneSprites.car(car.body, car.paint)
        val sp = c.sprite
        val len = sp.length
        if (car.x + len < from - 150f || car.x - len > to + 150f) return
        val dark = sky.dark
        val night = dark > .2f
        translate(car.x, roadY) {
            scale(car.dir, 1f, Offset.Zero) {
                val front = len / 2f - 1.5f
                if (night) {
                    // the pool ahead on the road, then the beam
                    translate(front + 34f, 1.5f) {
                        scale(46f, 9f, Offset.Zero) {
                            drawRect(
                                pool,
                                Offset(-1f, -1f),
                                Size(2f, 2f),
                                alpha = .8f * dark,
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
                    translate(front, -8f) {
                        scale(150f, 60f, Offset.Zero) {
                            drawPath(
                                beam,
                                beamBrush,
                                alpha = .75f * dark,
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
                    translate(-front, -8f) {
                        scale(9f, 4f, Offset.Zero) {
                            drawRect(
                                tail,
                                Offset(-1f, -1f),
                                Size(2f, 2f),
                                alpha = .8f * dark,
                                blendMode = BlendMode.Plus,
                            )
                        }
                    }
                }
                // the wheels, turning
                val deg = -car.wheel * 180f / Math.PI.toFloat()
                carWheel(sp, sp.wheelX0, deg)
                carWheel(sp, sp.wheelX1, deg)
                scale(carUnit, carUnit, Offset.Zero) {
                    val at = Offset(-sp.w / 2f, -sp.groundY)
                    if (dark < .99f) drawImage(c.day, at, alpha = 1f - dark)
                    if (dark > .01f) drawImage(c.night, at, alpha = dark)
                    if (night) drawImage(c.lights, at, alpha = dark, blendMode = BlendMode.Plus)
                }
            }
        }
    }
}
