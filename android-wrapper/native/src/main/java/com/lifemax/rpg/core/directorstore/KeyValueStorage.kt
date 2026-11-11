package com.lifemax.rpg.core.directorstore

/**
 * A failure a storage reports, as the page sees a thrown `DOMException` or `Error`: its [name] and
 * [message]. Never carries a stored value.
 */
data class StorageError(val name: String, val message: String) {
    /** `String(e)` (`Error.prototype.toString`): `name: message`, or the one that is not empty. */
    override fun toString(): String =
        when {
            name.isEmpty() -> message
            message.isEmpty() -> name
            else -> "$name: $message"
        }

    /** The page's test for a full storage: `/quota/i` over `e.name + e.message` (LST-19). */
    val isQuota: Boolean
        get() = (name + message).contains("quota", ignoreCase = true)

    companion object {
        /** Chromium's error for a write that does not fit (LST-24). */
        fun quotaExceeded(key: String): StorageError =
            StorageError(
                "QuotaExceededError",
                "Failed to execute 'setItem' on 'Storage': Setting the value of '$key' exceeded the quota.",
            )
    }
}

/** What reading a key gave. */
sealed interface StorageRead {
    /** The key holds [text]. */
    data class Found(val text: String) : StorageRead

    /** The key holds nothing (`getItem` gave `null`). */
    data object Missing : StorageRead

    /** The read failed (`getItem` threw). */
    data class Failed(val error: StorageError) : StorageRead
}

/**
 * The key-value storage the Life Director store keeps its texts in: the page's `localStorage`
 * (`getItem`, `setItem`, `removeItem`) as an interface, so the store's rules run unchanged over the
 * web view's storage, a native file or key store, or a test fake that fails on purpose.
 *
 * A call never throws: [write] and [remove] give null when they worked and the error otherwise.
 * Texts are UTF-16 strings and may hold lone surrogates; a storage must give back exactly what it
 * was given (a store whose read-back differs is caught by the store's check, LST-20).
 */
interface KeyValueStorage {
    fun read(key: String): StorageRead

    fun write(key: String, text: String): StorageError?

    fun remove(key: String): StorageError?
}

/**
 * A storage in memory with no failures and an optional size cap counted as Chromium counts it (the
 * sum of key and value lengths in UTF-16 units, a replaced value no longer counting; LST-24).
 */
class MemoryStorage(private val quotaUnits: Long = Long.MAX_VALUE) : KeyValueStorage {
    private val items = LinkedHashMap<String, String>()

    /** The keys and texts held now, in the order the keys were first written. */
    val contents: Map<String, String>
        get() = LinkedHashMap(items)

    override fun read(key: String): StorageRead =
        items[key]?.let { StorageRead.Found(it) } ?: StorageRead.Missing

    override fun write(key: String, text: String): StorageError? {
        val replaced = items[key]?.let { key.length + it.length } ?: 0
        val used = items.entries.sumOf { (k, v) -> (k.length + v.length).toLong() }
        if (used - replaced + key.length + text.length > quotaUnits)
            return StorageError.quotaExceeded(key)
        items[key] = text
        return null
    }

    override fun remove(key: String): StorageError? {
        items.remove(key)
        return null
    }

    companion object {
        /** Chromium's localStorage cap per origin, in UTF-16 units of keys and values. */
        const val CHROMIUM_QUOTA_UNITS = 5_242_880L
    }
}
