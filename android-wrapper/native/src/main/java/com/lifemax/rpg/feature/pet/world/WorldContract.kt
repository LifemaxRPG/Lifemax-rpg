package com.lifemax.rpg.feature.pet.world

/**
 * The cat world contract between the page and native (docs/specs/CAT_BRAIN_PLAN.md section 2, phase
 * CB0): the capability both cat screens advertise, the limits every decoder holds the page and
 * native to, and the page's ids (places, toys, quirks) as 50-native-cat-world.js and the pet
 * segments name them. WorldFixtureTest reads the page's own goldens against these.
 */
object WorldContract {
    /**
     * Advertised by NativePetPlugin and NativeOverviewCardsPlugin; the page sends `world` for it.
     */
    const val CAPABILITY = "pet.world.v1"

    /**
     * The pair's moments (CB5): native reports each moment two cats completed (`social`) and the
     * page, which records it, says so with the pair's `met` in the block.
     */
    const val SOCIAL_CAPABILITY = "pet.social.v1"

    /** The `world` block's version, and the world blob versions this app reads and writes. */
    const val BLOCK_VERSION = 1
    const val BLOB_VERSION = 1

    /** The block's UTF-8 size without `stored`, and the stored blob's (the page refuses more). */
    const val BLOCK_MAX_BYTES = 6 * 1024
    const val BLOB_MAX_BYTES = 8 * 1024

    const val MAX_CATS = 2
    const val MAX_NAME = 40
    const val MAX_FED_CUES = 4
    const val MAX_CHEER_CUES = 4
    const val MAX_TOYS_ON_FLOOR = 8
    const val MAX_OUTBOX = 16
    const val MAX_CHEER_SEEN = 16
    const val MAX_AWAY_LINES = 3
    const val MAX_AWAY_LINE = 160
    const val MAX_BLOB_SPOTS = 6
    const val MAX_LEGACY_TOYS = 3
    const val MAX_INNATE = 2
    const val HOURS = 24
    const val RNG_STREAMS = 3

    /** Growth stage: -1 grown, 0..5 a kitten (petGrowIdx). Hunger stage 0 content .. 5 critical. */
    const val STAGE_GROWN = -1
    const val STAGE_MAX = 5
    const val HUNGER_MAX = 5
    const val BOND_MAX = 5
    const val PAIR_LEVEL_MAX = 4

    /** The page's depth (z): 0 the back wall to 100 the glass. */
    const val Z_MAX = 100.0

    /**
     * House x in room pixels: the widest house, the three first rooms with the three that open
     * later on either side (`pet/room/house.json`); what a cat may stand on today is the open part.
     */
    const val HOUSE_X_MIN = -576.0
    const val HOUSE_X_MAX = 576.0

    /** The most rooms the page may list as open (`house.rooms`). */
    const val MAX_HOUSE_ROOMS = 8

    /** The latest time any field may name (year 2200), and the longest dev clock shift (a year). */
    const val TIME_MAX = 7_258_118_400_000L
    const val CLOCK_SHIFT_MAX = 366L * 24 * 3600 * 1000

    /** The page's places (PET_SPOTS, 25-pet-likes.js). */
    val SPOTS =
        setOf(
            "bed",
            "sill",
            "tree",
            "rug",
            "couch",
            "lsill",
            "lrug",
            "pouf",
            "cushion",
            "counter",
            "ksill",
            "box",
            "kitchen",
        )

    /** The page's toys (PET_TOYS). */
    val TOYS = setOf("ball_a", "mouse", "feather", "ball_b", "yarn", "spring", "wand")

    /** Innate and acquired quirks (PET_QUIRK_INNATE, PET_QUIRK_ACQ in 26-pet-brain.js). */
    val QUIRKS_INNATE =
        setOf(
            "paw_tuck_sleeper",
            "gravity_tester",
            "dramatic_flopper",
            "greeble_spotter",
            "polite_paw_tap",
            "post_meal_zoomer",
            "shadow_chaser",
            "chirp_greeter",
        )
    val QUIRKS_ACQUIRED =
        setOf("night_owl_sentinel", "toy_hoarder", "lap_barnacle", "hydro_inspector")

    /** The album's first moments (PET_CORE_TEXT's first_* keys) native may report once per cat. */
    val FIRST_MOMENTS =
        setOf(
            "first_blink",
            "first_belly",
            "first_respect",
            "first_trophy",
            "first_glass",
            "first_vigil",
            "first_zoomies",
            "first_co",
        )

    /** Cheer cue kinds (plan 1.9; the timer and the rest day are state, in `context`). */
    val CHEER_KINDS = setOf("session_done", "level_up")

    /** Personality scores (PET_PERSONA, 32-pet-dna.js). */
    val PERSONA = listOf("sk", "ou", "dm", "sp", "fr", "vo")

    /** A cat's world id: the album's `c<born base36>` (petAlbumCatId), stable across renames. */
    val CAT_ID = Regex("c[0-9a-z]{1,12}")

    /** An operation id, as the page keeps it (80 characters at most). */
    val OP_ID = Regex("[A-Za-z0-9:._-]{1,80}")

    /** A local day as the page's getToday writes it. */
    val DAY = Regex("\\d{4}-\\d{2}-\\d{2}")

    /** A coat key: the four shipped names and the page's `dna_...` keys. */
    val COAT = Regex("[a-z][a-z0-9_]{0,59}")

    /** An activity or surface name the brain writes (lower case, underscores). */
    val WORD = Regex("[a-z][a-z0-9_]{0,31}")

    /** The world id of a cat born at [bornMs], as the page makes it. */
    fun catId(bornMs: Long): String = "c" + bornMs.toString(Character.MAX_RADIX)
}
