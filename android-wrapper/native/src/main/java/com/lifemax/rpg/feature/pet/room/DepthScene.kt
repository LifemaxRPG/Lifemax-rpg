package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.body.Occluders
import com.lifemax.rpg.feature.pet.body.World

/** Numbers of the 2.5D painter that are about the picture, not the light or the sway. */
internal object DepthTuning {
    /** A piece's contact shadow is this many art pixels tall (half height). */
    const val PIECE_SHADOW_HALF_HEIGHT = 3

    /**
     * How strong a piece's shadow is. It is the floor's own darkest colour, not the art's ink, so
     * it needs more strength to read: a deeper floor colour, never a black smear.
     */
    const val PIECE_SHADOW_STRENGTH = 2.4f

    /** A piece's shadow is this many pixels wider than the piece, each side. */
    const val PIECE_SHADOW_MARGIN = 1

    /** A piece stands on the floor when its bottom row is within this many rows of its base. */
    const val FLOOR_REACH = 2

    /** The row the window light on the floor moves with (the middle of its patches). */
    const val FLOOR_LIGHT_ROW = LightTuning.LIGHT_MID
}

/**
 * Paints the 2.5D room, in the same three steps as [RoomScene] (it is what [RoomScene] calls when
 * its style asks for the layers):
 * 1. [paintBackdrop]: the sky (a little wider than the glass, so a shifting wall never shows a
 *    gap), each room's base (the backdrop with the furniture removed: the wall moves as one, the
 *    floor row by row, so it shears like a floor and nothing standing on it slides), the seam where
 *    wall meets floor, the window light on the floor, and the rugs (row by row, like the floor they
 *    lie on).
 * 2. [paintScene]: the solid pieces, the atlas furniture and the cats, back to front by
 *    [DepthOrder]. Each standing piece is drawn as its cached pictures for the camera's step
 *    ([VolumeSheets]): its painted front and the sides and top the sway shows, in bands of depth so
 *    a cat passes behind, in front of and inside it; a piece that stands on the floor gets its
 *    contact shadow, in the floor's own colour, first.
 * 3. the classic overlay, [RoomScene.paintOverlay]: the time of day over everything.
 *
 * The camera sways with [sway]; step it with [step] once a frame. With [RoomStyle.parallax] off (or
 * reduced motion on) nothing moves and every piece is its painted sprite; with [RoomStyle.volume]
 * off the pieces move but stay flat. A frame draws only cached pictures: a step's pictures are
 * rendered the first time it is needed, and [step] renders one more each frame until all are made.
 * A piece between two steps is drawn as a cross-fade of both ([StepBlend], [StepMix]), so its sides
 * come and go smoothly as the camera turns; a camera at rest draws one step alone.
 */
internal class DepthScene(
    private val art: RoomArt,
    private val style: RoomStyle,
    private val scene: RoomScene,
) {
    private val layers = art.layers ?: error("the 2.5D room needs the layers")
    private val level = style.level

    /**
     * The camera's sway; set its tilt from a sensor, read its position to see where it is. The live
     * rooms have no idle drift ([RoomStyle.idleDrift]): their camera moves only when it pans.
     */
    val sway =
        DepthSway(
            style.reducedMotion,
            if (style.idleDrift) level.idleAmplitude else 0f,
            style.viewAngle,
        )

    private val solids = layers.pieces.filter { it.volume != null }
    private val sways = style.parallax && style.volume && !style.reducedMotion
    private val furnitureKey =
        FloatArray(scene.furnitureZ.size) { World.floorY(scene.furnitureZ[it]) }

    /** The solid pieces' pictures for every camera step. */
    val sheets =
        VolumeSheets(
            solids,
            furnitureKey,
            level,
            if (sways) VolumeSheets.stepsFor(solids, level) else 0,
        )

    /** The step nearest the sway now (the developer readout and tests). */
    var stepIndex = sheets.rest
        private set

    private val drawOrder = DepthOrder(sheets.capacity)
    private val mix = StepMix(sheets, furnitureKey)
    private val blend = Array(solids.size) { StepBlend(sheets.stepsPerSide) }
    private val pieceStep = IntArray(solids.size) { sheets.rest }
    private val pieceOver = IntArray(solids.size) { -1 }
    private val pieceWeight = FloatArray(solids.size)
    private var frameDt = 0f
    private val readyNow = StepReady { !asyncWarm || sheets.ready(it) }
    private val pieceX =
        FloatArray(solids.size) {
            (solids[it].houseX + solids[it].width / 2f) + RoomLayout.HOUSE_X0
        }
    private val follows = style.cameraFollow && sways
    private val catShift = IntArray(DepthOrder.MAX_CATS)
    private val catKey = FloatArray(DepthOrder.MAX_CATS)
    private val catFeet = FloatArray(DepthOrder.MAX_CATS)
    private val catAnchor = IntArray(DepthOrder.MAX_CATS)
    private val catCover = IntArray(DepthOrder.MAX_CATS)
    private val catBefore = IntArray(DepthOrder.MAX_CATS)
    private val pieceX0 =
        FloatArray(solids.size) { (solids[it].houseX + RoomLayout.HOUSE_X0).toFloat() }
    private val pieceX1 = FloatArray(solids.size) { pieceX0[it] + solids[it].width }

    init {
        require(solids.size <= DepthOrder.MAX_PIECES) { "too many solid pieces for a cat's mask" }
    }

    private val shadowed = BooleanArray(solids.size)
    private val shadowColour = IntArray(layers.bases.size) { floorShadowColour(layers.base(it)) }

    /**
     * The camera step nearest to where piece [name] was drawn in the last [paintScene] (tests and
     * tuning; between two steps it is drawn as a cross-fade of both, [positionOf]).
     */
    fun stepOf(name: String): Int {
        for (p in solids.indices) if (solids[p].name == name) return blend[p].nearest
        error("no solid piece $name")
    }

    /**
     * Where piece [name] was drawn in the last [paintScene], in camera steps (tests and tuning).
     */
    fun positionOf(name: String): Float {
        for (p in solids.indices) if (solids[p].name == name) return blend[p].shown
        error("no solid piece $name")
    }

    /** The piece (an index among the solids) and the floor row of drawing-order band [code]. */
    fun bandPiece(code: Int): Int = mix.piece[code]

    fun bandKey(code: Int): Float = mix.key[code]

    /** The solid pieces in manifest order. */
    val pieces: List<LayerPiece>
        get() = solids

    /** The solid piece [name] as the scene sees it. */
    fun solid(name: String): LayerPiece = solids.first { it.name == name }

    /** The order of the last [paintScene]. */
    val order: DepthOrder
        get() = drawOrder

    /** [argb] with its alpha scaled by the level's light strength. */
    private fun light(argb: Int): Int =
        (Math.round((argb ushr 24) * level.lightStrength) shl 24) or (argb and 0xFFFFFF)

    /**
     * Whether the camera steps are made by a background thread ([warm]): then a frame never makes
     * one itself but draws the nearest made step until the warmer is done. Off (the default: tests,
     * screenshots), [step] makes one more step each frame and a frame makes what it needs.
     */
    var asyncWarm = false

    /** Whether the rest picture is made, so the room can be drawn without a hitch. */
    val ready: Boolean
        get() = sheets.ready(sheets.rest)

    /** Make the rest picture (on the calling thread), so the room can be drawn without a hitch. */
    fun prepare() {
        sheets.step(sheets.rest)
    }

    /**
     * Make every camera step's pictures (rest first), on the calling thread; [cancelled] is asked
     * between steps. The live room calls it from a background thread, on entering the screen.
     */
    fun warm(cancelled: () -> Boolean = { false }) = sheets.warmAll(cancelled)

    /**
     * Let go of the cached camera steps but the rest one (the room left the screen); [warm] makes
     * them again.
     */
    fun release() {
        sheets.release()
        mix.clear()
        pieceStep.fill(sheets.rest)
        pieceOver.fill(-1)
        pieceWeight.fill(0f)
        for (b in blend) b.reset()
        stepIndex = sheets.rest
    }

    /**
     * Follow the room camera: [screenX] is the world x at the middle of the screen. Call it before
     * [step] each frame; a no-op unless the style asks for [RoomStyle.cameraFollow].
     */
    fun follow(screenX: Float) {
        if (follows) sway.follow(screenX)
    }

    /** Advance the sway by [dt] seconds, and make one more step's pictures if any are missing. */
    fun step(dt: Float) {
        sway.step(dt)
        frameDt += dt
        if (!asyncWarm) sheets.warmOne()
    }

    /** The sway the room moves with ([DepthSway.shown], held at rest): 0 when parallax is off. */
    private val swing: Float
        get() = if (style.parallax) sway.shown else 0f

    /** The wall's shift in pixels as the room is drawn now (the developer readout). */
    val wallShiftNow: Int
        get() = Parallax.wallShift(swing, level)

    /** The nearest floor row's shift in pixels as drawn now (the developer readout). */
    val nearShiftNow: Int
        get() = Parallax.shiftAtRow(swing, LiveDepthTuning.NEAR_ROW, level)

    /** The shift of a floor object (a cat, a prop) at depth [z], for the cat's own layer. */
    fun shiftAtZ(z: Float): Int = Parallax.shiftAtZ(swing, z, level)

    private fun rowShift(row: Float) = Parallax.shiftAtRow(swing, row, level)

    // ------------------------------------------------------------------ 1. behind the furniture

    /** The sky, the bases, the seam, the window light and the rugs. */
    fun paintBackdrop(c: RoomCanvas, hour: Double, clock: Double, topExtra: Int) {
        scene.skyFor(hour)
        val wall = Parallax.wallShift(swing, level)
        for (wx in RoomLayout.windows) scene.paintSky(c, wx, hour, clock, Parallax.maxWallShift)
        paintBases(c, wall, topExtra)
        if (style.floorLight) {
            SeamShade.paint(c, wall, RoomLayout.HOUSE_WIDTH, level.seamStrength)
            paintFloorLight(c, hour)
        } else {
            scene.paintWindowLight(c, hour)
        }
        for (i in 0 until layers.pieces.size) { // an index loop: no iterator per frame
            val piece = layers.pieces[i]
            if (piece.flat) paintRug(c, piece)
        }
    }

    private fun paintBases(c: RoomCanvas, wall: Int, topExtra: Int) {
        val seam = Parallax.WALL_ROW.toInt()
        strip(c, 0, seam, wall)
        if (topExtra > 0) {
            val rooms = HouseMap.current.rooms
            for (room in rooms.indices) {
                val x = room * RoomLayout.BACKDROP_WIDTH + wall
                c.image(
                    RoomImage.baseOf(rooms[room].id),
                    0,
                    0,
                    RoomLayout.BACKDROP_WIDTH,
                    1,
                    x,
                    -topExtra,
                    RoomLayout.BACKDROP_WIDTH,
                    topExtra,
                )
            }
            edges(c, 0, 1, -topExtra, topExtra, wall)
        }
        // the floor, in runs of rows that move by the same whole pixels
        var from = seam
        while (from < RoomLayout.HEIGHT) {
            val shift = rowShift(from.toFloat())
            var to = from + 1
            while (to < RoomLayout.HEIGHT && rowShift(to.toFloat()) == shift) to++
            strip(c, from, to - from, shift)
            from = to
        }
    }

    /** Rows [y] to `y + h` of the three bases, [shift] pixels over, the house's edges filled. */
    private fun strip(c: RoomCanvas, y: Int, h: Int, shift: Int) {
        val w = RoomLayout.BACKDROP_WIDTH
        val rooms = HouseMap.current.rooms
        for (room in rooms.indices) {
            c.image(RoomImage.baseOf(rooms[room].id), 0, y, w, h, room * w + shift, y, w, h)
        }
        edges(c, y, h, y, h, shift)
    }

    /**
     * Stretch the house's outer edge columns, rows [sy] to `sy + sh`, over the gap a [shift] opens
     * at rows [dy] to `dy + dh`.
     */
    private fun edges(c: RoomCanvas, sy: Int, sh: Int, dy: Int, dh: Int, shift: Int) {
        val w = RoomLayout.BACKDROP_WIDTH
        val rooms = HouseMap.current.rooms
        if (shift > 0) {
            c.image(RoomImage.baseOf(rooms.first().id), 0, sy, 1, sh, 0, dy, shift, dh)
        } else if (shift < 0) {
            c.image(
                RoomImage.baseOf(rooms.last().id),
                w - 1,
                sy,
                1,
                sh,
                RoomLayout.HOUSE_WIDTH + shift,
                dy,
                -shift,
                dh,
            )
        }
    }

    private fun paintFloorLight(c: RoomCanvas, hour: Double) {
        val colour =
            when {
                hour >= 7.5 && hour < 17.5 -> light(DAY_LIGHT)
                RoomLight.isNight(hour) -> light(NIGHT_LIGHT)
                else -> return
            }
        val shift = rowShift(DepthTuning.FLOOR_LIGHT_ROW)
        val lit = HouseMap.current.floorLights
        for (i in lit.indices) {
            FloorLight.paint(c, (lit[i] - RoomLayout.HOUSE_X0 + shift).toFloat(), hour, colour)
        }
    }

    /** A rug, lying on the floor: row by row, each row moving with the floor under it. */
    private fun paintRug(c: RoomCanvas, piece: LayerPiece) {
        var from = 0
        while (from < piece.height) {
            val shift = rowShift((piece.y + from).toFloat())
            var to = from + 1
            while (to < piece.height && rowShift((piece.y + to).toFloat()) == shift) to++
            val h = to - from
            c.image(
                RoomImage.LAYERS,
                piece.atlasX,
                piece.atlasY + from,
                piece.width,
                h,
                piece.houseX + shift,
                piece.y + from,
                piece.width,
                h,
            )
            from = to
        }
    }

    // ------------------------------------------------------------------ 2. furniture and cats

    /** The solid pieces, the atlas furniture and the cats, back to front. */
    fun paintScene(c: RoomCanvas, cats: CatLayer) {
        chooseSteps()
        val n = cats.count.coerceAtMost(DepthOrder.MAX_CATS)
        for (i in 0 until n) placeCat(cats, i)
        drawOrder.build(mix, furnitureKey, n, catKey, catFeet, catAnchor, catCover, catBefore)
        val order = drawOrder.order
        shadowed.fill(false)
        for (k in 0 until drawOrder.size) {
            val code = order[k]
            when {
                code >= DepthOrder.CAT -> {
                    val cat = code - DepthOrder.CAT
                    cats.draw(cat, catShift[cat], style, c)
                }
                code >= VolumeStep.FURNITURE -> {
                    val item = code - VolumeStep.FURNITURE
                    scene.shiftX = Parallax.shiftAtZ(swing, scene.furnitureZ[item], level)
                    scene.paintFurnitureAt(c, item)
                    scene.shiftX = 0
                }
                else -> paintBand(c, code)
            }
        }
    }

    /**
     * The camera steps each piece is drawn from. Without the camera-follow parallax every piece
     * follows the sway; with it each follows its own yaw: the idle drift and tilt plus where it
     * sits against the middle of the screen ([PanFollow.perspective]). A piece between two steps is
     * a cross-fade of both ([StepBlend]); with a background warmer it never waits for a step that
     * is not made.
     */
    private fun chooseSteps() {
        stepIndex = sheets.indexOf(swing, stepIndex)
        if (asyncWarm && !sheets.ready(sheets.rest)) {
            sheets.step(sheets.rest) // nothing made yet: the rest picture, once, whoever is late
        }
        val dt = frameDt
        frameDt = 0f
        for (p in solids.indices) {
            val want =
                if (follows) {
                    (sway.shownIdle + sway.shownPerspective(pieceX[p])).coerceIn(-1f, 1f)
                } else {
                    swing
                }
            val b = blend[p]
            b.update(want, dt, readyNow)
            pieceStep[p] = b.base
            pieceOver[p] = b.over
            pieceWeight[p] = b.weight
        }
        mix.update(pieceStep, pieceOver, pieceWeight)
    }

    /** Where cat [i] stands: on a piece's surface (at the surface's depth) or on the floor. */
    private fun placeCat(cats: CatLayer, i: Int) {
        catFeet[i] = cats.feet(i)
        catAnchor[i] = -1
        catCover[i] = -1
        catBefore[i] = 0
        val surface = cats.surface(i)
        var p = if (surface == null) -1 else solids.size - 1
        while (p >= 0) { // the last piece that has the surface; no iterator per frame
            val volume = solids[p].volume!!
            val depth = volume.surfaces[surface ?: break]
            if (depth != null) {
                catAnchor[i] = p
                catCover[i] = coverOf(volume, surface)
                catKey[i] = volume.sortFront - depth
                catShift[i] = onPiece(p, volume.parallaxFront - depth, depth)
                return
            }
            p--
        }
        catKey[i] = World.floorY(cats.depth(i))
        catShift[i] = rowShift(catKey[i])
        catBefore[i] = inFrontOf(cats.x(i), cats.depth(i), catFeet[i], catKey[i])
    }

    /**
     * The pieces a cat on the floor at world [x], depth [z] (feet on row [feet], sorting at [key])
     * is drawn in front of, a bit each: none, unless it stands inside a piece that stands clear of
     * the floor and a cat can pass behind (a platform of the cat tree, its post; not a cushion
     * lying on the sofa) and its head, sitting up, would be inside it ([Occluders.clipped]'s rule,
     * either way it faces). Then it is in front of every piece it stands inside, as a cat that tall
     * would have to be: a cat walking through under the tree's middle platform, or at the second
     * bowl half under it, passes in front of the tree instead of having the platform across its
     * head and its ears above it.
     */
    private fun inFrontOf(x: Float, z: Float, feet: Float, key: Float): Int {
        if (x.isNaN()) return 0
        val k = World.depthScale(z)
        val top = feet - Occluders.CAT_UPRIGHT_H * k
        val reach = (Occluders.HEAD_AHEAD + Occluders.HEAD_HALF) * k
        var clipped = false
        var mask = 0
        for (p in solids.indices) {
            if (!inside(p, x, reach, key)) continue
            mask = mask or (1 shl p)
            val piece = solids[p]
            if (piece.occludes && !piece.onFloor && top < piece.y + piece.height) clipped = true
        }
        return if (clipped) mask else 0
    }

    /** Whether a cat sorting at [key], [reach] either side of [x], stands inside piece [p]. */
    private fun inside(p: Int, x: Float, reach: Float, key: Float): Boolean {
        val v = solids[p].volume!!
        if (v.wall || key <= v.sortFront - v.depth || key >= v.sortFront) return false
        return x + reach > pieceX0[p] && x - reach < pieceX1[p]
    }

    /**
     * The shift of a cat standing [depth] rows inside piece [p], on floor row [row]: the piece's
     * front moves with the floor (the sway), and the surface behind it lags by the piece's own yaw,
     * the step the piece is drawn at, so the cat does not slide on the seat.
     */
    private fun onPiece(p: Int, row: Float, depth: Float): Int {
        val own = blend[p].sway
        val inside = (swing - own) * level.depthGain * depth
        return Parallax.shift(1f, swing * Parallax.gainAtRow(row, level) + inside)
    }

    /** The index among the solids of the piece covering a cat on [surface] of [volume], or -1. */
    private fun coverOf(volume: PieceVolume, surface: String): Int {
        val name = volume.covered[surface] ?: return -1
        for (k in solids.indices) if (solids[k].name == name) return k
        return -1
    }

    /**
     * One strip of a band of a piece: the piece's contact shadow first if it is its first drawn.
     */
    private fun paintBand(c: RoomCanvas, b: Int) {
        val alpha = mix.alpha[b]
        if (alpha < MIN_ALPHA) return
        val p = mix.piece[b]
        val piece = solids[p]
        val volume = piece.volume!!
        val shift = rowShift(volume.parallaxFront)
        val first = !shadowed[p]
        shadowed[p] = true
        if (first && style.softShadows && piece.onFloor) {
            ContactShadow.paint(
                c,
                piece.houseX + shift + piece.width / 2,
                piece.baseY!!,
                piece.width / 2 + DepthTuning.PIECE_SHADOW_MARGIN,
                Math.round(DepthTuning.PIECE_SHADOW_HALF_HEIGHT * level.shadowSize),
                DepthTuning.PIECE_SHADOW_STRENGTH * level.shadowStrength,
                shadowColour[piece.room],
            )
        }
        c.sheet(
            mix.image(b),
            mix.slot[b],
            sheets.version + mix.slot[b],
            mix.sx(b),
            mix.sy(b),
            mix.w(b),
            mix.h(b),
            piece.houseX + shift + mix.dx(b),
            piece.y + mix.dy(b),
            alpha,
        )
    }

    // ------------------------------------------------------------------ 3. over everything

    /** The classic overlay: the tint of the hour and the lamp's glow. */
    fun paintOverlay(c: RoomCanvas, hour: Double, topExtra: Int) =
        scene.paintOverlay(c, hour, topExtra)

    private companion object {
        /** rgba(255,236,160,.16) and rgba(150,170,255,.12), as the classic room's window light. */
        const val DAY_LIGHT = (41 shl 24) or 0xFFECA0
        const val NIGHT_LIGHT = (31 shl 24) or 0x96AAFF

        /** A band of a cross-fade fainter than this draws nothing a screen could show. */
        const val MIN_ALPHA = 1f / 512f

        /** The darkest colour of a room's floor: the colour its furniture's shadows are. */
        fun floorShadowColour(base: ArgbImage): Int {
            var best = 0
            var lum = Int.MAX_VALUE
            for (y in Parallax.WALL_ROW.toInt() + 1 until base.height) for (x in
                0 until base.width) {
                val p = base[x, y]
                val l = Palette.luminanceOf(p)
                if (p ushr 24 == 0xFF && l < lum) {
                    lum = l
                    best = p
                }
            }
            return best and 0xFFFFFF
        }
    }
}
