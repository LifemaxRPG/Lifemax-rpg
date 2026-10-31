# ADR 0011: Native tab swipe: native owns the motion, the page owns the decision

Status: phase 1 implemented, behind Settings, Navigation, "Native tab swipe (beta)", off by default (CONTRACT A34, A23 item 11, A24).

## Context

The owner went native mostly for the feel of moving between tabs: "not completely smooth, but I want there to be some tension in the middle of the swipe, so you have to really swipe to get through past that threshold to move to the next page." The web swipe (34-navigation.js) follows the finger 1:1 and commits at 22 % or 0.45 px/ms; it never felt right, and it cannot move a native screen, which lies over the WebView.

Three designs were judged (a full-shell rebuild around a new stage, a pure deck model that moves every screen under one parent, and a Compose-level gesture where the page decides). The third was chosen as the base: it moves pixels only and changes no ownership and no view hierarchy. Grafts from the other two fixed its two flaws (it read coordinates from a view moving under the finger, and its veil would have hidden the web bars).

## Decision

1. **Native owns the motion.** The gesture is read in the open screen's own View (`TabScreenView`, an `AbstractComposeView`, since `ComposeView` is final), in raw screen coordinates, giving a child one event to claim it first (a scroller, `Modifier.noTabSwipe`). `TabSwipe`, a main-thread sibling of ChromeHost, drives a pure state machine (`SwipeModel`: Idle, Armed, Dragging, Settling, AwaitPage) and is the only writer of the registered screens' `translationX`, visibility and accessibility. Screens move in place under `android.R.id.content`; nothing is re-parented, so no composition is disposed. Visited native neighbours wait parked (INVISIBLE at ±W); an unvisited one, a wall or an end is drawn by one veil at index 1, below every screen and bar.
2. **The feel is numbers, owned by Kotlin and tunable by the page.** A 1:1 follow to the knee, then a slope that falls evenly to the catch at 45 % of the width, a latch with hysteresis and an underdamped pop, a rubber band at walls, a release rule that a casual flick does not pass, and closed-form critically damped settles that never overshoot. Defaults live in `SwipeTune.kt`; the page mirrors them in `LMA_TABSWIPE` (a fixture test keeps the two equal) and may override any key from Developer mode; native clamps every value.
3. **The page owns the decision.** It projects what a swipe needs (`swipe` in every bar scene: the bar's tabs in order, what each one is to a swipe, whether a swipe may start, motion, haptics, colours, tuning), so the state inherits ChromeHost's sequence and stale rule. A release that commits sends one `swipe` event; the page answers `swipeSettle` before anything else (Capacitor delivers calls in order, so the answer reaches Android before the destination's open), then goes to the tab as a bar tap does. The ordinary A23 hand-over runs from there.
4. **A swipe moves pixels, never ownership.** Only the page's open and close change which screen is open, and they always win: any other open or close aborts the swipe; a refusal springs back; a commit the page has not seen through in 2.5 s is undone by a watchdog; every return to rest checks the Views against the expected layout and repairs and records any difference.
5. **Rolled out in phases.** Phase 1 is native to native, with walls at web tabs, the Overview, Money (another Activity) and the ends of the bar. Phase 2 adds the web and Overview edges (a veil that hands over to the web drawing, `ChromeHost.hold`), phase 3 brings the same curve to the web swipe and composes neighbours ahead of time, phase 4 holds the separate decisions (Money, ShellState, Food on the shared scaffold).

## Consequences

- The WebView is never translated and never loses a touch mid-gesture, so a stuck transform cannot blank the page; the worst case is a native screen out of place, which the watchdog, the rest check and the page's next open repair.
- The feel can be tuned on the owner's phone with no new APK; what cannot be tuned (the curve's shape, the gates, the clamps) is tested in JUnit.
- Two feels exist until phase 3: the web swipe (1:1, 22 %) and the native one (the catch at 45 %).
- In phase 1 a wall in the middle of the bar (a web tab between two native ones) may read as broken; the beta label, the bar editor (group the native tabs) and phase 2 answer it.
- A first swipe to a native tab not yet opened in the session shows the veil while the screen composes; phase 3's priming removes that stall.
- An unhandled touch on an open native screen now stops at the screen instead of reaching the WebView behind it, even with the switch off.
- The arbitration relies on Compose's `AndroidComposeView` calling `requestDisallowInterceptTouchEvent` when a child consumes movement (checked in Compose UI 1.7.6); every Compose upgrade re-checks it with `SwipeArbiterTest` and the phone checklist.
- Food keeps its own copy of the plugin hooks (about 30 lines) until it moves onto ComposeTabPlugin (phase 4).
