package com.lifemax.rpg.feature.overview.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.overview.domain.CardCelebration
import com.lifemax.rpg.feature.overview.domain.CardChip
import com.lifemax.rpg.feature.overview.domain.CardHaptics
import com.lifemax.rpg.feature.overview.domain.CardPoint
import com.lifemax.rpg.feature.overview.domain.Delta
import com.lifemax.rpg.feature.overview.domain.OverviewHead
import com.lifemax.rpg.feature.overview.domain.OverviewLayout
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.OverviewReply
import com.lifemax.rpg.feature.overview.domain.OverviewTileIds
import com.lifemax.rpg.feature.overview.domain.OverviewTip
import com.lifemax.rpg.feature.overview.domain.TileInfo
import org.json.JSONObject

/*
 * Strict reading of the Overview projection: a key that is missing, or holds the wrong kind of
 * value (a number where words belong, say), throws, so a page that changed its projection is
 * refused instead of drawn wrong. org.json would quietly turn a number into a string, hence the
 * typed readers below.
 */

internal fun JSONObject.str(key: String): String =
    get(key) as? String ?: throw IllegalArgumentException("$key is not a string")

internal fun JSONObject.bool(key: String): Boolean =
    get(key) as? Boolean ?: throw IllegalArgumentException("$key is not a boolean")

/** A whole number (counts, ids, ARGB colours). */
internal fun JSONObject.long(key: String): Long {
    val n = get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    require(n !is Double && n !is Float || n.toDouble() % 1.0 == 0.0) { "$key is not whole" }
    return n.toLong()
}

internal fun JSONObject.int(key: String): Int = long(key).toInt()

internal fun JSONObject.num(key: String): Float =
    (get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")).toFloat()

/** An ARGB colour: a whole number of at most 32 bits. */
internal fun JSONObject.color(key: String): Long =
    long(key).also { require(it in 0..0xFFFFFFFFL) { "$key is not a colour" } }

internal fun JSONObject.strings(key: String): List<String> {
    val a = getJSONArray(key)
    return (0 until a.length()).map {
        a.get(it) as? String ?: throw IllegalArgumentException("$key holds a non-string")
    }
}

internal fun JSONObject.list(key: String): List<JSONObject> = getJSONArray(key).objects()

/** The value at [key] decoded, or null where the page sends null; a missing key is malformed. */
internal fun <T> JSONObject.orNull(key: String, decode: JSONObject.() -> T): T? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else getJSONObject(key).decode()
}

/**
 * Words the page may leave out of a card's state ("intro" for a glance with a dial): "" or null.
 */
internal fun JSONObject.strOrNull(key: String): String? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else str(key)
}

internal fun head(h: JSONObject): OverviewHead {
    val edit = h.getJSONObject("edit")
    return OverviewHead(
        h.str("date"),
        h.str("hello"),
        h.str("classId"),
        edit.str("label"),
        edit.str("a11y"),
    )
}

internal fun tip(t: JSONObject) =
    OverviewTip(t.str("key"), t.str("icon"), t.str("title"), t.str("body"), t.str("dismiss"))

/** ovLayout(): ids the page knows, each once; a hidden id is one of the order's. */
internal fun layout(l: JSONObject): OverviewLayout {
    val order = l.strings("order")
    val hidden = l.strings("hidden")
    require(order.toSet().size == order.size) { "A tile appears twice in the order" }
    require(order.containsAll(hidden)) { "A hidden tile is not in the order" }
    return OverviewLayout(order, hidden)
}

internal fun tileInfo(t: JSONObject) =
    TileInfo(
        t.str("id"),
        t.str("name"),
        t.str("sub"),
        t.str("note"),
        t.str("icon"),
        t.color("color"),
    )

/** Every tile the page draws is named for the editor, once. */
internal fun requireTileInfo(info: List<TileInfo>) {
    val ids = info.map { it.id }
    require(ids.toSet().size == ids.size) { "A tile is named twice" }
    require(ids.toSet().containsAll(OverviewTileIds.ALL)) { "A tile has no name" }
}

internal fun delta(d: JSONObject) = Delta(d.bool("up"), d.str("text"))

internal fun chip(c: JSONObject) = CardChip(c.str("label"), c.str("value"))

internal fun point(p: JSONObject) = CardPoint(p.num("x"), p.num("y"))

internal fun notice(n: JSONObject) = OverviewNotice(n.long("id"), n.str("text"))

/** The image table: each key a data URL of an image (the avatar, app icons). */
internal fun images(j: JSONObject): Map<String, String> =
    j.keys().asSequence().associateWith {
        j.str(it).also { url -> require(url.startsWith("data:image/")) { "$it is no image" } }
    }

/** A celebration the page handed over (fxSink): its gains as Workout plan's gainOf makes them. */
internal fun celebration(c: JSONObject): CardCelebration {
    val gains =
        c.list("gains").map {
            XpGain(it.str("attribute"), it.str("short"), it.int("xp"), it.color("color"))
        }
    return CardCelebration(c.str("id"), c.int("xp"), point(c.getJSONObject("origin")), gains)
}

internal fun haptics(h: JSONObject): CardHaptics {
    val max = h.int("max")
    require(max in 1..1000) { "The haptic cap is out of range" }
    return CardHaptics(h.num("dm"), h.num("add"), h.int("amp"), h.num("min"), max)
}

/** The page's answer to a command, from the kit's reply (it arrives with an update). */
fun overviewReply(r: JSONObject) =
    OverviewReply(
        requestId = r.optString("requestId"),
        command = r.optString("command"),
        ok = r.optBoolean("ok"),
        changed = r.optBoolean("changed"),
        message = r.optString("message"),
    )
