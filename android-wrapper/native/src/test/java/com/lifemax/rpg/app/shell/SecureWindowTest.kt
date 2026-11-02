package com.lifemax.rpg.app.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The window's secure flag follows every holder (CONTRACT A35 item 4). */
class SecureWindowTest {
    private val page = Any()
    private val journal = Any()

    @Test
    fun eitherHolderSetsTheFlag() {
        val h = SecureHolds()
        assertEquals(true, h.hold(journal, true))
        assertNull(h.hold(page, true)) // already secure
        assertTrue(h.secure)
    }

    @Test
    fun onlyTheLastReleaseClearsIt() {
        val h = SecureHolds()
        h.hold(page, true)
        h.hold(journal, true)
        // the page leaves `history` while the native Journal is still drawn (make-before-break)
        assertNull(h.hold(page, false))
        assertTrue(h.secure)
        assertEquals(false, h.hold(journal, false))
        assertFalse(h.secure)
    }

    @Test
    fun aStaleReleaseChangesNothing() {
        val h = SecureHolds()
        assertNull(h.hold(page, false))
        h.hold(journal, true)
        assertNull(h.hold(page, false))
        assertNull(h.hold(journal, true)) // a repeat
        assertTrue(h.secure)
    }
}
