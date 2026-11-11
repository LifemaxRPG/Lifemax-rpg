package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.jsTrim
import java.util.Locale

/**
 * The host's word rules (LIFE_DIRECTOR_ADAPTERS_SPEC HA-3 to HA-8): the tag tables, the counts and
 * minutes read out of a quest's words, and which quests are milestones or untimed cards.
 *
 * The page's patterns are JavaScript regular expressions with the `i` flag and `\b`, `\w`, `\s`,
 * `\d`. Kotlin's (and Android's ICU) differ from JavaScript on all four outside ASCII, so every
 * pattern here is written with explicit classes: a word character is `[A-Za-z0-9_]`, white space is
 * JavaScript's own set ([SPACE]), and the `i` flag is applied by folding `A`-`Z` to lower case
 * first ([asciiLower]); a JavaScript `i` pattern of ASCII letters matches only ASCII letters, so
 * this is the same.
 */
object HostTexts {
    /** `KIND_TAG` (engine.js:2383): a quest's kind to a Life Director tag. */
    val KIND_TAG: Map<String, String> =
        linkedMapOf(
            "learning" to "study",
            "knowledge" to "study",
            "recall" to "study.review",
            "synthesis" to "study",
            "research" to "study",
            "logic" to "study",
            "focus" to "study.deep",
            "problem_solving" to "study",
            "reading" to "reading",
            "writing" to "creative.writing",
            "articulation" to "language.speaking",
            "skill" to "practical",
            "movement" to "fitness",
            "fitness" to "fitness",
            "strength" to "fitness.strength",
            "reps" to "fitness.strength",
            "active" to "fitness",
            "cardio" to "fitness.cardio",
            "endurance" to "fitness.cardio",
            "business" to "business",
            "building" to "business",
            "shipping" to "business",
            "outreach" to "business",
            "networking" to "social",
            "connection" to "social",
            "brainstorming" to "business",
            "finance" to "admin.finance",
            "audit" to "admin.finance",
            "review" to "admin.review",
            "reflection" to "admin.review",
            "planning" to "admin.review",
            "organization" to "chores",
            "environment" to "chores",
            "exploration" to "practical.outdoor",
            "gear" to "practical",
            "safety" to "practical",
            "recovery" to "wellbeing",
            "custom" to "general",
            "milestone" to "general",
        )

    /** `PASSIVE_KINDS` (engine.js:2391): kinds Life Director never plans time for. */
    val PASSIVE_KINDS: Set<String> =
        setOf(
            "hydration",
            "nutrition",
            "sleep",
            "rest",
            "warmup",
            "check",
            "consistency",
            "minutes",
            "seconds",
        )

    /** `ATTR_TAG` (engine.js:2392): a quest's attribute to a tag. */
    val ATTR_TAG: Map<String, String> =
        linkedMapOf(
            "physique" to "fitness",
            "endurance" to "fitness.cardio",
            "intelligence" to "study",
            "business" to "business",
            "social" to "social",
            "discipline" to "general",
            "adventure" to "practical",
            "wellbeing" to "wellbeing",
        )

    /** `TYPE_TAG` (engine.js:2396): a LifeMax activity type to a tag. */
    val TYPE_TAG: Map<String, String> =
        linkedMapOf(
            "strength" to "fitness.strength",
            "running" to "fitness.cardio",
            "language" to "language",
            "reading" to "reading",
            "business" to "business",
            "recovery" to "wellbeing",
            "practical" to "practical",
            "task" to "general",
            "custom" to "general",
        )

    /** `DAYS` (engine.js:2358): `onlyDays` numbers, Sunday first. */
    val DAYS: List<String> = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")

    private val WORDS: Map<String, Double> =
        mapOf(
            "one" to 1.0,
            "two" to 2.0,
            "three" to 3.0,
            "four" to 4.0,
            "five" to 5.0,
            "six" to 6.0,
            "seven" to 7.0,
            "eight" to 8.0,
            "nine" to 9.0,
            "ten" to 10.0,
            "once" to 1.0,
            "twice" to 2.0,
        )

    /** JavaScript's `\s`: WhiteSpace and LineTerminator. */
    const val SPACE =
        "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"
    private const val W = "A-Za-z0-9_"
    /** JavaScript's `\b` (outside `u` mode). */
    private const val B = "(?:(?<=[$W])(?![$W])|(?<![$W])(?=[$W]))"

    private val COUNT =
        Regex(
            "$B([0-9]+|one|two|three|four|five|six|seven|eight|nine|ten|once|twice)$B[$SPACE]*" +
                "(?:[$W-]+[$SPACE]+){0,3}?" +
                "(sessions?|workouts?|times|blocks?|runs?|days?|lessons?|chapters?|walks?|meals?)$B"
        )
    private val MINUTES = Regex("([0-9]{1,3})[$SPACE-]*(?:min|minutes?)$B")
    private val TIME_WORD = Regex("$B(?:minutes?|mins?|hours?|hrs?|seconds?|secs?)$B")
    private val EARNED =
        Regex(
            "$B(consecutive|cumulative|accumulat[$W]*|in total|total of|streak|maintain[$W]*|" +
                "every day|cover to cover|entire|from start to finish|deload week)$B" +
                "|$B[0-9]+[$SPACE]+(?:[a-z-]+[$SPACE]+){0,3}(sessions?|hours?|weeks?|days?|workouts?)$B" +
                "|$B(ten|twenty|fifty|hundred)[$SPACE]+(completed[$SPACE]+)?" +
                "(volumes|sessions|hours|weeks|days)$B"
        )

    /** `A`-`Z` to lower case, every other unit unchanged (same length, same positions). */
    fun asciiLower(text: String): String =
        String(CharArray(text.length) { text[it].let { c -> if (c in 'A'..'Z') c + 32 else c } })

    /** HA-3 `typeForTag` (LD-1, already native). */
    fun typeForTag(tag: String): String = LifeDirector.typeForTag(tag)

    /** HA-3 `tagForType`: the tag of an activity type, `general` for any other. */
    fun tagForType(type: String): String = TYPE_TAG[type] ?: "general"

    /** HA-6 `parseCount`: how many sessions the words ask for, 1 to 14; 1 without a count. */
    fun parseCount(text: String): Double {
        val m = COUNT.find(asciiLower(text)) ?: return 1.0
        val word = m.groupValues[1]
        val n = if (word.all { it in '0'..'9' }) word.toDouble() else WORDS.getValue(word)
        return maxOf(1.0, minOf(14.0, if (n == 0.0 || n.isNaN()) 1.0 else n))
    }

    /** HA-6 `parseMinutes`: the first `<n> min` in the words, 5 to 240; 0 without one. */
    fun parseMinutes(text: String): Double {
        val m = MINUTES.find(asciiLower(text)) ?: return 0.0
        return maxOf(5.0, minOf(240.0, m.groupValues[1].toDouble()))
    }

    /** HA-5 `mapQuestTag`: the kind's tag, else a guess from the words, else the attribute's. */
    fun mapQuestTag(q: BoardQuest): String {
        q.kind?.let { kind ->
            KIND_TAG[kind]?.let {
                return it
            }
        }
        val t = q.words.lowercase(Locale.ROOT)
        return when {
            "workout" in t || "lift" in t || "strength" in t -> "fitness.strength"
            "run" in t || "cardio" in t || "walk" in t -> "fitness.cardio"
            "read" in t || "book" in t || "page" in t -> "reading"
            "study" in t || "learn" in t || "course" in t -> "study"
            "spanish" in t || "language" in t || "vocab" in t -> "language"
            else -> q.attribute?.let { ATTR_TAG[it] } ?: "general"
        }
    }

    private fun isMilestoneQuest(q: BoardQuest?): Boolean =
        q != null && (q.kind == "milestone" || q.cadence == "epic")

    /** HA-7 `isMilestone`: a milestone earned by piling up other activity; never planned. */
    fun isMilestone(q: BoardQuest?): Boolean =
        isMilestoneQuest(q) && EARNED.containsMatchIn(asciiLower(q!!.words))

    /** HA-7 `isProjectMilestone`: a one-off milestone, planned once as an optional session. */
    fun isProjectMilestone(q: BoardQuest?): Boolean = isMilestoneQuest(q) && !isMilestone(q)

    /** HA-8 `isUntimedQuest`: a self-confirmed daily card with no duration in its words. */
    fun isUntimedQuest(q: BoardQuest?): Boolean {
        if (q == null || !q.hand || q.cadence != "daily" || q.verify != "self") return false
        val text = q.untimedText
        return parseMinutes(text) == 0.0 && !TIME_WORD.containsMatchIn(asciiLower(text))
    }

    /** `String.prototype.trim()`. */
    fun trim(text: String): String = text.jsTrim()
}
