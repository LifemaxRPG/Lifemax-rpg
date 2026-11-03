package com.lifemax.rpg.feature.pet.room

/** Numbers of the 2.5D painter that are about the picture, not the light or the sway. */
internal object DepthTuning {
    /** A piece's contact shadow is this many art pixels tall (half height) and this strong. */
    const val PIECE_SHADOW_HALF_HEIGHT = 3
    const val PIECE_SHADOW_STRENGTH = 0.8f

    /** A piece's shadow is this many pixels wider than the piece, each side. */
    const val PIECE_SHADOW_MARGIN = 1

    /** A piece stands on the floor when its bottom row is within this many rows of its base. */
    const val FLOOR_REACH = 2
}

/**
 * Paints the 2.5D room, in the same three steps as [RoomScene] (it is what [RoomScene] calls when
 * its style asks for the layers):
 * 1. [paintBackdrop]: the sky (a little wider than the glass, so a shifting wall never shows a
 *    gap), each room's base (the backdrop with the furniture removed), the seam where wall meets
 *    floor, the window light on the floor, and the rugs.
 * 2. [paintScene]: the standing furniture, the atlas furniture and the cats, back to front by
 *    [LayerStack]. Each thing is shifted by its plane's parallax; a thick piece gets its side face
 *    first; a piece that stands on the floor gets its contact shadow first; a cat is drawn by its
 *    own layer (it knows its shadow and its rim).
 * 3. the classic overlay, [RoomScene.paintOverlay]: the time of day over everything.
 *
 * The camera sways with [sway]; step it with [step] once a frame. With [RoomStyle.parallax] off (or
 * reduced motion on) every shift is 0 and the room is still.
 */
internal class DepthScene(
    private val art: RoomArt,
    private val style: RoomStyle,
    private val scene: RoomScene,
) {
    private val layers = art.layers ?: error("the 2.5D room needs the layers")

    /** The camera's sway; set its tilt from a sensor, read its position to see where it is. */
    val sway = DepthSway(style.reducedMotion, style.level.idleAmplitude)

    /** The drawing order of the last [paintScene]. */
    val stack = LayerStack(layers.pieces, scene.furnitureZ)

    private val level = style.level
    private val vector = IntArray(2)

    /** [argb] with its alpha scaled by the level's light strength. */
    private fun light(argb: Int): Int =
        (Math.round((argb ushr 24) * level.lightStrength) shl 24) or (argb and 0xFFFFFF)

    private val steps = IntArray(2 * DepthLevel.RICH.maxFacePx)

    /** Advance the sway by [dt] seconds. */
    fun step(dt: Float) = sway.step(dt)

    /** The sway the planes use: 0 when parallax is off. */
    private val swing: Float
        get() = if (style.parallax) sway.position else 0f

    /** The shift of a floor object (a cat, a prop) at depth [z], for the cat's own layer. */
    fun shiftAtZ(z: Float): Int = Parallax.shiftAtZ(swing, z, level)

    // ------------------------------------------------------------------ 1. behind the furniture

    /** The sky, the bases, the seam, the window light and the rugs. */
    fun paintBackdrop(c: RoomCanvas, hour: Double, clock: Double, topExtra: Int) {
        scene.skyFor(hour)
        val wall = Parallax.shift(swing, Plane.WALL, level)
        for (wx in RoomLayout.windows) scene.paintSky(c, wx, hour, clock, Parallax.maxShift)
        paintBases(c, wall, topExtra)
        if (style.floorLight) {
            SeamShade.paint(c, wall, RoomLayout.HOUSE_WIDTH, level.seamStrength)
            paintFloorLight(c, wall, hour)
        } else {
            scene.paintWindowLight(c, hour)
        }
        for (i in 0 until layers.pieces.size) { // an index loop: no iterator per frame
            val piece = layers.pieces[i]
            if (piece.flat) paintPiece(c, piece, wall)
        }
    }

    private fun paintBases(c: RoomCanvas, shift: Int, topExtra: Int) {
        val w = RoomLayout.BACKDROP_WIDTH
        val h = RoomLayout.HEIGHT
        for (room in 0..2) {
            val x = room * w + shift
            val image = BASES[room]
            c.image(image, 0, 0, w, h, x, 0, w, h)
            if (topExtra > 0) c.image(image, 0, 0, w, 1, x, -topExtra, w, topExtra)
        }
        // the house's two outer edges: stretch the edge column over the gap a shift opens
        if (shift > 0) {
            c.image(BASES[0], 0, 0, 1, h, 0, 0, shift, h)
            if (topExtra > 0) c.image(BASES[0], 0, 0, 1, 1, 0, -topExtra, shift, topExtra)
        } else if (shift < 0) {
            val end = RoomLayout.HOUSE_WIDTH + shift
            c.image(BASES[2], w - 1, 0, 1, h, end, 0, -shift, h)
            if (topExtra > 0) c.image(BASES[2], w - 1, 0, 1, 1, end, -topExtra, -shift, topExtra)
        }
    }

    private fun paintFloorLight(c: RoomCanvas, shift: Int, hour: Double) {
        val colour =
            when {
                hour >= 7.5 && hour < 17.5 -> light(DAY_LIGHT)
                RoomLight.isNight(hour) -> light(NIGHT_LIGHT)
                else -> return
            }
        for (i in 0..1) {
            FloorLight.paint(
                c,
                (RoomLayout.windows[i] - RoomLayout.HOUSE_X0 + shift).toFloat(),
                hour,
                colour,
            )
        }
    }

    // ------------------------------------------------------------------ 2. furniture and cats

    /** The standing furniture, the atlas furniture and the cats, back to front. */
    fun paintScene(c: RoomCanvas, cats: CatLayer) {
        stack.build(cats)
        val order = stack.order
        for (k in 0 until stack.size) {
            val code = order[k]
            when {
                code >= LayerStack.CAT -> {
                    val cat = code - LayerStack.CAT
                    cats.draw(cat, Parallax.shiftAtZ(swing, cats.depth(cat), level), style, c)
                }
                code >= LayerStack.FURNITURE -> {
                    val item = code - LayerStack.FURNITURE
                    scene.shiftX = Parallax.shiftAtZ(swing, scene.furnitureZ[item], level)
                    scene.paintFurnitureAt(c, item)
                    scene.shiftX = 0
                }
                else -> {
                    val piece = layers.pieces[code - LayerStack.PIECE]
                    if (!piece.flat) paintPiece(c, piece, Parallax.shift(swing, piece.plane, level))
                }
            }
        }
    }

    /** One piece: its floor shadow, its side face, then the piece, [shift] pixels over. */
    private fun paintPiece(c: RoomCanvas, piece: LayerPiece, shift: Int) {
        val x = piece.houseX + shift
        if (style.softShadows && piece.onFloor) {
            ContactShadow.paint(
                c,
                x + piece.width / 2,
                piece.baseY!!,
                piece.width / 2 + DepthTuning.PIECE_SHADOW_MARGIN,
                Math.round(DepthTuning.PIECE_SHADOW_HALF_HEIGHT * level.shadowSize),
                DepthTuning.PIECE_SHADOW_STRENGTH * level.shadowStrength,
            )
        }
        if (style.thickness && piece.thick) {
            Thickness.vector(swing, vector, level)
            val n = Thickness.offsets(vector[0], vector[1], steps)
            for (i in 0 until n) {
                c.image(
                    RoomImage.LAYERS_DARK,
                    piece.atlasX,
                    piece.atlasY,
                    piece.width,
                    piece.height,
                    x + steps[2 * i],
                    piece.y + steps[2 * i + 1],
                    piece.width,
                    piece.height,
                )
            }
        }
        c.image(
            RoomImage.LAYERS,
            piece.atlasX,
            piece.atlasY,
            piece.width,
            piece.height,
            x,
            piece.y,
            piece.width,
            piece.height,
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
        val BASES = arrayOf(RoomImage.LIVING_BASE, RoomImage.BEDROOM_BASE, RoomImage.KITCHEN_BASE)
    }
}
