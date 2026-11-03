/* What to cut out of the three painted room backdrops (see pet_room_cut.js, which does the cutting).
   Everything here is measured from the art, in room pixels (each backdrop is 192 x 144; x 0 is the room's left edge):
   - a piece is the union of its entries; an entry is a rectangle (x0, y0, x1, y1 inclusive) and, inside it, either
       only: [colours]   the pixels of exactly these colours (rrggbb), or
       not:  [colours]   every opaque pixel except these (the wall and floor colours of the room), or
       neither           every opaque pixel (a solid block, e.g. a cushion).
   - pieces are listed back to front; a pixel claimed by two pieces goes to the later one.
   - box: where the piece really is, as a solid rectangle. A later piece standing in front of it (a cushion on the sofa, the
     cat tree over the bookcase) leaves a hole in this piece; the hole inside the box is filled so the piece is whole.
   - base: the row of the piece's lowest pixel as the page keeps it (PET_Z_BLOCK in src/runtime/segments/24-pet-depth.js,
     the same number the cat's "stay in front" rule uses); null for a flat floor piece (a rug), which no cat is ever behind.
   - occludes: whether a cat can walk behind it (it is tall enough to hide one).
   - surfaces: the cat's standable surfaces (SurfaceId keys of feature/pet/body/World.kt) that lie on this piece.
   - how deep each piece is (the native room renders every standing piece as a small solid) is pet_room_volume_spec.js.
   fills: how the wall and floor behind a removed piece are rebuilt (see rebuildBase in pet_room_cut.js). */

const hex = (s) => s.trim().split(/\s+/);

const LIVING_BG = hex(`d6ba84 ccae78 ecdec4 f1e6d4 946238 b9814f c58e5a ac7646 e2ca96 d6c4a4 d8cfbe f8f2e9 cdbfa8 f5e6cb a27348 99693f
  7c502d b37e4e 8e6038 94663f cdb27e c4ab79 bca374 cfc3ac d9ccb4 e2d5bc d8c190 bcac90 d7cab2 603e24 806860 544042 a0867c`);

const PLANT_GREENS = hex('4fa36a 347a50 82c882 4b9c65 489561 77b877 2f7049 458f5d 31754c 72b072');
const PLANT_POT = hex('c4683a 9c532e d7723f ce6d3c');
const TABLE_WOOD = hex('684225 633f23 5b3a20 5f3c22 8e5e35 885a33');

const KITCHEN_BG = hex(`ecd896 e4ce8a dee8d6 e8dec6 92aa96 f1e6d4 c4d2c0 718475 c7beaa 7d9281 d8cfbe e6f1de f8f2e9 cdbfa8 786250
  cfbe84 d9c68a e2cf90 c3ccbc ccd5c4 d5decd 604e4a 3c2c30 806860 544042 a0867c 8a5a34 603e24`);
const TILE_WALL = hex('f0f2ec c6d0cc');

const BOOKS = hex(`4f8ad9 e98a3a 59b36b ffa545 a36028 ba6e2e 5ea5ff 376097 6ad680 3e7d4a 3f6ead 478f55 d9534f ff635e 973a37 ad423f
  ba9c54 ffeb7f a3894a e8e0d0 fffff9 a29c91 b9b3a6 e9c46a`);
const SISAL = hex('f0cf94 d8b070 a98040');
const PURPLE = hex('b49ae0 8a6cc0 664a96');

const ROOMS = {
  living: {
    source: 'living',
    pieces: [
      { name: 'rug_living', base: null, occludes: false, surfaces: [],
        entries: [{ rect: [36, 128, 156, 141], only: hex('468084 609c9c 28464e ecdec4') }] },
      { name: 'sofa', base: 128, occludes: true, surfaces: ['couch'],
        entries: [{ rect: [30, 85, 162, 128], not: LIVING_BG }], box: [30, 86, 161, 123] },
      { name: 'sofa_cushion_yellow', base: 128, occludes: false, surfaces: [],
        entries: [{ rect: [52, 98, 65, 107] }] },
      { name: 'sofa_cushion_pink', base: 128, occludes: false, surfaces: [],
        entries: [{ rect: [124, 100, 139, 123] }] },
      { name: 'floor_lamp', base: 129, occludes: false, surfaces: [],
        entries: [{ rect: [9, 24, 29, 129], only: hex('6e5a3c f6e2b0 dcbe82 ceb078 544028') }] },
      { name: 'side_table_plant', base: 126, occludes: false, surfaces: [],
        entries: [{ rect: [164, 66, 186, 128], only: [...PLANT_GREENS, ...PLANT_POT, ...TABLE_WOOD] }, { rect: [167, 105, 180, 106], only: ['946238'] }] },
    ],
    fills: [
      { rect: [8, 52, 29, 69], repeat: [] },                                        /* the picture frame: the pole's two columns take their neighbours */
      { rect: [181, 0, 191, 83], repeat: [[0, 1]] },                                /* the shade the door frame throws on the wall: constant down a column */
      { rect: [181, 84, 191, 116], repeat: [[0, 1]] },
      { rect: [6, 0, 180, 83], avoid: [[43, 6, 144, 83], [8, 52, 29, 69]], repeat: [[8, 12], [16, 0], [0, 24]] }, /* the diamond wallpaper */
      { rect: [6, 84, 180, 116], repeat: [] },                                      /* the panelled wainscot and its skirting */
      { rect: [0, 117, 191, 143], repeat: 'auto' },                                 /* the floor boards */
    ],
  },
  bedroom: {
    source: 'bedroom',
    pieces: [
      { name: 'rug_bedroom', base: null, occludes: false, surfaces: [],
        entries: [{ rect: [40, 126, 150, 142], only: hex('e9a3a0 c7566b f6dcb8') }], box: [40, 126, 150, 142] },
      { name: 'bookcase_plant', base: 112, occludes: false, surfaces: [],
        entries: [{ rect: [106, 32, 137, 111] }, { rect: [109, 0, 133, 31], only: hex('4fa36a 2f7a4a 8fd28a c8683a 8f4325 d89575') }],
        box: [106, 32, 137, 111] },
      { name: 'cat_tree_post', base: 137, occludes: true, surfaces: [],
        entries: [{ rect: [155, 76, 164, 128], only: SISAL }, { rect: [174, 106, 179, 128], only: SISAL },
          { rect: [146, 76, 153, 91], only: hex('e8e0d0 e9c46a fff3c4') }],
        box: [155, 76, 164, 128], holes: 'column' },
      { name: 'cat_tree_cubby', base: 137, occludes: true, surfaces: [],
        entries: [{ rect: [164, 80, 183, 99], only: [...PURPLE, '382852', '1e162d'] }] },
      { name: 'cat_tree_slab_base', base: 137, occludes: true, surfaces: [],
        entries: [{ rect: [136, 128, 181, 136], only: PURPLE }] },
      { name: 'cat_tree_slab_mid', base: 137, occludes: true, surfaces: ['mid'],
        entries: [{ rect: [122, 100, 181, 108], only: PURPLE }] },
      { name: 'cat_tree_slab_top', base: 137, occludes: true, surfaces: ['top'],
        entries: [{ rect: [122, 64, 187, 75], only: PURPLE }] },
    ],
    fills: [
      { rect: [104, 0, 191, 15], repeat: [[24, 0], [48, 0]] },                      /* the dithered top band of the wall */
      { rect: [104, 16, 191, 83], avoid: [[148, 25, 177, 49]], repeat: [[24, 0], [24, 10], [24, 20], [48, 20], [72, 20], [24, -20], [48, 0], [0, 1]] }, /* striped wall (the picture is not repeated) */
      { rect: [104, 84, 191, 116], repeat: [[32, 0], [64, 0]] },                    /* the panelled wainscot */
      { rect: [0, 117, 191, 143], repeat: 'auto' },                                 /* the floor boards */
    ],
  },
  kitchen: {
    source: 'kitchen',
    pieces: [
      { name: 'counter', base: 128, occludes: true, surfaces: ['counter'],
        entries: [{ rect: [58, 90, 186, 128], not: [...KITCHEN_BG, ...TILE_WALL] },
          { rect: [66, 76, 182, 89], only: hex('d65252 1e162d dc3c3c faaa3c 8cc85a bec4c8 aab2b4 788084 969ca0') },
          { rect: [164, 84, 177, 89], only: ['ece2ce'] }] },
      { name: 'fridge', base: 128, occludes: true, surfaces: [],
        entries: [{ rect: [14, 30, 53, 127], not: KITCHEN_BG }] },
    ],
    fills: [
      { rect: [8, 0, 60, 83], avoid: [[27, 11, 41, 25]], repeat: [[0, 1]] },        /* striped wall left of the cabinet: constant down a column (the clock is not) */
      { rect: [61, 0, 105, 58], avoid: [[60, 10, 106, 51]], repeat: [[0, 1], [12, 0]] },
      { rect: [61, 59, 186, 89], repeat: [[8, 0], [0, 12]] },                       /* the brick tiles of the splashback */
      { rect: [11, 84, 60, 116], repeat: [[16, 0]] },                                /* the wainscot */
      { rect: [61, 90, 185, 116], repeat: [[16, 0]] },
      { rect: [0, 117, 191, 143], repeat: 'auto' },                                 /* the tiled floor */
    ],
  },
};

module.exports = { ROOMS, ROOM_W: 192, ROOM_H: 144 };
