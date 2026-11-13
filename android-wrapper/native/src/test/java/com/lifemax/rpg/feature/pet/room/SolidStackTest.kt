package com.lifemax.rpg.feature.pet.room

import com.lifemax.rpg.feature.pet.art.ArgbImage
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.body.HouseCat
import com.lifemax.rpg.feature.pet.body.HouseLife
import com.lifemax.rpg.feature.pet.body.PlanStep
import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.body.World
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The continuous 3D prototype (docs/specs/CAT_3D_ENGINE_PLAN.md): every solid piece as a stack of
 * slices ([SolidStack]) moved by the camera's yaw itself. What it must keep from the cached steps
 * (the painted sprite at rest, the piece's own palette, the raster's silhouette, the cats' order)
 * and what it must change (a side grows a device pixel at a time, not an art pixel at a time).
 */
class SolidStackTest {
    private val art = PetArtResources.loadRoom()
    private val solids = art.layers!!.pieces.filter { it.volume != null }
    private val level = DepthLevel.MEDIUM

    /** A canvas at [scale] device pixels per house pixel that draws only stacks (opaque pixels). */
    private class FineCanvas(val scale: Int, val ox: Int, val oy: Int, val w: Int, val h: Int) :
        RoomCanvas {
        val pixels = IntArray(w * h)

        override fun stack(
            image: ArgbImage,
            slot: Int,
            version: Int,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Float,
            dy: Float,
        ) {
            // as the screen's canvas places it: at the nearest device pixel
            val x0 = Math.round(ox + dx * scale)
            val y0 = Math.round(oy + dy * scale)
            for (j in 0 until sh * scale) for (i in 0 until sw * scale) {
                val p = image[sx + i / scale, sy + j / scale]
                val x = x0 + i
                val y = y0 + j
                if (p ushr 24 != 0 && x in 0 until w && y in 0 until h) pixels[y * w + x] = p
            }
        }

        override fun clip(x: Int, y: Int, w: Int, h: Int) = Unit

        override fun unclip() = Unit

        override fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) = Unit

        override fun image(
            image: RoomImage,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Int,
            dy: Int,
            dw: Int,
            dh: Int,
        ) = Unit

        override fun quad(
            x0: Float,
            y0: Float,
            x1: Float,
            y1: Float,
            x2: Float,
            y2: Float,
            x3: Float,
            y3: Float,
            argb: Int,
        ) = Unit

        override fun multiply(rgb: Int, x: Int, y: Int, w: Int, h: Int) = Unit

        override fun overlay(mask: ArgbImage, version: Int, x: Int, y: Int) = Unit

        override fun sheet(
            image: ArgbImage,
            slot: Int,
            version: Int,
            sx: Int,
            sy: Int,
            sw: Int,
            sh: Int,
            dx: Int,
            dy: Int,
            alpha: Float,
        ) = Unit
    }

    /** Margin around a piece, in house pixels: room for its sides at any view. */
    private val margin = 6

    /** The whole stack of [piece] seen with ([vx], [vy]) at [scale], back to front. */
    private fun composite(piece: LayerPiece, vx: Float, vy: Float, scale: Int = 1): FineCanvas {
        val model = piece.volume!!.model
        val stack = SolidStack(model, piece.volume!!.wall)
        val c =
            FineCanvas(
                scale,
                margin * scale,
                margin * scale,
                (model.width + 2 * margin) * scale,
                (model.height + 2 * margin) * scale,
            )
        for (k in stack.bands - 1 downTo 0) stack.paintBand(c, 0, k, 0f, 0f, vx, vy)
        return c
    }

    @Test
    fun atRestEveryStackIsItsPaintedSpriteExactly() {
        for (piece in solids) {
            val model = piece.volume!!.model
            for (vx in floatArrayOf(0f, -0f)) {
                val c = composite(piece, vx, 0f)
                for (y in 0 until c.h) for (x in 0 until c.w) {
                    val mx = x - margin
                    val my = y - margin
                    val want =
                        if (mx in 0 until model.width && my in 0 until model.height) {
                            model.colours[my * model.width + mx].let {
                                if (it ushr 24 == 0) 0 else it
                            }
                        } else 0
                    assertEquals("${piece.name} at ($mx, $my)", want, c.pixels[y * c.w + x])
                }
            }
        }
    }

    @Test
    fun everyShadeIsFromThePiecesOwnPalette() {
        for (piece in solids) {
            val model = piece.volume!!.model
            val palette = model.palette.colours.toSet()
            val stack = SolidStack(model, piece.volume!!.wall)
            for (p in stack.image.pixels) {
                if (p ushr 24 != 0) assertTrue(piece.name, (p or OPAQUE) in palette)
            }
        }
    }

    /**
     * Where the cached steps are exact (a camera step's view), the stack has the raster's shape:
     * every pixel one covers and the other does not is within one art pixel of the other's
     * silhouette (the slices sit at the start of their half row and round to the nearest pixel, the
     * raster casts a ray through each pixel's middle), and the two mostly agree pixel for pixel.
     * The outline at the far edge of a revealed face is where the raster draws it, to within a
     * pixel too.
     */
    @Test
    fun atEachCameraStepTheStackHasTheRastersShape() {
        val n = VolumeSheets.stepsFor(solids, level)
        var worst = 1.0
        var worstName = ""
        var farOff = 0
        var inkRaster = 0
        var inkStack = 0
        for (piece in solids) {
            val model = piece.volume!!.model
            val ink = model.palette.colours[model.palette.ink]
            val frame = VolumeFrame(VolumeRaster.size(model, 1f, 1f))
            for (i in 0..2 * n) {
                val sway = (i - n).toFloat() / n
                val vx = -sway * level.depthGain
                val vy = -level.pitchAt(sway)
                VolumeRaster.render(model, vx, vy, piece.volume!!.wall, frame)
                val c = composite(piece, vx, vy)
                fun raster(x: Int, y: Int): Int {
                    val fx = x - margin - frame.x0
                    val fy = y - margin - frame.y0
                    return if (fx in 0 until frame.width && fy in 0 until frame.height) {
                        frame.colour[fy * frame.width + fx]
                    } else 0
                }
                fun stacked(x: Int, y: Int): Int =
                    if (x in 0 until c.w && y in 0 until c.h) c.pixels[y * c.w + x] else 0
                fun near(x: Int, y: Int, of: (Int, Int) -> Int): Boolean {
                    for (dy in -1..1) for (dx in -1..1) if (of(x + dx, y + dy) != 0) return true
                    return false
                }
                var both = 0
                var either = 0
                for (y in 0 until c.h) for (x in 0 until c.w) {
                    val r = raster(x, y)
                    val s = stacked(x, y)
                    if (r != 0 && s != 0) both++
                    if (r != 0 || s != 0) either++
                    if (r != 0 && s == 0 && !near(x, y, ::stacked)) farOff++
                    if (s != 0 && r == 0 && !near(x, y, ::raster)) farOff++
                    if (model.palette.colours[model.palette.ink] == RampTuning.ART_INK) {
                        if (r == ink) inkRaster++
                        if (s == ink) inkStack++
                    }
                }
                val agree = both.toDouble() / either
                if (agree < worst) {
                    worst = agree
                    worstName = "${piece.name} step $i"
                }
            }
        }
        println(
            "STACKSHAPE worst silhouette agreement %.4f (%s); %d pixels more than one off; ink %d (raster) %d (stack)"
                .format(worst, worstName, farOff, inkRaster, inkStack)
        )
        assertEquals("no pixel more than one art pixel off the raster's shape", 0, farOff)
        assertTrue("$worstName agrees $worst", worst > 0.8)
    }

    /**
     * The owner's complaint: a side appears an art pixel (5 to 9 device pixels) at a time. Swept
     * finely across the live camera's range, the stacked sofa's left edge on the screen never moves
     * more than one device pixel between two neighbouring yaws, while the cached steps move it a
     * whole art pixel at once.
     */
    @Test
    fun theSofasSideGrowsADevicePixelAtATime() {
        val sofa = solids.first { it.name == "sofa" }
        val model = sofa.volume!!.model
        val scale = 6
        val row = (model.height / 2) * scale + margin * scale // the middle of the sofa, on screen
        fun leftEdge(c: FineCanvas): Int {
            for (x in 0 until c.w) if (c.pixels[row * c.w + x] != 0) return x
            return -1
        }
        var last = Int.MIN_VALUE
        var biggest = 0
        var travel = 0
        val first = leftEdge(composite(sofa, 0f, 0f, scale))
        var yaw = 0f
        while (yaw <= 1.0001f) {
            val c = composite(sofa, StackView.vx(yaw, level), StackView.vy(yaw, level), scale)
            val edge = leftEdge(c)
            if (last != Int.MIN_VALUE) biggest = maxOf(biggest, abs(edge - last))
            travel = maxOf(travel, abs(edge - first))
            last = edge
            yaw += 0.005f
        }
        // the cached steps at the same sweep: the nearest step's picture, scaled
        val n = VolumeSheets.stepsFor(solids, level)
        var stepJump = 0
        var prev = Int.MIN_VALUE
        val frame = VolumeFrame(VolumeRaster.size(model, 1f, 1f))
        for (i in n..2 * n) {
            val sway = (i - n).toFloat() / n
            VolumeRaster.render(model, -sway * level.depthGain, -level.pitchAt(sway), true, frame)
            val y = model.height / 2 - frame.y0
            var edge = 0
            for (x in 0 until frame.width) {
                if (frame.colour[y * frame.width + x] != 0) {
                    edge = (x + frame.x0) * scale
                    break
                }
            }
            if (prev != Int.MIN_VALUE) stepJump = maxOf(stepJump, abs(edge - prev))
            prev = edge
        }
        println(
            "STACKSMOOTH sofa left edge: stack travels $travel device px, biggest move $biggest; " +
                "cached steps jump $stepJump device px"
        )
        assertTrue("the side shows", travel >= 2 * scale)
        assertTrue("moves at most a device pixel at a time: $biggest", biggest <= 1)
        assertTrue("the steps jumped an art pixel: $stepJump", stepJump >= scale)
    }

    // ------------------------------------------------------------------ in the room

    private class BoxCats(
        private val x: IntArray,
        private val z: FloatArray,
        private val feet: IntArray,
        private val surface: Array<String?>,
    ) : CatLayer {
        override val count = x.size

        override fun depth(i: Int) = z[i]

        override fun feet(i: Int) = feet[i].toFloat()

        override fun surface(i: Int) = surface[i]

        override fun draw(i: Int) = Unit

        override fun draw(i: Int, shift: Int, style: RoomStyle, canvas: RoomCanvas) {
            canvas.fill(x[i] - 6 + shift, feet[i] - 20, 12, 20, CAT)
        }
    }

    private fun paint(mode: Solid3d, sway: Float, cats: CatLayer): SoftCanvas {
        val scene = RoomScene(art, RoomStyle.DEPTH25)
        scene.depth!!.solid3d = mode
        scene.depth!!.sway.restAt(sway)
        val canvas = SoftCanvas(art)
        scene.paintBackdrop(canvas, 12.0, 0.0, 0)
        scene.paintScene(canvas, cats)
        return canvas
    }

    @Test
    fun theRoomAtRestIsTheSameWithTheStacksOnOrOff() {
        val cats =
            BoxCats(
                intArrayOf(80, 120),
                floatArrayOf(World.ART_Z, 10f),
                intArrayOf(132, 120),
                arrayOf(null, null),
            )
        val off = paint(Solid3d.OFF, 0f, cats).pixels
        for (mode in listOf(Solid3d.SOFA, Solid3d.ALL)) {
            val on = paint(mode, 0f, cats).pixels
            var differ = 0
            for (i in off.indices) if (off[i] != on[i]) differ++
            assertEquals("$mode at rest", 0, differ)
        }
    }

    @Test
    fun aCatOnTheSofaIsStillDrawnAfterEverySofaBandAndTheSideShows() {
        val scene = RoomScene(art, RoomStyle.DEPTH25)
        val depth = scene.depth!!
        depth.solid3d = Solid3d.SOFA
        depth.sway.restAt(1f)
        val cats =
            BoxCats(intArrayOf(90), floatArrayOf(World.ART_Z), intArrayOf(110), arrayOf("couch"))
        val canvas = SoftCanvas(art)
        scene.paintBackdrop(canvas, 12.0, 0.0, 0)
        scene.paintScene(canvas, cats)
        val order = depth.order
        val sofa = depth.pieces.indexOfFirst { it.name == "sofa" }
        var catAt = -1
        var lastSofa = -1
        var sofaBands = 0
        for (k in 0 until order.size) {
            val code = order.order[k]
            if (code >= DepthOrder.CAT) catAt = k
            else if (code < VolumeStep.FURNITURE && depth.bandPiece(code) == sofa) {
                lastSofa = k
                sofaBands++
            }
        }
        assertEquals("one band per floor row of the sofa", depth.stackOf("sofa").bands, sofaBands)
        assertTrue("the cat after the sofa", catAt > lastSofa)
        // swayed, the stacked room differs from the room at rest around the sofa
        val rest = paint(Solid3d.SOFA, 0f, cats).pixels
        var changed = 0
        for (i in rest.indices) if (rest[i] != canvas.pixels[i]) changed++
        assertTrue("the sway shows the sofa's side: $changed", changed > 50)
    }

    // ------------------------------------------------------------------ cost

    private class Cats(val house: HouseLife) : CatLayer {
        override val count = 2

        override fun depth(i: Int) = house.bodies[i].pose.z

        override fun feet(i: Int) = house.bodies[i].pose.y

        override fun draw(i: Int) = Unit
    }

    private fun frameCost(mode: Solid3d, soft: Boolean): Pair<Double, Int> {
        val scene = RoomScene(art, RoomStyle.live(level))
        val depth = scene.depth!!
        depth.asyncWarm = true
        depth.warm()
        depth.solid3d = mode
        val counting = CountingCanvas()
        val canvas: RoomCanvas = if (soft) SoftCanvas(art, 6) else counting
        val house = HouseLife(listOf(HouseCat(3, false), HouseCat(8, false)), autopilot = false)
        val cats = Cats(house)
        val director = CameraDirector()
        val steps = ArrayList<PlanStep>()
        repeat(80) {
            steps.add(PlanStep.Walk(-140f))
            steps.add(PlanStep.Walk(330f))
        }
        house.bodies[0].placeAt(-140f, World.ART_Z, SurfaceId.FLOOR, 1)
        house.bodies[0].replacePlan(1, steps)
        house.bodies[1].placeAt(90f, World.ART_Z, SurfaceId.FLOOR, 1)
        val dt = 1f / 60f
        fun frame() {
            house.step(dt)
            director.update(house.bodies[0], house.bodies[1], dt)
            depth.follow(director.camera.cx)
            depth.step(dt)
            scene.paintBackdrop(canvas, 20.0, 12.0, 6)
            scene.paintScene(canvas, cats)
            scene.paintOverlay(canvas, 20.0, 6)
        }
        repeat(60) { frame() }
        counting.calls = 0
        val frames = if (soft) 60 else 600
        val started = System.nanoTime()
        repeat(frames) { frame() }
        val ms = (System.nanoTime() - started) / 1e6 / frames
        return ms to counting.calls / frames
    }

    @Test
    fun aStackedFrameCostsAboutWhatASteppedOneDoes() {
        val lines = StringBuilder("STACKCOST (MEDIUM, live camera, two cats)")
        for (mode in Solid3d.entries) {
            val (painter, calls) = frameCost(mode, soft = false)
            val (soft, _) = frameCost(mode, soft = true)
            lines.append(
                "\n  %s: painter %.4f ms, %d draw calls a frame, software canvas %.2f ms"
                    .format(mode.label, painter, calls, soft)
            )
            assertTrue("$mode painter $painter", painter < 2.0)
        }
        var bytes = 0L
        var made = 0L
        var blitted = 0L
        var sprites = 0L
        for (piece in solids) {
            val started = System.nanoTime()
            val s = SolidStack(piece.volume!!.model, piece.volume!!.wall)
            made += System.nanoTime() - started
            bytes += 4L * s.image.width * s.image.height
            for (j in 0 until s.slices) {
                val e = s.sliceEntry(0, j)
                blitted += s.w[e].toLong() * s.h[e]
            }
            sprites += piece.width.toLong() * piece.height
        }
        lines.append(
            "\n  all %d stacks: %.2f MB of bitmaps, made in %.1f ms; a frame blits %d art pixels of slices (%.1fx the sprites' %d)"
                .format(
                    solids.size,
                    bytes / 1e6,
                    made / 1e6,
                    blitted,
                    blitted.toDouble() / sprites,
                    sprites,
                )
        )
        println(lines)
    }

    private companion object {
        const val OPAQUE = 0xFF000000.toInt()
        const val CAT = 0xFFFF00FF.toInt()
    }
}
