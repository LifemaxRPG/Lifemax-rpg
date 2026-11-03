package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.FrameRect
import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.room.RoomLayout.HOUSE_X0
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Paints the house the way the web room's `petDraw` does, in the same order, with the same numbers,
 * onto a [RoomCanvas]:
 * 1. [paintBackdrop]: the sky behind each window (six bands, stars, sun or moon, clouds), the three
 *    painted rooms, and the window light on the floor.
 * 2. [paintScene]: the furniture and the cats, back to front by depth (bed back, bowls, bed front,
 *    the box, the pouf, the cushion, and each cat between the pieces it stands among).
 * 3. [paintOverlay]: the time-of-day tint, multiplied over everything, and the lamp's glow.
 *
 * The room is pure data and arithmetic: it keeps no clock. The caller says what [hour] it is and
 * how many seconds the room has run (the clouds drift and the stars twinkle with it).
 *
 * With a [style] that asks for it ([RoomStyle.DEPTH25]) the same three calls draw the 2.5D room
 * instead ([DepthScene]): the furniture as layers between the backdrop and the cats, a little
 * parallax, solid furniture, soft shadows and light. The default style draws exactly what is above.
 *
 * @param art the shipped pictures.
 * @param style how to draw the room; [RoomStyle.CLASSIC] by default.
 */
internal class RoomScene(private val art: RoomArt, val style: RoomStyle = RoomStyle.CLASSIC) {
    private val sky = IntArray(2)
    private val order = IntArray(MAX_CATS)
    private val atlas = art.atlas
    private val sun = art.item("sun")
    private val moon = art.item("moon")
    private val cloud1 = art.item("cloud1")
    private val cloud2 = art.item("cloud2")
    private val bedBack = art.item("bed_back")
    private val bedFront = art.item("bed_front")
    private val bowlEmpty = art.item("bowl_empty")
    private val bowlFull = art.item("bowl_food")
    private val bowlWater = art.item("bowl_water")
    private val glow = LampGlow()

    /**
     * The 2.5D painter, when the style wants it and the art has the layers; null for the classic
     * room.
     */
    val depth: DepthScene? =
        if (style.depth && art.layers != null) DepthScene(art, style, this) else null

    /**
     * Shift every furniture sprite drawn from here on by this many pixels (the parallax); 0 in the
     * classic room.
     */
    internal var shiftX = 0

    /** Whether the food bowl is full (a fed cat) or empty. */
    var foodBowlFull = false

    // ------------------------------------------------------------------ 1. behind the furniture

    /**
     * The sky, the painted rooms and the window light. [topExtra] is how many rows above the walls
     * the card shows (the top row of each room is stretched up to fill them); 0 for the house
     * itself.
     */
    fun paintBackdrop(c: RoomCanvas, hour: Double, clock: Double, topExtra: Int) {
        if (depth != null) return depth.paintBackdrop(c, hour, clock, topExtra)
        RoomLight.skyAt(hour, sky)
        for (wx in RoomLayout.windows) paintSky(c, wx, hour, clock)
        paintRoom(c, RoomImage.LIVING, 0, topExtra)
        paintRoom(c, RoomImage.BEDROOM, RoomLayout.BACKDROP_WIDTH, topExtra)
        paintRoom(c, RoomImage.KITCHEN, 2 * RoomLayout.BACKDROP_WIDTH, topExtra)
        paintWindowLight(c, hour)
    }

    private fun paintRoom(c: RoomCanvas, image: RoomImage, x: Int, topExtra: Int) {
        val w = RoomLayout.BACKDROP_WIDTH
        c.image(image, 0, 0, w, RoomLayout.HEIGHT, x, 0, w, RoomLayout.HEIGHT)
        if (topExtra > 0) c.image(image, 0, 0, w, 1, x, -topExtra, w, topExtra)
    }

    /** One window's sky, clipped to its glass. [wx] is the window's offset in world pixels. */
    internal fun paintSky(c: RoomCanvas, wx: Int, hour: Double, clock: Double, grow: Int = 0) {
        val night = RoomLight.isNight(hour)
        val left = wx - RoomLayout.HOUSE_X0 // house x of the window's own origin
        val glassX = left + RoomLayout.GLASS_X - grow
        val glassW = RoomLayout.GLASS_W + 2 * grow
        c.clip(glassX, RoomLayout.GLASS_Y, glassW, RoomLayout.GLASS_H)
        // six bands of the sky, each the run of rows the web gives the same colour
        var start = 0
        for (row in 1..RoomLayout.GLASS_H) {
            if (row < RoomLayout.GLASS_H && skyBandOf(row) == skyBandOf(start)) continue
            val colour =
                RoomLight.skyBand(sky[RoomLight.TOP], sky[RoomLight.BOTTOM], skyBandOf(start))
            c.fill(glassX, RoomLayout.GLASS_Y + start, glassW, row - start, OPAQUE or colour)
            start = row
        }
        if (night || hour < STARS_UNTIL || hour >= STARS_FROM) paintStars(c, left, night, clock)
        if (hour >= SUN_FROM && hour < SUN_UNTIL) {
            val u = (hour - SUN_FROM) / (SUN_UNTIL - SUN_FROM)
            item(c, sun, left + 22 + u * 58 - 9, 62 - sin(u * PI) * 42)
        } else {
            val u = (if (hour >= SUN_UNTIL) hour - SUN_UNTIL else hour + 5) / 11
            item(c, moon, left + 22 + u * 58 - 7, 62 - sin(u * PI) * 40)
        }
        if (!night) {
            item(c, cloud1, left + 22 + ((clock * 1.6 + wx * 0.37) % 110) - 30, 22.0)
            item(c, cloud2, left + 22 + ((clock * 1.0 + 70 + wx * 0.21) % 110) - 24, 40.0)
        }
        c.unclip()
    }

    /** Pick the sky colours of [hour] for the painters that follow. */
    internal fun skyFor(hour: Double) = RoomLight.skyAt(hour, sky)

    /** Which of the six bands of the glass the row [row] (0 at the top) is in. */
    private fun skyBandOf(row: Int): Int =
        minOf(RoomLight.BANDS - 1, floor(row / GLASS_H * RoomLight.BANDS).toInt())

    /** Sixteen stars: bright and twinkling at night, faint at dusk and dawn. */
    private fun paintStars(c: RoomCanvas, left: Int, night: Boolean, clock: Double) {
        for (i in 0 until STARS) {
            val sx = 24 + ((i * 37 + 11) % 60)
            val sy = 16 + ((i * 23 + 5) % 34)
            val twinkle = 0.5 + 0.5 * sin(clock * 1.7 + i * 1.9)
            val alpha = if (night) (if (twinkle > 0.35) 1.0 else 0.35) else 0.25
            val size = if (i % 5 == 0) 2 else 1
            c.fill(left + sx, sy, size, size, STAR or (alpha8(alpha) shl 24))
        }
    }

    /** The window's light lying on the floor of the living room and the bedroom. */
    internal fun paintWindowLight(c: RoomCanvas, hour: Double) {
        val colour =
            when {
                hour >= 7.5 && hour < 17.5 -> DAY_LIGHT
                RoomLight.isNight(hour) -> NIGHT_LIGHT
                else -> return
            }
        for (i in 0..1) {
            val x = (RoomLayout.windows[i] - RoomLayout.HOUSE_X0).toFloat()
            c.quad(x + 30, 118f, x + 90, 118f, x + 112, 142f, x + 52, 142f, colour)
        }
    }

    // ------------------------------------------------------------------ 2. furniture and cats

    /**
     * The furniture and the [cats], back to front. A cat sorts just after whatever stands at its
     * own depth (it sits on a prop, not inside it); two cats at the same depth go by the row of
     * their feet.
     */
    fun paintScene(c: RoomCanvas, cats: CatLayer) {
        if (depth != null) return depth.paintScene(c, cats)
        val n = cats.count.coerceAtMost(MAX_CATS)
        for (i in 0 until n) order[i] = i
        // at most two cats: a swap sorts them
        if (n == 2 && behind(cats, 1, 0)) {
            order[0] = 1
            order[1] = 0
        }
        var next = 0
        for (item in FURNITURE.indices) {
            while (next < n && cats.depth(order[next]) + CAT_AFTER <= FURNITURE_Z[item]) {
                cats.draw(order[next++])
            }
            paintFurniture(c, FURNITURE[item])
        }
        while (next < n) cats.draw(order[next++])
    }

    private fun behind(cats: CatLayer, a: Int, b: Int): Boolean {
        val za = cats.depth(a)
        val zb = cats.depth(b)
        return za < zb || (za == zb && cats.feet(a) < cats.feet(b))
    }

    /** The depth the web sorts each piece of atlas furniture at, in drawing order. */
    internal val furnitureZ: FloatArray
        get() = FURNITURE_Z

    /**
     * Draw the atlas furniture number [k] (its place in [FURNITURE_Z]); the 2.5D room sorts them
     * itself.
     */
    internal fun paintFurnitureAt(c: RoomCanvas, k: Int) = paintFurniture(c, FURNITURE[k])

    private fun paintFurniture(c: RoomCanvas, piece: Furniture) {
        val bedX = (RoomLayout.BED_X - HOUSE_X0).toDouble()
        when (piece) {
            Furniture.BED_BACK -> item(c, bedBack, bedX, RoomLayout.BED_BACK_Y.toDouble())
            Furniture.BOWLS -> {
                val top = RoomLayout.BOWL_TOP.toDouble()
                val food = RoomLayout.BOWL_FOOD_X - RoomLayout.BOWL_HALF_WIDTH - HOUSE_X0
                val water = RoomLayout.BOWL_WATER_X - RoomLayout.BOWL_HALF_WIDTH - HOUSE_X0
                item(c, if (foodBowlFull) bowlFull else bowlEmpty, food.toDouble(), top)
                item(c, bowlWater, water.toDouble(), top)
            }
            Furniture.BED_FRONT -> item(c, bedFront, bedX, RoomLayout.BED_FRONT_Y.toDouble())
            Furniture.BOX_BACK -> prop(c, RoomImage.BOX_BACK, BOX_X, BOX_Z)
            Furniture.BOX_FRONT -> prop(c, RoomImage.BOX_FRONT, BOX_X, BOX_Z)
            Furniture.POUF -> prop(c, RoomImage.POUF, POUF_X, POUF_Z)
            Furniture.CUSHION -> prop(c, RoomImage.CUSHION, CUSHION_X, CUSHION_Z)
        }
    }

    /**
     * A free-standing prop at world [x] and depth [z]: scaled by the depth, its base on the floor
     * at that depth (the web's `petDrawProp` at 1x).
     */
    private fun prop(c: RoomCanvas, image: RoomImage, x: Int, z: Float) {
        val src = art.image(image)
        val pct = World.depthScalePct(z)
        val w = src.width * (pct / 100.0)
        val h = src.height * (pct / 100.0)
        val floorY =
            World.HORIZON_Y + (World.FLOOR_Y_ART - World.HORIZON_Y).toDouble() * pct / 100.0
        val dx = RoomLight.jsRound(x - w / 2) - RoomLayout.HOUSE_X0 + shiftX
        val dy = RoomLight.jsRound(floorY - h + 1)
        c.image(
            image,
            0,
            0,
            src.width,
            src.height,
            dx,
            dy,
            RoomLight.jsRound(w),
            RoomLight.jsRound(h),
        )
    }

    /** An atlas sprite with its top left at house position ([x], [y]), rounded half up. */
    private fun item(c: RoomCanvas, r: FrameRect, x: Double, y: Double) {
        c.image(
            RoomImage.ATLAS,
            r.x,
            r.y,
            r.width,
            r.height,
            RoomLight.jsRound(x) + shiftX,
            RoomLight.jsRound(y),
            r.width,
            r.height,
        )
    }

    // ------------------------------------------------------------------ 3. over everything

    /**
     * The time-of-day tint over the whole picture, and the living room lamp's glow when it is dim.
     */
    fun paintOverlay(c: RoomCanvas, hour: Double, topExtra: Int) {
        val tint = RoomLight.tint(hour)
        if (!RoomLight.tinted(tint)) return
        c.multiply(tint, 0, -topExtra, RoomLayout.HOUSE_WIDTH, RoomLayout.HEIGHT + topExtra)
        val alpha = RoomLight.lampAlpha(tint)
        val lx = RoomLayout.LAMP_X - RoomLayout.HOUSE_X0
        val reach = RoomLayout.LAMP_REACH
        glow.bake(alpha)
        c.overlay(glow.mask, glow.version, lx - reach, 0)
        c.fill(lx - 7, 26, 15, 16, LAMP_CORE or (alpha8(alpha * LAMP_CORE_GAIN) shl 24))
    }

    /** `0..1` as the 8-bit alpha a canvas stores, rounded half up. */
    private fun alpha8(a: Double): Int = RoomLight.jsRound(a * 255.0)

    private enum class Furniture {
        BED_BACK,
        BOWLS,
        BED_FRONT,
        BOX_BACK,
        BOX_FRONT,
        POUF,
        CUSHION,
    }

    private companion object {
        const val MAX_CATS = 2
        const val OPAQUE = 0xFF000000.toInt()
        const val STAR = 0xFFFFE6
        const val STARS = 16
        const val STARS_UNTIL = 7.0
        const val STARS_FROM = 18.5
        const val SUN_FROM = 6.0
        const val SUN_UNTIL = 19.0
        const val GLASS_H = RoomLayout.GLASS_H.toDouble()
        const val CAT_AFTER = 0.01f
        const val LAMP_CORE = 0xFFECB4 // rgb(255, 236, 180)
        const val LAMP_CORE_GAIN = 1.6

        /** rgba(255,236,160,.16) and rgba(150,170,255,.12) as 8-bit alpha colours. */
        const val DAY_LIGHT = (41 shl 24) or 0xFFECA0
        const val NIGHT_LIGHT = (31 shl 24) or 0x96AAFF

        /** The furniture in the order the web sorts it (stable by depth). */
        val FURNITURE =
            arrayOf(
                Furniture.BED_BACK,
                Furniture.BOWLS,
                Furniture.BED_FRONT,
                Furniture.BOX_BACK,
                Furniture.BOX_FRONT,
                Furniture.POUF,
                Furniture.CUSHION,
            )
        const val BOX_X = 290
        const val BOX_Z = 42f
        const val POUF_X = -34
        const val POUF_Z = 45f
        const val CUSHION_X = -120
        const val CUSHION_Z = 66f
        val FURNITURE_Z: FloatArray =
            floatArrayOf(
                RoomLayout.Z_BED_BACK,
                RoomLayout.Z_BOWLS,
                RoomLayout.Z_BED_FRONT,
                BOX_Z - 0.2f,
                BOX_Z + 0.2f,
                POUF_Z,
                CUSHION_Z,
            )
    }
}

/**
 * The living room lamp's glow: a soft round patch of warm light, as a picture. The web paints it as
 * a radial gradient (the colour at [RoomLayout.LAMP_INNER] pixels out, fading to nothing at
 * [RoomLayout.LAMP_REACH]); here it is baked once per strength, since the strength follows the tint
 * and changes slowly, and kept until it does.
 */
internal class LampGlow {
    private var alpha8 = -1
    private var image: ArgbImage = ArgbImage(0, 0, IntArray(0))

    /** The glow's picture: [RoomLayout.LAMP_REACH] * 2 wide, the whole house height. */
    val mask: ArgbImage
        get() = image

    /** Names the picture's contents; changes when the strength does. */
    val version: Int
        get() = alpha8

    /** Make the picture for a glow of [alpha] (0 to 0.32) if it is not the one held. */
    fun bake(alpha: Double) {
        val a8 = RoomLight.jsRound(alpha * 255.0)
        if (a8 == alpha8) return
        alpha8 = a8
        val reach = RoomLayout.LAMP_REACH
        val w = reach * 2
        val pixels = IntArray(w * RoomLayout.HEIGHT)
        val inner = RoomLayout.LAMP_INNER.toDouble()
        val strength = a8 / 255.0
        for (y in 0 until RoomLayout.HEIGHT) {
            for (x in 0 until w) {
                val dx = x + 0.5 - reach
                val dy = y + 0.5 - RoomLayout.LAMP_Y
                val d = Math.sqrt(dx * dx + dy * dy)
                val t = ((d - inner) / (reach - inner)).coerceIn(0.0, 1.0)
                val a = RoomLight.jsRound(strength * (1.0 - t) * 255.0)
                pixels[y * w + x] = (a shl 24) or GLOW
            }
        }
        image = ArgbImage(w, RoomLayout.HEIGHT, pixels)
    }

    private companion object {
        /** rgb(255, 214, 140). */
        const val GLOW = 0xFFD68C
    }
}
