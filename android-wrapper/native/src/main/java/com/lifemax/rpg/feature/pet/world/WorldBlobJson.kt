package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.body.SurfaceId
import com.lifemax.rpg.feature.pet.world.WorldContract.CAT_ID
import com.lifemax.rpg.feature.pet.world.WorldContract.DAY
import com.lifemax.rpg.feature.pet.world.WorldContract.HOUSE_X_MAX
import com.lifemax.rpg.feature.pet.world.WorldContract.HOUSE_X_MIN
import com.lifemax.rpg.feature.pet.world.WorldContract.OP_ID
import com.lifemax.rpg.feature.pet.world.WorldContract.TIME_MAX
import com.lifemax.rpg.feature.pet.world.WorldContract.WORD
import com.lifemax.rpg.feature.pet.world.WorldContract.Z_MAX
import org.json.JSONArray
import org.json.JSONObject

/**
 * The world blob on the wire (plan 2.3). [encode] writes it as native saves it (three decimals),
 * [decode] reads native's own blob strictly (anything off throws), and [read] reads a stored blob
 * as the world restores it: the envelope strictly, each cat on its own, so one damaged cat is
 * derived afresh while the other keeps its world; a newer version or a damaged envelope is null.
 */
object WorldBlobJson {
    val TOP_KEYS = setOf("v", "imported", "cats", "pair", "toys", "gift", "outbox", "away")
    private val CAT_KEYS =
        setOf(
            "pos",
            "act",
            "actAt",
            "dr",
            "mood",
            "trust",
            "spots",
            "qk",
            "rhythm",
            "fx",
            "rng",
            "day",
            "sched",
            "voice",
            "cheerSeen",
        )
    private val SURFACES = SurfaceId.entries.map { it.key }.toSet()
    private val SPOT = Regex("[a-z_]{1,16}")
    private const val VOICE_MIN = 0.5
    private const val VOICE_MAX = 2.0
    private const val HOUR_MAX = 36.0
    private const val SPOT_MAX = 100L
    private const val COUNTER_MAX = 1_000_000L
    private const val ID_MAX = Long.MAX_VALUE / 2

    /** A stored blob as read: [blob] null when it cannot be used at all; [rejectedCats] derived. */
    data class Read(val blob: WorldBlob?, val rejectedCats: Set<String>, val error: String?)

    fun decode(j: JSONObject): WorldBlob = top(j, strict = true).blob!!

    fun read(j: JSONObject): Read =
        try {
            top(j, strict = false)
        } catch (e: WorldFormatException) {
            Read(null, emptySet(), e.code)
        }

    private fun top(j: JSONObject, strict: Boolean): Read {
        ensure(jsonBytes(j) <= WorldContract.BLOB_MAX_BYTES, "size") { "blob over its size" }
        j.exactKeys(TOP_KEYS, "blob")
        ensure(j.int("v", 1, Int.MAX_VALUE) <= WorldContract.BLOB_VERSION, "version") {
            "a newer blob"
        }
        val imported = j.text("imported", 8, WORD)
        ensure(imported in WorldBlob.IMPORTED, "range") { "imported is not a known start" }
        val raw = j.map("cats", WorldContract.MAX_CATS, CAT_ID)
        val cats = LinkedHashMap<String, BlobCat>()
        val rejected = mutableSetOf<String>()
        for (id in raw.keys().asSequence().sorted()) {
            try {
                cats[id] = cat(raw.get(id) as? JSONObject ?: fail("type", "cat is not an object"))
            } catch (e: WorldFormatException) {
                if (strict) throw e
                rejected += id
            }
        }
        val toys = j.list("toys", WorldContract.MAX_TOYS_ON_FLOOR)
        val outbox = j.list("outbox", WorldContract.MAX_OUTBOX)
        val blob =
            WorldBlob(
                v = WorldContract.BLOB_VERSION,
                imported = imported,
                cats = cats,
                pair =
                    if (j.isNullAt("pair")) null else pair(j.child("pair", setOf("last", "cool"))),
                toys =
                    (0 until toys.length()).map {
                        toy(toys.objAt(it, setOf("id", "s", "x", "z", "by"), "toy"))
                    },
                gift =
                    if (j.isNullAt("gift")) null
                    else gift(j.child("gift", setOf("cat", "toy", "x", "z", "day"))),
                outbox =
                    (0 until outbox.length()).map {
                        entry(outbox.objAt(it, setOf("op", "cmd", "args"), "outbox"))
                    },
                away =
                    if (j.isNullAt("away")) null else away(j.child("away", setOf("from", "lines"))),
            )
        ensure(blob.outbox.map { it.op }.toSet().size == blob.outbox.size, "shape") {
            "an op twice in the outbox"
        }
        return Read(blob, rejected, null)
    }

    private fun cat(c: JSONObject): BlobCat {
        c.exactKeys(CAT_KEYS, "cat")
        val pos = c.child("pos", setOf("s", "x", "z", "dir"))
        val dr = c.child("dr", Drives.KEYS.toSet())
        val qk = c.child("qk", setOf("inn", "c"))
        val inn = qk.list("inn", WorldContract.MAX_INNATE)
        val rhythm = c.list("rhythm", WorldContract.HOURS)
        ensure(rhythm.length() == WorldContract.HOURS, "shape") { "rhythm is not 24 hours" }
        val fx = c.list("fx", WorldContract.FIRST_MOMENTS.size)
        val rng = c.list("rng", WorldContract.RNG_STREAMS)
        ensure(rng.length() == WorldContract.RNG_STREAMS, "shape") { "rng is not three streams" }
        val seen = c.list("cheerSeen", WorldContract.MAX_CHEER_SEEN)
        val sched = c.child("sched", setOf("wake", "bed"))
        return BlobCat(
            pos =
                BlobPos(
                    pos.oneOf("s", SURFACES),
                    pos.real("x", HOUSE_X_MIN, HOUSE_X_MAX),
                    pos.real("z", 0.0, Z_MAX),
                    dir(pos),
                ),
            act = c.text("act", 32, WORD),
            actAt = c.whole("actAt", 0, TIME_MAX),
            drives = Drives.of(Drives.KEYS.map { dr.real(it, 0.0, 1.0) }),
            mood = mood(c.child("mood", setOf("v", "a"))),
            trust = trust(c.child("trust", setOf("fam", "tru", "sec", "aut"))),
            spots = spots(c, "spots", WorldContract.MAX_BLOB_SPOTS),
            innate =
                distinct(
                    (0 until inn.length()).map { inn.textAt(it, 32, WORD, "inn") },
                    WorldContract.QUIRKS_INNATE,
                    "inn",
                ),
            counters = counters(qk.child("c", setOf("n", "t", "g", "k"))),
            rhythm =
                (0 until WorldContract.HOURS).map {
                    rhythm.wholeAt(it, 0, SPOT_MAX, "rhythm").toInt()
                },
            fx =
                distinct(
                    (0 until fx.length()).map { fx.textAt(it, 32, WORD, "fx") },
                    WorldContract.FIRST_MOMENTS,
                    "fx",
                ),
            rng =
                (0 until WorldContract.RNG_STREAMS).map {
                    rng.wholeAt(it, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong(), "rng").toInt()
                },
            day = c.text("day", 10, DAY),
            wake = sched.real("wake", 0.0, HOUR_MAX),
            bed = sched.real("bed", 0.0, HOUR_MAX),
            voice = c.real("voice", VOICE_MIN, VOICE_MAX),
            cheerSeen = (0 until seen.length()).map { seen.wholeAt(it, 1, ID_MAX, "cheerSeen") },
        )
    }

    private fun dir(pos: JSONObject): Int =
        pos.int("dir", -1, 1).also { ensure(it != 0, "range") { "dir is not a side" } }

    internal fun mood(m: JSONObject) = Mood(m.real("v", -1.0, 1.0), m.real("a", -1.0, 1.0))

    internal fun trust(t: JSONObject) =
        Trust(
            t.real("fam", 0.0, 1.0),
            t.real("tru", 0.0, 1.0),
            t.real("sec", 0.0, 1.0),
            t.real("aut", 0.0, 1.0),
        )

    internal fun counters(c: JSONObject) =
        QuirkCounters(
            c.int("n", 0, COUNTER_MAX.toInt()),
            c.int("t", 0, COUNTER_MAX.toInt()),
            c.int("g", 0, COUNTER_MAX.toInt()),
            c.int("k", 0, COUNTER_MAX.toInt()),
        )

    /** A map of the page's places to 0..100, at most [max] of them. */
    internal fun spots(o: JSONObject, key: String, max: Int): Map<String, Int> {
        val m = o.map(key, max, SPOT)
        return m.keys().asSequence().sorted().associateWith {
            ensure(it in WorldContract.SPOTS, "range") {
                "$key names a place the page does not know"
            }
            m.int(it, 0, SPOT_MAX.toInt())
        }
    }

    /** [ids] each one of [allowed], none twice. */
    internal fun distinct(ids: List<String>, allowed: Set<String>, what: String): List<String> {
        ensure(ids.all { it in allowed } && ids.toSet().size == ids.size, "range") {
            "$what has an unknown or repeated id"
        }
        return ids
    }

    private fun JSONObject.oneOf(key: String, allowed: Set<String>): String =
        text(key, 32, WORD).also { ensure(it in allowed, "range") { "$key is not a known one" } }

    private fun pair(p: JSONObject): BlobPair {
        val last = if (p.isNullAt("last")) null else p.child("last", setOf("kind", "at"))
        val cool = p.map("cool", SocialReport.KINDS.size, WORD)
        return BlobPair(
            last?.oneOf("kind", SocialReport.KINDS),
            last?.whole("at", 0, TIME_MAX) ?: 0L,
            cool.keys().asSequence().sorted().associateWith {
                ensure(it in SocialReport.KINDS, "range") { "cool names an unknown kind" }
                cool.whole(it, 0, TIME_MAX)
            },
        )
    }

    private fun toy(t: JSONObject) =
        BlobToy(
            t.oneOf("id", WorldContract.TOYS),
            t.oneOf("s", SURFACES),
            t.real("x", HOUSE_X_MIN, HOUSE_X_MAX),
            t.real("z", 0.0, Z_MAX),
            if (t.isNullAt("by")) null else t.text("by", 13, CAT_ID),
        )

    private fun gift(g: JSONObject) =
        BlobGift(
            g.text("cat", 13, CAT_ID),
            g.oneOf("toy", WorldContract.TOYS),
            g.real("x", HOUSE_X_MIN, HOUSE_X_MAX),
            g.real("z", 0.0, Z_MAX),
            g.text("day", 10, DAY),
        )

    private fun entry(e: JSONObject): OutboxEntry {
        val op = e.text("op", 80, OP_ID)
        val cmd = e.text("cmd", 16, WORD)
        ensure(cmd in QUEUED, "range") { "outbox holds a command that is never queued" }
        val args = e.get("args") as? JSONObject ?: fail("type", "args is not an object")
        val command = WorldCommandJson.decode(cmd, args)
        ensure(command.op == op, "shape") { "outbox op differs from its command's" }
        return OutboxEntry(command)
    }

    /**
     * The commands the outbox may hold: the brain's reports (never the owner's care, never a
     * world).
     */
    private val QUEUED = setOf("pet", "play", "gift", "moment", "social", "dwell")

    private fun away(a: JSONObject): Away {
        val lines = a.list("lines", WorldContract.MAX_AWAY_LINES)
        return Away(
            a.whole("from", 0, TIME_MAX),
            (0 until lines.length()).map {
                lines.textAt(it, WorldContract.MAX_AWAY_LINE, LINE, "lines")
            },
        )
    }

    private val LINE = Regex("[^\\u0000-\\u001f]{1,160}")

    /** The blob as native saves it. */
    fun encode(b: WorldBlob): JSONObject {
        val cats = JSONObject()
        b.cats.forEach { (id, c) -> cats.put(id, cat(c)) }
        return JSONObject()
            .put("v", b.v)
            .put("imported", b.imported)
            .put("cats", cats)
            .put("pair", b.pair?.let(::pair) ?: JSONObject.NULL)
            .put("toys", JSONArray(b.toys.map(::toy)))
            .put("gift", b.gift?.let(::gift) ?: JSONObject.NULL)
            .put(
                "outbox",
                JSONArray(
                    b.outbox.map {
                        JSONObject()
                            .put("op", it.op)
                            .put("cmd", it.cmd)
                            .put("args", WorldCommandJson.encode(it.command))
                    }
                ),
            )
            .put(
                "away",
                b.away?.let { JSONObject().put("from", it.from).put("lines", JSONArray(it.lines)) }
                    ?: JSONObject.NULL,
            )
    }

    private fun cat(c: BlobCat): JSONObject {
        val dr = JSONObject()
        Drives.KEYS.zip(c.drives.toList()).forEach { (k, v) -> dr.put(k, round3(v)) }
        return JSONObject()
            .put(
                "pos",
                JSONObject()
                    .put("s", c.pos.s)
                    .put("x", round3(c.pos.x))
                    .put("z", round3(c.pos.z))
                    .put("dir", c.pos.dir),
            )
            .put("act", c.act)
            .put("actAt", c.actAt)
            .put("dr", dr)
            .put("mood", JSONObject().put("v", round3(c.mood.v)).put("a", round3(c.mood.a)))
            .put(
                "trust",
                JSONObject()
                    .put("fam", round3(c.trust.fam))
                    .put("tru", round3(c.trust.tru))
                    .put("sec", round3(c.trust.sec))
                    .put("aut", round3(c.trust.aut)),
            )
            .put("spots", JSONObject(c.spots as Map<*, *>))
            .put(
                "qk",
                JSONObject()
                    .put("inn", JSONArray(c.innate))
                    .put(
                        "c",
                        JSONObject()
                            .put("n", c.counters.n)
                            .put("t", c.counters.t)
                            .put("g", c.counters.g)
                            .put("k", c.counters.k),
                    ),
            )
            .put("rhythm", JSONArray(c.rhythm))
            .put("fx", JSONArray(c.fx))
            .put("rng", JSONArray(c.rng))
            .put("day", c.day)
            .put("sched", JSONObject().put("wake", round3(c.wake)).put("bed", round3(c.bed)))
            .put("voice", round3(c.voice))
            .put("cheerSeen", JSONArray(c.cheerSeen))
    }

    private fun pair(p: BlobPair): JSONObject =
        JSONObject()
            .put(
                "last",
                p.lastKind?.let { JSONObject().put("kind", it).put("at", p.lastAt) }
                    ?: JSONObject.NULL,
            )
            .put("cool", JSONObject(p.cool as Map<*, *>))

    private fun toy(t: BlobToy): JSONObject =
        JSONObject()
            .put("id", t.id)
            .put("s", t.s)
            .put("x", round3(t.x))
            .put("z", round3(t.z))
            .put("by", t.by ?: JSONObject.NULL)

    private fun gift(g: BlobGift): JSONObject =
        JSONObject()
            .put("cat", g.cat)
            .put("toy", g.toy)
            .put("x", round3(g.x))
            .put("z", round3(g.z))
            .put("day", g.day)
}
