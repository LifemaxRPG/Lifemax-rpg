package com.lifemax.rpg.core.directorstore

/**
 * The storage keys and fixed values of the page's Life Director store (`LifeDirectorStore`,
 * `engine.js:2244-2246`; LIFE_DIRECTOR_STORE_SPEC.md LST-1).
 */
object StoreKeys {
    /** The main key: the whole Life Director state as one JSON text. */
    const val MAIN = "lifemax_director_state_v1"

    /** The recovery copy: the main key's previous text, kept by every save that changes it. */
    const val BACKUP = "$MAIN.bak"

    /** Written and removed once when a store is made, to learn whether the storage works. */
    const val PROBE = "$MAIN.probe"

    /** What the probe writes. */
    const val PROBE_VALUE = "1"

    /** The schema version the page writes and the only one it accepts after migration. */
    const val SCHEMA_VERSION = 1.0

    /** The `format` member of a backup text. */
    const val BACKUP_FORMAT = "lifemax-director-backup"

    private const val QUARANTINE_PREFIX = "lifemax_director_quarantine_"

    /**
     * Where an unreadable text found under [key] is copied: the prefix and the key with every
     * character outside `[A-Za-z0-9_]` turned into `_` (`key.replace(/\W/g, '_')`, per UTF-16
     * unit).
     */
    fun quarantineKey(key: String): String =
        QUARANTINE_PREFIX +
            key.map { c ->
                    if (c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c == '_') c else '_'
                }
                .joinToString("")
}

/**
 * The page's words for a refused save, which logic.js shows after "Not saved: " (LST-16, LST-19).
 */
object StoreWords {
    const val UNAVAILABLE =
        "Browser storage is unavailable here; changes last only until this page closes."
    const val INVALID_PREFIX = "Refusing to save invalid state: "
    const val STORAGE_FULL = "Storage is full. Export a backup and clear old Life Director history."
    const val NOT_VERIFIED = "Write verification failed"
    const val BACKUP_INVALID_PREFIX = "Backup is not valid: "
}
