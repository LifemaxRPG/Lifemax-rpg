package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.lifedirector.domain.GlanceState
import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.data.overviewReply
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.DotKind
import com.lifemax.rpg.feature.overview.domain.HealthCard
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.MoveWarn
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Overview cards' decoder (overview.cards.v1, plan §3.3) on the page's own projections
 * (overview-cards-*.json, made by tools/gen_overview_fixtures.js from the page's data functions),
 * and the commands against the page's reply table (overview-cards-replies.json).
 */
class OverviewTest {
    private fun text(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    private fun fixture(name: String = "established") =
        JSONObject(text("overview-cards-$name.json"))

    private fun decode(j: JSONObject): OverviewCards =
        WebOverviewRepository().apply { accept(j, true) }.state.value!!

    private fun JSONObject.at(path: String): JSONObject =
        path.split('.').fold(this) { o, k -> o.getJSONObject(k) }

    /** Every projection the page's fixture generator wrote, by name. */
    private val generated: List<String> =
        File(javaClass.getResource("/fixtures/overview-cards-established.json")!!.toURI())
            .parentFile
            .list()!!
            .filter { it.startsWith("overview-cards-") && it != "overview-cards-replies.json" }
            .map { it.removePrefix("overview-cards-").removeSuffix(".json") }
            .sorted()

    @Test
    fun aSmallLastPartOnAFullRingDecodes() {
        // pace 100 with 249 XP in one attribute and 1 in another: the page draws the small part at
        // its 0.5 floor from 99.6, so it ends at 100.1 (17-runtime.js, ovHeroData)
        val j = fixture()
        val ring = j.at("tiles.hero.ring")
        val color = ring.getJSONArray("arcs").getJSONObject(0).get("color")
        ring.put(
            "arcs",
            org.json
                .JSONArray()
                .put(JSONObject().put("color", color).put("length", 97.2).put("offset", 0))
                .put(JSONObject().put("color", color).put("length", 0.5).put("offset", -99.6)),
        )
        assertEquals(2, decode(j).tiles.hero.ring.arcs.size)
    }

    @Test
    fun everyProjectionThePageWroteDecodes() {
        assertTrue("the generator's states are all here", generated.size >= 16)
        generated.forEach { name ->
            val p = decode(fixture(name))
            assertEquals(name, 11, p.tileInfo.size)
            // every image a card names is in the table, under the page's key
            val keys =
                listOfNotNull(p.tiles.hero.avatar, p.tiles.cat?.portrait) +
                    p.tiles.apps?.items?.mapNotNull { it.image }.orEmpty()
            keys.forEach { assertTrue("$name: $it", it in p.images && it.matches(KEY)) }
            p.tiles.cat?.let { assertEquals(name, it.mode == CatMode.ALIVE, it.hearts != null) }
        }
    }

    @Test
    fun theGlancesThePageWroteDecodeInsideTheProjection() {
        for (name in listOf("setup", "day", "timer")) {
            val glance = JSONObject(text("overview-glance-$name.json"))
            val p = decode(fixture().also { it.at("tiles").put("plan", glance) })
            val plan = p.tiles.plan!!
            assertEquals(
                name,
                if (name == "setup") GlanceState.SETUP else GlanceState.DAY,
                plan.state,
            )
            assertEquals(name != "setup", plan.dial != null)
        }
        // no Life Director: no glance and no tile
        val none = decode(fixture().also { it.at("tiles").put("plan", JSONObject.NULL) })
        assertNull(none.tiles.plan)
        assertFalse("plan" in none.shown)
    }

    @Test
    fun decodesAnEstablishedSave() {
        val p = decode(fixture())
        assertEquals("WEDNESDAY, OCTOBER 14", p.head.date)
        assertEquals("Good evening, Synthetic Hero", p.head.hello)
        assertEquals("athlete", p.head.classId)
        assertEquals("Edit the Overview: reorder or hide tiles", p.head.editA11y)
        assertNull(p.tip)
        // no cat on this save: the page sends no cat tile
        val all = listOf("plan", "next", "hero", "rewards", "apps", "health", "attrs", "week")
        assertEquals(all + listOf("luck", "recent"), p.shown)

        val t = p.tiles
        assertEquals(GlanceState.SETUP, t.plan!!.state)
        assertEquals(4, t.next.moves.size)
        val fading = t.next.moves[0]
        assertEquals(MoveWarn.BAD, fading.warn)
        assertEquals(MoveGo.Attr("physique"), fading.go)
        assertEquals(MoveGo.View("quests"), t.next.moves[2].go)
        assertTrue(t.next.moves[2].pill)
        assertEquals(MoveGo.Luck, t.next.moves[3].go)

        assertEquals(38, t.hero.ring.today)
        assertEquals("of 60 XP", t.hero.ring.of)
        assertEquals(62, t.hero.ring.r)
        assertEquals(11, t.hero.ring.stroke)
        assertEquals(63.333f, t.hero.ring.arcs.single().length, 0.001f)
        assertEquals("GRANDMASTER", t.hero.title)
        assertEquals("ATHLETE", t.hero.className)
        assertEquals(7, t.hero.streak.dots.size)
        assertTrue(t.hero.streak.dots.last().now)
        assertEquals("▼ 15%", t.hero.foot[0].delta!!.text)
        assertNull(t.hero.foot[1].delta)
        assertNull(t.hero.avatar)

        assertEquals(listOf("Sparks", "Streak"), t.rewards.chips.map { it.label })
        assertTrue(t.apps!!.items.isEmpty())
        assertTrue(t.apps!!.hint.isNotEmpty())
        assertEquals("Set up health sync", (t.health as HealthCard.Off).button)
        assertEquals(DotKind.DECAY, t.attrs.gems[0].dot.kind)
        assertEquals(8, t.attrs.gems.size)
        assertEquals(6, t.week.selected)
        assertEquals("pace 60", t.week.pace.label)
        assertEquals("Log an activity and your week lights up here.", t.week.empty)
        assertNull(t.cat)
        assertFalse(t.luck.rolled)
        assertNull(t.luck.color)
        assertEquals(4, t.recent.items.size)
        assertEquals(150, p.haptics!!.amp)
        assertEquals(5L, p.haptics!!.min)
        assertEquals(32.5f, p.celebrationTarget.y)
        assertTrue(p.celebrations.isEmpty())
        assertEquals(0L, p.arrival)
        assertFalse(p.light)
    }

    @Test
    fun healthAppsAndTheCatAreThePages() {
        val h = decode(fixture("health"))
        val apps = h.tiles.apps!!
        assertEquals(listOf("com.example.maps", "com.example.two"), apps.items.map { it.pkg })
        assertTrue(apps.items[0].image!! in h.images)
        assertNull(apps.items[1].image)
        val on = h.tiles.health as HealthCard.On
        assertEquals("6,420", on.steps)
        assertEquals(7, on.days.size)

        val pet = decode(fixture("pet"))
        val cat = pet.tiles.cat!!
        assertEquals(CatMode.ALIVE, cat.mode)
        assertEquals(3, cat.hearts)
        assertEquals("i5247850a", cat.portrait)
        assertEquals("i5247850a", pet.catPortrait!!.key)
        assertTrue(pet.catPortrait!!.dataUrl.startsWith("data:image/png;base64,"))
        assertTrue("cat" in pet.shown)

        val gone = decode(fixture("cat-gone")).tiles.cat!!
        assertEquals(CatMode.GONE, gone.mode)
        assertNull(gone.hearts)
        assertNull(gone.portrait)

        val browser = decode(fixture("browser"))
        assertNull(browser.tiles.apps)
        assertNull(browser.tiles.health)
        assertFalse("apps" in browser.shown || "health" in browser.shown)
        assertEquals("i380f6147", decode(fixture("no-cat")).tiles.hero.avatar)
    }

    @Test
    fun theTipLuckNoticeAndCelebrationsAreThePages() {
        val tip = decode(fixture("tip")).tip!!
        assertEquals("Your command center", tip.title)
        assertEquals("Dismiss tip", tip.dismiss)
        val luck = decode(fixture("luck")).tiles.luck
        assertTrue(luck.rolled)
        assertEquals("7,316", luck.score)
        assertNotNull(luck.color)
        assertEquals("Saved · +12 XP", decode(fixture("notice")).notice!!.text)
        val fx = decode(fixture("celebration")).celebrations.single()
        assertEquals(1792013400000L, fx.id)
        assertEquals(25, fx.xp)
        assertEquals(740f, fx.origin!!.y)
        assertEquals(listOf("Physique", "Intelligence"), fx.gains.map { it.short })
        val fresh = decode(fixture("new"))
        assertTrue(fresh.tiles.recent.items.isEmpty())
        assertEquals("⌁", fresh.tiles.recent.emptyIcon)
        assertTrue(fresh.tiles.hero.ring.arcs.isEmpty())
        assertNull(fresh.tiles.week.delta)
    }

    @Test
    fun theOrderIsTheLayoutsNotTheJsons() {
        val j = fixture("pet")
        val order = listOf("recent", "luck", "plan", "hero", "rewards", "apps", "health", "next")
        j.put(
            "layout",
            JSONObject()
                .put("order", JSONArray(order + listOf("bogus", "attrs", "week", "cat")))
                .put("hidden", JSONArray(listOf("week", "apps"))),
        )
        val p = decode(j)
        // an unknown id is skipped, a hidden tile is left out
        assertEquals(order - "apps" + listOf("attrs", "cat"), p.shown)
    }

    @Test
    fun malformedDataIsRejectedAndTheLastGoodStateKept() {
        val breaks: List<(JSONObject) -> Unit> =
            listOf(
                { it.remove("head") },
                { it.at("head").put("date", 14) },
                { it.put("sequence", "2") },
                { it.remove("tokens") },
                { it.at("tokens").remove("accent") },
                { it.remove("tip") },
                { it.at("tiles").remove("luck") },
                { it.at("tiles").remove("plan") },
                { it.at("tiles").remove("cat") },
                { it.at("tiles.next").getJSONArray("moves").getJSONObject(0).put("warn", "odd") },
                { it.at("tiles.next").getJSONArray("moves").getJSONObject(0).put("color", "red") },
                {
                    it.at("tiles.next")
                        .getJSONArray("moves")
                        .getJSONObject(0)
                        .put("go", JSONObject().put("view", "log").put("luck", true))
                },
                {
                    it.at("tiles.hero.ring")
                        .getJSONArray("arcs")
                        .getJSONObject(0)
                        .put("length", 120)
                },
                { it.at("tiles.hero.ring").remove("stroke") },
                { it.at("tiles.hero.streak").getJSONArray("dots").remove(0) },
                { it.at("tiles.week").getJSONArray("bars").remove(0) },
                { it.at("tiles.week").put("selected", 7) },
                { it.at("tiles.week.pace").remove("label") },
                { it.at("tiles.luck").put("color", 0xFF00FF00) },
                { it.at("tiles.cat").put("hearts", 6) },
                { it.at("tiles.cat").put("hearts", JSONObject.NULL) },
                { it.at("tiles.cat").remove("hearts") },
                { it.at("tiles.cat").put("mode", "invite") },
                { it.at("tiles.cat").remove("portrait") },
                { it.at("tiles.cat").put("portrait", 7) },
                { it.at("tiles.cat").put("portrait", "i12345678") },
                { it.at("tiles.health").put("on", "yes") },
                { it.at("tiles.apps").put("items", JSONArray().put(JSONObject().put("pkg", 1))) },
                { it.at("tiles.plan").put("state", "day") },
                { it.at("layout").put("hidden", JSONArray(listOf("nowhere"))) },
                { it.at("layout").put("order", JSONArray(listOf("luck", "luck"))) },
                { it.getJSONArray("tileInfo").remove(0) },
                { it.at("images").put("x", "data:image/png;base64,AAAA") },
                { it.at("images").put("i5247850a", "https://example.com/a.png") },
                { it.put("celebrations", JSONArray().put(JSONObject().put("id", "a"))) },
                { it.at("haptics").put("amp", 0) },
                { it.at("haptics").put("min", 4.5) },
                { it.remove("haptics") },
                { it.remove("motion") },
            )
        breaks.forEachIndexed { i, broken ->
            val repo = WebOverviewRepository().apply { accept(fixture("pet"), true) }
            val good = repo.state.value
            val j = fixture("pet").put("sequence", 2)
            broken(j)
            assertThrows("break $i", Exception::class.java) { repo.accept(j) }
            assertSame("break $i keeps the last good state", good, repo.state.value)
        }
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebOverviewRepository()
        repo.accept(fixture().put("sequence", 2), true)
        repo.accept(fixture("tip"))
        assertNull(repo.state.value!!.tip)
        repo.accept(fixture("tip").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("overview-fixture", repo.state.value!!.session)
        repo.accept(fixture("tip").put("webSessionId", "other"), establishSession = true)
        assertEquals("other", repo.state.value!!.session)
        assertNotNull(repo.state.value!!.tip)
    }

    @Test
    fun anArrivalFromALaterPageDecodes() {
        // phase 3's arrival counter: today's page sends none (0), a later one a number
        assertEquals(3L, decode(fixture().put("arrival", 3)).arrival)
    }

    /**
     * Each command goes out under the page's name with the payload of the page's reply table; the
     * two writes also carry the screen's operation id ("op"), which the page does not read (it
     * recognises a repeat by the day's roll and tipsSeen).
     */
    @Test
    fun commandsGoOutAsThePagesReplyTableHasThem() {
        val table = JSONObject(text("overview-cards-replies.json"))
        val sent = mutableListOf<Pair<String, JSONObject>>()
        val wire = OverviewCommandSender { command, payload ->
            sent += command to payload
            "r${sent.size}"
        }
        val order =
            table.getJSONObject("layout").getJSONObject("payload").getJSONArray("order").let { a ->
                (0 until a.length()).map { a.getString(it) }
            }
        // the page's own editor (edit) is no longer asked: the native editor sends layout
        val calls: Map<String, (() -> String)?> =
            mapOf(
                "tip" to { wire.tip("op-1") },
                "luck" to { wire.luck("op-2") },
                "attr" to { wire.attr("physique", "attrs") },
                "shop" to { wire.shop() },
                "health" to { wire.health() },
                "appLaunch" to { wire.appLaunch("com.example.maps") },
                "appAdd" to { wire.appAdd() },
                "appsEdit" to { wire.appsEdit() },
                "edit" to null,
                "layout" to { wire.layout(order, listOf("luck")) },
                "layoutReset" to { wire.layoutReset() },
                "planOpen" to { wire.plan("", "") },
                "planGone" to { wire.plan("inst", "no-such-session") },
            )
        for ((name, call) in calls) {
            val row = table.getJSONObject(name)
            if (call == null) continue
            call()
            val (command, payload) = sent.last()
            assertEquals(name, row.getString("command"), command)
            val want = row.getJSONObject("payload")
            for (k in want.keys()) assertEquals(
                "$name.$k",
                want.get(k).toString(),
                payload.get(k).toString(),
            )
            val extra = payload.keys().asSequence().toSet() - want.keys().asSequence().toSet()
            assertTrue("$name sends $extra", extra.isEmpty() || extra == setOf("op"))
        }
        // and every reply in the table reads
        for (name in table.keys()) {
            val r = table.getJSONObject(name).getJSONObject("reply")
            val reply = overviewReply(r)
            assertEquals(r.getBoolean("ok"), reply.ok)
            assertEquals(r.optString("message"), reply.message)
        }
        val again = overviewReply(table.getJSONObject("tipAgain").getJSONObject("reply"))
        assertTrue(again.ok)
        assertFalse(again.changed)
        assertEquals("Already recorded.", again.message)
    }

    private companion object {
        val KEY = Regex("i[0-9a-f]{1,8}")
    }
}
