package com.lifemax.rpg.feature.pet.room

import org.json.JSONObject

/**
 * One room of the house, as `pet/room/house.json` lists it. Rooms stand side by side on one floor;
 * a room is [RoomLayout.BACKDROP_WIDTH] wide and starts at world x [x0].
 *
 * @property id the room's key in the manifest and the art (`living`, `sun`).
 * @property name the room's name for the player.
 * @property x0 world x of the room's left edge.
 * @property cut the world x where the next room starts for [HouseMap.roomOf] (the web's split for
 *   the three first rooms, which is not the art's seam); null for the last room.
 * @property strollLo where a cat that travels here may stop, the left end.
 * @property strollHi the right end of that stretch.
 * @property window the world x of the window's own origin (the sky is painted from there), or null
 *   for a room with no window.
 * @property floorLight whether the window's light lies on this room's floor.
 * @property core whether the room is always part of the house (the first three); every other room
 *   opens when the player unlocks it.
 * @property unlockBond the bond level (0 Stray .. 5 Devoted) at which the page opens the room, or
 *   null for a core room. The page decides; this is what it tells the player and the tests.
 */
internal class RoomDef(
    val id: String,
    val name: String,
    val x0: Int,
    val cut: Int?,
    val strollLo: Float,
    val strollHi: Float,
    val window: Int?,
    val floorLight: Boolean,
    val core: Boolean,
    val unlockBond: Int?,
)

/**
 * The house as data: the rooms in order from left to right and what the engine derives from them
 * (the house's edges, which room an x is in, where the windows are, how far a cat may walk). Built
 * from `house.json` ([parse]); [all] lists every room the game has, [rooms] the ones that are open:
 * the first three always, any other once it is unlocked (outward from the core, so the open rooms
 * are always one unbroken stretch).
 *
 * Only open rooms are drawn, walked in and shown by the camera, so with nothing unlocked the house
 * is the three rooms it has always been, pixel for pixel. [select] changes the open rooms; the art
 * and the scenes read the map when they are made, so the first time the art is loaded the choice is
 * [lock]ed for the rest of the process (a room that opens later shows from the next start), and the
 * cats, the camera and the art always agree on the house.
 */
internal class HouseMap
private constructor(
    /** Every room the game has, left to right. */
    val all: List<RoomDef>,
    /** The rooms that are open, left to right. */
    val rooms: List<RoomDef>,
    private val wallMargin: Int,
) {
    /** World x of the house's left edge. */
    val x0: Int = rooms.first().x0

    /** The house's width in pixels. */
    val width: Int = rooms.size * RoomLayout.BACKDROP_WIDTH

    /** World x of the house's right edge. */
    val x1: Int = x0 + width

    /** The farthest left and right a cat's feet may stand. */
    val walkMin: Float = (x0 + wallMargin).toFloat()
    val walkMax: Float = (x1 - wallMargin).toFloat()

    /** The windows' sky offsets (world x), left to right. */
    val windows: IntArray = rooms.mapNotNull { it.window }.toIntArray()

    /** The windows whose light lies on the floor, left to right. */
    val floorLights: IntArray = rooms.filter { it.floorLight }.mapNotNull { it.window }.toIntArray()

    /** The index in [rooms] of the room with [id], or -1 when it is not open. */
    fun indexOf(id: String): Int = rooms.indexOfFirst { it.id == id }

    /** The room the world x [x] is in, as an index into [rooms]. */
    fun roomOf(x: Float): Int {
        for (i in 0 until rooms.size - 1) {
            val cut = rooms[i].cut ?: continue
            if (x < cut) return i
        }
        return rooms.size - 1
    }

    /** The map with the core rooms and those of [unlocked] that join the open stretch. */
    fun select(unlocked: Set<String>): HouseMap {
        val first = all.indexOfFirst { it.core }
        val last = all.indexOfLast { it.core }
        var lo = first
        while (lo > 0 && all[lo - 1].id in unlocked) lo--
        var hi = last
        while (hi < all.size - 1 && all[hi + 1].id in unlocked) hi++
        return HouseMap(all, all.subList(lo, hi + 1), wallMargin)
    }

    companion object {
        /** Reads `house.json`: the rooms touch, left to right, and the core ones are together. */
        fun parse(text: String): HouseMap {
            val json = JSONObject(text)
            require(json.getInt("roomWidth") == RoomLayout.BACKDROP_WIDTH) { "house: room width" }
            require(json.getInt("roomHeight") == RoomLayout.HEIGHT) { "house: room height" }
            val list = json.getJSONArray("rooms")
            val rooms =
                List(list.length()) {
                    val o = list.getJSONObject(it)
                    val stroll = o.getJSONArray("stroll")
                    RoomDef(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        x0 = o.getInt("x0"),
                        cut = if (o.isNull("cut")) null else o.getInt("cut"),
                        strollLo = stroll.getDouble(0).toFloat(),
                        strollHi = stroll.getDouble(1).toFloat(),
                        window = if (o.isNull("window")) null else o.getInt("window"),
                        floorLight = o.getBoolean("floorLight"),
                        core = o.getBoolean("core"),
                        unlockBond = if (o.has("unlockBond")) o.getInt("unlockBond") else null,
                    )
                }
            for (i in 1 until rooms.size) {
                require(rooms[i].x0 == rooms[i - 1].x0 + RoomLayout.BACKDROP_WIDTH) {
                    "house: ${rooms[i].id} does not touch ${rooms[i - 1].id}"
                }
            }
            require(rooms.map { it.id }.toSet().size == rooms.size) { "house: duplicate room" }
            val first = rooms.indexOfFirst { it.core }
            val last = rooms.indexOfLast { it.core }
            require(first >= 0 && (first..last).all { rooms[it].core }) { "house: core rooms" }
            return HouseMap(rooms, rooms.subList(first, last + 1), json.getInt("wallMargin"))
        }

        private val shipped: HouseMap by lazy {
            val bytes =
                HouseMap::class.java.getResourceAsStream("/pet/room/house.json")?.use {
                    it.readBytes()
                } ?: error("missing pet/room/house.json")
            parse(String(bytes, Charsets.UTF_8))
        }

        @Volatile private var selected: HouseMap? = null
        @Volatile private var locked = false

        /** The map the engine reads: the core rooms, plus those [select]ed. */
        val current: HouseMap
            get() = selected ?: shipped

        /** Every room the game has, open or not. */
        val everything: List<RoomDef>
            get() = shipped.all

        /**
         * Opens the core rooms and those of [unlocked] (ids this game does not know are ignored);
         * what is built afterwards sees them. Once the art has been loaded the map is [lock]ed and
         * this changes nothing and answers false, unless [force] (tests).
         */
        fun select(unlocked: Set<String>, force: Boolean = false): Boolean {
            if (locked && !force) return false
            selected = shipped.select(unlocked)
            return true
        }

        /** Keeps the map as it is for the rest of the process: the art has been made from it. */
        fun lock() {
            locked = true
        }

        /** Back to the core rooms only, unlocked for [select] (tests restore this). */
        fun reset() {
            selected = null
            locked = false
        }
    }
}
