package com.lifemax.rpg.feature.library.media.data

import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaValidationException
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONObject

/** Where a loaded snapshot came from. Only a snapshot read from a file the store wrote may be trusted to name every audio file. */
enum class SnapshotSource {
    /** the live file, checksum verified */
    LIVE,
    /** the live file was unreadable; the backup was verified and restored over it */
    BACKUP,
    /** no store file has ever been written (first run): an empty store */
    FIRST_RUN,
}

data class LoadedSnapshot(val snapshot: MediaStoreSnapshot, val source: SnapshotSource)

/** Persistence boundary for [MediaStoreSnapshot]. */
interface MediaPersistenceStore {
    suspend fun load(): Result<LoadedSnapshot>
    suspend fun commit(snapshot: MediaStoreSnapshot): Result<Unit>
}

/**
 * Crash-safe atomic file store for [MediaStoreSnapshot].
 *
 * The file is an envelope `{"sha256": "<hex>", "payload": "<the snapshot's JSON as text>"}`. The payload is stored as text and
 * hashed exactly as written, so the check never depends on how a JSON library re-serialises an object (key order, number
 * formatting).
 *
 * Write: serialise; write `.tmp` and fsync; read `.tmp` back and verify; copy the live file to `.bak`; move `.tmp` over the live
 * file (atomically where the file system allows).
 *
 * Read: the live file if it verifies. If it exists but does not verify, it is kept as `<name>_unreadable_<ms>` and the backup is
 * tried; a verified backup is restored over the live file. If neither verifies, the load FAILS: an unreadable store is never
 * replaced by an empty one, because an empty store would then be saved over it and its audio files cleaned away. Only when no
 * store file exists at all (first run) is the result an empty store.
 */
class AtomicJsonFileMediaStore(
    private val directory: File,
    private val fileName: String = "lifemax_media_store_v1.json",
) : MediaPersistenceStore {
    private val liveFile = File(directory, fileName)
    private val tempFile = File(directory, "$fileName.tmp")
    private val backupFile = File(directory, "$fileName.bak")

    /** Tests only: the next commits fail as a full or read-only disk would. */
    internal var failWrites: Boolean = false

    override suspend fun load(): Result<LoadedSnapshot> = runCatching {
        if (!directory.exists()) directory.mkdirs()
        if (tempFile.exists()) tempFile.delete() // an interrupted write; the live file is still the last good one

        val liveError: Throwable? =
            if (liveFile.exists()) {
                val primary = runCatching { readVerified(liveFile) }
                if (primary.isSuccess) return@runCatching LoadedSnapshot(primary.getOrThrow(), SnapshotSource.LIVE)
                // keep the unreadable file for diagnosis and recovery (ADR 0008)
                runCatching {
                    Files.copy(liveFile.toPath(), File(directory, "${fileName}_unreadable_${System.currentTimeMillis()}").toPath())
                }
                primary.exceptionOrNull()
            } else null

        if (backupFile.exists()) {
            val recovered = runCatching { readVerified(backupFile) }
            if (recovered.isSuccess) {
                Files.copy(backupFile.toPath(), liveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                return@runCatching LoadedSnapshot(recovered.getOrThrow(), SnapshotSource.BACKUP)
            }
            throw IllegalStateException("The media store and its backup are both unreadable", recovered.exceptionOrNull())
        }
        if (liveError != null) throw IllegalStateException("The media store is unreadable and there is no backup", liveError)

        LoadedSnapshot(MediaStoreSnapshot.empty(), SnapshotSource.FIRST_RUN)
    }

    override suspend fun commit(snapshot: MediaStoreSnapshot): Result<Unit> = runCatching {
        if (failWrites) throw MediaValidationException(MediaError.StorageWriteFailed("Simulated write failure"))
        if (!directory.exists() && !directory.mkdirs()) {
            throw MediaValidationException(MediaError.StorageWriteFailed("Cannot create storage directory"))
        }
        val payloadText = snapshot.toJson().toString()
        val envelope = JSONObject().put("sha256", sha256(payloadText)).put("payload", payloadText).toString()

        FileOutputStream(tempFile).use { fos ->
            fos.write(envelope.toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }

        val verified = readVerified(tempFile)
        check(verified.authorityRevision == snapshot.authorityRevision) { "Read-after-write revision mismatch" }

        if (liveFile.exists()) Files.copy(liveFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        try {
            Files.move(tempFile.toPath(), liveFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), liveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readVerified(file: File): MediaStoreSnapshot {
        val outer = JSONObject(file.readText(StandardCharsets.UTF_8))
        val payloadText = outer.requireString("payload")
        check(outer.requireString("sha256") == sha256(payloadText)) { "Checksum mismatch in ${file.name}" }
        return MediaStoreSnapshot.fromJson(JSONObject(payloadText))
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
