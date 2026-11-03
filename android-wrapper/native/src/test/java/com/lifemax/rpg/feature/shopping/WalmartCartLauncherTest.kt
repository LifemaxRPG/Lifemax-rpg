package com.lifemax.rpg.feature.shopping

import com.lifemax.rpg.feature.shopping.data.LaunchOutcome
import com.lifemax.rpg.feature.shopping.data.NoAppToOpen
import com.lifemax.rpg.feature.shopping.data.OpenedVia
import com.lifemax.rpg.feature.shopping.data.UrlOpener
import com.lifemax.rpg.feature.shopping.data.WalmartCartLauncher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A phone that is told what to do: records every attempt, never touches Android. */
class FakeOpener(var system: () -> Unit = {}, var browser: () -> Unit = {}) : UrlOpener {
    val calls = mutableListOf<String>()

    override fun viewWithSystemHandling(url: String) {
        calls += "system:$url"
        system()
    }

    override fun viewInBrowser(url: String) {
        calls += "browser:$url"
        browser()
    }
}

class WalmartCartLauncherTest {
    private val url = "https://affil.walmart.com/cart/addToCart?items=145051970%7C1%2C165233284%7C1"
    private var now = 1_000_000L

    private fun launcher(opener: FakeOpener) =
        WalmartCartLauncher(opener, { now }, repeatWindowMs = 60_000)

    @Test
    fun theLinkGoesToTheSystemFirstSoTheWalmartAppCanTakeIt() {
        val opener = FakeOpener()
        val out = launcher(opener).launch(url)
        assertEquals(LaunchOutcome.Handed(OpenedVia.SYSTEM_LINK_HANDLING), out)
        assertEquals(listOf("system:$url"), opener.calls)
    }

    @Test
    fun whenNoAppTakesTheLinkTheBrowserIsTried() {
        val opener = FakeOpener(system = { throw NoAppToOpen("none") })
        val out = launcher(opener).launch(url)
        assertEquals(LaunchOutcome.Handed(OpenedVia.BROWSER), out)
        assertEquals(listOf("system:$url", "browser:$url"), opener.calls)
    }

    @Test
    fun whenNothingCanOpenItTheLaunchFailsOnceAndSaysSo() {
        val opener = FakeOpener({ throw NoAppToOpen("none") }, { throw NoAppToOpen("no browser") })
        val l = launcher(opener)
        val out = l.launch(url)
        assertTrue(out is LaunchOutcome.Failed)
        assertEquals(2, opener.calls.size) // one try each, no loop
        assertFalse(
            l.openedRecently(url)
        ) // a failure is not a launch: no confirmation needed next time
    }

    @Test
    fun anUnexpectedErrorIsNotRetriedInTheBrowser() {
        val opener = FakeOpener(system = { throw IllegalStateException("boom") })
        val out = launcher(opener).launch(url)
        assertTrue(out is LaunchOutcome.Failed)
        assertEquals(
            listOf("system:$url"),
            opener.calls,
        ) // the link may have opened: never try twice
    }

    @Test
    fun aSecondLaunchOfTheSameCartNeedsConfirmation() {
        val opener = FakeOpener()
        val l = launcher(opener)
        l.launch(url)
        now += 5_000
        assertEquals(LaunchOutcome.NeedsConfirmation, l.launch(url))
        assertEquals(1, opener.calls.size) // nothing was opened without a yes
    }

    @Test
    fun openAgainAnywayOpensItOnceMore() {
        val opener = FakeOpener()
        val l = launcher(opener)
        l.launch(url)
        now += 5_000
        assertTrue(l.launch(url, openAgainAnyway = true) is LaunchOutcome.Handed)
        assertEquals(2, opener.calls.size)
    }

    @Test
    fun afterTheWindowTheSameCartOpensWithoutAsking() {
        val opener = FakeOpener()
        val l = launcher(opener)
        l.launch(url)
        now += 61_000
        assertTrue(l.launch(url) is LaunchOutcome.Handed)
        assertEquals(2, opener.calls.size)
    }

    @Test
    fun aDifferentCartIsNotHeldBackByTheFirst() {
        val opener = FakeOpener()
        val l = launcher(opener)
        l.launch(url)
        assertTrue(l.launch("$url%2C99999%7C1") is LaunchOutcome.Handed)
        assertEquals(2, opener.calls.size)
    }

    @Test
    fun theLauncherNeverOpensAnythingByItselfAfterTheFirstCall() {
        val opener = FakeOpener()
        val l = launcher(opener)
        l.launch(url)
        now += 1
        l.openedRecently(url)
        now += 1
        assertEquals(1, opener.calls.size)
    }
}
