package com.lifemax.rpg.core.lifedirector

import java.time.ZoneId

/**
 * Which texts the page accepts as a time zone, and the zone each one names (LDT-2, LDT-3). The page
 * asks `Intl.DateTimeFormat` (ECMA-402), which accepts:
 * - an IANA name or link, or one of the few legacy names its ICU data still carries, compared
 *   without regard to ASCII letter case (`america/new_york` is New York; `İstanbul` is nothing);
 * - a UTC offset `±HH`, `±HHMM` or `±HH:MM` (hours 00 to 23, minutes 00 to 59), with `+`, `-` or
 *   U+2212 as the sign. Nothing else: not `Z`, not `GMT+5`, not `UTC+05:30`, not seconds.
 *
 * `ZoneId.of` differs on every point (it is case sensitive, takes `Z`, `GMT+5` and seconds, lacks
 * the legacy three-letter names, and refuses offsets past 18:00), so it is never called on the
 * page's text: names are looked up in a table built from the JVM's tz database, `ZoneId.SHORT_IDS`
 * (the same legacy names, with the same rules, as the page's ICU data) and [ICU_ONLY].
 */
object ZoneNames {
    /**
     * Names the page's ICU data accepts that are not in the JVM's zone id list, with the zone they
     * follow in 2025-2028 (checked by the alias golden cases): ICU keeps three links the tz
     * database has dropped, knows `Factory`, and `GMT+0` / `GMT-0` are tz database links that
     * `java.time` reads as offset prefixes instead of listing them.
     */
    val ICU_ONLY: Map<String, String> =
        mapOf(
            "ROC" to "Asia/Taipei",
            "US/Pacific-New" to "America/Los_Angeles",
            "Canada/East-Saskatchewan" to "America/Regina",
            "Factory" to "UTC",
            "GMT+0" to "Etc/GMT",
            "GMT-0" to "Etc/GMT",
        )

    private const val MAX_OFFSET_HOURS = 23
    private const val MAX_OFFSET_MINUTES = 59
    private const val MINUTES_PER_HOUR = 60
    private const val SECONDS_PER_MINUTE = 60
    private val OFFSET = Regex("""([+\-−])(\d\d)(?::?(\d\d))?""")

    private val byFoldedName: Map<String, PageZone> by lazy {
        val table = HashMap<String, PageZone>()
        for (id in ZoneId.getAvailableZoneIds()) table[fold(id)] = PageZone.of(ZoneId.of(id))
        for ((name, id) in ZoneId.SHORT_IDS) table.putIfAbsent(
            fold(name),
            PageZone.of(ZoneId.of(id)),
        )
        for ((name, id) in ICU_ONLY) table.putIfAbsent(fold(name), PageZone.of(ZoneId.of(id)))
        table
    }

    /** LDT-2 `isValidTimeZone`: whether the page takes [name] as a time zone. */
    fun isValid(name: String): Boolean = resolve(name) != null

    /**
     * LDT-3: the zone [name] names for the page, or null where the page's `Intl` throws a
     * `RangeError` (so does every engine function given that zone).
     */
    fun resolve(name: String): PageZone? = offset(name) ?: byFoldedName[fold(name)]

    private fun offset(name: String): PageZone? {
        val match = OFFSET.matchEntire(name) ?: return null
        val hours = match.groupValues[2].toInt()
        val minutes = match.groupValues[3].ifEmpty { "0" }.toInt()
        if (hours > MAX_OFFSET_HOURS || minutes > MAX_OFFSET_MINUTES) return null
        val sign = if (match.groupValues[1].single() == '+') 1 else -1
        return PageZone.fixed(sign * (hours * MINUTES_PER_HOUR + minutes) * SECONDS_PER_MINUTE)
    }

    /** ASCII letters to lower case; every other character (`İ`, `Ａ`) stays as it is. */
    private fun fold(name: String): String =
        String(
            CharArray(name.length) {
                name[it].let { c -> if (c in 'A'..'Z') c + ('a' - 'A') else c }
            }
        )
}
