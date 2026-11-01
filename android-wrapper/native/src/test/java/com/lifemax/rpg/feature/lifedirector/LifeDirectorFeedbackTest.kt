package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpGain
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.data.xpAwards
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Phase 4A's feedback: the XP celebration a reply carries (director.celebrations.v1), where its
 * orbs fly, and the sheet the Overview's glance asks for (intent), from the page's own replies and
 * projections (tools/gen_native_fixtures.js).
 */
class LifeDirectorFeedbackTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private val replies = fixture("replies-celebrate")

    private class Screen(
        val repo: WebLifeDirectorRepository,
        val vm: LifeDirectorViewModel,
        val commands: DirectorRecorder,
    ) {
        private var sequence = 1000L

        fun push(j: JSONObject, change: JSONObject.() -> Unit = {}) {
            repo.accept(JSONObject(j.toString()).put("sequence", ++sequence).apply(change))
            vm.projected()
        }
    }

    private fun screen(j: JSONObject): Screen {
        val repo = WebLifeDirectorRepository().apply { accept(j, true) }
        val commands = DirectorRecorder()
        return Screen(repo, LifeDirectorViewModel(repo, commands).apply { opened() }, commands)
    }

    private fun rejected(j: JSONObject) {
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: Exception) {
            return
        }
        fail("accepted a malformed projection")
    }

    // the reply's celebrations

    @Test
    fun `a done that pays XP carries its celebration in the page's shape`() {
        val r = directorReply(replies.getJSONObject("done"))
        assertTrue(r.ok)
        assertEquals(
            listOf(
                XpAward(
                    21,
                    listOf(
                        XpGain("physique", "Physique", 13, 0xFFFF6B57),
                        XpGain("discipline", "Discipline", 8, 0xFFA78BFA),
                    ),
                )
            ),
            r.celebrations,
        )
        assertTrue(directorReply(replies.getJSONObject("doneAgain")).celebrations.isEmpty())
    }

    @Test
    fun `the celebrations decoder is strict`() {
        val good = replies.getJSONObject("done").getJSONArray("celebrations")
        assertEquals(1, xpAwards(JSONArray(good.toString())).size)
        val breaks =
            listOf<(JSONObject) -> Unit>(
                { it.put("xp", "21") },
                { it.put("xp", 21.5) },
                { it.remove("gains") },
                { it.getJSONArray("gains").getJSONObject(0).put("color", "#ff8e57") },
                { it.getJSONArray("gains").getJSONObject(0).put("color", -1) },
                { it.getJSONArray("gains").getJSONObject(0).remove("short") },
                { it.getJSONArray("gains").getJSONObject(0).put("attribute", 7) },
                { it.getJSONArray("gains").getJSONObject(0).put("xp", JSONObject.NULL) },
            )
        for ((i, spoil) in breaks.withIndex()) {
            val a = JSONArray(good.toString()).also { spoil(it.getJSONObject(0)) }
            try {
                xpAwards(a)
                fail("break $i was accepted")
            } catch (_: Exception) {}
        }
    }

    @Test
    fun `a malformed celebration is not drawn but its reply still settles the write`() {
        val j = JSONObject(replies.getJSONObject("done").toString())
        j.getJSONArray("celebrations").getJSONObject(0).put("xp", "lots")
        val r = directorReply(j)
        assertEquals("director-33", r.requestId)
        assertTrue(r.ok)
        assertTrue(r.celebrations.isEmpty())
    }

    // the projection

    @Test
    fun `the projection carries where the orbs fly, and no intent unless the glance asked`() {
        val p = WebLifeDirectorRepository().apply { accept(fixture("today"), true) }.state.value!!
        val t = p.celebrationTarget!!
        assertEquals(32f, t.x, 0.01f)
        assertEquals(32.5f, t.y, 0.01f)
        assertNull(p.intent)
    }

    @Test
    fun `a page from before 4A sends neither, and the screen aims at the top centre`() {
        val j =
            fixture("today").apply {
                remove("celebrationTarget")
                remove("intent")
            }
        val p = WebLifeDirectorRepository().apply { accept(j, true) }.state.value!!
        assertNull(p.celebrationTarget)
        assertNull(p.intent)
    }

    @Test
    fun `the glance's intent is decoded, strictly`() {
        val p = WebLifeDirectorRepository().apply { accept(fixture("intent"), true) }.state.value!!
        val i = p.intent!!
        assertEquals(1L, i.id)
        assertEquals("session", i.sheet)
        assertTrue(i.target.isNotEmpty())
        val edits =
            listOf<(JSONObject) -> Unit>(
                { it.put("id", "1") },
                { it.put("id", 0) },
                { it.put("sheet", "") },
                { it.put("target", 5) },
                { it.remove("target") },
            )
        for (spoil in edits) rejected(fixture("intent").also { spoil(it.getJSONObject("intent")) })
        rejected(fixture("today").put("celebrationTarget", JSONObject().put("x", "32").put("y", 3)))
        rejected(fixture("today").put("celebrationTarget", JSONObject().put("x", 32)))
    }

    // the view model

    @Test
    fun `a reply's celebration is shown once, a new reply is a new one, and hiding ends it`() {
        val s = screen(fixture("sheet-session-now"))
        s.vm.onReply(directorReply(replies.getJSONObject("doneAgain")))
        assertNull(s.vm.feedback.celebration.value)
        s.vm.onReply(directorReply(replies.getJSONObject("done")))
        val first = s.vm.feedback.celebration.value!!
        assertEquals(21, first.awards.single().xp)
        s.vm.onReply(directorReply(replies.getJSONObject("done")))
        val second = s.vm.feedback.celebration.value!!
        assertTrue(second.id > first.id)
        s.vm.feedback.celebrated(first.id) // an earlier one finishing does not end the next
        assertNotNull(s.vm.feedback.celebration.value)
        s.vm.feedback.celebrated(second.id)
        assertNull(s.vm.feedback.celebration.value)
        s.vm.onReply(directorReply(replies.getJSONObject("done")))
        s.vm.hidden()
        assertNull(s.vm.feedback.celebration.value)
    }

    @Test
    fun `an award without gains is not celebrated`() {
        val s = screen(fixture("today"))
        s.vm.onReply(
            DirectorReply("r", "today", true, celebrations = listOf(XpAward(5, emptyList())))
        )
        assertNull(s.vm.feedback.celebration.value)
    }

    @Test
    fun `the glance's session opens the screen's own sheet, once`() {
        val j = fixture("intent")
        val s = screen(j)
        val target = s.repo.state.value!!.intent!!.target
        assertNull(s.vm.feedback.takeIntent())
        assertEquals(listOf("session" to target), s.commands.sheets)
        assertEquals(OpenSheet("session", target), s.vm.ui.value.sheet)
        s.vm.closeSheet()
        s.push(j) // the same intent in the next projection is not taken again
        assertNull(s.vm.feedback.takeIntent())
        assertEquals(1, s.commands.sheets.count { it.first == "session" })
        // a reloaded page counts its intents from 1 again
        s.repo.accept(
            JSONObject(j.toString()).put("webSessionId", "reloaded").put("sequence", 1),
            true,
        )
        assertNull(s.vm.feedback.takeIntent())
        assertEquals(2, s.commands.sheets.count { it.first == "session" })
    }

    @Test
    fun `the timer and meal sheets open here, a kind the screen does not draw is nobody's`() {
        val j = fixture("intent")
        fun take(sheet: String, target: String, id: Int): Pair<Tap?, Int> {
            val s = screen(j)
            s.push(j) {
                put("intent", JSONObject().put("id", id).put("sheet", sheet).put("target", target))
            }
            return s.vm.feedback.takeIntent() to s.commands.sheets.count { it.first == sheet }
        }
        assertEquals(null to 1, take("timer", "i-1", 2))
        assertEquals(null to 1, take("meal", "1793253600", 3))
        assertEquals(null to 0, take("disrupt", "", 4))
    }

    @Test
    fun `no intent, or a busy screen, takes nothing`() {
        val s = screen(fixture("today"))
        assertNull(s.vm.feedback.takeIntent())
        assertTrue(s.commands.sheets.isEmpty())
        val busy = screen(fixture("intent"))
        busy.vm.openSession("other") // a sheet open: the screen is busy
        assertNull(busy.vm.feedback.takeIntent())
        assertEquals(listOf("session" to "other"), busy.commands.sheets)
    }
}
