package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.directorstore.StoreKeys
import com.lifemax.rpg.core.lifedirector.PlanSchedule

/**
 * Every number the Life Director check works within (LIFE_DIRECTOR_SHADOW_SPEC.md SH-3, SH-20 to
 * SH-27). The transport limits are the economy check's (ECONOMY_SHADOW_PLAN 2.4), so one page
 * module's rules fit both.
 */
object DirectorShadowLimits {
    /** The message protocol; another `v` is answered `unsupported` (SH-2). */
    const val PROTOCOL = 1

    /** The capability `NativeShadow.handshake()` advertises beside the economy's (SH-1). */
    const val CAPABILITY = "director.shadow.v1"

    /** The `scope` every director message carries, and the plugin routes by (SH-1). */
    const val SCOPE = "director"

    /** The folder of the check's own ring in the no-backup folder (SH-30). */
    const val RING_FOLDER = "director-shadow"

    /**
     * What native's Life Director was built from (SH-4): the engine's version and the store's
     * schema. The page sends the same two from `LifeDirectorEngine.ENGINE_VERSION` and
     * `LifeDirectorStore.CURRENT`; another pair means the page was updated without the app.
     */
    val FINGERPRINT: String = PlanSchedule.ENGINE_VERSION + "/" + StoreKeys.SCHEMA_VERSION.toInt()

    /** Longest envelope, in characters (SH-21); longer is not sent, and the page sends a base. */
    const val MAX_ENVELOPE_CHARS = 512 * 1024

    /** Longest base part, and most parts (SH-21): a base of the state's text up to 8 MB. */
    const val MAX_BASE_PART_CHARS = 256 * 1024
    const val MAX_BASE_PARTS = 32

    /** Longest stitched state text native holds (SH-21); past it the check stops (`tooLarge`). */
    const val MAX_STATE_CHARS = 4 * 1024 * 1024

    /** Most changes one delta may carry (SH-6); more is refused and a base follows. */
    const val MAX_OPS = 20_000

    /** Deepest path a change may name (SH-6). */
    const val MAX_PATH = 4

    /**
     * A plan run longer than this makes native compare only one plan in [ENGINE_SAMPLE] until a
     * run is quick again (SH-24, the CPU budget for re-running placement).
     */
    const val ENGINE_SLOW_MS = 1_500L
    const val ENGINE_SAMPLE = 4

    /** Most paths a difference names (SH-31). */
    const val MAX_PATHS = 12
}
