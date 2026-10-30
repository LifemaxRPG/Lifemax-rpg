package com.lifemax.rpg.core.platform

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor

/**
 * The owner's choice whether Android may copy LifeMax's data into their Google backup and to a new
 * phone. Off until they turn it on in Settings. Kept on the Android side because the backup agent
 * reads it, sometimes in a process started only for the backup.
 */
object DeviceBackupChoice {
    private const val PREFS = "lifemax_device_backup"
    private const val ALLOWED = "allowed"

    fun allowed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(ALLOWED, false)

    /**
     * Saves the choice to disk before returning, so a backup that starts right after reads it;
     * false if that failed.
     */
    fun set(context: Context, allowed: Boolean): Boolean =
        context
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(ALLOWED, allowed)
            .commit()
}

/**
 * Android's backup agent for LifeMax. It runs the standard Auto Backup (with the exclusions in
 * backup_rules.xml and data_extraction_rules.xml) only while the owner allows it, and otherwise
 * hands Android nothing to copy. The app uses Auto Backup only (android:fullBackupOnly), never the
 * key-value API.
 */
class DeviceBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        if (DeviceBackupChoice.allowed(this)) super.onFullBackup(data)
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor,
    ) = Unit
}
