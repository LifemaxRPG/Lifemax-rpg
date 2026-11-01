package com.lifemax.rpg.core.economy

/**
 * Page content outside the economy that some golden cases depend on (spec section 1.3: content, not
 * rules). Each entry is what the recorded cases show of it; nothing here is a rule.
 */
internal object GoldenContent {
    /**
     * The page's save schema: a stored save with a higher one comes from a newer build (SAVE-5).
     */
    const val SCHEMA = 1.0

    private val defaultQuests =
        listOf(
            quest(
                "q1",
                "Learn for 20 minutes",
                "Focused study or reading. Show up or the attribute stalls.",
                "daily",
                15,
                "learning",
            ),
            quest(
                "q2",
                "Move with intention",
                "Train or move with intent. Recovery only counts if you actually recover.",
                "daily",
                15,
                "movement",
            ),
            quest(
                "q3",
                "Advance a priority",
                "Take one concrete step on an important project.",
                "daily",
                20,
                "business",
            ),
            quest(
                "q4",
                "Three meaningful sessions",
                "Complete three workouts or planned movement sessions this week.",
                "weekly",
                35,
                "fitness",
            ),
            quest(
                "q5",
                "Weekly reset",
                "Review your progress and choose next week’s priorities.",
                "weekly",
                25,
                "review",
            ),
            quest(
                "q6",
                "First proof of progress",
                "Complete a personal milestone and record the result.",
                "epic",
                75,
                "milestone",
            ),
        )

    private fun quest(
        id: String,
        name: String,
        desc: String,
        cadence: String,
        xp: Int,
        kind: String,
    ) =
        linkedMapOf(
            "id" to id,
            "name" to name,
            "desc" to desc,
            "cadence" to cadence,
            "xp" to xp.toDouble(),
            "kind" to kind,
        )

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
            "journalCoach" to
                linkedMapOf(
                    "days" to emptyMap<String, Any?>(),
                    "reviews" to emptyMap<String, Any?>(),
                    "cooldown" to emptyMap<String, Any?>(),
                    "pats" to emptyMap<String, Any?>(),
                    "welcome" to emptyMap<String, Any?>(),
                ),
            "dayReflections" to emptyMap<String, Any?>(),
        )
}
