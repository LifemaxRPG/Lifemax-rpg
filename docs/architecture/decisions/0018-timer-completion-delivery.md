# ADR 0018: Staged completion delivery before timer authority cutover

Status: protocol and pure Director preparation implemented on the isolated logic branch.
Not registered on Capacitor, not included in the production page, and not active authority.

## Implemented

`CompletionDelivery` attempts the oldest Kotlin outbox item. It preserves its identity after
timeouts and refuses mismatched acknowledgements. COMMITTED is the only response that can
remove an item; failed native acknowledgement writes require journal reconciliation.

`timer-completion-protocol.js` provides a synchronous receiver and game-effect commit adapter.
The receiver first persists prepared Director ledger/history/goals plus an effect list. Each
game effect and its receipt share one game snapshot write. It then finalizes the host receipt
and finally the Director receipt. Lost acknowledgements replay those same effects and receipts.
Both stores carry the same explicit authority epoch; ordinary web saves do not currently have
this metadata, so they cannot be accepted accidentally. Sequence high-water marks prevent
evicted receipts from executing again. Delivery must be FIFO from the single native owner.

`timer-completion-plan.js` uses the real engine's recordCompletion, reconcileQuestProgress,
ledgerHas, and residual repair. It prepares copies without calling save(), awarding rewards,
rendering, or emitting toasts. Archived event IDs are checked before recording. Missing residual
repair inputs reject preparation rather than silently dropping a remaining task.

## Explicit integration requirements

The protocol ports are synchronous and require exclusive access to both stores for each call.
All other Director/game writers must honor an outstanding prepared completion across calls.
Otherwise an ordinary save could replace a receipt, or the player could undo a reward while
its completion is still being finalized. No writer gate is installed by this slice.

The game `apply` port must be a PURE transformation. Existing Host.awardActivity and
Host.completeQuest are NOT suitable implementations: they mutate global state and save on
their own. Their activity XP, economy/streak hooks and quest bounty operations must be extracted
into an isolated candidate-state calculation, then saved with receipts in the same write.
Existing event IDs and quest-period checks must be preserved. A changed quest period requires
an explicit result, not a guessed bounty or an endless retry.

After that extraction, wire both store adapters, the bridge DTO identity mapping, lifecycle
worker, reset/restore epoch rotation, and legacy-writer exclusion. Test the actual host adapters
under failures before switching ownership. The current tests intentionally distinguish protocol
failure injection (synthetic host effects) from Director planning using the real game engine.
They do not establish production XP/economy parity or Android process-death behavior.

## Verification

- Every durable stage fails before and after write, then is retried after receiver recreation.
- Activity and quest effects are not repeated; newer completions wait behind pending work.
- Conflicting IDs, stale epochs, expired receipts, and invalid acknowledgements cannot commit.
- Pure planning checks real engine ledger, history, goals, quest overlay, partial completion,
  archived dedupe, input immutability and missing residual inputs.
- Kotlin transport tests cover restart/retry identity, refusal, timeout, mismatched payload,
  and native storage failure while acknowledging.
