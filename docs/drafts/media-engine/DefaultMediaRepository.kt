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

    /**
     * Loads the store. A store that cannot be read fails here (the screen shows Media read-only with the reason) and is never
     * replaced by an empty one. Orphaned audio is swept aside (never deleted, FileAudioBinaryStore.sweepOrphans) only when the
     * store came from the live file: a store restored from its backup may be older than the audio on disk, and a first run has
     * nothing to compare with.
     */
    override suspend fun initialize(): Result<MediaLibraryState> =
        mutex.withLock {
            val loaded = persistenceStore.load().getOrElse { return@withLock Result.failure(it) }
            currentSnapshot = loaded.snapshot
            mutableState.value = currentSnapshot.toLibraryState()
            if (loaded.source == SnapshotSource.LIVE) {
                val activeAudioFiles = currentSnapshot.items.mapNotNull { it.audio?.localFileName }.toSet()
                runCatching { audioBinaryStore.sweepOrphans(activeAudioFiles) }
            }
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

    /**
     * Copies the audio in first, outside the store's lock, so a long copy (up to 512 MB) never holds up other commands; then
     * commits under the lock. A copy whose command turns out to be a repeat, a conflict or a stale request, or whose item went
     * away meanwhile, is discarded.
     */
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
        // a cheap look first: a repeat, a conflict or a missing item needs no copy at all
        mutex.withLock {
            val snap = currentSnapshot
            val early = preflight(snap, context, "attachAudio", hash)
            if (early != null) return early
            if (snap.items.none { it.id == itemId }) {
                return MediaCommandResult.Failure(MediaError.ItemNotFound(itemId), snap.toLibraryState())
            }
        }
        val staged =
            audioBinaryStore.stageAudioStream(itemId, descriptor, inputStream).getOrElse { e ->
                val err = (e as? MediaValidationException)?.mediaError ?: MediaError.StorageWriteFailed(e.message ?: "Copy failed")
                return MediaCommandResult.Failure(err, state.value)
            }
        var promoted: String? = null
        val result =
            executeSerialized(context, "attachAudio", hash) { snap ->
                val idx = snap.items.indexOfFirst { it.id == itemId }
                if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
                val existing = snap.items[idx]
                val oldFileName = existing.audio?.localFileName
                val targetFileName = "${FileAudioBinaryStore.sanitizeId(itemId)}_r${snap.authorityRevision + 1L}.audio"
                audioBinaryStore.promoteStagedFile(staged, targetFileName).getOrThrow()
                promoted = targetFileName
                val updated =
                    MediaDomainRules.applyAudioAttachment(
                        item = existing,
                        descriptor = descriptor.copy(sizeBytes = staged.actualSizeBytes),
                        localFileName = targetFileName,
                        sha256 = staged.sha256,
                        nowEpochMs = clock.nowEpochMs(),
                        nowIso = clock.nowIsoUtc(),
                    )
                StagedMutation(
                    nextItems = snap.items.toMutableList().also { it[idx] = updated },
                    nextSeeded = snap.mediaSeededV1,
                    affectedItemId = itemId,
                    message = "Audio attached",
                    onCommitFailed = { audioBinaryStore.deleteCommittedFile(targetFileName) },
                    afterCommit = {
                        if (!oldFileName.isNullOrBlank() && oldFileName != targetFileName) audioBinaryStore.deleteCommittedFile(oldFileName)
                    },
                )
            }
        // not committed (a repeat, a conflict, a vanished item, a failed write): nothing the copy made may stay behind
        val committed = result is MediaCommandResult.Success && !result.deduplicated
        if (!committed) {
            val file = promoted
            if (file == null) audioBinaryStore.discardStagedFile(staged) else audioBinaryStore.deleteCommittedFile(file)
        }
        return result
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

    /**
     * Before playing: a file that is where the item says writes nothing and answers "Ready to play" (deduplicated = true:
     * nothing was committed). A missing file is repaired as the page does (08-runtime.js:50-55): the attachment is cleared and
     * saved, and the answer is AudioFileMissing.
     */
    override suspend fun verifyAndPreparePlayback(
        context: MediaOperationContext,
        itemId: String,
    ): MediaCommandResult {
        mutex.withLock {
            val snap = currentSnapshot
            val item = snap.items.firstOrNull { it.id == itemId }
                ?: return MediaCommandResult.Failure(MediaError.ItemNotFound(itemId), snap.toLibraryState())
            val audio = item.audio ?: return MediaCommandResult.Failure(MediaError.AudioFileMissing, snap.toLibraryState())
            val fileOk = !audio.localFileName.isNullOrBlank() && audioBinaryStore.verifyCommittedFile(audio.localFileName, audio.sizeBytes)
            if (fileOk) {
                // NEEDS (not supplied): if CommitReceipt cannot describe "nothing written", give MediaCommandResult a Ready case
                val nothingWritten =
                    CommitReceipt(context.operationId, context.clientId, context.clientSequence, "verifyAndPreparePlayback",
                        MediaCanonicalHash.sha256("verifyAndPreparePlayback", itemId), snap.authorityRevision, clock.nowEpochMs(), itemId, "Ready to play")
                return MediaCommandResult.Success(snap.toLibraryState(), nothingWritten, deduplicated = true)
            }
        }
        val hash = MediaCanonicalHash.sha256("verifyAndPreparePlayback", itemId)
        return executeSerialized(context, "verifyAndPreparePlayback", hash) { snap ->
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) throw MediaValidationException(MediaError.ItemNotFound(itemId))
            val cleared = snap.items[idx].copy(audio = null, updatedAtIso = clock.nowIsoUtc())
            StagedMutation(
                nextItems = snap.items.toMutableList().also { it[idx] = cleared },
                nextSeeded = snap.mediaSeededV1,
                affectedItemId = itemId,
                message = MediaError.AudioFileMissing.userMessage,
                postCommitError = MediaError.AudioFileMissing,
            )
        }
    }

    /**
     * Playback position: last write wins, so no receipt and no sequence check (a receipt per tick would push the receipts of
     * real changes out of the 256 kept per client). The position is published at once and written at most every
     * [PROGRESS_WRITE_MS], and always by [flushPlaybackProgress] (pause, the app going to the background, end of the file).
     * NEEDS (not supplied): MediaRepository's updatePlaybackProgress(context, ...) is replaced by these two.
     */
    suspend fun recordPlaybackProgress(itemId: String, positionSeconds: Double, durationSeconds: Double): Result<MediaLibraryState> =
        mutex.withLock {
            val snap = currentSnapshot
            val idx = snap.items.indexOfFirst { it.id == itemId }
            if (idx < 0) return@withLock Result.failure(MediaValidationException(MediaError.ItemNotFound(itemId)))
            val item = snap.items[idx]
            val audio = item.audio ?: return@withLock Result.failure(MediaValidationException(MediaError.AudioFileMissing))
            val safePos = positionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val safeDur = durationSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
            val resolvedDur = if (safeDur > 0.0) safeDur else audio.durationSeconds
            val clampedPos = if (resolvedDur > 0.0) safePos.coerceIn(0.0, resolvedDur) else safePos
            val updatedItem =
                item.copy(
                    audio = audio.copy(positionSeconds = clampedPos, durationSeconds = resolvedDur, updatedAtEpochMs = clock.nowEpochMs()),
                    updatedAtIso = clock.nowIsoUtc(),
                )
            currentSnapshot = snap.copy(items = snap.items.toMutableList().also { it[idx] = updatedItem })
            mutableState.value = currentSnapshot.toLibraryState()
            progressDirty = true
            if (clock.nowEpochMs() - lastProgressWriteMs >= PROGRESS_WRITE_MS) writeProgress()
            Result.success(mutableState.value)
        }

    /** Writes a position not yet written. A failed write keeps it pending for the next try. */
    suspend fun flushPlaybackProgress(): Result<Unit> = mutex.withLock { writeProgress() }

    private var progressDirty = false
    private var lastProgressWriteMs = 0L

    /* under the lock: the store as it is, revision unchanged (a position is not a change other clients must order against) */
    private suspend fun writeProgress(): Result<Unit> {
        if (!progressDirty) return Result.success(Unit)
        return persistenceStore.commit(currentSnapshot).onSuccess {
            progressDirty = false
            lastProgressWriteMs = clock.nowEpochMs()
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

    companion object {
        /** how often a playing position is written at most */
        const val PROGRESS_WRITE_MS = 15_000L
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
            preflight(snap, context, commandName, payloadHash)?.let { return@withLock it }
            val clientHwm = snap.clientHighWaterMarks[context.clientId] ?: 0L

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
            progressDirty = false // the snapshot just written carries any pending position
            val publishedState = candidateSnapshot.toLibraryState()
            mutableState.value = publishedState
            runCatching { staged.afterCommit?.invoke() }

            if (staged.postCommitError != null) {
                return@withLock MediaCommandResult.Failure(staged.postCommitError, publishedState)
            }
            MediaCommandResult.Success(publishedState, receipt, deduplicated = false)
        }

    /** The answer a command gets before any work: a wrong epoch, a repeat (the stored receipt), a conflict, or a stale request. */
    private fun preflight(snap: MediaStoreSnapshot, context: MediaOperationContext, commandName: String, payloadHash: String): MediaCommandResult? {
        if (context.expectedEpoch != snap.saveEpoch) {
            return MediaCommandResult.Failure(MediaError.InvalidEpoch(context.expectedEpoch, snap.saveEpoch), snap.toLibraryState())
        }
        val existing = snap.receipts.firstOrNull { it.operationId == context.operationId }
        if (existing != null) {
            return if (existing.payloadHash == payloadHash && existing.commandName == commandName) {
                MediaCommandResult.Success(state = snap.toLibraryState(), receipt = existing, deduplicated = true)
            } else {
                MediaCommandResult.Failure(MediaError.OperationConflict(context.operationId), snap.toLibraryState())
            }
        }
        val clientHwm = snap.clientHighWaterMarks[context.clientId] ?: 0L
        if (context.clientSequence <= clientHwm) {
            return MediaCommandResult.Failure(
                MediaError.StaleOrEvictedOperation(context.operationId, context.clientSequence, clientHwm),
                snap.toLibraryState(),
            )
        }
        return null
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
