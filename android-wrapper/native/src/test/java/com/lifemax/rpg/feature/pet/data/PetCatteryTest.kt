package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.CatteryDetail
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Cat tab's cattery (`pet.cattery.v1`) as the page makes it
 * (tools/gen_pet_cattery_fixtures.js), decoded strictly: what each screen's fixture says in the
 * page's own words, that a projection without the block still decodes, and that anything the page
 * might get wrong is refused with the last good state kept.
 */
class PetCatteryTest {
    private fun decode(name: String) =
        WebPetRepository().apply { accept(PetFixtures.cattery(name), true) }.state.value!!

    private fun detail(name: String) = decode(name).cattery!!.detail!!

    @Test
    fun aProjectionWithoutTheBlockStillDecodes() {
        val p = WebPetRepository().apply { accept(PetFixtures.projection("alive"), true) }
        assertNull(p.state.value!!.cattery)
    }

    @Test
    fun theEntryIsThereWithNoScreenOpen() {
        val c = decode("entry").cattery!!
        assertNull(c.view)
        assertNull(c.detail)
        assertEquals(3, c.entry.cats)
        assertEquals(16, c.entry.max)
        assertEquals(2, c.entry.memorial)
        assertTrue(c.entry.canAdopt)
        assertEquals("Breeding lab", c.words.lab)
        assertEquals("Meet", c.words.adoptButton)
    }

    @Test
    fun everyScreenDecodesWithItsDetail() {
        for (name in PetFixtures.CATTERY_VIEWS.drop(1)) {
            val c = decode(name).cattery!!
            assertNotNull(name, c.detail)
            assertEquals(
                name,
                CatteryScreen.of(if (name.startsWith("lab")) "lab" else name),
                c.view,
            )
        }
    }

    @Test
    fun theRosterHasTheHomeCatAndTheCatteryCatsInThePagesWords() {
        val d = detail("cattery") as CatteryDetail.Roster
        assertEquals(listOf("h0", "c1", "c2", "c3"), d.cats.map { it.id })
        assertEquals(listOf(true, false, true, false), d.cats.map { it.female })
        assertTrue(d.cats[0].home)
        assertTrue(d.cats[0].sub.endsWith("· generation 0 · at home"))
        assertTrue(d.cats[3].sub.endsWith("· sterile"))
        assertEquals("Release Ted? She will not come back.", d.cats[0].ask)
        assertEquals("Release Basil? He will not come back.", d.cats[1].ask)
        assertEquals("ears 1.1×", d.cats[0].chips.first().label)
        assertTrue(d.cats[0].chips.first().goal)
        assertEquals(-1, d.cats[1].preview.stage)
        assertEquals(1.34, d.cats[1].preview.rig.earWidth, 0.0)
        assertTrue(d.canHome)
        assertEquals(16, d.max)
        assertEquals(14, d.nameMax)
    }

    @Test
    fun theLabHasThePairThePagesForecastAndALitterOfThree() {
        val d = detail("lab") as CatteryDetail.Lab
        assertEquals(listOf("h0", "c2"), d.moms.map { it.key })
        assertEquals(listOf("c1"), d.dads.map { it.key })
        assertEquals("h0", d.mom)
        assertEquals("em+", d.goal)
        assertEquals(12, d.options.size)
        assertTrue(d.forecast.startsWith("Expected ears: "))
        assertTrue(d.best.startsWith("Best in your roster: Olive"))
        val litter = d.litter!!
        assertEquals("Litter of 3", litter.title)
        assertEquals(3, litter.kits.size)
        assertEquals("Ted × Basil", litter.parents)
        assertEquals(listOf("Kitten 1", "Kitten 2", "Kitten 3"), litter.kits.map { it.name })
        assertFalse(litter.full)
        assertTrue(litter.at > 0)
    }

    @Test
    fun withNoFertileMaleThePageSaysSo() {
        val d = detail("labNoMate") as CatteryDetail.Lab
        assertTrue(d.note.startsWith("You need at least one female and one fertile male."))
        assertTrue(d.dads.isEmpty())
        assertNull(d.litter)
    }

    @Test
    fun aFullCatteryIsTheLittersNote() {
        val d = detail("labFull") as CatteryDetail.Lab
        assertTrue(d.litter!!.full)
        assertEquals(
            "The cattery is full (16). Release a cat before keeping more.",
            d.litter!!.fullNote,
        )
    }

    @Test
    fun adoptionIsAPairOfFourKittensWithThePagesWords() {
        val d = detail("adopt") as CatteryDetail.Adopt
        assertEquals("pair", d.mode)
        assertEquals("More strays by the kitchen window", d.title)
        assertEquals(4, d.kits.size)
        assertEquals(listOf(0, 1, 2, 3), d.kits.map { it.index })
        assertEquals(0, d.kits.first().preview.stage)
        assertEquals(14, d.nameMax)
        assertTrue(d.parents.startsWith("Mother: "))
    }

    @Test
    fun theMemorialListsTheCatsThatPassedNewestFirst() {
        val d = detail("memorial") as CatteryDetail.Memorial
        assertEquals(listOf("Luna", "Pepper"), d.entries.map { it.name })
        assertTrue(d.entries[0].preview!!.sleep)
        assertTrue(d.entries[1].text.startsWith("Pepper is gone. You shared 12 days together"))
        assertFalse(d.canReopen)
    }

    private class Break(val what: String, val view: String, val change: JSONObject.() -> Unit)

    private fun JSONObject.block() = getJSONObject("cattery")

    private fun JSONObject.det() = block().getJSONObject("detail")

    private fun JSONObject.firstCard() = det().getJSONArray("cats").getJSONObject(0)

    private val breaks =
        listOf(
            Break("an unknown key in the block", "lab") { block().put("extra", 1) },
            Break("a missing entry", "lab") { block().remove("entry") },
            Break("an unknown view", "lab") { block().put("view", "garden") },
            Break("a detail for another screen", "lab") { block().put("view", "adopt") },
            Break("a screen open with no detail", "lab") { block().put("detail", JSONObject.NULL) },
            Break("a number as a word", "lab") { block().getJSONObject("words").put("lab", 5) },
            Break("a missing word", "lab") { block().getJSONObject("words").remove("keep") },
            Break("an unknown word", "lab") { block().getJSONObject("words").put("x", "y") },
            Break("a negative count", "entry") { block().getJSONObject("entry").put("cats", -1) },
            Break("a count beyond the cattery", "entry") {
                block().getJSONObject("entry").put("cats", 999)
            },
            Break("an adopt entry that is neither", "entry") {
                block().getJSONObject("entry").put("adopt", "first")
            },
            Break("a card without its id", "cattery") { firstCard().remove("id") },
            Break("a card with an unknown key", "cattery") { firstCard().put("x", 1) },
            Break("a female that is a string", "cattery") { firstCard().put("female", "yes") },
            Break("a coat that is not a key", "cattery") {
                firstCard().getJSONObject("preview").put("coat", "Not A Key")
            },
            Break("a stage beyond the kitten's", "cattery") {
                firstCard().getJSONObject("preview").put("stage", 6)
            },
            Break("a rig multiplier out of range", "cattery") {
                firstCard().getJSONObject("preview").getJSONObject("rig").put("em", 9.0)
            },
            Break("a rig with a key missing", "cattery") {
                firstCard().getJSONObject("preview").getJSONObject("rig").remove("bob")
            },
            Break("a chip that is not an object", "cattery") {
                firstCard().getJSONArray("chips").put(0, "ears")
            },
            Break("more cats than a cattery holds", "cattery") {
                val a = det().getJSONArray("cats")
                repeat(20) { a.put(a.getJSONObject(0)) }
            },
            Break("a name field of nothing", "cattery") { det().put("nameMax", 0) },
            Break("a goal that is not one", "lab") { det().put("goal", "xx+") },
            Break("an option that is not a goal", "lab") {
                det().getJSONArray("options").getJSONObject(0).put("value", "zz-")
            },
            Break("a litter without its stamp", "lab") {
                det().getJSONObject("litter").remove("at")
            },
            Break("a litter stamp that is a string", "lab") {
                det().getJSONObject("litter").put("at", "now")
            },
            Break("a litter of seven", "lab") {
                val a = det().getJSONObject("litter").getJSONArray("kits")
                repeat(4) { a.put(a.getJSONObject(0)) }
            },
            Break("a mother that is a number", "lab") { det().put("mom", 1) },
            Break("a pick with a key missing", "lab") {
                det().getJSONArray("moms").getJSONObject(0).remove("home")
            },
            Break("an adoption mode of its own", "adopt") { det().put("mode", "triple") },
            Break("kittens out of order", "adopt") {
                det().getJSONArray("kits").getJSONObject(1).put("i", 3)
            },
            Break("a pick beyond the litter", "adopt") { det().put("pick", 9) },
            Break("a kitten without its preview", "adopt") {
                det().getJSONArray("kits").getJSONObject(0).remove("preview")
            },
            Break("a memorial entry that is a string", "memorial") {
                det().getJSONArray("entries").put(0, "Luna")
            },
            Break("a memorial of forty-one", "memorial") {
                val a = det().getJSONArray("entries")
                repeat(40) { a.put(a.getJSONObject(0)) }
            },
            Break("a memorial preview of the wrong kind", "memorial") {
                det().getJSONArray("entries").getJSONObject(0).put("preview", 7)
            },
            Break("a reopen that is a string", "memorial") { det().put("canReopen", "yes") },
            Break("a text far too long", "lab") { det().put("intro", "x".repeat(5000)) },
            Break("the lists as an object", "lab") { det().put("moms", JSONObject()) },
            Break("the cattery as an array", "lab") { put("cattery", JSONArray()) },
        )

    @Test
    fun everyMalformedBlockIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(PetFixtures.cattery(b.view), true) }
            val bad = PetFixtures.cattery(b.view).put("sequence", 2).apply(b.change)
            assertTrue("accepted: ${b.what}", runCatching { repo.accept(bad) }.isFailure)
            assertEquals("kept the last good state: ${b.what}", 1L, repo.state.value!!.sequence)
            assertNotNull(repo.state.value!!.cattery)
        }
    }

    @Test
    fun aMalformedBlockIsRefusedOnAnOpenToo() {
        for (b in breaks) {
            val bad = PetFixtures.cattery(b.view).apply(b.change)
            assertTrue(
                "accepted: ${b.what}",
                runCatching { WebPetRepository().accept(bad, true) }.isFailure,
            )
        }
    }

    @Test
    fun aPageThatStopsSendingTheBlockTakesItAway() {
        val repo = WebPetRepository().apply { accept(PetFixtures.cattery("entry"), true) }
        repo.accept(PetFixtures.projection("alive").put("sequence", 2))
        assertNull(repo.state.value!!.cattery)
    }
}
