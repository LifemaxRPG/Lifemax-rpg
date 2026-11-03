package com.lifemax.rpg.feature.pet.world

/**
 * The cats' world as native saves it through the page (`state.petWorld.blob`, plan 2.3): native is
 * its only author, the page stores it unread inside an envelope it keeps itself (epoch, revision,
 * time, the op that wrote it), so none of those are repeated here. At most
 * [WorldContract.BLOB_MAX_BYTES] as JSON; [WorldBlobJson] reads it strictly and writes it with
 * three decimals.
 *
 * @property imported how the world began: "legacy1" (imported from the web brain) or "fresh".
 * @property cats each living cat's behaviour state, by world id.
 * @property toys the toys lying about the house (at most [WorldContract.MAX_TOYS_ON_FLOOR]).
 * @property outbox commands not yet answered by the page, re-sent after a restart.
 * @property away the "while you were away" story of the last catch-up.
 */
data class WorldBlob(
    val v: Int,
    val imported: String,
    val cats: Map<String, BlobCat>,
    val pair: BlobPair?,
    val toys: List<BlobToy>,
    val gift: BlobGift?,
    val outbox: List<OutboxEntry>,
    val away: Away?,
) {
    companion object {
        val IMPORTED = setOf("legacy1", "fresh")
    }
}

/**
 * One cat's behaviour state.
 *
 * @property act the running activity and [actAt] when it began (epoch ms).
 * @property drives the drives, 0..1.
 * @property spots the world's own memory of places, 0..100, the top six.
 * @property innate the two innate quirks and [counters] the acquired-quirk counters.
 * @property rhythm the owner's visiting rhythm per local hour, 0..100.
 * @property fx the first moments already reported (never reported again).
 * @property rng the three random streams (choice, idle, social) mid-day, so a restore continues.
 * @property day the local day the streams were seeded for, and [wake]/[bed] its schedule (hours).
 * @property voice the voice pitch; [cheerSeen] the last cheer cue ids played.
 */
data class BlobCat(
    val pos: BlobPos,
    val act: String,
    val actAt: Long,
    val drives: Drives,
    val mood: Mood,
    val trust: Trust,
    val spots: Map<String, Int>,
    val innate: List<String>,
    val counters: QuirkCounters,
    val rhythm: List<Int>,
    val fx: List<String>,
    val rng: List<Int>,
    val day: String,
    val wake: Double,
    val bed: Double,
    val voice: Double,
    val cheerSeen: List<Long>,
)

/** Where a cat is: surface [s] (a body SurfaceId key), house [x], depth [z], facing [dir] (±1). */
data class BlobPos(val s: String, val x: Double, val z: Double, val dir: Int)

/**
 * The drives, 0..1: energy, play, curiosity, social, company, comfort, territory, groom (plan 1.3;
 * hunger is the page's and never saved here).
 */
data class Drives(
    val en: Double,
    val pl: Double,
    val cu: Double,
    val so: Double,
    val co: Double,
    val cm: Double,
    val te: Double,
    val gr: Double,
) {
    /** The drives in their saved order. */
    fun toList(): List<Double> = listOf(en, pl, cu, so, co, cm, te, gr)

    companion object {
        val KEYS = listOf("en", "pl", "cu", "so", "co", "cm", "te", "gr")

        fun of(v: List<Double>) = Drives(v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7])
    }
}

/** The pair's last activity ([lastKind] at [lastAt]) and the cool-downs per kind (until, ms). */
data class BlobPair(val lastKind: String?, val lastAt: Long, val cool: Map<String, Long>)

/** A toy on the floor or a surface, held by cat [by] (a world id) or by nobody. */
data class BlobToy(val id: String, val s: String, val x: Double, val z: Double, val by: String?)

/** A gift a cat left at the glass on [day], waiting for the owner's tap. */
data class BlobGift(
    val cat: String,
    val toy: String,
    val x: Double,
    val z: Double,
    val day: String,
)

/** A command waiting for the page's answer, saved as `{op, cmd, args}` (args: its payload). */
data class OutboxEntry(val command: WorldCommand) {
    val op: String
        get() = command.op

    val cmd: String
        get() = command.name
}

/** The catch-up story: since [from] (epoch ms), at most three [lines]. */
data class Away(val from: Long, val lines: List<String>)
