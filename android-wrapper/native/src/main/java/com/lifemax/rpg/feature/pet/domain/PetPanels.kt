package com.lifemax.rpg.feature.pet.domain

/*
 * The Cat tab's panels (`pet.panels.v1`, the `panels` block of the projection, 50-native-pet-panels.js):
 * the read-mostly cards of the page's Cat view for the cat whose card shows. Every word is the
 * page's, made by the same functions that fill the page's own DOM (petMindData, petLikesData,
 * petInnerData, petDnaPanelData, petPairCardData, petToysData); native draws these and decides
 * nothing.
 */

/** The titles and button words of the panels' cards. */
data class PetPanelWords(
    val together: String,
    val switcher: String,
    val likes: String,
    val inner: String,
    val dna: String,
    val toys: String,
    val toysSub: String,
    val sparks: String,
    val buy: String,
    val bought: String,
    val genomeShow: String,
    val genomeHide: String,
    val moreText: String,
)

/**
 * The mind row: the bond style's id (a colour key) and name, the mood word, the sentence about how
 * the cat feels, and what it did while the owner was away (empty when that is not fresh).
 */
data class PetMind(
    val styleId: String,
    val styleName: String,
    val mood: String,
    val awayTitle: String,
    val text: String,
    val away: String,
)

/** A bar with a name and the page's words for it: an attachment thread, a temperament, a rank. */
data class PetBar(val label: String, val pct: Int, val word: String)

/**
 * One bar of the likes list: a place or a toy, as a share of the leader, with the favourite marked.
 */
data class PetLikeRow(val name: String, val pct: Int, val favourite: Boolean)

/** A line with a bold lead-in ([bold], may be empty) and the [rest] of it. */
data class PetLine(val bold: String, val rest: String)

/**
 * "What Miso likes": the page's lead-in and sentence, the top places and toys, and their headings.
 */
data class PetLikes(
    val lead: String,
    val body: String,
    val placesTitle: String,
    val toysTitle: String,
    val places: List<PetLikeRow>,
    val toys: List<PetLikeRow>,
    val noPlaces: String,
)

/** A quirk: its [name], where it came from ("Born with it") and what the cat does. */
data class PetQuirk(val name: String, val source: String, val text: String)

/** A moment the cat remembers: [text], where ([zone], may be empty) and how long ago. */
data class PetMemory(val text: String, val zone: String, val ago: String)

/** The inner-life panel: four threads, quirks, memories, the small print and the comfort zone. */
data class PetInner(
    val lead: String,
    val body: String,
    val quirksTitle: String,
    val memoriesTitle: String,
    val threads: List<PetBar>,
    val quirks: List<PetQuirk>,
    val memories: List<PetMemory>,
    val stats: String,
    val comfort: String,
)

/** A gene row: the [label] (with the gene and chromosome), the genotype and what it does. */
data class PetGeneRow(val label: String, val genotype: String, val text: String)

/** One locus of the full genome table. [gene] is empty for a modelled quantitative locus. */
data class PetLocus(
    val locus: String,
    val gene: String,
    val chromosome: String,
    val position: Int,
    val mother: String,
    val father: String,
)

/** The full genome table: its [title] ("Full genome (36 loci)"), [note], and [loci] while open. */
data class PetGenome(
    val title: String,
    val head: List<String>,
    val count: Int,
    val note: String,
    val loci: List<PetLocus>,
    val open: Boolean,
)

/** The body shape section: the proportions as bars, then the tail gene. */
data class PetMorph(
    val title: String,
    val intro: String,
    val rows: List<PetMorphRow>,
    val tail: PetGeneRow,
)

/** A proportion: its name with the page's word, how far along the scale, and the alleles. */
data class PetMorphRow(val label: String, val pct: Int, val note: String)

/**
 * The genetics panel, read only: the sex chip, karyotype and coat, the gene rows, what the cat
 * carries and what is hidden, the body shape, the temperament bars and the genome table.
 */
data class PetDna(
    val title: String,
    val female: Boolean,
    val sex: String,
    val karyotype: String,
    val coat: String,
    val sterile: String,
    val sub: String,
    val rows: List<PetGeneRow>,
    val carries: PetLine?,
    val hidden: PetLine?,
    val morph: PetMorph?,
    val temperamentTitle: String,
    val temperamentIntro: String,
    val temperamentReads: String,
    val persona: List<PetBar>,
    val mutationsLead: String,
    val mutations: String,
    val genome: PetGenome,
)

/**
 * The pecking order: the one [line] (its bold lead name may be empty) and each cat's confidence.
 */
data class PetRank(
    val title: String,
    val line: PetLine,
    val settled: Boolean,
    val rows: List<PetBar>,
)

/** The Together card, in the page's words. */
data class PetPairCard(
    val title: String,
    val first: String,
    val second: String,
    val level: String,
    val pct: Int,
    val text: String,
    val awayTitle: String,
    val away: String,
    val counts: String,
    val rank: PetRank?,
    val last: String,
)

/** A cat on the switcher: its name and coat. */
data class PetSwitchCat(val name: String, val coat: String)

/** The Together card with the switcher between the two cats and the one the card is about. */
data class PetPair(val card: PetPairCard, val focus: Int, val cats: List<PetSwitchCat>)

/**
 * A toy: its [word] ("Loves it", "Unlocks at Friend"), whether it is open to the cat, whether it
 * was bought early, what the early unlock costs in Sparks (0 for none) and whether the balance
 * covers it.
 */
data class PetToy(
    val id: String,
    val name: String,
    val unlocked: Boolean,
    val word: String,
    val bought: Boolean,
    val price: Int,
    val affordable: Boolean,
)

/** The toy list with the owner's Sparks. */
data class PetToys(val sparks: Int, val items: List<PetToy>)

/** Everything the panels show; a part the page could not build is null and its card is left out. */
data class PetPanels(
    val words: PetPanelWords,
    val mind: PetMind?,
    val likes: PetLikes?,
    val inner: PetInner?,
    val dna: PetDna?,
    val pair: PetPair?,
    val toys: PetToys?,
)
