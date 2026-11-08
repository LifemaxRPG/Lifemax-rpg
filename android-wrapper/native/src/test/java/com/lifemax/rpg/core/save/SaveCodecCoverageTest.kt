package com.lifemax.rpg.core.save

import com.lifemax.rpg.core.economy.Activity
import com.lifemax.rpg.core.economy.CareDay
import com.lifemax.rpg.core.economy.DecayEntry
import com.lifemax.rpg.core.economy.DecaySettings
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.FoodEntry
import com.lifemax.rpg.core.economy.HealthConnect
import com.lifemax.rpg.core.economy.HealthState
import com.lifemax.rpg.core.economy.JournalCoach
import com.lifemax.rpg.core.economy.Money
import com.lifemax.rpg.core.economy.MoneyTransaction
import com.lifemax.rpg.core.economy.PlanChecks
import com.lifemax.rpg.core.economy.Quest
import com.lifemax.rpg.core.economy.Rules
import com.lifemax.rpg.core.economy.Shields
import com.lifemax.rpg.core.economy.WeightPoint
import com.lifemax.rpg.core.economy.XpShare
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every part of the model is written and read back: a save with every field set to something other
 * than its default survives encode then decode unchanged. A new field in the model fails
 * [modelFieldsAreAllCovered] until the codec writes it, so the model can never hold something the
 * codec silently drops.
 */
class SaveCodecCoverageTest {
    @Test
    fun modelFieldsAreAllCovered() {
        val fields = fieldsOf(EconomySave::class.java)
        assertEquals(COVERED, fields)
        assertEquals(26, fieldsOf(Activity::class.java).size)
        assertEquals(10, fieldsOf(Quest::class.java).size)
        assertEquals(15, fieldsOf(HealthState::class.java).size)
        assertEquals(6, fieldsOf(Shields::class.java).size)
        assertEquals(3, fieldsOf(JournalCoach::class.java).size)
        assertEquals(3, fieldsOf(JournalCoach.Welcome::class.java).size)
    }

    @Test
    fun aFullyPopulatedSaveRoundTrips() {
        val empty = SaveTestData.decoded(JsonObject.EMPTY, SaveReading.STRICT)
        val out = SaveTestData.encoded(empty.document, FULL)
        assertEquals(FULL, SaveTestData.decoded(out.text, SaveReading.STRICT).save)
        val again = SaveTestData.decoded(out.text, SaveReading.STRICT)
        assertEquals(out.text, SaveTestData.encoded(again.document, again.save).text)
    }

    private fun fieldsOf(type: Class<*>): List<String> =
        type.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }

    private companion object {
        /**
         * The model's fields, all written by [EconomySaveWriter] (`rules` derives from
         * customRules).
         */
        val COVERED =
            listOf(
                "activities",
                "attrXp",
                "skillXp",
                "rules",
                "customRules",
                "restedXp",
                "sparks",
                "perk",
                "perksOwned",
                "attrStars",
                "ascended",
                "decayTotal",
                "decayApplied",
                "decayLog",
                "decay",
                "legacyCharXpBonus",
                "xpSchemaV2",
                "quests",
                "questDone",
                "restDays",
                "restTokens",
                "restBoughtAt",
                "shields",
                "themesOwned",
                "theme",
                "toysBought",
                "nutriDone",
                "calorieGoal",
                "foodLog",
                "careDay",
                "money",
                "actXp",
                "planChecks",
                "health",
                "weight",
                "devMode",
                "rerollsUsed",
                "journal",
                "dayReflections",
            )

        private val customRules =
            mapOf(
                "codec" to Rules.defaults.byType.getValue("reading").copy(base = 21.0, desc = "d")
            )

        val FULL =
            EconomySave(
                activities =
                    listOf(
                        Activity(
                            "a1",
                            "reading",
                            "Reading",
                            "i",
                            30.0,
                            1.2,
                            "Reading",
                            "intelligence",
                            "social",
                            24.0,
                            "2026-10-28T12:00:00.000Z",
                            "n",
                            listOf(XpShare("intelligence", 17.0)),
                            "health",
                            true,
                            true,
                            "daily",
                            "op",
                            "ld",
                            3.0,
                            "hc",
                            "act",
                            "link",
                            "done",
                            "session",
                            "2026-10-28",
                        )
                    ),
                attrXp = mapOf("physique" to 1.5),
                skillXp = mapOf("Reading" to 24.0),
                rules = Rules.defaults.mergedWith(customRules),
                customRules = customRules,
                restedXp = 12.0,
                sparks = 40.0,
                perk = "scholar",
                perksOwned = listOf("scholar"),
                attrStars = mapOf("physique" to 1.0),
                ascended = mapOf("physique" to 500.0),
                decayTotal = mapOf("social" to 3.0),
                decayApplied = mapOf("social" to 20000.0),
                decayLog = listOf(DecayEntry("2026-10-27", "social", 3.0)),
                decay = DecaySettings(false, 5.0, 1.0),
                legacyCharXpBonus = 7.0,
                xpSchemaV2 = true,
                quests =
                    listOf(
                        Quest(
                            "q",
                            "Q",
                            "weekly",
                            15.0,
                            "social",
                            2,
                            "custom",
                            "d",
                            true,
                            "act:reading:20",
                        )
                    ),
                questDone = mapOf("q_2026-W44" to "a1"),
                restDays = mapOf("2026-10-25" to true),
                restTokens = 1.0,
                restBoughtAt = 1.7e12,
                shields = Shields(2.0, 20000.0, 1.0, mapOf("2026-10-20" to true), 3.0, 1.6e12),
                themesOwned = listOf("dusk"),
                theme = "dusk",
                toysBought = mapOf("ball" to 1.0),
                nutriDone = mapOf("2026-10-28" to 1.0),
                calorieGoal = 2100.0,
                foodLog = listOf(FoodEntry("2026-10-28", 400.0)),
                careDay = CareDay("2026-10-28", feed = true, play = true, pet = true, paid = true),
                money =
                    Money(
                        10.0,
                        listOf(MoneyTransaction("t", "deposit", 5.0, "n", "2026-10-28", "a1")),
                        15.0,
                    ),
                actXp = mapOf("c1" to 30.0),
                planChecks =
                    mapOf("2026-10-28" to PlanChecks(mapOf("w" to listOf(true, false)), "a1")),
                health =
                    HealthState(
                        true,
                        true,
                        true,
                        true,
                        true,
                        mapOf("k" to 1.0),
                        1.0,
                        2.0,
                        "m",
                        "ok",
                        listOf("Steps"),
                        "hc",
                        HealthConnect.StepDay("2026-10-28", 4000.0),
                        3.0,
                        listOf(WeightPoint(3.0, 150.0)),
                    ),
                weight = 150.0,
                devMode = true,
                rerollsUsed = 1.0,
                journal =
                    JournalCoach(
                        mapOf("2026-10-28" to true),
                        mapOf("W2026-44" to JournalCoach.Review("yes", true)),
                        mapOf("2026-10-28" to JournalCoach.Welcome(4.0, setOf("pet"), 1)),
                    ),
                dayReflections = mapOf("2026-10-28" to "words"),
            )
    }
}
