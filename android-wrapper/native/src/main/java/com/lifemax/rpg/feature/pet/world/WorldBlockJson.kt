package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.world.WorldContract.CAT_ID
import com.lifemax.rpg.feature.pet.world.WorldContract.COAT
import com.lifemax.rpg.feature.pet.world.WorldContract.HOUSE_X_MAX
import com.lifemax.rpg.feature.pet.world.WorldContract.HOUSE_X_MIN
import com.lifemax.rpg.feature.pet.world.WorldContract.TIME_MAX
import com.lifemax.rpg.feature.pet.world.WorldContract.WORD
import com.lifemax.rpg.feature.pet.world.WorldContract.Z_MAX
import org.json.JSONObject

/**
 * Strict reading of the page's `world` block (plan 2.1, built by petWorldBlock): a key unknown or
 * missing, a value of the wrong kind or out of range, a list over its length or a block over
 * [WorldContract.BLOCK_MAX_BYTES] (without `stored`) throws [WorldFormatException]; [WorldFeed]
 * then keeps its last good block. Only the stored blob is read per cat ([WorldBlobJson.read]).
 */
object WorldBlockJson {
    private val TOP =
        setOf("v", "seq", "epoch", "clockOffsetMs", "cats", "pair", "context", "cue", "stored")
    private val CAT =
        setOf(
            "id",
            "slot",
            "name",
            "born",
            "sex",
            "coat",
            "stage",
            "grownAt",
            "ps",
            "morph",
            "bond",
            "zMax",
            "hunger",
            "care",
            "likes",
            "toys",
            "quirks",
        )
    private val LEGACY =
        setOf(
            "src",
            "mood",
            "trust",
            "energy",
            "spots",
            "glass",
            "fx",
            "qk",
            "rhythm",
            "voice",
            "toys",
            "pos",
        )
    private val ID = Regex("[a-z_0-9]{1,32}")
    private val NAME = Regex("[\\s\\S]{1,40}")
    private const val MORPH_MIN = 0.3
    private const val MORPH_MAX = 3.0
    private const val VOICE_MIN = 0.5
    private const val VOICE_MAX = 2.0
    private const val ELEV_MAX = 1000
    private const val COUNT_MAX = 1000
    private const val PCT = 100

    fun decode(j: JSONObject): WorldBlock {
        j.exactKeys(TOP, "world")
        val stored =
            if (j.isNullAt("stored")) null else j.child("stored", setOf("epoch", "rev", "blob"))
        val blob =
            stored?.let { it.get("blob") as? JSONObject ?: fail("type", "blob is not an object") }
        val size = jsonBytes(j) - (blob?.let(::jsonBytes) ?: 0)
        ensure(size <= WorldContract.BLOCK_MAX_BYTES, "size") { "block over its size" }
        val v = j.int("v", 1, Int.MAX_VALUE)
        ensure(v == WorldContract.BLOCK_VERSION, "version") { "an unknown block version" }
        val list = j.list("cats", WorldContract.MAX_CATS)
        val cats =
            (0 until list.length()).map {
                cat(list.get(it) as? JSONObject ?: fail("type", "cat is not an object"))
            }
        ensure(
            cats.map { it.id }.toSet().size == cats.size &&
                cats.map { it.slot }.toSet().size == cats.size,
            "shape",
        ) {
            "a cat twice"
        }
        ensure(cats.map { it.slot } == cats.map { it.slot }.sorted(), "shape") {
            "cats out of slot order"
        }
        val cue = j.child("cue", setOf("fed", "cheer"))
        val read = blob?.let(WorldBlobJson::read)
        return WorldBlock(
            v = v,
            seq = j.whole("seq", 1, Long.MAX_VALUE),
            epoch = j.whole("epoch", 0, Long.MAX_VALUE),
            clockOffsetMs =
                j.whole(
                    "clockOffsetMs",
                    -WorldContract.CLOCK_SHIFT_MAX,
                    WorldContract.CLOCK_SHIFT_MAX,
                ),
            cats = cats,
            pair =
                if (j.isNullAt("pair")) null
                else
                    pair(
                        j.child(
                            "pair",
                            setOf("rel", "level", "lead", "settled", "gainLeft"),
                            setOf("met"),
                        ),
                        cats.size,
                    ),
            context =
                j.child("context", setOf("timer", "restDay")).let {
                    WorldContext(it.flag("timer"), it.flag("restDay"))
                },
            fedCues = fed(cue, cats),
            cheerCues = cheer(cue),
            stored =
                stored?.let {
                    StoredWorld(
                        it.whole("epoch", 0, Long.MAX_VALUE),
                        it.whole("rev", 1, Long.MAX_VALUE),
                        read!!.blob,
                        read.rejectedCats,
                    )
                },
        )
    }

    private fun cat(c: JSONObject): WorldCat {
        c.exactKeys(CAT, "cat", setOf("legacy"))
        val ps = c.child("ps", WorldContract.PERSONA.toSet())
        val morph = c.child("morph", setOf("lg", "bl"))
        val bond = c.child("bond", setOf("lvl", "pct"))
        val h = c.child("hunger", setOf("stage", "lastFed", "vacation", "nextStageAt"))
        val care =
            c.child(
                "care",
                setOf("fedToday", "petsToday", "playsToday", "feedAt", "canPlay", "giftOk"),
            )
        val likes = c.child("likes", setOf("spots", "toys", "favSpot", "favToy"))
        val toys = c.list("toys", WorldContract.TOYS.size)
        val acq = c.child("quirks", setOf("acq")).list("acq", WorldContract.QUIRKS_ACQUIRED.size)
        val born = c.whole("born", 0, TIME_MAX)
        val id = c.text("id", 13, CAT_ID)
        ensure(id == WorldContract.catId(born), "shape") { "id is not the cat's birth" }
        val sex = c.text("sex", 1, Regex("[mf]"))
        return WorldCat(
            id = id,
            slot = c.int("slot", 0, WorldContract.MAX_CATS - 1),
            name =
                c.text("name", WorldContract.MAX_NAME, NAME).also {
                    ensure(it.isNotBlank(), "range") { "name is blank" }
                },
            born = born,
            sex = sex[0],
            coat = c.text("coat", 60, COAT),
            stage = c.int("stage", WorldContract.STAGE_GROWN, WorldContract.STAGE_MAX),
            grownAt = c.whole("grownAt", born, TIME_MAX),
            persona = WorldContract.PERSONA.associateWith { ps.real(it, 0.0, 1.0) },
            legMorph = morph.real("lg", MORPH_MIN, MORPH_MAX),
            bodyMorph = morph.real("bl", MORPH_MIN, MORPH_MAX),
            bond = CatBond(bond.int("lvl", 0, WorldContract.BOND_MAX), bond.int("pct", 0, PCT)),
            zMax = c.int("zMax", 0, Z_MAX.toInt()),
            hunger =
                CatHunger(
                    h.int("stage", 0, WorldContract.HUNGER_MAX),
                    h.whole("lastFed", 0, TIME_MAX),
                    h.flag("vacation"),
                    if (h.isNullAt("nextStageAt")) null else h.whole("nextStageAt", 0, TIME_MAX),
                ),
            care =
                CatCare(
                    care.int("fedToday", 0, COUNT_MAX),
                    care.int("petsToday", 0, COUNT_MAX),
                    care.int("playsToday", 0, COUNT_MAX),
                    care.whole("feedAt", 0, TIME_MAX),
                    care.flag("canPlay"),
                    care.flag("giftOk"),
                ),
            likes =
                CatLikes(
                    WorldBlobJson.spots(likes, "spots", WorldContract.SPOTS.size),
                    shares(likes, "toys", WorldContract.TOYS),
                    nullableId(likes, "favSpot", WorldContract.SPOTS),
                    nullableId(likes, "favToy", WorldContract.TOYS),
                ),
            toys =
                WorldBlobJson.distinct(
                    (0 until toys.length()).map { toys.textAt(it, 16, ID, "toys") },
                    WorldContract.TOYS,
                    "toys",
                ),
            acquired =
                WorldBlobJson.distinct(
                    (0 until acq.length()).map { acq.textAt(it, 32, ID, "acq") },
                    WorldContract.QUIRKS_ACQUIRED,
                    "acq",
                ),
            legacy = if (c.has("legacy")) legacy(c.child("legacy", LEGACY)) else null,
        )
    }

    private fun shares(o: JSONObject, key: String, ids: Set<String>): Map<String, Int> {
        val m = o.map(key, ids.size, ID)
        return m.keys().asSequence().sorted().associateWith {
            ensure(it in ids, "range") { "$key names one the page does not know" }
            m.int(it, 0, PCT)
        }
    }

    private fun nullableId(o: JSONObject, key: String, ids: Set<String>): String? =
        if (o.isNullAt(key)) null
        else
            o.text(key, 16, ID).also {
                ensure(it in ids, "range") { "$key is not one the page knows" }
            }

    private fun legacy(l: JSONObject): CatLegacy {
        val qk = l.child("qk", setOf("inn", "c"))
        val inn = qk.list("inn", WorldContract.MAX_INNATE)
        val fx = l.list("fx", WorldContract.FIRST_MOMENTS.size)
        val rhythm = l.list("rhythm", WorldContract.HOURS)
        ensure(rhythm.length() == WorldContract.HOURS, "shape") { "rhythm is not 24 hours" }
        val toys = l.list("toys", WorldContract.MAX_LEGACY_TOYS)
        val src = l.text("src", 8, WORD)
        ensure(src == "brain" || src == "fresh", "range") { "src is not a known source" }
        return CatLegacy(
            src = src,
            mood = WorldBlobJson.mood(l.child("mood", setOf("v", "a"))),
            trust = WorldBlobJson.trust(l.child("trust", setOf("fam", "tru", "sec", "aut"))),
            energy = l.real("energy", 0.0, 1.0),
            spots = WorldBlobJson.spots(l, "spots", WorldContract.MAX_BLOB_SPOTS),
            glass = l.int("glass", 0, PCT),
            fx =
                WorldBlobJson.distinct(
                    (0 until fx.length()).map { fx.textAt(it, 32, ID, "fx") },
                    WorldContract.FIRST_MOMENTS,
                    "fx",
                ),
            innate =
                WorldBlobJson.distinct(
                    (0 until inn.length()).map { inn.textAt(it, 32, ID, "inn") },
                    WorldContract.QUIRKS_INNATE,
                    "inn",
                ),
            counters = WorldBlobJson.counters(qk.child("c", setOf("n", "t", "g", "k"))),
            rhythm =
                (0 until WorldContract.HOURS).map {
                    rhythm.wholeAt(it, 0, PCT.toLong(), "rhythm").toInt()
                },
            voice = l.real("voice", VOICE_MIN, VOICE_MAX),
            toys =
                (0 until toys.length()).map {
                    val t = toys.objAt(it, setOf("id", "x", "z"), "toy")
                    LegacyToy(
                        t.text("id", 16, ID).also {
                            ensure(it in WorldContract.TOYS, "range") {
                                "toy is not one the page knows"
                            }
                        },
                        t.int("x", HOUSE_X_MIN.toInt(), HOUSE_X_MAX.toInt()),
                        t.real("z", 0.0, Z_MAX),
                    )
                },
            pos =
                if (l.isNullAt("pos")) null
                else
                    l.child("pos", setOf("x", "z", "elev")).let {
                        LegacyPos(
                            it.int("x", HOUSE_X_MIN.toInt(), HOUSE_X_MAX.toInt()),
                            it.real("z", 0.0, Z_MAX),
                            it.int("elev", 0, ELEV_MAX),
                        )
                    },
        )
    }

    private fun pair(p: JSONObject, cats: Int): WorldPair {
        ensure(cats == WorldContract.MAX_CATS, "shape") { "a pair needs two cats" }
        return WorldPair(
            p.real("rel", 0.0, 1.0),
            p.int("level", 0, WorldContract.PAIR_LEVEL_MAX),
            p.int("lead", -1, 1),
            p.flag("settled"),
            p.real("gainLeft", 0.0, 1.0),
            if (p.has("met")) p.flag("met") else null,
        )
    }

    /**
     * The meals the page served lately, oldest first, each naming a cat of the house and a new id.
     */
    private fun fed(cue: JSONObject, cats: List<WorldCat>): List<FedCue> {
        val list = cue.list("fed", WorldContract.MAX_FED_CUES)
        val slots = cats.map { it.slot }.toSet()
        val cues =
            (0 until list.length()).map {
                val c = list.objAt(it, setOf("cat", "id", "ago"), "fed cue")
                FedCue(
                    c.int("cat", 0, WorldContract.MAX_CATS - 1),
                    c.whole("id", 1, Long.MAX_VALUE),
                    c.whole("ago", 0, TIME_MAX),
                )
            }
        ensure(cues.map { it.id } == cues.map { it.id }.sorted().distinct(), "shape") {
            "fed cues out of order"
        }
        return cues.filter { it.cat in slots }
    }

    private fun cheer(cue: JSONObject): List<CheerCue> {
        val list = cue.list("cheer", WorldContract.MAX_CHEER_CUES)
        val cues =
            (0 until list.length()).map {
                val c = list.objAt(it, setOf("id", "kind", "ago"), "cheer cue")
                val kind = c.text("kind", 16, WORD)
                ensure(kind in WorldContract.CHEER_KINDS, "range") { "an unknown cheer" }
                CheerCue(c.whole("id", 1, Long.MAX_VALUE), kind, c.whole("ago", 0, TIME_MAX))
            }
        ensure(cues.map { it.id } == cues.map { it.id }.sorted().distinct(), "shape") {
            "cheer cues out of order"
        }
        return cues
    }
}
