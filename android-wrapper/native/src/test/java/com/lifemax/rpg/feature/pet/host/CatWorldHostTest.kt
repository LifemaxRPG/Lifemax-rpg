package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.world.MomentReport
import com.lifemax.rpg.feature.pet.world.OpIds
import com.lifemax.rpg.feature.pet.world.WorldBlobJson
import com.lifemax.rpg.feature.pet.world.WorldContract
import com.lifemax.rpg.feature.pet.world.WorldFixtures.NOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The whole loop against FakePage on a fake clock: a world starts from the page's block, runs with
 * a view attached, persists through the `world` command, survives a kill and a backup restore, and
 * stays the one world for every screen.
 */
class CatWorldHostTest {
    private fun WorldRig.started(): WorldRig = apply {
        project()
        host.attach()
    }

    @Test
    fun theWorldStartsOnTheFirstGoodBlockAndNotBefore() {
        val rig = WorldRig(cats = 2)
        assertFalse(rig.host.active)
        assertNull(rig.host.house)
        rig.project()
        assertTrue(rig.host.active)
        assertEquals(2, rig.host.house!!.count)
        assertEquals(1, rig.host.generation)
        assertEquals(2, rig.brain.restored!!.cats.size)
        // the same block again changes nothing: still one world
        val house = rig.host.house
        rig.project()
        assertSame(house, rig.host.house)
        assertEquals(1, rig.host.generation)
    }

    @Test
    fun withoutABrainOrABlockTheScreensKeepTheirAutopilot() {
        val none = WorldRig(cats = 1, withFactory = false)
        none.project()
        assertFalse(none.host.active)
        assertNull(none.host.house)

        val noBlock = WorldRig(cats = 1)
        noBlock.host.onProjection(hasBlock = false, accepted = false)
        noBlock.host.attach()
        noBlock.frame()
        assertFalse(noBlock.host.active)
    }

    @Test
    fun aPageThatStopsSendingTheBlockRetiresTheWorld() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(1)
        val generation = rig.host.generation
        repeat(3) { rig.host.onProjection(hasBlock = false, accepted = false) }
        assertFalse(rig.host.active)
        assertNull(rig.host.house)
        assertTrue(rig.host.generation > generation)
        // a newer block brings it back
        rig.page.cats[0].name = "Ted2"
        rig.project()
        assertTrue(rig.host.active)
    }

    @Test
    fun theWorldRunsAndIsSavedThroughTheWorldCommandNotOftenerThanOncePerMinute() {
        val rig = WorldRig(cats = 2).started()
        rig.minutes(30)
        val stored = rig.page.stored
        assertNotNull(stored)
        assertEquals(rig.page.epoch, stored!!.epoch)
        // the stored blob is native's own: it decodes strictly, with both cats and a write time
        val blob = WorldBlobJson.decode(stored.blob)
        assertEquals(rig.page.living().map { it.id }.toSet(), blob.cats.keys)
        assertTrue(blob.at in NOW..NOW + 31 * 60_000L)
        assertTrue("writes ${rig.host.writes}", rig.host.writes in 1..30)
        assertEquals(rig.host.writes.toLong(), stored.rev)
        assertTrue(rig.host.answers.keys.all { it == "ok" })
        assertTrue(rig.brain.steps > 30 * 60 * 120 - 240)
    }

    @Test
    fun aMomentIsSentOnceAndLeavesTheOutboxWhenThePageStoredIt() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(2)
        val id = rig.page.living()[0].id
        val moment =
            MomentReport(OpIds.firstMoment(id, "first_glass"), id, "first_glass", NOW, null)
        rig.brain.report(moment)
        rig.minutes(2)
        assertTrue(rig.page.cats[0].fx.contains("first_glass"))
        assertTrue(rig.port.sent.count { it.first == "moment" } == 1)
        val blob = WorldBlobJson.decode(rig.page.stored!!.blob)
        assertTrue("outbox empty once acknowledged: ${blob.outbox}", blob.outbox.isEmpty())
    }

    @Test
    fun anOutboxCommandSurvivesAKillAndIsReplayedOnceWithoutDoingItTwice() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(2)
        val id = rig.page.living()[0].id
        // the page stores everything but its answers never arrive: native cannot know
        rig.port.dropReplies = true
        val moment =
            MomentReport(OpIds.firstMoment(id, "first_glass"), id, "first_glass", NOW, null)
        rig.brain.report(moment)
        rig.minutes(1) // the command, then (after its timeout) the write that carries it
        assertTrue(rig.page.cats[0].fx.contains("first_glass"))
        val stored = WorldBlobJson.decode(rig.page.stored!!.blob)
        assertEquals(listOf(moment.op), stored.outbox.map { it.op })
        // the process dies; a new one starts from the page's stored world and replays the command
        val next = rig.restart()
        next.project()
        next.host.attach()
        next.minutes(2)
        val replays = next.port.sent.filter { it.first == "moment" }
        assertEquals("replayed once", 1, replays.size)
        assertEquals("the page did it once", setOf("first_glass"), next.page.cats[0].fx.toSet())
        assertTrue(WorldBlobJson.decode(next.page.stored!!.blob).outbox.isEmpty())
        next.minutes(2)
        assertEquals(1, next.port.sent.count { it.first == "moment" })
    }

    @Test
    fun aBackupRestoreAdoptsThePagesWorldUnderTheNewEpoch() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(3)
        val before = rig.page.stored!!
        val generation = rig.host.generation
        rig.page.restoreBackup()
        rig.minutes(3)
        assertTrue("the world was rebuilt", rig.host.generation > generation)
        val after = rig.page.stored!!
        assertEquals(rig.page.epoch, after.epoch)
        assertTrue(after.rev > before.rev)
    }

    @Test
    fun aStaleReplyHoldsWritingUntilThePagesWorldIsAdopted() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(2)
        rig.page.restoreBackup() // the page moved on and native has not heard yet
        val id = rig.page.living()[0].id
        rig.brain.report(
            MomentReport(OpIds.firstMoment(id, "first_zoomies"), id, "first_zoomies", NOW, null)
        )
        repeat(12) { rig.frame() } // the urgent write goes out under the old epoch
        assertNotNull(rig.host.answers["stale"])
        val writes = rig.host.writes
        repeat(5) { rig.frame() } // held: nothing more is sent
        assertEquals(writes, rig.host.writes)
        rig.project() // the new epoch's block arrives: adopted, writing resumes
        rig.minutes(2)
        assertEquals(rig.page.epoch, rig.page.stored!!.epoch)
    }

    @Test
    fun aRefusedSaveIsRetriedLaterAndNeverBlocksTheFrames() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(2)
        val rev = rig.page.stored!!.rev
        rig.page.saveFails = true
        rig.brain.report(
            MomentReport(
                OpIds.firstMoment("c1", "x"),
                rig.page.living()[0].id,
                "first_co",
                NOW,
                null,
            )
        )
        val steps = rig.brain.steps
        rig.minutes(1)
        assertTrue("the frames ran on", rig.brain.steps > steps)
        assertEquals(rev, rig.page.stored!!.rev)
        assertNotNull(rig.host.answers["unsaved"])
        rig.page.saveFails = false
        rig.minutes(2) // the retry comes after 30 s
        assertTrue(rig.page.stored!!.rev > rev)
        assertTrue(rig.page.cats[0].fx.contains("first_co"))
    }

    @Test
    fun twoScreensShareOneWorldAndAFrameIsNeverSteppedTwice() {
        val rig = WorldRig(cats = 2)
        rig.project()
        val a = rig.host.attach()
        val b = rig.host.attach()
        assertEquals(2, rig.host.viewCount)
        val house = rig.host.house!!
        rig.time.advanceMs(1000)
        rig.host.advance(rig.time.mono)
        val steps = rig.brain.steps
        val x = house.bodies[0].pose.x
        rig.host.advance(rig.time.mono) // the second view reports the same frame
        assertEquals(steps, rig.brain.steps)
        assertEquals(x, house.bodies[0].pose.x, 0f)
        assertSame(house, rig.host.house)
        rig.host.detach(a)
        rig.host.detach(a) // idempotent
        assertEquals(1, rig.host.viewCount)
        rig.host.detach(b)
        assertEquals(0, rig.host.viewCount)
    }

    @Test
    fun leavingSavesAndComingBackCatchesUpTheGap() {
        val rig = WorldRig(cats = 2)
        rig.project()
        val view = rig.host.attach()
        rig.minutes(2)
        rig.host.detach(view) // the last view saved what was waiting
        assertNotNull(rig.page.stored)
        assertNull("no gap, no story", rig.host.awayStory())
        // three hours pass with nothing on screen; the next view anchors and catches up
        rig.time.advanceMs(3 * 3_600_000L)
        rig.host.attach()
        rig.frame()
        val away = rig.host.awayStory()
        assertNotNull(away)
        assertTrue(away!!.lines.size in 1..WorldContract.MAX_AWAY_LINES)
        assertTrue(away.lines.all { it.length <= WorldContract.MAX_AWAY_LINE })
        assertTrue(away.from in NOW..NOW + 3 * 3_600_000L)
    }

    @Test
    fun aShortAbsenceIsNoCatchUp() {
        val rig = WorldRig(cats = 1)
        rig.project()
        val view = rig.host.attach()
        rig.minutes(1)
        rig.host.detach(view)
        rig.time.advanceMs(2 * 60_000L)
        rig.host.attach()
        rig.frame()
        assertNull(rig.host.awayStory())
    }

    @Test
    fun aColdStartCatchesUpFromTheStoredWritesTime() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(2)
        rig.host.onStop()
        rig.deliver()
        rig.time.advanceMs(5 * 3_600_000L) // the process is killed, the phone is used elsewhere
        val next = rig.restart()
        next.project()
        val story = next.host.awayStory()
        assertNotNull(story)
        assertTrue(story!!.lines.isNotEmpty())
        next.host.attach()
        next.minutes(2)
        // the story is written with the world, so it shows after a second restart too
        val blob = WorldBlobJson.decode(next.page.stored!!.blob)
        assertEquals(story.lines, blob.away!!.lines)
    }

    @Test
    fun theAppStoppingFlushesEvenWithNothingVisible() {
        val rig = WorldRig(cats = 1).started()
        rig.minutes(1)
        val rev = rig.page.stored!!.rev
        rig.port.visible = false
        rig.brain.report(
            MomentReport(
                OpIds.firstMoment("c", "x"),
                rig.page.living()[0].id,
                "first_belly",
                NOW,
                null,
            )
        )
        rig.frame()
        rig.frame()
        assertEquals(
            "nothing goes out while no screen can hear the answer",
            rev,
            rig.page.stored!!.rev,
        )
        rig.host.onStop()
        assertTrue(rig.page.stored!!.rev > rev)
    }

    @Test
    fun aKilledPageProcessNeverBlocksTheFrames() {
        val rig = WorldRig(cats = 1).started()
        rig.port.dropReplies = true // the page takes commands but its answers never come
        rig.brain.report(
            MomentReport(
                OpIds.firstMoment("c", "x"),
                rig.page.living()[0].id,
                "first_co",
                NOW,
                null,
            )
        )
        val steps = rig.brain.steps
        rig.minutes(3)
        assertTrue(rig.brain.steps > steps + 3 * 60 * 120 - 240)
        // unanswered writes are sent again after the timeout, with the same operation id
        val ids = rig.port.sent.filter { it.first == "world" }.size
        assertTrue("sent $ids world commands in 3 minutes", ids in 1..8)
    }
}
