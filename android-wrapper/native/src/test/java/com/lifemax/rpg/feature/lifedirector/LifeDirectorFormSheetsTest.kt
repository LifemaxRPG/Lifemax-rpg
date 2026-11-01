package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.core.designsystem.form.FieldKind
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormValidation
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.GoalFormSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoalTypeSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.GoneSheet
import com.lifemax.rpg.feature.lifedirector.domain.OpenSheet
import com.lifemax.rpg.feature.lifedirector.domain.RuleSheetModel
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.DirectorActions
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.SheetController
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * My time's commitment and goal sheets (phase 4B), from the page's own projections and replies
 * (director-form-*.json, director-replies-time.json): the decoders, and FormSheetController through
 * the view model.
 */
class LifeDirectorFormSheetsTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private val replies = fixture("replies-time")

    private fun sheetOf(name: String) =
        WebLifeDirectorRepository().apply { accept(fixture(name), true) }.state.value!!.sheet

    private fun rejects(j: JSONObject) {
        try {
            WebLifeDirectorRepository().accept(j, true)
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed sheet")
    }

    /** [name]'s projection with its sheet changed by [change]. */
    private fun broken(name: String, change: JSONObject.() -> Unit) =
        fixture(name).apply { getJSONObject("sheet").change() }

    private fun JSONObject.field(key: String): JSONObject {
        val list = getJSONArray("fields")
        for (i in 0 until list.length()) if (list.getJSONObject(i).getString("key") == key)
            return list.getJSONObject(i)
        throw AssertionError("no $key")
    }

    // The decoders

    @Test
    fun decodesTheNewCommitmentSheet() {
        val r = sheetOf("form-rule") as RuleSheetModel
        assertNull(r.ruleId)
        assertEquals("", r.id)
        assertEquals("Add a commitment", r.title)
        assertEquals("Add", r.save.label)
        assertEquals("ruleSave", r.save.command)
        assertEquals("Cancel", r.cancel)
        assertNull(r.remove)
        assertTrue(r.note.startsWith("Overnight shifts are fine"))
        val f = r.schema.fields.associateBy { it.key }
        assertEquals(
            listOf(
                "title",
                "kind",
                "rotating",
                "days",
                "on",
                "off",
                "anchor",
                "start",
                "end",
                "commute",
                "unwind",
                "load",
            ),
            r.schema.fields.map { it.key },
        )
        // required is a flag on the wire; the words are the page's ruleSave's
        assertEquals("Pick at least one day", f["days"]!!.required)
        assertEquals("Pick the first day on", f["anchor"]!!.required)
        assertEquals("Enter start and end times", f["start"]!!.required)
        assertNull(f["title"]!!.required)
        assertEquals(FieldValue.Days(setOf(1, 2, 3, 4, 5)), f["days"]!!.value)
        assertEquals("0", f["on"]!!.group)
        assertEquals("1", f["start"]!!.group)
        assertNull(f["title"]!!.group)
        // "" is no placeholder: the page's steppers stay steppers
        assertNull(f["commute"]!!.placeholder)
        assertFalse(f["commute"]!!.typedNumber)
        assertEquals(5, f["commute"]!!.step)
        assertEquals("9:00 AM", f["start"]!!.text)
        assertEquals(FieldKind.CHOICE, f["kind"]!!.kind)
        assertEquals(listOf("on", "off", "anchor"), f["rotating"]!!.reveals)
        assertNull(r.schema.fold)
        val shown = FormValidation.visibleKeys(r.schema, r.schema.initialValues())
        assertTrue("days" in shown && "on" !in shown)
    }

    @Test
    fun decodesAnEditOfARotatingShift() {
        val r = sheetOf("form-rule-rotating") as RuleSheetModel
        assertEquals("r-rot", r.id)
        assertEquals("Edit Nurse shift", r.title)
        assertEquals("Remove", r.remove!!.label)
        assertEquals("ruleRemove", r.remove!!.command)
        val shown = FormValidation.visibleKeys(r.schema, r.schema.initialValues())
        assertTrue("days" !in shown && "on" in shown && "anchor" in shown)
        assertEquals("7:00 PM", r.schema.fields.first { it.key == "start" }.text)
    }

    @Test
    fun decodesTheGoalForms() {
        val g = sheetOf("form-goal") as GoalFormSheetModel
        assertEquals("HABIT_READING", g.arch)
        assertEquals("goalAdd", g.save.command)
        assertEquals("Add goal", g.save.label)
        assertNull(g.remove)
        assertEquals("Give the goal a name", g.schema.fields.first().required)
        assertEquals("e.g. Learn guitar", g.schema.fields.first().placeholder)
        val fold = g.schema.fold!!
        assertEquals("Add a measurable target (optional)", fold.label)
        assertTrue(fold.note!!.startsWith("Life Director paces sessions"))
        assertEquals(listOf("units", "unit", "rate", "deadline"), fold.fields.map { it.key })
        assertTrue(fold.fields[0].typedNumber)
        assertEquals(FieldValue.Number(null), fold.fields[0].value)
        val c = sheetOf("form-goal-custom") as GoalFormSheetModel
        assertEquals("CUSTOM", c.id)
        assertNull(c.schema.fold)
        assertEquals(
            listOf("title", "tag", "minutes", "perWeek", "physical", "mental", "priority", "level"),
            c.schema.fields.map { it.key },
        )
        assertEquals(12, c.schema.fields[1].options.size)
    }

    @Test
    fun decodesTheGoalTypeGrid() {
        val t = sheetOf("form-goal-type") as GoalTypeSheetModel
        assertEquals("Add a goal", t.title)
        assertTrue(t.text.startsWith("Pick the closest kind"))
        assertEquals(9, t.options.size)
        assertEquals("FITNESS_HYPERTROPHY", t.options[0].arch)
        assertEquals("dumbbell", t.options[0].icon)
        assertEquals("CUSTOM", t.options.last().arch)
        assertEquals("Something else", t.options.last().title)
    }

    @Test
    fun readsACommitmentGone() {
        val j =
            fixture("form-rule")
                .put("sheet", JSONObject().put("kind", "rule").put("id", "r-x").put("gone", true))
        assertEquals(
            GoneSheet("rule", "r-x"),
            WebLifeDirectorRepository().apply { accept(j, true) }.state.value!!.sheet,
        )
    }

    @Test
    fun refusesMalformedFormSheets() {
        rejects(broken("form-rule") { remove("title") })
        rejects(broken("form-rule") { remove("remove") })
        rejects(broken("form-rule") { remove("id") })
        rejects(broken("form-rule") { put("note", 3) })
        rejects(broken("form-rule") { getJSONObject("save").put("command", "goalAdd") })
        // Remove belongs to an edit only, and an edit has it
        rejects(
            broken("form-rule") {
                put("remove", JSONObject().put("label", "Remove").put("command", "ruleRemove"))
            }
        )
        rejects(broken("form-rule-rotating") { put("remove", JSONObject.NULL) })
        rejects(
            broken("form-rule-rotating") { getJSONObject("remove").put("command", "goalRemove") }
        )
        rejects(broken("form-rule") { field("days").put("required", "Pick at least one day") })
        rejects(broken("form-rule") { field("on").put("group", "rot") })
        rejects(broken("form-rule") { field("start").put("value", "09:00") })
        rejects(broken("form-rule") { field("kind").put("value", "SCHOOL") })
        rejects(broken("form-goal") { getJSONObject("fold").remove("summary") })
        rejects(broken("form-goal") { getJSONObject("fold").put("label", "x").remove("note") })
        rejects(broken("form-goal") { put("id", "") })
        rejects(broken("form-goal") { getJSONObject("save").put("command", "ruleSave") })
        rejects(
            broken("form-goal") {
                put("remove", JSONObject().put("label", "Remove").put("command", "ruleRemove"))
            }
        )
        rejects(broken("form-goal-type") { getJSONArray("options").remove(8) })
        rejects(broken("form-goal-type") { getJSONArray("options").getJSONObject(0).remove("sub") })
        rejects(
            broken("form-goal-type") {
                getJSONArray("options").put(0, getJSONArray("options").getJSONObject(1))
            }
        )
        rejects(broken("form-goal-type") { put("options", JSONArray()) })
    }

    // The controller

    private class Screen(
        val repo: WebLifeDirectorRepository,
        val vm: LifeDirectorViewModel,
        val commands: DirectorRecorder,
        val emitted: MutableList<Tap>,
        val actions: DirectorActions,
    ) {
        private var sequence = 1000L

        fun push(j: JSONObject) {
            repo.accept(JSONObject(j.toString()).put("sequence", ++sequence))
            vm.projected()
        }

        val forms
            get() = vm.ui.value.forms

        val toast
            get() = vm.ui.value.toast?.text

        val last
            get() = commands.changes.last()
    }

    private fun screen(name: String, edit: JSONObject.() -> Unit = {}): Screen {
        val repo = WebLifeDirectorRepository().apply { accept(fixture(name).apply(edit), true) }
        val commands = DirectorRecorder()
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val emitted = mutableListOf<Tap>()
        return Screen(
            repo,
            vm,
            commands,
            emitted,
            DirectorActions(vm) { a, id -> emitted += Tap(a, id) },
        )
    }

    private fun Screen.answerSheet() =
        vm.onReply(DirectorReply("sheet-${commands.sheets.size}", "sheet", true))

    /** The page's reply [name] from director-replies-time.json, to the screen's latest write. */
    private fun Screen.reply(name: String) =
        vm.onReply(
            directorReply(replies.getJSONObject(name))
                .copy(requestId = "change-${commands.changes.size}")
        )

    private fun Screen.reply(ok: Boolean, changed: Boolean, message: String) =
        vm.onReply(DirectorReply("change-${commands.changes.size}", "do", ok, message, changed))

    /** A new commitment's sheet open, its model sent. */
    private fun newRule(): Screen {
        val s = screen("form-rule")
        s.vm.forms.openRule("")
        s.answerSheet()
        return s
    }

    /** "Add a goal" from the Goals section's button, then Read More picked and its form sent. */
    private fun readMore(): Screen {
        val s = screen("form-goal-type")
        s.actions.tap(Tap("addGoal"))
        assertEquals("goalType" to "", s.commands.sheets.last())
        s.answerSheet()
        assertNotNull(s.vm.forms.goalType)
        s.actions.forms.pick("HABIT_READING")
        assertEquals("goalForm" to "HABIT_READING", s.commands.sheets.last())
        s.push(fixture("form-goal"))
        s.answerSheet()
        return s
    }

    @Test
    fun addGoalOpensTheGridNatively() {
        val s = readMore()
        assertTrue(s.emitted.isEmpty())
        assertEquals("HABIT_READING", s.vm.forms.form!!.id)
        assertEquals("HABIT_READING", s.forms.picked)
        assertTrue(s.vm.forms.stepsBack(s.vm.ui.value.sheet, s.forms))
    }

    @Test
    fun withoutSetupAddGoalIsThePages() {
        val s =
            screen("form-goal-type") {
                put("covers", JSONArray(listOf("today", "week", "quests", "goals", "settings")))
            }
        s.actions.tap(Tap("addGoal"))
        assertEquals(listOf(Tap("addGoal")), s.emitted)
        assertTrue(s.commands.sheets.isEmpty())
    }

    @Test
    fun backStepsFromTheFormToTheGrid() {
        val s = readMore()
        assertTrue(s.vm.forms.back())
        assertEquals(OpenSheet("goalType", ""), s.vm.ui.value.sheet)
        assertNull(s.forms.picked)
        // the grid is the first step: Back now closes, as any sheet
        assertFalse(s.vm.forms.stepsBack(s.vm.ui.value.sheet, s.forms))
        assertFalse(s.vm.forms.back())
    }

    @Test
    fun aGoalIsCheckedInThePagesWordsThenAdded() {
        val s = readMore()
        s.actions.forms.change("title", FieldValue.Text("  "))
        s.actions.forms.save()
        assertEquals(mapOf("title" to "Give the goal a name"), s.forms.errors)
        assertTrue(s.commands.changes.isEmpty())
        s.actions.forms.change("title", FieldValue.Text("Read 20 books"))
        assertTrue(s.forms.errors.isEmpty())
        s.actions.forms.save()
        val (name, args) = s.last
        assertEquals("goalAdd", name)
        assertEquals("HABIT_READING", args["arch"])
        assertEquals("Read 20 books", args["title"])
        assertEquals("3", args["priority"])
        assertEquals("NOVICE", args["level"])
        assertEquals(JSONObject.NULL, args["units"])
        assertEquals("", args["deadline"])
        val op = args["op"] as String
        assertEquals(op, s.vm.forms.operation("goalAdd", "HABIT_READING"))
        assertEquals("goalAdd", s.forms.pending)
        // one at a time
        s.actions.forms.save()
        assertEquals(1, s.commands.changes.size)
        s.reply("goalAdd")
        assertNull(s.forms.pending)
        assertNull(s.vm.ui.value.sheet)
        assertEquals("" to "", s.commands.sheets.last())
        assertNull(s.vm.forms.operation("goalAdd", "HABIT_READING"))
        assertTrue(s.forms.drafts.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheOperationAndTheForm() {
        val s = readMore()
        s.actions.forms.change("title", FieldValue.Text("Read more"))
        s.actions.forms.save()
        val op = s.last.second["op"]
        s.reply("sleepSaveNotSaved")
        assertEquals(OpenSheet("goalForm", "HABIT_READING"), s.vm.ui.value.sheet)
        assertTrue(s.forms.refusal!!.startsWith("Not saved"))
        assertEquals(FieldValue.Text("Read more"), s.vm.forms.values(s.vm.forms.form!!)["title"])
        s.actions.forms.save()
        assertEquals(op, s.last.second["op"])
        // landed already on the retry: the page knows it by its op
        s.reply("goalAddAgain")
        assertNull(s.vm.ui.value.sheet)
        assertEquals("Already added", s.toast)
        assertNull(s.vm.forms.operation("goalAdd", "HABIT_READING"))
    }

    @Test
    fun aLostReplyIsSentAgainWithTheSameOperation() {
        val s = readMore()
        s.actions.forms.save()
        val op = s.last.second["op"]
        s.vm.hidden()
        s.vm.opened()
        assertNull(s.forms.pending)
        s.actions.forms.save() // no sheet open: nothing
        assertEquals(1, s.commands.changes.size)
        s.actions.tap(Tap("addGoal"))
        s.push(fixture("form-goal-type"))
        s.answerSheet()
        s.actions.forms.pick("HABIT_READING")
        s.push(fixture("form-goal"))
        s.answerSheet()
        s.actions.forms.save()
        assertEquals(op, s.last.second["op"])
    }

    @Test
    fun thePagesRefusalShowsUnderItsField() {
        val s = readMore()
        s.actions.forms.save()
        s.reply("goalAddNoName")
        assertEquals(mapOf("title" to "Give the goal a name"), s.forms.errors)
        assertNotNull(s.vm.ui.value.sheet)
        // a definite refusal ends the operation: nothing was added
        assertNull(s.vm.forms.operation("goalAdd", "HABIT_READING"))
        s.actions.forms.save()
        s.reply(false, false, "That is not one of the choices.")
        assertEquals("That is not one of the choices.", s.forms.refusal)
    }

    @Test
    fun aNewCommitmentSendsItsShownFieldsWithAnOperation() {
        val s = newRule()
        s.actions.forms.change("days", FieldValue.Days(emptySet()))
        s.actions.forms.save()
        assertEquals(mapOf("days" to "Pick at least one day"), s.forms.errors)
        assertTrue(s.commands.changes.isEmpty())
        // a rotating shift has no day chips: its days on and off and first day instead
        s.actions.forms.change("rotating", FieldValue.Check(true))
        s.actions.forms.change("commute", FieldValue.Number(500))
        s.actions.forms.save()
        val (name, args) = s.last
        assertEquals("ruleSave", name)
        assertTrue("days" !in args && "id" !in args)
        assertEquals(true, args["rotating"])
        assertEquals(4, args["on"])
        assertEquals("2026-10-28", args["anchor"])
        assertEquals(540, args["start"])
        assertEquals(240, args["commute"]) // kept within its box, as the page does
        assertEquals(s.vm.forms.operation("ruleSave", ""), args["op"])
        s.reply("ruleSave")
        assertNull(s.vm.ui.value.sheet)
        assertNull(s.vm.forms.operation("ruleSave", ""))
    }

    @Test
    fun theDaysAreSentAsThePageReadsThem() {
        val s = newRule()
        s.actions.forms.change("days", FieldValue.Days(setOf(5, 1, 3)))
        s.actions.forms.save()
        assertEquals(listOf(1, 3, 5), s.last.second["days"])
        assertTrue("on" !in s.last.second)
    }

    @Test
    fun anEditNamesItsCommitmentAndRemoveAsksFirst() {
        val s = screen("form-rule-rotating")
        s.vm.forms.openRule("r-rot")
        s.answerSheet()
        s.actions.forms.save()
        assertEquals("r-rot", s.last.second["id"])
        assertTrue("op" !in s.last.second)
        s.reply("ruleSaveRemoved")
        assertEquals("That commitment was removed.", s.forms.refusal)
        s.actions.forms.remove()
        val c = s.forms.confirm!!
        assertEquals("Nurse shift", c.title)
        assertEquals("Remove", c.button)
        assertTrue(s.vm.forms.stepsBack(s.vm.ui.value.sheet, s.forms))
        assertTrue(s.vm.forms.back()) // Back keeps it
        assertNull(s.forms.confirm)
        assertEquals(1, s.commands.changes.size)
        s.actions.forms.remove()
        s.actions.forms.confirmRemove()
        assertEquals("ruleRemove" to mapOf<String, Any>("id" to "r-rot"), s.last)
        s.reply("ruleRemove")
        assertNull(s.vm.ui.value.sheet)
    }

    @Test
    fun aCommitmentGoneClosesItsSheetWithThePagesWords() {
        val s = screen("form-rule-rotating")
        s.vm.forms.openRule("r-rot")
        s.answerSheet()
        s.push(
            fixture("form-rule-rotating")
                .put("sheet", JSONObject().put("kind", "rule").put("id", "r-rot").put("gone", true))
        )
        assertNull(s.vm.ui.value.sheet)
        assertEquals(SheetController.GONE, s.toast)
    }

    @Test
    fun aDraftStaysWhileTheScreenHides() {
        val s = newRule()
        s.actions.forms.change("title", FieldValue.Text("Gym"))
        s.vm.hidden()
        assertNull(s.vm.ui.value.sheet)
        s.vm.opened()
        s.vm.forms.openRule("")
        s.answerSheet()
        assertEquals(FieldValue.Text("Gym"), s.vm.forms.values(s.vm.forms.form!!)["title"])
        // Cancel keeps the draft too; a save that lands drops it
        s.actions.forms.close()
        assertNull(s.vm.ui.value.sheet)
        assertEquals(1, s.forms.drafts.size)
    }

    @Test
    fun nothingOpensOverAnotherSheet() {
        val s = newRule()
        s.vm.forms.openGoalType()
        assertEquals(1, s.commands.sheets.size)
    }
}
