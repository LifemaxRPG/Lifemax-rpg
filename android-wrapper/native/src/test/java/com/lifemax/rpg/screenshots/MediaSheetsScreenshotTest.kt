package com.lifemax.rpg.screenshots

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaField
import com.lifemax.rpg.feature.library.domain.MediaReply
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaEdits
import com.lifemax.rpg.feature.library.presentation.MediaScreen
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test

/**
 * The Library's Media sheets (plan phase 3) as the phone draws them, over the pane, from the
 * projection a page gives a screen that draws them (fixtures/media-sheets.json): the add sheet, an
 * edit refused in the page's words, an old item's unusual type kept, the two confirms and a tick
 * waiting for the page's answer. Same phone and image rules as MediaScreenshotTest. A select's open
 * menu is a popup, which Paparazzi does not draw.
 */
class MediaSheetsScreenshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(deviceConfig = phone(), useDeviceResolution = true, maxPercentDifference = 0.001)

    private fun phone(fontScale: Float = 1f) =
        DeviceConfig.PIXEL_5.copy(
            screenHeight = 1080,
            density = Density(420),
            fontScale = fontScale,
        )

    private fun model(light: Boolean) =
        MediaViewModel(
                WebMediaRepository().apply {
                    accept(
                        JSONObject(
                                javaClass.getResource("/fixtures/media-sheets.json")!!.readText()
                            )
                            .put("light", light)
                            .put("tokens", ScreenshotPalette.tokens(light)),
                        true,
                    )
                }
            )
            .apply { onProjection() }

    private fun MediaViewModel.id(title: String) =
        ui!!.projection.items.first { it.title == title }.id

    /**
     * The pane from list item [item], with what [setUp] opened over it; a sheet scrolled to its
     * [end] (its notes, Save and Cancel).
     */
    private fun snap(
        light: Boolean = false,
        item: Int = 0,
        end: Boolean = false,
        setUp: MediaViewModel.() -> Unit,
    ) {
        val vm = model(light).apply(setUp)
        val s = vm.sheets
        paparazzi.snapshot {
            MediaScreen(
                vm.ui!!,
                MediaActions(),
                list = rememberLazyListState(item),
                edits = MediaEdits(vm.ui!!.projection.form, s.sheet, s.ask, s.ticks),
                // past the end, which the card's scroll keeps to its end
                sheetScroll = rememberScrollState(if (end) 100_000 else 0),
            )
        }
    }

    /** "＋ Add media": the page's empty form, a Book to read, over the dimmed pane. */
    @Test fun addDark() = snap { sheets.handle(MediaAction.Add) }

    @Test fun addLight() = snap(light = true) { sheets.handle(MediaAction.Add) }

    /** The add sheet's end: the link, the notes box, Save item and Cancel. */
    @Test fun addEndDark() = snap(end = true) { sheets.handle(MediaAction.Add) }

    @Test fun addEndLight() = snap(light = true, end = true) { sheets.handle(MediaAction.Add) }

    /** Save sent, the page not answered yet: nothing can be changed and Save is faded. */
    @Test
    fun addSendingDark() =
        snap(end = true) {
            sheets.add()
            sheets.change(MediaField.TITLE, "Dune")
            sheets.change(MediaField.NOTES, "Book one of six")
            sheets.save()
        }

    /** An add with a blank title: the page's own words under the title, nothing sent. */
    @Test
    fun addRefusedDark() = snap {
        sheets.add()
        sheets.change(MediaField.CREATOR, "Frank Herbert")
        sheets.save()
    }

    /**
     * Edit of an old bookmark whose stored link is not http: the page refused it, and its words
     * stand under the link the form started from.
     */
    @Test
    fun editRefusedDark() =
        snap(end = true) {
            sheets.handle(MediaAction.Edit(id("Old bookmark")))
            sheets.change(MediaField.NOTES, "Find the new address")
            sheets.save()
            sheets.onReply(
                MediaReply("upsert", "", false, false, "Use a valid http or https link", "", "link")
            )
        }

    /** Edit of an item of a type the form no longer offers: the type is kept, not blanked. */
    @Test
    fun editUnusualTypeLight() =
        snap(light = true) { sheets.handle(MediaAction.Edit(id("Saga, volume one"))) }

    /** The page's Remove confirm, word for word, without the page's stray backslash. */
    @Test
    fun confirmRemoveDark() =
        snap(item = ROWS) { sheets.handle(MediaAction.Remove(id("Saga, volume one"))) }

    @Test
    fun confirmRemoveAudioLight() =
        snap(light = true, item = ROWS) {
            sheets.handle(MediaAction.RemoveAudio(id("Saga, volume one")))
        }

    /** Two ticks sent and not answered yet: the state asked for, faded. */
    @Test
    fun ticksPendingDark() =
        snap(item = ROWS) {
            val items = ui!!.projection.items
            items.firstOrNull { !it.finished }?.let { sheets.tick(it, true) }
            items.firstOrNull { it.finished }?.let { sheets.tick(it, false) }
        }

    @Test
    fun ticksPendingLight() =
        snap(light = true, item = ROWS) {
            val items = ui!!.projection.items
            items.firstOrNull { !it.finished }?.let { sheets.tick(it, true) }
            items.firstOrNull { it.finished }?.let { sheets.tick(it, false) }
        }

    /** Larger text (Settings, font size, at its largest): the add sheet scrolls in its card. */
    @Test
    fun addLargeText() {
        paparazzi.unsafeUpdateConfig(phone(fontScale = 2f))
        snap { sheets.handle(MediaAction.Add) }
    }

    private companion object {
        /** The list's first row: under the switch, head, stats and filters. */
        const val ROWS = 4
    }
}
