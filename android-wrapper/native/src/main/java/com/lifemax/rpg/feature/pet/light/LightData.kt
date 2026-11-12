package com.lifemax.rpg.feature.pet.light

import com.lifemax.rpg.feature.pet.art.LightTint
import org.json.JSONArray
import org.json.JSONObject

/** What one light does to a cat it falls on: half-steps of shade and the colour it washes with. */
internal class LightDose(val halfSteps: Int, val tint: LightTint)

/**
 * A point light standing in a room (the living room's floor lamp): it lights cats within [reach]
 * house pixels of its x on either side, [LightDose.halfSteps] within [inner], one half-step less
 * out to [reach]. [whenDark] lights only shine once the room is dim (the classic room's lamp glow
 * rule).
 */
internal class PointLight(
    val id: String,
    val room: String,
    val x: Int,
    val reach: Int,
    val inner: Int,
    val dose: LightDose,
    val whenDark: Boolean,
)

/**
 * The room's light as data (`pet/light/lighting.json`, docs/specs/CAT_LIGHTING_PLAN.md section
 * 4.1): what the window patches, the window sills, shaded spots, the night and the lamps do to a
 * cat, how the weather dims the sun, and a per-room bias. Read strictly: a field this decoder does
 * not know, a missing field or a value out of range is a refusal, like the other pet resources.
 */
internal class LightData(
    /**
     * The hours the window light counts as day, from and until (the classic room's 7.5 and 17.5).
     */
    val dayFrom: Double,
    val dayUntil: Double,
    val patchDay: LightDose,
    val patchNight: LightDose,
    val sillSurfaces: Set<String>,
    val sillDay: LightDose,
    val sillTwilight: LightDose,
    val sillNight: LightDose,
    /**
     * Half-steps a cat gets on a surface tucked under or inside something (a box, under a slab).
     */
    val shade: Map<String, Int>,
    /** The tint every cat gets at night out of any light (it only touches dark coats). */
    val nightTint: LightTint,
    /** How strong the sun (and the moon) is in each weather, 0..1. */
    val sun: Map<Weather, Double>,
    val points: List<PointLight>,
    /** Half-steps added to every cat in a room, by room id. */
    val rooms: Map<String, Int>,
) {
    companion object {
        /** The shipped `pet/light/lighting.json`. */
        val shipped: LightData by lazy {
            val bytes =
                LightData::class.java.getResourceAsStream("/pet/light/lighting.json")?.use {
                    it.readBytes()
                } ?: error("missing pet/light/lighting.json")
            parse(String(bytes, Charsets.UTF_8))
        }

        fun parse(text: String): LightData {
            val o = JSONObject(text)
            o.only(
                "version",
                "day",
                "patch",
                "sill",
                "shade",
                "nightTint",
                "weather",
                "points",
                "rooms",
            )
            require(o.getInt("version") == 1) { "lighting: version" }
            val day = o.getJSONArray("day")
            require(day.length() == 2) { "lighting: day is [from, until]" }
            val dayFrom = day.getDouble(0)
            val dayUntil = day.getDouble(1)
            require(dayFrom in 0.0..24.0 && dayUntil in dayFrom..24.0) { "lighting: day hours" }
            val patch = o.getJSONObject("patch").only("day", "night")
            val sill = o.getJSONObject("sill").only("surfaces", "day", "twilight", "night")
            val shade = o.getJSONObject("shade")
            val weather = o.getJSONObject("weather")
            weather.only(*Weather.entries.map { it.key }.toTypedArray())
            val sun =
                Weather.entries.associateWith {
                    val w = weather.getJSONObject(it.key).only("sun")
                    w.getDouble("sun").also { s -> require(s in 0.0..1.0) { "lighting: sun $s" } }
                }
            val rooms = o.getJSONObject("rooms")
            return LightData(
                dayFrom,
                dayUntil,
                dose(patch.getJSONObject("day")),
                dose(patch.getJSONObject("night")),
                sill.getJSONArray("surfaces").strings().toSet(),
                dose(sill.getJSONObject("day")),
                dose(sill.getJSONObject("twilight")),
                dose(sill.getJSONObject("night")),
                shade.keys().asSequence().associateWith { halfSteps(shade.getInt(it)) },
                tint(o.getString("nightTint")),
                sun,
                o.getJSONArray("points").let { a ->
                    List(a.length()) { point(a.getJSONObject(it)) }
                },
                rooms.keys().asSequence().associateWith { halfSteps(rooms.getInt(it)) },
            )
        }

        private fun point(o: JSONObject): PointLight {
            o.only("id", "room", "x", "reach", "inner", "halfSteps", "tint", "when")
            val reach = o.getInt("reach")
            val inner = o.getInt("inner")
            require(reach > 0 && inner in 0..reach) { "lighting: point reach" }
            val whenDark =
                when (val w = o.getString("when")) {
                    "dark" -> true
                    "always" -> false
                    else -> throw IllegalArgumentException("lighting: when $w")
                }
            return PointLight(
                o.getString("id"),
                o.getString("room"),
                o.getInt("x"),
                reach,
                inner,
                LightDose(halfSteps(o.getInt("halfSteps")), tint(o.getString("tint"))),
                whenDark,
            )
        }

        private fun dose(o: JSONObject): LightDose {
            o.only("halfSteps", "tint")
            return LightDose(halfSteps(o.getInt("halfSteps")), tint(o.getString("tint")))
        }

        private fun halfSteps(v: Int): Int {
            require(v in -4..3) { "lighting: half-steps $v" }
            return v
        }

        private fun tint(name: String): LightTint =
            LightTint.entries.firstOrNull { it.name.lowercase() == name }
                ?: throw IllegalArgumentException("lighting: tint $name")

        private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }

        /** Refuses a field not in [names]; answers this object. */
        private fun JSONObject.only(vararg names: String): JSONObject {
            for (k in keys()) require(k in names) { "lighting: unknown field $k" }
            return this
        }
    }
}
