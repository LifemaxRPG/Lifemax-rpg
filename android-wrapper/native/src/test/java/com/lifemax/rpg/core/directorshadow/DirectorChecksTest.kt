package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.shadow.ShadowDecode
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowRedactor
import com.lifemax.rpg.core.shadow.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SH-9 to SH-18, SH-32, SH-33 on the real page's messages of the lived-in day: each check agrees
 * with the page as recorded, and notices each kind of change to what the page said.
 */
class DirectorChecksTest {
    private val checks = DirectorChecks(ShadowRedactor(ByteArray(ShadowLimits.SALT_BYTES) { 1 }))

    private fun env(text: String) = (DirectorShadowJson.envelope(text) as ShadowDecode.Ok).value

    /** The state texts after each save and read, and the read and engine facts, from the stream. */
    private val day: Triple<List<String>, List<Pair<String, ReadFacts>>, List<Pair<String, EngineFacts>>> by lazy {
        val m = TextMirror()
        val parts = ArrayList<String>()
        val saves = ArrayList<String>()
        val reads = ArrayList<Pair<String, ReadFacts>>()
        val engines = ArrayList<Pair<String, EngineFacts>>()
        for ((method, text) in DirectorShadowStream.messages) {
            if (method == "base") {
                val p = (DirectorShadowJson.basePart(text) as ShadowDecode.Ok).value
                parts += p.text
                if (p.part == p.parts - 1) {
                    m.apply((DirectorShadowJson.baseOps(parts.joinToString("")) as ShadowDecode.Ok).value, p.seq, true)
                    parts.clear()
                }
            }
            if (method != "observe") continue
            val e = env(text)
            when (e.kind) {
                DirectorShadowJson.SAVE, DirectorShadowJson.READ -> {
                    m.apply(e.ops, e.seq, false)
                    if (e.kind == DirectorShadowJson.SAVE) saves += m.stitch()!! else reads += m.stitch()!! to e.read!!
                }
                else -> engines += e.kind to e.engine!!
            }
        }
        Triple(saves, reads, engines)
    }

    private fun parse(text: String): JsonValue = (JsonReader.read(text) as JsonRead.Parsed).value

    private fun paths(v: Verdict): List<String> = (v as Verdict.Differed).found.map { it.shown }

    @Test
    fun everySaveRoundTrips() {
        for (t in day.first) assertEquals(Verdict.Same, checks.save(t).verdict)
    }

    @Test
    fun aTextNativeWouldWriteOtherwiseIsADifference() {
        val t = day.first.last().replaceFirst("\"schemaVersion\":1", "\"schemaVersion\":1.0")
        val v = checks.save(t).verdict
        assertEquals(listOf("store.text.schemaVersion"), paths(v))
        assertTrue((v as Verdict.Differed).unexplained.isNotEmpty())
    }

    @Test
    fun aTextTheStrictReaderRefusesIsAFindingNotADifference() {
        val t = day.first.last().replaceFirst("\"labQuests\":[]", "\"labQuests\":{}")
        val r = checks.save(t)
        assertEquals(Verdict.Same, r.verdict)
        assertEquals(listOf("strict.wrongType.labQuests", "encode.wrongType.labQuests"), r.notes)
    }

    @Test
    fun aTextDeeperThanNativeReadsIsAllowed() {
        val deep = "[".repeat(600) + "]".repeat(600)
        val t = day.first.last().replaceFirst("\"labQuests\":[]", "\"labQuests\":$deep")
        val v = checks.save(t).verdict as Verdict.Differed
        assertTrue(v.explained)
        assertEquals(setOf(DirectorChecks.DA_DEEP), v.allowed)
    }

    @Test
    fun everyReadAgrees() {
        for ((text, facts) in day.second) assertEquals(Verdict.Same, checks.read(parse(text), facts).verdict)
    }

    private fun withOut(f: ReadFacts, section: String, value: JsonValue) =
        f.copy(out = JsonObject(LinkedHashMap(f.out.members).also { it[section] = value }))

    @Test
    fun aReadModelThatSaysSomethingElseIsNamedBySection() {
        val (text, f) = day.second.last()
        val st = parse(text)
        val week = f.out.members["week"] as JsonObject
        val v = checks.read(st, withOut(f, "week", JsonObject(LinkedHashMap(week.members).also { it["fitText"] = JsonString("other") }))).verdict
        assertEquals(listOf("read.week.fitText"), paths(v))
        val days = f.out.members["days"] as JsonArray
        val d0 = days.items[0] as JsonObject
        val moved = JsonArray(listOf(JsonObject(LinkedHashMap(d0.members).also { it["startEpoch"] = JsonNumber((d0.members["startEpoch"] as JsonNumber).value + 3600) })) + days.items.drop(1))
        assertEquals(listOf("read.days[0].startEpoch"), paths(checks.read(st, withOut(f, "days", moved)).verdict))
        // a preview the page asked differently: native's own question is answered as failed, so its XP differs too
        val xp = f.out.members["xp"] as JsonArray
        val v2 = checks.read(st, f.copy(xp = xp.items.drop(1))).verdict
        assertTrue(paths(v2).toString(), paths(v2).any { it.startsWith("read.xp") })
    }

    @Test
    fun aZoneNativeOffsetsOtherwiseIsTzdata() {
        val (text, f) = day.second.last()
        assertEquals(Verdict.Skipped("tzdata"), checks.read(parse(text), f.copy(planOffset = 3600)).verdict)
        assertEquals(Verdict.Skipped("tzdata"), checks.read(parse(text), f.copy(planZone = "Not/AZone", planOffset = 0)).verdict)
    }

    @Test
    fun everyPlanAndRepairAgrees() {
        assertTrue(day.third.map { it.first }.toSet().containsAll(listOf(DirectorShadowJson.PLAN, DirectorShadowJson.REPAIR)))
        for ((kind, facts) in day.third) assertEquals(kind, Verdict.Same, checks.engine(kind, facts).verdict)
    }

    private fun startMoved(out: JsonValue, by: (Double) -> Double): JsonValue {
        val o = out as JsonObject
        val list = o.members["scheduledInstances"] as JsonArray
        val first = list.items[0] as JsonObject
        val moved = JsonObject(LinkedHashMap(first.members).also { it["startEpoch"] = JsonNumber(by((first.members["startEpoch"] as JsonNumber).value)) })
        return JsonObject(LinkedHashMap(o.members).also { it["scheduledInstances"] = JsonArray(listOf(moved) + list.items.drop(1)) })
    }

    @Test
    fun aPlanThatSaysSomethingElseIsADifferenceButNotItsTime() {
        val (kind, f) = day.third.first { it.first == DirectorShadowJson.PLAN }
        assertEquals(listOf("plan.scheduledInstances[0].startEpoch"), paths(checks.engine(kind, f.copy(output = startMoved(f.output) { it + 900 })).verdict))
        val bit = checks.engine(kind, f.copy(output = startMoved(f.output) { Math.nextUp(it) })).verdict as Verdict.Differed
        assertTrue(bit.explained)
        assertEquals(setOf(DirectorChecks.DA_FLOAT), bit.allowed)
        val o = f.output as JsonObject
        val timed = JsonObject(LinkedHashMap(o.members).also { it["computeTimeMs"] = JsonNumber(12345.0) })
        assertEquals(Verdict.Same, checks.engine(kind, f.copy(output = timed)).verdict)
    }

    @Test
    fun aRepairThatSaysSomethingElseIsADifference() {
        val (kind, f) = day.third.first { it.first == DirectorShadowJson.REPAIR }
        val o = f.output as JsonObject
        val out = JsonObject(LinkedHashMap(o.members).also { it["updatedSchedule"] = startMoved(o.members["updatedSchedule"]!!) { x -> x + 900 } })
        assertEquals(listOf("repair.updatedSchedule.scheduledInstances[0].startEpoch"), paths(checks.engine(kind, f.copy(output = out)).verdict))
    }
}
