# Quests native slice inventory

Baseline: branch `track/docs` at `d56403c0410c73686ce8c5675d16cbe627e2ebd1` (2026-10-30, Native API 23). Read-only audit; no code was changed.

Reference conventions. Paths are relative to `lifemax_modular_v73/src/`. Runtime segments are cited by file name alone (they all live in `runtime/segments/`; for example `04-runtime.js:193`). Segments are byte-cut, so a statement can start in one file and end in the next (for example `let qosCelebrateTimeout=0;` is split across `13-runtime.js:281` and `14-runtime.js:1`). "Verified" means read in the source at this baseline; anything not verified is marked **(unverified)**.

ADR 0003 applies: the 3D quest table (and its opening gate, reading and resting table) stays a WebView scene until Quests' own turn. This inventory therefore separates the **flat Quest Board** (`#view-quests`, a candidate for native presentation) from the **web scenes** (the 3D table, the flat cinematic, the splash gate and the quick riffle), which stay web. Correction to ADR 0003's wording: `50-gym3d.js` is the guided-workout gym (Workout plan tab, header comment `50-gym3d.js:1-4`), not part of Quests.

---

## 1. Summary

**What the tab is for.** Real-life objectives that award XP once per period: three drawn daily cards (the "Daily Hand" or "Fate Deck"), custom dailies, weekly missions, class nutrition missions, epic milestones gated by character level, and a read-only badge display. Completing a quest records a `type:'quest'` activity, so it feeds XP, Sparks, streak, decay timers, badges, voice, Life Director and the journal.

**Page view.** `quests` = `<section id="view-quests" class="view quest-os">` (`views/quests.html:1-91`). One view id; there are no sub-views registered with `navigate`. The web scenes are separate full-screen layers appended to `<body>`: `#fateScene` (3D table `50-quests-table.js:99`, or flat cinematic `50-quests-cine.js:38`), `#dhGate` (flat splash `50-quests-gate.js:11`), `#dailyStage` (quick riffle, inserted inside the board `50-quests-hand.js:231-235`), the WebGL particle canvas `#qfx` (`50-quests-fx.js:34`).

**Entry points (all verified).**

| Entry | Where | Path into the tab |
|---|---|---|
| Nav bar button | `index.template.html:49` (`data-view="quests"`, icon `flag`) | `navigate('quests')` |
| Bottom-bar scrubbing and tab swipe | `42-scrubnav.js:86` (icon), `34-navigation.js:95-106` (`lmaTabList`, `lmaSwipeBlocked`) | `lmaNavTo` |
| Native tab bars (Character, Food, Log screens) | `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/character/presentation/components/CharacterChrome.kt:71` maps `quests` to `flag`; `50-native-tabkit.js:133-136` | native `action` with `view:'quests'` closes the native screen, then `lmaNavTo('quests')` (A23 item 2: web destination) |
| Overview "Review today's quests" button | `views/dashboard.html:22` | `navigate('quests')` |
| Overview "next moves" card | `18-runtime.js:6-7` (`ovMoves`) | daily quests left / complete, `fn:"navigate('quests')"` |
| Back history | `34-navigation.js:69-74` (`lmaBack`), stack kept by `34-navigation.js:78-90` | previous view |
| Deep link `lifemax://open?feature=quests` | `50-deeplink.js:11,51` | `lmvGo({id:'quests'})` -> `lmaNavTo` (`46-voice-nlu.js:121-126`) |
| Launcher shortcut and Assistant "Open quests" | `android-wrapper/res/xml/shortcuts.xml:42-56,71-80`, strings `res/values/lifemax_shortcuts.xml:7-8,29-33` | the deep link above |
| Voice "open my quests" / reply chips | `46-voice-nlu.js:34,66` (navigation table), `lmvOpenChip('quests',...)` in `46-voice-nlu.js:252,271`, `50-voice-gen4.js:276,305,402`, `50-voice-gen5.js:191-193,669,718` | `lmvGo` |
| Cloud voice function "open screen" | `50-voice-gemini.js:17` (enum includes `quests`) | normal request -> `lmvGo` |
| Widgets | none: the home-screen widget is the Life Director dial (`40-widget.js:1-13`) | none |
| Notifications | none for quests: reminders are Life Director sessions, workouts and check-ins (`33-android.js:49-80`). Life Director may schedule sessions derived from quests (`features/life-director/logic.js:2434-2450`), so a reminder can be indirectly about a quest **(unverified in practice)** | none |

**Size.** View markup 90 lines. Core logic about 120 lines across `04-runtime.js:46-47,193-196`, `13-runtime.js:262-281`, `14-runtime.js:2-51`, `43-economy.js:46-56`, `05-runtime.js:14-36`. Quest segments: `50-quests-hand.js` 319 lines, `50-quests-table.js` 403, `50-quests-cine.js` 160, `50-quests-fx.js` 134, `50-quests-cards.js` 93, `50-quests-gate.js` 39, `50-sfx.js` 31. Embedded assets: card art `50-quests-art.js` 4.70 MB (two painted packs as base64 WebP, 9 images each: 1.68 MB and 1.84 MB binary), table mesh and textures `50-quests-table-data.js` 345 KB, casino sounds `50-sfx-data.js` 580 KB (shared with the Luck dice). About 5.6 MB of the 13.7 MB built page is Quests. CSS: about 350 quest lines in `styles/06-quests.css` (lines 110-172 of that file are Meals styles).

**Native risk: high.**
1. Every completion writes XP, Sparks, rested XP, badges, streak and decay inputs into the core save `lifemax_rpg_prototype_v1`, which holds the owner's years of history. There is no receipt and no rollback on save failure (`04-runtime.js:193`).
2. In the default configuration the owner never sees the flat board: deal mode defaults to `table` (`50-quests-cine.js:16`), so every visit opens the 3D table and the flat page is hidden by `html.q3d #view-quests{visibility:hidden}` (`styles/06-quests.css:457`, `50-quests-hand.js:244-251,312-313`). A native flat board only replaces what the owner sees if the deal mode is not the 3D table (see section 8).
3. Hidden web graphics keep running under a covering native screen unless guarded (foil `requestAnimationFrame` loop keyed on `dhViewOn()`, `50-quests-fx.js:101-121`).
4. Logic reads the DOM: `toggleQuest` takes the quest note from `#qnote-<id>` (`04-runtime.js:193`); `createQuest` reads the form inputs (`04-runtime.js:196`).

---

## 2. States

| State | What decides it | Where |
|---|---|---|
| Boot render before the hand segment exists | `renderQuests` calls `dhAutoVerify`/`dhRenderHand` inside try/catch; they are undefined until `50-quests-hand.js` runs, which then calls `dhEnsure();renderQuests()` | `14-runtime.js:35`, `50-quests-hand.js:4-5,319` |
| Onboarding not finished | `#onboarding` overlay covers the app; NativeTabKit also blocks on `!state.onboardingComplete` | `index.template.html:10-36`, `50-native-tabkit.js:56` |
| First visit after the tutorial | Feature tip box inserted at the top of `#view-quests` if `state.tutorialDone && !state.tipsSeen.quests`; dismiss writes `tipsSeen.quests`. In 3D table mode the flat page is hidden, so the tip is never seen there | `13-runtime.js:45-49,93-110`, `13-runtime.js:76-80`, called from `navigate` `04-runtime.js:49` |
| Fresh save | `freshState().quests = defaultQuests` (q1..q6), then `dhEnsure` drops the fixed dailies q1-q3 once (`dhMig`) and `applyClassContent` swaps in class quests | `04-runtime.js:9`, `00-runtime.js:23-30`, `50-quests-hand.js:98-101`, `13-runtime.js:264-270` |
| No class chosen | Class card draws from any attribute (`dhClassAttrs()` null); default weeklies/epic kept | `50-quests-hand.js:83,87`, `13-runtime.js:269` |
| Empty sections | Daily list: empty message only when there are no dailies at all, including hand cards ("No daily missions yet. Forge one of your own."); weekly empty message; epic empty message ("Your first epic milestone awaits."); nutrition card hidden when there are no nutrition quests | `14-runtime.js:39-49` |
| Hand not yet dealt today (`state.dailyDraw.dealt` falsy) | `dhSchedDeal` hides the cards (`fd-pre`) and, after 260 ms on the tab, starts the deal for the current deal mode | `50-quests-hand.js:223-229` |
| Deal mode `table` (default) + WebGL2 + no reduced motion | First visit: 3D scene opens on the gate ("Get your quests" / "Not now"); later visits: 3D resting table. Flat page hidden by class `q3d` | `50-quests-cine.js:16`, `50-quests-hand.js:238-279,312-313`, `50-quests-table.js:95-396` |
| Deal mode `cine` | Flat splash `dhGate` then the flat cinematic `dhCinematic`; afterwards the flat board | `50-quests-hand.js:255-261,279`, `50-quests-gate.js:9-39`, `50-quests-cine.js:32-159` |
| Deal mode `quick` | Riffle in `#dailyStage`, cards fly to their places (FLIP), cards stay face-down until tapped | `50-quests-hand.js:281-301` |
| Deal mode `off` ("Instant") | Cards just show (face-down until tapped); `dealt` set | `50-quests-hand.js:280` |
| Reduced motion (`fxReduced()`) or no `Element.animate` | No deal, `dealt=true` immediately, no 3D, no particles, CSS animations off | `50-quests-hand.js:254`, `07-runtime.js:44`, `styles/06-quests.css:106-107,219` |
| No WebGL2 / table load failure | `dhTable3D` returns `false`: `q3d` removed, flat splash then flat cinematic | `50-quests-hand.js:267-279`, `50-quests-table.js:97,105,111` |
| WebGL context lost (particles) | `QFX` disables itself (`lost=true`) | `50-quests-fx.js:43` |
| Scrubbing the tab bar | Deal waits while the scrub veil is on; 3D layer hidden by `html.sv-on #fateScene` | `50-quests-hand.js:240`, `42-scrubnav.js:104`, `styles/07-finishing.css:98` |
| Left the tab during gate/reading | Scene torn down, `dealt` not set (day not used up) | `50-quests-table.js:211,214,288`, `50-quests-gate.js:37`, `50-quests-hand.js:269,275,306` |
| "Not now" on the 3D gate | `dealt=true`; scene removed. **Likely** leaves the flat page hidden (class `q3d` is only cleared by a one-shot 4 s timer that finds `#fateScene` still present) until the next visit, which opens the resting table. **(unverified on device)** | `50-quests-table.js:231`, `50-quests-hand.js:271,313` |
| 3D resting table: idle / inspecting a card (dock) / ledger open / new-quest panel open / reshuffle | Local variables of the rest promise (`inspecting`, `ledger.hidden`, `newq.hidden`) | `50-quests-table.js:299-389` |
| Hand card states | face-down (`d.up[slot]` false and not done), face-up, turned to its back by a corner (`d.turned[slot]`), done (stamp, no redraw), auto-verified vs self-claimed (by `verify`) | `50-quests-hand.js:163,186-206` |
| Quest row states | done (`is-done`, no note field) / open (note field) | `13-runtime.js:274-280` |
| Epic states | locked (stage > 1 and character level < `stageLvl(stage)`: levels 1,3,5,8,12 for stages 1-5), done, open | `13-runtime.js:272-273`, `14-runtime.js:44-49` |
| Custom-quest form | hidden / shown (`#customQuestCard` `display`) | `views/quests.html:74-78`, `04-runtime.js:194-195` |
| Celebration flash | `#qosCelebration` shown 900 ms after a fresh completion (not with reduced motion) | `14-runtime.js:6-15` |
| New day while the app stays open | No midnight timer re-renders the board. `isQuestDone` switches to today's key at once, but yesterday's hand cards stay in `state.quests` until the next `dhEnsure` (any render, or a save). The 3D resting table polls `isQuestDone` every 450 ms, so after midnight it can show yesterday's cards as not done, and a claim then completes yesterday's card under today's key **(edge case, from code reading)** | `04-runtime.js:46-47`, `50-quests-hand.js:94-114`, `50-quests-table.js:326-333,385` |
| Save failed | `save()` toasts "Could not save to this browser (storage full?)" and returns false; `toggleQuest` ignores it, so memory and storage diverge | `04-runtime.js:13,193` |

---

## 3. Displayed facts

### 3.1 Flat board header, hero and stats

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| Heading | static: "THE ADVENTURE CONTINUES", "Quest Board.", tagline; button "New quest" | markup | none |
| Hero copy | static: "YOUR ADVENTURE LOG", "LIVE", "Small victories. Epic momentum.", caption "YOUR JOURNEY / CHAPTER ONE"; pixel castle SVG (floats, `qosArtFloat` 5.5 s) | `views/quests.html:11-48`, `styles/06-quests.css:29,99` | none |
| `#qosDailyProgress` | `done / total` of today's dailies (includes the 3 hand cards) | `qosUpdateSummary` `14-runtime.js:17-33` | `state.quests`, `state.questDone`, date |
| `#qosDailyFill`, `#qosDailyPct` | percent bar and text | same, `14-runtime.js:21,24,32` | same |
| `#qosMotivation` | "Today's missions cleared. Well played." / "Keep that momentum going." / "Your next quest is waiting." | `14-runtime.js:31` | same |
| `#qosDailyTotal` "ACTIVE TODAY" | count of today's dailies (cadence daily, not nutrition, `questToday`) | `14-runtime.js:25,36` | `state.quests`, weekday |
| `#qosWeeklyTotal` "WEEKLY QUESTS" | count of weekly non-nutrition quests | `14-runtime.js:26,37` | `state.quests` |
| `#qosAvailableXP` "XP AVAILABLE" | sum of nominal `q.xp` of not-done daily, weekly and nutrition quests; ignores caps, perks, rested XP and excludes epics | `14-runtime.js:20,27` | `state.quests`, `state.questDone` |
| Section counts `#qosDailyCount`, `#qosWeeklyCount`, `#qosNutritionCount` | "`n` quests" (no singular form) | `14-runtime.js:28-30` | same |
| Stat icons | `flag`, `calendar`, `trophy`, section icons `sun`, `calendar`, `heart` via `data-px`. `sun` and `calendar` do not exist in `ICONS`, so `px()` draws the `star` fallback (verified: `06-runtime.js:85` `ICONS[n]||ICONS.star`; additions at `06-runtime.js:83` are only check/shield/shuffle) | `05-runtime.js:30` fills `[data-px]` | none |

### 3.2 Daily Hand (flat cards, `#dailyHand`)

Rendered by `dhRenderHand` (`50-quests-hand.js:209-220`) from `dhCardHtml` (`50-quests-hand.js:186-206`); skipped when the signature (`id`, done, up, turned, reroll cost, dealt) is unchanged. Horizontal snap row, card 76 % width, max 300 px, aspect 3:4.7 (`styles/06-quests.css:174-177`).

| Fact | Computed by | Reads |
|---|---|---|
| Which three cards | `dhEnsure` draws once per day (`dhDraw` seeded by date, luck score, nonce, slot) and materialises them as daily quests `dh_<date>_<slot>_<nonce>` | `50-quests-hand.js:84-114`; `state.dailyDraw`, `state.quests`, `state.luck` (`luckToday` `18-runtime.js:104`), `state.activities` (7-day XP per attribute for the Maintenance slot `50-quests-hand.js:66-72`), `state.classId` (Class slot `50-quests-hand.js:6,83`), rest day (`ecoRestDay` `43-economy.js:58-62`: `state.restDays`, plan rest day, 20+ min recovery) excludes tier 3 |
| Template library | 53 templates, 8 attributes; tiers 1/2/3 = 23/22/8 templates, XP 10/20/30; verify types: 31 `self`, 15 `act:type:min`, 3 `water:pct`, 3 `pet:feed|pet|play`, 1 `plan` | `50-quests-hand.js:8-62` |
| Tier odds | luck shifts weight from tier 1 to tier 3; Maintenance slot damps tier 3 | `dhPick` `50-quests-hand.js:74-82` |
| Slot name and numeral | Maintenance / Class / Wildcard, I / II / III | `50-quests-hand.js:161` |
| Card back | painted back image of the active pack plus "TAP TO TURN OVER" (pixel text), or a drawn back (eye sprite, numeral) | `qcardBack` `50-quests-cards.js:78-81` |
| Painted face | art image of the quest attribute; parchment panel at pack-specific `top%/bottom%`; tier pips (3), arcana name (`QPX_ARC`: The Colossus, The Wanderer, The Oracle, The Merchant, The Bard, The Monk, The Fool, The Hermit), numeral, quest name (pixel font, 2 lines, real text in `.sr`), description, "FULFILLED" stamp when done | `qartFace` `50-quests-cards.js:86-93`, `QPX_ARC` `50-quests-cards.js:54-57` |
| Drawn face (fallback when a pack has no art) | numeral, slot, seeded night scene (stars, moon, bats, eye/skull by tier, thorns, candles, attribute icon), arcana name, tier label (MINOR/RARE/MAJOR ARCANA), name, description, pips, attribute short name, "+N XP" | `50-quests-hand.js:197-204`, `qcardScene`/`qcardArt` `50-quests-cards.js:61-76` |
| Dock under each card | state text: "Fulfilled" / "Fulfils itself when you log it" (auto verify) / Hold button (self verify); slot name; "+N XP"; "Redraw · free" or "Redraw · 50 Sparks" (hidden when done) | `50-quests-hand.js:189-191,205` |
| Accent colour per attribute | `--ac` from `QPX_ARC[attr].ac` | `50-quests-hand.js:194` |
| Art pack | chosen pack (`lma_qpack`: `auto`/`classic`/`halloween`); `auto` picks a pack in season (Halloween 10-01 to 11-02, so active on the baseline date); missing images fall back to Classic | `50-quests-art.js:2,5-11,20`; images decoded to Blob URLs on first use `50-quests-art.js:12-19` |
| Foil and tilt (tier 2 and 3 face-up cards; tier 1 tilt only) | WebGL2 shader into a shared 220x340 canvas copied onto each card at about 30 fps; tilt from finger position, else `deviceorientation`, else a slow idle sway | `50-quests-fx.js:81-126` |
| Particles | bursts on reveal, claim, sparks on turn, trails during the deal | `QFX` `50-quests-fx.js:52-79` |
| Hold progress ring | SVG ring filling over 0.9 s while held | `styles/06-quests.css:207-212`, `DH_HOLD_MS` `50-quests-hand.js:5` |

### 3.3 Mission lists

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#dailyQuests` | today's non-hand dailies (custom dailies, class hydration daily) as rows | `renderQuests` `14-runtime.js:36,40`; row `questRow` `13-runtime.js:274-280` | `state.quests` (cadence, kind, `day` vs weekday `13-runtime.js:271`), `state.questDone` |
| `#weeklyQuests` | weekly non-nutrition quests | `14-runtime.js:37,41` | same |
| `#nutritionCard` / `#nutritionQuests` | class nutrition quests (daily ones only on their weekday) | `14-runtime.js:38,42-43` | same |
| Row content | checkbox (done state), name, description, note field "Add a private note (optional)" (max 300, only when open), "+N XP" (nominal) | `13-runtime.js:274-280` | quest object |
| `#achievements` "Epic milestones" | epics sorted by stage: icon `lock`/`trophy`/`flag`, name, "Unlock at level N" or description, "+N XP", checkbox unless locked | `14-runtime.js:44-49` | `state.quests`, `state.questDone`, character level (`characterLevel` `04-runtime.js:17`) |

### 3.4 Badges (rendered by Character/Overview code, shown on this tab)

| Element | Shows | Computed by | Reads |
|---|---|---|---|
| `#badgeCount` | `earned/13` | `renderExtras` `05-runtime.js:35` | `state.badges` |
| `#badges` | 13 badges (`BADGES` `05-runtime.js:14-28`), earned with icon, locked with `lock`; name and description; `title` tooltip | `05-runtime.js:36` | `state.badges` (ISO dates written by `checkBadges` `05-runtime.js:29` during every `save`) |

### 3.5 Transient feedback

| Feedback | Trigger | Where |
|---|---|---|
| "QUEST CLEARED / +N XP EARNED" flash | fresh completion via `qosToggle`; shows nominal `q.xp`, even when caps made the award 0 or rested XP/perk made it larger | `14-runtime.js:6-15`, CSS `styles/06-quests.css:89-94,101-102` |
| Toasts | "Quest complete · +N XP", "Quest XP limit reached for today/this week", "One epic quest reward per day", "Quest undone · XP removed", "Locked until you reach level N", "Give your quest a name", "Quest created", "Finished cards cannot be rerolled", "Not enough Sparks", "Rerolled for 50 Sparks", "Free reroll used", "Badge unlocked: …" | `04-runtime.js:193,196`, `50-quests-hand.js:120,129`, `44-rewards.js:76`, `05-runtime.js:29` |
| XP orbs, "+N XP" float, XP sound, haptic | any save that raised attribute XP (`checkLevelUps`) | `05-runtime.js:8-12`, `celebrateXP` `07-runtime.js:77-81`, origin `lastTap` `07-runtime.js:46-47` |
| Level-up overlay `#levelup` | level gained | `showLevelUp` `07-runtime.js:83` |
| Card claim burst, haptic "success", chip sounds | a hand card turned done while the tab is active | `50-quests-hand.js:217` |

### 3.6 Web scenes (stay web under ADR 0003)

| Scene | Shows | Where |
|---|---|---|
| 3D gate (first visit, table mode) | table floating in the dark with the deck, candles, embers; "A NEW DAY. A NEW DRAW.", "YOUR CARDS ARE WAITING", "Three quests, drawn from the dark."; buttons "GET YOUR QUESTS", "NOT NOW" | `50-quests-table.js:100,223-239` |
| 3D reading | camera dolly, typed captions "THE TABLE IS SET", "THE DECK IS SHUFFLED", "THE CARDS ARE LAID", "YOUR CARDS AWAIT", "TURN THE CARD"; deck drop, two riffles, three cards laid, each lifted and turned by finger (arcball), arcana name and tier title; SKIP button | `50-quests-table.js:240-285` |
| 3D resting table (the board in table mode) | three face-up cards with painted faces drawn to canvas (`t3face` 640x1120, `50-quests-table.js:61-75`), progress "`n` / `t` FULFILLED" with pips and "WEEKLY `wd` / `w`", bar LEDGER / NEW QUEST / RESHUFFLE | `50-quests-table.js:299-318` |
| Ledger panel | sections WEEKLY MISSIONS, YOUR OWN QUESTS (non-hand dailies today), NUTRITION, EPIC MILESTONES, BADGES `earned / 13` (earned names only) | `50-quests-table.js:358-366` |
| Flat splash (`cine` mode) | eye, candles, embers, same texts, "GET YOUR QUESTS", "NOT NOW" | `50-quests-gate.js:9-39` |
| Flat cinematic (`cine` mode, or table fallback) | fog shader background, deck, riffle, three cards, flip-by-drag, titles, SKIP | `50-quests-cine.js:4-159` |
| Quick riffle (`quick` mode) | 10 backs riffled three times, then the three cards fly into place | `50-quests-hand.js:281-301` |

---

## 4. Actions

Common to every completion path: `toggleQuest(id,checked)` (`04-runtime.js:193`) is the only quest XP owner. On complete it refuses locked quests, sets `state.questDone[questKey]=activityId`, computes XP with `ecoQuestXP` (`43-economy.js:46-56`: daily 3 per day, weekly 3 per rolling 7 days, epic 1 per day; daily +15 % with the `compound` perk; consumes `state.restedXP`; daily/weekly pass through the daily soft/hard cap 180/240, epics do not), pushes a `type:'quest'` activity (note from `#qnote-<id>`), then `save()`. On undo it deletes the key, removes the activity, subtracts nominal `q.xp` (cosmetic: `save` rebuilds XP from activities, `04-runtime.js:13,19-32`), and saves. `save()` then runs, in order: `rebuildAttrXP`, `rebuildSkillXP`, `ecoMigrate`, `ecoSparksSync` (+Sparks equal to XP for each new activity, never taken back, `44-rewards.js:13-16`), `checkBadges`, `checkLevelUps`, `localStorage.setItem`, `renderAll`; then the wrappers `lmaSoon` (reminder resync, `33-android.js:182`) and `dhAutoVerify` (`50-quests-hand.js:152-153`). A quest activity also counts for the streak (`05-runtime.js:13`) and resets the decay clock of the quest's attribute (custom quests default to `discipline`) (`lastTrained` `05-runtime.js:45`).

### 4.1 Flat board

| Control | Handler | Reads | Writes | Side effects | Opens |
|---|---|---|---|---|---|
| "New quest" (header) | `showCustomQuest` `04-runtime.js:194` | none | DOM only | focuses the name input | inline card `#customQuestCard` (4.2) |
| Quest row checkbox (daily, weekly, nutrition) | `qosToggle(id,checked)` `14-runtime.js:2-16` -> `toggleQuest` | quest, lock, `#qnote-<id>` value | see common path | flash, toasts, orbs, level-up, badge toast, hand burst if a hand card, reminder resync | none |
| Quest note field | none (plain input, max 300) | none | none until completion; the value is wiped by any re-render, and every `save` anywhere re-renders | none | none |
| Epic checkbox (unlocked only) | `qosToggle` `14-runtime.js:48` | same | same; epic XP limited to 1 epic per day and exempt from the daily cap | same | none |
| Card back tap / Enter / Space | `dhReveal(slot)` `50-quests-hand.js:173-180` | `state.dailyDraw` | `dailyDraw.up[slot]=true`, written straight to `localStorage` (no `save`, no render) | haptic `double`, sound `card-slide`, tier burst, foil kick. If the card is already up and turned, it turns it back instead (not idempotent) | none |
| Card corner (4 per card, face-up only) | `dhTurn(slot)` `50-quests-hand.js:165-172` | same | toggles `dailyDraw.turned[slot]`, direct `localStorage` write | haptic `tap`, sound, sparks | none |
| Hold button (self-verify cards) | pointerdown `dhHoldStart` / up, leave, cancel `dhHoldEnd` (`50-quests-hand.js:155-160,190`); Enter/Space claims at once | quest | after 900 ms: `qosToggle(id,true)` | haptic `success` then the common path | none |
| "Redraw · free / 50 Sparks" | `dhReroll(slot)` `50-quests-hand.js:117-131` | `dailyDraw`, `state.sparks` | first redraw of the day free, then `ecoSpend(50)` (`44-rewards.js:76`); `rerollsUsed++`, `nonce++`, new template (excluding the current three), replaces the quest, `up[slot]=false`; `save()` | toast; new card face-down. Not idempotent: a repeat spends again | none |
| "↻ Shuffle again" | `dhReplayDeal` `50-quests-hand.js:310` | deal mode | replays the deal in the current mode; `quick` resets `up` to all false | sound `ui-bell`; table mode opens the full 3D reading | web scene |
| Swipe on the card row | browser scroll (`overflow-x:auto`, snap); tab swipe is blocked inside scrollers (`34-navigation.js:97-106`) | none | none | tilt follows the finger (`50-quests-fx.js:124`) | none |
| Feature tip dismiss | `dismissFeatureTip('quests')` `13-runtime.js:76-80` | none | `state.tipsSeen.quests=true`, direct `localStorage` write | removes the tip | none |

### 4.2 Inline custom-quest card `#customQuestCard` (`views/quests.html:74-78`)

| Field / control | Detail | Handler |
|---|---|---|
| Quest name `#newQuestName` | free text, no max length; required ("Give your quest a name") | read by `createQuest` |
| Cadence `#newQuestCadence` | Daily / Weekly / Epic (one-time) | same |
| XP `#newQuestXP` | number, default 25, clamped 1..500 (0 or invalid becomes 25) | same |
| "Create quest" | pushes `{id:'custom_'+Date.now(), name, desc:'Custom quest created by you.', cadence, xp, kind:'custom'}` (no attribute, so `discipline` is used on completion; no `day`, so shown daily), clears the name, hides the card, `save()`, toast | `createQuest` `04-runtime.js:196` |
| "Cancel" | hides the card | `hideCustomQuest` `04-runtime.js:195` |

There is no edit or delete for custom quests anywhere in `src` (verified: the only `state.quests` writers are `createQuest`, the 3D panel, `dhEnsure`, `dhReroll` and `applyClassContent`).

### 4.3 3D table scene (web; listed so that nothing is lost)

| Control | Handler | Writes / effects |
|---|---|---|
| GET YOUR QUESTS | `50-quests-table.js:211,232-239` | sounds, burst, camera in, then the reading |
| NOT NOW | `50-quests-table.js:211,231` | returns `skip`: `dealt=true` (`50-quests-hand.js:306`) |
| SKIP | `skip` `50-quests-table.js:183-184` | finishes every tween |
| Drag to turn a lifted card (reading) | `flipByHand` `50-quests-table.js:191-208` | settles front or back by flick velocity; front ends the step |
| Tap a resting card | `inspect` `50-quests-table.js:348-352,380-384` | lifts it, free spin by drag (`spinFree` `50-quests-table.js:336-347`), dock shown |
| Dock Hold | `dhHoldStart` (same as flat) `50-quests-table.js:311` | claim, then the 450 ms poll retextures the card with a burst (`50-quests-table.js:319-333`) |
| Dock Redraw | `dhSceneRedraw` -> `dhReroll` `50-quests-table.js:312,369,399` | as flat redraw |
| Dock ✕ / tap outside | `dhSceneBack` -> `release` `50-quests-table.js:313,353-356,383,398` | card back to its slot |
| LEDGER / ✕ | `api.ledger` `50-quests-table.js:370,378,400` | panel |
| Ledger row button | `dhLedgerToggle` -> `qosToggle(id,!done)` `50-quests-table.js:359,374,403` | common path (can undo) |
| NEW QUEST panel: name (max 60), cadence Daily/Weekly only, XP 1..500; CREATE / CANCEL | `50-quests-table.js:367,372-373,401-402` | pushes a custom quest exactly like `createQuest` (but no Epic option), `save()`, toast, opens the ledger |
| RESHUFFLE | `api.again` `50-quests-table.js:375,378` | full 3D reading again (`50-quests-hand.js:250`) |
| Leaving the tab | interval polls `dhViewOn()` (200/250 ms) | tears the scene down |

On every finished reading the scene writes `dealt=true` and `up=[true,true,true]` straight to `localStorage` (`50-quests-table.js:293`).

### 4.4 Settings that change this tab (Settings, Look & feel, "Quest effects" card, `views/settings.html:25-31`, `50-settings-tabs.js:3`)

| Control | Handler | Stores |
|---|---|---|
| Card effects | `qfxSet` `50-quests-fx.js:133` | `localStorage.lma_qfx` (`off` disables particles and foil; does not disable the 3D table) |
| Quest and dice sounds | `qsfxSetVol` `50-sfx.js:30` | `localStorage.lma_sfxvol` (also needs `state.fx.sound`) |
| Card deal: 3D table / Classic cinematic / Quick / Instant | `dhSetDealMode` `50-quests-cine.js:17` | `localStorage.lma_qdeal` (default `table`) |
| Card art | `qartSetPack` `50-quests-art.js:9` | `localStorage.lma_qpack` (default `auto`) |
| Reduce effects, Sound, Haptics | elsewhere in Settings | `state.fx` (`07-runtime.js:43-44`, `50-haptics.js:51`) |

### 4.5 Writers outside this tab

| Writer | What it does to quest data | Where |
|---|---|---|
| Any `save()` anywhere (Log, Plan, Food water, cat care, Life Director, voice, health sync) | `dhAutoVerify` completes hand cards whose `verify` is met (logged activity of the type and duration, water percentage of `state.mealsMeta.waterGoalMl` from `state.waterLog`, cat fed/petted/played today, plan day done) | `50-quests-hand.js:133-153` |
| Voice "complete the first one" / "uncheck" after a quest list | `toggleQuest(id,on)` then `save()` (no flash, no hold; can complete auto-verify cards) | `50-voice-gen5.js:184-191` |
| Voice "check off everything here" (on the Quests view) | confirm card, then `toggleQuest` for each open daily and weekly (up to 10) | `50-voice-gen5.js:624,668-669` |
| Voice undo | `toggleQuest(id,false)` | `50-voice-gen5.js:32` |
| Life Director bounty | `H.completeQuest` checks the period key and done state, then `toggleQuest(id,true)` | `features/life-director/logic.js:2527-2535` |
| Class switch | `switchClassTo` -> `applyClassContent` (replaces `q1-q6` and `cc_*` quests) -> `save` -> `renderQuests` | `14-runtime.js:121`, `13-runtime.js:264-270` |
| Load | `applyClassContent` on every load | `04-runtime.js:12` |
| `deleteActivity` | removes `questDone` entries pointing at the activity. No caller in `src` (verified by grep) | `04-runtime.js:197` |
| Import / reset / delete all | replace the whole state | `04-runtime.js:265-267` |
| Native Cash failure path | restores the whole state from a string taken before the command | `50-native-cash.js:48` |

---

## 5. Cross-tab couplings

### 5.1 Function wrappers and reassignments

| Function | Wrapped/reassigned by | Effect |
|---|---|---|
| `save` (`04-runtime.js:13`) | `33-android.js:182` (reminder resync after), `50-quests-hand.js:152-153` (`dhAutoVerify` after, guarded by `dhBusy`), `32-pet-dna.js:627,636` (temporarily a no-op during adoption) | every save may complete hand cards and re-save |
| `navigate` (`04-runtime.js:49`) | 18 segments (CONTRACT A30). Quests-relevant: `50-quests-hand.js:315-316` (adds/removes `q3d` before navigation; `dhRenderHand` 120 ms after arriving), `34-navigation.js:78-90` (Back stack, bar), `42-scrubnav.js:199-213`, `48-journal-lock.js:79`, `50-native-overview.js:36,146` (heal, `native-cover`), `50-native-tabkit.js:192-201` (one per native tab), `31-overview-edit.js:151` | load order decides wrapper order; a native Quests tab adds one more |
| `renderQuests` (`14-runtime.js:34`) | not reassigned today. A NativeTabKit tab with `render:'renderQuests'` would reassign `globalThis.renderQuests` (`50-native-tabkit.js:202-209`); callers use the global name (`04-runtime.js:182,193`, `14-runtime.js:121`, `50-quests-hand.js:319`) | refresh hook works |
| `lmaBuzz`, `haptic` | `50-haptics.js:30-46` | hand haptics go through `LifeMaxHaptics.play` (`hapPlay` `50-haptics.js:47-50`, export `50-haptics.js:75`) |
| `toggleQuest`, `qosToggle`, `isQuestDone`, `questKey`, `questRow`, `dhMaybeDeal` | never reassigned (verified; single definitions in `runtime/symbol-index.json`) | note: `window.LifeMaxDailyHand` captured `dhMaybeDeal`, `dhReroll` and others by reference at `50-quests-hand.js:317`, so wrappers added later are bypassed through that object (tests use it) |
| `renderQuests` is reached on every `save` via `renderAll` (`04-runtime.js:182`) | | so every save in the app runs `dhAutoVerify` and `dhRenderHand` |

Exported globals: `window.LifeMaxDailyHand` (`50-quests-hand.js:317`), `QFX` (`50-quests-fx.js:7`), `QSFX` (`50-sfx.js:4`), `window.dhRest` while the resting table is up (`50-quests-table.js:377,390`), the `dhScene*` and `dhLedgerToggle` helpers used by inline `onclick` (`50-quests-table.js:398-403`).

### 5.2 Timers, loops and global listeners

| What | Period / trigger | Where |
|---|---|---|
| Deal start delay | 260 ms after arriving (rest table: 40 ms) | `50-quests-hand.js:224-228` |
| `q3d` safety removal | one shot, 4 s | `50-quests-hand.js:313` |
| Scrub wait retry | 300 ms | `50-quests-hand.js:240` |
| Hold to claim | 900 ms | `50-quests-hand.js:158` |
| Celebration flash | 900 ms | `14-runtime.js:13` |
| Foil loop | `requestAnimationFrame`, drawn at most every 33 ms while tier 2/3 face-up cards exist and `dhViewOn()`; stops when the page is hidden | `50-quests-fx.js:101-121` |
| Particle loop | `requestAnimationFrame` until the last particle dies | `50-quests-fx.js:55-61` |
| Flat splash | embers every 260 ms, leave check every 250 ms | `50-quests-gate.js:25,37` |
| 3D scene | render loop every frame (resting and idle: about every 48 ms); leave check 200/250 ms; gate embers 300 ms; resting: card sync 450 ms, leave 250 ms, embers 700 ms | `50-quests-table.js:182,211,214,228,385-387` |
| Cinematic | render loop every frame | `50-quests-cine.js:45` |
| Document `pointermove`/`pointerup` | always registered (tilt) | `50-quests-fx.js:124-125` |
| Window `deviceorientation` | always registered from boot | `50-quests-fx.js:126` |
| Window `resize` | after the particle canvas exists | `50-quests-fx.js:44` |
| Document `pointerdown` (capture) | records `lastTap` for XP orbs | `07-runtime.js:47` |
| `visibilitychange` | decay may apply and save on return (re-renders the board) | `11-runtime.js:41` |
| Settings sync | `setTimeout(...,0)` fills `qfxToggle`, `qsfxVol`, `qdealSel`, `qpackSel` | `50-quests-fx.js:134`, `50-sfx.js:31`, `50-quests-cine.js:160`, `50-quests-art.js:21` |

No `MutationObserver`, no IndexedDB, no background execution (verified by reading the quest segments).

### 5.3 Other tabs that read this tab's data

| Reader | Reads | Where |
|---|---|---|
| Overview | `achievementCount` and `questsStat` (= `Object.keys(state.questDone).length`, all time), "Quests completed" today, next-move card | `04-runtime.js:180`, `views/dashboard.html:21-22,28`, `18-runtime.js:6-7` |
| Character report / export summary | "Quests completed" all time | `05-runtime.js:78` |
| Badges | "Quest Taker" = 5 completions | `05-runtime.js:25` |
| Journal coach | quest activities per day (`QUEST_HUNTER` day type at 3+) | `47-journal-coach.js:44,87,97,326,332` |
| Voice | quest lists, counts, goals, morning brief, "what's left" | `46-voice-nlu.js:240-252,257,271`, `50-voice-gen4.js:230-246,274,305,400-404`, `50-voice-gen5.js:141-147,160,177,219,715` |
| Life Director | all quests as its own quest records (milestones, weekly counts by `questKey`), completion bounty | `features/life-director/logic.js:2338-2450,2527-2535` |
| Streak, decay | quest activities count as training | `05-runtime.js:13,45` |
| Economy | quest activities count toward the daily XP cap and give Sparks | `43-economy.js:18`, `44-rewards.js:13-16` |

This tab reads from others: luck (Character), plan rest days and plan completion (Workout plan), water log and goal (Food), cat state (Cat), activities (Log, health sync), class (onboarding/Settings), Sparks and perks (shop).

---

## 6. Persistence

| Store | Key / field | Schema | Writers |
|---|---|---|---|
| Core save `localStorage['lifemax_rpg_prototype_v1']` (`01-runtime.js:46`) | `quests[]` | `{id, name, desc, cadence:'daily'|'weekly'|'epic', xp, kind, attribute?, stage?, day?:'any'|'mon'..'sun'}`; hand cards add `{hand:true, slot, tpl, verify, tier}` with `kind:'hand'`; custom `{kind:'custom'}`. Class sets: athlete 21 quests (6 daily, 5 weekly, 10 epic, stages 1-5), scholar/entrepreneur/explorer 35 each (19 daily incl. 5 nutrition, 6 weekly, 10 epic); class non-nutrition, non-hydration dailies are dropped in favour of the Hand | `13-runtime.js:264-270`, `50-quests-hand.js:94-131`, `04-runtime.js:196`, `50-quests-table.js:373` |
| | `questDone{}` | `"<id>_<YYYY-MM-DD>"` (daily), `"<id>_<Monday YYYY-MM-DD>"` (weekly), `"<id>_once"` (epic) -> activity id `quest_<ms>`; never pruned | `04-runtime.js:46,193,197` |
| | `activities[]` quest records | `{id:'quest_<ms>', cad, epic, skill:'Quest Completion', type:'quest', name:'Quest: …', icon:'flag', duration:0, xp, primary, date ISO, notes, xpSplit:[{attr,xp}], sp:1}` | `04-runtime.js:193`, `44-rewards.js:14` |
| | `dailyDraw` | `{date, nonce, rerollsUsed, cards:[{tpl,id,n}x3], up?:[bool x3], turned?:[bool x3], dealt?:bool}`; `up`, `turned`, `dealt` are presentation flags written directly to storage | `50-quests-hand.js:103-107,123,127,166-167,174-175,254,260,306`, `50-quests-table.js:293`, `50-quests-cine.js:144` |
| | `dhMig`, `classContent` | migration markers | `50-quests-hand.js:98-101`, `13-runtime.js:270` |
| | `badges{}`, `sparks`, `restedXP`, `attrXP`, `skillXP`, `tipsSeen.quests`, `perk`/`perksOwned`, `fx` | shared fields this tab changes or reads | `05-runtime.js:29`, `44-rewards.js:13-16,76`, `43-economy.js:19-23`, `04-runtime.js:19-40`, `13-runtime.js:78` |
| Device-only `localStorage` | `lma_qdeal`, `lma_qfx`, `lma_qpack`, `lma_sfxvol` | strings | section 4.4 |
| Android prefs `lifemax_android_prefs_v1` (`33-android.js:3,25`) | none for Quests today; a native preview would add a key (for example `nativeQuests`) | | |
| IndexedDB | none | | |

Migrations: `load()` merges the saved object over `freshState()` (`04-runtime.js:12`); `applyClassContent` on every load and class change; `dhMig` one-time; daily cleanup drops earlier days' hand quests (their history stays in `questDone` and `activities`, `50-quests-hand.js:102`); `ecoMigrate` (`43-economy.js:71-85`) marks all activities as already paid in Sparks. There is no quest schema version.

Sizes (measured from synthetic records, not from the owner's save): a quest activity is about 300 bytes of JSON, a `questDone` entry about 52, a hand quest about 234, `dailyDraw` about 254. At five completions a day the save grows by about 1.75 KB a day, about 640 KB a year, inside the same `localStorage` quota as everything else. The owner's real size is unknown.

---

## 7. Native readiness

### 7.1 Easy (projection plus Compose)

Header copy, progress bar and the three stats; mission rows (name, description, nominal XP, done); epic rows with lock and unlock level; badge grid (13 items, `PixelIcon` already has `flag`, `trophy`, `lock`, `heart`, `star` and the badge icons in `core/designsystem/components/PixelIcon.kt`; `sun` and `calendar` are absent there too, which matches the web's star fallback); empty states; light/dark tokens; the static castle art (a vector path); counts and percentages from the projection.

### 7.2 Medium

| Item | Why |
|---|---|
| Hand cards | painted art must ship as Android resources from `lifemax_modular_v73/art/cards/packs/*` (never through the bridge: 1.7 MB per pack); the pack choice and season rule stay web, so the projection names the resolved pack per image (`qartSource`); parchment panel offsets per pack and attribute (`QART_PACKS[pack].panel`); pixel font (`QPX_FONT` 5x7, about 60 glyphs, `50-quests-cards.js:3-38`) and sprites ported as presentation code; drawn fallback face only needed if a pack lacks art |
| Card flip and corner turn | `graphicsLayer.rotationY`; reduced-motion path; `up` and `turned` live in the core save today |
| Snap carousel | `LazyRow` with snap; tab swipe must not fire inside it |
| Hold to claim | 900 ms press with a progress ring; accessible alternative (TalkBack custom action), as the web gives Enter/Space |
| Text input | custom quest form (name, cadence, XP 1..500) and per-quest notes (max 300). Notes are read from the DOM by `toggleQuest`, so a native note needs a command that passes it |
| Celebration | the web flash and XP orbs play in the WebView; a native command must hold them (`holdCelebrations` `07-runtime.js:76`, as Log does `50-native-log.js:93-104`) |
| Freshness | invalidation on every `save` (via `renderQuests`), on resume and on day change. The projection must notice `state.dailyDraw.date !== getToday()` and let the page re-render instead of showing yesterday's hand |

### 7.3 Hard (stays web at first)

3D table (WebGL2, OBJ mesh, PBR-lite shading, arcball flip, ray-quad hit test, `50-quests-table.js`); cinematic fog shader and flip-by-drag (`50-quests-cine.js`); GPU particles and holographic foil with device tilt (`50-quests-fx.js`); riffle and FLIP deal (`50-quests-hand.js:281-301`); casino sounds (`50-sfx.js`, 51 clips; native would need bundled OGG in SoundPool); seeded draws, luck odds, weakest-attribute targeting and auto-verification (`50-quests-hand.js:63-153`); every XP, Sparks and badge rule.

### 7.4 Web dependencies that stay delegated in phase 1

`qosToggle`/`toggleQuest` (all completion and undo), `dhReroll` (spends Sparks), `createQuest` and the inline form, quest notes, `dhReveal`/`dhTurn` (save-resident flags plus web sound/haptics), `dhReplayDeal` and every deal scene (gate, 3D reading and resting table, cinematic, riffle), `dhEnsure`/`dhAutoVerify`, `checkBadges`, celebrations, the four Quest settings.

### 7.5 Hazards a native screen must respect

1. **The board's default face is a web scene.** With `lma_qdeal` = `table`, WebGL2 and no reduced motion, every visit opens `#fateScene`. A native screen opened over it would hide a live WebGL scene that keeps polling and rendering. `blocked()` must keep native closed while any scene is up or wanted.
2. **Foil keeps rendering under a cover.** `foilFrame` runs while face-up tier 2/3 cards exist in `#dailyHand` and `dhViewOn()` is true; `#view-quests` stays `.active` under a native screen (`50-quests-fx.js:101-121`). Proposed guard: while the native screen is open, the projection segment makes `dhViewOn()` return false (it is a global function called by name from `50-quests-fx.js:103`, `50-quests-hand.js:217,224-228,242`, `50-quests-gate.js:37`, `50-quests-table.js:98,211,214,386`), which also stops deal scheduling.
3. **Projection must not write.** `renderQuests` writes (`dhEnsure` creates cards, `dhAutoVerify` completes quests and saves). The projection reads `state` only; the page's own render path stays the writer.
4. **No idempotency today.** `toggleQuest(id,true)` is safe to repeat for the same period (it checks `questDone`), but completing, undoing and completing again pays Sparks every cycle, because undo removes the activity (so the per-day caps reset) and Sparks are never taken back (`44-rewards.js:13-16`). A custom epic of 500 XP is exempt from the daily cap (`43-economy.js:18,49`). `dhReroll` repeated spends 50 Sparks again. Any native command inherits this; A14 receipts are required before it ships.
5. **Save failure is silent for quests.** `toggleQuest` does not check `save()`'s result (`04-runtime.js:193`), unlike `recordActivity` (`04-runtime.js:192`). A native command must report "outcome unknown" honestly (A14). (`recordActivity` is at `04-runtime.js:191`.)
6. **Back.** `lmaTopOverlay` knows none of the quest layers (`34-navigation.js:54-68`); Back during a scene changes tab and the scene's leave poll tears it down. A native screen's Back must go through the shared coordinator (A16).

### 7.6 Proposed phasing

- **Phase 0 (owner decision, section 8 question 1).** Decide whether native replaces the flat board only (shown when deal mode is not the 3D table) or becomes the resting board, with the 3D table kept for the daily reading and "Reshuffle".
- **Phase 1: presentation only, opt-in ("Native Quests preview").** Native draws the flat board from the projection. It opens only when no web scene is up or wanted. Every action delegates by closing the screen and running the page handler (NativeTabKit's `action` path), then `revisit()` reopens when the page is calm. No native writes.
- **Phase 2: commands behind A14/A15.** `setQuestDone`, `claimCard`, `createQuest`, `reveal`/`setTurned`, each with an operation id stored where its effect lands, period checks and held celebrations.
- **Phase 3: Sparks.** `reroll` with expected-cost and card-id checks and a receipt.
- **Phase 4: Quests' turn under ADR 0003.** Spike Compose versus SurfaceView/GL versus embedded WebView for the table, using the screenshot and timing tools.

### 7.7 Phase 1 sketch (proposal; nothing here is implemented)

`50-native-quests.js`, after `50-native-tabkit.js` and after the quest segments:

```js
NativeTabKit.create({
  id: "quests", plugin: "NativeQuests", capability: "quests.projection.v1",
  setting: "nativeQuests", toggle: "nativeQuestsToggle",
  projection, onAction, render: "renderQuests",
  blocked,                       // the page must stay visible
  globals: { open: "openNativeQuests", enable: "setNativeQuestsEnabled", projection: "NativeQuestsProjection" },
});

function blocked() {
  const d = state.dailyDraw;
  return dhDealing || dhGating
    || !!document.querySelector("#fateScene, #dhGate, #dailyStage:not([hidden])")
    || dhQ3dWant()                                   // 3D table mode: the resting board is the web table
    || !d || d.date !== getToday()                   // stale hand: let the page render first
    || (!d.dealt && dhDealMode() !== "off" && !fxReduced())   // today's deal ceremony is web
    || document.getElementById("customQuestCard").style.display === "block"
    || !!document.activeElement?.closest?.("#view-quests input, #view-quests select");
}
// revisit(): after dhMaybeDeal settles (wrap the global; LifeMaxDailyHand.deal keeps the original),
// after hideCustomQuest/createQuest, and from the renderQuests hook.
```

Projection fields (all computed with existing page functions; no writes):

```json
{
  "date": "2026-10-30",
  "header": { "done": 2, "total": 4, "pct": 50, "motivation": "Keep that momentum going.",
              "activeToday": 4, "weekly": 6, "nutrition": 0, "availableXP": 95 },
  "hand": {
    "dealt": true, "rerollCost": 0, "rerollsUsed": 0, "freeRerolls": 1,
    "cards": [ { "slot": 0, "slotName": "Maintenance", "numeral": "I", "id": "dh_2026-10-30_0_0",
                 "tpl": "t2", "name": "Proper strength session", "desc": "25 minutes of resistance training.",
                 "attribute": "physique", "attributeShort": "PHY", "arcana": "The Colossus", "accent": 4292891210,
                 "tier": 2, "tierLabel": "RARE ARCANA", "xp": 20, "auto": true,
                 "done": false, "up": true, "turned": false,
                 "art": { "pack": "halloween", "face": "physique", "back": "back", "panel": [69.9, 12.2] } } ]
  },
  "daily":     [ { "id": "custom_1700000000000", "name": "…", "desc": "…", "xp": 25, "done": false, "kind": "custom" } ],
  "weekly":    [ { "id": "cc_scholar_21", "name": "…", "desc": "…", "xp": 35, "done": true } ],
  "nutrition": [],
  "epics":     [ { "id": "cc_scholar_30", "name": "…", "sub": "Unlock at level 5", "xp": 75,
                   "done": false, "locked": true, "unlockLevel": 5 } ],
  "badges":    { "earned": 7, "total": 13,
                 "items": [ { "id": "quest", "name": "Quest Taker", "desc": "Complete 5 quests", "icon": "flag", "earned": true } ] },
  "fx":        { "reduced": false, "dealMode": "quick" },
  "tabs":      [ { "id": "dashboard", "label": "Overview" } ]
}
```

Phase 1 delegated actions (`onAction`, run after the kit has closed the screen):

| Native intent | Page handler | Reopen |
|---|---|---|
| `toggle {id, checked}` | `qosToggle(id, checked)` (the page shows its flash) | `revisit()` after the celebration (about 1 s) |
| `claim {id}` (after the native 900 ms hold) | `qosToggle(id, true)` | same |
| `reveal {slot}` / `turn {slot}` | `dhReveal(slot)` / `dhTurn(slot)` (check state first: `dhReveal` toggles back a turned card) | `revisit()` |
| `reroll {slot}` | `dhReroll(slot)` | `revisit()` |
| `newQuest` | `showCustomQuest()`; `blocked()` stays true until the card is hidden | after `hideCustomQuest`/`createQuest` |
| `note {id}` | focus `#qnote-<id>` in the page | when the field loses focus |
| `replay` | `dhReplayDeal()` (web scene) | after `dhMaybeDeal` settles |
| `epicToggle {id, checked}` | `qosToggle` | `revisit()` |

Phase 2 commands (each needs an ADR and the A14 tests; sketched for the catalog):

| Command | Payload | Owner call | Guards |
|---|---|---|---|
| `setQuestDone` | `{id, done, period, note?, opId}` | `toggleQuest` path, note passed in instead of read from the DOM | quest exists; `questKey(q) === period` (as Life Director does, `logic.js:2531`); not locked; desired-state idempotency; receipt; held celebrations; reply carries the XP actually recorded on the activity |
| `claimCard` | `{slot, cardId, period, opId}` | `setQuestDone` for a self-verify hand card | card id still in the slot |
| `createQuest` | `{name, cadence, xp, opId}` | `createQuest` rules (clamp 1..500, name required) | opId stored on the quest |
| `reveal` / `setTurned` | `{slot, cardId, value}` | set `dailyDraw.up`/`turned` | idempotent by value; no effects |
| `reroll` | `{slot, cardId, expectedCost, opId}` | `dhReroll` | cost and card unchanged; receipt (spends Sparks) |

Catalog entry to add when phase 1 ships (`BRIDGE_CATALOG.json`): `feature: quests`, `status: native-read-only-preview`, `capabilities: [quests.projection.v1]`, `writes: []`, `delegatedInteractions: [qosToggle, dhReveal, dhTurn, dhReroll, showCustomQuest/createQuest, quest notes, dhReplayDeal and every deal scene]`, invalidation "renderQuests (every save), resume, 15 s while visible, day change", memory cost measured with the WebView alive (ADR 0003).

Tests to add: a node projection test with fixtures for a fresh save, a dealt day, a stale day, locked epics, an empty class, a completed hand; a blocked() matrix over the four deal modes, reduced motion and no WebGL2; the hand-over simulation with Quests as a native tab; a screenshot test per state.

---

## 8. Open questions for the owner

1. **Which board should native replace?** With the default "3D table" deal, you always see the 3D table and never the flat board. Should the native Quests screen (a) replace only the flat board, used when the card deal is set to Classic cinematic, Quick or Instant, or (b) become the everyday board, with the 3D table kept for the first reading of the day and "Reshuffle"?
2. **Undo and Sparks.** Completing a quest, undoing it and completing it again gives Sparks every time (undo does not take Sparks back, and undo also resets the daily quest limits). Rested XP used by a quest is not returned either. Is that intended, or should undo refund? (This is a rule question; nothing will change during the visual rewrite.)
3. **"QUEST CLEARED +N XP".** The flash shows the quest's listed XP even when the daily limit gave 0, or when rested XP or the Compound Habit perk gave more. Should the native version show the XP actually earned?
4. **Custom quests can't be edited or deleted** anywhere today. Do you want edit and delete in the native version (a new rule in the web code first)?
5. **New quest on the 3D table** offers only Daily and Weekly and limits the name to 60 characters; the flat form also offers Epic and has no limit. Which one is right?
6. **"Not now" on the 3D gate** may leave the Quests page blank until you come back to the tab. Have you seen that?
7. **Icons.** The "Daily missions" and "Weekly" icons are meant to be a sun and a calendar but show as stars (the pixel set has no sun or calendar). Keep stars, or draw a sun and a calendar natively?
8. **Quest notes.** A note typed on a quest row disappears if anything else saves before you tick the quest. Do you use notes? Should native keep them?
9. **Card art.** Classic and Halloween decks exist (Halloween is on until 2 November). Are more seasonal decks coming? They will need to be bundled with the app for native.
10. **Sounds and haptics.** Should the native Quests screen play the same card and chip sounds and buzzes as the web board?
11. **Weekly limit.** Weekly quests reset on Monday, but the 3-a-week XP limit counts the last 7 days. Intended?
12. **Phone warmth.** The resting 3D table keeps drawing (about 20 frames a second when idle) for as long as you are on the Quests tab. Is battery or heat a concern on the S25 Ultra?
