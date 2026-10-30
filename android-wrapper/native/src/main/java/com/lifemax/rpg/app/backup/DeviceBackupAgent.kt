package com.lifemax.rpg.app.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Android's backup agent for LifeMax, declared in the manifest by patch_manifest.py. While the
 * owner allows device backup it runs Android's standard Auto Backup, with the exclusions in
 * data_extraction_rules.xml (Android 12 and newer) and backup_rules.xml. Otherwise it backs up one
 * small marker file, so Android's earlier copy of LifeMax is replaced by an empty one whatever the
 * backup service does with a backup of nothing (it may reject it and keep the old copy). Every
 * request is recorded for Settings. The app uses Auto Backup only (android:fullBackupOnly), never
 * the key-value API.
 */
class DeviceBackupAgent : BackupAgent() {
    override fun onFullBackup(data: FullBackupDataOutput) {
        val allowed = DeviceBackupChoice.allowed(this)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) data.transportFlags else 0
        DeviceBackupChoice.recordRequest(this, flags, allowed)
        val marker = File(filesDir, OFF_MARKER)
        if (allowed) {
            marker.delete()
            super.onFullBackup(data)
        } else {
            if (!marker.exists()) marker.writeText("Device backup is off in LifeMax Settings.\n")
            fullBackupFile(marker, data)
        }
    }

    override fun onQuotaExceeded(backupDataBytes: Long, quotaBytes: Long) =
        DeviceBackupChoice.recordTooBig(this, backupDataBytes, quotaBytes)

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

    private companion object {
        const val OFF_MARKER = "device-backup-off.txt"
    }
}
