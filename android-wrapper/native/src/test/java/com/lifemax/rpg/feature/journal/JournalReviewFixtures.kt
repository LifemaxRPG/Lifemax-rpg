package com.lifemax.rpg.feature.journal

import com.lifemax.rpg.feature.journal.domain.HeatCell
import com.lifemax.rpg.feature.journal.domain.ReviewBar
import com.lifemax.rpg.feature.journal.domain.ReviewChart
import com.lifemax.rpg.feature.journal.domain.ReviewCoach
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import com.lifemax.rpg.feature.journal.domain.ReviewGridItem
import com.lifemax.rpg.feature.journal.domain.ReviewHead

/** Hand-made reviews from the page's jcReviewData, as JournalBodyFixtures' days are made. */
object JournalReviewFixtures {
    /** Week 42: seven active days. */
    val week =
        ReviewEntry(
            "W:2026-W42",
            ReviewHead("W42", "Week 42: 650 XP across 7/7 days", "Oct 12 to Oct 18"),
            ReviewChart.Bars(
                listOf(
                    ReviewBar("M", "37", 21, 0xFF38BDF8L, false),
                    ReviewBar("T", "135", 78, 0xFF14B8A6L, false),
                    ReviewBar("W", "99", 57, 0xFF14B8A6L, false),
                    ReviewBar("T", "67", 39, 0xFF64748BL, false),
                    ReviewBar("F", "54", 31, 0xFF64748BL, false),
                    ReviewBar("S", "173", 100, 0xFF14B8A6L, false),
                    ReviewBar("S", "85", 49, 0xFF14B8A6L, false),
                )
            ),
            listOf(
                ReviewGridItem("XP", "650 XP (+35% vs prior week)"),
                ReviewGridItem("Top stat", "Physique (29% of output)"),
            ),
            ReviewCoach(
                "Coach",
                "Strong expansion week. You pushed volume up by 35%, led by Saturday (173 XP). Watch your Monday start so you do not carry fatigue.",
            ),
            "What is ONE adjustment to your 24-hour Director dial that would make next week 10% smoother?",
            "",
            "Write if you want to.",
            2000,
            50,
            false,
            "Write 50+ characters for +30 XP",
        )

    /** Week 43: three active days and four empty bars, answered and paid. */
    val weekPaid =
        ReviewEntry(
            "W:2026-W43",
            ReviewHead("W43", "Week 43: 283 XP across 3/7 days", "Oct 19 to Oct 25"),
            ReviewChart.Bars(
                listOf(
                    ReviewBar("M", "41", 23, 0xFF64748BL, false),
                    ReviewBar("T", "66", 38, 0xFF64748BL, false),
                    ReviewBar("W", "176", 100, 0xFF14B8A6L, false),
                    ReviewBar("T", "", 3, 0xFF475569L, false),
                    ReviewBar("F", "", 3, 0xFF475569L, false),
                    ReviewBar("S", "", 3, 0xFF475569L, false),
                    ReviewBar("S", "", 3, 0xFF475569L, false),
                )
            ),
            listOf(
                ReviewGridItem("XP", "283 XP (-56% vs prior week)"),
                ReviewGridItem("Top stat", "Intelligence (44% of output)"),
            ),
            ReviewCoach(
                "Coach",
                "A lighter week (283 XP). Your anchor was Intelligence, which kept you moving even when the schedule got tight.",
            ),
            "What is ONE adjustment to your 24-hour Director dial that would make next week 10% smoother?",
            "Move the run to the morning and keep the evenings for reading.",
            "Write if you want to.",
            2000,
            50,
            true,
            "+30 XP claimed",
        )

    /** September's chronicle: the heat grid from a Tuesday. */
    val month =
        ReviewEntry(
            "M:2026-09",
            ReviewHead("SEP", "September Chronicle · Level 8 to 10", "Sep 1 to Sep 30"),
            ReviewChart.Heat(
                listOf("M", "T", "W", "T", "F", "S", "S"),
                1,
                listOf(
                    HeatCell("1", 0xFF475569L, 0.18, true, "Sep 1: 0 XP"),
                    HeatCell("2", 0xFF475569L, 0.18, true, "Sep 2: 0 XP"),
                    HeatCell("3", 0xFF475569L, 0.18, true, "Sep 3: 0 XP"),
                    HeatCell("4", 0xFF475569L, 0.18, true, "Sep 4: 0 XP"),
                    HeatCell("5", 0xFF475569L, 0.18, true, "Sep 5: 0 XP"),
                    HeatCell("6", 0xFF475569L, 0.18, true, "Sep 6: 0 XP"),
                    HeatCell("7", 0xFF475569L, 0.18, true, "Sep 7: 0 XP"),
                    HeatCell("8", 0xFF475569L, 0.18, true, "Sep 8: 0 XP"),
                    HeatCell("9", 0xFF475569L, 0.18, true, "Sep 9: 0 XP"),
                    HeatCell("10", 0xFF475569L, 0.18, true, "Sep 10: 0 XP"),
                    HeatCell("11", 0xFF475569L, 0.18, true, "Sep 11: 0 XP"),
                    HeatCell("12", 0xFF475569L, 0.18, true, "Sep 12: 0 XP"),
                    HeatCell("13", 0xFF475569L, 0.18, true, "Sep 13: 0 XP"),
                    HeatCell("14", 0xFF475569L, 0.18, true, "Sep 14: 0 XP"),
                    HeatCell("15", 0xFF475569L, 0.18, true, "Sep 15: 0 XP"),
                    HeatCell("16", 0xFF14B8A6L, 1.0, false, "Sep 16: 167 XP"),
                    HeatCell("17", 0xFF64748BL, 0.6101796407185629, false, "Sep 17: 74 XP"),
                    HeatCell("18", 0xFF64748BL, 0.40479041916167663, false, "Sep 18: 25 XP"),
                    HeatCell("19", 0xFF14B8A6L, 0.6646706586826348, false, "Sep 19: 87 XP"),
                    HeatCell("20", 0xFF14B8A6L, 0.9413173652694611, false, "Sep 20: 153 XP"),
                    HeatCell("21", 0xFF14B8A6L, 0.7317365269461078, false, "Sep 21: 103 XP"),
                    HeatCell("22", 0xFF64748BL, 0.47604790419161674, false, "Sep 22: 42 XP"),
                    HeatCell("23", 0xFF14B8A6L, 0.8239520958083832, false, "Sep 23: 125 XP"),
                    HeatCell("24", 0xFF14B8A6L, 0.8826347305389222, false, "Sep 24: 139 XP"),
                    HeatCell("25", 0xFF14B8A6L, 0.6227544910179641, false, "Sep 25: 77 XP"),
                    HeatCell("26", 0xFF64748BL, 0.5263473053892216, false, "Sep 26: 54 XP"),
                    HeatCell("27", 0xFF14B8A6L, 0.8365269461077844, false, "Sep 27: 128 XP"),
                    HeatCell("28", 0xFF14B8A6L, 0.6982035928143713, false, "Sep 28: 95 XP"),
                    HeatCell("29", 0xFF14B8A6L, 0.6604790419161676, false, "Sep 29: 86 XP"),
                    HeatCell("30", 0xFF64748BL, 0.429940119760479, false, "Sep 30: 31 XP"),
                ),
            ),
            listOf(
                ReviewGridItem("Total XP", "1,386"),
                ReviewGridItem("Levels gained", "2"),
                ReviewGridItem("Quests cleared", "0"),
                ReviewGridItem("Reflections written", "3"),
                ReviewGridItem("Best day", "Sep 16 (167 XP)"),
                ReviewGridItem("Quiet stretches returned from", "0"),
            ),
            ReviewCoach("Coach", "A fresh month. Notice what you want more of."),
            "If September was a chapter in your character's story, what was the title of this chapter, and what skill did you actually level up in real life?",
            "",
            "Write if you want to.",
            2000,
            60,
            false,
            "Write 60+ characters for +50 XP",
        )
}
