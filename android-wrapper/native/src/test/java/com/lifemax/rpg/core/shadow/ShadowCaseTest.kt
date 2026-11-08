package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.economy.SavePipeline
import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import java.io.File
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Save case…" (decision O-4, ECONOMY_SHADOW_PLAN WP6e): an unexplained difference keeps its whole
 * action, unredacted, in memory only, and nowhere else; only an explicit id asks for it; a same or
 * an allowed action keeps none; at most [ShadowLimits.CASES_KEPT] are held, a repeat replacing its
 * own; Clear and a new app process forget them. The plugin's side is checked from its source: the
 * case reaches only a file the owner picked in the system's picker, and nothing in the check uses
 * the network.
 */
class ShadowCaseTest {
    private val built = GoldenActions.build()
    private val note = "Private: physio said rest the left knee"

    private val log =
        built.first {
            it.recorded &&
                it.action.kind == "log.record" &&
                it.action.saves.all { ok -> ok } &&
                count(it.action.after) > count(it.action.before)
        }

    private fun count(state: Map<String, JsonValue>) =
        (state["activities"] as? JsonArray)?.items?.size ?: 0

    private fun session() =
        ShadowSession(
                ShadowSessionTest.FP,
                ShadowLedger(ShadowSessionTest.T, ByteArray(ShadowLimits.SALT_BYTES) { 7 }),
                1,
                112,
                GoldenActions.CONTENT,
            )
            .also { it.configure(ShadowSessionTest.configure(ShadowSessionTest.FP), false) }

    private fun run(vararg actions: FakeShadowPage.Action): ShadowSession {
        GoldenActions.CONTENT.setup = GoldenActions.ECONOMY
        val s = session()
        val page = FakeShadowPage()
        for (part in page.base(actions.first().before)) assertTrue(s.base(part, 0).ok)
        for (a in actions) s.observe(page.send(a), FakeShadowPage.T0)
        return s
    }

    /** The golden Log as a page that wrote [words] into the new record's notes and [extra] XP. */
    private fun lying(words: String = note, extra: Double = 1.0): FakeShadowPage.Action {
        val items = (log.action.after["activities"] as JsonArray).items
        val last = (items.last() as JsonObject).members
        val xp = ((last["xp"] as? JsonNumber)?.value ?: 0.0) + extra
        val record = JsonObject(last + ("notes" to JsonString(words)) + ("xp" to JsonNumber(xp)))
        return log.action.copy(
            after = log.action.after + ("activities" to JsonArray(items.dropLast(1) + record))
        )
    }

    @Test
    fun anUnexplainedDifferenceKeepsItsWholeActionInMemoryOnly() {
        val s = run(lying())
        val cases = s.summary().cases
        assertEquals(1, cases.size)
        val entry = s.ledger.mismatches().last()
        assertEquals(entry.id, cases.single().id)
        assertEquals("log.record", cases.single().kind)
        val text = s.caseText(entry.id)
        assertNotNull(text)
        // unredacted: the owner's words and the action's envelope as the page sent it
        assertTrue(note in text!!)
        assertTrue("\"envelope\":{\"v\":1" in text)
        assertTrue("\"native\":{\"state\":" in text)
        assertTrue("\"keys\":[\"activities\"]" in text)
        // never in the ring, the report or the summary
        assertFalse(note in s.ringText())
        assertFalse(note in s.report(FakeShadowPage.T0, ZoneOffset.UTC))
        assertFalse(note in s.summary().toString())
    }

    @Test
    fun onlyAnUnexplainedDifferenceAndOnlyItsOwnIdGiveACase() {
        assertTrue(run(log.action).summary().cases.isEmpty())
        val failed =
            built.first {
                it.recorded &&
                    it.action.kind == "log.record" &&
                    it.action.saves.any { ok -> !ok } &&
                    it.action.toasts.isNotEmpty()
            }
        val unsaved =
            failed.action.toasts.last {
                it == SavePipeline.SAVE_FULL || it == SavePipeline.SAVE_READ_ONLY
            }
        val allowed = run(failed.action.copy(toasts = failed.action.toasts + unsaved))
        assertEquals(1, allowed.ledger.allowed)
        assertTrue("an allowed difference keeps no case", allowed.summary().cases.isEmpty())
        val s = run(lying())
        assertNull(s.caseText("m-99"))
        assertNull(s.caseText(""))
        assertNull(s.caseText("t-1"))
    }

    @Test
    fun aRepeatedDifferenceKeepsOneCaseWithItsCount() {
        val s = run(lying(), lying())
        assertEquals(1, s.ledger.mismatches().size)
        val case = s.summary().cases.single()
        assertEquals(2, case.count)
        assertEquals(s.ledger.mismatches().single().id, case.id)
    }

    @Test
    fun differentDifferencesAreBoundedToTheLatestFive() {
        val keys =
            listOf(
                "sparks",
                "restedXP",
                "streakShields",
                "restTokens",
                "decayTotal",
                "shieldMiss",
                "legacyCharXPBonus",
            )
        val lies =
            keys.mapIndexed { i, k ->
                log.action.copy(after = log.action.after + (k to JsonNumber(5000.0 + i)))
            }
        val s = run(*lies.toTypedArray())
        val entries = s.ledger.mismatches().filter { it.unexplained }
        assertTrue(entries.size >= ShadowLimits.CASES_KEPT + 1)
        val cases = s.summary().cases
        assertEquals(ShadowLimits.CASES_KEPT, cases.size)
        assertEquals(
            entries.takeLast(ShadowLimits.CASES_KEPT).map { it.id }.reversed(),
            cases.map { it.id },
        )
        assertNull(s.caseText(entries.first().id))
    }

    @Test
    fun clearAndANewAppProcessForgetEveryCase() {
        val s = run(lying())
        val id = s.summary().cases.single().id
        s.clear()
        assertNull(s.caseText(id))
        assertTrue(s.summary().cases.isEmpty())
        // the ring keeps the difference across a restart, but never its case
        val again = run(lying())
        val reread = ShadowLedgerJson.read(again.ringText())!!
        assertEquals(1, reread.mismatches().size)
        val restarted = ShadowSession(ShadowSessionTest.FP, reread, 1, 112)
        assertTrue(restarted.summary().cases.isEmpty())
        assertNull(restarted.caseText(reread.mismatches().single().id))
    }

    @Test
    fun theCaseLeavesOnlyThroughTheOwnersPickedFileAndNothingUsesTheNetwork() {
        val plugin = source("bridge/web/NativeShadowPlugin.kt")
        // the case is asked for in one place: saveCase, which opens the system's "save as" picker
        assertEquals(1, Regex("""\.case\(""").findAll(plugin).count())
        val saveCase = plugin.substringAfter("fun saveCase(").substringBefore("@ActivityCallback")
        assertTrue(".case(" in saveCase)
        assertTrue("Intent.ACTION_CREATE_DOCUMENT" in saveCase)
        // the only write is to the document the picker answered with
        assertEquals(1, Regex("openOutputStream").findAll(plugin).count())
        assertTrue("openOutputStream(uri" in plugin.substringAfter("fun caseChosen("))
        val core = File(root(), "core/shadow").listFiles()!!.filter { it.name.endsWith(".kt") }
        for (text in
            core.map { it.readText() } +
                plugin +
                source("app/shadow/ShadowWorker.kt") +
                source("app/shadow/ShadowRing.kt")) {
            for (net in
                listOf("java.net", "HttpURLConnection", "okhttp", "Socket", "URL(")) assertFalse(
                net,
                net in text,
            )
        }
        // nothing in core/shadow writes a file; the ring is the app layer's only file
        for (f in core) assertFalse(f.name, "java.io.File" in f.readText())
    }

    private fun root(): File {
        val start = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "android-wrapper/native/src/main/java/com/lifemax/rpg") }
            .firstOrNull { it.isDirectory }
            ?: generateSequence(start) { it.parentFile }
                .map { File(it, "native/src/main/java/com/lifemax/rpg") }
                .first { it.isDirectory }
    }

    private fun source(path: String) = File(root(), path).readText()
}
