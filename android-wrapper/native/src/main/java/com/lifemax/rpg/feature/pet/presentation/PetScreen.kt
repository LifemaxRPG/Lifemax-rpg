package com.lifemax.rpg.feature.pet.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.PageButton
import com.lifemax.rpg.core.designsystem.components.PageCard
import com.lifemax.rpg.core.designsystem.components.PageEyebrow
import com.lifemax.rpg.core.designsystem.components.PageToast
import com.lifemax.rpg.core.designsystem.components.playToast
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.PetNotice
import com.lifemax.rpg.feature.pet.domain.PetProjection
import com.lifemax.rpg.feature.pet.host.CatWorldHost

/**
 * What the screen is showing on top of the page's projection: the command being answered and a
 * message.
 */
class PetUi(
    val waiting: String? = null,
    val notice: PetNotice? = null,
    /** The toy tray under Play is open (`pet.play.v1`). */
    val toyTray: Boolean = false,
    /**
     * DEVELOPER MODE: what the depth is doing on this phone, asked for twice a second; null (the
     * default) shows nothing.
     */
    val depthNote: (() -> String)? = null,
)

/** Everything the screen can ask for, so the screen itself holds no state of the page's. */
class PetActions(
    val feed: () -> Unit = {},
    val blink: () -> Unit = {},
    val vacation: (Boolean) -> Unit = {},
    val rename: (String) -> Unit = {},
    val focus: (Int) -> Unit = {},
    val buyToy: (String) -> Unit = {},
    val expandGenome: (Boolean) -> Unit = {},
    /** The Play button, the full-screen room and the rest of the Cat page: the page's own view. */
    val handOver: (action: String) -> Unit = {},
    val noticeShown: (PetNotice) -> Unit = {},
    /** The full-screen room, the screen's own with a page that has touch. */
    val openRoom: () -> Unit = {},
    /** Play: the screen's toy tray with a page that sends one, else the page's own (handOver). */
    val play: () -> Unit = {},
    /** A toy picked from the tray (null: the cat picks): tossed to the cats in the room. */
    val playToy: (String?) -> Unit = {},
    /** DEVELOPER DEMO: the "Demo parkour" button (developer mode only, `pet.demo.v1`). */
    val demo: () -> Unit = {},
    /** A cattery screen from the entry card (`pet.cattery.v1`). */
    val catteryOpen: (com.lifemax.rpg.feature.pet.domain.CatteryScreen) -> Unit = {},
)

/** The page's actions that stay on the page in this release, as the screen names them to it. */
object PetHandOver {
    const val ROOM = "room"
    const val PLAY = "play"
    const val MORE = "page"
}

/**
 * The Cat tab as the page lays it out: one scrolling list whose first item is the live room, full
 * width (at a whole number of device pixels per room pixel) with its View room chip, then the cat's
 * card (name, hearts, mood, bond, buttons, stats, favourites, hint), the care card and the way to
 * the rest of the Cat page. The room scrolls away with the cards. The room is [room], given the
 * modifier that sizes it, so a test can fix it; the cats and the camera belong to the room state
 * its caller keeps (see rememberRoomState), so an item that leaves the list and comes back finds
 * them where they were, and a room scrolled completely off screen is not composed, so its clock
 * does not run. [listState] lets a test start part-way down. The page's message shows as its toast
 * over the bottom.
 */
@Composable
fun PetScreen(
    p: PetProjection,
    ui: PetUi,
    actions: PetActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    careOpen: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    room: @Composable (Modifier) -> Unit,
) {
    PageTheme(remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }) {
        val c = LocalPageColors.current
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(topInsetDp.dp))
                HorizontalDivider(color = c.line)
                LazyColumn(
                    Modifier.weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    // the room is the first thing in the list, so it scrolls away with the cards;
                    // its cats and camera live in the caller's room state, not in this item
                    item("room") {
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.fillMaxWidth().background(Stage)) {
                                room(Modifier.fillMaxWidth().aspectRatio(ROOM_ASPECT))
                                ViewRoomChip(
                                    p.words.room,
                                    {
                                        if (p.touch != null) actions.openRoom()
                                        else actions.handOver(PetHandOver.ROOM)
                                    },
                                    Modifier.align(Alignment.BottomEnd),
                                )
                            }
                            HorizontalDivider(color = c.lineSoft)
                        }
                    }
                    item("card") {
                        CatCard(p, ui, actions, Modifier.padding(12.dp, 12.dp, 12.dp, 0.dp))
                    }
                    petTogetherItem(p, ui, actions)
                    item("care") {
                        PetCareCard(
                            p,
                            ui.waiting,
                            actions.vacation,
                            actions.rename,
                            Modifier.padding(12.dp, 14.dp, 12.dp, 0.dp),
                            startOpen = careOpen,
                        )
                    }
                    petPanelItems(p, ui, actions)
                    petCatteryItem(p, actions)
                    item("more") {
                        MoreRow(p, Modifier.padding(12.dp, 14.dp, 12.dp, 0.dp)) {
                            actions.handOver(PetHandOver.MORE)
                        }
                    }
                }
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(bottomInsetDp.dp))
            }
            ui.notice?.let { notice ->
                NoticeToast(
                    notice,
                    p,
                    actions.noticeShown,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomInsetDp.dp + 12.dp),
                )
            }
        }
    }
}

/**
 * `.pet-card`: the head, the mood, the bond, the buttons, the stats, the favourites and the hint.
 */
@Composable
private fun CatCard(p: PetProjection, ui: PetUi, actions: PetActions, modifier: Modifier) {
    val c = LocalPageColors.current
    PageCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                PageEyebrow(p.eyebrow)
                Text(
                    p.name,
                    Modifier.padding(top = 2.dp).semantics { heading() },
                    color = c.text,
                    fontSize = 25.sp,
                    fontWeight = FontWeight(650),
                    letterSpacing = (-0.75).sp,
                    lineHeight = 30.sp,
                )
            }
            PetHearts(p.hearts, Modifier.padding(top = 7.dp))
        }
        if (p.mood.isNotEmpty())
            Text(
                p.mood,
                Modifier.padding(start = 2.dp, top = 1.dp, end = 2.dp, bottom = 2.dp),
                color = c.text,
                fontSize = 14.5.sp,
                fontWeight = FontWeight(650),
                lineHeight = 21.75.sp,
            )
        PanelSource.mind(p.panels?.mind, p.focus, CatWorldHost.shared)?.let {
            PetMindRow(it, Modifier.padding(top = 10.dp))
        }
        PetBondBar(p.bond.pct, p.bond.text, Modifier.padding(start = 2.dp, top = 11.dp, end = 2.dp))
        p.growth?.let { PetGrowthLine(it, Modifier.padding(top = 12.dp)) }
        Buttons(p, ui, actions)
        PetStats(
            listOf(
                p.lastMeal.value to p.lastMeal.label,
                p.mealsToday to "meals today",
                p.daysTogether to "days together",
            ),
            Modifier.padding(top = 14.dp),
        )
        PetFavourites(
            listOf("Favorite place" to p.favourites.place, "Favorite toy" to p.favourites.toy),
            Modifier.padding(top = 8.dp),
        )
        if (p.hint.isNotEmpty())
            PetNote(p.hint, Modifier.padding(start = 2.dp, top = 13.dp, end = 2.dp))
        p.touch
            ?.words
            ?.countsLine
            ?.takeIf { it.isNotEmpty() }
            ?.let { PetNote(it, Modifier.padding(start = 2.dp, top = 8.dp, end = 2.dp)) }
    }
}

/**
 * `.pet-actions`: Feed (fainter while the cat is not due a meal) beside Play, and Slow blink under
 * them.
 */
@Composable
private fun Buttons(p: PetProjection, ui: PetUi, actions: PetActions) {
    Column(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PageButton(
                p.feed.label,
                p.feed.label,
                actions.feed,
                Modifier.weight(1.5f)
                    .alpha(if (p.feed.wait || ui.waiting == "feed") WAIT_ALPHA else 1f),
            )
            PetSecondaryButton(p.words.play, p.words.play, actions.play, Modifier.weight(1f))
        }
        val tray = p.play
        if (ui.toyTray && tray != null) PetToyTray(tray, actions.playToy)
        PetSecondaryButton(
            p.words.blink,
            p.words.blinkHint,
            actions.blink,
            Modifier.fillMaxWidth(),
            small = true,
        )
        p.demo?.let {
            PetSecondaryButton(
                it.label,
                it.hint,
                actions.demo,
                Modifier.fillMaxWidth(),
                small = true,
            )
        }
        ui.depthNote?.let { DepthReadout(it, Modifier.padding(horizontal = 2.dp)) }
    }
}

/** The way to everything the Cat page has that this screen does not: the page's own Cat view. */
@Composable
private fun MoreRow(p: PetProjection, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalPageColors.current
    PageCard(modifier.fillMaxWidth().tapTarget(Role.Button, onClick = onClick), padding = 15.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text(
                    p.words.moreTitle,
                    color = c.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (p.together.isNotEmpty())
                    PetNote(p.together, Modifier.padding(top = 4.dp), size = 12.sp)
                PetNote(p.words.moreText, Modifier.padding(top = 4.dp), size = 12.sp)
            }
            Text("→", color = c.muted, fontSize = 20.sp)
        }
    }
}

/** `.pet-expand-hint`: the corner chip that opens the page's full-screen room. */
@Composable
private fun ViewRoomChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(9.dp)
            .clip(ChipShape)
            .background(ChipFill)
            .tapTarget(Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            "⛶ $text",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight(600),
            lineHeight = 14.4.sp,
        )
    }
}

/**
 * The page's message as its toast shows it: it rises in, stays as long as the page's, and fades
 * away.
 */
@Composable
internal fun NoticeToast(
    notice: PetNotice,
    p: PetProjection,
    onShown: (PetNotice) -> Unit,
    modifier: Modifier,
) {
    val shown = remember(notice) { Animatable(0f) }
    LaunchedEffect(notice) {
        shown.playToast()
        onShown(notice)
    }
    PageToast(notice.text, p.tokens, p.light, modifier, shown = { shown.value })
}

/** `.pet-stage`'s fixed dark and the room window's 192 x 144 room pixels. */
private val Stage = Color(0xFF10131C)
private const val ROOM_ASPECT = 192f / 144f
private val ChipFill = Color(0xCC10131C)
private val ChipShape = RoundedCornerShape(9.dp)

/** `#petFeedBtn.is-wait`: the Feed button at 60 % while the cat is not due a meal. */
private const val WAIT_ALPHA = .6f
