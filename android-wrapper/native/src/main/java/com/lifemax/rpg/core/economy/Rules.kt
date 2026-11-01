package com.lifemax.rpg.core.economy

/** The eight attributes, in display order (section 2.1). */
enum class Attribute(val id: String, val title: String) {
    Physique("physique", "Physique"),
    Endurance("endurance", "Endurance"),
    Intelligence("intelligence", "Intelligence"),
    Business("business", "Business"),
    Social("social", "Social"),
    Discipline("discipline", "Discipline"),
    Adventure("adventure", "Adventure"),
    Wellbeing("wellbeing", "Wellbeing");

    companion object {
        val ids: List<String> = entries.map { it.id }

        fun of(id: String?): Attribute? = entries.firstOrNull { it.id == id }

        /** The short name a message shows; an unknown id is shown as itself. */
        fun shortName(id: String): String = of(id)?.title ?: id
    }
}

/** The economy's constants (`ECO`, section 3). */
object Eco {
    const val CHAR_BASE = 200.0
    const val CHAR_SLOPE = 75.0
    const val SOFT_CAP = 180.0
    const val HARD_CAP = 240.0
    const val MIN_MINUTES = 5.0
    const val MAX_PER_TYPE = 3
    const val MAX_MANUAL_MINUTES = 360.0
    const val RESTED_CAP = 300.0
    const val RESTED_RATE = 0.5
    const val RESTED_BONUS = 0.5
    const val DAILY_QUESTS = 3
    const val WEEKLY_QUESTS = 3
    const val EPIC_PER_DAY = 1
    const val DEPOSIT_MIN = 50.0
    const val DEPOSIT_CAP_DAY = 25.0
    const val DEPOSIT_CAP_WEEK = 50.0
    const val REST_PER_WEEK = 2

    /** DUR-2. */
    const val DURATION_RULES = "30 min = base XP, up to 3× at 120 min. Under 5 min earns nothing."
}

/**
 * How one activity type scores (RULES-1). [base] is a double because a stored rule may hold
 * anything.
 */
data class Rule(
    val name: String,
    val icon: String,
    val base: Double,
    val skill: String,
    val primary: String,
    val secondary: String,
    val desc: String = "",
)

/** The live rules table: the defaults merged with the save's own entries (RULES-1). */
data class Rules(val byType: Map<String, Rule>) {
    /** `rules[type] || rules.custom`. */
    fun of(type: String?): Rule = byType[type] ?: byType.getValue("custom")

    operator fun contains(type: String?): Boolean = type in byType

    /** RULES-1: a stored entry replaces the default whole; a stored type of its own is added. */
    fun mergedWith(stored: Map<String, Rule>): Rules = Rules(byType + stored)

    companion object {
        /** RULES-2. */
        val defaults =
            Rules(
                linkedMapOf(
                    "reading" to
                        Rule(
                            "Focused reading / study",
                            "book",
                            24.0,
                            "Knowledge",
                            "intelligence",
                            "discipline",
                            "Practice focused learning",
                        ),
                    "business" to
                        Rule(
                            "Focused business work",
                            "briefcase",
                            25.0,
                            "Entrepreneurship",
                            "business",
                            "discipline",
                            "Build something valuable",
                        ),
                    "strength" to
                        Rule(
                            "Strength workout",
                            "dumbbell",
                            28.0,
                            "Lifting",
                            "physique",
                            "discipline",
                            "Strength and resistance training",
                        ),
                    "running" to
                        Rule(
                            "Run / cardio",
                            "heart",
                            26.0,
                            "Running",
                            "endurance",
                            "wellbeing",
                            "Cardio or running session",
                        ),
                    "language" to
                        Rule(
                            "Language practice",
                            "chat",
                            24.0,
                            "Spanish",
                            "intelligence",
                            "social",
                            "Build a language skill",
                        ),
                    "recovery" to
                        Rule(
                            "Sleep / recovery routine",
                            "moon",
                            16.0,
                            "Recovery Routine",
                            "wellbeing",
                            "endurance",
                            "Complete a planned recovery routine",
                        ),
                    "practical" to
                        Rule(
                            "Practical skill practice",
                            "mountain",
                            24.0,
                            "Practical Skills",
                            "adventure",
                            "intelligence",
                            "Practice a useful hands-on skill",
                        ),
                    "task" to
                        Rule(
                            "Difficult planned task",
                            "target",
                            20.0,
                            "Execution",
                            "discipline",
                            "business",
                            "Follow through on a meaningful task",
                        ),
                    "custom" to
                        Rule(
                            "Custom activity",
                            "star",
                            20.0,
                            "Custom Skill",
                            "adventure",
                            "",
                            "Your own activity",
                        ),
                )
            )
    }
}
