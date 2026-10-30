# ADR 0010: Android device backup is the owner's choice, off by default

Status: accepted by the owner ("have it off by default and just put a toggle for it in settings").

## Context

Capacitor's default manifest let Android's Auto Backup copy all app data, journal and health entries included, into the owner's Google backup and to a new phone (backlog SEC-4). `android:allowBackup` is fixed in the manifest and cannot be switched at run time. LifeMax already has its own backups to a folder the owner picks (ADR 0004).

## Decision

1. The manifest allows backup only through `DeviceBackupAgent` (`android:fullBackupOnly`; `app/backup`). While the owner allows it, the agent runs Android's standard Auto Backup. Otherwise it backs up a single marker file: an empty backup may be rejected by the backup service and leave the previous copy in place, so "off" deliberately replaces Android's copy of LifeMax with an empty one at the next backup, on any service. This was verified against the Android source (BackupAgent, FullBackupEngine, PerformFullTransportBackupTask, BackupEligibilityRules) by an independent review; Google's own transport is closed source.
2. The choice is kept on the Android side (`DeviceBackupChoice`, off by default, written with `commit()` and rolled back in memory if the write fails), because the agent can run in a process started only for the backup. Settings, Automatic backup, shows and changes it through the `DeviceBackup` plugin.
3. The rules leave out of every backup the over-the-air web bundle and its state (`files/live/`, `lifemax_live.xml`), which belong to the APK on the phone they were downloaded to, and the choice itself, so a restored phone starts with the switch off. A Google copy is made only when the phone can encrypt it end to end (`disableIfNoEncryptionCapabilities`).
4. The agent records each request (time, phone transfer or Google backup, copied or not) and a backup refused for size (`onQuotaExceeded`) in the no-backup folder; Settings shows them, and warns when the switch is off and no backup folder is set, because the phone then holds the only copy.

## Consequences

- After the update that ships this, Android's copy of LifeMax is emptied at its next backup unless the owner turns the switch on; the folder backup (ADR 0004) becomes the only copy off the phone. The owner was told.
- A copy Android made before this update can still be restored onto a fresh install until that next backup replaces it.
- Whether Samsung Smart Switch moves app data through Android's backup agent (where the switch applies) is not public; the request record in Settings shows whether Android asked.
- With the switch on, the whole app is copied, including settings and the saved Gemini API key, and a backup over Android's size limit (about 25 MB) copies nothing; Settings then says so.
