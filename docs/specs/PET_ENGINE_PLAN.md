# Cat engine: native 2.5D rewrite (plan)

Status: plan, not started. Owner decisions in this document are marked **Decided**; the rest are proposals to confirm.
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

## 5. 2.5D (depth with restraint)

The camera stays fixed. Depth comes from layers, light and a tiny sway, not from a 3D model.

- **Layers.** Cut the painted furniture out of the room PNGs (the survey's `PET_Z_BLOCK` rows give each base) into separate sprites: wall, back furniture, cat tree, sofa, counter, front props. The cat is drawn between layers, so it can pass behind a sofa arm or sit inside the tree, which today is forbidden by the "stay in front" rule. Cutting needs the wall behind each piece filled in; art work, done with scripted masks and checked by eye.
- **Slight thickness.** Furniture gets a few pixels of side and top face, drawn as a darker, offset copy of the silhouette (at most 6 to 10 art pixels) that shifts with the camera sway. The cat tree reads as a tree with a trunk. Limit: no perspective warping, no deep extrusion.
- **Parallax.** Four to six planes shift by 1 to 6 px with the camera sway (a slow idle drift plus phone tilt, damped). Fixed horizon, as today.
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

### 5.1 Built: the art and engine foundation (CT9, Native API 52, off by default)

Everything below is native and exists behind one flag, `RoomStyle` (`feature/pet/room`); `RoomStyle.CLASSIC` is the default everywhere, so the Overview card, the Cat tab and `RoomFidelityTest` draw and test exactly as before. No screen uses the 2.5D room yet: the integration point is `rememberRoomState(looks, style = RoomStyle.DEPTH25)` (and `HouseStage(kit, house, style)`, `RoomScene(art, style)`), with `DEPTH25_STILL` for reduced motion. Wiring a screen and the cats' behaviour among the layers (walking behind, hopping onto the pieces) is the next phase.

- **The cut** (`tools/pet_room_cut.js` with the measured spec `pet_room_cut_spec.js`; `gen_pet_native_assets.js` writes it with the rest of the art and `gen_pet_room_layers.js` is the same step on its own, with `--check` in CI and `--preview DIR` for a contact sheet). Each backdrop becomes a base (the room with the furniture removed and the wall and floor rebuilt) plus one transparent sprite per piece, in `resources/pet/room/layers/` with `layers.json` (place, base row, depth z, plane, whether a cat can be hidden behind it, the cat surfaces it carries, whether it may be thick). Pieces (15): the two rugs, the sofa and its two cushions, the floor lamp, the side table with the plant, the bookcase with its plant, the cat tree in five parts (trunk with the dangling toy, cubby, three platforms), the counter (with kettle, sink, tap, fruit) and the fridge. Not cut, on purpose: the curtains (the sky is the glass behind them and they are cheap to leave), the pictures, the clock, the wall cabinet, the windows; the bed, bowls, box, pouf and floor cushion are already their own sprites.
- **How a piece is found.** A rectangle and a colour rule per piece (`only` these colours, or `not` the room's wall and floor colours, or solid for a cushion), pieces listed back to front. A piece a later piece stands in front of (the cushions on the sofa, the tree over the bookcase, the tree over the rug, the platforms over the trunk) is made whole inside its box by running its own pixels along the row (a post: down the column), so nothing is missing when the parts move apart.
- **How the wall is rebuilt.** Each removed pixel takes the colour one pattern repeat away: the diamond wallpaper `(8,12)` and `(16,0)`, the bedroom's striped wall `(24,0)` and its diagonals, the kitchen's vertical stripes (straight down the column), the brick backsplash `(8,0)` and `(0,12)`, the floor boards and tiles by the period found on each row; failing that, the nearest live pixel on the row and then the column. The repeats were measured from the art (exact matches, 97 to 100 percent of pairs); a few pixels of wall behind the sofa and the counter are the nearest neighbour's, hidden by the piece. Reassembly is exact by construction and checked twice: the build refuses a room whose base plus pieces is not its backdrop, and `RoomLayersTest` redoes it from the shipped files, pixel for pixel.
- **Parallax** (`Parallax`, `DepthSway`): five planes, sky 0, wall and floor 1, back furniture 2 (bookcase, fridge), standing furniture 3 (sofa, counter, tree), near things 5 (lamp, plant table), and cats and floor props by depth (1 at the back wall, 3 at the art depth where the furniture stands, 6 at the glass): shifts are `round(sway * gain)` whole pixels, the same for a cat and a piece at the same depth. The sway is a damped spring (0.7 Hz, damping 0.95) pulled toward an idle drift (two slow waves, 13 s and 8.3 s, amplitude 0.45) plus the phone's tilt, stepped in fixed 1/240 s slices so the frame rate does not change it; reduced motion holds it at 0. The sky is drawn two pixels wider than the glass so a shifting wall never shows a gap; the house's two outer edges stretch their edge column.
- **Thickness** (`Thickness`): the face is the piece's silhouette swept along a vector of at most 6 art pixels minus the silhouette, drawn as dark (56 percent) atlas copies under the piece; at rest one pixel up and two to the right, sliding with the sway. Chosen pieces only (sofa, tree, counter, fridge); no scaling or warping. Look decision: a side face on the shaded side and a thin top edge read as a slab and a trunk without the sprite looking embossed; the face is deliberately thin so the flat 2D look of the art (and the cats) stays believable beside it.
- **Shadows and light.** `ContactShadow`: three nested whole-pixel ellipses (a checkerboard fringe, a ring, a core) in the art's own outline colour, under every cat (from the body's own shadow numbers, so it shrinks and fades as the cat jumps) and under each piece that stands on the floor. `FloorLight`: the window's four panes lean across the floor as four patches with the window bars between them and slide with the hour (the classic quad's colours and strength). `SeamShade`: six stepped rows of dark under the skirting, three above. `RimLight`: a cat within 150 px of a window gets its silhouette in warm (day), orange (dusk and dawn) or cool (night) one pixel to the window side, under the cat; no rim in front of the window.
- **Order** (`LayerStack`): everything sorted by depth (the page's `petZFromY` of the piece's base row), a cat behind a piece when it is deeper than that row (so it can walk behind the sofa and show its ears over the back) and in front otherwise; a cat on a surface a piece carries is drawn right after that piece (on the sofa: over the seat, behind the cushions; on the tree's middle platform: in front of the trunk and cubby and behind the platform above, that is inside the tree). The "stay in front" rule (`petZMinAt`) is unchanged here; lifting it for the cut pieces is the wiring phase.
- **Tests.** `RoomLayersTest` (exact reassembly, manifest against `PET_Z_BLOCK`, surfaces), `DepthMathTest` (planes, rounding, depth of a row, spring damping, frame-rate independence, clamped tilt, reduced motion), `ThicknessTest` (face against a brute-force sweep, at most 6 px, drawn face equals the mask), `DepthSceneTest` (the layered room with every effect off equals the classic room pixel for pixel at four hours and with cats in front; a cat behind the sofa's arm is hidden by it and in front is not; on the sofa; inside the tree), `RoomAllocationTest` (a depth frame allocates nothing). `Depth25ScreenshotTest` records 17 new images to judge the look: each room at rest and swayed left and right, by day and night, with and without cats.
- **What needs the phone.** The feel of the idle drift and of the tilt (there is no tilt sensor read yet: `HouseStage.setTilt` takes the value), the cost of a frame with every effect on (none of it allocates), and whether the face and the rim read well on a real screen at its pixel scale.
- **Depth levels** (`DepthLevel`, `RoomStyle.level`, `RoomStyle.depth25(level)`): the owner asked for a little more depth, so strength is one named level that scales everything together. SUBTLE is what was first built; MEDIUM and RICH push the same effects (the art is only moved and darkened, never scaled or skewed).

| | SUBTLE | MEDIUM | RICH |
|---|---|---|---|
| parallax px per unit of sway: wall/floor, back, mid, front, nearest cat | 1, 2, 3, 5, 6 | 2, 3, 5, 7, 9 | 3, 5, 7, 10, 12 |
| idle drift amplitude (share of full sway) | 0.45 | 0.55 | 0.65 |
| side face: at most px, top at rest, side at rest, extra side per unit of sway | 6, 1, 2, 3 | 9, 2, 3, 5 | 12, 2, 4, 7 |
| contact shadow size, darkness | x1, x1 | x1.25, x1.1 | x1.5, x1.2 |
| window light, rim light, seam shade | x1, x1, x1 | x1.3, x1.25, x1.25 | x1.6, x1.5, x1.5 |

`Depth25LevelsScreenshotTest` records the cat-tree bedroom, the living room with both cats and a cat behind the sofa arm at each level, at rest and swayed right (18 images); `DepthLevelTest` checks that SUBTLE equals the first build, every number grows with the level, and the face and parallax stay in each level's limits.

