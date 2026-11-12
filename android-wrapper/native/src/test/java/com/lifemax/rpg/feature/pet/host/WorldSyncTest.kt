package com.lifemax.rpg.feature.pet.host

import com.lifemax.rpg.feature.pet.world.GiftTaken
import com.lifemax.rpg.feature.pet.world.MomentReport
import com.lifemax.rpg.feature.pet.world.SaveWorld
import com.lifemax.rpg.feature.pet.world.WorldBlob
import com.lifemax.rpg.feature.pet.world.WorldBlobJson
import com.lifemax.rpg.feature.pet.world.WorldCommand
import com.lifemax.rpg.feature.pet.world.WorldContract
import com.lifemax.rpg.feature.pet.world.WorldFixtures
import com.lifemax.rpg.feature.pet.world.WorldReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The persistence state machine on its own, with scripted answers: debounce, urgency, the outbox,
 * epoch and revision, stale, size, unsaved, timeouts and the flush.
 */
class WorldSyncTest {
    private val blob = WorldFixtures.blob().copy(outbox = emptyList())
    private val sent = ArrayList<Pair<WorldCommand, String>>()
    private val sync = WorldSync().also { it.reset(epoch = 3, rev = 10) }
    private var now = 1_000_000L

    private fun pump(visible: Boolean = true, flush: Boolean = false) =
        sync.pump(now, visible, flush, { WorldTrim.apply(blob, it) }) { c, id ->
            sent += c to id
            true
        }

    private fun reply(
        ok: Boolean,
        why: String = "",
        changed: Boolean = ok,
        epoch: Long = 3,
        rev: Long = 10,
    ) = WorldReply("world", sent.last().second, ok, changed, "", why, epoch, rev)

    private fun moment(n: Int) =
        MomentReport("c1:first_glass$n", WorldFixtures.TED, "first_glass", now, null)

    @Test
    fun nothingIsSentUntilTheWorldChanges() {
        pump()
        assertTrue(sent.isEmpty())
        sync.markDirty()
        pump()
        val save = sent.single().first as SaveWorld
        assertEquals(11, save.rev)
        assertEquals(3, save.epoch)
        assertTrue(save.op.matches(WorldContract.OP_ID))
    }

    @Test
    fun writesAreDebouncedToOnePerMinute() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = true), now)
        repeat(10) {
            now += 5_000
            sync.markDirty()
            pump()
        }
        assertEquals("only the first, inside the minute", 1, sent.size)
        now += HostTuning.WRITE_EVERY_MS
        pump()
        assertEquals(2, sent.size)
        assertEquals(12L, (sent.last().first as SaveWorld).rev)
    }

    @Test
    fun aMomentGoesOutAtOnceAndItsWriteIsNotOftenerThanFiveSecondsAndAFlushGoesAtOnce() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = true), now)
        sync.enqueue(moment(1))
        now += 1_000
        pump()
        assertEquals("the command goes first, at once", "moment", sent.last().first.name)
        sync.onReply(WorldReply("moment", sent.last().second, true, true, "", ""), now)
        now += 1_000
        pump()
        assertEquals("the write is too close to the last", 2, sent.size)
        now += HostTuning.URGENT_GAP_MS
        pump()
        assertEquals(3, sent.size)
        assertTrue(
            "the outbox is empty again",
            (sent.last().first as SaveWorld).blob.outbox.isEmpty(),
        )
        sync.onReply(reply(ok = true), now)
        sync.markDirty()
        now += 100
        pump(flush = true)
        assertEquals("a flush ignores the interval", 4, sent.size)
    }

    @Test
    fun theWriteCarriesTheOutboxToo() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = true), now)
        sync.enqueue(moment(1))
        now += 1_000
        pump() // the command, which is never answered
        now += HostTuning.REPLY_TIMEOUT_MS
        pump() // timed out: the write goes now, carrying the command
        val write = sent.last().first as SaveWorld
        assertEquals(listOf("c1:first_glass1"), write.blob.outbox.map { it.op })
    }

    @Test
    fun anOutboxCommandIsSentAfterTheWriteAndLeavesWhenOk() {
        sync.enqueue(moment(1))
        now += HostTuning.URGENT_GAP_MS
        pump()
        sync.onReply(reply(ok = true), now)
        pump()
        val cmd = sent.last().first
        assertEquals("moment", cmd.name)
        sync.onReply(WorldReply("moment", sent.last().second, true, true, "", ""), now)
        assertTrue(sync.pending().isEmpty())
        pump()
        assertEquals("the outbox change is a write of its own, later", 2, sent.size)
    }

    @Test
    fun theOutboxHoldsSixteenAndDropsTheOldest() {
        repeat(20) { sync.enqueue(moment(it)) }
        assertEquals(16, sync.pending().size)
        assertEquals("c1:first_glass4", sync.pending().first().op)
        sync.enqueue(moment(19)) // the same op twice is one
        assertEquals(16, sync.pending().size)
    }

    @Test
    fun theOutboxOfAStoredWorldIsReplayedAfterAReset() {
        val entries =
            listOf(moment(1), moment(2)).map { com.lifemax.rpg.feature.pet.world.OutboxEntry(it) }
        sync.reset(3, 10, entries)
        assertEquals(2, sync.pending().size)
        pump()
        assertEquals("moment", sent.single().first.name)
        assertEquals("c1:first_glass1", sent.single().first.op)
    }

    @Test
    fun anAnswerForAnotherRequestIsNotOurs() {
        sync.markDirty()
        pump()
        assertFalse(sync.onReply(WorldReply("world", "cwh-999", true, true, "", ""), now))
        assertTrue(sync.inFlight)
    }

    @Test
    fun aStaleEpochHoldsWritingUntilReset() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = false, why = "stale", epoch = 4, rev = 10), now)
        assertTrue(sync.held)
        assertEquals(4L, sync.staleEpoch)
        now += HostTuning.WRITE_EVERY_MS * 2
        pump()
        assertEquals(1, sent.size)
        sync.reset(4, 10)
        sync.markDirty()
        pump()
        assertEquals(4L, (sent.last().first as SaveWorld).epoch)
    }

    @Test
    fun aStaleRevisionOfTheSameEpochCatchesUpAndWritesNext() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = false, why = "stale", epoch = 3, rev = 14), now)
        assertFalse(sync.held)
        pump()
        assertEquals(15L, (sent.last().first as SaveWorld).rev)
    }

    @Test
    fun aStaleReplyThatDoesNotNameANewerRevisionStillMovesOnToTheNextRevision() {
        // the reply to revision 11 was lost, the page kept 11 and then refuses a resend as stale
        // while naming a revision no newer than ours (or none): the same revision must not be sent
        // forever
        for (named in listOf(10L, 11L, null)) {
            val s = WorldSync().also { it.reset(epoch = 3, rev = 10) }
            val out = ArrayList<Pair<WorldCommand, String>>()
            val send = { c: WorldCommand, id: String ->
                out += c to id
                true
            }
            var t = 5_000_000L
            s.markDirty()
            s.pump(t, true, false, { blob }, send)
            t += HostTuning.REPLY_TIMEOUT_MS
            s.pump(t, true, false, { blob }, send) // no answer: the same revision again
            assertEquals(11L, (out.last().first as SaveWorld).rev)
            s.onReply(
                WorldReply("world", out.last().second, false, false, "", "stale", 3, named),
                t,
            )
            assertFalse(s.held)
            t += HostTuning.RETRY_MS
            s.pump(t, true, false, { blob }, send)
            val next = out.last().first as SaveWorld
            assertEquals("named=$named", 12L, next.rev)
            s.onReply(WorldReply("world", out.last().second, true, true, "", "", 3, 12), t)
            assertEquals(12L, s.rev)
            assertFalse(s.isDirty)
        }
    }

    @Test
    fun aSizeRefusalTrimsLevelByLevelThenGivesUpUntilTheNextChange() {
        val big =
            blob.copy(
                cats =
                    blob.cats.mapValues { (_, c) ->
                        c.copy(spots = (1..6).associate { "spot$it" to it })
                    }
            )
        val trimmed = (0..HostTuning.TRIM_LEVEL_LAST).map { WorldTrim.apply(big, it) }
        assertEquals(6, trimmed[0].cats.values.first().spots.size)
        assertEquals(4, trimmed[1].cats.values.first().spots.size)
        assertNull(trimmed[2].away)
        assertTrue(trimmed[3].toys.all { it.by != null })
        assertEquals(2, trimmed[4].cats.values.first().spots.size)
        sync.markDirty()
        pump()
        repeat(HostTuning.TRIM_LEVEL_LAST) { level ->
            sync.onReply(reply(ok = false, why = "size"), now)
            assertEquals(level + 1, sync.trimLevel)
            now += HostTuning.TRIM_RETRY_MS
            pump()
        }
        sync.onReply(reply(ok = false, why = "size"), now)
        assertFalse("nothing left to trim: wait for a change", sync.isDirty)
        assertEquals(HostTuning.TRIM_LEVEL_LAST + 1, sent.size)
    }

    @Test
    fun unsavedIsRetriedAfterThirtySecondsWithTheSameWorld() {
        sync.markDirty()
        pump()
        sync.onReply(reply(ok = false, why = "unsaved"), now)
        now += 10_000
        pump()
        assertEquals(1, sent.size)
        now += HostTuning.RETRY_MS
        pump()
        assertEquals(2, sent.size)
        assertEquals((sent[0].first as SaveWorld).rev, (sent[1].first as SaveWorld).rev)
        assertEquals((sent[0].first as SaveWorld).op, (sent[1].first as SaveWorld).op)
    }

    @Test
    fun noAnswerSendsTheSameOperationAgainAndALateOkIsRepeatSafe() {
        sync.markDirty()
        pump()
        val first = sent.single().first as SaveWorld
        now += HostTuning.REPLY_TIMEOUT_MS
        pump()
        val again = sent.last().first as SaveWorld
        assertEquals(first.op, again.op)
        // the page had stored it: "Already recorded." is ok but changed nothing, so the world stays
        // dirty
        sync.onReply(reply(ok = true, changed = false), now)
        assertTrue(sync.isDirty)
        assertEquals(11L, sync.rev)
    }

    @Test
    fun theNextBlockShowingTheRevisionCountsAsTheAnswerWhenItWasLost() {
        sync.markDirty()
        pump()
        sync.onBlock(storedRev = 11)
        assertFalse(sync.inFlight)
        assertFalse(sync.isDirty)
        assertEquals(11L, sync.rev)
    }

    @Test
    fun nothingIsSentWhileNoScreenCanHearTheAnswerExceptAFlush() {
        sync.markDirty()
        pump(visible = false)
        assertTrue(sent.isEmpty())
        pump(visible = false, flush = true)
        assertEquals(1, sent.size)
    }

    @Test
    fun aFlushOverAnUnansweredWriteIsTheNextRevision() {
        sync.markDirty()
        pump()
        sync.markDirty()
        pump(flush = true)
        assertEquals(listOf(11L, 12L), sent.map { (it.first as SaveWorld).rev })
    }

    @Test
    fun aRefusedCommandIsDroppedAfterFiveTriesButUnsavedIsNever() {
        val gift = GiftTaken("c1:gift:1", WorldFixtures.TED)
        sync.enqueue(gift)
        now += HostTuning.URGENT_GAP_MS
        pump()
        sync.onReply(reply(ok = true), now) // the write carrying it
        repeat(HostTuning.MAX_ATTEMPTS) {
            pump()
            sync.onReply(WorldReply("gift", sent.last().second, false, false, "", "refused"), now)
            now += HostTuning.RETRY_MS
        }
        assertTrue(sync.pending().isEmpty())
        sync.enqueue(gift)
        now += HostTuning.WRITE_EVERY_MS
        pump()
        sync.onReply(reply(ok = true), now) // the write carrying it
        repeat(HostTuning.MAX_ATTEMPTS + 2) {
            pump()
            assertEquals("gift", sent.last().first.name)
            sync.onReply(WorldReply("gift", sent.last().second, false, false, "", "unsaved"), now)
            now += HostTuning.RETRY_MS
        }
        assertEquals(1, sync.pending().size)
    }

    @Test
    fun theBlobOfAWriteDecodesStrictly() {
        sync.markDirty()
        pump()
        val b: WorldBlob = (sent.single().first as SaveWorld).blob
        WorldBlobJson.decode(WorldBlobJson.encode(b))
    }
}
