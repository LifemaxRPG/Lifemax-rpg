package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.economy.EconomySave
import com.lifemax.rpg.core.economy.JournalCoach

/**
 * The journal coach's stored memory, read side: what [com.lifemax.rpg.core.economy.Coach] reads
 * (the once-only marks and the written reflections). Writing them is the page's while it is the
 * only writer; the economy returns changed copies for the shadow comparison instead.
 */
interface CoachStore {
    /** `journalCoach`: the once-only awards, reviews and welcome-back days. */
    fun journal(): JournalCoach

    /** `dayReflections`: the written reflection per day key. */
    fun reflections(): Map<String, String>

    /** The reflection written for [day], or null. */
    fun reflection(day: String): String? = reflections()[day]

    /** [save] with this store's coach memory in it (what an economy action starts from). */
    fun into(save: EconomySave): EconomySave =
        save.copy(journal = journal(), dayReflections = reflections())
}

/** The coach memory of the latest decoded save: the one source of truth while the page writes. */
class SaveCoachStore(private val source: SaveSource) : CoachStore {
    override fun journal(): JournalCoach = source.view()?.economy?.journal ?: JournalCoach()

    override fun reflections(): Map<String, String> =
        source.view()?.economy?.dayReflections ?: emptyMap()
}
