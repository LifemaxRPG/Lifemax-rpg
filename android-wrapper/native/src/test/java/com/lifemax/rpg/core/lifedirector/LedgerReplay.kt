package com.lifemax.rpg.core.lifedirector

import com.lifemax.rpg.core.lifedirector.KernelGolden.threw
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The ledger golden cases, `ld-ledger-golden.json.gz` (recorded from the page in Chromium; format
 * in `tools/gen_ld_ledger_golden.js`), read one line at a time, and the adapters that run each page
 * function natively and write its answer as the page's JSON.
 */
object LedgerReplay {
    const val FILE = "ld-ledger-golden.json.gz"

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

    /**
     * What each page function name runs natively ([a] may be shorter than the call's arguments).
     */
    val ADAPTERS: Map<String, (List<JsonValue>) -> JsonValue> =
        mapOf(
            "emptyLedger" to { a -> CompletionLedger.empty(a.getOrNull(0)) },
            "completionEventId" to
                { a ->
                    JsonString(
                        CompletionLedger.eventId(
                            a.getOrNull(0),
                            a.getOrNull(1),
                            a.getOrNull(2),
                            a.getOrNull(3),
                        )
                    )
                },
            "canTransition" to
                { a ->
                    JsonBool.of(MissConsequences.canTransition(a.getOrNull(0), a.getOrNull(1)))
                },
            "recordCompletion" to { a -> CompletionLedger.record(a.getOrNull(0)).json() },
            "revokeEvent" to
                { a ->
                    val (ledger, revoked) =
                        CompletionLedger.revoke(
                            a.getOrNull(0),
                            a.getOrNull(1),
                            a.getOrNull(2),
                            a.getOrNull(3),
                        )
                    ScheduleJson.obj {
                        this["ledger"] = ledger
                        this["revoked"] = JsonBool.of(revoked)
                    }
                },
            "ledgerHas" to
                { a ->
                    JsonBool.of(CompletionLedger.has(a.getOrNull(0), a.getOrNull(1)))
                },
            "compactLedger" to { a -> CompletionLedger.compact(a.getOrNull(0), a.getOrNull(1))!! },
            "reconcileQuestProgress" to
                { a ->
                    QuestProgress.reconcile(
                            a.getOrNull(0),
                            a.getOrNull(1),
                            a.getOrNull(2),
                            a.getOrNull(3),
                            a.getOrNull(4),
                        )
                        .json()
                },
            "detectMisses" to
                { a ->
                    JsonArray(
                        MissConsequences.detect(a.getOrNull(0), a.getOrNull(1), a.getOrNull(2))
                    )
                },
            "applyMissConsequence" to
                { a ->
                    MissConsequences.apply(
                            a.getOrNull(0),
                            a.getOrNull(1),
                            a.getOrNull(2),
                            a.getOrNull(3),
                            a.getOrNull(4),
                        )
                        .json()
                },
            "stream" to { a -> LedgerStream.run(a[0] as JsonObject) },
        )

    /** Thrown when native refuses a shape the page accepts (a deviation, not an answer). */
    class Refused(val reason: String) : RuntimeException(reason)

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
        } catch (e: LedgerRefusal) {
            throw Refused(e.message ?: "refused")
        }

    /** Null when [case] gives the page's answer, else where the answers first differ. */
    fun difference(case: KernelGolden.Case): String? {
        val got =
            try {
                run(case)
            } catch (e: Refused) {
                return "${case.id}: refused (${e.reason})"
            } catch (e: RuntimeException) {
                return "${case.id}: threw ${e::class.simpleName}: ${e.message} at ${e.stackTrace.take(3).joinToString(" < ")}"
            }
        return if (got == case.out) null
        else "${case.id}: " + PlacementReplay.firstDifference(case.out, got, "out")
    }
}
