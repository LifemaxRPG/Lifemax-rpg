package com.lifemax.rpg.app.shell.swipe

import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.defaultBar
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.nativeRow
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.state
import com.lifemax.rpg.app.shell.swipe.SwipeFixtures.swipeJson
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeStateTest {
    private val everyNative =
        setOf("character", "meals", "recipes", "log", "plan", "media", "life-director")

    private fun pane(key: String, bar: List<SwipeFixtures.Entry> = defaultBar) =
        Neighbour.Pane(state("x", bar).tabs.first { it.key == key })

    private fun veil(key: String) = Neighbour.Veil(state("x").tabs.first { it.key == key })

    private fun wall(key: String?, bar: List<SwipeFixtures.Entry> = defaultBar) =
        Neighbour.Wall(key?.let { k -> state("x", bar).tabs.first { it.key == k } })

    private fun sides(
        view: String,
        bar: List<SwipeFixtures.Entry> = defaultBar,
        composed: Set<String> = everyNative,
    ): Pair<Neighbour, Neighbour>? =
        state(view, bar).neighbours(view, composed)?.let { it.prev to it.next }

    @Test
    fun theWholeFieldIsRead() {
        val s = SwipeState.parse(swipeJson("character", haptics = "strong"), 42)!!
        assertEquals(true, s.on)
        assertEquals(true, s.motion)
        assertEquals(HapticLevel.Strong, s.haptics)
        assertEquals("character", s.view)
        assertEquals("character", s.active)
        assertEquals(defaultBar.map { it.key }, s.tabs.map { it.key })
        assertEquals(SwipeTab("food", "meals", "Food", "food", TabKind.Native), s.tabs[2])
        assertEquals(false, s.tabs.last().source)
        assertEquals(TabKind.Stop, s.tabs[8].kind)
        assertEquals(
            SwipeColors(0xFF101820.toInt(), 0xFFF2F2F2.toInt(), 0xFF8A93A0.toInt()),
            s.colors,
        )
        assertEquals(SwipeTune.DEFAULT, s.tune)
        assertEquals(42, s.sequence)
    }

    @Test
    fun theDefaultBar() {
        assertEquals(wall("dashboard") to pane("food"), sides("character"))
        assertEquals(pane("character") to wall("pet"), sides("meals"))
        assertEquals(wall("pet") to wall("quests"), sides("log"))
        assertEquals(wall("quests") to pane("library"), sides("plan"))
        assertEquals(pane("plan") to wall("money"), sides("media"))
    }

    @Test
    fun theOwnersReorderPutsFiveNativeTabsSideBySide() {
        assertEquals(
            wall(null, nativeRow) to pane("food", nativeRow),
            sides("character", nativeRow),
        )
        assertEquals(
            pane("character", nativeRow) to pane("log", nativeRow),
            sides("meals", nativeRow),
        )
        assertEquals(pane("food", nativeRow) to pane("plan", nativeRow), sides("log", nativeRow))
        assertEquals(pane("log", nativeRow) to pane("library", nativeRow), sides("plan", nativeRow))
        assertEquals(
            pane("plan", nativeRow) to wall("dashboard", nativeRow),
            sides("media", nativeRow),
        )
    }

    @Test
    fun aHiddenTabIsSimplyNotThere() {
        val noCat = defaultBar.filter { it.key != "pet" }
        assertEquals(pane("character", noCat) to pane("log", noCat), sides("meals", noCat))
    }

    @Test
    fun theEndsAreWalls() {
        val bar = defaultBar.drop(1)
        assertEquals(wall(null, bar), sides("character", bar)!!.first)
        val last = defaultBar.dropLast(2)
        assertEquals(wall(null, last), sides("media", last)!!.second)
    }

    @Test
    fun foodResolvesToRecipesWhenThatWasTheLastPane() {
        val recipes = defaultBar.map { if (it.key == "food") it.copy(view = "recipes") else it }
        val food = pane("food", recipes)
        assertEquals("recipes", food.tab.view)
        assertEquals(wall("dashboard", recipes) to food, sides("character", recipes))
        assertEquals(pane("character", recipes) to wall("pet", recipes), sides("recipes", recipes))
        assertNull(
            "meals is not the food tab now",
            state("meals", recipes).neighbours("meals", everyNative),
        )
    }

    @Test
    fun libraryOnTheJournalIsAWebWall() {
        val journal =
            defaultBar.map {
                if (it.key == "library") it.copy(view = "history", kind = "web") else it
            }
        assertEquals(wall("quests", journal) to wall("library", journal), sides("plan", journal))
    }

    @Test
    fun aStopIsAWall() {
        val (_, next) = sides("media")!!
        assertEquals(TabKind.Stop, (next as Neighbour.Wall).tab!!.kind)
    }

    @Test
    fun anUnvisitedNativeTabIsTheVeil() {
        assertEquals(
            wall("dashboard") to veil("food"),
            sides("character", composed = setOf("character")),
        )
    }

    @Test
    fun aTabThatMayNotStartASwipeHasNoSides() {
        assertNull(sides("life-director"))
        // but it can be a destination
        val ldNext = defaultBar.filter { it.key != "money" }
        assertEquals(pane("life-director", ldNext), sides("media", ldNext)!!.second)
    }

    @Test
    fun aMissingOrStaleStateHasNoSides() {
        assertNull(SwipeState.parse(null, 1))
        assertNull(SwipeState.parse(JSONObject(), 1))
        assertNull(SwipeState.parse(swipeJson("character").put("v", 2), 1))
        assertNull(SwipeState.parse(swipeJson("character").put("tabs", JSONArray()), 1))
        assertNull(SwipeState.parse(swipeJson("character").put("tabs", "character,food"), 1))
        val broken = swipeJson("character")
        broken.getJSONArray("tabs").getJSONObject(3).remove("view")
        assertNull(
            "one entry without a view would shift every neighbour",
            SwipeState.parse(broken, 1),
        )
        // another view is current
        assertNull(state("meals").neighbours("character", everyNative))
        // not on the bar
        assertNull(state("settings").neighbours("settings", everyNative))
        // the active key resolves to another view
        val other = SwipeState.parse(swipeJson("character").put("active", "food"), 1)!!
        assertNull(other.neighbours("character", everyNative))
    }

    @Test
    fun theFlagsAndTheirDefaults() {
        val bare = swipeJson("character")
        listOf("on", "motion", "haptics", "colors", "tune").forEach(bare::remove)
        val s = SwipeState.parse(bare, 1)!!
        assertEquals(false, s.on)
        assertEquals(true, s.motion)
        assertEquals(HapticLevel.Off, s.haptics)
        assertNull(s.colors)
        assertEquals(SwipeTune.DEFAULT, s.tune)
        assertEquals(HapticLevel.Off, state("character", haptics = "off").haptics)
        assertEquals(HapticLevel.Normal, state("character", haptics = true).haptics)
        assertEquals(HapticLevel.Off, state("character", haptics = false).haptics)
        assertEquals(HapticLevel.Normal, state("character", haptics = "rumble").haptics)
        assertEquals(false, state("character", on = false).on)
        assertEquals(false, state("character", motion = false).motion)
    }

    @Test
    fun theTuneRidesTheState() {
        val tune = JSONObject().put("heavy", 0.4).put("breakAt", 2)
        val s = state("character", tune = tune)
        assertEquals(0.4f, s.tune.heavy, 1e-6f)
        assertEquals(0.7f, s.tune.breakAt, 1e-6f)
    }

    @Test
    fun unknownKindsAndNullTextAreSafe() {
        val json = swipeJson("character")
        val cat = json.getJSONArray("tabs").getJSONObject(3)
        cat.put("kind", "hologram").put("label", JSONObject.NULL)
        val s = SwipeState.parse(json, 1)!!
        assertEquals(TabKind.Web, s.tabs[3].kind)
        assertEquals("", s.tabs[3].label)
        val incomplete = swipeJson("character").put("colors", JSONObject().put("bg", 1))
        assertNull(SwipeState.parse(incomplete, 1)!!.colors)
    }
}
