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

## As built: buildings on a street, and a living city (NativeApi 170)

The owner's words: "instead of the cities thing, let's have it just be a building, and each habit adds a new floor to it,
and it's along a street, and you can scroll through the different habit stacks along the street, and keep the stars and
the cityscape background. The buildings should have little windows, and you should see the silhouettes of people working
inside", and "animate the background cityscape as well, and maybe add birds, airplanes, make the stars twinkle. Make it
look like a real place." The towers became buildings on one street, around them a living city (what were pending items 2
and 3). Rules, store and sheets are unchanged; the scene's geometry and the screen changed. The older paragraphs above
that speak of towers, blocks and the city describe the scene this replaced.

- The street: one building per foundation, side by side along one continuous street with a gap between lots holding a
  lamp, a tree or a bench by turns (a lamp and a tree finish either end). A sideways drag moves the street with the
  finger and ticks as each building passes the middle; let go, it coasts on and settles softly on the building nearest
  where it would stop (`StreetLayout.settle`), which is the one in front: the big button and the dots act on it. All
  buildings share the camera of the one in front (between two, the camera is between theirs), so a tall building zooms
  the whole street out a little; a building too tall even at 0.7 tilts the view up (the street is lowered) to its next
  floor and the floor above it, and an up-and-down drag pans it. The lowering is capped (`SceneWindow.panRoom`) so the
  ground floor, sign and awning always stand `LOBBY_AIR` clear of the bottom band (rough-day switch, big button, dots);
  a building too tall for that is drawn down to 0.6 (`CAMERA_TALL_MIN`) until its next floor is reachable. The City
  button is now Street: it (or a pinch) zooms out until the whole street fits, with each building's name under it and
  the 12-week time-lapse, in which the buildings grow floor by floor. The time-lapse is one slim card (the week, the
  slider between its two ends) so the street keeps the height; "+ Foundation" is a small button beside Building.
- A building: the ground floor is the foundation, a lobby with a shopfront, a striped awning (its colour the
  building's own) and a sign carrying its name, lit at night, with someone walking about inside. Each habit is a floor
  above it in order, as tall as its size, with six columns of windows that line up up the building (two rows on a
  stretch floor) and, in the band under them, a small plaque with its name and reps. A floor done today has warm lit
  windows with small silhouettes inside, picked by the habit's name (typing, stretching, reading, lifting, someone
  walking past; generic office life otherwise), moving in steps twelve times a second; an undone floor is dark glass;
  the next floor's outline breathes. Lights and people grow with total reps: one window and one person under 10, two
  from 10, most windows from 30, every window, more people and a gold cornice from 66. The facade follows the material:
  a steel frame and glass with scaffolding, then brick, then cut stone, then stone with gold trim. One missed day is a
  crack and a flickering light, two are sputtering lights and a small warning sign, coming back leaves a gold seam; a
  rough day shows today's floors at their two-minute height. A habit being broken is run-down storeys (boarded and
  dimming windows, scaffolding, a crane on top when they are the top), one taken down per clean day; once gone it is a
  tree in a small park beside the building. The rooftop reward is a sign on the roof: dark when locked, lit with
  chasing bulbs when the building is done today, with a flag beside it once claimed; otherwise a water tank stands
  there. Finishing a floor switches its lights on window by window, the people arrive with a hop, the floor settles on a
  spring with a buzz and a glow; a new material gets a sweep of light; a newly stacked habit drops into place with a
  puff of scaffolding dust.
- The living city (`domain/Ambient.kt`, pure Kotlin, every moving thing a function of the seconds and a seed, the day):
  every star twinkles at its own pace and stars fade in at dusk and out at dawn; a shooting star now and then (about one
  a minute, a second long); three rows of far city slide at their own speeds as the street scrolls, with windows
  switching on and off slowly at night and red warning lights blinking on the tallest; a glow over the horizon at
  night, haze by day; an airplane crossing every couple of minutes (red, green and strobe lights at night, a contrail by
  day); a V of birds by day and at dusk; clouds drifting (thin streaks passing the moon at night); a blimp now and then
  on a bright day; lamps with pools of light at night; a car's lights on the far road; trees swaying, the rooftop flag
  waving. The sky's colours blend over an hour at dawn and dusk. All of it stops when the tab is hidden and is a still
  picture (nothing passing) under reduced motion.
- Cost: one Canvas. Each building is worked out once when the habits change (`BuildingArt`: measured words, wall
  brushes and the paths of its brick, stone or steel, sills and reflections, its window order and who is in them); the
  silhouettes are point lists turned into paths once; the passes through the sky are written into kept objects; brushes
  are cached. The street's clock ticks about 30 times a second; only the buildings and furniture in view are drawn.
- Where: `domain/HabitsScene.kt` (building, street, camera, taps, windows, occupancy), `domain/Ambient.kt`,
  `domain/Silhouettes.kt`; `presentation/scene/BuildingArt.kt`, `BuildingPainter.kt`, `Backdrop.kt`, `HabitsStage.kt`.
  Tests: `HabitsSceneTest`, `AmbientTest`, `SilhouettesTest`, and `HabitsScreenshotTest` (the street at night, dusk and
  by day and in the light theme, a busy building, a brand-new one, the run-down building and the park, the rooftop
  locked, claimable and claimed, a rough day, a tall building, the zoomed-out street and its time-lapse, the dials and
  sheets, large text, and the night and day skies held at a moment with a plane and a shooting star, and birds and a
  contrail).

## As built: light and shadow (NativeApi 178)

The owner asked for "a real lighting system with shadows that move". One key light at a time (`domain/SceneLight.kt`, pure
Kotlin, `SceneLightTest`): the sun by day, the moon by night, standing where the sky draws it (`orbAt`), behind the
buildings. Light travels sideways (away from the side it stands on), down, and out of the facades towards the viewer, as a
unit vector; elevation 10° at the ends of the arc to 52° at the top (kept low so shadows stay long enough to read on the thin pavement). Its strength rises smoothly from 0 at the horizon, so
the sun sets to nothing before the moon (strength 0.3 of the sun) rises from nothing: no shadow pops at the 20:30 / 05:30
hand-over. A low dusk sun is 0.45 of noon.

Every shadow is a true projection along that light (`BuildingPainter.shadows`, drawn after the pavement and before anything
that stands on it): one matrix per light (`x' = x - lean·y`, `y' = -reach·y`, the ground foreshortened by
`SceneLight.FLAT` 0.5) turns what stands up into its shadow down the pavement, leaning away from the light. Casters: each
building's block (a parallelogram from its foot, so sunlight falls through the gaps between buildings), the park trees and
the pavement trees (the same sprite, tinted the shade's colour with `ColorFilter.tint(SrcIn)` under the same wind shear, so
the shadow sways with the tree), the lamp posts (their own paths) and the benches. All of a frame's shadows go into one
`saveLayer` at the shade's depth (sun 0.40, moon 0.26, times strength), so overlapping shadows are no deeper. The shade's
colour is the sky's blue by day, violet at dusk, near black under the moon.

What moves within seconds: tree shadows (wind); cloud shadows by day (`Backdrop` records each frame's cloud x, width and
solidity; `HabitsStage.cloudShade` draws a soft band of shade under each, offset sideways by the light's lean times a 90 dp
cloud height, clipped to the ground and the buildings' rectangles, 0.2 deep at full sun); at night each lobby window
throws a warm trapezoid of light 26 units down the pavement (`BuildingPainter.spill`) and the visitor's shadow is drawn
inside it from the same frames as the visitor (feet at the glass, stretched 1.15×, spreading from the window's middle),
sliding and stopping as they do. The visitor's silhouette uses the painter's `personFilter`, set only while the shadow is
drawn.

Also in this pass: a failing light on a missed floor (`Ambient.flickerLevel`) dims and recovers over a fraction of a second
in some 1.6 s slots (0.9 s when sputtering) instead of toggling 9–14 times a second, which the owner saw as rapid flicker
in the upper windows.

Not done: no developer hour slider for Habits (the projection has no developer flag), so the sun's position follows the
clock; lamp-cast shadows at night (the lamps are far from the trees and benches, the effect would be faint); facade shading
by the sun's side.

### Lighting v2: a near light (NativeApi 180)

The owner: "the shadows should move when you scroll... it should feel like real AAA stuff." A light infinitely far away
changes nothing when the camera moves, so the key light is now treated as standing where the sky draws it. The stage hands
the painter the orb's street x and the view's width in street units each frame; every caster's lean is
`SceneLight.lean(light, offset)` = the hour's lean plus `SPLAY` (0.9) per view-width the caster stands to the side of the
light (clamped at 1.5 widths), so shadows fan out from the sun and swing as the street scrolls under it (one matrix, set
per caster; `NearLightTest` checks the fan, the clamp and continuity through the light). Per building, `sunSide(offset)`
(-1 lit from its left .. 1 from its right, times strength, the moon at 0.4) drives: a wash of light from the lit edge and
shade from the far edge over each wall (`facadeLight`, unit brushes under a transform, drawn before windows and trim), the
right-hand side faces lit white or deepened (`sideLight`, floors, lobby and roof), and the sun's glint on every pane
(`glint`: a soft diagonal streak whose position across the pane follows the pane's offset from the light, so it travels
across the glass as you pan; day only). Tree shadows are drawn three times, each a little larger about the foot and
fainter (`PENUMBRA`), so they are sharp at the trunk and soft at the crown. At night each street lamp within `LAMP_REACH`
(64) of a building throws a warm additive pool on its walls (`lampLight`, clipped to the front and side). Shade depth
went to sun 0.46 / moon 0.28 to keep the tree cores dark under the penumbra split.

## As built: the sky's small things as painted art (NativeApi 181)

The owner: "detail all the little small details, like the car in the background going by, the plane, the shooting stars...
also the sun and moon, AAA quality." Three parallel passes, each a pure-Kotlin generator (reusing `Noise`/`Px`) baked once
into ARGB layers and drawn with `drawImage` + `ColorFilter.tint`, as the clouds are; each checked on java.awt contact sheets
before merging:

- `SunMoonSprites.kt` + `domain/MoonPhase.kt` (`SunMoonSpritesTest`): the sun as disc/core/limb/rays/corona/glow layers
  (limb darkening, a corona of noise streamers, rays turning 2.5°/s, a Plus-blended glow; at dusk 1.16× and deep orange);
  the moon as body/highlands/halo plus one dark-side layer per 1/32 of the cycle, baked lazily for the current phase only.
  The phase comes from the scene's date (mean synodic month from the 2000-01-06 new moon, at local midday); the terminator
  is soft on the night side only, with 8 % earthshine; the halo scales with illumination. `Backdrop(seed, day)` now takes
  the date. 523 KB.
- `AirSprites.kt` + `BirdShape.kt` (`AirSpritesTest`): the plane (5 tintable layers: shade, lit, glint, belly bounce,
  windows/beacon), its contrail (two engine cores merging into a noisy body that dissipates over 216 dp), the blimp (whole
  airship baked; the night panel's sweeping light, propeller and running lights stay vector). Birds stay vector: `BirdShape`
  writes a body/head/tail and wrist-bending wings into a reused FloatArray; `Ambient` gained tiers (3 sizes), per-tier
  flap rates, glides, bobs and a flock drift (`FLOCK` 7). 1,362 KB.
- `RoadSkySprites.kt` (`RoadSkySpritesTest`): four vehicles (hatchback, sedan, SUV, van) with day and night layers,
  headlight beam/pool/glow and tail-light reflection at night, a haze tint by day, 30 % of passes followed by a second
  vehicle; the meteor as a baked streak (core + halo, a fireball variant with green/orange) with a head bloom peaking
  mid-run, 7 sparkles from pure helpers and a train that fades after the head burns out (`SHOOT_SECONDS` 1.6). 214 KB.

Merging the three touched `Backdrop.kt` and `SceneSprites.kt` in overlapping places (each pass removed a different piece of
the old vector art); the old `rays` path, disc brushes and the blimp/plane paths and brushes are gone. Dead code left:
`haloBrush`/`haloWide` in `Backdrop.brushes()` and `SceneInk.Crater`/`SceneInk.Rays`. The pool is now about 6 MB
(`SceneSprites.bytes()`), the people's frames apart.

## As built: walking like people, and weather (NativeApi 183)

Walk irregularity (`domain/Gait.kt`, `Visit.kt`, `PeopleActivities.kt`, `GaitTest`): a per-look `Gait.Style` baked into each
look's own 16 walk frames (stride ±12 %, arm swing per arm, bob, posture lean, far-step timing split, shoulder roll, hip);
a per-person `Gait.Signature` applied at draw time with no memory (tempo .92–1.08, lean as a skew about the feet, bob
scale, a limp for ~6 % of seeds, hitch rate); per-step `Gait.liveTravel/livePhase/bob` (two incommensurate sines ±3 %
plus occasional hitches, slope always > 0 so the same live distance drives position and frame and feet never slide);
standing `Gait.sway/settle` and a watch-glance for 60 % of lobby visitors (3 extra baked frames per look). People atlas
5.2 MB. The pedestrian pass (street walkers) is to call `Gait.pace(size, look)`, `liveTravel`, `strides`, `bob`, `lean`,
`tempo`.

Weather (`domain/StreetWeather.kt`, `presentation/scene/WeatherSprites.kt`, `StreetWeatherTest`, `WeatherSpritesTest`):
the day's weather from the date (CLEAR 58 / OVERCAST 18 / RAIN 14 / MIST 10 per 100; a rain spell 1.5–5 h with 20 min
ramps; the pavement wets in 15 min and dries over 2 h; mist from ~4 h gone by 8.5–10 h; 2026-03-02, the screenshot date,
is clear), as `WeatherState(cloudiness, rain, wet, mist)` with `WeatherOverride.forced` for tests. `KeyLight.flat` and
`StreetWeather.light()` flatten the key light under cloud (strength ×.2 when overcast, no cloud shadows, grey shade).
Painter: `weather()` after everything on the street: `wet()` (Multiply darkening, a reflection layer of the lobby fronts,
signs, awnings, first-storey lit windows, trees, lamps with a long streak down the road, drawn twice ±0.6 for ripple,
masked to fade by the kerb), `puddles()` (3 per 200 units, sky-filled with a far rim, lamp glow at night, rain rings),
`rain()` (two scrolled streak layers, brighter in the lamp cones, splashes on pavement and awnings, drips). Backdrop:
`weather()` draws a grey veil, three low stratus and the mist band after the horizon. New screenshot tests `rainNight`,
`overcastDay`, `wetAfterRainDay`, `mistMorning`. Not verified in Compose before CI: the reflection layer's masks and
the lamp streak strength are the knobs if the wet street reads wrong. 232 KB of sprites.

## As built: the seasons and a lens flare (NativeApi 184)

`domain/Season.kt` (`SeasonTest`): `SeasonState(leaf, mature, autumn, blossom, snow, snowfall, dayOfYear)` from the date
with smoothstep curves (leaf up days 75–135 and down 288–335; autumn 255–295 up, 305–340 down; blossom 94–114 / 126–148;
snow 335–356, thaw 44–70; snowfall on 45 % of winter days, seeded per year); `Season.override` for tests (the screenshot
helper forces SUMMER so the 2026-03-02 references keep their green trees). `TreeSprites` grew `TreeSeason` looks
(SUMMER unchanged byte-for-byte, TURNING, AUTUMN in amber or red by variant, SPRING, BLOSSOM for variants 2 and 7, BARE as
a recursive branch skeleton kept inside the leafy clumps) plus a snow overlay per variant; `TreeDress.plan()` blends at
most two canopy looks over the bare skeleton per frame; baked lazily per season (`SceneSprites.tree(variant, season)`,
~1.1 MB per extra look, 484 KB of snow). `BuildingPainter.tree()` draws the dress; `treeSilhouette()` casts the skeleton
plus the canopy at the leaf alpha, so a bare tree casts a bare shadow. `SeasonFall.kt`: leaves and petals from each crown
(tumble, flutter, drift, land, slide, fade) and snowfall at two depths leaning with the wind; still scatter at 0 s; off on
the zoomed-out street. Snow caps: `snowGround/snowLamp/snowBench/snowPark/snowLobby/snowFloor/snowRoof`, one hook each,
white by day and the moon's blue at night. `domain/LensFlare.kt` + `FlareSprites.kt`: six additive ghosts on the far side
of the picture centre from the sun, wandering ≤7 dp with the scroll, strength 1 by day / .35 at dusk / 0 at night and
faded where a building covers the sun (`Backdrop.sunClear`); drawn in `backdrop()` right after the orb, so a ghost over a
building is behind it. New screenshot tests `autumnAfternoon`, `winterSnowDay`, `springBlossom`, `dayFlare`; every daytime
reference with the sun in frame changes (the flare).

## As built: street life (NativeApi 185)

The owner's top ask, with the bar set at "the pedestrian is close to the camera and must look amazing". `domain/StreetRig.kt`
builds the hero walker from the people rig at street scale (4 `StreetLooks`: long coat + crop + shoulder bag, jacket +
beanie + backpack, jacket + skirt + ponytail + handbag, tee + cap; coat hem trailing the stride, lapel, collar, cuffs, shoes
with heel and toe, hands with thumbs, bowed limbs, the far side at half tone, head nod with bob lag, hair/bag secondary
motion; 32 frames a cycle plus 4 settle frames); `StreetProps.kt` the dog (6 trot frames + sniff) and the bicycle with a
rider whose legs follow the cranks; `StreetSprites.kt` bakes them lazily at 3.6 px per unit with a `STREET` palette in
`PeopleRaster` (walkers ≤ 2.9 MB), and paints the near car (3 bodies × 5 paints, day/night bodies and an additive lights
layer). `domain/StreetLife.kt` is the pure schedule: a passer every 40–70 s (WALKER / DOG_WALKER / CYCLIST / PAIR),
crossing the whole visible street with 40-unit margins, the dog walker pausing on a whole number of strides; a car every
70 ± 20 s at ~95 units/s. Walkers and pairs take their ground from `Gait.liveTravel` (the per-step irregularity), the
same live distance placing them and picking the frame, so no foot slides; the dog walker stays metered so its stop lands
on the passing pose; the cyclist rolls. `SceneLight.NearLight` holds the lamp (lens 11.5, height 89.5, reach 70, lean and
reach per caster, stretch 1.8) and headlight projections. `StreetLifePainter.kt`: `keyShadows` inside the key-light layer,
`lampShadows` (a point-light shear per lamp, fading with the pool), `people` (contact shadows per foot, a rim on the lit
side, a warm additive overlay under a lamp, ±6 % by lane, the sagging lead; hidden at DOT/NONE), `sweep` (a Plus layer:
a warm wash clipped per facade and the lamp posts/trees cut out as DstOut silhouettes scaled about the headlight, so wall
shadows swing as the car passes), `car` (pool, beam, tail glow, turning wheels, day/night crossfade). Hooks: one line in
`BuildingPainter.shadows()`, draw calls in `HabitsStage` after shadows (lamp shadows), after the buildings (sweep,
people), after the furniture (car). Screenshot tests `streetWalkerDay` (STREET_WALKER_AT) and `nearCarNight` (NEAR_CAR_AT).
Tuned from contact sheets only: the wash strength, shadow depth (.6 × dark) and rim (.7) may want a nudge on the phone.

## As built: perspective, the side faces follow the camera (NativeApi 186)

`domain/Perspective.kt` (`PerspectiveTest`): `side(u, k)` = `((.5 − u)·2k).coerceIn(−1, 1)` for a building at screen fraction u
(positive: the right face shows, negative: the left, 0 at the middle, continuous through it), `width(side)` = DX·ease(|side|),
`shift(side)` the signed width the drawing uses, `left/right(shift)` the drawn footprint; `K_FOCUS` 1.2, `K_STREET` .5 blended
by zoom. `BuildingArt` now bakes a UNIT side face hinged on x 0 per floor and for the lobby; `BuildingPainter.onSide {}`
scales it to the live width and mirrors it for a left face; `roofFace`'s back edge, the cornice slab, the selection outline,
the shadow block, the ground AO strip, `lampLight`'s clip, the wet reflections' lobby block and street life's sweep clips all
take `shiftAt(cx)`; `sideLight` flips its lit side for a left face; `BuildingLayout.hit(x, at, shift)` tests the shown side;
`HabitsStage` sets `painter.viewX`/`perspectiveK` and uses the shift for cloud-shade rects, `sunClear` and taps. The park
keeps its lot position. Screenshot tests `perspectiveStreetLeft/Right/City`; every Habits reference changes (the front
building near the middle now shows a thin side).

## As built: the developer's clock (NativeApi 191)

The one page change of this round, the smallest possible: `50-native-habits.js` adds `dev: !!state.devMode` to the screen's
surroundings (the page's developer mode, Settings > Enable developer mode); `tests/native-habits.test.js` lists the key.
Natively `HabitsSurroundings.dev` (optional, strict when present, `HabitsFileTest`) rides into `HabitsProjection.dev`;
`presentation/HabitsDev.kt` holds `previewHour` in memory (never saved); `HabitsRoute` draws the street at that hour when
developer mode is on; `Controls` shows the chips Real / Day 13:00 / Dusk 18:36 / Night 23:00 under the dots. Toggling
developer mode in Settings reaches the open screen at the kit's next refresh (or on re-opening the tab).

## As built: the rooms behind the windows (NativeApi 192)

`RoomSprites.kt` rewritten (`RoomsTest`): a room is baked once per (kind, variant, window height) as `Room(base, lamp,
screen?, front)`: the unlit room at 3.2 px/unit (textured back wall, side walls narrowing to the back, a floor in perspective
with boards and a rug, skirting, corner shade, 2–4 shaded furniture pieces, props, the lamp fixture), an additive lamp layer
(warm pool on the wall and floor, sill spill, drawn Plus × the dusk pass's `lightsOn`), an additive screen glow for OFFICE and
LIVING (× `max(tv, monitor)`), and what stands in front of the person (desk, counter, sink, piano, table, board). Fifteen
kinds, three `Scheme` variants each, 135 bakes ≤ 4 MB lazily (variant 0 warmed, ~1.2 MB). `PeopleActivities` gained
TV_WATCHING, TREE_POSE, CHILD_PLAYING (two child looks, `Looks.CHILDREN`), TALKING (a mirrored pair), `RoomKind.DINING`,
per-room `shift` tables; people atlas 5.65 MB (< 6 MB kept). `Silhouettes.cast()` applies the spread rule: no two spatially
adjacent windows of one kind, the habit's lead kind capped at half the lit windows, variants rotating; curtains in seven
kinds. `windowLife()` draw order: base → lamp → day tint and `sunlight()` (a warm parallelogram skewed by `sunSide`, Plus,
by day) → person → screen → front → cooking steam (the dusk puffs) → curtains → sill item → glass sheen. The lobby got a lit
sign behind the desk. Screenshot tests `roomsDay`, `roomsNight`. The combined people+rooms budget assertion went from 6 to
8 MB (actual ≈ 7.0).

## As built: the buildings as real facades (NativeApi 193)

`FacadeSprites.kt` (`FacadeTest`): a seamless, exposure-neutral wall tile per `HabitMaterial` at 3 px/unit (CLAY brick
courses, STONE ashlar, GOLD polished panels, GLASS steel spandrels, FADE flaking render), 449 KB, mean colours within .11 of
the old `Facade` walls so the progression still reads; plus the awning cloth tile. `BuildingDetail.kt`: the architecture as
baked `Shapes` per ink (window frames with reveals, sills with lit top and shadow line, lintels by material, a string course
per floor, a drainpipe on a per-building side, a sparse AC unit or balcony in the last-to-light window, spray tags on boarded
ruin windows, cornice coping and dentils, lobby plinth, roof clutter picks). `FloorArt` carries `material` and ink paths built
once; `BuildingPainter.wall()` draws the tile as a repeated `ImageShader` brush under a 1/PPU transform; `inks()` with LOD;
the lobby's framed sign with a tube lit by `Dusk.lightsOn(SIGN)`, mullions and transom, panelled door; `roof()`'s cornice
drop shadow and `clutter()`; the reward sign's steel frame and lamps. Untouched: the window loop's light/room code, the
lighting passes, `Perspective`, `crackLine`, snow caps (same sill/cornice geometry). Screenshot tests `facadesDay`,
`lobbyNight`; every Habits reference changes.

### Facades v2: depth, glass and night (after NativeApi 197)

The owner: "bring the building exteriors up to the standard of everything else, AAA quality". The architecture gained
light-dependent inks (`DetailInk` REVEAL, RAISED, FACE_L/R, CAST_FROM_LEFT/RIGHT, AO, SOFFIT, GLASS_TOP,
GLASS_FROM_LEFT/RIGHT, BAR, BAR_LIGHT) whose alpha comes from one pure function, `FacadeLook.alpha(ink, sunSide, direct,
dark)` (signed: positive white, negative the sky's shade), so a frame only draws the floor's prebuilt paths at alphas; the
shadows flip with `sunSide` and soften under overcast (`direct` = strength × (1 − flat)). Every opening now reaches
`REVEAL` (2) past its pane: jambs and head in depth, the jamb facing the light lit, the head in shade, the frame inside it,
the sill (its top still at the pane's foot, `sillTop`, where the snow lands) with its cast shadow, lintels over the top
row only. Stone and gold floors have pilasters between the bays; glass floors are a curtain wall (mullions proud of the
glass with lit edges, spandrels mirroring the sky). A third path set per floor, `FloorArt.over`, is drawn over the rooms:
the head's and the lit jamb's shadow on the glass and a transom over tall panes. Every pane mirrors the hour's sky
(`paneBrush` from `SkyColors`, kept per sky) with the far city faint in its lower part by day (`FacadeSprites.mirror`,
slid with the street), fading as the room is lit; lit rooms light their reveals at night. The walls take the night's
ambient light (a cool Multiply, `NIGHT_DEPTH`), a darkening towards the pavement, a wet darkening and a faint haze up tall
buildings; the side faces carry their wall tile skewed to the face with two slit windows; gold and glass catch a
travelling polish band. The lobby: granite slabs (`FacadeSprites.lobbyTile`), a recessed shopfront (reveals and glass
shadows in `BuildingArt.lobbyOver`), sky-mirroring glass, a door with glass panels, pulls, kick plates and a transom that
glows at night, a step, two wall lamps with a warm halo, the sign with depth and raised letters (shadow by day, backlit
at night; `lobbyName` drawn as before), the awning's scalloped shadow leaning from the light and its cloth lit across.
The cornice's shadow follows the light, its arris catches it; rooftop things are shaded by side and rimmed at dusk.
New bakes ~93 KB. `FacadeTest` covers the bakes, the flip, the snow anchors and the over paths staying on the glass;
screenshot tests `facadesSunLeft/Right`, `glassTowerDay/Night` (scenario `glass`).

## As built: the sun and moon redesign (NativeApi 196)

`SunMoonSprites.kt` rewritten (`SunMoonSpritesTest`): the sun as six white layers (core, bloom, sky, rays, disc, disc light,
long gradients dithered); by day only bloom (brightness monotone from the centre, no ring or limb), the disc and its light
coming out low in the sky as a soft, flattened, gently limb-darkened orange body with a warm glow along the horizon; it fades
into the haze over the last half hour of dusk as the moon fades in. The moon's surface (true maria placement, Plato and
Grimaldi floors, Tycho's rays, Copernicus/Kepler/Aristarchus, fine crater texture, terminator relief) is stored once at
5 px/dp; only the current phase's picture is made, lazily, and a colour matrix turns it into the lit side (full brightness
at every phase), earthshine (about a tenth of the lit side on a 10 % crescent, none from ~25 % lit) and the sky behind. Per
hour colours and strengths live in pure `SunLook` / `MoonLook`. `Backdrop.heavenlyBody/orbLayer/orbTints` rewritten, plus
`sunLayer`, `skyAt`, `throughVeil` (the sun or moon through overcast, after the veil); the lens flare scales with sun
presence and (1 − cloudiness). 695 KB. The airliner is drawn at `PLANE_SIZE` .75 of its sprite (owner: "further away").

## Pending work (handed over)

State: build 517 (commit ee9a1d5, NativeApi 168) is published: native Habits with towers, typed suggestions, no cat. The
owner's requests since then, in his order of priority. Item 1 was fixed in NativeApi 169 (sheets at the bottom, no
double keyboard shift, the header stays); items 2 (buildings on a street) and 3 (the living background) are built in
NativeApi 170 and described in "As built: buildings on a street" above. Item 1 as reported:

1. Three phone bugs (Galaxy S25 Ultra, build 517), fix first:
   - a Habits sheet (seen on "New habit") hangs from near the top of the screen and is cut off mid-screen, with the scene
     visible below it; it must be a bottom sheet above the bottom bar, growing upward, scrollable;
   - with the keyboard open the sheet disappears (only the squeezed scene and the keyboard show). Suspect: the host view is
     resized by the IME and the sheet also applies imePadding, or the sheet is laid out inside the scene's measured content
     rect. Compare with Life Director's SheetHost, the Food add-food sheet and Recipes;
   - the app's top bar (picture, name, Log activity, mic, settings) is missing on the Habits tab: the screen covers the whole
     display. Other native tabs draw below that header. Suspect the cover rect taken from the empty placeholder view.
   Check every Habits sheet for the same problems.
Also open: Habits is not in the owner's manual backup (a native export/import is the proposed fix); the "After yog" partial
suggestion; the toast offset at large text. CI records Habits screenshots when the newest commit message carries
`[refresh-habits-snapshots]`. Local compile of the Habits Kotlin, Compose included, worked with the embeddable Kotlin 2.0.21
compiler and the Compose plugin from the Gradle cache (helper scripts were in /tmp/w/h and may not survive).
