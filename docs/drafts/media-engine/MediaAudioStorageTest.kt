package com.lifemax.rpg.feature.library.media

import com.lifemax.rpg.feature.library.media.data.AtomicJsonFileMediaStore
import com.lifemax.rpg.feature.library.media.data.DefaultMediaRepository
import com.lifemax.rpg.feature.library.media.data.FileAudioBinaryStore
import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.MediaCommandResult
import com.lifemax.rpg.feature.library.media.domain.MediaDomainRules
import com.lifemax.rpg.feature.library.media.domain.MediaDraft
import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaOperationContext
import com.lifemax.rpg.feature.library.media.domain.MediaStatus
import com.lifemax.rpg.feature.library.media.domain.MediaType
import java.io.ByteArrayInputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAudioStorageTest {

    @Test
    fun attachReplaceProgressEndAndMissingFileReconciliation() = runBlocking {
        val tempDir = Files.createTempDirectory("lifemax_media_audio_test").toFile()
        try {
            val audioDir = tempDir.resolve("audio")
            val store = AtomicJsonFileMediaStore(tempDir)
            val audioStore = FileAudioBinaryStore(audioDir)
            val repo =
                DefaultMediaRepository(
                    persistenceStore = store,
                    audioBinaryStore = audioStore,
                    clock = { 1_793_400_000_000L },
                    idGenerator = { "media_1" },
                )
            val epoch = repo.initialize().getOrThrow().saveEpoch
            var seq = 0L
            fun ctx() = MediaOperationContext("op-${++seq}", "c1", seq, epoch)

            // 1. Create a Book item
            repo.upsert(ctx(), MediaDraft(title = "Can't Hurt Me", type = "Book", status = "unread"))

            // 2. Attach audio -> upgrades to Audiobook + Progress
            val bytes1 = ByteArray(4096) { (it % 251).toByte() }
            val desc1 = AudioAttachmentDescriptor("cant_hurt_me_v1.m4b", "audio/mp4", bytes1.size.toLong())
            val attach1 =
                repo.attachAudio(ctx(), "media_1", desc1, ByteArrayInputStream(bytes1))
                    as MediaCommandResult.Success
            val item1 = attach1.state.items.single()
            assertEquals(MediaType.AUDIOBOOK, item1.type)
            assertEquals(MediaStatus.PROGRESS, item1.status)
            assertNotNull(item1.audio?.localFileName)

            // 3. Update playback progress
            repo.updatePlaybackProgress(ctx(), "media_1", positionSeconds = 420.0, durationSeconds = 3600.0)

            // 4. Replace audio -> preserves position & duration, deletes old file after commit
            val oldFile = repo.state.value.items.single().audio!!.localFileName!!
            val bytes2 = ByteArray(8192) { ((it * 7) % 253).toByte() }
            val desc2 = AudioAttachmentDescriptor("cant_hurt_me_v2.m4b", "audio/mp4", bytes2.size.toLong())
            val attach2 =
                repo.attachAudio(ctx(), "media_1", desc2, ByteArrayInputStream(bytes2))
                    as MediaCommandResult.Success
            val item2 = attach2.state.items.single()
            assertEquals("cant_hurt_me_v2.m4b", item2.audio!!.name)
            assertEquals(420.0, item2.audio!!.positionSeconds, 0.001)
            assertEquals(3600.0, item2.audio!!.durationSeconds, 0.001)
            assertTrue(!audioDir.resolve(oldFile).exists())
            assertTrue(audioDir.resolve(item2.audio!!.localFileName!!).exists())

            // 5. Mark audio ended -> sets FINISHED and finishedAtIso
            val ended =
                repo.markAudioEnded(ctx(), "media_1", 3600.0, 3600.0) as MediaCommandResult.Success
            val item3 = ended.state.items.single()
            assertEquals(MediaStatus.FINISHED, item3.status)
            assertNotNull(item3.finishedAtIso)

            // 6. Simulate missing file on disk -> verifyAndPreparePlayback clears audio and saves
            audioDir.resolve(item3.audio!!.localFileName!!).delete()
            val missing =
                repo.verifyAndPreparePlayback(ctx(), "media_1") as MediaCommandResult.Failure
            assertEquals(MediaError.AudioFileMissing, missing.error)
            assertNull(repo.state.value.items.single().audio)

            // 7. Reject > 512 MB descriptor
            val tooBigDesc =
                AudioAttachmentDescriptor(
                    "huge.m4b",
                    "audio/mp4",
                    MediaDomainRules.MAX_AUDIO_BYTES + 1L,
                )
            val tooBig =
                repo.attachAudio(ctx(), "media_1", tooBigDesc, ByteArrayInputStream(ByteArray(8)))
                    as MediaCommandResult.Failure
            assertTrue(tooBig.error is MediaError.AudioFileTooLarge)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
