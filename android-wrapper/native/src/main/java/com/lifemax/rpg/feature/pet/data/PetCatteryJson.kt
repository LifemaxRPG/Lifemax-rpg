package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.art.RigMorph
import com.lifemax.rpg.feature.pet.art.RigWire
import com.lifemax.rpg.feature.pet.domain.AdoptKit
import com.lifemax.rpg.feature.pet.domain.CatChip
import com.lifemax.rpg.feature.pet.domain.CatPreview
import com.lifemax.rpg.feature.pet.domain.CatteryCat
import com.lifemax.rpg.feature.pet.domain.CatteryDetail
import com.lifemax.rpg.feature.pet.domain.CatteryEntry
import com.lifemax.rpg.feature.pet.domain.CatteryLitter
import com.lifemax.rpg.feature.pet.domain.CatteryOption
import com.lifemax.rpg.feature.pet.domain.CatteryPick
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.CatteryWords
import com.lifemax.rpg.feature.pet.domain.MemorialEntry
import com.lifemax.rpg.feature.pet.domain.PetCattery
import org.json.JSONArray
import org.json.JSONObject

/*
 * Strict reading of the Cat tab's `cattery` block (pet.cattery.v1, 50-native-pet-cattery.js), like
 * the rest of the projection: a key it does not know or lacks, a value of the wrong kind, a list
 * longer than the page ever sends, a detail that is not the screen the block says is open, throws,
 * and the repository keeps its last good state.
 */

private val WORD_KEYS =
    setOf(
        "cattery",
        "lab",
        "memorial",
        "adoptTitle",
        "adoptHint",
        "adoptButton",
        "mother",
        "father",
        "goal",
        "breed",
        "keep",
        "release",
        "keepAll",
        "releaseAll",
        "home",
        "homeTaken",
        "stray",
        "strayHint",
        "rename",
        "nameLabel",
        "newLitter",
        "adopt",
        "letIn",
        "reopen",
        "none",
        "cancel",
        "save",
        "back",
    )
private val ENTRY_KEYS = setOf("cats", "max", "memorial", "adopt")
private val BLOCK_KEYS = setOf("words", "entry", "view", "detail")
private val PREVIEW_KEYS = setOf("coat", "stage", "rig", "sleep")
private val CHIP_KEYS = setOf("label", "title", "goal")
private val CARD_KEYS = setOf("id", "name", "female", "sub", "chips", "preview", "home", "ask")
private val ROSTER_KEYS =
    setOf("kind", "cats", "count", "max", "canHome", "nameMax", "header", "small")
private val LAB_KEYS =
    setOf(
        "kind",
        "intro",
        "goal",
        "options",
        "best",
        "note",
        "moms",
        "dads",
        "mom",
        "dad",
        "forecast",
        "litter",
    )
private val LITTER_KEYS = setOf("at", "title", "parents", "kits", "full", "fullNote")
private val ADOPT_KEYS =
    setOf(
        "kind",
        "mode",
        "eyebrow",
        "title",
        "body",
        "parents",
        "kits",
        "pick",
        "placeholder",
        "nameMax",
        "memory",
    )
private val KIT_KEYS = setOf("i", "female", "coat", "trait", "persona", "preview")
private val MEMORIAL_KEYS = setOf("kind", "entries", "canReopen")
private val MEMORIAL_ENTRY_KEYS = setOf("name", "coat", "preview", "text")

private val COAT = Regex("[a-z][a-z0-9_]{0,59}")
private val GOAL = Regex("(em|ew|tl|lg|bl|wk)[+-]")
private val ADOPT_MODES = setOf("", "first", "pair")
private const val MAX_ROSTER = 18
private const val MAX_KITS = 6
private const val MAX_ADOPT = 6
private const val MAX_MEMORIAL = 40
private const val MAX_OPTIONS = 12
private const val MAX_CHIPS = 8
private const val MAX_TEXT = 700
private const val MAX_NAME = 40
private const val MAX_CATS = 200
private const val MAX_NAME_FIELD = 40

private fun JSONObject.text(key: String): String =
    str(key).also { require(it.length <= MAX_TEXT) { "$key is too long" } }

private fun JSONObject.name(key: String): String =
    str(key).also { require(it.isNotBlank() && it.length <= MAX_NAME) { "$key is not a name" } }

private fun JSONObject.list(key: String, max: Int): List<JSONObject> {
    val a = get(key) as? JSONArray ?: throw IllegalArgumentException("$key is not a list")
    require(a.length() <= max) { "$key is too long" }
    return (0 until a.length()).map {
        a.get(it) as? JSONObject ?: throw IllegalArgumentException(key)
    }
}

private fun preview(j: JSONObject): CatPreview {
    j.exactly(PREVIEW_KEYS, "preview")
    val coat = j.str("coat")
    require(COAT.matches(coat)) { "coat is not a coat key" }
    val stage = j.int("stage")
    require(stage in -1..5) { "stage out of range" }
    val rig = if (j.isNull("rig")) RigMorph.NEUTRAL else RigWire.decode(j.getJSONObject("rig"))
    return CatPreview(coat, stage, rig, j.bool("sleep"))
}

private fun JSONObject.previewOrNull(key: String): CatPreview? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null
    else preview(get(key) as? JSONObject ?: throw IllegalArgumentException(key))
}

private fun card(j: JSONObject): CatteryCat {
    j.exactly(CARD_KEYS, "cat")
    val chips =
        j.list("chips", MAX_CHIPS).map {
            it.exactly(CHIP_KEYS, "chip")
            CatChip(it.text("label"), it.text("title"), it.bool("goal"))
        }
    return CatteryCat(
        j.str("id").also { require(it.isNotBlank() && it.length <= MAX_NAME) { "id" } },
        j.name("name"),
        j.bool("female"),
        j.text("sub"),
        chips,
        preview(j.get("preview") as? JSONObject ?: throw IllegalArgumentException("preview")),
        j.bool("home"),
        j.text("ask"),
    )
}

private fun picks(j: JSONObject, key: String) =
    j.list(key, MAX_ROSTER).map {
        it.exactly(setOf("key", "name", "home"), "pick")
        CatteryPick(it.name("key"), it.name("name"), it.bool("home"))
    }

private fun roster(j: JSONObject): CatteryDetail.Roster {
    j.exactly(ROSTER_KEYS, "cattery")
    val nameMax = j.int("nameMax")
    require(nameMax in 1..MAX_NAME_FIELD) { "nameMax out of range" }
    return CatteryDetail.Roster(
        j.list("cats", MAX_ROSTER).map(::card),
        j.int("count").also { require(it in 0..MAX_ROSTER) { "count" } },
        j.int("max").also { require(it in 1..MAX_ROSTER) { "max" } },
        j.bool("canHome"),
        nameMax,
        j.text("header"),
        j.text("small"),
    )
}

private fun lab(j: JSONObject): CatteryDetail.Lab {
    j.exactly(LAB_KEYS, "lab")
    val options =
        j.list("options", MAX_OPTIONS).map {
            it.exactly(setOf("value", "label"), "option")
            CatteryOption(
                it.str("value").also { v -> require(GOAL.matches(v)) { "goal" } },
                it.text("label"),
            )
        }
    val goal = j.str("goal")
    require(GOAL.matches(goal)) { "goal is not a goal" }
    val litter =
        if (j.isNull("litter")) null
        else {
            val l = j.get("litter") as? JSONObject ?: throw IllegalArgumentException("litter")
            l.exactly(LITTER_KEYS, "litter")
            CatteryLitter(
                l.long("at").also { require(it >= 0) { "at" } },
                l.text("title"),
                l.text("parents"),
                l.list("kits", MAX_KITS).map(::card),
                l.bool("full"),
                l.text("fullNote"),
            )
        }
    return CatteryDetail.Lab(
        j.text("intro"),
        goal,
        options,
        j.text("best"),
        j.text("note"),
        picks(j, "moms"),
        picks(j, "dads"),
        j.str("mom"),
        j.str("dad"),
        j.text("forecast"),
        litter,
    )
}

private fun adopt(j: JSONObject): CatteryDetail.Adopt {
    j.exactly(ADOPT_KEYS, "adopt")
    val mode = j.str("mode")
    require(mode in ADOPT_MODES) { "mode" }
    val kits =
        j.list("kits", MAX_ADOPT).map {
            it.exactly(KIT_KEYS, "kit")
            AdoptKit(
                it.int("i"),
                it.bool("female"),
                it.text("coat"),
                it.text("trait"),
                it.text("persona"),
                preview(
                    it.get("preview") as? JSONObject ?: throw IllegalArgumentException("preview")
                ),
            )
        }
    require(kits.indices.all { kits[it].index == it }) { "kits are not numbered in order" }
    val pick = j.int("pick")
    require(pick == 0 || pick in kits.indices) { "pick names no kitten" }
    val nameMax = j.int("nameMax")
    require(nameMax in 1..MAX_NAME_FIELD) { "nameMax out of range" }
    return CatteryDetail.Adopt(
        mode,
        j.text("eyebrow"),
        j.text("title"),
        j.text("body"),
        j.text("parents"),
        kits,
        pick,
        j.text("placeholder"),
        nameMax,
        j.text("memory"),
    )
}

private fun memorial(j: JSONObject): CatteryDetail.Memorial {
    j.exactly(MEMORIAL_KEYS, "memorial")
    val entries =
        j.list("entries", MAX_MEMORIAL).map {
            it.exactly(MEMORIAL_ENTRY_KEYS, "entry")
            MemorialEntry(
                it.text("name"),
                it.text("coat"),
                it.previewOrNull("preview"),
                it.text("text"),
            )
        }
    return CatteryDetail.Memorial(entries, j.bool("canReopen"))
}

private fun words(j: JSONObject): CatteryWords {
    j.exactly(WORD_KEYS, "cattery words")
    fun w(k: String) = j.text(k)
    return CatteryWords(
        w("cattery"),
        w("lab"),
        w("memorial"),
        w("adoptTitle"),
        w("adoptHint"),
        w("adoptButton"),
        w("mother"),
        w("father"),
        w("goal"),
        w("breed"),
        w("keep"),
        w("release"),
        w("keepAll"),
        w("releaseAll"),
        w("home"),
        w("homeTaken"),
        w("stray"),
        w("strayHint"),
        w("rename"),
        w("nameLabel"),
        w("newLitter"),
        w("adopt"),
        w("letIn"),
        w("reopen"),
        w("none"),
        w("cancel"),
        w("save"),
        w("back"),
    )
}

/** The `cattery` block of [j] (a projection), or null when the page sent none (an older page). */
internal fun decodeCattery(j: JSONObject): PetCattery? {
    if (!j.has("cattery")) return null
    val b = j.get("cattery") as? JSONObject ?: throw IllegalArgumentException("cattery")
    b.exactly(BLOCK_KEYS, "cattery")
    val entryJ = b.getJSONObject("entry")
    entryJ.exactly(ENTRY_KEYS, "entry")
    val adoptWord = entryJ.str("adopt")
    require(adoptWord == "" || adoptWord == "pair") { "adopt entry" }
    val entry =
        CatteryEntry(
            entryJ.int("cats").also { require(it in 0..MAX_CATS) { "cats" } },
            entryJ.int("max").also { require(it in 1..MAX_ROSTER) { "max" } },
            entryJ.int("memorial").also { require(it in 0..MAX_CATS) { "memorial" } },
            adoptWord == "pair",
        )
    val viewWord = b.str("view")
    val view =
        if (viewWord.isEmpty()) null
        else CatteryScreen.of(viewWord) ?: throw IllegalArgumentException("view")
    val detail =
        if (b.isNull("detail")) null
        else {
            val d = b.get("detail") as? JSONObject ?: throw IllegalArgumentException("detail")
            val kind = d.str("kind")
            val open = view ?: throw IllegalArgumentException("detail with no screen")
            require(kind == open.wire) { "detail is not the open screen's" }
            when (open) {
                CatteryScreen.CATTERY -> roster(d)
                CatteryScreen.LAB -> lab(d)
                CatteryScreen.ADOPT -> adopt(d)
                CatteryScreen.MEMORIAL -> memorial(d)
            }
        }
    require(view == null || detail != null) { "a screen is open with no detail" }
    return PetCattery(words(b.getJSONObject("words")), entry, view, detail)
}
