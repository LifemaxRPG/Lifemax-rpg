package com.lifemax.rpg.app.shell

import com.lifemax.rpg.app.shell.swipe.SwipeFixtures
import com.lifemax.rpg.app.shell.swipe.SwipeGate
import com.lifemax.rpg.app.shell.swipe.TabSwipe
import com.lifemax.rpg.bridge.web.NativeCashPlugin
import com.lifemax.rpg.feature.cash.CashHost
import com.lifemax.rpg.feature.cash.CashSession
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NativeScreensTest {
    private val hosts = listOf("character", "food", "overview")
    private val dropped = mutableListOf<String>()
    private val cash = NativeCashPlugin()

    /** What Capacitor's BridgeWebViewClient calls when the WebView starts loading a page. */
    private fun pageStarts() = NativeScreens.onPageStarted(null)

    private fun addHosts() = hosts.forEach { host -> NativeScreens.add(host) { dropped += host } }

    @After fun removeHosts() = (hosts + "late" + cash).forEach(NativeScreens::remove)

    @Test
    fun aPageThatStartsLoadingDropsEveryScreenOnce() {
        addHosts()
        pageStarts()
        assertEquals(hosts, dropped)
    }

    @Test
    fun aHostThatWasDestroyedIsNotAsked() {
        addHosts()
        NativeScreens.remove("food")
        pageStarts()
        assertEquals(listOf("character", "overview"), dropped)
    }

    @Test
    fun aDropThatAddsAHostLetsEveryOtherHostDropToo() {
        // a drop may be the first touch of an object that then registers itself
        NativeScreens.add("character") {
            dropped += "character"
            NativeScreens.add("late") { dropped += "late" }
        }
        NativeScreens.add("food") { dropped += "food" }
        pageStarts()
        assertEquals(listOf("character", "food"), dropped)
    }

    @Test
    fun cashEndsItsSessionWithoutReportingToThePageThatIsGone() {
        var reported = false
        CashHost.session = CashSession("cash_1", JSONObject()) {}
        CashHost.closed = { _, _ -> reported = true }
        cash.load()
        pageStarts()
        assertNull("the new page may open Cash again", CashHost.session)
        assertNull(CashHost.closed)
        assertFalse(reported)
    }

    @Test
    fun theTabSwipeForgetsThePageThatIsGoneBeforeTheHostsDropTheirScreens() {
        var openAtTheDrop: Boolean? = null
        NativeScreens.add("character") { openAtTheDrop = TabSwipe.isOpen("character") }
        try {
            TabSwipe.opened("character")
            TabSwipe.scene(SwipeFixtures.swipeJson("character"), sequence = 1)
            assertNull("the page allows a swipe from Character", TabSwipe.gate("character"))
            pageStarts()
            assertEquals("a removal is teardown, not a swipe cut short", false, openAtTheDrop)
            assertFalse(TabSwipe.isOpen("character"))
            assertEquals(
                "no state until the new page draws a scene",
                SwipeGate.Off,
                TabSwipe.gate("character"),
            )
        } finally {
            TabSwipe.removed("character")
        }
    }
}
