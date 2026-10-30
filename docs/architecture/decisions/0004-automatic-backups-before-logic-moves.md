# 0004: Automatic, verified backups before any logic or data moves to native

Status: accepted by the owner ("I'm down with an automatic backup system"). Implemented on 30 October 2026; on the phone it takes effect once the owner chooses a backup folder.

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

## Implementation

Built from the data-safety findings (BACKLOG.md, Data safety and the load() half of the save/epoch item). `lifemax_modular_v73/tests/backup-restore.test.js` checks each point.

1. **Format version.** The core save carries `schema` (`SAVE_SCHEMA` in 04-runtime.js). `load()` brings an older save up to it through `migrateSave()`, the one place for future migrations; the flag migrations from before the number (`xpSchemaV2`, `mediaSeededV1`, the class quests) run there and stay idempotent. `writeSave()` is the only writer of the core save. A save from a newer schema (an APK or web update rolled back) is shown read-only: nothing is migrated or written, and the owner is told once. A backup carries the schema; an import refuses a newer one and migrates an older one the way `load()` does. An import or reset whose new save cannot be stored loads the stored one back.
2. **Verified backups.** A backup counts only after its file is read back (`BackupFolder.readBackup`) and checked by `backupProblem`: it parses as a backup the import accepts, and its schema, export time, activity, rule and extra-key counts and size match what was written. Only then are older files pruned; a file that fails, or is cut short while it is written, is deleted. Settings shows the last verified backup and warns when there is no folder, or backups are off, failing, never verified or older than twice the interval. With no folder, the owner is asked once by a small pill that opens the backup settings.
3. **Snapshot before risky changes.** Before an import, a reset, downloading or restarting into a web update and installing an APK, `lmaBackupFirst` takes a verified backup when a folder is chosen and waits for it. These snapshots rotate apart from the scheduled ones (`life-max-before-*`, newest 5). If one fails, the owner decides whether to go on; at launch, where nobody asked, the web update is not downloaded and Settings says why. The in-app safety copy is still kept.
4. **Restore as a tested contract.** Synthetic golden saves in `tests/fixtures/saves` (new, established, legacy, with extras; made once and never regenerated) are backed up on one phone, restored on another and compared: the save, the rules and the extra keys come back equal.

A backup holds the core save, the rules, Life Director, the Android preferences without the phone's own backup settings (a restore keeps those) and the theme. Display preferences, caches and the audiobook files in IndexedDB, which the owner has as files, are not in it.

Still open:

- Decision 1 holds on the phone only once the owner picks a folder (Settings, Phone & sync, Automatic backup). Until then Settings warns, and the first change of logic or data ownership waits for it.
- The save epoch for native writes (BACKLOG.md, Native write path) comes with the first native write, not before (CONTRACT A13, A20).
