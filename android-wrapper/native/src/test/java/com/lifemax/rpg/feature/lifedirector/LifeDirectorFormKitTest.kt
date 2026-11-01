package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.core.designsystem.form.FieldKind
import com.lifemax.rpg.core.designsystem.form.FieldValue
import com.lifemax.rpg.core.designsystem.form.FormOption
import com.lifemax.rpg.core.designsystem.form.FormValidation
import com.lifemax.rpg.core.designsystem.form.formRows
import com.lifemax.rpg.feature.lifedirector.data.formSchema
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDial
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDialMath
import com.lifemax.rpg.feature.lifedirector.presentation.time.MiniDialWords
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The form kit's schema decoder and checks (4B), and the mini sleep dial's arithmetic, against a
 * schema shaped as the page's rule and goal sheets (ruleSheet and goalForm in logic.js) are drawn.
 */
class LifeDirectorFormKitTest {
    private val days =
        JSONArray(
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").mapIndexed { k, l ->
                JSONObject().put("value", k).put("label", l)
            }
        )

    /** The rule sheet, in the page's order and words. */
    private fun rule(): JSONObject =
        JSONObject()
            .put(
                "fields",
                JSONArray()
                    .put(field("rTitle", "text", "Name", "Work").put("maxLength", 40))
                    .put(
                        field("rKind", "select", "Type", "WORK")
                            .put(
                                "options",
                                JSONArray()
                                    .put(option("WORK", "Work shift"))
                                    .put(option("CLASS", "School / class"))
                                    .put(option("APPOINTMENT", "Regular commitment"))
                                    .put(option("UNAVAILABLE", "Unavailable (personal)")),
                            )
                    )
                    .put(
                        field("rRot", "check", "Rotating shift pattern (e.g. 4 on / 4 off)", false)
                            .put("reveals", JSONArray(listOf("rOn", "rOff", "rAnchor")))
                            .put("hides", JSONArray(listOf("rDays")))
                    )
                    .put(
                        field("rDays", "days", "Days", JSONArray(listOf(1, 2, 3, 4, 5)))
                            .put("options", days)
                            .put("required", true)
                    )
                    .put(num("rOn", "Days on", 4, 1, 14).put("group", 0))
                    .put(num("rOff", "Days off", 4, 1, 14).put("group", 0))
                    .put(
                        field("rAnchor", "date", "First day on", "2026-11-01")
                            .put("group", 0)
                            .put("required", true)
                    )
                    .put(field("rStart", "time", "Starts", 540).put("group", 1))
                    .put(field("rEnd", "time", "Ends", 1020).put("group", 1))
                    .put(
                        num("rCommute", "Travel each way (min)", 0, 0, 240)
                            .put("step", 5)
                            .put("group", 2)
                    )
                    .put(
                        num("rUnwind", "Unwind after (min)", 30, 0, 120)
                            .put("step", 5)
                            .put("group", 2)
                    )
                    .put(field("rNote", "text", "Note", "").put("note", JSONObject.NULL)),
            )
            .put(
                "fold",
                JSONObject()
                    .put("summary", "Add a measurable target (optional)")
                    .put("note", "Life Director paces sessions to hit the date.")
                    .put(
                        "fields",
                        JSONArray()
                            .put(
                                field("gUnits", "number", "Total", JSONObject.NULL)
                                    .put("placeholder", "3600")
                            )
                            .put(field("gDeadline", "date", "By", "")),
                    ),
            )

    private fun field(key: String, kind: String, label: String, value: Any) =
        JSONObject()
            .put("key", key)
            .put("kind", kind)
            .put("label", label)
            .put("value", value)
            .put("required", false)
            .put("group", JSONObject.NULL)

    /**
     * The page's words for the required fields of this test's form (FormWords for the real ones).
     */
    private val words =
        mapOf(
            "rDays" to "Pick at least one day",
            "rAnchor" to "Pick the first day on",
            "rTitle" to "Give the goal a name",
            "rRot" to "Tick it",
        )

    private fun JSONObject.formSchema() = formSchema(words)

    private fun num(key: String, label: String, value: Int, min: Int, max: Int) =
        field(key, "number", label, value).put("min", min).put("max", max)

    private fun option(value: String, label: String) =
        JSONObject().put("value", value).put("label", label)

    /** The rule schema with field [key] changed by [change]. */
    private fun ruleWith(key: String, change: JSONObject.() -> Unit): JSONObject {
        val j = rule()
        val list = j.getJSONArray("fields")
        for (i in 0 until list.length()) if (list.getJSONObject(i).getString("key") == key)
            list.getJSONObject(i).change()
        return j
    }

    private fun refused(j: JSONObject) {
        assertThrows(Exception::class.java) { j.formSchema() }
    }

    @Test
    fun decodesTheRuleSheetInThePagesOrder() {
        val s = rule().formSchema()
        assertEquals(
            listOf(
                "rTitle",
                "rKind",
                "rRot",
                "rDays",
                "rOn",
                "rOff",
                "rAnchor",
                "rStart",
                "rEnd",
                "rCommute",
                "rUnwind",
                "rNote",
            ),
            s.fields.map { it.key },
        )
        val kinds = s.fields.associate { it.key to it.kind }
        assertEquals(FieldKind.CHOICE, kinds["rKind"])
        assertEquals(FieldKind.DAYS, kinds["rDays"])
        assertEquals(FieldKind.TIME, kinds["rStart"])
        assertEquals(FieldValue.Days(setOf(1, 2, 3, 4, 5)), s.fields[3].value)
        assertEquals(FormOption("0", "Su"), s.fields[3].options.first())
        assertEquals("Pick at least one day", s.fields[3].required)
        assertEquals(listOf("rDays"), s.fields[2].hides)
        assertEquals(FieldValue.Time(540), s.fields[7].value)
        assertEquals(5, s.fields[9].step)
        assertEquals(40, s.fields[0].maxLength)
        assertNull(s.fields[11].note)
        assertEquals("Add a measurable target (optional)", s.fold!!.label)
        assertEquals("Life Director paces sessions to hit the date.", s.fold!!.note)
        assertEquals(FieldValue.Number(null), s.fold!!.fields[0].value)
        assertTrue(s.fold!!.fields[0].typedNumber)
        assertEquals(FieldValue.Date(""), s.fold!!.fields[1].value)
        assertEquals(14, s.initialValues().size)
    }

    @Test
    fun readsChoiceAsSelect() {
        assertEquals(
            FieldKind.CHOICE,
            ruleWith("rKind") { put("kind", "choice") }.formSchema().fields[1].kind,
        )
    }

    @Test
    fun refusesWhatTheScreenCouldSendWrongly() {
        refused(ruleWith("rTitle") { remove("label") })
        refused(ruleWith("rTitle") { put("kind", "colour") })
        refused(ruleWith("rTitle") { put("value", 5) })
        refused(ruleWith("rStart") { put("value", "09:00") })
        refused(ruleWith("rStart") { put("value", 1440) })
        refused(ruleWith("rStart") { put("value", -1) })
        refused(ruleWith("rStart") { put("value", 540.5) })
        refused(ruleWith("rAnchor") { put("value", "2026-1-1") })
        refused(ruleWith("rKind") { put("value", "SCHOOL") })
        refused(ruleWith("rKind") { remove("options") })
        refused(ruleWith("rTitle") { put("options", JSONArray().put(option("a", "A"))) })
        refused(ruleWith("rDays") { put("value", JSONArray(listOf(1, 7))) })
        refused(ruleWith("rDays") { put("value", JSONArray(listOf(1, 1))) })
        refused(ruleWith("rDays") { put("value", "1,2") })
        refused(
            ruleWith("rDays") {
                put("options", JSONArray().put(JSONObject().put("value", 7).put("label", "X")))
            }
        )
        refused(ruleWith("rOn") { put("value", 15) })
        refused(ruleWith("rOn") { put("value", JSONObject.NULL) })
        refused(ruleWith("rOn") { put("min", 20) })
        refused(ruleWith("rOn") { put("step", 0) })
        refused(ruleWith("rOn") { put("min", "1") })
        refused(ruleWith("rRot") { put("value", "false") })
        refused(ruleWith("rRot") { put("reveals", JSONArray(listOf("gone"))) })
        refused(ruleWith("rRot") { put("hides", JSONArray(listOf("rRot"))) })
        refused(ruleWith("rTitle") { put("reveals", JSONArray(listOf("rKind"))) })
        refused(ruleWith("rTitle") { put("note", 5) })
        refused(ruleWith("rTitle") { put("required", "Give the goal a name") })
        refused(ruleWith("rTitle") { remove("required") })
        refused(ruleWith("rOn") { put("group", "rot") })
        refused(ruleWith("rOn") { put("group", 0.5) })
        refused(ruleWith("rOff") { put("key", "rOn") })
        refused(rule().apply { remove("fields") })
        refused(rule().apply { put("fold", "target") })
        refused(rule().apply { getJSONObject("fold").remove("summary") })
        refused(rule().apply { getJSONObject("fold").remove("note") })
    }

    @Test
    fun anOptionalKeyMayBeAbsentOrNull() {
        val s =
            ruleWith("rTitle") {
                    put("placeholder", JSONObject.NULL)
                    put("group", JSONObject.NULL)
                    put("maxLength", JSONObject.NULL)
                }
                .formSchema()
        assertNull(s.fields[0].maxLength)
        assertNull(s.fields[0].group)
        assertNull(rule().apply { put("fold", JSONObject.NULL) }.formSchema().fold)
        assertNull(rule().apply { remove("fold") }.formSchema().fold)
    }

    @Test
    fun aCheckRevealsAndHidesFields() {
        val s = rule().formSchema()
        val off = FormValidation.visibleKeys(s, s.initialValues())
        assertTrue("rDays" in off)
        assertTrue("rOn" !in off && "rOff" !in off && "rAnchor" !in off)
        val on =
            FormValidation.visibleKeys(s, s.initialValues() + ("rRot" to FieldValue.Check(true)))
        assertTrue("rDays" !in on)
        assertTrue("rOn" in on && "rAnchor" in on)
    }

    @Test
    fun checksOnlyShownFieldsInThePagesWords() {
        val s = rule().formSchema()
        val start = s.initialValues()
        assertEquals(emptyMap<String, String>(), FormValidation.errors(s, start))
        val noDays = start + ("rDays" to FieldValue.Days(emptySet()))
        assertEquals(mapOf("rDays" to "Pick at least one day"), FormValidation.errors(s, noDays))
        // a rotating shift has no day chips, as the page skips them
        val rotating = noDays + ("rRot" to FieldValue.Check(true))
        assertEquals(emptyMap<String, String>(), FormValidation.errors(s, rotating))
        val noAnchor = rotating + ("rAnchor" to FieldValue.Date(""))
        assertEquals(
            mapOf("rAnchor" to "Pick the first day on"),
            FormValidation.errors(s, noAnchor),
        )
    }

    @Test
    fun aRequiredTextOrCheckIsEmptyWhenBlankOrUnticked() {
        val s =
            ruleWith("rTitle") { put("required", true) }
                .also { j -> j.getJSONArray("fields").getJSONObject(2).put("required", true) }
                .formSchema()
        val blank = s.initialValues() + ("rTitle" to FieldValue.Text("   "))
        assertEquals(
            mapOf("rTitle" to "Give the goal a name", "rRot" to "Tick it"),
            FormValidation.errors(s, blank),
        )
    }

    @Test
    fun numbersAreKeptWithinTheirLimitsAndTextToItsLength() {
        val s = rule().formSchema()
        val commute = s.fields.first { it.key == "rCommute" }
        assertEquals(FieldValue.Number(240), FormValidation.clamp(commute, FieldValue.Number(500)))
        assertEquals(FieldValue.Number(0), FormValidation.clamp(commute, FieldValue.Number(-5)))
        assertEquals(
            FieldValue.Number(null),
            FormValidation.clamp(commute, FieldValue.Number(null)),
        )
        val title = s.fields.first()
        assertEquals(
            40,
            (FormValidation.clamp(title, FieldValue.Text("x".repeat(60))) as FieldValue.Text)
                .text
                .length,
        )
        val sent =
            FormValidation.sendable(s, s.initialValues() + ("rUnwind" to FieldValue.Number(999)))
        assertEquals(FieldValue.Number(120), sent["rUnwind"])
        assertTrue("rOn" !in sent)
        assertEquals(s.allFields.map { it.key }.filter { it in sent }, sent.keys.toList())
    }

    @Test
    fun aGroupsFieldsShareARow() {
        val rows = formRows(rule().formSchema().fields).map { r -> r.map { it.key } }
        assertEquals(
            listOf(
                listOf("rTitle"),
                listOf("rKind"),
                listOf("rRot"),
                listOf("rDays"),
                listOf("rOn", "rOff", "rAnchor"),
                listOf("rStart", "rEnd"),
                listOf("rCommute", "rUnwind"),
                listOf("rNote"),
            ),
            rows,
        )
    }

    private val clock = (0 until 96).map { "c$it" }
    private val lengths = (0 until 96).map { "l$it" }

    private fun mini(bed: Int = 1380, wake: Int = 420) =
        MiniDial(
            bed = bed,
            wake = wake,
            bedText = "page bed",
            wakeText = "page wake",
            length = "page length",
            step = 15,
            context = emptyList(),
            clock = clock,
            lengths = lengths,
            words = MiniDialWords("Sleep", "Bedtime", "Wake up", "Sleep window"),
        )

    @Test
    fun theMiniDialSnapsAsThePageDoes() {
        assertEquals(1380, MiniDialMath.snap(345f, 15))
        assertEquals(0, MiniDialMath.snap(359.9f, 15))
        // Math.round rounds a half up: 7.5 minutes is the 15
        assertEquals(15, MiniDialMath.snap(1.875f, 15))
        assertEquals(0, MiniDialMath.snap(1.8f, 15))
        assertEquals(1380f / 4 to (420f + 1440f) / 4, MiniDialMath.arc(1380, 420))
        assertEquals(60f to 120f, MiniDialMath.arc(240, 480))
        assertEquals(480, MiniDialMath.length(1380, 420))
        assertEquals(1440, MiniDialMath.length(600, 600))
        assertEquals(0, MiniDialMath.stepped(1425, 1, 15))
        assertEquals(1425, MiniDialMath.stepped(0, -1, 15))
        assertEquals(1395, MiniDialMath.stepped(1382, 1, 15))
        assertEquals(1380, MiniDialMath.stepped(1382, -1, 15))
    }

    @Test
    fun theMiniDialSpeaksThePagesWords() {
        val t = MiniDialMath.texts(mini(), 1380, 420)
        assertEquals("c92", t.bed)
        assertEquals("c28", t.wake)
        assertEquals("l32", t.length)
        // a whole day is the length list's first entry
        assertEquals("l0", MiniDialMath.texts(mini(), 600, 600).length)
        // saved times between stops keep the page's own texts
        val off = MiniDialMath.texts(mini(1387, 422), 1387, 422)
        assertEquals("page bed", off.bed)
        assertEquals("page wake", off.wake)
        assertEquals("page length", off.length)
        // once one moves, the stops' words
        val moved = MiniDialMath.texts(mini(1387, 422), 1395, 422)
        assertEquals("c93", moved.bed)
        assertEquals("page wake", moved.wake)
    }
}
