package com.lifemax.rpg.feature.library.media

import com.lifemax.rpg.feature.library.media.data.AtomicJsonFileMediaStore
import com.lifemax.rpg.feature.library.media.data.DefaultMediaRepository
import com.lifemax.rpg.feature.library.media.data.FileAudioBinaryStore
import com.lifemax.rpg.feature.library.media.domain.MediaCommandResult
import com.lifemax.rpg.feature.library.media.domain.MediaDraft
import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaOperationContext
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryTransactionTest {

    @Test
    fun idempotentRetryConflictHighWaterAndEpochEnforcement() = runBlocking {
        val tempDir = Files.createTempDirectory("lifemax_media_tx_test").toFile()
        try {
            val store = AtomicJsonFileMediaStore(tempDir)
            val audioStore = FileAudioBinaryStore(tempDir.resolve("audio"))
            var clockMs = 1_793_400_000_000L
            var idSeq = 0
            val repo =
                DefaultMediaRepository(
                    persistenceStore = store,
                    audioBinaryStore = audioStore,
                    clock = { clockMs },
                    idGenerator = { now -> "media_${now}_${++idSeq}" },
                )
            val init = repo.initialize().getOrThrow()
            val epoch = init.saveEpoch

            // 1. First upsert succeeds
            val ctx1 = MediaOperationContext("op-1", "client-A", 10L, epoch)
            val draft1 = MediaDraft(title = "The Pragmatic Programmer", type = "Book", creator = "Hunt & Thomas")
            val res1 = repo.upsert(ctx1, draft1) as MediaCommandResult.Success
            assertFalse(res1.deduplicated)
            assertEquals(1, res1.state.items.size)
            assertEquals(1L, res1.state.authorityRevision)

            // 2. Identical retry with same operationId is deduplicated without advancing revision
            val retry1 = repo.upsert(ctx1, draft1) as MediaCommandResult.Success
            assertTrue(retry1.deduplicated)
            assertEquals(1, retry1.state.items.size)
            assertEquals(1L, retry1.state.authorityRevision)
            assertEquals(res1.receipt, retry1.receipt)

            // 3. Same operationId with different payload is rejected as OperationConflict
            val conflict =
                repo.upsert(ctx1, draft1.copy(title = "Different Title")) as MediaCommandResult.Failure
            assertTrue(conflict.error is MediaError.OperationConflict)

            // 4. Stale/evicted sequence (sequence <= highWaterMark 10 with unknown opId) is rejected
            val staleCtx = MediaOperationContext("op-evicted", "client-A", 9L, epoch)
            val staleRes = repo.upsert(staleCtx, MediaDraft(title = "Old Request")) as MediaCommandResult.Failure
            assertTrue(staleRes.error is MediaError.StaleOrEvictedOperation)

            // 5. Wrong saveEpoch is rejected
            val badEpochCtx = MediaOperationContext("op-2", "client-A", 11L, "wrong-epoch")
            val badEpochRes = repo.upsert(badEpochCtx, MediaDraft(title = "Valid")) as MediaCommandResult.Failure
            assertTrue(badEpochRes.error is MediaError.InvalidEpoch)

            // 6. Failed storage write leaves in-memory state and revision untouched
            store.simulateWriteFailure = true
            val failCtx = MediaOperationContext("op-3", "client-A", 11L, epoch)
            val failRes = repo.upsert(failCtx, MediaDraft(title = "Should Not Persist")) as MediaCommandResult.Failure
            assertTrue(failRes.error is MediaError.StorageWriteFailed)
            assertEquals(1, repo.state.value.items.size)
            assertEquals(1L, repo.state.value.authorityRevision)

            // 7. Re-enable storage and retry op-3 succeeds cleanly
            store.simulateWriteFailure = false
            val recoveredRes = repo.upsert(failCtx, MediaDraft(title = "Should Not Persist")) as MediaCommandResult.Success
            assertEquals(2, recoveredRes.state.items.size)
            assertEquals(2L, recoveredRes.state.authorityRevision)
            assertNotNull(repo.getReceipt("op-3"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
