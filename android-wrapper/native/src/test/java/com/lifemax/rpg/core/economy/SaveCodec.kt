package com.lifemax.rpg.core.economy

/**
 * Reads a recorded page state into an [EconomySave] and writes one back. Writing is relative to a
 * baseline: a field the economy left equal to the baseline is written as the baseline's raw JSON,
 * so values this module reads loosely (a text number, a stray key) come back exactly as recorded.
 * Fields that hold more than the economy owns (money, health, plan, journal) keep their other keys.
 */
internal object SaveCodec {
    data object Absent

    /**
     * The money object the page creates when a save has none (MIG-5); its non-economy keys are page
     * defaults.
     */
    private val NEW_MONEY =
        linkedMapOf<String, Any?>(
            "account" to "",
            "start" to 0.0,
            "goal" to 0.0,
            "tx" to emptyList<Any?>(),
        )

    /** The keys the golden cases record (every key any case reports changed or deleted). */
    val TRACKED =
        listOf(
            "activities",
            "attrXP",
            "skillXP",
            "customRules",
            "restedXP",
            "sparks",
            "perk",
            "perksOwned",
            "attrStars",
            "ascended",
            "decayTotal",
            "decayApplied",
            "decayLog",
            "decay",
            "legacyCharXPBonus",
            "xpSchemaV2",
            "quests",
            "questDone",
            "restDays",
            "restTokens",
            "restBoughtAt",
            "streakShields",
            "shieldTick",
            "shieldMiss",
            "shieldDays",
            "shieldMilestone",
            "shieldBoughtAt",
            "themesOwned",
            "theme",
            "toysBought",
            "nutriDone",
            "mealsMeta",
            "foodLog",
            "careDay",
            "money",
            "actXP",
            "plan",
            "health",
            "weight",
            "journalCoach",
            "dayReflections",
        )

    fun decode(json: Map<String, Any?>): EconomySave {
        val customRules = json["customRules"]?.obj()?.mapValues { (_, v) -> rule(v.obj()) }
        val shields =
            Shields(
                count = json.num("streakShields"),
                tick = json["shieldTick"]?.let { num(it) },
                miss = json.num("shieldMiss"),
                days = json["shieldDays"]?.obj()?.mapValues { truthy(it.value) },
                milestone = json.num("shieldMilestone"),
                boughtAt = json.num("shieldBoughtAt"),
            )
        return EconomySave(
            activities = json["activities"].list().map { activity(it.obj()) },
            attrXp = numbers(json["attrXP"]),
            skillXp = numbers(json["skillXP"]),
            rules = Rules.defaults.mergedWith(customRules.orEmpty()),
            customRules = customRules,
            restedXp = json.num("restedXP"),
            sparks = json.num("sparks"),
            perk = json.str("perk"),
            perksOwned = json["perksOwned"]?.list()?.map(::text),
            attrStars = numbers(json["attrStars"]),
            ascended = numbers(json["ascended"]),
            decayTotal = numbers(json["decayTotal"]),
            decayApplied = json["decayApplied"]?.let(::numbers),
            decayLog =
                json["decayLog"]?.list()?.map {
                    it.obj().let { e -> DecayEntry(text(e["date"]), text(e["attr"]), e.num("xp")) }
                },
            decay =
                json["decay"]?.obj()?.let { d ->
                    DecaySettings(
                        d["on"]?.let(::truthy),
                        d["grace"]?.let { num(it) },
                        d["rate"]?.let { num(it) },
                    )
                },
            legacyCharXpBonus = json.num("legacyCharXPBonus"),
            xpSchemaV2 = truthy(json["xpSchemaV2"]),
            quests = json["quests"].list().map { quest(it.obj()) },
            questDone = json["questDone"].obj().mapValues { text(it.value) },
            restDays = json["restDays"].obj().mapValues { truthy(it.value) },
            restTokens = json.num("restTokens"),
            restBoughtAt = json.num("restBoughtAt"),
            shields = shields,
            themesOwned = json["themesOwned"]?.list()?.map(::text),
            theme = json.str("theme"),
            toysBought = json["toysBought"]?.let(::numbers),
            nutriDone = json["nutriDone"]?.let(::numbers),
            calorieGoal = json["mealsMeta"].obj().num("calGoal"),
            foodLog =
                json["foodLog"].list().map {
                    it.obj().let { e -> FoodEntry(e.str("date"), e.num("kcal")) }
                },
            careDay =
                json["careDay"]?.obj()?.let { c ->
                    CareDay(
                        text(c["day"]),
                        truthy(c["feed"]),
                        truthy(c["play"]),
                        truthy(c["pet"]),
                        truthy(c["paid"]),
                    )
                },
            money = json["money"]?.obj()?.let(::money),
            actXp = numbers(json["actXP"]),
            planChecks =
                json["plan"].obj()["checks"].obj().mapValues { (_, c) ->
                    PlanChecks(
                        c.obj()["s"].obj().mapValues { (_, v) -> v.list().map(::truthy) },
                        c.obj().str("actId"),
                    )
                },
            health = health(json["health"].obj()),
            weight = json["weight"]?.let { num(it) },
            devMode = truthy(json["devMode"]),
            rerollsUsed = json["dailyDraw"].obj().num("rerollsUsed").orZeroValue(),
            journal = journal(json["journalCoach"].obj()),
            dayReflections = json["dayReflections"].obj().mapValues { text(it.value) },
        )
    }

    private fun Double.orZeroValue() = if (isNaN()) 0.0 else this

    fun numbers(value: Any?): Map<String, Double> = value.obj().mapValues { num(it.value) }

    fun rule(j: Map<String, Any?>) =
        Rule(
            name = text(j["name"]),
            icon = text(j["icon"]),
            base = j.num("base"),
            skill = text(j["skill"]),
            primary = text(j["primary"]),
            secondary = text(j["secondary"]),
            desc = text(j["desc"]),
        )

    fun activity(j: Map<String, Any?>) =
        Activity(
            id = text(j["id"]),
            type = text(j["type"]),
            name = j.str("name"),
            icon = j.str("icon"),
            duration = j.num("duration"),
            quality = if (j.containsKey("quality")) j.num("quality") else null,
            skill = j.str("skill"),
            primary = j.str("primary"),
            secondary = j.str("secondary"),
            xp = j.num("xp"),
            date = j.str("date"),
            notes = j.str("notes"),
            split =
                j["xpSplit"].list().map { s ->
                    s?.obj()?.let { XpShare(text(it["attr"]), it.num("xp")) } ?: XpShare("", 0.0)
                },
            source = j.str("src"),
            sparked = truthy(j["sp"]),
            epic = j["epic"]?.let(::truthy),
            cadence = j.str("cad"),
            opId = j.str("opId"),
            ldEventId = j.str("ldEventId"),
            ldBonus = j["ldBonus"]?.let { num(it) },
            healthKey = j.str("hc"),
            actId = j.str("actId"),
            link = j.str("link"),
            result = j.str("result"),
            kind = j.str("kind"),
            planDate = j.str("planDate"),
        )

    fun quest(j: Map<String, Any?>) =
        Quest(
            id = text(j["id"]),
            name = text(j["name"]),
            cadence = j.str("cadence"),
            xp = j.num("xp"),
            attribute = j.str("attribute"),
            stage = j["stage"]?.let { num(it) }?.takeUnless { it.isNaN() }?.toInt(),
            kind = j.str("kind"),
            desc = j.str("desc"),
            hand = truthy(j["hand"]),
            verify = j.str("verify"),
        )

    private fun money(j: Map<String, Any?>) =
        Money(
            start = j.num("start"),
            transactions =
                j["tx"].list().map { t ->
                    t.obj().let {
                        MoneyTransaction(
                            text(it["id"]),
                            text(it["type"]),
                            it.num("amount"),
                            text(it["note"]),
                            text(it["date"]),
                            it.str("actId"),
                        )
                    }
                },
            peakBalance = j.num("peakBalance"),
        )

    private fun health(j: Map<String, Any?>) =
        HealthState(
            on = truthy(j["on"]),
            exercise = truthy(j["ex"]),
            steps = truthy(j["steps"]),
            sleep = truthy(j["sleep"]),
            weight = truthy(j["wt"]),
            seen = numbers(j["seen"]),
            since = j.num("since"),
            last = j.num("last"),
            message = text(j["msg"]),
            sdk = text(j["sdk"]),
            granted = j["granted"].list().map(::text),
            source = text(j["src"]),
            today =
                j["today"]?.obj()?.let { HealthConnect.StepDay(text(it["date"]), it.num("count")) },
            weightTime = j["wtTime"]?.let { num(it) },
            weightHistory =
                j["wtHist"]?.list()?.map { p ->
                    p.obj().let { WeightPoint(it.num("t"), it.num("lb")) }
                },
        )

    private fun journal(j: Map<String, Any?>) =
        JournalCoach(
            days = j["days"].obj().mapValues { truthy(it.value.obj()["xpAwarded"]) },
            reviews =
                j["reviews"].obj().mapValues { (_, r) ->
                    JournalCoach.Review(text(r.obj()["answer"]), truthy(r.obj()["paid"]))
                },
            welcome =
                j["welcome"].obj().mapValues { (_, w) ->
                    JournalCoach.Welcome(
                        w.obj().num("gap"),
                        w.obj()["claimed"].obj().filterValues(::truthy).keys,
                    )
                },
        )

    /** The JSON of each tracked key for [save], written against [baseline] and its [raw] JSON. */
    fun encode(
        save: EconomySave,
        baseline: EconomySave,
        raw: Map<String, Any?>,
    ): Map<String, Any?> {
        val out = linkedMapOf<String, Any?>()
        fun put(key: String, now: Any?, was: Any?, write: () -> Any?) {
            out[key] = if (now == was) raw.getOrElse(key) { Absent } else write()
        }
        val s = save
        val b = baseline
        put("activities", s.activities, b.activities) {
            list(
                s.activities,
                b.activities,
                raw["activities"].list(),
                ::activity,
                ::activityJson,
                { it.copy(sparked = false) },
            ) { r, a ->
                if (a.sparked && !truthy(r["sp"])) r + ("sp" to 1.0) else r
            }
        }
        put("attrXP", s.attrXp, b.attrXp) { s.attrXp }
        put("skillXP", s.skillXp, b.skillXp) { s.skillXp }
        put("customRules", s.customRules, b.customRules) {
            s.customRules?.mapValues { ruleJson(it.value) } ?: Absent
        }
        put("restedXP", s.restedXp, b.restedXp) { s.restedXp }
        put("sparks", s.sparks, b.sparks) { s.sparks }
        put("perk", s.perk, b.perk) { s.perk }
        put("perksOwned", s.perksOwned, b.perksOwned) { s.perksOwned ?: Absent }
        put("attrStars", s.attrStars, b.attrStars) { s.attrStars }
        put("ascended", s.ascended, b.ascended) { s.ascended }
        put("decayTotal", s.decayTotal, b.decayTotal) { s.decayTotal }
        put("decayApplied", s.decayApplied, b.decayApplied) { s.decayApplied ?: Absent }
        put("decayLog", s.decayLog, b.decayLog) {
            s.decayLog?.map { mapOf("date" to it.date, "attr" to it.attr, "xp" to it.xp) } ?: Absent
        }
        put("decay", s.decay, b.decay) {
            s.decay?.let { d ->
                listOfNotNull(
                        d.on?.let { "on" to it },
                        d.grace?.let { "grace" to it },
                        d.rate?.let { "rate" to it },
                    )
                    .toMap()
            } ?: Absent
        }
        put("legacyCharXPBonus", s.legacyCharXpBonus, b.legacyCharXpBonus) { s.legacyCharXpBonus }
        put("xpSchemaV2", s.xpSchemaV2, b.xpSchemaV2) { s.xpSchemaV2 }
        put("quests", s.quests, b.quests) {
            list(s.quests, b.quests, raw["quests"].list(), ::quest, ::questJson, { it }) { r, _ ->
                r
            }
        }
        put("questDone", s.questDone, b.questDone) { s.questDone }
        put("restDays", s.restDays, b.restDays) { s.restDays }
        put("restTokens", s.restTokens, b.restTokens) { s.restTokens }
        put("restBoughtAt", s.restBoughtAt, b.restBoughtAt) { s.restBoughtAt }
        put("streakShields", s.shields.count, b.shields.count) { s.shields.count }
        put("shieldTick", s.shields.tick, b.shields.tick) { s.shields.tick ?: Absent }
        put("shieldMiss", s.shields.miss, b.shields.miss) { s.shields.miss }
        put("shieldDays", s.shields.days, b.shields.days) { s.shields.days ?: Absent }
        put("shieldMilestone", s.shields.milestone, b.shields.milestone) { s.shields.milestone }
        put("shieldBoughtAt", s.shields.boughtAt, b.shields.boughtAt) { s.shields.boughtAt }
        put("themesOwned", s.themesOwned, b.themesOwned) { s.themesOwned ?: Absent }
        put("theme", s.theme, b.theme) { s.theme }
        put("toysBought", s.toysBought, b.toysBought) { s.toysBought ?: Absent }
        put("nutriDone", s.nutriDone, b.nutriDone) { s.nutriDone ?: Absent }
        put("mealsMeta", s.calorieGoal, b.calorieGoal) {
            raw["mealsMeta"].obj() + ("calGoal" to s.calorieGoal)
        }
        put("foodLog", s.foodLog, b.foodLog) {
            s.foodLog.map { mapOf("date" to it.date, "kcal" to it.kcal) }
        }
        put("careDay", s.careDay, b.careDay) { s.careDay?.let(::careJson) ?: Absent }
        put("money", s.money, b.money) {
            s.money?.let { moneyJson(it, b.money, raw["money"].obj()) } ?: Absent
        }
        put("actXP", s.actXp, b.actXp) { s.actXp }
        put("plan", s.planChecks, b.planChecks) { planJson(s.planChecks, raw["plan"].obj()) }
        put("health", s.health, b.health) { healthJson(s.health, raw["health"].obj()) }
        put("weight", s.weight, b.weight) { s.weight ?: Absent }
        put("journalCoach", s.journal, b.journal) {
            journalJson(s.journal, raw["journalCoach"].obj())
        }
        put("dayReflections", s.dayReflections, b.dayReflections) { s.dayReflections }
        return out
    }

    /**
     * A list whose unchanged elements are written as recorded; [touch] adapts a recorded element
     * (`sp`).
     */
    private fun <T> list(
        now: List<T>,
        was: List<T>,
        raw: List<Any?>,
        decode: (Map<String, Any?>) -> T,
        write: (T) -> Any?,
        key: (T) -> T,
        touch: (Map<String, Any?>, T) -> Map<String, Any?>,
    ): List<Any?> {
        val pool =
            raw.map { it.obj() }
                .groupByTo(linkedMapOf()) { key(decode(it)) }
                .mapValues { it.value.toMutableList() }
        check(was.size == raw.size)
        return now.map { item ->
            pool[key(item)]?.removeFirstOrNull()?.let { touch(it, item) } ?: write(item)
        }
    }

    fun activityJson(a: Activity): Map<String, Any?> =
        linkedMapOf<String, Any?>().apply {
            put("id", a.id)
            a.opId?.let { put("opId", it) }
            a.ldEventId?.let { put("ldEventId", it) }
            a.cadence?.let { put("cad", it) }
            a.epic?.let { put("epic", it) }
            put("type", a.type)
            a.actId?.let { put("actId", it) }
            a.link?.let { put("link", it) }
            a.kind?.let { put("kind", it) }
            a.planDate?.let { put("planDate", it) }
            a.name?.let { put("name", it) }
            a.icon?.let { put("icon", it) }
            put("duration", a.duration)
            a.result?.let { put("result", it) }
            a.quality?.let { put("quality", if (it.isNaN()) null else it) }
            a.skill?.let { put("skill", it) }
            a.primary?.let { put("primary", it) }
            a.secondary?.let { put("secondary", it) }
            put("xp", a.xp)
            a.date?.let { put("date", it) }
            a.notes?.let { put("notes", it) }
            put("xpSplit", a.split.map { mapOf("attr" to it.attr, "xp" to it.xp) })
            a.source?.let { put("src", it) }
            a.healthKey?.let { put("hc", it) }
            a.ldBonus?.let { put("ldBonus", it) }
            if (a.sparked) put("sp", 1.0)
        }

    private fun ruleJson(r: Rule) =
        mapOf(
            "name" to r.name,
            "icon" to r.icon,
            "base" to r.base,
            "skill" to r.skill,
            "primary" to r.primary,
            "secondary" to r.secondary,
            "desc" to r.desc,
        )

    private fun questJson(q: Quest): Map<String, Any?> =
        linkedMapOf<String, Any?>("id" to q.id, "name" to q.name).apply {
            q.desc?.let { put("desc", it) }
            put("cadence", q.cadence)
            put("xp", q.xp)
            q.kind?.let { put("kind", it) }
            q.attribute?.let { put("attribute", it) }
            q.stage?.let { put("stage", it.toDouble()) }
        }

    private fun careJson(c: CareDay): Map<String, Any?> =
        linkedMapOf<String, Any?>("day" to c.day).apply {
            if (c.feed) put("feed", 1.0)
            if (c.play) put("play", 1.0)
            if (c.pet) put("pet", 1.0)
            if (c.paid) put("paid", 1.0)
        }

    private fun moneyJson(m: Money, was: Money?, raw: Map<String, Any?>): Map<String, Any?> {
        val tx =
            list(
                m.transactions,
                was?.transactions.orEmpty(),
                raw["tx"].list(),
                { t -> money(mapOf("tx" to listOf(t))).transactions.single() },
                ::txJson,
                { it.copy(actId = null) },
            ) { r, t ->
                if (t.actId != null) r + ("actId" to t.actId) else r
            }
        val base = raw.ifEmpty { NEW_MONEY }
        return base +
            linkedMapOf("start" to (if (m.start.isNaN()) raw["start"] else m.start), "tx" to tx)
                .apply { if (!m.peakBalance.isNaN()) put("peakBalance", m.peakBalance) }
    }

    private fun txJson(t: MoneyTransaction): Map<String, Any?> =
        linkedMapOf<String, Any?>(
                "id" to t.id,
                "type" to t.type,
                "amount" to t.amount,
                "note" to t.note,
                "date" to t.date,
            )
            .apply { t.actId?.let { put("actId", it) } }

    private fun planJson(
        checks: Map<String, PlanChecks>,
        raw: Map<String, Any?>,
    ): Map<String, Any?> {
        val rawChecks = raw["checks"].obj()
        return raw +
            ("checks" to
                checks.mapValues { (date, c) ->
                    val was = rawChecks[date].obj()
                    val entry =
                        linkedMapOf<String, Any?>(
                            "s" to c.ticks,
                            "v" to (was["v"] ?: emptyMap<String, Any?>()),
                        )
                    c.actId?.let { entry["actId"] = it }
                    entry
                })
    }

    private fun healthJson(h: HealthState, raw: Map<String, Any?>): Map<String, Any?> =
        raw +
            linkedMapOf<String, Any?>(
                    "on" to h.on,
                    "ex" to h.exercise,
                    "steps" to h.steps,
                    "sleep" to h.sleep,
                    "wt" to h.weight,
                    "seen" to h.seen,
                    "last" to h.last,
                    "since" to h.since,
                    "msg" to h.message,
                    "sdk" to h.sdk,
                    "granted" to h.granted,
                    "src" to h.source,
                    "today" to h.today?.let { mapOf("date" to it.date, "count" to it.count) },
                )
                .apply {
                    h.weightTime?.let { put("wtTime", it) }
                    h.weightHistory?.let {
                        put("wtHist", it.map { p -> mapOf("t" to p.time, "lb" to p.pounds) })
                    }
                }

    private fun journalJson(j: JournalCoach, raw: Map<String, Any?>): Map<String, Any?> {
        val rawDays = raw["days"].obj()
        return raw +
            linkedMapOf(
                "days" to
                    j.days.mapValues { (d, awarded) ->
                        rawDays[d].obj() + if (awarded) mapOf("xpAwarded" to true) else emptyMap()
                    },
                "reviews" to
                    j.reviews.mapValues { (_, r) ->
                        linkedMapOf<String, Any?>("answer" to r.answer).apply {
                            if (r.paid) put("paid", true)
                        }
                    },
                "welcome" to
                    j.welcome.mapValues { (d, w) ->
                        raw["welcome"].obj()[d].obj() +
                            mapOf("gap" to w.gap, "claimed" to w.claimed.associateWith { 1.0 })
                    },
            )
    }
}
