# ADR 0020: Ordinary save fencing for native timer transactions

Status: implemented on the isolated timer branch; cutover remains disabled.

The normal game writeSave and Director Store.save paths reject writes when either
durable save has a pending native completion, their native epochs disagree, or the
candidate has stale receipt metadata. Director reset goes through this same guard.
Standalone Director hosts without the shared fence reject native-owned saves.
Unmarked legacy saves retain ordinary behavior, including damaged Director recovery.

The privileged completion adapters bypass these ordinary save functions and retain
their own epoch, snapshot and receipt checks. The fence does not make an ordinary
web save a privileged completion transaction.

Tests call the actual writeSave, Store.save and Store.clear functions. Pending work,
reset, stale metadata, changed epochs and unreadable native storage cannot overwrite
either the primary save or backup. Ordinary legacy saves remain writable.

Remaining integration: direct localStorage imports, backup restore and any other raw
writers must use a coordinated reset/restore barrier before authority can activate.
Legacy focus writers must route through the native owner. The fence blocks durable
overwrites; it does not roll back every legacy in-memory mutation or implement the
native bridge, timer import, lifecycle worker, or device recovery acceptance.
