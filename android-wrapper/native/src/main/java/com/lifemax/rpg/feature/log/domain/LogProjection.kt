package com.lifemax.rpg.feature.log.domain

/** One activity preset as the page defines it (its rules stay in the page). */
data class LogPreset(
    val id: String,
    val name: String,
    val icon: String,
    val base: Int,
    val skill: String,
    val primary: String,
    val secondary: String,
)

data class LogAttribute(
    val id: String,
    val name: String,
    val short: String,
    val icon: String,
    val color: Long,
)

data class LogQuality(val value: String, val label: String, val hint: String)

/** The form, as plain data. The page's recordActivity takes exactly these fields. */
data class LogDraft(
    val type: String,
    val customName: String,
    val duration: Int,
    val quality: String,
    val skill: String,
    val primary: String,
    val secondary: String,
    val notes: String,
)

data class XpShare(val attribute: String, val xp: Int)

/** What the page says the award would be for a draft. */
data class LogPreview(
    val total: Int,
    val skill: String,
    val durationFactor: Double,
    val split: List<XpShare>,
    val enough: Boolean,
)

data class LogProjection(
    val session: String,
    val sequence: Long,
    val presets: List<LogPreset>,
    val attributes: List<LogAttribute>,
    val qualities: List<LogQuality>,
    val maxMinutes: Int,
    val draft: LogDraft,
    val draftRevision: Long,
    val preview: LogPreview,
    val tokens: Map<String, Long>,
    val light: Boolean,
) {
    fun preset(id: String) = presets.firstOrNull { it.id == id }

    fun attribute(id: String) = attributes.firstOrNull { it.id == id }
}
