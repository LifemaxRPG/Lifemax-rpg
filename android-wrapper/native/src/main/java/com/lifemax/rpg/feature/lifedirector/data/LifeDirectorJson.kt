package com.lifemax.rpg.feature.lifedirector.data

import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.BannerAction
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.CentreButton
import com.lifemax.rpg.feature.lifedirector.domain.CentreNext
import com.lifemax.rpg.feature.lifedirector.domain.Chip
import com.lifemax.rpg.feature.lifedirector.domain.ChipKind
import com.lifemax.rpg.feature.lifedirector.domain.Countdown
import com.lifemax.rpg.feature.lifedirector.domain.CurrentRing
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DialCentre
import com.lifemax.rpg.feature.lifedirector.domain.DialRadii
import com.lifemax.rpg.feature.lifedirector.domain.DirectorBanner
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.DirectorNotice
import com.lifemax.rpg.feature.lifedirector.domain.DirectorToday
import com.lifemax.rpg.feature.lifedirector.domain.EarlierToday
import com.lifemax.rpg.feature.lifedirector.domain.FocusTimer
import com.lifemax.rpg.feature.lifedirector.domain.HourWord
import com.lifemax.rpg.feature.lifedirector.domain.MixShare
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.RingWord
import com.lifemax.rpg.feature.lifedirector.domain.SessionState
import com.lifemax.rpg.feature.lifedirector.domain.StripDay
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.domain.UpNext
import com.lifemax.rpg.feature.lifedirector.domain.UpNextAction
import com.lifemax.rpg.feature.lifedirector.domain.UpNextKind
import org.json.JSONArray
import org.json.JSONObject

/*
 * The page's Life Director projection (LifeDirector.native in logic.js) as typed data. Every
 * function throws on malformed input: an unknown kind, an angle off the dial, a day not on the
 * strip.
 */

internal fun JSONObject.banner(): DirectorBanner? {
    if (isNull("banner")) return null
    val b = getJSONObject("banner")
    val kind = b.getString("kind")
    require(kind == "bad" || kind == "warn") { "Unknown banner kind $kind" }
    return DirectorBanner(
        bad = kind == "bad",
        text = b.getString("text"),
        actions =
            b.getJSONArray("actions").objects().map {
                BannerAction(
                    it.getString("label"),
                    Tap(it.getString("act"), it.optString("id", "")),
                    it.optBoolean("ghost"),
                    it.optBoolean("small"),
                )
            },
        dismiss = b.getBoolean("dismiss"),
    )
}

internal fun JSONObject.notice(): DirectorNotice? =
    if (isNull("notice")) null
    else getJSONObject("notice").let { DirectorNotice(it.getLong("id"), it.getString("text")) }

internal fun JSONObject.today(): DirectorToday? {
    if (isNull("today")) return null
    val t = getJSONObject("today")
    val strip =
        t.getJSONArray("strip").objects().map { d ->
            StripDay(
                d.getString("label"),
                d.getInt("date"),
                d.getJSONArray("dots").strings().map { state(it) },
            )
        }
    val dayIndex = t.getInt("dayIndex")
    require(dayIndex in strip.indices) { "Day $dayIndex is not on the strip" }
    val mix = t.getJSONArray("mix").objects().map { mix(it) }
    require(mix.size == 7) { "Expected the seven shares of the day" }
    val rows = t.getJSONObject("rows")
    return DirectorToday(
        now = t.getLong("now"),
        dayIndex = dayIndex,
        isToday = t.getBoolean("isToday"),
        strip = strip,
        dial = dial(t.getJSONObject("dial")),
        centre = centre(t.getJSONObject("core")),
        mix = mix,
        upNext = if (t.isNull("upNext")) null else upNext(t.getJSONObject("upNext")),
        reminders = t.getJSONArray("reminders").strings(),
        sessions = t.getInt("sessions"),
        ahead = rows(rows.getJSONArray("ahead"), "a"),
        earlier =
            if (rows.isNull("earlier")) null
            else
                rows.getJSONObject("earlier").let {
                    EarlierToday(it.getString("summary"), rows(it.getJSONArray("rows"), "e"))
                },
    )
}

private fun dial(d: JSONObject): DirectorDial {
    val r = d.getJSONObject("radii")
    val radii =
        DialRadii(
            r.float("C"),
            r.float("RO"),
            r.float("WO"),
            r.float("RI"),
            r.float("WI"),
            r.float("T0"),
            r.float("T1"),
            r.float("RL"),
        )
    require(radii.centre == HALF_SIDE) { "The dial is a 360-unit square" }
    require(radii.inner + radii.innerWidth / 2 < radii.outer - radii.outerWidth / 2)
    val arcs =
        d.getJSONArray("arcs").objects().map {
            val ring = it.getString("ring")
            require(ring == "outer" || ring == "inner") { "Unknown ring $ring" }
            DialArc(
                outer = ring == "outer",
                style = ArcStyle.valueOf(it.getString("style").uppercase()),
                kind = kind(it.getString("kind")),
                a0 = it.float("a0"),
                a1 = it.float("a1"),
                start = it.getLong("s"),
                end = it.getLong("e"),
                title = it.getString("title"),
                label = it.getString("label"),
                state = state(it.getString("state")),
                next = it.getBoolean("next"),
                icon = it.getString("icon"),
                tap = tap(it),
                tip = it.getString("tip"),
            )
        }
    val words =
        d.getJSONArray("words").objects().map {
            RingWord(
                it.float("r"),
                it.float("a0"),
                it.float("a1"),
                kind(it.getString("kind")),
                it.getBoolean("inner"),
                it.getString("text"),
                it.getBoolean("flip"),
            )
        }
    arcs.forEach {
        requireSpan(it.a0, it.a1)
        require(it.outer == (it.style == ArcStyle.BAND)) {
            "Bands are the outer ring, the rest inner"
        }
    }
    words.forEach { requireSpan(it.a0, it.a1) }
    val hand = if (d.isNull("hand")) null else d.float("hand")
    require(hand == null || hand in 0f..360f) { "The hand is off the dial" }
    return DirectorDial(
        start = d.getLong("startEpoch"),
        end = d.getLong("endEpoch"),
        radii = radii,
        hourWords =
            d.getJSONArray("hourWords").objects().map {
                HourWord(it.getInt("h"), it.getString("word"))
            },
        arcs = arcs,
        words = words,
        hand = hand,
        ring =
            if (d.isNull("ring")) null
            else
                d.getJSONObject("ring").let {
                    CurrentRing(
                        it.float("r"),
                        it.float("frac").also { f -> require(f in 0f..1f) },
                        kind(it.getString("kind")),
                        it.getBoolean("warn"),
                    )
                },
    )
}

private fun centre(c: JSONObject): DialCentre {
    val kickKind = c.getString("kickKind")
    return DialCentre(
        kick = c.getString("kick"),
        kickKind = kickKind.takeUnless { it.isEmpty() || it == "go" }?.let { kind(it) },
        kickLive = kickKind == "go",
        warn = c.getBoolean("warn"),
        title = c.getString("title"),
        date = c.getBoolean("date"),
        countdown =
            if (c.isNull("big")) null
            else
                c.getJSONObject("big").let {
                    Countdown(it.getInt("h"), it.getInt("m"), it.getBoolean("less"))
                },
        timer = if (c.isNull("timer")) null else timer(c.getJSONObject("timer")),
        sub = c.getString("sub"),
        next =
            if (c.isNull("next")) null
            else
                c.getJSONObject("next").let {
                    CentreNext(
                        it.getString("label"),
                        it.getString("title"),
                        it.getBoolean("live"),
                        tap(it),
                    )
                },
        button =
            if (c.isNull("button")) null
            else
                c.getJSONObject("button").let {
                    CentreButton(
                        it.getString("label"),
                        requireNotNull(tap(it)) { "A button without an action" },
                        it.getBoolean("go"),
                    )
                },
    )
}

private fun upNext(u: JSONObject) =
    UpNext(
        kind = UpNextKind.valueOf(u.getString("kind").uppercase()),
        cue = u.getString("cue"),
        title = u.getString("title"),
        timing = u.getString("when"),
        timer = if (u.isNull("timer")) null else timer(u.getJSONObject("timer")),
        chips = chips(u.getJSONArray("chips")),
        actions =
            u.getJSONArray("actions").objects().map {
                val day = if (it.has("day")) it.getInt("day") else null
                val tap = tap(it)
                require((day == null) != (tap == null)) { "An action is a tap or a day" }
                UpNextAction(
                    it.getString("label"),
                    tap,
                    day,
                    it.optBoolean("primary"),
                    it.optBoolean("ghost"),
                )
            },
    )

private fun timer(t: JSONObject) =
    FocusTimer(
        startedAt = t.getLong("startedAt"),
        pausedMs = t.getLong("pausedMs"),
        paused = if (t.isNull("paused")) null else t.getLong("paused"),
        tap = tap(t),
    )

private fun mix(m: JSONObject) =
    MixShare(
        kind(m.getString("kind")),
        m.getString("label"),
        m.getInt("minutes"),
        m.getString("text"),
    )

/** Rows keyed by their block; a block seen twice gets a counter so keys stay unique. */
private fun rows(list: JSONArray, prefix: String): List<OrderRow> {
    val seen = HashMap<String, Int>()
    return list.objects().map {
        val start = it.getLong("s")
        val kind = kind(it.getString("kind"))
        val base = "$prefix:$start:${it.getString("id").ifEmpty { kind.name }}"
        val n = seen.merge(base, 1, Int::plus)!!
        OrderRow(
            key = if (n == 1) base else "$base#$n",
            start = start,
            end = it.getLong("e"),
            time = it.getString("time"),
            length = it.getString("length"),
            kind = kind,
            title = it.getString("title"),
            icon = it.getString("icon"),
            state = state(it.getString("state")),
            stateLabel = it.getString("stateLabel"),
            now = it.getBoolean("now"),
            past = it.getBoolean("past"),
            sub = it.getString("sub"),
            chips = chips(it.getJSONArray("chips")),
            tap = tap(it),
        )
    }
}

private fun chips(list: JSONArray) =
    list.objects().map {
        Chip(ChipKind.valueOf(it.getString("kind").uppercase()), it.getString("text"))
    }

/** The tap an element carries, or null when its act is empty or absent. */
private fun tap(j: JSONObject): Tap? =
    j.optString("act", "").takeIf { it.isNotEmpty() }?.let { Tap(it, j.optString("id", "")) }

private fun kind(k: String) = BlockKind.valueOf(k.uppercase())

private fun state(s: String) =
    if (s.isEmpty()) SessionState.NONE else SessionState.valueOf(s.uppercase())

private fun requireSpan(a0: Float, a1: Float) =
    require(a0 >= 0f && a0 < a1 && a1 <= 360f) { "Arc $a0..$a1 is off the dial" }

private fun JSONObject.float(key: String) =
    getDouble(key).toFloat().also { require(it.isFinite()) { "$key is not a number" } }

internal fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

internal fun JSONArray.strings() = (0 until length()).map { getString(it) }

private const val HALF_SIDE = 180f
