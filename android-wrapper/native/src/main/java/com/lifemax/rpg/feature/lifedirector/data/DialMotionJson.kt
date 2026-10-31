package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.BuildGhost
import com.lifemax.rpg.feature.lifedirector.domain.BuildItem
import com.lifemax.rpg.feature.lifedirector.domain.BuildSweep
import com.lifemax.rpg.feature.lifedirector.domain.BuildWords
import com.lifemax.rpg.feature.lifedirector.domain.DialBuild
import com.lifemax.rpg.feature.lifedirector.domain.DialConfirm
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialStop
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.DragWords
import com.lifemax.rpg.feature.lifedirector.domain.FlyKind
import com.lifemax.rpg.feature.lifedirector.domain.HandleEnd
import com.lifemax.rpg.feature.lifedirector.domain.ReadoutStep
import org.json.JSONObject

/*
 * The dial's handles, the places they land, the build to play (LifeDirector.native's nativeHandles
 * and nativeBuild in logic.js) and the dial commands' replies, as typed data. Malformed input
 * throws, so the repository keeps the last good projection.
 */

/** The dial's handles over its stops; every handle has a length for every stop. */
internal class DialHandles(
    val handles: List<DialHandle>,
    val stops: List<DialStop>,
    val words: DragWords,
)

internal fun handles(d: JSONObject): DialHandles {
    val stops =
        d.getJSONArray("stops").objects().map {
            DialStop(it.getInt("m"), it.angle("a"), it.angle("from"), it.getString("time"))
        }
    if (stops.isNotEmpty()) require(stops[0].from == 0f) { "The first stop starts the day" }
    // the finger's angles rise; a day whose clocks go back can land a later stop on an earlier
    // place, so only the picking angles are in order
    stops.zipWithNext().forEach { (a, b) -> require(b.from > a.from) { "Stops out of dial order" } }
    stops.forEach { require(it.m in 0 until DAY_MINUTES) { "Minute ${it.m} is not in the day" } }
    val handles =
        d.getJSONArray("handles").objects().map {
            val kind = it.getString("kind")
            require(kind == "sleep" || kind == "work") { "Unknown handle kind $kind" }
            val end = it.getString("end")
            require(end == "start" || end == "end") { "Unknown handle end $end" }
            val lengths = it.getJSONArray("lengths").strings()
            require(lengths.size == stops.size) { "A length for every stop" }
            DialHandle(
                key = it.getString("key").also { k -> require(k.isNotBlank()) },
                rule = it.getString("rule"),
                end = if (end == "start") HandleEnd.START else HandleEnd.END,
                kind = if (kind == "sleep") BlockKind.SLEEP else BlockKind.WORK,
                a = it.angle("a"),
                minute = it.getInt("minute").also { m -> require(m in 0 until DAY_MINUTES) },
                fixed = it.angle("fixed"),
                label = it.getString("label"),
                aria = it.getString("aria"),
                lengths = lengths,
            )
        }
    require(handles.isEmpty() || stops.isNotEmpty()) { "Handles without places to land" }
    val words = d.getJSONObject("dragWords")
    return DialHandles(
        handles,
        stops,
        DragWords(words.getString("tooShort"), words.getString("release")),
    )
}

internal fun build(d: JSONObject): DialBuild? {
    if (d.isNull("build")) return null
    val b = d.getJSONObject("build")
    val mode = b.getString("mode")
    require(mode == "build" || mode == "repair") { "Unknown build mode $mode" }
    val full = b.getBoolean("full")
    require(full == (mode == "build")) { "Only a build is full" }
    val sweep =
        if (b.isNull("sweep")) null
        else
            b.getJSONObject("sweep").let { s ->
                BuildSweep(
                    s.getInt("ms").also { require(it > 0) },
                    s.getString("kick"),
                    s.getJSONArray("readout").objects().map {
                        ReadoutStep(it.angle("a"), it.getString("hour"), it.getString("what"))
                    },
                )
            }
    require((sweep != null) == full) { "A full build sweeps, a repair does not" }
    require(sweep == null || sweep.readout.isNotEmpty()) { "A sweep without a read-out" }
    val start = words(b.getJSONObject("start"))
    val lock = words(b.getJSONObject("lock"))
    val end = b.getInt("end")
    require(start.at in 0..lock.at && lock.at <= end) { "Build phases out of order" }
    val items =
        b.getJSONArray("items").objects().map {
            val kind = it.getString("kind")
            require(kind == "new" || kind == "moved") { "Unknown build item $kind" }
            BuildItem(
                id = it.getString("id"),
                kind = if (kind == "new") FlyKind.NEW else FlyKind.MOVED,
                a0 = it.angle("a0"),
                a1 = it.angle("a1"),
                from = it.getDouble("from").toFloat().also { f -> require(f.isFinite()) },
                at = it.getInt("at"),
                ms = it.getInt("ms"),
                land = it.getInt("land"),
            )
        }
    items.forEach {
        require(it.at >= 0 && it.ms > 0 && it.land >= it.at) { "A flight out of time" }
    }
    return DialBuild(
        id = b.getLong("id"),
        full = full,
        sweep = sweep,
        intro = if (b.isNull("intro")) null else words(b.getJSONObject("intro"), 0),
        start = start,
        items = items,
        keep = b.getJSONArray("keep").strings(),
        flips = b.getJSONArray("flips").strings(),
        ghosts =
            b.getJSONArray("ghosts").objects().map { BuildGhost(it.angle("a0"), it.angle("a1")) },
        lock = lock,
        end = end,
    )
}

private fun words(w: JSONObject, at: Int = w.getInt("at")) =
    BuildWords(at, w.getString("kick"), w.getString("big"), w.getString("sub"))

/**
 * A command's reply in NativeTabKit's envelope, with what the dial commands add. A confirmation
 * missing any of its words is no confirmation: the screen then lets the handle go, where a throw
 * would leave it waiting for an answer that has already come.
 */
fun directorReply(r: JSONObject): DirectorReply =
    DirectorReply(
        requestId = r.optString("requestId"),
        command = r.optString("command"),
        ok = r.optBoolean("ok"),
        message = r.optString("message"),
        changed = r.optBoolean("changed"),
        moved = r.optBoolean("moved"),
        confirm = r.optJSONObject("confirm")?.let { runCatching { confirm(it) }.getOrNull() },
    )

private fun confirm(c: JSONObject) =
    DialConfirm(
        title = c.getString("title"),
        from = c.getString("from"),
        to = c.getString("to"),
        problem = c.getString("problem"),
        body = c.getString("body"),
        note = c.getString("note"),
        apply = c.getString("apply"),
        keep = c.getString("keep"),
    )

private fun JSONObject.angle(key: String) =
    getDouble(key).toFloat().also { require(it in 0f..360f) { "$key $it is off the dial" } }

private const val DAY_MINUTES = 1440
