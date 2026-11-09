package com.lifemax.rpg.feature.pet.world

import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Test driver: the page's side of the cat world contract without a WebView. It keeps the page's
 * part of the cats (identity, bond, hunger, today's counters, likes, the album's flags, the pair)
 * and the stored world, builds the `world` block the way petWorldBlock does, and answers every
 * command with the page's rules (50-native-cat-world.js for `world`, 15-runtime.js and plan 2.2 for
 * the rest): the payload read strictly ([WorldCommandJson]), ok only when "saved", a repeat of a
 * landed op "Already recorded." with nothing done twice, a refused save taken back ([saveFails]),
 * and for `world` the version, living cats, size and epoch/revision checks. Later phases drive the
 * whole loop (brain, outbox, blob) against it on the JVM.
 */
class FakePage(var now: Long = WorldFixtures.NOW) {
    /** One cat as the page keeps it (the fields the contract reads). */
    data class Cat(
        val born: Long,
        var name: String,
        var bondPts: Int = BOND_AT[3],
        var lastFed: Long,
        var vacation: Boolean = false,
        var vacationSince: Long = 0,
        var day: String = "",
        var fedToday: Int = 0,
        var petsToday: Int = 0,
        var playsToday: Int = 0,
        var lastGiftDay: String = "",
        var gifts: Int = 0,
        var alive: Boolean = true,
        val fx: MutableSet<String> = sortedSetOf(),
        val acquired: MutableList<String> = mutableListOf(),
        val spotHours: MutableMap<String, Double> = sortedMapOf(),
        val toyCredit: MutableMap<String, Double> = sortedMapOf(),
        /** The web brain the page reads for the import (petWorldLegacy); null: a fresh one. */
        var legacy: JSONObject? = null,
        /** The growth stage the page sends (-1 grown, 0..5 a kitten). */
        var stage: Int = -1,
    ) {
        val id: String
            get() = WorldContract.catId(born)
    }

    /**
     * The pair's record (state.petPair): friendship, the day's gain, counts, standoff wins, whether
     * the two have [met] and the [last] moment's kind.
     */
    data class Pair(
        var rel: Double = REL_START,
        var day: String = "",
        var gain: Double = 0.0,
        val n: MutableMap<String, Int> = sortedMapOf(),
        val wins: MutableMap<String, Int> = sortedMapOf(),
        var met: Boolean = false,
        var last: String? = null,
    )

    /** The page records the pair's moments (`social`, pet.social.v1): its block names `met`. */
    var social = true

    /** state.petWorld: the envelope the page keeps around native's blob. */
    data class Stored(val epoch: Long, val rev: Long, val op: String, val blob: JSONObject)

    val cats = mutableListOf<Cat>()
    var pair: Pair? = null
    var epoch = 0L
    var stored: Stored? = null

    /**
     * While true every save is refused (storage full): commands answer not ok and change nothing.
     */
    var saveFails = false

    /** Today is one of the owner's rest days (the block's `context.restDay`). */
    var restDay = false

    /** Saves that reached storage. */
    var saves = 0
        private set

    private val landed = ArrayDeque<String>()

    /**
     * The meals served lately, as the page keeps them for its `fed` cues (NativePet.fedCues): each
     * cat's slot, an id rising per meal (from 1 again after [reload]) and when.
     */
    val meals = ArrayList<Triple<Int, Long, Long>>()
    private var mealSeq = 0L

    /** The page reloaded: its meal ids start again from one (the meals it listed are gone). */
    fun reload() {
        meals.clear()
        mealSeq = 0L
    }

    /** A meal for living cat [c], served now by any of the page's routes (petFeed). */
    fun served(c: Cat) {
        meals += Triple(living().indexOf(c).coerceAtMost(1), ++mealSeq, now)
        while (meals.size > WorldContract.MAX_FED_CUES) meals.removeAt(0)
    }

    private fun fedJson(): JSONArray {
        val list = JSONArray()
        for ((cat, id, at) in meals) list.put(
            JSONObject().put("cat", cat).put("id", id).put("ago", (now - at).coerceAtLeast(0))
        )
        return list
    }

    private var seq = 0L
    private var seqKey = ""

    val today: String
        get() = Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate().toString()

    fun adopt(cat: Cat): Cat =
        cat.also {
            cats += it
            if (living().size == 2 && pair == null) pair = Pair()
        }

    fun living() = cats.filter { it.alive }

    fun level(c: Cat) = BOND_AT.indexOfLast { c.bondPts >= it }

    fun hours(c: Cat) =
        ((if (c.vacation) c.vacationSince else now) - c.lastFed).coerceAtLeast(0) / HOUR.toDouble()

    fun hungerStage(c: Cat) =
        STAGE_AT_H.indexOfFirst { hours(c) < it }.let { if (it < 0) STAGE_AT_H.size else it }

    /**
     * A backup restore: the save comes back as it was and the world epoch rises (applyBackup's
     * wrapper).
     */
    fun restoreBackup() {
        epoch++
    }

    // ---- the block ----

    /** The `world` block as petWorldBlock builds it from this page's state. */
    fun block(): JSONObject {
        val keep = stored
        val held = keep?.blob?.optJSONObject("cats")
        val list = JSONArray()
        living().forEach { c ->
            list.put(catJson(c, cats.indexOf(c).coerceAtMost(1), held?.has(c.id) != true))
        }
        val b =
            JSONObject()
                .put("v", 1)
                .put("seq", 0)
                .put("epoch", epoch)
                .put("clockOffsetMs", 0)
                .put("cats", list)
                .put("pair", pairJson() ?: JSONObject.NULL)
                .put("context", JSONObject().put("timer", false).put("restDay", restDay))
                .put(
                    "cue",
                    JSONObject()
                        .put("fed", JSONArray(meals.map { it.second }))
                        .put("cheer", JSONArray()),
                )
                .put(
                    "stored",
                    keep?.let {
                        JSONObject()
                            .put("epoch", it.epoch)
                            .put("rev", it.rev)
                            .put("blob", JSONObject(it.blob.toString()))
                    } ?: JSONObject.NULL,
                )
        // as petWorldBlock: a cue's age alone is no change, only which meals it lists
        val key = b.toString()
        b.getJSONObject("cue").put("fed", fedJson())
        if (key != seqKey) {
            seqKey = key
            seq = maxOf(seq + 1, now)
        }
        return b.put("seq", seq)
    }

    private fun catJson(c: Cat, slot: Int, legacy: Boolean): JSONObject {
        val d = c.day == today
        val stage = hungerStage(c)
        val persona = JSONObject()
        WorldContract.PERSONA.forEach { persona.put(it, 0.5) }
        return JSONObject()
            .put("id", c.id)
            .put("slot", slot)
            .put("name", c.name)
            .put("born", c.born)
            .put("sex", "m")
            .put("coat", "ginger")
            .put("stage", c.stage)
            .put("grownAt", c.born + GROW_MS)
            .put("ps", persona)
            .put("morph", JSONObject().put("lg", 1).put("bl", 1))
            .put("bond", JSONObject().put("lvl", level(c)).put("pct", 0))
            .put("zMax", if (stage >= 3) 35 else 84)
            .put(
                "hunger",
                JSONObject()
                    .put("stage", stage)
                    .put("lastFed", c.lastFed)
                    .put("vacation", c.vacation)
                    .put(
                        "nextStageAt",
                        if (c.vacation || stage >= STAGE_AT_H.size) JSONObject.NULL
                        else c.lastFed + STAGE_AT_H[stage] * HOUR,
                    ),
            )
            .put(
                "care",
                JSONObject()
                    .put("fedToday", if (d) c.fedToday else 0)
                    .put("petsToday", if (d) c.petsToday else 0)
                    .put("playsToday", if (d) c.playsToday else 0)
                    .put("feedAt", c.lastFed + FEED_GAP_H * HOUR)
                    .put("canPlay", (if (d) c.playsToday else 0) < PLAYS_PER_DAY)
                    .put("giftOk", giftOk(c)),
            )
            .put(
                "likes",
                JSONObject()
                    .put("spots", shares(c.spotHours))
                    .put("toys", shares(c.toyCredit))
                    .put("favSpot", JSONObject.NULL)
                    .put("favToy", JSONObject.NULL),
            )
            .put("toys", JSONArray(WorldContract.TOYS.sorted()))
            .put("quirks", JSONObject().put("acq", JSONArray(c.acquired)))
            .also {
                if (legacy)
                    it.put(
                        "legacy",
                        c.legacy?.let { l -> JSONObject(l.toString()) } ?: freshLegacy(),
                    )
            }
    }

    private fun shares(m: Map<String, Double>): JSONObject {
        val top = maxOf(1.0, m.values.maxOrNull() ?: 1.0)
        return JSONObject(m.mapValues { Math.round(it.value / top * PCT).toInt() } as Map<*, *>)
    }

    private fun freshLegacy() =
        JSONObject()
            .put("src", "fresh")
            .put("mood", JSONObject().put("v", 0.2).put("a", -0.1))
            .put(
                "trust",
                JSONObject().put("fam", 0.4).put("tru", 0.4).put("sec", 0.4).put("aut", 0.7),
            )
            .put("energy", 0.5)
            .put("spots", JSONObject())
            .put("glass", 0)
            .put("fx", JSONArray())
            .put(
                "qk",
                JSONObject()
                    .put("inn", JSONArray())
                    .put("c", JSONObject().put("n", 0).put("t", 0).put("g", 0).put("k", 0)),
            )
            .put("rhythm", JSONArray(List(WorldContract.HOURS) { 5 }))
            .put("voice", 1.0)
            .put("toys", JSONArray())
            .put("pos", JSONObject.NULL)

    private fun pairJson(): JSONObject? {
        val p = pair ?: return null
        if (living().size < 2) return null
        val gain = if (p.day == today) p.gain else 0.0
        return JSONObject()
            .put("rel", round3(p.rel))
            .put("level", PAIR_LEVELS.indexOfFirst { p.rel < it }.coerceAtLeast(0))
            .put("lead", -1)
            .put("settled", false)
            .put("gainLeft", round3(DAY_CAP - gain))
            .also { if (social) it.put("met", p.met) }
    }

    private fun giftOk(c: Cat) = level(c) >= GIFT_LEVEL && c.lastGiftDay != today

    // ---- commands ----

    /**
     * Runs command [name] with [payload] as the page does; answers the reply in NativeTabKit's
     * envelope.
     */
    fun command(name: String, payload: JSONObject, requestId: String = "r-1"): JSONObject {
        val reply =
            try {
                run(WorldCommandJson.decode(name, payload))
            } catch (e: WorldFormatException) {
                if (name == "world")
                    worldAnswer(false, false, "The cats' world was not stored.", "shape")
                else answer(false, false, GENERIC, "shape")
            }
        return JSONObject().put("command", name).put("requestId", requestId).also { r ->
            reply.keys().forEach { r.put(it, reply.get(it)) }
        }
    }

    /** Sends [c] as native would (its encoded payload). */
    fun send(c: WorldCommand, requestId: String = "r-1"): JSONObject =
        command(c.name, WorldCommandJson.encode(c), requestId)

    private fun run(c: WorldCommand): JSONObject {
        if (c.op.isNotEmpty() && (c.op in landed || (c is SaveWorld && stored?.op == c.op))) {
            return if (c is SaveWorld) worldAnswer(true, false, WorldReply.ALREADY, "")
            else if (c is CareFeed) petAnswer(true, false, WorldReply.ALREADY, "")
            else answer(true, false, WorldReply.ALREADY, "")
        }
        return when (c) {
            is SaveWorld -> world(c)
            is CareFeed,
            is CareVacation,
            is CareRename -> care(c)
            else -> persisting(c) { report(c) }
        }
    }

    private fun world(c: SaveWorld): JSONObject {
        val blob = WorldBlobJson.encode(c.blob)
        if (
            blob.getJSONObject("cats").keys().asSequence().toSet() != living().map { it.id }.toSet()
        )
            return worldAnswer(false, false, NOT_STORED, "shape")
        if (jsonBytes(blob) > WorldContract.BLOB_MAX_BYTES)
            return worldAnswer(false, false, NOT_STORED, "size")
        if (c.epoch != epoch || c.rev <= (stored?.rev ?: 0))
            return worldAnswer(false, false, NOT_STORED, "stale")
        val before = stored
        stored = Stored(c.epoch, c.rev, c.op, blob)
        if (!save()) {
            stored = before
            return worldAnswer(false, false, UNSAVED, "unsaved")
        }
        land(c.op)
        return worldAnswer(true, true, "", "")
    }

    /**
     * What a brain report does to the page's books; answers (changed, refusal reason, level
     * reached).
     */
    private fun report(c: WorldCommand): Triple<Boolean, String, Int> {
        when (c) {
            is PetDone -> {
                val cat = find(c.cat) ?: return Triple(false, "refused", 0)
                roll(cat)
                if (c.heldMs < PET_MS || cat.petsToday >= PETS_PER_DAY) return Triple(false, "", 0)
                cat.petsToday++
                return Triple(true, "", gain(cat, PET_BOND))
            }
            is PlayDone -> {
                val cat = find(c.cat) ?: return Triple(false, "refused", 0)
                roll(cat)
                cat.toyCredit.merge(c.toy, 1.0, Double::plus)
                if (c.played && cat.playsToday < PLAYS_PER_DAY) {
                    cat.playsToday++
                    gain(cat, PLAY_BOND)
                }
                return Triple(true, "", 0)
            }
            is GiftTaken -> {
                val cat = find(c.cat)?.takeIf(::giftOk) ?: return Triple(false, "refused", 0)
                cat.gifts++
                cat.lastGiftDay = today
                return Triple(true, "", 0)
            }
            is MomentReport -> {
                val cat = find(c.cat) ?: return Triple(false, "refused", 0)
                val changed =
                    when (c.kind) {
                        "quirk" ->
                            if (c.quirk in cat.acquired) false else cat.acquired.add(c.quirk!!)
                        "blink",
                        "respect" -> cat.fx.add("first_${c.kind}") || true
                        else -> cat.fx.add(c.kind)
                    }
                return Triple(changed, "", 0)
            }
            is SocialReport -> {
                val p =
                    pair?.takeIf {
                        living().size == 2 && c.cats.toSet() == living().map { it.id }.toSet()
                    } ?: return Triple(false, "refused", 0)
                if (p.day != today) p.day = today.also { p.gain = 0.0 }
                val g = minOf(PAIR_GAIN[c.kind] ?: 0.0, DAY_CAP - p.gain).coerceAtLeast(0.0)
                p.rel = round3(minOf(1.0, p.rel + g))
                p.gain += g
                p.n.merge(c.kind, 1, Int::plus)
                c.winner?.let { p.wins.merge(it, 1, Int::plus) }
                if (c.kind == "greet" || c.kind == "hiss") p.met = true
                p.last = c.kind
                return Triple(true, "", 0)
            }
            is DwellReport -> {
                val cat = find(c.cat) ?: return Triple(false, "refused", 0)
                c.spots.forEach { (id, s) -> cat.spotHours.merge(id, s / HOUR_S, Double::plus) }
                c.toys.forEach { (id, s) -> cat.toyCredit.merge(id, s / HOUR_S, Double::plus) }
                return Triple(true, "", 0)
            }
            else -> error("not a report")
        }
    }

    /** The page's care commands, answered in the Cat tab's reply shape (PetReply). */
    private fun care(c: WorldCommand): JSONObject {
        val house = living()
        if (house.isEmpty()) return petAnswer(false, false, "There is no cat.", "")
        when (c) {
            is CareFeed -> {
                if (house.all { it.vacation })
                    return petAnswer(false, false, "Vacation mode is on.", "vacation")
                val hungry =
                    house
                        .filter { !it.vacation && hours(it) >= FEED_GAP_H }
                        .ifEmpty {
                            return petAnswer(false, false, "Not hungry yet.", "full")
                        }
                return persistingPet(c.op) {
                    hungry.forEach {
                        roll(it)
                        it.lastFed = now
                        served(it)
                        it.fedToday++
                        if (it.fedToday <= 2) gain(it, FEED_BOND)
                    }
                }
            }
            is CareVacation -> {
                if (house[0].vacation == c.on) return petAnswer(true, false, WorldReply.ALREADY, "")
                return persistingPet("") {
                    house.forEach {
                        if (c.on) {
                            it.vacation = true
                            it.vacationSince = now
                        } else {
                            it.lastFed += now - it.vacationSince
                            it.vacation = false
                        }
                    }
                }
            }
            is CareRename -> {
                if (house[0].name == c.newName)
                    return petAnswer(true, false, WorldReply.ALREADY, "")
                return persistingPet("") { house[0].name = c.newName }
            }
            else -> error("not a care command")
        }
    }

    private fun persisting(
        c: WorldCommand,
        change: () -> Triple<Boolean, String, Int>,
    ): JSONObject {
        val before = snapshot()
        val (changed, why, levelUp) = change()
        if (why.isNotEmpty())
            return answer(false, false, "That is not possible now.", why).also { restore(before) }
        if (changed && !save())
            return answer(false, false, UNSAVED, "unsaved").also { restore(before) }
        land(c.op)
        return answer(true, changed, "", "").also { if (c is PetDone) it.put("levelUp", levelUp) }
    }

    private fun persistingPet(op: String, change: () -> Unit): JSONObject {
        val before = snapshot()
        change()
        if (!save()) return petAnswer(false, false, UNSAVED, "").also { restore(before) }
        if (op.isNotEmpty()) land(op)
        return petAnswer(true, true, "", "")
    }

    private fun save(): Boolean = (!saveFails).also { if (it) saves++ }

    private fun land(op: String) {
        if (op.isEmpty()) return
        landed.addLast(op)
        if (landed.size > LANDED) landed.removeFirst()
    }

    private fun find(id: String) = living().firstOrNull { it.id == id }

    private fun roll(c: Cat) {
        if (c.day != today) {
            c.day = today
            c.fedToday = 0
            c.petsToday = 0
            c.playsToday = 0
        }
    }

    /** petGain: no bond while hungry (stage 2 or more); answers the level reached, 0 when none. */
    private fun gain(c: Cat, pts: Int): Int {
        if (hungerStage(c) >= 2) return 0
        val before = level(c)
        c.bondPts += pts
        return if (level(c) > before) level(c) else 0
    }

    private fun snapshot(): List<Any?> =
        listOf(
            cats.map {
                it.copy(
                    fx = it.fx.toSortedSet(),
                    acquired = it.acquired.toMutableList(),
                    spotHours = it.spotHours.toSortedMap(),
                    toyCredit = it.toyCredit.toSortedMap(),
                )
            },
            pair?.copy(n = pair!!.n.toSortedMap(), wins = pair!!.wins.toSortedMap()),
            stored,
        )

    @Suppress("UNCHECKED_CAST")
    private fun restore(s: List<Any?>) {
        cats.clear()
        cats += s[0] as List<Cat>
        pair = s[1] as Pair?
        stored = s[2] as Stored?
    }

    private fun answer(ok: Boolean, changed: Boolean, message: String, why: String) =
        JSONObject().put("ok", ok).put("changed", changed).put("message", message).put("why", why)

    private fun worldAnswer(ok: Boolean, changed: Boolean, message: String, why: String) =
        answer(ok, changed, message, why).put("epoch", epoch).put("rev", stored?.rev ?: 0)

    private fun petAnswer(ok: Boolean, changed: Boolean, message: String, why: String) =
        answer(ok, changed, message, why).put("inHours", 0).put("reaction", "")

    companion object {
        val BOND_AT = intArrayOf(0, 30, 90, 200, 360, 580).toList()
        val STAGE_AT_H = listOf(8, 14, 26, 50, 120)
        val PAIR_LEVELS = listOf(0.15, 0.3, 0.55, 0.8, 9.0)
        // PET_PAIR_CFG.GAIN (29-pet-pair.js); a standoff gains no friendship (petPairGain's default
        // is for unknown kinds)
        val PAIR_GAIN =
            mapOf(
                "greet" to 0.03,
                "hiss" to 0.015,
                "chase" to 0.035,
                "groom" to 0.045,
                "nap" to 0.03,
                "standoff" to 0.0,
            )
        const val HOUR = 3_600_000L
        const val HOUR_S = 3600.0
        const val FEED_GAP_H = 4
        const val PETS_PER_DAY = 3
        const val PLAYS_PER_DAY = 2
        const val PET_MS = 1100L
        const val PET_BOND = 2
        const val PLAY_BOND = 3
        const val FEED_BOND = 6
        const val GIFT_LEVEL = 5
        const val REL_START = 0.1
        const val DAY_CAP = 0.16
        const val GROW_MS = 30 * 24 * HOUR
        const val PCT = 100
        const val LANDED = 40
        const val GENERIC = "Something went wrong. Nothing was changed."
        const val NOT_STORED = "The cats' world was not stored."
        const val UNSAVED = "Could not save to this browser (storage full?)"
    }
}
