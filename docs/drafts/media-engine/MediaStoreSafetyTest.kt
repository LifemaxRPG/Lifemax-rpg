package com.lifemax.rpg.feature.library.media

import com.lifemax.rpg.feature.library.media.data.AtomicJsonFileMediaStore
import com.lifemax.rpg.feature.library.media.data.DefaultMediaRepository
import com.lifemax.rpg.feature.library.media.data.FileAudioBinaryStore
import com.lifemax.rpg.feature.library.media.data.SnapshotSource
import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.MediaCommandResult
import com.lifemax.rpg.feature.library.media.domain.MediaDraft
import com.lifemax.rpg.feature.library.media.domain.MediaOperationContext
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The fixes for the review's findings: an unreadable store never becomes an empty one, audio is never deleted for being
 *  unnamed, a progress tick writes no receipt, and a playback check that finds its file writes nothing. */
class MediaStoreSafetyTest {
    private fun tempDir(): File = Files.createTempDirectory("lifemax_media_safety").toFile()

    private suspend fun libraryWithAudio(dir: File): Pair<DefaultMediaRepository, String> {
        val repo = DefaultMediaRepository(AtomicJsonFileMediaStore(dir), FileAudioBinaryStore(dir.resolve("audio")), clock = { 1_793_400_000_000L }, idGenerator = { "media_1" })
        val epoch = repo.initialize().getOrThrow().saveEpoch
        repo.upsert(MediaOperationContext("op-1", "c1", 1L, epoch), MediaDraft(title = "Can't Hurt Me", type = "Book"))
        val bytes = ByteArray(4096) { 7 }
        val attached = repo.attachAudio(MediaOperationContext("op-2", "c1", 2L, epoch), "media_1",
            AudioAttachmentDescriptor("a.m4b", "audio/mp4", bytes.size.toLong()), ByteArrayInputStream(bytes)) as MediaCommandResult.Success
        return repo to attached.state.items.single().audio!!.localFileName!!
    }

    @Test
    fun anUnreadableStoreWithoutBackupFailsToLoadAndItsAudioStays() = runBlocking {
        val dir = tempDir()
        try {
            val (_, audioFile) = libraryWithAudio(dir)
            File(dir, "lifemax_media_store_v1.json.bak").delete()
            File(dir, "lifemax_media_store_v1.json").writeText("{ damaged")
            val again = DefaultMediaRepository(AtomicJsonFileMediaStore(dir), FileAudioBinaryStore(dir.resolve("audio")))
            assertTrue("an unreadable store must fail, not load empty", again.initialize().isFailure)
            assertTrue("its audio must still be there", dir.resolve("audio").resolve(audioFile).exists())
            assertTrue("the unreadable file is kept", dir.listFiles()!!.any { it.name.startsWith("lifemax_media_store_v1.json_unreadable_") })
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun aDamagedFieldIsAnErrorNotADefault() = runBlocking {
        val dir = tempDir()
        try {
            libraryWithAudio(dir)
            val store = AtomicJsonFileMediaStore(dir)
            assertEquals(SnapshotSource.LIVE, store.load().getOrThrow().source)
            // rewrite the live file with a valid checksum but an unknown authority: the backup is used instead
            val live = File(dir, "lifemax_media_store_v1.json")
            val env = org.json.JSONObject(live.readText())
            val payload = env.getString("payload").replace("\"authorityMode\":\"WEB_OWNED\"", "\"authorityMode\":\"SOMEONE\"")
            val sha = java.security.MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
            live.writeText(org.json.JSONObject().put("sha256", sha).put("payload", payload).toString())
            assertEquals(SnapshotSource.BACKUP, store.load().getOrThrow().source)
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun audioNoItemNamesIsMovedAsideNotDeleted() = runBlocking {
        val dir = tempDir()
        try {
            libraryWithAudio(dir)
            val stray = dir.resolve("audio").resolve("someone_r9.audio").apply { writeBytes(ByteArray(16)) }
            DefaultMediaRepository(AtomicJsonFileMediaStore(dir), FileAudioBinaryStore(dir.resolve("audio"))).initialize().getOrThrow()
            assertTrue(!stray.exists())
            assertTrue(dir.resolve("audio").resolve(FileAudioBinaryStore.ORPHAN_DIR).resolve("someone_r9.audio").exists())
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun progressTicksAndReadyChecksWriteNoReceipts() = runBlocking {
        val dir = tempDir()
        try {
            val (repo, _) = libraryWithAudio(dir)
            val before = repo.state.value.authorityRevision
            repeat(300) { repo.recordPlaybackProgress("media_1", it.toDouble(), 3600.0).getOrThrow() }
            repo.flushPlaybackProgress().getOrThrow()
            val epoch = repo.state.value.saveEpoch
            val ready = repo.verifyAndPreparePlayback(MediaOperationContext("op-3", "c1", 3L, epoch), "media_1") as MediaCommandResult.Success
            assertTrue(ready.deduplicated)
            assertEquals(before, repo.state.value.authorityRevision)
            assertTrue("the receipts of real changes are still there", repo.getReceipt("op-2") != null)
            val reloaded = DefaultMediaRepository(AtomicJsonFileMediaStore(dir), FileAudioBinaryStore(dir.resolve("audio")))
            assertEquals(299.0, reloaded.initialize().getOrThrow().items.single().audio!!.positionSeconds, 0.001)
        } finally { dir.deleteRecursively() }
    }
}
