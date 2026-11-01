# ADR 0012: The Overview's cards: one owner, two native drawings

Status: phase 1 implemented, behind Settings, Appearance, "Native Overview cards (beta)", off by default (plans/OVERVIEW_BODY.md; `50-native-overview-cards.js`, `feature/overview`). The live hybrid stays the Overview's default until A33 holds.

## Context

The Overview is the most-seen screen and the one native tab whose body is still the live WebView: the hybrid (LiveOverviewShell, OverviewBars, NativeOverviewPlugin) draws native bars over the page and masks the web bars. TalkBack cannot reach those native bars, every arrival rebuilds them, and the Overview cannot be a native swipe neighbour. The cards draw the head and every card natively from the page's projection (`overview.cards.v1`), so the Overview becomes a ComposeTabPlugin screen under ChromeHost's shared bars like every other native tab. But the hybrid must keep working: it is the default, the fallback when the cards cannot draw, and what an old APK has. So one view, `dashboard`, has two native drawings.

## Decision

1. **One owner, one registration.** `50-native-overview.js` stays the owner of the `dashboard` view. It registers one composite with `NativeTabs`, which asks the cards first (wanted: the switch on and not given up), then the live shell. The cards' tab is created with the kit's `managed: true`, so the kit neither registers it nor opens it on arrival; it still closes it when the page leaves the view and keeps its watcher, refresh and `revisit()`. While the cards are wanted the hybrid counts itself blocked, and its `open()` returns early, so the undecided drawing never flushes the hand-over. Precedence: the cards when on and not given up; otherwise the live shell when "Native Overview" is on; otherwise Classic.
2. **Each drawing closes the other once it has opened.** `NativeTabs.heal()` sees one entry for the view, so it cannot repair two drawings left open behind it; the cards' `afterOpen` closes an open shell (`NativeOverviewShell.close()`), and the shell's open closes active cards.
3. **Giving up is for the session.** A missing capability, an open or a refresh rejected (`cfg.onFail`) makes the cards give up: the live shell takes the view (`heal()`), a toast says so once, and nothing retries them until the switch is turned off and on again (A19: a safe fallback, not a hang).
4. **Celebrations through `fxSink`.** An XP celebration that starts while the cards show (Native Log's held orbs 450 ms after its save, a health sync's XP) is handed to them: `celebrateXP` asks `fxSink` first, the cards put it in the projection with an id the screen plays once, and the page plays the sound and buzz 120 ms later and holds nothing, so the screen does not step aside. Level-ups, and a celebration that starts while the cards are not showing, play on the page; the cards step aside and come back after (`afterCelebrations`).
5. **The cat is a door to the web room.** The Cat card draws a portrait the page renders (`petPortrait`) and goes to the room on the page (ADR 0003 keeps the room web). While the cards cover the page the pet loop is stopped (`petWantRun` reads `NativeOverviewCards.covering()`), so `lm_pet_seen` is not refreshed while only the card is seen, and a long-absence greeting can fire on entering the room. That is honest: the cat was not seen.
6. **The landscape rule.** Above 860 CSS px the page's sidebar is a side rail, not the bottom bar the shared bars are drawn from, so the cards step aside and the page's wide Overview shows, as with the hybrid; rotating back brings the cards (`resize` calls the kit's `follow()`).

## Amendments

- **ADR 0006 and NAV-6.** "A new native tab needs no edit in `50-native-overview.js`" still holds for a new tab. A view with two native drawings registers one composite in its owner module, so a second drawing of an existing view does edit that module (NATIVE_TAB_GUIDE.md).
- **ADR 0011 item 5, and CONTRACT A34 item 6.** The Overview is a swipe wall only while the hybrid draws it. The composite's `body` getter answers `'web'` only while the cards are not wanted, and its `wouldOpen` is the cards' `coverable()`, so with the cards on (and Native tab swipe on) the Overview is a native neighbour both ways, with no Overview-specific swipe code; the apps row takes `noTabSwipe()` while it overflows. With the cards off, or given up, it is a wall as before. ADR 0011's z-order row "Overview shell, only while Overview is open" and swipe phase 2's "the Overview's open calls `ChromeHost.closeAll()`" apply only to the hybrid.
- **CONTRACT A23.** A view may have two native drawings; its owner module registers one composite (item 9, one registry, is unchanged).

## Consequences

- With the cards on, the Overview keeps ChromeHost's bars across tab switches, TalkBack reads its header and body as native nodes, and it is a warm, kept-composed switch.
- With the cards on and Native tab swipe off (its default), the Overview has no tab swipe: the web swipe needs the live body under the finger. The switch's hint and the MIGRATION row say so; whether that is acceptable is the owner's open decision 5.
- The hybrid's code (LiveOverviewShell's non-chromeOnly mode, OverviewBars, NativeOverviewPlugin) stays until the cards have been the default for a release with no regression (A33).
- The A28 simulation runs every seed list with the cards off, on, switched mid-run from native Settings, and failing (the shell must hold the view alone), with faults for both drawings showing, the shell opening while the cards are wanted, and the screen left closing before either drawing showed.
