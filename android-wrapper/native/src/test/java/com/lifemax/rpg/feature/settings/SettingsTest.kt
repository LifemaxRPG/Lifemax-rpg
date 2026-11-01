package com.lifemax.rpg.feature.settings

import com.lifemax.rpg.feature.settings.data.WebSettingsRepository
import com.lifemax.rpg.feature.settings.data.toEvent
import com.lifemax.rpg.feature.settings.data.toJson
import com.lifemax.rpg.feature.settings.data.toSettingsReply
import com.lifemax.rpg.feature.settings.domain.ButtonStyle
import com.lifemax.rpg.feature.settings.domain.ControlOp
import com.lifemax.rpg.feature.settings.domain.FieldInput
import com.lifemax.rpg.feature.settings.domain.SettingsCommand
import com.lifemax.rpg.feature.settings.domain.SettingsControl
import com.lifemax.rpg.feature.settings.domain.SettingsPageAction
import com.lifemax.rpg.feature.settings.domain.SettingsProjection
import com.lifemax.rpg.feature.settings.domain.SettingsReply
import com.lifemax.rpg.feature.settings.domain.SettingsRow
import com.lifemax.rpg.feature.settings.presentation.Asked
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel
import com.lifemax.rpg.feature.settings.presentation.components.clock
import com.lifemax.rpg.feature.settings.presentation.components.key
import com.lifemax.rpg.feature.settings.presentation.components.rowBoxes
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SettingsTest {
    private fun fixture(name: String = "default") =
        JSONObject(javaClass.getResource("/fixtures/settings-$name.json")!!.readText())

    private fun decode(name: String = "default") =
        WebSettingsRepository().apply { accept(fixture(name), true) }.state.value!!

    private fun JSONObject.card(id: String): JSONObject {
        val cards = getJSONArray("cards")
        return (0 until cards.length())
            .map { cards.getJSONObject(it) }
            .first { it.getString("id") == id }
    }

    private fun JSONObject.row(card: String, index: Int): JSONObject =
        card(card).getJSONArray("rows").getJSONObject(index)

    private fun JSONObject.firstControl(card: String, row: Int): JSONObject =
        row(card, row).getJSONArray("controls").getJSONObject(0)

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

    private inline fun <reified T : SettingsControl> SettingsProjection.control(id: String): T =
        controls().first { it.id == id } as T

    @Test
    fun decodesTheWebProjection() {
        val p = decode()
        assertEquals(17, p.tokens.size)
        assertFalse(p.light)
        assertEquals("RULES & DATA", p.head.eyebrow)
        assertEquals("Settings & data", p.head.title)
        assertEquals(listOf("look", "voice", "phone", "game", "data"), p.categories.map { it.id })
        assertEquals("Look & feel", p.categories.first().name)
        assertEquals("look", p.category)
        assertEquals("Classic Settings", p.classic.label)
        assertNull(p.notice)
        assertEquals(23, p.cards.size)
        assertEquals(8, p.cardsIn("look").size)
        assertEquals(listOf("appearance", "tabsCard", "lmaNavCard"), p.cards.take(3).map { it.id })
        // every control of the page's phone, each with its own id (inventory section 9, item 6)
        assertEquals(133, p.controls().size)
        assertEquals(133, p.controls().map { it.id }.toSet().size)

        val light = p.control<SettingsControl.Switch>("lightMode")
        assertEquals("Enable light mode", light.label)
        assertEquals(ControlOp.Toggle, light.op)
        assertFalse(light.on)
        // a switch Android asks about before it turns on is the page's while off
        assertEquals(ControlOp.Reveal, p.control<SettingsControl.Switch>("health").op)
        assertFalse(p.control<SettingsControl.Switch>("backup").enabled)

        val strength = p.control<SettingsControl.Choice>("haptics.strength")
        assertEquals("normal", strength.value)
        assertEquals(listOf("Light", "Normal", "Strong", "Max"), strength.options.map { it.label })
        assertEquals(3, p.control<SettingsControl.Select>("cardArt").options.size)

        val tabs = p.cards.first { it.id == "tabsCard" }.rows.filterIsInstance<SettingsRow.Tab>()
        assertEquals(11, tabs.size)
        assertEquals("dashboard", tabs.first().view)
        assertEquals("grid", tabs.first().icon)
        assertFalse(tabs.first().earlier!!.enabled)
        assertEquals("Move Overview later", tabs.first().later!!.description)
        val extra = tabs.last()
        assertEquals("settings", extra.view)
        assertFalse(extra.visible)
        assertNull(extra.earlier)
        assertNull(extra.later)
        assertEquals("Show Settings in the bar", extra.show.label)

        val profile = p.cards.first { it.id == "character-profile" }
        assertEquals("game", profile.category)
        val picture = (profile.rows[0] as SettingsRow.Line).controls[0] as SettingsControl.Avatar
        assertEquals("N", picture.initial)
        assertEquals("", picture.image)
        val name = (profile.rows[1] as SettingsRow.Field).controls.single() as SettingsControl.Field
        assertEquals(FieldInput.Text, name.input)
        assertEquals("The Life Architect", name.placeholder)
        val delete = p.cards.first { it.id == "data-management" }.rows[4] as SettingsRow.Line
        assertTrue(delete.danger)
        assertEquals(ButtonStyle.Danger, (delete.controls.single() as SettingsControl.Button).style)
        val backup = p.cards.first { it.id == "lmaBakCard" }.rows.first() as SettingsRow.Note
        assertTrue(backup.warn)
    }

    @Test
    fun decodesEveryFixture() {
        val many = decode("many")
        assertEquals("phone", many.category)
        assertEquals(ControlOp.Toggle, many.control<SettingsControl.Switch>("health").op)
        assertEquals(ControlOp.Toggle, many.control<SettingsControl.Switch>("calendar").op)
        assertTrue(many.control<SettingsControl.Switch>("backup").enabled)
        assertEquals("strong", many.control<SettingsControl.Choice>("haptics.strength").value)
        val times = many.controls().filterIsInstance<SettingsControl.Field>()
        assertTrue(times.any { it.input == FieldInput.Time && it.value == "17:00" })
        val dev = many.cards.first { it.id == "devModeCard" }
        assertEquals(16, dev.rows.size) // with the native tab swipe's feel (testing) at its end
        assertTrue(dev.rows[1] is SettingsRow.Note)

        assertTrue(decode("light").light)

        val bar = decode("bar").cards.first { it.id == "tabsCard" }.rows
        val tabs = bar.filterIsInstance<SettingsRow.Tab>()
        assertEquals("quests", tabs[1].view)
        assertEquals(listOf("pet", "money"), tabs.filter { !it.visible }.map { it.view })
        val settings = tabs.first { it.view == "settings" }
        assertTrue(settings.visible)
        assertTrue(settings.earlier != null && settings.later != null)
        // the page's gear, once Settings is in the bar as before
        assertEquals("gear", settings.icon)
        assertTrue(tabs.all { it.icon.isNotEmpty() })
    }

    /** What TalkBack calls each control: the page gives every one a name, in every fixture. */
    @Test
    fun everyControlHasAName() {
        for (name in listOf("default", "many", "light", "bar")) {
            val unnamed =
                decode(name)
                    .controls()
                    .filter { control ->
                        when (control) {
                            is SettingsControl.Switch -> control.label
                            is SettingsControl.Select -> control.label
                            is SettingsControl.Field -> control.label
                            is SettingsControl.Choice -> control.label
                            else -> null // a button says its words, the picture its title
                        }?.isBlank() == true
                    }
                    .map { it.id }
            assertEquals(name, emptyList<String>(), unnamed)
        }
        assertEquals(
            "Class to switch to",
            decode("many").control<SettingsControl.Select>("#devClassSel").label,
        )
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebSettingsRepository()
        repo.accept(fixture().put("sequence", 2), true)
        repo.accept(fixture("light"))
        assertFalse(repo.state.value!!.light)
        repo.accept(fixture("light").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("settings-fixture", repo.state.value!!.session)
        assertFalse(repo.state.value!!.light)
        repo.accept(fixture("light").put("sequence", 3))
        assertTrue(repo.state.value!!.light)
        repo.accept(fixture("many").put("webSessionId", "other"), establishSession = true)
        assertEquals("other", repo.state.value!!.session)
        assertEquals("phone", repo.state.value!!.category)
    }

    @Test
    fun malformedProjectionsKeepTheLastGoodOne() {
        val repo = WebSettingsRepository()
        repo.accept(fixture(), true)
        val broken: List<Pair<String, JSONObject.() -> Unit>> =
            listOf(
                "another protocol" to { put("protocol", 2) },
                "the protocol as text" to { put("protocol", "1") },
                "the sequence as text" to { put("sequence", "9") },
                "the sequence with a fraction" to { put("sequence", 9.5) },
                "the session as a number" to { put("webSessionId", 7) },
                "no tokens" to { remove("tokens") },
                "a token missing" to { getJSONObject("tokens").remove("accent") },
                "a token that is not a number" to { getJSONObject("tokens").put("bg", "dark") },
                "a token as numeric text" to { getJSONObject("tokens").put("bg", "4278190080") },
                "light not a boolean" to { put("light", "no") },
                "no head" to { remove("head") },
                "no chips" to { put("categories", JSONArray()) },
                "two chips with one id" to
                    {
                        getJSONArray("categories").getJSONObject(1).put("id", "look")
                    },
                "a selected category that is no chip" to { put("category", "sound") },
                "no classic row" to { remove("classic") },
                "two cards with one id" to
                    {
                        getJSONArray("cards").getJSONObject(1).put("id", "appearance")
                    },
                "a card on no chip" to { card("appearance").put("category", "sound") },
                "a card without an id" to { card("appearance").put("id", "") },
                "a title that is a number" to { card("appearance").put("title", 7) },
                "an unknown kind of row" to { row("appearance", 0).put("type", "slider") },
                "a row without its hint" to { row("appearance", 0).remove("hint") },
                "a row whose danger is text" to { row("appearance", 0).put("danger", "yes") },
                "a note holding a switch" to
                    {
                        row("lmaBakCard", 0)
                            .put(
                                "controls",
                                JSONArray()
                                    .put(JSONObject(firstControl("appearance", 0).toString())),
                            )
                        firstControl("appearance", 0).put("id", "elsewhere")
                    },
                "an empty row of controls" to { row("tabsCard", 11).put("controls", JSONArray()) },
                "a form field holding a button" to
                    {
                        row("character-profile", 1)
                            .getJSONArray("controls")
                            .put(JSONObject(firstControl("tabsCard", 11).toString()).put("id", "x"))
                    },
                "a bar row whose box is a button" to
                    {
                        row("tabsCard", 0)
                            .put(
                                "show",
                                JSONObject(firstControl("tabsCard", 11).toString()).put("id", "y"),
                            )
                    },
                "a bar row's arrow that is a switch" to
                    {
                        row("tabsCard", 0)
                            .put(
                                "earlier",
                                JSONObject(firstControl("appearance", 0).toString()).put("id", "z"),
                            )
                    },
                "a bar row without its view" to { row("tabsCard", 0).put("view", "") },
                "two controls with one id" to
                    {
                        firstControl("appearance", 1).put("id", "lightMode")
                    },
                "a control without an id" to { firstControl("appearance", 0).put("id", "") },
                "an unknown kind of control" to
                    {
                        firstControl("appearance", 0).put("kind", "slider")
                    },
                "an unknown op" to { firstControl("appearance", 0).put("op", "slide") },
                "a switch pressed" to { firstControl("appearance", 0).put("op", "press") },
                "a field toggled" to { firstControl("xp-rules", 0).put("op", "toggle") },
                "a button chosen" to { firstControl("tabsCard", 11).put("op", "choose") },
                "an unknown button style" to { firstControl("tabsCard", 11).put("style", "ghost") },
                "an unknown input" to { firstControl("xp-rules", 0).put("input", "date") },
                "a switch without its state" to { firstControl("appearance", 0).remove("on") },
                "enabled as text" to { firstControl("appearance", 0).put("enabled", "true") },
                "a tick as text" to { firstControl("appearance", 0).put("on", "true") },
                "light as text" to { put("light", "false") },
                "options that are not a list" to
                    {
                        firstControl("lmaNavCard", 3).put("options", 3)
                    },
                "a picture from the web" to
                    {
                        firstControl("character-profile", 0)
                            .put("image", "https://example.com/me.png")
                    },
                "a notice numbered 0" to
                    {
                        put("notice", JSONObject().put("id", 0).put("text", "Saved"))
                    },
                "a notice without words" to { put("notice", JSONObject().put("id", 3)) },
                "a notice numbered as text" to
                    {
                        put("notice", JSONObject().put("id", "3").put("text", "Saved"))
                    },
            )
        for ((what, change) in broken) {
            val sequence = repo.state.value!!.sequence + 1
            val projection = fixture().put("sequence", sequence).put("light", true)
            projection.change()
            try {
                repo.accept(projection)
                fail("accepted $what")
            } catch (e: Exception) {
                // rejected, as it should be
            }
            assertEquals(what, 1L, repo.state.value!!.sequence)
            assertFalse(what, repo.state.value!!.light)
        }
        // the next good projection is taken
        repo.accept(fixture().put("sequence", 50).put("light", true))
        assertTrue(repo.state.value!!.light)
    }

    @Test
    fun thePagesStateOfTheMomentIsDrawnAsItComes() {
        val repo = WebSettingsRepository()
        val early = fixture()
        // Card art before its renderer filled it, a form field whose inputs are hidden, a picture
        early.firstControl("qfxCard", 3).put("options", JSONArray()).put("value", "")
        early.row("character-profile", 3).put("controls", JSONArray())
        early.firstControl("character-profile", 0).put("image", "data:image/png;base64,AAAA")
        early.put("notice", JSONObject().put("id", 4).put("text", "Light mode on"))
        repo.accept(early, true)
        val p = repo.state.value!!
        assertTrue(p.control<SettingsControl.Select>("cardArt").options.isEmpty())
        val weight = p.cards.first { it.id == "character-profile" }.rows[3] as SettingsRow.Field
        assertTrue(weight.controls.isEmpty())
        assertEquals("Light mode on", p.notice!!.text)
    }

    // ---------- the view model ----------

    private class Sent(val command: SettingsCommand, val requestId: String)

    private class Harness(name: String = "default") {
        val repo = WebSettingsRepository().apply { accept(JSONObject(read(name)), true) }
        val sent = mutableListOf<Sent>()
        var now = 0L
        private var ids = 0
        val vm =
            SettingsViewModel(repo, { c, id -> sent += Sent(c, id) }, { "r${++ids}" }, { now })
                .apply { onOpen() }

        fun p() = repo.state.value!!

        fun action(): SettingsPageAction? = vm.actions.tryReceive().getOrNull()

        /** The page's answer to the last command, with a fresh projection in which [change] ran. */
        fun answer(
            ok: Boolean = true,
            message: String = "",
            reveal: String? = null,
            change: JSONObject.() -> Unit = {},
        ) {
            val last = sent.last()
            val next = JSONObject(read("default")).put("sequence", p().sequence + 1)
            next.change()
            repo.accept(next)
            vm.onUpdate()
            vm.onReply(
                SettingsReply(last.command.name, last.requestId, ok, ok, message, true, reveal)
            )
        }

        companion object {
            fun read(name: String) =
                SettingsTest::class.java.getResource("/fixtures/settings-$name.json")!!.readText()
        }
    }

    @Test
    fun aSwitchAsksThePageAndShowsTheStateAskedForUntilItAnswers() {
        val h = Harness()
        val light = h.p().control<SettingsControl.Switch>("lightMode")
        h.vm.toggle(light)
        assertEquals(SettingsCommand.Toggle("lightMode", true), h.sent.single().command)
        assertEquals(Asked.On(true), h.vm.ui.value.pending["lightMode"])
        assertTrue(h.vm.ui.value.on(light))
        // a second tap while the page has not answered asks nothing more
        h.vm.toggle(light)
        assertEquals(1, h.sent.size)
        h.answer(message = "Light mode on") {
            getJSONArray("cards")
                .getJSONObject(0)
                .getJSONArray("rows")
                .getJSONObject(0)
                .getJSONArray("controls")
                .getJSONObject(0)
                .put("on", true)
        }
        assertTrue(h.vm.ui.value.pending.isEmpty())
        assertTrue(h.vm.ui.value.on(h.p().control("lightMode")))
        assertEquals("Light mode on", h.vm.ui.value.toast!!.text)
        assertNull(h.action())
    }

    @Test
    fun theProjectionIsTheTruthOnceThePageHasAnswered() {
        val h = Harness()
        val nav = h.p().control<SettingsControl.Switch>("tabs.pet.show")
        h.vm.toggle(nav)
        // the page refused (keep at least two): the projection still shows the tab, and so does
        // the screen, with the page's words
        h.answer(ok = false, message = "Keep at least two tabs in the bar.")
        assertTrue(h.vm.ui.value.on(h.p().control("tabs.pet.show")))
        assertEquals("Keep at least two tabs in the bar.", h.vm.ui.value.toast!!.text)
    }

    @Test
    fun aSwitchThePageKeepsIsRevealedAndTheScreenTakesNoMoreTaps() {
        val h = Harness()
        h.vm.toggle(h.p().control("health"))
        assertEquals(SettingsPageAction.Reveal("health"), h.action())
        assertTrue(h.sent.isEmpty())
        // the screen is closing: nothing else reaches the page
        h.vm.toggle(h.p().control("lightMode"))
        h.vm.classic()
        h.vm.selectCategory("phone")
        assertTrue(h.sent.isEmpty())
        assertNull(h.action())
        // opened again, it takes taps
        h.vm.onOpen()
        h.vm.toggle(h.p().control("lightMode"))
        assertEquals(1, h.sent.size)
    }

    @Test
    fun disabledControlsTakeNoTap() {
        val h = Harness()
        h.vm.toggle(h.p().control("backup"))
        h.vm.choose(h.p().control<SettingsControl.Select>("backup.days"), "7")
        h.vm.press(h.p().control("tabs.dashboard.earlier"))
        assertTrue(h.sent.isEmpty())
        assertNull(h.action())
    }

    @Test
    fun choicesAskForAnOptionTheControlHas() {
        val h = Harness()
        val scrub = h.p().control<SettingsControl.Select>("scrubSpeed")
        h.vm.choose(scrub, scrub.value)
        h.vm.choose(scrub, "warp")
        assertTrue(h.sent.isEmpty())
        val other = scrub.options.first { it.value != scrub.value }.value
        h.vm.choose(scrub, other)
        assertEquals(SettingsCommand.Choose("scrubSpeed", other), h.sent.single().command)
        assertEquals(other, h.vm.ui.value.value(scrub))

        val strength = h.p().control<SettingsControl.Choice>("haptics.strength")
        h.vm.choose(strength, "max")
        assertEquals(SettingsCommand.Choose("haptics.strength", "max"), h.sent.last().command)
        assertEquals("max", h.vm.ui.value.value(strength))
        // a form's select is the page's
        h.vm.choose(h.p().control<SettingsControl.Select>("#characterBuild"), "Athlete")
        assertEquals(SettingsPageAction.Reveal("#characterBuild"), h.action())
        assertEquals(2, h.sent.size)
    }

    @Test
    fun buttonsArePressedOrRevealed() {
        val h = Harness()
        h.vm.press(h.p().control("tabs.quests.earlier"))
        assertEquals(SettingsCommand.Press("tabs.quests.earlier"), h.sent.single().command)
        assertEquals(Asked.Press, h.vm.ui.value.pending["tabs.quests.earlier"])
        h.answer()
        assertTrue(h.vm.ui.value.pending.isEmpty())
        val check =
            h.p().controls().first { it is SettingsControl.Button && it.label == "Check now" }
        h.vm.press(check as SettingsControl.Button)
        assertEquals(SettingsPageAction.Reveal(check.id), h.action())
        assertEquals(1, h.sent.size)
    }

    @Test
    fun fieldsAndThePictureAreThePages() {
        val h = Harness()
        val name = h.p().control<SettingsControl.Field>("#characterName")
        h.vm.reveal(name)
        assertEquals(SettingsPageAction.Reveal("#characterName"), h.action())
        assertTrue(h.sent.isEmpty())
    }

    @Test
    fun aChipIsSelectedAtOnceThenByThePage() {
        val h = Harness()
        h.vm.selectCategory("phone")
        assertEquals("phone", h.vm.ui.value.category(h.p()))
        assertEquals(SettingsCommand.Category("phone"), h.sent.single().command)
        // the same chip again, or one the page does not have, asks nothing
        h.vm.selectCategory("phone")
        h.vm.selectCategory("sound")
        assertEquals(1, h.sent.size)
        h.answer { put("category", "phone") }
        assertNull(h.vm.ui.value.category)
        assertEquals("phone", h.vm.ui.value.category(h.p()))
        // refused: the page's chip shows again
        h.vm.selectCategory("data")
        h.answer(ok = false, message = "There is no such category.") { put("category", "phone") }
        assertEquals("phone", h.vm.ui.value.category(h.p()))
    }

    @Test
    fun anAnswerThatSaysRevealHandsTheSwitchToThePage() {
        val h = Harness("many")
        h.vm.toggle(h.p().control("reminders"))
        h.answer(ok = false, message = "Android asks first", reveal = "reminders")
        assertEquals(SettingsPageAction.Reveal("reminders"), h.action())
        assertEquals("Android asks first", h.vm.ui.value.toast!!.text)
    }

    @Test
    fun anAnswerLostOnTheWayStopsHoldingItsControl() {
        val h = Harness()
        val light = h.p().control<SettingsControl.Switch>("lightMode")
        h.vm.toggle(light)
        h.vm.selectCategory("voice")
        h.now = 4_999
        h.repo.accept(fixture().put("sequence", 2))
        h.vm.onUpdate()
        assertTrue(h.vm.ui.value.pending.containsKey("lightMode"))
        h.now = 5_000
        h.repo.accept(fixture().put("sequence", 3))
        h.vm.onUpdate()
        assertTrue(h.vm.ui.value.pending.isEmpty())
        assertEquals("look", h.vm.ui.value.category(h.p()))
        h.vm.toggle(light)
        assertEquals(3, h.sent.size)
    }

    @Test
    fun eachPageToastShowsOnce() {
        val h = Harness()
        val notice = { id: Int, text: String ->
            fixture()
                .put("sequence", h.p().sequence + 1)
                .put("notice", JSONObject().put("id", id).put("text", text))
        }
        h.repo.accept(notice(3, "Backup saved"))
        h.vm.onUpdate()
        val first = h.vm.ui.value.toast!!
        assertEquals("Backup saved", first.text)
        h.vm.toastShown(first.id)
        assertNull(h.vm.ui.value.toast)
        // the same toast in the next projection, and after the screen opens again, is not repeated
        h.repo.accept(notice(3, "Backup saved"))
        h.vm.onUpdate()
        h.vm.onOpen()
        assertNull(h.vm.ui.value.toast)
        h.repo.accept(notice(4, "Backup saved"))
        h.vm.onUpdate()
        assertEquals("Backup saved", h.vm.ui.value.toast!!.text)
        assertTrue(h.vm.ui.value.toast!!.id > first.id)
    }

    @Test
    fun openingAgainForgetsWhatThePageWillNotAnswer() {
        val h = Harness()
        h.vm.toggle(h.p().control("lightMode"))
        h.vm.selectCategory("data")
        h.vm.onOpen()
        assertTrue(h.vm.ui.value.pending.isEmpty())
        assertEquals("look", h.vm.ui.value.category(h.p()))
        // a late answer to the old request changes nothing
        h.vm.onReply(SettingsReply("toggle", "r1", true, true, "", true, null))
        assertTrue(h.vm.ui.value.pending.isEmpty())
    }

    @Test
    fun theClassicPageIsAnActionOfItsOwn() {
        val h = Harness()
        h.vm.classic()
        assertEquals(SettingsPageAction.Classic, h.action())
        assertEquals("classic" to "", SettingsPageAction.Classic.toEvent())
        assertEquals("reveal" to "#x", SettingsPageAction.Reveal("#x").toEvent())
    }

    // ---------- the wire ----------

    @Test
    fun commandsAndAnswersHaveThePagesShape() {
        assertEquals(
            """{"id":"lightMode","on":true}""",
            SettingsCommand.Toggle("lightMode", true).toJson().toString(),
        )
        assertEquals(
            """{"id":"scrubSpeed","value":"fast"}""",
            SettingsCommand.Choose("scrubSpeed", "fast").toJson().toString(),
        )
        assertEquals(
            """{"id":"tabs.reset"}""",
            SettingsCommand.Press("tabs.reset").toJson().toString(),
        )
        assertEquals("""{"id":"phone"}""", SettingsCommand.Category("phone").toJson().toString())
        assertEquals(
            listOf("toggle", "choose", "press", "category"),
            listOf(
                    SettingsCommand.Toggle("a", true),
                    SettingsCommand.Choose("a", "b"),
                    SettingsCommand.Press("a"),
                    SettingsCommand.Category("a"),
                )
                .map { it.name },
        )

        val reply =
            JSONObject(
                    """{"command":"toggle","requestId":"r9","ok":false,"changed":true,""" +
                        """"saved":false,"message":"Could not save","reveal":"health"}"""
                )
                .toSettingsReply()!!
        assertEquals(
            SettingsReply("toggle", "r9", false, true, "Could not save", false, "health"),
            reply,
        )
        val plain =
            JSONObject(
                    """{"command":"press","requestId":"r2","ok":true,"changed":true,"message":""}"""
                )
                .toSettingsReply()!!
        assertTrue(plain.saved)
        assertNull(plain.reveal)
        assertNull(JSONObject("""{"command":"press","ok":true}""").toSettingsReply())
    }

    // ---------- the layout ----------

    @Test
    fun rowsKeepTheirKeysAndThePagesSpacing() {
        for (name in listOf("default", "many", "light", "bar")) {
            val p = decode(name)
            val keys =
                p.cards.flatMap { card -> card.rows.mapIndexed { i, row -> row.key(card, i) } }
            assertEquals(name, keys.size, keys.toSet().size)
        }
        val p = decode()
        val appearance = p.cards.first { it.id == "appearance" }
        val boxes = rowBoxes(appearance)
        // under the title's 12 dp margin, then .settingsline's 13 dp padding; the card's 16 under
        // the last, which has no line
        assertEquals(25f, boxes.first().top.value)
        assertEquals(13f, boxes.first().bottom.value)
        assertTrue(boxes.dropLast(1).all { it.line })
        assertFalse(boxes.last().line)
        assertEquals(29f, boxes.last().bottom.value)

        val tabs = p.cards.first { it.id == "tabsCard" }
        assertEquals("control/tabs.quests.show", tabs.rows[5].key(tabs, 5))
        // the bar editor's intro is 8 dp above its rows; Reset sits 12 dp under them
        val tabBoxes = rowBoxes(tabs)
        assertEquals(21f, tabBoxes.first().top.value)
        assertEquals(12f, tabBoxes.last().top.value)

        val haptics = rowBoxes(p.cards.first { it.id == "hapCard" })
        // the strength 10 dp under the switch's row and 8 above the tries; the paragraph 8 under
        assertEquals(listOf(25f, 10f, 8f, 8f), haptics.map { it.top.value })

        // the backup warning first: 10 dp under it before the first row's padding
        val backup = rowBoxes(p.cards.first { it.id == "lmaBakCard" })
        assertEquals(12f, backup[0].top.value)
        assertEquals(23f, backup[1].top.value)

        // the picture's own margins, collapsing with the intro's and the form's
        val profile = rowBoxes(p.cards.first { it.id == "character-profile" })
        assertEquals(14f, profile[0].top.value)
        assertEquals(16f, profile[1].top.value)
        assertEquals(12f, profile[2].top.value)

        // developer mode's tools: the class select and Switch, then "Preview start screen" in an
        // element of its own, 10 dp under them (margin-top:10px), not run together
        val dev = decode("many").cards.first { it.id == "devModeCard" }
        val select =
            dev.rows.indexOfFirst { row ->
                row is SettingsRow.Controls && row.controls.any { it.id == "#devClassSel" }
            }
        val preview = dev.rows[select + 1] as SettingsRow.Controls
        assertEquals(
            "▶ Preview start screen",
            (preview.controls.single() as SettingsControl.Button).label,
        )
        assertEquals(10f, rowBoxes(dev)[select + 1].top.value)
    }

    @Test
    fun timesAreWrittenAsThePhonesBrowserWritesThem() {
        assertEquals("05:00 PM", clock("17:00", Locale.US))
        assertEquals("07:00 AM", clock("07:00", Locale.US))
        assertEquals("17:00", clock("17:00", Locale.GERMANY))
        assertEquals("", clock(""))
        assertEquals("soon", clock("soon"))
    }
}
