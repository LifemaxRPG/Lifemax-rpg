# ADR 0021: Native journal preparation for timer import and epoch replacement

Status: preparation primitives implemented; no authority activation or bridge registration.

TimerMigration preserves the legacy activity ID, start generation, paused timestamp,
accumulated pause, and planned duration. Missing duration uses the verified schedule target.
Orphaned, unavailable or invalid focus fails instead of silently becoming an empty timer.
The input is typed; strict wire decoding is still required at the bridge.

Import writes only an empty journal, or accepts an identical durable imported snapshot after
a lost acknowledgement. A different existing snapshot requires reconciliation. Preparation
does not disable the web timer: callers must finish the cross-store authority handshake
before either native controls or automatic native completion can run.

TimerRepository.rotateEpoch accepts an expected current epoch and a new initial snapshot.
An uncertain journal or outstanding completion outbox blocks replacement. It does not discard
unacknowledged rewards. A failed rotation may have committed; reconciliation accepts only the
old or intended new epoch and reloads which one actually persisted. Old-epoch commands and
acknowledgements cannot affect the replacement.

These primitives do not coordinate game/Director restore writes. The restore coordinator
still needs to persist an intent covering all stores, preserve the old state until completion,
rotate both web markers, switch focus ownership, and resume safely after interruption.
Android bridge/lifecycle wiring and physical-device acceptance remain outstanding.
