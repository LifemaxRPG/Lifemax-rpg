# ADR 0008: The save is protected on every path that can lose it

Status: implemented; extended by ADR 0004's work.

## Context

The pre-rewrite review found ways the owner's single save could be lost or silently diverge: an unreadable save was replaced by a blank profile, a failed write still reported "Activity saved", a double tap from a native screen could pay XP twice, and an import or reset replaced the whole save without asking or keeping a copy.

## Decision

1. `load()` keeps each unreadable save under `lifemax_rpg_prototype_v1_unreadable_<time>` before anything is written, and tells the owner. With no room for that copy nothing is written: the save stays read-only until the owner exports it and deletes or imports (ADR 0004).
2. `save()` returns whether the write worked; `recordActivity` takes back an activity whose save failed, so it is neither shown nor counted, and reports the failure.
3. `recordActivity` takes an operation id, so a repeated submit pays once; native Log uses one id per draft.
4. Whole-save replacements (import, onboarding import, reset) go through `applyBackup`: it refuses a file that is not a backup, asks before replacing a save with activities, and keeps the current save under `lifemax_rpg_prototype_v1_safety` first, cancelling if it cannot. Delete-all clears the safety and unreadable copies too, as its warning promises.
5. Celebrations of a write that a native screen requested are held and played after the reply, and not at all if the write failed (`holdCelebrations`).

## Consequences

- Regression tests in `log-activity.test.js` fail on the old code; the 163-case golden is unchanged.
- The safety copy is one slot: a second replacement overwrites the first copy. Folder backups (ADR 0004) are the durable layer.
