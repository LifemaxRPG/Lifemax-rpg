package com.lifemax.rpg.feature.library.media.data

import com.lifemax.rpg.feature.library.media.domain.AudioAttachmentDescriptor
import com.lifemax.rpg.feature.library.media.domain.AudioBinaryStore
import com.lifemax.rpg.feature.library.media.domain.MediaDomainRules
import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaValidationException
import com.lifemax.rpg.feature.library.media.domain.StagedAudioFile
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Injectable disk space check so storage exhaustion can be deterministically unit-tested.
 */
fun interface UsableSpaceProvider {
    fun usableSpaceBytes(dir: File): Long
}

/**
 * File-system implementation of [AudioBinaryStore] and chunked IndexedDB migration receiver.
 *
 * - Enforces [MediaDomainRules.MAX_AUDIO_BYTES] (512 MB) and 16 MB free-space headroom.
 * - Streams in 64 KB buffers (never holds whole audiobooks in memory).
 * - Uses `.part` files during staging and promotes to `.audio` files only on commit.
 */
class FileAudioBinaryStore(
    private val audioDirectory: File,
    private val spaceProvider: UsableSpaceProvider = UsableSpaceProvider { it.usableSpace },
) : AudioBinaryStore {

    private class ActiveChunkTransfer(
        val stagingId: String,
        val mediaId: String,
        val descriptor: AudioAttachmentDescriptor,
        val tempFile: File,
        val outputStream: FileOutputStream,
        val digest: MessageDigest,
        var bytesWritten: Long = 0L,
    )

    private val activeTransfers = ConcurrentHashMap<String, ActiveChunkTransfer>()

    override suspend fun stageAudioStream(
        mediaId: String,
        descriptor: AudioAttachmentDescriptor,
        input: InputStream,
    ): Result<StagedAudioFile> = runCatching {
        validateDescriptorAndSpace(descriptor)
        val stagingId = UUID.randomUUID().toString()
        val tempFileName = "staging_${sanitizeId(mediaId)}_$stagingId.part"
        val tempFile = File(audioDirectory, tempFileName)
        val digest = MessageDigest.getInstance("SHA-256")
        var totalWritten = 0L
        val buffer = ByteArray(64 * 1024)

        try {
            FileOutputStream(tempFile).use { fos ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    totalWritten += read
                    if (totalWritten > MediaDomainRules.MAX_AUDIO_BYTES) {
                        throw MediaValidationException(
                            MediaError.AudioFileTooLarge(totalWritten, MediaDomainRules.MAX_AUDIO_BYTES)
                        )
                    }
                    digest.update(buffer, 0, read)
                    fos.write(buffer, 0, read)
                }
                fos.flush()
                fos.fd.sync()
            }
        } catch (t: Throwable) {
            tempFile.delete()
            throw t
        }

        if (totalWritten <= 0L) {
            tempFile.delete()
            throw MediaValidationException(MediaError.AudioFileEmpty)
        }
        if (descriptor.sizeBytes > 0L && totalWritten != descriptor.sizeBytes) {
            tempFile.delete()
            throw MediaValidationException(
                MediaError.AudioIntegrityMismatch("Expected ${descriptor.sizeBytes} bytes, got $totalWritten")
            )
        }
        val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
        if (!descriptor.expectedSha256.isNullOrBlank() &&
            !descriptor.expectedSha256.equals(actualSha, ignoreCase = true)
        ) {
            tempFile.delete()
            throw MediaValidationException(
                MediaError.AudioIntegrityMismatch("SHA-256 mismatch")
            )
        }

        StagedAudioFile(
            stagingId = stagingId,
            mediaId = mediaId,
            tempFileName = tempFileName,
            actualSizeBytes = totalWritten,
            sha256 = actualSha,
        )
    }

    /**
     * Initiates a bounded chunked transfer (for IndexedDB -> native streaming migration).
     */
    fun beginChunkedTransfer(
        mediaId: String,
        descriptor: AudioAttachmentDescriptor,
    ): Result<String> = runCatching {
        validateDescriptorAndSpace(descriptor)
        val stagingId = UUID.randomUUID().toString()
        val tempFileName = "staging_${sanitizeId(mediaId)}_$stagingId.part"
        val tempFile = File(audioDirectory, tempFileName)
        val fos = FileOutputStream(tempFile)
        val digest = MessageDigest.getInstance("SHA-256")
        activeTransfers[stagingId] =
            ActiveChunkTransfer(
                stagingId = stagingId,
                mediaId = mediaId,
                descriptor = descriptor,
                tempFile = tempFile,
                outputStream = fos,
                digest = digest,
            )
        stagingId
    }

    fun writeChunk(
        stagingId: String,
        chunk: ByteArray,
        offset: Int = 0,
        length: Int = chunk.size - offset,
    ): Result<Long> = runCatching {
        val transfer =
            activeTransfers[stagingId]
                ?: throw MediaValidationException(MediaError.AudioIntegrityMismatch("Unknown transfer $stagingId"))
        require(offset >= 0 && length >= 0 && offset + length <= chunk.size) { "Invalid chunk bounds" }
        val nextTotal = transfer.bytesWritten + length
        if (nextTotal > MediaDomainRules.MAX_AUDIO_BYTES) {
            abortChunkedTransfer(stagingId)
            throw MediaValidationException(
                MediaError.AudioFileTooLarge(nextTotal, MediaDomainRules.MAX_AUDIO_BYTES)
            )
        }
        transfer.outputStream.write(chunk, offset, length)
        transfer.digest.update(chunk, offset, length)
        transfer.bytesWritten = nextTotal
        nextTotal
    }

    fun finishChunkedTransfer(stagingId: String): Result<StagedAudioFile> = runCatching {
        val transfer =
            activeTransfers.remove(stagingId)
                ?: throw MediaValidationException(MediaError.AudioIntegrityMismatch("Unknown transfer $stagingId"))
        try {
            transfer.outputStream.flush()
            transfer.outputStream.fd.sync()
            transfer.outputStream.close()
        } catch (t: Throwable) {
            transfer.tempFile.delete()
            throw t
        }
        if (transfer.bytesWritten <= 0L) {
            transfer.tempFile.delete()
            throw MediaValidationException(MediaError.AudioFileEmpty)
        }
        if (transfer.descriptor.sizeBytes > 0L && transfer.bytesWritten != transfer.descriptor.sizeBytes) {
            transfer.tempFile.delete()
            throw MediaValidationException(
                MediaError.AudioIntegrityMismatch(
                    "Expected ${transfer.descriptor.sizeBytes} bytes, got ${transfer.bytesWritten}"
                )
            )
        }
        val actualSha = transfer.digest.digest().joinToString("") { "%02x".format(it) }
        val expectedSha = transfer.descriptor.expectedSha256
        if (!expectedSha.isNullOrBlank() && !expectedSha.equals(actualSha, ignoreCase = true)) {
            transfer.tempFile.delete()
            throw MediaValidationException(MediaError.AudioIntegrityMismatch("SHA-256 mismatch"))
        }
        StagedAudioFile(
            stagingId = stagingId,
            mediaId = transfer.mediaId,
            tempFileName = transfer.tempFile.name,
            actualSizeBytes = transfer.bytesWritten,
            sha256 = actualSha,
        )
    }

    fun abortChunkedTransfer(stagingId: String) {
        val transfer = activeTransfers.remove(stagingId) ?: return
        runCatching { transfer.outputStream.close() }
        runCatching { transfer.tempFile.delete() }
    }

    override suspend fun promoteStagedFile(
        staged: StagedAudioFile,
        targetFileName: String,
    ): Result<Unit> = runCatching {
        val source = File(audioDirectory, staged.tempFileName)
        if (!source.exists()) {
            throw MediaValidationException(MediaError.AudioIntegrityMismatch("Staged file missing"))
        }
        val target = File(audioDirectory, targetFileName)
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override suspend fun discardStagedFile(staged: StagedAudioFile) {
        File(audioDirectory, staged.tempFileName).delete()
    }

    override suspend fun verifyCommittedFile(
        localFileName: String,
        expectedSizeBytes: Long,
    ): Boolean {
        if (localFileName.isBlank()) return false
        val file = File(audioDirectory, localFileName)
        return file.exists() && file.isFile && (expectedSizeBytes <= 0L || file.length() == expectedSizeBytes)
    }

    override suspend fun deleteCommittedFile(localFileName: String) {
        if (localFileName.isBlank()) return
        File(audioDirectory, localFileName).delete()
    }

    override suspend fun cleanupOrphans(activeFileNames: Set<String>): Int {
        if (!audioDirectory.exists()) return 0
        var removed = 0
        val children = audioDirectory.listFiles().orEmpty()
        for (file in children) {
            if (!file.isFile) continue
            val isStalePart = file.name.endsWith(".part") && activeTransfers.values.none { it.tempFile.name == file.name }
            val isOrphanAudio = file.name.endsWith(".audio") && file.name !in activeFileNames
            if (isStalePart || isOrphanAudio) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    private fun validateDescriptorAndSpace(descriptor: AudioAttachmentDescriptor) {
        if (!audioDirectory.exists() && !audioDirectory.mkdirs()) {
            throw MediaValidationException(MediaError.StorageWriteFailed("Cannot create audio directory"))
        }
        if (descriptor.sizeBytes <= 0L) {
            throw MediaValidationException(MediaError.AudioFileEmpty)
        }
        if (descriptor.sizeBytes > MediaDomainRules.MAX_AUDIO_BYTES) {
            throw MediaValidationException(
                MediaError.AudioFileTooLarge(descriptor.sizeBytes, MediaDomainRules.MAX_AUDIO_BYTES)
            )
        }
        val requiredSpace = descriptor.sizeBytes + HEADROOM_BYTES
        val availableSpace = spaceProvider.usableSpaceBytes(audioDirectory)
        if (availableSpace in 1 until requiredSpace) {
            throw MediaValidationException(MediaError.InsufficientStorage(requiredSpace, availableSpace))
        }
    }

    companion object {
        private const val HEADROOM_BYTES = 16L * 1024L * 1024L // 16 MB safety margin

        fun sanitizeId(id: String): String =
            id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(64).ifEmpty { "media" }
    }
}
