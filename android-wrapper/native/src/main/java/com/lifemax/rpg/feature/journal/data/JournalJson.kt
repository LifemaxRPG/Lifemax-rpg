package com.lifemax.rpg.feature.journal.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.core.data.pageTokens
import com.lifemax.rpg.feature.journal.domain.AttrPill
import com.lifemax.rpg.feature.journal.domain.BlankDay
import com.lifemax.rpg.feature.journal.domain.CareNote
import com.lifemax.rpg.feature.journal.domain.CoachPrompt
import com.lifemax.rpg.feature.journal.domain.CounterWords
import com.lifemax.rpg.feature.journal.domain.DayEntry
import com.lifemax.rpg.feature.journal.domain.DayHead
import com.lifemax.rpg.feature.journal.domain.HeatCell
import com.lifemax.rpg.feature.journal.domain.HeroBackup
import com.lifemax.rpg.feature.journal.domain.HeroRhythm
import com.lifemax.rpg.feature.journal.domain.HeroStat
import com.lifemax.rpg.feature.journal.domain.Insight
import com.lifemax.rpg.feature.journal.domain.JournalChrome
import com.lifemax.rpg.feature.journal.domain.JournalEmpty
import com.lifemax.rpg.feature.journal.domain.JournalEntry
import com.lifemax.rpg.feature.journal.domain.JournalHero
import com.lifemax.rpg.feature.journal.domain.JournalList
import com.lifemax.rpg.feature.journal.domain.JournalListItem
import com.lifemax.rpg.feature.journal.domain.JournalLock
import com.lifemax.rpg.feature.journal.domain.JournalNotice
import com.lifemax.rpg.feature.journal.domain.JournalPane
import com.lifemax.rpg.feature.journal.domain.JournalTip
import com.lifemax.rpg.feature.journal.domain.LockCard
import com.lifemax.rpg.feature.journal.domain.LockStatus
import com.lifemax.rpg.feature.journal.domain.OpenJournal
import com.lifemax.rpg.feature.journal.domain.PlanNote
import com.lifemax.rpg.feature.journal.domain.ReflectBox
import com.lifemax.rpg.feature.journal.domain.ReviewBar
import com.lifemax.rpg.feature.journal.domain.ReviewChart
import com.lifemax.rpg.feature.journal.domain.ReviewCoach
import com.lifemax.rpg.feature.journal.domain.ReviewCounter
import com.lifemax.rpg.feature.journal.domain.ReviewEntry
import com.lifemax.rpg.feature.journal.domain.ReviewGridCell
import com.lifemax.rpg.feature.journal.domain.ReviewHead
import com.lifemax.rpg.feature.journal.domain.RhythmCell
import com.lifemax.rpg.feature.journal.domain.TimelineItem
import com.lifemax.rpg.feature.journal.domain.WelcomeCard
import com.lifemax.rpg.feature.journal.domain.WelcomeChip
import com.lifemax.rpg.feature.journal.domain.WorkoutNotes
import org.json.JSONArray
import org.json.JSONObject

/*
 * The strict decoder of `library.journal.projection.v1` (plan §3.3). A key that is missing, or of
 * the wrong kind, throws, so a frame the page did not mean is never drawn; a key the page sends as
 * null is read as none only where the projection allows it. The repository keeps the last good
 * state when this throws.
 */

/** A frame as decoded, before the repository decides whether to take it. */
internal sealed interface DecodedFrame {
    val chrome: JournalChrome
    val notice: JournalNotice?

    data class Open(
        override val chrome: JournalChrome,
        val journal: OpenJournal,
        override val notice: JournalNotice?,
    ) : DecodedFrame

    data class Withheld(
        override val chrome: JournalChrome,
        val status: LockStatus,
        override val notice: JournalNotice?,
    ) : DecodedFrame
}

/**
 * The keys only an open frame may carry. A withheld frame carrying any of them is malformed: the
 * page built it while its lock was closed and something private may be in it (A35 item 1), so it is
 * refused whole rather than drawn.
 */
internal val PRIVATE_KEYS = listOf("tip", "hero", "list", "selection", "empty", "entry")

internal fun decodeFrame(j: JSONObject): DecodedFrame {
    require(j.getInt("protocol") == 1) { "Unsupported journal protocol" }
    val session = j.getString("webSessionId")
    val sequence = j.getLong("sequence")
    require(session.isNotBlank() && sequence > 0) { "A frame without its session" }
    val panes =
        j.getJSONArray("panes").objects().map {
            JournalPane(it.getString("id"), it.getString("label"), it.getBoolean("current"))
        }
    require(panes.count { it.current } == 1) { "One pane is the current one" }
    val lock = j.getJSONObject("lock")
    val card = lock.getJSONObject("card")
    val chrome =
        JournalChrome(
            session,
            sequence,
            panes,
            JournalLock(
                lock.getBoolean("on"),
                LockCard(card.getString("title"), card.getString("body"), card.getString("unlock")),
            ),
            j.getJSONObject("tokens").pageTokens(),
            j.getBoolean("light"),
        )
    val notice = j.orNull("notice") { JournalNotice(it.getLong("id"), it.getString("text")) }
    if (j.getBoolean("withheld")) {
        PRIVATE_KEYS.forEach { require(!j.has(it)) { "A withheld frame carries $it" } }
        require(chrome.lock.on) { "A withheld frame with the lock off" }
        val status = j.getJSONObject("status")
        return DecodedFrame.Withheld(
            chrome,
            LockStatus(status.getBoolean("prompting"), status.getString("error")),
            notice,
        )
    }
    return DecodedFrame.Open(chrome, openJournal(j), notice)
}

private fun openJournal(j: JSONObject): OpenJournal {
    val edit = j.getString("edit")
    require(edit == "page" || edit == "native") { "Unknown edit mode $edit" }
    val list = list(j.getJSONObject("list"))
    val selection = j.stringOrNull("selection")
    val empty =
        j.orNull("empty") {
            JournalEmpty(it.getString("icon"), it.getString("title"), it.getString("body"))
        }
    val entry = j.orNull("entry", ::entry)
    if (empty != null)
        require(entry == null && list.items.isEmpty()) { "An empty Journal with days" }
    else require(entry != null && entry.key == selection) { "The entry is not the selected one" }
    require(selection == null || empty != null || selection in list.keys) {
        "The selection is not in the list"
    }
    return OpenJournal(
        j.getBoolean("motion"),
        edit,
        j.orNull("tip") {
            JournalTip(
                it.getString("icon"),
                it.getString("title"),
                it.getString("body"),
                it.getString("dismiss"),
            )
        },
        hero(j.getJSONObject("hero")),
        list,
        selection,
        empty,
        entry,
    )
}

private fun hero(h: JSONObject): JournalHero {
    val backup = h.getJSONObject("backup")
    val stats =
        h.getJSONArray("stats").objects().map {
            HeroStat(it.getString("value"), it.getString("label"), it.getLong("color"))
        }
    require(stats.size == 4) { "Expected the hero's four stats" }
    val r = h.getJSONObject("rhythm")
    val cells = r.getJSONArray("cells").objects().map(::rhythmCell)
    require(cells.size == 28) { "Expected 28 days of rhythm" }
    return JournalHero(
        h.getString("eyebrow"),
        h.getString("title"),
        HeroBackup(backup.getString("label"), backup.getString("spoken")),
        stats,
        HeroRhythm(r.getString("title"), r.getString("legend"), cells),
    )
}

private fun rhythmCell(c: JSONObject): RhythmCell {
    val off = c.getBoolean("off")
    val color = c.longOrNull("color")
    val alpha = c.floatOrNull("alpha")
    require(off || (color != null && alpha != null && alpha in 0f..1f)) {
        "A logged day without its colour"
    }
    return RhythmCell(color, alpha, off, c.getBoolean("today"), c.getString("label"))
}

private fun list(l: JSONObject): JournalList {
    val items =
        l.getJSONArray("items").objects().map {
            when (val kind = it.getString("kind")) {
                "day" ->
                    JournalListItem.Day(
                        it.getString("key"),
                        it.getString("weekday"),
                        it.getString("date"),
                        it.getInt("bar").also { b ->
                            require(b in 0..100) { "A bar out of range" }
                        },
                        it.getString("xp"),
                        it.getBoolean("pen"),
                        it.getLong("color"),
                        it.getBoolean("today"),
                        it.getString("spoken"),
                    )
                "quiet" -> JournalListItem.Quiet(it.getString("text"))
                "review" ->
                    JournalListItem.Review(
                        it.getString("key"),
                        it.getString("badge"),
                        it.getString("title"),
                        it.getString("sub"),
                    )
                else -> throw IllegalArgumentException("Unknown tile kind $kind")
            }
        }
    val list = JournalList(l.getString("rev"), items)
    require(list.keys.size == items.count { it !is JournalListItem.Quiet }) {
        "A tile appears twice"
    }
    return list
}

private fun entry(e: JSONObject): JournalEntry =
    when (val kind = e.getString("kind")) {
        "day" -> day(e)
        "review" -> review(e)
        else -> throw IllegalArgumentException("Unknown entry kind $kind")
    }

private fun day(e: JSONObject): DayEntry {
    val h = e.getJSONObject("head")
    val care = e.orNull("care") { CareNote(it.getString("lead"), it.getString("text")) }
    val welcome =
        e.orNull("welcome") { w ->
            WelcomeCard(
                w.getString("head"),
                w.getString("body"),
                w.getJSONArray("chips").objects().map {
                    WelcomeChip(it.getString("id"), it.getString("label"), it.getBoolean("done"))
                },
            )
        }
    val insight =
        e.orNull("insight") {
            Insight(
                it.getString("label"),
                it.getLong("color"),
                it.getString("text"),
                it.stringOrNull("pattern"),
            )
        }
    val reflect = reflect(e.getJSONObject("reflect"))
    // the care note stands alone (jcTop): no welcome, insight or question beside it
    if (care != null)
        require(welcome == null && insight == null && reflect.prompt == null) {
            "The care note shares the day"
        }
    return DayEntry(
        e.getString("key"),
        DayHead(
            h.getString("number"),
            h.getString("weekday"),
            h.getString("month"),
            h.getString("xp"),
            h.getString("xpLabel"),
        ),
        e.getJSONArray("meta").strings(),
        care,
        welcome,
        insight,
        reflect,
        e.getJSONArray("timeline").objects().map(::timelineItem),
        e.orNull("workoutNotes") {
            WorkoutNotes(
                it.getString("icon"),
                it.getString("title"),
                it.getJSONArray("notes").objects().map(::planNote),
            )
        },
        e.orNull("blank") {
            BlankDay(it.getString("icon"), it.getString("title"), it.getString("text"))
        },
    )
}

private fun reflect(r: JSONObject): ReflectBox {
    val c = r.getJSONObject("counter")
    val prompt =
        r.orNull("prompt") {
            CoachPrompt(
                it.getString("kicker"),
                it.getString("text"),
                it.getString("id"),
                it.getString("swap"),
            )
        }
    val label = r.stringOrNull("label")
    require((prompt == null) != (label == null)) { "A box has a question or a label" }
    val box =
        ReflectBox(
            prompt,
            label,
            r.getString("text"),
            r.getString("placeholder"),
            r.getInt("max"),
            r.getInt("min"),
            r.getBoolean("canPay"),
            r.getBoolean("awarded"),
            r.getDouble("meter").toFloat(),
            CounterWords(
                c.getString("awarded"),
                c.getString("ready"),
                c.getString("short"),
                c.getString("old"),
            ),
        )
    require(box.min > 0 && box.max >= box.min && box.text.length <= box.max) {
        "A box out of range"
    }
    require(box.meter in 0f..100f) { "A meter out of range" }
    return box
}

private fun timelineItem(t: JSONObject): TimelineItem =
    TimelineItem(
        t.getString("time"),
        t.getLong("color"),
        t.getString("icon"),
        t.getString("name"),
        t.orNull("attr") { AttrPill(it.getString("text"), it.getLong("color")) },
        t.stringOrNull("minutes"),
        t.stringOrNull("skill"),
        t.getString("xp"),
        t.stringOrNull("notes"),
        t.arrayOrNull("plan")?.objects()?.map(::planNote).orEmpty(),
    )

private fun planNote(p: JSONObject) =
    PlanNote(
        p.getString("label"),
        p.stringOrNull("weight").orEmpty(),
        p.stringOrNull("note").orEmpty(),
    )

private fun review(e: JSONObject): ReviewEntry {
    val h = e.getJSONObject("head")
    val coach = e.getJSONObject("coach")
    val counter = e.getJSONObject("counter")
    val r =
        ReviewEntry(
            e.getString("key"),
            ReviewHead(h.getString("badge"), h.getString("title"), h.getString("range")),
            chart(e.getJSONObject("chart")),
            e.getJSONArray("grid").objects().map {
                ReviewGridCell(it.getString("label"), it.getString("value"))
            },
            ReviewCoach(coach.getString("badge"), coach.getString("text")),
            e.getString("question"),
            e.getString("answer"),
            e.getString("placeholder"),
            e.getInt("max"),
            e.getInt("min"),
            e.getBoolean("paid"),
            ReviewCounter(counter.getString("paid"), counter.getString("short")),
        )
    require(r.min > 0 && r.max >= r.min && r.answer.length <= r.max) { "An answer out of range" }
    return r
}

private fun chart(c: JSONObject): ReviewChart =
    when (val kind = c.getString("kind")) {
        "bars" ->
            ReviewChart.Bars(
                c.getJSONArray("bars").objects().map {
                    ReviewBar(
                        it.getString("label"),
                        it.getString("value"),
                        it.getInt("height").also { v ->
                            require(v in 0..100) { "A bar out of range" }
                        },
                        it.getLong("color"),
                        it.getBoolean("today"),
                    )
                }
            )
        "heat" ->
            ReviewChart.Heat(
                c.getJSONArray("weekdays").strings().also {
                    require(it.size == 7) { "Seven weekdays" }
                },
                c.getInt("lead").also { require(it in 0..6) { "A lead out of range" } },
                c.getJSONArray("cells").objects().map {
                    HeatCell(
                        it.getString("day"),
                        it.getLong("color"),
                        it.getDouble("alpha").toFloat().also { a ->
                            require(a in 0f..1f) { "An opacity out of range" }
                        },
                        it.getBoolean("off"),
                        it.getString("label"),
                    )
                },
            )
        else -> throw IllegalArgumentException("Unknown chart kind $kind")
    }

/** The object at [key] decoded, or null where the page sends null; a missing key is malformed. */
private fun <T> JSONObject.orNull(key: String, decode: (JSONObject) -> T): T? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else decode(getJSONObject(key))
}

private fun JSONObject.arrayOrNull(key: String): JSONArray? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else getJSONArray(key)
}

/** A string, or null where the page sends null; a number or a missing key is malformed. */
private fun JSONObject.stringOrNull(key: String): String? {
    require(has(key)) { "Missing $key" }
    if (isNull(key)) return null
    val v = get(key)
    require(v is String) { "$key is not a string" }
    return v
}

private fun JSONObject.longOrNull(key: String): Long? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else getLong(key)
}

private fun JSONObject.floatOrNull(key: String): Float? {
    require(has(key)) { "Missing $key" }
    return if (isNull(key)) null else getDouble(key).toFloat()
}

/** The strings of an array; an element that is not a string is malformed. */
private fun JSONArray.strings(): List<String> =
    (0 until length()).map { i ->
        get(i).also { require(it is String) { "Not a string" } } as String
    }
