package com.lifemax.rpg.feature.log.data

import com.lifemax.rpg.feature.log.domain.LogDraft
import org.json.JSONObject

/**
 * The draft in the shape the page's recordActivity reads (the web form's field names and string
 * values).
 */
fun LogDraft.toJson(): JSONObject =
    JSONObject()
        .put("type", type)
        .put("customName", customName)
        .put("duration", duration.toString())
        .put("quality", quality)
        .put("skill", skill)
        .put("primary", primary)
        .put("secondary", secondary)
        .put("notes", notes)
