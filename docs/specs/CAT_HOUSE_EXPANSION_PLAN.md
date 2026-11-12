# Cat house expansion plan: more rooms, more to explore

Status: PLAN, with WP-H1 built and a first slice of H4, H5 and H7 (see "Built so far" below). Written 2026-11-12
against `origin/codex/director-dial-controls` (26c9575).
The owner's wish: "expand the house, add some extra rooms, make the cats explore a little more".

## Built so far (NativeApi 160)

- **H1, done.** `R/house.json` lists every room (id, name, world x, the `roomOf` cut, the stretch a trip stops in, the window,
  whether its light lies on the floor, core or not, the bond that opens it). `room/HouseMap.kt` reads it; `RoomLayout.HOUSE_X0`,
  `HOUSE_WIDTH`, `windows`, `World.HOUSE_MIN_X/MAX_X`, `World.roomOf`, the floor surface, `Spot.room`, the classic and 2.5D room
  painters, the layer manifest parser (`houseX` by room place) and `HousePlans.travel` all read the map. With the first three rooms
  open the engine is the old one: `RoomFidelityTest`, `RoomLayersTest`, the golden traces and every other existing test are unchanged.
- **H4, first version.** `lifemax_modular_v73/tools/gen_pet_room_new.js` draws the Sunroom, Study and Bathroom (bases, 10 solid
  and 5 flat pieces, depth maps, door frames at the new seams, `layers_ext.json`); CI checks it (`--check`).
  Placeholder art in the style of the painted rooms; an artist replaces files by name.
- **H5, floor travel.** Walk limits, camera bounds, the floor and the planner follow the open rooms; `travel` picks a room by weight
  for any number of rooms (a neighbour 70 percent, the rest otherwise: the old three-way roll for three). Each new room has a
  floor place (sun patch, study rug, bath mat) the brain chooses only while its room is open (`Spot.open`).
- **H7, the unlock.** The page opens rooms by bond (Sunroom 2, Study 3, Bathroom 4; highest bond among cats that are grown or
  past kitten stage 2), records them in `state.petHouse` with the world command's save, and sends `house` in the world block;
  native reads it (optional key) and opens the rooms (`HouseMap.select`) before the art is made.
- **H2, first slice: raised places.** Five new `SurfaceId`s, native only like the parkour tops (`bench` in the Sunroom, `desk`
  and `shelf` in the Study, `tub` rim and `sink` in the Bathroom), each with its `Surface` row (`body/World.kt`), a `Solid`
  (`Solids.kt`; the chairs are solids with no owner), `Occluders` pieces for every standing piece of the three rooms, and a
  `Spot` (`Spots.kt`, no page id), chosen only while the room is open (`Spot.open`). The generator draws each surface into
  `layers_ext.json` (`surfaces`, `volume.surfaces`, and `stand`: feet row and columns) and `--check`s that every column of the
  walk lies on painted pixels of its piece. The study shelf was lowered (top row 34) and the sink cabinet moved down so the
  shelf is reached from the desk and the sink from the floor with the cats' jump. `NewRoomsSurfaceWalkTest` runs every
  `SurfaceWalkTest` check with the whole house open and compares the body's tables with the manifest.
- **H7, start-up.** `HouseMemory` keeps the opened rooms in `files/pet-house.json` (written when a block opens a room);
  `CatWorldSetup.restoreHouse` reads it and calls `HouseMap.select` when a Cat screen plugin loads (`load()` of
  `NativePetPlugin` and `NativeOverviewCardsPlugin`), so the house is known before the first projection and before any art.
- **H9, first slice.** The catch-up story words the new places (`CatchUpRun.where`: "in the sun patch", "up on the sink").
  Replays already never pick a closed room's places (`Spot.open`).
- **Not built:** the generated solids/occluders/place tables (H2; the tables above are written by hand and tested against the
  manifest), the 64-bit piece mask and step residency (H3), per-room
  light, sun patch that moves, birds, dripping tap (H6), the reveal toast and `seen` use, the map strip and glide (H8), per-room
  catch-up lines beyond the place words (H9), per-cat kitten limits (the page applies the kitten rule house-wide instead), the phone check (H10).
- **Decisions taken at the plan's defaults** (OWNER DECISIONs still open for the owner to overturn): rooms Sunroom, Study,
  Bathroom (OQ-H3); side-by-side strip (OQ-H2); unlock by bond 2/3/4 using the highest bond (option B, OQ-H1, OQ-H10); kittens
  stage 0 to 2 do not count towards it (OQ-H9, house-wide); generated placeholder art shipped room by room (OQ-H4); old seams stay
  open plan, new seams get a drawn door frame (OQ-H12). A room that opens while the app runs shows from the next start of the app
  (the art, the cats and the camera must agree on the house; `HouseMap.lock`).

Citation shorthand (all paths from the repo root):
- `N/` = `android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet/`
- `R/` = `android-wrapper/native/src/main/resources/pet/room/`
- `P/` = `lifemax_modular_v73/src/runtime/segments/` (the page)
- Anything marked **UNVERIFIED** I did not read, run or measure. Anything marked **OWNER DECISION** is the
  owner's call, not an engineering one.

Terms: a **px** is one pixel of the room art, which is drawn small (a room is 192 by 144
px), scaled up by a whole number on the phone. A **piece** is one cut-out furniture picture drawn over the
room's backdrop. A **volume sheet** is a second picture of a piece in which each pixel's colour says how far
back it stands, so the engine can give furniture a little thickness. A **solid** is a rectangle a flying
(jumping) cat cannot pass through. A **surface** is a standable place (floor, sofa, window sill). A **golden
test** replays a fixed scenario and fails if one number differs from a stored answer. **Blob** here is the
cats' world, saved as one small block of JSON text.

## 1. What exists today (findings)

### 1.1 The headline correction: the house is already three rooms wide

The brief says the Cat tab shows "one living room". The code says otherwise. The house is one long strip of
three painted rooms standing side by side, 576 px wide, and the camera pans across it:

- `N/room/RoomArt.kt:72-76`: `HOUSE_X0 = -192`, `HOUSE_WIDTH = 576`, `HEIGHT = 144`, `BACKDROP_WIDTH = 192`.
- World x of each room: living -192..0, bedroom 0..192, kitchen 192..384 (`R/layers.json` line 1 gives each
  room a `houseX` of 0, 192, 384; `N/body/Solids.kt:15-16` says the same).
- Cats may walk from x = -154 to 346 (`N/body/World.kt:11-12`), one continuous floor with no wall between rooms.
- The page has the same strip: `P/15-runtime.js:130-135` (`PET_HX0=-192`, `PET_HW=576`, `petRoomOf`).
- The Overview card and the Cat tab show a window at least 192 px wide of this strip
  (`N/presentation/HouseStage.kt:34-44`); the full-screen room shows at least 120 px across and the whole
  height (`HouseStage.kt:445-456`, `fullScale`, whole-number scales only).

Why it may feel like one room (**UNVERIFIED on a phone**): a 192 px window shows one room and moves only when the cat
does; crossing rooms is one rare plan (`N/body/HousePlans.kt:244-257`, `travel`, 70 percent to the neighbour,
`NEAR_ROOM_SHARE`, line 404); there is no doorway or wall; the camera is being fixed by another agent. So this plan
adds rooms **and** makes crossings legible and worth making.

### 1.2 Which rooms are drawn, which are only art, which the page knows

| Room | Backdrop art | Cut-out furniture | Native engine knows it | Page world model knows it |
|---|---|---|---|---|
| Living (world x -192..0) | `R/living.png`, `R/layers/living_base.png` | sofa, 2 cushions, floor lamp, side table with plant, rug (`R/layers.json` rows for room "living") | yes: `World.roomOf`, `Spots`, `Solids` (sofa, side table), `Occluders` | yes: `PET_SPOTS` couch, lsill, lrug, pouf, cushion (`P/25-pet-likes.js:8-21`) |
| Bedroom (0..192) | `R/bedroom.png`, `layers/bedroom_base.png` | bookcase with plant, cat tree (post, cubby, 3 slabs), rug | yes (bookcase and tree solids, `Solids.kt:52-64`) | yes: bed, sill, tree, rug |
| Kitchen (192..384) | `R/kitchen.png`, `layers/kitchen_base.png` | counter, fridge | yes (counter, fridge solids, `Solids.kt:67-70`) | yes: counter, ksill, box, kitchen tiles |

All three are fully drawn, cut out, given volume sheets and wired; no room art is unused. The other files in `R/` are
`items.png` (256 by 128: sun, moon, clouds, bowls, bed halves) and `props/` (box, cushion, pouf), which are props, not
rooms. There is **no hallway, bathroom, study or sunroom anywhere**, in art, data or code.

Measured by me: the backdrops use 86, 99 and 73 colours, 218 in all; hand-pixelled (measured hex colour sets in
`lifemax_modular_v73/tools/pet_room_cut_spec.js:17-30`).

### 1.3 How the room is composed

- **Backdrop plus pieces.** Each room is a base picture (the wall and floor with the furniture removed and the
  wall rebuilt behind it) plus pieces drawn over it in manifest order; drawing them back gives the original
  backdrop exactly (`N/room/RoomLayers.kt:92-97`, `RoomLayersTest`). The manifest is `R/layers.json`: 3 rooms,
  15 pieces (rug x2, sofa, 2 cushions, lamp, side table, bookcase, 5 tree pieces, counter, fridge).
- **Pieces' pixels** are packed into one atlas 256 px wide (`RoomLayers.kt:115`, `ATLAS_WIDTH`).
- **Depth.** 13 of the 15 pieces have a volume (the 2 rugs are flat and have none). A volume says depth in floor rows, whether the back is against the wall, and how far behind the
  front a cat on each surface of the piece stands (`RoomLayers.kt:24-35`). Floor rows: the back wall meets
  the floor at row 117 (`room/Parallax.kt:21`, `WALL_ROW`; `room/DepthLight.kt` `SEAM_ROW = 117`), the cats'
  art depth floor is row 132, the horizon 98 (`N/body/World.kt:13-16`).
- **Camera steps.** The camera sways a little; the room is cut into `2n+1` camera steps (`n = 3, 4, 5` for
  SUBTLE, MEDIUM, RICH) and each step's pictures of every solid piece are cached as strips in a 512 px wide
  sheet (`N/room/VolumeSheets.kt:7-30, 377-381`; `docs/specs/PET_ENGINE_PLAN.md` section 5.2 line 194: 9 steps
  at MEDIUM is 3.5 MB of bitmaps, `DepthFrameCostTest` asserts under 8 MB).
- **Depth guard.** If the average frame costs over 0.6 of the display's budget the level steps down
  MEDIUM, SUBTLE, off (flat classic room); it steps up only after 120 s calm
  (`N/room/DepthGuard.kt:9-41`).
- **Hard limits that matter for more rooms** (found by reading):
  1. `DepthOrder.MAX_PIECES = 31` solid pieces, because a cat's "which pieces are in front of me" answer is
     one 32-bit number (`N/room/DepthOrder.kt:177`; `DepthScene.kt` asserts it in its `init`). **13 are used.**
  2. `SurfaceId` has 15 entries and a solid names the surfaces it carries as a bit each in a 32-bit number
     (`N/body/Solids.kt:34-37`, `Solid.bit = 1 shl ordinal`; `World.kt:6-33`). **17 free.**
  3. The room count 3 is written in many places: `RoomLayers.kt:128` (names array), `RoomArt.kt:12-13, 24-25,
     40, 54-55` (backdrop enums), `DepthScene.kt:103` (`IntArray(3)`), `DepthScene.kt:478` (`BASES`),
     `RoomLayout.windows` (3 sky offsets, `RoomArt.kt:79`), `RoomScene.kt:73-74` (classic room painting),
     `RoomScene.kt:134` (window light on the floor of only two rooms), `World.roomOf` (`World.kt:139`),
     `HousePlans.roomOf` (`HousePlans.kt:389-401`), `Spot.room` (`Spots.kt:39`), the page's `petRoomOf`.
  4. A piece's house position is `room index * 192 + x` (`RoomLayers.kt:72-73`), which cannot place a room left of the
     living room; the manifest already has a `houseX` per room that the parser ignores (`RoomLayers.kt:127-129`).
  5. `HOUSE_X0` is used in 29 places (grep); every hard-coded coordinate is in the current world x frame.

### 1.4 How cats navigate

- **Two levels, no grid.** Walking is one number: x along a single continuous floor that spans
  `HOUSE_MIN_X..HOUSE_MAX_X` (`N/body/ParkourPlanner.kt:300-302`), plus a depth number z (how near the
  glass). There is no tile grid and no path search along the floor.
- **Jumping is a graph.** `ParkourPlanner.plan` runs Dijkstra (cheapest-route search) over the 15 surfaces,
  trying every pair as a possible hop; a hop is an edge when a flight within the cat's limits links a take-off
  and a landing and `Solids.clear` finds no furniture in the arc (`ParkourPlanner.kt:60-130`;
  `Solids.kt:73-100`). So the cost of one plan grows roughly with the square of the number of surfaces
  (15 now, about 25 with this plan: roughly 3 times as many pair checks, **UNVERIFIED cost on a phone**).
- **Places.** `Spots` is the fixed table of "places to visit" with flags (SLEEP, REST, SOFT, SUNNY, WINDOW,
  HIGH, HIDDEN, PARKOUR, SCRATCH, ROOM_FLOOR, BOWL), each carrying a surface, an x and a depth z
  (`N/brain/Spots.kt:25-110`). about 21 before the bowls and scratch places. Per-cat liking lives in `SpotBook`.
- **Plans.** `HousePlans` builds step lists (walk, leap, act). `travel` is the only "go to another room"
  plan and it is a 3-way roll (`HousePlans.kt:244-257`); room floor ranges are literals (`ROOM_LO/HI`,
  lines 402-403: -140..-74, 16..170, 218..328). Cats keep "a polite distance" via `Claim`
  (`HousePlans.kt:1-60`).
- **Hiding.** `Occluders` keeps cats from resting where furniture hides them; its boxes are hand-copied from the
  manifest (`N/body/Occluders.kt:1-50`).
- **Doors, portals, rooms as objects: none** in `feature/pet` (grep). A room is only `roomOf(x)`, with cut points -4
  and 196 (the web split, `World.kt:137-139`; not the art seams at 0 and 192).

### 1.5 How the page saves the world (and what a new room must fit into)

- The page is the only writer of the save. Native hands it "the world blob"; the page checks and stores it as `state.petWorld = {epoch, rev, at, op, blob}` with `writeSave()` (`P/50-native-cat-world.js:215-235`).
- The blob is capped at 8 KiB (`WorldContract.BLOB_MAX_BYTES`, `N/world/WorldContract.kt:27`; page `BLOB_MAX`,
  `50-native-cat-world.js:21`), at most 2 cats, version 1. Each cat has `pos {s, x, z, dir}`, `spots` (place
  name to a small count; place names are any `[a-z_]{1,16}`, `WorldBlobJson.kt:42`), plus plan state.
- **Closed lists that new rooms must extend**: surfaces (`WorldBlobJson.kt:41`, built from the `SurfaceId` enum
  so it grows by itself), the page's `PET_SPOTS` (`P/25-pet-likes.js:8-21`), `WorldContract.SPOTS`
  (`WorldContract.kt:60-77`).
- **x is bounded to the three rooms** in the blob: `pos.x`, toys' x, the gift's x are all validated against
  `HOUSE_X_MIN..MAX = -192..384` (`WorldBlobJson.kt:128, 227, 236`; `WorldContract.kt:55-56`). A cat saved in
  a new room today would make its blob entry fail to read (that cat is then "derived afresh", the rest of
  the world is kept, `WorldBlobJson.kt:13-19`).
- The page's classic room clamps any saved x to the old house (`petClampHouse`, `P/24-pet-depth.js:123`), so a
  cat last seen in a new room reappears in the living room or kitchen if the player switches to Classic.
- Offline catch-up: native replays the same planner in a fast mode over the gap and tells up to three lines
  (`N/brain/CatchUpRun.kt:1-60`); the page's older one is `petOfflineCatchup` (`P/26-pet-brain.js:630-657`).
- There is **no "unlocked rooms" or "items placed" state anywhere** in `state.pet*`. Unlock precedents that
  do exist: toys unlock by bond level or days (`P/25-pet-likes.js:24-31`: `unlock:{L:1}`, `{days:2}`); how near
  the glass a cat may come depends on trust tier and stage (`petDepthMaxZ`, `P/24-pet-depth.js:46`). Bond
  levels are Stray, Wary, Curious, Friendly, Fond, Devoted (`P/15-runtime.js:57`, 0 to 5).

### 1.6 What the screens assume about one room

- A **192 px minimum window and whole-number scale** (`HouseStage.kt:34-44, 445-456`): a 1152 px house cannot be
  shown whole (it would need a scale below 1).
- The **camera** follows one point with a lead and eases by walking state; it cuts if the target is over 260 px away
  (`CUT`) and keeps the view inside `HOUSE_X0..HOUSE_X0+HOUSE_WIDTH` (`N/room/RoomCamera.kt:22-73, 66, 56-61`);
  `CameraDirector` chooses between two cats (`RoomCamera.kt:85-137`).
- The **Overview card and the Cat tab share one `HouseStage`**; a change reruns `CatHouseScreenshotTest`,
  `RoomFidelityTest`, `RoomAllocationTest` (`docs/specs/CAT_TAB_PLAN.md` line 211).
- **Light**: one lamp glow (`RoomArt.kt:103-107`), window light on the floor only for living and bedroom
  (`RoomScene.kt:134`), sun and moon positions that assume a 64 px glass 22 px in (`RoomScene.kt:96-105`), rim light
  from three window centres (`DepthLight.kt:159-165`).

## 2. The target house

### 2.1 Rooms

Keep the existing three exactly where they are (their coordinates are in saved cat positions, the page's
tables and every test) and **add on the two ends of the strip, never in the middle**. Three new rooms, chosen
for what a cat does in them:

| # | Room | Where (world x) | Why it is worth walking to | Cat behaviours it enables |
|---|---|---|---|---|
| 1 | **Sunroom** (glass room) | right of the kitchen, 384..576 | The warmest, brightest place. A long window bench, a wicker chair, a plant shelf, birds at a feeder outside the glass. | sunbathing on a sun patch that moves with the hour; bird-watching (window + HIGH flags); high plant shelf as a parkour goal; the room the cat visits most on sunny afternoons |
| 2 | **Study** | left of the living room, -384..-192 | The tall, quiet room. A desk, a chair, a tall shelf, a paper basket. | desk top (a leap from the chair), shelf top (a two-hop climb: chair, desk, shelf), basket to hide in (the "box" behaviour reused), nap on the chair |
| 3 | **Bathroom** | left of the study, -576..-384 | Small and odd: a bathtub, a sink with a dripping tap, a laundry basket. | sit in the dry tub (a HIDDEN, cool place), the sink for the "hydro inspector" quirk the page already has (`WorldContract.kt` `hydro_inspector`), curl in the laundry basket |

Not chosen: a **hallway** (little to do; its value is a doorway, which every seam gets, 2.3) and a **balcony/garden**
(a different scene, WP-H11, OQ-H5). Swaps if the owner prefers: a **cat nursery** (toy room) or a **cellar** (dark,
very hidden) instead of the bathroom.

Resulting strip, left to right (6 rooms, 1152 px wide, 192 px each):
`Bathroom | Study | Living | Bedroom | Kitchen | Sunroom`, world x -576..576.

Walkable range grows from -154..346 to -538..538 (38 px in from each outer wall, as today: the old limits are 38
px inside -192 and 384).

### 2.2 Floor plan: recommend the same side-by-side strip

Options:
- **A. Side by side, as today (recommended).** Every room is the same 192 by 144 front-on view with the same
  floor perspective, the same wall-to-floor row (117) and the same camera. All of this is already built: the
  camera pans in x only; solids, spots, surfaces, blob positions and the depth math are all one x axis.
  Cost: new art and wiring, but no new concept in the engine.
- **B. Top-down-ish 2.5D rooms.** New art for all three old rooms, a new projection, a two-axis cat position, a 2-D
  path search and a new camera; it discards the cut-outs, volume sheets and `RoomFidelityTest`. **Not recommended.**
- **C. Two storeys with stairs.** Needs a vertical camera axis and a taller house (height and the blob's position are
  one row of rooms today); big, and no more cat places per unit of work. **Not now**; a second strip could come later.

Cost of a long line: Bathroom to Sunroom is about 1000 px, about 31 s at the 32 px/s walk (`N/body/Tuning.kt:23`);
fine while cats mostly visit near rooms (the 70 percent neighbour share).

### 2.3 Doorways, stairs, flaps and how a cat travels

- **Travel is walking along the same continuous floor** (v1). No teleport, no portal, no loading. This keeps
  the planner, the camera, the offline catch-up and every saved x working with no new concept. A room change
  is just `roomOf(x)` changing.
- **Doorways are drawn, not built.** Each seam of a new room gets a flat "door frame" piece (two pillars and a
  lintel, about 14 by 100 px, drawn under every cat so a pillar never hides one, like a rug:
  `baseY = null` in the manifest). At the three new seams (Study|Living at -192, Bathroom|Study at -384,
  Kitchen|Sunroom at 384) these are the only change in the walking path. The two old seams stay open plan
  (the living room's right edge has a side table and the bedroom's left a curtain, so a pillar would cover
  furniture). **UNVERIFIED whether open-plan seams look acceptable in a pan; the door-frame pieces are cheap to
  add later.**
- **A locked room is a closed door.** The door frame is drawn with a door panel (second flat piece, hidden once
  unlocked) and the walkable range stops at the door; the camera also stops there (it cannot show a locked room).
- **No stairs** (one storey): height is parkour (chair, desk, shelf). **Cat flap: stretch** (WP-H11), the one real
  "portal" (the cat leaves the house), with its own state.

### 2.4 Camera

- **Follow the selected cat across rooms** (the camera fix's assumption, section 7). On 1152 px the same code works,
  because it reads only the cat's x and `HOUSE_X0`/`HOUSE_WIDTH`; those come from the house map (locked end rooms
  excluded, `RoomCamera.left`).
- **Cut or glide.** `CUT = 260 px` (`RoomCamera.kt:66`) suits six rooms: a cat placed far away (catch-up) cuts. For a
  deliberate "look at that cat" switch **recommend a 0.5 s glide**, so the owner sees where the other cat is (H8).
- **House overview = a map strip, not a zoomed-out room**: whole-number scales cannot show 1152 px. A thin strip under
  the room, drawn by code from data: one cell per room in order, locked ones greyed, a dot per cat, the camera's room
  outlined; tap a cell to glide there. No art. About 24 dp tall at the room's lower edge (**UNVERIFIED on the phone**;
  placement is a design choice for the owner).
- Depth skew must be constant along the strip (section 7); every room uses the same wall row 117 so seams match.

### 2.5 Per-room furniture and places to visit, as data

The point of the plan is that a room is a **data file**, not code. One `house.json` (generated, shipped in
`R/`) holds a list of rooms; each room lists its pieces (already in `layers.json`), its windows, its lights,
and its **places** (the thing the planner reads). A place has: id, a label, surface, x, depth z, the same flags
as `Spots` (SLEEP, REST, SOFT, SUNNY, WINDOW, HIGH, HIDDEN, PARKOUR, SCRATCH, BOWL), a `kind` for the story
text, and optional `needs` (the piece the leap must go over). Today these are the `Spots.add(...)` literals
(`Spots.kt:76-110`) and the page's `PET_SPOTS`; both become generated from the one file, so the page and
native cannot disagree (the page's table is the closed list the world contract checks, section 1.5).

**Existing places** keep their ids and coordinates (bed, sofa, sills, tree, pouf, box, counter, fridge top, rugs, bowls, ...).

**New places** (x are proposals inside each room's walkable stretch; depth z 40 to 60 on floors; a design target
until the art exists):

Sunroom (world 384..576):
- `sun_bench`: a long window bench, surface BENCH, SLEEP REST SOFT SUNNY WINDOW. Two spots (left and right end).
- `sun_patch`: floor place, REST SUNNY ROOM_FLOOR, **x moves with the hour** (the patch of sun slides across
  the floor, 10 to 16 h), so a cat that likes sun "follows" it. Pure arithmetic on the hour, deterministic.
- `wicker`: wicker chair seat, surface WICKER, REST SOFT.
- `plant_shelf`: HIGH PARKOUR, reached by the bench then the shelf (two hops).
- `birds`: a watch spot at the glass by the bird feeder, WINDOW HIGH; only meaningful with birds drawn in the
  sky layer (WP-H6 draws two or three birds that come and go on a clock; cheap, in the sky painter).
- `sun_cushion`: a prop (like the pouf), SLEEP SOFT.

Study (world -384..-192):
- `desk`: desk top, surface DESK, REST; reached from the chair seat or by a leap from the floor if the cat is
  strong enough (the planner decides from the cat's jump limits, `JumpCapability`).
- `chair`: chair seat, REST SOFT SLEEP; the stepping stone.
- `study_shelf`: tall shelf top, HIGH PARKOUR; the highest place in the house: needs chair, desk, shelf.
- `paper_basket`: a hide place (like `box`), HIDDEN SLEEP.
- `study_sill`: window sill, WINDOW REST.
- `study_rug`: floor, REST ROOM_FLOOR.

Bathroom (world -576..-384):
- `tub`: inside the dry tub, a low floor-like surface TUB, REST SLEEP HIDDEN (cool).
- `tub_rim`: the rim, TUBRIM, REST, reached from the floor (a step).
- `sink`: the cabinet top beside a tap, SINK, REST HIGH; a tap that drips (a particle every few seconds
  while a cat is near).
- `laundry_basket`: prop, SLEEP SOFT HIDDEN.
- `bath_mat`: floor, REST ROOM_FLOOR.

That is 10 new surfaces (DESK, CHAIR, STUDY_SHELF, STUDY_SILL, TUB, TUB_RIM, SINK, BENCH, WICKER, PLANT_SHELF)
plus the new props (sun cushion, baskets), taking the 15 to about 25 to 27 of the 32 the bit mask can
carry (section 1.3 item 2). Cats can take a surface that is a prop (`prop = true`, like the pouf) with no
bit-per-solid if it carries nothing.

Explore-ness: a **novelty bonus** for the planner (not owned here, section 7): a room or place not used lately adds
weight to `travel` toward it, decaying as the cat stays. Weights go in `BrainTuning`.

---

## 3. Art needs

Format: 192 by 144 px RGBA, 8 bits per channel (as every file in `R/`). Every room must share the wall-to-floor row 117, the cats' floor row 132 and horizon 98, so a seam
has no step and the floor perspective matches (`Parallax.kt:21`, `World.kt:13-16`).

### 3.1 Files per new room

For each room `X` in {sun, study, bath}:

| File | Size (px) | What |
|---|---|---|
| `R/X.png` | 192 x 144 | the finished backdrop (classic flat room and the screenshot reference); equals the base with every piece drawn over it |
| `R/layers/X_base.png` | 192 x 144 | wall and floor only, windows' glass left clear (the sky shows through) |
| `R/layers/<piece>.png` | per table | one picture per cut-out piece, tight bounding box, clear where empty |
| `R/volume/<piece>.png` | same as its piece | volume sheet for each standing piece (not for rugs and mats), written by `tools/pet_room_volume.js` from a depth spec |
| `R/layers.json` | rows | one `rooms` entry (`base`, `houseX`) and one `pieces` row per piece: name, room, file, x, y, w, h, baseY, z, occludes, surfaces, volume {front, depth, wall, surfaces, covered} |
| `R/items.png` additions | within 256 x 128 or a second atlas | bird sprites (2 to 3 frames, about 8 x 6), basket, sun cushion props, door panel |

Piece table (design targets, scaled from the existing pieces: sofa 132x42, counter 124x52, fridge 40x98,
bookcase 32x112):

| Room | Piece | w x h | Standing? (volume) | Surface carried |
|---|---|---|---|---|
| Sunroom | `sun_bench` | 120 x 28 | yes, depth 8, wall | BENCH |
| Sunroom | `wicker_chair` | 44 x 48 | yes, depth 8 | WICKER |
| Sunroom | `plant_shelf` | 60 x 70 | yes, depth 6, wall | PLANT_SHELF |
| Sunroom | `big_plant` | 30 x 70 | yes (occludes) | none |
| Sunroom | `door_frame_sun` | 14 x 100 | flat | none |
| Study | `desk` | 96 x 44 | yes, depth 10 | DESK |
| Study | `desk_chair` | 28 x 44 | yes, depth 6 | CHAIR |
| Study | `study_shelf` | 36 x 110 | yes, depth 7, wall | STUDY_SHELF |
| Study | `paper_basket` | 30 x 24 | yes (a prop) | none (a hide place) |
| Study | `study_rug`, `door_frame_study` | 100 x 12, 14 x 100 | flat | none |
| Bathroom | `bathtub` | 112 x 40 | yes, depth 10 | TUB, TUB_RIM |
| Bathroom | `sink_cabinet` | 56 x 60 | yes, depth 8, wall | SINK |
| Bathroom | `laundry_basket` | 28 x 30 | yes (a prop) | none |
| Bathroom | `bath_mat`, `door_frame_bath` | 60 x 10, 14 x 100 | flat | none |

Solid pieces added: 4 (sunroom) + 4 (study) + 3 (bathroom) = **11**; with the 13 existing that is 24 of the 31
the cat mask can name. That leaves 7 spare. Rule for the artist: **at most 5 solid pieces per room**. (If a
future room needs more, widen the mask to 64 bits, which is a small change tracked in WP-H3.)

The **shared palette**: the union of the three painted rooms is 218 colours (measured). Rules for every new
room: wall and floor colours come from one of three families already used (cream wall and plank floor: living
and bedroom; sage and white tile: kitchen), furniture uses the existing woods (`TABLE_WOOD`, the tree's
browns) and plant greens/pots (`PLANT_GREENS`, `PLANT_POT` in `pet_room_cut_spec.js:21-26`), each colour
used with 2 to 3 shades plus a highlight, black outlines avoided (the existing art has none: it uses a darker
shade of the piece's own colour). Window sky colours are painted by the engine from the hour (`RoomLight`);
the base leaves the glass transparent.

### 3.2 What a script can make, honestly

A generator (new `tools/gen_pet_room_new.js`, in the style of `tools/gen_pet_room_layers.js`) can produce every file
above and the manifest rows, **exactly consistent with the engine** (sizes, `baseY`, depth, the exact-reassembly
property `RoomLayersTest` checks). The pictures:

- **Good, same family:** walls with wainscot, skirting, window frames, two-tone curtains, plank and tile floors in the
  right perspective rows, banded rugs, door frames, desks/benches/tubs/cabinets as boxes with a lit top, a shaded side
  and a few details, plants as 3-green blobs in a pot, a tap, a bird of 6 to 8 px.
- **Plainer than the hand-drawn rooms:** less texture, no hand-placed clutter (the books, picture frames, clock, the
  tree's rope), flatter colour. My honest rating: a clean, consistent, slightly flat "game-jam" version of the existing
  look; side by side with the bedroom the difference shows. **UNVERIFIED by eye until the first render.**
- **Not possible with quality by script:** a lived-in atmosphere, distinctive props (the cat tree), a wicker weave,
  living plants and birds, a garden view through the glass.

### 3.3 What needs the owner or an artist, and placeholders that are good enough to ship

- The final hand-pixelled look of the three backdrops and main pieces is the quality ceiling. The deliverable is
  the same PNG names and sizes, so replacing a file needs **no code change** if the piece's box and `baseY` stay the
  same; if the size changes the manifest row changes and `RoomLayersTest` and the volume generator rerun.
- Two or three bird sprites and the sun-patch shape are small enough to hand-draw; a garden view is its own picture.
- **Ship the generated art as v1**, in this order of value: bases and seam door frames (rooms read as rooms); the
  3 to 4 solid pieces per room that carry places (bench, desk, tub, sink); then plants and clutter. To make them
  feel intended: one accent colour per room (sun: warm yellow, study: deep green, bathroom: soft blue tile), one hero
  object drawn with extra care (bench with cushions, desk with a lamp, tub with a tap), the existing outline and
  highlight rules, and file names identical to the final ones. The update-history entry (`docs/UPDATE_HISTORY.md`
  excludes unfinished work) is written once, when a room really unlocks for the owner (WP-H7).

## 4. Engine work

### 4.1 House graph and room transitions

- A **`HouseMap`** (new, in `N/room/`) built from the generated `house.json`: an ordered list of `RoomDef`
  {id, name, worldX0, art ids, windows, lights, daylight, places}. The world x of a room is **data**, not
  `index * 192`; existing rooms keep -192, 0, 192 so nothing saved moves.
- From it come: `World.roomOf`, `HousePlans.roomOf`, `Spot.room`, `RoomLayout.HOUSE_X0/HOUSE_WIDTH` (now the
  bounds of the unlocked part), the window list, and the walkable range `spanLo/spanHi` that `HousePlans`,
  `HouseLife`, `ToyPhysics` and `ParkourPlanner` read today as `World.HOUSE_MIN_X/MAX_X`
  (`HousePlans.kt:100-118`, `HouseLife.kt:46-47`, `ToyPhysics.kt:45-46`, `ParkourPlanner.kt:300-302`).
- The graph is a **line** (each room joined to its neighbours). A transition is crossing the seam x; a
  "room entered" event is raised once per crossing (for the novelty bonus, the camera and the story text).
  Keep `roomOf` cut points at the real seams for the new rooms and the old -4 / 196 for the three old
  ones (the web's split) so existing plans and goldens do not move.
- **Locked rooms**: the span is the contiguous range of unlocked rooms; because the new rooms sit at the ends,
  any combination of unlocks is contiguous when unlocked **outward from the old house**. Left chain: Study
  before Bathroom; right: Sunroom alone. Therefore three independent unlock states with one ordering rule.

### 4.2 Solids, occluders and surfaces per room

- Today `Solids` and `Occluders` are literal tables copied from the manifest by hand and checked by tests
  (`Solids.kt:50-70`, `Occluders.kt:1-50`, `OccludersTest`). With 11 more pieces, **generate both from
  `layers.json`** (the manifest already carries each piece's box, `baseY`, `occludes`, `surfaces`). This also
  removes the risk of a hand-copied number being wrong. Kept as test: generated tables equal the current
  literals for the three old rooms (a golden test of the generator, so the move is provably safe).
- `SurfaceId` gains the new entries (still an enum for now; about 27 of 32 bits), and the owner-mask in `Solid` and
  `Claim.passes` widens from `Int` to `Long` (a mechanical change; 64 surfaces). `World.table` rows for the
  new surfaces come from `house.json` (y, x0, x1, z).
- Surfaces and props are keyed by `SurfaceId.key` strings in the blob, so the blob's surface whitelist grows
  with the enum (`WorldBlobJson.kt:41`).

### 4.3 Path planning across rooms

- **Floor travel** needs nothing new: one continuous floor, `Walk(x)` across any number of rooms.
  `HousePlans.travel` changes from a hard-coded 3-way roll to "pick an unlocked room by weight (near rooms
  first, novelty bonus), pick a place in it from `house.json`" (`HousePlans.kt:244-257`).
- **Jump planning** keeps the Dijkstra. Two real changes:
  1. **Prune edges by distance**: a hop is only tried between surfaces within a maximum sideways reach (a few
     tens of px beyond the best jump) and never across a room boundary except through the floor. With about 27
     surfaces, an unpruned plan tries about 700 ordered pairs against 225 today; pruning brings it back near
     today's number (**UNVERIFIED**: measure with `ParkourReport`, `DemoParkourTest`). Plans are made per
     decision, not per frame, but offline catch-up makes many (below).
  2. **Cross-room goals**: floor walk to the room, then the existing surface route (`plan` takes start and goal x).
- **Reachability test**: for every valid unlock combination every place is reachable by the strongest cat profile; a
  weak cat's unreachable place is a "not reachable" answer (the planner returns null today), never a loop.

### 4.4 Light and time of day per room

- Windows become data: `{room, centre x, glass rect (x, y, w, h), floorLight (yes/no), rim (yes/no)}`
  (today three fixed 64 by 56 glass rectangles, `RoomLayout.GLASS_*`, sun and moon computed from them in
  `RoomScene.paintSky`). The sunroom needs a wider glass (128 px) with its own clouds and birds; the study a 48 by 44
  pane; the bathroom none (frosted pane in the base, no sky).
- `daylight` factor per room (sunroom 1.0, study 0.8, bathroom 0.3) scales the window light on the floor and the rim
  light. The time-of-day tint is one global multiply today (`RoomScene.kt:3-4`); **UNVERIFIED** whether a per-room tint
  is cheap, the fallback is one global tint plus per-room window light.
- Lights become a list `{x, y, inner, reach, atNight}` replacing the single lamp
  (`RoomArt.kt:103-107`): the study's desk lamp and the bathroom's night light.
- The **sun patch** (section 2.5) uses the same window rectangle and the hour to place a bright parallelogram on
  the sunroom floor, and the place's x. Pure arithmetic.

### 4.5 Depth and volume per room

Same camera, rows and `DepthLevel`. Volume sheets for the new solids come from `tools/pet_room_volume.js` and a
per-piece depth spec (`lifemax_modular_v73/tools/pet_room_volume_spec.js` for the old rooms; **UNVERIFIED** how it
measures, so for generated boxes we supply depth by hand). Standing pieces get a volume; rugs, mats and door
frames do not; a wall piece sets `wall = true`.

### 4.6 Memory and performance budget

All numbers are estimates until measured on a phone (**UNVERIFIED**). Current baseline from the engine plan:
13 solid pieces, 9 steps at MEDIUM, 3.5 MB of step bitmaps, test cap 8 MB.

| Item | Today | Naive, 6 rooms | Plan |
|---|---|---|---|
| Solid pieces | 13 | 24 | 24 (cap 31) |
| Step bitmaps (MEDIUM, 9 steps) | 3.5 MB | about 6.5 MB (scales with pieces: 3.5 x 24/13) | keep under 4 MB by **residency** (below) |
| Bases and piece atlas | about 0.4 MB | about 0.8 MB | fine |
| Planner cost per plan | 225 pair checks | about 700 | back near 225 with the distance prune |

- **Residency**: build and keep camera-step pictures only for the pieces in the camera's room and its two
  neighbours (3 rooms, the same amount as today); other rooms show their rest picture only (their pieces
  are off screen anyway: a window is 192 to 240 px wide). Moving the camera to a new room warms its steps on
  the existing background thread (`HouseStage.warmDepth`) as the camera approaches, and lets go of far ones
  (`trimDepth`). `DepthFrameCostTest` is extended: same cap, six rooms unlocked, camera at each end.
- **Guard** unchanged (`DepthGuard`). **UNVERIFIED**: that `DepthScene.paintScene` skips pieces outside the window; if
  not, 11 more pieces add work every frame (fix in WP-H3).

### 4.7 Offline catch-up across rooms

`CatchUpRun` already plays the same planner in a fast mode and tells where each cat spent most of its time
(`CatchUpRun.kt:1-60`), so more rooms come free in the simulation; what changes:
- The planner never chooses a **locked** room (span limits, 4.1).
- Story lines come from the place's `kind` and room ("spent the afternoon sunbathing in the sunroom"): new phrase rows,
  still at most three lines.
- **Cost**: the replay makes many plans, so more surfaces cost more (**UNVERIFIED**; measure in `CatchUpAndFrameTest`).
- A cat that ends the replay in a room is placed there (a declared cut); the camera cuts to it and residency warms
  that room before the first frame.

### 4.8 Saved data: what the page must save

The page remains the only writer (`50-native-cat-world.js:215-235` pattern). Proposed keys, all bounded:

1. **`state.petHouse`** (new, written by the page only):
   `{ v: 1, rooms: { sun: <unlock time ms>, study: <ms>, bath: <ms> }, seen: ["sun", ...] }`
   - `rooms`: keys only from a fixed vocabulary (`sun`, `study`, `bath`, room for 5 more later); each value
     one integer (unlock time); at most 8 keys; about 100 bytes. Absent key = locked. Rooms are never removed
     (a downgrade of bond does not lock a room again).
   - `seen`: ids of rooms whose "new room" moment the owner has seen; at most 8 strings. Lets the page show
     the reveal exactly once (same pattern as the album's first moments, `WorldContract.FIRST_MOMENTS`).
   - Written with `writeSave()` and rolled back on failure, like `petWorld`; it must be covered by backup and restore
     (**UNVERIFIED** how the backup lists fields; follow what `petWorld` does).
2. **A `house` block in the world block** (page to native, not stored): `{ v: 1, rooms: ["sun","study"], seq }`,
   the sorted unlocked ids, sent in every projection that carries `world`. Native reads it; it never writes or
   invents an unlock.
3. **World blob** (native to page, 8 KiB): no new keys (its top-level and cat keys are exact lists,
   `WorldBlobJson.kt:25-39`); version stays 1. Changes: widen the x bound for `pos.x`, toys and gift from
   -192..384 to -576..576 (`WorldContract.HOUSE_X_MIN/MAX`, used at `WorldBlobJson.kt:128, 227, 236`;
   `HousePlaces.kt:42`; `WorldToys.kt:68`); `spots` gains new place ids (about 12 more keys across two cats,
   an estimated 300 bytes, **UNVERIFIED** against the current typical blob size). A cat's room is derived from
   x, so no room field is saved.
   - **Restore rule**: when a blob puts a cat in a room that is not unlocked (a backup from before an unlock,
     or a restored older save), native moves the cat to the nearest unlocked floor at restore. Native never
     errors.
   - **Downgrade**: an older app reading a blob with a cat at x < -192 fails that cat's entry and derives it
     afresh (the documented behaviour, `WorldBlobJson.kt:13-19`); acceptable.
4. **Page tables**: add the new places to `PET_SPOTS` (`25-pet-likes.js:8`, each with `room`, `sleep`, `rest`,
   flags) and `WorldContract.SPOTS`; generate both from `house.json` (a build check that they match). New
   places are in the page's likes and mind rows but the page's **classic room never draws or visits them**
   (they carry a `native: 1` mark; `petLikesPickSpot` skips them in classic, using `petSpotOK`,
   `25-pet-likes.js:53`).

Tests: node tests for `petHouse` validation (unknown ids ignored, over 8 keys refused), a strict decoder test for the
widened bounds, a golden for the restore rule.

---

## 5. Progression: how rooms unlock (OWNER DECISION)

Options:

| Option | Rule | For | Against |
|---|---|---|---|
| A. All open | six rooms from the first run of the update | simplest; the most exploring at once; no new state | nothing to look forward to; a Stray (cat that does not yet trust) roams a house it should hide in; no reason to come back |
| **B. By bond level (recommended)** | a room opens when the cat's bond level reaches a threshold (0 Stray .. 5 Devoted, `P/15-runtime.js:57`) | already computed by the page, already the pattern for toys (`unlock:{L:n}`) and for depth (`petDepthMaxZ`); free; ties exploring to the daily care loop; existing owners with bonded cats get rooms at once | two cats: needs a rule (use the higher bond, recommended) |
| C. By cat stage | kitten stages 0 to 5, grown at -1 | matches a cat's "growing up" | stages pass in a few days, then everything is open forever; no effect on a grown cat |
| D. By purchase | spend the game's coins to "build" a room (the economy exists, `docs/specs/ECONOMY_SPEC.md`) | a goal, a sink | needs prices, a command from native to page, a shop screen, balance; the most work |
| E. By player level | the owner's own quest level | ties the cat's house to real-life progress (the app's theme) | couples a feature to a different system; slow for a "make cats explore more" wish |

**Recommendation: B, with the numbers Sunroom at Curious (2), Study at Friendly (3), Bathroom at Fond (4)**,
two cats using the higher bond, plus a **kitten rule**: a cat in stage 0 to 2 stays in the three original
rooms regardless (cheap: the span the planner reads is per cat, which is how `zMax` already works for depth).
Existing cats at Devoted therefore get all three on the first launch with a short reveal (OQ-H8). D can be
added later as an extra "decor" sink without changing B. **OWNER DECISION: A, B, C, D, E, or a mix, and the thresholds.**

---

## 6. Work packages (in dependency order)

Sizes: S = under 400 lines changed or under a day of agent work; M = 400 to 1500; L = over 1500 or spans page
and native. Each package keeps `RoomFidelityTest`, `RoomAllocationTest` and `CatHouseScreenshotTest` green
(`CAT_TAB_PLAN.md` line 211), and the three-room behaviour **byte-identical** until a room is unlocked.

| WP | Size | What | Tests and checks | Depends on |
|---|---|---|---|---|
| **H1** | M | `HouseMap`/`RoomDef` table from data; remove the hard-coded 3 (list in 1.3 item 3); `houseX` honoured from the manifest; `HOUSE_X0` and `HOUSE_WIDTH` become data; still exactly the three old rooms | `RoomFidelityTest` unchanged pixel for pixel; `RoomLayersTest`; `GoldenTraceTest`, `BrainGoldenTest`, `SurfaceWalkTest` unchanged | none |
| **H2** | M | Solids, Occluders, Spots, SurfaceId bit masks (Int to Long) generated from `layers.json` plus `house.json`; page and native place tables generated from one file | generator output equals today's literals; `OccludersTest` (measures real pixels); `ParkourPlannerTest`; a node check that `PET_SPOTS` equals the generated list | H1 |
| **H3** | M | `MAX_PIECES` to a 64-bit mask; step residency (3 rooms around the camera); confirm out-of-view pieces are not drawn | `DepthFrameCostTest` (cap with 6 rooms at both ends); `DepthSceneTest`; a screenshot at each end of the house | H1 |
| **H4** | L | Generator `tools/gen_pet_room_new.js`: three rooms' bases, pieces, volume sheets, door frames, manifest rows, `house.json` entries; first look rendered for the owner | exact reassembly (compose equals backdrop), size rules (192x144, piece boxes), palette check (each colour in the shared family), `RoomLayersTest` on all six | H1 (format) |
| **H5** | L | House graph and spans: unlocked-room span for planner, `HouseLife`, toys, camera extents; `travel` over rooms by weight; ParkourPlanner distance prune; locked-door drawing; restore rule | cross-room pathfinding tests; reachability for every valid unlock set; `SteadyCatTest`/`RoomStillnessTest`/`TurnSteadyTest` extended across a seam (no jitter, no drift); new golden traces for the six-room house with the old three unchanged | H2, H3 |
| **H6** | M | Per-room light and windows (data), sun patch, birds, dripping tap, lights list; the new places' weights and flags | `RoomFidelityTest` for the old rooms; new light goldens by hour; screenshots at 8, 13, 22 h per room | H4, H5 |
| **H7** | L | Page and contract: `state.petHouse`, unlock rules (the chosen option), the `house` block, widened blob bounds, `PET_SPOTS` additions, classic-room skip, Settings "update history" bullets, node and Kotlin tests, backup coverage | node tests for validation and rollback; `native-handover-sim` fakes extended with a mutation shown failing; strict decoder tests; restore golden; blob size test | H5 |
| **H8** | M | Camera: house bounds from the map, glide for a deliberate switch, cut for catch-up; house map strip in the Cat tab and the Overview card (decision on the Overview, OQ-H7) | `RoomCameraTest` (seam, glide, bounds with locks); screenshots of the strip (all locked, some, all; two cats); text size and dark mode | H5, and the camera fix landing |
| **H9** | S | Catch-up story lines by room and kind; planner never uses a locked room in replay | `CatchUpAndFrameTest`: same reopen tells the same story; replay time budget; story golden | H5, H7 |
| **H10** | S then external | Phone check and polish; replace placeholders with the artist's files as they arrive | the owner's list (below); `RoomLayersTest` on each replacement | all |
| **H11** (stretch) | L | Cat flap and short trips outside (state `away`, return with a gift) | own plan | after H10 |

If time is short: H1 to H4, then H5 to H7 for the Sunroom only; the other two rooms are then data additions.

### What the owner checks on the phone (after H5 and again after H7)

1. Does a cat cross into a new room within about a minute of watching? Does the walk across a seam look
   smooth and the camera follow without a jerk?
2. Does each new room read as a room next to the old ones, or does the placeholder art look pasted in?
3. Does the sun patch slide across the sunroom floor over an afternoon and does a cat sit in it?
4. Is the house map strip understandable at a glance, and does a tap on a room move the camera there?
5. Switch the selected cat: does the camera glide to the other cat across rooms?
6. After an hour away, does "while you were away" mention a new room?
7. Locked room: is the closed door clear, and does the reveal happen once?
8. Frame feel at the far ends, and warmth after ten minutes on the Cat tab, compared with before.

---

## 7. What this plan assumes of the other two agents

One agent is **retuning the exploration planner**, another is **fixing the camera** (follow the selected cat, constant
depth skew). I have not seen either's work (**UNVERIFIED**). The plan builds on both and needs this from them; if an
assumption is false, the work package named changes.

Planner retune (`HousePlans.kt`, `Selector`, `BrainTuning`):
- Room choice for a trip is a **weighted pick from a table**, with weights in `BrainTuning` and the room list from one
  table, not three constants. **Request: add no new `LIVING/BEDROOM/KITCHEN` branches.** If they did, H5 absorbs it.
- A **novelty term** (weight up for a room or place not visited lately) exists or can be added. If not, H6 adds a
  small decaying per-cat counter in memory (not saved, no blob change).
- Exploration stays a **fraction of decisions**; I would keep `NEAR_ROOM_SHARE` 0.7 so a house of six does not make a
  cat roam constantly.

Camera fix (`RoomCamera.kt`, `HouseStage.kt`, `DepthSway`):
- The camera follows **one selected cat** and the Cat tab can say which. H8 then only changes the bounds and adds the
  glide. If the two-cat `CameraDirector` stays, H8 keeps its rules and adds the glide on a manual switch.
- The **depth skew is constant along the strip**: today `DepthScene.kt:85, 95, 290, 447-463` read each piece's x from
  the house x. If skew still depends on distance from the camera, far rooms of a 1152 px house sway differently and H3
  restricts parallax to the camera's room and its neighbours (the residency window).
- **Whole-number scales stay** (`fullScale`), so the overview is the map strip.

## 8. Risks and open questions

Risks, worst first:

1. **Art quality gap** between hand-drawn old rooms and generated new ones (3.2). Mitigation: the placeholder rules,
   one accent colour and one hero object per room, an artist pass later by file name. The owner judges the first
   render before H5 spends effort.
2. **The "3" is everywhere** (1.3 item 3): a missed copy shows as a cat walking through a wall or a blank room.
   H1 runs with only the old rooms and must leave every golden unchanged, which proves the de-hard-coding without new
   art; a grep test fails the build if a literal `192`, `576` or `LIVING_END` returns outside the map.
3. **Depth memory and the 31-piece mask**: 24 of 31 is workable; a naive six-room build is about 6.5 MB against an 8 MB
   test cap (**UNVERIFIED**). Mitigation: residency, 64-bit mask, 5 solids per room.
4. **Planner cost** (about 3 times the pair checks per plan; offline catch-up makes many). Mitigation: distance prune,
   measure with `ParkourReport` and the catch-up test.
5. **Saved data**: an older backup can put a cat in a locked room (the restore rule moves it); an old app reading a new
   blob loses that one cat's world (documented), so ship page and native in one release.
6. **Two lists of places** (`PET_SPOTS` and `Spots`) are hand-written today and can drift; generating both from
   `house.json` plus a check removes that.
7. **Long walks and an empty feel**: ends of the strip are far apart. Mitigation: map strip, neighbour-biased trips,
   glide on a manual switch.
8. **Phone-only unknowns**: frame cost at the far ends, glide feel, seam smoothness, per-room tint cost.

Open questions, each with a recommendation:

- **OQ-H1 (OWNER DECISION) Progression**, options A to E (section 5). Recommend **B** (bond 2, 3, 4) plus the kitten rule.
- **OQ-H2 Floor plan**: recommend the **strip** (2.2).
- **OQ-H3 (OWNER DECISION) Which rooms**: recommend **Sunroom, Study, Bathroom**; nursery or cellar as swaps; no hallway.
- **OQ-H4 (OWNER DECISION) Placeholder art**: ship generated art room by room (Sunroom first), review the first render
  before wiring the rest, replace later by file name.
- **OQ-H5 Cat flap and outdoor trips**: not now (WP-H11): needs an "outside" state in the blob, the gift rules and art.
- **OQ-H6 Page classic room**: keep three rooms, new places hidden there. Yes.
- **OQ-H7 Overview card**: it keeps following the selected cat with no map strip (too small); the strip is in the Cat tab
  and the full-screen room only.
- **OQ-H8 Reveal moment**: on unlock, a short toast and the camera gliding to the new door once the next time the Cat
  tab opens (the `seen` list). Yes.
- **OQ-H9 Kittens** (stage 0 to 2) stay in the original three rooms. Yes.
- **OQ-H10 Two cats**: both are simulated on or off screen (the world steps all bodies, `HouseStage.kt:279` comment;
  **UNVERIFIED** that off-screen bodies cost nothing extra); unlock uses the higher bond. Yes.
- **OQ-H11 Sun patch and birds**: yes, cheap, the visible reward for the Sunroom.
- **OQ-H12 Seams**: old seams stay open plan; revisit after the phone check.
