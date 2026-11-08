package com.lifemax.rpg.core.economy

import com.lifemax.rpg.core.quests.ContentQuest
import com.lifemax.rpg.core.quests.QuestContentLoader
import com.lifemax.rpg.core.quests.QuestContentRead

/**
 * Page content outside the economy that some golden cases depend on (spec section 1.3: content, not
 * rules). The default quests come from the page's content file; the rest are what the recorded
 * cases show of it. Nothing here is a rule.
 */
internal object GoldenContent {
    /**
     * The page's save schema: a stored save with a higher one comes from a newer build (SAVE-5).
     */
    const val SCHEMA = 1.0

    /**
     * `freshState().quests`, read from the page's own content file through the production loader
     * ([QuestContentLoader]); a file that does not load fails every case that starts from a fresh
     * save, loudly.
     */
    private val defaultQuests: List<Map<String, Any?>> =
        when (val read = QuestContentLoader.packaged) {
            is QuestContentRead.Loaded -> read.content.defaultQuests.map(::questMap)
            is QuestContentRead.Refused ->
                error("quest content refused: ${read.path}: ${read.reason}")
        }

    /** A quest as the page stores it: its keys in the page's order, only those the page has. */
    private fun questMap(q: ContentQuest): Map<String, Any?> =
        linkedMapOf<String, Any?>(
                "id" to q.id,
                "name" to q.name,
                "desc" to q.desc,
                "cadence" to q.cadence,
                "xp" to q.xp.toDouble(),
                "kind" to q.kind,
            )
            .apply {
                q.attribute?.let { put("attribute", it) }
                q.day?.let { put("day", it) }
                q.stage?.let { put("stage", it.toDouble()) }
            }

    /** The attribute-sheet entries the cases use (`ACTS`); icons are their attribute's. */
    private val catalogEntries =
        listOf(
            FixedAwards.CatalogEntry(
                "physique.complete-a-strength-workout",
                "Complete a Strength Workout",
                "physique",
                "dumbbell",
                35.0,
                "Lifting",
                planKinds = listOf("strength"),
            ),
            FixedAwards.CatalogEntry(
                "physique.reach-a-strength-milestone",
                "Reach a Strength Milestone",
                "physique",
                "dumbbell",
                40.0,
                "Lifting",
                milestone = true,
            ),
            FixedAwards.CatalogEntry(
                "endurance.complete-a-cardio-session",
                "Complete a Cardio Session",
                "endurance",
                "heart",
                25.0,
                "Cardio",
                minMinutes = 20.0,
                planKinds = listOf("cardio", "athletic"),
            ),
            FixedAwards.CatalogEntry(
                "business.review-your-spending",
                "Review Your Spending",
                "business",
                "briefcase",
                15.0,
                daily = true,
            ),
            FixedAwards.CatalogEntry(
                "business.create-or-update-a-budget",
                "Create or Update a Budget",
                "business",
                "briefcase",
                20.0,
                weekly = true,
            ),
            FixedAwards.CatalogEntry(
                "business.earn-your-first-customer-or-sale",
                "Earn Your First Customer or Sale",
                "business",
                "briefcase",
                50.0,
                once = true,
            ),
            FixedAwards.CatalogEntry(
                "business.transfer-money-into-savings",
                "Transfer Money Into Savings",
                "business",
                "briefcase",
                15.0,
                savings = true,
            ),
            FixedAwards.CatalogEntry(
                "dev.boost1.physique",
                "Dev: +1 level",
                "physique",
                "dumbbell",
                Double.NaN,
                "Developer",
                boostLevels = 1,
            ),
            FixedAwards.CatalogEntry(
                "dev.boost2.physique",
                "Dev: +2 levels",
                "physique",
                "dumbbell",
                Double.NaN,
                "Developer",
                boostLevels = 2,
            ),
        )

    fun catalog(id: String): FixedAwards.CatalogEntry = catalogEntries.first { it.id == id }

    /** `PLAN_LINK`: the catalog entries a plan kind stands for. */
    val planLinks =
        mapOf(
            "strength" to listOf("physique.complete-a-strength-workout"),
            "cardio" to listOf("endurance.complete-a-cardio-session"),
            "athletic" to listOf("endurance.complete-a-cardio-session"),
        )

    /** The days of the default (foundation) plan that the cases tick, from `planDay`. */
    fun planDay(date: String): FixedAwards.PlanDay =
        when (date) {
            "2026-10-27" ->
                FixedAwards.PlanDay(
                    "cardio",
                    "Cardio A — Aerobic Conditioning",
                    "Cardio A",
                    "heart",
                    30.0,
                    "endurance",
                    "wellbeing",
                    listOf(FixedAwards.PlanItem("C1", 1)),
                )
            "2026-10-29" ->
                FixedAwards.PlanDay(
                    "recovery",
                    "Active Recovery & Mobility",
                    "Active Recovery",
                    "moon",
                    20.0,
                    "wellbeing",
                    "",
                    (1..6).map { FixedAwards.PlanItem("M$it", 1) },
                )
            else -> error("no plan day recorded for $date")
        }

    /** `freshState()`, as far as the recorded keys go: what `load()` merges a stored save over. */
    val freshState: Map<String, Any?> =
        linkedMapOf(
            "activities" to emptyList<Any?>(),
            "attrXP" to Attribute.ids.associateWith { 0.0 },
            "skillXP" to emptyMap<String, Any?>(),
            "customRules" to emptyMap<String, Any?>(),
            "quests" to defaultQuests,
            "questDone" to emptyMap<String, Any?>(),
            "dayReflections" to emptyMap<String, Any?>(),
        )
}
