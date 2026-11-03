package com.lifemax.rpg.feature.pet.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Cat tab's panels (`pet.panels.v1`) as the page makes them, decoded strictly: what each
 * fixture says (the page's own words for the mind row, likes, inner life, genetics, the Together
 * card and the toys), that a projection from a page without the capability still decodes with no
 * panels, and that anything the page might get wrong is refused with the last good state kept.
 */
class PetPanelsTest {
    private fun fixture(name: String) = PetFixtures.projection("panels-$name")

    private fun decode(name: String) =
        WebPetRepository().apply { accept(fixture(name), true) }.state.value!!

    private fun panels(name: String) = decode(name).panels!!

    @Test
    fun aProjectionWithoutThePanelsStillDecodes() {
        for (name in listOf("alive", "two-cats", "dna")) {
            val p = WebPetRepository().apply { accept(PetFixtures.projection(name), true) }
            assertNull(name, p.state.value!!.panels)
        }
    }

    @Test
    fun everyStateOfACatHasItsPanels() {
        for (name in PetFixtures.PANEL_STATES) {
            val p = panels(name)
            assertNotNull("$name has likes", p.likes)
            assertNotNull("$name has inner life", p.inner)
            assertNotNull("$name has genetics", p.dna)
            assertNotNull("$name has toys", p.toys)
            assertEquals("Genetics", p.dna!!.title)
            assertEquals(7, p.toys!!.items.size)
        }
    }

    @Test
    fun aBondedCatsMindRowAndLikes() {
        val p = panels("bonded")
        val mind = p.mind!!
        assertEquals("partner", mind.styleId)
        assertEquals("Proud partner", mind.styleName)
        assertEquals("content", mind.mood)
        assertTrue(mind.text.startsWith("Miso is content and cosy."))
        assertEquals("While you were away", mind.awayTitle)
        val likes = p.likes!!
        assertEquals("What Miso likes.", likes.lead)
        assertEquals(100, likes.places.first().pct)
        assertTrue(likes.places.first().favourite)
        assertTrue(likes.toys.any { it.name == "Jingle ball" && it.favourite })
        assertEquals("Places", likes.placesTitle)
    }

    @Test
    fun anInnerLifeWithQuirksMemoriesAndSmallPrint() {
        val inner = panels("bonded").inner!!
        assertEquals(
            listOf("Familiarity", "Trust", "Safety", "Confidence"),
            inner.threads.map { it.label },
        )
        assertEquals(3, inner.quirks.size)
        assertEquals("Learned with you", inner.quirks.last().source)
        assertEquals(3, inner.memories.size)
        assertTrue(inner.memories.any { it.zone == "the front of the living room" })
        assertTrue(inner.stats.contains("slow blinks shared"))
        assertTrue(inner.comfort.startsWith("When he needs comfort"))
    }

    @Test
    fun aStrayHasNoPlacesYet() {
        val p = panels("stray")
        assertTrue(p.likes!!.places.isEmpty())
        assertTrue(p.likes!!.noPlaces.startsWith("No favorite places yet"))
        assertEquals(0, p.inner!!.memories.size)
        assertEquals("newcomer", p.mind!!.styleId)
    }

    @Test
    fun aHungryCatsMindRowLeavesTheMoodSentenceOut() {
        assertFalse(panels("hungry").mind!!.text.startsWith("Miso is"))
        assertTrue(panels("bonded").mind!!.text.startsWith("Miso is"))
    }

    @Test
    fun aFemaleCatsPanelsAreInShe() {
        val inner = panels("geneticFemale").inner!!
        assertFalse(
            Regex("\\b(he|his|him)\\b", RegexOption.IGNORE_CASE).containsMatchIn(inner.body)
        )
        assertTrue(inner.body.contains("she feels about you"))
        assertTrue(panels("geneticFemale").dna!!.female)
    }

    @Test
    fun theGeneticsPanelHasItsRowsBarsAndBodyShape() {
        val dna = panels("geneticFemale").dna!!
        assertEquals("♀ Female", dna.sex)
        assertEquals(10, dna.rows.size)
        assertEquals("Sex", dna.rows.first().label)
        assertEquals(6, dna.persona.size)
        val morph = dna.morph!!
        assertEquals("Body shape", morph.title)
        assertEquals(6, morph.rows.size)
        assertTrue(morph.tail.label.startsWith("Tail"))
        assertEquals("Temperament", dna.temperamentTitle)
        assertTrue(dna.mutations.isNotEmpty())
        assertTrue(dna.mutationsLead.startsWith("New mutation"))
        assertFalse(dna.genome.open)
        assertTrue(dna.genome.loci.isEmpty())
        assertEquals(listOf("Locus", "Chr", "From mother", "From father"), dna.genome.head)
        assertTrue(dna.genome.count >= 20)
    }

    @Test
    fun theGenomeTableComesOnlyWhileItIsOpen() {
        val dna = panels("geneticFemaleGenome").dna!!
        assertTrue(dna.genome.open)
        assertEquals(dna.genome.count, dna.genome.loci.size)
        assertTrue(dna.genome.loci.all { it.locus.isNotEmpty() && it.mother.isNotEmpty() })
    }

    @Test
    fun aSterileMaleHasItsNote() {
        val dna = panels("geneticSterile").dna!!
        assertTrue(dna.sterile.contains("XXY males are sterile"))
        assertFalse(dna.female)
    }

    @Test
    fun aKittensGeneticsAreShownForTheGrownCat() {
        assertTrue(panels("kitten").dna!!.sub.contains("(still growing)"))
        assertTrue(panels("kitten").dna!!.morph!!.intro.contains("(Shown for the grown cat.)"))
    }

    @Test
    fun twoCatsHaveTheTogetherCardAndTheSwitcher() {
        val p = panels("twoCats")
        val pair = p.pair!!
        assertEquals(0, pair.focus)
        assertEquals(listOf("Ted", "Lily"), pair.cats.map { it.name })
        val card = pair.card
        assertEquals("TOGETHER", card.title)
        assertEquals("Getting along", card.level)
        assertEquals(52, card.pct)
        assertTrue(card.counts.startsWith("4 nose touches"))
        assertTrue(card.last.startsWith("Last: groomed each other"))
        val rank = card.rank!!
        assertEquals("Ted", rank.line.bold)
        assertTrue(rank.settled)
        assertEquals(listOf(82, 41), rank.rows.map { it.pct })
        assertEquals("", decode("twoCats").together)
    }

    @Test
    fun theSecondCatInFocusHasItsOwnPanels() {
        val p = decode("twoCatsSecond")
        assertEquals(1, p.focus)
        assertEquals("Lily", p.name)
        assertEquals(1, p.panels!!.pair!!.focus)
        assertEquals("What Lily likes.", p.panels!!.likes!!.lead)
    }

    @Test
    fun oneCatHasNoTogetherCard() {
        assertNull(panels("bonded").pair)
    }

    @Test
    fun theToyListShowsOpenAndLockedToysWithTheirUnlocks() {
        val items = panels("stray").toys!!.items
        val ball = items.first { it.id == "ball_a" }
        assertTrue(ball.unlocked)
        assertEquals(0, ball.price)
        val feather = items.first { it.id == "feather" }
        assertFalse(feather.unlocked)
        assertTrue(feather.word.startsWith("Unlocks at"))
        assertEquals(200, feather.price)
        assertFalse(feather.bought)
    }

    @Test
    fun aToyBoughtEarlyIsOpenAndBought() {
        val toys = panels("strayBought").toys!!
        val feather = toys.items.first { it.id == "feather" }
        assertTrue(feather.unlocked)
        assertTrue(feather.bought)
        assertEquals(1000, toys.sparks)
    }

    /** One way the page's panels could be wrong, made from a good projection. */
    private class Break(val what: String, val change: JSONObject.() -> Unit)

    private fun JSONObject.panels() = getJSONObject("panels")

    private fun JSONObject.dna() = panels().getJSONObject("dna")

    private val breaks =
        listOf(
            Break("an unknown panel") { panels().put("diary", JSONObject()) },
            Break("a missing panel") { panels().remove("toys") },
            Break("panels as a list") { put("panels", JSONArray()) },
            Break("an unknown word") { panels().getJSONObject("words").put("dance", "x") },
            Break("a missing word") { panels().getJSONObject("words").remove("buy") },
            Break("an unknown key in the mind") { panels().getJSONObject("mind").put("x", 1) },
            Break("a number for the mood") { panels().getJSONObject("mind").put("mood", 5) },
            Break("a missing style id") {
                panels().getJSONObject("mind").getJSONObject("style").remove("id")
            },
            Break("a like over 100 percent") {
                panels()
                    .getJSONObject("likes")
                    .getJSONArray("places")
                    .getJSONObject(0)
                    .put("pct", 101)
            },
            Break("a like that is not a boolean") {
                panels()
                    .getJSONObject("likes")
                    .getJSONArray("places")
                    .getJSONObject(0)
                    .put("fav", 1)
            },
            Break("an unknown key in a place") {
                panels().getJSONObject("likes").getJSONArray("places").getJSONObject(0).put("x", 1)
            },
            Break("a river of places") {
                val places = panels().getJSONObject("likes").getJSONArray("places")
                repeat(20) { places.put(places.getJSONObject(0)) }
            },
            Break("a thread over 100 percent") {
                panels()
                    .getJSONObject("inner")
                    .getJSONArray("threads")
                    .getJSONObject(0)
                    .put("pct", 400)
            },
            Break("a fractional percentage") {
                panels()
                    .getJSONObject("inner")
                    .getJSONArray("threads")
                    .getJSONObject(0)
                    .put("pct", 12.5)
            },
            Break("a missing quirk text") {
                panels()
                    .getJSONObject("inner")
                    .getJSONArray("quirks")
                    .getJSONObject(0)
                    .remove("text")
            },
            Break("a quirk as words") { panels().getJSONObject("inner").put("quirks", "none") },
            Break("an unknown key in the dna") { dna().put("x", 1) },
            Break("a missing dna key") { dna().remove("sex") },
            Break("a female that is words") { dna().put("female", "yes") },
            Break("an unknown key in a gene row") {
                dna().getJSONArray("rows").getJSONObject(0).put("x", 1)
            },
            Break("a genome of three columns") {
                dna().getJSONObject("genome").put("head", JSONArray().put("a").put("b").put("c"))
            },
            Break("more loci than the genome says it has") {
                dna()
                    .getJSONObject("genome")
                    .put("count", 0)
                    .put(
                        "loci",
                        JSONArray()
                            .put(
                                JSONObject()
                                    .put("locus", "O")
                                    .put("q", false)
                                    .put("gene", "g")
                                    .put("chr", "X")
                                    .put("pos", 1)
                                    .put("mom", "O")
                                    .put("dad", "Y")
                            ),
                    )
            },
            Break("a locus with an unknown key") {
                dna().getJSONObject("genome").put("loci", JSONArray().put(JSONObject().put("x", 1)))
            },
            Break("a carries line with a stray key") {
                dna().put("carries", JSONObject().put("bold", "a").put("rest", "b").put("x", 1))
            },
            Break("carries as a list") { dna().put("carries", JSONArray().put("a")) },
            Break("a pair with a focus that names no cat") {
                panels().getJSONObject("pair").put("focus", 2)
            },
            Break("a pair with one cat") {
                panels()
                    .getJSONObject("pair")
                    .put("cats", JSONArray().put(JSONObject().put("name", "a").put("coat", "b")))
            },
            Break("a card without its level") {
                panels().getJSONObject("pair").getJSONObject("card").remove("level")
            },
            Break("a rank with a lead that names no cat") {
                panels()
                    .getJSONObject("pair")
                    .getJSONObject("card")
                    .getJSONObject("rank")
                    .put("lead", 2)
            },
            Break("a toy with a negative price") {
                panels()
                    .getJSONObject("toys")
                    .getJSONArray("items")
                    .getJSONObject(0)
                    .put("price", -1)
            },
            Break("negative Sparks") { panels().getJSONObject("toys").put("sparks", -1) },
            Break("a toy that is not a boolean") {
                panels()
                    .getJSONObject("toys")
                    .getJSONArray("items")
                    .getJSONObject(0)
                    .put("unlocked", "yes")
            },
            Break("a river of toys") {
                val items = panels().getJSONObject("toys").getJSONArray("items")
                repeat(30) { items.put(items.getJSONObject(0)) }
            },
            Break("a toy without a name") {
                panels().getJSONObject("toys").getJSONArray("items").getJSONObject(0).remove("name")
            },
        )

    /**
     * The good projection each break is made from: two cats, so every part of the panels is there.
     */
    private fun good() = fixture("twoCats")

    @Test
    fun everyMalformedPanelIsRefusedAndTheLastGoodStateKept() {
        for (b in breaks) {
            val repo = WebPetRepository().apply { accept(good(), true) }
            val bad = good().put("sequence", 2).apply(b.change)
            val refused = runCatching { repo.accept(bad) }.isFailure
            assertTrue("accepted: ${b.what}", refused)
            assertEquals("kept the last good state: ${b.what}", 1L, repo.state.value!!.sequence)
            assertNotNull(repo.state.value!!.panels)
        }
    }

    @Test
    fun aMalformedPanelIsRefusedOnAnOpenToo() {
        for (b in breaks) {
            val bad = good().apply(b.change)
            assertTrue(
                "accepted: ${b.what}",
                runCatching { WebPetRepository().accept(bad, true) }.isFailure,
            )
        }
    }

    @Test
    fun aPageThatStopsSendingPanelsTakesThemAway() {
        val repo = WebPetRepository().apply { accept(good(), true) }
        val older = PetFixtures.projection("two-cats").put("sequence", 2)
        repo.accept(older)
        assertNull(repo.state.value!!.panels)
    }

    @Test
    fun aPanelThePageCouldNotBuildIsLeftOut() {
        val json =
            good().apply { panels().put("dna", JSONObject.NULL).put("inner", JSONObject.NULL) }
        val p = WebPetRepository().apply { accept(json, true) }.state.value!!.panels!!
        assertNull(p.dna)
        assertNull(p.inner)
        assertNotNull(p.likes)
    }
}
