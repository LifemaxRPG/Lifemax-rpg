# Cat engine: native 2.5D rewrite (plan)

Status: plan, not started. Owner decisions in this document are marked **Decided**; the rest are proposals to confirm.
Art lab (built, `PET_ART_LAB.md`): a toolchain (`tools/pet_art`) that makes new frames from the cat's own frames, and twelve new rows for every coat and kitten stage: a three-part jump (section 4.4), a turn with a body dip and the front view (4.3), the toy pickup set (reach and bite, carry walk with a mouth anchor per frame, head-flick toss), the waking yawn and head shake, a head-bump, grooming another cat and a nose touch, plus three pair poses (snuggle, groom, greet) as two coordinated singles with an offset table. Native ships them (`Clips`, `LabTable`); engine use is CT4/CT5.
Source of facts about today's system: the as-built survey (kept beside this plan as `PET_AS_BUILT.md`).

## 1. What the owner asked for

- **Decided.** The cats are redone. Same art: basic 2D cute cats, never realistic.
- **Decided.** Much better animation: smooth 2D motion, "the little cat is really living in there". 30 fps is not a requirement; smooth is.
- **Decided.** 2.5D: a fixed camera with a little depth. The cat tree and furniture get slight depth, not so much that flat furniture art looks odd beside flat cats.
- **Decided.** Cats come back live on the native Overview card ("a major upgrade").
- **Decided.** Claude builds it alone (Gemini is out of reach). Agents are allowed.
- Order: after the visual rewrite, before the logic port.

## 2. What exists (short)

A Canvas 2D pixel game in the page. Rooms are three painted PNGs (living room, bedroom, kitchen) with the furniture painted in. The cat is a palette-indexed sprite sheet (72x48, 28 animation rows, 4 coats, kitten stages) plus a rig (ears, tail, whiskers, leg and torso stretch) and an HD 2x face layer. A plan queue (walk, hop, act, face) runs at about 30 fps; a brain picks plans once a second. State lives in the saved game blob. Nothing is native except one still PNG on the Overview card.

What makes it look stiff (from the survey): animation changes instantly with no blending; every row starts on whatever frame the lifetime clock lands on; hops are two frames; walking is constant speed with a fixed step rate (feet slip); turning is a mirror flip; ears and tail have a few fixed poses; no breathing; the cat is only blocked from furniture, never in front of or behind it.

## 3. Architecture

### 3.1 Split: the body is native, the mind stays on the page (for now)

The page keeps owning the save, the brain, care, DNA and quests. The new native **cat body** takes over everything that moves and is drawn: the step machine (walk, hop, act, face), animation, physics, camera, light, touch, particles, sound.

The seam is a small, strict protocol (the same style as the other tabs):

- Page to native (the *mind*): `scene` (room, time of day, which furniture and toys exist, the cats with their look: coat key, growth, rig genes, pose flags from the brain), `plan` (a short queue of intents: go to a spot, hop to a surface, act `<animation>` for `<seconds>`, face a direction, with a plan id) and `affect` (the brain's mood, ear, tail, pupil states) whenever they change.
- Native to page (the *body*): `stepDone {planId, stepIndex}`, `arrived`, `touched {kind, where}` (tap, hold, stroke), `interrupted {planId}`, and a 1 Hz `pose` (position, surface) so the brain keeps its saved position and its checks.

The page sends about two steps ahead so a delayed bridge call never stalls a cat; native finishes the queue by itself and then idles, which is what a cat does anyway.

When the brain is ported to Kotlin in the logic phase, the same protocol becomes an in-process interface and the bridge disappears. The body does not change.

### 3.2 Why not port everything first

The brain (898 lines), pair director and likes are tuning-heavy and tied to save compatibility (seed strings, hash, day plan order). Porting them is a logic-phase job with golden tests (as done for the economy). The owner's wish is about how the cat looks and moves. The body-first split delivers that without touching saves. It also makes the later port safer: the brain's output is already a recorded plan stream.

### 3.3 Rendering

- Compose `Canvas` inside the native Overview card and the Pet screen, `FilterQuality.None` for sprites, 60 fps display-synced (`withFrameNanos`), no fixed game rate. Motion is time-based: positions and poses are functions of a clock and a velocity, so 60/90/120 Hz all look right.
- A pure Kotlin `CatBody` (no Android types) does movement, animation state and springs. It is stepped with explicit `dt`. Tested by a recorded simulation (see section 7), rendered by a thin `CatCanvas`.
- Assets: decode once from the page's embedded art (`petAssets`, growth sheets, HD, rig; about 5 MB) into native files; sheet order, frame size and palette indices are kept exactly as today so existing coats (including `dna_...` keys) render identically. The page composes DNA coats (it already does); native asks the page for any coat sheet it lacks (key to PNG), caches it by key, the same contract as `petPortrait`. Port the DNA composer later with the brain.
- Pixel look (**to confirm**): recommended is crisp pixels with smooth motion: art stays on its pixel grid, but position and effects move on a 1/4-pixel grid (what the web room does) and secondary motion (ears, tail, breathing) uses sub-pixel offsets rendered through the rig at the art's own resolution. No smoothing filter on the art.

### 3.4 Where the cat lives on screen

Overview card (live, small), Pet tab (full room), full-screen room, and later a home-screen widget or wallpaper (needs the body to run headless: another reason `CatBody` has no UI types).

## 4. Animation system (the big win)

Everything below is native, time-based, deterministic.

1. **Clips with phase.** Each row becomes a clip: frames, base rate, loop or one-shot, foot-contact frames, and an exit set. A clip starts at frame 0 (or a matched phase, e.g. walk to run keeps the stride), never at the global clock's phase.
2. **Transitions.** Pixel art cannot cross-fade, so blends are built from motion: short transition clips where they exist (`crouch`, `scratch_in/out`, `leap`), and otherwise a spring-driven squash and stretch of the whole body over 80-140 ms (stand to sit settles, a cat dropping into a loaf compresses). Interrupts finish the current beat (a footfall, a blink) when it is cheap, instead of cutting.
3. **Locomotion.** Velocity eases in and out (acceleration, deceleration, a skid on a hard stop). The walk cycle rate follows speed so feet do not slip; walk to trot to run is continuous. Turning is a quick body dip and a mirror at the middle of the dip, not a pop. A small body bob follows each footfall.
4. **Jumps.** Anticipation (crouch and wiggle), launch stretch, apex hang, landing squash and a settle. Height and duration come from the distance. Hops to the tree use the same code with different parameters.
5. **Secondary motion.** Ears, tail and whiskers are spring chains driven by the rig's part lists: they lag the body, overshoot, and swing when the cat turns. The brain's states (ear airplane, tail question mark) become targets for the springs, not fixed poses. Breathing is a slow torso scale; a sleeping cat breathes slower and deeper; a cat's tail tip twitches with a seeded noise.
6. **Attention.** A look target (the finger, a toy, a window bird) turns head, eyes and ears toward it with a short delay. Needs a head part in the rig (see risks).
7. **Reactions.** Startle, curiosity, slow blink, hungry begging, purr vibration: short reaction clips layered on top of whatever the cat is doing.
8. **Idle life.** Between plans a cat is never frozen: weight shifts, ear flicks, glances. Seeded so two cats differ.

### 4.1 Parkour (built, Native API 55)

The owner: "he should have to jump up on the counter first and then onto the refrigerator; they all need a little bit different depth." The hand-wired surface graph (`RoutePlanner`, a port of `petRoute`) is gone. Cats now jump by **capability**; every route, every jump and the table below come from geometry plus a cat's `JumpCapability`.

- **Capability** (`body/JumpCapability.kt`, numbers in `Tuning.Parkour`): ballistics with gravity 520 px/s^2. A standing adult reaches an apex 68 px above the take-off feet (rise 64 after the 4 px clearance) at up to 60 px/s sideways; a run-up adds apex +3 and speed up to +86 (ground speed after `sqrt(2*220*d)`, capped 96); furthest flight 56 px, furthest safe drop 84 px. Kitten stage 0..5 scales apex and drop by `Growth.scale` (0.6..0.93) and reach by its root; a weak (hungry) cat has 60 percent of everything and no run-up; the rig's `bl`/`lg` morphs, tiredness and mood are hooks (`CatBody.bodyLength/legLength/fatigue/mood`, `HouseCat`). The room's rungs fit it: sill 58 up (fits), counter 41, tree top 68 from the floor (does not fit; mid 32 then top 36), fridge top 101 from the floor (does not fit) but 60 from the counter (fits, at 90 percent of the cat's best).
- **Surfaces**: the web's twelve plus `fridge` (row 31, x 214..238), `bookcase` (row 33, x 108..136) and `sidetable` (row 105, x -22..-12; a lamp and the rugs are not surfaces). Their pieces carry the new keys in the room manifest (`tools/pet_room_cut_spec.js`, the only change there). Solids a flight must clear (`Solids.kt`: sofa, side table, bookcase, tree slabs and post, fridge, counter) are the manifest's piece boxes; a leap to or from a surface the piece carries ignores it. No flight crosses a room wall (x -4, 196). That is why the living-room sill is reached over the sofa and the kitchen sill over the counter.
- **Planner** (`body/ParkourPlanner.kt`): every pair of surfaces is a possible hop; it is an edge when a flight within the limits links a take-off and a landing point and clears the solids. Edge kinds: WALK (same surface), STEP (a few px, no crouch), LEAP (up or across a gap), DROP (controlled fall); a route is a CLIMB-CHAIN of them, Dijkstra over the surfaces with cost = walk + run-up + flight + settle + 0.7 s per hop + 3 s x effort^2 (x3 for a weak cat), a walk between two leaps costing 4x so chains land where the next run-up starts, and a seeded 0.0007 jitter as tie-break. `CatBody.routeTo` returns the steps (empty if the cat cannot get there; `parkourRoute` tells the cases apart).
- **Choreography** (`body/LeapDirector.kt`, step `PlanStep.Leap`; the page's `Hop` and old `Jump` are untouched): `turn` row when the cat must face the target; for a hard jump (effort >= 0.55) a pause with a look up (head glance), and near the limit (>= 0.85) a measuring wiggle; run-up if planned (no crouch, push at speed), else `jump_up` crouch; the arc is exact ballistics (`y = y0 - v0 t + g t^2/2`, flight time and apex from the plan, ends exactly on the target), `jump_air` rising then falling frame, `jump_land` squash. Back to back, the landing is cut to 0.1 s and the next leap starts from it (planner-made chains: floor, counter, fridge). Failed jumps cannot happen: only feasible jumps are planned.
- **House**: new autopilot moves FRIDGE, BOOKCASE and WINDOW with dwell (sit, loaf, wash, watch) on top, coming back the way they went. Two cats never land on the same single-occupancy surface, at the top or on the way (`Claim.passes`; the sofa keeps its 40 px rule).

Reachability (`ParkourReport`, recorded as `pet-traces/parkour-reach.txt`; floor to goal, `>` between hops):

| cat | apex / reach | fridge | counter | sill | tree top | bookcase | living sill | kitchen sill |
|---|---|---|---|---|---|---|---|---|
| adult | 68 / 60 | counter, fridge | counter | direct | mid, top | sill, bookcase | couch, lsill | counter, ksill |
| kitten 0 | 40.8 / 46 | cannot | box, counter | mid, top, bookcase, sill | mid, top | mid, top, bookcase | couch, lsill | box, counter, ksill |
| kitten 1..3 | 45..54 | cannot | counter | mid, top, bookcase, sill | mid, top | mid, top, bookcase | couch, lsill | counter, ksill |
| kitten 4 | 58.9 / 56 | cannot | counter | bed, sill | mid, top | bed, sill, bookcase | couch, lsill | counter, ksill |
| kitten 5 | 63.5 / 58 | counter, fridge | counter | direct | mid, top | sill, bookcase | couch, lsill | counter, ksill |
| weak adult | 40.8 / 36 | cannot | cannot | cannot | mid, top | mid, top, bookcase | couch, lsill | cannot |

All direct edges for an adult (rise in px, effort = share of its best): floor to bed 4, mid 32, couch 23, counter 41, sill 58 (0.87), box, pouf, cushion, side table; bed to sill 54; sill to bookcase 41 (run-up); mid to top 36; top to bookcase 31; couch to lsill 36 and side table; counter to ksill 17 and fridge 60 (0.90, run-up 10); box to counter 38; drops: counter to floor 41, fridge to counter 60, top to floor 68, bookcase to sill 41 and top 31, sill to floor 58, couch, mid, lsill to their stand-ins. Not there: floor to fridge, top, lsill, ksill or bookcase; fridge or bookcase to the floor (101 and 99 px, over the safe drop). The full per-cat edge list (about 45 edges each) is the golden.

Gaps in the art (not invented): a wall-kick or scramble frame for the near-limit jumps (the cat just flies), a stretch-reach from a crouch for the fridge edge, a paws-on-edge pull-up, a land-and-skid for drops.

## 5. 2.5D (depth with restraint)

The camera stays fixed. Depth comes from layers, light and a tiny sway, not from a 3D model.

- **Layers.** Cut the painted furniture out of the room PNGs (the survey's `PET_Z_BLOCK` rows give each base) into separate sprites: wall, back furniture, cat tree, sofa, counter, front props. The cat is drawn between layers, so it can pass behind a sofa arm or sit inside the tree, which today is forbidden by the "stay in front" rule. Cutting needs the wall behind each piece filled in; art work, done with scripted masks and checked by eye.
- **Solid furniture (the owner's note on the first look: "not a big black shadow; render a little tiny 3D couch").** Every standing piece is a small true solid: its painted sprite is the front, and each painted pixel is a column of solid running back by a measured depth profile (the sofa's seat, backrest and arms, the bookcase's cabinet with recessed shelves and books at the front, the tree's round trunk and slab platforms, the counter's worktop overhang and set-back plinth, the fridge's handles). A tiny software renderer shows the sides and tops the camera sway reveals, shaded from the piece's own colour ramps, and cats pass behind, in front of and inside the pieces by depth. At rest every piece is its painted sprite exactly. Built: section 5.1.
- **Parallax.** One camera: the wall moves by a small gain and every point by the floor row it stands on (the floor itself is drawn row by row, so it shears and nothing slides on it); a slow idle drift plus phone tilt, damped. Fixed horizon.
- **Light and shadow.** Soft contact shadows under the cat and props (replacing the 2 px blob), a window light cast on the floor that the cat crosses, an ambient dark gradient at floor-wall seams, the existing time-of-day tint kept. Rim light on the cat from the window side, a few pixels wide.
- **Depth of the cat.** Today's z-scale (55 to 185 percent) is the main depth cue and it mixes pixel sizes. Proposal: reduce the scale range, keep pixel size consistent per cat, and rely on y and layer order (so the pixels never shimmer).

## 6. Phases

Each phase ends in something the owner can see on the phone, behind a new Settings switch ("Native cats (beta)", off by default), with the page room untouched until the end.

| Phase | Result | Notes |
|---|---|---|
| P0 | Plan and as-built survey | this document |
| P1 | Pure `CatBody` + animation system + a recorded-simulation test harness; a debug room that plays every clip and transition | no bridge yet; screenshot and trace tests |
| P2 | Asset pipeline (sheets, rig, palette) and coats rendering identical to the web; `CatCanvas` | pixel-compare against `petPortrait` for the four legacy coats and a DNA coat |
| P3 | Overview card: live native cat on the card, driven by a local autopilot (idle life, no brain) | the quick visible win: smooth animated cat on the Overview. Built: capability `overview.cat.live.v1`, setting `nativeCats`; see OVERVIEW_INVENTORY.md, Live cat |
| P3b | The live cat for any coat: genetic (`dna_...`) coats | the page stays the composer; native asks `coat {key, kind}` (capability `overview.cat.coat.v1`) for the grey-index sheet and palette, caches by key, shows the still until the adult sheet is in |
| P3c | Both cats in the real room | The card draws the web room itself (painted rooms, sky by the local hour, light, bed, bowls, props, tint, lamp) with an easing camera and one or two cats living in the whole house on a polite-distance autopilot. Built: capability `overview.cat.live.v2` (the list shape `{cats: [...]}`, Native API 48); the web room against native pixel by pixel in `RoomFidelityTest`; see OVERVIEW_INVENTORY.md, Live cat |
| P4 | Page bridge: `scene`, `plan`, `affect`, events; the brain drives the native body | seed-for-seed the same day schedule as the page |
| P5 | Pet tab and full screen native: touch, feeding, play, toys, sleep, box, pair (two cats) | parity checklist from the survey section 5 |
| P6 | 2.5D layers, thickness, light, sway | needs the furniture cut-out art; can start in parallel after P2 |
| P7 | Retire the page room (keep behind a switch for a release), widget or wallpaper as a follow-up | |
| Later | Port the brain, DNA and care maths to Kotlin with golden tests (logic phase) | Saving moves to native: Opus job |

P3 first is deliberate: the owner sees a living cat on the Overview early, before the heavy bridge work.

## 7. Testing

- `CatBody` is pure: tests step it with fixed `dt` and assert traces (position, clip, frame, pose) for walk, hop, turn, sit, interrupt. A recorded trace is a golden file, so animation changes show up as reviewable diffs.
- Unit tests for the springs (settle time, no overshoot beyond a bound), phase matching, jump arcs, and the plan queue (empty queue idles, interrupt, stale plan id ignored).
- Pixel tests: legacy coats and DNA coat frames equal the web's rendering for a set of frames (Paparazzi plus a recorded web render). New images only: existing screenshots stay unchanged.
- Page bridge: strict decoders (as everywhere), a hand-over simulation test like the other tabs, shown failing when a rule is broken.
- Phone-only: smoothness at 120 Hz, battery and frame time (owner measures with the built-in debug overlay; target under 4 ms CPU per frame on the S25 Ultra, no allocations per frame in steady state), touch feel.

## 8. Risks

- **Rig has no head.** Look-at and head turns need a head part. Today's rig only has ears, tail, whiskers and stretch bands. Option: cut the head out of each frame by a mask per row (frames are hand-drawn art; the survey could not tell if the rig frames were hand-drawn or generated). Fallback: whole-body lean and eye-only look-at.
- **Cut-out furniture art.** Painted-in furniture needs its back wall reconstructed. Mitigation: do it per piece, tree first (the owner's example).
- **Two pixel densities.** 1x art and 2x HD art mixed. Proposal above keeps one density per cat.
- **Bridge latency.** Mitigated by the plan queue running ahead.
- **Save compatibility.** None touched in P1 to P7: the page remains the only writer. The later port needs seed-string and hash exactness (survey section 8).
- **Usage.** P1 and P4 are the design-heavy parts and warrant Opus. P2, P3, P5 and P6 follow the plan and can run on Sonnet with agents.

## 9. Open questions for the owner

1. Pixel look: crisp pixels with smooth motion (recommended), or fully smooth sub-pixel art?
2. Wish list: which moments should be unmistakably alive (waking up, begging, zoomies, sleeping, being petted, two cats together, watching the window)?
3. Should the Overview card be a small live window onto the room, or a close-up on the cat?

### 5.1 Built: the art and engine foundation (CT9, Native API 52; solid furniture Native API 55; off by default)

Everything below is native and exists behind one flag, `RoomStyle` (`feature/pet/room`); `RoomStyle.CLASSIC` is the default everywhere, so the Overview card, the Cat tab and `RoomFidelityTest` draw and test exactly as before. No screen uses the 2.5D room yet: the integration point is `rememberRoomState(looks, style = RoomStyle.DEPTH25)` (and `HouseStage(kit, house, style)`, `RoomScene(art, style)`), with `DEPTH25_STILL` for reduced motion. Wiring a screen and the cats' behaviour among the layers (walking behind, hopping onto the pieces) is the next phase.

- **The cut** (`tools/pet_room_cut.js` with the measured spec `pet_room_cut_spec.js`; `gen_pet_native_assets.js` writes it with the rest of the art and `gen_pet_room_layers.js` is the same step on its own, with `--check` in CI and `--preview DIR` for a contact sheet). Each backdrop becomes a base (the room with the furniture removed and the wall and floor rebuilt) plus one transparent sprite per piece, in `resources/pet/room/layers/` with `layers.json` (place, base row, depth z, whether a cat can be hidden behind it, the cat surfaces it carries, and its volume: the depth map and where a cat on each surface stands in depth). Pieces (15): the two rugs, the sofa and its two cushions, the floor lamp, the side table with the plant, the bookcase with its plant, the cat tree in five parts (trunk with the dangling toy, cubby, three platforms), the counter (with kettle, sink, tap, fruit) and the fridge. Not cut, on purpose: the curtains (the sky is the glass behind them and they are cheap to leave), the pictures, the clock, the wall cabinet, the windows; the bed, bowls, box, pouf and floor cushion are already their own sprites.
- **How a piece is found.** A rectangle and a colour rule per piece (`only` these colours, or `not` the room's wall and floor colours, or solid for a cushion), pieces listed back to front. A piece a later piece stands in front of (the cushions on the sofa, the tree over the bookcase, the tree over the rug, the platforms over the trunk) is made whole inside its box by running its own pixels along the row (a post: down the column), so nothing is missing when the parts move apart.
- **How the wall is rebuilt.** Each removed pixel takes the colour one pattern repeat away: the diamond wallpaper `(8,12)` and `(16,0)`, the bedroom's striped wall `(24,0)` and its diagonals, the kitchen's vertical stripes (straight down the column), the brick backsplash `(8,0)` and `(0,12)`, the floor boards and tiles by the period found on each row; failing that, the nearest live pixel on the row and then the column. The repeats were measured from the art (exact matches, 97 to 100 percent of pairs); a few pixels of wall behind the sofa and the counter are the nearest neighbour's, hidden by the piece. Reassembly is exact by construction and checked twice: the build refuses a room whose base plus pieces is not its backdrop, and `RoomLayersTest` redoes it from the shipped files, pixel for pixel.
- **Parallax** (`Parallax`, `DepthSway`), redefined with the solid furniture (Native API 55): depth is measured in floor rows (the row of floor a point stands on; the back wall is row 117) and a point on row `r` moves `round(sway * (wallGain + depthGain * (r - 117)))` whole pixels. The wall and everything on it moves by `wallGain`; the floor is drawn in runs of rows that move alike, so it shears like a floor; a cat moves by `World.floorY` of its depth, a cat on a piece by the row of that surface, a piece's front by its front row and its back by the row it reaches back to. One number thus gives the side a piece shows, the floor's shear and the cats' parallax, and they always agree (nothing skates on the floor, a cat on the sofa does not slide on the seat). The sky does not move (it is drawn `maxWallShift` pixels wider than the glass). The sway is the damped spring of before (0.7 Hz, damping 0.95, idle waves 13 s and 8.3 s, fixed 1/240 s steps, reduced motion holds it at 0).
- **Solid furniture** (`VolumeModel`, `VolumeRaster`, `VolumeSheets`; profiles `tools/pet_room_volume_spec.js`, generator `tools/pet_room_volume.js`, written with the cut into `resources/pet/room/volume/<piece>.png` and the manifest's `volume` entries, so `gen_pet_native_assets.js --check` and `gen_pet_room_layers.js --check` cover them). Each standing piece (13: not the rugs) has a depth map: for every painted pixel the front and back of a column of solid in eighths of a floor row (red and green of the PNG). The profiles are rules over rectangles and colours: a constant front, a front that runs back row by row (a painted top: the sofa seat, the arms, the worktop, the platforms, the fridge's top), a round body (in each row the matched span is a circle seen from above: the trunk, the lamp's shade and stem, the pots, the kettle), a soft cushion (nearest in the middle). Depths: sofa 11 rows (backrest 7 to 11, seat 0.5, arms 0, seams 1), bookcase 7 (frame 0, books 1.5 to 5.5, back panel 5.5, shelf tops 2.5, against the wall), cat tree platforms 12 (top face 10 to 1 from the back row to the front), trunk round about row 6 with a 3-row radius, cubby 2 with its hollow at 9.5, counter 11 (worktop 0 with its top 10 to 2.5, doors 1, panels and gaps 1.25, plinth 2), fridge 11 (handles 0, body 1, top 9 to 4), lamp 6 (shade a cone 2.5 deep), side table 6, cushions 3 (the pink one stands 1 row in front of the seat, the yellow one leans 6 back). The generator pushes back the few pixels that a later piece covers at rest (16 under the yellow cushion, 30 of the trunk under the platforms) and proves the promise below; it also checks every surface lies inside its piece.
  - **Rendering.** An oblique projection: a point `d` rows behind the front plane is drawn `d * vx` across and `d * vy` down, `vx = -sway * depthGain` (the back lags the front, so the side toward the camera shows) and `vy = -pitch * sway^2` (the camera swings on a shallow arc, a little higher at either end, so the tops show). Each picture pixel casts one ray through the columns (an exact grid walk, nearest first: a z-buffer by construction) and shows the face it meets: a column's front is the painted pixel untouched; a side, top or underside takes the material behind the painted outline there, one ramp step lighter for a top, one darker for a left side (the light is from the windows, above left), two for a right side, one more in a crevice (where a side meets the wall or a deeper part); a small step between neighbouring columns of one surface (the rows of a painted top, the columns of a trunk) keeps the painted colour, so a top reads as one plane and not as stairs; and where a revealed face of two or more pixels ends at an edge, a piece painted with the art's outline colour (the sofa, counter, fridge, cubby, yellow cushion) gets that outline, one pixel, like the cats; pieces painted without it (the tree, the lamp) get none. Ramps (`Palette`) come from each piece's own colours: greys with greys, other colours with those within 30 degrees of hue, by luminance, the outline never a shade, no jump of more than 110 in luminance; so a rendered piece uses only the colours it was painted in (the sofa's sides are its own shadow blue, the fridge's side its own grey).
  - **Cache.** The sway is cut into `2n + 1` camera steps, `n = ceil(deepest piece * depthGain)` (3, 4, 5 for SUBTLE, MEDIUM, RICH: a step moves the deepest back at most one pixel), with hysteresis (a new step only 0.15 of a step past the boundary). A step's pictures are rendered once, cut into bands of one floor row of depth, kept as strips of 12 rows cropped to their pixels and packed into one 512-wide sheet (about 100,000 pixels a step, about 4 MB for all eleven RICH steps); the canvas keeps one bitmap per step (`RoomCanvas.sheet`, a slot per step). The step the camera needs is rendered on first use and `DepthScene.step` renders one more each frame until all are made (about 11 ms a step on the JVM of the build machine under heavy load). Reduced motion renders only the rest step.
  - **Order** (`DepthOrder`, replacing `LayerStack`): every strip sorts by the floor row it stands on (its piece's front row minus its band), the atlas furniture by `World.floorY` of its depth, ties in manifest order, so the rest step redraws the painted room exactly. A cat goes after everything whose row is not nearer than its own: behind the sofa's arm, beside the tree with the near half of a platform's side in front of it and the far half behind. A cat on a piece's surface stands at that surface's depth (manifest `volume.surfaces`: couch 4, mid 1, top 3, counter 5) and the piece's nearer bands are pulled back to just before it (a cat sits on the seat, not behind its front); a surface may name a piece wholly over a cat on it (`volume.covered`: the top platform over a cat on the middle one, whose sitting pose is taller than the gap). Two cats go by how near they reach, lower first when both are on pieces, so the top platform's front lies between a cat on the middle platform and one on top.
- **Shadows and light.** `ContactShadow`: three nested whole-pixel ellipses (a checkerboard fringe, a ring, a core); under a cat in the art's own outline colour (as before), under a piece that stands on the floor in that room's darkest floor colour at a higher strength, so it reads as a deeper floor and never as a black smear. `FloorLight`: the window's four panes lean across the floor as four patches with the window bars between them and slide with the hour. `SeamShade`: six stepped rows of dark under the skirting, three above. `RimLight`: a cat within 150 px of a window gets its silhouette in warm (day), orange (dusk and dawn) or cool (night) one pixel to the window side, under the cat.
- **Tests.** `VolumeTest` (every piece rendered at rest equals its sprite pixel for pixel; a sway shows sides and leaves every visible front as painted; every shade inside the piece's own palette; the sofa's sides are its blues and not ink, the lit side not darker; ramps; quantised and held camera steps; the same steps at 30, 60 and 144 Hz; reduced motion renders only rest; the rest step's bands redraw the backdrops exactly; the cats' surfaces lie on their pieces where `World` puts them; a cat on the sofa after every sofa band and before the pink cushion's front; a cat on the floor between the bands nearer and farther; two cats in the tree with the whole top platform between them), `RoomLayersTest` (exact reassembly, manifest, every standing piece a solid), `DepthMathTest` (the one camera, rounding, the spring), `DepthLevelTest` (the table below), `DepthSceneTest` (the layered room with every effect off is the classic room exactly; the solid room at rest is the flat one exactly; the sofa's side shows when swayed; the cat behind the arm, on the sofa, inside the tree), `RoomAllocationTest` (a frame of the solid room allocates nothing once its steps are made). `VolumeScreenshotTest` records 33 images: each room with cats at each level swayed left, at rest and right, a cat behind the sofa's arm and cats around the tree at night (MEDIUM). The 35 `Depth25*` images of the first look are gone with the dark-copy thickness they showed.
- **What needs the phone.** The feel of the idle drift and the tilt (no tilt sensor is read yet: `HouseStage.setTilt` takes the value); the cost of the about 200 bitmap strips a frame draws (the painter's own work is a few hundredths of a millisecond on the JVM and allocates nothing) and of making each step once (one longer frame per step at first); the memory of the step bitmaps; whether the sides read on a real screen at its pixel scale.
- **Depth levels** (`DepthLevel`, `RoomStyle.level`, `RoomStyle.depth25(level)`): one camera at three strengths; at rest every level is the painted room.

| | SUBTLE | MEDIUM | RICH |
|---|---|---|---|
| wall gain (px per unit of sway) | 1 | 1.5 | 2 |
| depth gain (extra px per unit of sway per floor row in front of the wall) | 0.18 | 0.27 | 0.36 |
| the sofa's side at full sway (11 rows), a cat near the glass (row 150) | 2 px, 7 px | 3 px, 10 px | 4 px, 14 px |
| pitch at full sway (px of top per floor row of depth) | 0.10 | 0.16 | 0.22 |
| camera steps cached (`2n + 1`) | 7 | 9 | 11 |
| idle drift amplitude (share of full sway) | 0.45 | 0.55 | 0.65 |
| contact shadow size, darkness | x1, x1 | x1.25, x1.1 | x1.5, x1.2 |
| window light, rim light, seam shade | x1, x1, x1 | x1.3, x1.25, x1.25 | x1.6, x1.5, x1.5 |


