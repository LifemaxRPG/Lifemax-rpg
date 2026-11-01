package com.lifemax.rpg.feature.library.media.integration

import com.lifemax.rpg.feature.library.media.data.DefaultMediaRepository
import com.lifemax.rpg.feature.library.media.data.FileAudioBinaryStore
import com.lifemax.rpg.feature.library.media.data.LegacySaveMediaAdapter
import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.AuthorityMode
import com.lifemax.rpg.feature.library.media.domain.MediaClock
import com.lifemax.rpg.feature.library.media.domain.MediaLibraryState
import java.util.UUID
import org.json.JSONObject

/**
 * Coordinates staged migration, shadow verification, audio binary migration, atomic ownership
 * activation, and rollback between the legacy web save (`lifemax_rpg_prototype_v1` + IndexedDB)
 * and the native Media engine (CONTRACT A10, A20).
 *
 * By default, the store remains in [AuthorityMode.WEB_OWNED] until [activateNativeOwnership]
 * succeeds after full verification.
 */
class MediaCutoverCoordinator(
    private val repository: DefaultMediaRepository,
    private val audioBinaryStore: FileAudioBinaryStore,
    private val clock: MediaClock = MediaClock { System.currentTimeMillis() },
) {
    data class VerificationReport(
        val passed: Boolean,
        val expectedItemCount: Int,
        val actualItemCount: Int,
        val missingAudioItemIds: List<String>,
        val mismatchedItemIds: List<String>,
        val detail: String,
    )

    /**
     * Step 1: Imports `mediaItems` and `mediaSeededV1` from `lifemax_rpg_prototype_v1` into
     * [AuthorityMode.SHADOW_VERIFYING] without activating native authority.
     */
    suspend fun stageMetadataFromLegacySave(
        rawSaveJson: JSONObject,
        applySeedIfUnseeded: Boolean = true,
    ): Result<MediaLibr
    // NEEDS (not supplied): the supplied file stops here, mid-declaration. activateNativeOwnership and
    // rollbackToWebOwnership must be written before anything here is wired up; see FIXES.md for what they must do.aryState> = runCatching {
        val newEpoch = "media-epoch-${clock.nowEpochMs()}-${UUID.randomUUID().toString().take(8)}"
        val stagedSnapshot =
            LegacySaveMediaAdapter.importFromLegacySaveJson(
                rawSaveJson = rawSaveJson,
                newSaveEpoch = newEpoch,
                targetMode = AuthorityMode.SHADOW_VERIFYING,
                applySeedIfUnseeded = applySeedIfUnseeded,
            )
        repository.replaceSnapshotForCutover(stagedSnapshot).getOrThrow()
    }

    /**
     * Step 2: Streams an audio blob from IndexedDB into native storage for [mediaId] during
     * [AuthorityMode.SHADOW_VERIFYING].
     */
    fun beginAudioMigrationTransfer(
        mediaId: String,
        descriptor: AudioAttachmentDescriptor,
    ): Result<String> = audioBinaryStore.beginChunkedTransfer(mediaId, descriptor)

    fun writeAudioMigrationChunk(
        stagingId: String,
        chunk: ByteArray,
        offset: Int = 0,
        length: Int = chunk.size - offset,
    ): Result<Long> = audioBinaryStore.writeChunk(stagingId, chunk, offset, length)

    suspend fun completeAudioMigrationTransfer(stagingId: String): Result<MediaLibraryState> = runCatching {
        val staged = audioBinaryStore.finishChunkedTransfer(stagingId).getOrThrow()
        val snap = repository.currentSnapshotForCutover()
        val idx = snap.items.indexOfFirst { it.id == staged.mediaId }
        check(idx >= 0) { "Media item ${staged.mediaId} not found in staged snapshot" }
        val existing = snap.items[idx]
        val audio = existing.audio ?: error("Item ${staged.mediaId} has no audio metadata")
        val nextRev = snap.authorityRevision + 1L
        val targetFileName = "${FileAudioBinaryStore.sanitizeId(staged.mediaId)}_r$nextRev.audio"
        audioBinaryStore.promoteStagedFile(staged, targetFileName).getOrElse {
            audioBinaryStore.discardStagedFile(staged)
            throw it
        }
        val updatedAudio =
            audio.copy(
                sizeBytes = staged.actualSizeBytes,
                localFileName = targetFileName,
                sha256 = staged.sha256,
                updatedAtEpochMs = clock.nowEpochMs(),
            )
        val nextItems = snap.items.toMutableList().also { it[idx] = existing.copy(audio = updatedAudio) }
        val nextSnap = snap.copy(authorityRevision = nextRev, items = nextItems)
        repository.replaceSnapshotForCutover(nextSnap).getOrElse {
            audioBinaryStore.deleteCommittedFile(targetFileName) // not recorded, so not kept
            throw it
        }
    }

    /**
     * Step 3: Verifies that all metadata and attached audio binaries in the staged native store
     * match [referenceLegacySaveJson] and exist on disk.
     */
    suspend fun verifyStagedMigration(
        referenceLegacySaveJson: JSONObject,
        applySeedIfUnseeded: Boolean = true,
    ): VerificationReport {
        val expected =
            LegacySaveMediaAdapter.importFromLegacySaveJson(
                rawSaveJson = referenceLegacySaveJson,
                newSaveEpoch = "verify-ref",
                targetMode = AuthorityMode.SHADOW_VERIFYING,
                applySeedIfUnseeded = applySeedIfUnseeded,
            )
        val actual = repository.currentSnapshotForCutover()
        val actualById = actual.items.associateBy { it.id }
        val mismatched = mutableListOf<String>()
        val missingAudio = mutableListOf<String>()

        for (expItem in expected.items) {
            val actItem = actualById[expItem.id]
            if (actItem == null ||
                actItem.title != expItem.title ||
                actItem.type != expItem.type ||
                actItem.status != expItem.status ||
                actItem.creator != expItem.creator ||
                actItem.notes != expItem.notes ||
                actItem.link != expItem.link ||
                actItem.finishedAtIso != expItem.finishedAtIso ||
                (expItem.audio == null) != (actItem.audio == null)
            ) {
                mismatched += expItem.id
                continue
            }
            val expAudio = expItem.audio
            if (expAudio != null) {
                val actAudio = actItem.audio
                // against what the old save says the file is, not what the copy recorded about itself: a copy cut short, or
                // one of the wrong blob, must not pass
                val fileOk =
                    actAudio != null &&
                        !actAudio.localFileName.isNullOrBlank() &&
                        (expAudio.sizeBytes <= 0L || actAudio.sizeBytes == expAudio.sizeBytes) &&
                        actAudio.positionSeconds == expAudio.positionSeconds &&
                        actAudio.durationSeconds == expAudio.durationSeconds &&
                        audioBinaryStore.verifyCommittedFile(actAudio.localFileName, actAudio.sizeBytes)
                if (!fileOk) {
                    missingAudio += expItem.id
                }
            }
        }

        val countMatches = expected.items.size == actual.items.size
        val passed = countMatches && mismatched.isEmpty() && missingAudio.isEmpty()
        return VerificationReport(
            passed = passed,
            expectedItemCount = expected.items.size,
            actualItemCount = actual.items.size,
            missingAudioItemIds = missingAudio,
            mismatchedItemIds = mismatched,
            detail = if (passed) "Verified ${actual.items.size} items" else "Verification failed",
        )
    }

    /**
     * Step 4: Atomically activates [AuthorityMode.NATIVE_OWNED] only if [verifyStagedMigration] passes.
     */
    suspend fun activateNativeOwnership(
        referenceLegacySaveJson: JSONObject,
        applySeedIfUnseeded: Boolean = true,
    ): Result<MediaLibr
    // NEEDS (not supplied): the supplied file stops here, mid-declaration. activateNativeOwnership and
    // rollbackToWebOwnership must be written before anything here is wired up; see FIXES.md for what they must do.
