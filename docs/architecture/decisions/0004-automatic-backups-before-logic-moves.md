# 0004: Automatic, verified backups before any logic or data moves to native

Status: accepted by the owner ("I'm down with an automatic backup system").

## Context

The owner's progress is one web save plus extra keys and IndexedDB data, the owner has no computer, and the plan later moves rules and data ownership into Kotlin. A backup that exists but has never been restored is not a backup. The app already has a folder-based automatic backup (BackupFolder plugin, `lmaBackupDue`, `lmaRunBackup`, Settings) that the owner chooses a folder for.

## Decision

Before any rule, data model or persistence moves out of web code:

1. The automatic backup must be on, must have a folder, and must run on a schedule and on leaving the app, with the last success time visible in Settings and a visible warning when it is stale or failing.
2. A backup is only counted once it verifies: the file is read back and parsed, and its size and key counts are compared with the live save.
3. A restore path exists in the app and is exercised by a test against golden save fixtures (export, import, compare), so the format is a tested contract before Kotlin reads or writes it.
4. The save carries a format version and a migration step, so a native reader can refuse or migrate an older or newer save instead of guessing.
5. The design is finished from the findings of the data-safety review that accompanies this decision; this ADR records the commitment, not the final mechanism.

## Consequences

- Native tabs that write through web handlers (Log, Food, Cash) keep using the existing save; the backup work does not block presentation-only tabs, but it blocks the first change of logic or data ownership.
- The owner is asked once to confirm the backup folder in Settings.
