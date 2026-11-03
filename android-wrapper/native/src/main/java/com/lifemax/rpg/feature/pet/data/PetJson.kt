package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.core.data.pageTokens
import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.art.RigWire
import com.lifemax.rpg.feature.pet.domain.PetBond
import com.lifemax.rpg.feature.pet.domain.PetCare
import com.lifemax.rpg.feature.pet.domain.PetCatLook
import com.lifemax.rpg.feature.pet.domain.PetFavourites
import com.lifemax.rpg.feature.pet.domain.PetFedCue
import com.lifemax.rpg.feature.pet.domain.PetFeed
import com.lifemax.rpg.feature.pet.domain.PetGrowth
import com.lifemax.rpg.feature.pet.domain.PetMeal
import com.lifemax.rpg.feature.pet.domain.PetNotice
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.domain.PetWords
import org.json.JSONObject

/*
 * Strict reading of the Cat tab's projection (pet.projection.v1): an object with a key it does not
 * know, a key missing, or a value of the wrong kind (a number where words belong: org.json would
 * quietly turn it into a string) throws, so a page that changed its projection is refused instead
 * of drawn wrong. The repository keeps the last good state when this throws.
 */

private val TOP_KEYS =
    setOf(
        "protocol",
        "webSessionId",
        "sequence",
        "notice",
        "tokens",
        "light",
        "cats",
        "focus",
        "name",
        "eyebrow",
        "hearts",
        "mood",
        "bond",
        "grow",
        "lastMeal",
        "mealsToday",
        "days",
        "favs",
        "hint",
        "feed",
        "care",
        "together",
        "words",
        "cue",
    )

private val CAT_KEYS = setOf("name", "coat", "stage", "weak")

/** What a coat key may look like: the four shipped names and the page's `dna_...` keys. */
private val COAT = Regex("[a-z][a-z0-9_]{0,59}")

/** The most cats a house has, the most fed cues a projection carries, and the sanest name limit. */
private const val MAX_CATS = 2
private const val MAX_CUES = 4
private const val MAX_NAME = 40

internal fun JSONObject.str(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not a string")

internal fun JSONObject.bool(key: String): Boolean =
    get(key) as? Boolean ?: throw IllegalArgumentException("$key is not a boolean")

/** A whole number (counts, ids). */
internal fun JSONObject.long(key: String): Long {
    val n = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    require(n !is Double && n !is Float || n.toDouble() % 1.0 == 0.0) { "$key is not whole" }
    return n.toLong()
}

internal fun JSONObject.int(key: String): Int = long(key).toInt()

/** The object at [key], which must hold exactly [keys]. */
internal fun JSONObject.obj(key: String, keys: Set<String>): JSONObject =
    (get(key) as? JSONObject ?: throw IllegalArgumentException("$key is not an object")).also {
        it.exactly(keys, key)
    }

/** The object at [key] with exactly [keys], or null where the page sends null. */
internal fun JSONObject.objOrNull(key: String, keys: Set<String>): JSONObject? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else obj(key, keys)
}

/**
 * Throws unless this object has every one of [keys] and no key but those and [optional]: a key
 * missing or one more is malformed.
 */
internal fun JSONObject.exactly(
    keys: Set<String>,
    what: String = "projection",
    optional: Set<String> = emptySet(),
) {
    val have = keys().asSequence().toSet()
    require(have.containsAll(keys) && (keys + optional).containsAll(have)) {
        "$what keys differ: unknown ${have - keys - optional}, missing ${keys - have}"
    }
}

internal fun decodePet(j: JSONObject, session: String, sequence: Long): PetProjection {
    j.exactly(TOP_KEYS, optional = TOUCH_KEYS + "panels")
    val cats = j.getJSONArray("cats")
    require(cats.length() in 1..MAX_CATS) { "A house has one or two cats" }
    val looks =
        (0 until cats.length()).map {
            val c = cats.get(it) as? JSONObject ?: throw IllegalArgumentException("cat")
            // `rig` rides on the screen's pet.rig.v1: absent from a page that has not been told
            c.exactly(if (c.has("rig")) CAT_KEYS + "rig" else CAT_KEYS, "cat")
            PetCatLook(
                c.name("name"),
                c.coat(),
                c.int("stage").also(::stage),
                c.bool("weak"),
                if (c.has("rig")) RigWire.decode(c.getJSONObject("rig")) else RigMorph.NEUTRAL,
            )
        }
    val focus = j.int("focus")
    require(focus in looks.indices) { "focus names no cat" }
    val hearts = j.int("hearts")
    require(hearts in 0..5) { "hearts out of range" }
    val bond = j.obj("bond", setOf("pct", "text"))
    val care = j.obj("care", CARE_KEYS)
    val nameMax = care.int("nameMax")
    require(nameMax in 1..MAX_NAME) { "nameMax out of range" }
    val cue = j.obj("cue", setOf("fed"))
    return PetProjection(
        session = session,
        sequence = sequence,
        cats = looks,
        focus = focus,
        name = j.name("name"),
        eyebrow = j.str("eyebrow"),
        hearts = hearts,
        mood = j.str("mood"),
        bond = PetBond(bond.percent("pct"), bond.str("text")),
        growth = j.objOrNull("grow", setOf("label", "day", "pct", "text"))?.growth(),
        lastMeal =
            j.obj("lastMeal", setOf("value", "label")).let {
                PetMeal(it.str("value"), it.str("label"))
            },
        mealsToday = j.str("mealsToday"),
        daysTogether = j.str("days"),
        favourites =
            j.obj("favs", setOf("place", "toy")).let {
                PetFavourites(it.str("place"), it.str("toy"))
            },
        hint = j.str("hint"),
        feed =
            j.obj("feed", setOf("label", "wait")).let { PetFeed(it.str("label"), it.bool("wait")) },
        care =
            PetCare(
                care.str("how"),
                care.str("bond"),
                care.str("vacationHint"),
                care.bool("vacation"),
                care.name("name", nameMax),
                nameMax,
            ),
        together = j.str("together"),
        words = j.obj("words", WORD_KEYS).words(),
        fedCues = fedCues(cue, looks.size),
        notice =
            j.objOrNull("notice", setOf("id", "text"))?.let {
                PetNotice(it.long("id"), it.str("text"))
            },
        tokens = j.getJSONObject("tokens").pageTokens(),
        light = j.bool("light"),
        panels =
            if (j.has("panels"))
                decodePanels(
                    j.get("panels") as? JSONObject ?: throw IllegalArgumentException("panels")
                )
            else null,
        touch = decodeTouch(j, looks.size),
    )
}

private val CARE_KEYS = setOf("how", "bond", "vacationHint", "vacation", "name", "nameMax")

private val WORD_KEYS =
    setOf(
        "room",
        "play",
        "blink",
        "blinkHint",
        "careTitle",
        "vacationTitle",
        "nameLabel",
        "rename",
        "moreTitle",
        "moreText",
    )

private fun stage(value: Int) = require(value in -1..5) { "growth stage out of range" }

private fun JSONObject.name(key: String, max: Int = MAX_NAME): String =
    str(key).also { require(it.isNotBlank() && it.length <= max) { "$key is not a name" } }

private fun JSONObject.coat(): String =
    str("coat").also { require(COAT.matches(it)) { "coat is not a coat key" } }

private fun JSONObject.percent(key: String): Int =
    int(key).also { require(it in 0..100) { "$key is not a percentage" } }

private fun JSONObject.growth() = PetGrowth(str("label"), str("day"), percent("pct"), str("text"))

private fun JSONObject.words() =
    PetWords(
        str("room"),
        str("play"),
        str("blink"),
        str("blinkHint"),
        str("careTitle"),
        str("vacationTitle"),
        str("nameLabel"),
        str("rename"),
        str("moreTitle"),
        str("moreText"),
    )

/** The meals the page served lately, oldest first, each naming a cat of the house and a new id. */
private fun fedCues(cue: JSONObject, cats: Int): List<PetFedCue> {
    val list = cue.getJSONArray("fed")
    require(list.length() <= MAX_CUES) { "too many fed cues" }
    val cues =
        (0 until list.length()).map {
            val c = list.get(it) as? JSONObject ?: throw IllegalArgumentException("cue")
            c.exactly(setOf("cat", "id", "ago"), "cue")
            val cat = c.int("cat")
            val id = c.long("id")
            val ago = c.long("ago")
            require(cat in 0 until MAX_CATS && id > 0 && ago >= 0) { "a fed cue out of range" }
            PetFedCue(cat, id, ago)
        }
    require(cues.map { it.id }.let { ids -> ids == ids.sorted().distinct() }) {
        "fed cues are not in order"
    }
    return cues.filter { it.cat < cats }
}
