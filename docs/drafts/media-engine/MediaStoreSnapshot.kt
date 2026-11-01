package com.lifemax.rpg.feature.library.media.data

import com.lifemax.rpg.feature.library.media.domain.AudioAttachment
import com.lifemax.rpg.feature.library.media.domain.AuthorityMode
import com.lifemax.rpg.feature.library.media.domain.CommitReceipt
import com.lifemax.rpg.feature.library.media.domain.MediaDomainRules
import com.lifemax.rpg.feature.library.media.domain.MediaError
import com.lifemax.rpg.feature.library.media.domain.MediaItem
import com.lifemax.rpg.feature.library.media.domain.MediaLibraryState
import com.lifemax.rpg.feature.library.media.domain.MediaStatus
import com.lifemax.rpg.feature.library.media.domain.MediaType
import com.lifemax.rpg.feature.library.media.domain.MediaValidationException
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializable snapshot of the native Media store, including authority metadata,
 * client high-water sequence numbers, and bounded commit receipts.
 */
data class MediaStoreSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val saveEpoch: String,
    val authorityRevision: Long,
    val authorityMode: AuthorityMode,
    val mediaSeededV1: Boolean,
    val items: List<MediaItem>,
    val clientHighWaterMarks: Map<String, Long> = emptyMap(),
    val receipts: List<CommitReceipt> = emptyList(),
) {
    fun toLibraryState(): MediaLibraryState =
        MediaLibraryState(
            saveEpoch = saveEpoch,
            authorityRevision = authorityRevision,
            authorityMode = authorityMode,
            mediaSeededV1 = mediaSeededV1,
            items = items,
            stats = MediaDomainRules.computeStats(items),
        )

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", schemaVersion)
        root.put("saveEpoch", saveEpoch)
        root.put("authorityRevision", authorityRevision)
        root.put("authorityMode", authorityMode.name)
        root.put("mediaSeededV1", mediaSeededV1)

        val hwmObj = JSONObject()
        for ((k, v) in clientHighWaterMarks) hwmObj.put(k, v)
        root.put("clientHighWaterMarks", hwmObj)

        val receiptsArr = JSONArray()
        for (r in receipts) {
            receiptsArr.put(
                JSONObject()
                    .put("operationId", r.operationId)
                    .put("clientId", r.clientId)
                    .put("clientSequence", r.clientSequence)
                    .put("commandName", r.commandName)
                    .put("payloadHash", r.payloadHash)
                    .put("resultingRevision", r.resultingRevision)
                    .put("committedAtEpochMs", r.committedAtEpochMs)
                    .put("affectedItemId", r.affectedItemId ?: JSONObject.NULL)
                    .put("message", r.message)
            )
        }
        root.put("receipts", receiptsArr)

        val itemsArr = JSONArray()
        for (item in items) {
            itemsArr.put(LegacySaveMediaAdapter.itemToJson(item, includeNativeAudioFields = true))
        }
        root.put("items", itemsArr)
        return root
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_RECEIPTS_PER_CLIENT = 256

        fun empty(
            saveEpoch: String = "media-epoch-initial",
            authorityMode: AuthorityMode = AuthorityMode.WEB_OWNED,
        ): MediaStoreSnapshot =
            MediaStoreSnapshot(
                schemaVersion = CURRENT_SCHEMA_VERSION,
                saveEpoch = saveEpoch,
                authorityRevision = 0L,
                authorityMode = authorityMode,
                mediaSeededV1 = false,
                items = emptyList(),
            )

        /**
         * Reads the store strictly: a missing or wrong-kind field is an error, never a default. This is the authoritative copy
         * once native owns Media, so a damaged field must not quietly turn into "web owns it" (an older copy winning) or an
         * empty list. The caller treats the failure as an unreadable file (AtomicJsonFileMediaStore falls back to its backup).
         */
        fun fromJson(root: JSONObject): MediaStoreSnapshot {
            val schema = root.requireInt("schemaVersion")
            if (schema > CURRENT_SCHEMA_VERSION) {
                throw MediaValidationException(
                    MediaError.UnsupportedSchemaVersion(schema, CURRENT_SCHEMA_VERSION)
                )
            }
            require(schema >= 1) { "schemaVersion $schema is not a version" }
            val epoch = root.requireString("saveEpoch")
            require(epoch.isNotBlank()) { "saveEpoch is blank" }
            val revision = root.requireLong("authorityRevision")
            require(revision >= 0L) { "authorityRevision is negative" }
            val modeName = root.requireString("authorityMode")
            val mode =
                AuthorityMode.values().firstOrNull { it.name == modeName }
                    ?: throw IllegalArgumentException("Unknown authorityMode $modeName")
            val seeded = root.requireBoolean("mediaSeededV1")

            val hwmMap = mutableMapOf<String, Long>()
            val hwm = root.requireObject("clientHighWaterMarks")
            for (k in hwm.keys()) hwmMap[k] = hwm.requireLong(k).also { require(it >= 0L) { "high-water mark of $k is negative" } }

            val receiptsList = mutableListOf<CommitReceipt>()
            val receipts = root.requireArray("receipts")
            for (i in 0 until receipts.length()) {
                val r = receipts.get(i) as? JSONObject ?: throw IllegalArgumentException("receipt $i is not an object")
                val opId = r.requireString("operationId")
                require(opId.isNotBlank()) { "receipt $i has no operationId" }
                receiptsList +=
                    CommitReceipt(
                        operationId = opId,
                        clientId = r.requireString("clientId"),
                        clientSequence = r.requireLong("clientSequence"),
                        commandName = r.requireString("commandName"),
                        payloadHash = r.requireString("payloadHash"),
                        resultingRevision = r.requireLong("resultingRevision"),
                        committedAtEpochMs = r.requireLong("committedAtEpochMs"),
                        affectedItemId = if (r.isNull("affectedItemId")) null else r.requireString("affectedItemId"),
                        message = r.requireString("message"),
                    )
            }

            // NEEDS (not supplied): parseItemsArray must be strict in the same way for the native store; the lenient legacy
            // import (duplicate ids repaired, unknown fields kept) belongs to importFromLegacySaveJson only.
            val itemsList = LegacySaveMediaAdapter.parseItemsArray(root.requireArray("items"))
            return MediaStoreSnapshot(
                schemaVersion = schema,
                saveEpoch = epoch,
                authorityRevision = revision,
                authorityMode = mode,
                mediaSeededV1 = seeded,
                items = itemsList,
                clientHighWaterMarks = hwmMap,
                receipts = receiptsList,
            )
        }
    }
}

/* Strict readers: present and of the right kind, or an IllegalArgumentException naming the field. */
private fun JSONObject.need(name: String): Any {
    require(has(name) && !isNull(name)) { "$name is missing" }
    return get(name)
}

internal fun JSONObject.requireString(name: String): String =
    need(name) as? String ?: throw IllegalArgumentException("$name is not text")

internal fun JSONObject.requireBoolean(name: String): Boolean =
    need(name) as? Boolean ?: throw IllegalArgumentException("$name is not true or false")

internal fun JSONObject.requireLong(name: String): Long {
    val v = need(name) as? Number ?: throw IllegalArgumentException("$name is not a number")
    val d = v.toDouble()
    require(d == Math.floor(d) && !d.isInfinite()) { "$name is not a whole number" }
    return v.toLong()
}

internal fun JSONObject.requireInt(name: String): Int =
    requireLong(name).also { require(it in Int.MIN_VALUE..Int.MAX_VALUE) { "$name is out of range" } }.toInt()

internal fun JSONObject.requireObject(name: String): JSONObject =
    need(name) as? JSONObject ?: throw IllegalArgumentException("$name is not an object")

internal fun JSONObject.requireArray(name: String): JSONArray =
    need(name) as? JSONArray ?: throw IllegalArgumentException("$name is not a list")
