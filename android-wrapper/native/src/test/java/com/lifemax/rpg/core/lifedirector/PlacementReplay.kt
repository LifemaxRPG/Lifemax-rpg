package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.bool
import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The placement golden cases, `ld-placement-golden.json.gz` (recorded from the page in Chromium;
 * format in `tools/gen_ld_placement_golden.js`), read one line at a time (the file is large: no
 * case is kept once replayed), and the adapters that run each page function name on the native
 * placement code and write its answer as the page's JSON ([PlacementJson]).
 */
object PlacementReplay {
    const val FILE = "ld-placement-golden.json.gz"

    private fun file(): File {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
            .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
    }

    /** Calls [visit] with every case, in the file's order. */
    fun forEachCase(visit: (KernelGolden.Case) -> Unit) {
        GZIPInputStream(file().inputStream()).bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                if (!line.startsWith("{\"id\"")) continue
                val read = JsonReader.read(line.removeSuffix(","))
                check(read is JsonRead.Parsed) {
                    "a case of $FILE does not parse: ${line.take(80)}"
                }
                val c = read.value as JsonObject
                visit(
                    KernelGolden.Case(
                        KernelGolden.text(c["id"]),
                        KernelGolden.text(c["family"]),
                        KernelGolden.text(c["rule"]),
                        KernelGolden.text(c["fn"]),
                        (c["args"] as JsonArray).items,
                        KernelGolden.decode(c["out"]!!),
                    )
                )
            }
        }
    }

    /** `dependsOn.includes(taskId)` on a stored task, as anchorsDependents reads it. */
    private fun includes(list: JsonValue?, taskId: JsonValue?): Boolean =
        when {
            !PageValues.truthy(list) -> false
            list is JsonArray -> list.items.any { taskId != null && PageValues.same(it, taskId) }
            list is JsonString -> list.value.contains(PageValues.text(taskId))
            else -> throw PageTypeError("includes is not a function")
        }

    private fun anchors(a: List<JsonValue>): JsonValue {
        val taskId = a[1].takeIf { it != JsonNull }
        return bool(
            PlanConstruction.anchors(
                (a[0] as JsonArray).items,
                { PageValues.truthy(PageValues.member(it, "isPlaced")) },
                { PageValues.truthy(PageValues.member(it, "coveredBy")) },
                { includes(PageValues.member(it, "dependsOn"), taskId) },
            )
        )
    }

    /** What each page function name runs natively. */
    val ADAPTERS: Map<String, (List<JsonValue>) -> JsonValue> =
        mapOf(
            "prepare" to { a -> PlacementJson.prepared(PlanPreparation.prepare(a[0])) },
            "anchors" to ::anchors,
            "place" to
                { a ->
                    PlacementJson.placed(
                        PlanConstruction.construct(a[0]),
                        (a[1] as JsonString).value,
                    )
                },
        )

    /** What the native code gives for [case], written as the page writes its answer. */
    fun run(case: KernelGolden.Case): JsonValue =
        try {
            ADAPTERS.getValue(case.fn)(case.args)
        } catch (e: PageTypeError) {
            threw("TypeError")
        } catch (e: PageRangeError) {
            threw("RangeError")
        } catch (e: EngineRefusal) {
            threw("Error")
        }

    /** Null when [case] gives the page's answer, else where the answers first differ. */
    fun difference(case: KernelGolden.Case): String? {
        val got =
            try {
                run(case)
            } catch (e: RuntimeException) {
                return "${case.id}: threw ${e::class.simpleName}: ${e.message}"
            }
        return if (got == case.out) null else "${case.id}: " + firstDifference(case.out, got, "out")
    }

    /** The path of the first member or item where [a] (the page's) and [b] (native) differ. */
    fun firstDifference(a: JsonValue, b: JsonValue, at: String): String =
        when {
            a is JsonObject && b is JsonObject ->
                (a.members.keys + b.members.keys).firstNotNullOfOrNull { k ->
                    val x = a[k]
                    val y = b[k]
                    if (x == y) null
                    else if (x == null || y == null)
                        "$at.$k (page ${x?.let(KernelGolden::show)}, native ${y?.let(KernelGolden::show)})"
                    else firstDifference(x, y, "$at.$k")
                } ?: at
            a is JsonArray && b is JsonArray ->
                (0 until maxOf(a.items.size, b.items.size)).firstNotNullOfOrNull { i ->
                    val x = a.items.getOrNull(i)
                    val y = b.items.getOrNull(i)
                    if (x == y) null
                    else if (x == null || y == null)
                        "$at[$i] (lengths ${a.items.size} ${b.items.size})"
                    else firstDifference(x, y, "$at[$i]")
                } ?: at
            else -> "$at: page ${KernelGolden.show(a)} native ${KernelGolden.show(b)}"
        }
}
