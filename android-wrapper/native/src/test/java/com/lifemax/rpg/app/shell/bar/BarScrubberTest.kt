package com.lifemax.rpg.app.shell.bar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bar for the scrubber: a clock the test moves, and everything the scrubber asks of it noted. */
private class FakeBar(val tabs: List<BarTab> = bar(14), override val viewport: Float = 400f) :
    ScrubHost {
    var clock = 0L
    override var scroll = 0f
    override val maxScroll = (tabs.last().right - viewport).coerceAtLeast(0f)
    val opened = mutableListOf<String>()
    val buzzes = mutableListOf<ScrubBuzz>()
    var shown: BarTab? = null
    val holds = mutableListOf<Boolean>()
    private val waiting = HashMap<Int, Pair<Long, () -> Unit>>()

    override fun scrollToDp(dp: Float) {
        scroll = dp.coerceIn(0f, maxScroll)
    }

    override fun open(id: String) {
        opened += id
    }

    override fun buzz(kind: ScrubBuzz) {
        buzzes += kind
    }

    override fun highlight(tab: BarTab?) {
        shown = tab
    }

    override fun hold(on: Boolean) {
        holds += on
    }

    override fun after(slot: Int, delayMs: Long, action: () -> Unit) {
        waiting[slot] = clock + delayMs to action
    }

    override fun cancel(slot: Int) {
        waiting.remove(slot)
    }

    /** Let [ms] pass, running what falls due. */
    fun advance(ms: Long) {
        val end = clock + ms
        while (true) {
            val next =
                waiting.entries.filter { it.value.first <= end }.minByOrNull { it.value.first }
            if (next == null) break
            waiting.remove(next.key)
            clock = next.value.first
            next.value.second()
        }
        clock = end
    }

    companion object {
        /** [n] tabs of 64 dp side by side, tab i called "t$i". */
        fun bar(n: Int) = List(n) { BarTab("t$it", "id$it", it * 64f, it * 64f + 64f, 6f, 58f) }
    }
}

class BarScrubberTest {
    private fun scrubber(
        host: FakeBar,
        active: String = "t0",
        haptics: Boolean = true,
        speed: ScrubSpeed = ScrubSpeed.Normal,
    ) =
        BarScrubber(host).apply {
            scene =
                BarScrubScene(
                    host.tabs,
                    active,
                    speed,
                    haptics,
                    50,
                    0x33223344,
                    12f,
                    0xFF0E1117.toInt(),
                )
        }

    /**
     * Slide from [from] to [to] dp in [steps] moves, [stepMs] apart; true if it was ever a scrub.
     */
    private fun BarScrubber.slide(
        host: FakeBar,
        from: Float,
        to: Float,
        steps: Int,
        stepMs: Long,
    ): Boolean {
        var taken = false
        for (k in 1..steps) {
            host.advance(stepMs)
            taken = move(from + (to - from) * k / steps, 30f, host.clock) || taken
        }
        return taken
    }

    @Test
    fun aScrubKeepsTheBarUntilItHasLanded() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 380f, 20, 8)
        assertEquals(listOf(true), host.holds) // asked once, at the start, not on every move
        s.up()
        assertEquals(listOf(true), host.holds) // the pill has not landed yet
        host.advance(BarScrubber.RELEASE_MS + 1)
        assertEquals(listOf(true, false), host.holds)
    }

    @Test
    fun aSceneThatArrivesAfterTheScrubLetsTheBarGo() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 380f, 20, 8)
        s.up()
        s.sceneArrived(host.shown!!.key)
        assertEquals(listOf(true, false), host.holds)
    }

    @Test
    fun aTapNeverHoldsTheBar() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(100f, 30f)
        s.move(102f, 31f, 10)
        s.up()
        host.advance(BarScrubber.RELEASE_MS + 1)
        assertTrue(host.holds.isEmpty())
    }

    @Test
    fun aTapIsNotAScrub() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(100f, 30f)
        assertFalse(s.move(102f, 31f, 10))
        s.up()
        assertFalse(s.scrubbing)
        assertTrue(host.opened.isEmpty())
        assertTrue(host.buzzes.isEmpty())
    }

    @Test
    fun aSlideMoreUpThanAlongIsNotAScrub() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(100f, 50f)
        assertFalse(s.move(112f, 10f, 10)) // 12 along, 40 up
        assertFalse(s.move(140f, 10f, 20)) // and it stays off for the rest of the touch
        assertFalse(s.scrubbing)
    }

    @Test
    fun aSlideStartsAScrubWithAFirmPulseAndShowsTheStartTab() {
        val host = FakeBar()
        val s = scrubber(host, active = "t3")
        s.down(200f, 30f)
        assertTrue(s.move(207f, 30f, 10))
        assertTrue(s.scrubbing)
        assertEquals(listOf(ScrubBuzz.Start), host.buzzes)
        assertEquals("t3", host.shown?.key)
    }

    @Test
    fun slidingAcrossTheBarGoesThroughEveryTabToTheLastOne() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 400f, steps = 120, stepMs = 8)
        assertEquals("t13", host.shown?.key)
        // a tick for each tab passed, none missing, in order
        assertEquals(13, host.buzzes.count { it == ScrubBuzz.Tick })
        s.up()
        assertEquals("id13", host.opened.last())
        assertEquals(ScrubBuzz.End, host.buzzes.last())
    }

    @Test
    fun theStripScrollsToKeepTheTabUnderTheFingerInView() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        for (k in 1..120) {
            host.advance(8)
            s.move(40f + 360f * k / 120, 30f, host.clock)
            val shown = host.shown ?: continue
            assertTrue(
                "${shown.key} in view",
                shown.left >= host.scroll && shown.right <= host.scroll + host.viewport,
            )
        }
        assertEquals(host.maxScroll, host.scroll, 0.01f)
    }

    @Test
    fun theScrubSpeedSettingIsHonoured() {
        fun tabAfterShortSlide(speed: ScrubSpeed): Int {
            val host = FakeBar()
            val s = scrubber(host, speed = speed)
            s.down(100f, 30f)
            s.slide(host, 100f, 160f, steps = 12, stepMs = 8)
            return host.shown!!.key.drop(1).toInt()
        }
        val normal = tabAfterShortSlide(ScrubSpeed.Normal)
        val fast = tabAfterShortSlide(ScrubSpeed.Fast)
        val faster = tabAfterShortSlide(ScrubSpeed.Faster)
        assertTrue("$normal < $fast < $faster", normal < fast && fast < faster)
    }

    @Test
    fun aSlowFingerOpensEachTabItRestsOn() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 90f, steps = 5, stepMs = 80) // two tabs along, slowly: 0.125 dp a ms
        host.advance(150)
        assertTrue("opened what it rests on", host.opened.isNotEmpty())
        val rested = host.shown!!
        assertEquals(rested.id, host.opened.last())
    }

    @Test
    fun aFastFingerOpensNothingUntilItEasesOrLifts() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 400f, steps = 18, stepMs = 5) // 20 dp every 5 ms: 4 dp/ms
        host.advance(80)
        assertTrue("still moving fast: no tab opened yet", host.opened.isEmpty())
        s.up()
        assertEquals(listOf("id13"), host.opened)
    }

    @Test
    fun theFingerLiftingOpensTheTabUnderItOnce() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 200f, steps = 8, stepMs = 12)
        host.advance(300) // it rested: the page went there
        assertEquals(listOf("id6"), host.opened)
        val before = host.opened.size
        s.up()
        assertEquals("nothing more to open: it is already there", before, host.opened.size)
    }

    @Test
    fun aScrubThatComesBackToTheStartTabOpensNothing() {
        val host = FakeBar()
        val s = scrubber(host, active = "t5")
        s.down(200f, 30f)
        s.move(260f, 30f, 10)
        s.move(200f, 30f, 20)
        s.up()
        assertTrue(host.opened.isEmpty())
    }

    @Test
    fun hapticsOffMakesNoBuzz() {
        val host = FakeBar()
        val s = scrubber(host, haptics = false)
        s.down(40f, 30f)
        s.slide(host, 40f, 300f, steps = 30, stepMs = 8)
        s.up()
        assertTrue(host.buzzes.isEmpty())
        assertTrue("it still scrubs", host.opened.isNotEmpty())
    }

    @Test
    fun theSelectedPillStaysUntilThePageShowsItThere() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 200f, steps = 8, stepMs = 12)
        s.up()
        val landed = host.shown
        assertEquals("t6", landed?.key)
        s.sceneArrived("t3") // an earlier tab's scene: not the one it ended on
        assertEquals(landed, host.shown)
        s.sceneArrived("t6")
        assertNull(host.shown)
    }

    @Test
    fun theSelectedPillGoesAfterAMomentIfNoSceneComes() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 200f, steps = 8, stepMs = 12)
        s.up()
        host.advance(BarScrubber.RELEASE_MS + 10)
        assertNull(host.shown)
    }

    @Test
    fun aBarOfOneTabIsNotScrubbed() {
        val host = FakeBar(tabs = FakeBar.bar(1))
        val s = scrubber(host)
        s.down(100f, 30f)
        assertFalse(s.move(160f, 30f, 10))
    }

    @Test
    fun aBarWithNoSceneFromThePageIsNotScrubbed() {
        val host = FakeBar()
        val s = BarScrubber(host)
        s.down(100f, 30f)
        assertFalse(s.move(160f, 30f, 10))
    }

    @Test
    fun aPageOnAViewTheBarHasNoTabForStartsAtTheFirstTab() {
        val host = FakeBar()
        val s = scrubber(host, active = "settings")
        s.down(40f, 30f)
        s.move(46f, 30f, 10)
        assertEquals("t0", host.shown?.key)
    }

    @Test
    fun aCancelledTouchEndsLikeALift() {
        val host = FakeBar()
        val s = scrubber(host)
        s.down(40f, 30f)
        s.slide(host, 40f, 400f, steps = 18, stepMs = 5)
        s.cancel()
        assertEquals(listOf("id13"), host.opened)
        assertFalse(s.scrubbing)
    }
}
