package com.lifemax.rpg.core.save

/**
 * The save fields the native economy models (ECONOMY_SPEC section 2.3): the top-level keys of the
 * page's save (`lifemax_rpg_prototype_v1`) that [EconomySaveCodec] reads into an `EconomySave` and
 * may write back. Every other key, and every nested key not listed in [PARTIAL], belongs to the
 * page and is carried through unchanged.
 */
object SaveFields {
    /** Every tracked top-level key, in the order the spec lists them. */
    val TRACKED: List<String> =
        listOf(
            "activities",
            "attrXP",
            "skillXP",
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
            "customRules",
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
            "careDay",
            "mealsMeta",
            "foodLog",
            "money",
            "actXP",
            "plan",
            "health",
            "weight",
            "devMode",
            "dailyDraw",
            "journalCoach",
            "dayReflections",
        )

    /**
     * Tracked keys that hold more than the economy owns, with the nested keys it models; the rest
     * of each object (and of each record in the lists named) is the page's and is never written.
     * Records of `activities`, `quests` and `money.tx` are followed by their `id`, so a record the
     * economy changes keeps its other keys too.
     */
    val PARTIAL: Map<String, List<String>> =
        mapOf(
            "mealsMeta" to listOf("calGoal"),
            "foodLog" to listOf("[].date", "[].kcal"),
            "money" to listOf("account", "start", "goal", "tx", "peakBalance"),
            "plan" to listOf("checks.*.s", "checks.*.v", "checks.*.actId"),
            "health" to
                listOf(
                    "on",
                    "ex",
                    "steps",
                    "sleep",
                    "wt",
                    "seen",
                    "last",
                    "since",
                    "msg",
                    "sdk",
                    "granted",
                    "src",
                    "today",
                    "wtTime",
                    "wtHist",
                ),
            "dailyDraw" to listOf("rerollsUsed"),
            "journalCoach" to
                listOf(
                    "days.*.xpAwarded",
                    "reviews.*.answer",
                    "reviews.*.paid",
                    "welcome.*.gap",
                    "welcome.*.claimed",
                    "welcome.*.tier",
                ),
        )
}

/**
 * How the tracked fields are read.
 * - [STRICT], for a save native may write: each tracked value must have a JSON type the page itself
 *   writes there (a number or null for a number, text for text, a boolean or number for a flag, an
 *   object or array where one belongs); anything else refuses the whole save.
 * - [AS_PAGE] reads every value the way the page's own rules do (`Number(v)`, `String(v)`,
 *   truthiness: ECONOMY_SPEC NUM-3), refusing nothing that is JSON. It exists to replay the page on
 *   saves it tolerates (the golden cases' malformed inputs, a later shadow comparison) and must not
 *   be used to decide what native writes.
 */
enum class SaveReading {
    STRICT,
    AS_PAGE,
}

/**
 * One reason a save was refused. Nothing here repeats a value from the save: [at] names the place
 * with fixed key names and array indexes, and `*` for a key the owner chose (a skill, a day).
 *
 * @property field the tracked top-level key, or empty for the save as a whole.
 * @property at the place inside [field] (`[3].xp`, `.checks.*.s`), empty for the field itself.
 * @property expected what belongs there.
 * @property found what is there (a JSON kind, or a short description).
 */
data class SaveProblem(val field: String, val at: String, val expected: String, val found: String)

/** Why the codec would not decode or encode a save. */
enum class SaveRefusalReason {
    /** The stored text is not JSON (`JSON.parse` would throw). */
    NOT_JSON,

    /** The stored JSON is not an object. */
    NOT_AN_OBJECT,

    /** A tracked field has a type the page does not write there ([SaveReading.STRICT]). */
    WRONG_TYPE,

    /** The model holds NaN or an infinity where a value changed; the page would store null. */
    NON_FINITE_NUMBER,

    /**
     * Encoding failed its own check: reading the text back did not give the same economy, or a
     * field native does not own came out different. Nothing may be written.
     */
    NOT_FAITHFUL,

    /** An unexpected failure inside the codec, named by its class only. */
    INTERNAL,
}

/** A refusal: the save is left exactly as stored, and [problems] say why. */
data class SaveRefusal(val reason: SaveRefusalReason, val problems: List<SaveProblem>)
