package com.lifemax.rpg.feature.pet.art

/**
 * A bounded least-recently-used store of composed frames: [capacity] pictures of [cells] pixels,
 * each found by a 64-bit key. Looking a key up and re-using the picture of the stalest key both
 * allocate nothing; a picture's pixel array is created the first time its slot is used, so a cache
 * that never fills never costs its whole size.
 *
 * Not thread safe: the screen draws from one thread.
 */
class RigCache(val capacity: Int, private val cells: Int) {
    private val keys = LongArray(capacity)
    private val used = BooleanArray(capacity)
    private val stamp = LongArray(capacity)
    private val pictures = arrayOfNulls<IntArray>(capacity)
    private var clock = 0L

    /** The slot holding [key], made the most recent, or -1. */
    fun find(key: Long): Int {
        for (i in 0 until capacity) {
            if (used[i] && keys[i] == key) {
                stamp[i] = ++clock
                return i
            }
        }
        return -1
    }

    /**
     * The slot to fill for [key], which was not found: a free one, else the least recently used.
     * The caller must write the slot's [pixels] (they hold whatever was there before).
     */
    fun claim(key: Long): Int {
        var slot = -1
        var oldest = Long.MAX_VALUE
        for (i in 0 until capacity) {
            if (!used[i]) {
                slot = i
                break
            }
            if (stamp[i] < oldest) {
                oldest = stamp[i]
                slot = i
            }
        }
        keys[slot] = key
        used[slot] = true
        stamp[slot] = ++clock
        return slot
    }

    /** The pixels of [slot] (created on first use). */
    fun pixels(slot: Int): IntArray = pictures[slot] ?: IntArray(cells).also { pictures[slot] = it }

    /** How many slots hold a picture. */
    val size: Int
        get() = used.count { it }
}
