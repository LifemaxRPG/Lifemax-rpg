package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.pet.art.RigMorph
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cat's proportions (`rig`) in the Cat tab's projection and in the Overview's live cats: read
 * strictly (the seven keys, numbers inside the genes' range), optional (a page that does not send
 * it, or a cat the art fits as drawn, is the art as drawn), and a bad one refuses the whole
 * projection so the last good one stays.
 */
class RigProjectionTest {
    private val rig = """{"em":2.2,"ew":1.5,"tl":1.8,"lg":1.4,"bl":1.5,"wk":1.8,"bob":false}"""

    private fun withRig(json: String?): JSONObject =
        PetFixtures.projection("alive").also {
            if (json != null) it.getJSONArray("cats").getJSONObject(0).put("rig", JSONObject(json))
        }

    private fun accepts(j: JSONObject): Boolean =
        runCatching { WebPetRepository().accept(j, true) }.isSuccess

    @Test
    fun theProportionsArriveWithTheCat() {
        val repo = WebPetRepository().apply { accept(withRig(rig), true) }
        val got = repo.state.value!!.cats[0].rig
        assertEquals(RigMorph(2.2, 1.5, 1.8, 1.4, 1.5, 1.8, false), got)
        assertFalse(got.neutral)
    }

    @Test
    fun noRigIsTheArtAsDrawn() {
        val repo = WebPetRepository().apply { accept(withRig(null), true) }
        assertTrue(repo.state.value!!.cats[0].rig.neutral)
    }

    @Test
    fun aBobtailIsCarried() {
        val repo =
            WebPetRepository().apply {
                accept(withRig(rig.replace("\"bob\":false", "\"bob\":true")), true)
            }
        assertTrue(repo.state.value!!.cats[0].rig.bob)
    }

    @Test
    fun aMalformedRigRefusesTheProjection() {
        val bad =
            listOf(
                """{"em":2.2,"ew":1.5,"tl":1.8,"lg":1.4,"bl":1.5,"wk":1.8}""", // no bob
                rig.replace("}", ""","extra":1}"""), // an unknown key
                rig.replace("\"em\":2.2", "\"em\":\"2.2\""), // a number as words
                rig.replace("\"em\":2.2", "\"em\":3.0"), // above the genes' range
                rig.replace("\"wk\":1.8", "\"wk\":0.1"), // below it
                rig.replace("\"bob\":false", "\"bob\":0"), // not a boolean
                "{}",
            )
        for (b in bad) assertFalse("accepted: $b", accepts(withRig(b)))
    }

    @Test
    fun aRigOnTheLiveCardsOfTheOverviewIsReadTheSameWay() {
        fun cards(extra: JSONObject?): JSONObject {
            val j =
                JSONObject(
                    javaClass.getResource("/fixtures/overview-cards-pet-live.json")!!.readText()
                )
            val cat = j.getJSONObject("tiles").getJSONObject("cat")
            if (extra != null) {
                cat.getJSONObject("live").getJSONArray("cats").getJSONObject(0).put("rig", extra)
            }
            return j
        }
        val ok = WebOverviewRepository().apply { accept(cards(JSONObject(rig)), true) }
        assertEquals(
            RigMorph(2.2, 1.5, 1.8, 1.4, 1.5, 1.8, false),
            ok.state.value!!.tiles.cat!!.live!!.first.rig,
        )
        val plain = WebOverviewRepository().apply { accept(cards(null), true) }
        assertTrue(plain.state.value!!.tiles.cat!!.live!!.first.rig.neutral)
        val bad = WebOverviewRepository().apply { accept(cards(null), true) }
        val refused =
            runCatching {
                    bad.accept(
                        cards(JSONObject(rig.replace("\"tl\":1.8", "\"tl\":9"))).put("sequence", 2),
                        true,
                    )
                }
                .isFailure
        assertTrue(refused)
        assertTrue(bad.state.value!!.tiles.cat!!.live!!.first.rig.neutral)
    }
}
