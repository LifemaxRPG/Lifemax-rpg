package com.lifemax.rpg.app.shadow

import com.lifemax.rpg.core.shadow.ShadowLedger
import com.lifemax.rpg.core.shadow.ShadowLedgerJson
import com.lifemax.rpg.core.shadow.ShadowLimits
import java.io.File
import java.io.IOException
import java.security.SecureRandom

/**
 * The only file the economy shadow writes (ECONOMY_SHADOW_PLAN 3.1):
 * `<noBackupFilesDir>/economy-shadow/ring.json`, outside device backups and the backup folder
 * (diagnostics, not gameplay data). Written whole, atomically (a temporary file, then a rename),
 * never past [ShadowLimits.RING_MAX_BYTES]. The mirror of the save is never here.
 *
 * @param root the no-backup folder (`Context.getNoBackupFilesDir()`); a test passes a temp folder.
 */
class ShadowRing(root: File) {
    private val dir = File(root, FOLDER)

    /** The ring file. */
    val file = File(dir, NAME)

    /** The ledger stored here, or null when there is none or it does not read. */
    fun load(): ShadowLedger? =
        try {
            if (file.isFile && file.length() <= ShadowLimits.RING_MAX_BYTES)
                ShadowLedgerJson.read(file.readText(Charsets.UTF_8))
            else null
        } catch (_: IOException) {
            null
        }

    /** A new ledger with a fresh per-install salt. */
    fun fresh(now: Long): ShadowLedger =
        ShadowLedger(now, ByteArray(ShadowLimits.SALT_BYTES).also { SecureRandom().nextBytes(it) })

    /**
     * Stores [text] in place of the ring, atomically; false when it could not (no retry: the caller
     * counts it and the next change writes again).
     */
    fun store(text: String): Boolean {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size > ShadowLimits.RING_MAX_BYTES) return false
        val temp = File(dir, "$NAME.tmp")
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                temp.delete()
                false
            } else true
        } catch (_: IOException) {
            temp.delete()
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        const val FOLDER = "economy-shadow"
        const val NAME = "ring.json"
    }
}
