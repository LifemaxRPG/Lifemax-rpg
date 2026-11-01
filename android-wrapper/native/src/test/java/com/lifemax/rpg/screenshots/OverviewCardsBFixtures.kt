package com.lifemax.rpg.screenshots

import com.lifemax.rpg.feature.overview.domain.AppItem
import com.lifemax.rpg.feature.overview.domain.AppsTile
import com.lifemax.rpg.feature.overview.domain.AttrGem
import com.lifemax.rpg.feature.overview.domain.AttrsTile
import com.lifemax.rpg.feature.overview.domain.CardChip
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.CatTile
import com.lifemax.rpg.feature.overview.domain.GemDot
import com.lifemax.rpg.feature.overview.domain.GemLegend
import com.lifemax.rpg.feature.overview.domain.HealthDay
import com.lifemax.rpg.feature.overview.domain.HealthTile
import com.lifemax.rpg.feature.overview.domain.WeekBar
import com.lifemax.rpg.feature.overview.domain.WeekDelta
import com.lifemax.rpg.feature.overview.domain.WeekPace
import com.lifemax.rpg.feature.overview.domain.WeekSeg
import com.lifemax.rpg.feature.overview.domain.WeekTile
import com.lifemax.rpg.feature.overview.domain.WeekTip
import com.lifemax.rpg.feature.overview.domain.WeekTop

/**
 * Cards B's tiles as the page's data functions answer them (ovAttrsData, ovWeekData, ovHealthData,
 * ovAppsData, petPeekData) on the established save at 2026-10-14 21:30, with the extras states of
 * tests/overview-classic.test.js on top: copied from the built page, so the images can be laid
 * beside the page's own at 412 px. // align at merge: from the overview-*.json fixtures once the
 * decoder lands.
 */
object OverviewCardsBFixtures {
    private const val PHYSIQUE = 0xFFFF6B57
    private const val ENDURANCE = 0xFFFF4D8D
    private const val INTELLIGENCE = 0xFF5B8CFF
    private const val BUSINESS = 0xFFF5C542
    private const val SOCIAL = 0xFFFF9F43
    private const val DISCIPLINE = 0xFFA78BFA
    private const val ADVENTURE = 0xFF3DDC84
    private const val WELLBEING = 0xFF4DD0E1

    private fun gem(
        id: String,
        short: String,
        icon: String,
        color: Long,
        focus: Boolean,
        kind: String,
        text: String,
        level: String,
        week: String,
        pct: Float,
    ) = AttrGem(id, short, icon, color, focus, GemDot(kind, text), level, week, pct)

    val attrs =
        AttrsTile(
            "Attributes",
            "Details ›",
            listOf(
                gem(
                    "physique",
                    "Physique",
                    "dumbbell",
                    PHYSIQUE,
                    true,
                    "decay",
                    "Decaying about 28 XP a day",
                    "LV 6",
                    "+41",
                    81.78f,
                ),
                gem(
                    "endurance",
                    "Endurance",
                    "heart",
                    ENDURANCE,
                    true,
                    "ok",
                    "Decay starts in 3 days",
                    "LV 9",
                    "+2",
                    94.33f,
                ),
                gem(
                    "intelligence",
                    "Intelligence",
                    "book",
                    INTELLIGENCE,
                    false,
                    "decay",
                    "Decaying about 98 XP a day",
                    "LV 14",
                    "quiet",
                    1.41f,
                ),
                gem(
                    "business",
                    "Business",
                    "briefcase",
                    BUSINESS,
                    false,
                    "soon",
                    "Decay starts in 2 days",
                    "LV 9",
                    "+31",
                    83.33f,
                ),
                gem(
                    "social",
                    "Social",
                    "chat",
                    SOCIAL,
                    false,
                    "decay",
                    "Decaying about 7 XP a day",
                    "LV 3",
                    "quiet",
                    10.67f,
                ),
                gem(
                    "discipline",
                    "Discipline",
                    "target",
                    DISCIPLINE,
                    false,
                    "today",
                    "Trained today",
                    "LV 10",
                    "+56",
                    11.08f,
                ),
                gem(
                    "adventure",
                    "Adventure",
                    "mountain",
                    ADVENTURE,
                    false,
                    "decay",
                    "Decaying about 70 XP a day",
                    "LV 11",
                    "quiet",
                    63.71f,
                ),
                gem(
                    "wellbeing",
                    "Wellbeing",
                    "moon",
                    WELLBEING,
                    true,
                    "ok",
                    "Decay starts in 3 days",
                    "LV 9",
                    "+4",
                    16f,
                ),
            ),
            listOf(
                GemLegend("today", "today"),
                GemLegend("ok", "safe"),
                GemLegend("soon", "due soon"),
                GemLegend("decay", "fading"),
            ),
        )

    private fun bar(
        date: String,
        label: String,
        xp: Int,
        day: String,
        rest: String,
        vararg segs: Pair<Long, Float>,
    ) =
        WeekBar(
            date,
            label,
            label == "Today",
            xp,
            segs.map { WeekSeg(it.first, it.second) },
            "$date $xp XP",
            WeekTip(day, rest),
        )

    val week =
        WeekTile(
            "This week",
            "134 XP",
            WeekDelta(false, "▼ 15% vs last week"),
            WeekPace("pace 60", 0.8333333f),
            listOf(
                bar("2026-10-08", "T", 0, "Thursday", "0 XP — a rest day"),
                bar(
                    "2026-10-09",
                    "F",
                    59,
                    "Friday",
                    "59 XP — Physique 41, Discipline 18",
                    PHYSIQUE to 0.5694444f,
                    DISCIPLINE to 0.25f,
                ),
                bar("2026-10-10", "S", 0, "Saturday", "0 XP — a rest day"),
                bar("2026-10-11", "S", 31, "Sunday", "31 XP — Business 31", BUSINESS to 0.4305556f),
                bar(
                    "2026-10-12",
                    "M",
                    6,
                    "Monday",
                    "6 XP — Wellbeing 4, Endurance 2",
                    ENDURANCE to 0.0277778f,
                    WELLBEING to 0.0555556f,
                ),
                bar("2026-10-13", "T", 0, "Tuesday", "0 XP — a rest day"),
                bar(
                    "2026-10-14",
                    "Today",
                    38,
                    "Today",
                    "38 XP — Discipline 38",
                    DISCIPLINE to 0.5277778f,
                ),
            ),
            listOf(
                WeekTop(DISCIPLINE, "Discipline 56"),
                WeekTop(PHYSIQUE, "Physique 41"),
                WeekTop(BUSINESS, "Business 31"),
            ),
            "Log an activity and your week lights up here.",
            6,
        )

    /** The over-pace state: a 620 XP ride today puts today's bar at the top, over the pace line. */
    val weekOverPace =
        week.copy(
            total = "178 XP",
            delta = WeekDelta(true, "▲ 13% vs last week"),
            pace = WeekPace("pace 60", 0.7317073f),
            bars =
                listOf(
                    bar("2026-10-08", "T", 0, "Thursday", "0 XP — a rest day"),
                    bar(
                        "2026-10-09",
                        "F",
                        59,
                        "Friday",
                        "59 XP — Physique 41, Discipline 18",
                        PHYSIQUE to 0.5f,
                        DISCIPLINE to 0.2195122f,
                    ),
                    bar("2026-10-10", "S", 0, "Saturday", "0 XP — a rest day"),
                    bar(
                        "2026-10-11",
                        "S",
                        31,
                        "Sunday",
                        "31 XP — Business 31",
                        BUSINESS to 0.3780488f,
                    ),
                    bar(
                        "2026-10-12",
                        "M",
                        6,
                        "Monday",
                        "6 XP — Wellbeing 4, Endurance 2",
                        ENDURANCE to 0.0243902f,
                        WELLBEING to 0.0487805f,
                    ),
                    bar("2026-10-13", "T", 0, "Tuesday", "0 XP — a rest day"),
                    bar(
                        "2026-10-14",
                        "Today",
                        82,
                        "Today",
                        "82 XP — Discipline 76, Wellbeing 4, Endurance 2",
                        ENDURANCE to 0.0243902f,
                        DISCIPLINE to 0.9268293f,
                        WELLBEING to 0.0487805f,
                    ),
                ),
            top =
                listOf(
                    WeekTop(DISCIPLINE, "Discipline 94"),
                    WeekTop(PHYSIQUE, "Physique 41"),
                    WeekTop(BUSINESS, "Business 31"),
                ),
        )

    /**
     * A week with nothing logged: every day the empty slab, and the words in place of the pills.
     */
    val weekEmpty =
        week.copy(
            total = "0 XP",
            delta = null,
            pace = WeekPace("pace 100", 0.8333333f),
            bars =
                week.bars.map {
                    it.copy(
                        xp = 0,
                        segs = emptyList(),
                        a11y = it.date + " 0 XP",
                        tip = WeekTip(it.tip.label, "0 XP — a rest day"),
                    )
                },
            top = emptyList(),
        )

    val healthOn =
        HealthTile.On(
            "Activity today",
            "Samsung Health · 7 min ago",
            "6,420",
            "steps",
            "3,580 to your 10,000 goal",
            64,
            listOf(
                HealthDay("T", 0.20666667f, false, "3,100"),
                HealthDay("F", 0.80333334f, false, "12,050"),
                HealthDay("S", 0.53333336f, false, "8,000"),
                HealthDay("S", 0f, false, "0"),
                HealthDay("M", 0.62666667f, false, "9,400"),
                HealthDay("T", 1f, false, "15,000"),
                HealthDay("W", 0.428f, true, "6,420"),
            ),
            listOf(
                CardChip("Workouts", "2 · 1h 35m"),
                CardChip("Last night", "7h 32m"),
                CardChip("Weight", "181.4 lb"),
            ),
            "Details ›",
        )

    val healthGoal =
        HealthTile.On(
            "Activity today",
            "1 days ago",
            "5,000",
            "steps",
            "Goal reached 🎉",
            100,
            listOf(HealthDay("W", 1f, true, "5,000")),
            listOf(CardChip("Workouts", "none yet")),
            "Details ›",
        )

    val healthWaiting =
        HealthTile.On(
            "Activity today",
            "not synced yet",
            "–",
            "steps",
            "Waiting for the first sync",
            0,
            emptyList(),
            emptyList(),
            "Details ›",
        )

    val healthOff =
        HealthTile.Off(
            "Activity today",
            "Connect Samsung Health to see your steps, workouts, sleep and weight here, and turn them into XP.",
            "Set up health sync",
        )

    private fun app(pkg: String, label: String, image: String?) =
        AppItem(pkg, label, image, "Open $label")

    val apps =
        AppsTile(
            "Apps",
            "Edit ›",
            listOf(
                app("com.example.one", "Maps & more", "icon-green"),
                app("com.example.two", "Two", null),
                app("c.3", "Calendar", "icon-blue"),
                app("c.4", "Spotify", "icon-green"),
                app("c.5", "Messages", "icon-orange"),
                app("c.6", "Camera", "icon-blue"),
            ),
            "Add app",
            "",
        )

    val appsEmpty =
        AppsTile(
            "Apps",
            "Edit ›",
            emptyList(),
            "Add app",
            "Add the apps you open most and they will sit right here.",
        )

    val pet = CatTile(CatMode.Alive, "COMPANION", "Miso", hearts = 3)
    val twoCats = CatTile(CatMode.Alive, "COMPANION", "Miso & Pip", hearts = 2)
    val catGone = CatTile(CatMode.Gone, "IN MEMORY", "Miso")
    val stray =
        CatTile(
            CatMode.Invite,
            "A VISITOR",
            "A stray kitten keeps stopping by",
            text = "Open the door and see who it is.",
        )
}
