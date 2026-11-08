package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.Activity
import com.lifemax.rpg.core.economy.CareDay
import com.lifemax.rpg.core.economy.DecayEntry
import com.lifemax.rpg.core.economy.DecaySettings
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.FoodEntry
import com.lifemax.rpg.core.economy.HealthConnect
import com.lifemax.rpg.core.economy.HealthState
import com.lifemax.rpg.core.economy.JournalCoach
import com.lifemax.rpg.core.economy.Money
import com.lifemax.rpg.core.economy.MoneyTransaction
import com.lifemax.rpg.core.economy.PlanChecks
import com.lifemax.rpg.core.economy.Quest
import com.lifemax.rpg.core.economy.Rule
import com.lifemax.rpg.core.economy.Rules
import com.lifemax.rpg.core.economy.Shields
import com.lifemax.rpg.core.economy.WeightPoint
import com.lifemax.rpg.core.economy.XpShare
import com.lifemax.rpg.core.save.PageReads.At
import com.lifemax.rpg.core.save.PageReads.Companion.given

/**
 * The tracked fields of a stored save ([SaveFields.TRACKED]) read into an [EconomySave], as the
 * page reads them (section 2.3). Only reads: every key is left where it is in the stored JSON.
 */
internal class EconomySaveReader(private val reads: PageReads) {
    fun save(json: JsonObject): EconomySave {
        val r = reads
        fun at(field: String) = At(field)
        val customRules =
            given(json["customRules"])?.let { rules ->
                r.members(rules, at("customRules")).mapValues { (_, v) ->
                    rule(r.record(v, at("customRules").anyKey()), at("customRules").anyKey())
                }
            }
        return EconomySave(
            activities =
                r.items(json["activities"], at("activities")).mapIndexed { i, v ->
                    val where = at("activities").index(i)
                    activity(r.record(v, where), where)
                },
            attrXp = r.numbers(json["attrXP"], at("attrXP")),
            skillXp = r.numbers(json["skillXP"], at("skillXP")),
            rules = Rules.defaults.mergedWith(customRules.orEmpty()),
            customRules = customRules,
            restedXp = r.number(json["restedXP"], at("restedXP")),
            sparks = r.number(json["sparks"], at("sparks")),
            perk = r.optionalText(json["perk"], at("perk")),
            perksOwned =
                given(json["perksOwned"])?.let { owned ->
                    r.items(owned, at("perksOwned")).mapIndexed { i, v ->
                        r.text(v, at("perksOwned").index(i))
                    }
                },
            attrStars = r.numbers(json["attrStars"], at("attrStars")),
            ascended = r.numbers(json["ascended"], at("ascended")),
            decayTotal = r.numbers(json["decayTotal"], at("decayTotal")),
            decayApplied = given(json["decayApplied"])?.let { r.numbers(it, at("decayApplied")) },
            decayLog = given(json["decayLog"])?.let { decayLog(it, at("decayLog")) },
            decay = given(json["decay"])?.let { decay(r.members(it, at("decay")), at("decay")) },
            legacyCharXpBonus = r.number(json["legacyCharXPBonus"], at("legacyCharXPBonus")),
            xpSchemaV2 = r.flag(json["xpSchemaV2"], at("xpSchemaV2")),
            quests =
                r.items(json["quests"], at("quests")).mapIndexed { i, v ->
                    val where = at("quests").index(i)
                    quest(r.record(v, where), where)
                },
            questDone =
                r.members(json["questDone"], at("questDone")).mapValues {
                    r.text(it.value, at("questDone").anyKey())
                },
            restDays =
                r.members(json["restDays"], at("restDays")).mapValues {
                    r.flag(it.value, at("restDays").anyKey())
                },
            restTokens = r.number(json["restTokens"], at("restTokens")),
            restBoughtAt = r.number(json["restBoughtAt"], at("restBoughtAt")),
            shields = shields(json),
            themesOwned =
                given(json["themesOwned"])?.let { owned ->
                    r.items(owned, at("themesOwned")).mapIndexed { i, v ->
                        r.text(v, at("themesOwned").index(i))
                    }
                },
            theme = r.optionalText(json["theme"], at("theme")),
            toysBought = given(json["toysBought"])?.let { r.numbers(it, at("toysBought")) },
            nutriDone = given(json["nutriDone"])?.let { r.numbers(it, at("nutriDone")) },
            calorieGoal =
                r.members(json["mealsMeta"], at("mealsMeta")).let {
                    r.number(it["calGoal"], at("mealsMeta").key("calGoal"))
                },
            foodLog =
                r.items(json["foodLog"], at("foodLog")).mapIndexed { i, v ->
                    val where = at("foodLog").index(i)
                    val e = r.record(v, where)
                    FoodEntry(
                        r.optionalText(e["date"], where.key("date")),
                        r.number(e["kcal"], where.key("kcal")),
                    )
                },
            careDay =
                given(json["careDay"])?.let {
                    careDay(r.members(it, at("careDay")), at("careDay"))
                },
            money = given(json["money"])?.let { money(r.members(it, at("money")), at("money")) },
            actXp = r.numbers(json["actXP"], at("actXP")),
            planChecks = planChecks(json["plan"], at("plan")),
            health = health(r.members(json["health"], at("health")), at("health")),
            weight = given(json["weight"])?.let { r.number(it, at("weight")) },
            devMode = r.flag(json["devMode"], at("devMode")),
            rerollsUsed =
                r.members(json["dailyDraw"], at("dailyDraw")).let {
                    val used = r.number(it["rerollsUsed"], at("dailyDraw").key("rerollsUsed"))
                    if (used.isNaN()) 0.0 else used
                },
            journal =
                journal(r.members(json["journalCoach"], at("journalCoach")), at("journalCoach")),
            dayReflections =
                r.members(json["dayReflections"], at("dayReflections")).mapValues {
                    r.text(it.value, at("dayReflections").anyKey())
                },
        )
    }

    private fun shields(json: JsonObject): Shields {
        val r = reads
        return Shields(
            count = r.number(json["streakShields"], At("streakShields")),
            tick = given(json["shieldTick"])?.let { r.number(it, At("shieldTick")) },
            miss = r.number(json["shieldMiss"], At("shieldMiss")),
            days =
                given(json["shieldDays"])?.let { days ->
                    r.members(days, At("shieldDays")).mapValues {
                        r.flag(it.value, At("shieldDays").anyKey())
                    }
                },
            milestone = r.number(json["shieldMilestone"], At("shieldMilestone")),
            boughtAt = r.number(json["shieldBoughtAt"], At("shieldBoughtAt")),
        )
    }

    /** One rule of `customRules` (RULES-1). */
    fun rule(j: Map<String, JsonValue>, at: At): Rule {
        val r = reads
        return Rule(
            name = r.text(j["name"], at.key("name")),
            icon = r.text(j["icon"], at.key("icon")),
            base = r.number(j["base"], at.key("base")),
            skill = r.text(j["skill"], at.key("skill")),
            primary = r.text(j["primary"], at.key("primary")),
            secondary = r.text(j["secondary"], at.key("secondary")),
            desc = r.text(j["desc"], at.key("desc")),
        )
    }

    /** One ledger record (section 2.2). */
    fun activity(j: Map<String, JsonValue>, at: At): Activity {
        val r = reads
        fun text(key: String) = r.optionalText(j[key], at.key(key))
        return Activity(
            id = r.text(j["id"], at.key("id")),
            type = r.text(j["type"], at.key("type")),
            name = text("name"),
            icon = text("icon"),
            duration = r.number(j["duration"], at.key("duration")),
            quality = if ("quality" in j) r.number(j["quality"], at.key("quality")) else null,
            skill = text("skill"),
            primary = text("primary"),
            secondary = text("secondary"),
            xp = r.number(j["xp"], at.key("xp")),
            date = text("date"),
            notes = text("notes"),
            split =
                r.items(j["xpSplit"], at.key("xpSplit")).mapIndexed { i, s ->
                    val where = at.key("xpSplit").index(i)
                    // The page reads a null share as nothing for no attribute.
                    if (s == JsonNull) XpShare("", 0.0).also { r.record(s, where) }
                    else
                        r.record(s, where).let {
                            XpShare(
                                r.text(it["attr"], where.key("attr")),
                                r.number(it["xp"], where.key("xp")),
                            )
                        }
                },
            source = text("src"),
            sparked = r.flag(j["sp"], at.key("sp")),
            epic = given(j["epic"])?.let { r.flag(it, at.key("epic")) },
            cadence = text("cad"),
            opId = text("opId"),
            ldEventId = text("ldEventId"),
            ldBonus = given(j["ldBonus"])?.let { r.number(it, at.key("ldBonus")) },
            healthKey = text("hc"),
            actId = text("actId"),
            link = text("link"),
            result = text("result"),
            kind = text("kind"),
            planDate = text("planDate"),
        )
    }

    /** One quest of the board (QUEST-1 to QUEST-4). */
    fun quest(j: Map<String, JsonValue>, at: At): Quest {
        val r = reads
        return Quest(
            id = r.text(j["id"], at.key("id")),
            name = r.text(j["name"], at.key("name")),
            cadence = r.optionalText(j["cadence"], at.key("cadence")),
            xp = r.number(j["xp"], at.key("xp")),
            attribute = r.optionalText(j["attribute"], at.key("attribute")),
            stage =
                given(j["stage"])
                    ?.let { r.number(it, at.key("stage")) }
                    ?.takeUnless { it.isNaN() }
                    ?.toInt(),
            kind = r.optionalText(j["kind"], at.key("kind")),
            desc = r.optionalText(j["desc"], at.key("desc")),
            hand = r.flag(j["hand"], at.key("hand")),
            verify = r.optionalText(j["verify"], at.key("verify")),
        )
    }

    private fun decayLog(value: JsonValue, at: At): List<DecayEntry> =
        reads.items(value, at).mapIndexed { i, v ->
            val where = at.index(i)
            val e = reads.record(v, where)
            DecayEntry(
                reads.text(e["date"], where.key("date")),
                reads.text(e["attr"], where.key("attr")),
                reads.number(e["xp"], where.key("xp")),
            )
        }

    private fun decay(d: Map<String, JsonValue>, at: At) =
        DecaySettings(
            given(d["on"])?.let { reads.flag(it, at.key("on")) },
            given(d["grace"])?.let { reads.number(it, at.key("grace")) },
            given(d["rate"])?.let { reads.number(it, at.key("rate")) },
        )

    private fun careDay(c: Map<String, JsonValue>, at: At) =
        CareDay(
            reads.text(c["day"], at.key("day")),
            reads.flag(c["feed"], at.key("feed")),
            reads.flag(c["play"], at.key("play")),
            reads.flag(c["pet"], at.key("pet")),
            reads.flag(c["paid"], at.key("paid")),
        )

    private fun money(j: Map<String, JsonValue>, at: At): Money {
        val r = reads
        return Money(
            start = r.number(j["start"], at.key("start")),
            transactions =
                r.items(j["tx"], at.key("tx")).mapIndexed { i, v ->
                    val where = at.key("tx").index(i)
                    val t = r.record(v, where)
                    MoneyTransaction(
                        r.text(t["id"], where.key("id")),
                        r.text(t["type"], where.key("type")),
                        r.number(t["amount"], where.key("amount")),
                        r.text(t["note"], where.key("note")),
                        r.text(t["date"], where.key("date")),
                        r.optionalText(t["actId"], where.key("actId")),
                    )
                },
            peakBalance = r.number(j["peakBalance"], at.key("peakBalance")),
        )
    }

    private fun planChecks(plan: JsonValue?, at: At): Map<String, PlanChecks> {
        val r = reads
        val checks = at.key("checks")
        return r.members(r.members(plan, at)["checks"], checks).mapValues { (_, c) ->
            val day = r.members(c, checks.anyKey())
            val ticks = checks.anyKey().key("s")
            PlanChecks(
                r.members(day["s"], ticks).mapValues { (_, v) ->
                    r.items(v, ticks.anyKey()).mapIndexed { i, tick ->
                        r.flag(tick, ticks.anyKey().index(i))
                    }
                },
                r.optionalText(day["actId"], checks.anyKey().key("actId")),
            )
        }
    }

    private fun health(j: Map<String, JsonValue>, at: At): HealthState {
        val r = reads
        fun flag(key: String) = r.flag(j[key], at.key(key))
        return HealthState(
            on = flag("on"),
            exercise = flag("ex"),
            steps = flag("steps"),
            sleep = flag("sleep"),
            weight = flag("wt"),
            seen = r.numbers(j["seen"], at.key("seen")),
            since = r.number(j["since"], at.key("since")),
            last = r.number(j["last"], at.key("last")),
            message = r.text(j["msg"], at.key("msg")),
            sdk = r.text(j["sdk"], at.key("sdk")),
            granted =
                r.items(j["granted"], at.key("granted")).mapIndexed { i, v ->
                    r.text(v, at.key("granted").index(i))
                },
            source = r.text(j["src"], at.key("src")),
            today =
                given(j["today"])?.let { today ->
                    val t = r.members(today, at.key("today"))
                    HealthConnect.StepDay(
                        r.text(t["date"], at.key("today").key("date")),
                        r.number(t["count"], at.key("today").key("count")),
                    )
                },
            weightTime = given(j["wtTime"])?.let { r.number(it, at.key("wtTime")) },
            weightHistory =
                given(j["wtHist"])?.let { history ->
                    r.items(history, at.key("wtHist")).mapIndexed { i, p ->
                        val where = at.key("wtHist").index(i)
                        val point = r.record(p, where)
                        WeightPoint(
                            r.number(point["t"], where.key("t")),
                            r.number(point["lb"], where.key("lb")),
                        )
                    }
                },
        )
    }

    private fun journal(j: Map<String, JsonValue>, at: At): JournalCoach {
        val r = reads
        val day = at.key("days").anyKey()
        val review = at.key("reviews").anyKey()
        val welcome = at.key("welcome").anyKey()
        return JournalCoach(
            days =
                r.members(j["days"], at.key("days")).mapValues {
                    r.flag(r.members(it.value, day)["xpAwarded"], day.key("xpAwarded"))
                },
            reviews =
                r.members(j["reviews"], at.key("reviews")).mapValues { (_, v) ->
                    val e = r.members(v, review)
                    JournalCoach.Review(
                        r.text(e["answer"], review.key("answer")),
                        r.flag(e["paid"], review.key("paid")),
                    )
                },
            welcome =
                r.members(j["welcome"], at.key("welcome")).mapValues { (_, v) ->
                    val w = r.members(v, welcome)
                    val claimed = welcome.key("claimed")
                    JournalCoach.Welcome(
                        r.number(w["gap"], welcome.key("gap")),
                        r.members(w["claimed"], claimed)
                            .filter { r.flag(it.value, claimed.anyKey()) }
                            .keys,
                        tier(w["tier"], welcome.key("tier")),
                    )
                },
        )
    }

    /** The welcome tier: a stored number, else 0 (a record written before tiers). */
    private fun tier(value: JsonValue?, at: At): Int {
        if (value != null && value != JsonNull) reads.number(value, at)
        return (value as? JsonNumber)?.value?.toInt() ?: 0
    }
}
