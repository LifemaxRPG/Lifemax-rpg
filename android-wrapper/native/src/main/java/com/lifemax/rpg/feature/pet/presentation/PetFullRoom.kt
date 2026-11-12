package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetProjection

/**
 * The room over the whole screen: its own window above the app's bars, the system bars hidden (a
 * swipe from the edge brings them back for a moment), the house at the largest whole number of
 * device pixels that shows it from floor to ceiling, and more of it sideways in landscape. The cats
 * answer a finger here as on the Cat tab, and the bowl feeds. Back or the close chip ends it. The
 * room runs while [running] and holds still with [still].
 */
@Composable
internal fun PetFullRoom(
    p: PetProjection,
    room: RoomState.Ready,
    running: Boolean,
    still: Boolean,
    onBowl: () -> Unit,
    onClose: () -> Unit,
    onCat: (Int) -> Unit = {},
) {
    Dialog(
        onDismissRequest = onClose,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        PetFullRoomContent(p, room, running, still, onBowl, onClose, onCat)
    }
}

/**
 * What [PetFullRoom] shows: the room filling its window on the room's dark, and the close chip in
 * the corner clear of the cutout and the bars. Apart from the window so a test can draw it.
 */
@Composable
internal fun PetFullRoomContent(
    p: PetProjection,
    room: RoomState.Ready,
    running: Boolean,
    still: Boolean,
    onBowl: () -> Unit,
    onClose: () -> Unit,
    onCat: (Int) -> Unit = {},
) {
    val names = p.cats.joinToString(" and ") { it.name }
    Box(Modifier.fillMaxSize().background(FullRoomDark)) {
        RoomHost(
            room,
            running = running,
            still = still,
            modifier =
                Modifier.fillMaxSize().semantics { contentDescription = "$names in the room" },
            touch = RoomTouch(fullScreen = true, onBowl = onBowl, onCat = onCat),
        )
        Box(
            Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(CHIP_MARGIN)
                .clip(CHIP_SHAPE)
                .background(CHIP_FILL)
                .tapTarget(Role.Button, onClick = onClose)
                .padding(horizontal = CHIP_PAD_X, vertical = CHIP_PAD_Y)
        ) {
            Text(
                "✕ ${p.touch?.words?.closeRoom.orEmpty()}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight(600),
                lineHeight = 16.sp,
            )
        }
    }
}

private val FullRoomDark = Color(0xFF10131C)
private val CHIP_FILL = Color(0xCC10131C)
private val CHIP_SHAPE = RoundedCornerShape(10.dp)
private val CHIP_MARGIN = 10.dp
private val CHIP_PAD_X = 12.dp
private val CHIP_PAD_Y = 9.dp
