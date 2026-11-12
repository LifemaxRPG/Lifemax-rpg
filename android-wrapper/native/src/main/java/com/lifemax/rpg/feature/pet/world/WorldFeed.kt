package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.room.HouseMap
import org.json.JSONObject

/**
 * Where the page's `world` blocks land, from either cat screen (the Cat tab and the Overview cards
 * both carry it): the newest block wins by `seq` (the page raises it with the clock, so a reloaded
 * page's blocks are newer too); a block whose `epoch` differs from the one held means the page
 * replaced the world, which the world host reads from [latest]. A malformed block is refused and
 * counted by its error code while the last good one is kept. In CB0 nothing reads [latest] yet: the
 * screens decode the block and draw what they always drew; CB2's world host takes it from here.
 * Never throws, logs nothing.
 */
class WorldFeed(
    /**
     * Told the rooms a block's `house.rooms` opens whenever a block becomes the latest: the app's
     * one feed opens them in the cat house ([HouseMap.select]); the feeds of tests tell nobody (the
     * house is process-wide).
     */
    private val onHouse: ((Set<String>) -> Unit)? = null
) {
    @Volatile
    var latest: WorldBlock? = null
        private set

    private val errorCounts = LinkedHashMap<String, Int>()

    /** Malformed blocks refused so far, by error code (`shape`, `type`, `range`, `size`, ...). */
    val errors: Map<String, Int>
        @Synchronized get() = LinkedHashMap(errorCounts)

    /**
     * Takes the `world` block of a projection, when it has one; answers whether it became the
     * latest. A block no newer than the one held (a lower or equal seq) is ignored.
     */
    @Synchronized
    fun offer(block: JSONObject?): Boolean {
        if (block == null) return false
        val decoded =
            try {
                WorldBlockJson.decode(block)
            } catch (e: WorldFormatException) {
                errorCounts.merge(e.code, 1, Int::plus)
                return false
            } catch (e: RuntimeException) { // org.json's own, or anything else unforeseen
                errorCounts.merge("json", 1, Int::plus)
                return false
            }
        val held = latest
        if (held != null && decoded.seq <= held.seq) return false
        onHouse?.invoke(decoded.houseRooms)
        latest = decoded
        return true
    }

    /** The `world` block of a projection, which may have none (a screen of an older page). */
    fun offerFrom(projection: JSONObject): Boolean = offer(projection.optJSONObject(KEY))

    companion object {
        /** The projection key the page sends the block under. */
        const val KEY = "world"

        /** One feed for the process: both screens draw the same cats. */
        val shared = WorldFeed { rooms ->
            HouseMap.select(rooms) // before the art is made
            HouseMemory.remember(rooms) // and known at the next start, before any block
        }
    }
}
