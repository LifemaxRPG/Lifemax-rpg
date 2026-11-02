package com.lifemax.rpg.feature.settings

import com.lifemax.rpg.feature.settings.data.WebSettingsRepository
import com.lifemax.rpg.feature.settings.data.toJson
import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.FieldInput
import com.lifemax.rpg.feature.settings.domain.SettingsCommand
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsReply
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import com.lifemax.rpg.feature.settings.presentation.Asked
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel
import com.lifemax.rpg.feature.settings.presentation.components.minutesOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The text, number and time fields the screen edits itself (settings.fields.v1): the projection the
 * page sends when the screen says it can, its strict decoding, the drafts and the two commands
 * (setField, saveForm) with the page's answers. A page without the capability still projects every
 * field as a hand-over, which SettingsTest's fixtures prove unchanged.
 */
class SettingsFieldsTest {
    private fun read(name: String) =
        SettingsFieldsTest::class.java.getResource("/fixtures/settings-$name.json")!!.readText()

    private fun decode(name: String = "fields") =
        WebSettingsRepository().apply { accept(JSONObject(read(name)), true) }.state.value!!

    private fun SettingsProjection.controls(): List<SettingsControl> =
        cards.flatMap { card ->
            card.rows.flatMap { row ->
                when (row) {
                    is SettingsRow.Line -> row.controls
                    is SettingsRow.Note -> row.controls
                    is SettingsRow.Controls -> row.controls
                    is SettingsRow.Field -> row.controls
                    is SettingsRow.Tab -> listOfNotNull(row.earlier, row.later, row.show)
                    is SettingsRow.Text -> emptyList()
                }
            }
        }

    private fun SettingsProjection.field(id: String) =
        controls().first { it.id == id } as SettingsControl.Field

    private fun SettingsProjection.save(group: String) =
        controls().filterIsInstance<SettingsControl.Button>().first { it.group == group }

    /** Every control object of the raw projection, to break one. */
    private fun JSONObject.rawControls(): List<JSONObject> =
        getJSONArray("cards").objects().flatMap { card ->
            card.getJSONArray("rows").objects().flatMap { row ->
                val list = mutableListOf<JSONObject>()
                row.optJSONArray("controls")?.objects()?.let { list += it }
                list
            }
        }

    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.raw(id: String) = rawControls().first { it.getString("id") == id }

    // ---------- the projection ----------

    @Test
    fun theFieldsAreEditableAndCarryThePagesLimitsAndForms() {
        val p = decode()
        val fields = p.controls().filterIsInstance<SettingsControl.Field>()
        assertEquals(19, fields.size)
        assertTrue(fields.all { it.op == ControlOp.Edit && it.edit != null })

        val name = p.field("#characterName")
        assertEquals(FieldInput.Text, name.input)
        assertEquals("profile", name.edit!!.group)
        assertEquals(40, name.edit!!.maxLength)
        assertEquals("The Life Architect", name.placeholder)

        val weight = p.field("#characterWeight").edit!!
        assertEquals(1.0, weight.min!!, 0.0)
        assertEquals(1400.0, weight.max!!, 0.0)
        assertEquals(0.1, weight.step!!, 0.0)
        assertNull(weight.maxLength)

        assertEquals(14.0, p.field("#decayGrace").edit!!.max!!, 0.0)
        assertNull(p.field("#decayGrace").edit!!.step)
        assertEquals("rules", p.field("#rule-reading").edit!!.group)
        assertEquals(9, fields.count { it.edit!!.group == "rules" })

        // a time is saved at once: no form
        val workout = p.field("reminders.workoutTime")
        assertEquals(FieldInput.Time, workout.input)
        assertEquals("", workout.edit!!.group)
        assertEquals("17:00", workout.value)
        assertEquals(
            listOf(
                "reminders.workoutTime",
                "reminders.checkinTime",
                "reminders.quietFrom",
                "reminders.quietTo",
            ),
            fields.filter { it.input == FieldInput.Time }.map { it.id },
        )
        // the check-in's time is the page's disabled input while the check-in is off
        assertFalse(p.field("reminders.checkinTime").enabled)
    }

    @Test
    fun theSaveButtonsNameTheirFormAndOtherButtonsAreUnchanged() {
        val p = decode()
        assertEquals(
            listOf("Save character", "Save decay settings", "Save XP rules"),
            listOf("profile", "decay", "rules").map { p.save(it).label },
        )
        assertTrue(listOf("profile", "decay", "rules").all { p.save(it).op == ControlOp.Save })
        // Reset defaults still belongs to the page
        val reset =
            p.controls().filterIsInstance<SettingsControl.Button>().first {
                it.label == "Reset defaults"
            }
        assertEquals(ControlOp.Reveal, reset.op)
        assertEquals("", reset.group)
    }

    @Test
    fun withoutTheCapabilityEveryFieldStaysAHandOver() {
        listOf("default", "many", "light", "bar").forEach { name ->
            val fields = decode(name).controls().filterIsInstance<SettingsControl.Field>()
            assertTrue(name, fields.isNotEmpty())
            assertTrue(name, fields.all { it.op == ControlOp.Reveal && it.edit == null })
            assertTrue(
                name,
                decode(name).controls().filterIsInstance<SettingsControl.Button>().none {
                    it.op == ControlOp.Save
                },
            )
        }
    }

    @Test
    fun theDecoderRefusesWhatTheScreenCouldNotDraw() {
        fun refuses(why: String, break_: JSONObject.() -> Unit) {
            val j = JSONObject(read("fields"))
            j.break_()
            try {
                WebSettingsRepository().accept(j, true)
                fail("accepted: $why")
            } catch (_: IllegalArgumentException) {} catch (_: org.json.JSONException) {}
        }
        refuses("a time that names a form") { raw("reminders.workoutTime").put("group", "profile") }
        refuses("a text without a form") { raw("#characterName").put("group", "") }
        refuses("a field without its group") { raw("#decayRate").remove("group") }
        refuses("a limit as text") { raw("#decayRate").put("max", "10") }
        refuses("a missing limit (null is how the page says none)") {
            raw("#decayRate").remove("min")
        }
        refuses("a length that is not whole") { raw("#characterName").put("maxLength", 2.5) }
        refuses("a Save without its form") { raw("save.rules").put("group", "") }
        refuses("a Save without its group key") { raw("save.rules").remove("group") }
        refuses("a form whose Save is gone") { raw("save.decay").put("op", "reveal") }
        refuses("a Save for a form with no fields") { raw("save.decay").put("group", "nothing") }
        refuses("two Saves for one form") { raw("save.decay").put("group", "rules") }
        refuses("a field edited and also pressed") { raw("#decayRate").put("op", "press") }
        refuses("a password field (the page never sends one)") {
            raw("#characterName").put("input", "password")
        }
        refuses("a Save that is a plain field") { raw("#decayRate").put("op", "save") }
    }

    @Test
    fun aPageThatSendsNoFormsAtAllDecodesAsBefore() {
        // the same projection with every Edit and Save put back to what an older page sends
        val j = JSONObject(read("fields"))
        j.rawControls().forEach { c ->
            when {
                c.optString("op") == "edit" -> {
                    c.put("op", "reveal")
                    listOf("group", "min", "max", "step", "maxLength").forEach(c::remove)
                }
                c.optString("op") == "save" -> c.put("op", "reveal").remove("group")
            }
        }
        val p = WebSettingsRepository().apply { accept(j, true) }.state.value!!
        assertTrue(
            p.controls().filterIsInstance<SettingsControl.Field>().all { it.op == ControlOp.Reveal }
        )
    }

    // ---------- the commands on the wire ----------

    @Test
    fun theCommandsAreTheShapesThePageReads() {
        val set = SettingsCommand.SetField("reminders.workoutTime", "18:45")
        assertEquals("setField", set.name)
        val setJson = set.toJson()
        assertEquals(setOf("id", "value"), setJson.keys().asSequence().toSet())
        assertEquals("reminders.workoutTime", setJson.getString("id"))
        assertEquals("18:45", setJson.getString("value"))
        val save =
            SettingsCommand.SaveForm(
                "profile",
                mapOf("characterName" to "Ada", "characterWeight" to "175.5"),
            )
        assertEquals("saveForm", save.name)
        val saveJson = save.toJson()
        assertEquals(setOf("group", "values"), saveJson.keys().asSequence().toSet())
        assertEquals("profile", saveJson.getString("group"))
        val values = saveJson.getJSONObject("values")
        assertEquals(setOf("characterName", "characterWeight"), values.keys().asSequence().toSet())
        assertEquals("Ada", values.getString("characterName"))
        // text stays text: a weight is never turned into a number on the way
        assertEquals("175.5", values.get("characterWeight"))
    }

    @Test
    fun aTimeIsMinutesAfterMidnight() {
        assertEquals(17 * 60, minutesOf("17:00"))
        assertEquals(0, minutesOf("00:00"))
        assertEquals(23 * 60 + 59, minutesOf("23:59"))
        listOf("", "24:00", "7:05", "17:60", "1700", "17:00 PM").forEach {
            assertNull(it, minutesOf(it))
        }
    }

    // ---------- the view model ----------

    private class Sent(val command: SettingsCommand, val requestId: String)

    private class Harness(name: String = "fields") {
        val repo = WebSettingsRepository().apply { accept(JSONObject(read(name)), true) }
        val sent = mutableListOf<Sent>()
        var now = 0L
        private var ids = 0
        val vm =
            SettingsViewModel(repo, { c, id -> sent += Sent(c, id) }, { "r${++ids}" }, { now })
                .apply { onOpen() }

        fun p() = repo.state.value!!

        fun field(id: String) = p().controls().first { it.id == id } as SettingsControl.Field

        private fun SettingsProjection.controls() =
            cards.flatMap { c ->
                c.rows.flatMap { r ->
                    when (r) {
                        is SettingsRow.Line -> r.controls
                        is SettingsRow.Field -> r.controls
                        is SettingsRow.Controls -> r.controls
                        is SettingsRow.Note -> r.controls
                        else -> emptyList()
                    }
                }
            }

        fun button(label: String) =
            p().controls().first { it is SettingsControl.Button && it.label == label }
                as SettingsControl.Button

        /** The page's answer to the last command, with the projection after [change]. */
        fun answer(
            ok: Boolean = true,
            message: String = "",
            saved: Boolean = true,
            change: JSONObject.() -> Unit = {},
        ) {
            val last = sent.last()
            val next = JSONObject(read("fields")).put("sequence", p().sequence + 1)
            next.change()
            repo.accept(next)
            vm.onUpdate()
            vm.onReply(
                SettingsReply(last.command.name, last.requestId, ok, ok, message, saved, null)
            )
        }

        companion object {
            fun read(name: String) =
                SettingsFieldsTest::class
                    .java
                    .getResource("/fixtures/settings-$name.json")!!
                    .readText()
        }
    }

    private fun JSONObject.setValue(id: String, value: String) {
        getJSONArray("cards").objects().forEach { card ->
            card.getJSONArray("rows").objects().forEach { row ->
                row.optJSONArray("controls")
                    ?.objects()
                    ?.filter { it.getString("id") == id }
                    ?.forEach { it.put("value", value) }
            }
        }
    }

    @Test
    fun typingMakesADraftAndNothingGoesToThePageUntilSave() {
        val h = Harness()
        val name = h.field("#characterName")
        h.vm.type(name, "Ada")
        assertEquals("Ada", h.vm.ui.value.text(name))
        assertTrue(h.sent.isEmpty())
        // typing the page's own value back is no draft
        h.vm.type(name, "N")
        assertTrue(h.vm.ui.value.drafts.isEmpty())
        // a disabled field takes nothing
        h.vm.type(h.field("reminders.checkinTime"), "x")
        assertTrue(h.vm.ui.value.drafts.isEmpty())
    }

    @Test
    fun saveSendsTheWholeFormAndTheStoredValuesReplaceTheDrafts() {
        val h = Harness()
        h.vm.type(h.field("#characterName"), "Ada")
        h.vm.type(h.field("#characterWeight"), "175.5")
        h.vm.type(h.field("#decayGrace"), "9") // another form's draft stays out of this save
        val save = h.button("Save character")
        h.vm.press(save)
        val sent = h.sent.single()
        assertEquals(
            SettingsCommand.SaveForm(
                "profile",
                mapOf(
                    "characterName" to "Ada",
                    "characterWeight" to "175.5",
                    "characterHeightFt" to "",
                    "characterHeightIn" to "",
                ),
            ),
            sent.command,
        )
        assertEquals(Asked.Press, h.vm.ui.value.pending[save.id])
        // a second tap while the page has not answered asks nothing more
        h.vm.press(save)
        assertEquals(1, h.sent.size)
        // what was typed stays on the screen until the page answers
        assertEquals("Ada", h.vm.ui.value.text(h.field("#characterName")))
        h.answer(message = "Character saved") {
            setValue("#characterName", "Ada")
            setValue("#characterWeight", "175.5")
        }
        assertEquals(mapOf("#decayGrace" to "9"), h.vm.ui.value.drafts)
        assertTrue(h.vm.ui.value.pending.isEmpty())
        assertEquals("Character saved", h.vm.ui.value.toast!!.text)
        assertEquals("Ada", h.vm.ui.value.text(h.field("#characterName")))
    }

    @Test
    fun aRefusedSaveKeepsWhatWasTypedAndShowsThePagesWords() {
        val h = Harness()
        h.vm.type(h.field("#characterName"), "Ada")
        h.vm.press(h.button("Save character"))
        h.answer(
            ok = false,
            saved = false,
            message = "Could not save to this browser (storage full?)",
        )
        assertEquals("Ada", h.vm.ui.value.text(h.field("#characterName")))
        assertEquals("Could not save to this browser (storage full?)", h.vm.ui.value.toast!!.text)
        assertTrue(h.vm.ui.value.pending.isEmpty())
        // and the owner can save again
        h.vm.press(h.button("Save character"))
        assertEquals(2, h.sent.size)
    }

    @Test
    fun whatIsTypedWhileSavingIsNotLostWhenTheAnswerComes() {
        val h = Harness()
        h.vm.type(h.field("#characterName"), "Ada")
        h.vm.press(h.button("Save character"))
        h.vm.type(h.field("#characterName"), "Adam")
        h.answer { setValue("#characterName", "Ada") }
        assertEquals("Adam", h.vm.ui.value.text(h.field("#characterName")))
    }

    @Test
    fun saveWithNothingTypedStillAsksThePageToSaveItsOwnValues() {
        val h = Harness()
        h.vm.press(h.button("Save XP rules"))
        val command = h.sent.single().command as SettingsCommand.SaveForm
        assertEquals("rules", command.group)
        assertEquals(9, command.values.size)
        assertEquals("24", command.values["rule-reading"])
        assertTrue(command.values.keys.none { it.startsWith("#") })
    }

    @Test
    fun aPickedTimeGoesAtOnceAndShowsAsPickedUntilTheAnswer() {
        val h = Harness()
        val time = h.field("reminders.workoutTime")
        h.vm.time(time, 18 * 60 + 45)
        assertEquals(
            SettingsCommand.SetField("reminders.workoutTime", "18:45"),
            h.sent.single().command,
        )
        assertEquals("18:45", h.vm.ui.value.text(time))
        // the same minute as the page holds asks nothing
        h.vm.time(h.field("reminders.quietFrom"), 22 * 60)
        assertEquals(1, h.sent.size)
        // refused: the page's time shows again, with its words
        h.answer(ok = false, message = "This field cannot be changed right now.")
        assertEquals("17:00", h.vm.ui.value.text(h.field("reminders.workoutTime")))
        assertEquals("This field cannot be changed right now.", h.vm.ui.value.toast!!.text)
        // stored: the projection holds it
        h.vm.time(time, 7 * 60 + 5)
        h.answer { setValue("reminders.workoutTime", "07:05") }
        assertEquals("07:05", h.vm.ui.value.text(h.field("reminders.workoutTime")))
        assertEquals(
            SettingsCommand.SetField("reminders.workoutTime", "07:05"),
            h.sent.last().command,
        )
    }

    @Test
    fun aLostAnswerFreesTheControlButKeepsTheDraft() {
        val h = Harness()
        h.vm.type(h.field("#characterName"), "Ada")
        val save = h.button("Save character")
        h.vm.press(save)
        h.now = 6_000
        h.vm.onUpdate()
        assertFalse(save.id in h.vm.ui.value.pending)
        assertEquals("Ada", h.vm.ui.value.text(h.field("#characterName")))
    }

    @Test
    fun reopeningTheScreenStartsFromThePage() {
        val h = Harness()
        h.vm.type(h.field("#characterName"), "Ada")
        h.vm.onOpen()
        assertTrue(h.vm.ui.value.drafts.isEmpty())
        assertEquals("N", h.vm.ui.value.text(h.field("#characterName")))
    }

    @Test
    fun aFieldHandedToThePageStillRevealsWhenThePageDidNotSayEdit() {
        val h = Harness("default")
        val name = h.field("#characterName")
        assertEquals(ControlOp.Reveal, name.op)
        h.vm.type(name, "Ada") // an old page's field takes no typing
        assertTrue(h.vm.ui.value.drafts.isEmpty())
        h.vm.reveal(name)
        assertEquals(
            com.lifemax.rpg.feature.settings.domain.SettingsPageAction.Reveal("#characterName"),
            h.vm.actions.tryReceive().getOrNull(),
        )
    }
}
