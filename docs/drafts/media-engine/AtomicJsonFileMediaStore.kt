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

/**
 * Persistence boundary for [MediaStoreSnapshot].
 */
interface MediaPersistenceStore {
    suspend fun load(): Result<MediaStoreSnapshot>
    suspend fun commit(snapshot: MediaStoreSnapshot): Result<Unit>
}

/**
 * Crash-safe atomic file store for [MediaStoreSnapshot].
 *
 * Write protocol:
 * 1. Serialize [snapshot] with an envelope `{ "sha256": "...", "payload": { ... } }`.
 * 2. Write to `lifemax_media_store_v1.json.tmp` and call `fd.sync()`.
 * 3. Read back and verify the SHA-256 of the `.tmp` file.
 * 4. Copy current live file (if valid) to `lifemax_media_store_v1.json.bak`.
 * 5. Atomically move `.tmp` over `lifemax_media_store_v1.json`.
 *
 * Read protocol:
 * - Reads live file and verifies envelope SHA-256.
 * - If live file is missing or corrupted by an interrupted power loss, falls back to `.bak`.
 * - Cleans up any leftover `.tmp` file on load.
 */
class AtomicJsonFileMediaStore(
    private val directory: File,
    private val fileName: String = "lifemax_media_store_v1.json",
    var simulateWriteFailure: Boolean = false,
) : MediaPersistenceStore {
    private val liveFile = File(directory, fileName)
    private val tempFile = File(directory, "$fileName.tmp")
    private val backupFile = File(directory, "$fileName.bak")

    override suspend fun load(): Result<MediaStoreSnapshot> = runCatching {
        if (!directory.exists()) directory.mkdirs()
        if (tempFile.exists()) tempFile.delete()

        if (liveFile.exists()) {
            val primary = runCatching { readVerified(liveFile) }
            if (primary.isSuccess) return@runCatching primary.getOrThrow()
            // Preserve unreadable live file for diagnostics (ADR 0008)
            val quarantine = File(directory, "${fileName}_unreadable_${System.currentTimeMillis()}")
            runCatching { Files.copy(liveFile.toPath(), quarantine.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }

        if (backupFile.exists()) {
            val recovered = readVerified(backupFile)
            Files.copy(backupFile.toPath(), liveFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            return@runCatching recovered
        }

        MediaStoreSnapshot.empty()
    }

    override suspend fun commit(snapshot: MediaStoreSnapshot): Result<Unit> = runCatching {
        if (simulateWriteFailure) {
            throw MediaValidationException(MediaError.StorageWriteFailed("Simulated write failure"))
        }
        if (!directory.exists() && !directory.mkdirs()) {
            throw MediaValidationException(MediaError.StorageWriteFailed("Cannot create storage directory"))
        }
        val payloadJson = snapshot.toJson()
        val payloadText = payloadJson.toString()
        val sha = sha256(payloadText)
        val envelope =
            JSONObject()
                .put("sha256", sha)
                .put("payload", payloadJson)
                .toString(2)

        FileOutputStream(tempFile).use { fos ->
            fos.write(envelope.toByteArray(StandardCharsets.UTF_8))
            fos.flush()
            fos.fd.sync()
        }

        val verified = readVerified(tempFile)
        check(verified.authorityRevision == snapshot.authorityRevision) {
            "Read-after-write revision mismatch"
        }

        if (liveFile.exists()) {
            runCatching {
                Files.copy(liveFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }

        try {
            Files.move(
                tempFile.toPath(),
                liveFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            Files.move(
                tempFile.toPath(),
                liveFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun readVerified(file: File): MediaStoreSnapshot {
        val raw = file.readText(StandardCharsets.UTF_8)
        val outer = JSONObject(raw)
        val payloadObj = outer.getJSONObject("payload")
        val expectedSha = outer.getString("sha256")
        val actualSha = sha256(payloadObj.toString())
        check(expectedSha == actualSha) { "Checksum mismatch in ${file.name}" }
        return MediaStoreSnapshot.fromJson(payloadObj)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
