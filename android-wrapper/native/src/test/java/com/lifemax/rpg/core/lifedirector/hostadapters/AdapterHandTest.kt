package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomyContext
import com.lifemax.rpg.core.economy.LifeDirector
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.quests.HandTemplate
import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveReading
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hand-written checks of the adapter's typed surface, beside the golden replay. */
class AdapterHandTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilli()

    private fun host(
        json: String,
        zone: String = "UTC",
        storage: Storage = Storage.Writable,
        plans: PlanDays = NoPlanDays,
        meals: MealDays = NoMealDays,
    ): EconomyHost {
        val read = JsonReader.read(json) as JsonRead.Parsed
        val stored = read.value as JsonObject
        val decoded = EconomySaveCodec.decode(stored, SaveReading.AS_PAGE) as SaveDecoding.Decoded
        val ctx = {
            EconomyContext(EconomyClock(now, ZoneId.of(zone)), AdapterGolden.CountedIds(), storage)
        }
        return (EconomyHost.of(
                decoded.document.stored,
                decoded.save,
                ctx,
                AdapterGolden.library,
                plans,
                meals,
            ) as HostAnswer.Answer)
            .value
    }

    private val ep = Instant.parse("2026-10-28T12:00:00Z").epochSecond.toDouble()

    @Test
    fun countsAndMinutesReadTheWordsAsThePageDoes() {
        assertEquals(3.0, HostTexts.parseCount("Complete 3 workouts"), 0.0)
        assertEquals(2.0, HostTexts.parseCount("Go for TWO long runs"), 0.0)
        assertEquals(1.0, HostTexts.parseCount("Run twice this week"), 0.0)
        assertEquals(14.0, HostTexts.parseCount("99 runs"), 0.0)
        assertEquals(1.0, HostTexts.parseCount("0 sessions"), 0.0)
        assertEquals(1.0, HostTexts.parseCount("x3 sessions"), 0.0)
        // JavaScript's \s takes a no-break space; Java's would not
        assertEquals(3.0, HostTexts.parseCount("Workout 3 times"), 0.0)
        assertEquals(30.0, HostTexts.parseMinutes("30-minute walk"), 0.0)
        assertEquals(5.0, HostTexts.parseMinutes("Stretch 1 min"), 0.0)
        assertEquals(234.0, HostTexts.parseMinutes("1234 min"), 0.0)
        assertEquals(0.0, HostTexts.parseMinutes("an hour"), 0.0)
    }

    @Test
    fun tagsFollowKindThenWordsThenAttribute() {
        fun q(name: String, kind: String? = null, attribute: String? = null) =
            BoardQuest("x", "x", name, null, "daily", kind, attribute, null, null, false, 0.0, null)
        assertEquals("fitness.cardio", HostTexts.mapQuestTag(q("Read", kind = "cardio")))
        assertEquals("fitness.strength", HostTexts.mapQuestTag(q("WorKout")))
        assertEquals("business", HostTexts.mapQuestTag(q("Call", attribute = "business")))
        assertEquals("general", HostTexts.mapQuestTag(q("Call", attribute = "charisma")))
        assertEquals("general", HostTexts.tagForType("yoga"))
        assertEquals("custom", HostTexts.typeForTag("social"))
    }

    @Test
    fun milestonesAndUntimedCards() {
        val epic =
            BoardQuest(
                "m",
                "m",
                "50 Sessions Logged",
                null,
                "epic",
                null,
                null,
                null,
                null,
                false,
                0.0,
                null,
            )
        val project =
            BoardQuest(
                "p",
                "p",
                "Record your form",
                null,
                "once",
                "milestone",
                null,
                null,
                null,
                false,
                0.0,
                null,
            )
        assertTrue(HostTexts.isMilestone(epic))
        assertFalse(HostTexts.isProjectMilestone(epic))
        assertTrue(HostTexts.isProjectMilestone(project))
        val card =
            BoardQuest(
                "d",
                "d",
                "Posture reset",
                "Stand tall",
                "daily",
                "hand",
                null,
                null,
                "self",
                true,
                10.0,
                null,
            )
        assertTrue(HostTexts.isUntimedQuest(card))
        assertFalse(HostTexts.isUntimedQuest(card.copyText("Five minutes of stretching")))
        assertFalse(HostTexts.isUntimedQuest(null))
    }

    private fun BoardQuest.copyText(desc: String) =
        BoardQuest(id, idText, name, desc, cadence, kind, attribute, day, verify, hand, xp, stage)

    @Test
    fun aQuestValueThePageNeverWritesIsRefused() {
        val read =
            BoardQuests.read(
                (JsonReader.read("""[{"id":7,"name":"x"}]""") as JsonRead.Parsed).value
            )
        assertEquals(HostRefusal.UNREADABLE_QUEST, (read as HostAnswer.Refused).reason)
        assertEquals("quests[0].id", read.detail)
    }

    @Test
    fun notConnectedAnswersAreEmpty() {
        val h = host("""{"activities":[],"quests":null}""")
        assertFalse(h.connected)
        assertEquals("player", h.playerId())
        assertEquals(
            HostAnswer.Answer(emptyList<HostQuest>()),
            h.readQuests(ep, "Mars/Base", QuestOverlay(), emptySet()),
        )
        assertEquals(emptyList<HostActivity>(), h.activitiesSince(null))
        assertEquals(
            "HOST_NOT_CONNECTED",
            h.awardActivity(AwardEvent("e1", "reading", ep, 30.0, "Read", "COMPLETED"), null).reason,
        )
        // the page reads the board here without asking whether it is connected (LA03)
        val retired = h.retiredUntimedQuest(QuestLink("dh_2026-10-27_0_0", "Posture reset"))
        assertEquals(HostRefusal.NO_BOARD, (retired as HostAnswer.Refused).reason)
    }

    @Test
    fun anUnknownZoneOrTimeIsRefusedNotThrown() {
        val h =
            host(
                """{"activities":[],"quests":[{"id":"w","name":"Run 3 times","cadence":"weekly","xp":10}]}"""
            )
        assertEquals(
            HostRefusal.UNKNOWN_ZONE,
            (h.readQuests(ep, "Mars/Base", QuestOverlay(), emptySet()) as HostAnswer.Refused).reason,
        )
        assertEquals(
            HostRefusal.INVALID_TIME,
            (h.readQuests(Double.NaN, "UTC", QuestOverlay(), emptySet()) as HostAnswer.Refused)
                .reason,
        )
        assertEquals(
            HostRefusal.INVALID_TIME,
            (QuestReading.weekBounds(1e300, "UTC") as HostAnswer.Refused).reason,
        )
        assertEquals(
            HostRefusal.UNKNOWN_ZONE,
            (h.mealOfBlock(MealBlock("Lunch", ep), "Nowhere") as HostAnswer.Refused).reason,
        )
    }

    @Test
    fun aWeeklyQuestComesWithNextWeeksCopy() {
        val h =
            host(
                """{"activities":[],"quests":[{"id":"w","name":"Run 3 times","cadence":"weekly","xp":10}]}"""
            )
        val qs =
            (h.readQuests(
                    ep,
                    "UTC",
                    QuestOverlay(mapOf("w_2026-10-26" to listOf("a", "b"))),
                    setOf("b"),
                ) as HostAnswer.Answer)
                .value
        assertEquals(listOf("w", "w@next"), qs.map { it.id })
        assertEquals(3.0, qs[0].requiredCount, 0.0)
        assertEquals(1.0, qs[0].progressCount!!, 0.0)
        assertEquals("w_2026-11-02", qs[1].hostRef!!.key)
        assertTrue(qs[1].hostRef!!.future)
    }

    @Test
    fun anAwardIsPaidOnceAndTakenBackWhenTheSaveFails() {
        val h = host("""{"activities":[],"quests":[]}""")
        val e = AwardEvent("e1", "fitness.strength", ep - 3600, 45.0, "Push", "COMPLETED")
        assertTrue(h.awardActivity(e, "Push A").awarded)
        assertEquals("ALREADY_IN_LIFEMAX", h.awardActivity(e, null).reason)
        assertEquals(1, h.save.activities.size)
        val full = host("""{"activities":[],"quests":[]}""", storage = Storage.Full)
        assertEquals("HOST_SAVE_FAILED", full.awardActivity(e, null).reason)
        assertTrue(full.save.activities.isEmpty())
    }

    @Test
    fun theAwardIsDatedOnTheDevice() {
        // 03:30 UTC is still the 27th in New York (D1)
        val h = host("""{"activities":[],"quests":[]}""", zone = "America/New_York")
        val e =
            AwardEvent(
                "e1",
                "reading",
                Instant.parse("2026-10-28T03:30:00Z").epochSecond.toDouble(),
                30.0,
                "Read",
                "COMPLETED",
            )
        h.awardActivity(e, null)
        val clock = EconomyClock(now, ZoneId.of("America/New_York"))
        assertEquals("2026-10-27", clock.dayOf(h.save.activities.single().date))
    }

    @Test
    fun aBountyGoesThroughTheBoard() {
        val h =
            host(
                """{"activities":[],"quests":[{"id":"d","name":"Run","cadence":"daily","xp":15}]}"""
            )
        assertEquals(
            "PERIOD_CHANGED",
            h.completeQuest(LifeDirector.QuestRef("d", "d_2026-10-27")).reason,
        )
        val r = h.completeQuest(LifeDirector.QuestRef("d", "d_2026-10-28"))
        assertTrue(r.completed)
        assertEquals(listOf("Quest complete · +15 XP"), h.toasts)
        assertEquals(
            "ALREADY_DONE",
            h.completeQuest(LifeDirector.QuestRef("d", "d_2026-10-28")).reason,
        )
        assertFalse(
            h.completeQuest(LifeDirector.QuestRef("d", "d_2026-10-28", future = true)).completed
        )
    }

    @Test
    fun planSessionsComeFromThePlanPort() {
        val plans =
            object : PlanDays {
                override fun day(key: String) =
                    when (key) {
                        "2026-10-28" ->
                            PlanDay("strength", "Push", "physique", null, listOf("a", "b"), null)
                        "2026-10-29" -> PlanDay("rest", "Rest", null, null, emptyList(), null)
                        else -> PlanDay("cardio", "", null, 45.0, listOf("run"), "Easy run ")
                    }

                override fun isDone(key: String) = key == "2026-10-30"
            }
        val h = host("""{"activities":[],"quests":[],"plan":{"id":"x"}}""", plans = plans)
        val s = (h.readPlanSessions(ep, "UTC", 3.0) as HostAnswer.Answer).value
        assertEquals(listOf("2026-10-28", "2026-10-30"), s.map { it.key })
        assertEquals(30.0, s[0].sessionMinutes, 0.0)
        assertEquals("Plan: Session", s[1].title)
        assertTrue(s[1].done)
        val d =
            h.activityDescription(DescribedSession("x", listOf(QuestLink("plan:2026-10-31@next"))))
        assertEquals(HostAnswer.Answer("Easy run"), d)
    }

    @Test
    fun mealsAreFoundByDayAndSlot() {
        val meals = MealDays { day ->
            if (day == "2026-10-28")
                MealDay(
                    listOf(
                        MealItem("breakfast", "Oats", 538.5, false, false),
                        MealItem("lunch", "Pasta", 538.49, true, false),
                    )
                )
            else null
        }
        val h = host("""{"activities":[],"quests":[]}""", meals = meals)
        val m = h.scheduledMeal(MealRef(title = "Lunch", day = "2026-10-28"))!!
        assertEquals(1, m.index)
        assertEquals(538.0, m.kcal, 0.0)
        assertTrue(m.logged)
        assertEquals(
            539.0,
            h.scheduledMeal(MealRef(slot = "breakfast", day = "2026-10-28"))!!.kcal,
            0.0,
        )
        assertNull(h.scheduledMeal(MealRef(title = "Brunch", day = "2026-10-28")))
        assertNull(h.scheduledMeal(MealRef(title = "Lunch", day = "2026-02-30")))
        assertNull(h.scheduledMeal(MealRef(title = "Lunch", startEpoch = 0.0)))
        assertEquals("2026-10-28", h.scheduledMeal(MealRef(title = "Lunch", startEpoch = ep))!!.day)
    }

    /**
     * Rules the shipped content never reaches (every rest day has no items, every plan kind is in
     * the tag table, no two Hand templates share a name, none has a blank description), read from
     * the page's code (HA-17, HA-19, HA-21).
     */
    @Test
    fun rulesTheShippedContentNeverReaches() {
        val plans =
            object : PlanDays {
                override fun day(key: String) =
                    when (key) {
                        "2026-10-28" ->
                            PlanDay("Active REST", "R", null, null, listOf("walk"), null)
                        else -> PlanDay("mobility", "M", "endurance", null, listOf("flow"), null)
                    }

                override fun isDone(key: String) = false
            }
        val h = host("""{"activities":[],"quests":[],"plan":{"id":"x"}}""", plans = plans)
        val s = (h.readPlanSessions(ep, "UTC", 2.0) as HostAnswer.Answer).value
        assertEquals(listOf("2026-10-29"), s.map { it.key })
        assertEquals("fitness.cardio", s[0].tag)
        val twins =
            listOf(
                HandTemplate("a1", "physique", 1, "Twin", "One.", "self", 5),
                HandTemplate("a2", "physique", 1, "Twin", "Two.", "self", 5),
                HandTemplate("a3", "physique", 1, "Blank", "  ", "self", 5),
                HandTemplate("a4", "physique", 1, "Blank", "Kept.", "self", 5),
            )
        val board = Board(emptyList())
        assertEquals(
            HostAnswer.Answer(null),
            QuestReading.retired(board, twins, QuestLink("dh_x", "Twin")),
        )
        val link = listOf(QuestLink("dh_x", "Blank"))
        assertEquals(
            "Kept.",
            PlanReading.describe(NoPlanDays, board, twins, DescribedSession("t", link)),
        )
        assertEquals(
            "",
            PlanReading.describe(
                NoPlanDays,
                board,
                twins,
                DescribedSession("Twin", listOf(QuestLink("dh_x"))),
            ),
        )
    }

    @Test
    fun toInt32IsThePagesBitwiseOr() {
        assertEquals(1.0, PlanReading.toInt32(1.9), 0.0)
        assertEquals(-1.0, PlanReading.toInt32(-1.9), 0.0)
        assertEquals(0.0, PlanReading.toInt32(Double.NaN), 0.0)
        assertEquals(-2147483648.0, PlanReading.toInt32(2147483648.0), 0.0)
        assertEquals(0.0, PlanReading.toInt32(4294967296.0), 0.0)
    }
}
