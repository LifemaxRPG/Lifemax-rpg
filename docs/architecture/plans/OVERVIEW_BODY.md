# Native Overview cards (the Overview body): build plan

## 1. Decision

**Build it as a NativeTabKit tab on the `dashboard` view, behind a new "Native Overview cards (beta)" switch that is off by default. The live hybrid stays the default until A33 is met.**

**What becomes native.** The head and every card the page shows, in the page's own order:
- the head (date, greeting, Edit, class crest);
- the feature tip;
- Today's plan (Life Director's glance dial);
- Next moves; Level & today (the ring); Rewards; App shortcuts; Activity today (health); Attributes; This week; Cat; Daily luck; Recent activity;
- the page's toast while it shows;
- an XP celebration that starts while the cards show (Native Log's held orbs, the XP of a health sync). The page hands it to the screen, which draws it as Workout plan already does, and the page plays the sound and the buzz.

The Overview editor goes native in phase 2. In phase 1, Edit hands over to the page's own editor.

**What stays on the page, and how the screen reaches it.** Every one of these is delegated:
- the live cat room (the Cat card becomes a door to it);
- the Daily Luck overlay;
- the attribute sheet;
- the Rewards shop;
- the Health page;
- the app picker and the Apps tab;
- the Quests and Journal destinations;
- Life Director's sheets;
- level-ups, and an XP celebration that starts while the cards are not showing (the classic Log form's, for example). The cards step aside for these and come back after;
- the wide Overview above 860 CSS px (landscape on the owner's phone), as the live hybrid does today.

The screen reaches a page overlay through a **command**. The command runs while the screen still covers the page. The overlay then makes the kit step aside at once (the body watcher in 50-native-tabkit.js:224-225), and `watch()` brings the screen back when the page is clear again. This is the pattern Library and Workout plan use. Destinations are `{view}` actions, so the A23 hand-over closes the screen after a native destination has opened, or at once for a web one.

**One owner for the `dashboard` view.** 50-native-overview.js stays the view's owner. Its NativeTabs registration becomes a composite that asks the cards first, then the live shell:
- The cards tab is created with a new kit option, `managed: true`. It skips the kit's own `cover`/`register` and its open-on-navigate, but still closes itself when the page leaves the view.
- When the cards are wanted, the hybrid treats itself as blocked.
- When the cards cannot open or update (a missing capability, a decoder rejection), they give up for the session and the live shell takes the view (A19: a safe fallback, not a hang). The kit reports the failure through a new `cfg.onFail`.
- Each drawing closes the other once it has opened, so two drawings are never left open behind one registry entry (`heal()` cannot see that case).
- With the switch off, or on an APK without the plugin, nothing changes.

**The shell.** The cards are a ComposeTabPlugin screen, so the Overview uses ChromeHost's shared bars like every other native tab:
- the header is fixed, natively hit-tested and readable by TalkBack;
- the bars instance is kept across tab switches.

LiveOverviewShell(chromeOnly=false), OverviewBars and NativeOverviewPlugin serve only the hybrid. NativeOverviewPlugin is untouched and still rejects any `body`.

**Tab swipe.** With Native tab swipe (beta) also on, the Overview with cards is a native swipe neighbour from phase 1, both ways. With it off (its default), the Overview has no tab swipe while the cards are on: the web swipe needs the live body under the finger. The toggle hint, the MIGRATION row and §6 say so, and the owner decides whether that is acceptable (open decision 5).

**One decision, two drawings.** Phase 0 splits each card's renderer on the page into a data function and an HTML function that draws from it:
- Classic stays byte-identical, and a test proves it.
- The projection is built from the same data functions, so the screen copies no rule.
- The projection takes its order from `ovLayout()`, never from the DOM.

**Why**
- It is the most-seen screen, and its bars are the one set TalkBack cannot reach. The hybrid's frame hides its native strips (NO_HIDE_DESCENDANTS, LiveOverviewShell.kt:28-33), so TalkBack reads the web bars that sit masked at opacity 0 underneath (50-native-overview.js:106). The web body beside the frame is readable today. With the cards, the bars become ChromeHost's accessible shared chrome, and the body moves from DOM semantics to Compose semantics, which closes the web's own gaps: hero, Cat, Rewards and Health are clickable divs with no role (41-ov-apps.js:33-39, 39-health.js:141, 44-rewards.js:136), and the week day is a one-of-seven choice with no group.
- Every arrival rebuilds the bars from scratch. `NativeOverviewPlugin.open` calls `ChromeHost.closeAll()` and builds a new LiveOverviewShell with two software-layer strips each time (NativeOverviewPlugin.kt:43-56).
- With the cards on, the Overview becomes a warm, kept-composed switch like the other native tabs, and with native swipe on, a native swipe neighbour.

**Alternatives rejected**
- Putting a body back into NativeOverviewPlugin's scene. This is the DOM-capture approach the retired Canvas Overview used; A17 forbids it, and the plugin rejects `body` on purpose.
- Punching a hole in the screen to show the live cat canvas. The ComposeTabPlugin screen is opaque and full-screen, and the WebView would have to scroll in step with a LazyColumn. Too fragile.
- Drawing the cat room natively. ADR 0003 keeps the pet room web.
- A periodic still frame of the peek canvas. That is a page snapshot standing in for live UI (A17), and it needs the pet loop running unseen.
- Opening the live hybrid while a celebration plays and swapping to the cards after it (the review's option A for Log saves). It needs "Native Overview" on (the hybrid's `blocked()` starts with `!enabled()`, 50-native-overview.js:80), so with that off the web bars would show again; it rebuilds the hybrid's shell on every save (`ChromeHost.closeAll()`, NativeOverviewPlugin.kt:45); and it swaps the body from web to native two seconds later, the swap the rest of this plan avoids. The `fxSink` hand-off (3.5) draws the orbs on the cards instead.
- Keeping the Overview a swipe wall (`body:'web'`) until phase 4. Every ComposeTabPlugin screen is a TabScreenView registered with TabSwipe (.wt/swipe ComposeTabPlugin.kt:196-209), and a swipe may start from any source tab whatever its own kind (.wt/swipe SwipeState.kt:103-113). The cards could then swipe to Character while Character's swipe back met a wall.

### What I checked in the code

| Claim | Status | Where |
|---|---|---|
| Fresh-load tile order is wrong until the first save; `ovLayoutApply()` alone fixes it | ✔ probed | before: `rewards,apps,health,plan,next,hero,…`, after `ovLayoutApply()`: `plan,next,hero,rewards,apps,health,…` (/tmp/ovplan/probe1.js). Cause: 31-overview-edit.js:149 and 18-runtime.js:73 run before the splices in 39-health.js:157-164, 41-ov-apps.js:24-31 and 44-rewards.js:138-141 |
| Every runtime segment and every forged feature script is an inline script, so the splices are done before the page finishes parsing; an `ovLayoutApply()` after each splice fixes the order without waiting for any event | ✔ | index.template.html:132 (one `<script>` of segments); forge_features.py:45-46 (feature scripts inline before `</body>`) |
| `LifeDirector.native.today(0)` works with Life Director never mounted; `act()` returns false then | ✔ probed | 8 arcs, `act → false`, `showing() → null` (probe1) |
| After `navigate('life-director')` plus `LifeMaxFeatures.open('life-director')`, `native.act('inst', id)` opens the session sheet at once, with no 30 ms twin click needed | ✔ probed | `acted: true`, `showing: "sheet"` on the director-day fixture at 2026-10-28T12:00Z (/tmp/ovplan/probe2.js) |
| The dashboard feature tip also appears **at boot**, not only on navigate and finishTutorial | ✔ (corrects the map) | 13-runtime.js:115 (`setTimeout(maybeShowFeatureTip…,400)`); probe1 found it present |
| A kit tab keyed `dashboard` would double-open and break make-before-break | ✔ by reading | Both navigate wrappers queue an open on `dashboard` (50-native-overview.js:150; 50-native-tabkit.js:236-237). Each open that declines calls `NativeTabs.flush` (50-native-overview.js:141; 50-native-tabkit.js:151-152), which would close the previous native tab before the other drawing opens. Hence `managed: true` and the composite |
| Adding `#ovEdit` to `lmaTopOverlay()` would close the default hybrid's bars for the whole edit and leave them closed after Done | ✔ by reading | the hybrid's `blocked()` counts `lmaTopOverlay()` (50-native-overview.js:80); its body observer watches class changes in the subtree (:166) and `ovEditOpen` adds `.ov-editing` (31-overview-edit.js:57); `closed` reopens only if unblocked at that moment (:135); nothing reopens on `ovEditClose` (31-overview-edit.js:64-72). Hence the `lmaBack` fix instead |
| Pushing `fxUntil` alone never releases `afterCelebrations` when Log's held orbs play on time; adding `setTimeout(fxSettle, ms)` does | ✔ probed | `fxSettle` runs only when called (07-runtime.js:81-82): by celebrateXP 1550 ms after it starts (:86) and by a level-up's close (:103). Without: never fired, 2 of 2 (/tmp/ovfinal/fx2.js; the review's /tmp/ovattack/fx.js, 3 of 3). With: fired at 2051-2055 ms, 3 of 3. This plan needs neither: `fxSink` (3.5) |
| Native Log plays its held XP celebration **on the page** 450 ms after going to the Overview; its level-up is not held and comes 950 ms after the save | ✔ | 50-native-log.js:11, 93-100; 05-runtime.js:11-12 |
| The kit's open returns a bare `false` whatever the cause, and `revisit()` asks only `enabled()` | ✔ | 50-native-tabkit.js:151-153, 178, 195-198, 205-208; 216-218. Hence `cfg.onFail` |
| TalkBack reads the hybrid's web body; its frame hides only the native strips, and the masked web bars stay in the tree | ✔ | LiveOverviewShell.kt:28-33 (the WebView is a sibling in `android.R.id.content`); 50-native-overview.js:106 (opacity 0, not hidden) |
| The voice HUD is a body child whose `on` class is set only by `lmvHudOpen`; it is created 2.5 s after boot or when voice is switched on; no native tab steps aside for it | ✔ (corrects the design and the review) | 45-voice.js:478-479, 490, 589, 615; 50-native-tabkit.js:224-225; no `lmv-hud` in any 50-native-*.js |
| A deep schedule link relies on the Overview "leaving the page showing" | ✔ | 50-deeplink.js:99-101 |
| Only the hybrid stops the thumbnail warm pass; the pass starts 9 s after boot and navigates through every view | ✔ | 50-native-overview.js:163; 42-scrubnav.js:52-73, 216 |
| The pet loop keeps running while view-dashboard is active and `#petPeekCanvas` exists | ✔ | 17-runtime.js:74, re-checked every frame at 17-runtime.js:77 |
| A purchased accent is applied 800 ms after boot; the only other caller is a buy inside the shop | ✔ | 44-rewards.js:68-81, 149 |
| A health sync redraws the Overview through `save()`; only an app icon's arrival redraws outside `renderOverview` | ✔ | 39-health.js:104 → 04-runtime.js:44 → 05-runtime.js:30; 41-ov-apps.js:22 |
| Only the attribute sheet is translucent; the shop, Health, the app picker and Luck are opaque | ✔ | 02-layout.css:807 (`rgba(4,6,12,.74)`); 07-finishing.css:45 and features/apps/style.css:13 (`var(--bg)`); 03-features.css:304 (`#000`) |
| `haptic()` is `navigator.vibrate`, scaled by level through NavHaptics | ✔ | 07-runtime.js:45; 50-haptics.js:5-30 (`dm`, `add`, `min`, cap 260, amplitude) |
| A native screen may hold Back while visible: precedent in ld2 | ✔ | .wt/ld2 LifeDirectorScreen.kt:104 `BackHandler(enabled = visible && …)` |
| The read-only ChronoDial (handles and build optional) exists in ld2; **no `DialIntro` exists**; main's dial has no intro animation | ✔ (corrects the design) | .wt/ld2 …/dial/ChronoDial.kt:62-73 (`hold`, `asking`, `handles`, `run`); BuildPlayer and BuildTimeline are the build animation; the web's `ld-dial-in` is life-director/style.css:399-401 |
| Character already projects the avatar data URL and decodes it with a 2,000,000 cap | ✔ | CharacterHeroCard.kt:38-50 |
| App icons are 84 px PNGs | ✔ | AppLauncherPlugin.java:32 |
| A native celebration layer exists (Workout plan); the page plays the cue 120 ms after the reply | ✔ | feature/plan/presentation/components/PlanFeedbackLayer.kt (335 lines); `Celebration` and `PagePoint` in feature/plan/domain; 50-native-plan.js:17, 171-175, 222-224 |
| Swipe's `webBody()` reads a `body` property, so a getter works; swipe keeps `coverable()` private and adds `wouldOpen` to the kit's registration; the kit's return value is unchanged | ✔ (corrects the design) | .wt/swipe 50-native-overview.js (`webBody(view){return tabs.get(view)?.body==='web'}`); .wt/swipe 50-native-tabkit.js:72-75, 234, 268 |
| Native swipe is its own beta, off by default; the web swipe runs on the live body and is on by default | ✔ | .wt/swipe 50-native-swipe.js:40-41; 34-navigation.js:95, 99-106, 138-198 |
| No orientation lock; above 860 CSS px the page's sidebar is a side rail; only the hybrid has a width rule | ✔ | AndroidManifest.xml:32 (configChanges only); 02-layout.css:325-342; 50-native-overview.js:80 |
| Native Settings draws the Appearance rows from the DOM, and its four fixtures carry them | ✔ | .wt/settings 50-native-settings.js:1-10, 23-40; settings-{default,bar,light,many}.json contain "Native Library (beta)"; native-settings.test.js:62, 120, 245 |
| `ovMoves`' name is split across two segment files; native-overview.browser.js runs a text slice of 17+18 with stubs | ✔ | 17-runtime.js ends `function ovMo`, 18-runtime.js:1 begins `ves(){`; native-overview.browser.js:5-6 |
| `writeSave` already alerts once for a read-only save; `save()` toasts only when the save is not read-only | ✔ | 04-runtime.js:41, 44 |
| ADR 0006 says a new native tab needs no edit in 50-native-overview.js | ✔ | decisions/0006-handover-registry-and-safety-net.md:19; BACKLOG.md:9 (NAV-6) |
| track/integ is merged; main is at Native API 29; ADR 0011 is committed on track/swipe, so the next is 0012 | ✔ | `git merge-base --is-ancestor`; NativeApi.java:10; .wt/swipe f9a4b0d |
| The cat's sprite frame matches its DNA, morph and HD look | **Not verified** | rt.sheets keyed by coat (15-runtime.js:188), by DNA key (32-pet-dna.js:394), HD (50-pet-hd.js). The portrait is deferred to phase 3 |
| CharacterClassCrest matches `onbSvgCrest` at 54 dp | **Not verified** | |
| Payload sizes, memory, haptic feel, LazyColumn drag at 120 Hz, cold-start timing, TalkBack's order under a native screen | **Not verified** | Measured in the phases below |

### Review points settled

| # | Review point | Settled | In this plan |
|---|---|---|---|
| 1 | `#ovEdit` in `lmaTopOverlay` breaks the default hybrid | Accepted | `lmaTopOverlay` is left alone. `lmaBack` closes an open editor **after** the overlay check (the review put it before): a popup that can sit over the editor (`.updpop`, `.lmdl-ask`, the tutorial's swallow) still closes first, as today. A hybrid test checks that Edit, Done and Back leave the live shell open (3.7, 4, 5) |
| 2 | `fxPending` never releases `afterCelebrations`; no place to register the wait | Accepted, fixed another way | Probed both ways. `fxPending` is dropped: the page's `celebrateXP` hands its drawing to the cards while they show (`fxSink`), so nothing holds them away during Log's 450 ms. A celebration the page plays is released by celebrateXP's own `fxSettle`; the wait is registered once, behind a flag, from `cfg.blocked`, so every path that asks registers it (3.5). Real-timer test (5) |
| 3 | Log saves swap the bars twice in phases 1-2 | Accepted, option B | Native orbs move into phase 1 (`fxSink`, `PageCelebration`). Option A is rejected (§1). Phone check 5 is pass or fail on the bars |
| 4 | The gave-up fallback cannot be built | Accepted | `cfg.onFail(reason)`; `gaveUp` in `cfg.blocked`, `wanted()` and so the cover; `NativeTabs.heal()`; reset on the toggle; a sim mode with failing cards (3.1, 3.6, 5) |
| 5 | The TalkBack argument is wrong | Accepted | Why rewritten; a TalkBack parity pass against today's web body; roles and `selectableGroup` (§1, 2, 5) |
| 6 | Tab swipe is lost with native swipe off | Accepted | Recorded in §2, §6, the MIGRATION row and the hint; open decision 5; default-on waits for it (phase 4) |
| 7 | Swipe work in the wrong phase; the `coverable()` claim | Accepted; swipe moves into phase 1 | `body:'web'` until phase 4 is rejected (§1). Exporting `coverable()` is this plan's kit change; `noTabSwipe` on the apps row and the grip; ADR 0012 amends ADR 0011 and A34 (3.6, 3.8, 4) |
| 8 | The Settings row changes native Settings fixtures and screenshots | Accepted | Its own phase-1 step after settings merges (3.9, 7) |
| 9 | The page jumps behind the attribute sheet | Accepted, narrowed | `attr` carries its tile and the page scrolls it into view first. The other sheets are opaque, so they need nothing (3.4) |
| 10 | Most refresh observers are unnecessary | Accepted, one correction | The `#ovHealth`/`#ovRewards`/head observers and `onChange` are dropped; `ovAppsRender` is wrapped. The HUD is created 2.5 s after boot or when voice is switched on, after the cards' segment runs, so it is followed through a wrapper on `lmvHudOpen` (3.5) |
| 11 | The layout commands lack A33.3's tests | Accepted | Node tests in phase 2 (5) |
| 12 | The hybrid's width rule is dropped | Accepted; the rule is kept | `innerWidth > 860` in `cfg.blocked`, `resize` calls `follow()`; a BACKLOG item for the other tabs (3.5) |
| 13 | `DialIntro` does not exist | Accepted | New Life Director work with its own phase-3 step (3.2, 4, 7) |
| 14 | ADR 0006; `heal()` cannot see both drawings | Accepted | ADR 0012 amends ADR 0006 and NAV-6; each drawing closes the other on opening (3.1) |
| 15 | Two traps in the phase-0 refactor | Accepted | The split is fixed in its own commit; helpers stay inside the slice and are guarded; native-overview.browser.js runs in every phase-0 step (4, 5) |
| 16 | Two messages for a read-only save; rows not redrawn | Accepted | Toast only when `!saveReadOnly`; the take-back re-applies and rebuilds the rows; callers stop on `false` (3.2) |
| 17 | Smaller gaps (A24 memory, phase-1 buzz, the mixed hop, the veil's thumbnail) | Accepted, all four | Memory measured and catalogued; `PageBuzz` in phase 1; `toggle-cards` only on the Settings view; the boot thumbnail snap is skipped under the veil (4, 5) |

## 2. What the owner sees and feels

**Phase 0 (all Overviews, no switch).**
- The tiles are in the editor's order from the very first frame after a cold start. Today Rewards, Apps and Health sit above Today's plan until something saves.
- Back closes the open editor instead of leaving the tab.
- A layout change that could not be stored is taken back. When storage is full it says "Could not save to this browser (storage full?)"; a read-only save keeps its one alert and adds no toast.

**Phase 1 (Settings → Appearance → Native Overview cards (beta)).**
- **Bars.** The Overview arrives the way Character and Food do: the bars stay put, and there is no teardown flash between native tabs. The header no longer scrolls away; it is fixed like on every other native tab.
- **Cards.** Every card looks like the page's at 412 dp, in dark and light and in the bought accent:
  - the ring's per-attribute glowing arcs;
  - the gold level badge;
  - the stacked week bars with the dashed pace line;
  - the gems with their decay dots;
  - the monospace luck number;
  - the health mini-bars;
  - app icons.
- **Freshness.** Greeting, date, "N min ago" and the luck day refresh every 15 s. Classic refreshes them only on a save.
- **Taps.** Each tap goes where the page's goes:
  - Character, Log, Workout plan, Settings: native when on;
  - the cat room, Quests and the Journal: web;
  - the attribute sheet, Luck, the shop, the Health page and the app picker open over the page. The attribute sheet shows the tile you tapped behind it. The cards come back by themselves when these close;
  - App shortcuts launch the app without leaving the cards.
- **Plan glance.** A tap on the dial opens Life Director's Today and the tapped session's sheet. A running timer on the glance ticks every second; the page's moves in 30-s steps.
- **Cat.** A compact card: an icon, the name ("Miso", or "Miso & Pip"), and five pixel hearts. "IN MEMORY" for a cat that has passed. Tapping it opens the live room. The live window stays in Classic and hybrid.
- **Edit** opens the page's editor (web bars). Done or Back brings the cards back in the new order.
- **After a native Log save** the bars never change. The Log screen gives way to the cards, and the XP orbs fly from the bottom of the screen to the logo on the cards, with the page's sound and buzz. A level-up plays on the page as it does today, then the cards come back.
- **A health sync that earns XP** while the cards show plays its orbs on the cards too.
- **TalkBack** reads the bars as native buttons (today it reads the invisible web bars under the native strips). It reads the cards through Compose: hero, Cat, Rewards and Health as buttons (on the web they have no role), the week days as one selectable group, the dial's sessions with their labels.
- **Tab swipe.** With Native tab swipe (beta) on, Character ↔ Overview swipes natively both ways, and the apps row scrolls without switching tabs. With it off, there is no tab swipe on the Overview while the cards are on.
- **Landscape.** Wider than 860 dp, the page's own wide Overview shows, as with the live hybrid. Rotating back brings the cards.

**Phase 2.**
- Edit turns the column into the editor in place, natively. Drag a row by its grip and it lifts (scale 1.03, a coloured shadow) while the others glide apart.
- Haptics fire where the page's do, at the owner's chosen strength:
  - 8 ms open;
  - 10 ms lift;
  - 4 ms per slot crossed;
  - 8 ms drop;
  - 6 ms eye toggle;
  - 8-30-8 ms reset;
  - 5 ms per TalkBack move (the page's arrow-key move).
- The list auto-scrolls near the edges. The eye hides a tile; Reset restores the default. Each change saves at once; one that cannot be stored snaps back with the page's message.
- Back or Done closes the editor. TalkBack offers "Move up", "Move down", "Move to top", "Move to bottom" and "Hide/Show".

**Phase 3.**
- Arrival motion like the page's:
  - the ring sweeps in (1.15 s from −130°, scale .92);
  - the week bars grow (.8 s);
  - today's XP counts up (900 ms, cubic ease-out);
  - the XP and attribute bars fill (1.1 s);
  - the cards fade up 30 ms apart;
  - Life Director's dial fades in session by session.
- All of it is off with reduced motion.
- On a cold start the web body is veiled, so the cards appear without a visible swap.
- The cat card shows the cat's own sitting sprite.

**Phase 4.**
- After the owner's use, the cards may become the default (A33), once Native tab swipe is the default too or the owner has answered decision 5, so the default Overview never loses its swipe.

## 3. Architecture

### 3.1 Who owns what

- **Page** (the only writer): data functions, words, the layout save, Luck, the tip, every sheet, the level-ups and celebrations it plays, and the decision between the two drawings (50-native-overview.js).
- **NativeTabKit** (`managed: true`): session, sequence, open, update, close, the 15-s refresh, command serialisation, the overlay watcher, `revisit()`, `watch()`, failure reports (`onFail`) and notices.
- **Native**: drawing, local UI state, haptics from projected levels, and the celebrations handed to it:
  - local UI state means the week-day selection, edit mode, the pending layout, arrival playback and scroll;
  - the native code computes no game rule.

The composite registration lives in 50-native-overview.js and replaces lines 157-158:

```js
/* The Overview has two native drawings: the cards (50-native-overview-cards.js, beta) and this live shell.
   One registration answers for the view and asks the cards first (ADR 0012). */
const cards = () => window.NativeOverviewCards || null;
const cardsWanted = () => !!cards()?.wanted();
const closeShell = () => { if (N.open) { mask(false); N.plugin.close({ session: N.session }); } };
NativeTabs.cover('dashboard', () => cardsWanted() || (!!plugin() && !blocked(true)));
NativeTabs.register('dashboard', {
  isOpen: () => N.open || !!cards()?.active(),
  isOpening: () => N.opening || !!cards()?.opening(),
  open: () => (cardsWanted() ? cards().open() : open(false)),
  close: () => Promise.all([cards()?.close(), closeShell()]),
  get body() { return cardsWanted() ? undefined : 'web'; },  // native tab swipe's webBody()
  wouldOpen: () => cardsWanted() && cards().coverable(),     // native tab swipe's kind()
});
/* Whichever drawing opens closes a stray other at once: the repair heal() cannot make, since it sees one entry for the view. */
window.NativeOverviewShell = { isOpen: () => N.open, close: closeShell };
```

Further changes in that file:
- `blocked()` gains `||cardsWanted()`.
- The navigate wrapper (line 150) queues `cardsWanted() ? cards().open() : open(…)`. Only one drawing is ever asked, so neither one's decline flushes the other's hand-over.
- Boot: a 900-ms timer asks the cards when they are wanted. That is after the page's 800-ms theme and rewards pass (44-rewards.js:149), which matters because nothing re-sends the tokens when the accent changes (3.5). The hybrid's 1800-ms timer (line 167) stays; its `blocked()` refuses while the cards are wanted.
- `activate()` (lines 172-178) sets `native-cover` for `dashboard` too when the cards are wanted, so no web fade-in plays under them.
- The hybrid's successful `open()` closes the cards if they are open; the cards' `afterOpen` closes the shell if `NativeOverviewShell.isOpen()`.

`wanted()` is `tab.enabled() && !gaveUp`. `gaveUp` is set by the kit's `cfg.onFail(reason)`:
- `'capability'`: the handshake lacks `overview.cards.v1`;
- `'open'`: `plugin.open` rejected or threw (a decoder rejection);
- `'update'`: a refresh was rejected.

It sits in `cfg.blocked`, so the kit's `revisit()` and watcher never retry. It is out of `wanted()`, so the composite's cover and the hybrid's `blocked()` hand the view to the live shell. `onFail` then calls `NativeTabs.heal()`, which opens the composite's hybrid within 600 ms, and toasts once: "The native Overview cards could not open. The live Overview is showing." Turning the switch off and on again resets it.

### 3.2 Files

**Web, new**
- `src/runtime/segments/50-native-overview-cards.js`, included after 50-native-library.js in index.template.html:132. It holds:
  - `projection()` and `commands`;
  - `NativeTabKit.create({ id:'dashboard', plugin:'NativeOverviewCards', capability:'overview.cards.v1', setting:'nativeOverviewCards', toggle:'nativeOverviewCardsToggle', managed:true, render:'renderOverview', notices:true, projection, commands, beforeOpen, afterOpen, afterClose, onFail, blocked, watch, globals:{open:'openNativeOverviewCards', enable:'setNativeOverviewCardsEnabled', projection:'NativeOverviewCardsProjection'} })`;
  - `window.NativeOverviewCards = { wanted, active, opening, coverable, open, close, covering, diagnostics }`;
  - the image table, the celebration sink (`fxSink`), the gave-up flag, the pet-loop seam, the thumbnail-pass guard, and three page hooks that call the kit: `ovAppsRender` → `schedule()`, `lmvHudOpen` → `follow()`, `resize` → `follow()`.
- `tests/overview.test.js` (phase 0), `tests/native-overview-cards.test.js` (phase 1).
- `tests/fixtures/overview-classic/*.json`: each card's HTML for the golden saves, captured **before** the phase 0 refactor.

**Web, changed**

| File | Change |
|---|---|
| 17-runtime.js | first, in its own commit: the `/* next moves */` comment and `function ovMo` move to 18-runtime.js so the name is whole; then `ovHeroData()` (ovHeroHtml draws from it), `petPeekData()` {mode, name, hearts}, and `petWantRun()`'s dashboard branch adds `&&!window.NativeOverviewCards?.covering()` |
| 18-runtime.js | `ovMoves()` whole, returning `go: {view} \| {attr:id} \| {luck:true}` (ovNextHtml builds `onclick` from it); `ovAttrsData`, `ovWeekData` (with `ovTipParts`), `ovLuckData`, `ovRecentData`, `ovHeadData`. All of them sit inside the slice native-overview.browser.js runs (`function ovKey(` to `window.__ovReady`) and are guarded with `typeof` where they reach outside it |
| 39-health.js | `ovHealthData()`; `ovLayoutApply()` right after its splice |
| 41-ov-apps.js | `ovAppsData()`; `ovLayoutApply()` right after its splice |
| 44-rewards.js | `ovRewardsData()`; `ovLayoutApply()` right after its splice |
| 29-pet-pair.js | `petPeekName()` (the "A & B" rule, used by its wrapper at 523-525 and by `petPeekData`) |
| 31-overview-edit.js | `ovLayoutSave` returns the stored flag. On failure it restores the previous layout, calls `ovLayoutApply()`, rebuilds the open editor's rows (`list.innerHTML = ovEditRowsHtml()`, the list keeps its wiring), and toasts `unsavedMessage()` only when `!saveReadOnly` (`writeSave` already alerts once for a read-only save, 04-runtime.js:41). `ovEditToggle`, `ovEditCommit` and `ovEditReset` stop when it answers `false` |
| 34-navigation.js | `lmaBack()` closes an open `#ovEdit` (`ovEditClose()`) after the overlay check and before the stack. `lmaTopOverlay()` is unchanged |
| 13-runtime.js | `dismissFeatureTip` returns what `writeSave()` answered |
| 07-runtime.js | `celebrating()` (`luBusy \|\| Date.now()<fxUntil`); `let fxSink=null` and, in `celebrateXP` right after the `fxHold` check, `if(fxSink&&fxSink(delta,gains,lastTap))return;` |
| features/life-director/logic.js | `glanceData()`, which glanceHtml draws from; `native.glance()`; `native.fromGlance(act,id)` = openFromGlance's navigation plus `nativeAct` |
| 50-native-tabkit.js | `managed`; `active()`, `opening()` and `coverable()` returned; `cfg.afterClose`; `cfg.onFail`; `cfg.notices` |
| 50-native-overview.js | composite, `cardsWanted` in `blocked()`, `native-cover`, the 900-ms boot ask, `NativeOverviewShell` and the cross-close |
| 50-native-library.js | its toast counter moves onto the kit's `cfg.notices`; its tests stay unchanged |
| views/settings.html | the new row (3.9) |
| tools/gen_native_fixtures.js | `overview(b)`, and the page's `hapScale` outputs per level for `PageBuzz` |
| tests/native-handover-sim.test.js | the cards fake, the modes, the new hops and faults |
| tests/native-swipe.test.js (from track/swipe) | the Overview with the cards on and off |
| tests/native-settings.test.js (from track/settings) | the toggle map (line 245) and the row coverage include the new row |
| module-map.json, symbol-index.json | regenerated |
| 50-native-settings.js (from track/settings) | `["nativeOverviewCards", "#nativeOverviewCardsToggle", PREFS]` in BOUND |

50-native-log.js does not change: its held orbs reach the cards through `fxSink`.

**Native, new** (canonical tree, ktfmt; each file under 600 lines)
- `bridge/web/NativeOverviewCardsPlugin.kt`: ComposeTabPlugin, `tabId = "dashboard"`, `capabilities = listOf("overview.cards.v1")`. It holds `accept`, `Content`, `onReply` and the command sender.
- `app/OverviewContainer.kt`.
- `feature/overview/domain/`: `OverviewProjection.kt` (Head, Tip, TileInfo, Layout, and one type per card), `OverviewRepository.kt`.
- `feature/overview/data/`:
  - `WebOverviewRepository.kt`: decoder; rejects malformed data, keeps the last good state, ignores older sequences, requires the tokens via `pageTokens()`;
  - `OverviewJson.kt`: per-card decoders;
  - `OverviewEvents.kt`: action and command wire names.
- `feature/overview/presentation/`: `OverviewRoute.kt`, `OverviewScreen.kt` (a LazyColumn keyed by tile id, with the celebration layer over it), `OverviewViewModel.kt`, `OverviewColors.kt` (extends `PageColors`).
- `feature/overview/presentation/components/`:
  - `OverviewHead.kt`, `OverviewCardHead.kt` (`.ov-h`), `OverviewChips.kt` (`.ovh-chip`);
  - `NextMovesCard.kt`, `HeroCard.kt`, `TodayRing.kt`, `RewardsCard.kt`, `AppsCard.kt` (its row takes `noTabSwipe()` while it overflows, as the web's swipe refuses a start on an overflowing strip, 34-navigation.js:105), `HealthCard.kt`;
  - `AttributesCard.kt`, `WeekCard.kt`, `WeekChart.kt` (the days `selectable` inside a `selectableGroup`), `CatCard.kt`, `LuckCard.kt`, `RecentCard.kt`;
  - phase 2: `OverviewEditor.kt`, `ReorderColumn.kt` (the grip takes `noTabSwipe()`);
  - phase 3: `OverviewArrival.kt`.
- `feature/lifedirector/presentation/glance/DirectorGlance.kt` (phase 1): Life Director's glance, drawn by Life Director's package as on the page (`LifeDirector.glance`). It uses the read-only `ChronoDial`, the glance head and foot and the empty states. It is paired with `LifeDirectorJson.glance()` in Life Director's data package. Overview → Life Director is the one sanctioned cross-feature dependency, and it mirrors the page.
- `feature/lifedirector/presentation/dial/DialIntro.kt` (phase 3, new Life Director work): the web's `ld-dial-in` as a timeline, like `BuildTimeline` (outer ring .5 s; each session .45 s from .25 s + i × 70 ms; the hand .4 s from .5 s). `ChronoDial` gains an optional `intro` parameter. It has a reduced-motion path and start, middle and end screenshots.
- **Core lifts** (each with a Gallery section; the existing references must stay byte-identical):
  - `core/designsystem/components/ClassCrest.kt` (from Character's `CharacterClassCrest`);
  - `PageTip.kt` (from Library's `FeatureTipCard`);
  - `core/data/PageImages.kt` (data-URL decode with an LRU keyed by the page's key; Character's avatar decode moves onto it);
  - `Css.kt` gains `cssRadialGradient` (elliptical, for the hero and luck backgrounds; Css.kt has only the linear one);
  - `PageBuzz` and `HapticScale` (phase 1): ld2's `DirectorBuzz` lifted, with an optional amplitude, and Life Director moves onto it; `HapticScale` is the page's `hapScale` (`dm`, `add`, `min`, the 260 ms cap on the on-times) with the level's amplitude;
  - `PageCelebration` (phase 1): the particles of Workout plan's `PlanFeedbackLayer` (ring, burst, orbs to the logo, the rising "+N XP"), with `Celebration`, `XpGain` and `PagePoint` moving to core. Workout plan keeps its toast and calls it.
- Tests: `feature/overview/OverviewTest.kt`, `OverviewViewModelTest.kt`, phase 2 `OverviewEditorTest.kt`, `screenshots/OverviewScreenshotTest.kt`, and fixtures `src/test/resources/fixtures/overview-*.json`.

**Native, changed**
- `MainActivity.java`: `registerPlugin(NativeOverviewCardsPlugin.class)`.
- `NativeApi.java`: main's value + 1 **at merge**.
- Character and Library import the lifted pieces; Workout plan imports `PageCelebration` and the moved types; Life Director imports `PageBuzz` (phase 1) and gains `DialIntro` (phase 3).
- After settings merges: `settings-default.json`, `settings-bar.json`, `settings-light.json` and `settings-many.json` are regenerated, and the SettingsScreenshotTest references that show the Appearance card are re-recorded, dark and light.

**Docs**
- `OVERVIEW_INVENTORY.md`: every fact and tap, from the map, corrected, plus the TalkBack parity table (each web node with its label, role and state beside its native node).
- ADR `0012-native-overview-cards.md`: one owner, two drawings. It amends ADR 0006 and NAV-6 (a view with two native drawings registers one composite in its owner module, so a second drawing does edit that module); amends ADR 0011 item 5 (the Overview is a swipe wall only while the hybrid draws it); records the cat as a door to the web room and the `lm_pet_seen` effect, celebrations through `fxSink`, and the landscape rule.
- A BRIDGE_CATALOG entry `overview-cards`, with the payload size, the memory cost (A24) and the tab timing once measured. The `overview` entry stays for the hybrid.
- A MIGRATION ledger row "Overview (cards)", listing the delegations, the loss of tab swipe while native swipe is off, and the landscape rule.
- CONTRACT: an A23 note (a view may have two native drawings; its module registers one composite; item 9 unchanged); A34 item 6 amended to match ADR 0011.
- NATIVE_TAB_GUIDE: `managed`, `onFail`, `afterClose`, `notices`, and `fxSink` for a screen that draws celebrations.
- BACKLOG:
  - the voice HUD under every native tab;
  - the warm pass versus kit tabs;
  - the glance heartbeat under cover;
  - the dead selectors in the hybrid: `actionSelector`'s body entries (50-native-overview.js:87) and `.lock-ov, .journal-lock` in `blocked()` (:80), which nothing creates;
  - the width rule for the other native tabs;
  - the WebView in TalkBack's order under a native screen, if phase 1 confirms it.

### 3.3 The projection (`overview.cards.v1`)

The kit adds `protocol`, `webSessionId`, `sequence`, `tokens` and `light`. Every string is the page's own wording. Colours are ARGB: `var(--x)` is resolved through the token map, and attribute colours come from `ATTR_COL`.

| Field | Content | Source |
|---|---|---|
| `head` | `{date, hello, classId, edit:{label:'Edit', a11y:'Edit the Overview: reorder or hide tiles'}}` | `ovHeadData()`: the date as renderOverview words it (18-runtime.js:56), `ovGreeting()` (17-runtime.js:335), `classById` |
| `tip` | `{key:'dashboard', icon, title, body, dismiss:'Dismiss tip'}` or null | `FEATURE_TIPS.dashboard` while `tutorialDone && !tipsSeen.dashboard`, as Library does |
| `layout` | `{order, hidden}` | `ovLayout()` (31-overview-edit.js:19) |
| `tileInfo` | `[{id, name, sub, note, icon, color}]` in `OV_DEFAULT` order | `OV_TILES` plus `ovTileNote(id)`; the cat's sub is "<name>'s room" (31-overview-edit.js:41-43) |
| `tiles.plan` | `{state:'setup'\|'none'\|'day', title, link, intro?, button?, dial?, core?, foot?:{text, done}}` or null | `LifeDirector.native.glance()`, the same decisions as glanceHtml. The core has the cleaned title (full title kept for TalkBack), no "Nothing else planned today", and the timer as its only tap (logic.js:4701-4753) |
| `tiles.next` | `{title, note, moves:[{title, sub, icon, color, warn, done, cta, pill, go}]}` | `ovMoves()` (18-runtime.js, whole after phase 0). `go` is a view the page names, or `{attr}`, or `{luck}` |
| `tiles.hero` | `{today, pace, overPct, ring:{r:62, stroke:11}, arcs:[{color, offset, length}], avatar:imageKey\|null, initial, level, title, className, xp:{text, next, pct}, streak:{days, label, dots:[{on, now}]}, foot:[{label, value, delta?}], a11y}` | `ovHeroData()`. Arcs are in pathLength-100 units, exactly as the SVG's dasharray and offset (17-runtime.js:343-344) |
| `tiles.rewards` | `{title, link:'Shop ›', chips:[{label, value}]}` | `ovRewardsData()` (44-rewards.js:131-137) |
| `tiles.apps` | `{title, link:'Edit ›', items:[{pkg, label, image}], add:'Add app', hint}` or null when `!lmaNative()` | `ovAppsData()` (41-ov-apps.js:3-12) |
| `tiles.health` | off: `{on:false, title, text, button}`; on: `{title, meta, steps, unit, sub, pct, days:[{label, frac, now, a11y}], chips, more}`; null when `!lmaNative()` | `ovHealthData()` (39-health.js:137-156) |
| `tiles.attrs` | `{title, link, gems:[{id, short, icon, color, focus, dot:{kind, text}, level, week, pct}], legend}` | `ovAttrsData()` (18-runtime.js:20-27) |
| `tiles.week` | `{title, total, delta?, pace:{value, frac}, bars:[{date, label, today, xp, segs:[{color, frac}], a11y, tip:{label, rest}}], top, empty?, selected}` | `ovWeekData()` (18-runtime.js:29-43); `selected` is `OV_SEL` |
| `tiles.cat` | `{mode:'alive'\|'gone', name, hearts}` or null; phase 3 adds `portrait:imageKey` | `petPeekData()` (17-runtime.js:313-321, the hearts at 73; `petPeekName()`) |
| `tiles.luck` | `{eyebrow, rolled, score, tier:{name, color}?, text, button}` | `ovLuckData()` (18-runtime.js:46) |
| `tiles.recent` | `{title, link:'History ›', items:[{name, icon, color, meta, xp}], empty:{icon:'⌁', text}}` | `ovRecentData()` (18-runtime.js:47-50) |
| `images` | `{key: dataURL}` for the avatar and app icons referenced above; key = FNV-1a of the URL, memoised per string | `state.avatar`, `lmaLoad().apps[].icon` |
| `notice` | `{id, text}` while the page's toast shows | the kit's `cfg.notices`, lifted from Library (50-native-library.js:90-105) |
| `celebrations` (phase 1) | `[{id, xp, origin:{x, y}, gains:[{attribute, short, xp, color}]}]`, each kept for 2 s after the page handed it over; native plays each id once | the cards' `fxSink` (3.5); `origin` is the page's `lastTap` (Log sets it to the bottom centre, 50-native-log.js:98); `gains` as Workout plan's `gainOf` |
| `celebrationTarget` (phase 1) | the logo's centre, as Workout plan sends it | 50-native-plan.js:171-175 |
| `motion` | `{reduced}` | `fxReduced()` |
| `haptics` | `{dm, add, amp, min, max}` or null | `HAP_LEVELS[hapLevelKey()]` and `HAP_MAX` when `fxOpt().haptics` |
| `arrival` (phase 3) | a number that rises when the screen opens within 600 ms of a page arrival | navigate wrapper and `beforeOpen(entered)` |

Images are sent with every projection, as Character's avatar already is. Native decodes each key once (`PageImages`). The size is measured in phase 1 and recorded in the catalog. If a typical save exceeds about 300 KB, phase 3 switches to "keys sent once per open".

### 3.4 Commands and actions

| From the screen | Kind | What the page runs | Writes | Afterwards |
|---|---|---|---|---|
| Hero, attributes "Details ›" | action `{view:'character'}` | the hand-over | — | native Character opens first when on |
| A Next move whose `go` is a view (log, quests, plan, pet) | action `{view}` | the hand-over | — | web destinations close the cards at once |
| Recent "History ›" | action `{view:'history'}` | the hand-over | — | the Journal behind its lock |
| Health off "Set up health sync" | action `{view:'settings'}` | the hand-over | — | native Settings when on |
| Cat card | action `{view:'pet'}` | the hand-over | — | the live room (web) |
| Plan glance (dial arc, timer, link, button, empty states) | command `plan {act, id}` | `LifeDirector.native.fromGlance(act, id)`: go to Life Director, Today tab, mount, then `nativeAct(act, id)`; `''` or `glanceOpen` only opens | Life Director's own, later | it leaves the view, so the hand-over closes the cards after native Life Director opens |
| Gem, or a "Train" move | command `attr {id, tile}` | the page scrolls `.ov-tile[data-tile=tile]` into view (`block:'center'`), then `openAttr(id)` | — | the sheet (noscroll); the screen steps aside, and comes back on close. The sheet's backdrop is translucent (02-layout.css:807), so the tapped tile must be what shows behind it |
| "Roll"/"View roll", or a move with `{luck}` | command `luck` | `openLuck()` | **the day's roll**: `state.luck`, `luckLog` through `save()`, before the animation (19-runtime.js:93-96) | steps aside; comes back on `luckClose`. No spoiler: the noscroll watcher closes the screen before the reply could be sent (3.5) |
| Rewards card or "Shop ›" | command `shop` | `ecoShopOpen()` | the shop's own | `.hsheet` steps aside, and the screen returns when it is removed |
| Health card or "Details ›" | command `health` | `lmaHealthOpen()` | the sync's own | as above |
| An app | command `appLaunch {pkg}` | `AppsShortcuts.launch(pkg)`, only for a `pkg` in `lmaLoad().apps` | — | stays; a failure arrives as `notice` |
| "+ Add app" | command `appAdd` | `AppsShortcuts.openPicker()` | the picker's prefs | `.apps-sheet` steps aside |
| Apps "Edit ›" | command `appsEdit` | `navigate('apps'); LifeMaxFeatures.open('apps')` | — | web tab, closes at once |
| Tip ✕ | command `tip` | `dismissFeatureTip('dashboard')` | `tipsSeen` | stays; `ok:false` with `unsavedMessage()` when not stored |
| Edit (phase 1) | command `edit` | `ovEditOpen()` | — | `blocked` while `#ovEdit` shows; back on Done or Back (`lmaBack`, phase 0) |
| Edit changes (phase 2) | command `layout {order, hidden}`, `layoutReset` | `ovLayoutSave` (the owner), `ovLayoutApply` | `state.ovLayout` (set semantics) | stays; the reply carries the page's layout; a refused write says why and the layout is the stored one |
| Week bar | local | — | — | selection in the view model, a 6 ms buzz through `PageBuzz` at the owner's strength |

All of these use NATIVE_TAB_GUIDE's rule: a handler that leaves the view or covers it runs as a command, so the page never flashes. Its reply never arrives, and the view model stops waiting when the screen next opens (as `LogViewModel.opened`). One stepping-aside tap is accepted per opening, as in Library.

### 3.5 Covering, stepping aside, coming back

`cfg.blocked()`, on top of the kit's base rule (view, onboarding, `lmaTopOverlay()`, noscroll):
- `gaveUp`;
- `innerWidth > 860`, the hybrid's rule (50-native-overview.js:80). Above it the page's `.sidebar` is a side rail (02-layout.css:325-342), not the bottom bar the shared bars are drawn from;
- `lmaThumbWarmBusy`;
- `celebrationShowing()`: `celebrating()`, and while it is true, a wait registered once behind a flag, as 50-native-director.js:8, 25-33 does:
  ```js
  if (!waiting) { waiting = true; afterCelebrations(() => { waiting = false; tab.revisit(); }); }
  ```
  Every path that asks (an open, the watcher, a refresh) registers it, so the screen comes back however the celebration began;
- `document.querySelector('.luov, .lmv-hud.on')`;
- phase 1 only: `!document.getElementById('ovEdit').hidden`.

`watch(observe)` lets the kit's own watcher call `revisit()` (no observer of the tab's own for stepping aside). It observes `#ovEdit`'s `hidden` attribute. Sheets that are body children (`.hsheet`, `.apps-sheet`, `.luov`) and the noscroll class are already watched. Two more things change `blocked()` without touching the body, so they call `tab.follow()` themselves:
- a wrapper on `lmvHudOpen`, the one place the HUD's `on` class is set (45-voice.js:490). The HUD is created 2.5 s after boot or when voice is switched on (45-voice.js:615, 589), after this segment has run, so it cannot be observed at load;
- `resize`, for a rotation across 860.

**Celebrations.** `celebrateXP` asks `fxSink` first (07-runtime.js:83, after the `fxHold` check). The cards' sink takes a celebration when `tab.active() && !tab.blocked()`:
- it adds `{id, xp, origin, gains}` to the projection's `celebrations`, sends at once, and plays `xpCue(xp)` 120 ms later (Workout plan's `CUE_AFTER_MS`, 50-native-plan.js:17);
- it sets no `fxUntil`, so the screen does not step aside for it.

So Native Log's held orbs, played 450 ms after its save (50-native-log.js:96-100), land on the cards: the cards opened during those 450 ms, because no page celebration held them away. A celebration the sink refuses plays on the page and sets `fxUntil`; `celebrationShowing()` keeps the screen away until celebrateXP's own `fxSettle` at 1550 ms (07-runtime.js:86) or a level-up's close (07-runtime.js:103). A level-up always plays on the page.

**Refresh** comes from:
- the kit's render wrapper on `renderOverview` (outermost of the chain 39, 41, 44, hybrid). A health sync redraws through `save()` (39-health.js:104 → 04-runtime.js:44 → renderOverview);
- a wrapper on `ovAppsRender` that calls `tab.schedule()`, since an app icon arrives outside renderOverview (41-ov-apps.js:22);
- the 15-s interval and resume;
- command replies and the celebration sink.

Nothing else is observed. The accent changes only at boot, before the cards open, and inside the shop, which the cards step aside for; their reopen sends fresh tokens. `LifeDirector.native.onChange` is not used: it fires on Life Director's own renders, not on the glance's.

**Boot.**
- `view-dashboard` is active in the markup and no `navigate` runs, so the owner's boot timer asks the cards to open at 900 ms, after the page's 800-ms theme and rewards pass.
- Onboarding, the tutorial and the feature-tip timing block or are projected as above.

**Pet loop.**
- `covering()` is `tab.active()`.
- `afterOpen` and `afterClose` call `petSync()`, which stops the rAF loop while the cards cover the page and restarts it when they step aside on the dashboard.
- Side effect: `lm_pet_seen` is not refreshed while only the card is seen, so a long-absence greeting can fire on entering the room. That is honest, since the cat was not seen. Recorded in the ADR.

**Thumbnail pass.**
- `lmaThumbWarm` is wrapped like the hybrid's (50-native-overview.js:163): it refuses while the cards are open or opening.

**Life Director's glance heartbeat.**
- The page's own glance keeps re-rendering every 30 s under the cards (logic.js:4754-4758). That is where its sync and persist live (logic.js:4717), so `native.glance()` stays a pure read.
- Moving the heartbeat out of the glance render is a BACKLOG item for Life Director's later phases.

### 3.6 Kit changes (50-native-tabkit.js)

- `cfg.managed`:
  - skips `NativeTabs.cover/register` and the arrival open in the navigate wrapper;
  - keeps `NativeTabs.cancel` and `entered` on arrival, `leave` on departure, the watcher, refresh and `revisit`.
- The return value gains `active()`, `opening()` and `coverable()`. ld2 adds `active()`. Swipe adds `coverable()` only as a private function and puts `wouldOpen` in the registration that `managed` skips, so exporting it is this plan's change. Whichever lands first, the others rebase.
- `cfg.afterClose()` runs after `close()` and after the plugin's `closed` event.
- `cfg.onFail(reason)` runs when the handshake lacks the capability (`'capability'`), when an open throws or is rejected (`'open'`), and when a refresh is rejected (`'update'`), after the kit has closed.
- `cfg.notices`: the toast counter lifted from Library.

### 3.7 Hand-over and Back

- **Arrival from a native tab (bar tap, swipe, `{view}`).** The cards open first, the previous screen closes, and ChromeHost keeps one set of bars (A23 items 1, 4 and 5). The Overview shell is not involved.
- **Arrival from a web tab.** The kit opens over it.
- **Departure.** A `{view}` action, a navigating command, a bar tap, a swipe, or Back popping the stack, through the kit's navigate wrapper `leave('dashboard', v, close)`.
- **Native Log save.** The cards open as the Log screen closes. Log's held orbs reach them through `fxSink` 450 ms after the save. A level-up (05-runtime.js:12, 950 ms after the save) puts `.luov` over the page; the cards step aside and come back after its close. If the cards are not showing at 450 ms (still opening on a slow cold start, or held away by the HUD), the page plays the orbs as today and the cards come back after them.
- **Back.** It stays the page's `lmaBack` (MainActivity.java:129-140).
  - On the Overview with an empty stack nothing happens: it is the home screen.
  - Phase 0 makes Back close the web editor, inside `lmaBack` after the overlay check. `lmaTopOverlay` stays as it is, because the hybrid's `blocked()` counts it and its body observer would close the shell for the whole edit (50-native-overview.js:80, 135, 166).
  - Phase 2: `BackHandler(enabled = visible && editing) { vm.endEdit() }`, following ld2's precedent (A24 allows a handler active only while visible).
  - Leaving the screen ends edit mode quietly; the changes are already saved.
- **Reloads.** `NativeScreens` removes the screen (ComposeTabPlugin.load). `lmaLeaveNative` sees `covered('dashboard')`, leaves for Settings, then `closeAll` (35-android-upkeep.js:59), and the composite's `close` closes both drawings.
- **Deep schedule link.**
  - From another native tab it moves to the Overview and opens the HUD in the same task. The cards' queued open then finds `.lmv-hud.on` and declines, so the preview shows.
  - On the Overview, `lmvHudOpen(true)` makes the cards step aside through its wrapper, and `lmvHudOpen(false)` brings them back.

### 3.8 The shell: Overview shell versus ChromeHost; native swipe

| | Cards on | Cards off (today) |
|---|---|---|
| Bars | ChromeHost shared bars (one instance across native tabs; owner token; the 3-s stale rule) | its own LiveOverviewShell, built on every open; `ChromeHost.closeAll()` |
| Header | fixed; native hits; TalkBack reads its native nodes | scrolls with the page; touches forwarded to the DOM topbar; the native strips hidden from TalkBack, which reads the masked web bars |
| Body | Compose LazyColumn, kept composed when hidden | the live WebView |
| Web bars | not masked; they show only while the cards step aside | masked with opacity 0 |
| Tab swipe | native with Native tab swipe (beta) on; none with it off | the web swipe on the live body (34-navigation.js:138-198) |

Swipe changes (phase 1, after swipe merges):
- The kind rule reads the composite's `body` getter and `wouldOpen`. With the cards on, the Overview is `native`, so Character ↔ Overview works both ways with no Overview-specific swipe code. Its screen becomes a `TabScreenView` through ComposeTabPlugin.
- `noTabSwipe()` on the apps row while it overflows, and on phase 2's grip.
- ADR 0012 amends ADR 0011 item 5, and CONTRACT A34 item 6, to "the Overview is a wall while the hybrid draws it".
- The z-order row "Overview shell, only while Overview is open" applies only with the cards off.
- Swipe phase 2's "Overview's open calls `ChromeHost.closeAll()`, reveal under the shell's transparent middle" is likewise hybrid-only.

### 3.9 Settings, compatibility, versions

- **Row.** Settings → Appearance, right after "Native Overview" (settings.html:8): `#nativeOverviewCardsSetting`, **Native Overview cards (beta)**, hint "Draws the Overview's cards natively. The cat's room, Daily Luck, the shop and the health page still open on the page. Swiping between tabs on the Overview needs Native tab swipe (beta). Turn off for the live Overview." Input `#nativeOverviewCardsToggle`, key `lmaLoad().nativeOverviewCards`, **off by default**, disabled until the plugin exists (kit `sync()`).
- **Native Settings** (after settings merges): the BOUND entry, the test's toggle map and row coverage, the four `settings-*.json` fixtures regenerated, and the Appearance references re-recorded in dark and light.
- **Precedence.** Cards when on and not given up; otherwise the live shell when "Native Overview" is on; otherwise Classic.
- **Old APK.** No `NativeOverviewCards` plugin, so the row is disabled and the hybrid runs as today.
- **Old page with a new APK.** Nothing calls the plugin.
- `webMinApi` does not rise.
- `NativeApi.VALUE` = main + 1 at merge (29 today; settings, ld2 and swipe will each take one).

## 4. Phases (each shippable alone; everything native sits behind the beta switch)

**Phase 0: web groundwork (all Overviews; no switch).**
1. Fixes, in two commits:
   - `ovMoves`' name made whole in 18-runtime.js, a pure move proven by native-overview.browser.js and the existing tests;
   - the tile order (`ovLayoutApply()` after each of the three splices); Back closes the editor (`lmaBack`); honest layout saves (stored flag, take-back, rows rebuilt, one message); `dismissFeatureTip` answers.
2. One decision, two drawings for every card. Capture `tests/fixtures/overview-classic` first, on the four golden saves plus extras states (pet, two cats, cat gone, health on and off, apps, luck rolled, over pace), then refactor. Every card's innerHTML must be identical. The helpers stay inside the slice native-overview.browser.js runs, and that test runs in this step.
3. Life Director: `glanceData()` (glanceHtml identical), `native.glance()`, `native.fromGlance()`. This lands **after ld2**, which rewrites 418 lines of logic.js.
4. (Optional, recommended, after swipe, settings and ld2 merge.) A pure move of 50-native-overview.js into 50-native-tabs.js (NativeTabs), 50-native-chrome.js (NativeOverviewSnapshot) and 50-native-overview.js (the hybrid and the dashboard owner). Tests change only their paths.

Owner gets: the correct order from the first frame, Back closing the editor, and honest layout saves.

**Phase 1: native cards, read and delegate.** Lands after settings, ld2 and swipe have merged.
- Web half: the segment, the composite and cross-close, the kit options (`managed`, the exported getters, `afterClose`, `onFail`, `notices`), the gave-up flag, the width rule, the HUD and resize hooks, `celebrating()` and `fxSink`, the setting, the pet and thumbnail seams, the boot ask. The web editor is delegated through `edit`.
- Fixtures.
- Native domain and decoder, the plugin, the container.
- Cards A: head, tip, next moves, hero ring, rewards, luck, recent.
- Cards B: attributes, week chart (with `PageBuzz` and `HapticScale` for its 6 ms buzz), health, apps, cat (the icon card).
- Life Director's `DirectorGlance`, with a per-second timer tick from `startedAt`/`pausedMs` only while `visible`.
- Celebrations: the `PageCelebration` lift (Workout plan's references and tests unchanged) and the cards drawing what `fxSink` hands them.
- Native swipe neighbour: the composite's `body`/`wouldOpen`, `noTabSwipe()` on the apps row, native-swipe tests with the cards on and off, sim swipe hops through `dashboard`, the ADR 0011 and A34 amendment.
- Native Settings row: BOUND, the test's toggle map and rows, the regenerated fixtures and the re-recorded Appearance references.
- Docs and catalog (payload, memory cost, tab timing); the sim.
- Review fixes, including the TalkBack parity pass.

Owner gets: everything in §2 phase 1.

**Phase 2: the native editor.**
- Edit mode in place: `OverviewEditor` with a plain `Column` of up to 11 rows inside the screen's LazyColumn, auto-scrolling that list's state.
- Grip drag starts on the grip's touch; anywhere on the row after a long press. The grip takes `noTabSwipe()`.
- Rows glide with `animateItem`-equivalent offsets (260 ms, cubic-bezier(.2,.8,.2,1)); the drop settles in 220 ms; the open animation is 280 ms with rows 30 ms apart; reduced motion turns these off.
- `PageBuzz` at the page's moments.
- Commands `layout` and `layoutReset`, with their node tests. The new order shows at once; a refused answer snaps back to the page's layout with the page's message.
- TalkBack custom actions; `BackHandler`. The `edit` delegation is removed.

Owner gets: the editor without leaving the cards.

**Phase 3: feel.**
- Arrival motion keyed by `arrival` (so a screen that returns after a sheet or a celebration stays still).
- `DialIntro`: new Life Director work in its package, with its reduced-motion path and screenshots, used by `DirectorGlance` on arrival.
- The cold-start veil: `html.native-boot #view-dashboard{visibility:hidden}` while the cards are wanted, cleared on open, on decline, or after 1500 ms. `lmaThumbSchedule` skips its snap while `html.native-boot` is set, or the 1200-ms boot snap (42-scrubnav.js:215) would store a blank Overview.
- The cat portrait: a page-owned `petPortrait(p)` in the pet code that draws one idle frame the way the room does (DNA, morph, HD), proven against the room in a screenshot.

Owner gets: the page's motion and no swaps.

**Phase 4: rollout.**
- After the owner's use (A33), and once Native tab swipe defaults to on too or the owner has answered decision 5: the switch may default to on. "Native Overview" then means the live shell as an alternative, and Classic remains.
- The hybrid's code (LiveOverviewShell non-chromeOnly mode, OverviewBars, NativeOverviewPlugin) is deleted only after a further release with no regression (A33).

## 5. Tests

**Phase 0, web**

New `tests/overview.test.js` (Playwright, golden saves, fixed clock):
- order at load equals `ovLayout().order`, with the three spliced tiles, in Classic and under a fake hybrid;
- `ovLayout` normalisation (unknown, duplicate and new ids placed after their default predecessor);
- Back closes the editor and does not navigate; a popup over the editor closes first;
- with a fake NativeOverview plugin installed after load (the map's probe recipe): Edit then Done, and Edit then Back, leave the live shell open, with no `close` call and the web bars still masked;
- a failed and a read-only layout save are taken back and the open editor's rows show the stored layout; storage full toasts once; read-only only alerts, once; backup-restore.test.js:59 still holds;
- `dismissFeatureTip`'s answer;
- `ovMoves` priorities and the cut at 4 (no activities, fading versus due, quests left versus done, rest day, workout done, luck, hungry or starving cat, stray kitten);
- `ovPace` (fewer than 2 active days gives 100; the 60-400 clamp);
- hero arcs (the sum never exceeds the fill; the 2.4 gap only with more than one part);
- the week's max and pace line.

Classic identity: every card's innerHTML equals `tests/fixtures/overview-classic` (captured before the refactor); the glance HTML equals its capture.

native-overview.browser.js is at risk in phase 0 (it runs a text slice of 17+18 with only a few functions stubbed) and runs in every phase-0 step.

**Phase 1, web**

New `tests/native-overview-cards.test.js` (fake NativeOverviewCards and NativeOverview plugins):
- the projection against the page's own DOM: move titles against `#ovNext .ov-move b`, the arc dasharray against the SVG, bar `aria-label`s, the gems' dot classes, the luck text, recent rows, the tile order against `ovLayout()` before any save;
- each covering rule steps aside and comes back: attribute sheet (its tile scrolled into view first), Luck (no update between `save()` and the overlay), shop, Health, app picker, the voice HUD (opened and closed through `lmvHudOpen`), level-up, a celebration the page plays, the thumbnail pass, onboarding and tutorial, the web editor, a resize past 860 and back;
- celebrations, with real timers:
  - a native Log save with the cards wanted: the cards open before 450 ms, the projection then carries Log's held orbs, no `.fx-xp` appears on the page, and the cue plays 120 ms later;
  - the same save with the HUD open: the page plays the orbs at 450 ms, the HUD closes at 1 s, the cards stay away and reopen by about 2.1 s;
  - a level-up at 950 ms: the cards step aside and come back after its close;
  - a health-sync save with XP while the cards show: drawn by the cards;
- every command and action;
- `tip` and `luck` repeated (the second roll changes nothing);
- `appLaunch` with an unknown `pkg` refused;
- notices;
- the hybrid never opens while the cards are wanted, and `NativeTabs.flush` is not called by the undecided drawing;
- an old APK (no plugin) leaves the hybrid working;
- giving up: a missing capability, an open that rejects and an update that rejects each give up for the session; no retry on body mutations; the hybrid holds the view after `heal()`; the toggle resets it;
- the cross-close in both directions;
- the boot open after the theme (the accent token is right in the first projection);
- `petWantRun()` false while covering and true again after;
- `fromGlance` opens the session sheet;
- an app icon arriving refreshes the open screen.

Existing tests stay green unchanged: native-overview.test.js, native-overview.browser.js, native-overview-preference.test.js, native-overview-chrome.test.js, native-overview-build.test.py, backup-restore.test.js, swipe.test.js, tabs.test.js, native-kit-overlay.test.js, native-handover.test.js, Library's tests after the notices lift, and the pet acceptance scripts. native-swipe.test.js gains the Overview with the cards on (native, both ways) and off (a wall); native-settings.test.js gains the row.

**Phase 1, simulation (A28)**
- `native-handover-sim.test.js` gains the fake `NativeOverviewCards: mk('cards','cards',false)`, run in four modes:
  - cards off: `WANT.dashboard = 'overview'`;
  - cards on: `WANT.dashboard = 'cards'`;
  - mixed: a seeded hop `toggle-cards` flips the setting, only while the view is `settings` (the only place the switch exists, on the page or in native Settings);
  - failing: a cards fake that rejects `open` and `update`; the hybrid must hold `dashboard` alone.
- New hops: `tap-plan` (the `plan` command from the cards), `tap-move` (a `{view}` action), and swipe hops through `dashboard`.
- New faults:
  - `overview` and `cards` visible together;
  - the hybrid opened while the cards were wanted;
  - a previous native tab closed before either Overview drawing showed (with nothing in flight).
- Shown failing on three mutations: dropping `managed` (double open), removing `cardsWanted()` from the hybrid's `blocked()`, and removing `gaveUp` from `cfg.blocked` (the failing cards retry and tear the shared bars).

**Phase 2, web (A33.3)**
- `layout` with the same layout twice gives one state and one write;
- `layoutReset` twice gives the default layout;
- a `writeSave` that fails gives `ok:false` with `unsavedMessage()`, the layout is taken back, and the reply's projection carries the stored layout.

**Kotlin unit tests (plain JUnit)**
- `OverviewTest`:
  - decoder: malformed or missing keys reject and keep the last good state; an older sequence is ignored; missing tokens reject;
  - an unknown tile id is skipped;
  - order comes from `layout`, not from the JSON;
  - image keys decode once (`PageImages` LRU and the 2,000,000 cap);
  - `celebrations` decode;
  - every action and command wire name;
  - `LifeDirectorJson.glance` for its three states;
  - `HapticScale` equals the page's `hapScale` at every level (the fixture the generator writes).
- `OverviewViewModelTest`:
  - week selection survives projections and resets when the dates shift;
  - one stepping-aside tap per opening;
  - a waiting command stops waiting on reopen;
  - a celebration plays once per id and never while hidden;
  - phase 2 pending layout, refusal snap-back with the message, edit ends on hide;
  - phase 3: arrival plays once per counter, never when hidden, and not with reduced motion.
- Phase 2 `OverviewEditorTest`: the drag slot maths (crossing midpoints, step = row + gap), auto-scroll zones, TalkBack move actions producing the right order.
- Workout plan's and Life Director's tests pass unchanged after the `PageCelebration` and `PageBuzz` lifts.

**Screenshots (Paparazzi, A32)**

`OverviewScreenshotTest`: PIXEL_5 at 420 dpi (411 dp, like the owner's phone), screenfuls, `maxPercentDifference = 0.001`, dark and light for each:
- new save;
- established, top to bottom;
- legacy or ascended;
- extras (cat, rested, shields, health on, apps with icons);
- tip;
- notice;
- glance: set-up, no day, planned, timer running;
- hero over pace;
- cat gone and two cats;
- health off, waiting for the first sync, goal reached;
- apps empty;
- luck rolled;
- a selected week day;
- font scale 2.0;
- phase 2: the editor, a hidden tile, a lifted row (view-model state);
- phase 3: `DialIntro` at its start, middle and end.

Gallery sections for `ClassCrest` and `PageTip`. Character's, Library's, Workout plan's and Life Director's references stay unchanged after the lifts.

Review step: compare each screenful side by side with the page at 412 CSS px, using the map's probe recipe (override `lmaNative` and `lmaPlugin` after load; never install a Capacitor stub before load, since the warm pass hangs under a paused clock). Then the TalkBack parity pass: every node TalkBack reads in today's web body (label, role, state) beside its native node, in OVERVIEW_INVENTORY.md, each gap closed or recorded.

**Android instrumentation**
- OverviewParityTest stays as it is (cards off).
- Add `overviewCardsAreComposeAndKeepTheBars`: with the cards on, the tag `native-dashboard-screen` is present, `native-shared-chrome` is present, `overview-live-shell` is absent, the WebView stays accelerated, and `state` is unchanged.

**Phone checks (S25 Ultra, release build)**
1. Cold start with the cards on: how the Overview appears (record any swap), and the bought accent is right at once.
2. Side by side with the switch off and on: every card in dark and light, and "GRANDMASTER" is not clipped.
3. The tile order after a cold start matches the editor, in Classic too.
4. Each tap lands where the page's does. Sheets open over the page and the cards return; the attribute sheet shows the tapped tile behind it. A luck roll is never spoiled.
5. Native Log save, **pass or fail**: without a level-up the bars never change and the orbs fly on the cards; with a level-up the page's level-up shows, then the cards come back. Never two screens, never a blank one. Record whether a cold first open is showing by 450 ms.
6. Glance: a tap opens Life Director's Today and the sheet, and the running timer ticks each second.
7. App shortcuts launch; a missing app shows its message.
8. The Health card updates after a sync without leaving it; a sync's XP plays on the cards.
9. The mic in the bar opens the HUD visibly on the Overview; a schedule link works.
10. Back: nothing on the Overview; it closes the editor (web in phase 1, native in phase 2); with the cards off, the live shell stays open through Edit, Done and Back.
11. Phase 2: drag feel, haptics at lift, crossing and drop, auto-scroll, hide, reset; TalkBack moves.
12. TalkBack through every card and both bars; linear navigation never reaches the web page behind the screen.
13. Show tab timing: Character → Overview and back. Record the warm-switch numbers and the memory cost (`dumpsys meminfo` before the first open, after it, and after 20 switches) in the catalog.
14. Leave the Overview open for 10 minutes: no warmth, and the pet loop is stopped (`NativeOverviewCards.diagnostics()`).
15. Large text and reduced motion.
16. Tab swipe: with Native tab swipe on, Character ↔ Overview both ways, and the apps row scrolls without switching tabs; with it off, no swipe on the Overview (expected, recorded).
17. Landscape: rotating shows the page's wide Overview, and rotating back brings the cards.

## 6. Risks and mitigations

- **The cat loses its live window in the cards** (feel). Mitigations:
  - the card is a one-tap door to the live room;
  - Classic and hybrid keep the window;
  - phase 3 adds the cat's own sprite.
  The owner decides, and the ADR records it. If the window is essential, the Overview stays hybrid: the room is web by rule.
- **The Overview loses its tab swipe** while the cards are on and Native tab swipe is off. Mitigations:
  - with native swipe on, the Overview is a native neighbour from phase 1;
  - the toggle hint, the MIGRATION row and §2 say so;
  - open decision 5;
  - default-on waits for native swipe to be default too.
- **Three presentations to keep in step** (Classic, hybrid, cards). Mitigations:
  - one data function per card;
  - Classic byte-identity tests;
  - the projection-against-DOM tests;
  - retiring the hybrid after the cards become default (A33).
- **Registry mistakes mean blank or double screens.** Mitigations:
  - a single composite owner and `managed`;
  - the cross-close on every successful open;
  - the gave-up fallback, which never retries;
  - the sim in four modes with the new faults and the three shown mutations.
- **Swaps that remain**: a level-up after a Log save (the page's overlay, then the cards), a celebration the page plays because the cards were not showing, and a cold first open slower than Log's 450 ms. Mitigations: the page and cards are drawn to match; Workout plan and Library share the level-up behaviour; the timing is measured on the phone (check 5).
- **The cold-start swap.** Mitigation: the phase 3 veil; measured in phase 1.
- **Image payload every 15 s, and memory.** Mitigations: decode once per key; payload and memory measured and catalogued (A24); send-once if large.
- **Fidelity: glows, color-mix, elliptical radial gradients, the dashed pace line under its label, 2-line clamps.** Mitigations:
  - core Css helpers (`boxShadow`, `glowPaint`, `mix`, new `cssRadialGradient`);
  - side-by-side review at full resolution, as Life Director's review did.
  Known differences go in the ledger: Roboto and system monospace, 48 dp targets.
- **Overlays the projection does not draw hide under the screen.** Mitigation: the explicit `blocked` and `watch` lists and the two hooks, each with a test. The HUD is fixed for the cards here and filed for every tab.
- **Core lifts touch other tabs** (`PageCelebration` in Workout plan, `PageBuzz` in Life Director, `ClassCrest`/`PageTip`/`PageImages` in Character and Library). Mitigation: their references and tests must stay byte-identical, checked in the same step.
- **Drag-to-reorder has no Compose primitive.** Mitigations: a small `ReorderColumn` over at most 11 rows (no lazy maths); slot logic unit-tested; tuned on the phone.
- **Back ownership (A16).** Mitigations: the handler only while visible and editing; ld2 precedent; a view-model test.
- **Pet seam in pet code.** Mitigations: one predicate in `petWantRun`; pet acceptance tests; the `lm_pet_seen` effect documented.
- **The Life Director seams collide with ld2 and Life Director phases 3-4** (the glance data in logic.js, `DialIntro` in ChronoDial). Mitigation: land after ld2; keep both in Life Director's package on both sides; `DialIntro` is an optional parameter that leaves the Today screen's dial unchanged.
- **Merge conflicts.** settings (settings.html, BOUND, tabkit, a two-line edit to 39-health.js's settings row), ld2 (logic.js, tabkit, sim), swipe (50-native-overview.js NativeTabs, tabkit, ComposeTabPlugin). Mitigations: land after all three, rebase, and take NativeApi at merge.
- **The fixed header costs vertical space versus the hybrid's scroll-away.** This is consistent with every other native tab. It is an owner decision; a scroll-away for the shared header would be a ChromeHost feature for all tabs.
- **Freshness differs:** native refreshes every 15 s, Classic only on save. Deliberate, recorded.

**Open decisions for the owner**
1. Is the cat card (door, then sprite) acceptable instead of the live window?
2. Should the centre's Start/Details buttons on the glance stay "open Life Director" (page parity: `.ld-glance .ld-core{pointer-events:none}`, style.css:628-636), or act directly?
3. Fit "GRANDMASTER" by tightening its letter-spacing (native), instead of clipping as the web does?
4. Fixed header, or a shared scroll-away header later?
5. With the cards on and Native tab swipe off, the Overview has no tab swipe. Accept that (the hint says so), or open the cards only while native swipe is on?

## 7. Effort (agent build steps)

One step is a focused, reviewed commit-sized unit. Recipes took about 4 (web half, screen, two review steps). Library took about 8 (three web phase-0 steps, web half, docs, actions split, screen, review fixes).

| Phase | Steps | Contents |
|---|---|---|
| 0 | 3 (+1 optional) | fixes (the `ovMoves` split in its own commit, tile order, Back, honest saves, the tip's answer); data split with Classic identity; Life Director glance seam after ld2; optional file split |
| 1 | 11 | web half and composite; fixtures; domain, decoder, plugin; cards A; cards B with `PageBuzz`; Life Director glance; celebrations (`PageCelebration` lift, `fxSink` drawing); native swipe neighbour; native Settings row (fixtures, references, row test); docs and sim; review fixes with the TalkBack parity pass |
| 2 | 3 | editor UI and reorder; commands, failure paths and their node tests; TalkBack, Back, screenshots |
| 3 | 4 | arrival motion; `DialIntro` (new Life Director work); veil and the thumbnail skip; cat portrait |
| 4 | 2 | default rollout and ledger; hybrid retirement a release later |
| **Total** | **23 (+1)** | |

Landing order: phase 0 steps 1-2 can land now (web only; the only overlap is settings' two-line edit to 39-health.js's settings row). Step 3 lands after ld2. Everything else lands after settings, ld2 and swipe have merged.

**Not verified** (to settle in the named phase):
- portrait fidelity for DNA, morph and HD cats (phase 3);
- the crest match (phase 1 review);
- image payload size and the memory cost (phase 1);
- One UI haptic feel at the projected amplitudes (phase 1 for the week day, phase 2 for the editor);
- drag smoothness at 120 Hz (phase 2);
- cold-start timing, and whether a cold first open is showing by Log's 450 ms (phase 1);
- whether TalkBack's linear navigation reaches the WebView under a ComposeTabPlugin screen (phase 1; if it does, the fix belongs in ComposeTabPlugin for every tab, and BACKLOG records it).
