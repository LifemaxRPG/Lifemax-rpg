package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.Gait
import com.lifemax.rpg.feature.habits.domain.K
import com.lifemax.rpg.feature.habits.domain.Looks
import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import com.lifemax.rpg.feature.habits.domain.Silhouettes
import com.lifemax.rpg.feature.habits.domain.Spline
import com.lifemax.rpg.feature.habits.domain.Stroll
import com.lifemax.rpg.feature.habits.domain.Visit
import com.lifemax.rpg.feature.habits.presentation.scene.PeopleSprites
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** How people walk and jog: the cycle, its smoothness, its feet, and the lobby visitor's play. */
class GaitTest {
    private val walk = PeopleActivities.of(Activity.WALKING)

    @Test
    fun aWalkIsTwoStepsEightKeyPosesSixteenFramesAtOneHundredTenStepsAMinute() {
        assertEquals(16, walk.frames)
        assertEquals(9, walk.keys.size) // eight key poses and the first again
        assertTrue(walk.smooth)
        assertTrue("cycle ${Gait.CYCLE}", Gait.CYCLE in 1.0f..1.1f)
        val stepsAMinute = 120f / Gait.CYCLE
        assertTrue("steps $stepsAMinute", stepsAMinute in 105f..120f)
        assertEquals(Gait.STRIDE / Gait.CYCLE, Gait.SPEED, 1e-4f)
        assertEquals(2f * Gait.STEP, Gait.STRIDE, 1e-4f)
        // the loop plays at the rate that makes its frames last one cycle
        assertEquals(Gait.CYCLE, walk.seconds, 1e-3f)
        // the turn has four frames on the way and the standing one
        assertEquals(Gait.TURN_FRAMES, walk.extra.size)
        assertEquals(walk.frames + walk.extra.size, walk.totalFrames)
        val jog = PeopleActivities.of(Activity.JOGGING)
        assertEquals(16, jog.frames)
        assertTrue(jog.smooth)
        assertTrue(jog.seconds in .6f..0.85f)
    }

    @Test
    fun theWalkIsAClosedLoopAndTheSecondStepIsTheFirstWithTheOtherLeg() {
        for (spec in listOf(walk, PeopleActivities.of(Activity.JOGGING))) {
            assertTrue(Spline.closed(spec.keys, .001f))
            assertTrue(FigBuilder.same(Spline.sample(spec.keys, 1f), spec.poseAt(0), .001f))
            // frame 0 is frame N: the loop's own end
            assertTrue(FigBuilder.same(spec.poseAtPhase(1f - 1e-5f), spec.poseAt(0), .05f))
            val half = spec.frames / 2
            for (k in 0 until half) {
                val a = spec.poseAt(k)
                val b = spec.poseAt(k + half)
                assertEquals("${spec.activity} $k legs", a[K.LN], b[K.LF], .8f)
                assertEquals("${spec.activity} $k knees", a[K.KN], b[K.KF], .8f)
                assertEquals("${spec.activity} $k arms", a[K.AN], b[K.AF], .8f)
                assertEquals("${spec.activity} $k legs", a[K.LF], b[K.LN], .8f)
            }
        }
    }

    @Test
    fun noJumpsBetweenFramesOrBetweenTheSamplesInBetween() {
        for (spec in listOf(walk, PeopleActivities.of(Activity.JOGGING))) {
            // from one baked frame to the next: no joint turns more than a bound
            for (k in 0 until spec.frames) {
                val a = spec.poseAt(k)
                val b = spec.poseAt((k + 1) % spec.frames)
                for (i in 0 until K.SIZE) {
                    val bound =
                        when (i) {
                            K.KN,
                            K.KF -> 28f
                            K.ANN,
                            K.ANF -> 34f
                            K.AN,
                            K.AF -> 16f
                            else -> 12f
                        }
                    assertTrue(
                        "${spec.activity} ${K.NAMES[i]} $k: ${abs(a[i] - b[i])}",
                        abs(a[i] - b[i]) <= bound,
                    )
                }
            }
            // and the spline between them is continuous: samples a sixty-fourth apart
            var prev = spec.poseAtPhase(0f)
            for (s in 1..128) {
                val p = spec.poseAtPhase(s / 128f)
                for (i in 0 until K.SIZE) assertTrue("${K.NAMES[i]} $s", abs(p[i] - prev[i]) < 16f)
                prev = p
            }
        }
    }

    @Test
    fun armsSwingAgainstLegsKneesBendInTheSwingAndAnklesTiltAtHeelStrikeAndToeOff() {
        var worstKneeInSwing = 0f
        for (k in 0 until walk.frames) {
            val p = walk.poseAt(k)
            // the near arm is back when the near leg is forward, and the other way about
            if (p[K.LN] - p[K.LF] > 4f) assertTrue("frame $k", p[K.AN] < p[K.AF])
            if (p[K.LF] - p[K.LN] > 4f) assertTrue("frame $k", p[K.AF] < p[K.AN])
            worstKneeInSwing = maxOf(worstKneeInSwing, p[K.KN], p[K.KF])
        }
        // the swinging knee bends a good deal (a pendulum leg would stay straight)
        assertTrue("swing knee $worstKneeInSwing", worstKneeInSwing in 55f..80f)
        // at the contact the leading leg is nearly straight, with the toes up (heel strike)
        val c = walk.poseAt(0)
        assertTrue("contact knee ${c[K.KN]}", c[K.KN] < 20f)
        val tilt0 = c[K.LN] - c[K.KN] + c[K.ANN]
        assertTrue("heel strike tilt $tilt0", tilt0 in 10f..25f)
        // the trailing foot is pushing off with the toes down
        val tilt8 = walk.poseAt(10)[K.LN] - walk.poseAt(10)[K.KN] + walk.poseAt(10)[K.ANN]
        assertTrue("toe-off tilt $tilt8", tilt8 < -25f)
        // the body leans forward a little and the shoulders turn against the hips
        for (k in 0 until walk.frames) {
            val p = walk.poseAt(k)
            assertTrue(p[K.LEAN] in 2f..5f)
            // the near hip leads when the near leg does; the shoulder is the other way
            if (p[K.LN] - p[K.LF] > 10f) assertTrue(p[K.TWIST] > -.6f + 0f && p[K.SHX] < -.64f)
        }
        assertTrue(walk.poseAt(0)[K.TWIST] > walk.poseAt(8)[K.TWIST] + .5f)
        assertTrue(walk.poseAt(0)[K.SHX] < walk.poseAt(8)[K.SHX] - .8f)
    }

    @Test
    fun theHeadBobsAboutTwoAndAHalfPercentOfHeightAndIsLowestAtTheContact() {
        for (l in 0 until Looks.COUNT) {
            val figs =
                (0 until walk.frames).map {
                    FigBuilder(Looks.ALL[l], walk.poseAt(it), 0f).build(null, 0f)
                }
            val ys = figs.map { it.joints.headY }
            val b = FigBuilder.bounds(figs[0].shapes())
            val height = b[3] - b[1]
            val bob = ys.max() - ys.min()
            assertTrue("look $l bob ${100 * bob / height}%", bob / height in .015f..0.035f)
            // lowest (largest y) at the contact or the down pose of either step
            val lowest = ys.indexOf(ys.max()) % 8
            assertTrue("look $l lowest frame $lowest", lowest in 0..2 || lowest == 7)
            // highest on the way up, not at the contact
            val highest = ys.indexOf(ys.min()) % 8
            assertTrue("look $l highest frame $highest", highest in 4..6)
        }
    }

    /** The planted foot's reference point in the world: where it does not move. */
    private fun contactWorldX(phase: Float, near: Boolean, look: Int): Float? {
        val p = walk.poseAtPhase(phase)
        val fb = FigBuilder(Looks.ALL[look], p, 0f).build(null, 0f)
        val sa = if (near) p[K.LN] - p[K.KN] else p[K.LF] - p[K.KF]
        val ank = if (near) p[K.ANN] else p[K.ANF]
        val t = Math.toRadians((sa + ank).toDouble())
        val ax = if (near) fb.joints.footNX else fb.joints.footFX
        val ay = if (near) fb.joints.footNY else fb.joints.footFY
        val b = Looks.ALL[look].body.build
        val hgt = Looks.ALL[look].body.height
        val heelY = .3 * cos(t) + .25 * sin(t) + .68 * b
        val toeY = .38 * cos(t) - 2.1 * hgt * sin(t) + .52 * b
        val low = -ay - maxOf(heelY, toeY)
        // in the air (more than a twelfth of a unit up): nothing is planted
        if (low > .08) return null
        val tiltDeg = Math.toDegrees(t)
        val x =
            when {
                tiltDeg > 3.0 -> ax + .3 * sin(t) - .25 * cos(t)
                tiltDeg < -3.0 -> ax + .38 * sin(t) + 2.1 * hgt * cos(t)
                else -> ax.toDouble()
            }
        // the body goes at the gait's pace for its own height: a shorter person has a shorter
        // stride
        return (x + Gait.STRIDE * Looks.ALL[look].body.height * phase).toFloat()
    }

    @Test
    fun thePlantedFootStaysWhereItLandsWhileTheBodyMovesOverIt() {
        // a figure drawn at the gait's own speed: pelvis at STRIDE * phase; in each stretch of the
        // stance (heel down, foot flat, heel up) the point on the ground does not move
        for (look in 0 until Looks.COUNT) {
            val groups = ArrayList<MutableList<Float>>()
            val kinds = ArrayList<Int>()
            var prevKind = -1
            for (s in 0 until 64) {
                val phase = s / 64f
                val x = contactWorldX(phase, true, look)
                val p = walk.poseAtPhase(phase)
                val t = (p[K.LN] - p[K.KN] + p[K.ANN]).toDouble()
                val kind = if (x == null) -1 else if (t > 3.0) 0 else if (t < -3.0) 2 else 1
                if (kind != prevKind || kind == -1) {
                    if (kind != -1) {
                        groups.add(ArrayList())
                        kinds.add(kind)
                    }
                    prevKind = kind
                }
                if (kind != -1) groups.last() += x!!
            }
            assertTrue("look $look groups ${groups.size}", groups.size >= 3)
            for ((i, g) in groups.withIndex()) {
                if (g.size < 3) continue
                val slide = g.max() - g.min()
                // the heel and the flat foot hold within a third of a unit (under a pixel); the
                // toe, as it rolls off and lifts, within three quarters
                val tol = if (kinds[i] == 2) .75f else .4f
                assertTrue("look $look slide $slide over ${g.size} samples", slide < tol)
            }
            // and in all the foot is on the ground for a bit over half the cycle (60 per cent)
            val onGround = (0 until 64).count { contactWorldX(it / 64f, true, look) != null }
            assertTrue("look $look on the ground $onGround of 64", onGround in 32..44)
        }
    }

    @Test
    fun theSpeedIsTheStrideTimesTheFrequencyAndThePhaseFollowsTheDistance() {
        // one stride of ground is one cycle, whatever the size; a smaller person takes more steps
        for (size in listOf(.5f, .8f, 1f)) {
            val p0 = Gait.phase(0f, size)
            val p1 = Gait.phase(size * Gait.STRIDE, size)
            assertEquals(p0, p1, 1e-4f)
            val half = Gait.phase(size * Gait.STEP, size)
            assertEquals(.5f, ((half - p0) + 1f) % 1f, 1e-4f)
        }
        // at the rig's own size the pace is stride over cycle, in a unit of the building
        assertEquals(Gait.STRIDE / Gait.CYCLE, Gait.SPEED, 1e-4f)
        // a walker crossing a window follows it: the distance covered is the phase's distance
        val size = .7f
        val w = 17f
        val seed = 4
        var prev = Silhouettes.walkTravel(.01f, seed, w, size)
        var checked = 0
        for (i in 1..4000) {
            val t = .01f + i / 100f
            val d = Silhouettes.walkTravel(t, seed, w, size)
            if (!d.isNaN() && !prev.isNaN()) {
                // moving at the pace of the gait for this size
                assertEquals(Gait.SPEED * size * .01f, d - prev, 2e-3f)
                checked++
            }
            prev = d
        }
        assertTrue(checked > 500)
    }

    @Test
    fun theFrameForAPhaseIsMonotonicAndWrapsAndTheNeighbourIsTheNextOneOver() {
        var last = -1
        var wraps = 0
        for (i in 0..3200) {
            val phase = i / 1600f // two cycles, in steps of a 1600th
            val f = Gait.nearest(phase, 16)
            assertTrue(f in 0..15)
            if (last >= 0) {
                val d = (f - last + 16) % 16
                assertTrue("step $i: $last to $f", d == 0 || d == 1)
                if (f < last) wraps++
            }
            last = f
            val o = Gait.offset(phase, 16)
            assertTrue(o >= -.5f && o < .5f)
            val n = Gait.neighbour(phase, 16)
            val expect = if (o >= 0f) (f + 1) % 16 else (f + 15) % 16
            assertEquals(expect, n)
            // phase = nearest frame plus the offset
            val t = (phase - floor(phase)) * 16f
            val gap = ((t - f - o) % 16f + 16f) % 16f
            assertTrue("gap $gap", gap < 2e-3f || gap > 16f - 2e-3f)
        }
        assertEquals(2, wraps)
        // the continuous loop clock is the same: a person's phase only ever advances, and wraps
        val loop = Silhouettes.phase(walk, 10f, 3)
        assertTrue(loop in 0f..1f)
        var prev = Silhouettes.phase(walk, .01f, 3)
        var wrapCount = 0
        for (i in 1..3000) {
            val p = Silhouettes.phase(walk, .01f + i / 100f, 3)
            if (p < prev) wrapCount++ else assertTrue(p - prev < .03f)
            prev = p
        }
        assertTrue(wrapCount in 26..28) // 30 seconds of a 1.09 second cycle
        // and still while time stands still
        assertEquals(0f, Silhouettes.phase(walk, 0f, 9), 0f)
        assertEquals(0, Silhouettes.frame(walk, 0f, 9))
    }

    // ------------------------------------------------------------------------ the lobby

    private fun visitAt(t: Float, seed: Int, size: Float, w: Float, into: Stroll) {
        // t seconds into a visit: the visit's own clock starts where the cycle's offset does
        val cycle = Visit.cycle(seed)
        val off = com.lifemax.rpg.feature.habits.domain.Ambient.hash(seed, 0, 100) * cycle
        Visit.at(t - off + cycle * 3f, w, seed, size, into)
    }

    @Test
    fun theVisitorWalksOutStopsTurnsLooksTurnsBackAndWalksInWithoutSkating() {
        val seed = 3
        val size = .52f
        val w = 56f
        val s = Stroll()
        val total = Visit.length(w, size)
        assertTrue("visit $total", total in 15f..35f)
        val dt = 1f / 60f
        var t = .02f
        // nobody before the door opens: the first picture is the visitor stepping in at the edge
        visitAt(0f, seed, size, w, s)
        assertTrue(s.here)
        assertEquals(Stroll.WALK, s.mode)
        assertEquals(1f, s.dir, 0f)
        assertTrue("starts out of sight ${s.x}", s.x <= Visit.DOOR + .01f)
        // speeds along the way
        val out = Visit.walkOut(w, size)
        val v = size * Gait.SPEED
        var maxStep = 0f
        var prev = s.x
        var stopAt = -1f
        val speeds = ArrayList<Pair<Float, Float>>()
        while (t < out - .001f) {
            visitAt(t, seed, size, w, s)
            assertEquals(Stroll.WALK, s.mode)
            speeds += t to (s.x - prev) / dt
            maxStep = maxOf(maxStep, abs(s.x - prev))
            prev = s.x
            t += dt
        }
        // an easy start: slow in the first tenth of a second, at pace by the end of the third
        val early = speeds.first { it.first > .09f }.second
        val atPace = speeds.first { it.first > Visit.START + .05f }.second
        assertTrue("start speed $early of $v", early < v * .35f)
        assertEquals(v, atPace, v * .05f)
        // never faster than the gait's pace (no step is longer than it)
        assertTrue(maxStep <= v * dt * 1.02f)
        // a gentle stop: the last tenth of a second is nearly still
        val last = speeds.last().second
        assertTrue("stop speed $last", last < v * .12f)
        // the walk ends on the passing frame, the one the turn starts from
        visitAt(out - .002f, seed, size, w, s)
        assertEquals(Gait.FRAMES / 4, s.frame)
        stopAt = s.x
        // the turn: frames run from the passing one through the four turn frames to standing
        val seen = ArrayList<Int>()
        var tt = out + .001f
        while (tt < out + Visit.TURN) {
            visitAt(tt, seed, size, w, s)
            assertEquals(Stroll.TURN, s.mode)
            assertEquals(1f, s.dir, 0f)
            assertEquals(stopAt, s.x, 1e-3f)
            if (seen.lastOrNull() != s.frame) seen += s.frame
            tt += dt
        }
        assertEquals(listOf(4, 16, 17, 18, 19, 20), seen)
        // standing: still, looking out, from the frame the turn ended on
        visitAt(out + Visit.TURN + .01f, seed, size, w, s)
        assertEquals(Stroll.STAND, s.mode)
        assertEquals(0, s.frame)
        assertEquals(stopAt, s.x, 1e-3f)
        visitAt(out + Visit.TURN + Visit.STAND - .05f, seed, size, w, s)
        assertEquals(Stroll.STAND, s.mode)
        assertEquals(1f, s.dir, 0f)
        // the turn back faces the other way and runs the frames in reverse
        val back = ArrayList<Int>()
        tt = out + Visit.TURN + Visit.STAND + .001f
        while (tt < out + 2 * Visit.TURN + Visit.STAND - .001f) {
            visitAt(tt, seed, size, w, s)
            assertEquals(Stroll.TURN, s.mode)
            assertEquals(-1f, s.dir, 0f)
            if (back.lastOrNull() != s.frame) back += s.frame
            tt += dt
        }
        assertEquals(listOf(20, 19, 18, 17, 16, 4), back)
        // then in at pace, the same distance, and out of sight; the phase follows the distance
        tt = out + 2 * Visit.TURN + Visit.STAND + .01f
        var px = Float.NaN
        var pPhaseFrame = -1
        var gone = false
        while (tt < out + 2 * Visit.TURN + Visit.STAND + total) {
            visitAt(tt, seed, size, w, s)
            if (!s.here) {
                gone = true
                break
            }
            assertEquals(Stroll.WALK, s.mode)
            assertEquals(-1f, s.dir, 0f)
            if (!px.isNaN()) assertTrue("backwards step", s.x <= px + 1e-4f)
            px = s.x
            tt += dt
            pPhaseFrame = s.frame
        }
        assertTrue(gone)
        assertTrue("went in through the door at $px", px < Visit.DOOR + 3f)
    }

    @Test
    fun theVisitorsFramesAdvanceWithTheGroundTheyCover() {
        val seed = 5
        val size = .5f
        val w = 56f
        val s = Stroll()
        var prevX = Float.NaN
        var prevPhase = Float.NaN
        var checked = 0
        val out = Visit.walkOut(w, size)
        var t = Visit.START + .3f
        while (t < out - Visit.STOP - .3f) {
            visitAt(t, seed, size, w, s)
            // the frame is the nearest to the phase for the distance from the door
            val phase = Gait.PASSING + (s.x - Visit.DOOR) / (size * Gait.STRIDE)
            assertEquals(Gait.nearest(phase, Gait.FRAMES), s.frame)
            assertTrue(s.mix in 0f..0.5f)
            if (!prevX.isNaN()) {
                // frames per distance: 16 frames to a stride
                assertTrue(abs((s.x - prevX)) < size * Gait.STRIDE / 16f)
            }
            prevX = s.x
            prevPhase = phase
            t += .01f
            checked++
        }
        assertTrue(checked > 100)
    }

    @Test
    fun atRestTheVisitorStandsStillAtTheFarEndInOneBuildingInThreeAndNobodyIsMidStride() {
        val s = Stroll()
        var here = 0
        for (seed in 0 until 300) {
            Visit.at(0f, 56f, seed, .5f, s)
            if (s.here) {
                here++
                assertEquals(Stroll.STAND, s.mode)
                assertEquals(0, s.frame)
                assertEquals(0f, s.mix, 0f)
                assertEquals(56f * .55f, s.x, 1e-4f)
            }
        }
        assertTrue("here $here", here in 60..150)
        // and the stills are the same every time
        for (spec in PeopleActivities.ALL) {
            assertTrue(FigBuilder.same(spec.poseAt(0), spec.poseAt(0), 0f))
            assertEquals(0, Silhouettes.frame(spec, 0f, 7))
        }
        val a = PeopleSprites.render(0, Activity.WALKING, 0)
        val b = PeopleSprites.render(0, Activity.WALKING, 0)
        assertTrue(a.pixels.contentEquals(b.pixels))
    }

    @Test
    fun aFrameIsKeptOnlyAsBigAsItsPixelsAndTheAtlasStaysSmall() {
        val full = PeopleSprites.render(2, Activity.WALKING, 4)
        val baked = PeopleSprites.bake(2, Activity.WALKING, 4)
        assertTrue(baked.w <= full.w && baked.h <= full.h)
        assertTrue(baked.w * baked.h < full.w * full.h)
        // the same picture: every pixel of the full frame is in the crop at the shifted spot
        val dx = (full.ox - baked.ox).toInt()
        val dy = (full.oy - baked.oy).toInt()
        for (y in 0 until baked.h) for (x in 0 until baked.w) assertEquals(
            full.pixels[(y + dy) * full.w + x + dx],
            baked.pixels[y * baked.w + x],
        )
        // every walking and jogging frame of every look, kept: well within the budget
        var bytes = 0
        for (a in listOf(Activity.WALKING, Activity.JOGGING)) {
            val spec = PeopleActivities.of(a)
            for (l in 0 until Looks.COUNT) for (f in 0 until spec.totalFrames) {
                val s = PeopleSprites.bake(l, a, f)
                bytes += s.w * s.h * 4
            }
        }
        assertTrue("walking and jogging $bytes", bytes < 1_600_000)
    }
}
