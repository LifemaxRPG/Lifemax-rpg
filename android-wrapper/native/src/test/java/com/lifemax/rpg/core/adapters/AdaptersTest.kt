package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.EconomyClock
import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.Health
import com.lifemax.rpg.core.economy.HealthConnect
import com.lifemax.rpg.core.economy.Storage
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.SaveDecoding
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The adapters' own behaviour: what the golden files cannot show, and the edges they pin. */
class AdaptersTest {
    private val day = 86_400_000L

    private fun json(text: String): JsonObject =
        (JsonReader.read(text) as JsonRead.Parsed).value as JsonObject

    private fun viewOf(text: String): SaveView =
        SaveView.of(SaveHolder().load(text) as SaveDecoding.Decoded)

    @Test
    fun theClockIsReadAfreshAtEveryReading() {
        val device = FixedDeviceClock("2026-10-28T23:30:00.000Z", "UTC")
        assertEquals("2026-10-28", device.reading().today)
        device.now += 60 * 60_000L
        assertEquals("2026-10-29", device.reading().today)
        device.now -= 2 * day // the owner sets the clock back by hand: followed at once
        assertEquals("2026-10-27", device.reading().today)
        val before = device.reading()
        device.zone = ZoneId.of("Pacific/Pago_Pago") // a new zone applies from the next reading
        assertEquals("2026-10-27", before.today)
        assertEquals("2026-10-26", device.reading().today)
    }

    @Test
    fun oneReadingServesAWholeAction() {
        var reads = 0
        val device =
            object : DeviceClock {
                override fun nowMillis() = (reads++ * day) + 1_000_000_000_000L

                override fun zone(): ZoneId = ZoneId.of("UTC")
            }
        val factory = ContextFactory(device, { null }, NoPlanFacts)
        val inputs = factory.begin()
        assertEquals("one instant per action", 1, reads)
        assertEquals(inputs.economy.now, inputs.economy.clock.now)
        factory.begin()
        assertEquals(2, reads)
    }

    @Test
    fun noSaveMeansNoHandContextAndEmptyFacts() {
        val inputs =
            ContextFactory(
                    FixedDeviceClock("2026-10-28T12:00:00.000Z", "UTC"),
                    { null },
                    NoPlanFacts,
                )
                .begin()
        assertNull(inputs.hand)
        assertEquals(0.0, inputs.economy.handFacts.waterPercent, 0.0)
        assertTrue(inputs.economy.handFacts.petCounts.isEmpty())
        assertFalse(inputs.economy.handFacts.planDone)
    }

    @Test
    fun readingNeverChangesTheSaveOrItsStorageMode() {
        val view = viewOf("""{"waterLog":[{"date":"2026-10-28","ml":500}]}""")
        val inputs =
            ContextFactory(
                    FixedDeviceClock("2026-10-28T12:00:00.000Z", "UTC"),
                    { view },
                    NoPlanFacts,
                )
                .begin(Storage.ReadOnly)
        assertEquals(Storage.ReadOnly, inputs.economy.storage)
        assertEquals(25.0, inputs.economy.handFacts.waterPercent, 0.0)
        assertSame(view, SaveView(view.economy, view.stored).let { view })
        assertEquals(
            viewOf("""{"waterLog":[{"date":"2026-10-28","ml":500}]}""").stored,
            view.stored,
        )
    }

    @Test
    fun theWaterPercentSaysWhatThePagesComparisonSays() {
        // page: total >= goal * pct / 100; adapter: total * 100 / goal >= pct, whole millilitres
        var checked = 0
        for (goal in listOf(1, 3, 7, 250, 1000, 1500, 2000, 2250, 2500, 3000, 3333, 5000, 9999)) {
            for (pct in 1..100) for (total in 0..goal * 2 step maxOf(1, goal / 40)) {
                val page = total >= goal * pct / 100.0
                val mine = total * 100.0 / goal >= pct
                assertEquals("goal $goal, $pct%, total $total", page, mine)
                checked++
            }
        }
        assertTrue(checked > 10_000)
    }

    @Test
    fun theWaterGoalIsReadAsThePageReadsIt() {
        fun percent(meta: String, ml: Int = 1000): Double =
            SaveFacts.waterPercent(
                json("""{"mealsMeta":$meta,"waterLog":[{"date":"d","ml":$ml}]}"""),
                "d",
            )
        assertEquals(50.0, percent("{}"), 0.0) // default 2000
        assertEquals(50.0, percent("""{"waterGoalMl":0}"""), 0.0)
        assertEquals(50.0, percent("""{"waterGoalMl":"junk"}"""), 0.0)
        assertEquals(50.0, percent("null"), 0.0)
        assertEquals(100.0, percent("""{"waterGoalMl":"1000"}"""), 0.0)
        assertEquals(100_000.0, percent("""{"waterGoalMl":-5}"""), 0.0) // Math.max(1, goal)
    }

    @Test
    fun luckCountsOnlyTodaysCurrentFourDiceRoll() {
        fun score(luck: String) = SaveFacts.luckScore(json("""{"luck":$luck}"""), "d")
        assertEquals(6200.0, score("""{"date":"d","ver":2,"dice":[1,2,3,4],"score":6200}""")!!, 0.0)
        assertNull(score("""{"date":"e","ver":2,"dice":[1,2,3,4],"score":6200}"""))
        assertNull(score("""{"date":"d","ver":1,"dice":[1,2,3,4],"score":6200}"""))
        assertNull(score("""{"date":"d","ver":2,"dice":[1,2,3],"score":6200}"""))
        assertNull(score("""{"date":"d","ver":2,"dice":[1,2,3,4],"score":"6200"}"""))
        assertNull(SaveFacts.luckScore(json("{}"), "d"))
    }

    @Test
    fun healthWindowsCountBackFromTheActionsInstant() {
        val reads = FakeHealthReads()
        val device = FixedDeviceClock("2026-10-28T12:00:00.000Z", "UTC")
        val port = reads.at(device.reading())
        val now = device.now
        port.steps(30)
        port.weight(60)
        port.exercise(now - 7 * day, now)
        assertEquals(
            listOf(
                "steps ${now - 30 * day} $now",
                "weight ${now - 60 * day} $now",
                "exercise ${now - 7 * day} $now",
            ),
            reads.asked,
        )
    }

    @Test
    fun aFailedHealthReadReachesTheSyncAsTheFailureMessage() {
        val device = FixedDeviceClock("2026-10-28T12:00:00.000Z", "UTC")
        val reads =
            FakeHealthReads(
                status = HealthConnect.Status("available", listOf("steps")),
                failing = "steps",
            )
        val view = viewOf("""{"health":{"on":true,"steps":true}}""")
        val inputs = ContextFactory(device, { view }, NoPlanFacts, reads).begin()
        val outcome = Health.sync(view.economy, inputs.health, false, inputs.economy)
        assertEquals("Sync failed: ${FakeHealthReads.FAIL}", outcome.save.health.message)
    }

    @Test
    fun aPhoneWithoutHealthConnectIsReportedUnavailable() {
        val device = FixedDeviceClock("2026-10-28T12:00:00.000Z", "UTC")
        val view = viewOf("""{"health":{"on":true}}""")
        val inputs = ContextFactory(device, { view }, NoPlanFacts).begin()
        val outcome = Health.sync(view.economy, inputs.health, true, inputs.economy)
        assertEquals("Health Connect is not available on this phone.", outcome.save.health.message)
    }

    @Test
    fun theCoachStoreReadsTheSavesMemoryAndNothingElse() {
        val view =
            viewOf(
                """{"journalCoach":{"days":{"2026-10-28":{"xpAwarded":15}}},"dayReflections":{"2026-10-28":"words"}}"""
            )
        val store = SaveCoachStore { view }
        assertEquals(mapOf("2026-10-28" to true), store.journal().days)
        assertEquals("words", store.reflection("2026-10-28"))
        assertNull(store.reflection("2026-10-27"))
        val empty = SaveCoachStore { null }
        assertTrue(empty.journal().days.isEmpty())
        assertEquals(EconomySave().journal, empty.into(EconomySave()).journal)
    }

    @Test
    fun aRefusedSaveKeepsTheLastGoodOne() {
        val holder = SaveHolder()
        assertNull(holder.view())
        holder.load("""{"waterLog":[]}""")
        val good = holder.view()
        assertTrue(holder.load("not json") is SaveDecoding.Refused)
        assertSame(good, holder.view())
        holder.clear()
        assertNull(holder.view())
    }

    @Test
    fun recordDatesReadAsThePageReadsThem() {
        val clock = EconomyClock(0, ZoneId.of("America/New_York"))
        assertEquals("1969-12-31", PageDays.dayOf(JsonNull, clock))
        assertEquals("Invalid Date", PageDays.dayOf(null, clock))
        assertEquals("1969-12-31", PageDays.dayOf(JsonNumber(0.0), clock))
        assertEquals("Invalid Date", PageDays.dayOf(JsonNumber(Double.NaN), clock))
        assertEquals("2026-10-28", PageDays.dayOf(JsonString("2026-10-28T12:00:00.000Z"), clock))
    }

    @Test
    fun randomIdsHaveThePagesShapes() {
        val ids = RandomIdSource()
        assertTrue(ids.uuid().matches(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")))
        assertTrue(ids.token().matches(Regex("[0-9a-z]{4}")))
        assertFalse(ids.uuid() == ids.uuid())
    }
}
