package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.JournalBodyFixtures.COUNTER
import com.lifemax.rpg.feature.journal.JournalBodyFixtures.PLACEHOLDER
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.DayHead
import com.lifemax.rpg.feature.journal.domain.JournalCare
import com.lifemax.rpg.feature.journal.domain.JournalInsight
import com.lifemax.rpg.feature.journal.domain.JournalReflect
import com.lifemax.rpg.feature.journal.domain.JournalWelcome
import com.lifemax.rpg.feature.journal.domain.PlanNote
import com.lifemax.rpg.feature.journal.domain.ReflectPrompt
import com.lifemax.rpg.feature.journal.domain.TimelineAttr
import com.lifemax.rpg.feature.journal.domain.TimelineItem
import com.lifemax.rpg.feature.journal.domain.WelcomeChip

/**
 * Hand-made coach days (welcome back, the care note) from the page's builders, as
 * JournalBodyFixtures' days.
 */
object JournalCoachFixtures {
    /** The care note in place of the coach, the plain label on the box. */
    val care =
        DayEntry(
            "2026-10-28",
            DayHead("28", "Wednesday", "October 2026", "30", "XP today"),
            listOf("1 entry", "2 exercise notes"),
            JournalCare(
                "That sounds really heavy.",
                "You do not have to carry it alone. If you might hurt yourself, please call or text 988 (in the US) or your local emergency number right now, or reach out to someone you trust. The coach stays quiet today.",
            ),
            null,
            null,
            JournalReflect(
                null,
                "Reflection (optional)",
                "Some days I feel I want to die, honestly.",
                PLACEHOLDER,
                4000,
                40,
                true,
                false,
                100.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFF87171L,
                    "dumbbell",
                    "Strength training",
                    TimelineAttr("Physique", 0xFFF87171L),
                    "45 min",
                    null,
                    "+30",
                    "Felt strong today, kept the pace easy.",
                    listOf(
                        PlanNote("bench", "60 kg", "easy"),
                        PlanNote("squat", "80 kg", "last set hard"),
                    ),
                )
            ),
            null,
            null,
        )

    /** Welcome back after 4 quiet days (tier 1). */
    val welcome1 =
        DayEntry(
            "2026-10-28",
            DayHead("28", "Wednesday", "October 2026", "30", "XP today"),
            listOf("1 entry", "2 exercise notes"),
            null,
            JournalWelcome(
                "Welcome back. Ready when you are.",
                "You were off-grid for 4 days. No penalties, no backlog to clear. Today is a clean slate.",
                listOf(WelcomeChip("pet", "Claim +10 XP", false)),
            ),
            JournalInsight(
                "Chapter Resumed",
                0xFFF59E0BL,
                "You are back. That is the only thing that matters today.",
                "Pattern noticed: 100% of your XP over the last 2 weeks was logged between 5 AM and 11 AM. Put your hardest block there.",
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "What made you open LifeMax again today?",
                    "PRM_E4",
                    "Swap question",
                ),
                null,
                "",
                PLACEHOLDER,
                4000,
                40,
                true,
                false,
                0.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFF87171L,
                    "dumbbell",
                    "Strength training",
                    TimelineAttr("Physique", 0xFFF87171L),
                    "45 min",
                    null,
                    "+30",
                    "Felt strong today, kept the pace easy.",
                    listOf(
                        PlanNote("bench", "60 kg", "easy"),
                        PlanNote("squat", "80 kg", "last set hard"),
                    ),
                )
            ),
            null,
            null,
        )

    /** After 8 days (tier 2): the catch-up and Life Director chips. */
    val welcome2 =
        DayEntry(
            "2026-10-28",
            DayHead("28", "Wednesday", "October 2026", "30", "XP today"),
            listOf("1 entry", "2 exercise notes"),
            null,
            JournalWelcome(
                "Good to see you again.",
                "It has been 8 days since your last log. Life happens outside the screen. Do not try to make up for last week, just pick one small block on the Director dial today.",
                listOf(
                    WelcomeChip("line", "One-sentence catch-up · +20 XP", false),
                    WelcomeChip("director", "Open Life Director", false),
                ),
            ),
            JournalInsight(
                "Chapter Resumed",
                0xFFF59E0BL,
                "You are back. That is the only thing that matters today.",
                "Pattern noticed: 100% of your XP over the last 2 weeks was logged between 5 AM and 11 AM. Put your hardest block there.",
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "What made you open LifeMax again today?",
                    "PRM_E4",
                    "Swap question",
                ),
                null,
                "",
                PLACEHOLDER,
                4000,
                40,
                true,
                false,
                0.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFF87171L,
                    "dumbbell",
                    "Strength training",
                    TimelineAttr("Physique", 0xFFF87171L),
                    "45 min",
                    null,
                    "+30",
                    "Felt strong today, kept the pace easy.",
                    listOf(
                        PlanNote("bench", "60 kg", "easy"),
                        PlanNote("squat", "80 kg", "last set hard"),
                    ),
                )
            ),
            null,
            null,
        )

    /** After 15 days (tier 3), its chip already claimed. */
    val welcome3Claimed =
        DayEntry(
            "2026-10-28",
            DayHead("28", "Wednesday", "October 2026", "30", "XP today"),
            listOf("1 entry", "2 exercise notes"),
            null,
            JournalWelcome(
                "New chapter unlocked.",
                "You have been away for 15 days. Your level (9), Sparks and everything are right where you left them, and your rolling average is reset for a week so today feels fair.",
                listOf(WelcomeChip("fresh", "Start fresh · +25 XP", true)),
            ),
            JournalInsight(
                "Chapter Resumed",
                0xFFF59E0BL,
                "You are back. That is the only thing that matters today.",
                null,
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "What made you open LifeMax again today?",
                    "PRM_E4",
                    "Swap question",
                ),
                null,
                "",
                PLACEHOLDER,
                4000,
                40,
                true,
                false,
                0.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFF87171L,
                    "dumbbell",
                    "Strength training",
                    TimelineAttr("Physique", 0xFFF87171L),
                    "45 min",
                    null,
                    "+30",
                    "Felt strong today, kept the pace easy.",
                    listOf(
                        PlanNote("bench", "60 kg", "easy"),
                        PlanNote("squat", "80 kg", "last set hard"),
                    ),
                )
            ),
            null,
            null,
        )
}
