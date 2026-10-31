scores: Scored 1 to 10, where higher is better on every axis (risk 10 = safest, effort 10 = cheapest).

Design 1, "Detent swipe: TabStage + NativeShell" (feel angle): feel 9, risk 4, architecture 7, effort 3. Total 23.
- Best thinking on feel: the exact curve, the pop, slop compensation, the adaptive no-overshoot settle, a click on every commit.
- Largest blast radius. Phase 0 wraps every screen in a TabFrame and hands all visibility to TabStage.
- Neighbours wait VISIBLE off-screen and need a draw watchdog.
- Later phases translate the WebView and steal touches from it mid-gesture. A stuck transform there means a blank app.
- It adds a NativeShell plugin with its own publish triggers, which the bar scene already covers.

Design 2, "Compose-level gesture + TabSwipe; the page decides" (risk angle): feel 7, risk 8, architecture 7, effort 7. Total 29. Chosen as the base.
- Moves pixels only, never ownership. No change to the view hierarchy.
- State rides on the bar scene, so it reuses ChromeHost's sequence and stale rule.
- Behind a beta switch, with walls at web edges first.
- Two flaws, both fixed by grafts. The gesture reads local coordinates from a view that is itself moving (screenX = local + translationX). Its full-screen veil at index 1 would cover the web bars during ChromeHost's 60 ms release.

Design 3, "TabDeck" (architecture angle): feel 8, risk 6, architecture 8, effort 5. Total 27.
- Cleanest model: a pure DeckModel. Also the rule that whoever draws the pixels under the finger owns the gesture, the enter slide for web to native, and a correct map correction (Life Director is a bar tab).
- It moves every screen under a new DeckFrame as a precondition. That changes z-order with the Overview shell and the shared bars, and brings no value to the owner on its own.
- canScrollHorizontally builds the semantics tree on the main thread at claim time (checked in the bytecode).
- It blocks touches for up to 1.5 s while waiting for the page's answer.

Grafts into the base:
- From D1:
  - the gesture moves to the View level, using raw coordinates and deferring one event;
  - adaptive critically damped settle spring with a velocity cap;
  - neighbours park off-screen, but INVISIBLE rather than VISIBLE;
  - ChromeHost.hold for web reveals;
  - Modifier.noTabSwipe;
  - a click on every commit;
  - tuning over the air;
  - handleOnResume checks the visible flag.
- From D3:
  - a pure SwipeModel state machine;
  - the "pixels under the finger" ownership rule;
  - enter slide and native-enter for web to native;
  - TalkBack and window-focus gates;
  - the Kotlin/page defaults parity test;
  - A34 for the new contract item.
- Rejected:
  - WebView translation, live peek and web steal (D1);
  - re-parenting into a deck, and canScroll lookups (D3);
  - the Compose Main-pass surface (D2), kept as the fallback.
plan: # Native tab swipe with a mid-swipe catch: build plan

## 1. Decision

**Build it now, at the shell level. Design 2 is the base, with grafts from the other two.**

- **The gesture lives in the open screen's own View.**
  - `ComposeView` is a final class, so a new `TabScreenView` subclasses `AbstractComposeView` instead.
  - It intercepts in raw screen coordinates. A child gets one input event to claim the gesture first.
- **TabSwipe moves screens in place.** It is a main-thread coordinator, a sibling of ChromeHost. It moves the existing sibling views of `android.R.id.content` with `translationX` only. Nothing is re-parented, so `DisposeOnDetachedFromWindow` never fires.
  - Visited native neighbours wait "parked": INVISIBLE (laid out, not drawn) at ±W. At claim time they become VISIBLE.
  - Unvisited neighbours, ends and walls are drawn by one SwipeVeil (the page colour, plus the tab's icon and label).
- **The page decides.**
  - The page projects tab order, which tabs are native, and the settings. This is a `swipe` field in every bar scene (`publishChrome`), so it inherits ChromeHost's sequence and stale rule.
  - On a committing release, the owner's plugin sends one `swipe` event. The page validates it, answers `swipeSettle`, then navigates with `lmaNavTo(to,{fast:true})`.
  - The normal A23 hand-over runs from there: leave, open, flush, close.
- **A swipe moves pixels, never ownership.** Page `open` and `close` always win.
- **Rollout.**
  - Phase 1 is native↔native with walls everywhere else, behind **Native tab swipe (beta)**, off by default.
  - Phase 2 adds the web and Overview edges.
  - No bar reorder is needed. For phase 1 the owner can group the native tabs with the existing Settings → Bottom bar editor if they like.

**Why the others lost**
- **D1** has the best feel thinking, and its numbers are grafted in. But it rebuilds visibility ownership before any value reaches the owner, and its later phases translate the WebView and steal touches from it mid-gesture. A stuck transform means a blank app.
- **D3** puts every screen under a new parent. That changes z-order with the Overview shell and the shared bars, as a precondition with no user value. Its `canScrollHorizontally` check builds Compose's semantics map on the main thread at claim time.
- **D2 as written** has two flaws:
  - It reads coordinates from the screen that is moving under the finger.
  - Its full-screen veil would hide the web bars during ChromeHost's 60 ms release.

**What I checked in the code**

| Claim | Status | Where |
|---|---|---|
| Compose tells the parent View when a child takes a drag | ✔ | `AndroidComposeView.dispatchTouchEvent` calls `parent.requestDisallowInterceptTouchEvent(true)` when movement is consumed (ui-android 1.7.6 bytecode, offsets 100–111) |
| `TabScreenView` is possible | ✔ | `AbstractComposeView` leaves `onInterceptTouchEvent`, `dispatchTouchEvent` and `requestDisallowInterceptTouchEvent` overridable; `ComposeView` is final |
| D3's scroll check costs a semantics build | ✔ | `canScrollHorizontally` → `composeAccessibilityDelegate.canScroll` → `getCurrentSemanticsNodes()` (D3's risk is real) |
| Cross-plugin call order holds | ✔ | Capacitor runs every plugin call on one "CapacitorPlugins" `HandlerThread` (Bridge.java:138, 217, 863), so `swipeSettle` reaches native before the destination's `open` |
| Life Director is a bar tab | ✔ (map was wrong) | Last tab, after Money: forge_features.py builds nav buttons for every feature unless `nav:false`; life-director/feature.json has no `nav`, apps has `nav:false` |
| Life Director has its own web swipe | ✔ | Section swipe in life-director/logic.js:4794-4806 |
| .wt/fix-swipe does not overlap | ✔ | Merged: f24a411 is an ancestor of HEAD; `HEAD..fix/swipe` is empty |
| track/integ overlaps | ✔ | Changes `ChromeHost.show` (new `opening` param), ComposeTabPlugin, NativeNutritionPlugin and 50-native-tabkit.js. **Land after integ.** |
| track/ld2 overlaps | ✔ | Touches 50-native-tabkit.js and the sim |
| track/settings overlaps | ✔ | Touches settings.html. Native Settings maps rows by key (50-native-settings.js:25,35-36), so the new row needs a key there |
| Translated screens never show the WebView | ✔ | Every screen paints an opaque `fillMaxSize` background (Character:58, Log:92, Plan:91, Recipes:94, Media:99, Food:33, Life Director:129) |
| Parking costs no drawing | ✔ | No continuous animations except Life Director's (gated on `visible`) and Cash |
| `native-cover` beats the swipe slide | ✔ | `!important` (02-layout.css:818) beats `data-swipe-dir` (851-852) |
| Only bar taps set `native-cover` | ✔ | Set in `activate()` (50-native-overview.js:174-177), not on the `{view}` action path (50-native-tabkit.js:153) |
| The swipe field can ride every scene | ✔ | `publishChrome` adds `sequence` and `navScroll` fresh on every publish, cache hit or miss (50-native-overview.js:108) |
| Native bar follows only the first scene | ✔ | LiveOverviewShell scrolls to `navScroll` only on first show (LiveOverviewShell.kt:115-124) |
| Haptic and animator APIs compile | ✔ | compileSdk 35, minSdk 26: `GESTURE_THRESHOLD_*` (API 34) compiles; `ValueAnimator.getDurationScale()` and `areAnimatorsEnabled()` exist |
| A hidden home for tuning exists | ✔ | Developer mode card (settings.html:120-124, `renderDevMode` 04-runtime.js:253) |
| Next free numbers | ✔ | Contract ends at A33, so the new item is A34; next ADR is 0011 |
| D1: single-line search fields claim horizontal drags | Not verified | Likely only when the text overflows. They get the exemption anyway |

## 2. The feel, as numbers

Notation:
- **W** = screen width in px. **u** = finger travel / W toward the side being revealed, measured from the claim point (the slop is subtracted).
- **dir** = +1 for the next tab (finger moving left).
- **o** = how far the screen has moved, as a fraction of W.

```
hold(u)    = u                                   u ≤ a          (1:1)
           = u − (1−m)(u−a)² / (2(b−a))          a < u ≤ b      (slope falls 1 → m)
           = hold(b) + m(u−b)                    u > b          (one event only, before the latch)
through(u) = min(u, 1)
latched    = latched ? u ≥ b−h : u ≥ b
on a latch flip:  r += s_old − s_new             (residual offset; keeps its velocity)
r → 0 by spring:  latch ("pop") ζ=0.65 k=1200;  unlatch ζ=1.0 k=1200
o          = clamp(s(u) + r, 0, 1)
owner.tx   = round(−dir·o·W);   neighbour.tx = owner.tx + dir·W   (whole px, so no seam)
wall/end:  o = E·(1 − e^(−κ·u/E))   (rubber band; the veil fills the gap)
```

**Defaults** (`SwipeTune.kt`; the page can override each one, and native clamps it):

| Key | Default | Clamp | Meaning |
|---|---|---|---|
| `knee` a | 0.18 | 0.05–0.35 | 1:1 follow up to here |
| `breakAt` b | 0.45 | a+0.10–0.70 | the catch point |
| `heavy` m | 0.25 | 0.05–1.0 | slope at the catch (1.0 = no tension) |
| `hyst` h | 0.06 | 0–0.15 | the catch re-engages below b−h |
| `popDamping`, `popStiffness` | 0.65, 1200 | 0.4–1.0, 400–3000 | the pop |
| `wallMax` E, `wallSlope` κ | 0.08, 0.35 | 0–0.15, 0–0.6 | ends and walls |
| `flingDp` | 1200 dp/s | 500–3000 | |
| `flingMin` | 0.12 | 0.05–0.35 | |
| `backFlingDp` | 600 dp/s | 200–2000 | |
| `commitK`, `cancelK` | 1000, 800 | 300–4000 | max k 4000; settle cap 450 ms |
| `slopFactor` | 1.0 × scaledTouchSlop | 0.75–2.0 | |
| `lockRatio` | 1.3 | 1.0–3.0 | |
| `edgeDp` | 24 | 16–48 | |
| `haptic` | `threshold` | `threshold`, `tick`, `click`, `off` | |

Not tunable: the shape of the curve, the gates in §4 and the clamps.

**What it feels like on a 412 dp-wide phone**
- The screen tracks the finger 1:1 for 74 dp.
- Then it gets heavy. Values of o: u=.25 → .243, .30 → .280, .35 → .310, .40 → .333, .45 → .349.
- At 185 dp of finger travel the screen sits at 144 dp, 42 dp behind the finger. That is the catch.
- Crossing the catch, the screen pops to the finger in about 120 ms: 6.8% overshoot (about 3 dp), settled by about 170 ms, with one haptic click.
- Pulling back below 0.39 brings the tension back (critically damped, no second click).
- For comparison, the web commits at 22% or 0.45 px/ms, with no tension in the middle.

**Release rule** (v = finger velocity toward the neighbour, from VelocityTracker over the last 100 ms, raw coordinates):
1. Wall → cancel.
2. v ≤ −backFlingDp → cancel, even when latched.
3. Latched → commit.
4. v ≥ flingDp and u ≥ flingMin (about 49 dp) → commit.
5. Otherwise cancel. A casual 600–1000 dp/s flick springs back. That is the "really swipe".

**Settle springs** (own closed-form `Spring1D`, driven by Choreographer `frameTimeNanos`):
- Commit:
  - critically damped, target o = 1;
  - v0 = finger velocity × the curve's slope (×1 when latched);
  - k = clamp((v0/|Δ|)², commitK, 4000);
  - v0 capped at √k·|Δ| (it can never cross the target), and clamped at the target as a guard.
  - From rest at o=0.5: 95% at 150 ms, visually done at about 170 ms. Snap at 450 ms.
- Cancel: the same, with cancelK, target 0. 95% at about 165 ms. It never goes below 0, so the other side never peeks.
- A refusal retargets the running spring to 0, keeping position and velocity.
- Times are divided by `ValueAnimator.getDurationScale()`; a scale of 0 means instant.
- Finger-following is direct manipulation and is never scaled.

**Haptics** (`SwipeHaptics`; `performHapticFeedback` on the owner view, **without** `FLAG_IGNORE_GLOBAL_SETTING`):

| Moment | API 34+ (S25 Ultra) | Below 34 |
|---|---|---|
| Latch (forward over the catch) | `GESTURE_THRESHOLD_ACTIVATE` | `CLOCK_TICK` |
| Unlatch | `GESTURE_THRESHOLD_DEACTIVATE` | none |
| Fling commit that never latched (at release) | `ACTIVATE` | `CLOCK_TICK` |
| Page refused | `REJECT` (30+) | none |

- Nothing plays at walls, at landing or on a plain cancel, so every commit gets exactly one click.
- Haptics follow the page's on/off and level:
  - `light` uses `CLOCK_TICK`;
  - `normal` uses the table above;
  - `strong` and `max` use `VibrationEffect.createPredefined(EFFECT_HEAVY_CLICK)` with `USAGE_TOUCH` attributes on 33+.
- The `haptic` tune key lets the owner compare kinds on One UI.

**Where tuning lives**
- Kotlin defaults: `SwipeTune.kt`.
- Page mirror: `LMA_TABSWIPE` in 50-native-swipe.js. A fixture test asserts the two are equal.
- Overrides: `lmaLoad().swipeTune`, sent as `swipe.tune`.
- The owner's controls: a **"Swipe feel (testing)"** block inside Developer mode (`#devModeTools`):
  - Tension starts at (`knee`)
  - Catch point (`breakAt`)
  - Heaviness (`heavy`)
  - Flick speed (`flingDp`)
  - Detent haptic (`haptic`)
  - Reset
- Changes take effect on the next tab open, or within 15 s on the open tab's refresh. No new APK is needed.

## 3. Architecture, files, protocol

### Z-order and who owns what

Children of `android.R.id.content`, bottom to top:

| Index | Child | Notes |
|---|---|---|
| 0 | The WebView's CoordinatorLayout | |
| 1 | SwipeVeil (new) | GONE when unused |
| … | Native `TabScreenView`s | Newest open on top |
| last | Shared chrome (ChromeHost) | Still the top child; content slides under the still bars |
| | Overview shell | Only while Overview is open |

- **Page:** decides and navigates.
- **TabSwipe:**
  - is the only writer of `translationX`, `alpha` and peek/park visibility for registered screens;
  - resets on every page `open` and `close`;
  - aborts on any foreign `open` or `close`, or on a scene whose active tab is neither the source nor the destination;
  - a watchdog fires 2.5 s after a commit;
  - a self-check runs at every return to Idle (owner at 0 and VISIBLE; parked screens = composed native neighbours, INVISIBLE at ±W; the rest GONE). A violation is repaired and recorded.
- **Plugins:** still create, accept and remove. They call TabSwipe hooks instead of setting visibility themselves.

### Phase 1 files

**New: `android-wrapper/native/src/main/java/com/lifemax/rpg/app/shell/swipe/`**
- `TabScreenView.kt`: an `AbstractComposeView` subclass. It holds the content lambda and overrides:
  - `dispatchTouchEvent`: if TabSwipe shields this view, return true; otherwise feed the arbiter, then `super`;
  - `onInterceptTouchEvent`: asks TabSwipe;
  - `requestDisallowInterceptTouchEvent(true)`: records that a child claimed the gesture, then `super`;
  - `onTouchEvent`: a floor; it returns true on DOWN when this view is the open owner, so nothing falls through to the WebView.
- `SwipeArbiter.kt` (pure): the gates and lock rules in §4.
- `TensionCurve.kt` (pure): the curve, latch and residual.
- `ReleaseRule.kt` (pure).
- `Spring1D.kt` (pure).
- `SwipeTune.kt` (pure): defaults, clamps, parse.
- `SwipeState.kt` (pure): parses `scene.swipe` and resolves neighbours.
- `SwipeModel.kt` (pure state machine):
  - states: Idle → Armed → Dragging → Settling(commit|cancel) → AwaitPage → Idle;
  - outputs a `StageScene`: per pane `tx`, visibility, shield and a11y-hidden, plus veil `tx` and label, a haptic, and an outbound swipe.
- `TabSwipe.kt`:
  - registry by view id ('character', 'meals', 'recipes', 'log', 'plan', 'media', 'life-director');
  - drives Choreographer `CALLBACK_ANIMATION`. Input only stores the finger position and posts the callback, so the output lands in the same frame as the input;
  - applies the `StageScene`;
  - parks neighbours on an idle handler after landing and on each scene;
  - registers with NativeScreens to reset on page load;
  - creates the veil on idle when the first scene arrives with `swipe.on`.
- `SwipeVeil.kt`: a small ComposeView with the `bg` colour plus `PixelIcon` and label in `muted` (core/designsystem/components/PixelIcon.kt). Not important for accessibility.
- `SwipeHaptics.kt`.

**New elsewhere**
- `core/designsystem/gesture/NoTabSwipe.kt`: `Modifier.noTabSwipe()` runs `pointerInput` at `PointerEventPass.Initial`; on DOWN it calls `LocalView.current.parent.requestDisallowInterceptTouchEvent(true)`.

**Changed**
- `bridge/web/ComposeTabPlugin.kt`:
  - `view: TabScreenView?`;
  - `createView` registers with TabSwipe;
  - `open` calls `TabSwipe.opened(tabId, screen)` in place of VISIBLE + bringToFront (lines 103-104). It adopts the running spring if this view is the awaited destination, otherwise resets. bringToFront is kept;
  - `hide` calls `TabSwipe.closed(tabId)`, which defers GONE while the view is still sliding out, then parks;
  - `remove` calls `TabSwipe.removed`;
  - new `@PluginMethod swipeSettle`;
  - the handshake capabilities get `"shell.swipe.v1"` added;
  - new `protected open val swipeSource = true`;
  - `handleOnResume` checks `visible`, not View visibility (line 147).
- `bridge/web/NativeLifeDirectorPlugin.kt`: `swipeSource = false`.
- `bridge/web/NativeNutritionPlugin.kt`: same hooks with id `'meals'`; the layer becomes a `TabScreenView`; `swipeSettle` checks the session; `capabilities()` adds `swipe:1`.
- `app/shell/ChromeHost.kt`: forwards `scene.optJSONObject("swipe")` to `TabSwipe.scene()` only when the scene is drawn (inside track/integ's `!stale` branch).
- `app/shell/LiveOverviewShell.kt`: when `swipe.active` changes, glides the bar to `navScroll` (170 ms cubic ease-out, instant with motion off) instead of first show only.
- `app/shell/TabTiming.kt`: accepts a `"swipe:<to>"` tap stamp.
- Text fields get `noTabSwipe()`:
  - feature/library/presentation/components/MediaFilters.kt:138, 217;
  - feature/recipes/presentation/components/RecipesFilterCard.kt:100, 157;
  - feature/log/presentation/components/LogDetailsCard.kt:56.
- `android-wrapper/native/NativeApi.java`: VALUE = main's value + 1 **at merge** (29 if nothing else lands first).

**Web**
- New `lifemax_modular_v73/src/runtime/segments/50-native-swipe.js`, included after 50-native-nutrition in index.template.html. It contains `LMA_TABSWIPE`, `NativeSwipe.state()`, `kind()`, `request()`, `sync()`, `lmaSetNativeSwipe` and the dev tuning handlers.
- 50-native-overview.js:
  - `publishChrome` adds `swipe: window.NativeSwipe?.state()`;
  - NativeTabs gains `wouldOpen(view)` (an optional `register` hook, falling back to `covered`), `webBody(view)`, `stop(view,fn)`, `stopped(view)` and `isOpen(view)`;
  - Overview registers `body:'web'`.
- 50-native-tabkit.js:
  - adds `plugin.addListener('swipe', e => NativeSwipe.request(cfg.id, plugin, e))` in the `if(!ready)` block (line 152);
  - registers `wouldOpen: () => enabled() && state.onboardingComplete && !lmaTopOverlay() && !noscroll && !cfg.blocked?.()`, which is `blocked()` without the `lmaView` term.
- 50-native-nutrition.js: the same listener (with session) and `wouldOpen`.
- 50-native-cash.js: `NativeTabs.stop('money', () => !!plugin)`.
- views/settings.html:
  - in `#lmaNavCard`, after "Swipe to switch tabs": a row `#nativeSwipeSetting` with **Native tab swipe (beta)**, input `#nativeSwipeToggle`, hint "Swipe between native tabs. The page holds back until you pull past the halfway catch. Needs Swipe to switch tabs.", stored in `lmaLoad().nativeSwipe`, **default off**, disabled until a plugin reports `shell.swipe.v1`;
  - in `#devModeTools`: the Swipe feel (testing) block.
- After track/settings merges, add `["nativeSwipe", "#nativeSwipeToggle", PREFS]` to 50-native-settings.js.
- Regenerate module-map.json and symbol-index.json.

**Docs**
- CONTRACT:
  - new **A34 Tab swipe**: gates, curve keys, protocol, "page decides";
  - **A23 item 11**: a swipe ends only in the page's `lmaNavTo` and the hand-over; it answers before it navigates; watchdog;
  - **A24 park/peek clause**: a hidden screen may be INVISIBLE at ±W, or VISIBLE only while dragging or settling, with `visible=false`, no updates, no bars and no focus;
  - **A22 note**: `scene.swipe` is the seed of ShellState.tabs.
- ADR 0011 (native tab swipe: native owns the motion, the page owns the decision).
- NATIVE_TAB_GUIDE: Swipe section (a new tab gets it free; mark drag regions with `noTabSwipe`; opt out like Life Director).
- BRIDGE_CATALOG: the `swipe` event, the `swipeSettle` method and the capability, per plugin.
- MIGRATION ledger; BACKLOG for phases 2 to 4.

### Protocol (v1)

**Page → native.** The field rides inside every `navScene` (kit) and `chromeScene` (Food):

```json
"swipe": {"v":1, "on":true, "motion":true, "haptics":"normal",
  "view":"character", "active":"character",
  "tabs":[{"key":"dashboard","view":"dashboard","label":"Overview","icon":"grid","kind":"web"},
          {"key":"character","view":"character","label":"Character","icon":"person","kind":"native"},
          {"key":"food","view":"meals","label":"Food","icon":"heart","kind":"native"},
          {"key":"pet","view":"pet","label":"Cat","icon":"paw","kind":"web"}, …,
          {"key":"money","view":"money","label":"Money","icon":"coin","kind":"stop"},
          {"key":"life-director","view":"life-director","label":"Life Director","icon":"dial","kind":"native","source":false}],
  "colors":{"bg":ARGB,"text":ARGB,"muted":ARGB}, "tune":null}
```

Field rules:
- `on`: `nativeSwipe===true && lmaSwipeEnabled() && onboardingComplete && !lmaThumbWarmBusy && !lmaTopOverlay() && !body.noscroll && lmaView()!=='life-director'`.
- `motion`: `!lmaMotionOff()`.
- `haptics`: `fxOpt().haptics ? hapLevelKey() : 'off'`.
- `active`: `lmaTabKey(lmaView())` if it is on the bar, else `""`.
- `tabs`: built from `lmaTabList()`, so reorders and hidden tabs are reflected. `view` comes from `lmaResolve(key)`.
- `kind`:
  - `stop` if `NativeTabs.stopped`;
  - `native` if `has && !webBody && covered && wouldOpen`;
  - otherwise `web`.

**Native → page.** Event `swipe` on the owner's plugin, sent only when a release commits:

`{token:"<view>#<n>", from:<owner view>, to:<tab key>, dir:1|-1, sequence:<scene seq used>, via:"drag"|"fling"}`

NativeNutrition adds `session`. At the same moment, `TabTiming.tap("swipe:"+to)` is stamped.

**Page → native.** `swipeSettle({token, outcome:"native"|"refused", reason?})` on the same plugin (Food adds `session`). It is called synchronously in the listener, **before** navigating. Phase 2 adds `"web"`.

**Page handler `NativeSwipe.request(src, plugin, e)`**

Refuse if any of these holds:
- reason `off`: the swipe is not on;
- reason `overlay`: an overlay, `noscroll`, or a lock cover;
- reason `stale`:
  - `lmaView()!==src`, or `e.from!==src`, or `!NativeTabs.isOpen(src)`;
  - the current index `i` is below 0, or `tabs[i+e.dir]?.dataset.view!==e.to`;
- reason `kind`: `kind(lmaResolve(e.to))` is not `native` (phase 1).

Otherwise:
1. `swipeSettle({outcome:'native'})`;
2. toggle `html.native-cover` on;
3. `delete html.dataset.swipeDir` (`lmaSwipeNav` stays false);
4. `lmaNavTo(e.to,{fast:true})`: no View Transition, recorded in `lmaNavStack`, every wrapper runs;
5. `lmaSyncActive()`.

Everything is wrapped in try/catch, which answers `refused`.

**Native outcome handling**
- A token that does not match → ignored.
- `refused` → spring back, `REJECT` haptic.
- `native` → wait.
  - The destination's `open` adopts the running spring (no visual re-show; it takes the bars and the new lit tab).
  - The source's `close` is deferred until the source is off screen.
- The source closes without the destination opening (it fell back to web) → hide the peek. Phase 1 cuts; phase 2 swaps to the veil and fades.

**Capability and versions**
- `"shell.swipe.v1"` in `ComposeTabPlugin.handshake`; `swipe:1` in `NativeNutrition.capabilities`.
- An old APK ignores `scene.swipe`, and its settings row stays disabled.
- An old page sends no field, so nothing ever claims.
- `webMinApi` does not need to rise.

## 4. Gesture arbitration, one rule per conflict

**Gates at DOWN** (all must hold, otherwise the view stays passive for the whole gesture):
- the latest accepted scene has `swipe.v==1` and `swipe.on`;
- this view is the TabSwipe owner, is open (`visible`), has `swipeSource`, and `swipe.view` equals its id;
- TabSwipe is Idle;
- exactly one pointer, finger or stylus (mouse ignored);
- the IME is hidden (`isVisible(ime())`);
- touch exploration is off;
- `hasWindowFocus()`;
- `rawX` is outside `max(systemGestures().left/right, edgeDp)` of both edges.

**Lock** (dx, dy are raw values measured from the DOWN; slop = `scaledTouchSlop`):
- **Dead** if |dy| ≥ slop and |dx| < 1.3|dy|.
- **Candidate** if |dx| ≥ slop and |dx| ≥ 1.3|dy|. The view does **not** intercept on this event, so a child that crosses its own slop on this same event consumes it and sends disallow.
- On the **next** MOVE, if no child claimed and the ratio still holds, `onInterceptTouchEvent` returns true. The children get `ACTION_CANCEL`. The anchor is DOWN + sign·slop, so the screen starts at 0.

| Conflict | Rule |
|---|---|
| Vertical root scrollers (LazyColumn: Character, Plan, Recipes, Media; verticalScroll: Log, Food) | They claim at \|dy\| ≥ slop and send disallow, which kills the swipe. The dead rule backs this up. |
| Inner horizontal scrollers: Character sort chips (CharacterAttributesCard.kt:39-45), Plan week/day LazyRows (PlanCalendarCard.kt:84-108), Food recents (FoodLogPane.kt:268-270) | Claimed during the one-event deferral, so the child wins, even at the end of its scroll (overscroll still consumes). Web parity: overflowing overflow-x never swipes. |
| A list that is still flinging | Compose's `startDragImmediately` claims on the first move, so no swipe until it stops. |
| Text fields (Library and Recipes search, Log notes) | `Modifier.noTabSwipe()`; and no swipe while the IME shows. Parity with the web's INPUT/TEXTAREA rule. |
| DropdownMenus, dialogs, selection handles | Separate windows, plus the window-focus gate. |
| Tap-only controls (PaneSwitch, LibrarySwitch, FoodPaneSwitcher, Food week strip, radar, Life Director dial taps) | Below slop nothing happens. A claim sends `ACTION_CANCEL`, so no tap fires. |
| Shared bars | The header consumes; the bottom bar's HorizontalScrollView scrolls. Both sit above the screens. Scrubbing stays web-only. |
| System Back edges (gesture navigation) | Starts inside max(inset, 24 dp) are ignored. No exclusion rects. A system `ACTION_CANCEL` (pilfer) springs back. Back stays `lmaBack`. |
| Second pointer | Before the claim: dead. After: cancel and spring back, consuming until every pointer is up. |
| Life Director (a bar tab after Money; web section swipe; .wt/ld2 dial handles) | Never a source: `swipeSource=false` and the page sets `on:false` there. It can be a destination. |
| TalkBack | Off under touch exploration. Bar hits stay the route. |
| Page overlays, sheets, onboarding, thumbnail warm pass | `on:false`, and the page refuses at commit. |
| Make-before-break overlap, or a screen not yet open | Owner and `swipe.view` gates. |
| Screens moving during a settle or the page wait | Shielded: their `dispatchTouchEvent` returns true until Idle. Phase 1 has no catch. |
| Web swipe double-firing | The floor in `TabScreenView.onTouchEvent` stops any native-screen touch reaching the WebView. |
| Cash (another Activity) | `kind:"stop"` is a wall. Its SwipeToDismissBox, chip bar and tilt are untouched. |

## 5. The web edge and the Overview edge

**Principle:** whoever draws the pixels under the finger at DOWN owns the gesture.
- The WebView is never translated.
- The page never renders a neighbour ahead of time. It can only show a view by making it current, which runs hooks, history, `petOpen` and the WebGL table.

**Phase 1:** web, Overview, stop and end neighbours are walls (rubber band over the label-less veil).

**Phase 2, native → web or Overview** (Food→Cat, Log→Cat/Quests, Plan→Quests, Character→Overview, Library on the Journal pane):
1. During the drag, the labelled veil (destination icon and label on `bg`) slides in as the neighbour. The tension and release rule are unchanged.
2. On commit:
   - the page answers `web`, adds `html.swipe-reveal` (CSS `html.swipe-reveal .view.active>*{animation:none!important}`, cleared on the next normal navigate or after 800 ms), and navigates fast;
   - the source closes at once (A23 item 2); TabSwipe defers its GONE until it is off screen;
   - TabSwipe calls `ChromeHost.hold(token)`, so the 60 ms release waits. Without this, the veil would hide the web bars and they would flicker. `closeAll()` and `closeNow()` still win.
3. Once the spring has landed and the answer is in, TabSwipe calls `webView.postVisualStateCallback`. When it fires (cap 400 ms), the veil fades over 120 ms × scale, then `unhold`. The web bars underneath are the same drawing.
4. For Overview: its `open` calls `ChromeHost.closeAll()` and its shell bars take over. The veil sits under the shell's transparent middle and fades to reveal the live dashboard. Character's close stays parked until Overview opens, as today.

A `native` answer whose destination then falls back to web (Plan editing, Library player, Life Director not on Today) uses the same veil swap and fade.

**Phase 2, web → native and Overview body → native.** The web swipe in 34-navigation keeps the drag. In `lmaSwipeGo` (34-navigation.js:168), when `NativeSwipe.kind(v)==='native'` and the capability is present:
- set `native-cover` and a new `html.native-enter` (`.view.active>*{visibility:hidden}`), so the static web copy never flashes;
- do not set `data-swipe-dir`;
- record the direction and release velocity.

The kit and Nutrition pass `open({…, enter:"next"|"prev", velocity})`. `TabSwipe.opened` places the screen at ±W and runs the commit spring to 0. `native-enter` is cleared when `open` resolves, or after 800 ms.

**Web ↔ web** stays the 34-navigation swipe until phase 3 ports the same curve.

## 6. Phases

**Phase 1 (shippable alone; beta switch off by default): native ↔ native with the catch.**
- Everything in §3, §4 and §2.
- Unvisited native neighbour:
  - the labelled veil slides in and commit is allowed;
  - the page opens the screen cold;
  - `TabSwipe.opened` keeps the veil until the new view's first `OnPreDraw`, then swaps the view in at the veil's offset in one frame.
- Walls everywhere else. Bar glide fix.
- Owner gets: a smooth swipe with tension, a click at the catch and spring settles between Character↔Food and Workout plan↔Library in the default order. Optionally reorder (Settings → Bottom bar) to Character, Food, Log, Workout plan, Library to swipe along five native tabs. Tuning happens on the phone through Developer mode.
- Land after track/integ merges. Rebase onto ld2 and settings.

**Phase 2: every edge.**
- §5 in full: veil reveal with `postVisualStateCallback`; `ChromeHost.hold`/`unhold`; fallback swap; `enter` slide-in for web → native and Overview → native; `swipe-reveal` and `native-enter` CSS (02-layout.css).
- Owner gets: every tab boundary on the bar swipes, except Money.

**Phase 3: polish and parity.**
- `prime({snapshot})` behind capability `"shell.prime.v1"`: the kit composes never-opened native neighbours at idle (no touch for 800 ms, one at a time). Needs an A24 amendment and memory measured per screen in BRIDGE_CATALOG. Removes the cold-open hitch.
- Chained swipes: catch during a settle through the inverse curve.
- The web swipe uses the same curve, release rule and `lmaBuzz('tick')` at the catch behind the beta flag (34-navigation `lmaPgSet` and `lmaSwipeEnd`).
- `setAccessibilityPaneTitle` on open.
- Owner gets: one feel everywhere and no first-visit stall.

**Phase 4 (each its own decision):**
- Money: stay a stop, launch Cash on commit with `ActivityOptions`, or move Cash into the main window.
- A22 ShellState replaces `scene.swipe`, with bars drawn from state and a lit tab that follows the drag.
- Food onto ComposeTabPlugin.
- PageThumb bitmaps in the veil.

## 7. Tests

**Phase 1, Kotlin (plain JUnit in `src/test/java/com/lifemax/rpg/app/shell/swipe/`)**
- `TensionCurveTest`:
  - golden values .10/.25/.30/.35/.40/.45 → .100/.243/.280/.310/.333/.349 (±1e-3);
  - slope 1 at the knee and m at the break; monotonic for 200 random clamped tunes;
  - latch at b, unlatch below b−h, no chatter with ±0.01 jitter;
  - the residual keeps o continuous at every flip;
  - the wall stays below E with slope κ at 0.
- `ReleaseRuleTest`: a table of wall / latched / back-fling / fling with too little travel / slow.
- `Spring1DTest`:
  - commit never exceeds 1 for v0 up to 20 000 px/s;
  - cancel never goes below 0;
  - 95% within 120–200 ms;
  - scale 0 lands at once; scale 2 doubles the time;
  - a retarget keeps position and velocity continuous.
- `SwipeArbiterTest`:
  - edge zones with gesture insets 0, 24 and 48 dp;
  - lock exactly one event after the candidate event;
  - a child claim in the candidate event yields;
  - dead-first vertical;
  - multi-touch before and after the claim;
  - IME, focus, touch exploration, `source:false`, `swipe.view` mismatch, not Idle;
  - slop compensation starts at 0.
- `SwipeStateTest`: neighbours for the default bar, reorder, hidden tab, ends, food→recipes, library→history (web), stop, unvisited → veil, missing or stale state → null.
- `SwipeModelTest` (fake panes and clock), with the Idle invariant checked after every random sequence:
  - commit → `native` → adopt → deferred close;
  - refused; stale token; source closed before the destination;
  - watchdog; a foreign open mid-drag aborts; `on:false` mid-drag;
  - `drop()` mid-settle;
  - cold veil swap;
  - parking set after landing.
- `SwipeTuneTest`: clamps, and parity with the page defaults (fixture written by tools/gen_native_fixtures.js).
- `NativeScreensTest` gains the TabSwipe host.

**Phase 1, Paparazzi (A32)**
- `SwipeVeilScreenshotTest`: dark, light, font scale 2.0, label-less filler.
- **Every existing reference unchanged**, which proves the `TabScreenView` swap is invisible.

**Phase 1, web**
- New `tests/native-swipe.test.js` (Playwright, fake plugins):
  - the scene's `swipe` field: tab order, reorder and hide, foodPane and libPane resolution, kinds from the registry, `on` / `motion` / `haptics` flags, Life Director `source:false`;
  - every refusal reason;
  - on acceptance:
    - `swipeSettle` is issued before the destination's `open`;
    - no `startViewTransition`, no `data-vt`, no `data-swipe-dir`;
    - `native-cover` is set;
    - `lmaNavStack` grows;
    - exactly one lit tab.
- `native-handover-sim.test.js` (A28):
  - fakes gain `swipeSettle` and keep their last scene;
  - new hops `swipe-next` and `swipe-prev` emit from that possibly stale scene;
  - new faults:
    - settle issued after the destination's `open`;
    - an accepted `native` whose view never shows;
    - an accepted swipe while `on:false`;
    - zero screens once settled;
    - the existing "tapped screen closed early" fault, applied to swipes too;
  - show the sim failing on two mutations: answering after `lmaNavTo`, and dropping the `to` check.
- `swipe.test.js`, `tabs.test.js` and `native-handover.test.js` stay unchanged.

**Phase 2 tests**
- Web:
  - a `web` outcome adds `swipe-reveal` and no `viewIn` or slide animation runs (`getAnimations`);
  - a blocked native destination answers `web`;
  - `lmaSwipeGo` into a native destination passes `enter` and clears `native-enter`.
- Sim: `pet` and `quests` destinations (WANT = none showing).
- Kotlin: veil-fade path, chrome hold/unhold, `enter` placement.

**Phase 3 tests:** `swipe.test.js` assertions under the flag; prime faults in the sim (primed screen visible, chrome owner, or updated); catch tests in `SwipeModelTest`.

**Phone checks for the owner's checklist (S25 Ultra, release build)**
1. The catch feels physical. Tune knee, catch point and heaviness in Developer mode, and report the numbers.
2. The click at the catch at each haptic level, with system Touch feedback on and off. Try the `tick` and `click` alternatives.
3. Back from both edges at the default and high Back sensitivity. Swipes starting about 30 dp inboard work.
4. Diagonal swipes on Character, Plan, Media and Food lists: a scroll never turns into a tab switch, and a firm horizontal one always does.
5. Character sort chips, the Plan week/day strips and Food recents scroll without switching tabs.
6. Search fields and Log notes never switch tabs; nothing switches with the keyboard up.
7. 120 Hz smoothness. There is no hairline seam between the two screens.
8. First swipe to a never-opened tab: the veil, then the screen. Note any stall.
9. After several swipes the native bar keeps the lit tab visible.
10. Show tab timing gives a swipe line (release → second frame of the new screen).
11. Remove animations: switches are instant. Note whether the web side also turns its motion off.
12. 50 random swipes, taps and Back presses: never two screens, never a blank screen.
13. Phase 2: no web-bar flicker on reveal; Overview arrives cleanly; a web → native slide-in has no flash.

## 8. Risks and mitigations

- **The diagonal cone against vertical lists can only be confirmed on the phone.** Mitigation: `lockRatio` and `slopFactor` are tunable over the air. The one-event deferral costs about 8 ms, and slop compensation hides the distance.
- **A transform left behind means a half-blank screen.** Mitigations:
  - TabSwipe is the single writer;
  - resets on every page `open` and `close`, on NativeScreens drop and from the 2.5 s watchdog;
  - the Idle self-check repairs and records;
  - SwipeModel invariants are checked after random sequences;
  - the WebView is never translated.
- **The hand-over gets more complex (A23).** Mitigations: the answer comes before navigation; the page validates `from` and `to`; any foreign `open` or `close` aborts; deferred closes; the sim proves it with mutations (A28).
- **Cold destination composes during the settle and the spring stalls once per tab per session.** Mitigation: the veil hides the empty frame; phase 3 `prime()` fixes it.
- **Stale peek content.** A parked neighbour shows its last projection, then recomposes once on `open`. Mitigation: A24 allows this; prime keeps it fresh later.
- **INVISIBLE parking cost.** A neighbour is laid out after each landing (on an idle handler) and on parent relayouts. Unchanged MATCH_PARENT views skip measuring, but this needs checking on the phone. Fallback: park only at an eligible DOWN.
- **Compose upgrade.** The arbitration relies on `requestDisallowInterceptTouchEvent` from `AndroidComposeView`, checked in 1.7.6. Mitigation: re-check on every BOM bump, using `SwipeArbiterTest` plus the phone checklist. The fallback is D2's Compose Main-pass surface.
- **Haptics on One UI.** `GESTURE_THRESHOLD_*` may feel weak, and plays nothing when touch feedback is off (by design). Mitigation: the `haptic` tune key.
- **Two feels until phase 3** (web 1:1 and 22%, native catch at 45%). Mitigation: the phase 3 port behind the same flag.
- **Walls in the middle of the bar in phase 1 may read as broken.** Mitigation: the beta label, the reorder tip, and phase 2 right behind.
- **Food is a second copy of the hooks and can drift.** Mitigation: about 30 lines, shared TabSwipe code, the sim covers Food; Food moves onto ComposeTabPlugin in phase 4.
- **Merge conflicts** with track/integ (ChromeHost.show `opening`, ComposeTabPlugin, NativeNutrition, tabkit), ld2 (tabkit, sim) and settings (settings.html, the native settings key list). Mitigation: land after integ, rebase, and set NativeApi at merge time.
- **Version mismatch either way.** Mitigation: capability gates, and a scene field that older builds ignore. CI enforces the NativeApi bump, ktfmt and the 600-line rule; split TabSwipe if it grows.
