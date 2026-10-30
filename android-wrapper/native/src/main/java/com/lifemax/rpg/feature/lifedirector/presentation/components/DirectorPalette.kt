package com.lifemax.rpg.feature.lifedirector.presentation.components

import androidx.compose.ui.graphics.Color
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind

/**
 * Life Director's own colours (src/features/life-director/style.css). It is an instrument panel and
 * stays dark in both LifeMax themes, so these are fixed, not theme tokens.
 */
object DirectorPalette {
    val bg0 = Color(0xFF090B10)
    val line = Color.White.copy(alpha = .07f)
    val line2 = Color.White.copy(alpha = .13f)
    val panelEdge = Color.White.copy(alpha = .04f)
    val ink = Color(0xFFEFF4FF)
    val ink2 = Color(0xFFB6C2D9)
    val ink3 = Color(0xFF8592AA)
    val good = Color(0xFF34D399)

    val sleep = Color(0xFF8B5CF6)
    val work = Color(0xFFF59E0B)
    val commute = Color(0xFF64748B)
    val maint = Color(0xFF06B6D4)
    val task = Color(0xFF10B981)
    val rest = Color(0xFFF472B6)
    val free = Color(0xFF3B82F6)
    val block = Color(0xFFEF4444)
    val meal = Color(0xFFFDBA74)
    val appointment = Color(0xFF60A5FA)

    fun of(kind: BlockKind): Color =
        when (kind) {
            BlockKind.SLEEP -> sleep
            BlockKind.WORK -> work
            BlockKind.COMMUTE -> commute
            BlockKind.MAINT -> maint
            BlockKind.BLOCK -> block
            BlockKind.TASK -> task
            BlockKind.MEAL -> meal
            BlockKind.REST -> rest
            BlockKind.FREE -> free
        }

    /** The lighter tint the centre writes "Now" in. */
    fun kick(kind: BlockKind): Color =
        when (kind) {
            BlockKind.SLEEP -> Color(0xFFB9A3FF)
            BlockKind.WORK -> Color(0xFFFBBF4A)
            BlockKind.COMMUTE -> Color(0xFFA8B5C8)
            BlockKind.MAINT -> Color(0xFF67E3F5)
            BlockKind.BLOCK -> Color(0xFFFCA5A5)
            BlockKind.TASK -> mint
            BlockKind.MEAL -> Color(0xFFFDC896)
            BlockKind.REST -> Color(0xFFF9A8D4)
            BlockKind.FREE -> Color(0xFF93B8FF)
        }

    val sky = Color(0xFF86B4FF)
    val glass = Color(0x99050A14)
    val glassEdge = Color(0x476F94D3)
    val mint = Color(0xFF6EE7B7)
    val amber = Color(0xFFFFD28A)
    val rose = Color(0xFFFCA5A5)
    val nowBlue = Color(0xFF7DD3FC)
    val warn = Color(0xFFFBBF24)
    val warnBright = Color(0xFFFDE68A)
    val goTop = Color(0xFF22C48F)
    val goBottom = Color(0xFF0EA877)
    val goInk = Color(0xFF04150F)
    val toast = Color(0xFF1B2232)
    val toastShadow = Color.Black.copy(alpha = .45f)
    val statusOk = Color(0xFF38E6B4)

    // White washes over the panel: cards, fills and rules
    val cardTop = Color.White.copy(alpha = .035f)
    val cardBottom = Color.White.copy(alpha = .015f)
    val restCard = Color.White.copy(alpha = .03f)
    val faintFill = Color.White.copy(alpha = .02f)
    val buttonFill = Color.White.copy(alpha = .045f)
    val centreButton = Color.White.copy(alpha = .05f)
    val chipFill = Color.White.copy(alpha = .06f)
    val pillFill = Color.White.copy(alpha = .07f)
    val mixTrack = Color.White.copy(alpha = .04f)
    val rule = Color.White.copy(alpha = .14f)

    // The dial
    val bezel = listOf(0f to Color(0xFF171E2C), .35f to Color(0xFF0B0E15), 1f to Color(0xFF080C14))
    val face = listOf(0f to Color(0xFF0B1122), .55f to Color(0xFF121826), 1f to Color(0xFF1B1B22))
    val hub = listOf(0f to Color(0xFF141C2E), .5f to Color(0xFF101625), 1f to Color(0xFF0C111C))
    val edge =
        listOf(
            0f to Color(0x42D2E2FF),
            .42f to Color(0x0A8093B0),
            .7f to Color(0x47000000),
            1f to Color(0x99000000),
        )
    val bezelStroke = Color(0x1FB9CBE2)
    val chapter = Color(0xFF10192A)
    val channel = Color(0xA3050A13)
    val divider = Color(0x0BACBDDE)
    val lip = Color(0x09B2C9EE)
    val tick = Color.White.copy(alpha = .1f)
    val tickHour = Color.White.copy(alpha = .26f)
    val tickSix = Color.White.copy(alpha = .5f)
    val hourWord = Color(0xFF7D8AA3)
    val hourWordMain = Color(0xFFC9D4EA)
    val moon = Color(0xB3A9B8E8)
    val sun = Color(0xBFF5C46B)
    val outerTrack = Color.White.copy(alpha = .025f)
    val innerTrack = free.copy(alpha = .065f)
    val done = Color(0xFF1F6F55)
    val halo = Color(0xD9EAFFF6)
    val nextGlow = Color(0xE634D399)
    val iconOnSession = Color(0xFF04241A)
    val iconOnMeal = Color(0xFF3B1D06)
    val check = Color(0xFFD1FAE5)
    val cross = Color(0xFFFECACA)
    val wordDark = Color(0xFF1A1204)
    val wordOnMaint = Color(0xFF032229)
    val wordOnCommute = Color(0xFFEEF2F8)
    val wordFree = Color(0xD98FB6FF)
    val wordRest = Color(0xE6F9A8D4)
    val past = Color(0x9E05080E)
    val hand = nowBlue
    val handGlow = Color(0xFF38BDF8)
    val handTip = Color(0xFFE0F2FE)
    val ringTrack = Color.White.copy(alpha = .05f)
    val ringFree = Color(0xFF6F9CFF)
}
