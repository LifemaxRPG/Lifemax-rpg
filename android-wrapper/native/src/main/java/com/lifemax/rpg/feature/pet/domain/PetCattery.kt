package com.lifemax.rpg.feature.pet.domain

import com.lifemax.rpg.feature.pet.art.RigMorph

/*
 * The Cat tab's cattery (`pet.cattery.v1`, the `cattery` block of the projection,
 * 50-native-pet-cattery.js): the Cattery, the Breeding lab, Adoption and the Memorial. The rules
 * are the page's, unchanged; the page rolls every litter and every genome and words everything, and
 * native draws it and asks. A screen's [CatteryDetail] is sent only while that screen is open.
 */

/** The four screens, with the name the page knows each by. */
enum class CatteryScreen(val wire: String) {
    CATTERY("cattery"),
    LAB("lab"),
    ADOPT("adopt"),
    MEMORIAL("memorial");

    companion object {
        fun of(wire: String): CatteryScreen? = entries.firstOrNull { it.wire == wire }
    }
}

/** The titles and button words of the four screens, in the page's words. */
data class CatteryWords(
    val cattery: String,
    val lab: String,
    val memorial: String,
    val adoptTitle: String,
    val adoptHint: String,
    val adoptButton: String,
    val mother: String,
    val father: String,
    val goal: String,
    val breed: String,
    val keep: String,
    val release: String,
    val keepAll: String,
    val releaseAll: String,
    val home: String,
    val homeTaken: String,
    val stray: String,
    val strayHint: String,
    val rename: String,
    val nameLabel: String,
    val newLitter: String,
    val adopt: String,
    val letIn: String,
    val reopen: String,
    val none: String,
    val cancel: String,
    val save: String,
    val back: String,
)

/**
 * What the Cat tab shows of the cattery: how many cats wait there, whether a second cat may come.
 */
data class CatteryEntry(val cats: Int, val max: Int, val memorial: Int, val canAdopt: Boolean)

/**
 * What a preview draws: the coat key, the growth stage (-1 grown), the proportions, asleep or
 * sitting.
 */
data class CatPreview(
    val coat: String,
    val stage: Int,
    val rig: RigMorph = RigMorph.NEUTRAL,
    val sleep: Boolean = false,
)

/**
 * A shape chip ("ears 1.2×"): [label], its full [title] for TalkBack, and whether it is the goal.
 */
data class CatChip(val label: String, val title: String, val goal: Boolean)

/**
 * A cat of the roster or a kitten of a litter, in the page's words. [ask] is the release question.
 */
data class CatteryCat(
    val id: String,
    val name: String,
    val female: Boolean,
    val sub: String,
    val chips: List<CatChip>,
    val preview: CatPreview,
    val home: Boolean,
    val ask: String,
)

data class CatteryOption(val value: String, val label: String)

data class CatteryPick(val key: String, val name: String, val home: Boolean)

/** A litter; [at] names it, so a Keep for a litter that was replaced is refused by the page. */
data class CatteryLitter(
    val at: Long,
    val title: String,
    val parents: String,
    val kits: List<CatteryCat>,
    val full: Boolean,
    val fullNote: String,
)

/** A kitten of an adoption litter: the page's words for it. */
data class AdoptKit(
    val index: Int,
    val female: Boolean,
    val coat: String,
    val trait: String,
    val persona: String,
    val preview: CatPreview,
)

/** One cat that has passed: its [text] is the page's sentence. */
data class MemorialEntry(
    val name: String,
    val coat: String,
    val preview: CatPreview?,
    val text: String,
)

sealed interface CatteryDetail {
    data class Roster(
        val cats: List<CatteryCat>,
        val count: Int,
        val max: Int,
        val canHome: Boolean,
        val nameMax: Int,
        val header: String,
        val small: String,
    ) : CatteryDetail

    data class Lab(
        val intro: String,
        val goal: String,
        val options: List<CatteryOption>,
        val best: String,
        val note: String,
        val moms: List<CatteryPick>,
        val dads: List<CatteryPick>,
        val mom: String,
        val dad: String,
        val forecast: String,
        val litter: CatteryLitter?,
    ) : CatteryDetail

    /** [mode] is `first` (no cat at home), `pair` (a second cat) or empty (none can be adopted). */
    data class Adopt(
        val mode: String,
        val eyebrow: String,
        val title: String,
        val body: String,
        val parents: String,
        val kits: List<AdoptKit>,
        val pick: Int,
        val placeholder: String,
        val nameMax: Int,
        val memory: String,
    ) : CatteryDetail

    data class Memorial(val entries: List<MemorialEntry>, val canReopen: Boolean) : CatteryDetail
}

/** The cattery block: [view] is the screen the page is sending [detail] for, or null. */
data class PetCattery(
    val words: CatteryWords,
    val entry: CatteryEntry,
    val view: CatteryScreen?,
    val detail: CatteryDetail?,
)

/**
 * What the screens ask of the page, each one `cattery` command. One that changes things ([changes])
 * is sent with an operation id that a repeat of the same tap reuses, so it records at most once.
 */
sealed interface CatteryCommand {
    /** True for a command that writes the save and so needs an operation id. */
    val changes: Boolean
        get() = true

    data class View(val screen: CatteryScreen?) : CatteryCommand {
        override val changes
            get() = false
    }

    data class Goal(val value: String) : CatteryCommand {
        override val changes
            get() = false
    }

    data class Pick(val who: String, val key: String) : CatteryCommand {
        override val changes
            get() = false
    }

    data class Breed(val mom: String, val dad: String) : CatteryCommand

    data class Keep(val at: Long, val index: Int) : CatteryCommand

    data class Drop(val at: Long, val index: Int) : CatteryCommand

    data class KeepAll(val at: Long) : CatteryCommand

    data class DropAll(val at: Long) : CatteryCommand

    data class Release(val id: String) : CatteryCommand

    data class Rename(val id: String, val name: String) : CatteryCommand

    data class Home(val id: String) : CatteryCommand

    data object Stray : CatteryCommand

    data object NewLitter : CatteryCommand {
        override val changes
            get() = false
    }

    data class PickKit(val index: Int) : CatteryCommand {
        override val changes
            get() = false
    }

    data class Adopt(val mode: String, val kit: Int, val name: String) : CatteryCommand

    data object Reopen : CatteryCommand
}
