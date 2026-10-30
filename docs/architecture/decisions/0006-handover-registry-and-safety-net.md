# ADR 0006: The hand-over reads one registry, and a safety net repairs what slips through

Status: implemented (A23 items 7 to 9).

## Context

ADR 0002 made tab changes make-before-break. A seeded simulation of rapid tab hopping (A28) then found rare blank screens, about 1 in 70 sequences: an open cancelled while still in flight, a close overtaking an open, a stale screen left behind. Separately, the Overview code kept its own lists of which tabs were native, so every new tab meant editing it and a forgotten edit meant a slide animation or a missed hand-over.

## Decision

1. A cancelled open that is wanted again is issued again behind the close (generation counter in the kit; an `again` flag in Food).
2. Every tab registers how to ask whether it is open or opening and how to open and close it (`NativeTabs.register`). `NativeTabs.heal()` runs 600 ms after the last navigation and, once nothing is in flight, opens the current view's tab if missing and closes any other.
3. Whether a view is native is read from that registry (`NativeTabs.has`, `NativeTabs.covered`); no tab-name list exists anywhere else.

## Consequences

- 40 seeded sequences (about 285 hops) run in CI on every push with no blank screen; the same test fails on the old logic.
- The safety net can hide a new race instead of exposing it, so it is a repair, never a substitute: a heal that fires is a defect to trace (A29's trace will record it).
- A new native tab needs no edit in `50-native-overview.js`.
