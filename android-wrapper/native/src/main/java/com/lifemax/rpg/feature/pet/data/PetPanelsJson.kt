package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetBar
import com.lifemax.rpg.feature.pet.domain.PetDna
import com.lifemax.rpg.feature.pet.domain.PetGeneRow
import com.lifemax.rpg.feature.pet.domain.PetGenome
import com.lifemax.rpg.feature.pet.domain.PetInner
import com.lifemax.rpg.feature.pet.domain.PetLikeRow
import com.lifemax.rpg.feature.pet.domain.PetLikes
import com.lifemax.rpg.feature.pet.domain.PetLine
import com.lifemax.rpg.feature.pet.domain.PetLocus
import com.lifemax.rpg.feature.pet.domain.PetMemory
import com.lifemax.rpg.feature.pet.domain.PetMind
import com.lifemax.rpg.feature.pet.domain.PetMorph
import com.lifemax.rpg.feature.pet.domain.PetMorphRow
import com.lifemax.rpg.feature.pet.domain.PetPair
import com.lifemax.rpg.feature.pet.domain.PetPairCard
import com.lifemax.rpg.feature.pet.domain.PetPanelWords
import com.lifemax.rpg.feature.pet.domain.PetPanels
import com.lifemax.rpg.feature.pet.domain.PetQuirk
import com.lifemax.rpg.feature.pet.domain.PetRank
import com.lifemax.rpg.feature.pet.domain.PetSwitchCat
import com.lifemax.rpg.feature.pet.domain.PetToy
import com.lifemax.rpg.feature.pet.domain.PetToys
import org.json.JSONArray
import org.json.JSONObject

/*
 * Strict reading of the Cat tab's `panels` block (pet.panels.v1, 50-native-pet-panels.js), like the
 * rest of the projection: an object with a key it does not know or lacks, a value of the wrong
 * kind, a list longer than the page ever sends or a percentage out of range throws, and the
 * repository keeps its last good state. A part the page could not build arrives as null.
 */

private val WORD_KEYS =
    setOf(
        "together",
        "switcher",
        "likes",
        "inner",
        "dna",
        "toys",
        "toysSub",
        "sparks",
        "buy",
        "bought",
        "genomeShow",
        "genomeHide",
        "moreText",
    )
private val PANEL_KEYS = setOf("words", "mind", "likes", "inner", "dna", "pair", "toys")
private val BAR_KEYS = setOf("label", "pct", "word")
private val LIKE_KEYS = setOf("name", "pct", "fav")
private val LIKES_KEYS =
    setOf("lead", "body", "placesTitle", "toysTitle", "places", "toys", "noPlaces")
private val INNER_KEYS =
    setOf(
        "lead",
        "body",
        "quirksTitle",
        "memoriesTitle",
        "threads",
        "quirks",
        "memories",
        "stats",
        "comfort",
    )
private val LINE_KEYS = setOf("bold", "rest")
private val GENE_KEYS = setOf("label", "gt", "text")
private val CAT_KEYS = setOf("name", "coat")
private val RANK_ROW_KEYS = setOf("name", "pct", "word")

/** The most rows any list in the panels has: loci are the longest (a cat's genome is 40 or so). */
private const val MAX_ROWS = 16
private const val MAX_LOCI = 128
private const val GENOME_COLUMNS = 4
private const val MAX_TOYS = 24
private const val MAX_TEXT = 4000
private const val MAX_SPARKS = 100_000_000L

internal fun decodePanels(j: JSONObject): PetPanels {
    j.exactly(PANEL_KEYS, "panels")
    return PetPanels(
        words = j.obj("words", WORD_KEYS).words(),
        mind = j.objOrNull("mind", setOf("style", "mood", "awayTitle", "text", "away"))?.mind(),
        likes = j.objOrNull("likes", LIKES_KEYS)?.likes(),
        inner = j.objOrNull("inner", INNER_KEYS)?.inner(),
        dna = j.objOrNull("dna", DNA_KEYS)?.dna(),
        pair = j.objOrNull("pair", setOf("card", "focus", "cats"))?.pair(),
        toys = j.objOrNull("toys", setOf("sparks", "items"))?.toys(),
    )
}

private fun JSONObject.words() =
    PetPanelWords(
        str("together"),
        str("switcher"),
        str("likes"),
        str("inner"),
        str("dna"),
        str("toys"),
        str("toysSub"),
        str("sparks"),
        str("buy"),
        str("bought"),
        str("genomeShow"),
        str("genomeHide"),
        str("moreText"),
    )

/** A string of the page's words, kept to a sane length. */
private fun JSONObject.text(key: String): String =
    str(key).also { require(it.length <= MAX_TEXT) { "$key is too long" } }

private fun JSONObject.percent(key: String): Int =
    int(key).also { require(it in 0..100) { "$key is not a percentage" } }

/** The objects of the array at [key], each holding exactly [keys], at most [max] of them. */
private fun <T> JSONObject.rows(
    key: String,
    keys: Set<String>,
    max: Int = MAX_ROWS,
    read: JSONObject.() -> T,
): List<T> {
    val list = get(key) as? JSONArray ?: throw IllegalArgumentException("$key is not a list")
    require(list.length() <= max) { "$key has too many rows" }
    return (0 until list.length()).map {
        val row = list.get(it) as? JSONObject ?: throw IllegalArgumentException("$key row")
        row.exactly(keys, key)
        row.read()
    }
}

private fun JSONObject.strings(key: String): List<String> {
    val list = get(key) as? JSONArray ?: throw IllegalArgumentException("$key is not a list")
    require(list.length() <= MAX_ROWS) { "$key has too many rows" }
    return (0 until list.length()).map {
        (list.get(it) as? String ?: throw IllegalArgumentException("$key row")).also { s ->
            require(s.length <= MAX_TEXT) { "$key row is too long" }
        }
    }
}

private fun JSONObject.mind(): PetMind {
    val style = obj("style", setOf("id", "name"))
    return PetMind(
        style.str("id"),
        style.str("name"),
        str("mood"),
        text("awayTitle"),
        text("text"),
        text("away"),
    )
}

private fun JSONObject.bar() = PetBar(text("label"), percent("pct"), text("word"))

private fun JSONObject.likes(): PetLikes {
    val row: JSONObject.() -> PetLikeRow = { PetLikeRow(text("name"), percent("pct"), bool("fav")) }
    return PetLikes(
        text("lead"),
        text("body"),
        text("placesTitle"),
        text("toysTitle"),
        rows("places", LIKE_KEYS, read = row),
        rows("toys", LIKE_KEYS, read = row),
        text("noPlaces"),
    )
}

private fun JSONObject.inner() =
    PetInner(
        text("lead"),
        text("body"),
        text("quirksTitle"),
        text("memoriesTitle"),
        rows("threads", BAR_KEYS) { bar() },
        rows("quirks", setOf("name", "source", "text")) {
            PetQuirk(text("name"), text("source"), text("text"))
        },
        rows("memories", setOf("text", "zone", "ago")) {
            PetMemory(text("text"), text("zone"), text("ago"))
        },
        text("stats"),
        text("comfort"),
    )

private val DNA_KEYS =
    setOf(
        "title",
        "female",
        "sex",
        "karyotype",
        "coat",
        "sterile",
        "sub",
        "rows",
        "carries",
        "hidden",
        "temperament",
        "persona",
        "mutations",
        "genome",
        "morph",
        "genomeOpen",
    )

private fun JSONObject.gene() = PetGeneRow(text("label"), text("gt"), text("text"))

private fun JSONObject.dna(): PetDna {
    val temperament = obj("temperament", setOf("title", "intro", "reads"))
    val mutations = objOrNull("mutations", setOf("lead", "list"))
    val genome = obj("genome", setOf("title", "head", "count", "note", "loci"))
    val head = genome.strings("head")
    require(head.size == GENOME_COLUMNS) { "the genome table has four columns" }
    val loci =
        genome.rows("loci", setOf("locus", "q", "gene", "chr", "pos", "mom", "dad"), MAX_LOCI) {
            PetLocus(text("locus"), text("gene"), text("chr"), int("pos"), text("mom"), text("dad"))
        }
    val count = genome.int("count")
    require(count in 0..MAX_LOCI && loci.size <= count) { "genome count out of range" }
    return PetDna(
        title = text("title"),
        female = bool("female"),
        sex = text("sex"),
        karyotype = text("karyotype"),
        coat = text("coat"),
        sterile = text("sterile"),
        sub = text("sub"),
        rows = rows("rows", GENE_KEYS) { gene() },
        carries = objOrNull("carries", LINE_KEYS)?.line(),
        hidden = objOrNull("hidden", LINE_KEYS)?.line(),
        morph = objOrNull("morph", setOf("title", "intro", "rows", "tail"))?.morph(),
        temperamentTitle = temperament.text("title"),
        temperamentIntro = temperament.text("intro"),
        temperamentReads = temperament.text("reads"),
        persona = rows("persona", BAR_KEYS) { bar() },
        mutationsLead = mutations?.text("lead") ?: "",
        mutations = mutations?.text("list") ?: "",
        genome =
            PetGenome(
                genome.text("title"),
                head,
                count,
                genome.text("note"),
                loci,
                bool("genomeOpen"),
            ),
    )
}

private fun JSONObject.line() = PetLine(text("bold"), text("rest"))

private fun JSONObject.morph() =
    PetMorph(
        text("title"),
        text("intro"),
        rows("rows", setOf("label", "pct", "note")) {
            PetMorphRow(text("label"), percent("pct"), text("note"))
        },
        obj("tail", GENE_KEYS).gene(),
    )

private fun JSONObject.pair(): PetPair {
    val focus = int("focus")
    val cats = rows("cats", CAT_KEYS, 2) { PetSwitchCat(text("name"), text("coat")) }
    require(cats.size == 2 && focus in 0..1) { "a pair has two cats" }
    return PetPair(obj("card", CARD_KEYS).card(), focus, cats)
}

private val CARD_KEYS =
    setOf("title", "a", "b", "level", "pct", "text", "awayTitle", "away", "counts", "rank", "last")

private fun JSONObject.card() =
    PetPairCard(
        title = text("title"),
        first = text("a"),
        second = text("b"),
        level = text("level"),
        pct = percent("pct"),
        text = text("text"),
        awayTitle = text("awayTitle"),
        away = text("away"),
        counts = text("counts"),
        rank = objOrNull("rank", setOf("title", "line", "lead", "settled", "rows"))?.rank(),
        last = text("last"),
    )

private fun JSONObject.rank(): PetRank {
    val line = obj("line", LINE_KEYS).line()
    require(int("lead") in -1..1) { "lead names no cat" }
    return PetRank(
        text("title"),
        line,
        bool("settled"),
        rows("rows", RANK_ROW_KEYS, 2) { PetBar(text("name"), percent("pct"), text("word")) },
    )
}

private fun JSONObject.toys(): PetToys {
    val sparks = long("sparks")
    require(sparks in 0..MAX_SPARKS) { "sparks out of range" }
    val items =
        rows(
            "items",
            setOf("id", "name", "unlocked", "word", "bought", "price", "affordable"),
            MAX_TOYS,
        ) {
            val price = int("price")
            require(price >= 0) { "a price is not negative" }
            PetToy(
                text("id"),
                text("name"),
                bool("unlocked"),
                text("word"),
                bool("bought"),
                price,
                bool("affordable"),
            )
        }
    return PetToys(sparks.toInt(), items)
}
