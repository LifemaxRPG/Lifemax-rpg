# ADR 0026: Hold clock track to schedule one activity

A stationary long press on the day wheel opens a schema-driven goalForm sheet with an at:epoch
identity. Movement beyond touch slop instead starts calendar scrubbing; a tap does neither.
The native dial maps its actual start/end epoch range to the pressed angle and snaps to fifteen
minutes. The normal day wheel remains the sole owner after a rotation begins. Existing handles
and activity holds keep their routes. Native forms reuse draft, validation and stable operation IDs.

activityAtForm returns name, tag, local date, start minute and duration fields. activityAtAdd is
a web-authoritative semantic command: it validates date round-trip, future time, current plan
horizon and availability/instance conflicts, then saves a pinned PLANNED instance with no rewards.
The whole candidate state rolls back on persistence failure. Identical operation IDs find the
same scheduled/history instance; regeneration preserves the locked occurrence.

This first slice refuses days outside the generated horizon rather than creating occurrences that
a later plan rebuild could silently drop. Future horizon-independent manual activities need
their own persistent facts and reconciliation across generation, completion and removal.
The feature does not activate the separate native timer migration.

The form travels over existing sheet(kind=goalForm,id=at:epoch) and do(name=activityAtAdd,args)
transports. Only at: sheets accept that save command; ordinary goal forms still require goalAdd.
Tests use the real availability and generation engine for time selection, conflicts, duplicate
operations, pinned preservation, invalid dates and storage rollback. Device gesture review is pending.
