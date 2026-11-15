package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.feature.habits.domain.Activity
import com.lifemax.rpg.feature.habits.domain.FigBuilder
import com.lifemax.rpg.feature.habits.domain.HabitsRules
import com.lifemax.rpg.feature.habits.domain.Looks
import com.lifemax.rpg.feature.habits.domain.PeopleActivities
import com.lifemax.rpg.feature.habits.domain.PeopleLod
import com.lifemax.rpg.feature.habits.domain.RoomKind
import com.lifemax.rpg.feature.habits.domain.Silhouettes
import com.lifemax.rpg.feature.habits.domain.Spline
import com.lifemax.rpg.feature.habits.domain.occupancy
import com.lifemax.rpg.feature.habits.presentation.scene.PeopleSprites
import com.lifemax.rpg.feature.habits.presentation.scene.RoomSprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The people in the windows: what they do, who they are, how they move, how big they are, what they
 * cost.
 */
class SilhouettesTest {
    @Test
    fun peopleDoWhatTheHabitIsCalled() {
        assertEquals(Activity.PUSHUPS, Silhouettes.activityOf("Ten push-ups"))
        assertEquals(Activity.PUSHUPS, Silhouettes.activityOf("Plank 1 min"))
        assertEquals(Activity.READING, Silhouettes.activityOf("Read 10 pages"))
        assertEquals(Activity.READING, Silhouettes.activityOf("Language app"))
        assertEquals(Activity.WRITING, Silhouettes.activityOf("Journal 5 min"))
        assertEquals(Activity.PLANNING, Silhouettes.activityOf("Plan the day"))
        assertEquals(Activity.STRETCHING, Silhouettes.activityOf("Stretch 10 min"))
        assertEquals(Activity.MEDITATING, Silhouettes.activityOf("Meditate"))
        assertEquals(Activity.BRUSHING, Silhouettes.activityOf("Cold shower"))
        assertEquals(Activity.WALKING, Silhouettes.activityOf("Walk 20 min"))
        assertEquals(Activity.JOGGING, Silhouettes.activityOf("Go for a run"))
        assertEquals(Activity.MAKING_BED, Silhouettes.activityOf("Make the bed"))
        assertEquals(Activity.DRINKING, Silhouettes.activityOf("Glass of water"))
        assertEquals(Activity.WATERING, Silhouettes.activityOf("Water the plants"))
        assertEquals(Activity.COOKING, Silhouettes.activityOf("Cook dinner"))
        assertEquals(Activity.SWEEPING, Silhouettes.activityOf("Tidy 5 minutes"))
        assertEquals(Activity.PHONE_CALL, Silhouettes.activityOf("Call a friend"))
        assertEquals(Activity.GUITAR, Silhouettes.activityOf("Practice guitar"))
        assertEquals(Activity.TYPING, Silhouettes.activityOf("Clear the inbox"))
        // nothing fits: ordinary office life instead
        assertNull(Silhouettes.activityOf("Zzz"))
        assertNull(Silhouettes.activityOf(""))
    }

    @Test
    fun theCatalogueWidensWhatAHabitsNameMatches() {
        // "Do 10 push-ups" is in the catalogue with the key "chest"; a name found only by its keys
        // still fits
        assertEquals(Activity.PUSHUPS, Silhouettes.activityOf("Do 10 push-ups"))
        assertTrue(Silhouettes.activitiesOf("Exercise").size >= 3)
    }

    @Test
    fun aFloorsPeopleMostlyDoItsHabitAndSomeDoOtherThings() {
        val reading = (0 until 8).map { Silhouettes.activityFor("Read 30 min", it, 11) }
        assertEquals(Activity.READING, reading[0])
        assertTrue(reading.count { it == Activity.READING } >= 4)
        assertTrue(reading.toSet().size >= 2)
        // a name nothing fits fills its windows with different office life
        val generic = (0 until 8).map { Silhouettes.activityFor("Zzz", it, 5) }
        assertTrue(generic.toSet().size >= 4)
        // the same floor is the same every time
        assertEquals(reading, (0 until 8).map { Silhouettes.activityFor("Read 30 min", it, 11) })
        // and generic floors with different habits differ
        val other = (0 until 8).map { Silhouettes.activityFor("Zzz", it, 6) }
        assertNotEquals(generic, other)
    }

    @Test
    fun theCastOfAFloorIsDeterministicVariedAndKeepsItsHeadcount() {
        val a = Silhouettes.cast("Read 30 min", "h1/0", 8, 5)
        val b = Silhouettes.cast("Read 30 min", "h1/0", 8, 5)
        assertEquals(8, a.size)
        assertEquals(5, a.count { it.activity != null })
        assertTrue(
            a.indices.all {
                a[it].activity == b[it].activity &&
                    a[it].look == b[it].look &&
                    a[it].curtain == b[it].curtain
            }
        )
        // the first five have someone, the rest an empty room with its lights on
        assertTrue((0 until 5).all { a[it].activity != null })
        assertTrue((5 until 8).all { a[it].activity == null })
        // six people on a floor are six different looks
        val six = Silhouettes.cast("Read 30 min", "h1/1", 6, 6)
        assertEquals(6, six.map { it.look }.toSet().size)
        // every window's room is the room its activity is seen in
        for (w in a) if (w.activity != null)
            assertEquals(PeopleActivities.of(w.activity!!).room, w.room)
        // curtains and sill things turn up now and then across a few floors
        val many = (0 until 12).flatMap { Silhouettes.cast("Walk", "x$it", 8, 3).toList() }
        assertTrue(many.any { it.curtain != 0 })
        assertTrue(many.any { it.sill != 0 })
        assertTrue(many.any { it.flip } && many.any { !it.flip })
    }

    @Test
    fun howManyAreLitAndInThemStillGrowsWithReps() {
        val rules = HabitsRules.RULES
        assertEquals(1, occupancy(rules.clay - 1, 6).people)
        assertEquals(2, occupancy(rules.clay, 6).people)
        assertEquals(2, occupancy(rules.clay, 6).lit)
        val stone = occupancy(rules.stone, 6)
        assertEquals(5, stone.lit)
        val gold = occupancy(rules.gold, 6)
        assertEquals(6, gold.lit)
        assertTrue(gold.people > stone.people)
        // the cast follows the occupancy
        assertEquals(gold.lit, Silhouettes.cast("Walk", "k", gold.lit, gold.people).size)
    }

    @Test
    fun anActivityHasAtLeastFourteenKindsAndEachIsAClosedLoop() {
        assertTrue(Activity.entries.size >= 14)
        for (spec in PeopleActivities.ALL) {
            assertTrue("${spec.activity} frames", spec.frames in 2..8)
            assertTrue("${spec.activity} loop closed", Spline.closed(spec.keys, .001f))
            // sampled evenly round the loop: the frame after the last is the first again
            val wrap = Spline.sample(spec.keys, 1f)
            assertTrue(FigBuilder.same(wrap, spec.poseAt(0), .001f))
            assertTrue("${spec.activity} speed", spec.fps in .3f..13f)
        }
        // somebody is typing, reading, writing, stretching, lifting, jogging, meditating,
        // cooking...
        val acts = PeopleActivities.ALL.map { it.activity }.toSet()
        assertEquals(Activity.entries.toSet(), acts)
    }

    @Test
    fun peopleAreBuiltLikePeople() {
        for (l in 0 until Looks.COUNT) {
            val fig =
                FigBuilder(Looks.ALL[l], com.lifemax.rpg.feature.habits.domain.pose(), 0f)
                    .build(null, 0f)
            val b = FigBuilder.bounds(fig.shapes())
            val height = b[3] - b[1]
            // standing: about seven and a half heads, 21 to 28 units
            assertTrue("height $height", height in 20f..28f)
            // the head is about a seventh of it, the shoulders about two head widths
            val head = fig.joints.headY
            assertTrue(head < fig.joints.shoulderY && fig.joints.shoulderY < fig.joints.pelvisY)
            val torso = fig.joints.pelvisY - fig.joints.shoulderY
            assertTrue("torso $torso", torso in 5.5f..8.6f)
            // grounded: the feet are on y = 0
            assertEquals(0f, b[3], .01f)
        }
        // four distinct bodies
        val heights =
            Looks.ALL.map {
                    FigBuilder.bounds(
                        FigBuilder(it, com.lifemax.rpg.feature.habits.domain.pose(), 0f)
                            .build(null, 0f)
                            .shapes()
                    )
                }
                .map { it[3] - it[1] }
        assertTrue(heights.toSet().size >= 3)
    }

    @Test
    fun everyoneStaysInsideTheirWindow() {
        for (a in Activity.entries) {
            val spec = PeopleActivities.of(a)
            val r = PeopleActivities.reach(a)
            val fit = PeopleActivities.fitted(a)
            val room = PeopleActivities.roomFit(spec.room)
            // the figure drawn at its window scale fits across a window 17 wide, from the room's
            // middle
            val shift = PeopleActivities.shift(spec.room)
            val s = room
            val left = s * (r[0] * fit + shift) + 8.5f
            val right = s * (r[2] * fit + shift) + 8.5f
            if (a != Activity.WALKING) {
                assertTrue("$a left $left", left > -.8f)
                assertTrue("$a right $right", right < 17.8f)
            }
            // and no taller than the tallest window
            assertTrue("$a tall", (-r[1]) * fit <= 32f)
            assertTrue("$a floor", r[3] <= 1.3f)
        }
    }

    @Test
    fun theFramesTurnOverInStepsAndEachPersonIsOutOfStepWithTheOthers() {
        for (spec in PeopleActivities.ALL) {
            val frames = (0 until 4000).map { Silhouettes.frame(spec, it / 50f + .01f, 3) }
            assertTrue(frames.all { it in 0 until spec.frames })
            assertEquals((0 until spec.frames).toSet(), frames.toSet())
            // still while time stands still
            assertEquals(0, Silhouettes.frame(spec, 0f, 9))
            assertEquals(0f, Silhouettes.blend(spec, 0f, 9), 0f)
            if (spec.blend) {
                val b = (0 until 100).map { Silhouettes.blend(spec, it / 7f + .3f, 2) }
                assertTrue(b.all { it in 0f..1f } && b.any { it > .5f })
            }
        }
        val typing = PeopleActivities.of(Activity.TYPING)
        val one = (0 until 40).map { Silhouettes.frame(typing, it / 10f + .1f, 1) }
        val two = (0 until 40).map { Silhouettes.frame(typing, it / 10f + .1f, 5) }
        assertNotEquals(one, two)
    }

    @Test
    fun someoneWalkingPastCrossesTheWindowAndLeavesIt() {
        val w = 17f
        assertEquals(w / 2f, Silhouettes.walkX(0f, 4, w), 0f)
        val xs = (1..130).map { Silhouettes.walkX(it / 20f, 4, w) }
        assertTrue(xs.any { it.isNaN() })
        val inside = xs.filter { !it.isNaN() }
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { it in -6.01f..(w + 6.01f) })
        val dir = Silhouettes.walkDir(4)
        inside.zipWithNext().forEach { (a, b) ->
            if (kotlin.math.abs(b - a) < 5f) assertTrue((b - a) * dir >= 0f)
        }
    }

    @Test
    fun someoneWalksAlongTheWholeFloorNowAndThen() {
        val xs = (1..2000).map { Silhouettes.commuterX(it / 10f, 7, 184f) }
        assertTrue(xs.any { it.isNaN() })
        val inside = xs.filter { !it.isNaN() }
        assertTrue(inside.size > 50)
        assertTrue(inside.all { it in -10.1f..194.1f })
        assertTrue(Silhouettes.commuterX(0f, 7, 184f).isNaN())
        // one person arrives and leaves the lobby too
        val v = (1..400).map { Silhouettes.lobbyVisitor(it / 10f, 56f) }
        assertTrue(v.any { it.isNaN() } && v.any { !it.isNaN() })
        assertTrue(Silhouettes.lobbyVisitor(0f, 56f) > 20f)
    }

    @Test
    fun peopleStepOutAndLampsGoOffForAMomentOnly() {
        val here = (1..3000).map { Silhouettes.here(4, it / 10f) }
        assertTrue(here.any { it == 0f })
        assertTrue(here.count { it == 1f } > here.size * .8f)
        assertEquals(1f, Silhouettes.here(4, 0f), 0f)
        val lamp = (1..3000).map { Silhouettes.lamp(4, it / 10f) }
        assertTrue(lamp.any { it == 0f } && lamp.count { it == 1f } > lamp.size * .7f)
        assertEquals(1f, Silhouettes.lamp(4, 0f), 0f)
    }

    @Test
    fun detailFadesWithScale() {
        assertEquals(PeopleLod.FULL, Silhouettes.lod(1.2f))
        assertEquals(PeopleLod.FULL, Silhouettes.lod(.6f))
        assertEquals(PeopleLod.BLOB, Silhouettes.lod(.4f))
        assertEquals(PeopleLod.DOT, Silhouettes.lod(.2f))
        assertEquals(PeopleLod.NONE, Silhouettes.lod(.08f))
        // never more detail further out
        var last = 0
        for (i in 150 downTo 5) {
            val o = Silhouettes.lod(i / 100f).ordinal
            assertTrue(o >= last)
            last = o
        }
    }

    @Test
    fun peopleAreSmallerInAShortWindowAndEveryRoomIsPaintedForEachSize() {
        assertTrue(RoomSprites.personScale(21f) < RoomSprites.personScale(37f))
        assertEquals(1f, RoomSprites.personScale(37f), 0f)
        for (kind in RoomKind.entries) {
            val r = RoomSprites.render(kind, 27.5f)
            assertEquals(r.back.w * r.back.h, r.back.pixels.size)
            // the room has something in it: not an empty overlay
            assertTrue(r.back.pixels.count { (it ushr 24) > 40 } > 100)
        }
    }

    @Test
    fun theAtlasStaysWithinItsMemoryBudgetEvenIfEveryFrameIsMade() {
        val worst = PeopleSprites.worstCaseBytes()
        var rooms = 0
        for (h in com.lifemax.rpg.feature.habits.domain.BuildingSpec.HEIGHTS) {
            val rows =
                if (h >= com.lifemax.rpg.feature.habits.domain.BuildingSpec.TWO_ROWS) 2 else 1
            val wh = (h - 23f - 6f * (rows - 1)) / rows
            rooms += RoomKind.entries.size * RoomSprites.bytes(wh)
        }
        assertTrue("worst case ${worst + rooms}", worst + rooms < 6 * 1024 * 1024)
        // a frame is lit: not black, with a soft glow past its edge
        val f = PeopleSprites.render(0, Activity.TYPING, 0)
        val solid = f.pixels.count { (it ushr 24) == 255 }
        val soft = f.pixels.count { (it ushr 24) in 1..254 }
        assertTrue(solid > 100 && soft > 50)
        val brightest = f.pixels.filter { (it ushr 24) == 255 }.maxOf { (it shr 16) and 255 }
        assertTrue("not pure black", brightest > 40)
        // made the same every time
        assertTrue(f.pixels.contentEquals(PeopleSprites.render(0, Activity.TYPING, 0).pixels))
    }
}
