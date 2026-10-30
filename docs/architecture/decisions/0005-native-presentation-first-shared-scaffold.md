# ADR 0005: Native presentation first, logic stays in web code, one shared scaffold per side

Status: accepted by the owner and implemented (Native API 20 to 23).

## Context

The whole app was a web page in a Capacitor shell. The owner wants a native app that looks and feels native, without losing years of saved progress and without a period in which the app is half broken. The page holds every rule (XP, quests, scheduling, nutrition) and the only save. Each earlier native screen (Overview, Character, Food, Cash) had been built its own way.

## Decision

1. Tabs become native one at a time, presentation first. The page stays the authority for data and rules: a native screen draws a projection the page builds, and sends actions back. Logic moves to Kotlin only after every tab's visuals are native, and only under A14/A15 and ADR 0004.
2. A native action either closes the screen and runs the page's own handler (delegation, listed in the catalog and the ledger), or is a command the page runs while the screen stays open (`cfg.commands`), replying with the next update. A command that writes goes through the page's own function, is safe to repeat (an operation id or set-semantics) and reports a failed save.
3. One scaffold per side: `NativeTabKit` (web) owns sessions, sequence numbers, open/update/close, hand-over, refresh, the settings switch, commands, `blocked()`/`revisit()`; `ComposeTabPlugin` (Kotlin) owns the handshake, the kept-alive screen, the shared bars and insets. A tab is a projection, its actions, a decoder, a view model and its screen.
4. New tabs ship as an opt-in beta until A33's definition of done.

## Consequences

- A tab is small: Character's web half went from 283 to 109 lines, its plugin to 33; Log was the first tab built on the kit from the start.
- The WebView stays alive underneath for the whole rewrite, and every projection crosses the bridge as JSON (A25 budgets, backlog P4).
- Food and Cash predate the scaffold and are recorded exceptions until they move onto it.
