package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.domain.PetRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Decodes the page's Cat tab (`pet.projection.v1`, 50-native-pet.js). Malformed data (a key
 * missing, unknown or of the wrong kind) is rejected, which throws (the kit reports it as a failed
 * open or update) and the last good state is kept; an update older than the one held is ignored; a
 * projection from another page session is taken only on an explicit open.
 */
class WebPetRepository : PetRepository {
    private val mutable = MutableStateFlow<PetProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.int("protocol") == 1) { "Unsupported cat protocol" }
        val session = j.str("webSessionId")
        val sequence = j.long("sequence")
        require(session.isNotBlank() && sequence > 0) {
            "A projection needs a session and a sequence"
        }
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return
        mutable.value = decodePet(j, session, sequence)
    }
}
