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
