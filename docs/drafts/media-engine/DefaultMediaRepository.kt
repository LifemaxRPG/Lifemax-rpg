package com.lifemax.rpg.feature.library.media.data

import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.AudioBinaryStore
import com.lifemax.rpg.feature.library.media.domain.CommitReceipt
import com.lifemax.rpg.feature.library.media.domain.MediaClock
import com.lifemax.rpg.feature.library.media.domain.MediaCommandResult
import com.lifemax.rpg.feature.library.media.domain.MediaDomainRules
import com.lifemax.rpg.feature.library.media.domain.MediaDraft
import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaIdGenerator
import com.lifemax.rpg.feature.library.media.domain.MediaItem
import com.lifemax.rpg.feature.library.media.domain.MediaLibraryState
import com.lifemax.rpg.feature.library.media.domain.MediaOperationContext
import com.lifemax.rpg.feature.library.media.domain.MediaRepository
import com.lifemax.rpg.feature.library.media.domain.MediaStatus
import com.lifemax.rpg.feature.library.media.domain.MediaValidationException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DefaultMediaRepository(
    private val persistenceStore: MediaPersistenceStore,
    private val audioBinaryStore: AudioBinaryStore,
    private val clock: MediaClock = MediaClock { System.currentTimeMillis() },
    private val idGenerator: MediaIdGenerator = MediaIdGenerator { nowMs ->
        "media_${nowMs}_${UUID.randomUUID().toString().take(8)}"
    },
) : MediaRepository {

    private val mutex = Mutex()
    private var currentSnapshot: MediaStoreSnapshot = MediaStoreSnapshot.empty()
    private val mutableState = MutableStateFlow(currentSnapshot.toLibraryState())

    override val state: StateFlow<MediaLibraryState> = mutableState.asStateFlow()

    override suspend fun initialize(): Result<MediaLibraryState> =
        mutex.withLock {
            val loaded = persistenceStore.load()
            if (loaded.isFailure) return@withLock Result.failure(loaded.exceptionOrNull()!!)
            currentSnapshot = loaded.getOrThrow()
            mutableState.value = currentSnapshot.toLibraryState()
            val activeAudioFiles =
                currentSnapshot.items.mapNotNull { it.audio?.localFileName }.toSet()
            runCatching { audioBinaryStore.cleanupOrphans(activeAudioFiles) }
            Result.success(mutableState.value)
        }

    override suspend fun seedIfNeeded(context: MediaOperationContext): MediaCommandResult =
        executeSerialized(
            context = context,
            commandName = "seedIfNeeded",
            payloadHash = MediaCanonicalHash.sha256("seedIfNeeded"),
        ) { snap ->
            val (seededItems, seededFlag) =
                MediaDomainRules.seedDefaultReadingList(snap.items, snap.mediaSeededV1)
            StagedMutation(
                nextItems = seededItems,
                nextSeeded = seededFlag,
                affectedItemId = null,
                message = "Reading list seeded",
            )
        }

    override suspend fun upsert(
        context: MediaOperationContext,
        draft: MediaDraft,
    ): MediaCommandResult {
        val hash =
            MediaCanonicalHash.sha256(
                "upsert",
                draft.id,
                draft.title.trim(),
                draft.type.trim(),
                draft.creator.trim(),
                draft.status.trim(),
                draft.link.trim(),
                draft.notes.trim(),
            )
        return executeSerialized(context, "upsert", hash) { snap ->
            val validated = MediaDomainRules.validateAndNormalizeDraft(draft).getOrThrow()
            val nowMs = clock.nowEpochMs()
            val nowIso = clock.nowIsoUtc()
            val (nextItems, item) =
                MediaDomainRules.applyUpsert(snap.items, validated, nowIso) {
                    idGenerator.nextMediaId(nowMs)
                }
            val msg = if (validated.id != null) "Media item updated" else "Added to your library"
            StagedMutation(nextItems, snap.mediaSeededV1, item.id, msg)
        }
    }

    override suspend fun toggleFinished(
        context: MediaOperationContext,
        itemId: String,
        checked: Boolean,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("toggleFinished", itemId, checked.toString())
        return executeSerialized(context, "toggleFinished", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val updated =
                MediaDomainRules.applyToggleFinished(snap.items[idx], checked, clock.nowIsoUtc())
            val nextItems = snap.items.toMutableList().also { it[idx] = updated }
            val msg = if (checked) "Marked as finished" else "Moved back to in progress"
            StagedMutation(nextItems, snap.mediaSeededV1, itemId, msg)
        }
    }

    override suspend fun setStatus(
        context: MediaOperationContext,
        itemId: String,
        status: MediaStatus,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("setStatus", itemId, status.wireValue)
        return executeSerialized(context, "setStatus", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val updated = snap.items[idx].copy(status = status, updatedAtIso = clock.nowIsoUtc())
            val nextItems = snap.items.toMutableList().also { it[idx] = updated }
            StagedMutation(nextItems, snap.mediaSeededV1, itemId, "Status updated")
        }
    }

    override suspend fun delete(
        context: MediaOperationContext,
        itemId: String,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("delete", itemId)
        return executeSerialized(context, "delete", hash) { snap ->
            val existing =
                snap.items.firstOrNull { it.id == itemId }
                    ?: throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val nextItems = snap.items.filterNot { it.id == itemId }
            StagedMutation(
                nextItems = nextItems,
                nextSeeded = snap.mediaSeededV1,
                affectedItemId = itemId,
                message = "Removed from library",
                afterCommit = {
                    existing.audio?.localFileName?.let { audioBinaryStore.deleteCommittedFile(it) }
                },
            )
        }
    }

    override suspend fun attachAudio(
        context: MediaOperationContext,
        itemId: String,
        descriptor: AudioAttachmentDescriptor,
        inputStream: InputStream,
    ): MediaCommandResult {
        val hash =
            MediaCanonicalHash.sha256(
                "attachAudio",
                itemId,
                descriptor.fileName,
                descriptor.mimeType,
                descriptor.sizeBytes.toString(),
                descriptor.expectedSha256,
            )
        // Stage binary before holding commit lock so large streams do not block reads,
        // or inside executeSerialized if deduplication check should short-circuit first.
        return executeSerialized(context, "attachAudio", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val existing = snap.items[idx]
            val oldFileName = existing.audio?.localFileName
            val staged = audioBinaryStore.stageAudioStream(itemId, descriptor, inputStream).getOrThrow()
            val nextRev = snap.authorityRevision + 1L
            val targetFileName = "${FileAudioBinaryStore.sanitizeId(itemId)}_r$nextRev.audio"

            val promoteRes = audioBinaryStore.promoteStagedFile(staged, targetFileName)
            if (promoteRes.isFailure) {
                audioBinaryStore.discardStagedFile(staged)
                throw promoteRes.exceptionOrNull()!!
            }

            val nowMs = clock.nowEpochMs()
            val updated =
                MediaDomainRules.applyAudioAttachment(
                    item = existing,
                    descriptor = descriptor.copy(sizeBytes = staged.actualSizeBytes),
                    localFileName = targetFileName,
                    sha256 = staged.sha256,
                    nowEpochMs = nowMs,
                    nowIso = clock.nowIsoUtc(),
                )
            val nextItems = snap.items.toMutableList().also { it[idx] = updated }
            StagedMutation(
                nextItems = nextItems,
                nextSeeded = snap.mediaSeededV1,
                affectedItemId = itemId,
                message = "Audio attached",
                onCommitFailed = { audioBinaryStore.deleteCommittedFile(targetFileName) },
                afterCommit = {
                    if (!oldFileName.isNullOrBlank() && oldFileName != targetFileName) {
                        audioBinaryStore.deleteCommittedFile(oldFileName)
                    }
                },
            )
        }
    }

    override suspend fun removeAudio(
        context: MediaOperationContext,
        itemId: String,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("removeAudio", itemId)
        return executeSerialized(context, "removeAudio", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val existing = snap.items[idx]
            val oldFile = existing.audio?.localFileName
            val updated = existing.copy(audio = null, updatedAtIso = clock.nowIsoUtc())
            val nextItems = snap.items.toMutableList().also { it[idx] = updated }
            StagedMutation(
                nextItems = nextItems,
                nextSeeded = snap.mediaSeededV1,
                affectedItemId = itemId,
                message = "Audio removed",
                afterCommit = {
                    if (!oldFile.isNullOrBlank()) audioBinaryStore.deleteCommittedFile(oldFile)
                },
            )
        }
    }

    override suspend fun verifyAndPreparePlayback(
        context: MediaOperationContext,
        itemId: String,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("verifyAndPreparePlayback", itemId)
        return executeSerialized(context, "verifyAndPreparePlayback", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val item = snap.items[idx]
            val audio = item.audio ?: throw MediaValidationException(MediaError.AudioFileMissing)
            val fileOk =
                !audio.localFileName.isNullOrBlank() &&
                    audioBinaryStore.verifyCommittedFile(audio.localFileName, audio.sizeBytes)
            if (!fileOk) {
                // Reconcile missing file exactly like 08-runtime.js:50-55: clear item.audio and save
                val cleared = item.copy(audio = null, updatedAtIso = clock.nowIsoUtc())
                val nextItems = snap.items.toMutableList().also { it[idx] = cleared }
                StagedMutation(
                    nextItems = nextItems,
                    nextSeeded = snap.mediaSeededV1,
                    affectedItemId = itemId,
                    message = MediaError.AudioFileMissing.userMessage,
                    postCommitError = MediaError.AudioFileMissing,
                )
            } else {
                StagedMutation(snap.items, snap.mediaSeededV1, itemId, "Ready to play")
            }
        }
    }

    override suspend fun updatePlaybackProgress(
        context: MediaOperationContext,
        itemId: String,
        positionSeconds: Double,
        durationSeconds: Double,
    ): MediaCommandResult {
        val safePos = positionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val safeDur = durationSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val hash =
            MediaCanonicalHash.sha256(
                "updatePlaybackProgress",
                itemId,
                "%.3f".format(java.util.Locale.US, safePos),
                "%.3f".format(java.util.Locale.US, safeDur),
            )
        return executeSerialized(context, "updatePlaybackProgress", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val item = snap.items[idx]
            val audio = item.audio ?: throw MediaValidationException(MediaError.AudioFileMissing)
            val resolvedDur = if (safeDur > 0.0) safeDur else audio.durationSeconds
            val clampedPos = if (resolvedDur > 0.0) safePos.coerceIn(0.0, resolvedDur) else safePos
            val updatedAudio =
                audio.copy(
                    positionSeconds = clampedPos,
                    durationSeconds = resolvedDur,
                    updatedAtEpochMs = clock.nowEpochMs(),
                )
            val updatedItem = item.copy(audio = updatedAudio, updatedAtIso = clock.nowIsoUtc())
            val nextItems = snap.items.toMutableList().also { it[idx] = updatedItem }
            StagedMutation(nextItems, snap.mediaSeededV1, itemId, "Progress saved")
        }
    }

    override suspend fun markAudioEnded(
        context: MediaOperationContext,
        itemId: String,
        finalPositionSeconds: Double,
        durationSeconds: Double,
    ): MediaCommandResult {
        val hash = MediaCanonicalHash.sha256("markAudioEnded", itemId)
        return executeSerialized(context, "markAudioEnded", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val item = snap.items[idx]
            val nowIso = clock.nowIsoUtc()
            val resolvedDur =
                durationSeconds.takeIf { it.isFinite() && it > 0.0 }
                    ?: item.audio?.durationSeconds
                    ?: finalPositionSeconds.coerceAtLeast(0.0)
            val endPos =
                if (resolvedDur > 0.0) resolvedDur
                else finalPositionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val updatedAudio =
                item.audio?.copy(
                    positionSeconds = endPos,
                    durationSeconds = resolvedDur,
                    updatedAtEpochMs = clock.nowEpochMs(),
                )
            val updatedItem =
                item.copy(
                    status = MediaStatus.FINISHED,
                    updatedAtIso = nowIso,
                    finishedAtIso = item.finishedAtIso ?: nowIso,
                    audio = updatedAudio,
                )
            val nextItems = snap.items.toMutableList().also { it[idx] = updatedItem }
            StagedMutation(
                nextItems = nextItems,
                nextSeeded = snap.mediaSeededV1,
                affectedItemId = itemId,
                message = "Book finished · ${item.title}",
            )
        }
    }

    override suspend fun getReceipt(operationId: String): CommitReceipt? =
        mutex.withLock { currentSnapshot.receipts.firstOrNull { it.operationId == operationId } }

    suspend fun currentSnapshotForCutover(): MediaStoreSnapshot = mutex.withLock { currentSnapshot }

    suspend fun replaceSnapshotForCutover(newSnapshot: MediaStoreSnapshot): Result<MediaLibraryState> =
        mutex.withLock {
            val res = persistenceStore.commit(newSnapshot)
            if (res.isFailure) return@withLock Result.failure(res.exceptionOrNull()!!)
            currentSnapshot = newSnapshot
            mutableState.value = currentSnapshot.toLibraryState()
            Result.success(mutableState.value)
        }

    private class StagedMutation(
        val nextItems: List<MediaItem>,
        val nextSeeded: Boolean,
        val affectedItemId: String?,
        val message: String,
        val postCommitError: MediaError? = null,
        val onCommitFailed: (suspend () -> Unit)? = null,
        val afterCommit: (suspend () -> Unit)? = null,
    )

    private suspend fun executeSerialized(
        context: MediaOperationContext,
        commandName: String,
        payloadHash: String,
        block: suspend (MediaStoreSnapshot) -> StagedMutation,
    ): MediaCommandResult =
        mutex.withLock {
            val snap = currentSnapshot
            if (context.expectedEpoch != snap.saveEpoch) {
                return@withLock MediaCommandResult.Failure(
                    MediaError.InvalidEpoch(context.expectedEpoch, snap.saveEpoch),
                    snap.toLibraryState(),
                )
            }

            val existingReceipt = snap.receipts.firstOrNull { it.operationId == context.operationId }
            if (existingReceipt != null) {
                return@withLock if (existingReceipt.payloadHash == payloadHash &&
                    existingReceipt.commandName == commandName
                ) {
                    MediaCommandResult.Success(
                        state = snap.toLibraryState(),
                        receipt = existingReceipt,
                        deduplicated = true,
                    )
                } else {
                    MediaCommandResult.Failure(
                        MediaError.OperationConflict(context.operationId),
                        snap.toLibraryState(),
                    )
                }
            }

            val clientHwm = snap.clientHighWaterMarks[context.clientId] ?: 0L
            if (context.clientSequence <= clientHwm) {
                return@withLock MediaCommandResult.Failure(
                    MediaError.StaleOrEvictedOperation(
                        context.operationId,
                        context.clientSequence,
                        clientHwm,
                    ),
                    snap.toLibraryState(),
                )
            }

            val staged =
                try {
                    block(snap)
                } catch (e: MediaValidationException) {
                    return@withLock MediaCommandResult.Failure(e.mediaError, snap.toLibraryState())
                } catch (t: Throwable) {
                    return@withLock MediaCommandResult.Failure(
                        MediaError.StorageWriteFailed(t.message ?: "Unexpected error"),
                        snap.toLibraryState(),
                    )
                }

            val nextRevision = snap.authorityRevision + 1L
            val receipt =
                CommitReceipt(
                    operationId = context.operationId,
                    clientId = context.clientId,
                    clientSequence = context.clientSequence,
                    commandName = commandName,
                    payloadHash = payloadHash,
                    resultingRevision = nextRevision,
                    committedAtEpochMs = clock.nowEpochMs(),
                    affectedItemId = staged.affectedItemId,
                    message = staged.message,
                )

            val updatedHwm =
                snap.clientHighWaterMarks.toMutableMap().apply {
                    put(context.clientId, maxOf(clientHwm, context.clientSequence))
                }
            val updatedReceipts = pruneReceipts(snap.receipts + receipt)
            val candidateSnapshot =
                snap.copy(
                    authorityRevision = nextRevision,
                    mediaSeededV1 = staged.nextSeeded,
                    items = staged.nextItems,
                    clientHighWaterMarks = updatedHwm,
                    receipts = updatedReceipts,
                )

            val commitResult = persistenceStore.commit(candidateSnapshot)
            if (commitResult.isFailure) {
                runCatching { staged.onCommitFailed?.invoke() }
                val cause = commitResult.exceptionOrNull()
                val err =
                    if (cause is MediaValidationException) cause.mediaError
                    else MediaError.StorageWriteFailed(cause?.message ?: "Commit failed")
                return@withLock MediaCommandResult.Failure(err, snap.toLibraryState())
            }

            currentSnapshot = candidateSnapshot
            val publishedState = candidateSnapshot.toLibraryState()
            mutableState.value = publishedState
            runCatching { staged.afterCommit?.invoke() }

            if (staged.postCommitError != null) {
                return@withLock MediaCommandResult.Failure(staged.postCommitError, publishedState)
            }
            MediaCommandResult.Success(publishedState, receipt, deduplicated = false)
        }

    private fun pruneReceipts(all: List<CommitReceipt>): List<CommitReceipt> {
        val byClient = all.groupBy { it.clientId }
        val kept = mutableListOf<CommitReceipt>()
        for ((_, list) in byClient) {
            kept += list.takeLast(MediaStoreSnapshot.MAX_RECEIPTS_PER_CLIENT)
        }
        return kept.sortedBy { it.resultingRevision }
    }
}
