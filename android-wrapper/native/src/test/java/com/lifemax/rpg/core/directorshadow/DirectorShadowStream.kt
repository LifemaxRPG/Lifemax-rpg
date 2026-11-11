package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.shadow.ShadowLedger
import com.lifemax.rpg.core.shadow.ShadowLimits
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * The Life Director check's messages for the lived-in day, as the real page sent them to the
 * stand-in plugin (`lifemax_modular_v73/tests/native-director-shadow.test.js` RECORD=1): `[method,
 * text]` in order. [play] hands them to a session exactly as the plugin would.
 */
object DirectorShadowStream {
    const val FILE = "director-shadow-stream.json.gz"
    const val T = 1_793_000_000_000L

    val messages: List<Pair<String, String>> by lazy {
        val start = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val f =
            generateSequence(start) { it.parentFile }
                .map { File(it, "lifemax_modular_v73/tests/fixtures/$FILE") }
                .firstOrNull { it.isFile } ?: error("$FILE not found above $start")
        val text = GZIPInputStream(f.inputStream()).bufferedReader(Charsets.UTF_8).readText()
        val root = (JsonReader.read(text) as JsonRead.Parsed).value as JsonObject
        (root.members["stream"] as JsonArray).items.map {
            val pair = (it as JsonArray).items
            (pair[0] as JsonString).value to (pair[1] as JsonString).value
        }
    }

    fun ledger(): ShadowLedger = ShadowLedger(T, ByteArray(ShadowLimits.SALT_BYTES) { 3 })

    fun session(
        checks: ((com.lifemax.rpg.core.shadow.ShadowRedactor) -> DirectorChecks)? = null
    ): DirectorShadowSession = DirectorShadowSession(ledger(), 1, 135, checks = checks)

    /**
     * Every message to [s], as the plugin hands them over; [edit] may change an envelope's text
     * first.
     */
    fun play(s: DirectorShadowSession, edit: (String) -> String = { it }) {
        var t = T
        for ((method, text) in messages) {
            t += 1000
            when (method) {
                "configure" -> s.configure(text, batterySaver = false)
                "base" -> s.base(text, t)
                "observe" -> if (s.accept(text.length, false).accepted) s.observe(edit(text), t)
            }
        }
    }

    /** The envelopes of [kind]. */
    fun envelopes(kind: String): List<String> =
        messages
            .filter { it.first == "observe" && it.second.contains("\"kind\":\"$kind\"") }
            .map { it.second }
}
