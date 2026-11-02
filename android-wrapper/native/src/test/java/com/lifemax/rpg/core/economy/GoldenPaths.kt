package com.lifemax.rpg.core.economy

/** The award paths beyond Log and quests (section 11.3 onward), as the golden cases call them. */
internal object GoldenPaths {
    fun dispatch(
        run: GoldenRun,
        fn: String,
        args: List<Any?>,
        env: Map<String, Any?>,
        ctx: EconomyContext,
    ): Any? {
        val s = run.save
        fun arg(i: Int) = args.getOrNull(i)
        val dom = env["dom"].obj()
        val connected = run.raw["quests"] is List<*>
        return when (fn) {
            "LifeMaxHost.typeForTag" -> LifeDirector.typeForTag(text(arg(0)))
            "LifeMaxHost.xpFor" ->
                run.apply(
                        LifeDirector.xpFor(
                            s,
                            text(arg(0)),
                            num(arg(1)),
                            num(arg(2)),
                            arg(3)?.let(::text),
                            truthy(arg(4)),
                            ctx,
                        )
                    )
                    .let {
                        mapOf(
                            "xp" to it.xp,
                            "rule" to GoldenCalls.ruleJson(it.rule),
                            "split" to GoldenCalls.shares(it.split),
                        )
                    }
            "LifeMaxHost.awardActivity" -> {
                val e = arg(0).obj()
                val session =
                    LifeDirector.Session(
                        eventId = text(e["eventId"]),
                        tag = text(e["activityTypeTag"]),
                        startEpochSeconds = e.num("actualStartEpoch"),
                        minutes = e.num("actualDurationMinutes"),
                        title = text(e["title"]),
                        partial = e["lifecycleState"] == "PARTIALLY_COMPLETED",
                    )
                val r =
                    run.apply(
                        LifeDirector.awardActivity(
                            s,
                            session,
                            arg(1).obj().str("title"),
                            ctx,
                            connected,
                        )
                    )
                linkedMapOf<String, Any?>("awarded" to r.awarded).apply {
                    r.reason?.let { put("reason", it) }
                    r.activityId?.let { put("activityId", it) }
                    r.xp?.let { put("xp", it) }
                }
            }
            "LifeMaxHost.completeQuest" -> {
                val ref =
                    arg(0)?.obj()?.let {
                        LifeDirector.QuestRef(
                            it.str("questId"),
                            it.str("key"),
                            truthy(it["future"]),
                        )
                    }
                val r = run.apply(LifeDirector.completeQuest(s, ref, ctx, connected))
                linkedMapOf<String, Any?>("completed" to r.completed).apply {
                    r.reason?.let { put("reason", it) }
                    r.xp?.let { put("xp", it) }
                    r.name?.let { put("name", it) }
                }
            }
            "lmaHealthAward" -> run.apply(Health.award(s, healthImport(arg(0).obj()), ctx))
            "lmaHealthSync" ->
                run.apply(
                        Health.sync(
                            s,
                            env["health"]?.obj()?.let(::FakeHealthConnect),
                            truthy(arg(0)),
                            ctx,
                        )
                    )
                    .let { null }
            "lmvActType" ->
                Voice.activityType(text(arg(0))).let {
                    mapOf("type" to it.type, "name" to it.name, "noun" to it.noun)
                }
            "lmvLogXp" ->
                run.apply(
                        Voice.logXp(
                            s,
                            text(arg(0)),
                            num(arg(1)),
                            arg(2)?.let(::text),
                            truthy(arg(3)),
                            ctx,
                        )
                    )
                    .let { mapOf("xp" to it.xp, "msg" to it.message) }
            "lmvLogNow" -> {
                val o = arg(0).obj()
                val said =
                    Voice.Said(
                        text(o["type"]),
                        text(o["name"]),
                        o.num("duration"),
                        o.str("day"),
                        o.num("atMs").toLong(),
                    )
                val r = run.apply(Voice.logNow(s, said, ctx))
                if (r.ok) mapOf("ok" to true, "xp" to r.xp, "id" to r.id)
                else mapOf("ok" to false, "msg" to r.message)
            }
            "ecoNutritionCheck" -> run.apply(Bonuses.nutrition(s, ctx)).let { null }
            "ecoCare" ->
                run.apply(
                        Bonuses.catCare(
                            s,
                            Bonuses.Care.entries.first { it.key == text(arg(0)) },
                            ctx,
                        )
                    )
                    .let { null }
            "recordMoneyTransaction" ->
                run.apply(
                        Bonuses.recordMoney(
                            s,
                            text(arg(0)),
                            num(arg(1), args.size > 1),
                            text(arg(2)),
                            ctx,
                        )
                    )
                    .let { mapOf("ok" to it.ok, "message" to it.message) }
            "removeMoneyTransaction" -> run.apply(Bonuses.removeMoney(s, text(arg(0)), ctx))
            "moneyBalance" -> (s.money ?: Money()).balance
            "planToggle" -> {
                val date = text(arg(0))
                val day = GoldenContent.planDay(date)
                val result =
                    run.apply(
                        FixedAwards.tickPlan(
                            s,
                            date,
                            day,
                            text(arg(1)),
                            num(arg(2)).toInt(),
                            truthy(arg(3)),
                            GoldenContent.planLinks[day.kind].orEmpty(),
                            ctx,
                        )
                    )
                mapOf("saved" to result.saved, "message" to result.message)
            }
            "actStatus" ->
                FixedAwards.status(s, GoldenContent.catalog(text(arg(0))), ctx).let {
                    mapOf(
                        "blocked" to it.blocked,
                        "factor" to it.factor,
                        "n" to it.n.toDouble(),
                        "xp" to it.xp,
                    )
                }
            "confirmAct" -> {
                val input =
                    FixedAwards.CatalogInput(
                        text(dom["actDur"]),
                        text(dom["actRes"]),
                        text(dom["actNote"]),
                    )
                run.apply(FixedAwards.confirm(s, GoldenContent.catalog(text(arg(0))), input, ctx))
                    .let { null }
            }
            "undoCat" -> run.apply(FixedAwards.undo(s, text(arg(0)), ctx)).let { null }
            "saveBase" ->
                run.apply(
                        FixedAwards.setBase(
                            s,
                            text(arg(0)),
                            if (truthy(arg(1))) null else text(dom["actBase"]),
                            ctx,
                        )
                    )
                    .let { null }
            "jcAward" ->
                num(arg(0)).also { run.save = Coach.award(s, it, text(arg(1)), text(arg(2)), ctx) }
            "jcOnReflect" -> run.apply(Coach.reflect(s, text(arg(0)), text(arg(1)), ctx))
            "jcClaim" ->
                run.apply(Coach.claim(s, Coach.Claim.entries.first { it.key == text(arg(0)) }, ctx))
            "jcCatchUp" -> run.apply(Coach.catchUp(s, text(env["prompt"]), ctx)).let { null }
            "jcSaveReview" ->
                run.apply(Coach.saveReview(s, text(arg(0)), text(arg(1)), ctx)).let { null }
            "applyDecay" -> run.apply(Decay.apply(s, ctx))
            "getDecay" ->
                Decay.settings(s).let {
                    mapOf("on" to it.on, "grace" to it.grace, "rate" to it.rate)
                }
            "saveDecay" ->
                run.apply(
                        Decay.saveSettings(
                            s,
                            truthy(dom["decayOn"]),
                            text(dom["decayGrace"]),
                            text(dom["decayRate"]),
                            ctx,
                        )
                    )
                    .let { null }
            "lastTrained" -> Decay.lastTrained(s, text(arg(0)), ctx.clock)
            "ecoRestDay" -> Decay.isRestDay(s, text(arg(0)), ctx)
            "ecoRestUsed" -> Decay.restDaysUsed(s, ctx.clock).toDouble()
            "ecoTakeRest" -> run.apply(Decay.takeRest(s, ctx)).let { null }
            "streak" -> Decay.streak(s, ctx).toDouble()
            "ecoActive" -> Decay.isActive(s, text(arg(0)), ctx.clock)
            "ecoShieldTick" -> run.apply(Decay.shieldTick(s, ctx)).let { null }
            "saveRules" -> {
                val bases =
                    dom.filterKeys { it.startsWith("rule-") }
                        .mapKeys { it.key.removePrefix("rule-") }
                        .mapValues { text(it.value) }
                run.apply(SavePipeline.saveRuleBases(s, bases, ctx)).let { null }
            }
            "resetRules" -> run.apply(SavePipeline.resetRules(s, ctx)).let { null }
            else -> error("no golden mapping for $fn")
        }
    }

    private fun healthImport(o: Map<String, Any?>) =
        Health.Import(
            at = o.num("at").toLong(),
            type = text(o["type"]),
            name = text(o["name"]),
            minutes = o.num("duration").let { if (it.isNaN()) 0.0 else it },
            key = o.str("hc"),
            note = o.str("note"),
            rule =
                o["rule"]?.obj()?.let { r ->
                    Rule(
                        "",
                        text(r["icon"]),
                        Double.NaN,
                        text(r["skill"]),
                        text(r["primary"]),
                        text(r["secondary"]),
                    )
                },
            skill = o.str("skill"),
            primary = o.str("primary"),
            secondary = if (o.containsKey("secondary")) text(o["secondary"]) else null,
            xp = o["xp"]?.let { num(it) },
        )

    /**
     * The Health Connect plugin as a golden case fakes it: fixed answers, and [FAIL] for the read
     * named `fail`.
     */
    private class FakeHealthConnect(private val fake: Map<String, Any?>) : HealthConnect {
        private fun read(name: String) {
            if (fake["fail"] == name) throw IllegalStateException(FAIL)
        }

        override fun status(): HealthConnect.Status {
            read("status")
            val s = fake["status"].obj()
            return HealthConnect.Status(text(s["sdk"]), s["granted"].list().map(::text))
        }

        override fun exercise(from: Long, to: Long): List<HealthConnect.Workout> {
            read("exercise")
            return fake["exercise"].list().map { w ->
                w.obj().let {
                    HealthConnect.Workout(
                        text(it["id"]),
                        it.str("type"),
                        it.num("start").toLong(),
                        it.num("end").toLong(),
                        it.str("title"),
                        it.str("origin"),
                    )
                }
            }
        }

        override fun steps(days: Int): List<HealthConnect.StepDay> {
            read("steps")
            return fake["steps"].list().map { d ->
                d.obj().let { HealthConnect.StepDay(text(it["date"]), it.num("count")) }
            }
        }

        override fun sleep(from: Long, to: Long): List<HealthConnect.SleepSession> {
            read("sleep")
            return fake["sleep"].list().map { z ->
                z.obj().let {
                    HealthConnect.SleepSession(
                        text(it["id"]),
                        it.num("start").toLong(),
                        it.num("end").toLong(),
                    )
                }
            }
        }

        override fun weight(days: Int): HealthConnect.Weight? {
            read("weight")
            return fake["weight"]?.obj()?.let {
                HealthConnect.Weight(it["kg"]?.let { kg -> num(kg) }, it.num("time"))
            }
        }

        companion object {
            const val FAIL = "Health Connect stopped answering"
        }
    }
}
