package com.lifemax.rpg.core.designsystem.feedback

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/*
 * The page-level celebration layer every native screen shares: where the orbs start and where they
 * fly, and, for a screen whose projection holds the page's celebrations (the Overview cards'
 * `celebrations`, handed over by the page's fxSink), which one plays and when. The particles
 * themselves are XpCelebration's. The page plays the sound and the buzz (xpCue) 120 ms after it
 * hands an award over, so the screen draws as soon as the award arrives and waits for nothing.
 */

/** A point of the page in CSS pixels from its top left, which are dp on the phone. */
data class PagePoint(val x: Float, val y: Float)

/**
 * An award the page handed to the screen and holds in its projection for 2 s (the Overview's
 * `celebrations[]`): its [id], unique within a page session, the total [xp], where the finger was
 * ([origin], the page's lastTap, null when it has none) and the [gains] that colour it.
 */
data class HeldCelebration(
    val id: Long,
    val xp: Int,
    val origin: PagePoint?,
    val gains: List<XpGain>,
) {
    val award: XpAward
        get() = XpAward(xp, gains)
}

/**
 * Which held celebration plays next. The page keeps each one in the projection for 2 s and sends it
 * with every update in that time, so the screen remembers the ids it has started and plays each
 * once. Nothing starts while the screen is hidden (A24); one cut short by hiding is not replayed.
 * An id the page has stopped sending is forgotten, so the memory stays as small as the list.
 */
class CelebrationQueue {
    private val started = HashSet<Long>()

    /** The first of [held] not started yet, or null while hidden or when all have started. */
    fun next(held: List<HeldCelebration>, visible: Boolean): HeldCelebration? {
        started.retainAll(held.mapTo(HashSet()) { it.id })
        if (!visible) return null
        return held.firstOrNull { it.id !in started && it.gains.isNotEmpty() }
    }

    /** [id] has started: it never plays again. */
    fun start(id: Long) {
        started += id
    }
}

/**
 * One celebration of [awards], named by [id], drawn over the whole box from [origin] (px from the
 * box's top left; the middle when unspecified) to [target] (dp from the top left: the projection's
 * `celebrationTarget`; NativeTabKit's own fallback, the top centre, when the page sent none), with
 * fewer particles under [reduced] motion. [onFinished] gets the id once it is over. [startAt] is
 * how many seconds have already played (0 on the phone; screenshot tests freeze a moment with it).
 */
@Composable
fun PageCelebrationLayer(
    id: Long,
    awards: List<XpAward>,
    origin: Offset,
    target: PagePoint?,
    reduced: Boolean,
    onFinished: (Long) -> Unit,
    startAt: Float = 0f,
) {
    val state = rememberXpCelebration(id, awards, reduced, startAt)
    LaunchedEffect(id) {
        state?.play()
        onFinished(id)
    }
    if (state == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val to = target?.let { DpOffset(it.x.dp, it.y.dp) } ?: DpOffset(maxWidth / 2, 44.dp)
        XpCelebration(state, origin, to)
    }
}

/**
 * The celebrations a projection holds, drawn over the whole box (which should be the screen, since
 * the page's points are the screen's): each plays once, from where the page says the finger was to
 * the logo at [target], one after the other, and only while [visible]. Draw it over the content; it
 * takes no touches. [startAt] freezes the first one at a moment, for screenshot tests.
 */
@Composable
fun PageCelebrations(
    held: List<HeldCelebration>,
    target: PagePoint?,
    reduced: Boolean,
    visible: Boolean,
    startAt: Float = 0f,
) {
    val queue = remember { CelebrationQueue() }
    var playing by remember { mutableStateOf<HeldCelebration?>(null) }
    // decided in composition, so the first frame that holds an award already draws it; each write
    // happens once per award, so it settles in the same frame
    if (!visible) playing = null
    else if (playing == null) playing = queue.next(held, true)?.also { queue.start(it.id) }
    val c = playing ?: return
    val density = LocalDensity.current.density
    val origin = c.origin?.let { Offset(it.x * density, it.y * density) } ?: Offset.Unspecified
    PageCelebrationLayer(
        c.id,
        listOf(c.award),
        origin,
        target,
        reduced,
        onFinished = { done -> if (playing?.id == done) playing = null },
        startAt = startAt,
    )
}
