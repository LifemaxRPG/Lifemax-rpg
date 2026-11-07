# ADR 0019: Isolated legacy reward preparation and single-write game receipts

Status: implemented on native-timer-engine; no production bridge registration or cutover.

## Reusing the actual reward rules

`LifeMaxHost.prepareTimerEffect` reuses awardActivity/completeQuest against a JSON copy of
the game snapshot. Because these helpers still reference lexical runtime globals, the adapter
synchronously substitutes candidate state/rules and no-op save/toast functions, then restores
every original reference in finally. It rejects reentrant preparation and never awaits.

This is a narrow transitional exception to ADR 0018's pure-apply requirement: from the caller's
perspective preparation has no persistent or UI effects, but its implementation temporarily
binds globals. Future changes to the invoked helper graph must not introduce callbacks, renders,
plugins, async work or additional writes. Tests execute the real helpers and compare results,
including caps, rested spending, perks, stars, quest completion and exception cleanup. The
quest note DOM read remains legacy behavior; it performs no DOM writes. Locked and mismatched
quest periods are rejected before the legacy helper can render or complete the wrong period.

## Commit boundary

`LifeMaxHost.createTimerGameStore` requires an explicit NATIVE timer epoch, completed economy
migration, a writable save, and exact agreement between live state and the last durable JSON.
Stale or dirty snapshots fail closed. Preparation preserves unknown fields in the full save.
The writer rebuilds attribute and skill XP, syncs Sparks, and checks badges silently on the
candidate, then writes the candidate including its effect receipt in ONE localStorage setItem.
It verifies the write before publishing the candidate as live state. It does not call save(),
renderAll(), checkLevelUps(), or Daily Hand while the transaction is being prepared/committed.
Existing public save() and ordinary award functions are unchanged.

An exception after attempting storage is UNKNOWN: live state rolls back, further reads/writes
are blocked, and reconcile must reread the same epoch's durable snapshot. This also handles an
exception after storage committed. Post-commit UI, badge/level notifications, and Daily Hand
verification still need explicit lifecycle wiring after the full completion receipt is final.

## Remaining integration gates

- Guard ALL game/Director writers while a completion is pending, including undo, imports,
  settings reset, rollover, voice and background/health updates. Dirty snapshots cannot simply
  be discarded to force a transaction through.
- Coordinate the timer epoch and ownership marker across both saves and the native journal.
- Bind the endpoint and DTO identity conversion, then route legacy focus writers and deadline
  catch-up to the Kotlin owner. The dormant methods alone do not perform this routing.
- On a changed quest period or missing quest, keep the pending completion for explicit
  reconciliation; do not silently drop the bounty or award a new period's quest.
- Reconcile notifications and run actual Android restart/background/restore tests before rollout.

Verification includes an end-to-end staged receiver using the real reward adapter, losing the
game-write acknowledgement, reconciling, and retrying without paying activity or quest twice.
Badge evaluation is stubbed in the isolated writer test; full runtime badge/Daily Hand lifecycle
and Android behavior remain cutover gates, not claims established by that test.
