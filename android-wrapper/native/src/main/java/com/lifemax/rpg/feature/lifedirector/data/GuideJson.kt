package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.lifedirector.domain.GuideTag
import org.json.JSONObject

/*
 * The dial's part of a guide step (phase 4A, Today's guide): the mode, the spotlight's shape and the name tags the page pins. Strict
 * like the rest: every step carries them (Week's with no mode and no tags), and a missing or wrong-kind field, an unknown mode or a
 * tag off the dial throws, so the projection is refused.
 */

/** The dial's look on a guide step, as nativeGuide sends it. */
internal data class GuideDialJson(
    val mode: String,
    val round: Boolean,
    val pad: Float,
    val tags: List<GuideTag>,
)

internal fun JSONObject.guideDial(): GuideDialJson {
    val mode = get("mode") as? String ?: throw IllegalArgumentException("mode is not text")
    require(mode in MODES) { "Unknown guide mode $mode" }
    val pad = number("pad")
    require(pad >= 0f) { "A spotlight padding below zero" }
    return GuideDialJson(
        mode = mode,
        round = getBoolean("round"),
        pad = pad,
        tags =
            getJSONArray("tags").objects().map { t ->
                GuideTag(
                    x = t.number("x").also { require(it in 0f..SIDE) { "A tag off the dial" } },
                    y = t.number("y").also { require(it in 0f..SIDE) { "A tag off the dial" } },
                    text =
                        (t.get("text") as? String)?.takeIf { it.isNotEmpty() }
                            ?: throw IllegalArgumentException("A tag without words"),
                    kind =
                        t.get("kind") as? String
                            ?: throw IllegalArgumentException("kind is not text"),
                )
            },
    )
}

/** a number, never text or null taken as one */
private fun JSONObject.number(key: String): Float =
    ((get(key) as? Number) ?: throw IllegalArgumentException("$key is not a number"))
        .toFloat()
        .also { require(it.isFinite()) { "$key is not a number" } }

/** the page's data-tour modes ("mini" is set-up's mini dial, 4B) and none */
private val MODES = setOf("", "clock", "outer", "inner", "handles", "center", "mini")

private const val SIDE = 360f
