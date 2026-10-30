# ADR 0010: Android device backup is the owner's choice, off by default

Status: accepted by the owner ("have it off by default and just put a toggle for it in settings").

## Context

Capacitor's default manifest let Android's Auto Backup copy all app data, journal and health entries included, into the owner's Google backup and to a new phone (backlog SEC-4). `android:allowBackup` is fixed in the manifest and cannot be switched at run time. LifeMax already has its own backups to a folder the owner picks (ADR 0004).

## Decision

1. The manifest allows backup only through `DeviceBackupAgent` (`android:fullBackupOnly`), which runs Android's standard Auto Backup only while the owner's choice allows it and otherwise hands Android nothing to copy.
2. The choice is kept on the Android side (`DeviceBackupChoice`, off by default), because the agent can run in a process started only for the backup. Settings, Automatic backup, shows and changes it through the `DeviceBackup` plugin.
3. Even when allowed, the over-the-air web bundle and its state (`files/live/`, `lifemax_live.xml`) are excluded: they belong to the APK on the phone they were downloaded to.

## Consequences

- After the update that ships this, new device backups contain nothing until the owner turns the switch on; restoring onto a new phone then brings no LifeMax data unless it was on. The folder backup is unaffected.
- What Android does with a copy it made earlier is up to Android and Google (see the Settings text); the switch controls new copies.
