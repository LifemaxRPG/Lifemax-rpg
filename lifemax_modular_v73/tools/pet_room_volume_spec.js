/* The depth profiles of the room furniture: how far back each painted pixel of a cut-out piece is, so the native room can render
   every piece as a small solid (see pet_room_volume.js, which turns this into the depth maps, and feature/pet/room/Volume*.kt,
   which renders them). Measured from the art, in room pixels like pet_room_cut_spec.js (each backdrop is 192 x 144).

   Depth is measured in FLOOR ROWS: one unit is the distance between two rows of floor at that depth, the same unit the cats' depth
   uses (a cat whose feet are one row higher stands one unit farther back). A piece's front plane stands on its base row (the row the
   cut spec gives it, PET_Z_BLOCK) minus `front`; every pixel of the piece is a column of solid from `f` (its front) to `b` (its back)
   units behind that plane. With the camera at rest only the fronts are seen, straight on, so the piece is its painted sprite exactly;
   as the camera sways the fronts at different depths slide apart and the sides and tops between them show.

   A piece:
     depth    the default back of every pixel (and the deepest the piece is)
     front    where its front plane is, in units behind its base row (negative: in front of it); default 0
     wall     true when its back stands against the wall: its back then moves with the wall when the camera sways, and the crease
              where its sides meet the wall is shaded (sofa, counter, fridge, bookcase)
     shapes   applied in order, a later one wins. Each has a rect (x0, y0, x1, y1 inclusive, room pixels) and, inside it, optionally
              only/not a list of colours (like the cut spec), and one of:
                f: n | [top, bottom]   the front, constant or running linearly from the rect's top row to its bottom row (a painted
                                       top face: its rows lie farther back the higher they are)
                b: n                   the back (may be given with f)
                round: { centre, depth }
                                       a body that is round seen from above: in each row the matched pixels' span is its width, its
                                       middle is nearest (centre - depth) and its edges are at the centre; the back mirrors it.
                                       A trunk, a pot, a lamp shade, a leg.
                bulge: { f, edge }     a soft cushion: f in the middle, f + edge at the rim (by the distance to the matched area's
                                       edge), back unchanged
     surfaces where a cat that stands on the piece is in depth, in units behind the front plane, by surface key
              (feature/pet/body World.kt); the room draws such a cat at that depth, and moves it with that depth when the camera sways.
     covered  by surface key, the piece that is wholly in front of a cat standing there (the platform over the middle one: a cat
              sitting up is taller than the gap, and its head goes behind the platform, never through its top).
   The generator also pushes back any pixel that a later piece covers when the camera is at rest, so the room drawn by depth is the
   painted room exactly at rest (see pet_room_volume.js). */

const hex = (s) => s.trim().split(/\s+/);

const SOFA_BLUES = hex('486298 6280ba 324470');
const BOOK_WOOD = hex('432a18 684225 a87446 8a5a34 53341d');
const LEAF_LIGHT = hex('82c882 77b877 72b072 8fd28a');
const LEAF_DARK = hex('347a50 2f7049 31754c 2f7a4a');
const POT = hex('c4683a 9c532e d7723f ce6d3c c8683a 8f4325 d89575');
const SISAL = hex('f0cf94 d8b070 a98040');
const DOOR_PANEL = hex('80a68c');
const DOOR_GAP = hex('62846e');
const BRASS = hex('d8b070 deae76');

const VOLUMES = {
  /* the living room */
  sofa: {
    depth: 11, wall: true,
    shapes: [
      { rect: [40, 86, 151, 105], f: 7 },                                     /* the backrest, a slab against the wall */
      { rect: [40, 86, 151, 88], f: [10, 8] },                                /* its top, seen from above */
      { rect: [30, 96, 44, 123], f: 0 }, { rect: [147, 96, 161, 123], f: 0 }, /* the arms, from the front to the wall */
      { rect: [30, 96, 44, 98], f: [9, 3] }, { rect: [147, 96, 161, 98], f: [9, 3] },
      { rect: [45, 106, 146, 123], f: 0.5 },                                  /* the seat cushions */
      { rect: [45, 106, 146, 108], f: [6.5, 2.5] },                           /* their tops */
      { rect: [45, 107, 146, 119], only: ['324470'], f: 1 },                /* the seams between the cushions */
      { rect: [45, 120, 146, 123], f: 1.5 },                                  /* the shadowed base under them */
      { rect: [34, 124, 37, 127], round: { centre: 1.5, depth: 1 } },         /* the legs */
      { rect: [92, 124, 95, 127], round: { centre: 1.5, depth: 1 } },
      { rect: [154, 124, 157, 127], round: { centre: 1.5, depth: 1 } },
    ],
    surfaces: { couch: 4 },
  },
  sofa_cushion_yellow: {
    depth: 3, front: 6,                                                        /* leaning on the backrest */
    shapes: [{ rect: [52, 98, 65, 107], bulge: { f: 0, edge: 1 } }],
  },
  sofa_cushion_pink: {
    depth: 3, front: -1,                                                       /* standing up against the arm, at the seat's front */
    shapes: [{ rect: [124, 100, 139, 123], bulge: { f: 0, edge: 1.5 } }],
  },
  floor_lamp: {
    depth: 6,
    shapes: [
      { rect: [11, 26, 29, 41], round: { centre: 3, depth: 2.5 } },           /* the shade, a cone */
      { rect: [19, 42, 24, 125], round: { centre: 3, depth: 0.75 } },         /* the stem */
      { rect: [12, 126, 27, 128], round: { centre: 3, depth: 2 } },           /* the foot, a disc */
    ],
  },
  side_table_plant: {
    depth: 6,
    shapes: [
      { rect: [164, 66, 186, 93], f: 2, b: 4 },                               /* the leaves: thin, the light ones nearer */
      { rect: [164, 66, 186, 93], only: LEAF_LIGHT, f: 1.5, b: 4 },
      { rect: [164, 66, 186, 93], only: LEAF_DARK, f: 3, b: 4 },
      { rect: [164, 90, 186, 103], only: POT, round: { centre: 3, depth: 2.5 } },
      { rect: [164, 104, 186, 107], f: 0.5 },                                 /* the table top, its top face seen from above */
      { rect: [164, 104, 186, 105], f: [4, 2] },
      { rect: [164, 108, 186, 128], round: { centre: 1.5, depth: 0.75 } },    /* the front legs */
    ],
    surfaces: { sidetable: 2 },                                                /* parkour: on the table top, beside the pot */
  },
  /* the bedroom */
  bookcase_plant: {
    depth: 7, wall: true,
    shapes: [
      { rect: [106, 32, 137, 111], f: 0 },                                    /* the cabinet */
      { rect: [106, 32, 137, 33], f: [4, 1] },                                /* its top board, seen from above */
      { rect: [108, 34, 136, 108], only: ['432a18'], f: 5.5 },                /* the back panel: the shelves are recessed */
      { rect: [108, 34, 136, 108], not: BOOK_WOOD, f: 1.5, b: 5.5 },          /* the books stand at the front of the shelves */
      { rect: [108, 34, 136, 109], only: ['8a5a34'], f: 2.5 },                /* the shelf boards' tops */
      { rect: [109, 20, 133, 31], only: POT, round: { centre: 3.5, depth: 2.5 } }, /* the plant on top */
      { rect: [109, 0, 133, 23], not: POT, f: 3, b: 4.5 },
      { rect: [109, 0, 133, 23], only: hex('8fd28a 4fa36a'), f: 2.5, b: 4.5 },
    ],
    surfaces: { bookcase: 2 },                                                 /* parkour: on the top board */
  },
  cat_tree_post: {
    depth: 10,
    shapes: [
      { rect: [155, 76, 164, 128], only: SISAL, round: { centre: 6, depth: 3 } }, /* the trunk through the platforms */
      { rect: [174, 106, 179, 128], only: SISAL, round: { centre: 8, depth: 1.5 } }, /* the back post under the cubby */
      { rect: [146, 76, 153, 91], f: 3, b: 3.5 },                             /* the dangling toy: the string */
      { rect: [146, 84, 153, 91], round: { centre: 3.5, depth: 1.25 } },      /* and the ball, behind a cat at the platform's edge */
    ],
  },
  cat_tree_cubby: {
    depth: 10,
    shapes: [
      { rect: [164, 80, 183, 99], f: 2 },                                     /* a box on the trunk */
      { rect: [164, 80, 183, 99], only: ['1e162d'], f: 9.5 },                 /* with a hollow you can see into */
    ],
  },
  cat_tree_slab_base: {
    depth: 12,
    shapes: [{ rect: [136, 128, 181, 136], f: 0 }, { rect: [136, 128, 181, 132], f: [8, 1] }],
  },
  cat_tree_slab_mid: {
    depth: 12,
    shapes: [{ rect: [122, 100, 181, 108], f: 0 }, { rect: [122, 100, 181, 104], f: [9, 1] }],
    surfaces: { mid: 1 },                                                      /* near its front edge: in front of the trunk and the toy */
    covered: { mid: 'cat_tree_slab_top' },                                     /* a cat sitting up here has the top platform over its head */
  },
  cat_tree_slab_top: {
    depth: 12,
    shapes: [{ rect: [122, 64, 187, 75], f: 0 }, { rect: [122, 64, 187, 71], f: [10, 1] }],
    surfaces: { top: 3 },
  },
  /* the kitchen */
  counter: {
    depth: 11, wall: true,
    shapes: [
      { rect: [58, 76, 186, 128], f: 1 },
      { rect: [58, 90, 186, 97], f: 0 },                                      /* the worktop overhangs the cupboards */
      { rect: [58, 90, 186, 92], f: [10, 2.5] },                              /* and is seen from above */
      { rect: [60, 98, 183, 122], only: DOOR_PANEL, f: 1.25 },                 /* the door panels sit in their frames */
      { rect: [60, 98, 183, 122], only: DOOR_GAP, f: 1.25 },
      { rect: [60, 98, 183, 122], only: BRASS, f: 0.5 },                      /* the knobs stand out */
      { rect: [60, 123, 183, 128], f: 2 },                                    /* the kick plinth, set back */
      { rect: [68, 76, 84, 89], round: { centre: 5, depth: 2 } },             /* the kettle, at the back of the worktop */
      { rect: [138, 76, 151, 89], f: 8, b: 9 },                               /* the tap, against the splashback */
      { rect: [162, 80, 179, 89], round: { centre: 5, depth: 2 } },           /* the fruit bowl */
    ],
    surfaces: { counter: 5 },
  },
  fridge: {
    depth: 11, wall: true,
    shapes: [
      { rect: [14, 30, 53, 127], f: 1 },
      { rect: [14, 30, 53, 31], f: [9, 4] },                                  /* its top */
      { rect: [14, 32, 53, 125], only: ['aab2b4'], f: 0 },                    /* the handles stand out */
      { rect: [14, 32, 53, 125], only: hex('fafaf0 e9c46a 8cc85a 50a05a dc3c3c 3c78dc'), f: 0.5 }, /* the note and the magnets */
      { rect: [14, 66, 53, 67], f: 1.25 },                                     /* the seam between the doors */
      { rect: [14, 126, 53, 127], f: 1.5 },                                   /* the kick at the bottom */
    ],
    surfaces: { fridge: 6 },                                                   /* parkour: on its top */
  },
};

module.exports = { VOLUMES };
