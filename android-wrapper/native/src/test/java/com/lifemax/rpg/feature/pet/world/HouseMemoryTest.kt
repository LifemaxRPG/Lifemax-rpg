package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.room.HouseMap
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The open rooms are known at the app's start ([HouseMemory], WP-H7): written when a block opens a
 * room, read and handed to the [HouseMap] before the first projection, never closed again.
 */
class HouseMemoryTest {
    private val dir =
        File.createTempFile("house", "dir").apply {
            delete()
            mkdirs()
        }
    private val store = File(dir, HouseMemory.FILE)

    @Before
    fun clean() {
        HouseMemory.reset()
        HouseMap.reset()
    }

    @After
    fun back() {
        HouseMemory.reset()
        HouseMap.reset()
        dir.deleteRecursively()
    }

    private fun ids() = HouseMap.current.rooms.map { it.id }

    @Test
    fun withNoFileTheHouseIsTheThreeFirstRooms() {
        assertTrue(HouseMemory.restore(store).isEmpty())
        assertEquals(listOf("living", "bedroom", "kitchen"), ids())
        assertFalse(store.exists())
    }

    @Test
    fun aRoomTheBlockOpensIsKeptForTheNextStart() {
        HouseMemory.restore(store)
        HouseMemory.remember(setOf("sun"))
        HouseMemory.remember(setOf("sun")) // nothing new: no second write
        assertTrue(store.exists())
        // the next start of the app: a new process knows the sunroom before any block
        HouseMemory.reset()
        HouseMap.reset()
        assertEquals(setOf("sun"), HouseMemory.restore(store))
        assertEquals(listOf("living", "bedroom", "kitchen", "sun"), ids())
    }

    @Test
    fun roomsOnlyJoin() {
        HouseMemory.restore(store)
        HouseMemory.remember(setOf("sun", "study"))
        HouseMemory.remember(setOf("study")) // a block that lists fewer closes nothing
        HouseMemory.reset()
        assertEquals(setOf("sun", "study"), HouseMemory.restore(store))
    }

    @Test
    fun aDamagedFileIsNoRoomsAndNothingIsWrittenBeforeRestore() {
        HouseMemory.remember(setOf("sun")) // no file chosen yet
        assertFalse(store.exists())
        store.writeText("{not json")
        assertTrue(HouseMemory.restore(store).isEmpty())
        assertEquals(3, HouseMap.current.rooms.size)
    }

    @Test
    fun aRoomCutOffFromTheHouseIsKeptAndOpensWhenItsNeighbourDoes() {
        HouseMemory.restore(store)
        HouseMemory.remember(setOf("bath"))
        HouseMemory.reset()
        assertEquals(setOf("bath"), HouseMemory.restore(store))
        assertEquals(3, HouseMap.current.rooms.size) // no study yet: the bathroom is cut off
        HouseMemory.remember(setOf("study"))
        HouseMemory.reset()
        HouseMemory.restore(store)
        assertEquals(5, HouseMap.current.rooms.size)
    }
}
