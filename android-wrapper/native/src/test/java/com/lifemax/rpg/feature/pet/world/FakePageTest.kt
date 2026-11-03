package com.lifemax.rpg.feature.pet.world

import com.lifemax.rpg.feature.pet.data.toPetReply
import com.lifemax.rpg.feature.pet.world.WorldFixtures.NOW
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The FakePage driver plays the page's rules: its block decodes strictly, every command answers as
 * the page does (ok only when saved, a repeat "Already recorded.", a refused save taken back), and
 * the world save checks version, cats, size, epoch and revision like 50-native-cat-world.js.
 */
class FakePageTest {
    private fun house(two: Boolean = false) =
        FakePage().apply {
            adopt(
                FakePage.Cat(
                    born = NOW - 40 * DAY_MS,
                    name = "Ted",
                    bondPts = FakePage.BOND_AT[5],
                    lastFed = NOW - 5 * FakePage.HOUR,
                )
            )
            if (two)
                adopt(
                    FakePage.Cat(
                        born = NOW - 20 * DAY_MS,
                        name = "Lily",
                        lastFed = NOW - 5 * FakePage.HOUR,
                    )
                )
        }

    private fun FakePage.reply(c: WorldCommand) = WorldCommandJson.reply(send(c))!!

    private fun FakePage.blob(): WorldBlob {
        val ids = living().map { it.id }
        return WorldFixtures.blob()
            .copy(
                cats = ids.associateWith { WorldFixtures.cat() },
                outbox = emptyList(),
                gift = null,
                toys = emptyList(),
            )
    }

    @Test
    fun theBlockDecodesAndSeqRisesOnlyWithAChange() {
        val page = house(two = true)
        val a = WorldBlockJson.decode(page.block())
        assertEquals(page.living().map { it.id }, a.cats.map { it.id })
        assertTrue(a.cats.all { it.legacy != null })
        assertEquals(a.seq, WorldBlockJson.decode(page.block()).seq)
        page.send(PetDone(OpIds.of(a.cats[0].id, "pet", 1), a.cats[0].id, 1500))
        assertTrue(WorldBlockJson.decode(page.block()).seq > a.seq)
    }

    @Test
    fun aWorldSaveFollowsThePagesChecks() {
        val page = house(two = true)
        val ok = page.reply(SaveWorld("w1", 0, 1, page.blob()))
        assertTrue(ok.ok && ok.changed && ok.rev == 1L && ok.epoch == 0L)
        val again = page.reply(SaveWorld("w1", 0, 1, page.blob()))
        assertTrue(again.ok && !again.changed && again.message == WorldReply.ALREADY)
        assertEquals("stale", page.reply(SaveWorld("w2", 0, 1, page.blob())).why)
        assertEquals("stale", page.reply(SaveWorld("w3", 5, 2, page.blob())).why)
        val one =
            page.blob().let {
                it.copy(cats = it.cats.entries.take(1).associate { e -> e.key to e.value })
            }
        assertEquals("shape", page.reply(SaveWorld("w4", 0, 2, one)).why)
        val blobJson = WorldBlobJson.encode(page.blob()).put("v", 2)
        val newer =
            page.command(
                "world",
                JSONObject().put("op", "w5").put("epoch", 0).put("rev", 2).put("blob", blobJson),
            )
        assertEquals("shape", WorldCommandJson.reply(newer)!!.why)
        // the stored world comes back in the block, and the legacy goes for the cats it holds
        val b = WorldBlockJson.decode(page.block())
        assertEquals(1L, b.stored!!.rev)
        assertTrue(b.cats.all { it.legacy == null })
        assertEquals(page.blob(), b.stored!!.blob)
    }

    @Test
    fun aRefusedSaveIsTakenBack() {
        val page = house()
        page.saveFails = true
        val r = page.reply(SaveWorld("w1", 0, 1, page.blob()))
        assertFalse(r.ok)
        assertEquals("unsaved", r.why)
        assertEquals(null, page.stored)
        val ted = page.living()[0]
        val pet = page.reply(PetDone("p1", ted.id, 2000))
        assertFalse(pet.ok)
        assertEquals(0, page.living()[0].petsToday)
        page.saveFails = false
        assertTrue(
            "the same op after a failed save is done once",
            page.reply(PetDone("p1", ted.id, 2000)).changed,
        )
        assertEquals(1, page.living()[0].petsToday)
    }

    @Test
    fun aNewEpochAfterARestoreRefusesTheOldWorld() {
        val page = house()
        page.reply(SaveWorld("w1", 0, 1, page.blob()))
        page.restoreBackup()
        val old = page.reply(SaveWorld("w2", 0, 2, page.blob()))
        assertEquals("stale", old.why)
        assertEquals(1L, old.epoch)
        assertTrue(page.reply(SaveWorld("w3", 1, 2, page.blob())).ok)
    }

    @Test
    fun theReportsKeepThePagesBooks() {
        val page = house(two = true)
        val (ted, lily) = page.living().map { it.id }
        // three pets a day count, a short stroke does not, a repeat is recorded once
        assertTrue(page.reply(PetDone("p1", ted, 1200)).changed)
        assertTrue(page.reply(PetDone("p1", ted, 1200)).message == WorldReply.ALREADY)
        assertFalse(page.reply(PetDone("p2", ted, 900)).changed)
        assertTrue(page.reply(PetDone("p3", ted, 1200)).changed)
        assertTrue(page.reply(PetDone("p4", ted, 1200)).changed)
        val fourth = page.reply(PetDone("p5", ted, 1200))
        assertTrue(fourth.ok && !fourth.changed)
        assertEquals(3, page.living()[0].petsToday)
        // a first moment is recorded once whatever the op
        assertTrue(
            page
                .reply(
                    MomentReport(
                        OpIds.firstMoment(ted, "first_glass"),
                        ted,
                        "first_glass",
                        NOW,
                        null,
                    )
                )
                .changed
        )
        assertFalse(page.reply(MomentReport("other-op", ted, "first_glass", NOW, null)).changed)
        assertTrue(
            page
                .reply(
                    MomentReport(
                        OpIds.quirk(ted, "lap_barnacle"),
                        ted,
                        "quirk",
                        NOW,
                        "lap_barnacle",
                    )
                )
                .changed
        )
        assertEquals(listOf("lap_barnacle"), page.living()[0].acquired)
        // the gift: Ted is devoted, once a day; Lily is not
        assertTrue(page.reply(GiftTaken("g1", ted)).changed)
        assertEquals("refused", page.reply(GiftTaken("g2", ted)).why)
        assertEquals("refused", page.reply(GiftTaken("g3", lily)).why)
        // the pair's day cap stays the page's
        repeat(8) { page.reply(SocialReport("s$it", "groom", listOf(ted, lily), null, NOW)) }
        assertEquals(FakePage.REL_START + FakePage.DAY_CAP, page.pair!!.rel, 1e-9)
        assertEquals(8, page.pair!!.n["groom"])
        page.reply(SocialReport("s9", "standoff", listOf(ted, lily), lily, NOW))
        assertEquals(1, page.pair!!.wins[lily])
        // dwell feeds the likes in hours; a play counts twice a day
        page.reply(
            DwellReport("d1", lily, page.today, mapOf("box" to 7200), mapOf("mouse" to 1800))
        )
        assertEquals(2.0, page.living()[1].spotHours["box"]!!, 1e-9)
        assertTrue(page.reply(PlayDone("y1", lily, "mouse", true)).changed)
        page.reply(PlayDone("y2", lily, "mouse", true))
        page.reply(PlayDone("y3", lily, "mouse", true))
        assertEquals(2, page.living()[1].playsToday)
        assertFalse(
            "canPlay follows the count",
            WorldBlockJson.decode(page.block()).cats[1].care.canPlay,
        )
        // an unknown cat is refused, a malformed payload is "shape"
        assertEquals("refused", page.reply(PetDone("p9", "c1", 1500)).why)
        assertEquals(
            "shape",
            WorldCommandJson.reply(
                    page.command("pet", JSONObject().put("op", "x").put("cat", 0).put("heldMs", 1))
                )!!
                .why,
        )
    }

    @Test
    fun theCareCommandsAnswerInTheCatTabsShape() {
        val page = house()
        val fed = page.send(CareFeed("f1")).toPetReply()
        assertNotNull(fed)
        assertTrue(fed!!.ok && fed.changed)
        assertEquals("full", page.send(CareFeed("f2")).toPetReply()!!.why)
        assertEquals(WorldReply.ALREADY, page.send(CareFeed("f1")).toPetReply()!!.message)
        assertTrue(page.send(CareVacation(true)).toPetReply()!!.changed)
        assertEquals(WorldReply.ALREADY, page.send(CareVacation(true)).toPetReply()!!.message)
        assertEquals("vacation", page.send(CareFeed("f3")).toPetReply()!!.why)
        assertTrue(page.send(CareRename("Teddy")).toPetReply()!!.changed)
        assertEquals("Teddy", page.living()[0].name)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}
