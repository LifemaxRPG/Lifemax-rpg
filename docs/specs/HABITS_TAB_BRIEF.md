# Habits tab: brief for the designer

Status: brief only. No design, no code. Written so the next model (or a fresh session) can start from the owner's
request, the source material and a survey of what the app already has.

## The owner's ask

"A habits tab, using these (Atomic Habits) as reference. Design a system that makes it as easy as possible to alter
your habits." Then: "super intuitive, unique design, and useful. The same philosophy that went into designing the Life
Director tile" (the dial: one glance shows the whole day; every inch earns its place). The owner wants to see what a
model designs from scratch, so the design is open; the constraints below are not.

## Source material (owner-supplied PDFs, Atomic Habits bonus pages)

The Habits Cheat Sheet:

| Law | Create a good habit | Break a bad habit (inversion) |
| --- | --- | --- |
| 1 Obvious / Invisible | 1.1 scorecard of current habits; 1.2 implementation intention "I will [behavior] at [time] in [location]"; 1.3 habit stacking "After [current habit], I will [new habit]"; 1.4 design the environment so cues are visible | 1.5 reduce exposure: remove the cues of bad habits |
| 2 Attractive / Unattractive | 2.1 temptation bundling (pair a want with a need); 2.2 join a culture where the behaviour is normal; 2.3 a motivation ritual before a hard habit | 2.4 reframe: highlight the benefits of avoiding it |
| 3 Easy / Difficult | 3.1 reduce friction (fewer steps); 3.2 prime the environment; 3.3 master the decisive moment; 3.4 the two-minute rule (downscale until it takes two minutes or less); 3.5 automate (technology, one-time purchases) | 3.6 increase friction; 3.7 commitment device (restrict future choices) |
| 4 Satisfying / Unsatisfying | 4.1 immediate reward; 4.2 make "doing nothing" enjoyable (see the benefits of avoiding); 4.3 habit tracker, "don't break the chain"; 4.4 never miss twice | 4.5 accountability partner; 4.6 habit contract (costs public and painful) |

The Media pages (figures worth building into the design, not just quoting):
1% better a day (1.01^365 = 37.78; 0.99^365 = 0.03); the plateau of latent potential (results lag effort, a "valley of
disappointment"); three layers of change (outcomes, processes, identity) and identity-based habits (start from who you
want to be); the four stages cue, craving, response, reward as a loop; habit stacking as a chain where each habit is the
cue for the next; environment design (before/after of making water more available); the dopamine spike (reward
prediction: a missed expected reward drops, a late one spikes); social norms; the habit line (automaticity rises with
repetitions, a habit forms past a threshold, about 60 to 80 days in the walking example); friction and decisive moments;
the Goldilocks rule (motivation peaks at manageable difficulty between boredom and failure); mastery as stacked habit
lines.

## What the app already has (survey, file references are under lifemax_modular_v73/src/)

No feature or state key is named "habits" yet.

- Adding a web tab takes four edits: a nav button in `index.template.html:44-52`, a `views/<x>.html` section included
  at `:56-68`, JS in a new `runtime/segments/NN-<x>.js` (not ES modules, one shared global scope, listed in the script
  chain at `:132`), CSS in `styles/`. Then run `python3 tools/gen_module_map.py` (CI fails on a stale module map).
  Edit `src/` only, never `dist/`. Tab order, hiding and extras live in `50-tabs-edit.js` (`lmaTabsMigrate` must not
  hide a new tab from an existing saved order). Native tab recipe: `docs/architecture/NATIVE_TAB_GUIDE.md` (web
  projection plus a `Native<Tab>Plugin extends ComposeTabPlugin`, a `BRIDGE_CATALOG.json` entry, a settings toggle);
  design web-first, then project to native.
- Closest existing models: Quests (`state.quests`, `cadence` daily/weekly, `state.questDone`, `toggleQuest` at
  `04-runtime.js:288`, Daily Hand in `50-quests-hand.js`, spec `docs/specs/QUESTS_SPEC.md`) and the Journal coach store
  (`47-journal-coach.js`: lazy `jcStore()` writer, never-creating `jcRead()` reader).
- Logging and economy: `recordActivity(d)` (`04-runtime.js:286`, optional `opId` for idempotency) pushes to
  `state.activities` and awards XP per `43-economy.js` and `docs/specs/ECONOMY_SPEC.md`; `streak()` (`05-runtime.js:13`)
  counts non-passive activity days plus rest days; Sparks and perks are in `44-rewards.js`. Decide early whether a habit
  completion goes through `recordActivity` (counts for XP, streaks and Sparks and obeys the caps) or stays separate.
- Life Director (`features/life-director/`): goals, scheduled instances, weekly recurring rules painted as maintenance
  blocks, "morning routine" as a time block, not a habit list; state in localStorage `lifemax_director_state_v1`, not in
  the core save. A habit's "time and place" (implementation intention) and "after X" (stack) map naturally onto these
  blocks and onto Quests.
- Reminders: Capacitor LocalNotifications is wired (`33-android.js`: `lmaWantedReminders`, `lmaSync`, quiet hours,
  per-feature prefs in `lmaLoad().notif`). Habit reminders add entries keyed like `'h|'+habitId+'|'+date`, then
  `lmaSoon()`.
- Identity: `CLASSES` (athlete, scholar, entrepreneur; `05-runtime.js:200`), `state.classId`, and the attribute set that
  XP maps to; `dhWeakestAttr` already picks the weakest attribute. Identity-based habits ("I am someone who...") can
  reuse class and attribute.
- The cat: no habit hook; cheapest hooks are a `catcare`-style activity or extending the Daily Hand's `dhVerifyMet`.

## Rules a new feature must respect

- Core save: localStorage `lifemax_rpg_prototype_v1`, `SAVE_SCHEMA=1`; only `writeSave()` writes it, `save()` runs the
  economy hooks first. Add state as a new optional top-level key (for example `state.habits`) created lazily, read
  through a reader that never creates it, additive only, no schema bump; the whole `state` is already covered by the
  backups (ADR 0004/0008, `04-runtime.js:372-382`). A key that affects XP must be added to the economy shadow's
  `TRACKED` list (`05-shadow-boot.js:10`).
- Per-device preferences go in `lmaLoad()` / `lmaSave()`, not the core save.
- Local-date semantics only (`getToday()`, `parseD`, `addDays`; ECONOMY_SPEC TIME-1..8). Offline, embedded, never change
  the storage key.
- CLAUDE.md: user-facing release changes go in `src/data/update-history.json`
  (`docs/UPDATE_HISTORY.md`); programmer terms are defined briefly on first use when talking to the owner.

## Open decisions for the owner (ask once the design is on the table)

1. Do habit completions earn XP and Sparks (and count toward the streak), or is this a separate, quieter system?
2. Habits you want to break: how far does the tab go (friction and invisibility tools, contracts, accountability), or
   only good habits first?
3. Social features (accountability partner, culture) are out of scope until the app has any sharing; confirm.
4. Reminders: habit notifications on by default or opt in.

## As built (native, Native API 167)

The web tab built first (versions 1.0.0 and 1.1.0 of `src/features/habits`, saved under `state.habits`) was removed before it
shipped: the owner decided Habits is native from its data up. The concept is unchanged: habit stacking drawn literally. A
foundation is something you already do; habits are blocks stacked on it; one button lays the next block; material follows
total reps (glass, clay at 10, stone at 30, gold at 66) and a missed day never removes a rep; block height is the size (two
minutes, normal, stretch); one missed day is a crack, two a wobble, coming back leaves a gold seam; "rough day" lays the
two-minute size for today only; a habit being broken loses a floor per clean day and ends as a tree; a foundation can carry a
rooftop reward that unlocks when its stack is done; city view shows every tower with a 12-week time-lapse. There is no
cat on the tower (removed at the owner's request: the cat lives in the Cat tab only). Owner defaults taken: no XP, Sparks or streak; Quests separate; no reminders.

Where it lives (android-wrapper/native/src/main/java/com/lifemax/rpg/feature/habits): `domain/HabitsRules.kt` and
`HabitsEngine.kt` (the rules and commands, pure Kotlin), `data/HabitsFile.kt` (the habits file, `files/habits/habits.json`,
schema v 1, atomic write, safety copy), `domain/HabitsScene.kt` (geometry), `domain/AnchorCatalog.kt` and `HabitCatalog.kt` (the suggestions' plain
data: add a line to add an entry), `domain/HabitsSuggest.kt` (how what is typed finds an entry), `presentation/` (the screen,
the scene on a Canvas, the sheets). Details, the backup status and what remains on the page: docs/architecture/MIGRATION.md, "Habits".

Native design: the scene fills the screen and the controls float on it. A block is drawn face on with its top and side
showing, so its name is flat and readable. One tower stands in front, its neighbours peek in at the edges, smaller and
fainter; a sideways drag moves them with the finger and a release settles on a spring; an up-and-down drag pans a tall tower.
Blocks not laid yet today are blueprints and the next one glows. A tap on a block opens its dials in a sheet that leaves the
scene bright (size as three blocks of growing height, pairing, foundation, order, undo, retire); a tap on the foundation
edits it; the outline above the roof stacks a new habit; a dot per foundation and a plus sit under the big button.

Layout: the heading and the controls are measured first and the scene is told what they take, at any text size. The towers
keep to the window between them (`SceneWindow`): a tower is made smaller until it fits, down to 0.7; one still too tall keeps
its foundation on the ground line, with its name in view, and what stands on it slides down behind the slab (the pan), fading
out under the heading. The sun, the moon and the clouds keep under the heading and beside the tower in front. A crack or a
gold seam is its block's own: it starts on the top edge, stops inside the face and is clipped to it. While a block's dials are
open the heading is put away, so the block can rise over the sheet.

Suggestions: the new-foundation and new-habit sheets offer chips under the first field as it is typed. Foundations come from
about 110 everyday anchors (those of the current part of the day before anything is typed; an activity the catalog does not
know is offered as "After ..." and "Before ..."); a tap lays the foundation. Habits come from about 150 habits with their
two-minute, normal and stretch names, and about 40 habits to break with their clean days and a replacement; a tap fills every
field of the sheet, which now also asks for the stretch name or the replacement. Matching ignores case, hyphens and spaces,
looks at the start of every word, then at an entry's other words, then allows one slip of the finger from four letters on;
at most six are shown and never one that is already there.

Not built yet: drag to move a block, reminders, the first-run scorecard, Life Director supplying foundations, Habits in the
owner's manual backup.

## Pending work (handed over, nothing started in code)

State: build 517 (commit ee9a1d5, NativeApi 168) is published: native Habits with towers, typed suggestions, no cat. The
owner's requests since then, in his order of priority; none of this is built:

1. Three phone bugs (Galaxy S25 Ultra, build 517), fix first:
   - a Habits sheet (seen on "New habit") hangs from near the top of the screen and is cut off mid-screen, with the scene
     visible below it; it must be a bottom sheet above the bottom bar, growing upward, scrollable;
   - with the keyboard open the sheet disappears (only the squeezed scene and the keyboard show). Suspect: the host view is
     resized by the IME and the sheet also applies imePadding, or the sheet is laid out inside the scene's measured content
     rect. Compare with Life Director's SheetHost, the Food add-food sheet and Recipes;
   - the app's top bar (picture, name, Log activity, mic, settings) is missing on the Habits tab: the screen covers the whole
     display. Other native tabs draw below that header. Suspect the cover rect taken from the empty placeholder view.
   Check every Habits sheet for the same problems.
2. Scene redesign, the owner's words: "instead of the cities thing, let's have it just be a building, and each habit adds a
   new floor to it, and it's along a street, and you can scroll through the different habit stacks along the street, and keep
   the stars and the cityscape background. The buildings should have little windows, and you should see the silhouettes of
   people working inside." Agreed reading: one building per foundation on a continuous street (horizontal scroll with
   momentum and a soft snap; the centred building is the active one; a zoom-out fits the street and holds the time-lapse; the
   City toggle goes); ground floor is the foundation with its name on a sign; each habit is a floor, floor height is the
   habit's size, its name on a flat plaque; a floor done today has warm lit windows with small animated silhouettes, an
   undone floor is dark, the next one pulses; lit windows and people grow with total reps; facade finish follows the material
   steps (10, 30, 66 reps); a missed day is a crack and a flickering light, two is sputtering lights, coming back is a gold
   seam; a habit being broken is a run-down building taken down a floor per clean day, ending as a small park; the rooftop
   reward is a rooftop sign or garden that lights when the building is done today. No cat. Rules, store and sheets unchanged.
3. Living background, the owner's words: "animate the background cityscape as well, and maybe add birds, airplanes, make the
   stars twinkle. Make it look like a real place." Agreed reading: every star twinkles, a rare shooting star; far-skyline
   windows switching slowly at night, blinking warning lights, horizon glow; an occasional airplane (nav lights at night,
   contrail by day), birds by day and dusk, drifting clouds; street lamps, a distant car light, swaying trees, a waving flag.
   Ambient only, stops when the tab is hidden, a still frame under reduced motion, deterministic from time and a seed (pure
   Kotlin, unit tested), frozen in screenshot tests.
Also open: Habits is not in the owner's manual backup (a native export/import is the proposed fix); the "After yog" partial
suggestion; the toast offset at large text. CI records Habits screenshots when the newest commit message carries
`[refresh-habits-snapshots]`. Local compile of the Habits Kotlin, Compose included, worked with the embeddable Kotlin 2.0.21
compiler and the Compose plugin from the Gradle cache (helper scripts were in /tmp/w/h and may not survive).
