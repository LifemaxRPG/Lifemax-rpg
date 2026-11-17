package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Act
import com.lifemax.rpg.feature.habits.domain.Ambient
import com.lifemax.rpg.feature.habits.domain.Crew
import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.Gesture
import com.lifemax.rpg.feature.habits.domain.Layers
import com.lifemax.rpg.feature.habits.domain.Part
import com.lifemax.rpg.feature.habits.domain.Pass
import com.lifemax.rpg.feature.habits.domain.Passer
import com.lifemax.rpg.feature.habits.domain.StreetActs
import com.lifemax.rpg.feature.habits.domain.StreetCameos
import com.lifemax.rpg.feature.habits.domain.StreetCrew
import com.lifemax.rpg.feature.habits.domain.StreetGait
import com.lifemax.rpg.feature.habits.domain.StreetLayers
import com.lifemax.rpg.feature.habits.domain.StreetLife
import com.lifemax.rpg.feature.habits.domain.StreetLooks
import com.lifemax.rpg.feature.habits.domain.StreetRig
import com.lifemax.rpg.feature.habits.domain.Stroller
import com.lifemax.rpg.feature.habits.domain.buildingLayout
import com.lifemax.rpg.feature.habits.domain.streetLayout
import com.lifemax.rpg.feature.habits.presentation.scene.StreetSprites
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The crew (GU1–GU5): rare, together, never through each other or anyone else, and cheap. */
class StreetCrewTest {
    private val seed = HabitsScenarios.TODAY.hashCode()
    private val city = HabitsScenarios.projection("city")
    private val street = streetLayout(city.stacks.map { buildingLayout(it) })
    private val crew = Crew()

    @Test
    fun theCrewLooksAreIndexedAfterTheOrdinaryOnesAndDrawTheSameAsTheirOwnAppearance() {
        assertEquals(StreetLooks.COUNT, StreetLooks.CREW_AT)
        assertEquals(5, StreetLooks.CREW_COUNT)
        for (i in 0 until StreetLooks.CREW_COUNT) {
            val look = StreetLooks.CREW_AT + i
            val gu = StreetCameos.CREW[i]
            assertTrue(StreetLooks.crew(look))
            assertTrue(StreetLooks.of(look) === gu)
            assertTrue(StreetLooks.ALL.none { it === gu })
            // the walk is the look's own, and the layered painter's arm lengths are the rig's
            assertTrue(StreetGait.style(look) === gu.walkStyle)
            assertEquals(4.1f * gu.height, StreetRig.upperLength(look), 1e-6f)
            for (f in 0 until StreetRig.TOTAL_FRAMES step 3) {
                val a = StreetRig.frame(look, f).shapes
                val b = StreetRig.frame(gu, f).shapes
                assertEquals(a.size, b.size)
                for (k in a.indices) {
                    assertEquals(a[k].rgb, b[k].rgb)
                    assertArrayEquals(a[k].pts, b[k].pts, 0f)
                }
            }
            // the hands are the crew's shared size in the forearm layer too
            val fore = StreetRig.forearm(look, true, StreetRig.OPEN)
            assertTrue(fore.isNotEmpty())
            assertEquals(
                StreetCameos.GROUP_HAND_BUILD,
                gu.build * gu.armScale * gu.handScale,
                1e-4f,
            )
        }
    }

    @Test
    fun theyComeNowAndThenEveryFewMinutesAndNeverInTheFirstFew() {
        val p = StreetCrew.period(street.start, street.end)
        val d = StreetCrew.duration(street.start, street.end)
        assertTrue("a walk takes $d s", d < p)
        var walks = 0
        var on = 0
        var all = 0
        var was = false
        var t = .5f
        val hours = 6
        while (t < hours * 3600f) {
            val now = StreetCrew.at(t, seed, street.start, street.end, crew)
            if (t < StreetCrew.PERIOD) assertFalse(now)
            if (now && !was) walks++
            if (now) on++
            all++
            was = now
            t += 1f
        }
        val every = hours * 60f / walks
        println("crew: $walks walks in $hours h (one every $every min), on ${100f * on / all}%")
        assertTrue("one every $every minutes", every in 3f..8f)
    }

    @Test
    fun theSameMomentIsTheSameEveryTimeAndEveryoneIsOnThePavement() {
        val other = Crew()
        var t = 100f
        var seen = 0
        while (t < 2 * 3600f) {
            if (StreetCrew.at(t, seed, street.start, street.end, crew)) {
                // asked afresh, from nothing, the same
                StreetCrew.at(t, seed, street.start, street.end, other)
                for (i in 0 until StreetCrew.SIZE) {
                    val a = crew.members[i]
                    val b = other.members[i]
                    assertEquals(a.x, b.x, 0f)
                    assertEquals(a.lane, b.lane, 0f)
                    assertEquals(a.frame, b.frame)
                    assertEquals(a.act.act, b.act.act)
                    assertEquals(a.act.w, b.act.w, 0f)
                    assertTrue(StreetLooks.crew(a.look))
                    assertTrue(a.frame % StreetCrew.STEP == 0 && a.frame < StreetGait.FRAMES)
                    assertTrue(a.next % StreetCrew.STEP == 0 && a.next < StreetGait.FRAMES)
                    assertTrue(a.mix in 0f..0.5f)
                    assertTrue(a.lane in 7f..crew.top + 1e-4f)
                    assertTrue(a.face in -1f..1f)
                    assertTrue(
                        a.x >= street.start - StreetLife.MARGIN - 60f &&
                            a.x <= street.end + StreetLife.MARGIN + 60f
                    )
                }
                seen++
            }
            t += 3.7f
        }
        assertTrue(seen > 50)
    }

    /**
     * Followed a thirtieth of a second at a time: everyone moves smoothly (no jumps in place,
     * facing or the walk's frame), stays in the group, and nobody walks through anybody.
     */
    @Test
    fun theyMoveSmoothlyKeepTogetherAndNeverWalkThroughEachOther() {
        val dt = 1f / 30f
        var t = 100f
        var steps = 0
        var closest = Float.MAX_VALUE
        var fastest = 0f
        val lastX = FloatArray(StreetCrew.SIZE)
        val lastLane = FloatArray(StreetCrew.SIZE)
        val lastFace = FloatArray(StreetCrew.SIZE)
        val lastPhase = FloatArray(StreetCrew.SIZE)
        var was = false
        val kinds = HashSet<Act>()
        var backwards = 0
        while (t < 3600f) {
            val on = StreetCrew.at(t, seed, street.start, street.end, crew)
            if (on) {
                var lo = Float.MAX_VALUE
                var hi = -Float.MAX_VALUE
                for (i in 0 until StreetCrew.SIZE) {
                    val m = crew.members[i]
                    kinds += m.act.act
                    // where the walk is: its frame and the mix towards the next
                    val step =
                        if (m.next == (m.frame + StreetCrew.STEP) % StreetGait.FRAMES) 1 else -1
                    val phase = (m.frame + step * m.mix * StreetCrew.STEP) / StreetGait.FRAMES
                    if (was) {
                        assertTrue("x at $t", abs(m.x - lastX[i]) < 30f * dt)
                        val dl = abs(m.lane - lastLane[i]) / dt
                        fastest = maxOf(fastest, dl)
                        assertTrue("lane at $t: $dl", dl < 9f)
                        assertTrue("face at $t", abs(m.face - lastFace[i]) < 10f * dt)
                        var dp = phase - lastPhase[i]
                        if (dp > .5f) dp -= 1f
                        if (dp < -.5f) dp += 1f
                        assertTrue("phase at $t: $dp", abs(dp) < .09f)
                        if (dp < -.001f) backwards++
                    }
                    lastX[i] = m.x
                    lastLane[i] = m.lane
                    lastFace[i] = m.face
                    lastPhase[i] = phase
                    lo = min(lo, m.x)
                    hi = maxOf(hi, m.x)
                }
                // a loose group, not strung out along the street
                assertTrue("spread ${hi - lo} at $t", hi - lo < 64f)
                for (i in 0 until StreetCrew.SIZE) for (j in i + 1 until StreetCrew.SIZE) {
                    val a = crew.members[i]
                    val b = crew.members[j]
                    val d = hypot((a.x - b.x) / 6f, (a.lane - b.lane) / 3f)
                    closest = min(closest, d)
                    assertTrue("$i and $j at $t: ${a.x - b.x}, ${a.lane - b.lane}", d > .42f)
                }
                steps++
            }
            was = on
            t += dt
        }
        println(
            "crew: closest $closest, fastest sideways $fastest, acts $kinds, $backwards backward steps"
        )
        assertTrue(steps > 1000)
        for (a in listOf(Act.SHOVE, Act.CATCH, Act.LAUGH, Act.STORY, Act.JOG, Act.TALK)) assertTrue(
            "$a",
            a in kinds,
        )
        assertTrue(backwards > 30)
    }

    @Test
    fun withMotionReducedTheyJustWalk() {
        var t = 100f
        var seen = 0
        while (t < 3600f) {
            if (StreetCrew.at(t, seed, street.start, street.end, crew, goof = false)) {
                for (m in crew.members) {
                    assertEquals(1f, abs(m.face), 0f)
                    assertEquals(0f, m.act.w, 0f)
                    assertEquals(0f, m.lean, 0f)
                }
                seen++
            }
            t += 1.3f
        }
        assertTrue(seen > 100)
    }

    @Test
    fun othersPassThemOnTheKerbSideAndTheyCloseUpToLetThem() {
        val out = Array(StreetLife.MOST) { Passer() }
        var t = 100f
        var passed = 0
        while (t < 3 * 3600f) {
            val n = StreetLife.passers(t, seed, street.start, street.end, out, street.centers, crew)
            if (crew.on)
                for (i in 0 until n) {
                    val p = out[i]
                    assertTrue(p.lane in 7f..14f)
                    for (m in crew.members) {
                        val px =
                            if (p.kind == Stroller.PAIR) p.x - p.dir * StreetLife.SIDE_BACK / 2f
                            else p.x
                        val reach = if (p.kind == Stroller.CYCLIST) 10f else 4f
                        if (abs(px - m.x) > reach) continue
                        assertTrue(
                            "$t: ${p.kind} at ${p.lane}, crew at ${m.lane}",
                            p.lane - m.lane > 1.5f,
                        )
                        passed++
                    }
                }
            t += .5f
        }
        println("crew: $passed close passes")
        assertTrue(passed > 20)
    }

    @Test
    fun theOrdinaryStreetIsUnchangedWhenTheyAreNotOn() {
        val a = Array(StreetLife.MOST) { Passer() }
        val b = Array(StreetLife.MOST) { Passer() }
        var t = .5f
        while (t < 3600f) {
            val n = StreetLife.passers(t, seed, street.start, street.end, a, street.centers)
            val m = StreetLife.passers(t, seed, street.start, street.end, b, street.centers, crew)
            assertEquals(n, m)
            if (!crew.on)
                for (i in 0 until n) {
                    assertEquals(a[i].x, b[i].x, 0f)
                    assertEquals(a[i].lane, b[i].lane, 0f)
                }
            t += 2.3f
        }
    }

    /** The moments the screenshot tests hold the street at: the crew is in none of them. */
    @Test
    fun theyAreNotInTheReferenceScreenshots() {
        val blimp = run {
            val pass = Pass()
            var t = 1f
            while (t < 20000f) {
                if (Ambient.blimp(t, seed, pass) && pass.t in .45f..0.55f) break
                t += .5f
            }
            t
        }
        val moments =
            floatArrayOf(
                9.6f,
                12.3f,
                21.4f,
                31.7f,
                45f,
                HabitsScenarios.TV_AT,
                HabitsScenarios.NEAR_CAR_AT,
                HabitsScenarios.STREET_WALKER_AT,
                HabitsScenarios.NIGHT_SKY_AT,
                HabitsScenarios.DAY_SKY_AT,
                blimp,
            )
        for (name in listOf("city", "tall", "decay", "glass", "park", "edge")) {
            val s = streetLayout(HabitsScenarios.projection(name).stacks.map { buildingLayout(it) })
            for (t in moments) assertFalse(
                "$name at $t",
                StreetCrew.at(t, seed, s.start, s.end, crew),
            )
        }
    }

    /** The screenshot of a shove (HabitsScreenshotTest.crewShoveDay) has a moment to show. */
    @Test
    fun aShovePassesTheFirstBuilding() {
        var t = StreetCrew.PERIOD
        var found = 0f
        while (t < 20000f && found == 0f) {
            if (StreetCrew.at(t, seed, street.start, street.end, crew)) {
                val middle = (crew.lo + crew.hi) / 2f
                if (
                    abs(middle - street.centers[0]) < 25f &&
                        crew.members.any { it.act.act == Act.SHOVE && it.act.w > .9f }
                )
                    found = t
                t += .05f
            } else t += 2f
        }
        println("crew: a shove at the first building at $found s")
        assertTrue(found > 0f)
    }

    @Test
    fun theirActsMoveTheArmsSmoothly() {
        val out = Layers()
        val g = Gesture()
        // each eased in as quickly as the crew's beats ease it
        val acts =
            mapOf(
                Act.SHOVE to .25f,
                Act.BACKHAND to .45f,
                Act.CATCH to .3f,
                Act.LAUGH to .3f,
                Act.STORY to .5f,
                Act.JOG to .3f,
            )
        for (look in StreetLooks.CREW_AT until StreetLooks.CREW_AT + StreetLooks.CREW_COUNT) for ((
            act, blend) in acts) {
            var lastDeg = Float.NaN
            var t = -.2f
            val dur = 3f
            while (t < dur + .2f) {
                g.clear()
                g.act = act
                g.t = t
                g.dur = dur
                g.w = StreetActs.envelope(t, dur, blend)
                val phase = (t / 1.09f).let { it - kotlin.math.floor(it) }
                val f = (phase * StreetCrew.FRAMES).toInt() % StreetCrew.FRAMES * StreetCrew.STEP
                StreetLayers.lay(look, f, (f + StreetCrew.STEP) % StreetGait.FRAMES, 0f, 0f, g, out)
                var deg = Float.NaN
                for (i in 0 until out.count) if (out.part[i] == Part.UPPER_N) deg = out.deg[i]
                if (!lastDeg.isNaN()) assertTrue("$act look $look at $t", abs(deg - lastDeg) < 9f)
                lastDeg = deg
                t += 1f / 60f
            }
        }
    }

    @Test
    fun theCrewsPicturesFitTheirOwnSmallBudget() {
        val bytes = StreetSprites.crewBytes()
        println("crew sprites: ${bytes / 1024} KiB")
        // kept only while they are about, apart from the others' (which keep their own budget)
        assertTrue("crew is $bytes bytes", bytes < 5 * 1024 * 1024)
        // every crew frame stands on its feet like the others'
        for (i in 0 until StreetLooks.CREW_COUNT) {
            val s = StreetSprites.crewWalker(StreetLooks.CREW_AT + i, 10).sprite
            assertTrue(s.h - s.oy < 4f && s.h - s.oy > -1f)
            val h = StreetRig.height(StreetLooks.CREW_AT + i) * StreetLife.SIZE
            assertTrue("crew $i is $h", h in 25f..31f)
            assertTrue(
                FigBuilder.bounds(StreetRig.body(StreetLooks.CREW_AT + i, 10).shapes)[3] < .5f
            )
        }
    }
}
