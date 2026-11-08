package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.Activity
import com.lifemax.rpg.core.economy.CareDay
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.HealthState
import com.lifemax.rpg.core.economy.JournalCoach
import com.lifemax.rpg.core.economy.Money
import com.lifemax.rpg.core.economy.MoneyTransaction
import com.lifemax.rpg.core.economy.PlanChecks
import com.lifemax.rpg.core.economy.Quest
import com.lifemax.rpg.core.economy.Rule

/**
 * The tracked fields of an [EconomySave] as JSON, in the shapes the page writes them. A field the
 * model holds as "not set" is left out (or written null where the page keeps the key with null:
 * `perk`, `theme`, a quest's `cadence`). A number whose missing key reads as NaN is written, when
 * NaN, as [JsonMerge.UNSET] ("not set"); any other NaN is a value the economy computed, which
 * [EconomySaveCodec] refuses to store (or stores as null when reading as the page).
 *
 * This is only half of writing a save: [JsonMerge] lays these values over the stored save, so every
 * key not written here, and every value that did not change, stays as stored.
 */
internal object EconomySaveWriter {
    /**
     * The money object the page creates when a save has none (MIG-5): its non-economy keys are page
     * defaults, written only when the save had no money at all.
     */
    private const val NEW_ACCOUNT = ""
    private const val NEW_GOAL = 0.0

    fun write(s: EconomySave): JsonObject = obj {
        put("activities", JsonArray(s.activities.map(::activity)))
        put("attrXP", numbers(s.attrXp))
        put("skillXP", numbers(s.skillXp))
        put("restedXP", maybeUnset(s.restedXp))
        put("sparks", maybeUnset(s.sparks))
        put("perk", text(s.perk))
        s.perksOwned?.let { put("perksOwned", texts(it)) }
        put("attrStars", numbers(s.attrStars))
        put("ascended", numbers(s.ascended))
        put("decayTotal", numbers(s.decayTotal))
        s.decayApplied?.let { put("decayApplied", numbers(it)) }
        s.decayLog?.let { log ->
            put(
                "decayLog",
                JsonArray(
                    log.map {
                        obj {
                            put("date", JsonString(it.date))
                            put("attr", JsonString(it.attr))
                            put("xp", maybeUnset(it.xp))
                        }
                    }
                ),
            )
        }
        s.decay?.let { d ->
            put(
                "decay",
                obj {
                    d.on?.let { put("on", JsonBool.of(it)) }
                    d.grace?.let { put("grace", JsonNumber(it)) }
                    d.rate?.let { put("rate", JsonNumber(it)) }
                },
            )
        }
        put("legacyCharXPBonus", maybeUnset(s.legacyCharXpBonus))
        put("xpSchemaV2", JsonBool.of(s.xpSchemaV2))
        s.customRules?.let { rules ->
            put("customRules", JsonObject(rules.mapValues { rule(it.value) }))
        }
        put("quests", JsonArray(s.quests.map(::quest)))
        put("questDone", JsonObject(s.questDone.mapValues { JsonString(it.value) }))
        put("restDays", JsonObject(s.restDays.mapValues { JsonBool.of(it.value) }))
        put("restTokens", maybeUnset(s.restTokens))
        put("restBoughtAt", maybeUnset(s.restBoughtAt))
        put("streakShields", maybeUnset(s.shields.count))
        s.shields.tick?.let { put("shieldTick", JsonNumber(it)) }
        put("shieldMiss", maybeUnset(s.shields.miss))
        s.shields.days?.let { days ->
            put("shieldDays", JsonObject(days.mapValues { JsonBool.of(it.value) }))
        }
        put("shieldMilestone", maybeUnset(s.shields.milestone))
        put("shieldBoughtAt", maybeUnset(s.shields.boughtAt))
        s.themesOwned?.let { put("themesOwned", texts(it)) }
        put("theme", text(s.theme))
        s.toysBought?.let { put("toysBought", numbers(it)) }
        s.nutriDone?.let { put("nutriDone", numbers(it)) }
        s.careDay?.let { put("careDay", careDay(it)) }
        put("mealsMeta", obj { put("calGoal", maybeUnset(s.calorieGoal)) })
        put(
            "foodLog",
            JsonArray(
                s.foodLog.map {
                    obj {
                        put("date", text(it.date))
                        put("kcal", maybeUnset(it.kcal))
                    }
                }
            ),
        )
        s.money?.let { put("money", money(it)) }
        put("actXP", numbers(s.actXp))
        put("plan", obj { put("checks", JsonObject(s.planChecks.mapValues { plan(it.value) })) })
        put("health", health(s.health))
        s.weight?.let { put("weight", JsonNumber(it)) }
        put("devMode", JsonBool.of(s.devMode))
        put("dailyDraw", obj { put("rerollsUsed", JsonNumber(s.rerollsUsed)) })
        put("journalCoach", journal(s.journal))
        put("dayReflections", JsonObject(s.dayReflections.mapValues { JsonString(it.value) }))
    }

    /** A ledger record with the keys the page gives it, in the page's order (section 2.2). */
    fun activity(a: Activity): JsonObject = obj {
        put("id", JsonString(a.id))
        a.opId?.let { put("opId", JsonString(it)) }
        a.ldEventId?.let { put("ldEventId", JsonString(it)) }
        a.cadence?.let { put("cad", JsonString(it)) }
        a.epic?.let { put("epic", JsonBool.of(it)) }
        put("type", JsonString(a.type))
        a.actId?.let { put("actId", JsonString(it)) }
        a.link?.let { put("link", JsonString(it)) }
        a.kind?.let { put("kind", JsonString(it)) }
        a.planDate?.let { put("planDate", JsonString(it)) }
        a.name?.let { put("name", JsonString(it)) }
        a.icon?.let { put("icon", JsonString(it)) }
        put("duration", maybeUnset(a.duration))
        a.result?.let { put("result", JsonString(it)) }
        a.quality?.let { put("quality", JsonNumber(it)) }
        a.skill?.let { put("skill", JsonString(it)) }
        a.primary?.let { put("primary", JsonString(it)) }
        a.secondary?.let { put("secondary", JsonString(it)) }
        put("xp", maybeUnset(a.xp))
        a.date?.let { put("date", JsonString(it)) }
        a.notes?.let { put("notes", JsonString(it)) }
        put(
            "xpSplit",
            JsonArray(
                a.split.map {
                    obj {
                        put("attr", JsonString(it.attr))
                        put("xp", maybeUnset(it.xp))
                    }
                }
            ),
        )
        a.source?.let { put("src", JsonString(it)) }
        a.healthKey?.let { put("hc", JsonString(it)) }
        a.ldBonus?.let { put("ldBonus", JsonNumber(it)) }
        if (a.sparked) put("sp", JsonNumber(1.0))
    }

    private fun rule(r: Rule) = obj {
        put("name", JsonString(r.name))
        put("icon", JsonString(r.icon))
        put("base", maybeUnset(r.base))
        put("skill", JsonString(r.skill))
        put("primary", JsonString(r.primary))
        put("secondary", JsonString(r.secondary))
        put("desc", JsonString(r.desc))
    }

    private fun quest(q: Quest) = obj {
        put("id", JsonString(q.id))
        put("name", JsonString(q.name))
        q.desc?.let { put("desc", JsonString(it)) }
        put("cadence", text(q.cadence))
        put("xp", maybeUnset(q.xp))
        q.kind?.let { put("kind", JsonString(it)) }
        q.attribute?.let { put("attribute", JsonString(it)) }
        q.stage?.let { put("stage", JsonNumber(it.toDouble())) }
        if (q.hand) put("hand", JsonBool.TRUE)
        q.verify?.let { put("verify", JsonString(it)) }
    }

    private fun careDay(c: CareDay) = obj {
        put("day", JsonString(c.day))
        if (c.feed) put("feed", JsonNumber(1.0))
        if (c.play) put("play", JsonNumber(1.0))
        if (c.pet) put("pet", JsonNumber(1.0))
        if (c.paid) put("paid", JsonNumber(1.0))
    }

    private fun money(m: Money) = obj {
        put("account", JsonString(NEW_ACCOUNT))
        put("start", maybeUnset(m.start))
        put("goal", JsonNumber(NEW_GOAL))
        put("tx", JsonArray(m.transactions.map(::transaction)))
        put("peakBalance", maybeUnset(m.peakBalance))
    }

    private fun transaction(t: MoneyTransaction) = obj {
        put("id", JsonString(t.id))
        put("type", JsonString(t.type))
        put("amount", maybeUnset(t.amount))
        put("note", JsonString(t.note))
        put("date", JsonString(t.date))
        t.actId?.let { put("actId", JsonString(it)) }
    }

    /** A plan day; `v` (the page's own) is written empty only for a day the save did not have. */
    private fun plan(c: PlanChecks) = obj {
        put("s", JsonObject(c.ticks.mapValues { (_, ticks) -> JsonArray(ticks.map(JsonBool::of)) }))
        put("v", JsonObject.EMPTY)
        c.actId?.let { put("actId", JsonString(it)) }
    }

    private fun health(h: HealthState) = obj {
        put("on", JsonBool.of(h.on))
        put("ex", JsonBool.of(h.exercise))
        put("steps", JsonBool.of(h.steps))
        put("sleep", JsonBool.of(h.sleep))
        put("wt", JsonBool.of(h.weight))
        put("seen", numbers(h.seen))
        put("last", maybeUnset(h.last))
        put("since", maybeUnset(h.since))
        put("msg", JsonString(h.message))
        put("sdk", JsonString(h.sdk))
        put("granted", texts(h.granted))
        put("src", JsonString(h.source))
        put(
            "today",
            h.today?.let {
                obj {
                    put("date", JsonString(it.date))
                    put("count", maybeUnset(it.count))
                }
            } ?: JsonNull,
        )
        h.weightTime?.let { put("wtTime", JsonNumber(it)) }
        h.weightHistory?.let { history ->
            put(
                "wtHist",
                JsonArray(
                    history.map {
                        obj {
                            put("t", maybeUnset(it.time))
                            put("lb", maybeUnset(it.pounds))
                        }
                    }
                ),
            )
        }
    }

    private fun journal(j: JournalCoach) = obj {
        put(
            "days",
            JsonObject(
                j.days.mapValues { (_, awarded) ->
                    obj { if (awarded) put("xpAwarded", JsonBool.TRUE) }
                }
            ),
        )
        put(
            "reviews",
            JsonObject(
                j.reviews.mapValues { (_, r) ->
                    obj {
                        put("answer", JsonString(r.answer))
                        if (r.paid) put("paid", JsonBool.TRUE)
                    }
                }
            ),
        )
        put(
            "welcome",
            JsonObject(
                j.welcome.mapValues { (_, w) ->
                    obj {
                        put("gap", maybeUnset(w.gap))
                        put("claimed", JsonObject(w.claimed.associateWith { JsonNumber(1.0) }))
                        if (w.tier != 0) put("tier", JsonNumber(w.tier.toDouble()))
                    }
                }
            ),
        )
    }

    private fun obj(build: LinkedHashMap<String, JsonValue>.() -> Unit) =
        JsonObject(LinkedHashMap<String, JsonValue>().apply(build))

    /**
     * A number the reader gives as NaN when its key is missing (NUM-3): NaN is written as
     * [JsonMerge.UNSET], so the merge leaves a missing key missing, and refuses (or, reading as the
     * page, writes null) where a stored number became NaN.
     */
    private fun maybeUnset(x: Double): JsonNumber =
        if (x.isNaN()) JsonMerge.UNSET else JsonNumber(x)

    private fun numbers(m: Map<String, Double>) = JsonObject(m.mapValues { JsonNumber(it.value) })

    private fun texts(list: List<String>) = JsonArray(list.map(::JsonString))

    private fun text(s: String?): JsonValue = s?.let(::JsonString) ?: JsonNull
}
