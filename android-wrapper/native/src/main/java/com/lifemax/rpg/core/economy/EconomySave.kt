package com.lifemax.rpg.core.economy

/**
 * The part of the save this area reads or writes (section 2.3), plus the live [rules] table the
 * page keeps beside it. A field the save may lack is nullable when the spec treats "missing"
 * differently from "empty"; a number the save may lack or garble is NaN until a rule reads it
 * (NUM-3).
 */
data class EconomySave(
    val activities: List<Activity> = emptyList(),
    val attrXp: Map<String, Double> = Attribute.ids.associateWith { 0.0 },
    val skillXp: Map<String, Double> = emptyMap(),
    val rules: Rules = Rules.defaults,
    val customRules: Map<String, Rule>? = null,
    val restedXp: Double = Double.NaN,
    val sparks: Double = Double.NaN,
    val perk: String? = null,
    val perksOwned: List<String>? = null,
    val attrStars: Map<String, Double> = emptyMap(),
    val ascended: Map<String, Double> = emptyMap(),
    val decayTotal: Map<String, Double> = emptyMap(),
    val decayApplied: Map<String, Double>? = null,
    val decayLog: List<DecayEntry>? = null,
    val decay: DecaySettings? = null,
    val legacyCharXpBonus: Double = Double.NaN,
    val xpSchemaV2: Boolean = false,
    val quests: List<Quest> = emptyList(),
    val questDone: Map<String, String> = emptyMap(),
    val restDays: Map<String, Boolean> = emptyMap(),
    val restTokens: Double = Double.NaN,
    val restBoughtAt: Double = Double.NaN,
    val shields: Shields = Shields(),
    val themesOwned: List<String>? = null,
    val theme: String? = null,
    val toysBought: Map<String, Double>? = null,
    val nutriDone: Map<String, Double>? = null,
    val calorieGoal: Double = Double.NaN,
    val foodLog: List<FoodEntry> = emptyList(),
    val careDay: CareDay? = null,
    val money: Money? = null,
    val actXp: Map<String, Double> = emptyMap(),
    val planChecks: Map<String, PlanChecks> = emptyMap(),
    val health: HealthState = HealthState(),
    val weight: Double? = null,
    val devMode: Boolean = false,
    val rerollsUsed: Double = 0.0,
    val journal: JournalCoach = JournalCoach(),
    val dayReflections: Map<String, String> = emptyMap(),
) {
    /** PERK-1: a perk counts when it is equipped and owned. */
    fun hasPerk(id: String): Boolean = perk == id && perksOwned.orEmpty().contains(id)

    /** STAR-1. */
    fun stars(attr: String): Int = attrStars[attr].orNaN.orZero.toInt()

    /** CURVE-6. */
    fun attrXp(attr: String): Double = attrXp[attr].orNaN.orZero

    /** DERIVE-3. */
    val totalXp: Double
        get() = attrXp.values.sum()

    /** CURVE-5: ascended XP keeps counting toward the character. */
    val characterLevel: LevelProgress
        get() = Curve.Character.progress(totalXp + legacyCharXpBonus.orZero + ascended.values.sum())

    /** CURVE-6. */
    fun attrLevel(attr: String): LevelProgress = Curve.Attribute.progress(attrXp(attr))

    /** CURVE-9. */
    fun isLocked(quest: Quest): Boolean =
        (quest.stage ?: 1) > 1 && characterLevel.level < Levels.stageLevel(quest.stage)

    /**
     * DERIVE-4: a new record added to the caches in memory, as the award paths do before they save
     * (the save's rebuild then gives the same numbers). [skillGain] is what the skill gains, the
     * record's XP unless a path says otherwise [D11].
     */
    fun credited(record: Activity, skillGain: Double = record.xp): EconomySave {
        val skill = record.skill.orEmpty()
        return copy(
            activities = activities + record,
            attrXp =
                record.shares.fold(attrXp) { sums, s ->
                    sums + (s.attr to sums[s.attr].orNaN.orZero + s.xp)
                },
            skillXp = skillXp + (skill to skillXp[skill].orNaN.orZero + skillGain),
        )
    }

    /** DERIVE-1 and DERIVE-2: the caches rebuilt from the ledger. */
    fun rebuilt(): EconomySave =
        copy(attrXp = activities.attributeXp(decayTotal, ascended), skillXp = activities.skillXp())
}

/** A quest as the board stores it (QUEST-1 to QUEST-4). */
data class Quest(
    val id: String,
    val name: String,
    val cadence: String?,
    val xp: Double,
    val attribute: String? = null,
    val stage: Int? = null,
    val kind: String? = null,
    val desc: String? = null,
    val hand: Boolean = false,
    val verify: String? = null,
) {
    val isEpic: Boolean
        get() = cadence == "epic"

    val isWeekly: Boolean
        get() = cadence == "weekly"
}

/** One line of the decay log (DECAY-3): the day, the attribute and the XP it lost. */
data class DecayEntry(val date: String, val attr: String, val xp: Double)

/** The stored decay settings; [DecaySettings.effective] fills the defaults (DECAY-1). */
data class DecaySettings(
    val on: Boolean? = null,
    val grace: Double? = null,
    val rate: Double? = null,
) {
    fun effective(): DecaySettings = DecaySettings(on ?: true, grace ?: 4.0, rate ?: 3.0)
}

/** Streak shield bookkeeping (SHIELD-2). [tick] is null when the save has none yet. */
data class Shields(
    val count: Double = Double.NaN,
    val tick: Double? = null,
    val miss: Double = Double.NaN,
    val days: Map<String, Boolean>? = null,
    val milestone: Double = Double.NaN,
    val boughtAt: Double = Double.NaN,
)

/** A food log entry, as far as the nutrition bonus reads it (NUTRI-1). */
data class FoodEntry(val date: String?, val kcal: Double)

/** Today's cat care so far, and whether its bonus was paid (CARE-1). */
data class CareDay(
    val day: String,
    val feed: Boolean = false,
    val play: Boolean = false,
    val pet: Boolean = false,
    val paid: Boolean = false,
)

/** The savings account, as far as deposit XP reads it (MONEY-1). */
data class Money(
    val start: Double = 0.0,
    val transactions: List<MoneyTransaction> = emptyList(),
    val peakBalance: Double = Double.NaN,
) {
    /** MONEY-1 step 2: the balance, re-rounded to cents. */
    val balance: Double
        get() =
            jsRound(
                (start.orZero +
                    transactions.sumOf { if (it.type == "deposit") it.amount else -it.amount }) *
                    100
            ) / 100
}

/** A deposit or withdrawal; [actId] is the record its XP made (MONEY-1, UNDO-1). */
data class MoneyTransaction(
    val id: String,
    val type: String,
    val amount: Double,
    val note: String,
    val date: String,
    val actId: String? = null,
)

/** The ticks of one plan day: item key to one flag per required repetition (PLAN-1). */
data class PlanChecks(
    val ticks: Map<String, List<Boolean>> = emptyMap(),
    val actId: String? = null,
)

/** The journal coach's once-only marks (COACH-1). */
data class JournalCoach(
    val days: Map<String, Boolean> = emptyMap(),
    val reviews: Map<String, Review> = emptyMap(),
    val welcome: Map<String, Welcome> = emptyMap(),
) {
    /** A weekly (`W...`) or monthly (`M...`) review's answer, and whether it paid. */
    data class Review(val answer: String, val paid: Boolean = false)

    /**
     * A welcome-back day: the gap in days, and the chips already claimed (`pet`, `fresh`, `line`).
     */
    data class Welcome(val gap: Double, val claimed: Set<String> = emptySet())
}
