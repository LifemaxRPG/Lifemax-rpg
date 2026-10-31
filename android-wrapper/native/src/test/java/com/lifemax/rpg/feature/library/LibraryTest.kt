package com.lifemax.rpg.feature.library

import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.data.toEvent
import com.lifemax.rpg.feature.library.domain.MediaAction
import com.lifemax.rpg.feature.library.domain.MediaFilter
import com.lifemax.rpg.feature.library.domain.PageNotice
import com.lifemax.rpg.feature.library.presentation.MediaActions
import com.lifemax.rpg.feature.library.presentation.MediaViewModel
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun fixture(name: String = "full") =
        JSONObject(javaClass.getResource("/fixtures/media-$name.json")!!.readText())

    private fun JSONObject.item(index: Int): JSONObject = getJSONArray("items").getJSONObject(index)

    private fun setUp(name: String = "full"): Pair<WebMediaRepository, MediaViewModel> {
        val repo = WebMediaRepository().apply { accept(fixture(name), true) }
        val vm = MediaViewModel(repo).apply { onProjection() }
        return repo to vm
    }

    private fun MediaViewModel.shownIds() = ui!!.shown.map { it.id }

    @Test
    fun decodesTheWebProjection() {
        val p = WebMediaRepository().apply { accept(fixture(), true) }.state.value!!
        assertEquals(listOf("media", "history"), p.panes.map { it.id })
        assertTrue(p.panes.first().current)
        assertEquals("＋ Add media", p.header.add)
        assertNull(p.tip)
        assertEquals(listOf("23", "4", "5", "17%"), p.stats.map { it.value })
        assertEquals(listOf(null, null, null, 17), p.stats.map { it.bar })
        assertEquals(MediaFilter("", "all", "all"), p.filter)
        assertEquals(1L, p.filterRevision)
        assertEquals(23, p.items.size)
        val book = p.items.first()
        assertEquals("Project Hail Mary", book.title)
        assertEquals("Audiobook · Andy Weir", book.subtitle)
        assertEquals("project-hail-mary.m4b", book.audio!!.file)
        assertEquals("26% listened", book.audio!!.progress.label)
        assertEquals("4:10:00 / 16:10:00", book.audio!!.progress.time)
        val memos = p.items.first { it.title == "Voice memos" }.audio!!
        assertEquals("—", memos.progress.time)
        assertEquals(0.0, memos.progress.pct, 0.0)
        val legacy = p.items.first { it.title == "Old import" }
        assertEquals("" to "", legacy.type to legacy.status)
        assertEquals("Other · Unknown", legacy.subtitle)
        assertTrue(p.items.first { it.title == "The Bitter Lesson" }.finished)
        assertEquals("", p.noMatch.add)
        assertEquals("＋ Add your first item", p.empty.add)
        assertNull(p.notice)

        val tip = WebMediaRepository().apply { accept(fixture("tip"), true) }.state.value!!.tip!!
        assertEquals("Personal library", tip.title)
        assertEquals("Dismiss tip", tip.dismiss)
        val emptied = WebMediaRepository().apply { accept(fixture("empty"), true) }.state.value!!
        assertEquals(PageNotice(15, "Removed from library"), emptied.notice)
    }

    @Test
    fun olderAndForeignUpdatesAreIgnored() {
        val repo = WebMediaRepository()
        repo.accept(fixture().put("sequence", 2), true)
        repo.accept(fixture("empty"))
        assertEquals(23, repo.state.value!!.items.size)
        repo.accept(fixture("empty").put("sequence", 9).put("webSessionId", "other"))
        assertEquals("media-fixture", repo.state.value!!.session)
        repo.accept(fixture("empty").put("webSessionId", "other"), establishSession = true)
        assertEquals("other", repo.state.value!!.session)
        assertTrue(repo.state.value!!.items.isEmpty())
    }

    @Test
    fun malformedProjectionsKeepTheLastGoodOne() {
        val repo = WebMediaRepository()
        repo.accept(fixture(), true)
        val broken =
            listOf(
                fixture().put("protocol", 2),
                fixture().also { it.remove("header") },
                fixture().also { it.getJSONArray("stats").remove(3) },
                fixture().also { it.getJSONArray("stats").getJSONObject(3).put("bar", 120) },
                fixture().also { it.getJSONArray("panes").getJSONObject(1).put("current", true) },
                fixture().also { it.getJSONObject("filter").put("status", "glorious") },
                fixture()
                    .also { it.getJSONObject("filter").put("type", "Book") }
                    .also { it.getJSONObject("typeFilter").put("options", JSONArray()) },
                fixture().also { it.item(1).put("id", it.item(0).getString("id")) },
                fixture().also { it.item(0).put("finished", true) },
                fixture().also {
                    it.item(0).getJSONObject("audio").getJSONObject("progress").put("pct", 140)
                },
                fixture().also { it.item(2).remove("notes") },
                fixture().also { it.getJSONObject("tokens").remove("line-soft") },
                // a key the page always sends is missing, or not an object: never read as absent
                fixture().also { it.remove("tip") },
                fixture().put("tip", "Personal library"),
                fixture().also { it.item(0).remove("audio") },
                fixture().also { it.item(0).put("audio", "project-hail-mary.m4b") },
                fixture().also { it.getJSONArray("stats").getJSONObject(0).remove("bar") },
                fixture().also { it.getJSONObject("empty").remove("add") },
                fixture().also { it.remove("notice") },
                fixture().put("notice", JSONObject().put("id", 3)),
            )
        broken.forEachIndexed { i, json ->
            assertThrows(Exception::class.java) { repo.accept(json.put("sequence", 2L + i)) }
        }
        assertEquals(1L, repo.state.value!!.sequence)
        assertEquals(23, repo.state.value!!.items.size)
    }

    /** The rows the page itself shows for each sample filter (media-filter-parity.json). */
    @Test
    fun theFilterShowsExactlyThePagesRows() {
        val (repo, vm) = setUp()
        val items = repo.state.value!!.items
        val cases = fixture("filter-parity").getJSONArray("cases")
        assertEquals(15, cases.length())
        for (k in 0 until cases.length()) {
            val case = cases.getJSONObject(k)
            val f = case.getJSONObject("filter")
            val filter =
                MediaFilter(f.getString("search"), f.getString("status"), f.getString("type"))
            val ids = case.getJSONArray("ids").let { a -> (0 until a.length()).map(a::getString) }
            assertEquals("$filter", ids, filter.matching(items).map { it.id })
            vm.search(filter.search)
            vm.showStatus(filter.status)
            vm.showType(filter.type)
            assertEquals("$filter through the view model", ids, vm.shownIds())
        }
    }

    /** JavaScript's trim, not Kotlin's: a byte-order mark is trimmed, a file separator is not. */
    @Test
    fun theSearchIsTrimmedAsThePageTrimsIt() {
        val items = WebMediaRepository().apply { accept(fixture(), true) }.state.value!!.items
        fun shown(search: String) =
            MediaFilter(search, "all", "all").matching(items).map { it.title }
        assertEquals(listOf("Deep Work"), shown("\uFEFF\u00A0deep\u3000\n"))
        assertTrue(shown("\u001Cdeep").isEmpty())
    }

    @Test
    fun theScreenKeepsItsFilterUntilThePageSetsOne() {
        val (repo, vm) = setUp()
        assertEquals(23, vm.ui!!.shown.size)
        vm.showStatus("finished")
        vm.showType("not a type")
        assertEquals(MediaFilter("", "finished", "all"), vm.ui!!.filter)
        // the page's 15-second refresh, and a reopening after a delegated tap: same revision
        repo.accept(fixture().put("sequence", 2))
        vm.onProjection()
        repo.accept(fixture().put("sequence", 3), true)
        vm.opened()
        vm.onProjection()
        assertEquals(4, vm.ui!!.shown.size)
        assertEquals(3L, vm.ui!!.projection.sequence)
        // a voice search raised the page's revision
        repo.accept(fixture("search").put("sequence", 4))
        vm.onProjection()
        assertEquals(MediaFilter("wealth", "all", "all"), vm.ui!!.filter)
        assertEquals(
            listOf(
                "The Psychology of Money",
                "The Simple Path to Wealth",
                "The Almanack of Naval Ravikant",
            ),
            vm.ui!!.shown.map { it.title },
        )
        // a new page session starts from the page's filter
        vm.search("deep")
        repo.accept(fixture().put("webSessionId", "reloaded"), true)
        vm.onProjection()
        assertEquals(MediaFilter("", "all", "all"), vm.ui!!.filter)
    }

    /**
     * The page's message shows once, and one cut short by the screen closing does not come back.
     */
    @Test
    fun thePagesMessageShowsOnce() {
        val (repo, vm) = setUp()
        assertNull(vm.notice)
        fun notice(id: Int, text: String) = JSONObject().put("id", id).put("text", text)
        fun update(
            sequence: Long,
            notice: JSONObject?,
            open: Boolean = false,
            page: String? = null,
        ) {
            val json = fixture().put("sequence", sequence).put("notice", notice ?: JSONObject.NULL)
            page?.let { json.put("webSessionId", it) }
            repo.accept(json, open)
            if (open) vm.opened()
            vm.onProjection()
        }
        update(2, notice(7, "Removed from library"), open = true)
        val removed = PageNotice(7, "Removed from library")
        assertEquals(removed, vm.notice)
        update(3, notice(7, "Removed from library")) // a refresh while the page still shows it
        assertEquals(removed, vm.notice)
        vm.noticeShown(removed)
        assertNull(vm.notice)
        update(4, notice(7, "Removed from library"))
        assertNull(vm.notice)
        // closed while showing it; back while the page still shows it
        update(5, notice(8, "Marked as finished"))
        update(6, notice(8, "Marked as finished"), open = true)
        assertNull(vm.notice)
        // the same words again are a new message, and so is the first of a reloaded page
        update(7, notice(9, "Marked as finished"))
        assertEquals(PageNotice(9, "Marked as finished"), vm.notice)
        vm.noticeShown(PageNotice(8, "Marked as finished"))
        assertEquals(9L, vm.notice!!.id)
        update(1, notice(7, "Removed from library"), open = true, page = "reloaded")
        assertEquals(removed, vm.notice)
        update(2, null, page = "reloaded")
        assertEquals(removed, vm.notice)
    }

    @Test
    fun anEmptyLibraryAndAFilterThatLeavesNothing() {
        val (_, empty) = setUp("empty")
        assertTrue(empty.ui!!.shown.isEmpty() && empty.ui!!.projection.items.isEmpty())
        val (_, vm) = setUp()
        vm.search("nothing like this")
        assertTrue(vm.ui!!.shown.isEmpty())
        assertEquals(23, vm.ui!!.projection.items.size)
    }

    @Test
    fun eachActionReachesThePageAsItsOwnHandlerExpects() {
        val id = "reading_deep_work"
        assertEquals("add" to "", MediaAction.Add.toEvent())
        assertEquals("tip" to "", MediaAction.DismissTip.toEvent())
        assertEquals("navigate" to "history", MediaAction.OpenPane("history").toEvent())
        assertEquals("edit" to id, MediaAction.Edit(id).toEvent())
        assertEquals("finished" to id, MediaAction.SetFinished(id, true).toEvent())
        assertEquals("unfinished" to id, MediaAction.SetFinished(id, false).toEvent())
        assertEquals("remove" to id, MediaAction.Remove(id).toEvent())
        assertEquals("play" to id, MediaAction.Play(id).toEvent())
        assertEquals("attach" to id, MediaAction.Attach(id).toEvent())
        assertEquals("replace" to id, MediaAction.Replace(id).toEvent())
        assertEquals("removeAudio" to id, MediaAction.RemoveAudio(id).toEvent())
        assertEquals("link" to id, MediaAction.OpenLink(id).toEvent())
    }

    /**
     * The page answers a tap by closing the screen: one goes per opening, a quick second does not.
     */
    @Test
    fun oneTapIsHandedOverPerOpening() {
        val (_, vm) = setUp()
        val sent = mutableListOf<MediaAction>()
        val actions = MediaActions(vm) { sent += it }
        actions.page(MediaAction.SetFinished("reading_outlive", true))
        actions.page(MediaAction.Remove("reading_outlive"))
        assertEquals(listOf<MediaAction>(MediaAction.SetFinished("reading_outlive", true)), sent)
        vm.opened()
        actions.page(MediaAction.Add)
        assertEquals(MediaAction.Add, sent.last())
        // searching and filtering stay on the screen
        actions.search("habits")
        assertEquals(listOf("reading_atomic_habits"), vm.shownIds())
        assertFalse(vm.handOver())
    }
}
