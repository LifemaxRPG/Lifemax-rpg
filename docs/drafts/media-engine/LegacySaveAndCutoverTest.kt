package com.lifemax.rpg.feature.library.media

import com.lifemax.rpg.feature.library.media.data.AtomicJsonFileMediaStore
import com.lifemax.rpg.feature.library.media.data.DefaultMediaRepository
import com.lifemax.rpg.feature.library.media.data.FileAudioBinaryStore
import com.lifemax.rpg.feature.library.media.data.LegacySaveMediaAdapter
import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.AuthorityMode
import com.lifemax.rpg.feature.library.media.integration.MediaCutoverCoordinator
import com.lifemax.rpg.feature.library.media.integration.MediaUiProjectionMapper
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySaveAndCutoverTest {

    private fun loadFixture(): JSONObject =
        JSONObject(
            javaClass.getResource("/fixtures/legacy-media-save.json")?.readText()
                ?: SAMPLE_LEGACY_SAVE
        )

    @Test
    fun legacySaveRoundTripPreservesUnknownFieldsAndRepairsDuplicateIds() {
        val raw = loadFixture()
        val snap =
            LegacySaveMediaAdapter.importFromLegacySaveJson(
                rawSaveJson = raw,
                newSaveEpoch = "epoch-1",
                applySeedIfUnseeded = false,
            )
        assertEquals(3, snap.items.size)
        // Duplicate ID "media_dup" repaired to "media_dup_dup2"
        assertEquals(listOf("media_1", "media_dup", "media_dup_dup2"), snap.items.map { it.id })
        assertEquals("futureTagValue", snap.items[0].unknownFields["futureCustomField"])
        assertEquals(42, snap.items[0].audio!!.unknownFields["customAudioBookmark"])

        val exported = LegacySaveMediaAdapter.exportToLegacySavePatch(snap)
        val firstExported = exported.getJSONArray("mediaItems").getJSONObject(0)
        assertEquals("futureTagValue", firstExported.getString("futureCustomField"))
        assertEquals(42, firstExported.getJSONObject("audio").getInt("customAudioBookmark"))
    }

    @Test
    fun stagedCutoverVerifiesAudioBeforeActivatingAndSupportsRollback() = runBlocking {
        val tempDir = Files.createTempDirectory("lifemax_cutover_test").toFile()
        try {
            val store = AtomicJsonFileMediaStore(tempDir)
            val audioStore = FileAudioBinaryStore(tempDir.resolve("audio"))
            val repo = DefaultMediaRepository(store, audioStore)
            repo.initialize().getOrThrow()
            val coordinator = MediaCutoverCoordinator(repo, audioStore)

            val raw = loadFixture()
            // 1. Stage metadata
            val stagedState = coordinator.stageMetadataFromLegacySave(raw, applySeedIfUnseeded = false).getOrThrow()
            assertEquals(AuthorityMode.SHADOW_VERIFYING, stagedState.authorityMode)

            // 2. Verification fails before audio binary for "media_1" is migrated
            val preAudioReport = coordinator.verifyStagedMigration(raw, applySeedIfUnseeded = false)
            assertFalse(preAudioReport.passed)
            assertEquals(listOf("media_1"), preAudioReport.missingAudioItemIds)

            // 3. Stream audio chunks for "media_1"
            val audioBytes = ByteArray(1024) { 0x5A.toByte() }
            val stagingId =
                coordinator.beginAudioMigrationTransfer(
                    mediaId = "media_1",
                    descriptor = AudioAttachmentDescriptor("habits.m4b", "audio/mp4", 1024L),
                ).getOrThrow()
            coordinator.writeAudioMigrationChunk(stagingId, audioBytes, 0, 512).getOrThrow()
            coordinator.writeAudioMigrationChunk(stagingId, audioBytes, 512, 512).getOrThrow()
            coordinator.completeAudioMigrationTransfer(stagingId).getOrThrow()

            // 4. Verification now passes and activates NATIVE_OWNED
            val activeState = coordinator.activateNativeOwnership(raw, applySeedIfUnseeded = false).getOrThrow()
            assertEquals(AuthorityMode.NATIVE_OWNED, activeState.authorityMode)

            // 5. UI projection matches Phase 1 schema
            val proj = MediaUiProjectionMapper.toProjectionJson(activeState, "sess-1", 1L)
            assertEquals(3, proj.getJSONObject("stats").getInt("total"))

            // 6. Rollback restores WEB_OWNED and returns patch
            val patch = coordinator.rollbackToWebOwnership().getOrThrow()
            assertEquals(AuthorityMode.WEB_OWNED, repo.state.value.authorityMode)
            assertEquals(3, patch.getJSONArray("mediaItems").length())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    companion object {
        const val SAMPLE_LEGACY_SAVE = """
        {
          "state": {
            "schema": 1,
            "mediaSeededV1": true,
            "mediaItems": [
              {
                "id": "media_1",
                "title": "Atomic Habits",
                "type": "Audiobook",
                "creator": "James Clear",
                "status": "progress",
                "link": "https://jamesclear.com/atomic-habits",
                "notes": "Chapter 3",
                "futureCustomField": "futureTagValue",
                "audio": {
                  "name": "habits.m4b",
                  "size": 1024,
                  "type": "audio/mp4",
                  "position": 120.5,
                  "duration": 600.0,
                  "customAudioBookmark": 42
                }
              },
              {
                "id": "media_dup",
                "title": "Deep Work",
                "type": "Book",
                "creator": "Cal Newport",
                "status": "finished",
                "link": "javascript:evil()",
                "notes": ""
              },
              {
                "id": "media_dup",
                "title": "Meditations",
                "type": "Book",
                "creator": "Marcus Aurelius",
                "status": "unread",
                "link": "",
                "notes": ""
              }
            ]
          }
        }
        """
    }
}
