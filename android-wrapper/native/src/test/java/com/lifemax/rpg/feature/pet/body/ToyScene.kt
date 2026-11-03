package com.lifemax.rpg.feature.pet.body

import com.lifemax.rpg.feature.pet.art.Growth
import com.lifemax.rpg.feature.pet.art.LabTable
import java.util.Locale

/**
 * Test support: one cat and its toys in a room, stepped at a chosen host frame rate, with the mouth
 * anchor read back the way a renderer would compute it. The scenes of the golden traces, the
 * physics checks and the contact sheets are all made here.
 */
class ToyScene(
    kind: ToyKind = ToyKind.BALL_A,
    toyX: Float = 110f,
    catX: Float = 60f,
    val hz: Int = 60,
    seed: Int = 3,
    toyOn: SurfaceId = SurfaceId.FLOOR,
    catOn: SurfaceId = SurfaceId.FLOOR,
    toyZ: Float = World.ART_Z,
    catZ: Float = World.ART_Z,
) {
    val field = ToyField()
    val toy: ToyBody = field.add(kind, toyX, toyOn, toyZ)
    val body = CatBody(seed = seed, startX = catX, startSurface = catOn, startZ = catZ)
    val mouth = FloatArray(2)
    var frame = 0
        private set

    init {
        body.attachToys(field, anchors)
    }

    /** The time since the start, seconds. */
    val time: Float
        get() = frame.toFloat() / hz

    private var planId = 0

    /** Give the cat a new plan. */
    fun plan(vararg steps: PlanStep) {
        planId++
        body.replacePlan(planId, steps.toList())
    }

    /** One host frame. */
    fun step() {
        body.step(1f / hz)
        frame++
    }

    /** The cat's mouth now, in room pixels, into [mouth]. */
    fun locateMouth() {
        val p = body.pose
        val clip = Clips.all[p.clip]
        MouthAnchors.locate(
            anchors.u(p.clip, p.frame),
            anchors.v(p.clip, p.frame),
            clip.bottom,
            p.flip,
            p.scaleX,
            p.scaleY,
            p.rotation,
            p.depthScale,
            p.x,
            p.y + p.bob,
            mouth,
        )
    }

    /** Run for [seconds], calling [each] after every frame. */
    fun run(seconds: Float, each: (ToyScene) -> Unit = {}) {
        repeat(Math.round(seconds * hz)) {
            step()
            each(this)
        }
    }

    /** One text row: time, cat, toy, mouth anchor and the distance between toy pivot and mouth. */
    fun row(): String {
        locateMouth()
        val p = body.pose
        val t = toy
        return String.format(
            Locale.ROOT,
            "%6.3f cat x=%7.2f %-7s f%d%s | toy %-5s x=%7.2f y=%6.2f v=%7.1f,%7.1f a=%+.2f sq=%.2f,%.2f%s | mouth %6.2f,%6.2f",
            time,
            p.x,
            p.clipName,
            p.frame,
            if (p.flip) " flip" else "",
            t.state,
            t.x,
            t.y,
            t.vx,
            t.vy,
            t.drawAngle,
            t.drawScaleX,
            t.drawScaleY,
            if (t.airborne) " air" else "",
            mouth[0],
            mouth[1],
        )
    }

    /** The golden-trace text of [seconds] of play: a row every [every] frames and the events. */
    fun trace(seconds: Float, every: Int = 6): String {
        val out = StringBuilder()
        out.append(row()).append('\n')
        run(seconds) {
            val p = body.pose
            for (i in 0 until p.events.count) out.append(TraceSupport.eventRow(time, p, i))
                .append('\n')
            if (frame % every == 0) out.append(row()).append('\n')
        }
        return out.toString()
    }

    companion object {
        private val lab: LabTable by lazy {
            LabTable.parse(
                ToyScene::class
                    .java
                    .getResourceAsStream("/pet/lab.json")!!
                    .bufferedReader()
                    .readText()
            )
        }

        /** The adult's mouth anchors from the real lab table. */
        val anchors: MouthAnchors by lazy { MouthAnchors.of(lab, Growth.ADULT) }

        /** The lab table, for tests of the frame constants. */
        fun labTable(): LabTable = lab
    }
}
