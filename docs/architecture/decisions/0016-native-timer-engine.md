# Life Director timer rules: first logic migration slice

Status: implemented as an isolated, non-authoritative Kotlin engine. Not activated in the app.
Baseline: 87c06d80c2d9d75a5a82d724e127c23bd37388bf.

## Ownership

`TimerEngine` is a pure reducer under `feature/lifedirector/domain/timer`. It proposes an immutable
timer state and, for finishing, a completion message. It has no Android, bridge, storage, ticker,
or XP dependencies. No production code calls it yet. Life Director remains WEB_OWNED.

This satisfies the first extraction step of contract A07/A10, not the ownership cutover gate.
The dedicated CI workflow compiles the exact source with Kotlin 2.0.21 and runs JVM tests without
building or publishing an APK. Tests also compare supported transitions with fixtures generated
from the actual current JavaScript `Today.timer` handler.

## Existing behavior and dependencies

- `logic.js` keeps `ST.ui.focus` with id, start, paused, pausedMs, and optional plannedMs.
  `persist()` saves the enclosing Life Director state through `LifeDirectorStore`.
- Web button handlers directly change focus; native commands enter through `Today.timer`.
  `focusSheet` clears orphaned focus. Completion of an activity also clears matching focus.
  These paths must all use the future owner before activation.
- `focusTick` checks deadlines once per second and calls `autoFinish`. Elapsed time derives from
  timestamps, not tick counts. Background catch-up therefore caps automatic credit at planned
  duration. Manual completion uses max(1, round(elapsed milliseconds / 60000)).
- `finishInstance` records an engine ledger event, invokes `Host.awardActivity`, updates schedule
  history, runs `afterEvent`, may reschedule residual work, then persists Life Director state.
  Those steps cross save boundaries. A timer being cleared is not proof that rewards committed.
- Reset pauses at zero and creates a newer timestamp generation. Replacement discards old elapsed
  time without logging. Schedule eligibility and duration resolution stay outside the reducer.

## Deliberate differences to verify before activation

Every control in Kotlin requires both activity id and timer generation. Legacy pause/resume do
not validate either. Kotlin rejects stale requests rather than inferring success from a matching
activity title/id. This reducer is not a durable receipt/deduplication system.

Backward wall-clock movement is clamped for accumulated pauses. Ordinary wall-clock behavior is
preserved for parity, including background catch-up. A future monotonic-clock adapter must define
reboot and manual clock-change behavior before activation. The reducer itself schedules no alarms
and promises no exact background execution time.

## Required next steps before authority changes

1. Finish inventory of store keys/schema, backup/restore/reset entry points, all focus writers,
   host reward receipts, and schedule mutations which can invalidate the active activity.
2. Implement a serialized native storage adapter. Persist timer state and a completion outbox
   together before publishing either. On failed writes retain the prior committed state.
3. Give operations stable identity scoped to an authority epoch; reject stale delivery after
   reset/restore. Persist and reconcile completion delivery through an idempotent web logging
   endpoint. Test interruption before and after each of the two save commits, including reward
   failure and lost acknowledgments. Never clear pending completion solely on a timeout.
4. Test import/export compatibility and interrupted migration, retaining a recoverable original.
   Route ALL web controls and tick completion to the native owner, then atomically activate it.
5. Wire foreground/resume catch-up and existing UI projections; verify real device restart,
   background, midnight, and paused-timer behavior. No always-running service is assumed.

No screen toggle may reactivate old JavaScript timer writes after cutover. Until these gates pass,
the current shipped timer and reward implementation remain authoritative and unchanged.
