package com.lifemax.rpg.feature.pet.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mood's tone on the body (CatBody.tone, BodyTone): neutral changes nothing at all (every pose
 * field bitwise, so the shipped traces stay as they are), a content cat carries its tail higher, an
 * excited one walks faster and pricks its ears, a calm one breathes deeper.
 */
class BodyToneTest {
    private fun walk(body: CatBody, seconds: Float): List<String> {
        body.replacePlan(1, listOf(PlanStep.Walk(300f), PlanStep.Act("idle_sit", 3f)))
        val out = ArrayList<String>()
        repeat(Math.round(seconds * 60)) {
            body.step(1f / 60f)
            out += TraceSupport.fingerprint(body.pose)
        }
        return out
    }

    @Test
    fun aNeutralToneIsTheBodyAsItWas() {
        val plain = CatBody(seed = 5)
        val toned = CatBody(seed = 5).also { it.tone(0f, 0f) }
        assertEquals(walk(plain, 8f), walk(toned, 8f))
    }

    @Test
    fun contentLiftsTheTailAndExcitementQuickensTheWalk() {
        val calm = CatBody(seed = 5).also { it.tone(0.8f, -0.6f) }
        val excited = CatBody(seed = 5).also { it.tone(0.2f, 0.9f) }
        val plain = CatBody(seed = 5)
        repeat(240) { for (b in listOf(calm, excited, plain)) b.step(1f / 60f) }
        assertTrue("tail", calm.pose.tail[0] > plain.pose.tail[0])
        assertTrue(
            "ears forward",
            excited.pose.ear[1] < plain.pose.ear[1] || excited.pose.ear[0] > plain.pose.ear[0],
        )
        // an excited cat covers the same walk sooner
        val a = CatBody(seed = 5).also { it.tone(0f, 1f) }
        val b = CatBody(seed = 5)
        for (c in listOf(a, b)) c.replacePlan(1, listOf(PlanStep.Walk(250f)))
        repeat(120) { for (c in listOf(a, b)) c.step(1f / 60f) }
        assertTrue("faster ${a.pose.x} vs ${b.pose.x}", a.pose.x > b.pose.x + 2f)
        // calm breathes deeper: the breath swings further from 1 over a cycle
        fun swing(c: CatBody): Float {
            var most = 0f
            repeat(300) {
                c.step(1f / 60f)
                most = maxOf(most, Math.abs(c.pose.breath - 1f))
            }
            return most
        }
        val deep = swing(CatBody(seed = 5).also { it.tone(0.5f, -1f) })
        val usual = swing(CatBody(seed = 5))
        assertTrue("deep $deep vs $usual", deep > usual)
        assertNotEquals(deep, usual)
    }
}
