package com.lifemax.rpg.feature.lifedirector.presentation.dial

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.lifemax.rpg.feature.lifedirector.domain.DialBuild
import com.lifemax.rpg.feature.lifedirector.presentation.components.LocalDirectorBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.components.animatorScale

/**
 * A build being played: [build], and how far into it the clock is ([ms], read while drawing, so a
 * frame redraws the dial without recomposing the screen). [skip] ends it at once.
 */
@Stable
class BuildRun(
    val build: DialBuild,
    private val clock: State<Long>,
    private val onSkip: () -> Unit,
) {
    val ms: Float
        get() = clock.value.toFloat()

    fun skip() = onSkip()
}

/**
 * Plays [build] once (runBuild's play rules): when it is new to [ledger] (once per page [session]
 * and id for the plugin's lifetime) and the screen is [ready] (showing, and no handle held or asked
 * about). A new id stops the one playing and plays from the start; the same id carries on. Hidden,
 * it stops and does not come back. Android's "Remove animations" draws the final state, as reduced
 * motion on the page does (the page then sends no build); another animator scale stretches the
 * timeline. Each session's touchdown buzzes 7 ms and the lock 10-40-18 (a full build) or 12 ms,
 * once the user has [touched] the screen. Null while nothing plays.
 */
@Composable
fun rememberBuildRun(
    build: DialBuild?,
    session: String,
    ready: Boolean,
    ledger: (String, Long) -> Boolean,
    touched: () -> Boolean,
): BuildRun? {
    val context = LocalContext.current
    val buzz = LocalDirectorBuzz.current
    val take by rememberUpdatedState(ledger)
    val user by rememberUpdatedState(touched)
    var run by remember { mutableStateOf<BuildRun?>(null) }
    val current by rememberUpdatedState(build)
    LaunchedEffect(build?.id, session, ready) {
        run = null
        val b = current ?: return@LaunchedEffect
        if (!ready || !take(session, b.id)) return@LaunchedEffect
        val scale = animatorScale(context)
        if (scale <= 0f) return@LaunchedEffect
        val clock = mutableLongStateOf(0L)
        var skipped = false
        run =
            BuildRun(b, clock) {
                skipped = true
                run = null
            }
        val start = withFrameMillis { it }
        var last = -1f
        while (!skipped) {
            val t = (withFrameMillis { it } - start) / scale
            if (buzz != null && user()) BuildBuzz.between(b, last, t)?.let { buzz.buzz(*it) }
            clock.longValue = t.toLong()
            last = t
            if (BuildTimeline.over(b, t)) break
        }
        run = null
    }
    return run
}

/** The build's buzzes (bfBuzz in logic.js). */
internal object BuildBuzz {
    private val LOCK_FULL = longArrayOf(10, 40, 18)
    private val LOCK_REPAIR = longArrayOf(12)
    private val LANDING = longArrayOf(7)

    /** What buzzes after [from] and up to [to] ms: the lock, or a touchdown. */
    fun between(b: DialBuild, from: Float, to: Float): LongArray? =
        when {
            b.lock.at > from && b.lock.at <= to -> if (b.full) LOCK_FULL else LOCK_REPAIR
            b.items.any { it.land > from && it.land <= to } -> LANDING
            else -> null
        }
}
