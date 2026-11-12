package com.lifemax.rpg.feature.pet.presentation

import com.lifemax.rpg.feature.pet.art.CatLightKey
import com.lifemax.rpg.feature.pet.art.CoatFixtures
import com.lifemax.rpg.feature.pet.art.CoatLook
import com.lifemax.rpg.feature.pet.art.FakeCoatBridge
import com.lifemax.rpg.feature.pet.art.LitRecolour
import com.lifemax.rpg.feature.pet.art.PageCoatSource
import com.lifemax.rpg.feature.pet.art.PetArt
import com.lifemax.rpg.feature.pet.art.PetArtResources
import com.lifemax.rpg.feature.pet.art.RigCompose
import com.lifemax.rpg.feature.pet.art.RigFrames
import com.lifemax.rpg.feature.pet.art.RigGrid
import com.lifemax.rpg.feature.pet.art.RigLayer
import com.lifemax.rpg.feature.pet.art.RigLibrary
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.art.RigSet
import com.lifemax.rpg.feature.pet.art.SheetKind
import com.lifemax.rpg.feature.pet.body.CatPose
import com.lifemax.rpg.feature.pet.body.Clips
import kotlin.math.roundToInt

/**
 * The sprite steadiness probe (docs/specs/PET_SPRITE_STEADINESS.md): a cat's poses are drawn, frame
 * by frame, the way the room draws them, on a software canvas ([SpriteRaster]), and each frame is
 * compared with the one before after taking away the sprite's own whole-pixel move.
 *
 * Two renderers: [Renderer.BEFORE] is the drawing as it was up to Native API 94 (the canvas turned
 * and scaled the bitmap by the springs' raw values, the breath a scale of the whole cat, the rig's
 * parts rounded to the nearest grid point every frame); [Renderer.STEADY] is [SpritePlan] as the
 * app draws now.
 */
internal object SteadinessProbe {
    enum class Renderer {
        BEFORE,
        STEADY,
    }

    /** A coat to draw: its label, key, kitten stage (-1 grown) and proportions. */
    class Look(val label: String, val coat: String, val stage: Int, val morph: RigMorph)

    val library: RigLibrary by lazy { PetArtResources.loadRig() }

    /** The shipped coats, and two genetic coats answered from the page's recorded replies. */
    val art: PetArt by lazy {
        val bridge = FakeCoatBridge()
        val source = PageCoatSource { 0L }.also { it.bind(bridge.requester) }
        val art = PetArtResources.load(source)
        for (label in DNA) {
            val coat = CoatFixtures.coats.getValue(label)
            for ((name, kind) in CoatFixtures.kinds) {
                art.sheet(coat, kind)
                source.onReply(CoatFixtures.reply(label, name, bridge.sent.last().id))
            }
        }
        art
    }

    /** The rig of [look] (with depth rows where its coat has them). */
    fun rigOf(look: Look, id: Int): RigSet {
        fun layer(depth: Boolean): RigLayer? {
            val kind = SheetKind.of(look.stage, depth)
            return RigLayer(
                library.kind(kind.key) ?: return null,
                art.sheet(look.coat, kind) ?: return null,
                art.palette(look.coat) ?: return null,
            )
        }
        val anims =
            Array(Clips.all.size) {
                art.growth.resolve(look.stage, Clips.all[it].name, art.anims)!!
            }
        return RigSet(
            id,
            look.morph,
            look.stage >= 0,
            art.anims,
            anims,
            layer(false)!!,
            layer(true),
        )
    }

    /** The paws' row of [clip] at [look]'s stage. */
    fun feet(look: Look, clip: Int): Int =
        art.growth.resolve(look.stage, Clips.all[clip].name, art.anims)!!.bottom

    /** What one step between two frames changed, by kind of cause. */
    enum class Cause {
        /** The art's frame, the clip or the way the cat faces changed: the art meant it. */
        ART,

        /** The rig's grid point changed (an ear, the tail or the whiskers moved a step). */
        RIG,

        /** The breath moved a step (steady renderer only; before, the breath was continuous). */
        BREATH,

        /** The turn or squash and stretch moved a step (steady renderer only). */
        BODY,

        /** The cat's held light key changed (docs/specs/CAT_LIGHTING_PLAN.md section 7). */
        LIGHT,

        /** Nothing the art, the rig or the body meant changed. */
        NONE,
    }

    /**
     * Draws one cat's poses with one renderer at [s] device pixels per art pixel and keeps the
     * measurements.
     */
    class Run(val look: Look, val renderer: Renderer, val s: Int, rigId: Int) {
        val rig = rigOf(look, rigId)
        private val frames = RigFrames()
        private val grid = RigGrid()
        private val steady = SpriteSteady()
        private val plan = SpritePlan()
        private val bandOut = IntArray(SpritePlan.MAX_BANDS * SpritePlan.BAND)
        private var last: SpriteDraw? = null
        /** The latest frame as drawn (device pixels, in room coordinates). */
        var lastRaster = Raster.EMPTY
            private set

        private val history = ArrayDeque<Pair<Raster, SpriteDraw>>()
        private var lastKeys = LongArray(KEYS) { -1 }

        /** Device pixels changed per step (after the whole-pixel move is taken away). */
        val changed = ArrayList<Int>()

        /** Of [changed], the pixels opaque in both frames whose colour changed: the coat. */
        val coat = ArrayList<Int>()

        /** The cause of each step. */
        val causes = ArrayList<Cause>()

        /** Pixels per step that went back to the colour they had two frames before (a, b, a). */
        val flicker = ArrayList<Int>()

        /** Device pixel moves of the paws up or down that were undone on the next frame. */
        var bounces = 0

        private var lastDy = 0

        /** One line per changed step when not null. */
        var trace: StringBuilder? = null

        /**
         * The light key the steady renderer draws each frame in, from the pose and the run's time
         * in seconds; null draws every frame neutral, as the app does with lighting off.
         */
        var lightOf: ((CatPose, Double) -> Int)? = null

        /** The light keys drawn, frame by frame. */
        val lights = ArrayList<Int>()

        private var drawnFrames = 0

        /** Draw [pose] (paws at the room's rounding of its position) and measure the change. */
        fun frame(pose: CatPose) {
            if (!hasArt(pose.clip)) return
            val footX = (pose.x * s).roundToInt()
            val footY = ((pose.y + pose.bob) * s).roundToInt()
            val feet = feet(look, pose.clip)
            val keys = LongArray(KEYS)
            val draw =
                if (renderer == Renderer.BEFORE) before(pose, footX, footY, feet, keys)
                else steady(pose, footX, footY, feet, keys)
            val prev = last
            val raster =
                if (prev != null && draw.sameUpToTranslation(prev)) moved(lastRaster, prev, draw)
                else SpriteRaster.draw(draw)
            if (prev != null) measure(raster, draw, prev, keys, pose)
            history.addFirst(raster to draw)
            while (history.size > HISTORY) history.removeLast()
            last = draw
            lastRaster = raster
            lastKeys = keys
        }

        private fun measure(
            now: Raster,
            draw: SpriteDraw,
            prevDraw: SpriteDraw,
            keys: LongArray,
            pose: CatPose,
        ) {
            val dx = draw.footX - prevDraw.footX
            val dy = draw.footY - prevDraw.footY
            if (dy != 0) {
                if (dy == -lastDy) bounces++
                lastDy = dy
            }
            val cause =
                when {
                    keys[K_ART] != lastKeys[K_ART] -> Cause.ART
                    keys[K_RIG] != lastKeys[K_RIG] -> Cause.RIG
                    keys[K_BREATH] != lastKeys[K_BREATH] -> Cause.BREATH
                    keys[K_BODY] != lastKeys[K_BODY] -> Cause.BODY
                    keys[K_LIGHT] != lastKeys[K_LIGHT] -> Cause.LIGHT
                    else -> Cause.NONE
                }
            causes.add(cause)
            if (draw.sameUpToTranslation(prevDraw)) {
                changed.add(0)
                coat.add(0)
                flicker.add(0)
                return
            }
            val prev = history[0].first
            val older = history.getOrNull(1)
            var inner = 0
            var all = 0
            var flick = 0
            val x0 = minOf(now.x0, prev.x0 + dx)
            val y0 = minOf(now.y0, prev.y0 + dy)
            val x1 = maxOf(now.x0 + now.w, prev.x0 + dx + prev.w)
            val y1 = maxOf(now.y0 + now.h, prev.y0 + dy + prev.h)
            for (y in y0 until y1) for (x in x0 until x1) {
                val a = now[x, y]
                val b = prev[x - dx, y - dy]
                if (a == b) continue
                all++
                if (a ushr 24 != 0 && b ushr 24 != 0) inner++
                if (older != null) {
                    val ox = draw.footX - older.second.footX
                    val oy = draw.footY - older.second.footY
                    if (older.first[x - ox, y - oy] == a) flick++
                }
            }
            changed.add(all)
            coat.add(inner)
            flicker.add(flick)
            trace?.append(
                "${changed.size} ${pose.clipName}#${pose.frame} foot ${draw.footX},${draw.footY} " +
                    "$cause changed $all coat $inner " +
                    "flicker $flick rot ${pose.rotation} sx ${pose.scaleX} sy ${pose.scaleY} " +
                    "breath ${pose.breath} rig ${keys[K_RIG]}\n"
            )
        }

        private val sheetFrames = HashMap<Long, IntArray>()

        /** True if the cat has art for [clip]: the rig's, or the sheet's own (the lab rows). */
        fun hasArt(clip: Int): Boolean = known[clip]

        private val known =
            BooleanArray(Clips.all.size) {
                rig.has(it) || art.frame(look.coat, look.stage, Clips.all[it].name, 0) != null
            }

        /** The art frame as the sheet has it (what the app draws when there is no rig picture). */
        private fun sheet(pose: CatPose): IntArray {
            if (rig.has(pose.clip)) return rig.source(pose.clip, pose.frame)
            val f = Math.floorMod(pose.frame, Clips.all[pose.clip].frames)
            return sheetFrames.getOrPut(pose.clip * FRAME_SPAN + f) {
                art.frame(look.coat, look.stage, Clips.all[pose.clip].name, f)!!.pixels
            }
        }

        private val litFrames = HashMap<Long, IntArray>()

        /** The art frame lit by [light], as the app's lit rows have it (no rig picture). */
        private fun litSheet(pose: CatPose, light: Int): IntArray {
            if (rig.has(pose.clip)) return rig.source(pose.clip, pose.frame, light)
            val clip = Clips.all[pose.clip]
            val f = Math.floorMod(pose.frame, clip.frames)
            return litFrames.getOrPut((light * 1024L + pose.clip) * FRAME_SPAN + f) {
                val anim = art.growth.resolve(look.stage, clip.name, art.anims)!!
                val sheet = art.sheet(look.coat, SheetKind.of(look.stage, anim.depth))!!
                val palette = art.palette(look.coat)!!
                LitRecolour.region(
                        sheet,
                        palette,
                        art.anims.frameRect(anim, f),
                        CatLightKey.light(light, CoatLook.of(palette)),
                    )
                    .pixels
            }
        }

        private fun moved(r: Raster, from: SpriteDraw, to: SpriteDraw) =
            Raster(r.x0 + to.footX - from.footX, r.y0 + to.footY - from.footY, r.w, r.h, r.px)

        private fun artKey(pose: CatPose) =
            (pose.clip * FRAME_SPAN + Math.floorMod(pose.frame, Clips.all[pose.clip].frames)) * 2L +
                if (pose.flip) 1 else 0

        private fun before(
            pose: CatPose,
            footX: Int,
            footY: Int,
            feet: Int,
            keys: LongArray,
        ): SpriteDraw {
            RigMotion.read(pose, rig.kitten, motion = true, grid = grid)
            keys[K_ART] = artKey(pose)
            keys[K_RIG] = grid.key()
            val flip = if (pose.flip) -1.0 else 1.0
            val slot = frames.slotFor(rig, pose.clip, pose.frame, grid)
            val turn = Math.toDegrees(pose.rotation.toDouble())
            val sx = pose.scaleX * flip
            val sy = (pose.scaleY * pose.breath).toDouble()
            return if (slot >= 0) {
                val top = (1 - feet - RigCompose.MARGIN_TOP) * s
                SpriteDraw(
                    frames.pixels(slot).copyOf(),
                    frames.width,
                    frames.height,
                    -(FRAME_W / 2 + RigCompose.MARGIN_LEFT) * s,
                    listOf(Band(0, frames.height, top, frames.height * s)),
                    s,
                    footX,
                    footY,
                    turn,
                    sx,
                    sy,
                )
            } else {
                SpriteDraw(
                    sheet(pose),
                    FRAME_W,
                    FRAME_H,
                    -(FRAME_W / 2) * s,
                    listOf(Band(0, FRAME_H, (1 - feet) * s, FRAME_H * s)),
                    s,
                    footX,
                    footY,
                    turn,
                    sx,
                    sy,
                )
            }
        }

        private fun steady(
            pose: CatPose,
            footX: Int,
            footY: Int,
            feet: Int,
            keys: LongArray,
        ): SpriteDraw {
            val light = lightOf?.invoke(pose, drawnFrames++ / HZ.toDouble()) ?: 0
            lights.add(light)
            plan.plan(pose, rig, feet, FRAME_W, FRAME_H, steady, frames, s, motion = true, light)
            keys[K_LIGHT] = light.toLong()
            keys[K_ART] = artKey(pose)
            keys[K_RIG] = steady.grid.key()
            keys[K_BREATH] = plan.rise.toLong()
            keys[K_BODY] =
                (plan.turnDeg * 1000).roundToInt() * 1_000_000L +
                    (plan.scaleX * 1000).roundToInt() * 1000L +
                    (plan.scaleY * 1000).roundToInt()
            val pixels =
                if (plan.slot >= 0) frames.pixels(plan.slot).copyOf()
                else if (light != 0) litSheet(pose, light) else sheet(pose)
            val n = plan.bands(s, bandOut)
            val bands =
                List(n) {
                    val i = it * SpritePlan.BAND
                    Band(bandOut[i], bandOut[i + 1], bandOut[i + 2], bandOut[i + 3])
                }
            return SpriteDraw(
                pixels,
                plan.width,
                plan.height,
                plan.left * s,
                bands,
                s,
                footX,
                footY,
                plan.turnDeg.toDouble(),
                plan.scaleX.toDouble(),
                plan.scaleY.toDouble(),
            )
        }
    }

    /** The per-step numbers of one run, summarised. */
    class Summary(run: Run) {
        val steps = run.changed.size
        val changedSteps = run.changed.count { it > 0 }
        private val sorted = run.changed.sorted()
        val min = sorted.firstOrNull() ?: 0
        val median = if (sorted.isEmpty()) 0 else sorted[sorted.size / 2]
        val max = sorted.lastOrNull() ?: 0

        /** Coat pixels changed on steps where the art's frame did not change. */
        val coatOffArt =
            run.changed.indices
                .filter { run.causes[it] != Cause.ART }
                .sumOf { run.coat[it].toLong() }

        /** Steps that changed pixels with no cause at all. */
        val unexplained =
            run.changed.indices.count { run.causes[it] == Cause.NONE && run.changed[it] > 0 }

        /** Pixels changed on steps with no cause at all. */
        val unexplainedPixels =
            run.changed.indices
                .filter { run.causes[it] == Cause.NONE }
                .sumOf { run.changed[it].toLong() }
        val flicker = run.flicker.sumOf { it.toLong() }
        val bounces = run.bounces

        /** How many steps each cause had that changed pixels. */
        fun stepsOf(cause: Cause, runOf: Run) =
            runOf.changed.indices.count { runOf.causes[it] == cause && runOf.changed[it] > 0 }
    }

    const val HZ = 120
    const val FRAME_W = RigFrames.FRAME_WIDTH
    const val FRAME_H = RigFrames.FRAME_HEIGHT
    private const val HISTORY = 3
    private const val FRAME_SPAN = 64L
    private const val KEYS = 5
    private const val K_ART = 0
    private const val K_RIG = 1
    private const val K_BREATH = 2
    private const val K_BODY = 3
    private const val K_LIGHT = 4
    private val DNA = listOf("calico", "silver")
}
