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
 *    [DepthOrder]. Each standing piece is drawn from its baked faces ([SolidFaces]) at the camera's
 *    live angle: its painted front and the sides and top the angle shows, in bands of depth so a
 *    cat passes behind, in front of and inside it; a piece that stands on the floor gets its
 *    contact shadow, in the floor's own colour, first.
 * 3. the classic overlay, [RoomScene.paintOverlay]: the time of day over everything.
 *
 * The camera sways with [sway]; step it with [step] once a frame. Everything is continuous, like
 * the Habits street: the layers move by their parallax as it is (not rounded; each canvas places
 * them at its own nearest pixel, [RoomCanvas.nudge]) and each piece's sides are as wide as its yaw
 * says ([FaceView]), with no camera steps and no cross-fades. With [RoomStyle.parallax] off (or
 * reduced motion on) nothing moves and every piece is its painted sprite; with [RoomStyle.volume]
 * off the pieces move but stay flat. A frame renders nothing: the faces are baked once per piece
 * ([prepare], [warm]) and only where they are drawn changes.
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

    /** The solid pieces in bands of a floor row of depth, and their drawing order. */
    private val bands = SolidBands(solids, furnitureKey)

    private val drawOrder = DepthOrder(bands.orderSize)
    private val pieceX =
        FloatArray(solids.size) {
            (solids[it].houseX + solids[it].width / 2f) + RoomLayout.HOUSE_X0
        }
    private val follows = style.cameraFollow && sways
    private val catShift = FloatArray(DepthOrder.MAX_CATS)
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

    /** Each piece's yaw this frame (-1 to 1, continuous): which side it shows, and how much. */
    private val pieceYaw = FloatArray(solids.size)

    /** The yaw piece [name] was drawn at in the last [paintScene] (tests, tuning). */
    fun yawOf(name: String): Float {
        for (p in solids.indices) if (solids[p].name == name) return pieceYaw[p]
        error("no solid piece $name")
    }

    /** The faces of piece [name] (tests, tuning); baked if need be. */
    fun facesOf(name: String): SolidFaces = solid(name).volume!!.faces

    private val shadowed = BooleanArray(solids.size)
    private val shadowColour = IntArray(layers.bases.size) { floorShadowColour(layers.base(it)) }

    /** The piece (an index among the solids) and the floor row of drawing-order band [code]. */
    fun bandPiece(code: Int): Int = bands.piece[code]

    fun bandKey(code: Int): Float = bands.key[code]

    /** How many floor rows behind its piece's front drawing-order band [code] is (tests). */
    fun bandNumber(code: Int): Int = bands.band[code]

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
     * Whether the faces are baked by a background thread ([warm]): then a frame never bakes but
     * draws a piece whose faces are not baked yet as its flat painted sprite. Off (the default:
     * tests, screenshots), a frame bakes what it needs.
     */
    var asyncWarm = false

    /** Whether every piece's faces are baked, so the room can be drawn without a hitch. */
    val ready: Boolean
        get() {
            for (p in solids.indices) if (!solids[p].volume!!.baked) return false
            return true
        }

    /**
     * Bake every piece's faces on the calling thread (a few milliseconds a piece, once per art:
     * every room drawn from the same art shares them, whatever its level). The live room calls it
     * from a background thread before the room is first shown; [cancelled] is asked between pieces.
     */
    fun warm(cancelled: () -> Boolean = { false }) {
        for (p in solids.indices) {
            if (cancelled()) return
            solids[p].volume!!.faces
        }
    }

    /** [warm], all of it. */
    fun prepare() = warm()

    /**
     * Follow the room camera: [screenX] is the world x at the middle of the screen. Call it before
     * [step] each frame; a no-op unless the style asks for [RoomStyle.cameraFollow].
     */
    fun follow(screenX: Float) {
        if (follows) sway.follow(screenX)
    }

    /** Advance the sway by [dt] seconds. */
    fun step(dt: Float) {
        sway.step(dt)
    }

    /** The sway the room moves with ([DepthSway.shown], held at rest): 0 when parallax is off. */
    private val swing: Float
        get() = if (style.parallax) sway.shown else 0f

    /** The wall's shift in whole pixels as the room is drawn now (the developer readout). */
    val wallShiftNow: Int
        get() = Parallax.wallShift(swing, level)

    /** The nearest floor row's shift in whole pixels as drawn now (the developer readout). */
    val nearShiftNow: Int
        get() = Parallax.shiftAtRow(swing, LiveDepthTuning.NEAR_ROW, level)

    /** The shift of a floor object (a cat, a prop) at depth [z], in whole house pixels. */
    fun shiftAtZ(z: Float): Int = Parallax.shiftAtZ(swing, z, level)

    /** The shift of a floor object at depth [z] as it is drawn (not rounded). */
    fun offsetAtZ(z: Float): Float = Parallax.offsetAtZ(swing, z, level)

    private fun rowOffset(row: Float) = Parallax.offsetAtRow(swing, row, level)

    // ------------------------------------------------------------------ 1. behind the furniture

    /** The sky, the bases, the seam, the window light and the rugs. */
    fun paintBackdrop(c: RoomCanvas, hour: Double, clock: Double, topExtra: Int) {
        scene.skyFor(hour)
        val wall = Parallax.wallOffset(swing, level)
        for (wx in RoomLayout.windows) scene.paintSky(c, wx, hour, clock, Parallax.maxWallShift)
        paintBases(c, wall, topExtra)
        if (style.floorLight) {
            c.nudge(wall)
            SeamShade.paint(c, 0, RoomLayout.HOUSE_WIDTH, level.seamStrength)
            c.nudge(0f)
            paintFloorLight(c, hour)
        } else {
            scene.paintWindowLight(c, hour)
        }
        for (i in 0 until layers.pieces.size) { // an index loop: no iterator per frame
            val piece = layers.pieces[i]
            if (piece.flat) paintRug(c, piece)
        }
    }

    private fun paintBases(c: RoomCanvas, wall: Float, topExtra: Int) {
        val seam = Parallax.WALL_ROW.toInt()
        strip(c, 0, seam, wall)
        if (topExtra > 0) {
            val rooms = HouseMap.current.rooms
            edges(c, 0, 1, -topExtra, topExtra, wall)
            c.nudge(wall)
            for (room in rooms.indices) {
                val x = room * RoomLayout.BACKDROP_WIDTH
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
            c.nudge(0f)
        }
        // the floor, in runs of rows that move by the same amount (row by row once it sways)
        var from = seam
        while (from < RoomLayout.HEIGHT) {
            val shift = rowOffset(from.toFloat())
            var to = from + 1
            while (to < RoomLayout.HEIGHT && rowOffset(to.toFloat()) == shift) to++
            strip(c, from, to - from, shift)
            from = to
        }
    }

    /** Rows [y] to `y + h` of the three bases, [shift] pixels over, the house's edges filled. */
    private fun strip(c: RoomCanvas, y: Int, h: Int, shift: Float) {
        val w = RoomLayout.BACKDROP_WIDTH
        val rooms = HouseMap.current.rooms
        edges(c, y, h, y, h, shift)
        c.nudge(shift)
        for (room in rooms.indices) {
            c.image(RoomImage.baseOf(rooms[room].id), 0, y, w, h, room * w, y, w, h)
        }
        c.nudge(0f)
    }

    /**
     * Stretch the house's outer edge columns, rows [sy] to `sy + sh`, over the gap a [shift] opens
     * at rows [dy] to `dy + dh`: drawn first and a whole pixel wide or more, so the shifted base
     * drawn over them leaves no gap whatever pixel it lands on.
     */
    private fun edges(c: RoomCanvas, sy: Int, sh: Int, dy: Int, dh: Int, shift: Float) {
        val w = RoomLayout.BACKDROP_WIDTH
        val rooms = HouseMap.current.rooms
        val gap = Math.ceil(Math.abs(shift).toDouble()).toInt()
        if (shift > 0f) {
            c.image(RoomImage.baseOf(rooms.first().id), 0, sy, 1, sh, 0, dy, gap, dh)
        } else if (shift < 0f) {
            c.image(
                RoomImage.baseOf(rooms.last().id),
                w - 1,
                sy,
                1,
                sh,
                RoomLayout.HOUSE_WIDTH - gap,
                dy,
                gap,
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
        c.nudge(rowOffset(DepthTuning.FLOOR_LIGHT_ROW))
        val lit = HouseMap.current.floorLights
        for (i in lit.indices) {
            FloorLight.paint(c, (lit[i] - RoomLayout.HOUSE_X0).toFloat(), hour, colour)
        }
        c.nudge(0f)
    }

    /** A rug, lying on the floor: row by row, each row moving with the floor under it. */
    private fun paintRug(c: RoomCanvas, piece: LayerPiece) {
        var from = 0
        while (from < piece.height) {
            val shift = rowOffset((piece.y + from).toFloat())
            var to = from + 1
            while (to < piece.height && rowOffset((piece.y + to).toFloat()) == shift) to++
            val h = to - from
            c.nudge(shift)
            c.image(
                RoomImage.LAYERS,
                piece.atlasX,
                piece.atlasY + from,
                piece.width,
                h,
                piece.houseX,
                piece.y + from,
                piece.width,
                h,
            )
            from = to
        }
        c.nudge(0f)
    }

    // ------------------------------------------------------------------ 2. furniture and cats

    /** The solid pieces, the atlas furniture and the cats, back to front. */
    fun paintScene(c: RoomCanvas, cats: CatLayer) {
        aim()
        val n = cats.count.coerceAtMost(DepthOrder.MAX_CATS)
        for (i in 0 until n) placeCat(cats, i)
        drawOrder.build(bands, furnitureKey, n, catKey, catFeet, catAnchor, catCover, catBefore)
        val order = drawOrder.order
        shadowed.fill(false)
        for (k in 0 until drawOrder.size) {
            val code = order[k]
            when {
                code >= DepthOrder.CAT -> {
                    val cat = code - DepthOrder.CAT
                    cats.draw(cat, catShift[cat], style, c)
                }
                code >= DepthOrder.FURNITURE -> {
                    val item = code - DepthOrder.FURNITURE
                    c.nudge(Parallax.offsetAtZ(swing, scene.furnitureZ[item], level))
                    scene.paintFurnitureAt(c, item)
                    c.nudge(0f)
                }
                else -> paintBand(c, code)
            }
        }
    }

    /**
     * Each piece's yaw: without the camera-follow parallax every piece follows the sway; with it
     * each its own, the idle drift and tilt plus where it sits against the middle of the screen
     * ([PanFollow.perspective]). Continuous: a piece's sides are as wide as its yaw says.
     */
    private fun aim() {
        for (p in solids.indices) {
            pieceYaw[p] =
                when {
                    !sways -> 0f
                    follows -> (sway.shownIdle + sway.shownPerspective(pieceX[p])).coerceIn(-1f, 1f)
                    else -> swing.coerceIn(-1f, 1f)
                }
        }
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
        catShift[i] = rowOffset(catKey[i])
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
     * as its faces are drawn, so the cat does not slide on the seat.
     */
    private fun onPiece(p: Int, row: Float, depth: Float): Float {
        val inside = (swing - pieceYaw[p]) * level.depthGain * depth
        return swing * Parallax.gainAtRow(row, level) + inside
    }

    /** The index among the solids of the piece covering a cat on [surface] of [volume], or -1. */
    private fun coverOf(volume: PieceVolume, surface: String): Int {
        val name = volume.covered[surface] ?: return -1
        for (k in solids.indices) if (solids[k].name == name) return k
        return -1
    }

    /**
     * One band of a piece: the piece's contact shadow first if it is its first drawn, then its
     * depth strips of that band at the piece's yaw, the whole moved by the parallax of its front.
     */
    private fun paintBand(c: RoomCanvas, b: Int) {
        val p = bands.piece[b]
        val piece = solids[p]
        val volume = piece.volume!!
        c.nudge(rowOffset(volume.parallaxFront))
        val first = !shadowed[p]
        shadowed[p] = true
        if (first && style.softShadows && piece.onFloor) {
            ContactShadow.paint(
                c,
                piece.houseX + piece.width / 2,
                piece.baseY!!,
                piece.width / 2 + DepthTuning.PIECE_SHADOW_MARGIN,
                Math.round(DepthTuning.PIECE_SHADOW_HALF_HEIGHT * level.shadowSize),
                DepthTuning.PIECE_SHADOW_STRENGTH * level.shadowStrength,
                shadowColour[piece.room],
            )
        }
        val k = bands.band[b]
        if (asyncWarm && !volume.baked) {
            // the faces are still being baked: the painted sprite, flat, with the front
            if (k == 0) {
                c.image(
                    RoomImage.LAYERS,
                    piece.atlasX,
                    piece.atlasY,
                    piece.width,
                    piece.height,
                    piece.houseX,
                    piece.y,
                    piece.width,
                    piece.height,
                )
            }
        } else {
            val yaw = pieceYaw[p]
            volume.faces.paintBand(
                c,
                p,
                k,
                piece.houseX.toFloat(),
                piece.y.toFloat(),
                FaceView.vx(yaw, level),
                FaceView.vy(yaw, level),
            )
        }
        c.nudge(0f)
    }

    // ------------------------------------------------------------------ 3. over everything

    /** The classic overlay: the tint of the hour and the lamp's glow. */
    fun paintOverlay(c: RoomCanvas, hour: Double, topExtra: Int) =
        scene.paintOverlay(c, hour, topExtra)

    private companion object {
        /** rgba(255,236,160,.16) and rgba(150,170,255,.12), as the classic room's window light. */
        const val DAY_LIGHT = (41 shl 24) or 0xFFECA0
        const val NIGHT_LIGHT = (31 shl 24) or 0x96AAFF

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
