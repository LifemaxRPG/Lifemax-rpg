# ADR 0014: Library writes keyed by the page's natural receipts

Status: built for the Media sheets (plan phase 3, capability `library.media.write.v1`, behind "Native Library (beta)"); proposed, not built, for the Journal's writing (plan phase 2, capability `library.journal.write.v1`). The plan called this ADR 0013; the numbers moved up by one when the Overview's cards took 0012 (private projections are 0013).

## Context

A native screen that sends a write must live with a lost reply: the page answers only a showing screen, and the screen may hide, close or be killed between the page storing a change and the answer arriving. Native keeps no durable record of a command in flight (A14/A15 are not yet the native side's), and the page is the only writer (ADR 0005). So a write has to be safe to send twice, and `ok` must mean the page's `persist()` succeeded.

For the Library the page already has what it needs, and a native pending record would be a second source of truth:

- Media: an item has an id, status is a value, and a removed item stays removed.
- Journal: a reflection's reward is paid once per day by `journalCoach.days[d].xpAwarded`, a welcome-back chip by `welcome[d].claimed[kind]`, a review by `reviews[key].paid`.

## Decision

1. **Repeat safety comes from the page's own receipts, stored in the same save as the change.** No native pending record, nothing replayed after a restart.
2. **Media sheets (built).** `mediaUpsert(fields)` takes an `op` from the screen and stores it on the item as `addOp` in the same save, so the same add again answers the item it made (`changed: false`, "Already recorded.") instead of adding a second. An edit is a set by id; an edit of an item removed meanwhile is refused ("That item is no longer in your library") rather than adding it back under its old id. `setStatus` is a set. `remove` and `removeAudio` do nothing to an id already gone. Each answers `{ok, changed, message}` (an upsert also `id` and `field`); `ok` only once the save stored it, and a failed save is taken back by the page's own writer, so the same operation id lands it once on the next try.
3. **The screen keeps an operation id per (command, target) until a terminal reply** (`MediaSheetController`, as `SheetController` does for Food). A reply lost because the screen hid is not terminal: the screen that opens again stops waiting, keeps the sheet and its draft, and Save sends the same payload with the same id. A sheet closes only on the page's `ok`; a refusal is drawn in the page's words under the field the page names.
4. **Gated by capability.** The page sets `edit: "native"` and sends the form's words only when the handshake lists `library.media.write.v1`; each write command is refused ("Media cannot do that here.") for a screen that did not say it, and an older APK keeps the page's own form. Play and attach are separate (the audio dock, `shell.audio.v1`).
5. **Journal writing (proposed).** Text writes are sets with a base check (the screen sends the text it started from; the page refuses a changed base). A reward is paid in its own `save()` after the words are stored, with the page's `takeBack` for the award alone, keyed by the receipts above, so a repeat answers "Already recorded." and a failed save takes back only the reward and never the words. Text-only autosave (1.5 s) does not pay; the final write on leaving the field, on hide and on stop may. The page owners are split for this in phase 0 (`jcSaveReview`, `jcCatchUp`, `jcClaim`, `jcOnReflect` store the words first and pay second); the native commands and `edit: "native"` are not built.
6. **Private.** None of this relaxes ADR 0013: a write command is refused while the Journal is locked, and a draft is private text native must not keep at rest.

## Consequences

- BRIDGE_CATALOG `library.mutationsGate` states the Media rules above. `journal.mutationsGate` still says phase 1 writes are decision-only fields with no XP, a compare-and-set swap and an idempotent tip, and that phase 2's paying commands wait for this ADR.
- A crash between the page storing a change and the screen learning of it shows as the changed projection; the next Save of the same add answers "Already recorded."
- Not covered by a receipt: a screen killed while a sheet is open loses its draft. That is the screen's local state and is accepted.
- Gates for the Journal phase: repeat and failure tests for every paying command, the text-only autosave test, the flush-on-stop test, and the owner's phone checks 15-17 of the plan.
