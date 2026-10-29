package com.lifemax.rpg.feature.character.data

import com.lifemax.rpg.feature.character.domain.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** No Activity, WebView, save writes or gameplay calculations are held here. */
class WebCharacterRepository : CharacterRepository {
    private val mutable = MutableStateFlow<CharacterProjection?>(null)
    override val state = mutable.asStateFlow()

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        require(j.getInt("protocol") == 1)
        val session = j.getString("webSessionId")
        val sequence = j.getLong("sequence")
        require(session.isNotBlank() && sequence > 0)
        val old = mutable.value
        if (old != null && old.session != session && !establishSession) return
        if (old?.session == session && sequence <= old.sequence) return
        val a = j.getJSONArray("attributes")
        require(a.length() == 8)
        val attrs =
            (0 until a.length()).map { i ->
                val x = a.getJSONObject(i)
                val d = x.getJSONArray("days")
                AttributeProjection(
                    x.getString("id"),
                    x.getString("name"),
                    x.getString("short"),
                    x.getLong("color"),
                    x.getInt("level"),
                    x.getInt("previous"),
                    x.getDouble("into"),
                    x.getDouble("needed"),
                    x.getDouble("pct").toFloat(),
                    x.getDouble("total"),
                    x.getDouble("week"),
                    (0 until d.length()).map { d.getDouble(it).toFloat() },
                    x.getBoolean("focus"),
                    x.getString("status"),
                    x.getString("last"),
                    x.getDouble("rank"),
                )
            }
        require(
            attrs.map { it.id }.toSet() ==
                setOf(
                    "physique",
                    "endurance",
                    "intelligence",
                    "business",
                    "social",
                    "discipline",
                    "adventure",
                    "wellbeing",
                )
        )
        require(
            attrs.all { it.level >= 1 && it.needed > 0 && it.pct.isFinite() && it.days.size == 7 }
        )
        val s = j.getJSONArray("skills")
        val b = j.getJSONArray("benchmarks")
        val t = j.getJSONArray("tabs")
        val colors = j.getJSONObject("tokens")
        mutable.value =
            CharacterProjection(
                session,
                sequence,
                j.getString("name"),
                j.getString("avatar"),
                j.getString("title"),
                j.getInt("level"),
                j.getDouble("pct").toFloat(),
                j.getString("xp"),
                j.getString("nextLevel"),
                j.getString("nextTitle"),
                j.getString("streak"),
                j.getString("totalXP"),
                j.getString("week"),
                j.getString("luck"),
                j.getString("weight"),
                j.getString("bmi"),
                j.getInt("cap"),
                j.getString("archetype"),
                attrs,
                (0 until s.length()).map {
                    val x = s.getJSONObject(it)
                    SkillProjection(
                        x.getString("name"),
                        x.getDouble("xp"),
                        x.getInt("level"),
                        x.getDouble("pct").toFloat(),
                    )
                },
                (0 until b.length()).map {
                    val x = b.getJSONObject(it)
                    BenchmarkProjection(x.getString("name"), x.getString("value"))
                },
                (0 until t.length()).map {
                    val x = t.getJSONObject(it)
                    x.getString("id") to x.getString("label")
                },
                colors.keys().asSequence().associateWith { colors.getLong(it) },
                j.getBoolean("light"),
            )
    }
}
