package com.lifemax.rpg.feature.pet.world

/**
 * What native asks the page to record (plan 2.2). Every command names its cat by world id (not by
 * slot: a queued command must still mean the same cat after the house changes) and carries an
 * operation id [op], so a repeat of one that landed is answered "Already recorded." and done once.
 * The page is the only writer: a command is ok only once its save stored the change.
 * [WorldCommandJson] writes and reads them strictly (FakePage reads them as the page will).
 */
sealed interface WorldCommand {
    /** The command's wire name. */
    val name: String

    /** The operation id; empty for the two care commands that are repeat-safe by state. */
    val op: String
}

/** `world`: store [blob] as revision [rev] of the world at [epoch] (the page's, from the block). */
data class SaveWorld(override val op: String, val epoch: Long, val rev: Long, val blob: WorldBlob) :
    WorldCommand {
    override val name = "world"
}

/** `pet`: a stroke of at least 1.1 s on cat [cat] ([heldMs] long) counts as a pet. */
data class PetDone(override val op: String, val cat: String, val heldMs: Long) : WorldCommand {
    override val name = "pet"
}

/** `play`: an owner-offered game with [toy] ended; [played] when the cat really played. */
data class PlayDone(
    override val op: String,
    val cat: String,
    val toy: String,
    val played: Boolean,
) : WorldCommand {
    override val name = "play"
}

/** `gift`: the owner tapped the gift cat [cat] left (refused when the block's giftOk was false). */
data class GiftTaken(override val op: String, val cat: String) : WorldCommand {
    override val name = "gift"
}

/**
 * `moment`: something the album keeps happened to cat [cat] at [at] (plan 2.5): [kind] in
 * [MOMENT_KINDS]; a `quirk` names the acquired quirk in [quirk] (null for every other kind).
 */
data class MomentReport(
    override val op: String,
    val cat: String,
    val kind: String,
    val at: Long,
    val quirk: String?,
) : WorldCommand {
    override val name = "moment"

    companion object {
        val MOMENT_KINDS =
            setOf(
                "first_glass",
                "first_co",
                "first_zoomies",
                "first_belly",
                "first_vigil",
                "first_trophy",
                "blink",
                "respect",
                "quirk",
            )
    }
}

/**
 * `social`: a pair activity of [cats] (both world ids) completed at [at]; a `standoff` names its
 * [winner] (one of them), every other kind none.
 */
data class SocialReport(
    override val op: String,
    val kind: String,
    val cats: List<String>,
    val winner: String?,
    val at: Long,
) : WorldCommand {
    override val name = "social"

    companion object {
        val KINDS = setOf("greet", "hiss", "chase", "groom", "nap", "standoff")
    }
}

/** `dwell`: seconds cat [cat] spent in places [spots] and with [toys] on local [day]. */
data class DwellReport(
    override val op: String,
    val cat: String,
    val day: String,
    val spots: Map<String, Int>,
    val toys: Map<String, Int>,
) : WorldCommand {
    override val name = "dwell"

    companion object {
        /** A day has no more seconds than this. */
        const val DAY_SECONDS = 86_400
    }
}

/** The care commands as the Cat tab already sends them (50-native-pet.js): feed once per [op]. */
data class CareFeed(override val op: String) : WorldCommand {
    override val name = "feed"
}

/** Vacation mode on or off for the house; repeat-safe by state ("Already recorded."). */
data class CareVacation(val on: Boolean) : WorldCommand {
    override val name = "vacation"
    override val op = ""
}

/** Rename the focused cat (1 to 14 characters); repeat-safe by state. */
data class CareRename(val newName: String) : WorldCommand {
    override val name = "rename"
    override val op = ""

    companion object {
        const val MAX = 14
    }
}

/**
 * Operation ids. A first moment's op is the cat and the kind, so it can never be recorded twice; a
 * quirk's names the quirk; everything else counts up per cat and command ([n] from the world).
 */
object OpIds {
    fun firstMoment(cat: String, kind: String): String = "$cat:$kind"

    fun quirk(cat: String, quirk: String): String = "$cat:quirk:$quirk"

    fun of(cat: String, command: String, n: Long): String = "$cat:$command:$n"
}

/**
 * The page's answer to a world command: [ok] only when the save stored it (or a repeat found it
 * stored), [changed] when this call changed it, [why] for a refusal (`stale`, `size`, `shape`,
 * `unsaved`, `refused`); a `world` answer also names the page's [epoch] and stored [rev], a
 * stroke's the trust [levelUp] reached.
 */
data class WorldReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val why: String,
    val epoch: Long? = null,
    val rev: Long? = null,
    val levelUp: Int = 0,
) {
    companion object {
        const val ALREADY = "Already recorded."
        val WHY = setOf("", "stale", "size", "shape", "unsaved", "refused")
    }
}
