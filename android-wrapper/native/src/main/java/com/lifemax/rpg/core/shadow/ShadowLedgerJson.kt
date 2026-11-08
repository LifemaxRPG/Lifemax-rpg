package com.lifemax.rpg.core.shadow

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
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
 * app:{versionCode, nativeApi}, faults:{native, page}, counts:{kind:{seen, applied, compared, same,
 * allowed, different, skipped:{why:n}}}, refused:{why:n}, info:{word:n}, errors:{class:n},
 * codec:{word:n}, errorTimes, resyncTimes, nextId, entries:[{id, first, last, count, kind,
 * pageBuild, paths}], nextMismatch, mismatches:[{id, first, last, count, kind, pageBuild, paths,
 * allow, unexplained, page?:{xp, sparks, rested}, native?:{...}}], disables:[{at, reason}]}`.
 * Reading is strict about the shape and never throws (a WP6a ring without the comparison's keys, or
 * one before WP6e without `disables`, reads with them empty); a file that does not read is replaced
 * by a fresh ledger (the caller counts `info.ringReset`).
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
                            "compared" to num(k.compared),
                            "same" to num(k.same),
                            "allowed" to num(k.allowed),
                            "different" to num(k.different),
                            "skipped" to counts(k.skipped),
                        )
                    }
                ),
            "refused" to counts(l.refused),
            "info" to counts(l.info),
            "errors" to counts(l.errors),
            "codec" to counts(l.codec),
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
            "nextMismatch" to num(l.nextMismatch),
            "mismatches" to JsonArray(l.mismatches.map(::mismatch)),
            "disables" to
                JsonArray(
                    l.disables.map { obj("at" to num(it.at), "reason" to JsonString(it.reason)) }
                ),
        )

    private fun mismatch(m: MismatchEntry): JsonObject =
        JsonObject(
            LinkedHashMap<String, JsonValue>().apply {
                put("id", JsonString(m.id))
                put("first", num(m.first))
                put("last", num(m.last))
                put("count", num(m.count))
                put("kind", JsonString(m.kind))
                put("pageBuild", JsonString(m.pageBuild))
                put("paths", JsonArray(m.paths.map { JsonString(it) }))
                put("allow", JsonArray(m.allow.map { JsonString(it) }))
                put("unexplained", JsonBool.of(m.unexplained))
                m.page?.let { put("page", change(it)) }
                m.native?.let { put("native", change(it)) }
            }
        )

    private fun change(c: EconomyChange) =
        obj("xp" to num(c.xp), "sparks" to num(c.sparks), "rested" to num(c.rested))

    private fun changeOf(v: JsonValue?): EconomyChange? {
        val o = (v ?: return null) as? JsonObject ?: bad()
        return EconomyChange(double(o["xp"]), double(o["sparks"]), double(o["rested"]))
    }

    private fun double(v: JsonValue?): Double {
        val n = (v as? JsonNumber ?: bad()).value
        if (!n.isFinite()) bad()
        return n
    }

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
                    compared = optional(k, "compared")
                    same = optional(k, "same")
                    allowed = optional(k, "allowed")
                    different = optional(k, "different")
                    skipped.putAll(countsOf(k["skipped"]))
                }
        }
        l.refused.putAll(countsOf(o["refused"]))
        l.info.putAll(countsOf(o["info"]))
        l.errors.putAll(countsOf(o["errors"]))
        o["codec"]?.let { l.codec.putAll(countsOf(it)) }
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
        l.nextMismatch = optional(o, "nextMismatch").coerceAtLeast(1)
        for (e in (o["mismatches"] as? JsonArray)?.items.orEmpty()) {
            val x = e as? JsonObject ?: bad()
            l.mismatches +=
                MismatchEntry(
                    id = str(x, "id"),
                    first = long(x, "first"),
                    last = long(x, "last"),
                    count = long(x, "count").toInt(),
                    kind = str(x, "kind"),
                    pageBuild = str(x, "pageBuild"),
                    paths = (x["paths"] as? JsonArray ?: bad()).items.map { str(it) },
                    allow = (x["allow"] as? JsonArray ?: bad()).items.map { str(it) },
                    unexplained = (x["unexplained"] as? JsonBool ?: bad()).value,
                    page = changeOf(x["page"]),
                    native = changeOf(x["native"]),
                )
        }
        for (e in (o["disables"] as? JsonArray)?.items.orEmpty()) {
            val x = e as? JsonObject ?: bad()
            l.disables += DisableEvent(long(x, "at"), str(x, "reason"))
        }
        return l
    }

    /** A count a WP6a ring does not have: 0 when absent. */
    private fun optional(o: JsonObject, key: String): Int = o[key]?.let { longOf(it).toInt() } ?: 0

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
