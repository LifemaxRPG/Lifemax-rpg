package com.lifemax.rpg.app.backup

import android.app.backup.BackupAgent
import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * The owner's choice whether Android may copy LifeMax into their Google backup and to a new phone,
 * off until they turn it on in Settings, and a record of what Android last asked for. The choice is
 * kept on the Android side because [DeviceBackupAgent] reads it, sometimes in a process started
 * only for the backup. Neither is ever part of a backup: the choice's file is excluded by the
 * backup rules, so a restored phone starts with the switch off, and the record lives in the
 * no-backup folder.
 */
object DeviceBackupChoice {
    // keys of the record, read by DeviceBackupPlugin
    const val ASKED_AT = "askedAt"
    const val TRANSFER = "transfer"
    const val COPIED = "copied"
    const val TOO_BIG_BYTES = "tooBigBytes"
    const val QUOTA_BYTES = "quotaBytes"

    private const val PREFS = "lifemax_device_backup"
    private const val ALLOWED = "allowed"
    private const val RECORD = "device-backup-record.json"

    fun allowed(context: Context): Boolean = prefs(context).getBoolean(ALLOWED, false)

    /**
     * Saves the choice to disk before returning, so a backup that starts right after reads it. If
     * the write fails the value in memory is put back to the one on disk, so the switch never shows
     * a choice the agent would not see.
     */
    fun set(context: Context, allowed: Boolean): Boolean {
        val prefs = prefs(context)
        val before = prefs.getBoolean(ALLOWED, false)
        if (!prefs.edit().putBoolean(ALLOWED, allowed).commit()) {
            prefs.edit().putBoolean(ALLOWED, before).commit()
            return false
        }
        if (allowed != before) update(context) { remove(TOO_BIG_BYTES) }
        return true
    }

    /** What Android last asked for and whether a backup was too large, for Settings. */
    fun record(context: Context): JSONObject =
        runCatching { JSONObject(File(context.noBackupFilesDir, RECORD).readText()) }
            .getOrDefault(JSONObject())

    fun recordRequest(context: Context, transportFlags: Int, allowed: Boolean) =
        update(context) {
            put(ASKED_AT, System.currentTimeMillis())
            put(TRANSFER, (transportFlags and BackupAgent.FLAG_DEVICE_TO_DEVICE_TRANSFER) != 0)
            put(COPIED, allowed)
        }

    fun recordTooBig(context: Context, bytes: Long, quota: Long) =
        update(context) {
            put(TOO_BIG_BYTES, bytes)
            put(QUOTA_BYTES, quota)
        }

    private fun update(context: Context, change: JSONObject.() -> Unit) {
        runCatching {
            File(context.noBackupFilesDir, RECORD)
                .writeText(record(context).apply(change).toString())
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
