# ADR 0017: Timer persistence and completion delivery preparation

Status: implemented and tested in the isolated native-timer-engine branch; NOT active authority.

## Audit findings

The active timer is `ST.ui.focus` in `lifemax_director_state_v1`. `LifeDirectorStore`
also retains `.bak` and may recover from it. Reset writes a fresh state; import replaces
the entire Life Director save. Neither currently coordinates an authority epoch with Android.

Writers in `logic.js` include web `focusStart/Pause/Resume/Finish/Cancel`, the native
`Today.timer` handler (including reset/replace/autoFinish), orphan cleanup in `focusSheet`,
session completion and removal, import, and reset. `focusTick` performs deadline catch-up.
Disabling only the native screen's bridge controls would leave active web writers.

`finishInstance` updates the Director ledger, calls `Host.awardActivity`, changes schedule
history, invokes `afterEvent`, may reschedule residual work, then saves Director state.
`Host.awardActivity` uses `ldEventId` in the main game activities to deduplicate and rolls
back a failed host save, but Director completion is a separate commit. A returned ledger
event is not evidence that both saves committed. The future delivery endpoint must resume
these stages using the same durable event ID and reconcile reward/quest side effects.
Do not connect native completion delivery directly to the current `finishInstance`.

## Implemented boundary

`TimerRepository` serializes operations and persists timer, receipts, and completion outbox
in one snapshot before publishing. A completion is only a proposal; Kotlin never awards XP.
Stable identity is (authority epoch, increasing operation sequence). Retries retain the
original request including timestamp. A bounded receipt cache is safe because the persisted
sequence high-water mark rejects evicted old operations rather than reexecuting them.
New controls must allocate sequences through the single owner, not separate ViewModels.

The file journal uses versioned, checksummed, bounded binary snapshots, fsync, and atomic
rename, plus an exclusive lifetime file lock. Unsupported atomic moves fail closed. Missing
and corrupt files are distinct; corruption is never silently replaced with an empty save.
An exception during writing has UNKNOWN outcome. Projections and mutations remain blocked
until rereading the journal resolves whether the commit landed. Outbox delivery is likewise
blocked while uncertain. Only a durable receiver receipt can acknowledge a completion.
Timeouts and UI messages must never acknowledge one. Full outboxes preserve the active timer.

The persisted generation high-water mark prevents stale controls affecting a timer restarted
in the same millisecond. Wall-clock rollback keeps generations increasing but otherwise
retains the reducer's documented wall-clock semantics; reboot/clock-change device validation
is still required. File primitives require API 26+ and device-filesystem verification before
activation. This format is private staging storage, not yet a backup/import contract.

## Cutover gates still open

1. Add an epoch-scoped idempotent web completion endpoint with recoverable staged receipts
   across host rewards, Director ledger, quest side effects, and history commits.
2. Test each failure boundary in the real endpoint, including lost ACK and backup restore.
3. Wire the singleton repository at the composition root and perform one-way import of
   legacy focus. Persist/coordinate the ownership marker and epoch before disabling writers.
4. Route ALL web controls, ticks, and cleanup to that owner. Reset/restore must rotate epoch,
   preserve or explicitly reconcile the old outbox, and reject late acknowledgements.
5. Integrate backup/export and lifecycle catch-up; run Android device recovery checks.

No authority switch, native-mode toggle behavior, production UI, or release workflow changes
are part of this slice. The dedicated JVM workflow exercises the exact Kotlin sources and
the current JavaScript parity fixtures without publishing an APK.
