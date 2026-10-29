package com.lifemax.rpg.feature.character.domain

/** Display projection: all gameplay-derived values have already been computed by the web owner. */
data class AttributeProjection(
    val id: String,
    val name: String,
    val short: String,
    val color: Long,
    val level: Int,
    val previous: Int,
    val into: Double,
    val needed: Double,
    val pct: Float,
    val total: Double,
    val week: Double,
    val days: List<Float>,
    val focus: Boolean,
    val status: String,
    val last: String,
    val rank: Double,
)

data class SkillProjection(val name: String, val xp: Double, val level: Int, val pct: Float)

data class BenchmarkProjection(val name: String, val value: String)

data class CharacterProjection(
    val session: String,
    val sequence: Long,
    val name: String,
    val avatar: String,
    val title: String,
    val level: Int,
    val pct: Float,
    val xp: String,
    val nextLevel: String,
    val nextTitle: String,
    val streak: String,
    val totalXP: String,
    val week: String,
    val luck: String,
    val weight: String,
    val bmi: String,
    val cap: Int,
    val archetype: String,
    val attributes: List<AttributeProjection>,
    val skills: List<SkillProjection>,
    val benchmarks: List<BenchmarkProjection>,
    val tabs: List<Pair<String, String>>,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
