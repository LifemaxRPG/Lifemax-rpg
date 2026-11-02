# ADR 0013: Private projections: the native Journal holds nothing it should not

Status: implemented for the native Journal's reading phase, behind Settings, Appearance, "Native Journal (beta)", off by default (CONTRACT A35; plans/JOURNAL_AND_LIBRARY_SHEETS.md section 3.2; `50-native-journal.js`, `feature/journal`). The plan called this ADR 0012; the Overview's cards took that number first, so the numbers moved up by one (this is 0013, native writes are 0014).

## Context

The Journal is the one tab whose text the owner can lock (`48-journal-lock.js`, the Journal lock). On the page the lock is a CSS cover inside `#view-history`: the journal text is in the DOM under it, FLAG_SECURE is set only while the view is `history`, and the page relocks when the app goes to the background or after a grace minute. BACKLOG SEC-2 recorded that a native screen knows none of that, and that the obvious fix, putting the cover in `lmaTopOverlay()`, would make Back "close" the lock and stop every tab swipe from the card.

A native Journal is also drawn by machinery built for screens that are not private. A hidden screen stays composed with its last data (A24), the tab swipe may draw a hidden screen beside the open one (A34), the app may stop while it shows, and a frame sent just before a relock can land just after it. Any of these would show or keep journal text while the lock is on, if native decided anything about the lock itself.

## Decision

1. **The page decides, and a locked page sends nothing private.** `projection()` in `50-native-journal.js` asks `LifeMaxJournalLock.blocksNative()` first. While it is true the frame is a withheld frame: the switch's panes, the lock card's words (`lock`), the prompt's status (`status {prompting, error}`), the kit's notice and protocol fields. No tip, hero, list, selection or entry, and the open builders do not run. Every command but `unlock` is refused with "Unlock the Journal first." The page subscribes to the lock (`onChange` and `onStatus`, new in `48-journal-lock.js`), so a relock pushes the withheld frame at once and an unlock the open one, and the prompt starting, failing or being cancelled reaches the card as it happens.
2. **Native has no field to hold it in.** `WebJournalRepository` holds a `JournalFrame`: `Open` (the only variant with journal data), `Locked` (the card, from a withheld frame) or `Resting` (the chrome alone). The decoder is strict: a withheld frame that carries a private field, or an open frame missing one, is rejected and the last good frame kept.
3. **Dropped at rest, whatever the lock.** When the screen stops being drawn (`ComposeTabPlugin.onRested()`, called by `TabSwipe` when a pane that is not open leaves the Shown stage, and from `remove()`), the repository drops to `Resting` and the secure hold is let go. This is not tied to the lock being on: the lock can be switched on while the screen is hidden, and a swipe can draw a hidden screen. A re-show accepts the fresh frame before the pane is shown, and a swipe that pulls a parked Journal in draws the resting page until then.
4. **The session gate.** When the app stops while the Journal shows a frame whose lock is on, native turns it into its card at once, without a round trip (`handleOnStop`, `WebJournalRepository.stop()`), and from then on refuses that page session's open frames until the page's withheld frame (its own relock) has arrived. Frames are ordered by session and sequence (A23 item 3), so an open frame built before the relock but still in flight is refused whether it lands before the withheld frame (the gate) or after it (the sequence). A refused open frame still shows the card, made from its chrome, so a screen that rested meanwhile is never left blank. The gate is never set while the lock is off, and is cleared by the withheld frame, by an open frame from a new page session (a reload) and by an open frame that says the lock is off (the owner turned it off), so the card cannot get stuck.
5. **The secure hold.** `SecureWindow.hold(owner, on)` sets FLAG_SECURE while any holder wants it. `BiometricLockPlugin.secure` goes through it, and `NativeJournalPlugin` holds while its screen draws a frame whose lock is on, releasing it at rest. The page clearing its own flag on leaving `history` therefore no longer uncovers a Journal still on screen during make-before-break or a swipe's slide.
6. **Unlock is the page's prompt.** The `unlock` command relocks the page first if native had locked itself and the page never saw a hidden visibility (so its withheld frame clears the gate), then runs `LifeMaxJournalLock.prompt()` and answers `ok` only for `true`. Phase 0 reordered the success path to `jlUnlocked = true; renderHistory(); jlApply()`, so the first open frame is complete.
7. **The page leaves private text out of its own DOM while locked** (phase 0): `renderHistory` and `renderJournalEntry` empty the hero, tiles and entry while `blocksNative()` holds, every relock path redraws, and the boot render does it once at load. The cover, the grace minute, thumbnails and the Settings card are unchanged. The lock cover is still not in `lmaTopOverlay()`.

This replaces SEC-2's wall (no native screen may open on a locked Journal) with the lock card drawn natively from a withheld frame.

## Consequences

- Journal text exists in native memory only in an `Open` frame of a screen that is being drawn. A canary test on each layer proves it (A35 item 5): the web test, the fixtures (`journal-locked*.json` carry no private field), the hand-over simulation with the lock and reloads, and Kotlin (`JournalTest`, `JournalFixturesTest`), each shown failing when its rule is removed.
- The Journal's notices are in the withheld frame too: the page's toasts carry no journal text ("Backup saved" after Save as is one).
- A screen that hides itself is not private by being hidden. Any later tab that draws data the page keeps private follows A35, not this tab's code.
- The Overview, the Media pane and the audio dock draw nothing from the Journal.
- Not decided here: writing. Phase 1 writes only decisions with no XP (a swapped question, a dismissed tip); a reflection or review answer is typed in the page's own field (the `write` command focuses it while the screen still covers the page). Native writing waits for ADR 0014.

## Differences between the plan and the code

- The gate is also cleared by an open frame that says the lock is off, and a refused open frame draws the card from its chrome (both added in review).
- The Journal's catalog entry lists `shell.swipe.v1` and `v2` beside its projection capability; there is no `library.journal.write.v1` yet.
