package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.SaveFields

/**
 * The save keys the shadow carries and the names it may show (ECONOMY_SHADOW_PLAN 1.2, 2.2, 3.2).
 * The page's `05-shadow-boot.js` keeps the same lists; `ShadowKeysTest` and the page test hold them
 * equal.
 */
object ShadowKeys {
    /** Read-only inputs the adapters need besides the tracked keys (2.2). */
    val INPUTS: List<String> =
        listOf("waterLog", "pet", "luck", "classId", "classContent", "dhMig", "schema")

    /** Every top-level key an envelope or a base may name: the tracked keys, then [INPUTS]. */
    val SHADOW: List<String> = SaveFields.TRACKED + INPUTS

    /** [SHADOW] as a set, for decoding. */
    val SHADOW_SET: Set<String> = SHADOW.toSet()

    /** Array keys sent as record deltas (`rec`): new length and changed `[index, record]` pairs. */
    val REC_KEYS: Set<String> = setOf("activities", "quests", "decayLog")

    /**
     * The action kinds of table 1.2, with the kinds WP6b split so that one kind is one page entry
     * (`log.delete` / `log.remove` / `fixed.undo`, `director.award` / `director.quest`, the four
     * `coach.*`, the four `rules.*`) and the quest point of WP6c (`quest.pack`, a class switch's
     * quest pack). The set is open (a newer page may shadow more); a kind not here is accepted and
     * counted `skipped.unknownKind`.
     */
    val KINDS: Set<String> =
        setOf(
            "log.record",
            "log.delete",
            "log.remove",
            "fixed.undo",
            "voice.log",
            "quest.toggle",
            "quest.create",
            "quest.pack",
            "hand.ensure",
            "hand.reroll",
            "hand.verify",
            "health.sync",
            "director.award",
            "director.quest",
            "bonus.care",
            "bonus.nutrition",
            "money.record",
            "money.remove",
            "fixed.plan",
            "fixed.catalog",
            "coach.claim",
            "coach.reflect",
            "coach.review",
            "coach.catchup",
            "spark.buy",
            "ascend",
            "rest.take",
            "rules.save",
            "rules.reset",
            "rules.base",
            "rules.decay",
            "decay.apply",
            "boot.load",
            "boot.decay",
            "save.other",
        )

    /** The pseudo-kind under which edits made between two actions are recorded (the touch map). */
    const val BETWEEN = "between"

    /** The shape every kind name has: lower-case words joined by dots. */
    val KIND_SHAPE = Regex("[a-z]+(\\.[a-z]+)*")

    /**
     * Member names that are the app's own words and may be shown as they are (3.2): attribute ids,
     * the economy's nested keys, and fixed keys of tracked objects. Any other member name (a skill,
     * a day, an id, a rule the owner named) is shown only as a salted hash.
     */
    val FIXED_MEMBERS: Set<String> =
        setOf(
            // attribute ids (ATTRS)
            "physique",
            "endurance",
            "intelligence",
            "business",
            "social",
            "discipline",
            "adventure",
            "wellbeing",
        ) +
            SaveFields.PARTIAL.values.flatten().map {
                it.removePrefix("[].").substringBefore('.')
            } +
            setOf("on", "grace", "rate", "id", "date", "level", "pats", "shields", "rerollsUsed") +
            // the Daily Hand's draw (WP6c compares it whole): its own members, the app's words
            setOf("nonce", "cards", "up", "turned", "dealt")
}
