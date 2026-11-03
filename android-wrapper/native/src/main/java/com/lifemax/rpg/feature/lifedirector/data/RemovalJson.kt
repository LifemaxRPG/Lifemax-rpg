package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.Removal
import org.json.JSONObject

/**
 * Whether the row's session can be taken off the day (director.remove.v1): absent from a page that
 * does not say, otherwise strict. A session that cannot says why, so the screen never has to guess
 * a reason: a row with no reason, or a field that is not an object, is rejected with the rest.
 */
internal fun JSONObject.removal(): Removal? {
    if (!has("remove")) return null
    val r = getJSONObject("remove")
    val ok = r.getBoolean("ok")
    val reason = r.getString("reason")
    require(ok || reason.isNotBlank()) { "A session that cannot be removed says why" }
    return Removal(ok, reason)
}
