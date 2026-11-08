package com.lifemax.rpg.bridge.web

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.Process
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shadow.ShadowRing
import com.lifemax.rpg.app.shadow.ShadowWorker
import com.lifemax.rpg.core.quests.QuestContentLoader
import com.lifemax.rpg.core.quests.QuestContentRead
import com.lifemax.rpg.core.shadow.ShadowAccept
import com.lifemax.rpg.core.shadow.ShadowAnswer
import java.util.concurrent.ExecutorService

/**
 * The economy shadow's bridge (capability `economy.shadow.v1`, ECONOMY_SHADOW_PLAN 2.3; a plain
 * plugin like NativeAudio, not a tab). It decodes nothing itself: each call's `text` goes to the
 * [ShadowWorker] on its own background thread, and `observe` is answered before any work. It never
 * sends a command, never notifies the page, never writes the save and never uses the network; the
 * only file is the ring ([ShadowRing], in the no-backup folder). Every failure is answered, never
 * thrown into the page.
 */
@CapacitorPlugin(name = "NativeShadow")
class NativeShadowPlugin : Plugin() {
    private var executor: ExecutorService? = null
    private var worker: ShadowWorker? = null

    override fun load() {
        val context = context.applicationContext
        val pool =
            ShadowWorker.backgroundExecutor {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            }
        executor = pool
        worker =
            ShadowWorker(
                executor = pool,
                ring = ShadowRing(context.noBackupFilesDir),
                now = System::currentTimeMillis,
                batterySaver = { batterySaver(context) },
                open = ShadowWorker.opener(::fingerprint, versionCode(context), nativeApi()),
            )
    }

    @PluginMethod
    fun handshake(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        w.handshake { h ->
            call.resolve(
                JSObject()
                    .put("protocol", h.protocol)
                    .put("capabilities", JSArray(h.capabilities))
                    .put("fingerprint", h.fingerprint)
                    .put("state", h.state.word)
                    .maybe("reason", h.reason)
            )
        }
    }

    /** `{text}`: the configure message; answered once the switch state is in the ring. */
    @PluginMethod
    fun configure(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        val text = call.getString("text") ?: return call.resolve(refused("missingText"))
        w.configure(text) { a, stored -> call.resolve(answer(a).put("ok", a.ok && stored)) }
    }

    /** `{text}`: one base part. */
    @PluginMethod
    fun base(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        val text = call.getString("text") ?: return call.resolve(refused("missingText"))
        w.base(text) { call.resolve(answer(it)) }
    }

    /** `{text}`: one envelope; answered at once, the work happens on the worker. */
    @PluginMethod
    fun observe(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        val text = call.getString("text")
        val a = if (text == null) ShadowAccept(false, true, "missingText") else w.observe(text)
        call.resolve(
            JSObject()
                .put("accepted", a.accepted)
                .apply { if (!a.accepted) put("resync", a.resync) }
                .maybe("reason", a.reason)
        )
    }

    /** Counts only, for the Settings row. */
    @PluginMethod
    fun summary(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        w.summary { s ->
            val out =
                JSObject()
                    .put("v", 1)
                    .put("state", s.state.word)
                    .maybe("reason", s.reason)
                    .put("since", s.since)
                    .put(
                        "counts",
                        JSObject()
                            .put("seen", s.seen)
                            .put("applied", s.applied)
                            .put("skipped", s.skipped)
                            .put("compared", s.compared)
                            .put("same", s.same)
                            .put("allowed", s.allowed)
                            .put("different", s.different),
                    )
            s.lastDifferent?.let {
                out.put("different", JSObject().put("at", it.last).put("kind", it.kind))
            }
            s.last?.let {
                out.put(
                    "last",
                    JSObject()
                        .put("at", it.last)
                        .put("kind", it.kind)
                        .put("paths", JSArray(it.paths)),
                )
            }
            call.resolve(out)
        }
    }

    /** Empties the ring; ok only once the empty ring is on disk. */
    @PluginMethod
    fun clear(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        w.clear { stored -> call.resolve(JSObject().put("ok", stored)) }
    }

    override fun handleOnStop() {
        worker?.flush()
        super.handleOnStop()
    }

    override fun handleOnDestroy() {
        worker?.flush()
        executor?.shutdown()
        super.handleOnDestroy()
    }

    private fun answer(a: ShadowAnswer): JSObject =
        JSObject().put("ok", a.ok).put("state", a.state.word).maybe("reason", a.reason)

    private fun refused(reason: String) = JSObject().put("ok", false).put("reason", reason)

    private fun JSObject.maybe(key: String, value: String?): JSObject = apply {
        if (value != null) put(key, value)
    }

    private companion object {
        /** What native's rules were built from: the packaged quest content's hash (2.4). */
        fun fingerprint(): String =
            (QuestContentLoader.packaged as? QuestContentRead.Loaded)?.content?.contentHash ?: ""

        fun batterySaver(context: Context): Boolean =
            (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ==
                true

        fun versionCode(context: Context): Long =
            try {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
                else @Suppress("DEPRECATION") info.versionCode.toLong()
            } catch (_: Exception) {
                0L
            }

        /** NativeApi.VALUE lives in the Java shell's package; read it without widening it. */
        fun nativeApi(): Int =
            try {
                Class.forName("com.lifemax.rpg.NativeApi")
                    .getDeclaredField("VALUE")
                    .apply { isAccessible = true }
                    .getInt(null)
            } catch (_: Exception) {
                0
            }
    }
}
