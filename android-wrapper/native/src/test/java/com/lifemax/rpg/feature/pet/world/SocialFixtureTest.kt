package com.lifemax.rpg.feature.pet.world

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page's own records of the pair's moments, as the real page wrote them
 * (`fixtures/pet-social.json`, recorded by lifemax_modular_v73/tests/native-pet-social.test.js with
 * RECORD_FIXTURE=1, after it proved the `social` command records exactly as the page's pair
 * director): FakePage, which every host test plays the page with, records the same friendship,
 * day's gain within the cap, counts, meeting and last moment for the same `social` commands, so the
 * native tests of the moments are tested against the page's real rules.
 */
class SocialFixtureTest {
    private val fixture =
        JSONObject(javaClass.getResource("/fixtures/pet-social.json")!!.readText())

    @Test
    fun fakePageRecordsTheMomentsAsThePageDoes() {
        val steps = fixture.getJSONArray("steps")
        assertTrue(steps.length() >= 6)
        for (i in 0 until steps.length()) {
            val s = steps.getJSONObject(i)
            val name = s.getString("name")
            val page = FakePage(NOW)
            val a = page.adopt(FakePage.Cat(born = NOW - 40 * DAY, name = "Ted", lastFed = NOW))
            val b = page.adopt(FakePage.Cat(born = NOW - 35 * DAY, name = "Lily", lastFed = NOW))
            val start = s.getJSONObject("start")
            val pair = page.pair!!
            pair.rel = start.getDouble("rel")
            pair.gain = start.getDouble("gain")
            pair.day = if (start.getBoolean("sameDay")) page.today else ""
            pair.met = !name.startsWith("first meeting")
            val kinds = s.getJSONArray("kinds")
            for (k in 0 until kinds.length()) {
                val kind = kinds.getString(k)
                val winner = if (kind == "standoff") a.id else null
                val r =
                    page.send(SocialReport("c:social:$i:$k", kind, listOf(a.id, b.id), winner, NOW))
                assertTrue("$name: ${r.optString("why")}", r.getBoolean("ok"))
            }
            assertEquals(name, s.getDouble("rel"), pair.rel, EPS)
            assertEquals(name, s.getDouble("gain"), pair.gain, EPS)
            assertEquals(name, s.getBoolean("met"), pair.met)
            assertEquals(name, s.getString("last"), pair.last)
            val n = s.getJSONObject("n")
            for (k in n.keys()) assertEquals("$name n.$k", n.getInt(k), pair.n[k] ?: 0)
        }
    }

    private companion object {
        const val NOW = 1_794_124_800_000L
        const val DAY = 86_400_000L
        const val EPS = 1e-6
    }
}
