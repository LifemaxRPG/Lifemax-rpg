package com.lifemax.rpg.feature.pet.brain

import com.lifemax.rpg.feature.pet.body.World
import com.lifemax.rpg.feature.pet.room.HouseMap
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cats and the rooms that open as they grow close (docs/specs/CAT_HOUSE_EXPANSION_PLAN.md):
 * their places are in the table but chosen only while their room is open, and with every room open
 * the live brain takes the cats across the house, into rooms the old house did not have.
 */
class NewRoomsBrainTest {
    @After fun back() = HouseMap.reset()

    private val newPlaces = listOf("sun patch", "study rug", "bath mat")

    @Test
    fun thePlacesOfClosedRoomsAreNeverChosen() {
        HouseMap.reset()
        assertEquals(newPlaces, Spots.all.filter { !it.open }.map { it.id })
        val trace = BehaviourMeter.record(day = 0, startHour = 7.0, hours = 8.0)
        for (cat in 0..1) {
            val stats = trace.stats(cat, 7.0, 15.0)
            assertTrue("cat $cat visited ${stats.places}", stats.places.none { it in newPlaces })
            assertTrue("cat $cat rooms ${stats.rooms}", stats.rooms.all { it in 0..2 })
        }
    }

    @Test
    fun everyRoomsPlaceIsOpenWhenItsRoomIs() {
        HouseMap.select(setOf("sun", "study", "bath"), force = true)
        assertTrue(Spots.all.all { it.open })
        for (spot in Spots.all.filter { it.id in newPlaces }) {
            assertTrue(
                "${spot.id} is on the open floor",
                spot.x in World.HOUSE_MIN_X..World.HOUSE_MAX_X,
            )
        }
        assertEquals(5, Spots.SUN_PATCH.room)
        assertEquals(1, Spots.STUDY_RUG.room)
        assertEquals(0, Spots.BATH_MAT.room)
    }

    @Test
    fun withTheWholeHouseOpenTheCatsRoamIntoTheNewRooms() {
        HouseMap.select(setOf("sun", "study", "bath"), force = true)
        val days = (0..1).map { BehaviourMeter.record(day = it, startHour = 7.0, hours = 14.0) }
        val rooms = HashSet<Int>()
        val places = HashSet<String>()
        for (t in days) for (cat in 0..1) {
            val s = t.stats(cat, 8.0, 21.0)
            rooms += s.rooms
            places += s.places
        }
        println(
            "six rooms, two days: rooms $rooms, new places ${places.filter { it in newPlaces }}"
        )
        assertTrue("rooms visited: $rooms", rooms.size >= 4)
        assertTrue("a new room was reached: $rooms", rooms.any { it == 0 || it == 1 || it == 5 })
    }
}
