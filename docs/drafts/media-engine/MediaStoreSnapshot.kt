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

        fun fromJson(root: JSONObject): MediaStoreSnapshot {
            val schema = root.optInt("schemaVersion", 1)
            if (schema > CURRENT_SCHEMA_VERSION) {
                throw MediaValidationException(
                    MediaError.UnsupportedSchemaVersion(schema, CURRENT_SCHEMA_VERSION)
                )
            }
            val epoch = root.optString("saveEpoch", "media-epoch-initial").ifBlank { "media-epoch-initial" }
            val revision = root.optLong("authorityRevision", 0L).coerceAtLeast(0L)
            val mode =
                runCatching { AuthorityMode.valueOf(root.optString("authorityMode", "WEB_OWNED")) }
                    .getOrDefault(AuthorityMode.WEB_OWNED)
            val seeded = root.optBoolean("mediaSeededV1", false)

            val hwmMap = mutableMapOf<String, Long>()
            root.optJSONObject("clientHighWaterMarks")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    hwmMap[k] = obj.optLong(k, 0L)
                }
            }

            val receiptsList = mutableListOf<CommitReceipt>()
            root.optJSONArray("receipts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.optJSONObject(i) ?: continue
                    val opId = r.optString("operationId")
                    if (opId.isBlank()) continue
                    receiptsList +=
                        CommitReceipt(
                            operationId = opId,
                            clientId = r.optString("clientId", "default"),
                            clientSequence = r.optLong("clientSequence", 1L),
                            commandName = r.optString("commandName"),
                            payloadHash = r.optString("payloadHash"),
                            resultingRevision = r.optLong("resultingRevision", 0L),
                            committedAtEpochMs = r.optLong("committedAtEpochMs", 0L),
                            affectedItemId =
                                if (r.isNull("affectedItemId")) null
                                else r.optString("affectedItemId").takeIf { it.isNotEmpty() },
                            message = r.optString("message", ""),
                        )
                }
            }

            val itemsList = LegacySaveMediaAdapter.parseItemsArray(root.optJSONArray("items"))
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
