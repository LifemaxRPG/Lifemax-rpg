package com.lifemax.rpg.bridge.web

import android.os.Debug
import android.os.SystemClock
import com.getcapacitor.JSObject

/**
 * What priming a screen cost, as far as a phone can say (CONTRACT A24, `shell.prime.v1`). The heap
 * is read when the prime starts and again after the screen has had time to compose and be laid out
 * parked ([SETTLE_MS]); the difference is what the screen holds, give or take what the garbage
 * collector did meanwhile, so read it as a rough figure and compare screens, not as a bound. The
 * page keeps each answer (`window.__nativePrimePerf`) and BRIDGE_CATALOG lists the figures once
 * they have been read on the phone.
 */
internal class PrimeCost
private constructor(
    private val javaBytes: Long,
    private val nativeBytes: Long,
    private val startedAt: Long,
) {
    /** The cost now: heap growth in KB (Java, native) and the time since the start in ms. */
    fun read(): JSObject =
        JSObject()
            .put("javaKb", (used() - javaBytes) / BYTES_PER_KB)
            .put("nativeKb", (Debug.getNativeHeapAllocatedSize() - nativeBytes) / BYTES_PER_KB)
            .put("ms", SystemClock.uptimeMillis() - startedAt)

    companion object {
        /** Long enough for the first frames to compose the content and park the screen. */
        const val SETTLE_MS = 700L

        private const val BYTES_PER_KB = 1024L

        fun start() =
            PrimeCost(used(), Debug.getNativeHeapAllocatedSize(), SystemClock.uptimeMillis())

        private fun used(): Long = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }
    }
}
