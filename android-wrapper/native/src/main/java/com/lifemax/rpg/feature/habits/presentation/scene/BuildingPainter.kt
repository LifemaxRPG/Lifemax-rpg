package com.lifemax.rpg.feature.habits.presentation.scene

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.drawText
import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.BuildingSpec as B
import com.lifemax.rpg.feature.habits.domain.Dice
import com.lifemax.rpg.feature.habits.domain.Furniture
import com.lifemax.rpg.feature.habits.domain.FurnitureKind
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.PieceKind
import com.lifemax.rpg.feature.habits.domain.RuinWindow
import com.lifemax.rpg.feature.habits.domain.Silhouettes
import com.lifemax.rpg.feature.habits.domain.Trees
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * What changes from frame to frame on a building, kept in one place the stage refreshes (nothing is
 * made per frame). [seconds] runs while the screen shows and motion is on (0 otherwise: a still
 * picture). [landing] is the floor being finished today: [lights] 0 as its first window switches on
 * to 1 when all that will are on, [squash] its settle (1 at the start, back to 0), [burst] 0 to 1
 * for the glow that floods it, [sweep] 0 to 1 for the light that crosses a floor that changed
 * material. [felled] is the storey knocked off a habit being broken, [fell] 0 to 1 as it tumbles.
 * [adding] is a habit just stacked, [drop] 0 to 1 as its floor drops into place.
 */
internal class BuildingMotion {
    var seconds = 0f
    var landing: String? = null
    var lights = 1f
    var squash = 0f
    var burst = 1f
    var sweep = 1f
    var swept = false
    var felled: String? = null
    var fell = 1f
    var adding: String? = null
    var drop = 1f

    fun still(seconds: Float): BuildingMotion {
        this.seconds = seconds
        landing = null
        felled = null
        adding = null
        return this
    }
}

/**
 * Draws the street in building units: x along the street (a building's from its middle), y down
 * from the pavement line (0), so what stands is at negative y.
 */
internal class BuildingPainter {
    private val path = Path()
    private val dash = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
    private val thin = Stroke(.8f)
    private val hairline = Stroke(1.2f)
    private val limb = Stroke(Silhouettes.LIMB, cap = StrokeCap.Round)
    private val glowWide = Stroke(9f, join = StrokeJoin.Round)
    private val glowMid = Stroke(4.5f, join = StrokeJoin.Round)
    private val glowEdge = Stroke(1.8f, join = StrokeJoin.Round)
    private val crackStroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val seamGlow = Stroke(5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val seam = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    private val ghostLine = Stroke(1.4f, pathEffect = dash)
    private val ring = Stroke(2f)
    private val selection = Stroke(2.6f, join = StrokeJoin.Round)

    /** Each activity's poses as paths, made once: solid shapes, and the limbs to stroke. */
    private val shapes: Array<Array<Path>> =
        Array(Activity.entries.size) { a ->
            val poses = Silhouettes.poses(Activity.entries[a])
            Array(poses.size) { f ->
                Path().apply {
                    for (s in poses[f].shapes) {
                        moveTo(s[0], s[1])
                        var i = 2
                        while (i < s.size) {
                            lineTo(s[i], s[i + 1])
                            i += 2
                        }
                        close()
                    }
                }
            }
        }
    private val limbs: Array<Array<Path>> =
        Array(Activity.entries.size) { a ->
            val poses = Silhouettes.poses(Activity.entries[a])
            Array(poses.size) { f ->
                Path().apply {
                    val l = poses[f].limbs
                    var i = 0
                    while (i + 3 < l.size) {
                        moveTo(l[i], l[i + 1])
                        lineTo(l[i + 2], l[i + 3])
                        i += 4
                    }
                }
            }
        }

    /** A lamp's light, drawn as a unit circle under a transform so one brush serves them all. */
    private val pool =
        Brush.radialGradient(
            listOf(SceneInk.LampLight.copy(alpha = .55f), Color.Transparent),
            Offset.Zero,
            1f,
        )
    private val halo =
        Brush.radialGradient(
            listOf(SceneInk.LampLight.copy(alpha = .5f), Color.Transparent),
            Offset.Zero,
            1f,
        )

    /** The cone of light under a lamp's lens (lamp units: lens at y -91.5, pavement at 0). */
    private val cone =
        Path().apply {
            moveTo(6f, -91.5f)
            lineTo(16f, -91.5f)
            lineTo(39f, 0f)
            lineTo(-17f, 0f)
            close()
        }
    private val coneBrush =
        Brush.verticalGradient(
            listOf(SceneInk.LampLight.copy(alpha = .24f), SceneInk.LampLight.copy(alpha = .02f)),
            startY = -91.5f,
            endY = 0f,
        )

    /** A lamp post: a base, a tapering pole with a highlight, a curved arm, a hood. */
    private val lampBase =
        Path().apply {
            moveTo(-5f, 0f)
            lineTo(5f, 0f)
            lineTo(3.4f, -5f)
            lineTo(-3.4f, -5f)
            close()
            addRoundRect(RoundRect(-3.2f, -8.4f, 3.2f, -5f, 1f, 1f))
        }
    private val lampPole =
        Path().apply {
            moveTo(-2.2f, -8f)
            lineTo(2.2f, -8f)
            lineTo(1.25f, -88f)
            lineTo(-1.25f, -88f)
            close()
        }
    private val lampPoleLit =
        Path().apply {
            moveTo(-2.2f, -8f)
            lineTo(-.8f, -8f)
            lineTo(-.45f, -88f)
            lineTo(-1.25f, -88f)
            close()
        }
    private val lampArm =
        Path().apply {
            moveTo(0f, -86f)
            quadraticBezierTo(0f, -95.5f, 9f, -95.2f)
        }
    private val lampHood =
        Path().apply {
            moveTo(3.5f, -94.6f)
            quadraticBezierTo(11.5f, -101f, 20f, -94.6f)
            lineTo(18.6f, -91.8f)
            lineTo(5f, -91.8f)
            close()
        }
    private val armStroke = Stroke(2f, cap = StrokeCap.Round)

    /** A soft contact shadow, a unit circle under a transform. */
    private val contact =
        Brush.radialGradient(
            listOf(SceneInk.Shadow, SceneInk.Shadow.copy(alpha = .22f), Color.Transparent),
            Offset.Zero,
            1f,
        )

    /** Which side the light falls from: -1 the left (the trees are lit on it), 1 the right. */
    var lightFrom = -1f

    /** Each tree variant as paths, made once: trunk, shade, mid, light and glint tones. */
    private val treeArts: Array<TreeArt> = Array(Trees.VARIANTS) { TreeArt(Trees.shapes[it]) }

    /** Asphalt and paving specks, a tile of [SPECK_TILE] street units, made once. */
    private val roadSpecks: List<Offset> =
        List(46) {
            Offset(
                SPECK_TILE * Ambient.hash(11, it, 1),
                PAVEMENT + 4f + 46f * Ambient.hash(11, it, 2),
            )
        }
    private val roadGlints: List<Offset> =
        List(18) {
            Offset(
                SPECK_TILE * Ambient.hash(12, it, 1),
                PAVEMENT + 4f + 46f * Ambient.hash(12, it, 2),
            )
        }
    private val paveSpecks: List<Offset> =
        List(26) {
            Offset(
                SPECK_TILE * Ambient.hash(13, it, 1),
                1.5f + (PAVEMENT - 3f) * Ambient.hash(13, it, 2),
            )
        }

    // ------------------------------------------------------------------ the street itself

    /**
     * The pavement in front of the buildings from [from] to [to] along the street, its kerb, and
     * the road down to [depth].
     */
    fun DrawScope.pavement(sky: SkyColors, from: Float, to: Float, depth: Float) {
        val width = to - from
        drawRect(sky.pavement, Offset(from, 0f), Size(width, PAVEMENT))
        drawRect(sky.kerb, Offset(from, PAVEMENT), Size(width, 2.5f))
        drawRect(sky.road, Offset(from, PAVEMENT + 2.5f), Size(width, depth))
        // the kerb catches the light on its top edge and throws a shadow on the road under it
        drawRect(SceneInk.Highlight, Offset(from, PAVEMENT), Size(width, .9f), alpha = .8f)
        drawRect(SceneInk.Occlusion, Offset(from, PAVEMENT + 2.5f), Size(width, 2.2f), alpha = .6f)
        // paving joints, and the lane's dashes
        var x = (kotlin.math.floor(from / 24f)) * 24f
        while (x < to) {
            drawLine(sky.line, Offset(x, 0f), Offset(x - 5f, PAVEMENT), .8f)
            x += 24f
        }
        x = kotlin.math.floor(from / 46f) * 46f
        while (x < to) {
            drawRect(sky.kerb, Offset(x, PAVEMENT + 30f), Size(22f, 2f), alpha = .6f)
            x += 46f
        }
        // the texture of the asphalt and the slabs, a few deterministic specks (not when the street
        // is drawn small: it would be a haze of dots)
        if (width < 1500f) {
            var tile = kotlin.math.floor(from / SPECK_TILE) * SPECK_TILE
            while (tile < to) {
                translate(tile, 0f) {
                    drawPoints(
                        roadSpecks,
                        PointMode.Points,
                        SceneInk.Occlusion,
                        1.2f,
                        StrokeCap.Round,
                        alpha = .55f,
                    )
                    drawPoints(
                        roadGlints,
                        PointMode.Points,
                        SceneInk.Highlight,
                        1f,
                        StrokeCap.Round,
                        alpha = .35f,
                    )
                    drawPoints(
                        paveSpecks,
                        PointMode.Points,
                        SceneInk.Occlusion,
                        1f,
                        StrokeCap.Round,
                        alpha = .22f,
                    )
                }
                tile += SPECK_TILE
            }
        }
        drawRect(SceneInk.Occlusion, Offset(from, 0f), Size(width, 1.2f))
    }

    /** The pools of light the lamps throw on the pavement at night. */
    fun DrawScope.pools(items: List<Furniture>, from: Float, to: Float, dark: Float) {
        if (dark < .2f) return
        for (i in items.indices) {
            val f = items[i]
            if (f.kind != FurnitureKind.LAMP || f.x < from - 60f || f.x > to + 60f) continue
            translate(f.x + 6f, PAVEMENT * .55f) {
                scale(46f, 12f, Offset.Zero) { drawCircle(pool, 1f, Offset.Zero, alpha = dark) }
            }
        }
    }

    /** A lamp, a tree or a bench on the pavement. */
    fun DrawScope.furniture(f: Furniture, sky: SkyColors, seconds: Float) {
        val dark = sky.dark
        translate(f.x, 0f) {
            when (f.kind) {
                FurnitureKind.LAMP -> {
                    val on = if (dark > .2f) dark else 0f
                    if (on > 0f) drawPath(cone, coneBrush, alpha = on)
                    drawPath(lampBase, SceneInk.Lamp)
                    drawPath(lampPole, SceneInk.Lamp)
                    drawPath(lampPoleLit, SceneInk.Highlight, alpha = .5f)
                    drawPath(lampArm, SceneInk.Lamp, style = armStroke)
                    drawPath(lampHood, SceneInk.Lamp)
                    // the glass lens: dull by day with a glint, lit at night
                    drawRoundRect(
                        lerp(Color(0xFF8A90A0), SceneInk.LampLight, on),
                        Offset(6f, -91.8f),
                        Size(11f, 2.6f),
                        CornerRadius(1.2f, 1.2f),
                    )
                    if (on == 0f)
                        drawRect(Color.White, Offset(7.2f, -91.4f), Size(4f, .7f), alpha = .6f)
                    else
                        translate(11.5f, -89.5f) {
                            scale(26f, 26f, Offset.Zero) {
                                drawCircle(halo, 1f, Offset.Zero, alpha = on)
                            }
                        }
                }
                FurnitureKind.TREE -> tree(0f, f.seed, seconds, 1f, sky)
                FurnitureKind.BENCH -> {
                    // a soft shadow, cast-iron ends, wooden slats lit along their top edges
                    drawOval(SceneInk.Shadow, Offset(-15f, -1.4f), Size(30f, 4f), alpha = .5f)
                    drawRect(SceneInk.Lamp, Offset(-12.6f, -9f), Size(1.9f, 9f))
                    drawRect(SceneInk.Lamp, Offset(10.7f, -9f), Size(1.9f, 9f))
                    drawRect(SceneInk.Lamp, Offset(-12.8f, -19f), Size(1.6f, 10.5f))
                    drawRect(SceneInk.Lamp, Offset(11.2f, -19f), Size(1.6f, 10.5f))
                    drawRect(SceneInk.Lamp, Offset(-14.2f, -12.2f), Size(3.2f, 1.2f))
                    drawRect(SceneInk.Lamp, Offset(11f, -12.2f), Size(3.2f, 1.2f))
                    for (y in BENCH_SLATS) {
                        drawRect(SceneInk.Bench, Offset(-14f, y), Size(28f, 1.9f))
                        drawRect(SceneInk.Highlight, Offset(-14f, y), Size(28f, .6f), alpha = .45f)
                        drawRect(SceneInk.Occlusion, Offset(-14f, y + 1.5f), Size(28f, .4f))
                    }
                }
            }
        }
    }

    /**
     * A street tree at [x], leaning in the wind with motion on: a soft contact shadow, a tapering
     * trunk with a lit side, foliage in four tones lit from the sun or moon side (and, at night,
     * rimmed warm on the other by the street lamps). Which of the variants it is comes from [seed].
     */
    private fun DrawScope.tree(x: Float, seed: Int, seconds: Float, size: Float, sky: SkyColors) {
        val art = treeArts[Trees.variant(seed)]
        val shape = art.shape
        val lean = if (seconds > 0f) Ambient.sway(seed, seconds) else 0f
        val dark = sky.dark
        val warm = shape.warmth
        // the leaves' tones: a green leaning olive, bluer and deeper as the night comes on, the
        // light tone taking the sun's (or the moon's) colour
        fun leaf(day: Color, night: Color) = lerp(lerp(day, SceneInk.LeafOlive, warm), night, dark)
        val deep = leaf(SceneInk.LeafDeep, SceneInk.LeafDeepNight)
        val shade = leaf(SceneInk.LeafShade, SceneInk.LeafShadeNight)
        val mid = leaf(SceneInk.LeafMid, SceneInk.LeafMidNight)
        val light = lerp(leaf(SceneInk.LeafLight, SceneInk.LeafLightNight), sky.orb, .28f)
        val glint = lerp(leaf(SceneInk.LeafGlint, SceneInk.LeafGlintNight), sky.orb, .4f)
        val bark = lerp(SceneInk.BarkDark, SceneInk.BarkDarkNight, dark)
        val barkLit = lerp(SceneInk.BarkLit, SceneInk.BarkLitNight, dark)
        withTransform({
            translate(x, 0f)
            scale(size * -lightFrom, size, Offset.Zero)
        }) {
            // the contact shadow, pushed a little away from the light
            translate(3f, 1f) {
                scale(shape.halfWidth * .72f, 3.6f, Offset.Zero) {
                    drawCircle(contact, 1f, Offset.Zero, alpha = .9f - .3f * dark)
                }
            }
            rotate(lean, Offset(0f, -26f)) {
                drawPath(art.trunk, bark)
                drawPath(art.trunkLit, barkLit)
                if (dark > .2f)
                    translate(1.9f, .5f) {
                        drawPath(art.dark, SceneInk.LampLight, alpha = .45f * dark)
                    }
                drawPath(art.dark, deep)
                drawPath(art.shade, shade)
                drawPath(art.mid, mid)
                drawPath(art.light, light)
                art.glint?.let { drawPath(it, glint, alpha = .85f) }
            }
        }
    }

    // ------------------------------------------------------------------ a building

    /**
     * One building: its park, its lobby, every storey, its roof and what stands on it, and what is
     * playing on it. [live] is today (a past week of the time-lapse has every floor done and lit).
     * [detailed] draws words, people and plaques (a building drawn small has none).
     */
    fun DrawScope.building(
        art: BuildingArt,
        motion: BuildingMotion,
        selected: String?,
        accent: Color,
        live: Boolean,
        detailed: Boolean,
        sky: SkyColors,
    ) {
        val seconds = motion.seconds
        val half = B.W / 2f
        park(art, selected, accent, seconds, sky)
        // the building's shadow on the pavement
        drawRect(SceneInk.Shadow, Offset(-half + 4f, 0f), Size(B.W + B.DX, 5f), alpha = .5f)
        lobby(art, sky, detailed, seconds)
        val lit = lerp(SceneInk.WindowLitDay, SceneInk.WindowLit, sky.dark)
        val glass = lerp(SceneInk.GlassDay, SceneInk.GlassNight, sky.dark)
        val step = Ambient.step(seconds)
        for (i in art.floors.indices) {
            val fa = art.floors[i]
            val b = fa.block
            val landing = motion.landing == b.id && fa.piece.kind == PieceKind.FLOOR
            val adding = motion.adding == b.id
            val drop = if (adding) (1f - motion.drop) * DROP else 0f
            val alpha = if (adding) min(1f, motion.drop * 3f) else 1f
            val squash = if (landing) motion.squash else 0f
            translate(0f, -drop) {
                scale(1f + .025f * squash, 1f - .06f * squash, Offset(0f, -fa.piece.bottom)) {
                    floor(art, fa, motion, landing, alpha, live, detailed, lit, glass, step, sky)
                    if (selected == b.id && fa.piece.labelled) outline(fa.top, fa.bottom, accent)
                }
            }
            if (adding && motion.drop >= .8f) dust(fa.piece.bottom, (motion.drop - .8f) / .2f)
        }
        roof(art, live, detailed, sky, seconds)
        val gone = motion.felled
        if (gone != null && motion.fell < 1f) {
            val top = art.layout.labelled(gone)?.top ?: art.layout.roof
            fallen(top, gone, motion.fell)
        }
    }

    private fun DrawScope.park(
        art: BuildingArt,
        selected: String?,
        accent: Color,
        seconds: Float,
        sky: SkyColors,
    ) {
        val trees = art.layout.trees
        if (trees.isEmpty()) return
        val from = B.W / 2f + B.DX + 4f
        drawRect(SceneInk.Grass, Offset(from, -4f), Size(B.PARK_W - 10f, 4f))
        for (i in trees.indices) {
            val t = trees[i]
            if (selected == t.id)
                drawOval(accent, Offset(t.x - 17f, -5f), Size(34f, 10f), style = ring)
            tree(t.x, t.id.hashCode(), seconds, .8f, sky)
        }
        // a low fence along its front
        var x = from
        while (x <= from + B.PARK_W - 10f) {
            drawRect(SceneInk.Bench, Offset(x, -8f), Size(1.4f, 8f))
            x += 8f
        }
        drawRect(SceneInk.Bench, Offset(from, -6.5f), Size(B.PARK_W - 10f, 1.2f))
    }

    private fun DrawScope.lobby(
        art: BuildingArt,
        sky: SkyColors,
        detailed: Boolean,
        seconds: Float,
    ) {
        val half = B.W / 2f
        val top = -B.LOBBY_H
        drawRect(SceneInk.LobbyWall, Offset(-half, top), Size(B.W, B.LOBBY_H))
        drawRect(SceneInk.LobbyWallLow, Offset(-half, -10f), Size(B.W, 10f))
        drawPath(art.lobbySide, SceneInk.LobbySide)
        if (art.floors.isEmpty()) roofFace(-art.layout.roof, SceneInk.LobbyWall, half)
        // the sign over the awning, lit at night
        drawRect(SceneInk.SignBoard, Offset(-half + 10f, top + 4f), Size(B.W - 20f, 15f))
        if (sky.dark > .2f)
            drawRect(
                SceneInk.SignText,
                Offset(-half + 8f, top + 2f),
                Size(B.W - 16f, 19f),
                alpha = .07f * sky.dark,
            )
        if (detailed) {
            val n = art.lobbyName
            drawText(
                n,
                SceneInk.SignText,
                Offset(-n.size.width / 2f, top + 11.5f - n.size.height / 2f),
            )
        }
        // the awning, striped, with a scalloped edge
        val a0 = top + 21f
        val a1 = top + 30f
        path.rewind()
        path.moveTo(-half + 6f, a0)
        path.lineTo(half - 6f, a0)
        path.lineTo(half - 1f, a1)
        path.lineTo(-half + 1f, a1)
        path.close()
        drawPath(path, art.awning)
        var x = -half + 8f
        while (x < half - 8f) {
            drawRect(Color.White, Offset(x, a0), Size(6f, a1 - a0), alpha = .18f)
            x += 12f
        }
        x = -half + 5f
        while (x < half - 1f) {
            drawCircle(art.awning, 3.6f, Offset(x, a1))
            x += 8f
        }
        drawRect(SceneInk.Occlusion, Offset(-half + 1f, a1 + 3f), Size(B.W - 2f, 2f))
        // the shopfront: a door between two windows, lit at night
        val shop = lerp(SceneInk.Shop, SceneInk.ShopLit, .15f + .8f * sky.dark)
        val w0 = top + 35f
        drawRect(shop, Offset(-half + 9f, w0), Size(half - 9f - 13f, -4f - w0))
        drawRect(shop, Offset(13f, w0), Size(half - 9f - 13f, -4f - w0))
        drawRect(SceneInk.Door, Offset(-9f, w0 - 2f), Size(18f, -w0 + 2f))
        drawRect(shop, Offset(-6.5f, w0 + 1f), Size(13f, 6f), alpha = .8f)
        drawLine(
            SceneInk.LobbyWall,
            Offset(-half / 2f + 2f, w0),
            Offset(-half / 2f + 2f, -4f),
            1.4f,
        )
        drawLine(SceneInk.LobbyWall, Offset(half / 2f - 2f, w0), Offset(half / 2f - 2f, -4f), 1.4f)
        if (sky.dark < .5f) {
            drawLine(
                SceneInk.Reflection,
                Offset(-half + 14f, -6f),
                Offset(-half + 26f, w0 + 1f),
                3f,
            )
            drawLine(SceneInk.Reflection, Offset(18f, -6f), Offset(30f, w0 + 1f), 3f)
        }
        // someone in the shop
        if (detailed)
            person(
                Activity.WALKING,
                Silhouettes.frame(Activity.WALKING, Ambient.step(seconds), art.stack.id.hashCode()),
                -half + 9f,
                w0,
                half - 22f,
                -4f - w0,
                art.stack.id.hashCode(),
                seconds,
                .8f,
            )
        drawRect(SceneInk.Highlight, Offset(-half, top), Size(B.W, 1f))
    }

    private fun DrawScope.floor(
        art: BuildingArt,
        fa: FloorArt,
        motion: BuildingMotion,
        landing: Boolean,
        alpha: Float,
        live: Boolean,
        detailed: Boolean,
        lit: Color,
        glass: Color,
        step: Int,
        sky: SkyColors,
    ) {
        val half = B.W / 2f
        val b = fa.block
        val f = fa.facade
        val ruin = fa.piece.kind == PieceKind.RUIN
        val seconds = motion.seconds
        val top = fa.top
        drawRect(fa.wall, Offset(-half, top), Size(B.W, fa.bottom - top), alpha = alpha)
        drawPath(fa.side, f.side, alpha = alpha)
        drawPath(
            fa.texture,
            TEXTURE[if (ruin) 4 else b.material.ordinal],
            alpha = alpha,
            style = thin,
        )
        drawPath(fa.trim, f.trim, alpha = alpha * .9f)
        val done = !live || b.done
        val count = fa.windowCount
        val w = fa.windows
        if (ruin) {
            for (k in 0 until count) {
                val x = w[k * 4]
                val y = top + w[k * 4 + 1]
                val ww = w[k * 4 + 2]
                val h = w[k * 4 + 3]
                when (fa.ruin[k]) {
                    RuinWindow.BOARDED -> {
                        drawRect(SceneInk.Boards, Offset(x, y), Size(ww, h), alpha = alpha)
                        drawLine(
                            SceneInk.BoardLine,
                            Offset(x, y + h * .3f),
                            Offset(x + ww, y + h * .45f),
                            1f,
                            alpha = alpha,
                        )
                        drawLine(
                            SceneInk.BoardLine,
                            Offset(x, y + h * .7f),
                            Offset(x + ww, y + h * .6f),
                            1f,
                            alpha = alpha,
                        )
                    }
                    RuinWindow.DIM ->
                        drawRect(
                            lerp(glass, SceneInk.Dim, .4f + .5f * sky.dark),
                            Offset(x, y),
                            Size(ww, h),
                            alpha = alpha,
                        )
                    RuinWindow.DARK -> drawRect(glass, Offset(x, y), Size(ww, h), alpha = alpha)
                }
            }
            // scaffolding up the sides while it comes down
            drawLine(
                SceneInk.Scaffold,
                Offset(-half - 3f, top),
                Offset(-half - 3f, fa.bottom),
                1.2f,
                alpha = .8f * alpha,
            )
            drawLine(
                SceneInk.Scaffold,
                Offset(-half - 3f, top + 2f),
                Offset(-half, fa.bottom - 2f),
                .8f,
                alpha = .6f * alpha,
            )
        } else {
            // how many windows are lit: the occupancy once the floor is done today; while it is
            // being finished they switch on one by one
            val on =
                when {
                    !done -> 0
                    landing -> ceil(fa.occupancy.lit * motion.lights).toInt()
                    else -> fa.occupancy.lit
                }
            // a missed floor's failing lights: one flickers, or, missed twice, a few sputter
            val failing =
                if (!done && live && b.miss >= 1) (if (b.miss >= 2) min(3, count) else 1) else 0
            val sputter = b.miss >= 2
            val seed = b.id.hashCode()
            for (k in 0 until count) {
                val x = w[k * 4]
                val y = top + w[k * 4 + 1]
                val ww = w[k * 4 + 2]
                val h = w[k * 4 + 3]
                val bright =
                    when {
                        k < on -> 1f
                        k < failing ->
                            if (seconds <= 0f) (if (k == 0) .55f else 0f)
                            else if (Ambient.flicker(seed + k * 7, seconds, sputter)) .75f else 0f
                        else -> 0f
                    }
                if (bright > 0f) {
                    drawRect(lit, Offset(x, y), Size(ww, h), alpha = alpha * bright)
                    drawRect(
                        SceneInk.WindowLitLow,
                        Offset(x, y + h * .55f),
                        Size(ww, h * .45f),
                        alpha = alpha * bright * .45f,
                    )
                } else drawRect(glass, Offset(x, y), Size(ww, h), alpha = alpha)
            }
            if (on < count)
                drawPath(fa.reflect, SceneInk.Reflection, alpha = alpha * (1f - .6f * sky.dark))
            if (detailed && on > 0) {
                val people = min(fa.occupancy.people, on)
                for (k in 0 until people) {
                    val a = fa.activities[k]
                    val appear =
                        if (landing) ((motion.lights * fa.occupancy.lit - k) * 2f).coerceIn(0f, 1f)
                        else 1f
                    if (appear <= 0f) continue
                    // a little hop as they arrive
                    val hop = if (landing && appear < 1f) (1f - appear) * 3f else 0f
                    person(
                        a,
                        Silhouettes.frame(a, step, fa.seeds[k]),
                        w[k * 4],
                        top + w[k * 4 + 1] - hop,
                        w[k * 4 + 2],
                        w[k * 4 + 3],
                        fa.seeds[k],
                        seconds,
                        alpha * appear,
                    )
                }
            }
            if (b.material == HabitMaterial.GLASS) {
                // the frame is still going up: scaffold poles and a brace
                drawLine(
                    SceneInk.Scaffold,
                    Offset(-half - 3f, top),
                    Offset(-half - 3f, fa.bottom),
                    1.2f,
                    alpha = alpha,
                )
                drawLine(
                    SceneInk.Scaffold,
                    Offset(half + 3f, top),
                    Offset(half + 3f, fa.bottom),
                    1.2f,
                    alpha = alpha * .7f,
                )
                drawLine(
                    SceneInk.Scaffold,
                    Offset(-half - 3f, top),
                    Offset(-half + 4f, fa.bottom),
                    .8f,
                    alpha = alpha * .7f,
                )
            }
        }
        // the cornice line along the floor's top: gold from 66
        val gold = b.material == HabitMaterial.GOLD && !ruin
        drawRect(
            f.trim,
            Offset(-half - 1f, top),
            Size(B.W + 2f, if (gold) 3.2f else 1.6f),
            alpha = alpha,
        )
        drawLine(
            SceneInk.Occlusion,
            Offset(-half, fa.bottom - 1f),
            Offset(half, fa.bottom - 1f),
            1.5f,
            alpha = alpha,
        )
        val crack = fa.crack
        if (crack != null)
            clipRect(-half, top, half, fa.bottom) {
                if (b.miss >= 1) drawPath(crack, SceneInk.Crack, alpha = alpha, style = crackStroke)
                else {
                    drawPath(crack, SceneInk.SeamGlow, alpha = alpha, style = seamGlow)
                    drawPath(crack, SceneInk.Seam, alpha = alpha, style = seam)
                }
            }
        if (detailed && fa.name != null) plaque(fa, alpha)
        if (live && !done && b.miss >= 2 && !ruin) attention(half - 16f, top + 6f, seconds)
        val isNext = live && art.stack.next == b.id && fa.piece.labelled
        if (isNext) glow(top, if (ruin) fa.bottom else fa.bottom, f.tint, seconds)
        if (landing && motion.burst < 1f)
            drawRect(
                SceneInk.Glow,
                Offset(-half, top),
                Size(B.W, fa.bottom - top),
                alpha = (1f - motion.burst) * .55f,
            )
        if (landing && motion.swept && motion.sweep < 1f) sheen(top, fa.bottom, motion.sweep)
        if (ruin && art.craneOn === fa && fa.storey == fa.storeys - 1) crane(top, seconds)
    }

    /** The habit's name on a small plaque at the left of its floor, its count under it. */
    private fun DrawScope.plaque(fa: FloorArt, alpha: Float) {
        val name = fa.name ?: return
        val x = -B.W / 2f + B.PLAQUE_X
        // set in the band under the windows; a run-down storey's hangs over its boarded windows
        val band = if (fa.piece.kind == PieceKind.RUIN) fa.bottom - fa.top else B.PLAQUE_BAND
        val y = fa.bottom - band / 2f - fa.plaqueH / 2f - if (band == B.PLAQUE_BAND) 1f else 0f
        drawRoundRect(
            fa.facade.plaque,
            Offset(x, y),
            Size(fa.plaqueW, fa.plaqueH),
            CornerRadius(3f),
            alpha = alpha,
        )
        drawRect(SceneInk.Highlight, Offset(x + 2f, y), Size(fa.plaqueW - 4f, .8f), alpha = alpha)
        val mid = y + fa.plaqueH / 2f
        drawText(name, fa.facade.ink, Offset(x + 5f, mid - name.size.height / 2f), alpha = alpha)
        val count = fa.count ?: return
        drawText(
            count,
            fa.facade.inkSoft,
            Offset(x + 5f + name.size.width + 7f, mid - count.size.height / 2f + .5f),
            alpha = alpha,
        )
    }

    /** Missed twice: a small warning sign hung on the floor, breathing with motion on. */
    private fun DrawScope.attention(x: Float, y: Float, seconds: Float) {
        val pulse = if (seconds > 0f) .8f + .2f * sin(seconds * 3f) else 1f
        path.rewind()
        path.moveTo(x, y)
        path.lineTo(x + 7f, y + 12f)
        path.lineTo(x - 7f, y + 12f)
        path.close()
        drawPath(path, SceneInk.Attention, alpha = pulse)
        drawRect(SceneInk.AttentionInk, Offset(x - .8f, y + 3.5f), Size(1.6f, 4.8f))
        drawRect(SceneInk.AttentionInk, Offset(x - .8f, y + 9.4f), Size(1.6f, 1.6f))
    }

    /** A tower crane on the roof of a building being taken down. */
    private fun DrawScope.crane(top: Float, seconds: Float) {
        val x = B.W / 2f - 26f
        val swing = if (seconds > 0f) sin(seconds * .5f) * 4f else 0f
        drawRect(SceneInk.Crane, Offset(x - 1.5f, top - 54f), Size(3f, 54f))
        var y = top - 50f
        while (y < top) {
            drawLine(SceneInk.Crane, Offset(x - 1.5f, y), Offset(x + 1.5f, y + 6f), .7f)
            y += 6f
        }
        drawRect(SceneInk.Crane, Offset(x - 74f, top - 56f), Size(90f, 2.4f))
        drawRect(SceneInk.Lamp, Offset(x + 8f, top - 58f), Size(8f, 6f))
        val hook = x - 58f + swing
        drawLine(SceneInk.Lamp, Offset(x - 58f, top - 54f), Offset(hook, top - 26f), .8f)
        drawRect(SceneInk.Boards, Offset(hook - 5f, top - 26f), Size(10f, 5f))
    }

    private fun DrawScope.roofFace(y: Float, color: Color, half: Float) {
        path.rewind()
        path.moveTo(-half, y)
        path.lineTo(-half + B.DX, y - B.DY)
        path.lineTo(half + B.DX, y - B.DY)
        path.lineTo(half, y)
        path.close()
        drawPath(path, color)
    }

    /** The parapet, the roof, and what stands on it: a water tank, the reward's sign, the ghost. */
    private fun DrawScope.roof(
        art: BuildingArt,
        live: Boolean,
        detailed: Boolean,
        sky: SkyColors,
        seconds: Float,
    ) {
        val half = B.W / 2f
        val y = -art.layout.roof
        if (art.floors.isNotEmpty()) {
            val f = facade(art.crown ?: HabitMaterial.STONE)
            val gold = art.crown == HabitMaterial.GOLD
            val c = if (gold) f.trim else f.roof
            drawRect(c, Offset(-half - 3f, y - B.CORNICE), Size(B.W + 6f, B.CORNICE))
            drawRect(SceneInk.Occlusion, Offset(-half - 3f, y - 1.5f), Size(B.W + 6f, 1.5f))
            roofFace(y - B.CORNICE, f.roof, half + 3f)
            path.rewind()
            path.moveTo(half + 3f, y - B.CORNICE)
            path.lineTo(half + 3f + B.DX, y - B.CORNICE - B.DY)
            path.lineTo(half + 3f + B.DX, y - B.DY)
            path.lineTo(half + 3f, y)
            path.close()
            drawPath(path, f.side)
            if (gold) {
                // gold finials along the cornice
                var x = -half
                while (x <= half) {
                    drawCircle(f.trim, 2.2f, Offset(x, y - B.CORNICE - 2f))
                    x += B.W / 6f
                }
            }
            if (art.craneOn == null) {
                // a water tank on its legs
                val tx = -half + 26f
                val base = y - B.CORNICE - B.DY / 2f
                drawRect(SceneInk.Lamp, Offset(tx - 7f, base - 8f), Size(1.6f, 8f))
                drawRect(SceneInk.Lamp, Offset(tx + 5.4f, base - 8f), Size(1.6f, 8f))
                drawRect(SceneInk.Tank, Offset(tx - 8f, base - 22f), Size(16f, 14f))
                path.rewind()
                path.moveTo(tx - 9f, base - 22f)
                path.lineTo(tx, base - 28f)
                path.lineTo(tx + 9f, base - 22f)
                path.close()
                drawPath(path, SceneInk.Tank)
                drawLine(
                    SceneInk.Occlusion,
                    Offset(tx - 8f, base - 15f),
                    Offset(tx + 8f, base - 15f),
                    .8f,
                )
            }
        }
        val s = art.sign
        if (s != null) sign(art, s.top, s.width, detailed, sky, seconds)
        val g = art.ghost
        if (g != null && live) {
            val gh = g.width / 2f
            drawRoundRect(
                SceneInk.PlanFill,
                Offset(-gh, -g.top),
                Size(g.width, g.height),
                CornerRadius(6f),
            )
            drawRoundRect(
                SceneInk.PlanLine,
                Offset(-gh, -g.top),
                Size(g.width, g.height),
                CornerRadius(6f),
                style = ghostLine,
            )
            val words = art.ghostWords
            if (words != null && detailed)
                drawText(
                    words,
                    SceneInk.PlanText,
                    Offset(-words.size.width / 2f, -g.middle - words.size.height / 2f),
                )
        }
    }

    /**
     * The rooftop reward's sign: dark while it is locked, lit with running bulbs once the building
     * is done today, a flag beside it once it has been claimed.
     */
    private fun DrawScope.sign(
        art: BuildingArt,
        top: Float,
        width: Float,
        detailed: Boolean,
        sky: SkyColors,
        seconds: Float,
    ) {
        val st = art.stack
        val open = st.claimable || st.claimed
        val half = width / 2f
        val y = -top
        val panel = 24f
        drawLine(
            SceneInk.Lamp,
            Offset(-half + 14f, y + panel),
            Offset(-half + 14f, y + B.SIGN_H),
            2.4f,
        )
        drawLine(
            SceneInk.Lamp,
            Offset(half - 14f, y + panel),
            Offset(half - 14f, y + B.SIGN_H),
            2.4f,
        )
        drawLine(
            SceneInk.Lamp,
            Offset(-half + 14f, y + panel + 2f),
            Offset(half - 14f, y + B.SIGN_H - 1f),
            1f,
        )
        if (open) {
            val pulse = if (st.claimable && seconds > 0f) .65f + .35f * sin(seconds * 3.1f) else 1f
            drawRoundRect(
                SceneInk.Glow,
                Offset(-half - 6f, y - 6f),
                Size(width + 12f, panel + 12f),
                CornerRadius(10f),
                alpha = .25f * pulse,
            )
        }
        drawRect(
            if (open) SceneInk.SignLit else SceneInk.SignOff,
            Offset(-half, y),
            Size(width, panel),
        )
        drawRect(SceneInk.Occlusion, Offset(-half, y + panel - 2f), Size(width, 2f))
        if (open) {
            // bulbs round the edge: chasing while it waits to be claimed, steady once it has been
            val chase = Ambient.step(seconds) / 3
            var i = 0
            var x = -half + 4f
            while (x < half) {
                val on = st.claimed || seconds <= 0f || (i + chase) % 3 != 0
                drawCircle(SceneInk.Bulb, 1.3f, Offset(x, y + 2.2f), if (on) 1f else .3f)
                drawCircle(SceneInk.Bulb, 1.3f, Offset(x, y + panel - 3.6f), if (on) 1f else .3f)
                x += 9f
                i++
            }
        }
        val words = art.treat
        if (words != null && detailed)
            drawText(
                words,
                if (open) SceneInk.SignLitText else SceneInk.SignOffText,
                Offset(-words.size.width / 2f, y + panel / 2f - words.size.height / 2f),
            )
        if (st.claimed) {
            val x = half + 8f
            drawLine(SceneInk.FlagPole, Offset(x, y + B.SIGN_H), Offset(x, y - 14f), 1.6f)
            val wave = if (seconds > 0f) Ambient.flag(seconds) * 1.8f else 0f
            path.rewind()
            path.moveTo(x, y - 14f)
            path.quadraticTo(x + 8f, y - 12f - wave, x + 16f, y - 9.5f + wave)
            path.lineTo(x, y - 5f)
            path.close()
            drawPath(path, SceneInk.Flag)
        }
    }

    /** Someone in a window, clipped to it: their pose of this moment, facing their own way. */
    private fun DrawScope.person(
        a: Activity,
        frame: Int,
        x: Float,
        y: Float,
        w: Float,
        h: Float,
        seed: Int,
        seconds: Float,
        alpha: Float,
    ) {
        var cx = x + w / 2f
        var dir = if (seed and 1 == 0) 1f else -1f
        if (a == Activity.WALKING) {
            val at = Silhouettes.walkX(seconds, seed, w)
            if (at.isNaN()) return
            cx = x + at
            dir = Silhouettes.walkDir(seed)
        }
        val s = min(1f, (h - 2f) / 23f)
        val pose = Silhouettes.poses(a)[frame]
        clipRect(x, y, x + w, y + h) {
            translate(cx, y + h) {
                scale(dir * s, s, Offset.Zero) {
                    drawPath(shapes[a.ordinal][frame], SceneInk.Person, alpha = alpha)
                    drawPath(limbs[a.ordinal][frame], SceneInk.Person, alpha = alpha, style = limb)
                    drawCircle(
                        SceneInk.Person,
                        Silhouettes.HEAD_R,
                        Offset(pose.headX, pose.headY),
                        alpha,
                    )
                }
            }
        }
    }

    private fun silhouette(top: Float, bottom: Float) {
        val half = B.W / 2f
        path.rewind()
        path.moveTo(-half, bottom)
        path.lineTo(-half, top)
        path.lineTo(-half + B.DX, top - B.DY)
        path.lineTo(half + B.DX, top - B.DY)
        path.lineTo(half + B.DX, bottom - B.DY)
        path.lineTo(half, bottom)
        path.close()
    }

    /** The selected floor: its outline in the accent. */
    private fun DrawScope.outline(top: Float, bottom: Float, color: Color) {
        silhouette(top, bottom)
        drawPath(path, color, style = selection)
    }

    /**
     * The next floor to finish: its outline lit in its material's colour, breathing with motion on.
     */
    private fun DrawScope.glow(top: Float, bottom: Float, tint: Color, seconds: Float) {
        val pulse = if (seconds > 0f) .6f + .4f * sin(seconds * 2.6f) else 1f
        silhouette(top, bottom)
        drawPath(path, tint, alpha = .2f * pulse, style = glowWide)
        drawPath(path, tint, alpha = .35f * pulse, style = glowMid)
        drawPath(path, tint, alpha = .5f + .45f * pulse, style = glowEdge)
    }

    /** A band of light across the front, [at] 0 as it enters at the left to 1 as it leaves. */
    private fun DrawScope.sheen(top: Float, bottom: Float, at: Float) {
        val half = B.W / 2f
        val band = 34f
        val x = -half - band + (B.W + band * 2f) * at
        clipRect(-half, top, half, bottom) {
            val lean = (bottom - top) * .5f
            path.rewind()
            path.moveTo(x, bottom)
            path.lineTo(x + lean, top)
            path.lineTo(x + lean + band, top)
            path.lineTo(x + band, bottom)
            path.close()
            drawPath(path, SceneInk.Sheen, alpha = .7f)
        }
    }

    /** Scaffolding dust thrown out along a floor's base as it drops into place. */
    private fun DrawScope.dust(bottom: Float, burst: Float) {
        val half = B.W / 2f
        val alpha = (1f - burst) * .7f
        for (i in 0 until 10) {
            val left = i < 5
            val u = (i % 5 + .5f) / 5f
            val x = (if (left) -1f else 1f) * (half - 18f * u + burst * (26f + 9f * (i % 3)))
            drawCircle(
                SceneInk.Dust,
                2.5f + i % 3 + burst * 5f,
                Offset(x, -bottom - 2f - burst * 9f * u),
                alpha,
            )
        }
    }

    /** The storey that has just been knocked off, tumbling away to the right, and its rubble. */
    private fun DrawScope.fallen(from: Float, key: String, at: Float) {
        val f = facade(HabitMaterial.FADE)
        val half = B.W / 2f
        val ease = at * at
        translate(70f * at, 210f * ease) {
            rotate(46f * at, Offset(0f, -from - B.RUIN / 2f)) {
                drawRect(
                    f.wallTop,
                    Offset(-half, -from - B.RUIN),
                    Size(B.W, B.RUIN),
                    alpha = 1f - ease,
                )
            }
        }
        val dice = Dice(key + "f")
        for (i in 0 until 7) {
            val dx = (dice.next() - .5f) * 150f
            val up = dice.next() * 60f
            drawRect(
                if (i % 2 == 0) f.roof else f.side,
                Offset(
                    (dice.next() * 2f - 1f) * half * .8f + dx * at,
                    -from - B.RUIN / 2f - up * sin(at * PI.toFloat()) + 120f * ease,
                ),
                Size(4f + i % 3 * 2f, 3f + i % 2 * 2f),
                alpha = 1f - ease,
            )
        }
    }

    companion object {
        const val DROP = 160f

        /** How far the pavement runs in front of the buildings before the kerb. */
        const val PAVEMENT = 16f

        /** The tile the asphalt's specks repeat in, in street units. */
        const val SPECK_TILE = 120f

        /** The seat and back slats of a bench, from the pavement line up. */
        val BENCH_SLATS = floatArrayOf(-9.6f, -7.2f, -18.6f, -16.1f, -13.6f)

        /** The lines over a wall, by material (the last for a run-down storey). */
        private val TEXTURE =
            arrayOf(
                Color(0xFF2C3850),
                Color(0x5C3A1A0B),
                Color(0x40483E2C),
                Color(0x40483E2C),
                Color(0x55241A1D),
            )
    }
}
