package com.lifemax.rpg.feature.overview.data

import com.lifemax.rpg.feature.lifedirector.data.DirectorGlanceJson
import com.lifemax.rpg.feature.overview.domain.AppItem
import com.lifemax.rpg.feature.overview.domain.AppsCard
import com.lifemax.rpg.feature.overview.domain.AttrsCard
import com.lifemax.rpg.feature.overview.domain.CatCard
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.DotKind
import com.lifemax.rpg.feature.overview.domain.Gem
import com.lifemax.rpg.feature.overview.domain.GemDot
import com.lifemax.rpg.feature.overview.domain.HealthCard
import com.lifemax.rpg.feature.overview.domain.HealthDay
import com.lifemax.rpg.feature.overview.domain.HeroArc
import com.lifemax.rpg.feature.overview.domain.HeroCard
import com.lifemax.rpg.feature.overview.domain.HeroFoot
import com.lifemax.rpg.feature.overview.domain.HeroRing
import com.lifemax.rpg.feature.overview.domain.HeroStreak
import com.lifemax.rpg.feature.overview.domain.HeroXp
import com.lifemax.rpg.feature.overview.domain.LegendItem
import com.lifemax.rpg.feature.overview.domain.LiveCat
import com.lifemax.rpg.feature.overview.domain.LiveCats
import com.lifemax.rpg.feature.overview.domain.LuckCard
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.MoveWarn
import com.lifemax.rpg.feature.overview.domain.NextMove
import com.lifemax.rpg.feature.overview.domain.NextMoves
import com.lifemax.rpg.feature.overview.domain.OverviewTiles
import com.lifemax.rpg.feature.overview.domain.RecentCard
import com.lifemax.rpg.feature.overview.domain.RecentItem
import com.lifemax.rpg.feature.overview.domain.RewardsCard
import com.lifemax.rpg.feature.overview.domain.StreakDot
import com.lifemax.rpg.feature.overview.domain.WeekBar
import com.lifemax.rpg.feature.overview.domain.WeekCard
import com.lifemax.rpg.feature.overview.domain.WeekPace
import com.lifemax.rpg.feature.overview.domain.WeekSeg
import com.lifemax.rpg.feature.overview.domain.WeekTip
import com.lifemax.rpg.feature.overview.domain.WeekTop
import org.json.JSONObject

/*
 * Each card of `tiles` (plan §3.3), read strictly. Each card's checks are what the page's own
 * data function guarantees (a ring that never overfills, seven week bars), so data that breaks
 * them is refused rather than drawn.
 */

internal fun tiles(t: JSONObject) =
    OverviewTiles(
        plan = t.orNull("plan") { DirectorGlanceJson.decode(this) },
        next = next(t.getJSONObject("next")),
        hero = hero(t.getJSONObject("hero")),
        rewards =
            t.getJSONObject("rewards").let {
                RewardsCard(it.str("title"), it.str("link"), it.list("chips").map(::chip))
            },
        apps = t.orNull("apps") { apps(this) },
        health = t.orNull("health") { health(this) },
        attrs = attrs(t.getJSONObject("attrs")),
        week = week(t.getJSONObject("week")),
        cat = t.orNull("cat") { cat(this) },
        luck = luck(t.getJSONObject("luck")),
        recent = recent(t.getJSONObject("recent")),
    )

private fun next(n: JSONObject): NextMoves {
    val moves =
        n.list("moves").map {
            NextMove(
                title = it.str("title"),
                sub = it.str("sub"),
                icon = it.str("icon"),
                color = it.color("color"),
                warn =
                    when (val w = it.str("warn")) {
                        "" -> MoveWarn.NONE
                        "warn" -> MoveWarn.WARN
                        "bad" -> MoveWarn.BAD
                        else -> throw IllegalArgumentException("Unknown warning $w")
                    },
                done = it.bool("done"),
                cta = it.str("cta"),
                pill = it.bool("pill"),
                go = go(it.getJSONObject("go")),
            )
        }
    require(moves.size <= 4) { "The page shows four moves at most" }
    return NextMoves(n.str("title"), n.str("note"), moves)
}

/** A move's go: exactly one of {view}, {attr} or {luck:true}. */
internal fun go(g: JSONObject): MoveGo {
    require(g.length() == 1) { "A move goes one place" }
    return when {
        g.has("view") -> MoveGo.View(g.str("view"))
        g.has("attr") -> MoveGo.Attr(g.str("attr"))
        g.has("luck") && g.bool("luck") -> MoveGo.Luck
        else -> throw IllegalArgumentException("Unknown move target")
    }
}

private fun hero(h: JSONObject): HeroCard {
    val r = h.getJSONObject("ring")
    val arcs = r.list("arcs").map { HeroArc(it.color("color"), it.num("offset"), it.num("length")) }
    // each part starts on the ring; a small last part can end just past its end, because the page
    // draws every part at least 0.5 long (17-runtime.js, ovHeroData), so only the start is held
    require(
        arcs.all { it.length > 0f && it.length <= 100f && it.offset <= 0f && -it.offset < 100f }
    ) {
        "An arc is off the ring"
    }
    val xp = h.getJSONObject("xp")
    val streak = h.getJSONObject("streak")
    val dots = streak.list("dots").map { StreakDot(it.bool("on"), it.bool("now")) }
    require(dots.size == 7) { "The streak shows seven days" }
    return HeroCard(
        ring =
            HeroRing(
                r.str("label"),
                r.int("today"),
                r.bool("over"),
                r.str("of"),
                arcs,
                r.int("r"),
                r.int("stroke"),
            ),
        avatar = h.strOrNull("avatar"),
        initial = h.str("initial"),
        level = h.int("level"),
        title = h.str("title"),
        className = h.str("className"),
        xp = HeroXp(xp.str("text"), xp.str("next"), xp.num("pct")),
        streak = HeroStreak(streak.int("days"), streak.str("label"), dots),
        foot =
            h.list("foot").map {
                HeroFoot(it.str("label"), it.str("value"), it.orNull("delta") { delta(this) })
            },
    )
}

private fun apps(a: JSONObject) =
    AppsCard(
        a.str("title"),
        a.str("link"),
        a.list("items").map {
            AppItem(it.str("pkg"), it.str("label"), it.str("a11y"), it.strOrNull("image"))
        },
        a.str("add"),
        a.str("hint"),
    )

private fun health(h: JSONObject): HealthCard =
    if (!h.bool("on")) HealthCard.Off(h.str("title"), h.str("text"), h.str("button"))
    else
        HealthCard.On(
            title = h.str("title"),
            meta = h.str("meta"),
            steps = h.str("steps"),
            unit = h.str("unit"),
            sub = h.str("sub"),
            pct = h.int("pct").also { require(it in 0..100) },
            days =
                h.list("days").map {
                    HealthDay(it.str("label"), it.num("frac"), it.bool("now"), it.str("a11y"))
                },
            chips = h.list("chips").map(::chip),
            more = h.str("more"),
        )

private fun dotKind(k: String) =
    when (k) {
        "today" -> DotKind.TODAY
        "ok" -> DotKind.OK
        "soon" -> DotKind.SOON
        "decay" -> DotKind.DECAY
        "none" -> DotKind.NONE
        "off" -> DotKind.OFF
        "zero" -> DotKind.ZERO
        else -> throw IllegalArgumentException("Unknown dot $k")
    }

private fun attrs(a: JSONObject) =
    AttrsCard(
        a.str("title"),
        a.str("link"),
        a.list("gems").map {
            val dot = it.getJSONObject("dot")
            Gem(
                id = it.str("id"),
                short = it.str("short"),
                icon = it.str("icon"),
                color = it.color("color"),
                focus = it.bool("focus"),
                dot = GemDot(dotKind(dot.str("kind")), dot.str("text")),
                level = it.str("level"),
                week = it.str("week"),
                pct = it.num("pct"),
            )
        },
        a.list("legend").map { LegendItem(dotKind(it.str("kind")), it.str("label")) },
    )

private fun week(w: JSONObject): WeekCard {
    val pace = w.getJSONObject("pace")
    val bars =
        w.list("bars").map {
            val tip = it.getJSONObject("tip")
            WeekBar(
                date = it.str("date"),
                label = it.str("label"),
                today = it.bool("today"),
                xp = it.int("xp"),
                segs = it.list("segs").map { s -> WeekSeg(s.color("color"), s.num("frac")) },
                a11y = it.str("a11y"),
                tip = WeekTip(tip.str("label"), tip.str("rest")),
            )
        }
    require(bars.size == 7) { "The week is seven days" }
    val selected = w.int("selected")
    require(selected in bars.indices) { "The picked day is not in the week" }
    return WeekCard(
        title = w.str("title"),
        total = w.str("total"),
        delta = w.orNull("delta") { delta(this) },
        pace = WeekPace(pace.str("label"), pace.num("frac")),
        bars = bars,
        top = w.list("top").map { WeekTop(it.color("color"), it.str("text")) },
        empty = w.str("empty"),
        selected = selected,
    )
}

private fun cat(c: JSONObject): CatCard {
    val mode =
        when (val m = c.str("mode")) {
            "alive" -> CatMode.ALIVE
            "gone" -> CatMode.GONE
            else -> throw IllegalArgumentException("Unknown cat card $m")
        }
    // a living cat has its hearts; a cat in memory has none, and no portrait
    require(c.has("hearts")) { "Missing hearts" }
    val hearts = if (mode == CatMode.ALIVE) c.int("hearts") else null
    if (hearts == null) require(c.isNull("hearts")) { "A cat in memory has no hearts" }
    else require(hearts in 0..5) { "A cat has five hearts" }
    val portrait = c.strOrNull("portrait")
    require(portrait == null || mode == CatMode.ALIVE) { "Only a living cat has a portrait" }
    require(c.has("live")) { "Missing live" }
    val live = if (c.isNull("live")) null else liveCats(c.getJSONObject("live"))
    require(live == null || mode == CatMode.ALIVE) { "Only a living cat is drawn live" }
    return CatCard(mode, c.str("eyebrow"), c.str("name"), hearts, portrait, live)
}

/**
 * The live cats: either `overview.cat.live.v1`'s one cat, exactly {coat, stage, weak}, or
 * `overview.cat.live.v2`'s {cats: [one or two of {coat, stage, weak, name?}]}. Anything else is a
 * page this build is not for.
 */
internal fun liveCats(l: JSONObject): LiveCats {
    if (!l.has("cats")) return LiveCats(listOf(liveCat(l, named = false)))
    val extra = l.keys().asSequence().filter { it != "cats" }.toList()
    require(extra.isEmpty()) { "Unknown live keys $extra" }
    val list = l.getJSONArray("cats")
    require(list.length() in 1..LiveCats.MAX) { "A house has one or two live cats" }
    return LiveCats(List(list.length()) { liveCat(list.getJSONObject(it), named = true) })
}

/** One live cat's look; the cats of the list shape ([named]) may carry a name. */
internal fun liveCat(l: JSONObject, named: Boolean = false): LiveCat {
    val allowed = if (named) LIVE_KEYS + "name" else LIVE_KEYS
    val extra = l.keys().asSequence().filter { it !in allowed }.toList()
    require(extra.isEmpty()) { "Unknown live cat keys $extra" }
    val coat = l.str("coat")
    require(coat.isNotEmpty() && coat.length <= LIVE_COAT_MAX) { "A coat is a short key" }
    val stage = l.int("stage")
    require(stage in LiveCat.ADULT..LiveCat.LAST_STAGE) { "A growth stage is -1 to 5" }
    val name = if (l.has("name")) l.str("name") else null
    require(name == null || name.length in 1..LIVE_NAME_MAX) { "A cat's name is short" }
    return LiveCat(coat, stage, l.bool("weak"), name)
}

private val LIVE_KEYS = setOf("coat", "stage", "weak")
private const val LIVE_COAT_MAX = 64
private const val LIVE_NAME_MAX = 40

private fun luck(l: JSONObject): LuckCard {
    val rolled = l.bool("rolled")
    require(l.has("color")) { "Missing color" }
    val color = if (l.isNull("color")) null else l.color("color")
    require(rolled == (color != null)) { "Only a roll has its tier's colour" }
    return LuckCard(l.str("eyebrow"), rolled, l.str("score"), color, l.str("text"), l.str("button"))
}

private fun recent(r: JSONObject): RecentCard {
    val empty = r.getJSONObject("empty")
    return RecentCard(
        r.str("title"),
        r.str("link"),
        r.list("items").map {
            RecentItem(
                it.str("name"),
                it.str("icon"),
                it.color("color"),
                it.str("meta"),
                it.str("xp"),
            )
        },
        empty.str("icon"),
        empty.str("text"),
    )
}
