package com.lifemax.rpg.core.shadow

/**
 * Every number the economy shadow works within (ECONOMY_SHADOW_PLAN 2.4, 3.1 and the budgets of
 * section 4). Each limit names what happens past it where it is enforced; nothing here is tuned by
 * the device.
 */
object ShadowLimits {
    /** The envelope protocol; a message with another `v` is answered `unsupported`. */
    const val PROTOCOL = 1

    /** The capability `NativeShadow.handshake()` advertises for this protocol. */
    const val CAPABILITY = "economy.shadow.v1"

    /**
     * The capability of WP6c: the quest and Daily Hand kinds are compared, so the page may send
     * today's plan facts with every kind and the new quest points; an older app lacks it and the
     * page then sends the WP6a shapes only.
     */
    const val CAPABILITY_QUESTS = "economy.shadow.quests.v1"

    /**
     * The capability of WP6d: the Health sync and the day roll's decay are compared, so the page
     * records every Health Connect answer with its outcome (`env.health`: the plugin missing, a
     * sync already running, a read that threw and its message) and sends the boot's decay with its
     * toasts and the plan's rest days; an older app lacks it and gets the WP6c shapes.
     */
    const val CAPABILITY_DAY = "economy.shadow.day.v1"

    /**
     * The capability of WP6e: the owner's side of the check. `copyReport()` puts the redacted
     * report on the clipboard, `summary()` lists the differences a case can be saved for, and
     * `saveCase()` writes one of them, unredacted, to a file the owner picks (decision O-4); an
     * older app lacks it and the Settings card shows the switch and its line only.
     */
    const val CAPABILITY_REPORT = "economy.shadow.report.v1"

    /** Most difference entries the copied report lists, newest first (3.4). */
    const val REPORT_DIFFERENCES = 20

    /**
     * Longest copied report, in characters; past it the oldest listed differences are left out
     * (3.4: typically about 6 KB).
     */
    const val REPORT_MAX_CHARS = 16 * 1024

    /** Times the check stopped itself that the ring remembers (the report's history). */
    const val DISABLE_HISTORY = 10

    /**
     * Unexplained differences whose whole action is kept, in memory only, for "Save case…" (O-4);
     * the oldest goes first. Never written to the ring; gone when the app process ends.
     */
    const val CASES_KEPT = 5

    /** Most characters all kept cases may hold together; the oldest go first past it. */
    const val CASES_MAX_CHARS = 4 * 1024 * 1024

    /**
     * Longest record list a case copies whole; a longer one keeps only the records where the page's
     * and native's after-states differ (with the list's length).
     */
    const val CASE_WHOLE_ITEMS = 50

    /** Most records of one list a case keeps when it keeps only the differing ones. */
    const val CASE_MAX_ITEMS = 64

    /** More than this between two actions without a boot is an `info.clockJump` (4, clock). */
    const val CLOCK_JUMP_MS = 24L * 60 * 60 * 1000

    /**
     * Largest envelope text accepted (2.4); a larger one is refused as [ShadowRefusal.TOO_LARGE].
     */
    const val MAX_ENVELOPE_CHARS = 256 * 1024

    /** Largest `args` part of an envelope, as JSON text. */
    const val MAX_ARGS_CHARS = 16 * 1024

    /** Largest `env` part of an envelope, as JSON text. */
    const val MAX_ENV_CHARS = 64 * 1024

    /** Most toasts one action may report. */
    const val MAX_TOASTS = 20

    /** Longest toast text. */
    const val MAX_TOAST_CHARS = 300

    /** Most `writeSave()` outcomes one action may report. */
    const val MAX_SAVES = 8

    /** Most changed records in one `rec` delta; the page sends the whole key past it. */
    const val MAX_REC_CHANGES = 256

    /** Longest action kind name. */
    const val MAX_KIND_CHARS = 40

    /** Longest page session id. */
    const val MAX_SID_CHARS = 40

    /** Largest base part message (one slice of the base text, escaped inside its message). */
    const val MAX_BASE_PART_CHARS = 256 * 1024

    /** Most parts one base may come in (32 x 256 KB: the 8 MB transport limit). */
    const val MAX_BASE_PARTS = 32

    /**
     * Largest base text native will hold (section 4, native memory: the tree is about six times the
     * text). A larger save stops the check with the reason `tooLarge`; nothing is held.
     */
    const val MAX_HELD_CHARS = 4 * 1024 * 1024

    /** Largest page build or fingerprint text in `configure`. */
    const val MAX_LABEL_CHARS = 128

    /**
     * Internal errors within [ERROR_WINDOW_MS] that disable the check (section 4, self-disable).
     */
    const val ERRORS_BEFORE_DISABLE = 5

    /** The window [ERRORS_BEFORE_DISABLE] is counted in: 24 hours. */
    const val ERROR_WINDOW_MS = 24L * 60 * 60 * 1000

    /** Resynchronisations within an hour past which the check disables itself (`resyncLoop`). */
    const val RESYNCS_PER_HOUR = 4

    /** One hour, the window of [RESYNCS_PER_HOUR]. */
    const val HOUR_MS = 60L * 60 * 1000

    /** Envelopes the worker's slowness is judged over (section 4: across 50 actions). */
    const val SLOW_WINDOW = 50

    /** The percentile of [SLOW_WINDOW] that must stay under [SLOW_P95_MS]. */
    const val SLOW_PERCENTILE = 95

    /** Worker time per envelope past which, at the 95th percentile, the check stops (`slow`). */
    const val SLOW_P95_MS = 2_000L

    /** Envelopes waiting for the worker; the newest past this is dropped (`skipped.queue`). */
    const val QUEUE_CAPACITY = 16

    /** Most entries the ring keeps (3.1); the oldest go first. */
    const val RING_MAX_ENTRIES = 200

    /** Largest ring file, in bytes of UTF-8 (3.1); the oldest entries go first to fit. */
    const val RING_MAX_BYTES = 128 * 1024

    /** Shortest time between two ring writes while it changes (3.1). */
    const val RING_WRITE_INTERVAL_MS = 30_000L

    /** Most field paths one ring entry names; the rest are counted as `+N more` (1.3). */
    const val MAX_PATHS_PER_ENTRY = 12

    /** Most action kinds counted apart; later new kinds are counted together as `other`. */
    const val MAX_KINDS = 64

    /** Times in the ring are kept to the minute (3.1). */
    const val MINUTE_MS = 60_000L

    /** Hex digits of a content fingerprint the report shows. */
    const val FINGERPRINT_DIGITS = 12

    /** Hex digits of a salted hash shown for a redacted name (3.2). */
    const val HASH_DIGITS = 6

    /** Bytes of the ring's per-install salt. */
    const val SALT_BYTES = 16
}
