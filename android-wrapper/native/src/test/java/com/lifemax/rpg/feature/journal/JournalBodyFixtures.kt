package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.DayHead
import com.lifemax.rpg.feature.journal.domain.JournalBlank
import com.lifemax.rpg.feature.journal.domain.JournalEmpty
import com.lifemax.rpg.feature.journal.domain.JournalInsight
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.JournalReflect
import com.lifemax.rpg.feature.journal.domain.PlanNote
import com.lifemax.rpg.feature.journal.domain.ReflectCounter
import com.lifemax.rpg.feature.journal.domain.ReflectPrompt
import com.lifemax.rpg.feature.journal.domain.TimelineAttr
import com.lifemax.rpg.feature.journal.domain.TimelineItem
import com.lifemax.rpg.feature.journal.domain.WorkoutNotes

/**
 * Hand-made Journal body models for the screenshots and tests: the page's own data builders
 * (jcListData, jcDayData, journalDayData, jcReviewData) called on a lived-in six weeks at
 * 2026-10-28 21:00 UTC, en-US, then shaped as the projection carries them (plan §3.3). Replace with
 * decoded fixtures (journal-*.json) once the web half and the decoder are merged.
 */
@Suppress("LargeClass")
object JournalBodyFixtures {
    internal val COUNTER =
        ReflectCounter(
            "+15 XP & +2 bond claimed",
            "Saves for +15 XP",
            "Write 40+ characters for +15 XP",
            "Reflections on older days earn no XP",
        )
    internal const val PLACEHOLDER =
        "How did today feel? What mattered? What will you do differently?"

    /** The strip as the page lists it: today, the week's review, a quiet pill, older days. */
    val tiles: List<JournalListItem> =
        listOf(
            JournalListItem.Day(
                "2026-10-28",
                "Today",
                "28",
                17,
                "30",
                false,
                0xFF64748BL,
                true,
                "Today, 30 XP",
            ),
            JournalListItem.Day(
                "2026-10-27",
                "Tue",
                "27",
                45,
                "79",
                true,
                0xFF14B8A6L,
                false,
                "Yesterday, 79 XP",
            ),
            JournalListItem.Day(
                "2026-10-26",
                "Mon",
                "26",
                66,
                "117",
                false,
                0xFF14B8A6L,
                false,
                "Mon, Oct 26, 117 XP",
            ),
            JournalListItem.Review("W:2026-W43", "W", "Week 43", "weekly review"),
            JournalListItem.Quiet("4 quiet days"),
            JournalListItem.Day(
                "2026-10-21",
                "Wed",
                "21",
                100,
                "176",
                false,
                0xFF14B8A6L,
                false,
                "Wed, Oct 21, 176 XP",
            ),
            JournalListItem.Day(
                "2026-10-20",
                "Tue",
                "20",
                38,
                "66",
                false,
                0xFF64748BL,
                false,
                "Tue, Oct 20, 66 XP",
            ),
            JournalListItem.Day(
                "2026-10-19",
                "Mon",
                "19",
                23,
                "41",
                false,
                0xFF64748BL,
                false,
                "Mon, Oct 19, 41 XP",
            ),
            JournalListItem.Review("W:2026-W42", "W", "Week 42", "weekly review"),
            JournalListItem.Day(
                "2026-10-18",
                "Sun",
                "18",
                48,
                "85",
                false,
                0xFF14B8A6L,
                false,
                "Sun, Oct 18, 85 XP",
            ),
            JournalListItem.Day(
                "2026-10-17",
                "Sat",
                "17",
                98,
                "173",
                true,
                0xFF14B8A6L,
                false,
                "Sat, Oct 17, 173 XP",
            ),
            JournalListItem.Day(
                "2026-10-16",
                "Fri",
                "16",
                31,
                "54",
                false,
                0xFF64748BL,
                false,
                "Fri, Oct 16, 54 XP",
            ),
            JournalListItem.Day(
                "2026-10-15",
                "Thu",
                "15",
                38,
                "67",
                false,
                0xFF64748BL,
                false,
                "Thu, Oct 15, 67 XP",
            ),
            JournalListItem.Day(
                "2026-10-14",
                "Wed",
                "14",
                56,
                "99",
                false,
                0xFF14B8A6L,
                false,
                "Wed, Oct 14, 99 XP",
            ),
            JournalListItem.Day(
                "2026-10-13",
                "Tue",
                "13",
                77,
                "135",
                false,
                0xFF14B8A6L,
                false,
                "Tue, Oct 13, 135 XP",
            ),
            JournalListItem.Day(
                "2026-10-12",
                "Mon",
                "12",
                21,
                "37",
                true,
                0xFF38BDF8L,
                false,
                "Mon, Oct 12, 37 XP",
            ),
        )

    /** Today: a low-power day with its pattern line and question, one plan session. */
    val today =
        DayEntry(
            "2026-10-28",
            DayHead("28", "Wednesday", "October 2026", "30", "XP today"),
            listOf("1 entry", "2 exercise notes"),
            null,
            null,
            JournalInsight(
                "Low Power Mode",
                0xFF64748BL,
                "Low-output log (30 XP). Whether today was rest, friction, or just life happening off-screen, the slate resets on the next dial.",
                "Pattern noticed: 100% of your XP over the last 2 weeks was logged between 5 AM and 11 AM. Put your hardest block there.",
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "If a good friend had the exact day you just had, what would you tell them tonight?",
                    "PRM_D4",
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

    /** Today again, written and paid: the counter claimed, the meter full. */
    val todayWritten =
        today.copy(
            reflect =
                today.reflect.copy(
                    text = "Slow start, but the session felt good and I kept my word to myself.",
                    awarded = true,
                    meter = 100.0,
                )
        )

    /** A busy past day: four activities, a skill, no notes on the plan session's run. */
    val pastDay =
        DayEntry(
            "2026-10-21",
            DayHead("21", "Wednesday", "October 2026", "176", "XP today"),
            listOf("4 entries", "2 exercise notes"),
            null,
            null,
            JournalInsight(
                "Steady Stride",
                0xFF14B8A6L,
                "Solid baseline day: 176 XP, near your 90 XP average. Sustainable pace.",
                null,
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "Did you spend your best hours on your highest-priority skill today?",
                    "PRM_C3",
                    "Swap question",
                ),
                null,
                "",
                PLACEHOLDER,
                4000,
                40,
                false,
                false,
                0.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFFB923CL,
                    "flame",
                    "Morning run",
                    TimelineAttr("Endurance", 0xFFFB923CL),
                    "30 min",
                    null,
                    "+44",
                    null,
                    listOf(
                        PlanNote("bench", "60 kg", "easy"),
                        PlanNote("squat", "80 kg", "last set hard"),
                    ),
                ),
                TimelineItem(
                    "7:25 AM",
                    0xFF60A5FAL,
                    "book",
                    "Deep work",
                    TimelineAttr("Intelligence", 0xFF60A5FAL),
                    "90 min",
                    "Study",
                    "+59",
                    null,
                    null,
                ),
                TimelineItem(
                    "8:40 AM",
                    0xFFFBBF24L,
                    "briefcase",
                    "Client call",
                    TimelineAttr("Business", 0xFFFBBF24L),
                    "30 min",
                    null,
                    "+39",
                    null,
                    null,
                ),
                TimelineItem(
                    "9:55 AM",
                    0xFFF472B6L,
                    "chat",
                    "Dinner with friends",
                    TimelineAttr("Social", 0xFFF472B6L),
                    "120 min",
                    null,
                    "+34",
                    null,
                    null,
                ),
            ),
            null,
            null,
        )

    /** A day with nothing logged but a short reflection: the blank state. */
    val blankDay =
        DayEntry(
            "2026-10-11",
            DayHead("11", "Sunday", "October 2026", "0", "XP today"),
            listOf("0 entries"),
            null,
            null,
            JournalInsight(
                "Low Power Mode",
                0xFF64748BL,
                "Light footprint today (0 XP). Data is just a mirror, not a judge. Leave a quick note below if you want to remember what pulled your focus.",
                null,
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "If a good friend had the exact day you just had, what would you tell them tonight?",
                    "PRM_D4",
                    "Swap question",
                ),
                null,
                "Only a note today.",
                PLACEHOLDER,
                4000,
                40,
                false,
                false,
                45.0,
                COUNTER,
            ),
            listOf(),
            null,
            JournalBlank("✎", "Nothing logged this day yet", "A blank page is still a page."),
        )

    /** A day with workout notes and no plan session logged. */
    val workoutDay =
        DayEntry(
            "2026-10-09",
            DayHead("9", "Friday", "October 2026", "152", "XP today"),
            listOf("4 entries", "1 exercise note"),
            null,
            null,
            JournalInsight(
                "Steady Stride",
                0xFF14B8A6L,
                "Solid baseline day: 152 XP, near your 80 XP average. Sustainable pace.",
                null,
            ),
            JournalReflect(
                ReflectPrompt(
                    "Reflect",
                    "Which activity today felt easiest to start once you got past the first two minutes?",
                    "PRM_C1",
                    "Swap question",
                ),
                null,
                "",
                PLACEHOLDER,
                4000,
                40,
                false,
                false,
                0.0,
                COUNTER,
            ),
            listOf(
                TimelineItem(
                    "6:10 AM",
                    0xFFFB923CL,
                    "flame",
                    "Morning run",
                    TimelineAttr("Endurance", 0xFFFB923CL),
                    "30 min",
                    null,
                    "+38",
                    null,
                    null,
                ),
                TimelineItem(
                    "7:25 AM",
                    0xFF60A5FAL,
                    "book",
                    "Deep work",
                    TimelineAttr("Intelligence", 0xFF60A5FAL),
                    "90 min",
                    "Study",
                    "+53",
                    null,
                    null,
                ),
                TimelineItem(
                    "8:40 AM",
                    0xFFFBBF24L,
                    "briefcase",
                    "Client call",
                    TimelineAttr("Business", 0xFFFBBF24L),
                    "30 min",
                    null,
                    "+33",
                    null,
                    null,
                ),
                TimelineItem(
                    "9:55 AM",
                    0xFFF472B6L,
                    "chat",
                    "Dinner with friends",
                    TimelineAttr("Social", 0xFFF472B6L),
                    "120 min",
                    null,
                    "+28",
                    null,
                    null,
                ),
            ),
            WorkoutNotes("Workout notes", listOf(PlanNote("row", "40 kg", "smooth"))),
            null,
        )

    val empty =
        JournalEmpty(
            "◷",
            "No journal entries yet",
            "Log an activity, complete a quest, or finish a plan session. Everything you enter will show up here by day.",
        )
}
