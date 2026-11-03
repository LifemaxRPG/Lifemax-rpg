package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.room.CameraDirector
import com.lifemax.rpg.feature.pet.room.CatLayer
import com.lifemax.rpg.feature.pet.room.ContactShadow
import com.lifemax.rpg.feature.pet.room.RimLight
import com.lifemax.rpg.feature.pet.room.RimSpec
import com.lifemax.rpg.feature.pet.room.RoomCanvas
import com.lifemax.rpg.feature.pet.room.RoomLayout
import com.lifemax.rpg.feature.pet.room.RoomLight
import com.lifemax.rpg.feature.pet.room.RoomScene
import com.lifemax.rpg.feature.pet.room.RoomStyle
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The live room on the Overview card: the house, one or two cats living in it, a camera that
 * follows them, and the time of day. It is stepped once per display frame and drawn onto a Compose
 * canvas.
 *
 * What it draws is what the web room draws ([RoomScene]): the sky behind each window by the hour,
 * the three painted rooms, the window light on the floor, the furniture and the cats back to front
 * by depth, the time-of-day tint and the lamp's glow. The card shows a window of at least 192 room
 * pixels onto the 576 pixel house, at a whole number of device pixels per room pixel, drawn without
 * smoothing; the camera is the web's (see [CameraDirector]).
 *
 * A cat whose sprites are not here (yet, or ever) is not drawn and the camera ignores it, while the
 * other lives on; the first cat's sprites are what the card waits for before it shows the room.
 *
 * @param kit the room's art.
 * @param house the cats of the house.
 * @param hourNow the local time of day as a number of hours, 0 to 24; asked about once a second
 *   while the clock runs, so a test can fix the hour.
 */
internal class HouseStage(
    kit: RoomKit,
    val house: HouseLife,
    style: RoomStyle = RoomStyle.CLASSIC,
    private val hourNow: () -> Double = ::localHour,
) : CatLayer {
    private val scene = RoomScene(kit.art, style)
    private val canvas = ComposeRoomCanvas(kit.bitmaps)
    private val director = CameraDirector()
    private val sprites = arrayOfNulls<CatSprites>(HouseLife.MAX_CATS)
    private val visible = IntArray(HouseLife.MAX_CATS)
    private var visibleCount = 0
    private var clock = 0.0
    private var fedHouse = false
    private var bowlFullFor = 0f
    private var sinceHour = 0f
    private var hour = hourNow()
    private lateinit var scope: DrawScope
    private var scale = 1
    private var originX = 0
    private var originY = 0
    private val rim = RimSpec()

    /** Hand over the sprites for cat [i] (null while they load or if they cannot be had). */
    fun setSprites(i: Int, value: CatSprites?) {
        if (i >= house.count) return
        sprites[i] = value
        visibleCount = 0
        for (k in 0 until house.count) if (sprites[k] != null) visible[visibleCount++] = k
        frameCats()
    }

    /** Whether the food bowl shows food: a fed house. */
    fun setFedHouse(fed: Boolean) {
        fedHouse = fed
        scene.foodBowlFull = fed || bowlFullFor > 0f
    }

    /**
     * The owner fed cat [cat]: it goes to its bowl and eats, and the bowl shows food for as long as
     * the page's does ([BOWL_FULL_S]).
     */
    fun feed(cat: Int) {
        house.serveMeal(cat)
        bowlFullFor = BOWL_FULL_S
        scene.foodBowlFull = true
    }

    /** The owner tried to feed cat [cat] and it was full: it goes to its bowl and washes. */
    fun visitBowl(cat: Int) = house.visitBowl(cat)

    /** The owner slow blinks at cat [cat]; true if the cat blinked back. */
    fun slowBlink(cat: Int): Boolean = house.slowBlink(cat)

    /**
     * The tilt of the phone, -1 to 1 (0 when there is no sensor), which the 2.5D room's camera
     * sways with; ignored by the classic room and with reduced motion.
     */
    fun setTilt(tilt: Float) {
        scene.depth?.sway?.tilt = tilt
    }

    /** Put the 2.5D room's camera at rest at [at], -1 to 1 (screenshots and tests). */
    fun holdSway(at: Float) {
        scene.depth?.sway?.restAt(at)
    }

    /**
     * Put the camera on world x [x] at once (screenshots and tests; the director follows the cats).
     */
    fun lookAt(x: Float) = director.camera.cutTo(x)

    /** Advance the cats, the camera and the room's clock by [dt] seconds. */
    fun step(dt: Float) {
        house.step(dt)
        scene.depth?.step(dt)
        followCats(dt)
        if (bowlFullFor > 0f) {
            bowlFullFor -= dt
            if (bowlFullFor <= 0f) scene.foodBowlFull = fedHouse
        }
        clock += dt
        sinceHour += dt
        if (sinceHour >= HOUR_REFRESH_S) {
            sinceHour = 0f
            hour = hourNow()
        }
    }

    /** Step [seconds] at [hz] frames per second, as the screen does; for tests and previews. */
    fun advance(seconds: Float, hz: Int = 60) {
        val dt = 1f / hz
        repeat(Math.round(seconds * hz)) { step(dt) }
    }

    /** Frame the cats with the camera (the first time, it cuts to them). */
    private fun frameCats() {
        if (visibleCount == 0) return
        director.update(house.bodies[visible[0]], second(), 0f)
    }

    private fun second(): CatBody? = if (visibleCount > 1) house.bodies[visible[1]] else null

    private fun followCats(dt: Float) {
        if (visibleCount == 0) return
        director.update(house.bodies[visible[0]], second(), dt)
    }

    // ------------------------------------------------------------------ drawing

    /**
     * Draw the room onto this scope: the whole of it, with the house scaled by a whole number of
     * device pixels, the camera's view centred, and the bottom of the house on the bottom edge.
     */
    fun draw(target: DrawScope) {
        scope = target
        val s = pixelScale(target.size.width)
        scale = s
        val view = (target.size.width / s).coerceAtMost(RoomLayout.HOUSE_WIDTH.toFloat())
        val left = director.camera.left(view) - RoomLayout.HOUSE_X0
        originX = -(left * s).roundToInt()
        originY = (target.size.height - RoomLayout.HEIGHT * s).roundToInt()
        val topExtra = ceil(originY.toFloat() / s).toInt().coerceAtLeast(0)
        canvas.begin(target, s, originX, originY)
        scene.paintBackdrop(canvas, hour, clock, topExtra)
        scene.paintScene(canvas, this)
        scene.paintOverlay(canvas, hour, topExtra)
    }

    // ------------------------------------------------------------------ the cats, for the painter

    override val count: Int
        get() = visibleCount

    override fun depth(i: Int): Float = house.bodies[visible[i]].pose.z

    override fun feet(i: Int): Float = house.bodies[visible[i]].pose.y

    override fun surface(i: Int): String? = house.bodies[visible[i]].pose.surface.key

    /**
     * Cat [i] in the 2.5D room: [shift] pixels over for the parallax, the soft contact shadow in
     * place of the two-row blob, and a rim of window light under the cat on the window's side.
     */
    override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
        val k = visible[i]
        val pose = house.bodies[k].pose
        val houseX = RoomLayout.HOUSE_X0.toFloat()
        val footX = (originX + (pose.x - houseX + shift) * scale).roundToInt().toFloat()
        val footY = (originY + (pose.y + pose.bob) * scale).roundToInt().toFloat()
        if (style.softShadows) {
            val halfW = (pose.shadowHalfWidth * pose.shadowScale).roundToInt()
            ContactShadow.paint(
                canvas,
                (pose.shadowX - houseX + shift).roundToInt(),
                pose.shadowY.roundToInt(),
                halfW,
                ContactShadow.halfHeightFor(halfW),
                pose.shadowAlpha / Tuning.Shadow.ALPHA,
            )
        } else {
            val shadowX = originX + (pose.shadowX - houseX + shift) * scale
            scope.drawShadowAt(pose, scale, shadowX, originY + pose.shadowY * scale)
        }
        if (style.rimLight) {
            RimLight.of(hour, pose.x, rim)
            if (rim.side != 0) {
                val tint = Color(rim.colour or OPAQUE).copy(alpha = rim.strength)
                scope.drawSpriteAt(pose, sprites[k]!!, scale, footX + rim.side * scale, footY, tint)
            }
        }
        scope.drawSpriteAt(pose, sprites[k]!!, scale, footX, footY)
    }

    override fun draw(i: Int) {
        val k = visible[i]
        val pose = house.bodies[k].pose
        val houseX = RoomLayout.HOUSE_X0.toFloat()
        val footX = (originX + (pose.x - houseX) * scale).roundToInt().toFloat()
        val footY = (originY + (pose.y + pose.bob) * scale).roundToInt().toFloat()
        val shadowX = originX + (pose.shadowX - houseX) * scale
        val shadowY = originY + pose.shadowY * scale
        scope.drawShadowAt(pose, scale, shadowX, shadowY)
        scope.drawSpriteAt(pose, sprites[k]!!, scale, footX, footY)
    }

    private companion object {
        const val HOUR_REFRESH_S = 1f
        const val OPAQUE = 0xFF000000.toInt()

        /** How long the bowl shows food after a meal (the page empties it after 14 s). */
        const val BOWL_FULL_S = 14f
    }
}

/** The device's local time of day in hours, 0 to 24. */
internal fun localHour(): Double {
    val now = System.currentTimeMillis()
    return RoomLight.hourOf(now + TimeZone.getDefault().getOffset(now))
}
