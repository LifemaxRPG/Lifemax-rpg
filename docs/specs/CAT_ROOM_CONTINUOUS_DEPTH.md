# Cat room: continuous depth (faces baked once, drawn at the live angle)

Status: built (replaces the stepped, cross-faded furniture). Owner's go-ahead: "change the pet section over
to that method" (the Habits street's: faces baked once, only the transforms change per frame).
Background: `CAT_3D_ENGINE_PLAN.md` (the spike and the sprite-stacking prototype this finishes).

## 1. Before

Each solid piece (13: sofa, cushions, lamp, side table, bookcase, cat tree x5, counter, fridge) is a
column model (`VolumeModel`: a depth range behind every painted pixel). The room ray cast every piece
for each of `2 * steps + 1` camera steps (RICH: 11), cut the pictures into depth bands, packed them into
one sheet per step (`VolumeSheets`), and drew the nearest step, cross-fading two neighbouring steps while
the camera moved (`StepBlend`, `StepMix`). The geometry was quantised to art pixels per step, so a side
still came in an art pixel (5 to 9 device pixels) at a time, faded or not. The floor, rugs, props and cats
moved in whole art pixels too (`Parallax.shift`).

## 2. Why not one affine quad per face

The Habits buildings are boxes: three quads each. Our pieces are column models. Decomposed into maximal
faces (runs of equal depth along the silhouette and the rows) and split per band of depth for the cats'
order, the house is about 2,500 quads a frame (measured: sofa 190, bookcase 750, lamp 320, counter 320).
That is the per-quad cost the plan rejected (option A2). So the faces are baked as **depth strips** and
drawn with a per-strip transform (the "per-strip" variant of the same method): every strip is a plain
bitmap moved by its depth times the live view; nothing is stretched, so the art's pixels stay whole.

## 3. What is baked (once per piece, per art: `SolidFaces`, `PieceVolume.faces`)

- Strips of depth, 2 per floor row (`FaceTuning.SLICES_PER_ROW`). Strip `j` holds what the piece shows at
  depth `j / 2`: the painted fronts of the columns that start there (untouched: at rest the piece is its
  sprite exactly) and the rim of the cross-section, which is where the side and top faces pass through
  that depth, shaded once by the room's rules (`VolumeTuning`: top +1 ramp step, left side -1, right -2,
  crevice -1 more, slopes keep the painted colour). Slopes and round pieces (the tree trunk, the slab
  tops, the lamp) are therefore approximated by strips half a row apart, continuous in the view.
- Rim only: a strip pixel that the next nearer strip always covers (its column and its neighbours left,
  right and above reach that strip too; strips are at most 0.18 px apart across, 0.11 down) is dropped.
  The result is pixel-identical to drawing whole cross-sections (tested), at a fraction of the pixels.
- The rim's sides and tops are the pieces' authored surfaces (section 8), not the front's edge
  colours extruded.
- Two shadings (left sides, right sides) and, for pieces painted with the art's outline, their inked
  copies (the far edge's outline, which comes in continuously once a face is wider than a pixel).
- Each strip's rim is cut into runs (12 rows, split at gaps of 12 clear columns), each cropped; all
  packed into one bitmap per piece. Baked on the background thread before the room shows
  (`DepthScene.warm`/`prepare`), shared by every room of the same art at every depth level.

## 4. What is drawn per frame

- Each piece's yaw, continuous: the idle/tilt sway plus where it stands against the middle of the screen
  (`PanFollow`), as before but never snapped to a step. `vx = -yaw * depthGain`, `vy = -pitch * yaw^2`.
- Strip `j` of a piece is drawn at `front + (j / 2) * (vx, vy)`. A side face of a piece `D` rows deep is
  therefore exactly `D * |vx|` wide: continuous and monotonic in the camera, 0 at rest. On the phone each
  strip is placed at its nearest device pixel, so a side grows a device pixel at a time.
- Parallax is continuous too: the canvas takes a sub-pixel `nudge` for the layer being drawn (wall,
  each floor row, rugs, seam, window light, props, each piece's front plane, each cat and its shadow,
  toys), rounded to device pixels on the phone and to house pixels in the JVM canvas (where it is the old
  whole-pixel picture exactly). A cat and the floor under it take the same value, so feet do not slide.
- Draws: 1,126 strip runs for the whole house (only about a third is on screen; the phone's canvas
  skips the rest), all `drawImage` with integer device offsets, no allocation (tested over 20,000
  frames of a panning camera).
- Removed: `VolumeSheets`, `VolumeStep`, `StepMix`, `StepBlend`, `SheetTuning`, `SolidStack` and the
  developer switch "3D furniture" (`Solid3d`), `RoomCanvas.sheet`; the ray cast (`VolumeRaster`) moved
  to the tests as the reference the faces are checked against.

## 5. Order, cats, shadows, light, touch

- `SolidBands`: a piece's strips `2k`, `2k+1` form band `k`, sorting at `sortFront - (k + 0.5)`, exactly
  the rows the steps' bands sorted at; the order is made once (it no longer changes with the camera).
  `DepthOrder` is unchanged: cats go between the bands, a cat on a seat after every band of it, the top
  platform between two cats in the tree. Drawing strips back to front within a piece is a correct
  painter's order for any view (a strip only covers deeper ones), so the order holds however far the
  faces grow (tested at both extremes).
- Contact shadows: as before, drawn with the piece's first band, moved by its front's parallax.
- Light: the window light, seam and time-of-day tint are unchanged (they move with the continuous
  nudge). Faces are shaded from the pieces' own palettes as before.
- Touch and hit-testing: unchanged (they never used the furniture's pictures).

## 6. Memory and speed

Measured on the JVM (whole house, RICH; old numbers from the previous commit's code):

| | before (steps) | after (faces) |
|---|---|---|
| pixel arrays kept | 4.44 MB (11 steps) | 3.25 MB (13 pieces, all shadings) |
| canvas bitmaps | up to 11 step bitmaps (~4.4 MB), remade after every trim | 13 piece bitmaps (3.25 MB) |
| making them | 63 ms for the 11 steps, again after every trim | 12 to 19 ms, once per art (shared by every level) |
| draw calls a frame (whole house, counting canvas) | 1,228 | 2,194 (1,126 strip runs); the phone's canvas skips strips off the screen (about two thirds) |
| painter work (JVM, ordering and calls) | 0.03 to 0.06 ms | 0.03 to 0.05 ms |
| strip pixels | rest of the step bands | 54,507 rim pixels kept of 308,086 cross-section pixels (17.7%); a frame blits about 180,000 art px of runs |

The run size (12 rows, gaps of 12) trades memory against draw calls: 16 rows was 904 runs and 4.24 MB,
8 rows 1,873 runs and 2.51 MB.

## 7. Tiers and risks

- `DepthGuard` / `DepthLevel` are unchanged: RICH, MEDIUM, SUBTLE, then the classic flat room for a phone
  that cannot keep up. The faces do not depend on the level, so a change of level costs nothing.
- Risk: fill rate. Strips overlap (each covers the next deeper one almost entirely); rim-only keeps the
  overdraw near the sprites' own area. Only the phone can say the GPU cost; the guard steps down if it is
  too much.
- Risk: the far-edge outline is per strip (inked copy within the outline's width of its back), as in the
  prototype: a piece whose columns end at very different depths outlines with the deepest back of each
  strip.
- The screenshots change: sides are now finer than an art pixel on the phone, and the floor and cats sit
  at device-pixel offsets.

## 8. Authored faces

Owner, after build 558: "the 3D effect is way better" but "it looks a bit weird... you'll have to make
textures for each side of each furniture". Until then a side or top was the colour of the front's edge
pixel extruded backwards and re-shaded: a bookcase showed its edge books' colours along its side, the
counter's worktop its top surface's light rows running back as stripes, a platform of the cat tree a
staircase of its top rows.

Now every face pixel is asked of the piece's skin (`FaceSkin`, catalogue `FaceSkins` by piece name):
given where it is (`FaceSpot`: row and column on the sprite, which face, its strip, how many strips
behind the column's front and before its back, its fraction along the column, the rows of the piece
above and below it at that depth, whether it is the outside of the piece), the skin answers a colour of
the piece's own palette and a number of ramp steps (`FaceTex.of`), a colour chosen outright for that
side (`FaceTex.exact`), the outline (`FaceTex.INK`) or `KEEP` (the old extrusion). `SolidFaces` adds
the room's light on top (top +1, left -1, right -2, crevice -1) along the same ramp, light first, so a
texture still shows on a side that is already as dark as its ramp goes; slopes (the painted top rows,
round posts) are unlit as before. The front at rest is untouched (only face pixels are asked), the
rims and the bitmaps are the same size as before, and the skins run only while baking.

Rules every texture keeps:
- A side is its whole depth drawn `D * |vx|` pixels wide, about one device pixel per strip at the
  widest, fewer in between, so a feature one strip wide would come and go as the camera turns. A
  texture therefore changes along the depth only in bands (`FaceSpot.band`: a quarter, a fifth of
  the column) or at the front edge (the first strip, which once shown stays shown); its detail runs
  along the rows (stripes, hems, grain), which hold at every angle.
- The front edge of a face is the board's edge or the piping: lighter.
- Outlines carry round: on a piece outlined in ink, a side's top edge is inked where the front's top
  is; an outline row of the front stays an outline only as far back as what it outlines (the fridge's
  door split goes through the door, not the body).
- A face inside the piece (between seat cushions, inside a shelf, the walls of the cubby's opening) is
  in its own shadow.
- Where a ramp would step into another material (the browns share a hue with the terracotta and the
  red books, the lampshade's cream with the pole's brown), the colours of each side are given
  outright (`FaceSkins.Tones`: panel, grain and edge of the left and right side, a top and its grain).

What each piece shows:

| piece | sides | top |
|---|---|---|
| sofa | blue fabric arm ends: piping down the front edge, the rolled arm and back catching the light, the hem in shadow, the outline along the top; gaps between the seat cushions dark | fabric, piping at the front, darker at the back |
| cushions | their stripes carry round, rounded and darker as they turn | fabric |
| floor lamp | the cloth shade in its own three colours, darker as it turns; round pole and base | the open top: the rim, and the glowing inside |
| side table | leaves; round terracotta pot, bands carrying round; wooden table with grain along its edge and up the legs | soil in the pot within its rim; the table top's grain front to back, its lip lit |
| bookcase, study shelf | wooden side boards (grain up the board, lighter edge banding, the top edge) that hide the books; inside the shelves in shadow, books' covers dark | the top board and shelves with their grain; the tops of the books are their pages between the covers; soil in the pot |
| cat tree post | round: the sisal wrap carries on round the back with its slant, darker as it turns; the toy and string round | plain |
| cubby | carpeted walls with the light trim at the front edge; the opening's walls dark (only where the front has an opening) | carpet |
| platforms (3) | the board's edge as thick as the front shows it, rising towards the back as the top recedes, carpet; below it the dark underside | carpet |
| counter | worktop edge as thick as the front's (wood tones, lit top edge, dark underside); cabinet side panel: the doors' edge, the gap behind them, the shadow under the worktop; kettle, tap, fruit round | the worktop's painted top, its grain at the back |
| fridge | white: the doors a row thick with a lit edge, the grey seal behind them, the body darkening towards the back, the door split only through the doors, the kick plate dark, the outline along the top | white, lit lip, shadowed at the wall |
| sun bench, desk, desk chair | wood in their own tones with grain; cushions and seats as upholstery | wood grain, cushions as fabric |
| wicker chair, baskets | an over-and-under weave two rows tall and a quarter of the depth long; cushions and contents as fabric | |
| big plant | leaves darker towards the back, round pot | soil |
| bath, sink cabinet | enamel and painted wood: lit front edge, a shade darker towards the back | lit lip, darker at the back |

Cost: the rims are the same pixels as before, so the bitmaps are unchanged (3.25 MB for the 13
pieces of the first three rooms, 1,126 strip runs); baking takes 13 to 24 ms on the JVM (the skins
run once per art, on the background thread); nothing changes per frame.

Tests: `SolidFacesTest` (the sprite at rest exactly, the palette, the rims against whole
cross-sections, the ray cast's shape, the order at both extremes, the cost) still holds; new ones check
the bookcase's side boards are wood and its books' tops pages, the fridge's door split stops at the
door, and that no texture changes strip by strip along a side (beyond where the shape itself does).
