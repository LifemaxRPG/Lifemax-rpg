package com.lifemax.rpg.core.economy

/**
 * Section 11.4: logging a past activity by voice. Understanding the sentence is outside the
 * economy.
 */
object Voice {
    /** VOICE-3, a sentence's activity: the type, its pattern's name, and the word that matched. */
    data class Match(val type: String, val name: String, val noun: String)

    private class Pattern(val type: String, val name: String, words: String) {
        val regex = Regex("""\b($words)\b""")
    }

    private val PATTERNS =
        listOf(
            Pattern(
                "running",
                "Run / cardio",
                "run|ran|running|jog|jogged|jogging|cardio|treadmill|cycle|cycled|cycling|" +
                    "bike|biked|swim|swam|swimming|hike|hiked|hiking|walk|walked|walking|" +
                    "elliptical|rowing",
            ),
            Pattern(
                "strength",
                "Strength workout",
                "weight|weights|lift|lifted|lifting|gym|strength|workout|worked out|" +
                    "pushups?|push-ups?|push ups?|pullups?|pull-ups?|pull ups?|squat|squats|" +
                    "bench|exercise|exercised|trained|training|crossfit",
            ),
            Pattern(
                "reading",
                "Focused reading / study",
                "read|reading|study|studied|studying|homework|course",
            ),
            Pattern(
                "language",
                "Language practice",
                "spanish|french|german|italian|japanese|language|duolingo",
            ),
            Pattern(
                "recovery",
                "Sleep / recovery routine",
                """yoga|stretch|stretched|stretching|meditat\w*|nap|napped|sleep|recovery|""" +
                    "mobility",
            ),
            Pattern(
                "business",
                "Focused business work",
                "business|client|invoice|proposal|email|emails|deep work",
            ),
            Pattern(
                "practical",
                "Practical skill practice",
                "cook|cooked|clean|cleaned|repair|fixed|garden|gardening|woodwork|practice|" +
                    "practiced|laundry|chore|chores",
            ),
        )

    private val NOUN_NAMES =
        mapOf(
            "Walk" to listOf("walk", "walked", "walking"),
            "Yoga" to listOf("yoga"),
            "Swim" to listOf("swim", "swam", "swimming"),
            "Hike" to listOf("hike", "hiked", "hiking"),
            "Cycle" to listOf("cycle", "cycled", "cycling"),
            "Bike ride" to listOf("bike", "biked"),
            "Jog" to listOf("jog", "jogged", "jogging"),
        )

    /** VOICE-3: the first pattern that matches the (normalised, lower-case) sentence. */
    fun activityType(sentence: String): Match {
        for (p in PATTERNS) {
            val found = p.regex.find(sentence) ?: continue
            return Match(p.type, p.name, found.groupValues[1])
        }
        return Match("custom", "Custom activity", "")
    }

    /** VOICE-3: the record's name; `custom` is always `Activity`. */
    fun recordName(match: Match): String =
        if (match.type == "custom") "Activity"
        else NOUN_NAMES.entries.firstOrNull { match.noun in it.value }?.key ?: match.name

    /** VOICE-3: missing or under 5 minutes means 30; over 480 is refused (null). */
    fun minutesSaid(minutes: Double?): Double? =
        when {
            minutes == null || minutes.isNaN() || minutes < 5 -> 30.0
            minutes > 480 -> null
            else -> minutes
        }

    /** VOICE-1: scored without quality, a manual log on [day]. */
    fun logXp(
        save: EconomySave,
        type: String,
        minutes: Double,
        day: String?,
        peek: Boolean,
        ctx: EconomyContext,
    ): Outcome<Award> {
        val total = maxOf(0.0, jsRound(save.rules.of(type).base * durationFactor(minutes)))
        return save.award(type, total, minutes, ctx, day, manual = true, peek = peek)
    }

    /** What was said, decided: type, name, minutes, day and the moment it happened. */
    data class Said(
        val type: String,
        val name: String,
        val minutes: Double,
        val day: String?,
        val at: Long,
    )

    /** VOICE-2's answer. */
    data class LogResult(
        val ok: Boolean,
        val xp: Double? = null,
        val id: String? = null,
        val message: String? = null,
    )

    /** VOICE-2: records it split by the rule and starred; a failed save is kept [D13]. */
    fun logNow(save: EconomySave, said: Said, ctx: EconomyContext): Outcome<LogResult> {
        val rule = save.rules.of(said.type)
        val awarded = logXp(save, said.type, said.minutes, said.day, peek = false, ctx)
        val (xp, message) = awarded.value
        if (xp <= 0)
            return awarded.map {
                LogResult(false, message = message.ifEmpty { "No XP for that one" })
            }
        val parts = awarded.save.starred(splitXp(xp, rule.primary, rule.secondary))
        val total = parts.sumOf { it.xp }
        val record =
            Activity(
                id = ctx.ids.uuid(),
                type = said.type,
                name = said.name,
                icon = rule.icon,
                duration = said.minutes,
                quality = 1.0,
                skill = rule.skill,
                primary = rule.primary,
                secondary = rule.secondary,
                xp = total,
                date = EconomyClock.iso(said.at),
                notes = "Logged by voice",
                split = parts,
                source = "voice",
            )
        return SavePipeline.save(
                awarded.save.copy(activities = awarded.save.activities + record),
                ctx,
            )
            .map { LogResult(true, total, record.id) }
            .toast("Activity saved · +${total.text()} XP")
    }
}
