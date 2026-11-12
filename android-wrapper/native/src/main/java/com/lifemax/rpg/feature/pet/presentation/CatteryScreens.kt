package com.lifemax.rpg.feature.pet.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifemax.rpg.core.designsystem.LocalPageColors
import com.lifemax.rpg.core.designsystem.PageColors
import com.lifemax.rpg.core.designsystem.PageTheme
import com.lifemax.rpg.core.designsystem.components.tapTarget
import com.lifemax.rpg.feature.pet.domain.CatteryCat
import com.lifemax.rpg.feature.pet.domain.CatteryCommand
import com.lifemax.rpg.feature.pet.domain.CatteryDetail
import com.lifemax.rpg.feature.pet.domain.CatteryScreen
import com.lifemax.rpg.feature.pet.domain.CatteryWords
import com.lifemax.rpg.feature.pet.domain.PetNotice
import com.lifemax.rpg.feature.pet.domain.PetProjection

/** What a cattery screen can ask: Back, a command for the page, and "the message has shown". */
class CatteryActions(
    val back: () -> Unit = {},
    val send: (CatteryCommand) -> Unit = {},
    val noticeShown: (PetNotice) -> Unit = {},
)

/**
 * A dialog the screen has open for a cat: the page's confirm() for a release, its prompt() for a
 * name.
 */
private sealed interface Asking {
    data class Release(val cat: CatteryCat) : Asking

    data class Rename(val cat: CatteryCat, val max: Int) : Asking
}

/**
 * One of the cattery screens over the Cat tab, full screen: a header with Back (and the system Back
 * does the same), then the screen's list from the page's detail. While the detail has not come
 * ("Loading") nothing can be tapped. The page decides and words everything; a tap is a command and
 * the page's answer (its message in the toast, its new detail) is what changes the screen.
 */
@Composable
fun CatteryHost(
    p: PetProjection,
    screen: CatteryScreen,
    ui: PetUi,
    actions: CatteryActions,
    topInsetDp: Float = 64f,
    bottomInsetDp: Float = 72f,
    /** The only thing on the tab (no cat at home): no Back, nowhere behind it to go. */
    root: Boolean = false,
) {
    val cattery = p.cattery ?: return
    if (!root) BackHandler(onBack = actions.back)
    PageTheme(remember(p.tokens, p.light) { PageColors(p.tokens, p.light) }) {
        val c = LocalPageColors.current
        val words = cattery.words
        var asking by remember { mutableStateOf<Asking?>(null) }
        val detail = cattery.detail.takeIf { cattery.view == screen }
        val busy = ui.waiting != null
        // an adoption that happened (or cannot happen) leaves nothing to show: back to the Cat tab
        val gone = detail is CatteryDetail.Adopt && detail.mode.isEmpty() && !root
        LaunchedEffect(gone) { if (gone) actions.back() }
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(topInsetDp.dp))
                CatteryHeader(titleOf(screen, words), words.back, actions.back.takeUnless { root })
                HorizontalDivider(color = c.line)
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(12.dp, 12.dp, 12.dp, 16.dp),
                ) {
                    when (detail) {
                        null -> item("loading") { Loading() }
                        is CatteryDetail.Roster ->
                            rosterItems(detail, words, busy, actions.send) { asking = it }
                        is CatteryDetail.Lab ->
                            labItems(detail, words, busy, actions.send) { asking = it }
                        is CatteryDetail.Adopt -> adoptItems(detail, words, busy, actions)
                        is CatteryDetail.Memorial ->
                            memorialItems(detail, words, busy, actions.send)
                    }
                }
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(bottomInsetDp.dp))
            }
            ui.notice?.let {
                NoticeToast(
                    it,
                    p,
                    actions.noticeShown,
                    Modifier.align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = bottomInsetDp.dp + 12.dp),
                )
            }
        }
        // the page's confirm() and prompt(), asked here, before the command is sent
        when (val a = asking) {
            is Asking.Release ->
                CatteryConfirm(
                    a.cat.ask,
                    words.release,
                    words.cancel,
                    {
                        asking = null
                        actions.send(CatteryCommand.Release(a.cat.id))
                    },
                    { asking = null },
                )
            is Asking.Rename ->
                CatteryNameDialog(
                    words.rename + " " + a.cat.name,
                    words.nameLabel,
                    a.cat.name,
                    a.max,
                    words.save,
                    words.cancel,
                    {
                        asking = null
                        actions.send(CatteryCommand.Rename(a.cat.id, it))
                    },
                    { asking = null },
                )
            null -> {}
        }
    }
}

private fun titleOf(screen: CatteryScreen, w: CatteryWords) =
    when (screen) {
        CatteryScreen.CATTERY -> w.cattery
        CatteryScreen.LAB -> w.lab
        CatteryScreen.ADOPT -> w.adoptTitle
        CatteryScreen.MEMORIAL -> w.memorial
    }

/** The header: a Back button (48 dp) and the screen's title as a heading. */
@Composable
private fun CatteryHeader(title: String, back: String, onBack: (() -> Unit)?) {
    val c = LocalPageColors.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null)
            Text(
                "‹",
                Modifier.semantics { contentDescription = back }
                    .tapTarget(Role.Button, onClick = onBack)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                color = c.accent,
                fontSize = 28.sp,
            )
        Text(
            title,
            Modifier.weight(1f)
                .semantics { heading() }
                .padding(
                    start = if (onBack == null) 14.dp else 0.dp,
                    top = if (onBack == null) 12.dp else 0.dp,
                    bottom = if (onBack == null) 12.dp else 0.dp,
                ),
            color = c.text,
            fontSize = 20.sp,
            fontWeight = FontWeight(650),
        )
    }
}

@Composable
private fun Loading() {
    Text(
        "…",
        Modifier.fillMaxWidth().padding(24.dp).semantics {
            liveRegion = LiveRegionMode.Polite
            contentDescription = "Loading"
        },
        color = LocalPageColors.current.muted,
        fontSize = 20.sp,
    )
}

/**
 * Your cats: the roster with Bring home, Release and Rename for the cattery's, and Another stray.
 */
private fun LazyListScope.rosterItems(
    d: CatteryDetail.Roster,
    w: CatteryWords,
    busy: Boolean,
    send: (CatteryCommand) -> Unit,
    ask: (Asking) -> Unit,
) {
    item("head") { SectionHead(d.header, d.small) }
    d.cats.forEach { cat ->
        item(cat.id) {
            CatteryCatCard(cat, Modifier.padding(top = 10.dp)) {
                if (!cat.home)
                    CatteryButtons {
                        CatteryButton(
                            w.home,
                            if (d.canHome) w.home + " " + cat.name else w.homeTaken,
                            enabled = d.canHome && !busy,
                        ) {
                            send(CatteryCommand.Home(cat.id))
                        }
                        CatteryButton(w.rename, w.rename + " " + cat.name, enabled = !busy) {
                            ask(Asking.Rename(cat, d.nameMax))
                        }
                        CatteryButton(w.release, w.release + " " + cat.name, enabled = !busy) {
                            ask(Asking.Release(cat))
                        }
                    }
            }
        }
    }
    item("stray") {
        Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            CatteryButton(w.stray, w.stray, enabled = !busy) { send(CatteryCommand.Stray) }
            PetNote(w.strayHint, size = 12.sp)
        }
    }
}

/** The breeding lab: goal, the pair, the forecast, Breed, and the litter with Keep and Release. */
private fun LazyListScope.labItems(
    d: CatteryDetail.Lab,
    w: CatteryWords,
    busy: Boolean,
    send: (CatteryCommand) -> Unit,
    ask: (Asking) -> Unit,
) {
    item("intro") { PetNote(d.intro, size = 13.sp) }
    item("goal") {
        CatteryPicker(
            w.goal,
            d.options.map { Choice(it.value, it.label) },
            d.goal,
            { send(CatteryCommand.Goal(it)) },
            Modifier.padding(top = 12.dp),
        )
    }
    if (d.best.isNotEmpty()) item("best") { PetNote(d.best, Modifier.padding(top = 8.dp)) }
    if (d.note.isNotEmpty()) item("note") { PetNote(d.note, Modifier.padding(top = 10.dp)) }
    // no mother or no fertile father to pair: the way to find one is here, not a screen away
    if (d.moms.isEmpty() || d.dads.isEmpty())
        item("stray") {
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CatteryButton(w.stray, w.stray, enabled = !busy) { send(CatteryCommand.Stray) }
                PetNote(w.strayHint, size = 12.sp)
            }
        }
    if (d.moms.isNotEmpty() && d.dads.isNotEmpty()) {
        item("pair") {
            Column(
                Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CatteryPicker(
                    w.mother,
                    d.moms.map { Choice(it.key, it.name + if (it.home) " (home)" else "") },
                    d.mom,
                    { send(CatteryCommand.Pick("mom", it)) },
                )
                CatteryPicker(
                    w.father,
                    d.dads.map { Choice(it.key, it.name + if (it.home) " (home)" else "") },
                    d.dad,
                    { send(CatteryCommand.Pick("dad", it)) },
                )
                if (d.forecast.isNotEmpty()) PetNote(d.forecast)
                CatteryButton(w.breed, w.breed, primary = true, enabled = !busy) {
                    send(CatteryCommand.Breed(d.mom, d.dad))
                }
            }
        }
    }
    val litter = d.litter
    if (litter != null) {
        item("litter") { SectionHead(litter.title, litter.parents) }
        litter.kits.forEachIndexed { i, kit ->
            item("kit${i}_${kit.sub}") {
                CatteryCatCard(kit, Modifier.padding(top = 10.dp)) {
                    CatteryButtons {
                        CatteryButton(
                            w.keep,
                            w.keep + " " + kit.name,
                            primary = true,
                            enabled = !busy,
                        ) {
                            send(CatteryCommand.Keep(litter.at, i))
                        }
                        CatteryButton(w.release, w.release + " " + kit.name, enabled = !busy) {
                            send(CatteryCommand.Drop(litter.at, i))
                        }
                    }
                }
            }
        }
        item("litterAll") {
            Column(
                Modifier.padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatteryButtons {
                    CatteryButton(w.keepAll, w.keepAll, enabled = !busy) {
                        send(CatteryCommand.KeepAll(litter.at))
                    }
                    CatteryButton(w.releaseAll, w.releaseAll, enabled = !busy) {
                        send(CatteryCommand.DropAll(litter.at))
                    }
                }
                if (litter.full) PetNote(litter.fullNote)
            }
        }
    }
}

/** A heading with the page's small line beside or under it. */
@Composable
internal fun SectionHead(title: String, small: String, modifier: Modifier = Modifier) {
    val c = LocalPageColors.current
    Column(
        modifier.fillMaxWidth().padding(top = 8.dp).semantics(mergeDescendants = true) { heading() }
    ) {
        Text(title.uppercase(), color = c.muted, fontSize = 11.sp, letterSpacing = .88.sp)
        if (small.isNotEmpty()) PetNote(small, Modifier.padding(top = 2.dp), size = 12.sp)
    }
}
