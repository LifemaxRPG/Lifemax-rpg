package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.Role
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.HealthCard
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewNotice
import com.lifemax.rpg.feature.overview.domain.editorRows
import com.lifemax.rpg.feature.overview.presentation.OverviewActions
import com.lifemax.rpg.feature.overview.presentation.OverviewEditing
import com.lifemax.rpg.feature.overview.presentation.OverviewScreen
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What TalkBack is given by the native Overview (the TalkBack parity pass, OVERVIEW_INVENTORY.md
 * section 7): the merged semantics tree of the screen read in traversal order, from the page's own
 * projections. This module has no Compose test rule, so the tree comes from the view's semantics
 * owner after Paparazzi lays one frame out (SemanticsProbe); no image is written. What only a phone
 * can say (how TalkBack speaks the tree, where its focus lands) is listed in the inventory. With
 * LMA_SEMANTICS_DUMP set to a folder, each read also writes its tree there as text.
 */
class OverviewSemanticsTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 9000, density = Density(420)),
            useDeviceResolution = true,
            snapshotHandler = NoImages,
        )

    private fun cards(name: String, edit: (JSONObject) -> Unit = {}): OverviewCards {
        val j =
            JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())
                .put("tokens", ScreenshotPalette.tokens(false))
                .put("light", false)
        edit(j)
        return WebOverviewRepository().apply { accept(j, true) }.state.value!!
    }

    private fun read(
        name: String,
        cards: OverviewCards,
        notice: OverviewNotice? = null,
        editing: Boolean = false,
    ): List<A11yNode> {
        val probe = SemanticsProbe()
        paparazzi.snapshot {
            probe.Watch {
                OverviewScreen(
                    cards,
                    OverviewActions(),
                    notice = notice,
                    editing =
                        if (editing)
                            OverviewEditing(
                                open = true,
                                rows = editorRows(cards.layout, cards.tileInfo),
                                animateOpen = false,
                            )
                        else OverviewEditing(),
                    list = rememberLazyListState(),
                )
            }
        }
        System.getenv("LMA_SEMANTICS_DUMP")?.let {
            File(it, "overview_$name.txt").writeText(probe.nodes.joinToString("\n"))
        }
        return probe.nodes
    }

    private val List<A11yNode>.clickable
        get() = filter { it.clickable }

    /** The words of the node whose words start with [start]. */
    private fun List<A11yNode>.starting(start: String) =
        firstOrNull { it.text.startsWith(start) } ?: error("no node starts with \"$start\"")

    // ---- every screen state ----

    private fun everyTargetIs48(nodes: List<A11yNode>) =
        nodes.clickable.forEach {
            assertTrue("${it.text}: ${it.widthDp}x${it.heightDp} dp", it.widthDp >= 47.9f)
            assertTrue("${it.text}: ${it.widthDp}x${it.heightDp} dp", it.heightDp >= 47.9f)
        }

    /** A button inside a button is two stops for one destination, and a screen reader says both. */
    private fun noButtonInsideAButton(nodes: List<A11yNode>, except: String = "") {
        var outer: A11yNode? = null
        nodes.forEach { n ->
            if (outer != null && n.depth <= outer!!.depth) outer = null
            if (n.clickable) {
                assertTrue(
                    "\"${n.text}\" is a button inside \"${outer?.text}\"",
                    outer == null || n.text.contains(except),
                )
                outer = n
            }
        }
    }

    private fun nothingTwiceInARow(nodes: List<A11yNode>) =
        nodes.zipWithNext().forEach { (a, b) ->
            assertFalse(
                "\"${a.text}\" is read twice in a row",
                a.text.isNotEmpty() && a.text == b.text,
            )
        }

    @Test
    fun theCardsAreReadInThePagesOrderWithItsWords() {
        val c = cards("established")
        val nodes = read("established", c)
        // the head: the date, the greeting as a heading, then Edit
        assertEquals(c.head.date, nodes[0].text)
        assertTrue(nodes[1].heading)
        assertEquals(c.head.hello, nodes[1].text)
        val edit = nodes[2]
        assertEquals(Role.Button, edit.role)
        assertTrue(edit.text.contains(c.head.editA11y))
        // each tile's heading, in the layout's order, with the page's titles
        val titles =
            listOf(
                c.tiles.plan?.let { "Today’s plan" },
                c.tiles.next.title,
                c.tiles.rewards.title,
                c.tiles.apps?.title,
                c.tiles.attrs.title,
                c.tiles.week.title,
                c.tiles.recent.title,
            )
        val headings = nodes.filter { it.heading }.drop(1).map { it.text }
        val found = titles.filterNotNull().map { t -> headings.indexOfFirst { it.startsWith(t) } }
        assertTrue("a heading is missing: $headings", found.all { it >= 0 })
        assertEquals("the headings are out of order: $headings", found.sorted(), found)
        everyTargetIs48(nodes)
        // Activity today when health sync is off: the card opens the Health page and its button
        // goes
        // to Settings, two destinations, so two stops
        noButtonInsideAButton(nodes, (c.tiles.health as HealthCard.Off).button)
        nothingTwiceInARow(nodes)
    }

    @Test
    fun theWeekIsOneSelectableGroupAndTheDayReadoutIsLive() {
        val c = cards("established")
        val nodes = read("week", c)
        val days = nodes.filter { it.role == Role.RadioButton }
        assertEquals(c.tiles.week.bars.size, days.size)
        assertEquals("exactly one day is selected", 1, days.count { it.selected == true })
        days.zip(c.tiles.week.bars).forEach { (n, bar) ->
            assertTrue("a day reads the page's label", n.text.contains(bar.a11y))
        }
        val live = nodes.filter { it.live }
        assertEquals("only the selected day's readout is live", 1, live.size)
        val tip = c.tiles.week.bars[c.tiles.week.selected].tip!!
        assertTrue(live[0].text.contains(tip.label))
        // the readout sits right after the days, before the top attributes
        assertTrue(nodes.indexOf(live[0]) > nodes.indexOf(days.last()))
    }

    @Test
    fun theGemsReadTheirNameLevelWeekAndDot() {
        val c = cards("established")
        val nodes = read("gems", c)
        val gems =
            nodes.filter { n ->
                n.clickable &&
                    c.tiles.attrs.gems.any { g ->
                        n.text.contains(g.week) && n.text.contains(g.short)
                    }
            }
        assertEquals(c.tiles.attrs.gems.size, gems.size)
        c.tiles.attrs.gems.zip(gems).forEach { (g, n) ->
            // one description holds all four, so it does not matter which of words and
            // description a screen reader prefers
            assertEquals("[${g.short}, ${g.level}, ${g.week}, ${g.dot.text}]", n.text)
        }
    }

    @Test
    fun theRewardsCardIsOneButtonAndItsLinkIsNotASecondStop() {
        val c = cards("established")
        val nodes = read("rewards", c)
        val card = nodes.starting(c.tiles.rewards.title)
        assertEquals(Role.Button, card.role)
        assertTrue(
            "the link's words are part of the card",
            card.text.contains(c.tiles.rewards.link),
        )
        assertTrue(
            "no second button for Shop",
            nodes.none { it.clickable && it.text == c.tiles.rewards.link },
        )
    }

    @Test
    fun anAppsPlusIsDrawnNotRead() {
        val c = cards("health")
        val nodes = read("apps", c)
        val add = c.tiles.apps!!.add
        val node = nodes.single { it.clickable && it.text.contains(add) }
        assertEquals("only the page's label, not the plus", "[$add]", node.text)
        // every shortcut is a button read by the page's own label
        c.tiles.apps!!.items.forEach { a ->
            assertTrue(nodes.any { it.clickable && it.text.contains(a.a11y) })
        }
    }

    @Test
    fun theHealthCardAndItsDaysAreReadWithTheirPageWords() {
        val c = cards("health")
        val nodes = read("health", c)
        val on = c.tiles.health as HealthCard.On
        val card = nodes.starting(on.title)
        assertEquals(Role.Button, card.role)
        assertTrue(card.text.contains(on.steps))
        on.days.forEach { d ->
            assertTrue(nodes.any { it.text.contains(d.a11y) && it.text.contains(d.label) })
        }
    }

    @Test
    fun aDoneMoveKeepsThePagesCheckMarkAndAnOpenOneHidesItsArrow() {
        val c =
            cards("established") { j ->
                val m = j.getJSONObject("tiles").getJSONObject("next").getJSONArray("moves")
                m.getJSONObject(0).put("done", true).put("cta", "")
                m.getJSONObject(1).put("done", false).put("cta", "")
            }
        val nodes = read("done", c)
        val moves = c.tiles.next.moves
        assertTrue("a done move says ✓", nodes.starting(moves[0].title).text.endsWith("✓"))
        assertFalse(
            "an open one does not read its arrow",
            nodes.starting(moves[1].title).text.contains("›"),
        )
    }

    @Test
    fun theTipComesRightAfterTheHeadWithAFullSizeDismiss() {
        val c = cards("tip")
        val nodes = read("tip", c)
        val tip = c.tip!!
        val i = nodes.indexOfFirst { it.text == tip.title }
        assertTrue("the tip follows Edit", i == 3)
        val dismiss = nodes.single { it.text.contains(tip.dismiss) }
        assertEquals(Role.Button, dismiss.role)
        assertTrue(dismiss.widthDp >= 47.9f && dismiss.heightDp >= 47.9f)
    }

    @Test
    fun thePagesMessageIsALiveRegion() {
        val c = cards("notice")
        val nodes = read("notice", c, notice = c.notice)
        val live = nodes.filter { it.live && it.text.contains(c.notice!!.text) }
        assertEquals("the message is announced once, by one live node", 1, live.size)
        // the week's readout is the only other live region
        assertEquals(2, nodes.count { it.live })
    }

    @Test
    fun theCatCardIsOneBigButtonForTheLiveRoom() {
        for (name in listOf("pet", "cat-gone")) {
            val c = cards(name)
            val nodes = read(name, c)
            val cat = c.tiles.cat!!
            val node = nodes.filter { it.clickable }.single { it.text.endsWith(cat.name) }
            assertEquals(Role.Button, node.role)
            everyTargetIs48(listOf(node))
        }
    }

    @Test
    fun theEditorOpensInPlaceAndTheEditButtonSaysItIsOpen() {
        val c = cards("established")
        val closed = read("editor-closed", c)
        val opened = read("editor", c, editing = true)
        assertEquals("expand", closed.single { it.text.contains(c.head.editA11y) }.expands)
        assertEquals("collapse", opened.single { it.text.contains(c.head.editA11y) }.expands)
        // Edit stays where it was, and the editor is the next thing read: its heading, its hint,
        // Done, one row per tile with the moves as actions, Reset; none of the tiles
        val e = opened.indexOfFirst { it.text == c.editor.title }
        assertEquals("the editor's heading follows Edit", 3, e)
        assertTrue(opened[e].heading)
        assertEquals(c.editor.hint, opened[e + 1].text)
        assertEquals(Role.Button, opened[e + 2].role)
        assertTrue(opened[e + 2].text.startsWith(c.editor.done))
        val rows = opened.filter { it.actions.isNotEmpty() }
        assertEquals(c.layout.order.size, rows.size)
        rows.forEach { r ->
            assertTrue(
                "a row offers hide or show",
                r.actions.any { a -> a.startsWith(c.editor.hide) || a.startsWith(c.editor.show) },
            )
            assertTrue(
                "a row offers a move",
                r.actions.any { a -> a == c.editor.moveUp || a == c.editor.moveDown },
            )
        }
        val reset = opened.single { it.text == c.editor.reset }
        assertEquals(Role.Button, reset.role)
        everyTargetIs48(opened)
        assertTrue(
            "no tile is left behind the editor",
            opened.none { it.text.startsWith(c.tiles.next.moves[0].title) },
        )
        assertNotNull(opened.firstOrNull { it.text == c.editor.reset })
    }

    @Test
    fun theGlanceReadsItsHeadThenItsDialAndItsFoot() {
        val c =
            cards("established") { j ->
                val glance =
                    JSONObject(
                        javaClass.getResource("/fixtures/overview-glance-day.json")!!.readText()
                    )
                j.getJSONObject("tiles").put("plan", glance)
            }
        val nodes = read("glance", c)
        val plan = c.tiles.plan!!
        val head = nodes.indexOfFirst { it.heading && it.text == plan.title }
        assertTrue("the glance's title is a heading", head >= 0)
        assertEquals("then its link", plan.link, nodes[head + 1].text)
        assertEquals(Role.Button, nodes[head + 1].role)
        assertTrue("the foot is read after the dial", nodes.any { it.text == plan.foot!!.text })
        // every arc of the dial is a 48 dp node named by the page (the ones that do something are
        // buttons)
        plan.dial!!
            .arcs
            .filter { it.label.isNotEmpty() }
            .forEach { arc ->
                val node = nodes.firstOrNull { it.text.contains(arc.label) }
                assertNotNull("an arc is named: ${arc.label}", node)
                assertTrue(node!!.widthDp >= 47.9f && node.heightDp >= 47.9f)
                assertEquals(arc.tappable, node.clickable)
            }
        nothingTwiceInARow(nodes)
    }
}
