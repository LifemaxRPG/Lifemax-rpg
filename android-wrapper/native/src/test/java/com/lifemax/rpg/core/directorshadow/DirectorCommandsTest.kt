package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.lifedirector.host.TakeBackState
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter
import com.lifemax.rpg.core.shadow.ShadowDecode
import com.lifemax.rpg.core.shadow.ShadowLimits
import com.lifemax.rpg.core.shadow.ShadowRedactor
import com.lifemax.rpg.core.shadow.ShadowRefusal
import com.lifemax.rpg.core.shadow.Verdict
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SH-36 to SH-49 on the real page's commands and ticks of the lived-in day (Today's and Do's commands as a native screen sends
 * them, a completion by voice, the tick on screen and the Overview glance's three steps, against the real LifeMax host): each
 * replays natively to the page's reply, host calls, memory and state, and each kind of change to what the page said is noticed.
 */
class DirectorCommandsTest {
    private fun check(r: Ran, f: CommandFacts = r.facts, after: String = r.after, t1: Long = r.t1): CommandResult? =
        checks.command(parse(r.before), parse(after), f, t1) { known[it] }

    private fun paths(v: Verdict): List<String> = (v as? Verdict.Differed)?.found?.map { it.shown } ?: listOf(v.toString())

    private fun first(op: String, name: String = "", ok: Boolean? = null): Ran =
        ran.first { it.facts.op == op && it.facts.name == name && (ok == null || ((it.facts.reply as? JsonObject)?.members?.get("ok") == JsonBool.of(ok))) }

    private fun obj(v: JsonValue?) = v as JsonObject

    private fun with(o: JsonObject, k: String, v: JsonValue) = JsonObject(LinkedHashMap(o.members).also { it[k] = v })

    @Test
    fun everyCommandAndTickOfTheDayReplaysToThePage() {
        assertTrue(ran.size > 30)
        val k = HashMap<String, TakeBackState>()
        for (r in ran) {
            val res = checks.command(parse(r.before), parse(r.after), r.facts, r.t1) { k[it] }
            assertEquals("${r.facts.op}.${r.facts.name}", Verdict.Same, res.verdict)
            res.learned?.let { k[it.first] = it.second }
        }
        val names = ran.map { it.facts.op + "." + it.facts.name }.toSet()
        for (n in listOf("today.pin", "today.skip", "today.unskip", "today.done", "today.logToday", "today.timer", "today.disrupt", "today.energy", "today.replan",
            "do.goalPriority", "do.restDay", "do.pref", "do.goalAdd", "do.exportBackup", "finish.", "tick.", "syncHost.", "processMisses.", "maybeRollover."))
            assertTrue("$n in $names", n in names)
        assertTrue(ran.any { it.facts.calls.any { c -> c.fn == "awardActivity" } } && ran.any { it.facts.calls.any { c -> c.fn == "store.exportJson" } })
    }

    @Test
    fun aReplyThatSaysSomethingElseIsNamed() {
        val r = first("today", "done", ok = true)
        val f = r.facts.copy(reply = with(obj(r.facts.reply), "message", JsonString("other")))
        assertEquals(listOf("today.done.reply.message"), paths(check(r, f)!!.verdict))
    }

    @Test
    fun aHostCallMissingAddedOrOtherIsNamed() {
        val r = first("today", "done", ok = true)
        assertEquals(listOf("today.done.calls[#]"), paths(check(r, r.facts.copy(calls = r.facts.calls.dropLast(1)))!!.verdict).take(1))
        val extra = r.facts.calls + r.facts.calls.last()
        assertEquals(listOf("today.done.calls[#]"), paths(check(r, r.facts.copy(calls = extra))!!.verdict))
        val renamed = listOf(r.facts.calls[0].copy(fn = "milestones")) + r.facts.calls.drop(1)
        assertEquals("today.done.calls[0].fn", paths(check(r, r.facts.copy(calls = renamed))!!.verdict).first())
    }

    @Test
    fun anAwardsArgumentsAreComparedAndItsAnswerDrivesNative() {
        val r = first("today", "done", ok = true)
        val i = r.facts.calls.indexOfFirst { it.fn == "awardActivity" }
        val c = r.facts.calls[i]
        val args = c.args as JsonArray
        val titled = JsonArray(listOf(args.items[0], JsonObject(mapOf("title" to JsonString("Another session")))))
        val v = check(r, r.facts.copy(calls = r.facts.calls.toMutableList().also { it[i] = c.copy(args = titled) }))!!.verdict
        assertEquals(listOf("today.done.calls[$i].awardActivity[1].title"), paths(v))
        // the economy said its save failed: native then words the notice so and pays no bounty, unlike what the page did
        val failed = JsonObject(mapOf("awarded" to JsonBool.FALSE, "reason" to JsonString("HOST_SAVE_FAILED")))
        val v2 = check(r, r.facts.copy(calls = r.facts.calls.toMutableList().also { it[i] = c.copy(out = failed) }))!!.verdict
        assertTrue(paths(v2).toString(), paths(v2).any { it.startsWith("today.done.session.notice") } && paths(v2).any { it.startsWith("today.done.state") })
    }

    @Test
    fun theStateAndTheMemoryAfterAreCompared() {
        val r = first("do", "goalPriority", ok = true)
        val v = check(r, after = r.after.replaceFirst("\"priority\":5", "\"priority\":4"))!!.verdict
        assertTrue(paths(v).toString(), paths(v).single().matches(Regex("do\\.goalPriority\\.state\\.goals\\[\\d+]\\.priority")))
        val banner = JsonObject(mapOf("kind" to JsonString("bad"), "text" to JsonString("x")))
        val f = r.facts.copy(after = with(r.facts.after, "banner", banner))
        assertEquals(listOf("do.goalPriority.session.banner"), paths(check(r, f)!!.verdict))
    }

    @Test
    fun aTimingIsNotComparedAndAClockReadingIsTheCommandsMoment() {
        val r = ran.first { it.facts.op == "today" && it.facts.name == "replan" }
        val timed = Regex("\"computeTimeMs\":[0-9.]+").replace(r.after) { "\"computeTimeMs\":987.5" }
        assertTrue(timed != r.after)
        assertEquals(Verdict.Same, check(r, after = timed)!!.verdict)
        // the page read its clock a few milliseconds into the command: the same while it is inside the command's moment
        val notice = obj(r.facts.after.members["notice"])
        val late = r.facts.copy(after = with(r.facts.after, "notice", with(notice, "at", JsonNumber(r.facts.nowMs + 7.0))))
        assertEquals(Verdict.Same, check(r, late, t1 = r.facts.nowMs + 10)!!.verdict)
        assertEquals(listOf("today.replan.session.notice.at"), paths(check(r, late, t1 = r.facts.nowMs + 5)!!.verdict))
    }

    @Test
    fun aTakeBackGoesInFullOnceThenByItsDigest() {
        val full = ran.filter { (it.facts.before.members["takeBack"] as? JsonObject)?.members?.containsKey("before") == true }
        val ref = ran.filter { (it.facts.before.members["takeBack"] as? JsonObject)?.members?.let { m -> "digest" in m && "before" !in m } == true }
        assertTrue("${full.size} ${ref.size}", full.isNotEmpty() && ref.isNotEmpty())
        val r = ref.first()
        assertEquals(Verdict.Skipped(CommandCheck.TAKE_BACK), checks.command(parse(r.before), parse(r.after), r.facts, r.t1) { null }.verdict)
        // a take-back whose plan native writes otherwise than the page: its digest differs, and the command is not replayed on it
        val u = full.first()
        val tb = obj(u.facts.before.members["takeBack"])
        val f = u.facts.copy(before = with(u.facts.before, "takeBack", with(tb, "left", JsonString((tb.members["left"] as JsonString).value + " "))))
        assertEquals(listOf("${u.facts.op}.${u.facts.name}.takeBack.digest"), paths(check(u, f)!!.verdict))
        assertEquals(CommandCheck.digest(TakeBackState("", "", 0.0, tb.members["before"]!!, (tb.members["left"] as JsonString).value)), (tb.members["digest"] as JsonString).value)
    }

    @Test
    fun aZoneNativeReadsOtherwiseOrAnOpItDoesNotKnowIsSkipped() {
        val r = ran.first()
        assertEquals(Verdict.Skipped("tzdata"), check(r, r.facts.copy(planOffset = 3600))!!.verdict)
        assertEquals(Verdict.Skipped("unknownOp"), check(r, r.facts.copy(op = "later"))!!.verdict)
    }

    @Test
    fun theTickFollowsWhatLifeMaxsLogSaid() {
        val r = ran.first { it.facts.op == "tick" && it.facts.calls.any { c -> c.fn == "activitiesSince" && (c.out as JsonArray).items.isNotEmpty() } }
        val empty = r.facts.calls.map { if (it.fn == "activitiesSince") it.copy(out = JsonArray(emptyList())) else it }
        val v = check(r, r.facts.copy(calls = empty))!!.verdict
        // native takes nothing in, so its later calls are not the page's: the first that differs is named
        assertTrue(paths(v).toString(), paths(v).first().matches(Regex("tick\\.calls\\[\\d+]\\.fn")))
    }

    @Test
    fun aCommandAcrossASecondIsSkippedAndASlowOneSampled() {
        val s = DirectorShadowStream.session()
        DirectorShadowStream.play(s) { if (it.contains("\"kind\":\"director.cmd\"")) it.replace("\"flags\":[]", "\"flags\":[\"second\"]") else it }
        val c = s.ledger.kinds[DirectorShadowJson.CMD]!!
        assertEquals(0, c.compared)
        assertEquals(mapOf("second" to c.seen), c.skipped)
        val slow = DirectorShadowStream.session { r -> DirectorChecks(r, zoneOffset = { _, _ -> Thread.sleep(DirectorShadowLimits.ENGINE_SLOW_MS + 50); 0 }) }
        DirectorShadowStream.play(slow) { if (it.contains("\"kind\":\"director.tick\"")) it else it.replace("\"kind\":\"director.plan\"", "\"kind\":\"director.planx\"").replace("\"kind\":\"director.repair\"", "\"kind\":\"director.repairx\"").replace("\"kind\":\"director.read\"", "\"kind\":\"director.readx\"").replace("\"kind\":\"director.cmd\"", "\"kind\":\"director.cmdx\"") }
        val t = slow.ledger.kinds[DirectorShadowJson.TICK]!!
        assertTrue(t.toString(), (t.skipped["sampled"] ?: 0) > 0 && t.compared in 1 until t.seen)
    }

    @Test
    fun nothingTheCheckKeepsNamesWhatACommandCarried() {
        val s = DirectorShadowStream.session()
        // the page's replies said something else: differences whose paths are shown, never the words, ids or titles
        DirectorShadowStream.play(s) { if (it.contains("\"kind\":\"director.cmd\"")) it.replace("\"reply\":{\"ok\":true", "\"reply\":{\"ok\":false") else it }
        assertTrue(s.ledger.mismatches().any { it.kind == DirectorShadowJson.CMD && it.paths.any { p -> p.endsWith("reply.ok") } })
        val kept = s.ringText() + s.report(DirectorShadowStream.T, ZoneOffset.UTC)
        for (word in listOf("Practice guitar", "Read on the bus", "act-elsewhere", "goal-op-1", "log-1", "Build muscle", "Dentist", "g-read"))
            assertFalse(word, kept.contains(word))
    }

    @Test
    fun aMalformedCommandIsRefusedByKey() {
        val text = DirectorShadowStream.envelopes(DirectorShadowJson.CMD).first()
        fun refused(t: String) = (DirectorShadowJson.envelope(t) as ShadowDecode.Refused)
        assertEquals(ShadowRefusal.UNKNOWN_KEY, refused(text.replace("\"cmd\":{", "\"cmdx\":{")).reason)
        assertEquals(ShadowRefusal.UNKNOWN_KEY, refused(text.replace("\"retires\":", "\"other\":1,\"retires\":")).reason)
        assertEquals(ShadowRefusal.MISSING_KEY, refused(text.replace(Regex(",\"ids\":\\[[^\\]]*]"), "")).reason)
        assertEquals(ShadowRefusal.OUT_OF_RANGE, refused(text.replace("\"kind\":\"director.cmd\"", "\"kind\":\"director.tick\"")).reason)
        assertEquals(ShadowRefusal.BAD_ZONE, refused(text.replace(Regex("\"deviceZone\":\"[^\"]*\""), "\"deviceZone\":\"Mars/Base\"")).reason)
        assertEquals("cmd.deviceZone", refused(text.replace(Regex("\"deviceZone\":\"[^\"]*\""), "\"deviceZone\":\"Mars/Base\"")).at)
        val calls = Regex("\"calls\":\\[\\[\"([a-zA-Z.]+)\",(\\d+),(\\d+)]").find(text)!!
        assertEquals(ShadowRefusal.OUT_OF_RANGE, refused(text.replaceFirst(calls.value, "\"calls\":[[\"${calls.groupValues[1]}\",99999,${calls.groupValues[3]}]")).reason)
        assertEquals(ShadowRefusal.NOT_JSON, refused(text.replaceFirst("\"texts\":[", "\"texts\":[" + JsonWriter.write(JsonString("{not json")) + ",")).reason)
        assertEquals(ShadowRefusal.OUT_OF_RANGE, refused(text.replaceFirst(calls.value, "\"calls\":[[\"no such host\",${calls.groupValues[2]},${calls.groupValues[3]}]")).reason)
    }

    companion object {
        private val checks = DirectorChecks(ShadowRedactor(ByteArray(ShadowLimits.SALT_BYTES) { 1 }))

        class Ran(val kind: String, val before: String, val after: String, val facts: CommandFacts, val t1: Long)

        private fun env(text: String) = (DirectorShadowJson.envelope(text) as ShadowDecode.Ok).value

        /** Every command and tick of the stream, with the copy's texts before and after it (the session's own order). */
        private val ran: List<Ran> by lazy {
            val m = TextMirror()
            val parts = ArrayList<String>()
            val out = ArrayList<Ran>()
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
                if (e.kind == DirectorShadowJson.PLAN || e.kind == DirectorShadowJson.REPAIR) continue
                m.apply(e.ops, e.seq, false)
                val c = e.cmd ?: continue
                val before = m.stitch()!!
                m.apply(c.afterOps, e.seq, false)
                out += Ran(e.kind, before, m.stitch()!!, c, e.t1)
            }
            out
        }

        /** The take-backs the page sent in full, by digest, as a session keeps them. */
        private val known: Map<String, TakeBackState> by lazy {
            val k = HashMap<String, TakeBackState>()
            for (r in ran) checks.command(parse(r.before), parse(r.after), r.facts, r.t1) { k[it] }.learned?.let { k[it.first] = it.second }
            k
        }

        private fun parse(text: String): JsonValue = (JsonReader.read(text) as JsonRead.Parsed).value
    }
}
