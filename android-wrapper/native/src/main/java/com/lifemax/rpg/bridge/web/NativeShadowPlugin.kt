package com.lifemax.rpg.bridge.web

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.activity.result.ActivityResult
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import com.lifemax.rpg.app.shadow.DirectorShadowWorker
import com.lifemax.rpg.app.shadow.ShadowRing
import com.lifemax.rpg.app.shadow.ShadowWorker
import com.lifemax.rpg.core.directorshadow.DirectorShadowLimits
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
 *
 * The owner's side (WP6e, capability `economy.shadow.report.v1`): `copyReport` puts the redacted
 * report on the clipboard itself (the page never sees it) and answers ok once it is there;
 * `saveCase` writes one difference's unredacted case (decision O-4) to a file the owner picks in
 * the system's file picker, never on its own and never anywhere else, and answers ok once the file
 * is written; `clear` answers ok once the empty ring is on disk.
 *
 * The Life Director check (capability `director.shadow.v1`, LIFE_DIRECTOR_SHADOW_SPEC SH-1) joins
 * the same plugin: a call with `scope: "director"` goes to its own [DirectorShadowWorker] (its own
 * switch, counts and ring, on the same background thread); every other call is the economy's.
 */
@CapacitorPlugin(name = "NativeShadow")
class NativeShadowPlugin : Plugin() {
    private var executor: ExecutorService? = null
    private var worker: ShadowWorker? = null
    private var director: DirectorShadowWorker? = null

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
        director =
            DirectorShadowWorker(
                executor = pool,
                ring = ShadowRing(context.noBackupFilesDir, DirectorShadowWorker.FOLDER),
                now = System::currentTimeMillis,
                batterySaver = { batterySaver(context) },
                open = DirectorShadowWorker.opener(versionCode(context), nativeApi()),
            )
    }

    /** The Life Director check's worker when the call names its scope, else null. */
    private fun directorOf(call: PluginCall): DirectorShadowWorker? =
        if (call.getString("scope") == DirectorShadowLimits.SCOPE) director else null

    @PluginMethod
    fun handshake(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        w.handshake { h ->
            call.resolve(
                JSObject()
                    .put("protocol", h.protocol)
                    .put("capabilities", JSArray(h.capabilities + DirectorShadowLimits.CAPABILITY))
                    .put("fingerprint", h.fingerprint)
                    .put("state", h.state.word)
                    .maybe("reason", h.reason)
            )
        }
    }

    /** `{text}`: the configure message; answered once the switch state is in the ring. */
    @PluginMethod
    fun configure(call: PluginCall) {
        val text = call.getString("text") ?: return call.resolve(refused("missingText"))
        directorOf(call)?.let { d ->
            return d.configure(text) { a, stored -> call.resolve(answer(a).put("ok", a.ok && stored)) }
        }
        val w = worker ?: return call.reject("unavailable")
        w.configure(text) { a, stored -> call.resolve(answer(a).put("ok", a.ok && stored)) }
    }

    /** `{text}`: one base part. */
    @PluginMethod
    fun base(call: PluginCall) {
        val text = call.getString("text") ?: return call.resolve(refused("missingText"))
        directorOf(call)?.let { d -> return d.base(text) { call.resolve(answer(it)) } }
        val w = worker ?: return call.reject("unavailable")
        w.base(text) { call.resolve(answer(it)) }
    }

    /** `{text}`: one envelope; answered at once, the work happens on the worker. */
    @PluginMethod
    fun observe(call: PluginCall) {
        val d = directorOf(call)
        val w = worker ?: return call.reject("unavailable")
        val text = call.getString("text")
        val a =
            when {
                text == null -> ShadowAccept(false, true, "missingText")
                d != null -> d.observe(text)
                else -> w.observe(text)
            }
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
        val ask: ((com.lifemax.rpg.core.shadow.ShadowSummary) -> Unit) -> Unit =
            directorOf(call)?.let { d -> d::summary } ?: w::summary
        ask { s ->
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
            out.put(
                "cases",
                JSArray(
                    s.cases.map {
                        JSObject()
                            .put("id", it.id)
                            .put("kind", it.kind)
                            .put("at", it.at)
                            .put("count", it.count)
                    }
                ),
            )
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
        directorOf(call)?.let { d -> return d.clear { stored -> call.resolve(JSObject().put("ok", stored)) } }
        val w = worker ?: return call.reject("unavailable")
        w.clear { stored -> call.resolve(JSObject().put("ok", stored)) }
    }

    /** The redacted report, onto the clipboard; ok with its length once the clipboard holds it. */
    @PluginMethod
    fun copyReport(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        val d = directorOf(call)
        val label = if (d != null) DIRECTOR_REPORT_LABEL else REPORT_LABEL
        val build: ((String) -> Unit) -> Unit = d?.let { { a: (String) -> Unit -> it.report(answer = a) } } ?: { a -> w.report(answer = a) }
        build { text ->
            bridge.executeOnMainThread {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val ok =
                    try {
                        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
                        clipboard != null
                    } catch (_: RuntimeException) {
                        false
                    }
                call.resolve(JSObject().put("ok", ok).put("chars", if (ok) text.length else 0))
            }
        }
    }

    /**
     * `{id}`: the owner chose one difference and confirmed the warning; the system's file picker
     * asks where to save its case. Ok only once the file is written; `{ok:false, reason}` when no
     * case is held for it (`noCase`), the owner closed the picker (`cancelled`) or the write failed
     * (`notWritten`).
     */
    @PluginMethod
    fun saveCase(call: PluginCall) {
        val w = worker ?: return call.reject("unavailable")
        val id = call.getString("id")
        if (id == null || !CASE_ID.matches(id)) return call.resolve(refused("noCase"))
        w.case(id) { text ->
            if (text == null) return@case call.resolve(refused("noCase"))
            bridge.executeOnMainThread {
                pendingCase = text
                val intent =
                    Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(CASE_MIME)
                        .putExtra(Intent.EXTRA_TITLE, "lifemax-economy-case-$id.json")
                startActivityForResult(call, intent, "caseChosen")
            }
        }
    }

    @ActivityCallback
    private fun caseChosen(call: PluginCall?, result: ActivityResult) {
        val text = pendingCase
        pendingCase = null
        call ?: return
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null || text == null)
            return call.resolve(refused("cancelled"))
        val write = Runnable {
            val ok =
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use {
                        it.write(text.toByteArray(Charsets.UTF_8))
                        it.flush()
                        true
                    } ?: false
                } catch (_: Exception) {
                    false
                }
            call.resolve(if (ok) JSObject().put("ok", true) else refused("notWritten"))
        }
        try {
            executor?.execute(write) ?: write.run()
        } catch (_: RuntimeException) {
            call.resolve(refused("notWritten"))
        }
    }

    override fun handleOnStop() {
        worker?.flush()
        director?.flush()
        super.handleOnStop()
    }

    override fun handleOnDestroy() {
        worker?.flush()
        director?.flush()
        executor?.shutdown()
        super.handleOnDestroy()
    }

    private fun answer(a: ShadowAnswer): JSObject =
        JSObject().put("ok", a.ok).put("state", a.state.word).maybe("reason", a.reason)

    private fun refused(reason: String) = JSObject().put("ok", false).put("reason", reason)

    /** The case waiting for the owner's choice of file; memory only. */
    @Volatile private var pendingCase: String? = null

    private fun JSObject.maybe(key: String, value: String?): JSObject = apply {
        if (value != null) put(key, value)
    }

    private companion object {
        const val REPORT_LABEL = "LifeMax economy check report"
        const val DIRECTOR_REPORT_LABEL = "LifeMax Life Director check report"
        const val CASE_MIME = "application/json"
        val CASE_ID = Regex("m-[0-9]{1,9}")

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
