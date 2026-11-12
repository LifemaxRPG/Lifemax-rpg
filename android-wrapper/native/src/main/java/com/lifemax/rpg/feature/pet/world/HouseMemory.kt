package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.room.HouseMap
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The rooms the cats have opened, kept in a small file so the house is known when the app starts,
 * before the page sends its first projection (docs/specs/CAT_HOUSE_EXPANSION_PLAN.md WP-H7).
 *
 * The page decides which rooms open (`house.rooms` in the `world` block) and [WorldFeed] tells the
 * [HouseMap]; but the map is locked as soon as the room art is made, and a screen can make it
 * before the first block arrives. So [restore] runs at app start (when the first Cat screen plugin
 * loads), opens the rooms the last run knew of, and [remember] writes every room a block opens.
 * Rooms never close, so the file only grows; one this game does not know is ignored by the map. A
 * missing or damaged file is the three first rooms. Never throws.
 */
object HouseMemory {
    /** The file's name in the app's files directory. */
    const val FILE = "pet-house.json"

    private const val KEY = "rooms"

    @Volatile private var file: File? = null
    private val known = LinkedHashSet<String>()

    /**
     * Starts keeping the rooms in [store], opens those it names ([HouseMap.select]) and answers
     * them. Later calls (the second Cat screen) start nothing new and answer the same.
     */
    @Synchronized
    fun restore(store: File): Set<String> {
        if (file == null) {
            file = store
            known += read(store)
        }
        HouseMap.select(known)
        return LinkedHashSet(known)
    }

    /**
     * The page's [rooms] are open: they join the ones kept, and the file is written when that
     * changed. Does nothing before [restore] chose a file.
     */
    @Synchronized
    fun remember(rooms: Set<String>) {
        val store = file ?: return
        if (!known.addAll(rooms)) return
        write(store)
    }

    /** Forgets the file and the rooms (tests). */
    @Synchronized
    fun reset() {
        file = null
        known.clear()
    }

    private fun read(store: File): Set<String> =
        try {
            val list = JSONObject(store.readText()).getJSONArray(KEY)
            List(list.length()) { list.getString(it) }.toSet()
        } catch (e: Exception) { // no file yet, damaged, or not ours: nothing is known
            emptySet()
        }

    private fun write(store: File) {
        try {
            val temp = File(store.parentFile, store.name + ".tmp")
            temp.writeText(JSONObject().put(KEY, JSONArray(known.toList())).toString())
            if (!temp.renameTo(store)) store.writeText(temp.readText())
        } catch (e: Exception) { // a full disk: the page sends the rooms again with the next block
        }
    }
}
