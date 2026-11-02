package com.lifemax.rpg.feature.library

import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.data.toMediaReply
import com.lifemax.rpg.feature.library.data.upsertPayload
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaAskKind
import com.lifemax.rpg.feature.library.domain.MediaCommands
import com.lifemax.rpg.feature.library.domain.MediaField
import com.lifemax.rpg.feature.library.domain.MediaFields
import com.lifemax.rpg.feature.library.domain.MediaReply
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Media sheets (plan phase 3), native half: the form decoded strictly from the page's
 * projection (media-sheets.json, tools/gen_native_fixtures.js), the sheet's validation in the
 * page's words, the add's operation id kept until a terminal reply and reused on the retry, the
 * optimistic tick settling, the confirms in the page's words, and the wire names.
 */
class MediaSheetsTest {
    /** A page that records what the screen sends and answers nothing until told to. */
    private class FakePage : MediaCommands {
        val sent = mutableListOf<Triple<String, String, Any>>()
        private var n = 0

        private fun record(command: String, payload: Any) =
            "req${++n}".also { sent += Triple(command, it, payload) }

        override fun upsert(op: String, id: String?, fields: MediaFields) =
            record("upsert", Triple(op, id, fields))

        override fun setStatus(id: String, status: String) = record("setStatus", id to status)

        override fun remove(id: String) = record("remove", id)

        override fun removeAudio(id: String) = record("removeAudio", id)

        fun last() = sent.last()

        /** The last upsert's operation id, item id and fields. */
        @Suppress("UNCHECKED_CAST")
        fun lastUpsert() =
            sent.last { it.first == "upsert" }.third as Triple<String, String?, MediaFields>
    }

    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/media-$name.json")!!.readText())

    private fun setUp(
        name: String = "sheets"
    ): Triple<WebMediaRepository, MediaViewModel, FakePage> {
        val page = FakePage()
        val repo = WebMediaRepository().apply { accept(fixture(name), true) }
        val vm = MediaViewModel(repo, page).apply { onProjection() }
        return Triple(repo, vm, page)
    }

    private fun reply(
        request: String,
        command: String = "upsert",
        ok: Boolean = true,
        changed: Boolean = true,
        message: String = "",
        field: String = "",
    ) = MediaReply(command, request, ok, changed, message, "", field)

    private fun MediaViewModel.item(title: String) =
        ui!!.projection.items.first { it.title == title }

    @Test
    fun decodesTheFormFromThePage() {
        val f = WebMediaRepository().apply { accept(fixture("sheets"), true) }.state.value!!.form!!
        assertEquals("Add to your library" to "Edit media item", f.addHeading to f.editHeading)
        assertEquals("Save item" to "Cancel", f.save to f.cancel)
        assertEquals("Title *", f.title.label)
        assertEquals(160, f.title.maxLength)
        assertEquals("Add a title first", f.title.required)
        assertNull(f.creator.required)
        assertEquals(1000, f.notes.maxLength)
        assertEquals("Book" to "unread", f.defaultType to f.defaultStatus)
        assertEquals(8, f.type.options.size)
        assertEquals(listOf("unread", "progress", "finished"), f.status.options.map { it.id })
        assertEquals("ftp://example.com/old", f.links.values.single())
        assertEquals(
            "Remove \"Saga\" from your media library?",
            f.ask(MediaAskKind.REMOVE).about("Saga"),
        )
        assertEquals("Remove audio", f.ask(MediaAskKind.REMOVE_AUDIO).confirm)
    }

    @Test
    fun aPageThatPredatesTheSheetsKeepsItsOwnForm() {
        val (_, vm, page) = setUp("full")
        assertNull(vm.ui!!.projection.form)
        assertFalse(vm.sheets.handle(MediaAction.Add))
        val handed = mutableListOf<MediaAction>()
        MediaActions(vm, { handed += it }, {}).page(MediaAction.Add)
        assertEquals(listOf<MediaAction>(MediaAction.Add), handed)
        assertTrue(page.sent.isEmpty())
    }

    @Test
    fun aMalformedFormIsRejectedAndTheLastGoodStateKept() {
        val repo = WebMediaRepository().apply { accept(fixture("sheets"), true) }
        val good = repo.state.value
        fun next(change: JSONObject.() -> Unit) = fixture("sheets").put("sequence", 2).apply(change)
        val broken =
            listOf<JSONObject.() -> Unit>(
                { put("edit", "sometimes") },
                { put("form", JSONObject.NULL) },
                { put("edit", "page") },
                { getJSONObject("form").remove("links") },
                {
                    getJSONObject("form")
                        .getJSONObject("fields")
                        .getJSONObject("title")
                        .remove("required")
                },
                {
                    getJSONObject("form")
                        .getJSONObject("fields")
                        .getJSONObject("link")
                        .put("maxLength", 0)
                },
                {
                    getJSONObject("form")
                        .getJSONObject("confirms")
                        .getJSONObject("remove")
                        .put("text", "Remove it?")
                },
                { getJSONObject("form").getJSONObject("defaults").put("type", "Comic") },
            )
        broken.forEach { change ->
            assertThrows(Exception::class.java) { repo.accept(next(change)) }
            assertEquals(good, repo.state.value)
        }
        // a page that edits on itself sends edit "page" and no form
        repo.accept(next { put("edit", "page").put("form", JSONObject.NULL) })
        assertNull(repo.state.value!!.form)
    }

    @Test
    fun theSheetChecksTheTitleInThePagesWordsBeforeSending() {
        val (_, vm, page) = setUp()
        assertTrue(vm.sheets.handle(MediaAction.Add))
        val s = vm.sheets.sheet!!
        assertNull(s.id)
        assertEquals(MediaFields("", "Book", "", "unread", "", ""), s.draft)
        vm.sheets.change(MediaField.TITLE, "   ")
        vm.sheets.save()
        assertEquals(mapOf(MediaField.TITLE to "Add a title first"), vm.sheets.sheet!!.errors)
        assertTrue(page.sent.isEmpty())
        // typing in the refused box clears its words
        vm.sheets.change(MediaField.TITLE, "Dune")
        assertTrue(vm.sheets.sheet!!.errors.isEmpty())
    }

    @Test
    fun anAddKeepsItsOperationIdUntilATerminalReplyAndTheRetryReusesIt() {
        val (_, vm, page) = setUp()
        vm.sheets.add()
        vm.sheets.change(MediaField.TITLE, "Dune")
        vm.sheets.save()
        val (command, first, payload) = page.last()
        val (op, id, fields) = page.lastUpsert()
        assertEquals("upsert", command)
        assertNull(id)
        assertEquals("Dune", fields.title)
        assertTrue(vm.sheets.sheet!!.pending)
        assertEquals(op, vm.sheets.operation("new"))
        // the screen hid before the reply: the reopened screen stops waiting, keeps the draft
        vm.opened()
        assertFalse(vm.sheets.sheet!!.pending)
        assertEquals("Dune", vm.sheets.sheet!!.draft.title)
        vm.sheets.onReply(reply(first)) // a reply to a request no longer awaited changes nothing
        assertNotNull(vm.sheets.sheet)
        vm.sheets.save()
        val (_, second, again) = page.last()
        assertEquals(payload, again)
        // the page recognised the operation ("Added to your library", unchanged): ok closes it
        vm.sheets.onReply(reply(second, changed = false, message = "Added to your library"))
        assertNull(vm.sheets.sheet)
        assertNull(vm.sheets.operation("new"))
        vm.sheets.add()
        vm.sheets.change(MediaField.TITLE, "Dune Messiah")
        vm.sheets.save()
        assertNotEquals(op, page.lastUpsert().first)
    }

    @Test
    fun aRefusalIsShownInThePagesWordsAndOnlyOkCloses() {
        val (_, vm, page) = setUp()
        vm.sheets.add()
        vm.sheets.change(MediaField.TITLE, "Dune")
        vm.sheets.change(MediaField.LINK, "ftp://dune")
        vm.sheets.save()
        val op = vm.sheets.operation("new")
        vm.sheets.onReply(
            reply(
                page.last().second,
                ok = false,
                changed = false,
                message = "Use a valid http or https link",
                field = "link",
            )
        )
        val s = vm.sheets.sheet!!
        assertFalse(s.pending)
        assertEquals(mapOf(MediaField.LINK to "Use a valid http or https link"), s.errors)
        assertNull(vm.sheets.operation("new")) // a field's refusal is terminal
        vm.sheets.change(MediaField.LINK, "https://dune")
        vm.sheets.save()
        assertNotEquals(op, vm.sheets.operation("new"))
        val kept = vm.sheets.operation("new")
        // a save that failed: the page took it back; the same id lands it once next time
        val failed = "Could not save to this browser (storage full?)"
        vm.sheets.onReply(reply(page.last().second, ok = false, changed = false, message = failed))
        assertEquals(failed, vm.sheets.sheet!!.error)
        assertEquals(kept, vm.sheets.operation("new"))
    }

    @Test
    fun anEditStartsFromWhatThePagesFormShowsAndKeepsAnUnusualType() {
        val (_, vm, page) = setUp()
        val saga = vm.item("Saga, volume one")
        vm.sheets.handle(MediaAction.Edit(saga.id))
        val s = vm.sheets.sheet!!
        assertEquals(saga.id, s.id)
        assertEquals("Comic", s.draft.type)
        assertEquals("Comic", s.typeOptions.first().id)
        assertEquals(9, s.typeOptions.size)
        vm.sheets.save()
        val (_, id, fields) = page.lastUpsert()
        assertEquals(saga.id, id)
        assertEquals("Comic", fields.type)
        // a stored link the rows do not show is what the edit starts from, so it is not dropped
        vm.sheets.onReply(reply(page.last().second, message = "Media item updated"))
        val old = vm.item("Old bookmark")
        assertEquals("", old.link)
        vm.sheets.edit(old)
        assertEquals("ftp://example.com/old", vm.sheets.sheet!!.draft.link)
        assertEquals(8, vm.sheets.sheet!!.typeOptions.size)
    }

    @Test
    fun theTickShowsAtOnceAndSettlesOnThePagesAnswer() {
        val (_, vm, page) = setUp()
        val book = vm.ui!!.projection.items.first { !it.finished }
        vm.sheets.handle(MediaAction.SetFinished(book.id, true))
        assertEquals(mapOf(book.id to true), vm.sheets.ticks)
        assertEquals(Triple("setStatus", "req1", book.id to "finished"), page.last())
        vm.sheets.handle(MediaAction.SetFinished(book.id, false)) // one at a time per item
        assertEquals(1, page.sent.size)
        vm.sheets.onReply(reply("req1", command = "setStatus", message = "Marked as finished"))
        assertTrue(vm.sheets.ticks.isEmpty())
        vm.sheets.handle(MediaAction.SetFinished(book.id, false))
        assertEquals(book.id to "progress", page.last().third)
        vm.opened() // a reply lost with the screen: the tick shows the projection again
        assertTrue(vm.sheets.ticks.isEmpty())
    }

    @Test
    fun theConfirmsAskInThePagesWordsAndSendOnlyOnConfirm() {
        val (_, vm, page) = setUp()
        val saga = vm.item("Saga, volume one")
        vm.sheets.handle(MediaAction.Remove(saga.id))
        val ask = vm.sheets.ask!!
        assertEquals("Remove \"Saga, volume one\" from your media library?", ask.text)
        assertEquals("Remove" to "Cancel", ask.confirm to ask.cancel)
        vm.sheets.dismissAsk()
        assertTrue(page.sent.isEmpty())
        vm.sheets.handle(MediaAction.RemoveAudio(saga.id))
        assertEquals(MediaAskKind.REMOVE_AUDIO, vm.sheets.ask!!.kind)
        assertTrue(
            vm.sheets.ask!!
                .text
                .startsWith("Remove the stored audio file for \"Saga, volume one\"?")
        )
        vm.sheets.confirmAsk()
        assertNull(vm.sheets.ask)
        assertEquals(Triple("removeAudio", "req1", saga.id), page.last())
        vm.sheets.handle(MediaAction.Remove(saga.id))
        vm.sheets.confirmAsk()
        assertEquals("remove", page.last().first)
    }

    @Test
    fun aSheetOrConfirmOfAnItemGoneMeanwhileCloses() {
        val (repo, vm, _) = setUp()
        val saga = vm.item("Saga, volume one")
        vm.sheets.edit(saga)
        val next = fixture("sheets").put("sequence", 2)
        val items = next.getJSONArray("items")
        val keep =
            (0 until items.length())
                .map { items.getJSONObject(it) }
                .filter { it.getString("id") != saga.id }
        next.put("items", org.json.JSONArray(keep))
        repo.accept(next)
        vm.onProjection()
        assertNull(vm.sheets.sheet)
    }

    @Test
    fun aNewPageSessionForgetsTheOldOnesOperations() {
        val (repo, vm, _) = setUp()
        vm.sheets.add()
        vm.sheets.change(MediaField.TITLE, "Dune")
        vm.sheets.save()
        assertNotNull(vm.sheets.operation("new"))
        repo.accept(fixture("sheets").put("webSessionId", "another"), true)
        vm.opened()
        vm.onProjection()
        assertNull(vm.sheets.sheet)
        assertNull(vm.sheets.operation("new"))
    }

    @Test
    fun theScreenHandlesTheSheetsTapsAndHandsOverPlayAndAttach() {
        val (_, vm, page) = setUp()
        val handed = mutableListOf<MediaAction>()
        val opened = mutableListOf<String>()
        val actions = MediaActions(vm, { handed += it }, { opened += it })
        val book = vm.item("Atomic Habits")
        actions.page(MediaAction.Add)
        actions.page(MediaAction.Edit(book.id))
        actions.page(MediaAction.SetFinished(book.id, !book.finished))
        assertTrue(handed.isEmpty())
        assertEquals(1, page.sent.size)
        actions.page(MediaAction.Attach(book.id))
        assertEquals(listOf<MediaAction>(MediaAction.Attach(book.id)), handed)
        val linked = vm.ui!!.projection.items.first { it.link.isNotEmpty() }
        actions.page(MediaAction.OpenLink(linked.id))
        assertEquals(listOf(linked.link), opened)
        assertEquals(1, handed.size)
    }

    @Test
    fun wireNamesAndReplies() {
        val add = upsertPayload("op1", null, MediaFields("Dune", "Book", "", "unread", "", "n"))
        assertFalse(add.has("id"))
        assertEquals("op1", add.getString("op"))
        assertEquals(
            listOf("title", "type", "creator", "status", "link", "notes").sorted(),
            add.getJSONObject("fields").keys().asSequence().toList().sorted(),
        )
        assertEquals(
            "m1",
            upsertPayload("op2", "m1", MediaFields("", "", "", "", "", "")).getString("id"),
        )
        val r =
            JSONObject(
                    """{"command":"upsert","requestId":"q","ok":false,"changed":false,"message":"Add a title first","id":"","field":"title"}"""
                )
                .toMediaReply()!!
        assertEquals(MediaReply("upsert", "q", false, false, "Add a title first", "", "title"), r)
        assertEquals(
            MediaReply("remove", "q", true, false, "Removed from library"),
            JSONObject(
                    """{"command":"remove","requestId":"q","ok":true,"changed":false,"message":"Removed from library"}"""
                )
                .toMediaReply(),
        )
        assertNull(JSONObject("""{"command":"toggle","requestId":"q","ok":true}""").toMediaReply())
        assertThrows(Exception::class.java) {
            JSONObject("""{"command":"setStatus","requestId":"q","message":""}""").toMediaReply()
        }
    }
}
