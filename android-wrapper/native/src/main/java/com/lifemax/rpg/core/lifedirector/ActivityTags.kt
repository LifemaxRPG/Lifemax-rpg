package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.PageValues.text
import com.lifemax.rpg.core.lifedirector.PageValues.truthy
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * Activity tags (LDD-1 to LDD-3): dotted names such as `fitness.strength` or
 * `language.spanish.vocab`, where a quest's requirement names the kind of session that satisfies it
 * and a template names the kind of session it makes. Tags are compared as UTF-16 text, exactly: no
 * case folding, no normalisation.
 */
object ActivityTags {
    private const val WILDCARD = ".*"
    private const val EXACT_DEPTH = 1000.0

    /**
     * LDD-1 `tagMatches`: whether a session tagged [template] satisfies a requirement tagged
     * [required]. Either side falsy: no. A requirement ending in `.*` matches its base and every
     * tag under it; any other requirement matches itself and every tag under it (`fitness` matches
     * `fitness.strength`, not `fitnessx`). A requirement that is truthy but not text stops the page
     * with a `TypeError`, and so does a template that is not text once it is compared by prefix.
     */
    fun matches(required: JsonValue?, template: JsonValue?): Boolean {
        if (!truthy(required) || !truthy(template)) return false
        val req = (required as? JsonString)?.value ?: throw PageTypeError("endsWith")
        val base = if (req.endsWith(WILDCARD)) req.dropLast(WILDCARD.length) else req
        if (template is JsonString && template.value == base) return true
        val tpl = (template as? JsonString)?.value ?: throw PageTypeError("startsWith")
        return tpl.startsWith("$base.")
    }

    /**
     * LDD-2 `tagDepth`: how closely a matching [template] tag fits [required]: 1000 when they are
     * the same text, else the number of dot-separated parts of [required] after its first `.*` is
     * removed (wherever it is: `a.*.b` counts as `a.b`).
     */
    fun depth(required: String, template: JsonValue?): Double =
        if (template is JsonString && template.value == required) EXACT_DEPTH
        else required.replaceFirst(WILDCARD, "").split('.').size.toDouble()

    /**
     * LDD-3 `tagProfile`: the default demand of [tag]'s root, the text before its first dot (a
     * falsy tag is `general`; anything else is read as its text), or the `general` profile when the
     * root has none.
     */
    fun profile(tag: JsonValue?): TagProfile {
        val profiles = DemandTables.packaged.tagProfiles
        val root = (if (truthy(tag)) text(tag) else GENERAL).substringBefore('.')
        return profiles[root] ?: profiles.getValue(GENERAL)
    }

    private const val GENERAL = "general"
}
