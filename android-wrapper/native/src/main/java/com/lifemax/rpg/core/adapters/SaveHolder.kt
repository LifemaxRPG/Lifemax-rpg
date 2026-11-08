package com.lifemax.rpg.core.adapters

import com.lifemax.rpg.core.save.EconomySaveCodec
import com.lifemax.rpg.core.save.SaveDecoding
import com.lifemax.rpg.core.save.SaveReading

/**
 * Holds the latest decoded save for the adapters. [load] takes the stored text the page last
 * persisted and keeps the decoding only when the codec accepts it; a refused text leaves the
 * previous good view in place (the last good frame is kept, as with every projection). Reading
 * never writes anything. How the text reaches native (the device store or a bridge push) is WP6's
 * wiring; nothing calls [load] in the app yet.
 */
class SaveHolder(private val reading: SaveReading = SaveReading.AS_PAGE) : SaveSource {
    @Volatile private var held: SaveView? = null

    override fun view(): SaveView? = held

    /** Decodes [text] and holds it; returns the decoding so a caller can report a refusal. */
    fun load(text: String): SaveDecoding =
        EconomySaveCodec.decode(text, reading).also {
            if (it is SaveDecoding.Decoded) held = SaveView.of(it)
        }

    /** Forgets the held save. */
    fun clear() {
        held = null
    }
}
