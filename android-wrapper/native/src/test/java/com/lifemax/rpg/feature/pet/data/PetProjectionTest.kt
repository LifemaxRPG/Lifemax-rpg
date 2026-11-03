package com.lifemax.rpg.feature.pet.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Cat tab's projection as the page makes it, decoded strictly: what each fixture says, and that
 * anything the page might get wrong (a key it forgot, a key it added, a number where words belong,
 * a value out of its range) is refused, with the last good state kept.
 */
class PetProjectionTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.projection(name), true) }.state.value!!

    @Test
    fun aContentCatWithAnOpenFeedButton() {
        val p = decode("alive")
        assertEquals(listOf("Miso"), p.cats.map { it.name })
        assertEquals("ginger", p.cats[0].coat)
        assertEquals(-1, p.cats[0].stage)
        assertFalse(p.cats[0].weak)
        assertEquals("COMPANION · FRIENDLY", p.eyebrow)
        assertEquals(3, p.hearts)
        assertEquals("Miso", p.name)
        assertEquals("Feed Miso", p.feed.label)
        assertFalse(p.feed.wait)
        assertEquals("5 h", p.lastMeal.value)
        assertEquals("last meal", p.lastMeal.label)
        assertEquals("0 / 2", p.mealsToday)
        assertEquals("40", p.daysTogether)
        assertEquals("Still deciding", p.favourites.place)
        assertNull(p.growth)
        assertEquals("", p.mood)
        assertEquals("", p.together)
        assertTrue(p.fedCues.isEmpty())
        assertEquals("Slow blink", p.words.blink)
        assertEquals(14, p.care.nameMax)
        assertFalse(p.care.vacation)
    }

    @Test
    fun aCatThatWaitsForItsNextMeal() {
        val p = decode("content")
        assertEquals("Not hungry yet", p.feed.label)
        assertTrue(p.feed.wait)
    }

    @Test
    fun hungerShowsInTheMoodAndTheWeakRows() {
        assertEquals("Miso is hungry. Maybe time for a meal?", decode("hungry").mood)
        assertFalse(decode("hungry").cats[0].weak)
        val very = decode("very-hungry")
        assertEquals("Miso is very hungry and moving slowly.", very.mood)
        assertTrue(very.cats[0].weak)
        val weak = decode("weak")
        assertEquals("Miso looks weak and tired. A meal would help a lot.", weak.mood)
        assertTrue(weak.cats[0].weak)
    }

    @Test
    fun vacationPausesTheClock() {
        val p = decode("vacation")
        assertEquals("Vacation", p.lastMeal.value)
        assertEquals("clock paused", p.lastMeal.label)
        assertEquals("Vacation mode", p.feed.label)
        assertTrue(p.feed.wait)
        assertTrue(p.care.vacation)
    }

    @Test
    fun aKittenHasAGrowthLine() {
        val p = decode("kitten")
        assertEquals(2, p.cats[0].stage)
        assertTrue(p.eyebrow.startsWith("KITTEN"))
        val g = p.growth!!
        assertEquals("GROWING KITTEN", g.label)
        assertEquals("DAY 13 OF 30", g.day)
        assertEquals(40, g.pct)
    }

    @Test
    fun aStrayIsToldToBePatient() {
        val p = decode("stray")
        assertEquals(0, p.hearts)
        assertTrue(p.hint.startsWith("Strays take a few days"))
    }

    @Test
    fun twoCatsShareOneFeedButton() {
        val p = decode("two-cats")
        assertEquals(listOf("Ted", "Lily"), p.cats.map { it.name })
        assertEquals(0, p.focus)
        assertEquals("Feed both", p.feed.label)
        assertTrue(p.together.startsWith("Ted and Lily share the house"))
    }

    @Test
    fun aGeneticCoatIsNamedForTheScreenToAskFor() {
        assertTrue(decode("dna").cats[0].coat.startsWith("dna_"))
    }

    @Test
    fun aMealTheScreenPlaysOnce() {
        val cue = decode("fed").fedCues.single()
        assertEquals(0, cue.cat)
        assertEquals(1L, cue.id)
        assertEquals("Not hungry yet", decode("fed").feed.label)
    }

    /** One way the page's projection could be wrong, made from a good one. */
    private class Break(val what: String, val change: JSONObject.() -> Unit)

    private val breaks =
        listOf(
            Break("an unknown key") { put("extra", 1) },
            Break("an unknown key inside the bond") { getJSONObject("bond").put("extra", 1) },
            Break("an unknown key inside a cat") {
                getJSONArray("cats").getJSONObject(0).put("age", 3)
            },
            Break("an unknown word") { getJSONObject("words").put("dance", "Dance") },
            Break("a missing key") { remove("hint") },
            Break("a missing key inside the care card") { getJSONObject("care").remove("how") },
            Break("a missing word") { getJSONObject("words").remove("rename") },
            Break("a number for the name") { put("name", 5) },
            Break("words for the hearts") { put("hearts", "3") },
            Break("a fraction for the hearts") { put("hearts", 2.5) },
            Break("more hearts than there are") { put("hearts", 6) },
            Break("fewer hearts than none") { put("hearts", -1) },
            Break("a focus that names no cat") { put("focus", 1) },
            Break("a house with no cats") { put("cats", JSONArray()) },
            Break("a house with three cats") {
                val cats = getJSONArray("cats")
                cats.put(cats.getJSONObject(0)).put(cats.getJSONObject(0))
            },
            Break("a growth stage out of range") {
                getJSONArray("cats").getJSONObject(0).put("stage", 6)
            },
            Break("a coat that is not a key") {
                getJSONArray("cats").getJSONObject(0).put("coat", "Ginger Tabby")
            },
            Break("a nameless cat") { getJSONArray("cats").getJSONObject(0).put("name", " ") },
            Break("a bond over 100 percent") { getJSONObject("bond").put("pct", 101) },
            Break("a Feed wait that is not a boolean") { getJSONObject("feed").put("wait", "yes") },
            Break("a name limit of nothing") { getJSONObject("care").put("nameMax", 0) },
            Break("a name over its limit") {
                getJSONObject("care").put("name", "Fifteen letters!")
            },
            Break("a palette without a token") { getJSONObject("tokens").remove("accent") },
            Break("light as a word") { put("light", "dark") },
            Break("a notice with a stray key") {
                put("notice", JSONObject().put("id", 1).put("text", "Hi").put("more", 1))
            },
            Break("a notice without an id") { put("notice", JSONObject().put("text", "Hi")) },
            Break("a cue for a cat that cannot exist") {
                getJSONObject("cue").put("fed", JSONArray().put(cue(2, 1, 0)))
            },
            Break("a cue with an id of nothing") {
                getJSONObject("cue").put("fed", JSONArray().put(cue(0, 0, 0)))
            },
            Break("a cue from the future") {
                getJSONObject("cue").put("fed", JSONArray().put(cue(0, 1, -5)))
            },
            Break("cues out of order") {
                getJSONObject("cue").put("fed", JSONArray().put(cue(0, 5, 0)).put(cue(1, 4, 0)))
            },
            Break("the same cue twice") {
                getJSONObject("cue").put("fed", JSONArray().put(cue(0, 5, 0)).put(cue(0, 5, 0)))
            },
            Break("a river of cues") {
                val list = JSONArray()
                for (id in 1..5) list.put(cue(0, id.toLong(), 0))
                getJSONObject("cue").put("fed", list)
            },
            Break("another protocol") { put("protocol", 2) },
            Break("no session") { put("webSessionId", " ") },
        )

    private fun cue(cat: Int, id: Long, ago: Long) =
        JSONObject().put("cat", cat).put("id", id).put("ago", ago)

    @Test
    fun everyMalformedProjectionIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(PetFixtures.projection("alive"), true) }
            val bad = PetFixtures.projection("alive").put("sequence", 2).apply(b.change)
            val refused = runCatching { repo.accept(bad) }.isFailure
            assertTrue("accepted: ${b.what}", refused)
            assertEquals("kept the last good state: ${b.what}", 1L, repo.state.value!!.sequence)
            assertEquals("Miso", repo.state.value!!.name)
        }
    }

    @Test
    fun aMalformedProjectionIsRefusedOnAnOpenToo() {
        for (b in breaks) {
            val bad = PetFixtures.projection("alive").apply(b.change)
            // a projection with a protocol or session fault is refused before any state is made
            assertTrue(
                "accepted on open: ${b.what}",
                runCatching { WebPetRepository().accept(bad, true) }.isFailure,
            )
        }
    }

    @Test
    fun aCueForACatThatIsNotDrawnIsDropped() {
        val j =
            PetFixtures.projection("alive").apply {
                getJSONObject("cue").put("fed", JSONArray().put(cue(1, 3, 10)))
            }
        assertTrue(WebPetRepository().apply { accept(j, true) }.state.value!!.fedCues.isEmpty())
    }
}
