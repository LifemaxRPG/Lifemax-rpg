package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two cats living in the whole house: the same day for the same seeds at any frame rate, a polite
 * distance kept on the floor, every room visited, and never a step outside the house.
 */
class HouseLifeTest {
    private val ted = HouseCat(Autopilot.seedFor("Ted", "ginger"), false)
    private val lily = HouseCat(Autopilot.seedFor("Lily", "dna_black_0_n_0_0_n_0_3"), false)

    /** What a run of the house saw, frame by frame. */
    private class Day(count: Int) {
        val moves = List(count) { ArrayList<Autopilot.Move>() }
        val rooms = List(count) { HashSet<Int>() }
        val surfaces = List(count) { HashSet<SurfaceId>() }
        val clips = HashSet<String>()
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var closest = Float.MAX_VALUE
        val trace = ArrayList<String>()
    }

    private fun run(cats: List<HouseCat>, seconds: Int, hz: Int = 60): Day {
        val house = HouseLife(cats)
        val day = Day(cats.size)
        val dt = 1f / hz
        val last = Array(cats.size) { house.lastMove(it) }
        for (frame in 1..seconds * hz) {
            house.step(dt)
            for (i in 0 until house.count) {
                val pose = house.bodies[i].pose
                if (house.lastMove(i) != last[i]) {
                    last[i] = house.lastMove(i)
                    day.moves[i].add(last[i])
                }
                day.rooms[i].add(room(pose.x))
                day.surfaces[i].add(pose.surface)
                day.clips.add(pose.clipName)
                day.left = minOf(day.left, pose.x)
                day.right = maxOf(day.right, pose.x)
            }
            if (house.count == 2) {
                val a = house.bodies[0].pose
                val b = house.bodies[1].pose
                if (a.surface == b.surface) day.closest = minOf(day.closest, Math.abs(a.x - b.x))
            }
            if (frame % hz == 0) {
                day.trace.add(house.bodies.joinToString("|") { TraceSupport.fingerprint(it.pose) })
            }
        }
        return day
    }

    private fun room(x: Float) = if (x < -4f) 0 else if (x < 196f) 1 else 2

    @Test
    fun theSameHouseLivesTheSameDay() {
        assertEquals(run(listOf(ted, lily), 300).trace, run(listOf(ted, lily), 300).trace)
    }

    @Test
    fun theSameDayAtSixtyAndAHundredAndTwentyHertz() {
        val a = run(listOf(ted, lily), 240, 60)
        val b = run(listOf(ted, lily), 240, 120)
        for (i in 0..1) assertEquals(a.moves[i].take(6), b.moves[i].take(6))
    }

    @Test
    fun theTwoCatsLiveDifferently() {
        val d = run(listOf(ted, lily), 300)
        assertNotEquals(d.moves[0], d.moves[1])
        assertNotEquals(ted.seed, lily.seed)
    }

    @Test
    fun theCatsNeverStandOnEachOther() {
        var closest = Float.MAX_VALUE
        for (seed in 1..8) {
            val d = run(listOf(HouseCat(seed, false), HouseCat(seed * 31 + 7, seed % 2 == 0)), 600)
            closest = minOf(closest, d.closest)
        }
        // they are planned 40 px apart; allow for the sliding stop of a walk
        assertTrue("two cats on the same surface came within $closest px", closest >= 30f)
    }

    @Test
    fun noCatEverLeavesTheHouse() {
        for (seed in 1..8) {
            val d = run(listOf(HouseCat(seed, false), HouseCat(seed + 100, true)), 600)
            assertTrue("seed $seed left: ${d.left}", d.left >= World.HOUSE_MIN_X - 0.5f)
            assertTrue("seed $seed right: ${d.right}", d.right <= World.HOUSE_MAX_X + 0.5f)
        }
    }

    @Test
    fun everyCatVisitsMoreThanOneRoomAndTheHouseIsUsed() {
        val rooms = HashSet<Int>()
        val surfaces = HashSet<SurfaceId>()
        for (seed in 1..6) {
            val d = run(listOf(HouseCat(seed, false), HouseCat(seed * 17 + 3, false)), 1200)
            for (i in 0..1) {
                assertTrue("seed $seed cat $i stayed in ${d.rooms[i]}", d.rooms[i].size >= 2)
                rooms.addAll(d.rooms[i])
                surfaces.addAll(d.surfaces[i])
            }
        }
        assertEquals(setOf(0, 1, 2), rooms)
        for (up in listOf(SurfaceId.BED, SurfaceId.COUCH, SurfaceId.MID, SurfaceId.SILL)) {
            assertTrue("no cat ever went on $up: $surfaces", up in surfaces)
        }
    }

    @Test
    fun aCatDoesEverythingACatDoesInTheHouse() {
        val d = run(listOf(ted, lily), 1500)
        for (clip in
            listOf("idle_sit", "walk", "groom", "stretch", "loaf", "sleep", "eat", "leap")) {
            assertTrue("never showed $clip: ${d.clips}", clip in d.clips)
        }
        val moves = d.moves.flatten().toSet()
        for (move in listOf(Autopilot.Move.TRAVEL, Autopilot.Move.EAT, Autopilot.Move.BED)) {
            assertTrue("never did $move: $moves", move in moves)
        }
    }

    @Test
    fun aHungryCatGoesToTheBowlsMoreOften() {
        var fed = 0
        var hungry = 0
        for (seed in 1..6) {
            fed +=
                run(listOf(HouseCat(seed, false)), 900).moves[0].count { it == Autopilot.Move.EAT }
            hungry +=
                run(listOf(HouseCat(seed, true)), 900).moves[0].count { it == Autopilot.Move.EAT }
        }
        assertTrue("$hungry hungry visits against $fed", hungry > fed)
    }

    @Test
    fun aLoneCatLivesInTheHouseToo() {
        val d = run(listOf(ted), 600)
        assertTrue(d.moves[0].isNotEmpty())
        assertTrue(d.left >= World.HOUSE_MIN_X - 0.5f && d.right <= World.HOUSE_MAX_X + 0.5f)
    }

    @Test
    fun aCatThatStopsOnTheFloorStopsClearOfTheFurnitureInFrontOfIt() {
        // the room draws the cushion, the pouf, the box and the bed's rim in front of a cat on the
        // floor: a cat that rests there would stand half hidden
        val zones = listOf(-148f to -92f, -50f to -18f, 10f to 66f, 266f to 314f)
        for (seed in 1..6) {
            val house = HouseLife(listOf(HouseCat(seed, false), HouseCat(seed + 50, false)))
            repeat(60 * 600) {
                house.step(1f / 60f)
                for (body in house.bodies) {
                    if (!body.isIdle || body.pose.surface != SurfaceId.FLOOR) continue
                    for ((lo, hi) in zones) {
                        assertTrue(
                            "seed $seed rests at ${body.pose.x}, in front of $lo..$hi",
                            body.pose.x <= lo || body.pose.x >= hi,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theCatsStartApartAndOnTheFloor() {
        for (seed in 1..20) {
            val house = HouseLife(listOf(HouseCat(seed, false), HouseCat(seed + 1, false)))
            val a = house.bodies[0].pose
            val b = house.bodies[1].pose
            assertTrue("seed $seed: ${a.x} and ${b.x}", Math.abs(a.x - b.x) >= 50f)
            assertEquals(SurfaceId.FLOOR, a.surface)
        }
    }
}
