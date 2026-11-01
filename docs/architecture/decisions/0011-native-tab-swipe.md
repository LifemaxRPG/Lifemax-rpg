# ADR 0011: Native tab swipe: native owns the motion, the page owns the decision

Status: phase 1 implemented, behind Settings, Navigation, "Native tab swipe (beta)", off by default (CONTRACT A34, A23 item 11, A24).

## Context

The owner went native mostly for the feel of moving between tabs: "not completely smooth, but I want there to be some tension in the middle of the swipe, so you have to really swipe to get through past that threshold to move to the next page." The web swipe (34-navigation.js) follows the finger 1:1 and commits at 22 % or 0.45 px/ms; it never felt right, and it cannot move a native screen, which lies over the WebView.

Three designs were judged (a full-shell rebuild around a new stage, a pure deck model that moves every screen under one parent, and a Compose-level gesture where the page decides). The third was chosen as the base: it moves pixels only and changes no ownership and no view hierarchy. Grafts from the other two fixed its two flaws (it read coordinates from a view moving under the finger, and its veil would have hidden the web bars).

## Decision

1. **Native owns the motion.** The gesture is read in the open screen's own View (`TabScreenView`, an `AbstractComposeView`, since `ComposeView` is final), in raw screen coordinates, giving a child one event to claim it first (a scroller, `Modifier.noTabSwipe`). `TabSwipe`, a main-thread sibling of ChromeHost, drives a pure state machine (`SwipeModel`: Idle, Armed, Dragging, Settling, AwaitPage) and is the only writer of the registered screens' `translationX`, visibility and accessibility. Screens move in place under `android.R.id.content`; nothing is re-parented, so no composition is disposed. Visited native neighbours wait parked (INVISIBLE at ±W); an unvisited one, a wall or an end is drawn by one veil at index 1, below every screen and bar.
2. **The feel is numbers, owned by Kotlin and tunable by the page.** A 1:1 follow to the knee, then a slope that falls evenly to the catch at 45 % of the width, a latch with hysteresis and an underdamped pop, a rubber band at walls, a release rule that a casual flick does not pass, and closed-form critically damped settles that never overshoot. Defaults live in `SwipeTune.kt`; the page mirrors them in `LMA_TABSWIPE` (a fixture test keeps the two equal) and may override any key from Developer mode; native clamps every value.
3. **The page owns the decision.** It projects what a swipe needs (`swipe` in every bar scene while the switch is on: the bar's tabs in order, what each one is to a swipe, whether a swipe may start, motion, haptics, colours, tuning), so the state inherits ChromeHost's sequence and stale rule. A release that commits sends one `swipe` event; the page answers `swipeSettle` before anything else (Capacitor hands every plugin call, across plugins, to one thread in the order made, and each plugin posts it to the main thread in that order, so the answer reaches Android before the destination's open), then goes to the tab as a bar tap does. The ordinary A23 hand-over runs from there.
4. **A swipe moves pixels, never ownership.** Only the page's open and close change which screen is open, and they always win: any other open or close aborts the swipe; a refusal springs back; a commit the page has not seen through in 2.5 s ends at a watchdog, which springs the source back if the destination never opened and otherwise lays the open screens out at rest (the page's own 2.5 s flush closes the source); every return to rest checks the Views against the expected layout and repairs and records any difference.
5. **Rolled out in phases.** Phase 1 is native to native, with walls at web tabs, the Overview, Money (another Activity) and the ends of the bar. Phase 2 adds the web and Overview edges (a veil that hands over to the web drawing, `ChromeHost.hold`), phase 3 brings the same curve to the web swipe and composes neighbours ahead of time, phase 4 holds the separate decisions (Money, ShellState, Food on the shared scaffold).

## Consequences

- The WebView is never translated and never loses a touch mid-gesture, so a stuck transform cannot blank the page; the worst case is a native screen out of place, which the watchdog, the rest check and the page's next open repair.
- The feel can be tuned on the owner's phone with no new APK; what cannot be tuned (the curve's shape, the gates, the clamps) is tested in JUnit.
- Two feels exist until phase 3: the web swipe (1:1, 22 %) and the native one (the catch at 45 %).
- In phase 1 a wall in the middle of the bar (a web tab between two native ones) may read as broken; the beta label, the bar editor (group the native tabs) and phase 2 answer it.
- A first swipe to a native tab not yet opened in the session shows the veil while the screen composes; phase 3's priming removes that stall.
- An unhandled touch on an open native screen now stops at the screen instead of reaching the WebView behind it, even with the switch off.
- With the switch off the page sends no swipe state, so the native bar keeps following the page's scroll only on its first scene, as before. With it on, the bar glides to the page's scroll whenever that moves (170 ms, as the page's own), so the lit tab stays in view after a run of swipes.
- The arbitration relies on Compose's `AndroidComposeView` calling `requestDisallowInterceptTouchEvent` when a child consumes movement (checked in Compose UI 1.7.6); every Compose upgrade re-checks it with `SwipeArbiterTest` and the phone checklist.
- Food keeps its own copy of the plugin hooks (about 30 lines) until it moves onto ComposeTabPlugin (phase 4).

## The feel, in numbers

W is the width of android.R.id.content. u is the finger's travel toward the side being revealed as a fraction of W, measured from the claim point (the slop is already behind it). o is how far the screen has moved, in the same unit. Every number below is a `SwipeTune` default that the page may override and native clamps (the keys and clamps are in BRIDGE_CATALOG.json, `tab-swipe`).

```
hold(u)    = u                                   u ≤ a          (1:1)
           = u − (1−m)(u−a)² / (2(b−a))          a < u ≤ b      (the slope falls evenly from 1 to m)
           = hold(b) + m(u−b)                    u > b          (only on the event that latches)
through(u) = min(u, 1)                                          (latched: 1:1 again)
latched    = latched ? u ≥ b−h : u ≥ b
at a flip  r += s_old − s_new, keeping its velocity; a spring takes r back to 0:
           the pop at a latch (`popDamping` ζ 0.65, `popStiffness` k 1200), critically damped at an unlatch (k 1200)
o          = clamp(s(u) + r, 0, 1); a screen's translationX is round(−dir·o·W), its neighbour's that + dir·W
wall(u)    = E·(1 − e^(−κu/E))                                  (a wall or an end; the bare veil fills the gap)
```

a (`knee`) 0.18, b (`breakAt`) 0.45, m (`heavy`) 0.25, h (`hyst`) 0.06, E (`wallMax`) 0.08, κ (`wallSlope`) 0.35. On a 412 dp phone the screen follows the finger for 74 dp, then grows heavy (o = .243, .280, .310, .333, .349 at u = .25, .30, .35, .40, .45); at the catch, 185 dp of travel, it sits 42 dp behind the finger. Over the catch it pops to the finger in about 120 ms (about 7 % overshoot) with one click; pulled back under 0.39 it grows heavy again, critically damped and silent. Whole pixels mean no seam between the screens.

**Release** (v is the finger's velocity toward the neighbour from a VelocityTracker in raw coordinates), in order: a wall cancels; v ≤ −`backFlingDp` (600 dp/s) cancels, even latched; latched commits (`via: drag`); v ≥ `flingDp` (1200 dp/s) after at least `flingMin` (0.12) of travel commits (`via: fling`); anything else cancels, so a casual flick springs back.

**Settles** are closed-form critically damped springs (`Spring1D`) on Choreographer frame times, toward o = 1 for a commit and 0 for a cancel. v0 is the finger's velocity times the curve's slope; k = clamp((v0/|Δ|)², `commitK` 1000 or `cancelK` 800, 4000); v0 is capped at √k·|Δ|, so a settle never crosses its target (no gap past the far edge, no peek of the other side), and it snaps at 450 ms. From rest at o = 0.5 a commit is 95 % there at 150 ms and a cancel at about 168 ms. A refusal restarts the running spring toward 0 from where the screen is, as fast. Spring time is divided by `ValueAnimator.getDurationScale()` (0 lands at once); following the finger is never scaled. With the page's motion off (`swipe.motion` false) nothing follows the finger, the release rule is the same and every settle is instant.

**Haptics** (`SwipeHaptics`, on the source screen's View, honouring the system's touch feedback: no FLAG_IGNORE_GLOBAL_SETTING): nothing plays at walls, at landing or on a plain cancel, so a commit gets exactly one click, at the catch or, for a fling that never latched, at the release.

| `haptic` | page level | the catch | letting go of it |
|---|---|---|---|
| `threshold` (default) | light | CLOCK_TICK | none |
| | normal | GESTURE_THRESHOLD_ACTIVATE (API 34+), CLOCK_TICK below | GESTURE_THRESHOLD_DEACTIVATE (34+) |
| | strong, max | EFFECT_HEAVY_CLICK as touch feedback (29+), as normal below | as normal |
| `tick`, `click` | any but off | CLOCK_TICK, CONTEXT_CLICK | none |
| `off` | any | none | none |

The page refusing a commit plays REJECT (API 30+) at every level but off, whatever the style.

## Who gets the gesture

Whoever draws the pixels under the finger at the DOWN owns the gesture; the WebView is never translated and never loses a touch mid-gesture.

**Gates at the DOWN**, all of which must hold, or the screen stays passive for the whole gesture: the latest drawn scene has `swipe.v` 1 and `on`; this screen is the only open one, may be a source, and its view is `swipe.view`; no swipe is running; exactly one pointer, a finger or stylus; the keyboard is hidden; touch exploration is off; the window has focus; the DOWN is outside max(system gesture inset, `edgeDp` 24 dp) of both edges.

**The lock**, with dx and dy raw and measured from the DOWN and the slop `slopFactor` (1, and never below 1: a child scroller claims at the platform's slop and must be able to first) × scaledTouchSlop: dead if |dy| ≥ slop and |dx| < `lockRatio` (1.3) × |dy|; a candidate if |dx| ≥ slop and |dx| ≥ 1.3|dy|. The screen does not intercept on the candidate event, so a child crossing its own slop on that same event takes it and sends disallow. On the next move, if no child claimed and the ratio holds in the same direction, the screen intercepts (the children get ACTION_CANCEL), anchored at the DOWN plus the slop so it starts at 0 under the finger. A second pointer before the claim kills the swipe; after it, it cancels and the rest of the gesture is consumed. android.R.id.content splits touches, so a second finger on the shared bars never reaches the screen as a pointer: the bars' frame tells TabSwipe, with the same effect, and swallows its own touch after a cancel; a finger resting on a bar counts among a screen's pointers at its DOWN, so no swipe starts beside it.

| Conflict | Rule |
|---|---|
| Vertical scrollers (every screen's root list) | They take the gesture at the slop and send disallow; the dead rule backs them up. |
| Inner horizontal scrollers (Character's sort chips, the Workout plan's week and day strips, Food's recents) | They claim during the one-event wait, even at the end of their scroll, as an overflowing web row never swipes. |
| A list still flinging | It claims on the first move. |
| Text fields (the Library and Recipes search, the Log fields) | `Modifier.noTabSwipe()`, and no swipe while the keyboard shows. |
| Menus, dialogs, selection handles | Their own windows, and the focus gate. |
| Taps (switches, the week strip, the radar, the dial) | Below the slop nothing happens; a claim cancels the tap. |
| The shared bars | Above every screen: the header consumes, the bottom bar scrolls. A touch on them during a drag is a second finger: the drag springs back and no tab acts. A finger resting on them keeps a swipe from starting. |
| System Back | Starts in the edge zones are ignored; a system cancel springs back. |
| Life Director | Never a source (`swipeSource = false`, `source: false`, and the page says `on: false` there); it can be a destination. |
| TalkBack | Off under touch exploration; the bar stays the route. |
| Page overlays, sheets, onboarding, the thumbnail pass | `on: false`, and the page refuses at commit. |
| Screens moving with no finger on them | A touch that starts on them, or on the veil, is swallowed to its end. |
| The web swipe underneath | A touch nothing on an open screen takes stops at the screen. |
| Money (Cash, another Activity) | `kind: stop`, a wall. |
