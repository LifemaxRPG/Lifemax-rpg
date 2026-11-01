package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.OverviewCommandSender
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.data.overviewReply
import com.lifemax.rpg.feature.overview.domain.CatMode
import com.lifemax.rpg.feature.overview.domain.DotKind
import com.lifemax.rpg.feature.overview.domain.GlanceState
import com.lifemax.rpg.feature.overview.domain.HealthCard
import com.lifemax.rpg.feature.overview.domain.MoveGo
import com.lifemax.rpg.feature.overview.domain.MoveWarn
import com.lifemax.rpg.feature.overview.domain.OverviewCards
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

/** The Overview cards' decoder (overview.cards.v1, plan §3.3) and the commands' wire names. */
class OverviewTest {
    private fun fixture(name: String = "sample") =
        JSONObject(javaClass.getResource("/fixtures/overview-cards-$name.json")!!.readText())

    private fun decode(j: JSONObject): OverviewCards =
        WebOverviewRepository().apply { accept(j, true) }.state.value!!

    private fun JSONObject.at(path: String): JSONObject =
        path.split('.').fold(this) { o, k -> o.getJSONObject(k) }

    @Test
    fun decodesAnEstablishedSave() {
        val p = decode(fixture())
        assertEquals("WEDNESDAY, OCTOBER 14", p.head.date)
        assertEquals("Good evening, Synthetic Hero", p.head.hello)
        assertEquals("athlete", p.head.classId)
        assertEquals("Edit the Overview: reorder or hide tiles", p.head.editA11y)
        assertNull(p.tip)
        val all =
            listOf("plan", "next", "hero", "rewards", "apps", "health", "attrs", "week", "cat")
        assertEquals(all + listOf("luck", "recent"), p.shown)
        assertEquals(11, p.tileInfo.size)
        assertEquals("Miso’s room", p.tileInfo.first { it.id == "cat" }.sub)

        val t = p.tiles
        assertEquals(GlanceState.SETUP, t.plan!!.state)
        assertEquals("Plan my days", t.plan!!.button)
        assertEquals(4, t.next.moves.size)
        val fading = t.next.moves[0]
        assertEquals(MoveWarn.BAD, fading.warn)
        assertEquals(MoveGo.Attr("physique"), fading.go)
        assertEquals(MoveGo.View("quests"), t.next.moves[2].go)
        assertTrue(t.next.moves[2].pill)
        assertEquals(MoveGo.Luck, t.next.moves[3].go)

        assertEquals(38, t.hero.ring.today)
        assertEquals("of 60 XP", t.hero.ring.of)
        assertEquals(63.33f, t.hero.ring.arcs.single().length, 0.001f)
        assertEquals("GRANDMASTER", t.hero.title)
        assertEquals(7, t.hero.streak.dots.size)
        assertTrue(t.hero.streak.dots.last().now)
        assertEquals("▼ 15%", t.hero.foot[0].delta!!.text)
        assertNull(t.hero.foot[1].delta)
        assertNull(t.hero.avatar)

        assertEquals(listOf("Sparks", "Streak", "Rested"), t.rewards.chips.map { it.label })
        val apps = t.apps!!
        assertEquals(listOf("com.example.maps", "com.example.two"), apps.items.map { it.pkg })
        assertTrue(apps.items[0].image!! in p.images)
        assertNull(apps.items[1].image)
        val health = t.health as HealthCard.On
        assertEquals("6,420", health.steps)
        assertEquals(7, health.days.size)
        assertEquals(DotKind.DECAY, t.attrs.gems[0].dot.kind)
        assertEquals(8, t.attrs.gems.size)
        assertEquals(6, t.week.selected)
        assertEquals("pace 60", t.week.pace.label)
        assertEquals(CatMode.ALIVE, t.cat!!.mode)
        assertEquals(3, t.cat!!.hearts)
        assertNull(t.cat!!.portrait)
        assertFalse(t.luck.rolled)
        assertNull(t.luck.color)
        assertEquals(4, t.recent.items.size)
        assertEquals(150, p.haptics!!.amp)
        assertEquals(36f, p.celebrationTarget.x)
        assertTrue(p.celebrations.isEmpty())
        assertEquals(0L, p.arrival)
        assertFalse(p.light)
    }

    @Test
    fun aNewSaveShowsWhatThePageShows() {
        val p = decode(fixture("new"))
        assertEquals("Your command center", p.tip!!.title)
        assertEquals("Dismiss tip", p.tip!!.dismiss)
        assertTrue(p.tiles.luck.rolled)
        assertEquals("7,316", p.tiles.luck.score)
        assertNotNull(p.tiles.luck.color)
        assertTrue(p.tiles.recent.items.isEmpty())
        assertEquals("⌁", p.tiles.recent.emptyIcon)
        assertTrue(p.tiles.hero.ring.arcs.isEmpty())
        // no cat, not the Android app: the page draws no cat, apps or health tile
        assertFalse("cat" in p.shown || "apps" in p.shown || "health" in p.shown)
    }

    @Test
    fun theOrderIsTheLayoutsNotTheJsons() {
        val j = fixture()
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
                { it.at("tiles.hero.streak").getJSONArray("dots").remove(0) },
                { it.at("tiles.week").getJSONArray("bars").remove(0) },
                { it.at("tiles.week").put("selected", 7) },
                { it.at("tiles.luck").put("color", 0xFF00FF00) },
                { it.at("tiles.cat").put("hearts", 6) },
                { it.at("tiles.cat").put("mode", "invite") },
                { it.at("tiles.health").put("on", "yes") },
                {
                    it.at("tiles.apps").getJSONArray("items").getJSONObject(0).put("image", "nokey")
                },
                { it.at("tiles.plan").put("state", "day") },
                { it.at("layout").put("hidden", JSONArray(listOf("nowhere"))) },
                { it.at("layout").put("order", JSONArray(listOf("luck", "luck"))) },
                { it.getJSONArray("tileInfo").remove(0) },
                { it.put("images", JSONObject().put("x", "https://example.com/a.png")) },
                { it.put("celebrations", JSONArray().put(JSONObject().put("id", "a"))) },
                { it.remove("motion") },
            )
        breaks.forEachIndexed { i, broken ->
            val repo = WebOverviewRepository().apply { accept(fixture(), true) }
            val good = repo.state.value
            val j = fixture().put("sequence", 2)
            broken(j)
            assertThrows("break $i", Exception::class.java) { repo.accept(j) }
            assertSame("break $i keeps the last good state", good, repo.state.value)
        }
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebOverviewRepository()
        repo.accept(fixture().put("sequence", 2), true)
        repo.accept(fixture("new"))
        assertEquals("WEDNESDAY, OCTOBER 14", repo.state.value!!.head.date)
        repo.accept(fixture("new").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("overview-fixture", repo.state.value!!.session)
        repo.accept(fixture("new").put("webSessionId", "other"), establishSession = true)
        assertEquals("other", repo.state.value!!.session)
        assertNotNull(repo.state.value!!.tip)
    }

    @Test
    fun celebrationsAndPhaseThreeFieldsDecode() {
        val j = fixture()
        j.put(
            "celebrations",
            JSONArray()
                .put(
                    JSONObject()
                        .put("id", "fx-1")
                        .put("xp", 40)
                        .put("origin", JSONObject().put("x", 206).put("y", 800.5))
                        .put(
                            "gains",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("attribute", "physique")
                                        .put("short", "STR")
                                        .put("xp", 40)
                                        .put("color", 0xFFFF7A57)
                                ),
                        )
                ),
        )
        j.put("arrival", 3)
        j.at("tiles.cat").put("portrait", JSONObject.NULL)
        val p = decode(j)
        val fx = p.celebrations.single()
        assertEquals("fx-1", fx.id)
        assertEquals(800.5f, fx.origin.y)
        assertEquals("STR", fx.gains.single().short)
        assertEquals(0xFFFF7A57, fx.gains.single().color)
        assertEquals(3L, p.arrival)
    }

    @Test
    fun aDayGlanceReadsLifeDirectorsDial() {
        val today =
            JSONObject(javaClass.getResource("/fixtures/director-today.json")!!.readText())
                .getJSONObject("today")
        val j = fixture()
        j.at("tiles")
            .put(
                "plan",
                JSONObject()
                    .put("state", "day")
                    .put("title", "Today’s plan")
                    .put("link", "Life Director ›")
                    .put("intro", JSONObject.NULL)
                    .put("button", JSONObject.NULL)
                    .put("dial", today.getJSONObject("dial"))
                    .put("core", today.getJSONObject("core"))
                    .put(
                        "foot",
                        JSONObject().put("text", "3 sessions left · 2h").put("done", "1 done"),
                    ),
            )
        val plan = decode(j).tiles.plan!!
        assertEquals(GlanceState.DAY, plan.state)
        assertTrue(plan.dial!!.arcs.isNotEmpty())
        assertNotNull(plan.core)
        assertEquals("1 done", plan.foot!!.done)
    }

    @Test
    fun commandsGoOutUnderThePagesNames() {
        val sent = mutableListOf<Pair<String, String>>()
        val wire = OverviewCommandSender { command, payload ->
            sent +=
                command to
                    payload.keys().asSequence().sorted().joinToString(",") {
                        "$it=${payload.get(it)}"
                    }
            "r${sent.size}"
        }
        assertEquals("r1", wire.plan("inst", "s1"))
        wire.attr("physique", "next")
        wire.luck("op-1")
        wire.shop()
        wire.health()
        wire.appLaunch("com.example.maps")
        wire.appAdd()
        wire.appsEdit()
        wire.tip("op-2")
        wire.edit()
        assertEquals(
            listOf(
                "plan" to "act=inst,id=s1",
                "attr" to "id=physique,tile=next",
                "luck" to "op=op-1",
                "shop" to "",
                "health" to "",
                "appLaunch" to "pkg=com.example.maps",
                "appAdd" to "",
                "appsEdit" to "",
                "tip" to "op=op-2",
                "edit" to "",
            ),
            sent,
        )
        val reply =
            overviewReply(
                JSONObject()
                    .put("requestId", "r9")
                    .put("command", "tip")
                    .put("ok", false)
                    .put("message", "Could not save to this browser (storage full?)")
            )
        assertEquals("r9", reply.requestId)
        assertFalse(reply.ok)
        assertFalse(reply.changed)
    }
}
