# LifeMax RPG — modular development source (v73, Phase 2)

This project rebuilds the original uploaded v73 HTML **byte-for-byte**. The app remains one offline HTML file when distributed.

## Build options

- **Android, no terminal:** Open `LifeMax Forge` (provided separately), select this source ZIP, press **Assemble** and download the resulting HTML.
- **Computer / Termux:** From the project folder run `python build.py`.
- **Baseline integrity:** `python build.py --verify` checks that the output still matches the exact original v73 SHA-256. This check should fail after intentional edits; use plain `python build.py` for modified versions.

The core app is written to `dist/Life_Max_RPG_v73_modular_build.html`. That file does **not** contain the Forge features in `src/features/` (Life Director). To get the full, shippable file from a terminal:

```
python build.py
python forge_features.py dist/Life_Max_RPG_v73_modular_build.html dist/Life_Max_RPG_v81.html
```

`forge_features.py` injects features the same way LifeMax Forge does; on the unmodified v77 sources it reproduces the original `Life_Max_RPG_v77.html` byte-for-byte. The intermediate core file can be deleted afterwards.

## Directory map

- `src/index.template.html`: outer HTML shell, all `@@INCLUDE` references and load order.
- `src/views/`: 12 individual screen HTML files (e.g. `meals.html`, `quests.html`).
- `src/styles/`: 8 CSS sections, concatenated in their **original cascade order**. Some sections include CSS for multiple features because they are preserved rather than rearranged.
- `src/data/`: three embedded pet data blocks.
- `src/runtime/segments/`: **33 ordered JavaScript fragments** (v87 adds `32-pet-dna.js`, cat genetics; see below) (24 original, `29-pet-pair.js` for the second cat, plus `24-pet-depth.js` for the room depth stage, `25-pet-likes.js` for favorite places, toys and exploring, `26-pet-brain.js` for the cat's inner life, `27-pet-play.js` for the extra play games, and `28-pet-box.js` for the kitchen box) extracted at complete top-level statement boundaries. The builder combines them **inside a single `<script>` tag** to preserve global scope, initialization order, and existing inline event handlers.
- `src/runtime/symbol-index.json`: searchable map from named top-level JS functions/variables to their source file(s).
- `src/runtime/module-map.json`: segment metadata, sizes, tags and exported symbol names.
- `build.py`: zero-dependency deterministic HTML assembler.
- `source.sha256`: original v73 fingerprint.
- `dist/`: generated HTML; do not edit this directly.

## How to request a focused AI edit

**Example: Meals screen**

> Update the Meals page only. Read `src/views/meals.html`, relevant rules in `src/styles/`, and look up nutrition functions in `src/runtime/symbol-index.json` to identify the necessary `src/runtime/segments/*.js` files. Do not read or rewrite `dist/` or the entire source tree unless required. Return only changed source files, and explain any shared dependencies.

**Important:** These are ordered source fragments, not independent ES modules yet. A segment can contain more than one feature and can reference symbols declared in other segments. **Do not** add `import`, `export`, IIFEs, or a separate `<script>` per segment; that can change scope and break v73. Use `module-map.json` to navigate; keep source ordering intact. A future Phase 3 can extract real feature modules with explicit dependency interfaces and browser regression coverage.

## Editing rules

1. Always edit `src/` and rebuild. Never patch the generated `dist/` HTML.
2. Never change the order of include markers without checking dependencies.
3. Keep all app assets embedded so the assembled file works offline.
4. Do not change the storage key or overwrite users' saved data during refactoring.
5. Test on Android after modifications: navigation, Meals search/logging, Quests, Training, Media, pet, dark/light theme, persistence and offline reload.
6. Do not assume that an exact-hash build test replaces runtime testing. Baseline equality is a powerful safety check, not a complete browser test.

## v75–v76: pet room depth

`24-pet-depth.js` holds the depth stage (posZ, perspective scale, depth-sorted drawing, the pouf and cushion, the glass visit, parallax). Tunable values are in `PET_DEPTH` at the top of that file. New sprites (approach, retreat, sniff, bonk, pouf, cushion) are in the `depth` key of `src/data/petAssets.html`. Hooks were added to segments 15, 16 and 17. `dist/Life_Max_RPG_v77.html` already includes the Life Director feature; rebuilding through LifeMax Forge gives the same result.
v76: furniture limits come from each piece's base line in the art (`PET_Z_BLOCK`), walks detour in front of furniture, and the approach/retreat cycles are 6 frames with separate kitten sheets (`depth.stages`).

## v77: favorite places and toys

`25-pet-likes.js`: places (`PET_SPOTS`), toys (`PET_TOYS`), points of interest (`PET_POI`), and the tuning values at the top. New art (yarn, spring, cardboard box) is in `depth.toys` in `src/data/petAssets.html`. Toy tray and likes styles are at the end of `src/styles/03-features.css`.

## v78: the cat's inner life

`26-pet-brain.js` is a layer on top of the routine (petThink), the depth stage (24) and the likes (25). It re-weights the options petThink already offers and adds new ones; the day plan, bond levels, depth tiers, hunger, Vacation mode and memorial are unchanged. Saved in `state.pet.brain` (about 1-1.5 KB); `migratePetBrainState(p)` builds it from a v77 save, seeding the attachment threads from `bondPts`. Tunable values are in `PET_BRAIN` at the top of the file.

- Mood: valence x arousal drifting toward a baseline (trait, attachment, hunger, routine block, dawn/dusk). Petting fills sensory saturation: at 0.65 ears flatten and the tail tip flicks; above 0.85 he pulls away and flank-grooms. Stopping in the warning band and leaving him for 8 s raises trust and confidence.
- Attachment: familiarity, trust, safety, confidence, shown as a bond style and an "inner world" panel under Care and settings. Trust from petting alone is capped per day.
- Memory: emotional weights for 16 places, 24 recent episodes, 8 core memories. Comfort-seeking goes to the best place.
- Your rhythm: a 24-bin histogram of when you open the Cat screen. At a learned hour he waits at the glass; if you miss a usual hour he trots up chirping.
- Away time: coarse catch-up (up to 12 steps from his real routine), a "While you were away" line, toys left elsewhere, sometimes caught mid-groom or mid-play. Hunger is not touched.
- Behaviour: spontaneous approach to the glass (replaces the 20 s rule in 24), slow blinks offered and returned (also a Slow blink button), quiet company near the glass, hunting a glint, mote or moth with trophies, full zoomies, mid-walk hesitation, sleep stages (doze, curl, REM, belly-up at trust 0.75+), quirks (2 innate from his seed, up to 3 learned).
- Body language without new art: dilated pupils, flattened ears and the tail-tip flick are redrawn from existing frames at runtime (`petMakeVariant`); held poses are listed under `brain` in `src/data/petAssets.html`. The stage element also carries `data-ear`, `data-tail`, `data-pupil`, `data-mood`.
- Voice: recorded clips get a per-cat pitch; trill, triple chirp, window chatter, dream squeak and the silent-meow breath are synthesised with WebAudio.
- Hooks: 15 (petThink), 16 (steps, touch, hold, feed, greet, pitch, interrupt), 17 (drawing, session start, status, UI, dev buttons), 24 (tick, scene, glass), 25 (toy plays). CSS at the end of `src/styles/03-features.css`.
- Tests: `python3 tests/pet_brain_acceptance.py` (needs Playwright with Chromium; about 3 minutes, real time).

## v78.1: fixes from the first phone test

- **Invisible cat.** The cat's sprite sheets are built once into canvases. Android Chrome can wipe GPU-backed canvases when the app goes to the background; the room is redrawn every frame so it survived, the cat did not. Sprite canvases are now created with `willReadFrequently` (CPU-backed), and `petSpritesLost` / `petRebuildSprites` in 26 check every 2 s and on return to the app, rebuilding the sheets if they were wiped anyway.
- **Black eyes.** The "dilated pupils" effect recoloured the whole iris, which is only 2–4 px, so the eyes went solid black (and a playful kitten was dilated almost all the time). The recolour is off; the pupil state stays in `data-pupil` only. The ear redraw is now adult-only (kitten heads are too small).
- **Constant blinking.** The `idle_sit` art row loops a blink every 8 frames, about once a second. `PET_BLINK` / `petBlinkFrame` now hold the open-eye frames and blink every 4–11 s. Cat-offered slow blinks are also rarer (`BLINK_P` 0.05, checked every 15 s).
- **Mood stuck at the extremes.** Mood now drifts back faster (`LAMBDA_V` 0.15, `LAMBDA_A` 0.3 per minute) and each push counts for less near the limits (`petSoft`).

## v79: Life Director 1.3 — the projector

- **Fix: "Life Director hit an error: Instance not found".** Every re-plan dropped blocks that had already ended but were not resolved yet, so when more than one block was missed, the first repair erased the others and the next repair crashed (and nothing was saved, so it repeated on every open). `rescheduleActivity` now carries those blocks through (`carryPast`), and `processMisses` re-reads each block from the current plan and contains a failure to that one block. This also removes a hidden "refused to publish" (I5) when a missed block was another block's prerequisite. Error banners now offer "Re-plan from now".
- **Today:** the next activity is projected from a film-leader dial whose lit wedge is time left (a 60-minute countdown before it starts, the share remaining once it runs); a day reel shows the whole day to scale with a now marker, and tapping it jumps the running order there; blocks that already ended fold into "Earlier today"; sleep, travel and free time are slimmer than activities; condensed display type.
- **Week:** each day is the same to-scale reel, all on one clock, with today outlined.
- Presentation code: `leaderView`, `leaderHtml`, `reelStrip`, `reelHtml`, `jumpTo`, `renderToday`, `renderWeek` in `logic.js`; styles in the "v5 · THE PROJECTOR" section of `style.css`.

## v79.1: more ways to play

A toy used to mean one thing: stalk, wiggle, pounce. `27-pet-play.js` adds three more games; the pounce stays and shares the time with them.

- **Floor swats (new art).** He flops onto his side and bats the toy with a front paw: lift, cock beside the face, slap, pin, hook it back. About one strike in five sends it out of reach and he gets up to go after it. New sprite row `swat` (row 27, 6 frames, 9 fps) in every adult coat and every kitten stage, built from the side-lying kick pose with a jointed front leg; tuxedo cats have white paws, and on kittens the leg passes behind the face. `bottom.swat` is set for every growth stage.
- **Swat and chase.** He sits close, pats the toy out into the room (always away from the nearer wall), watches it roll, runs after it, and repeats 2–4 times, ending with a floor swat or a pounce. Balls and yarn get this most.
- **Belly-up juggle.** He walks up to the toy, rolls onto his back with it held up on his paws, jiggles it, then kicks it off.
- **Choosing.** A toy you hand him (`petOfferToy`) now picks between the pounce and these by toy (`petToyGame`): mice and feathers lean to swats, rolling toys to the chase; kittens favour swats and the juggle. On his own, `petPlayOptions` offers `swat`, `chase` and `hug` to `petThink` beside the kick, roll and wand; the brain files them under play. They count toward the daily play total like the pounce.
- **Tuning** in `PET_PLAY` at the top of the file. Dev hook: `petDevPlay2(1|2|3)`.
- **Hooks:** 16 (`petPlayTick` in the act step, `petPlayStepEnd` and `countPlay` at act end, `rt.ballFront` reset per step), 17 (swat frames run from the step start), 24 (toy drawn in front of him while his paws are on it), 25 (options and the offered-toy pick).
- **Tests:** `python3 tests/pet_play_acceptance.py` (about a minute). `pet_brain_acceptance.py` still passes.

## v79.2: the box

`28-pet-box.js`. He used to hop onto the kitchen box with the generic two-frame leap and then sit up tall, so it only covered his feet.

- **The jump.** Every hop into or out of the box, whoever planned it (a favourite spot, a nap, the brain, play), is taken over by `petBoxHopStart` / `petBoxJumpTick`. Going in: a crouch with a butt wiggle, a stretched push-off, a tucked arc over the rim, then feet first straight down into the box, a small landing squash, a puff, and the box rocks. The arc ends where he lands inside (sink included), so the drop is one continuous motion; its height is solved so the peak always clears the rim. Coming out: he springs up out of a crouch, sails over the side and lands.
- **Depth.** He waits for the jump and lands just in front of the box (`PET_BOX_FRONT_Z`), so he is never half behind it, and he sorts between the box's back and front while inside. Inside, `c.boxSink` lowers him below the rim; he is clipped at the box floor and walls (`petBoxDrawCat`), so how much shows is just how deep he sits. The tap area follows him down and stops at the rim.
- **Peek-a-boo** (`petBoxPlayPlan`, brain option `boxplay`, filed under play): ducked down to his ears, eyes rising slowly over the edge, quick ducks, pop-ups with a trill, a paw batting over the rim, looking around from just under the edge; then a box loaf ("if it fits") or a hop out. A favourite-spot visit to the box sometimes peeks once before settling.
- **Exact at every age.** `PET_BOX_FACE` holds the measured height of his ear tips and eyes above his feet for the adult and each kitten stage, so "peek" always lands on his eyes.
- **Tuning** in `PET_BOX`. Dev hook: `petDevBox()`.
- **Hooks:** 15 (box approach from in front), 16 (hop start and tick, `sink` on acts, `petBoxTick`, tap area), 24 (draw wrapper, sort depth, box rocking), 25 (option, favourite-spot settle).
- **Tests:** `python3 tests/pet_box_acceptance.py`. `pet_play_acceptance.py` and `pet_brain_acceptance.py` still pass.

## v79.3: a test button for every new cat feature

- **Where:** Settings → Enable developer mode, then Cat → Care and settings. The **Test new features** row sits at the top of the developer panel, newest first, highlighted. It currently holds Box peek-a-boo, Belly-up juggle, Swat & chase and Floor swat.
- **House rule:** every new cat feature registers its own button from its own segment, with one line: `petDevTest('Label', function(){ ... }, 'v79.x')`. The registry is `PET_DEV_TESTS` in `17-runtime.js`; `petDevTestsHtml()` renders it. A button that throws logs a warning instead of breaking the panel.

## v79.3: a test button for every cat feature

**House rule: every new cat feature ships with a test button.** Register it from the feature's own segment with one line:

```js
petDevTest('Box peek-a-boo', petDevBox, 'v79.2');   // label, function to run, version added
```

The buttons appear under **Cat → Developer: companion → Test new features** (turn on *Settings → Enable developer mode* first), newest first, highlighted above the older developer buttons. A test function should set the cat up so the feature starts within a second or so (e.g. `petDevBox` puts him just in front of the box) rather than waiting for him to wander over. Registry: `PET_DEV_TESTS`, `petDevTest`, `petDevRunTest`, `petDevTestsHtml` in `17-runtime.js`.

Current buttons: Box peek-a-boo (v79.2), Belly-up juggle, Swat & chase, Floor swat (v79.1).

## v80: two cats

`29-pet-pair.js`. You can now adopt a second cat, and the two of them do things together.

- **How it fits the engine.** The engine was written for one cat (everything reads `petData()` and `PET_RT`). It is not rewritten: the second cat has its own saved record, `state.pet2` (same shape as `state.pet`), and its own runtime, and `petAs(1, fn)` runs any engine call *as* the second cat (it points `petData()`, `PET_RT` and the bowl side at the second cat for the length of `fn`). The routine, brain, likes, play and box work for both cats unchanged. Sprite sheets, the room canvas, sound, camera, bowl, claw marks, glass fog and the box wobble are shared, not copied (`PET_PAIR_SHARED`). `state.pet` is never touched, so one-cat saves load exactly as before and nothing new is saved until a second cat is adopted.
- **Adopting.** Cat → Care and settings → *A second cat* → Meet. Same coat / name / personality picker; the kitten walks in from the kitchen and the first nose touch happens on its own (it can wake a napping cat for that one meeting).
- **Two cats on screen.** Name tabs under the title choose which cat the panels, Play, Slow blink and the camera follow; tapping a cat in the room does the same (applied when your finger lifts, so petting is never cut off). Holding a cat pets that cat. **Feed** feeds whoever is hungry ("Feed both" when both are); the second cat eats from the other side of the bowl (`PET_EATDIR`). Vacation mode covers both.
- **Together** (a director, `petPairTick`, checks every 6 s and runs one interaction as a list of stages; each stage gives both cats a plan and waits until both are done):
  - **Nose touch:** they meet face to face, heads low. Below friendship 0.25 it often ends in a hiss, the other cat grooming it off; later a cheek rub, a trill and a slow blink.
  - **Chase:** stalk, wiggle, pounce, a bolt across the room, a belly-up tussle; sometimes they swap and go again. The younger cat usually starts it.
  - **Grooming:** one grooms, the other purrs; friends may return the favour; close friends then fall asleep side by side.
  - **Snuggle:** a close friend curls up beside a cat napping on the floor.
- **Friendship** 0–1 (`state.petPair.rel`): Strangers, Wary, Getting along, Friends, Inseparable. Chase from 0.15 (or with a kitten), grooming from 0.35, napping together from 0.6. Gains per interaction, capped at 0.16 a day, plus a little while you are away ("While you were away…"). Shown in the **Together** card.
- **Living together:** they keep a little space between them (`petPairWalkStart`), never jump onto a spot the other is on (bed, sills, tree, box, pouf, cushion): he looks over and settles beside it (`petPairHopStart`), and are never placed on top of each other (`petPairUntangle`). While an interaction runs, the brain cannot pull either cat away; you still can (touch, feed, toys).
- **Hunger.** Each cat has its own clock. A second cat that starves is remembered and removed; if the first cat dies and the second lives, the second becomes the main cat.
- **Groundwork for later.** `state.petPair` records both cats' birth times (identity), so breeding / genetics can hang off it later.
- **Tuning** in `PET_PAIR_CFG`. **Test buttons** (developer mode): Two cats: adopt, nose touch, chase, grooming, nap together, friendship +0.2, remove second.
- **Hooks:** 15 (`petData` reads `PET_CTX`, `PET_EATDIR`), 16 (walk and hop start, bowl side), 24 (second cat in the depth-sorted scene, its particles), 26 (body-language attributes follow the selected cat; timers run as the right cat). Everything else is wrapped from 29. CSS at the end of `src/styles/03-features.css`.
- **Tests:** `python3 tests/pet_pair_acceptance.py` (21 checks, ~2 minutes). `pet_box`, `pet_play` and `pet_brain` acceptance still pass on v80.
- **Not done / known limits:** the interactions reuse existing frames (no new art: the nose touch uses the `rub` row, grooming the `groom` row facing the other cat). Body-language attributes and "Moments he remembers" describe the selected cat only. Texts still say "he" for both cats.

## v81: bedtime

`30-pet-sleep.js`, from a phone test: a cat napping on the living-room rug was hidden behind the big floor cushion (that sleeping spot lies right behind it).

- **Always in view.** Whenever a cat lies down on the floor (sleep or loaf), or is found there asleep, `petSleepCover` measures how much of him the pouf, cushion or box would cover. Over `PET_SLEEP.HIDE_MAX` (8%) and he settles just in front of it instead, or beside it if he does not trust you enough to come that far forward (`petSleepSpot`). Shy cats near the left wall may walk a fair way to open floor; that is intended. Raised spots (sofa, bed, sills, tree) were never hidden and are unchanged.
- **His favourite toy comes to bed.** When the routine sends him to sleep he takes his favourite toy (the most-played one while he has no favourite; never the wand): walks to it if it is on the floor, picks it up (head down), carries it in his mouth and puts it down by his face before kneading and curling up. On the bed or other furniture it stays beside him (`rt.sideToy`); when he gets up he takes it along and leaves it on the floor once he settles, where it is an ordinary floor toy (tap it to start a game). Found asleep when you open the app, he already has it. 85% of bedtimes for adults, every time for kittens (`PET_SLEEP.TOY_P`, `TOY_P_KIT`). Both cats, each with their own toy.
- **Hooks:** none in other segments; 30 wraps `petStartStep`, `petThink`, `petPlaceForNow`, `petDepthTick` and `petBrainScene`.
- **Test buttons:** Asleep behind the cushion, Bedtime with favourite toy. **Tests:** `python3 tests/pet_sleep_acceptance.py` (10 checks).

## v82: Life Director 1.4 — the chrono dial

The Life Director interface was rebuilt around the dial from the ChatGPT/Gemini concept, now driven by the real engine. **Engine, store and host adapter are unchanged** (everything above the interface controller in `src/features/life-director/logic.js`), so v81 Life Director saves load as-is (tested).

- **Today** is a 24-hour dial, midnight at the top. Outer ring: the time you gave Life Director (sleep, work, travel, routines, blocked time). Inner ring: what it planned (sessions in green; done dimmed with a dot; missed or skipped red dashed; protected downtime as a thin pink arc). A live hand shows now; the centre shows what is on or next. Below: the day mix (doubles as the colour legend), an Up next card (Start / Done / Details, real XP estimate from `Host.xpFor`), and the running order.
- **Handles** on the outer ring edit the recurring rule behind them (bedtime, wake, a commitment's start or end), 15-minute snap, keyboard arrows too. Releasing asks first ("This changes … for weekdays, not just this one day"), then re-plans. One-offs stay in "Something came up". Code: `dialHtml`, `ruleInstances`, `attachDial`, `confirmDial`, actions `dialApply` / `dialCancel`.
- **Timer:** Start on a session runs a focus timer (`ST.ui.focus`, survives leaving the app); Finish and log records the real minutes through the existing `finishInstance`, so XP and quests work exactly as before.
- **My time** (was Setup): sleep on a draggable mini dial, commitments (tap to edit; new Unwind-after field = `decompressMin`), daily routines (Getting ready = `morningRoutineMinutes`, Wind-down = `windDownMinutes`, Meals on/off and length). These prefs already existed in the engine; they just had no inputs. After setup, any change here re-plans automatically (`autoReplan`).
- **Week:** every day on one 24-hour bar, totals, and "Where your XP goes" by LifeMax attribute (`ATTR_COL` colours when available).
- **Fix:** Goals showed "Target share 0%" after any local repair (skip, miss, move), because `rescheduleActivity` does not recompute shares. The Goals card now falls back to the engine's own rule (priority ÷ sum of priorities). Display only.
- **Styles:** `style.css` rewritten (visual system v6). Always dark in both LifeMax themes. Avoid the class name `ld-ghost` for anything except buttons; the dial's drag preview is `ld-drag-arc`.
- Not changed: Goals, Quests, Settings, Lab content; missed-session handling; host sync; backup/restore.
- **Phone test focus:** dragging the dial handles with a finger, the timer surviving the app going to the background, and the bottom sheet over the LifeMax nav bar.

## v82: Life Director 1.4 — the chrono dial

The Life Director interface is rebuilt around a 24-hour dial (visual concept from an outside mock-up; all logic is Life Director's own). **Engine, store and host adapter are unchanged from 1.3**, so v81 saves load as-is (tested: same plan, no migration).

- **Today:** the dial shows midnight at the top. Outer ring = time you gave it (sleep, work, travel, routines); inner ring = what it planned (green sessions, pink protected downtime, red dashed missed). A live hand marks now; the centre says what is next. Tap a session for details. Drag a sleep or work handle (15-minute snap) to change that **recurring** rule; a confirmation shows the days it affects, then the week re-plans. One-off changes stay in "Something came up". Below the dial: day mix (also the colour legend), Up next (Start / Done / Details, with XP from `Host.xpFor`), and the running order.
- **Timer:** Start runs a session timer (`ST.ui.focus`, survives leaving the app). "Finish and log" records the real minutes through the normal `finishInstance` path, so XP and quests work exactly as before.
- **My time** (was Setup): first-run steps, a draggable sleep dial, commitments (tap to edit; new *Unwind after* field = `decompressMin`), and Daily routines (getting ready, wind-down, meals = existing engine prefs). Any change re-plans immediately once setup is done.
- **Week:** every day on one 24h bar, totals, and "Where your XP goes" by attribute (LifeMax's own formula and colours).
- Presentation code: `dialHtml`, `coreHtml`, `attachDial`, `attachMini`, `confirmDial`, `roHtml`, `mixHtml`, `stripHtml`, `renderToday`, `renderWeek`, `renderSetup`, `focusSheet` in `logic.js`; styles in `style.css` (rewritten, "CHRONO").
- Gotcha: never reuse `ld-ghost` for anything but buttons (the drag arc is `ld-drag-arc`; a shared class once disabled a button).
- Still to test on a real phone: finger-dragging the handles.

### v82 addition: the guide

A spotlight walkthrough (`TOURS` in `logic.js`; styles under "the guide" in `style.css`). It shows once per screen: **My time** on first run (6 steps), **Today** once a plan exists (7 steps, dimming the dial to one ring at a time via `data-tour` on `.ld-dial`), and **Week** (3 steps). Each step is one headline plus one short line; `tourLabels` pins name tags (Midnight/Noon/Now, Sleep/Work, Session/Done/Missed, handle names) onto the dial for the part being explained. Tapping outside the card advances. Seen guides are saved in `ST.ui.tours`. The **?** button in the header replays the current screen's guide; Settings → Guide → *Show the guide again* resets all three. Steps whose target is not on screen (no handles that day, no Up next card) are skipped. Escape closes, arrow keys step. Test: `tests`-style script used during development walked all 22 steps at 400×860 and 360×640 with no console errors.

### v82 addition: a dial you can read at a glance

- Words on the rings, written so they never read upside down (`arcText`): "Work · 9 AM–5 PM", "Sleep · until 7 AM", and on the inner ring "Free · 3h" for gaps of 90 min or more (future only) and "Downtime · 1h 30m". Text that does not fit is shortened or left off.
- Clock-face words in the person's own clock style (`hourWord`): Midnight, 3 AM … Noon … 9 PM (24-hour locales get 00:00 / 06:00). A moon marks the night half (top) and a sun the day half (bottom).
- Each session carries its activity icon; done sessions show a ✓, missed ones an ✕.
- On Today, the part of the day that has already gone is greyed (`.ld-past`), and the next session has a pulsing outline (`.ld-next`).
- Gotcha: style the dial's own SVG with `.ld-dial>svg`, never `.ld-dial svg`; the second also resizes the nested icon SVGs.

### v82 fix (Life Director 1.4.1): tomorrow's sessions could be marked done today

- Cause: the Today "Up next" card showed the next planned session even when it was on a later day, so its **Done** / **Start** buttons completed tomorrow's sessions early.
- Now a session can be started or marked done on its own day, or from 6 hours before it (`doable`; covers days that cross midnight). Up next shows "That's everything for today" with a link to the next day otherwise. A later-day session's sheet offers **Log it for today** instead (`logToday`): an ad-hoc completion with full XP that appears on today's dial, and the planned session stays.
- One-time repair on load (`repairEarlyDone`, flag `ST.fixes.earlyDone`): sessions completed a day or more ahead go back to planned on their day; the completed entry moves to when it was actually done. XP and LifeMax log entries are untouched.

### v82 fix (Life Director 1.4.2): milestones were planned as sessions

- Cause: LifeMax "epic" milestone quests ("50 Sessions Logged", "Two-Week Focus Streak") fell into the default quest branch and were planned as 30-minute sessions, often several times.
- Milestones earned by piling up other activity (`H.isMilestone`, matched by `EARNED_RE`: counts of sessions/hours/weeks/days, consecutive, cumulative, streak, entire…) are no longer given to the planner, are never all-day reminders, and any already on the plan are removed on the next sync. All 16 of these in the class content are matched.
- The 4 one-off project milestones (Foundational Form Audit, Explanatory Mastery, Subject Dossier Complete, Analytical Longform Project; `H.isProjectMilestone`) are still planned, once, as an optional session.
- The Quests tab lists earned milestones in their own "Milestones" section (earned / in progress).

### v82: Life Director 1.5.0 — the centre of the dial says what to do now

- On Today the centre always answers "what should I be doing?" for whatever is on, not just sessions: Sleeping, Working, Commute home, Dinner, Free time, Downtime, or the session's name; then time left in big type and when it ends; then what's next.
- `liveTimeline` joins today and tomorrow and stitches blocks split at midnight, so sleep counts down to the real wake time and "Next" sees past midnight.
- Two independent warnings (`nowState`): the top line turns amber for **Started 15m ago** (a session's time began 10+ min ago and no timer is running) or **Wrapping up** (current block ends within 10 min). The next line turns amber for **Starting in 12m** (a session within 15 min) and offers **Start early**. One short vibration 5 min before a session, once per session, only while the app is open.
- A thin ring inside the dial (`curRingHtml`) depletes as the current block runs out, in that block's colour (amber while wrapping up).
- Live: centre and ring refresh every 15 s in `liveTick`; other days show a summary plus the first session.

### v82: Life Director 1.5.1 — polish

- Header: the subtitle is short ("16 sessions ahead", plus "see notes in Week" when the plan has notes) and truncates before the ? button and clock instead of running under them.
- The sun and moon moved out of the dial face (they collided with the time-left ring) to small icons beside the Noon and Midnight labels.
- Centre typography: one family (Inter, tabular figures) instead of mixing in monospace; small letter-spaced labels (NOW, NEXT, WRAPPING UP, LATE BY 15M); time left as large numbers with small units (`bigTime`: "7h 55m"); a short divider before Next; tighter sizes when a button shows, and again on phones 400px wide or less, so nothing touches the ring.

### v82: Life Director 1.5.2 — meals on the inner ring

- Meals (breakfast, lunch, dinner) moved from the outer ring to the inner ring beside sessions, in their own colour (peach, `--meal`) with a fork-and-knife icon (`mealPx`, drawn on LifeMax's 8x8 pixel-icon grid because the host set has no food icon). Tapping one says what and when, and that meals are set in My time.
- `KIND_CLASS.MEAL` is now `meal` (was `maint`); routines (getting ready, wind-down) stay on the outer ring. The day mix and Week legend list Meals separately; the guide's ring steps mention them.

### v83: Life Director 1.6.0 — the build

Whenever Life Director builds or repairs your plan, the Today dial shows it happening (`runBuild` in `logic.js`, styles under "the build" at the end of `style.css`).

- **Full build** (any re-plan: first setup, Re-plan from now, changes in My time / Goals / Settings, weekly rollover): a scanner arm sweeps the day from midnight and draws your fixed time onto the outer ring exactly as it passes, the bezel ticks light up under it, and the centre reads out the hour and what fills it (Sleep, Work, Open time). Then the planner works: each new session launches from the centre and spirals out into its slot with a ripple, sparks, a counter tick and a light haptic; moved sessions orbit from their old time to the new one along a dotted trail; dropped sessions flash red and break up. Then it locks in: a shockwave, a small kick of the dial, the hand swings round to now, handles pop in, and the centre hands back to the live readout. About 2–4 s depending on how much changed.
- **Repair** (skip, move, missed blocks, Something came up): the same diff without the sweep, about 1.5 s.
- If the change happens on another tab (My time, Goals), the build waits and plays the next time you open Today, diffed against what you last saw. Several changes in a row become one build.
- Tap the dial to skip. Reduced motion or no Web Animations: no build, the plan just appears. Presentation only; nothing about the plan, saving or XP changed. The guide waits for the build to finish.
- Hooks: `queueBuild` (from `applyOutput`, mode `build` or `repair`), `takeBuild` / `runBuild` in `afterRender`, `render` stops a running build, `liveTick` leaves the centre alone while it runs.
- **Fix:** the protected-downtime arc on the inner ring never drew (a stray quote in its `d` attribute). It shows now as the thin pink arc the legend always described.

### v84: Life Director 1.7.0 — today's plan on the Overview

- **Overview order** (most important first): Today's plan (the chrono dial), Next moves, level & today's XP, Attributes, This week, Cat & Daily luck, Recent activity. Order is just the order of the containers in `src/views/dashboard.html`.
- **Today's plan card** (`#ovPlan`): Life Director's live Today dial, read-only (no drag handles), with "N sessions left · time" underneath. The dial is drawn by Life Director itself through the public `LifeDirector.glance(el, {intro})`; the Overview only calls it (`ovPlanGlance` in `18-runtime.js`, from `renderOverview` and `ovReplay`). If the feature is missing, the card stays hidden. Before setup it shows a short invitation instead.
- Works on a fresh app load before Life Director has been opened: `glance` loads the saved plan (`boot`) and runs the same sync / missed-block / weekly-rollover checks as opening Life Director. `mount` no longer re-boots when the glance already loaded the state.
- **Taps:** anything on the card opens Life Director → Today and repeats the tap there (`openFromGlance`): a session or meal opens its details, Start / Start early starts the timer, the link just opens it. Refreshes every 30 s while the Overview is on screen; the dial fades in on each visit to the Overview.
- Styles under "the glance" at the end of `style.css`. The card stays dark in the light theme, like the rest of Life Director.

## v85: edit the Overview

`31-overview-edit.js` (new segment, after 30). The Overview's tiles can be reordered and hidden from the app.

- **Edit** pill at the top right of the Overview opens the editor in place of the tiles: one row per tile (icon, name, what it shows), an eye to hide or show it, and a grip to drag it. On touch only the grip drags, so the list still scrolls; with a mouse the whole row drags; with a keyboard, focus a grip and use the arrow keys. Rows glide into place (FLIP). **Reset to default** restores the original order with everything shown. **Done**, Escape, or leaving the Overview closes it. Changes apply and save as you make them.
- **Tiles** (`OV_TILES`): Today's plan, Next moves, Level & today, Attributes, This week, Cat, Daily luck, Recent activity. The Cat and Daily luck tiles are now separate (they used to share `#ovDuo`), so the cat can go anywhere. The Cat tile still only appears once you have a cat, and Today's plan only with the Life Director feature; the editor says so on their rows.
- **Markup:** each tile is a `.ov-tile[data-tile]` wrapper inside `#ovTiles` in `src/views/dashboard.html`; the cards inside keep their old ids, so the render code is unchanged. A user-hidden tile gets `.ov-off` (the `hidden` attribute stays free for the tiles' own logic, e.g. `#ovPlan`).
- **Saved** in `state.ovLayout = {order, hidden}` in the normal LifeMax save (so backups include it). Old saves have none and get the default order. A tile added in a later version and missing from a saved order is placed after its default neighbour, so new tiles never vanish. `ovLayoutApply` runs at the end of `renderOverview`.
- **Adding a tile later:** add a `.ov-tile` wrapper in `dashboard.html` and one entry in `OV_TILES` (the array order is the default order).
- Styles: "v85 — Edit Overview" at the end of `src/styles/03-features.css`. `navigate` is wrapped once at the end of the segment to close the editor quietly when you leave.

## v86: Daily Luck message fix

- **Bug:** every Legendary roll (8,888–9,999) showed "Eight-eight-eight-eight. Buy two tickets.", a line written for exactly 8,888. An 8,898 read as if it were 8,888.
- **Fix:** the Legendary tier message is now "Off the charts. Buy two tickets."; exact scores can have their own line in `LUCK_EXACT` (01), currently just 8,888. `luckMsg(score)` (18) picks the exact line or the tier message and is used by both the result screen (19) and the luck card (18). Tiers, scores and saved rolls are unchanged.

## v87: cat DNA and sex

`32-pet-dna.js` (new segment, after 31). Every cat has a sex and a diploid genome modelled on real cat genetics. Breeding is not in the game yet; this is the groundwork for it.

- **Chromosomes.** 2n = 38 like a real cat: 18 autosome pairs plus X/Y. XX female, XY male, about 1 kitten in 2,500 XXY (a sterile male, the only kind of male that can be tortoiseshell). Each locus holds `[maternal, paternal]`; a male's X loci hold one allele.
- **Pigment genes** (`PET_LOCI`, real genes, approximate positions): O / ARHGAP36 on X, B / TYRP1 (B > b > bl), D / MLPH, A / ASIP, Mc / TAQPEP (mackerel > classic), Ta / DKK4 (ticked), C / TYR (C > cb = cs > c: sepia, mink, points, albino), W / KIT (W dominant white > ws spotting > w), L / FGF5 (long hair), I (silver, gene unknown). Epistasis as in real cats: dominant white hides everything, red cats always show tabby markings and hide B and A, non-agouti hides the tabby pattern, ticked hides mackerel/classic.
- **Polygenic traits** (`PET_QTL`, modelled loci with + / − alleles): white-spotting grade (3), eye pigment (4), body size (4), and temperament: the Feline Five (skittish, outgoing, dominant, spontaneous, friendly) plus vocal, 2–3 loci each. Temperament also has a per-cat developmental share fixed at birth and not inherited (`dna.dev`, heritability `h2` 0.4–0.5).
- **Phenotype** is computed, never saved: `petDnaPheno(dna)` → sex, karyotype, coat name, recipe and coat key, eye colour, weight, deafness (white cats), personality scores and the legacy `trait`. `petPheno(p)`, `petSexOf(p)`, `petPron(p,'he')` are the helpers for other code.
- **Behaviour.** The derived trait feeds everything that already reads `p.trait`. On top, `petBrainArbiter` is wrapped so option weights scale by category (`PET_OPT_CAT`) with the personality (`petDnaWeight`): play by spontaneity, exploring by outgoingness, social options by friendliness, hiding by skittishness, scratching by dominance, grooming up for long hair. A new cat's voice pitch follows size and sex, talkativeness follows vocal.
- **Drawing without new art.** Every coat is composed from the four original palette-indexed sheets (`petDnaCompose`): black = the shape, ginger/gray = stripes, ginger/gray/tuxedo = three white-spotting grades. Extras: classic tabby widens the stripes, tortoiseshell spreads them into red patches (palette slots 75/85), colourpoints darken thin parts and a face mask (slots 220–252, faint on young kittens, full by day 20), long hair fringes the outline. Colours in `PET_DNA_COL`. **The four legacy coats reproduce their original sheets pixel for pixel** (adult, depth and every kitten stage, tested) and keep their keys `ginger/gray/black/tuxedo`. Other coats get a self-describing key, e.g. `dna_black_1_n_3_0_n_0_1` (base, tortie, tabby, white, silver, point, long, eye); `petDnaRegister(key)` adds pseudo-URLs `dna:<kind>:<key>` to the asset JSON and growth JSON, and the wrapped `petLoadImg` composes them, so the engine's own loaders, kitten stages, depth frames and the GPU-loss rebuild all work unchanged. Until a sheet exists, `petDrawCat` draws the nearest legacy coat for that frame.
- **Adoption.** The intro and the second-cat card now show a litter of four kittens from two random parents (real meiosis, so siblings resemble each other), with sex, coat and temperament on each card and an *Another litter* button. The personality picker is gone: temperament comes from the genes. The old API still works: `petAdopt('tuxedo','Mo','shy')` and `petPickCoat`/`petPickTrait` build a genome that matches the requested coat and trait.
- **Existing saves.** Cats without `dna` get one on load (`petDnaOf`) that matches their coat and trait exactly, seeded from their birth time, as males (the app has always called them "he"). Nothing else in the save changes.
- **Pronouns.** Engine texts say "he"; for a female cat a MutationObserver on the Cat screen and the Overview card, and the `toast` wrapper, switch he/him/his to she/her. With two cats, the cat named in the text decides, otherwise the selected cat.
- **UI.** ♂/♀ next to the name; *Care and settings → Genetics*: sex and karyotype, coat, eyes, adult weight, every pigment gene with both alleles and what it does, what the cat carries, what is hidden by other genes, temperament bars, and the full genome table.
- **Breeding groundwork** (unused in play except for stray litters): `petDnaGamete` (meiosis, crossing-over by Haldane's map at ~1 cM/Mb, X/Y segregation, XXY males sterile), `petDnaConceive` (fertilisation, rare XXY, new mutations at 2e-5 per locus per gamete, recorded in `dna.mut`), `petDnaLitter`. Parent ids are kept in `dna.par`.
- **Saved:** `state.pet.dna`, `state.pet.sex` (and the same on `state.pet2`), about 0.8 KB per cat.
- **Test buttons** (developer mode): DNA: random genome, calico ♀, Siamese point, longhair classic tabby, undo, 2,000-kitten check (sex ratio, tortie only in females, ginger mostly male), draw 12 random cats.
- **Known limits:** body size changes weight and voice, not the drawn size; tortoiseshell patches are built from the stripe layer, so they are somewhat banded; in a tortie point the red patches fade into the points; Manx, folds, polydactyly and odd eyes are not modelled; the pair interactions do not use dominance/friendliness yet. The startup error "Cannot access 'OV_DEFAULT' before initialization" predates v87 (present in v86).

## v93: body shape genes and the breeding lab

`37-pet-morph.js`. Six more quantitative traits on top of the v87 genome (`PET_MORPH`): ear length, ear width, tail length, leg length, body length, whisker length. Each is 2–5 modelled + / − loci (`Em1…`, `Ew1…`, `Tl1…`, `Lg1…`, `Bl1…`, `Wk1…`), heritability 0.7–0.8, so selecting the longest-eared cats of each litter makes longer ears in a few generations (tested: ears 0.8× → 2.5× in five generations). A recessive major gene `Tb` makes a bobtail. `petDnaPheno(d).morph` holds the multipliers (1 = the art as drawn), `mz` the −1..1 scores, `mc` the allele counts.

- **Existing cats keep their body.** A genome flagged `legacy` (everything created before v87) gets balanced alleles and no noise, so every proportion is exactly 1 and the rig is skipped. Their kittens vary.
- **Genetics panel** gains a *Body shape* section.
- **Breeding lab** (Cat → Care and settings): a cattery (`state.cattery`, up to 16 cats kept as genomes), a mother and a father from your roster, a Monte-Carlo forecast for the goal trait, a litter of 2–6 you can keep or release, a goal (longest/shortest of any trait) with the best cat so far, *Another stray* for fresh genes, and *Bring home* when the second cat slot is free.
- **Test buttons** (developer mode): fill the cattery, ten generations of selection, and one-tap extreme shapes (long ears, tiny ears, long tail, bobtail, leggy and long, short and cobby, everything extreme, back to random).

## v94: the rig

`tools/rig_build.py` (offline, needs numpy and Pillow) cuts each adult frame into parts along the art's own outlines and writes `src/data/petRig.html`: ears, the tail chain with its centre line, whiskers, and the bands where legs and torso can be stretched. Rows it cannot map reliably (scratch, scratch in/out, bat, kick, swat, roll's tail, play's tail) keep their original art for the parts it cannot find. `--qa` writes contact sheets to `tools/qa/`.

`38-pet-rig.js` re-assembles a frame with the parts transformed (`petRigFrame`): ears scale along and across their axis, the tail bends along its own centre line (longer without getting thicker), whiskers lengthen, a row band stretches the legs, a column band stretches the torso, and the outline is repaired where a part was cut. Results are cached per sheet, row, frame and proportions. The hook is in `petDrawCat` (17): adult cats only for now; kittens, the front/back views and any cat whose proportions are all about 1 draw the plain sheet.

Known limits: scratch, bat, kick, swat keep their tails; wide ears (> 1.4×) look rough because pixels are re-spaced unevenly.

## v95: kittens and the front/back views

`tools/rig_build.py` now covers every sheet: `adult`, the six kitten stages `st0`–`st5`, the front/back-view sheet `depth` and its kitten stages `dst0`–`dst5` (one JSON block, about 520 KB). `petDrawCat` (17) passes the sheet kind to `petRigPick`, which scales a kitten's proportions by how grown it is (`petRigGrown`: half strength on day 0, full on day 30, so there is no pop when it grows up). Ears work in every view; tails work in the rows where the builder found a real chain; legs and torso stretch only in the rows flagged for it (walk, eat, rub, leap, walk_w, and the lying rows for the torso). A vertical chain that touches the floor is a leg, not a tail (kittens' tails have no rings), so those rows keep their plain tail. The back view (`walk_back`) and the scratch/bat rows are left alone until they are redrawn to fit the rig.

## v97: health sync (Samsung Health through Health Connect)

`android-wrapper/native/HealthSyncPlugin.kt` (Kotlin, `androidx.health.connect:connect-client`) reads exercise sessions, daily step totals, sleep sessions and the latest weight from Health Connect; Samsung Health writes into it once you allow that in Samsung Health → Settings → Health Connect. `patch_project.py` adds the Kotlin plugin, the client library and minSdk 26 to the generated project; `patch_manifest.py` adds the four `health.READ_*` permissions, the Health Connect package query and the two entry points Health Connect requires (permissions rationale, permission usage).

`39-health.js` runs the sync (app start, when you come back after 15 min, every 30 min, and *Sync now*). Each record is counted once (`state.health.seen`, keyed by the Health Connect record id, kept 21 days). Workouts become activities of the matching kind (strength, recovery, or cardio with the sport as the skill) with the Log screen's XP maths; a finished day of 2,000+ steps gives 3 XP per 1,000 up to 15,000; a night of 3+ hours is a recovery activity; the latest weight updates the character. All of it is marked `src:'health'`. The first sync reaches back two days. Settings → Health sync has the master switch, one switch per data type, *Allow access* and *Sync now*.

## v98 — Overview: Activity today
New Overview tile (Android app only, movable in Overview edit): today's steps with a goal bar (goal in Settings → Health sync), last 7 days of steps, today's workouts, last night's sleep and latest weight, all from Health Connect. Prompts to set up sync when it is off.

## v99 — Life Director dial widget (Android)
Home-screen widget showing the chrono dial. The web app draws the plain dial off screen to a PNG (`LifeDirector.widgetSnapshot`, run when you leave the app and every ~10 min while open); the native `LifeDialWidget` adds the shaded past, the moving hand and the centre text (on now / up next) and redraws every ~10 minutes. Tapping it opens Life Director. Add it from Settings → Home-screen widget (or the launcher's widget list). Native side: `LifeDialWidget.java`, `WidgetBridgePlugin.java`, `res/layout/widget_dial.xml`, `res/xml/lifemax_dial_widget_info.xml`.

## v100 — Expanded Health page
Tapping the Overview "Activity today" tile opens a full Health page and starts a sync: today's steps and goal bar, 30-day step chart with goal line (7-day average, best day, goal days), the week's workouts, last nights of sleep, weight with trend. Back closes it. Sync now button inside.

## v101 — Update pop-up at launch
The app checks for a newer build a few seconds after every launch (and when you return after 30+ minutes) and shows an "Update available" pop-up with Update now / Later. Later hides it until the next launch. Back closes it.

## v102 — Overview app row; pixel-art app icons removed
New Overview tile "App shortcuts": a scrolling row of your Apps-tab shortcuts using each app's own Android icon, with a + button that opens the app picker. The pixel-art icon set (art.js), the "Pixel-art icons" toggle and the per-app icon picker are gone from the Apps tab. Also fixed: the widget's tap-to-open now loads the Life Director tab properly.

## v103 — launch update check retries
If the check at launch fails (e.g. no network yet) it retries after 15 s, 45 s and 2 min, and Settings → App updates shows the error instead of staying silent.

## v106 — Bottom bar back to its original look; lighter scrub and page slide
The bar looks exactly as it did before scrubbing (the normal active-tab highlight; no sliding pill, no zoom, no bubble). Sliding a thumb along it still runs through the tabs: the active tab changes on the bar at once, the strip scrolls only to keep it in view, and the page follows as soon as the finger slows (70 ms) and on release; nothing is animated while scrubbing. Taps and releases use a short slide-and-fade (transform and opacity only, no scale or blur, 0.16 s out / 0.26 s in); a newer request finishes an unfinished slide immediately so fast taps never queue up.

## v107 — Economy v2 (Gemini design review, first half)
`src/runtime/segments/43-economy.js` (included right after 00): character level now uses `200 + 75*(L-1)` per level while attributes/skills keep `100 + 25*(L-1)`; a one-time migration (`state.xpSchemaV2`, `state.legacyCharXPBonus`) keeps every existing character and attribute level exactly where it is, updates untouched default activity rules, resets decay clocks and records the savings high-water mark. Logging: 5-minute minimum, duration curve (30 min = 1x, 60 = 2x, 120+ = 3x, effort bonuses need 20+ min), 3 rewarded logs per type per day (1x, 1x, 0.5x, then none), 6 h/day manual time cap, daily XP soft cap 180 (half value above) and hard cap 240; quests limited to 3 daily / 3 weekly / 1 epic reward per period. Health: steps (type `steps`, 4,000+ effective steps, 3 XP per 1,000 above 2,000 x0.5... see code, max 15) with run minutes subtracted, sleep paid by tier (max 15, longest night per day, none if a workout overlaps), workouts already logged by hand within 2 h are not paid twice. Passive steps/sleep no longer feed streaks or stop decay. Decay: 4 days grace, 3% of the current level's cost per day (max 15), never lowers a level, skips rest days (2 per rolling week, or any 20+ min recovery session); long absences fill a "rested" pool that adds +50% to the next sessions. Savings XP only for new highs above the balance high-water mark (5 to 25, once a day, 50/week). Titles extended to level 100. Not built yet: Sparks and its shop, attribute ascension, streak shields, nutrition/cat-care/Life Director bonuses.

## v108 — scrub lag and haptics
While the finger is moving fast the page no longer switches at all (the bar glides; the page follows when the finger eases, using the measured speed), page work runs a frame after the bar has painted, and heavy pages (measured per tab) wait longer before a live switch. Native `NavHapticsPlugin` gives a system tick for each tab passed and firmer pulses when the scrub starts and ends (works even when the browser vibration API is blocked); off if Haptics is off in the app's effects settings.

## v109 — Economy v2, second half
`44-rewards.js`: Sparks (1 per XP earned via `save()`; existing players start with 50% of lifetime XP; decay never takes them back), Rewards sheet (Overview tile + Settings): perks (Scholar's Flow, Iron Grace, Executive Edge, Compound Habit; one equipped), accent colours, cat toys unlocked early, rest day token (1/week) and streak shield refill (1/month), attribute ascension (level 20+, banks the attribute's XP into character level, restarts it at level 1 and gives a permanent +5% XP star, up to 3), streak shields (earned every 7-day streak, up to 2, cover a missed day), nutrition bonus (8 XP once a day when calories land within 15% of the goal), cat-care bonus (+10 XP once a day when fed, played with and petted), planned rest days from the workout plan, Life Director execution bonus (+10%, 2 to 5 XP, up to 15 a day, to Discipline) and Life Director awards now use the same economy rules and caps. Not built: the breeding serum.

## v110/v112 — scrub veil with tab thumbnails
While you scrub the bottom bar, a blurred picture of the tab you are over fills the screen (no icon, no label). The pictures are small snapshots (1/4 size JPEG, `PageThumbPlugin`, PixelCopy on Android 8+) taken 1.1 s after each visit to a tab and kept in `localStorage` (`lifemax_thumbs_v1`, not part of backups); a tab not yet visited shows a soft tinted blur. The real page is switched in when the finger rests (130 ms) or lifts, and the veil fades out once it has painted.

## v111 — whole-bar scrub
Edge to edge on the bottom bar covers every tab from any starting point: where your thumb lands is the tab you are on, the left edge is the first tab, the right edge the last, and the distance on each side is spread evenly between.

## v113 — real screenshots on the veil, outline icons
The scrub veil shows the blurred screenshot of each tab with a thin white outline icon and the tab's name (14 hand-drawn SVG icons in `LMA_TAB_ICONS`). Screenshots: `PageThumbPlugin` tries PixelCopy, then a software draw, and reports any error in full. A one-time pass 9 s after launch visits every tab without a picture and snapshots it (a thin progress line at the top; touching the screen stops it); Settings -> Tab previews shows how many tabs are captured, the last error, and "Capture all".

## v114 — fewer tabs
Media library and Journal now sit under one **Library** tab (a Media | Journal switch at the top of each page; the tab remembers which one you were on). The **Apps** tab is gone from the bar (the Overview app row stays, and its "Edit" link opens the shortcut editor, `nav: false` in the feature file). **Settings** moved to a gear button in the top bar. The bar now has 11 tabs: Overview, Character, Cat, Log activity, Quests, Workout plan, Library, Meals, Recipes, Money, Life Director.

## v115 — Food tab; Character and Cat reached from the Overview
Meals and Recipes share one **Food** tab (Meals | Recipes switch, remembered). The **Character** and **Cat** tabs are off the bar: the Overview's level-and-name tile now shows your profile picture (or initial) and opens the Character page when tapped, and the Cat tile opens the Cat page. The bar highlights Overview while you are on those pages. The bar is down to 8 tabs: Overview, Log activity, Quests, Workout plan, Library, Food, Money, Life Director.

## v116 — renamed to LifeMax, new icon
The app is now just **LifeMax**: launcher label (`capacitor.config.json` appName), page title, header, notifications, update pop-up, widget and release names. The package id (`com.lifemax.rpg`) and the backup file identifier are unchanged so updates and backups keep working. New launcher icon (pixel-art landscape with the LM monogram): adaptive foreground keeps the monogram inside the launcher's safe zone with a blurred extension of the artwork around it; legacy square and round icons and the splash use the artwork directly.

## v117 — LifeMax Voice, phase 1
See `VOICE.md` at the repo root. Offline-only voice/typed scheduling into Life Director (create_activity, query_schedule), clarification questions instead of guesses, three-tier idempotency (delivery guard, persistent operation journal with crash reconciliation, repeat-detector), preview before saving, Undo through a verified delete, diagnostics and a text simulator. Native `VoiceEnginePlugin` uses only the on-device recognizer (Android 12+).

## v118 — Character tab restored
The Character tab is back on the bottom bar with its original person icon, right after Overview (9 tabs). Tapping the level tile on the Overview still opens it too.

## v175 — load a trainer from a file (the model is never in the repo)
- The 3D gym can draw a skinned 54-joint character. It comes from a **trainer pack** file you choose in Settings → Look & feel → Guided workout → *Load trainer pack*; the app validates it, stores it in this device's IndexedDB, and uses it from then on (Remove puts the built-in low-poly trainer back). Nothing of any such model is in the repo, the app or the releases; only the picker, the loader, the skinning shader and the pose retargeter are.
- `python3 tools/make_trainer.py` converts a skinned Collada character from `art/trainer/src` into `art/trainer/trainer-pack.json` (git-ignored). The converter repaints the chest logo to read LIFEMAX. Send the pack file to the phone yourself (chat, cloud storage) and pick it with the button.
- Pack format: JSON, `format:"lifemax-trainer-pack"`, `version:1`, 54 joints (center, hip, legs, spine 1 to 4, clavicles, arms, wrist and fingers, neck, head), skinned vertices/indices for the body and a rigid face mask, and PNG textures.
- Face fix attempt: face and eyes are drawn with polygon offset so the head skin does not hide them.
- Tests in `tests/gym3d.test.js` cover the picker, validation and persistence; the pack-dependent ones run only when a pack exists locally.

## v173 — the 3D gym (first version of the new guided workout)
- `50-gym3d.js` puts a 3D gym scene over the existing guided-workout logic (21/22-runtime stay the source of truth; sets are still saved by `gwrLogSet`). Opening a guided workout shows today's plan on a board ("step onto the floor"); then the camera walks to each machine in turn, with chevrons on the floor pointing the way, and ends on a wide shot with a summary board.
- Each exercise is mapped (by name) to a movement: leg press, chest press, shoulder press, pulldown, row, leg extension, leg curl, squat, hinge, calf raise, plank, dead bug, treadmill walk, mobility. A low-poly trainer (planar forward-kinematics rig, limbs keep fixed lengths) repeats the rep at a fixed 4 s pace while the set is active, the machine's weight stack rising with him, and a big rep counter follows him. SET DONE opens a log sheet (weight steps of 2.5/5 with a "last time" reminder, reps, effort 6 to 10), then the gym interval clock (red digits, beeps at 3-2-1) counts the rest, with +/-15 s and Skip.
- Menu: itinerary, undo, skip, swap machine, end session, switch to classic. Pause closes the scene and resume reopens it. Settings → Look & feel → Guided workout switches between 3D gym and the classic screen; reduced motion or no WebGL2 uses classic.
- Draws at full rate only while the trainer moves or the camera walks (about 16 fps otherwise). Not done yet: real exercise animations and form (placeholder poses), pocket/voice mode, records wall, gym skins, a nicer trainer model, per-machine models, set-by-set weight memory per machine beyond last session.
- Tests: `tests/gym3d.test.js`.

## v172 — sound for the quest table and the Luck dice
- Recorded effects (Kenney Casino Audio, CC0, licence kept in `art/sfx/KENNEY_LICENSE.txt`, plus your notification bell) are packed by `python3 tools/make_sfx.py` into `50-sfx-data.js` (about 0.55 MB, mono Vorbis) and played by `QSFX` in `50-sfx.js` through one shared AudioContext. They honour the main Sound switch and have their own volume (Settings → Look & feel → Quest effects: Off / Low / Medium / High).
- Quest table: the bell rings when you press Get your quests and when you press Shuffle / Shuffle again; card-pack and slide sounds for the deck drop, the riffle, each card laid (with a thud), pushing the spare deck aside, lifting and putting back a card, turning a card face-up, and coins when a quest is fulfilled. The flat page's card turns and claims use them too.
- Luck dice: grab, shake and a throw as each die is released, and the recorded die sounds for every bounce (the old synthesized click is the fallback).
- Tests: `tests/sfx.test.js`.

## v171 — no flat-page flash; Halloween deck complete
- While the 3D table is opening (first visit, coming back, Reshuffle) the flat Quest Board page is hidden from the first frame (`html.q3d`), so it no longer flashes. The scene opens sooner, and Reshuffle swaps scenes without a fade.
- Halloween deck now has all eight cards: Endurance is the backpack and compass at the signpost, Adventure is the Fool's staircase with the jester emblem.

## v170 — the Quest Board is the table
- With the 3D table deal mode (the default) the old Quest Board page is never shown. The whole Quests tab is the scene, above the bottom bar: first visit of the day is the gate and the reading; afterwards (and on every later visit) it is the resting table: the three cards face-up on the wood, candles, the void, and a progress line (pips plus "X / 3 FULFILLED" and the weekly count).
- Tap a card to lift it to you (controls pill: Hold to claim or "fulfils itself", XP, Redraw, put back). It turns freely on any axis. Claiming or redrawing re-paints the card in 3D (a FULFILLED stamp, a spin and a burst).
- Ledger button: weekly missions, your own quests, nutrition quests, epics and badges, each with a check. New Quest: a small form inside the scene. Reshuffle replays the reading.
- The scene renders at full rate only while something moves (about 20 fps idle) to spare the battery, and is closed (GPU context freed) when you leave the tab. It ignores swipe-to-change-tab, so dragging cards never switches pages.
- The flat Quest Board page is still used for Quick, Instant and Classic cinematic deal modes, reduced motion and phones without WebGL2.
- Not carried over yet: the hero art, stats strip and badge grid of the old page (badges are in the Ledger).

## v169 — card art packs (Halloween deck)
- Card art now lives in packs: `art/cards/packs/<id>/` with `pack.json` (name, optional season dates, where the parchment text sits) and `src/<attribute>.png` plus `back.png`. `python3 tools/make_card_art.py` builds every pack into `50-quests-art.js`. A card a pack lacks falls back to the Classic pack, so a new seasonal deck can be added a few cards at a time.
- Packs: Classic and Halloween (Oct 1 to Nov 2). Settings → Look & feel → Card art: Automatic (uses the pack whose season is on today's date, else Classic) or pick one. Halloween has no Endurance (Wanderer) or Adventure (Fool) card yet, so those show the Classic paintings.
- Launcher icons use packs too: `android-wrapper/icons/<id>.png`, `python3 make_icons.py <id>` (remembered in `icons/ACTIVE`). The icon is native, so it cannot change by date at runtime, and switching it needs a NativeApi bump and a new APK.
- Size: each full pack adds about 1.5 to 2 MB to the app.

## v168 — the splash is the opening shot; thick cards; Halloween icon
- The Quest Board's first-visit splash is no longer a separate screen. The 3D scene opens straight on a wide shot of the table floating in the void, candles lit, deck waiting, with "YOUR CARDS ARE WAITING" above and a gold-framed "GET YOUR QUESTS" button below (light sweep, breathing glow, corner studs). Pressing it flares the button and candles and the camera is drawn in to the table, then the shuffle begins. "Not now" fades out and leaves the cards face-down; leaving the page closes the scene and it returns next visit. Without WebGL2 the older flat splash is used. (`50-quests-gate.js` is now only that fallback.)
- 3D cards now have real thickness (0.34 on a card 11.2 wide), a rounded-rectangle outline with a warm paper edge, so they no longer vanish when seen edge-on. Deck cards stack with spacing and rest on the wood.
- Halloween launcher icon and splash (`android-wrapper/icon-source.png`; the normal icon is kept as `icon-source-default.png`, swap them back and run `make_icons.py` after Halloween). Native change, so NativeApi is now 4 and needs a new APK.

## v167 — the threshold splash
- The first visit to the Quest Board each day (after you linger about a second) now opens on a dark void: a watching pixel eye, two flickering candles with rising embers, "YOUR CARDS ARE WAITING" and a "GET YOUR QUESTS" button. Pressing it ignites the button, opens the eye and fades into the 3D reading. "Not now" skips the reading and leaves the cards face-down; leaving the page removes it until next visit. It stops above the bottom bar so you can always leave.
- It appears for the 3D table and classic cinematic deal modes only (not Quick or Instant), and "Shuffle again" goes straight to the reading.

## v166 — table fixes
- The deck now sits at the back of the table and the three cards are laid in front of it, so they no longer land on top of it; the leftover deck is pushed to the side.
- 3D cards have rounded corners (the art's black corner pixels are cut away in the shader).

## v165 — edit the bottom bar
- Settings → Look & feel → Bottom bar: reorder the tabs with the arrows, switch tabs on or off (at least two stay), add Settings to the bar, or reset. The change applies to the real buttons, so swiping between tabs and scrubbing along the bar follow the new order and skip hidden tabs. Hidden pages stay reachable from links inside the app.
- Fix: some settings (swipe animation, scrub speed) were dropped when the app restarted, because the prefs loader only kept the keys it knew. It now keeps everything it finds.
- Test: `tests/tabs.test.js`.

## v164 — a table in the void, flip on any axis, corner flips
- The 3D reading now opens on a black void: the whole table floats in the dark, the candles light up, and the camera dollies in from far away to the tabletop; then the deck drops, is shuffled and the cards are laid. The old fog backdrop is gone from the 3D scene.
- Turning a lifted card is now arcball style: the card rotates about the axis perpendicular to your drag, so a corner-to-corner drag flips it diagonally. On release it settles upright on whichever face is turned toward you (front reveals, back springs back).
- After the reading, tap any corner of a card (small gold curls mark them) to turn it over to its back; tap the back to turn it face-up again. Remembered for the day.
- Fix: backface-hidden faces could swallow taps, so only the face turned toward you takes touches now.

## v163 — the 3D table
- The reading is now a real 3D scene by default (`50-quests-table.js`): the wooden table model you supplied (OBJ, normal map and a packed AO/roughness map, shrunk from about 10 MB to about 240 KB), two candles that are the only light (flickering, falling off with distance), fog, soft card shadows, and the cards as 3D objects drawn from the painted art plus the quest text. The deck drops onto the wood, is riffled, three cards are laid down, then each is lifted to the camera and turned by hand in real 3D, then laid face-up on the table.
- Settings → Quest effects → Card deal: 3D table (default), Classic cinematic (the flat version, also the automatic fallback if WebGL2 is missing), Quick, Instant.
- The reading starts only if you stay on the Quest Board for about a second, so swiping or scrubbing past it no longer triggers it.
- Re-generate the table assets with `python3 tools/make_table_art.py` (sources in `art/table/src`). Tests: `tests/table3d.test.js`.

## v162 — turn the card by hand; the Monk is painted
- The Monk painting (Discipline) is in, so all eight attributes now have painted cards.
- In The Reading you now turn each card yourself: it is lifted to the middle, then dragged with a finger it rotates in 3D (up to a full turn; vertical drag tilts it) with a moving sheen and shadow, carries on with a flick and springs to the nearest face. Releasing on the front reveals it (flash, burst, name); releasing on the back springs it back. A pulsing finger hint appears after a few idle seconds. Skip still reveals everything at once.
- Fixes: hidden deck backs no longer catch touches, the lifted card is always the top layer, the title text is readable.

## v161 — The Reading (cinematic deal)
- The first visit to the Quest Board each day plays a short film (`50-quests-cine.js`): the screen falls into a candlelit, foggy room (one fullscreen fog shader with two flickering candle lights), the deck drops out of the dark with a thud and dust, riffle-shuffles, three cards are slammed down on the table, then each card in turn is lifted to the middle, turned over with a flash and named in pixel text (Major Arcana get a bigger flash and a stronger buzz). The cards then glide into the carousel, already face-up.
- Skip is always in the corner. Settings → Look & feel → Quest effects → Card deal chooses Cinematic (default, roughly 14 seconds), Quick (the earlier short shuffle) or Instant. "Shuffle again" replays it. Reduced motion skips it.
- The hand is no longer rebuilt on every save, only when a card, its state or the reroll cost changes.
- Tests: `tests/cine.test.js` (16 checks).

## v160 — floating cards
- The strip under each painted card is gone. The cards float on their own; the Hold seal, slot, XP and Redraw sit in a separate dark pill beneath, which appears once the card is turned over.

## v159 — painted tarot cards
- The ChatGPT card paintings are in: Colossus (Physique), Wanderer (Endurance, the mountain path), Oracle (Intelligence), Merchant (Business), Bard (Social), Fool (Adventure, the hiker), Hermit (Wellbeing, with the cat), plus one shared card back. The quest name, arcana, difficulty and description are drawn on the parchment panel in dark ink; the Hold seal, XP and Redraw sit in a strip under the card.
- Discipline has no painting yet, so it keeps the generated pixel card. To add or swap one: drop `art/cards/src/<attribute>.png` (attribute id, or `back`), run `python3 tools/make_card_art.py`, rebuild. The text panel position per card is in `QART_PANEL` (50-quests-cards.js).
- Images are shrunk to 640 px WebP and embedded (about 1.9 MB added to the app); they become blob URLs once, so redraws are cheap.

## v158 — spooky fantasy tarot card faces
- New card faces (`50-quests-cards.js`): a pixel font (5x7, drawn from a glyph table, no font file), pixel sprites (skull, bats, eye, crescent moon, candles, thorns) and a seeded night scene in an arched window. Each attribute is a named arcana with its own colour (Colossus, Wanderer, Oracle, Merchant, Bard, Monk, Fool, Hermit). Rarity: Minor (1 star), Rare (2, adds bats and an eye), Major (3, adds a skull and gold frame).
- Quest names, numerals and XP are pixel text (real text stays in aria-labels); descriptions stay in a readable serif italic. Card backs show a glowing eye. Fulfilled cards get a pixel stamp.

## v157 — Fate Deck effects (WebGL2)
- `50-quests-fx.js` adds a small GPU engine: particles whose motion is computed in the vertex shader from their age (one buffer write per particle at birth, nothing per frame), used for riffle sparks, deal trails, a burst when a card is turned over (bigger for 3-star) and a fountain plus ring when a quest is fulfilled, by hold or automatically.
- Holographic foil on 2- and 3-star cards: one shared fragment shader (iridescence, diagonal sheen, sparkle, edge glow) is drawn per card at about 30 fps and copied onto the card. Cards also lean toward your finger, or with the phone's tilt.
- Settings → Look & feel → Quest effects turns it all off (plain cards). It also turns itself off with reduced motion, without WebGL2, or if the GPU context is lost. Foil only runs while the Quest Board is on screen.
- Tests: `tests/questfx.test.js` (15 checks, run with software GL).

## v156 — the Fate Deck (quests, tarot cards)
- The three Daily Hand cards are now tarot-style cards in a swipeable row: a numeral, a medallion with the attribute icon and difficulty pips, the quest, and a round hold-to-claim seal that fills as you hold. 3-star cards get a gold frame; fulfilled cards get a stamp.
- First visit each day: a face-down deck riffle-shuffles, then each real card flies from the deck to its place (FLIP animation, Web Animations API, GPU-composited). Cards deal face-down; tap one to turn it over. A redrawn card comes back face-down. "Shuffle again" replays it. Reduced-motion skips the show.
- Next (phase 2): a WebGL foil shader for 3-star cards and a claim burst, with a Settings switch and a 2D fallback.
- Tests: `tests/hand.test.js` (27 checks).

## v155 — Settings in categories
- A pill row under the Settings title switches between Look & feel, Voice & journal, Phone & sync, Character & game, and Data & advanced. Only that group's cards show; the last choice is remembered. Cards are grouped by their heading in `50-settings-tabs.js`, so new cards need adding to a group there (unlisted cards stay visible everywhere).

## v154 — the Daily Hand (quests, phase 1 and 2)
- Each day the Quest Board deals three cards: Maintenance (your weakest attribute over the last 7 days), Class (an attribute your class cares about) and Wildcard. The draw is seeded by the date, your Luck score and the reroll count, so it is stable all day. Luck nudges the odds toward harder cards; a rest day never deals a hard (3-star) card.
- 54 templates in `QUEST_LIBRARY` (`50-quests-hand.js`) across all eight attributes. Cards that match something you log (a strength/cardio/reading/business/language/recovery activity of enough minutes, water goal, feeding/petting/playing the cat, today's workout plan) complete themselves. The rest use a hold-to-claim button (about a second; Enter/Space also works).
- Reroll: one free per day, then 50 Sparks. A finished card cannot be rerolled.
- Cards are ordinary daily quests underneath, so XP, the 3-a-day cap, undo, badges and voice commands keep working. Custom quests, weeklies and epics are untouched. The old fixed class dailies (and the starter dailies) are retired once, and class swaps no longer add them back.
- Not done yet: voice-specific phrases for the Hand ("reroll my second quest"), weakness-targeting beyond the weekly attribute check, and a card history screen.
- Test: `tests/hand.test.js` (22 checks).

## v153 — the swipe follows your finger again, without moving the page container

v151's edge tag worked but was not the fluid swipe. Now the page follows the finger as it used to, done differently so the bottom bar is left alone:
- **While your finger is down:** every card on the page moves with it (one CSS variable is set on the page once per frame and each card reads it). Where there is no further tab the cards stretch a little and no more. The page container itself is never moved, faded or promoted; the tests check that on every frame.
- **Let go far enough** (a fifth of the screen, or a quick flick) and the cards carry on off the screen (70 to 150 ms, quicker if you flicked), then the next page takes over and its cards slide in from the side you swiped toward (0.26 s). **Not far enough:** they settle back (170 ms).
- Removed: the edge tag. The Settings switch **Swipe animation** is still there if it ever misbehaves (off: instant switches, no movement).
- If the bar blinks again with this, the cause is any movement of page content on your phone, and the next step is to look at how the bar is drawn rather than at the animation. `tests/swipe.test.js` is 32 checks. Web-only.

## v152 — faster scrubbing along the bottom bar

Sliding a finger along the bottom bar now moves through the tabs about 1.8 times as fast by default (a 100 px slide that used to cross two tabs now crosses three or four). The start threshold is 5 px instead of 7, and the wait before the page behind it switches is shorter (100 ms instead of 130, still longer for pages that are slow to draw). Settings > Navigation > **Bar scrubbing speed**: Normal (the old pace), Fast (the new default) or Faster (2.6 times: the last tab is reached before your finger gets to the edge). `tests/scrub.test.js` (10 checks). Web-only.

## v151 — a new swipe animation that never moves the page

Moving the whole page was what made the bottom bar blink on your phone (with the animation off it was fine), and two layer fixes did not cure it. So the page container is no longer touched at all while you swipe.
- **While you pull:** a small tag at the edge you are pulling toward names the next tab ("Character ›" on the right, "‹ Quests" on the left). It fades and slides in as you pull and turns blue when you have pulled far enough (a fifth of the screen, or a quick flick).
- **When you let go:** the new page appears at once, with no waiting, and its cards slide in a short way from the side you swiped toward (22 px, 0.24 s). That is the same kind of per-card animation the app has always used for a page change, which never blinked the bar. Nothing is animated or transformed on the page container; the tests check that on every frame of a swipe.
- Before the first tab and after the last there is no tag and nothing happens. Vertical scrolling, edges, sliders, the cat room and Life Director still never start a swipe. The Settings switch is now called **Swipe animation** (off: plain, instant tab switches; reduced motion does the same).
- `tests/swipe.test.js` is 30 checks. Web-only. Removed: the page-following drag, the page layer and the fade.

## v150 — the bar blink: the page keeps its own layer

The test with "Page slides with your finger" switched off showed the blink comes from the slide. Looking back through the builds: in v146 (page on a permanent layer) nobody saw the bar blink; in v147 I removed that permanent layer (I thought dropping it caused the post-swipe jump, but that was the cards re-fading, fixed separately), and the blink started. So the page is back on a permanent layer (`backface-visibility:hidden`, which does not make fixed pop-ups inside pages misbehave), plus `will-change` while a finger is down. The switch in Settings stays as a fallback. Web-only; `tests/swipe.test.js` is 28 checks.

## v149 — the bottom bar while swiping, take two

Reports: the bar still disappeared and jumped as you swipe through tabs. I could not make that happen in my browser test, so these are the real fault I could find plus three ways of making the rest less likely:
- **Two things were scrolling the bar at once.** On every tab change the bar was told to scroll smoothly to centre the tab (the browser's own `scrollIntoView`), and a moment later a second piece of code set its scroll position directly. With a swipe through several tabs they fight, and the tabs jump about or the lit tab slips out of view. There is now one mover (a 170 ms glide, cancelled by any new one), and the other piece stands aside while it glides. The test swipes through eight tabs on a narrow screen and checks the bar never scrolls back on itself and the lit tab is always on screen.
- **Lighter page animation:** the page now only slides (no fade). A fade makes the phone draw the whole tall page into an extra buffer every frame, which is the heaviest part and could starve the bar.
- **The tab preview snapshot** (a picture the app takes of the page a moment after you stop) now waits 2.6 s after a swipe instead of 1.1 s, so it cannot land in the middle of a series of swipes.
- **A switch in Settings > Navigation: "Page slides with your finger".** Turn it off and swipes switch the tab instantly with no movement, which also tells us whether the slide animation is the cause. Also removed the extra layer I put on the bar in v148.
- `tests/swipe.test.js` is 28 checks. Web-only.

## v148 — the bottom bar no longer blinks during a swipe

The bar was a frosted-glass strip (80% opaque, blurring the page behind it). While the page slides and fades under it, the phone has to redo that blur every frame, and on some phones that makes the bar drop out for a moment. On phones the bar is now solid (dark `#12151c`, white in the light theme), with no blur, on its own layer. Side effect, in a good way: the page used to faintly show through the bar (a dark card edge could look like a highlighted tab); that is gone too. I could not make the blink happen in my browser test (it is a phone graphics issue), so the test checks the bar is in place and visible on every frame of a swipe and that it no longer blurs. Please tell me if it still blinks. `tests/swipe.test.js` is 23 checks. Web-only.

## v147 — swipe stutter and the stuck tab highlight

- **Stutter after a swipe:** during a swipe the cards' fade-in is switched off so the page arrives in one piece, but the switch was turned back on the moment the swipe ended, and turning it on makes every card replay its fade-in from invisible. That was the little jump. The fade-in now stays off until you change page some other way (a tap on a tab, a button), and it is restored before that page change so nothing replays. I also dropped two layer hints (`will-change` on the page and `backface-visibility`) that made the page re-render once when they were taken away.
- **A tab that stays lit:** on a touch screen the browser keeps the hover look on the last thing you tapped, so a tab you had tapped stayed shaded after you left it. The tab hover look now only applies with a mouse. As a second safeguard, after every page change (swipe, tap, scrub, voice, Back) the bar is set so that exactly one tab is lit: the one for the page you are on.
- `tests/swipe.test.js` is now 21 checks (nothing replays after a swipe, one tab lit after many quick swipes and taps, hover only with a mouse). Web-only.

## v146 — swiping between tabs follows your finger

Before, nothing moved while you swiped; after you let go the app waited, took a screenshot-style transition and then faded each card in one by one, which felt like it lagged. Now:
- The page slides with your finger as soon as the swipe is clearly sideways (a light fade as it goes; a rubber-band stretch at the first and last tab).
- On release it finishes the slide: past a fifth of the screen, or a quick flick of even 36 px (speed counts), commits; otherwise it springs back. The slide-out takes 70 to 140 ms depending on how fast you flicked, then the new page arrives from the side in 190 ms, all as compositor animations (transform and opacity only).
- The page switch itself happens while the page is off-screen, and the cards no longer fade in one after another during a swipe.
- Tapping a tab in the bar is unchanged. Vertical scrolling, the screen edges, the cat room, Life Director, sliders and carousels still never start a swipe. With reduced motion on, it switches on release with no movement, as before.
- `tests/swipe.test.js` (15 checks, with synthetic touches). A tab switch costs 2 to 13 ms here, so a phone should hide it easily. Not felt on a device yet: please tell me how it feels and I will tune the distances and speeds.

## v145 — new app icon

New launcher icon (a pixel-art landscape with the LM monogram): `android-wrapper/icon-source.png` is the supplied artwork and `android-wrapper/make_icons.py` rebuilds the adaptive foreground (corners cropped, full bleed), the legacy square and round icons and the splash picture at every density. The icon is part of the APK, so `NativeApi.VALUE` is now 3 and the update check offers the new APK. The build guard now also watches `android-wrapper/res`.

## v144 — haptics update now asks for the APK

The v143 haptic strength needs the new Android pieces (Vibrate permission, two NavHaptics methods), so `NativeApi.VALUE` went from 1 to 2. A phone on an older APK is now offered the new APK by the update check instead of a quiet web update (the web update only goes to phones whose APK is new enough). To keep this from being forgotten: the build now has a guard step that compares the Android files (`android-wrapper/native`, the four `patch_*.py` scripts) with the last published build and **fails** if they changed while `NativeApi.VALUE` was not raised.

## v143 — haptic strength

Settings has a new **Haptics** card: a switch, four strengths (Light, Normal, Strong, Max), and "Try" buttons (tap, double tap, XP gain, level up). Every buzz in the app goes through it.
- In the Android app (needs this build, which adds the Vibrate permission and two methods on the NavHaptics plugin) the strength is real: the phone's vibration motor is driven at an amplitude (Light 90, Normal 150, Strong 230, Max 255 of 255) with longer pulses at the stronger steps. The bottom-bar ticks stay the system's crisp tick at Normal and become motor pulses at the other strengths.
- In a browser, or an older app build (it falls back by itself the first time the new calls are refused), a browser cannot ask for harder, so Strong and Max make each buzz longer instead (pauses in a pattern are kept; the longest buzz is capped at 260 ms).
- It was not possible to feel the difference here. Before this build the app did not hold the Vibrate permission, so the browser-style buzzes may have done nothing on the phone at all.
- Needs an APK install for the real strength; the web part alone gives the longer buzzes. `tests/haptics.test.js` (15 checks).

## v142 — thumbs up and down on every voice answer

Under every voice answer (a result, a card waiting for a yes, a question, a cancel) there is now "Was this right?" with a thumbs up and a thumbs down. Thumbs up is thanked and saved as a good answer. Thumbs down opens a small optional box, "What went wrong?", with Send and Skip comment. One rating per answer; a half-typed note survives the card redrawing, and typing does not close the card.
- A thumbs down is saved in the same log as the automatic flags: your sentence (as you said it), the screen, what the engine heard it as, its reply, and your comment. If the engine had already flagged that sentence, the comment is added to that entry instead of making a duplicate.
- Thumbs ups are saved separately (last 100) and appear at the end of the export under "Thumbs up".
- Settings > Voice (beta) > Export Voice Fallback Log counts both ("3 flagged answers ... and 2 marked good"). Clear removes both.
- `tests/voicefallback.test.js` is now 29 checks.

## v141 — tap outside to close voice

With the voice card open (listening, a result, or a card waiting for a yes), touching anywhere on the page behind it closes it. The touch still goes through, so you can scroll right away. A card that was waiting for a yes is dropped, the same as the ✕ button. Touches inside the card, on the mic button and in the typing box do not close it.

## v140 — Voice fallback log

Whatever the voice engine cannot understand is now saved on the phone (`lifemax_voice_fallback_logs_v1`, last 200): the sentence, the screen you were on, the voice session and the time. A sentence is saved when it matches no intent at all, or when the recogniser's own confidence was under 0.55. Answers to an open question and normal commands are not saved. Settings > Voice (beta) has a new **Export Voice Fallback Log** row (Export and Clear) that downloads `lifemax-voice-fallbacks-<date>.txt`. Code: `src/runtime/segments/50-voice-fallback.js` (`VoiceFallbackLogger`: `logFallback`, `getLogs`, `clearLogs`, `exportLogsAsText`); tests: `tests/voicefallback.test.js` (14 checks). Written in plain JavaScript like the rest of the runtime, not TypeScript.

## v139 — Voice generation 5: a day that goes off-script

New segment `50-voice-gen5.js` (wraps the voice segments; web only) and `tests/voice5.test.js` (102 checks). Voice tests overall: 222 + 90 + 102.
- **Negation:** "don't cancel my workout", "do not schedule that", "please don't delete anything", "I don't want you to remove it" do nothing and cancel a card that is showing ("Understood, I won't do that"). "Don't forget to schedule X" still schedules it; "never mind" still cancels.
- **Places to avoid:** "schedule a 1 hour workout tomorrow, but not in the morning", "reading anytime tomorrow after 11 AM", "not before 4 PM", "find free time today except between 12 and 2 PM". The time is found for you and the card says why.
- **A day gone wrong:** "push everything back 30 minutes" / "I'm running 20 minutes late" / "shift my afternoon forward 15 minutes" (everything from now on, or that part of the day; one Before → After card; applied together or not at all), "swap my workout and deep work today" (start times swap, each keeps its length), "clear my afternoon" / "cancel everything after 6 PM today" (a removal card naming each item). For these bulk moves "back", "later" and "forward" all mean later; "earlier", "ahead" and "sooner" mean earlier.
- **Only if there is room:** "if I have an hour free this afternoon, schedule guitar practice" books it, or says what the longest gap is and offers that; "squeeze in a 20 minute meditation before dinner" ends it right where dinner starts (6 PM if there is no dinner block).
- **"The second one" and "here":** after a list (quests, schedule, plan), "complete the first one", "check off the last one", "tell me more about number two"; on the Quests or Workout plan screen the list is the screen's. "What do I have left here?" / "summarize this screen" answers for whichever tab is open. "Mark the first exercise in my workout plan as done" ticks its sets.
- **Detailed logging:** "log bench press 3 sets of 10 at 185 pounds", "I did 50 pushups", "log a 5K run in 25 minutes", "I biked 10 miles", "walked 2 miles this morning". Saved on the activity as `detail` (sets, reps, weight, volume, distance, pace). With no time, the length is estimated and says so.
- **Water, meals, goals:** water by glass (250 ml), bottle (500 ml), large bottle or liter (1000 ml), oz or ml, kept in a new `waterLog` (default goal 2 L; "set my water goal to 3 liters"). Whole meals in one sentence ("for breakfast I had 3 eggs, two slices of toast and a protein shake"), matched to the food database or to a short built-in list of common foods, which is labelled "(estimate)"; anything it cannot match is listed and left out. "How many calories can I still eat today", "how much protein do I have left" (a protein goal is new: "set my protein goal to 150 grams").
- **Cats:** every cat's name is picked up from your save at the moment you speak: "what is Mochi's personality", "which cat is hungry", "how is Mochi doing". There are no treats or litter box in LifeMax, so those two say so instead of pretending.
- **Shortcuts:** "when I say 'beast mode', log a 60 minute workout and open my workout plan" (up to five steps); built in: "start my day", "end of day review", "wrap up my day", "wind down for bed".
- **Spoken Undo:** "undo", "undo that", "take that back", "revert last command" reverse the latest change even after the card is gone: a scheduled activity (its journal entry becomes UNDONE), a move or shift or swap, a removal or clear, a logged activity, food, water, a quest, plan sets, a goal. Marking a session done or skipping one cannot be reversed, and Undo says so. The stack keeps the last 25 and survives a restart.
- Browser-tested only.

## v138 — Voice generation 4: how people really talk

New segment `50-voice-gen4.js` (wraps the earlier voice segments; web only, no native change) and `tests/voice4.test.js` (90 checks).
- **Self-correction:** "schedule a workout tomorrow at 8, wait no, make it 9 for 45 minutes". The words after the last pivot ("wait no", "actually make that", "sorry I meant", "scratch that", "make it") replace the same kind of detail before it (time, day, length); everything else stays.
- **Two things at once:** "log a 30 minute walk and check my XP". Split only where the second half starts with a clear request word, or is a length + name + time after a scheduling first half; never inside "stretching and mobility", "between 2 and 4 PM" or "an hour and a half". The first one waits for your yes, the second follows, and one card shows both. Cancel the first and the second is dropped. The second scheduling request inherits the first one's day.
- **Edit the card on screen:** while a card is showing, "make it 45 minutes", "actually at 9:30", "move it to Wednesday", "push it back 30 minutes", "call it Leg Day", "for an hour and a half instead" change only that one detail and re-check overlaps. Anything that is not a pure edit (a question, a new command) still works as before.
- **"Right after that" / "right before deep work":** uses the last thing scheduled, or the activity you name; "after work" is still the work-hours anchor.
- **Briefing and coaching:** "how am I doing today" (XP against the daily pace, streak, steps, quests, what is next), "what's next / what am I supposed to be doing" (now, minutes left, next and how far away), "how close am I to my goals" (exact amounts left), "I have 30 minutes free, what should I do" (best fit from quests, today's plan, the walk you still need, the cat) with a one-tap chip.
- **Comparisons:** "did I walk more today than yesterday", "compare my XP this week to last week" (same number of days of each), "best / worst day for steps last week", "average daily steps". Days with nothing logged count as 0.
- **Plan, library, timer, apps:** "what's on my workout plan", "search my library for dune" (opens the Library with the search filled), a focus timer ("start a 25 minute focus timer", "how much time is left", "cancel timer"; ends with a note, a vibration and, in the app, a notification), "open Firefox / Claude / Gemini / Myspoti / Android settings" (matched against the phone's app list; "open settings" is still this app's Settings).
- **Help and shortcuts:** "what can I say" shows tappable examples by category. "Start my morning routine" gives the day. "When I say gym time, do log a 45 minute workout" teaches a shortcut (up to 20; "forget gym time" removes it).
- Tested in a browser only. The microphone and the on-device recogniser behave as before; opening other apps needs the Android app.

## v137 — Cat tab back on the bottom bar

The Cat tab is back in the bottom navigation, right after Character, and lights up when you are on the Cat page (it used to light Overview). The Overview tile still opens it too.

## v136 — ear sizes eased

The ear-length gene runs from 0.4x (tiny) to 2.5x (enormous). Drawn literally, the big end made long thin spikes and the small end made stubs, and flat ears of a small-eared cat nearly vanished. The drawing now eases the length (the gene values and chips are unchanged), widens the ear with its length, and gives flat ears a minimum size.

## v135 — wary cats' flat ears

A cautious cat (a new stray, say) pins its ears back. The HD redraw rotated the ears 48 degrees without shortening them, so they stuck out as long thin spikes. Flat ears now turn 36 degrees, shrink to 60% of their length and widen by 30%, so they lie sideways like airplane ears.

## v134 — every cat animation in HD

All 28 adult animations, the six kitten growth stages and the four front/back views (walk toward and away, sniff, bonk) are now drawn at 2x, for every coat and genetic coat (`tools/hires_build.py` -> `src/data/petHd.html`, about 3 MB). Details that make it work across every row: the face is redrawn live where both eyes are found (every frame except sleep, the two sleeping rows and the back views, which keep their own art); a frame drawn with closed eyes (blinks, purring, kneading, rubbing) keeps them closed (shut curves, or happy arches for purr, knead and rub); the face scales to the head so kittens do not get adult-sized eyes; the cat's mood body language (flattened ears, flicking tail tip) is applied through the same rig at 2x; and the animation is matched by its sheet row, so a kitten missing a pose falls back exactly as before. At night the cat looks sleepy only while sitting, loafing or looking up. Settings > Cat graphics > HD still turns it all off. `tests/pethd.test.js` (16 checks).

## v133 — updates without installing an APK

Web-only changes now reach the phone over the air. CI publishes `app.html` and a richer `version.json` (`nativeApi`, and a `web` block with a SHA-256) next to the APK. The launch check compares: if the release needs newer native abilities (`NativeApi.VALUE` in `android-wrapper/native/NativeApi.java`) it offers the APK as before; otherwise it quietly downloads the new web build, verifies it, and applies it on the next launch (or right away from the "Update ready · Restart" pill). A build that does not start properly is dropped after two tries and the bundled copy takes over. Native: `LiveWeb.java`, `LiveWebPlugin.java`, `NativeApi.java`; web: `35-android-upkeep.js`, `51-boot-ok.js`. See `ANDROID.md`. Tests: `tests/ota.test.js`.

## v132 — cute cat faces

The HD faces are big, glossy and soft: a rounded almond eye with a thin one-pixel outline and a slightly heavier upper lid (like eyeliner), a green iris (darker at the top) with a big oval pupil, a large highlight up on the outer side and a small one low on the inner side; a small soft pink nose with no dark edge; a thin short mouth. Expressions: neutral, happy, sleepy, surprised, angry, sad, blink (`petHdEye`, `petHdFace` in `50-pet-hd.js`). An earlier bolder pass was too heavy and was pulled back.

## v131 — HD cat: walking at twice the detail

The cat's walking animations (side view, front and back views, every growth stage, every coat including the genetic ones) are redrawn at 2x pixel density with the strong outlines kept. Built by `tools/hires.py` and `tools/hires_build.py` into `src/data/petHd.html`: the old art smoothed to 2x, with the face removed. The face is drawn live by `src/runtime/segments/50-pet-hd.js`: bigger eyes with a rim, iris, pupil and highlight, a nose, a mouth and whiskers that follow the cat's whisker gene, and seven expressions (neutral, happy, sleepy, surprised, angry, sad, blink). The cat blinks every few seconds, looks sleepy late at night, and sad when it has gone a long time without food. The ear, tail, leg and body genes still shape the cat: the existing rig data is scaled up and each part also takes the outline pixels touching it so nothing is left behind. The genetic-coat compositor (`32-pet-dna.js`) runs on the 2x sheets with all distances doubled. The room is now drawn at 4x (`PET_OS`) so a 2x pixel is exactly two screen pixels. Settings > Cat graphics turns HD walking off. Everything else (sleeping, playing, scratching and so on) still uses the original art until it is redrawn, row by row. Tests: `tests/pethd.test.js`.

## v130 — voice: past questions, honest zeros, follow-ups, logging what happened

"How many steps did I walk yesterday" now answers for yesterday (and says "0 steps yesterday" when that is true) instead of reporting today. One resolver understands past days and ranges (yesterday, 3 days ago, last Friday, this week, past 7 days, October 20...), and steps, workouts, XP, level progress, streak, achievements, food and "what did I do" all use it. "What about yesterday?" re-asks the last question. "Log a 30 minute run" / "I did yoga yesterday" saves an activity that already happened after a preview. Speech-to-text near misses are repaired, and every screen opens by voice. See `VOICE.md`; 222 voice checks.

## v129 — optional Journal lock

Settings > Journal privacy > **Lock the Journal**. When on, opening the Journal asks for your fingerprint or face, or your screen lock (PIN, pattern, password) if there is none. Android does the checking (`BiometricLockPlugin.java`, AndroidX biometric); the app stores only the on/off choice. You confirm it is you to turn it on or off. It locks again when you leave the app, or a minute after you leave the Journal (quick back-and-forth does not nag). While the Journal is open the screen is hidden from the app switcher and screenshots, and tab previews never capture it. The Media | Journal switch stays usable on the lock screen. Works in the Android app only; `tests/lock.test.js` (mocked Android prompt). Needs the new build (native plugin and a Gradle dependency).

## v128 — Journal redesign

The whole Journal got a premium pass. A "Your chronicle" hero shows day streak, days this month, XP this week and reflections, with a 4-week rhythm strip (one glowing cell per day, coloured by the day's badge, brighter for bigger days). The day list is now calendar tiles (weekday, date, an XP bar in the day's colour, a pen mark when you wrote a reflection) with gold review tiles and dashed quiet-stretch markers. A day opens with a big date numeral and a glowing XP counter, then the coach card and a notebook-style reflection page (lined paper, a progress meter to the 40-character reward), then the activity log as a timeline with colour-coded nodes, attribute pills and XP. Weekly reviews show a bar chart of the week; monthly reviews show a calendar heatmap. Entries fade in with a short stagger (off with reduced motion); works in the light theme.
Fixed in the same change: the Journal's first draw happens during start-up before the coach module exists, which could stop the app loading when there was saved history. The hooks now wait for the module and it redraws itself when loaded; `tests/coach.test.js` has a cold-start check.

## v127 — Journal coach

The Journal (Library tab) now reads your own data and talks back. Designed by Gemini, adapted to what the app really has; all offline and rule-based (`src/runtime/segments/47-journal-coach.js`, tests in `tests/coach.test.js`).
- **Day insight:** each day with data gets a badge (High Output, Dial Master, Off-Script Win, Quest Clear, Steady Stride, Rest Day, Quiet Anchor, Low Power, Chapter Resumed) and 1 to 2 kind lines, chosen by rules from that day's XP against your own 14-day average, how much of your Life Director dial you did, quests and cat care. The wording is picked by the date, so it never changes on reload.
- **Reflection prompt:** a question that fits the day, with a swap button. Writing 40+ characters about today or yesterday pays 15 XP and +2 bond, once a day (no back-farming old days).
- **Patterns:** at most one a day, from nine rules over the last 21 days (best time of day, dial too full, boom and bust, a keystone habit, a weak weekday, one stat only, small days that kept you going, journal lift, feeding the cat early). A pattern then rests for 7 days. Needs 7 active days.
- **Welcome back:** after 3+ quiet days a calm card instead of a broken streak, with a small one-time reward (10 / 20 / 25 XP by length of absence); after 14+ days your rolling average resets for a week. Quiet stretches collapse into one line in the day list.
- **Weekly and monthly reviews** appear inline in the day list (gold border), with a short coach summary and a closing question (+30 / +50 XP once).
- **Safe by design:** never reads weight or food data; a reflection with words of self-harm gets a gentle note and a crisis line (988 in the US) and the coach stays quiet that day. Coach XP is type `coach`: it pays Sparks but never counts as a training day or streak day, and is left out of the numbers the coach judges you by.
- Life Director 1.9.3 adds a read-only `LifeDirector.coach.dayStats(date)`.

## v126 — voice: understands questions, escapes and more

A question like "what are my quests today" is no longer mistaken for a new activity. The voice engine now works out what a sentence is for before scheduling: quests, XP and level, schedule and free time, your cat (status, feed, pet), steps, calories and macros, opening any screen, and completing, moving or cancelling an activity (move and cancel ask to confirm first). While it is waiting for an answer, "any time", "whenever", "now", "in 30 minutes", "morning" and so on pick a real free slot or time, "never mind" cancels, and a new question replaces the half-finished one. Life Director 1.9.2. See `VOICE.md`.

## v125 — cat: tail only stretches where it is free

The tail-length gene only re-draws the tail on walk, run, stretch, leap, rub and eat. In every sitting, lying, grooming and kneading pose the tail lies curled on the floor, and stretching it made a spike poking out the wrong side; those poses now keep their original tail art. `FREE_TAIL` in `tools/rig_build.py`.

## v123 — cat: new rear-view walk and a new wall scratch

- `walk_back` (all coats, every age): real head with ears (built from the front-view head with the face painted out), and a curved, swaying tail beside the body instead of a stripe down the back. `tools/redo_back.py`.
- Scratch family (`scratch`, `scratch_in`, `scratch_out`, adult, all coats): the cat sits and reaches up the wall, raking each paw down in turn, claws out. Claw marks now land where the paws do. `tools/redo_scratch.py`.
- Rig data regenerated (`tools/rig_build.py`).

## v122 — (rolled into v123)

## v121 — mic button in the top bar

The floating microphone button is gone. The mic now sits in the top bar next to Settings and looks like it; it pulses while listening.

## v120 — voice appointments on the inner ring

A meeting or appointment set by voice now draws on the dial's inner ring with your other plan items (blue arc), instead of the outer ring that is reserved for sleep, work and travel. Life Director 1.9.1.

## v119 — voice: fewer questions, no stray error
- Speech error 11 ("recognition service disconnected") came from replacing the recognizer while an older one was still winding down; callbacks from an older recognizer are now ignored (generation counter), and error 11/5 raised by our own cancel are never shown.
- The recognizer waits longer through pauses (2.2 s of silence ends a request) so a half-finished sentence is not cut off; after a question the mic reopens by itself so you can answer "p.m." without tapping.
- No length given = a standard 30 minutes (shown as "standard" in the preview), no question.
- Plausible times by kind of activity (`LMV_KINDS` in `45-voice.js`): a meeting at 3 is 3 PM, the bar at 7 is 7 PM, wake up at 6 is 6 AM, lunch at 12 is noon; ambiguous ones (gym at 6) still ask. An explicit unusual time ("meeting at 3 am") is kept but flagged. Assumptions are shown in the preview.

## v176 — trainer is skinned on the CPU
The trainer pack was invisible on a real phone GPU (integer vertex attributes plus a 54-matrix uniform array). The character is now skinned on the CPU each frame and drawn with a plain shader. If the pack ever fails to draw (GL error, textures not decoding), the built-in trainer is shown instead of nothing. The gym no longer syncs before its stations are built. The pack file format is unchanged, so the pack already on the phone still works.

## v177 — every exercise has its own designed movement
The trainer no longer uses one of a dozen generic poses. 63 movements (about 17 KB, `src/runtime/segments/50-gym3d-moves.js`) were designed with Gemini from `gemini/workouts/` prompts and checked against the rig with `moves-v1-*-validate.py` (feet on the floor, knees bending the right way, hands on the bar or floor, head and legs opposing when lying down). Each has key poses and a cycle (reps, holds, walking loops, flows). All 85 plan exercise names map to a movement (`moves-index.json`); other names (swaps) fall back to a keyword match, then to the old generic poses. The camera frames each movement from its reach. New props: bench, bike and elliptical, pull-up bar and assist platform. `tools/make_moves.py` regenerates the data segment.

## v178 — warm-ups and cool-downs move too
Every warm-up and cool-down step is titled alike ("Warm-up", "Cool-down") and showed the same standing pose. They now pick a movement from the text under the title (`G3_DESC` in `tools/make_moves.py`: cycling, jogging, squats, cat-cow, leg swings, neck rolls, arm circles, knee-to-chest, child's pose, legs up the wall, forward fold, chest/quad/calf/hip-flexor stretches, breathing, and so on). 8 new movements were authored for these (71 in all).

## v179 — 3D diagnostics
Settings → Look & feel → Guided workout → 3D diagnostics shows a small readout on the workout screen: frames drawn, frames per second, the guided-workout phase, which movement is playing, how long the set has been running, how long the trainer takes to draw (ms), and any error. A frame that throws no longer stops the screen silently: the error is caught and shown in the same readout.

## v180 — movements rebuilt by hand
The first movements (from Gemini's angle tables) looked janky: legs slid, feet floated, loops had corners. Every movement is now authored in `tools/moves_v2.py` with `tools/anim_lib.py`: planted feet and gripped handles are reach targets solved by two-bone inverse kinematics on the character's real joints, so they never slide; hips follow the planted foot (walking and running); positions and angles are sampled into dense keys and read back with a periodic cubic Hermite curve, so there are no corners. `tools/make_moves.py` checks that every target is within reach before it writes the data. The pelvis now turns with the spine, feet and hands can be held flat, spine curl bends the back (cat-cow, bridges), and new props were added (treadmill height, step box, wall, prone bench, pull-up bar). The workout also re-syncs itself if the gym ever misses a redraw. Tests: `tests/gym3d.test.js`.

## v181–v183 — cat eyes: big almond, set higher
The plain round eyes (neutral and surprised) are now big, soft almonds (a little wider than tall, gently pointed corners that lift slightly toward the outside, a tall soft pupil). Every eye sits about two pixels (at 1x) higher on the face and slightly closer together, so a blink or a mood change never makes the eyes jump; the happy, closed, sleepy, angry and sad eyes keep their own shapes. Whisker origins and the nose are unchanged. Tune `P0` and `ey, ex` in `50-pet-hd.js`.

## v184 — the quest reading no longer follows you to other tabs
Leaving the Quests tab in the middle of the 3D reading (tab bar, swipe, scrubber) now ends it: the scene is removed within a fifth of a second and the day is not used up, so the reading plays again when you return. While you scrub the tab bar, the blurred picture of the tab you are over also covers the 3D quest table (it used to sit above the blur), and a reading that would start while you are still scrubbing waits until you let go.

## v185 — the quest table no longer lingers when you scrub past Quests
The resting 3D table had no leave check during its first ~2.5 seconds (the camera swoop), and could even open after you had already left the tab. If the scrubber passed through Quests on its way to another tab, the table stayed on screen over that tab. It now never opens when the tab is gone, and the leave check runs from the first frame.

## v186 — Food tab rebuilt
The whole Food tab (Meals) is new: a macro-ring instrument (calories outside, protein / carbs / fat inside, tap a macro to isolate its ring, tap the middle to set targets), a week strip with a ring per day, one-tap quick add from recent foods, water, four meal cards, and a nutrition-details drawer (fiber, sugar, sodium, 7-day chart). The old Food Studio form and add sheet are replaced by one full sheet: search (bundled foods plus the optional USDA database), Recent / Favorites / My foods, a portion screen (serving chips, grams stepper, multipliers, live effect on today's macros), edit entry, create food (enter the label values for a serving), quick calories, and daily targets with presets. Code: `src/runtime/segments/50-food.js`, `src/styles/08-food.css`, `tests/food.test.js`. Data is unchanged (`foodLog`, `customFoods`, `waterLog`, `mealsMeta`, plus `protGoal`, `carbGoal`, `fatGoal`, `favs`).

## v187 — calories are no longer a ring
The dial now holds only the three macro rings (protein, carbs, fat). Calories are the tick bezel around it, which lights up clockwise, and the number in the middle.

## v188 — tap a ring
Tapping the protein, carbs or fat ring (or its tile) isolates it and shows its grams, target and amount left in the middle of the dial; tap it again, or the middle, to go back to calories. Daily targets now have their own "Targets" button beside the day arrows.

## v189 — cat and kitten eyes sit in the middle of the head
The painted heads are not always symmetric (a kitten's cheek can be wider on one side), so eyes placed from the face anchors looked off-centre. The face is now centred on the head at eye height: each frame measures the head's width in the body pixels and moves the eyes (and, a little less, the nose, mouth and whiskers) toward its middle, capped at 5 px and skipped when the head merges with the body.

## v190 — pecking order for two cats
Each cat has a confidence (0 to 1) that starts from its DNA (Dominant up, Skittish down, Outgoing a little up, shy / playful trait nudges it) and drifts back toward that baseline over days. A cat's power in a contest is 36 % DNA Dominant + 26 % maturity + 38 % confidence. Standoffs (walk up, stare, one stands tall and hisses, the other crouches and backs off to groom) are decided by a weighted coin flip on that power (a gap of 0.2 gives the stronger cat about 86 %), so the weaker cat can win; a win against the odds raises confidence more than an expected one, and a surprising loss costs more. Rank settles after three contests with a clear leader, and a flip is announced. Where it shows: who starts a chase (the confident cat, 65 %) and who grooms whom (the other, 65 %) when the cats are of similar age, standoffs in the director, and a Pecking order block on the Together card. Code: `src/runtime/segments/29-pet-rank.js` (hooks in 29-pet-pair.js), `tests/rank.test.js`; dev button "Two cats: standoff". Next: favourite spots and bowl order, posture (tail and ears) by rank, and resource tension.

## v191 — Smart Diet
The Food tab has three panes (Log, Plan, Shopping). Plan shows exactly what to eat on the selected day from the Athlete Smart Diet (Standard Diet or Simple Recipes): each meal with scaled ingredient weights, macros, a method, one-tap logging (or "Log the whole day") and Swap meal (the day rescales to hold its target). Portions scale to a calorie target = your calorie goal (or 2,500) x a fuel level (Auto from logged training minutes and the plan's day type, or Rest 0.9 / Light 1.0 / Moderate 1.12 / Hard 1.25): one factor is solved by bisection over the flexible ingredient ranges, so protein barely moves while carbs and fats follow activity. Vegetarian and Dairy-free preferences apply the meal swaps and replace any meal that cannot be made to fit. Shopping totals the week (or next week) by aisle, rounds up to whole packs, tucks things you probably have at home into a collapsed list, warns "buy twice" for fresh items spread over the week, ticks items off, and copies as text. Logged plan meals are normal foodLog entries with `entry.sd`. Data: `src/data/smartDiet.html`, built by `python3 tools/make_smartdiet.py` from `gemini/smart-diet/athlete/*.json` (validate them first with `gemini/smart-diet/validate.py`). Code: `src/runtime/segments/50-smartdiet.js`, `tests/smartdiet.test.js`. Known gap: the Simple diet has no dairy-free snacks, so a dairy-free user gets a pre/post-workout style snack instead.

## v192 — Smart Diet gets smarter
The Plan pane now follows the day as it happens. Meals you log keep what you logged, **Skip** drops a meal, and the meals still to come are re-solved so the day lands on the target given everything already eaten, including food that was not on the plan ("You ate 420 kcal outside the plan, so I trimmed the meals still to come by about 16%"). A coach line explains what changed or what to do next (protein behind after 2pm, start-of-day nudge, hard-day note, day complete). The hero is now a **fuel line**: one segment per meal, sized by calories, filled when logged, hatched when skipped, pulsing on the next one, with a grey segment for off-plan food. Meals are compact rows (time of day, name, calories, a tick or "next"); the next one is open by default and the rest expand on tap. Tests: 22 in `tests/smartdiet.test.js`.

## v193: Assistant / Gemini actions (deep links)
- `src/runtime/segments/50-deeplink.js` handles `lifemax://schedule/add?title=`, `lifemax://cats/feed`, `lifemax://open?feature=` (schedule, cats, feed, quests). Links that arrive before the app is ready are queued; a repeat within 3 s is ignored.
- Schedule links hand the title to the voice engine ("Put X on my schedule"), which asks for the time. Feed links feed every living cat (data-level, so it works with one or two cats).
- Android (needs a new APK, `NativeApi.VALUE` = 5): `android-wrapper/res/xml/shortcuts.xml` (static shortcuts plus `CREATE_THING` and `OPEN_APP_FEATURE` capabilities), `lifemax_shortcuts.xml` strings, `patch_manifest.py` adds the `lifemax://` intent-filter and the shortcuts meta-data, `@capacitor/app` added.
- Test without Google: `adb shell am start -a android.intent.action.VIEW -d "lifemax://cats/feed" com.lifemax.rpg`.
- Tests: `tests/deeplink.test.js` (15).

## v194: Gemini understanding for the voice engine
- `50-voice-gemini.js`: opt-in (Settings > Voice (beta)), key kept on the phone. The offline engine runs first; only when it does not understand (no intent, weak create guess, or low recogniser confidence) the one sentence plus today's date go to the Gemini API (`generateContent`, key in the `x-goog-api-key` header) with three functions: `add_to_schedule(title, date, time, duration_minutes)`, `feed_cats`, `open_screen`.
- A schedule call is turned back into a normal sentence ("Put X on my schedule on November 3 at 3:00 PM for 45 minutes") so the usual preview and confirm step still applies. Feeding and opening run directly.
- Tests: `tests/gemini.test.js` (10, fetch stubbed). `voice5` "end of day review" is time-of-day dependent and fails on earlier builds too.
