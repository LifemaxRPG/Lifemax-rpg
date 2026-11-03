package com.lifemax.rpg.feature.pet.brain

import java.util.Locale

/**
 * One pick, as the explain ring keeps it (plan 1.5 "Explain"): at simulated [atMs] the cat began
 * [activity] at [place] because of [reason]; [top] are the best candidates with their scores, the
 * chosen one among them (a reflex or a resume has none).
 */
class Why(
    val atMs: Long,
    val activity: String,
    val place: String,
    val reason: String,
    val top: List<Pair<String, Double>>,
) {
    /** One line: `sleep @ bed (bed hours) [sleep@bed 2.41, loaf@sofa 0.52]`. */
    override fun toString(): String {
        val ranks =
            top.joinToString(", ") { (n, s) -> n + " " + String.format(Locale.ROOT, "%.2f", s) }
        return "$activity @ $place ($reason)" + if (ranks.isEmpty()) "" else " [$ranks]"
    }
}

/**
 * The last [capacity] picks of one cat, oldest first (the day viewer, tests and a dev overlay read
 * it): every move can be traced to numbers. Bounded: a new pick drops the oldest.
 */
class ExplainRing(private val capacity: Int = CAPACITY) {
    private val ring = arrayOfNulls<Why>(capacity)
    private var next = 0

    /** How many picks were recorded in all (not only those kept). */
    var total = 0L
        private set

    /** Records [why]. */
    fun add(why: Why) {
        ring[next] = why
        next = (next + 1) % capacity
        total++
    }

    /** The newest pick, or null. */
    val last: Why?
        get() = ring[(next - 1 + capacity) % capacity]

    /** The kept picks, oldest first. */
    fun entries(): List<Why> {
        val out = ArrayList<Why>(capacity)
        for (i in 0 until capacity) ring[(next + i) % capacity]?.let { out += it }
        return out
    }

    companion object {
        const val CAPACITY = 32
    }
}
