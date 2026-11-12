package com.lifemax.rpg.feature.pet.world

/**
 * The page's `world` block (pet.world.v1, plan 2.1), sent in the Cat tab's and the Overview cards'
 * projections by petWorldBlock (50-native-cat-world.js). It is state, not a stream: [seq] rises
 * with any change (newest wins), [epoch] rises when the page replaces the world (a backup restore),
 * which makes native drop the world it holds for [stored]. Native never re-derives a care rule from
 * it.
 *
 * @property clockOffsetMs the page's dev time shift (petNow), added to the wall clock.
 * @property cats the living cats, one or two (none in a house without a living cat).
 * @property pair the friendship of a house of two, or null.
 * @property stored the world native last handed the page, or null before the first.
 */
data class WorldBlock(
    val v: Int,
    val seq: Long,
    val epoch: Long,
    val clockOffsetMs: Long,
    val cats: List<WorldCat>,
    val pair: WorldPair?,
    val context: WorldContext,
    val fedCues: List<FedCue>,
    val cheerCues: List<CheerCue>,
    val stored: StoredWorld?,
    /**
     * The ids of the extra rooms the page has opened for the cats (`house.rooms`, from their bond),
     * empty when the page says none or sends no `house` block. Rooms this game does not know are
     * kept out by [com.lifemax.rpg.feature.pet.room.HouseMap.select].
     */
    val houseRooms: Set<String> = emptySet(),
)

/**
 * One living cat as the page keeps it.
 *
 * @property id the album's `c<born base36>`, which names the cat in every command and in the blob.
 * @property slot 0 for the page's first cat, 1 for the second.
 * @property stage growth: -1 grown, 0..5 a kitten (petGrowIdx); [grownAt] when it is grown.
 * @property persona the DNA personality scores, 0..1, by [WorldContract.PERSONA] key.
 * @property legMorph the leg length multiplier and [bodyMorph] the body length one (genes).
 * @property zMax how near the glass the cat may come (petDepthMaxZ: trust tiers, the hunger hold).
 * @property toys the toys open to this cat (by bond, days, or bought early).
 * @property acquired the acquired quirks the page has recorded for the album.
 * @property legacy the web brain to import once, present while [WorldBlock.stored] lacks the cat.
 */
data class WorldCat(
    val id: String,
    val slot: Int,
    val name: String,
    val born: Long,
    val sex: Char,
    val coat: String,
    val stage: Int,
    val grownAt: Long,
    val persona: Map<String, Double>,
    val legMorph: Double,
    val bodyMorph: Double,
    val bond: CatBond,
    val zMax: Int,
    val hunger: CatHunger,
    val care: CatCare,
    val likes: CatLikes,
    val toys: List<String>,
    val acquired: List<String>,
    val legacy: CatLegacy?,
)

/** Bond [level] 0 (Stray) .. 5 (Devoted) and the [pct] of the way to the next. */
data class CatBond(val level: Int, val pct: Int)

/**
 * Hunger as the page counts it: [stage] 0..5 now, from [lastFed]; the clock is paused on
 * [vacation]. [nextStageAt] is when the next stage begins (null on vacation or at the last).
 * Between projections native only advances the stage at [nextStageAt].
 */
data class CatHunger(
    val stage: Int,
    val lastFed: Long,
    val vacation: Boolean,
    val nextStageAt: Long?,
)

/**
 * Today's counters (a new day reads 0) and what the care rules allow: [feedAt] is when petFeed's
 * four-hour gate opens, [canPlay] whether a play still counts today, [giftOk] whether a gift may be
 * given today (bond 5, none given yet).
 */
data class CatCare(
    val fedToday: Int,
    val petsToday: Int,
    val playsToday: Int,
    val feedAt: Long,
    val canPlay: Boolean,
    val giftOk: Boolean,
)

/** Liking of places and toys as shares of the leader (0..100), and the page's favourites. */
data class CatLikes(
    val spots: Map<String, Int>,
    val toys: Map<String, Int>,
    val favSpot: String?,
    val favToy: String?,
)

/**
 * The web brain of one cat, read once to seed its world (petWorldLegacy, plan 2.4): [src] "brain"
 * or "fresh" (no brain: the page's defaults for trait and bond). [spots] are the heaviest places of
 * its memory, 0..100, [glass] the front zones together; [fx] the first moments already reported;
 * [rhythm] the owner's visiting hours 0..100; [pos] where it was, when that is under ten minutes
 * old.
 */
data class CatLegacy(
    val src: String,
    val mood: Mood,
    val trust: Trust,
    val energy: Double,
    val spots: Map<String, Int>,
    val glass: Int,
    val fx: List<String>,
    val innate: List<String>,
    val counters: QuirkCounters,
    val rhythm: List<Int>,
    val voice: Double,
    val toys: List<LegacyToy>,
    val pos: LegacyPos?,
)

/** A toy the web brain left on the floor, at house [x] and depth [z]. */
data class LegacyToy(val id: String, val x: Int, val z: Double)

/** A saved position: house [x], depth [z] and the page's elevation units [elev] (0 the floor). */
data class LegacyPos(val x: Int, val z: Double, val elev: Int)

/** Valence [v] and arousal [a], -1..1. */
data class Mood(val v: Double, val a: Double)

/** The four attachment threads, 0..1: familiarity, trust, security, autonomy. */
data class Trust(val fam: Double, val tru: Double, val sec: Double, val aut: Double)

/** The acquired-quirk counters (night, toys, gentle, kitchen) the page's thresholds read. */
data class QuirkCounters(val n: Int, val t: Int, val g: Int, val k: Int)

/**
 * The pair's friendship: [rel] 0..1, [level] 0..4, rank [lead] (-1 unsettled, else the slot),
 * [settled], and [gainLeft], the day's friendship gain still open (the page keeps the cap). [met]
 * (whether the two have met) comes only from a page that records the moments native reports
 * (`social`, `pet.social.v1`); null from an older page, which has no `social` command.
 */
data class WorldPair(
    val rel: Double,
    val level: Int,
    val lead: Int,
    val settled: Boolean,
    val gainLeft: Double,
    val met: Boolean? = null,
)

/** What the owner is doing that the cats may answer: a [timer] running, today a [restDay]. */
data class WorldContext(val timer: Boolean, val restDay: Boolean)

/** A meal the page served lately, cat [cat] (slot), id [id], [agoMs] ago: played once. */
data class FedCue(val cat: Int, val id: Long, val agoMs: Long)

/** The owner's progress the cats cheer (plan 1.9): [kind] in [WorldContract.CHEER_KINDS]. */
data class CheerCue(val id: Long, val kind: String, val agoMs: Long)

/**
 * The world as the page stored it: written at [epoch] as revision [rev]. [blob] is null when it
 * could not be read (a newer version, a damaged one): every cat is then derived afresh.
 * [rejectedCats] names cats whose own entry was unreadable (those are derived; the rest are kept).
 */
data class StoredWorld(
    val epoch: Long,
    val rev: Long,
    val blob: WorldBlob?,
    val rejectedCats: Set<String>,
)
