package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.lifemax.rpg.feature.pet.body.Beat
import com.lifemax.rpg.feature.pet.body.Burst
import com.lifemax.rpg.feature.pet.body.CatBody
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.Petting
import com.lifemax.rpg.feature.pet.body.TouchModel
import com.lifemax.rpg.feature.pet.body.TouchSink
import com.lifemax.rpg.feature.pet.body.ToyField
import com.lifemax.rpg.feature.pet.body.Tuning
import com.lifemax.rpg.feature.pet.host.CatWorldHost
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
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val kit: RoomKit,
    val house: HouseLife,
    style: RoomStyle = RoomStyle.CLASSIC,
    private val hourNow: () -> Double = ::localHour,
) : CatLayer {
    private var scene = RoomScene(kit.art, style)

    /** How the room is drawn now ([useStyle] changes it). */
    var style = style
        private set

    /**
     * Whether the 2.5D room's camera steps are made by a background thread ([warmDepth]) and a
     * frame never waits for one. Off for screenshots and tests, which make what they need.
     */
    var asyncWarm = false
        set(value) {
            field = value
            scene.depth?.asyncWarm = value
        }

    /** Whether the room can be drawn without a hitch: the 2.5D room's rest picture is made. */
    val depthReady: Boolean
        get() = scene.depth?.ready ?: true

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

    /**
     * Whether ears, tail and whiskers follow the springs; false (reduced motion) keeps them as
     * drawn. A cat's proportions, from its genes, are not motion and always apply.
     */
    var secondaryMotion = true

    private var originX = 0
    private var originY = 0

    /**
     * The world x at the middle of the screen as last drawn (the camera's, kept inside the house).
     */
    private var screenCentre = 0f
    private var drawn = false
    private val rim = RimSpec()
    private val particles = TouchParticles(kit.art)

    /** Where a touch's page-side effects go: the buzz, the purr's sound, the page's books. */
    var touchSink: TouchSink? = null

    /**
     * Fill the whole of a window of any shape (the full-screen room): a larger scale, the house
     * centred upright with the dark around it. Off, the room is the card's.
     */
    var fullScreen = false

    /** The cats' reflexes to a finger; the room's pointer feeds it ([pointerDown]). */
    val petting =
        Petting(
            house,
            object : TouchSink {
                override fun burst(kind: Burst, x: Float, y: Float) = particles.spawn(kind, x, y)

                override fun buzz(beat: Beat) {
                    touchSink?.buzz(beat)
                }

                override fun purr(cat: Int, level: Float) {
                    touchSink?.purr(cat, level)
                }

                override fun stroke(cat: Int, outcome: TouchModel.Outcome) {
                    touchSink?.stroke(cat, outcome)
                }
            },
        )

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
        if (world?.ownerAct("feed:$cat") != true) house.serveMeal(cat)
        bowlFullFor = BOWL_FULL_S
        scene.foodBowlFull = true
    }

    /** The owner tried to feed cat [cat] and it was full: it goes to its bowl and washes. */
    fun visitBowl(cat: Int) {
        if (world?.ownerAct("bowl:$cat") != true) house.visitBowl(cat)
    }

    /** The owner slow blinks at cat [cat]; true if the cat blinked back. */
    fun slowBlink(cat: Int): Boolean =
        if (world?.ownerAct("blink:$cat") == true) house.blinkBack(cat) else house.slowBlink(cat)

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
     * Draw the room in [next] from now on (the performance guard stepped the depth down, or the
     * phone's motion setting changed): the cats and the camera carry on. The new room's rest
     * picture is made off the main thread first, so the swap is not a hitch.
     */
    suspend fun useStyle(next: RoomStyle) {
        if (next == style) return
        val made =
            withContext(Dispatchers.Default) {
                RoomScene(kit.art, next).also { it.depth?.prepare() }
            }
        made.foodBowlFull = scene.foodBowlFull
        made.depth?.asyncWarm = asyncWarm
        scene = made
        style = next
        canvas.releaseSheets()
    }

    /** Make the 2.5D room's rest picture (a background thread, before the room is first shown). */
    fun prepareDepth() {
        scene.depth?.prepare()
    }

    /**
     * Make every camera step's pictures of the 2.5D room (rest first). Run on a background thread
     * when the room comes on screen; [cancelled] is asked between steps.
     */
    fun warmDepth(cancelled: () -> Boolean = { false }) {
        scene.depth?.warm(cancelled)
    }

    /**
     * Let go of the cached camera steps and their bitmaps (the room left the screen or the app
     * stopped); [warmDepth] makes them again when it returns.
     */
    fun trimDepth() {
        scene.depth?.release()
        canvas.releaseSheets()
    }

    /**
     * One view of this stage (the inline room, the full-screen room): whether it is [animating]
     * now. Two views may share a stage, and one going quiet must not take the other's pictures.
     */
    class View {
        var animating = false
    }

    private val views = ArrayList<View>()

    /** A view of this stage appeared. */
    fun attach(view: View) {
        views.add(view)
    }

    /** A view of this stage is gone: let go of the pictures if no other view is running. */
    fun detach(view: View) {
        views.remove(view)
        trimWhenIdle()
    }

    /** [trimDepth] unless a view of this stage is animating. */
    fun trimWhenIdle() {
        for (i in views.indices) if (views[i].animating) return
        trimDepth()
    }

    /** The milliseconds the last frame's step and drawing took on this thread. */
    var workMs = 0f
        private set

    private var stepNanos = 0L
    private var drawNanos = 0L

    /**
     * Put the camera on world x [x] at once (screenshots and tests; the director follows the cats).
     */
    fun lookAt(x: Float) = director.camera.cutTo(x)

    /** A finger landed at device pixel ([px], [py]) of the last drawing; what it landed on. */
    fun pointerDown(px: Float, py: Float): Int = petting.down(roomX(px), roomY(py))

    /** The finger moved to device pixel ([px], [py]). */
    fun pointerMove(px: Float, py: Float) = petting.move(roomX(px), roomY(py))

    /** The finger lifted, or the touch was taken away. */
    fun pointerUp() = petting.up()

    private fun roomX(px: Float): Float = (px - originX) / scale + RoomLayout.HOUSE_X0

    private fun roomY(py: Float): Float = (py - originY) / scale

    /**
     * The process-wide cat world this stage shows, when its [house] is the world's (see
     * [CatWorldHost]): the world steps the bodies itself, once per frame time, however many views
     * report the frame, so the stage leaves them alone. Null for a room of its own.
     */
    var world: CatWorldHost? = null

    /** The house's toys, drawn with the cats (the world's); null draws none. */
    var toys: ToyField? = null
    private val toyPainter = ToyPainter(kit)

    /** Advance the cats, the camera and the room's clock by [dt] seconds. */
    fun step(dt: Float) {
        val started = System.nanoTime()
        if (world == null) house.step(dt)
        petting.step(dt)
        particles.step(dt)
        followCats(dt)
        scene.depth?.let {
            it.follow(if (drawn) screenCentre else director.camera.cx)
            it.step(dt)
        }
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
        stepNanos = System.nanoTime() - started
        workMs = (stepNanos + drawNanos) / NANOS_PER_MS
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
        val s =
            if (fullScreen) fullScale(target.size.width, target.size.height)
            else pixelScale(target.size.width)
        scale = s
        val view = (target.size.width / s).coerceAtMost(RoomLayout.HOUSE_WIDTH.toFloat())
        val left = director.camera.left(view) - RoomLayout.HOUSE_X0
        originX = -(left * s).roundToInt()
        originY =
            if (fullScreen) ((target.size.height - RoomLayout.HEIGHT * s) / 2f).roundToInt()
            else (target.size.height - RoomLayout.HEIGHT * s).roundToInt()
        val topExtra = if (fullScreen) 0 else ceil(originY.toFloat() / s).toInt().coerceAtLeast(0)
        screenCentre = director.camera.left(view) + view / 2f
        drawn = true
        val started = System.nanoTime()
        canvas.begin(target, s, originX, originY)
        toyPainter.begin(toys, target, s, originX, originY)
        scene.paintBackdrop(canvas, hour, clock, topExtra)
        scene.paintScene(canvas, this)
        toyPainter.rest()
        particles.paint(canvas)
        scene.paintOverlay(canvas, hour, topExtra)
        drawNanos = System.nanoTime() - started
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
        toyPainter.before(pose.z, shift)
        val houseX = RoomLayout.HOUSE_X0.toFloat()
        val footX = (originX + (pose.x - houseX + shift) * scale).roundToInt().toFloat()
        val footY = (originY + (pose.y + pose.bob) * scale).roundToInt().toFloat()
        if (style.softShadows) {
            val halfW =
                (pose.shadowHalfWidth * pose.shadowScale * style.level.shadowSize).roundToInt()
            ContactShadow.paint(
                canvas,
                (pose.shadowX - houseX + shift).roundToInt(),
                pose.shadowY.roundToInt(),
                halfW,
                ContactShadow.halfHeightFor(halfW),
                pose.shadowAlpha / Tuning.Shadow.ALPHA * style.level.shadowStrength,
            )
        } else {
            val shadowX = originX + (pose.shadowX - houseX + shift) * scale
            scope.drawShadowAt(pose, scale, shadowX, originY + pose.shadowY * scale)
        }
        if (style.rimLight) {
            RimLight.of(hour, pose.x, rim)
            if (rim.side != 0) {
                val tint =
                    Color(rim.colour or OPAQUE)
                        .copy(alpha = (rim.strength * style.level.rimStrength).coerceAtMost(1f))
                val x = footX + rim.side * scale
                scope.drawSpriteAt(pose, sprites[k]!!, scale, x, footY, tint, secondaryMotion)
            }
        }
        scope.drawSpriteAt(pose, sprites[k]!!, scale, footX, footY, motion = secondaryMotion)
        toyPainter.held(house.bodies[k], shift)
    }

    override fun draw(i: Int) {
        val k = visible[i]
        val pose = house.bodies[k].pose
        toyPainter.before(pose.z, 0)
        val houseX = RoomLayout.HOUSE_X0.toFloat()
        val footX = (originX + (pose.x - houseX) * scale).roundToInt().toFloat()
        val footY = (originY + (pose.y + pose.bob) * scale).roundToInt().toFloat()
        val shadowX = originX + (pose.shadowX - houseX) * scale
        val shadowY = originY + pose.shadowY * scale
        scope.drawShadowAt(pose, scale, shadowX, shadowY)
        scope.drawSpriteAt(pose, sprites[k]!!, scale, footX, footY, motion = secondaryMotion)
        toyPainter.held(house.bodies[k], 0)
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000f
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

/**
 * Device pixels per room pixel for the full-screen room: a whole number that shows at least
 * [FULL_VIEW_MIN] room pixels of the house across and the whole height of the house up and down. A
 * wide window (landscape) is limited by its height, so it shows more of the house.
 */
internal fun fullScale(width: Float, height: Float): Int =
    min(floor(width / FULL_VIEW_MIN), floor(height / RoomLayout.HEIGHT)).toInt().coerceAtLeast(1)

/** The narrowest slice of the house the full-screen room shows, in room pixels. */
private const val FULL_VIEW_MIN = 120f
