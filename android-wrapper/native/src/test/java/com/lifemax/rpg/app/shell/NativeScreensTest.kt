package com.lifemax.rpg.app.shell

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

    @After fun removeHosts() = (hosts + cash).forEach(NativeScreens::remove)

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
}
