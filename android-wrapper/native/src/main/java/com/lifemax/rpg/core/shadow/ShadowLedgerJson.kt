package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonNull
import com.lifemax.rpg.core.save.JsonNumber
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonRead
import com.lifemax.rpg.core.save.JsonReader
import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonValue
import com.lifemax.rpg.core.save.JsonWriter

/**
 * The ring file's text (ECONOMY_SHADOW_PLAN 3.1): `{v, since, salt, state, reason,
 * app:{versionCode, nativeApi}, faults:{native, page}, counts:{kind:{seen, applied,
 * skipped:{why:n}}}, refused:{why:n}, info:{word:n}, errors:{class:n}, errorTimes, resyncTimes,
 * nextId, entries:[{id, first, last, count, kind, pageBuild, paths}]}`. Reading is strict about the
 * shape and never throws; a file that does not read is replaced by a fresh ledger (the caller
 * counts `info.ringReset`).
 */
object ShadowLedgerJson {
    private const val VERSION = 1

    /** The ledger as the ring's JSON text. */
    fun write(ledger: ShadowLedger): String = JsonWriter.write(value(ledger))

    private fun value(l: ShadowLedger): JsonObject =
        obj(
            "v" to num(VERSION),
            "since" to num(l.since),
            "salt" to JsonString(hex(l.salt)),
            "state" to JsonString(l.state.word),
            "reason" to (l.reason?.let { JsonString(it) } ?: JsonNull),
            "app" to obj("versionCode" to num(l.versionCode), "nativeApi" to num(l.nativeApi)),
            "faults" to obj("native" to num(l.nativeFaults), "page" to num(l.pageFaults)),
            "counts" to
                JsonObject(
                    l.kinds.mapValuesTo(LinkedHashMap()) { (_, k) ->
                        obj(
                            "seen" to num(k.seen),
                            "applied" to num(k.applied),
                            "skipped" to counts(k.skipped),
                        )
                    }
                ),
            "refused" to counts(l.refused),
            "info" to counts(l.info),
            "errors" to counts(l.errors),
            "errorTimes" to JsonArray(l.errorTimes.map { num(it) }),
            "resyncTimes" to JsonArray(l.resyncTimes.map { num(it) }),
            "nextId" to num(l.nextId),
            "entries" to
                JsonArray(
                    l.entries.map {
                        obj(
                            "id" to JsonString(it.id),
                            "first" to num(it.first),
                            "last" to num(it.last),
                            "count" to num(it.count),
                            "kind" to JsonString(it.kind),
                            "pageBuild" to JsonString(it.pageBuild),
                            "paths" to JsonArray(it.paths.map { p -> JsonString(p) }),
                        )
                    }
                ),
        )

    /** Reads a ring text back, or null when it is not one this build wrote. */
    fun read(text: String): ShadowLedger? {
        val root = (JsonReader.read(text) as? JsonRead.Parsed)?.value as? JsonObject ?: return null
        return try {
            decode(root)
        } catch (_: Bad) {
            null
        }
    }

    private fun decode(o: JsonObject): ShadowLedger {
        if (long(o, "v") != VERSION.toLong()) bad()
        val salt = unhex(str(o, "salt"))
        if (salt.size != ShadowLimits.SALT_BYTES) bad()
        val l = ShadowLedger(long(o, "since"), salt)
        l.state = ShadowState.of(str(o, "state")) ?: bad()
        l.reason = (o["reason"] ?: bad()).let { if (it == JsonNull) null else str(it) }
        val app = o["app"] as? JsonObject ?: bad()
        l.versionCode = long(app, "versionCode")
        l.nativeApi = long(app, "nativeApi").toInt()
        val faults = o["faults"] as? JsonObject ?: bad()
        l.nativeFaults = long(faults, "native").toInt()
        l.pageFaults = long(faults, "page").toInt()
        for ((kind, v) in (o["counts"] as? JsonObject ?: bad()).members) {
            val k = v as? JsonObject ?: bad()
            l.kinds[kind] =
                KindCounts().apply {
                    seen = long(k, "seen").toInt()
                    applied = long(k, "applied").toInt()
                    skipped.putAll(countsOf(k["skipped"]))
                }
        }
        l.refused.putAll(countsOf(o["refused"]))
        l.info.putAll(countsOf(o["info"]))
        l.errors.putAll(countsOf(o["errors"]))
        l.errorTimes += longs(o["errorTimes"])
        l.resyncTimes += longs(o["resyncTimes"])
        l.nextId = long(o, "nextId").toInt()
        for (e in (o["entries"] as? JsonArray ?: bad()).items) {
            val x = e as? JsonObject ?: bad()
            l.entries +=
                TouchEntry(
                    id = str(x, "id"),
                    first = long(x, "first"),
                    last = long(x, "last"),
                    count = long(x, "count").toInt(),
                    kind = str(x, "kind"),
                    pageBuild = str(x, "pageBuild"),
                    paths = (x["paths"] as? JsonArray ?: bad()).items.map { str(it) },
                )
        }
        return l
    }

    private fun obj(vararg members: Pair<String, JsonValue>) = JsonObject(linkedMapOf(*members))

    private fun num(n: Number) = JsonNumber(n.toDouble())

    private fun counts(map: Map<String, Int>) =
        JsonObject(map.mapValuesTo(LinkedHashMap()) { (_, n) -> num(n) })

    private fun countsOf(v: JsonValue?): Map<String, Int> =
        (v as? JsonObject ?: bad()).members.mapValues { (_, n) -> longOf(n).toInt() }

    private fun longs(v: JsonValue?): List<Long> = (v as? JsonArray ?: bad()).items.map(::longOf)

    private fun long(o: JsonObject, key: String): Long = longOf(o[key])

    private fun longOf(v: JsonValue?): Long {
        val n = (v as? JsonNumber ?: bad()).value
        if (!n.isFinite() || n != Math.floor(n)) bad()
        return n.toLong()
    }

    private fun str(o: JsonObject, key: String): String = str(o[key])

    private fun str(v: JsonValue?): String = (v as? JsonString ?: bad()).value

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun unhex(text: String): ByteArray {
        if (text.length % 2 != 0 || !text.all { it in '0'..'9' || it in 'a'..'f' }) bad()
        return ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private class Bad : Exception() {
        override fun fillInStackTrace(): Throwable = this
    }

    private fun bad(): Nothing = throw Bad()
}
