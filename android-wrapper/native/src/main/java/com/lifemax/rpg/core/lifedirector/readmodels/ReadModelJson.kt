package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.lifedirector.GridBlock
import com.lifemax.rpg.core.lifedirector.PlanDay
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue

/**
 * The read models written as the page's JSON: the members the page's objects have, with the page's
 * names, a member the page leaves undefined left out. [week] is `nativeWeek()` exactly as the
 * projection's `week` section carries it, so a shadow check can compare it with the page's own
 * section member for member; the others are the page functions' answers, with a session named by
 * where it is stored ([StoredSession.ref]) instead of the stored object, and `weekModel` without
 * the rows' day and blocks (they are [days] and [blocks] of the same moment).
 */
object ReadModelJson {
    private fun obj(vararg members: Pair<String, JsonValue?>): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonValue>().apply {
                for ((k, v) in members) if (v != null) put(k, v)
            }
        )

    private fun num(v: Double): JsonValue = JsonNumber(v)

    private fun num(v: Int): JsonValue = JsonNumber(v.toDouble())

    private fun str(v: String): JsonValue = JsonString(v)

    private fun bool(v: Boolean): JsonValue = JsonBool.of(v)

    private fun <T> list(items: List<T>, f: (T) -> JsonValue): JsonArray = JsonArray(items.map(f))

    /** A day of `buildHorizon` (RM-3). */
    fun day(d: PlanDay): JsonValue =
        obj(
            "index" to num(d.index),
            "ymd" to
                obj(
                    "y" to num(d.date.year),
                    "mo" to num(d.date.month),
                    "d" to num(d.date.day),
                    "dow" to num(d.date.weekday),
                ),
            "key" to str(d.key),
            "dow" to num(d.date.weekday),
            "startEpoch" to num(d.startEpoch),
            "endEpoch" to num(d.endEpoch),
            "slots" to num(d.slots),
        )

    fun days(list: List<PlanDay>): JsonValue = list(list, ::day)

    /** A preview block of `unplannedSegments` (RM-5): the engine's block, cut to the day. */
    fun segment(b: GridBlock): JsonValue {
        val m = b.meta
        return obj(
            "code" to num(b.code),
            "startEpoch" to num(b.startEpoch),
            "endEpoch" to num(b.endEpoch),
            "kind" to m?.kind,
            "title" to m?.title,
            "refId" to m?.refId?.let(::str),
            "load" to m?.load?.let(::num),
            "restorative" to m?.restorative?.let(::bool),
            "protected" to m?.protected?.let(::bool),
        )
    }

    fun segments(list: List<GridBlock>): JsonValue = list(list, ::segment)

    /** A block of `dayBlocks` or `liveTimeline` (RM-6 to RM-11). */
    fun block(b: DayBlock): JsonValue =
        obj(
            "startEpoch" to num(b.startEpoch),
            "endEpoch" to num(b.endEpoch),
            "code" to b.code,
            "kind" to b.kind,
            "title" to b.title,
            "refId" to b.refId,
            "mealStartEpoch" to b.mealStartEpoch?.let(::num),
            "clipped" to b.clipped?.let(::bool),
            "inst" to b.session?.let { str(it.ref) },
        )

    fun blocks(list: List<DayBlock>): JsonValue = list(list, ::block)

    /** `nowState(now)` (RM-12, RM-13). */
    fun now(n: NowState): JsonValue {
        fun b(x: DayBlock?): JsonValue = x?.let(::block) ?: JsonNull
        return obj(
            "cur" to b(n.current),
            "next" to b(n.next),
            "nextTask" to b(n.nextSession),
            "kick" to (n.kick?.let { obj("kind" to str(it.kind), "sec" to num(it.seconds)) } ?: JsonNull),
            "soon" to
                (n.soon?.let { obj("kind" to str(it.kind), "b" to b(it.block), "sec" to num(it.seconds)) }
                    ?: JsonNull),
            "alert" to (n.alert?.let(::str) ?: JsonNull),
            "isSes" to bool(n.isSession),
            "running" to bool(n.running),
        )
    }

    /** `weekModel()` without the rows' day and blocks (RM-15 to RM-17). */
    fun weekModel(w: WeekModel): JsonValue =
        obj(
            "rows" to
                list(w.rows) {
                    obj(
                        "k" to num(it.index),
                        "n" to num(it.sessions),
                        "done" to num(it.done),
                        "m" to num(it.minutes),
                        "name" to str(it.name),
                        "aria" to str(it.aria),
                    )
                },
            "stats" to list(w.stats) { JsonArray(listOf(str(it.value), str(it.label))) },
            "attrs" to
                list(w.attributes) {
                    JsonArray(listOf(str(it.attr), obj("plan" to num(it.plan), "done" to num(it.done))))
                },
            "maxA" to num(w.maxAttribute),
            "fitText" to str(w.fitText),
        )

    /** `nativeWeek()`, the projection's `week` section (RM-19); null without a plan. */
    fun week(p: WeekProjection?): JsonValue {
        if (p == null) return JsonNull
        return obj(
            "stats" to list(p.stats) { obj("value" to str(it.value), "label" to str(it.label)) },
            "ticks" to list(p.ticks, ::str),
            "days" to
                list(p.days) { r ->
                    obj(
                        "index" to num(r.index),
                        "label" to str(r.label),
                        "date" to num(r.date),
                        "today" to bool(r.today),
                        "aria" to str(r.aria),
                        "count" to str(r.count),
                        "done" to str(r.done),
                        "minutes" to str(r.minutes),
                        "segs" to
                            list(r.segments) {
                                obj(
                                    "kind" to str(it.kind),
                                    "state" to str(it.state),
                                    "left" to num(it.left),
                                    "width" to num(it.width),
                                )
                            },
                    )
                },
            "legend" to list(p.legend) { obj("kind" to str(it.kind), "label" to str(it.label)) },
            "yield" to
                (p.yield?.let { y ->
                    obj(
                        "title" to str(y.title),
                        "note" to str(y.note),
                        "rows" to
                            list(y.rows) {
                                obj(
                                    "label" to str(it.label),
                                    "color" to num(it.colour),
                                    "done" to num(it.done),
                                    "plan" to num(it.plan),
                                    "total" to str(it.total),
                                )
                            },
                    )
                } ?: JsonNull),
            "fit" to
                obj(
                    "title" to str(p.fit.title),
                    "text" to str(p.fit.text),
                    "allFit" to str(p.fit.allFit),
                    "unscheduled" to
                        (p.fit.unscheduled?.let { u ->
                            obj(
                                "summary" to str(u.summary),
                                "groups" to
                                    list(u.groups) {
                                        obj(
                                            "title" to str(it.title),
                                            "tier" to str(it.tier),
                                            "text" to it.text,
                                        )
                                    },
                            )
                        } ?: JsonNull),
                ),
        )
    }
}
