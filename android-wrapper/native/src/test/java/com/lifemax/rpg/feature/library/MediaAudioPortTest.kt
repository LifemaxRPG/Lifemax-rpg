package com.lifemax.rpg.feature.library

import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaAudioPort
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * With the audio dock on, Play and Attach/Replace stay on the screen (commands and Android's
 * picker); without it every tap is the page's, as before, and one hand-over per opening.
 */
class MediaAudioPortTest {
    private class Port(override val dock: Boolean, override val canPick: Boolean) : MediaAudioPort {
        val calls = mutableListOf<String>()

        override fun play(id: String) {
            calls += "play $id"
        }

        override fun pick(id: String) {
            calls += "pick $id"
        }
    }

    private fun viewModel(): MediaViewModel {
        val json = JSONObject(javaClass.getResource("/fixtures/media-full.json")!!.readText())
        val repo = WebMediaRepository().apply { accept(json, true) }
        return MediaViewModel(repo).apply { onProjection() }
    }

    private fun run(port: Port?, vararg taps: MediaAction): List<String> {
        val page = mutableListOf<String>()
        val actions = MediaActions(viewModel(), port) { page += it.toString() }
        taps.forEach { actions.page(it) }
        return page
    }

    @Test
    fun withTheDockPlayIsACommandAndTheScreenStaysOpen() {
        val port = Port(dock = true, canPick = true)
        val page = run(port, MediaAction.Play("a"), MediaAction.Play("b"))
        assertEquals(listOf("play a", "play b"), port.calls)
        assertEquals(emptyList<String>(), page)
    }

    @Test
    fun withTheDockOffPlayGoesToThePageOncePerOpening() {
        val port = Port(dock = false, canPick = false)
        val page = run(port, MediaAction.Play("a"), MediaAction.Play("b"))
        assertEquals(emptyList<String>(), port.calls)
        assertEquals(1, page.size) // the second quick tap is dropped, as before
    }

    @Test
    fun attachAndReplaceOpenThePickerWhenItCan() {
        val port = Port(dock = true, canPick = true)
        val page =
            run(port, MediaAction.Attach("a"), MediaAction.Replace("b"), MediaAction.Attach("c"))
        assertEquals(listOf("pick a", "pick b", "pick c"), port.calls)
        assertEquals(emptyList<String>(), page)
    }

    @Test
    fun withoutAPickerTheRowsOwnButtonStillGetsTheTap() {
        val port = Port(dock = true, canPick = false)
        val page = run(port, MediaAction.Attach("a"))
        assertEquals(emptyList<String>(), port.calls)
        assertEquals(listOf(MediaAction.Attach("a").toString()), page)
    }

    @Test
    fun everyOtherTapIsStillThePages() {
        val port = Port(dock = true, canPick = true)
        val page = run(port, MediaAction.Remove("a"))
        assertEquals(listOf(MediaAction.Remove("a").toString()), page)
        assertEquals(emptyList<String>(), port.calls)
    }

    @Test
    fun noPortIsTheOldBehaviour() {
        val page = run(null, MediaAction.Play("a"))
        assertEquals(listOf(MediaAction.Play("a").toString()), page)
    }
}
