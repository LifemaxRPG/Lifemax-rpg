package com.lifemax.rpg.feature.lifedirector.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lifemax.rpg.core.designsystem.feedback.XpAward
import com.lifemax.rpg.core.designsystem.feedback.XpCelebration
import com.lifemax.rpg.core.designsystem.feedback.rememberXpCelebration
import com.lifemax.rpg.feature.lifedirector.domain.DirectorPoint
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorProjection
import com.lifemax.rpg.feature.lifedirector.domain.SheetKinds
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** An XP celebration to draw: [id] counts per plugin lifetime, so every award is a new burst. */
data class DirectorCelebration(val id: Long, val awards: List<XpAward>)

/**
 * Phase 4A's feedback around Today's writes, kept out of the view model: the XP celebration a reply
 * carries (the page plays only its sound and buzz), and the sheet the Overview's glance asked for
 * (the projection's intent), taken once by its id. [busy]: the screen is holding something (a
 * handle, a guide, a sheet), so an intent waits for nothing and is dropped; [open] opens a sheet of
 * Today's on the screen. [drawn] is the sheet kinds this screen draws; any other the glance asks
 * for is handed to the page as the tap its own element would send.
 */
class DirectorFeedback
internal constructor(
    private val projection: StateFlow<LifeDirectorProjection?>,
    private val busy: () -> Boolean,
    private val drawn: Set<String> = DRAWN,
    private val open: (kind: String, id: String) -> Unit,
) {
    private val mutable = MutableStateFlow<DirectorCelebration?>(null)
    private var serial = 0L
    private val taken = HashSet<Pair<String, Long>>()

    /** The celebration on show, until [celebrated]. */
    val celebration: StateFlow<DirectorCelebration?> = mutable.asStateFlow()

    /** A reply arrived: the XP it paid is celebrated, from the last tap, over the screen. */
    fun onReply(reply: DirectorReply) {
        val awards = reply.celebrations.filter { it.gains.isNotEmpty() }
        if (awards.isNotEmpty()) mutable.value = DirectorCelebration(++serial, awards)
    }

    /** Celebration [id] has played to its end. */
    fun celebrated(id: Long) = mutable.update { if (it?.id == id) null else it }

    /** Behind the page nothing plays (A24): a celebration cut short is not replayed. */
    fun hidden() {
        mutable.value = null
    }

    /**
     * The glance's intent, once: a sheet this screen draws is opened here (null comes back); any
     * other comes back as the tap the page answers. Null too when there is none, it was taken
     * already, or the screen is busy.
     */
    fun takeIntent(): Tap? {
        val p = projection.value ?: return null
        val i = p.intent ?: return null
        if (busy() || !taken.add(p.session to i.id)) return null
        if (i.sheet in drawn) {
            open(i.sheet, i.target)
            return null
        }
        return when (i.sheet) {
            SheetKinds.SESSION -> Tap("inst", i.target)
            SheetKinds.TIMER -> Tap("focusOpen")
            MEAL -> Tap("meal", i.target)
            else -> null
        }
    }

    companion object {
        /** The sheets this screen draws; a kind joins once its sheet is drawn natively. */
        val DRAWN = setOf(SheetKinds.SESSION, SheetKinds.TIMER, MEAL)
        private const val MEAL = "meal"
    }
}

/** Where the last finger came down on the screen, in its pixels: the celebration starts there. */
internal class TapSpot {
    var at: Offset = Offset.Unspecified
}

/**
 * [content] (the screen) with its feedback over it: the XP celebration of the latest reply, from
 * the tapped control (the middle when nothing was tapped) to the top bar's logo, fewer particles
 * under reduced motion as the page throws; and the glance's intent taken once the screen shows.
 */
@Composable
internal fun WithDirectorFeedback(
    feedback: DirectorFeedback,
    p: LifeDirectorProjection,
    visible: Boolean,
    handOver: () -> Boolean,
    emit: (action: String, id: String) -> Unit,
    content: @Composable () -> Unit,
) {
    LaunchedEffect(visible, p.session, p.intent?.id) {
        if (visible) feedback.takeIntent()?.let { t -> if (handOver()) emit(t.act, t.id) }
    }
    val spot = remember { TapSpot() }
    val celebration by feedback.celebration.collectAsState()
    Box(Modifier.fillMaxSize().pointerInput(spot) { recordTaps(spot) }) {
        content()
        val c = celebration
        if (visible && c != null) {
            val origin = remember(c.id) { spot.at }
            DirectorCelebrationLayer(
                c,
                origin,
                p.celebrationTarget,
                p.reducedMotion,
                feedback::celebrated,
            )
        }
    }
}

/**
 * [celebration] drawn over the whole box, from [origin] (px; the middle when unspecified) to
 * [target] (dp from the top left; the top centre when the page sent none), with fewer particles
 * under [reduced] motion. [onFinished] is called when it is over. [startAt] is how many seconds
 * have already played (0 on the phone; screenshot tests freeze a moment with it).
 */
@Composable
internal fun DirectorCelebrationLayer(
    celebration: DirectorCelebration,
    origin: Offset,
    target: DirectorPoint?,
    reduced: Boolean,
    onFinished: (Long) -> Unit,
    startAt: Float = 0f,
) {
    val state = rememberXpCelebration(celebration.id, celebration.awards, reduced, startAt)
    LaunchedEffect(celebration.id) {
        state?.play()
        onFinished(celebration.id)
    }
    if (state == null) return
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // NativeTabKit's own fallback when the page has no logo to aim at: the top centre
        val to = target?.let { DpOffset(it.x.dp, it.y.dp) } ?: DpOffset(maxWidth / 2, 44.dp)
        XpCelebration(state, origin, to)
    }
}

/** Notes every finger that comes down, taking nothing from it. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.recordTaps(spot: TapSpot) =
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes
                .firstOrNull { it.changedToDownIgnoreConsumed() }
                ?.let { spot.at = it.position }
        }
    }
