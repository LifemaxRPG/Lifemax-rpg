package com.lifemax.rpg.core.economy

/** The page's functions, as the golden cases call them, mapped onto this module. */
internal object GoldenCalls {
    fun dispatch(run: GoldenRun, fn: String, args: List<Any?>, env: Map<String, Any?>): Any? {
        val inputs = run.inputs(env)
        val ctx = inputs.economy
        val s = run.save
        fun arg(i: Int) = args.getOrNull(i)
        fun quest(i: Int) = s.quests.first { it.id == text(arg(i)) }
        return when (fn) {
            "ECO" -> eco()
            "DEFAULT_RULES" -> Rules.defaults.byType.mapValues { ruleJson(it.value) }
            "ECO_DURATION_RULES" -> Eco.DURATION_RULES
            "xpForLevel" -> Curve.Attribute.step(num(arg(0)).toInt())
            "charXpForLevel" -> Curve.Character.step(num(arg(0)).toInt())
            "charCumXP" -> Levels.characterCumulative(num(arg(0)).toInt())
            "levelData" ->
                level(
                    (if (arg(1) == "charXpForLevel") Curve.Character else Curve.Attribute).progress(
                        num(arg(0), args.isNotEmpty())
                    )
                )
            "xpToGainLevels" -> Levels.xpToGain(num(arg(0)), num(arg(1)).toInt())
            "stageLvl" ->
                Levels.stageLevel(num(arg(0)).takeUnless { it.isNaN() }?.toInt()).toDouble()
            "questLocked" -> s.isLocked(quest(0))
            "characterLevel" -> level(s.characterLevel)
            "totalXP" -> s.totalXp
            "attrLevel" -> level(s.attrLevel(text(arg(0))))
            "skillLevel" -> level(Curve.Attribute.progress(s.skillXp[text(arg(0))].orNaN.orZero))
            "dayNum" -> EconomyClock.dayNumber(text(arg(0)))?.toDouble() ?: Double.NaN
            "ecoDayKey" -> EconomyClock.dayKey(num(arg(0)).toLong())
            "getToday" -> ctx.today
            "activityDate" -> ctx.clock.dayOf(arg(0).obj().str("date"))
            "ecoDur" -> durationFactor(num(arg(0), args.isNotEmpty()))
            "ecoPassive" -> SaveCodec.activity(arg(0).obj()).isPassive
            "ecoPerk" -> s.hasPerk(text(arg(0)))
            "ecoCap" ->
                cap(
                    num(arg(0)),
                    if (args.size > 2) num(arg(2)) else s.activities.dayXp(text(arg(1)), ctx.clock),
                )
            "ecoDayXP" -> s.activities.dayXp(text(arg(0)), ctx.clock)
            "ecoRestedPeek" -> s.restedPeek(num(arg(0)))
            "ecoRestedTake" ->
                s.restedTake(num(arg(0))).let { (xp, after) ->
                    run.save = after
                    xp
                }
            "ecoStarXP" -> s.starXp(text(arg(0)), num(arg(1)))
            "ecoAward" -> {
                val o = arg(3).obj()
                val award =
                    run.apply(
                        s.award(
                            text(arg(0)),
                            num(arg(1)),
                            num(arg(2), args.size > 2),
                            ctx,
                            o.str("day"),
                            truthy(o["manual"]),
                            truthy(o["peek"]),
                        )
                    )
                mapOf("xp" to award.xp, "msg" to award.message)
            }
            "activityPreview" ->
                LogActivity.preview(s.rules, draft(arg(0))).let {
                    mapOf(
                        "total" to it.total,
                        "primary" to it.primary,
                        "secondary" to it.secondary,
                        "skill" to it.skill,
                        "durFactor" to it.durationFactor,
                    )
                }
            "activityAward" ->
                run.apply(LogActivity.award(s, draft(arg(0)), ctx, truthy(arg(1)))).let {
                    mapOf("xp" to it.xp, "split" to shares(it.split), "msg" to it.message)
                }
            "recordActivity" -> logResult(run.apply(LogActivity.record(s, draft(arg(0)), ctx)))
            "NativeLogProjection.clean" -> cleaned(LogActivity.clean(s.rules, rawDraft(arg(0))))
            "NativeLogProjection.commands.submit" -> {
                val o = arg(0).obj()
                val result =
                    run.apply(
                        LogActivity.submit(s, rawDraft(o["draft"]), o.str("operationId"), ctx)
                    )
                if (!result.ok) mapOf("ok" to false, "message" to result.message)
                else
                    mapOf(
                        "ok" to true,
                        "message" to result.message,
                        "xp" to result.xp,
                        "navigate" to "dashboard",
                    )
            }
            "questKey" -> Quests.key(quest(0), ctx.clock)
            "isQuestDone" -> Quests.isDone(s, quest(0), ctx.clock)
            "toggleQuest" ->
                run.apply(
                        Quests.toggle(
                            s,
                            text(arg(0)),
                            truthy(arg(1)),
                            ctx,
                            text(env["dom"].obj()["questNote"]),
                        )
                    )
                    .let { null }
            "ecoQuestXP" ->
                Quests.xpFor(s, quest(0), ctx).let { (xp, after) ->
                    run.save = after
                    xp
                }
            "createQuest" -> {
                val dom = env["dom"].obj()
                run.apply(
                        Quests.create(
                            s,
                            text(dom["newQuestName"]),
                            text(dom["newQuestCadence"]),
                            text(dom["newQuestXP"]),
                            ctx,
                        )
                    )
                    .let { null }
            }
            "ecoSparks" -> Sparks.balance(s)
            "ecoSparksSync" -> run.save = Sparks.synced(s)
            "ecoSpend" -> run.apply(Sparks.spend(s, num(arg(0))))
            "ecoBuy" ->
                run.apply(Sparks.buy(s, shopItem(text(arg(0))), text(arg(1)), ctx)).let { null }
            "dhRerollCost" -> DailyHand.rerollCost(s)
            "dhReroll" -> {
                val card =
                    run.raw["dailyDraw"]
                        .obj()["cards"]
                        .list()
                        .getOrNull(num(arg(0)).toInt())
                        .obj()
                        .str("id")
                run.apply(DailyHand.reroll(s, card, ctx))
            }
            "ecoAscend" ->
                run.apply(Ascension.ascend(s, text(arg(0)), ctx, env["confirm"] != false)).let {
                    null
                }
            "save" -> run.apply(SavePipeline.save(s, ctx)).also { run.redrawn() }
            "rebuildAttrXP" ->
                run.save = s.copy(attrXp = s.activities.attributeXp(s.decayTotal, s.ascended))
            "rebuildSkillXP" -> run.save = s.copy(skillXp = s.activities.skillXp())
            "ecoMigrate" -> run.save = SavePipeline.migrated(s, ctx)
            "load" -> load(run, env, ctx)
            "deleteActivity" ->
                run.apply(
                        SavePipeline.deleteActivity(s, text(arg(0)), ctx, env["confirm"] != false)
                    )
                    .let { null }
            else ->
                GoldenPaths.dispatch(
                    run,
                    fn,
                    args,
                    env,
                    ctx,
                    inputs.health.takeIf { env["health"] != null },
                )
        }.let { if (it is Unit) null else it }
    }

    private fun load(run: GoldenRun, env: Map<String, Any?>, ctx: EconomyContext): Any? {
        val stored = env["stored"].obj()
        // The stored save is merged over the fresh state one level deep (attrXP keeps all eight
        // keys).
        val merged =
            GoldenContent.freshState +
                stored.mapValues { (key, value) ->
                    val fresh = GoldenContent.freshState[key]
                    if (fresh is Map<*, *> && value is Map<*, *>) fresh.obj() + value.obj()
                    else value
                }
        val loaded =
            SavePipeline.load(
                SaveCodec.decode(merged),
                ctx,
                fromNewerBuild = num(stored["schema"]) > GoldenContent.SCHEMA,
            )
        run.raw = merged
        run.document = SaveCodec.document(merged)
        run.save = loaded.save
        run.readOnly = loaded.readOnly
        return null
    }

    fun eco() =
        linkedMapOf(
            "charBase" to Eco.CHAR_BASE,
            "charSlope" to Eco.CHAR_SLOPE,
            "softCap" to Eco.SOFT_CAP,
            "hardCap" to Eco.HARD_CAP,
            "minMin" to Eco.MIN_MINUTES,
            "maxPerType" to Eco.MAX_PER_TYPE.toDouble(),
            "maxManualMin" to Eco.MAX_MANUAL_MINUTES,
            "restedCap" to Eco.RESTED_CAP,
            "restedRate" to Eco.RESTED_RATE,
            "restedBonus" to Eco.RESTED_BONUS,
            "dailyQuests" to Eco.DAILY_QUESTS.toDouble(),
            "weeklyQuests" to Eco.WEEKLY_QUESTS.toDouble(),
            "epicPerDay" to Eco.EPIC_PER_DAY.toDouble(),
            "depositMin" to Eco.DEPOSIT_MIN,
            "depositCapDay" to Eco.DEPOSIT_CAP_DAY,
            "depositCapWeek" to Eco.DEPOSIT_CAP_WEEK,
            "restPerWeek" to Eco.REST_PER_WEEK.toDouble(),
        )

    fun ruleJson(r: Rule) =
        linkedMapOf(
            "name" to r.name,
            "icon" to r.icon,
            "base" to r.base,
            "skill" to r.skill,
            "primary" to r.primary,
            "secondary" to r.secondary,
            "desc" to r.desc,
        )

    fun level(p: LevelProgress) =
        mapOf("level" to p.level.toDouble(), "into" to p.into, "needed" to p.needed, "pct" to p.pct)

    fun shares(split: List<XpShare>) = split.map { mapOf("attr" to it.attr, "xp" to it.xp) }

    private fun shopItem(kind: String) =
        when (kind) {
            "theme" -> Sparks.Item.Theme
            "perk" -> Sparks.Item.Perk
            "toy" -> Sparks.Item.Toy
            "rest" -> Sparks.Item.RestToken
            "shield" -> Sparks.Item.ShieldRefill
            else -> Sparks.Item.Unknown
        }

    fun draft(value: Any?): LogDraft {
        val d = value.obj()
        return LogDraft(
            type = text(d["type"]),
            customName = text(d["customName"]),
            duration = text(d["duration"]),
            quality = text(d["quality"]),
            skill = text(d["skill"]),
            primary = text(d["primary"]),
            secondary = text(d["secondary"]),
            notes = text(d["notes"]),
            opId = d["opId"]?.takeIf(::truthy)?.let(::text),
        )
    }

    private fun rawDraft(value: Any?): Map<String, String?> =
        value.obj().mapValues { it.value?.let(::text) }

    private fun cleaned(d: LogDraft) =
        linkedMapOf(
            "type" to d.type,
            "customName" to d.customName,
            "duration" to d.duration,
            "quality" to d.quality,
            "skill" to d.skill,
            "primary" to d.primary,
            "secondary" to d.secondary,
            "notes" to d.notes,
        )

    private fun logResult(r: LogResult): Map<String, Any?> =
        linkedMapOf<String, Any?>("ok" to r.ok).apply {
            r.xp?.let { put("xp", it) }
            put("message", r.message)
            if (r.duplicate) put("duplicate", true)
        }
}
