package com.lifemax.rpg.app.shell.audio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import kotlinx.coroutines.delay

/** What the dock can ask of the page (50-native-audio.js runs each on the page's own player). */
class AudioDockActions(
    val toggle: () -> Unit = {},
    val skip: (seconds: Int) -> Unit = {},
    val seek: (fraction: Float) -> Unit = {},
    val rate: (Double) -> Unit = {},
    val minimize: () -> Unit = {},
    val close: () -> Unit = {},
)

/**
 * The dock as the host draws it: [state] is the page's last push, [clock] reads the time the
 * extrapolation counts from. Once a second while the seek bar is on screen and moving, and not
 * otherwise, it takes the time again (AudioDockState.ticks).
 */
@Composable
fun AudioDockRoute(state: AudioDockState, actions: AudioDockActions, clock: () -> Long) {
    var now by remember { mutableLongStateOf(clock()) }
    val ticking = state.ticks(onScreen = true)
    LaunchedEffect(state, ticking) {
        now = clock()
        while (ticking) {
            delay(TICK_MS)
            now = clock()
        }
    }
    AudioDock(state, now, actions)
}

private const val TICK_MS = 1000L

/**
 * The page's audio player (.audio-player) as one card above the bottom bar: the title and file, a
 * seek bar with both times, −15/Play/+15/+30 and the speed, minimise and close. Minimised it is the
 * round play button with the title. It is exactly [AudioDockMetrics] high, so the screens under it
 * can clear it before it is drawn.
 */
@Composable
fun AudioDock(state: AudioDockState, nowMs: Long, actions: AudioDockActions) {
    if (!state.visible) return
    val colors = remember(state.tokens, state.light) { PageColors(state.tokens, state.light) }
    // the dock is a fixed height (the screens clear it before it is drawn), so its text stops
    // growing where it would not fit
    val density = LocalDensity.current
    val capped = Density(density.density, minOf(density.fontScale, MAX_FONT_SCALE))
    CompositionLocalProvider(LocalDensity provides capped) {
        PageTheme(colors) {
            val c = LocalPageColors.current
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    Modifier.widthIn(max = if (state.minimized) 420.dp else 560.dp)
                        .fillMaxWidth()
                        .height(
                            (if (state.minimized) AudioDockMetrics.MINIMIZED_DP
                                else AudioDockMetrics.EXPANDED_DP)
                                .dp
                        )
                        .clip(shape)
                        .background(c.panel)
                        .border(1.dp, c.line, shape)
                        .semantics { contentDescription = "Audiobook player" }
                        .padding(
                            horizontal = 14.dp,
                            vertical = if (state.minimized) 8.dp else 8.dp,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.minimized) Minimized(state, actions)
                    else Expanded(state, nowMs, actions)
                }
            }
        }
    }
}

private const val MAX_FONT_SCALE = 1.15f

@Composable
private fun Expanded(state: AudioDockState, nowMs: Long, actions: AudioDockActions) {
    Row(Modifier.height(52.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleBlock(state, Modifier.weight(1f))
        DockButton("—", "Minimize player", Modifier.size(40.dp), onClick = actions.minimize)
        DockButton(
            "✕",
            "Close player",
            Modifier.padding(start = 6.dp).size(40.dp),
            onClick = actions.close,
        )
    }
    SeekRow(state, nowMs, actions.seek)
    Row(Modifier.height(40.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val w = Modifier.weight(1f).fillMaxHeight()
        DockButton("−15s", "Back 15 seconds", w) { actions.skip(-15) }
        DockButton(
            if (state.playing) "Pause" else "Play",
            if (state.playing) "Pause" else "Play",
            w,
            primary = true,
            onClick = actions.toggle,
        )
        DockButton("+15s", "Forward 15 seconds", w) { actions.skip(15) }
        DockButton("+30s", "Forward 30 seconds", w) { actions.skip(30) }
        DockButton(rateLabel(state.rate), "Speed", w) { actions.rate(state.nextRate()) }
    }
}

@Composable
private fun Minimized(state: AudioDockState, actions: AudioDockActions) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp)
                .clip(CircleShape)
                .background(c.accent)
                .clickable(
                    onClickLabel = if (state.playing) "Pause" else "Play",
                    role = Role.Button,
                    onClick = actions.toggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (state.playing) "❚❚" else "▶",
                color = c.onAccent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            state.title,
            Modifier.weight(1f),
            color = c.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DockButton("□", "Expand player", Modifier.size(40.dp), onClick = actions.minimize)
        DockButton("✕", "Close player", Modifier.size(40.dp), onClick = actions.close)
    }
}

@Composable
private fun TitleBlock(state: AudioDockState, modifier: Modifier) {
    val c = LocalPageColors.current
    Column(modifier) {
        Text(
            state.title,
            color = c.text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            state.sub,
            color = c.muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Both times and the bar between them. A drag shows where the finger is and seeks when it lifts.
 */
@Composable
private fun SeekRow(state: AudioDockState, nowMs: Long, seek: (Float) -> Unit) {
    val c = LocalPageColors.current
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state) { drag = null } // the page's next push is where the book now is
    val fraction = drag ?: state.fractionAt(nowMs)
    val shown = if (drag != null) state.duration * fraction else state.positionAt(nowMs)
    Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(audioClock(shown), color = c.muted, fontSize = 11.sp)
        SeekBar(
            fraction,
            state.duration > 0,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            { drag = it },
        ) {
            drag = it
            seek(it)
        }
        Text(
            if (state.duration > 0) audioClock(state.duration) else "—",
            color = c.muted,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    modifier: Modifier,
    onDrag: (Float) -> Unit,
    onSeek: (Float) -> Unit,
) {
    val c = LocalPageColors.current
    var width by remember { mutableStateOf(1) }
    fun at(x: Float) = (x / width).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxHeight()
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .semantics { contentDescription = "Seek" }
            .pointerInput(enabled) { if (enabled) detectTapGestures { onSeek(at(it.x)) } }
            .pointerInput(enabled) {
                if (enabled) {
                    var x = 0f
                    detectDragGestures(
                        onDragStart = { start: Offset ->
                            x = start.x
                            onDrag(at(x))
                        },
                        onDragEnd = { onSeek(at(x)) },
                    ) { change, delta ->
                        change.consume()
                        x += delta.x
                        onDrag(at(x))
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(c.line))
        Box(Modifier.fillMaxWidth(fraction).height(5.dp).clip(CircleShape).background(c.accent))
    }
}

/** A small button as the page's .btn.small: the accent when [primary], else the secondary look. */
@Composable
private fun DockButton(
    label: String,
    description: String,
    modifier: Modifier,
    primary: Boolean = false,
    onClick: () -> Unit,
) {
    val c = LocalPageColors.current
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .clip(shape)
            .background(if (primary) c.accent else if (c.light) c.panel else c.panel2)
            .then(if (primary) Modifier else Modifier.border(1.dp, c.line, shape))
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) c.onAccent else c.text,
            fontSize = 12.5.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** The speed as the page's select words it: 1×, 1.25×. */
fun rateLabel(rate: Double): String =
    (if (rate == Math.rint(rate)) rate.toInt().toString() else rate.toString()) + "×"
