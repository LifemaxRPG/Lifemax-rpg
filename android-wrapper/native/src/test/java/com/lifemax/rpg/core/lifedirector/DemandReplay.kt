package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.golden.GoldenFiles
import com.lifemax.rpg.core.lifedirector.KernelGolden.array
import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.number
import com.lifemax.rpg.core.lifedirector.KernelGolden.string
import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The demand golden cases, `ld-demand-golden.json.gz` (recorded from the page in Chromium; format
 * in `tools/gen_ld_demand_golden.js`), and the adapter table that runs each page function name on
 * the native demand code and writes its result as the page's JSON ([DemandJson]). Page names appear
 * only here and there. A [Mutation] replaces entries, for the mutation spot checks.
 */
object DemandReplay {
    const val FILE = "ld-demand-golden.json.gz"

    /** Every case, in the file's order. */
    val cases: List<KernelGolden.Case> by lazy {
        val read = JsonReader.read(GoldenFiles.text(FILE))
        check(read is JsonRead.Parsed) { "$FILE does not parse: $read" }
        ((read.value as JsonObject)["cases"] as JsonArray).items.map {
            val c = it as JsonObject
            KernelGolden.Case(
                KernelGolden.text(c["id"]),
                KernelGolden.text(c["family"]),
                KernelGolden.text(c["rule"]),
                KernelGolden.text(c["fn"]),
                (c["args"] as JsonArray).items.map(KernelGolden::decode),
                KernelGolden.decode(c["out"]!!),
            )
        }
    }

    /** Entries to use instead of the real ones. */
    class Mutation(val replace: Map<String, (List<JsonValue>) -> JsonValue> = emptyMap())

    private fun num(v: JsonValue?): Double = PageValues.number(v)

    private fun items(v: JsonValue?): List<JsonValue> = (v as JsonArray).items

    private fun m(v: JsonValue?, key: String): JsonValue? = (v as JsonObject)[key]

    private fun zone(v: JsonValue?): PageZone =
        requireNotNull(ZoneNames.resolve((v as JsonString).value))

    private fun quests(v: JsonValue?): List<PlayerQuest> = items(v).map(PlayerQuests::of)

    private fun used(v: JsonValue?): Set<String> = items(v).map { (it as JsonString).value }.toSet()

    private fun goalInfo(v: JsonValue?): Map<String, GoalInfo> =
        (v as JsonObject).members.mapValues { (_, g) ->
            GoalInfo(num(m(g, "lag")), num(m(g, "priority")), PageValues.text(m(g, "title")))
        }

    private fun compile(c: JsonValue?): JsonValue {
        val prefs = PlanPreferences.of(m(c, "prefs"))
        val days = PlanHorizon.days(num(m(c, "start")), num(m(c, "days")).toInt(), zone(m(c, "tz")))
        val plans = GoalPlans.expand(m(c, "goals"), prefs["intensityLevel"], num(m(c, "now"))).plans
        val set =
            Candidates.compile(
                plans,
                m(c, "quests"),
                CandidateIds.weeks(days),
                num(m(c, "now")),
                days,
                items(m(c, "history")),
                items(m(c, "kept")),
                used(m(c, "used")),
            )
        return DemandJson.candidates(set)
    }

    private fun prepare(a: List<JsonValue>): JsonValue {
        val t = a[3]
        val demand =
            TaskDemand(
                m(t, "dOpt"),
                m(t, "dMin"),
                m(t, "priority"),
                m(t, "questWeight"),
                m(t, "goalId"),
                m(t, "deadlineEpoch"),
                m(t, "IP"),
                m(t, "IC"),
            )
        return DemandJson.prepared(
            TaskPreparation.prepare(demand, goalInfo(a[4]), num(a[0]), num(a[1]), num(a[2]))
        )
    }

    private fun drain(spec: JsonValue?): JsonValue {
        val n = num(m(spec, "n")).toInt()
        val g = EnergyGrid(n)
        g.drainPhysical.fill(FILL_DRAIN)
        g.drainCognitive.fill(FILL_DRAIN)
        g.runAhead.fill(FILL_RUN)
        g.runBack.fill(FILL_RUN)
        items(m(spec, "slots")).forEachIndexed { i, slot ->
            val s = items(slot).map { num(it).toInt() }
            g.type[i] = s[0]
            g.load[i] = s[1]
            g.tag[i] = s[2]
            g.taskPhysical[i] = s[3]
            g.taskCognitive[i] = s[4]
        }
        g.restorative = items(m(spec, "blocks")).map { PageValues.truthy(m(it, "restorative")) }
        WakeCycleDrain.recompute(
            g,
            num(m(spec, "nowSlot")),
            num(m(spec, "startSlot")).toInt(),
            num(m(spec, "endSlot")).toInt(),
        )
        return DemandJson.drain(g)
    }

    private const val FILL_DRAIN = 0.25f
    private const val FILL_RUN: Short = -1

    /** Every page function the cases call, run natively. */
    val ADAPTERS: Map<String, (List<JsonValue>) -> JsonValue> =
        mapOf(
            "tagMatches" to { a -> bool(ActivityTags.matches(a.getOrNull(0), a.getOrNull(1))) },
            "tagDepth" to { a -> number(ActivityTags.depth((a[0] as JsonString).value, a[1])) },
            "tagProfile" to { a -> DemandJson.profile(ActivityTags.profile(a[0])) },
            "normalizeGoal" to { a -> DemandJson.goal(PlayerGoals.of(a.getOrNull(0))) },
            "suggestTemplates" to { a -> array(PlayerGoals.suggestTemplates(a[0]).map(::string)) },
            "goalTemplates" to
                { a ->
                    array(PlayerGoals.templates(PlayerGoals.of(a[0])).map(DemandJson::template))
                },
            "expandGoalActivities" to
                { a ->
                    DemandJson.expansion(
                        GoalPlans.expand(
                            a[0],
                            PlanPreferences.of(a[1])["intensityLevel"],
                            num(a[2]),
                        )
                    )
                },
            "goalInfoMap" to { a -> DemandJson.info(GoalPlans.info(a[0], num(a[1]))) },
            "normalizeQuest" to { a -> DemandJson.quest(PlayerQuests.of(a.getOrNull(0))) },
            "findQuestCycles" to { a -> array(PlayerQuests.cycles(quests(a[0])).map(::string)) },
            "freshIds" to
                { a ->
                    val set = if (PageValues.present(a[0])) used(a[0]) else null
                    array(
                        CandidateIds.fresh(set, (a[1] as JsonString).value, num(a[2])).map(::string)
                    )
                },
            "weeksOf" to
                { a ->
                    DemandJson.weeks(
                        CandidateIds.weeks(
                            PlanHorizon.days(num(a[0]), num(a[1]).toInt(), zone(a[2]))
                        )
                    )
                },
            "compileCandidates" to { a -> compile(a[0]) },
            "questCoverage" to
                { a ->
                    val status = a[2] as JsonObject
                    val planned =
                        items(a[1]).map { p ->
                            items(m(p, "linkedQuestRequirementIds")).map { m(it, "questId") }
                        }
                    DemandJson.coverage(PlayerQuests.coverage(quests(a[0]), planned) { status[it] })
                },
            "prepTask" to ::prepare,
            "recomputeBwc" to { a -> drain(a[0]) },
            "exp" to { a -> number(PageMath.exp(num(a[0]))) },
            "pow" to { a -> number(PageMath.pow(num(a[0]), num(a[1]))) },
            "square" to { a -> number(num(a[0]) * num(a[0])) },
            "demand" to { a -> DemandJson.demand(PlanDemand.of(a[0], items(a[1]), used(a[2]))) },
        )

    /** What the native code gives for [case], written as the page writes its answer. */
    fun run(case: KernelGolden.Case, mutation: Mutation = Mutation()): JsonValue {
        val adapter = mutation.replace[case.fn] ?: ADAPTERS.getValue(case.fn)
        return try {
            adapter(case.args)
        } catch (e: PageTypeError) {
            threw("TypeError")
        } catch (e: PageRangeError) {
            threw("RangeError")
        } catch (e: EngineRefusal) {
            threw("Error")
        }
    }

    /** Null when [case] gives the page's answer, else a description of the difference. */
    fun difference(case: KernelGolden.Case, mutation: Mutation = Mutation()): String? {
        val got =
            try {
                run(case, mutation)
            } catch (e: RuntimeException) {
                return "${case.id}: threw ${e::class.simpleName}: ${e.message}"
            }
        return if (got == case.out) null
        else
            "${case.id}: expected ${KernelGolden.show(case.out)}\n      got ${KernelGolden.show(got)}"
    }

    /** A number value, for mutations. */
    fun n(value: Double): JsonValue = JsonNumber(value)
}
