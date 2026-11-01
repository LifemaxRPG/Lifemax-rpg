# Media engine draft: review fixes

The eight Kotlin files here came from an outside agent (the first commit of this folder is them verbatim). They are **not in the
build** and **cannot compile yet**: the domain types (`MediaItem`, `MediaError`, `CommitReceipt`, `MediaCommandResult`,
`AudioBinaryStore`, `MediaRepository`, `MediaDomainRules`, …), `LegacySaveMediaAdapter`, `MediaCanonicalHash` and
`MediaUiProjectionMapper` were not supplied, and `MediaCutoverCoordinator.kt` stops in the middle of `activateNativeOwnership`.
Nothing here was compiled or run; the fixes are written against the supplied code and the types as it uses them.

This is a logic-phase change (native becomes the writer of Media). It stays out of the app until the missing parts exist, the
tests below pass in the real module, and the whole-save work in the last section is done.

## What was fixed

| # | Finding | Fix | File |
|---|---|---|---|
| 1 | An unreadable store with no backup loaded as an **empty** library, and startup then deleted every audio file not in it: every audiobook | Load fails instead (Media shows read-only); empty only when no store file was ever written (`SnapshotSource.FIRST_RUN`) | `AtomicJsonFileMediaStore.kt` |
| 2 | Audio not named by any item was **deleted** at startup, trusting a list that could be stale or wrong | Moved to `audio/orphans/` instead, and only when the store came from the live file (`SnapshotSource.LIVE`), never from the backup or a first run | `FileAudioBinaryStore.kt` (`sweepOrphans`), `DefaultMediaRepository.initialize` |
| 3 | The store was read leniently: a damaged `authorityMode` became `WEB_OWNED` (the older web copy would win), a missing list became empty, receipts took defaults | Strict reading: a missing or wrong-kind field is an error, so the file counts as unreadable and the backup is used | `MediaStoreSnapshot.kt` (`fromJson`, `require*`) |
| 4 | The checksum was taken over a re-serialised JSON object, not the bytes written (depends on key order and number formatting) | The payload is stored as text and hashed exactly as written | `AtomicJsonFileMediaStore.kt` |
| 5 | Every playback progress tick rewrote the whole store with a new receipt, pushing real changes' receipts out of the 256 kept, so a retried real change could apply twice | `recordPlaybackProgress` publishes at once, writes at most every 15 s, no receipt or sequence check (last write wins); `flushPlaybackProgress` on pause, background, end; a real commit carries any pending position | `DefaultMediaRepository.kt` |
| 6 | "Ready to play" committed a write and a receipt every time | A file that is there writes nothing (`deduplicated = true`); only a missing file is repaired and saved | `DefaultMediaRepository.verifyAndPreparePlayback` |
| 7 | Attaching audio copied up to 512 MB while holding the store's lock: every other command waited | Repeat, conflict and missing-item checks first; the copy runs outside the lock; the commit re-checks under it; a copy that is not committed is discarded | `DefaultMediaRepository.attachAudio` |
| 8 | Free space of 0 was taken as "unknown" and allowed | Refused (0 is also what a full disk reports) | `FileAudioBinaryStore.kt` |
| 9 | Migration verification compared the copied file with the size the copy recorded about itself | Compared with the old save's size, position and duration; type, finished date and attachment presence checked too | `MediaCutoverCoordinator.verifyStagedMigration` |
| 10 | A migrated file whose save then failed stayed on disk unrecorded | Deleted on that failure | `MediaCutoverCoordinator.completeAudioMigrationTransfer` |
| 11 | A public `simulateWriteFailure` switch on the production class | `internal var failWrites`, for tests in the module | `AtomicJsonFileMediaStore.kt` |
| 12 | A failed backup copy was ignored, so the live file could be replaced with no backup of it | The commit fails instead (the live file is untouched) | `AtomicJsonFileMediaStore.commit` |

New tests: `MediaStoreSafetyTest.kt` (fixes 1–3, 5, 6). The supplied tests are updated for the renamed hook and the progress
calls.

## What the missing parts must do (NEEDS in the code)

- `AudioBinaryStore`: rename `cleanupOrphans` to `sweepOrphans` with the meaning above; Settings or Diagnostics lists and clears
  `audio/orphans/`.
- `MediaRepository`: `updatePlaybackProgress(context, …)` is replaced by `recordPlaybackProgress` and `flushPlaybackProgress`.
- `LegacySaveMediaAdapter.parseItemsArray` reads the native store strictly, like `fromJson`; repairing duplicate ids and keeping
  unknown fields belongs to the legacy import only.
- `MediaCommandResult` / `CommitReceipt`: if a receipt cannot honestly say "nothing written", add a Ready case for
  `verifyAndPreparePlayback`.
- `activateNativeOwnership` must re-verify under the repository's lock, then commit `NATIVE_OWNED` in one write; and
  `rollbackToWebOwnership` must hand the page a patch it applies through its own save (with its take-back on failure) before
  the store goes back to `WEB_OWNED`, never the other way round.

## Before this can ship (not in these files)

- **Whole-save operations.** Today the page's save holds `mediaItems`, and import, reset, delete-all, backups, verified backups
  (ADR 0004) and Android device backup (ADR 0010) all act on that save. Once native owns Media, each of them must also act on the
  native store (and its audio files), or a restored backup comes back without its library, and a reset leaves the old one. The
  `saveEpoch` fence is the hook: a whole-save replacement starts a new epoch, and the page must tell the native store.
- **One writer.** From activation on, the web Library must stop writing `mediaItems`; its screens become a projection of the
  native store.
- **Contract.** A CONTRACT/ADR entry for the cutover (who owns Media, how rollback works) before the switch exists, behind a
  beta setting that is off by default.
