package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.BlockKind
import com.lifemax.rpg.feature.lifedirector.domain.BuildItem
import com.lifemax.rpg.feature.lifedirector.domain.DialHandle
import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.DirectorDial
import com.lifemax.rpg.feature.lifedirector.domain.DirectorReply
import com.lifemax.rpg.feature.lifedirector.domain.FlyKind
import com.lifemax.rpg.feature.lifedirector.domain.HandleEnd
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.presentation.DialHold
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.dial.BuildBuzz
import com.lifemax.rpg.feature.lifedirector.presentation.dial.BuildTimeline
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DragGeometry
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The dial's handles and its build on the native screen: the projection's new fields decoded, the
 * drag looked up in the page's stops and lengths, the dialDrop and dialApply flow with every reply
 * the page gives (director-replies.json), and the build's timeline, all on fixtures the page
 * produced.
 */
class LifeDirectorDialTest {
    private fun fixture(name: String): JSONObject =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private fun repository(name: String = "today") =
        WebLifeDirectorRepository().apply { accept(fixture(name), true) }

    private fun dial(name: String = "today"): DirectorDial =
        repository(name).state.value!!.today!!.dial

    private val replies = fixture("replies")

    /** The page's reply [name], answering [requestId]. */
    private fun reply(name: String, requestId: String): DirectorReply =
        directorReply(
            JSONObject(replies.getJSONObject(name).toString()).put("requestId", requestId)
        )

    private fun rejects(j: JSONObject) {
        val repo = repository()
        try {
            repo.accept(j.put("sequence", 2))
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        throw AssertionError("accepted a malformed projection")
    }

    private class Recorder : LifeDirectorCommands {
        val sent = mutableListOf<Triple<String, String, DialPlace>>()

        override fun showDay(index: Int) = "day-$index"

        override fun dialDrop(key: String, place: DialPlace) = record("dialDrop", key, place)

        override fun dialApply(key: String, place: DialPlace) = record("dialApply", key, place)

        private fun record(command: String, key: String, place: DialPlace): String {
            sent += Triple(command, key, place)
            return "director-${sent.size}"
        }
    }

    private fun viewModel(
        name: String = "today"
    ): Triple<WebLifeDirectorRepository, LifeDirectorViewModel, Recorder> {
        val repo = repository(name)
        val commands = Recorder()
        return Triple(repo, LifeDirectorViewModel(repo, commands).apply { opened() }, commands)
    }

    private fun DirectorDial.handle(label: String) = handles.single { it.label == label }

    private fun DirectorDial.stopAt(minute: Int) = stops.indexOfFirst { it.m == minute }

    private fun DirectorDial.held(label: String) =
        handle(label).let { DialHold(it, stops, dragWords, DragGeometry.stopOf(it, stops), true) }

    // The projection

    @Test
    fun decodesTheHandlesAndTheirPlacesAsThePageProjectsThem() {
        val p = repository().state.value!!
        val d = p.today!!.dial
        assertFalse(p.reducedMotion)
        assertEquals(
            "Tap a session for details. Drag a" to "handle to change your sleep or work hours.",
            p.today!!.hint,
        )
        assertEquals(
            listOf("Wake up", "Bedtime", "Work starts", "Work ends"),
            d.handles.map { it.label },
        )
        val bed = d.handle("Bedtime")
        assertEquals("sleep|start|2026-10-28|1793228400", bed.key)
        assertEquals(HandleEnd.START, bed.end)
        assertEquals(BlockKind.SLEEP, bed.kind)
        assertEquals(345f, bed.a, 0f)
        assertEquals(1380, bed.minute)
        assertEquals(360f, bed.fixed, 0f)
        assertEquals("Bedtime 11:00 PM", bed.aria)
        assertEquals(BlockKind.WORK, d.handle("Work ends").kind)
        assertEquals(96, d.stops.size)
        assertEquals(0f, d.stops.first().from, 0f)
        assertEquals("11:45 PM", d.stops.last().time)
        assertTrue(d.handles.all { it.lengths.size == d.stops.size })
        assertEquals("Too short", d.dragWords.tooShort)
        assertEquals("Release to apply", d.dragWords.release)
        assertNull(d.build)
        assertTrue(
            d.arcs.first { it.title == "Sleep" }.tip.endsWith("Drag its handle to change it.")
        )
    }

    @Test
    fun decodesTheBuildsThePagePlays() {
        val repair = dial("build").build!!
        assertFalse(repair.full)
        assertNull(repair.sweep)
        assertEquals("Re-planning", repair.intro!!.kick)
        assertEquals(140, repair.start.at)
        assertEquals("1 added", repair.start.sub)
        val item = repair.items.single()
        assertEquals(FlyKind.NEW, item.kind)
        assertEquals(Triple(140, 560, 521), Triple(item.at, item.ms, item.land))
        assertEquals(3, repair.keep.size)
        assertEquals(760, repair.lock.at)
        assertEquals("4", repair.lock.big)
        assertEquals(1580, repair.end)
        val full = dial("replan").build!!
        assertTrue(full.full)
        assertNull(full.intro)
        assertEquals(1250, full.sweep!!.ms)
        assertEquals("Mapping your day", full.sweep!!.kick)
        assertEquals(29, full.sweep!!.readout.size)
        val skip = dial("skip").build!!
        assertTrue(skip.items.isEmpty())
        assertEquals(listOf("i_1ap3yp1xvo91m2q4qdhxpu"), skip.flips)
        val reduced = repository("reduced").state.value!!
        assertTrue(reduced.reducedMotion)
        assertNull(reduced.today!!.dial.build)
    }

    @Test
    fun malformedHandlesAndBuildsAreRejected() {
        fun dialOf(j: JSONObject) = j.getJSONObject("today").getJSONObject("dial")
        rejects(
            fixture("today").also {
                dialOf(it)
                    .getJSONArray("handles")
                    .getJSONObject(0)
                    .getJSONArray("lengths")
                    .remove(0)
            }
        )
        rejects(
            fixture("today").also {
                dialOf(it).getJSONArray("stops").getJSONObject(0).put("from", 1)
            }
        )
        rejects(
            fixture("today").also {
                dialOf(it).getJSONArray("stops").getJSONObject(5).put("from", 0)
            }
        )
        rejects(
            fixture("today").also {
                dialOf(it).getJSONArray("handles").getJSONObject(0).put("kind", "meal")
            }
        )
        rejects(
            fixture("today").also {
                dialOf(it).getJSONArray("handles").getJSONObject(0).put("a", 361)
            }
        )
        rejects(fixture("today").also { dialOf(it).put("stops", org.json.JSONArray()) })
        rejects(fixture("build").also { dialOf(it).getJSONObject("build").put("full", true) })
        rejects(
            fixture("replan").also {
                dialOf(it).getJSONObject("build").put("sweep", JSONObject.NULL)
            }
        )
        rejects(
            fixture("build").also {
                dialOf(it).getJSONObject("build").getJSONObject("lock").put("at", 99_999)
            }
        )
        rejects(fixture("today").also { it.getJSONObject("today").getJSONArray("hint").remove(1) })
        rejects(fixture("today").also { it.remove("reducedMotion") })
    }

    @Test
    fun decodesEveryReplyOfTheDialCommands() {
        val drop = reply("drop", "r")
        assertTrue(drop.ok && drop.changed)
        val c = drop.confirm!!
        assertEquals("Change your bedtime?", c.title)
        assertEquals("11:00 PM" to "10:30 PM", c.from to c.to)
        assertEquals("Change it", c.apply)
        assertEquals("Keep 11:00 PM", c.keep)
        assertTrue(c.problem.isEmpty() && c.body.isNotEmpty() && c.note.isNotEmpty())
        val problem = reply("dropWithProblem", "r").confirm!!
        assertEquals("OK", problem.keep)
        assertTrue(problem.apply.isEmpty() && problem.problem.isNotEmpty())
        assertNull(reply("dropWhereItWas", "r").confirm)
        val notSaved = reply("applyNotSaved", "r")
        assertFalse(notSaved.ok)
        assertTrue(notSaved.changed && notSaved.moved)
        assertEquals("r", notSaved.requestId)
        assertEquals("dialApply", notSaved.command)
    }

    // The drag, looked up in the page's data

    @Test
    fun theFingerPicksTheLastStopWhoseAngleItHasReached() {
        val stops = dial().stops
        assertEquals(0, DragGeometry.stopAt(stops, 0f))
        assertEquals(0, DragGeometry.stopAt(stops, stops[1].from - .01f))
        assertEquals(1, DragGeometry.stopAt(stops, stops[1].from))
        assertEquals(40, DragGeometry.stopAt(stops, (stops[40].from + stops[41].from) / 2))
        assertEquals(95, DragGeometry.stopAt(stops, 359.99f))
        for (i in stops.indices) assertEquals(i, DragGeometry.stopAt(stops, stops[i].from))
    }

    @Test
    fun aTouchTakesTheNearestHandleWithinItsTarget() {
        val d = dial()
        val bed = d.handle("Bedtime")
        val at = DialGeometry.point(d.radii.outer, bed.a)
        assertEquals(bed, DragGeometry.handleAt(d.handles, d.radii.outer, at, 24f))
        val beside = DialGeometry.point(d.radii.outer + 23f, bed.a)
        assertEquals(bed, DragGeometry.handleAt(d.handles, d.radii.outer, beside, 24f))
        val outside = DialGeometry.point(d.radii.outer + 25f, bed.a)
        assertNull(DragGeometry.handleAt(d.handles, d.radii.outer, outside, 24f))
        // a dial drawn smaller than its units keeps a 48 dp target
        assertEquals(bed, DragGeometry.handleAt(d.handles, d.radii.outer, outside, 30f))
        // between two handles, the nearer
        val work = d.handle("Work starts")
        val nearWork = DialGeometry.point(d.radii.outer, work.a + 2f)
        assertEquals(work, DragGeometry.handleAt(d.handles, d.radii.outer, nearWork, 200f))
        assertEquals(d.stopAt(1380), DragGeometry.stopOf(bed, d.stops))
    }

    @Test
    fun aHeldHandleSpansWhatThePageSaysAndNothingWhenTooShort() {
        val d = dial()
        val bed = d.handle("Bedtime")
        assertEquals(330f to 360f, DragGeometry.span(bed, d.stops, d.stopAt(1320)))
        // bedtime at midnight: the page's 31 hours of sleep fill the whole ring
        assertEquals("31h of sleep", bed.lengths[0])
        assertEquals(0f to 360f, DragGeometry.span(bed, d.stops, 0))
        val wake = d.handle("Wake up")
        assertEquals(0f to 120f, DragGeometry.span(wake, d.stops, d.stopAt(480)))
        val workStart = d.handle("Work starts")
        val late = d.stopAt(1320)
        assertEquals("", workStart.lengths[late])
        assertNull(DragGeometry.span(workStart, d.stops, late))
        val preview = DragGeometry.preview(workStart, d.stops, late, d.dragWords)
        assertEquals("Work starts", preview.kick)
        assertEquals("10:00 PM", preview.time)
        assertEquals("Too short", preview.length)
        assertEquals("Release to apply", preview.release)
        val sleep = DragGeometry.preview(bed, d.stops, d.stopAt(1350), d.dragWords)
        assertEquals("8h 30m of sleep", sleep.length)
    }

    // The command flow

    @Test
    fun lettingGoWhereTheHandleIsOrCancellingSendsNothing() {
        val (repo, vm, commands) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.hold(d.held("Bedtime"))
        assertTrue(vm.ui.value.hold!!.held)
        vm.move(d.stopAt(1320))
        vm.move(d.stopAt(1380))
        vm.release(cancelled = false)
        assertNull(vm.ui.value.hold)
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1320))
        vm.release(cancelled = true)
        assertNull(vm.ui.value.hold)
        assertTrue(commands.sent.isEmpty())
    }

    @Test
    fun aDropAsksThePageAndKeepSendsNothingMore() {
        val (repo, vm, commands) = viewModel()
        val d = repo.state.value!!.today!!.dial
        val bed = d.handle("Bedtime")
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1350))
        vm.release(cancelled = false)
        assertEquals(listOf(Triple("dialDrop", bed.key, DialPlace.Stop(1350))), commands.sent)
        val hold = vm.ui.value.hold!!
        assertFalse(hold.held)
        assertEquals(d.stopAt(1350), hold.stop)
        assertNull(hold.span)
        assertTrue(vm.busy)
        // the handle stays where it was let go while the page answers, and taps wait
        assertFalse(vm.handOver())
        vm.onReply(reply("drop", "director-1"))
        assertEquals("Change your bedtime?", vm.ui.value.ask!!.confirm!!.title)
        vm.keep()
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.hold)
        assertEquals(1, commands.sent.size)
        assertTrue(vm.handOver())
    }

    @Test
    fun changeItAppliesTheSameChangeAndTheNoticeIsThePagesOwn() {
        val (repo, vm, commands) = viewModel()
        val d = repo.state.value!!.today!!.dial
        val bed = d.handle("Bedtime")
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1350))
        vm.release(cancelled = false)
        vm.onReply(reply("drop", "director-1"))
        vm.change()
        assertEquals(Triple("dialApply", bed.key, DialPlace.Stop(1350)), commands.sent.last())
        val ask = vm.ui.value.ask!!
        assertTrue(ask.applying)
        assertNull(ask.confirm)
        assertNotNull(vm.ui.value.hold)
        vm.change()
        assertEquals(2, commands.sent.size)
        // the reply comes with the projection it produced; its message is the frame's notice
        repo.accept(fixture("build").put("sequence", 2))
        vm.onReply(reply("apply", "director-2"))
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.hold)
        assertNull(vm.ui.value.toast)
    }

    @Test
    fun answersToEarlierRequestsAreIgnored() {
        val (repo, vm, _) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1350))
        vm.release(cancelled = false)
        vm.onReply(reply("drop", "director-0"))
        vm.onReply(reply("drop", "day-1"))
        assertNull(vm.ui.value.ask!!.confirm)
        vm.onReply(reply("dropStale", "director-1").copy(command = "day"))
        assertNotNull(vm.ui.value.ask)
    }

    @Test
    fun aDropWhereTheHandleIsOrRefusedLetsGo() {
        val (repo, vm, _) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.step(d.handle("Bedtime"), 1)
        vm.onReply(reply("dropWhereItWas", "director-1"))
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.toast)
        vm.hold(d.held("Work ends"))
        vm.move(d.stopAt(1080))
        vm.release(cancelled = false)
        vm.onReply(reply("dropStale", "director-2"))
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.hold)
        assertEquals(
            "That handle is no longer on the dial. Nothing was changed.",
            vm.ui.value.toast!!.text,
        )
    }

    @Test
    fun aProblemOffersOnlyOk() {
        val (repo, vm, commands) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(240))
        vm.release(cancelled = false)
        vm.onReply(reply("dropWithProblem", "director-1"))
        assertEquals("OK", vm.ui.value.ask!!.confirm!!.keep)
        vm.change()
        assertEquals(1, commands.sent.size)
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.hold)
    }

    @Test
    fun aRefusedSaveShowsThePagesBannerAndOtherRefusalsSayWhy() {
        val (repo, vm, _) = viewModel()
        val d = repo.state.value!!.today!!.dial
        fun applyAt(minute: Int, answer: String, n: Int) {
            vm.hold(d.held("Bedtime"))
            vm.move(d.stopAt(minute))
            vm.release(cancelled = false)
            vm.onReply(reply("drop", "director-$n"))
            vm.change()
            vm.onReply(reply(answer, "director-${n + 1}"))
        }
        applyAt(1350, "applyNotSaved", 1)
        assertNull(vm.ui.value.ask)
        assertNull(vm.ui.value.toast)
        applyAt(1350, "applyAgain", 3)
        assertEquals(
            "That handle is no longer on the dial. Nothing was changed.",
            vm.ui.value.toast!!.text,
        )
        applyAt(1350, "applyWhereItWas", 5)
        assertNull(vm.ui.value.ask)
    }

    @Test
    fun talkBackStepsAQuarterHourAndAsksFirst() {
        val (repo, vm, commands) = viewModel()
        val d = repo.state.value!!.today!!.dial
        val workEnds = d.handle("Work ends")
        vm.step(workEnds, 1)
        vm.step(workEnds, -1)
        vm.hold(d.held("Bedtime"))
        assertEquals(listOf(Triple("dialDrop", workEnds.key, DialPlace.Step(1))), commands.sent)
        assertNull(vm.ui.value.hold)
        vm.onReply(reply("step", "director-1"))
        assertEquals("Change when Work ends?", vm.ui.value.ask!!.confirm!!.title)
        vm.change()
        assertEquals(Triple("dialApply", workEnds.key, DialPlace.Step(1)), commands.sent.last())
        vm.step(workEnds, 2)
        assertEquals(2, commands.sent.size)
    }

    @Test
    fun anUpdateDoesNotMoveAHeldHandle() {
        val (repo, vm, _) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1320))
        val held = vm.ui.value.hold!!
        // the page moved bedtime meanwhile (the build fixture has it at 10:30 PM)
        repo.accept(fixture("build").put("sequence", 2))
        assertTrue(repo.state.value!!.today!!.dial.handles.none { it.key == held.handle.key })
        assertEquals(held, vm.ui.value.hold)
        vm.release(cancelled = false)
        assertEquals(1320 / 15, (vm.ui.value.ask!!.place as DialPlace.Stop).minute / 15)
    }

    @Test
    fun hidingLetsGoAndALateReplyChangesNothing() {
        val (repo, vm, _) = viewModel()
        val d = repo.state.value!!.today!!.dial
        vm.hold(d.held("Bedtime"))
        vm.move(d.stopAt(1350))
        vm.release(cancelled = false)
        vm.hidden()
        assertNull(vm.ui.value.hold)
        assertNull(vm.ui.value.ask)
        vm.onReply(reply("drop", "director-1"))
        assertNull(vm.ui.value.ask)
        vm.showDay(1)
        assertEquals(1, vm.ui.value.pendingDay)
    }

    @Test
    fun theDayWaitsWhileAHandleIsHeld() {
        val (repo, vm, _) = viewModel()
        vm.hold(repo.state.value!!.today!!.dial.held("Bedtime"))
        vm.showDay(1)
        assertNull(vm.ui.value.pendingDay)
        assertFalse(vm.handOver())
    }

    @Test
    fun eachBuildPlaysOncePerPage() {
        val (_, vm, _) = viewModel()
        assertTrue(vm.takeBuild("page", 1793200201500))
        assertFalse(vm.takeBuild("page", 1793200201500))
        assertTrue(vm.takeBuild("page", 1793200201501))
        assertTrue(vm.takeBuild("reloaded page", 1793200201500))
        assertFalse(vm.touched)
        vm.touch()
        assertTrue(vm.touched)
    }

    // The build's timeline

    @Test
    fun aRepairsReadOutFollowsThePagesWords() {
        val b = dial("build").build!!
        assertEquals("Re-planning" to "…", BuildTimeline.words(b, 0f).let { it.kick to it.big })
        assertEquals("0", BuildTimeline.words(b, 140f).big)
        assertEquals("1 added", BuildTimeline.words(b, 140f).sub)
        assertEquals("0", BuildTimeline.words(b, 520f).big)
        assertEquals("1", BuildTimeline.words(b, 521f).big)
        val lock = BuildTimeline.words(b, 760f)
        assertTrue(lock.lock)
        assertEquals(
            Triple("Locked in", "4", "sessions today"),
            Triple(lock.kick, lock.big, lock.sub),
        )
        assertEquals(0f, BuildTimeline.hud(b, 0f).first, 0f)
        assertEquals(1f, BuildTimeline.hud(b, 800f).first, 0f)
        assertEquals(0f, BuildTimeline.hud(b, 1580f).first, 0f)
        assertEquals(1.08f, BuildTimeline.hud(b, 1580f).second, .001f)
        // the live centre comes back over the last 320 ms
        assertEquals(0f, BuildTimeline.centre(b, 1259f).first, 0f)
        assertEquals(1f, BuildTimeline.centre(b, 1580f).first, 0f)
        assertFalse(BuildTimeline.over(b, 1599f))
        assertTrue(BuildTimeline.over(b, 1600f))
        // a repair shows everything else at once
        assertEquals(1f, BuildTimeline.revealed(b, 0f, 300f), 0f)
        assertEquals(1f, BuildTimeline.bandDrawn(b, 0f, 0f, 100f), 0f)
        assertEquals(0f, BuildTimeline.armAlpha(b, 100f), 0f)
        assertEquals(1f, BuildTimeline.handleScale(b, 0f, 2), 0f)
        assertEquals(200f, BuildTimeline.handAngle(b, 900f, 200f), 0f)
    }

    @Test
    fun aNewSessionLaunchesFromTheCentreAndLands() {
        val b = dial("build").build!!
        val item = b.items.single()
        val before = BuildTimeline.flight(b, item, 0f)
        assertEquals(-55f, before.rotation, 0f)
        assertEquals(.1f, before.scale, 0f)
        assertEquals(0f, before.alpha, 0f)
        assertEquals(2.6f, before.brightness, 0f)
        val landed = BuildTimeline.flight(b, item, 700f)
        assertEquals(0f, landed.rotation, 0f)
        assertEquals(1f, landed.scale, 0f)
        assertEquals(1f, landed.alpha, 0f)
        assertEquals(1f, BuildTimeline.flight(b, item, 1080f).brightness, 0f)
        assertNull(BuildTimeline.ripple(item, 520f))
        assertEquals(.3f, BuildTimeline.ripple(item, 521f)!!.first, .001f)
        assertNotNull(BuildTimeline.sparks(item, 700f))
        assertNull(BuildTimeline.sparks(item, 1002f))
        assertEquals(1.28f, BuildTimeline.counterTick(b, 521f).first, .001f)
        assertEquals(1f, BuildTimeline.counterTick(b, 781f).first, 0f)
        assertNull(BuildTimeline.trail(item, 300f))
    }

    @Test
    fun aMovedSessionOrbitsFromWhereItWasWithItsTrail() {
        val b = dial("build").build!!
        val moved = BuildItem("x", FlyKind.MOVED, 200f, 210f, -30f, 140, 626, 653)
        val start = BuildTimeline.flight(b, moved, 0f)
        assertEquals(-30f, start.rotation, 0f)
        assertEquals(.55f, start.alpha, 0f)
        assertEquals(0f, BuildTimeline.flight(b, moved, 766f).rotation, 0f)
        val trail = BuildTimeline.trail(moved, 140f + .25f * 826)!!
        assertEquals(170f to 210f, trail.a0 to trail.a1)
        assertEquals(.75f, trail.alpha, .001f)
        val fullBuild = b.copy(full = true)
        assertEquals(0f, BuildTimeline.flight(fullBuild, moved, 0f).alpha, 0f)
    }

    @Test
    fun aFullBuildSweepsTheDayThenLocks() {
        val b = dial("replan").build!!
        val sweep = b.sweep!!
        assertEquals(180f, BuildTimeline.armAngle(b, sweep.ms / 2f), .01f)
        assertEquals(0f, BuildTimeline.armAlpha(b, 0f), 0f)
        assertEquals(1f, BuildTimeline.armAlpha(b, 600f), 0f)
        assertEquals(0f, BuildTimeline.armAlpha(b, 1410f), 0f)
        val first = BuildTimeline.words(b, 0f)
        assertEquals(
            Triple("Mapping your day", "12 AM", "Sleep"),
            Triple(first.kick, first.big, first.sub),
        )
        // 135 degrees is 9 AM, work
        val nine = BuildTimeline.words(b, 135f / 360 * sweep.ms + 1)
        assertEquals("9 AM" to "Work", nine.big to nine.sub)
        assertEquals("Re-planning", BuildTimeline.words(b, 1410f).kick)
        // work (135 to 255 degrees) is drawn as the arm passes it
        assertEquals(0f, BuildTimeline.bandDrawn(b, 468f, 135f, 255f), 0f)
        assertEquals(.5f, BuildTimeline.bandDrawn(b, 468.75f + 208.33f, 135f, 255f), .01f)
        assertEquals(1f, BuildTimeline.bandDrawn(b, 900f, 135f, 255f), 0f)
        assertEquals(1f, BuildTimeline.tickFlash(b, 0f, 0), 0f)
        assertEquals(0f, BuildTimeline.tickFlash(b, 520.1f, 0), 0f)
        assertEquals(0f, BuildTimeline.tickFlash(b, 0f, 48), 0f)
        assertEquals(0f, BuildTimeline.revealed(b, 0f, 180f), 0f)
        assertEquals(1f, BuildTimeline.revealed(b, 885f, 180f), 0f)
        assertEquals(0f, BuildTimeline.faceShown(b, b.lock.at.toFloat()), 0f)
        assertEquals(1f, BuildTimeline.faceShown(b, b.lock.at + 500f), 0f)
        assertEquals(0f, BuildTimeline.handAngle(b, 0f, 200f), 0f)
        assertEquals(200f, BuildTimeline.handAngle(b, b.lock.at + 840f, 200f), 0f)
        assertEquals(0f, BuildTimeline.handleScale(b, b.lock.at + 200f, 0), 0f)
        assertEquals(1f, BuildTimeline.handleScale(b, b.lock.at + 900f, 3), 0f)
        val kick = (0..440).maxOf { BuildTimeline.dialScale(b, b.lock.at + it.toFloat()) }
        assertEquals(1.025f, kick, .0005f)
        assertEquals(1f, BuildTimeline.dialScale(b, b.lock.at + 441f), 0f)
        assertNotNull(BuildTimeline.wave(b, b.lock.at + 100f))
        assertNull(BuildTimeline.wave(b, b.lock.at + 761f))
    }

    @Test
    fun aSkipFlashesTheSessionThatLeft() {
        val b = dial("skip").build!!
        assertEquals(1f, BuildTimeline.flipBrightness(b, 0f), 0f)
        assertEquals(2.2f, BuildTimeline.flipBrightness(b, 140f + 210f), .001f)
        assertEquals(1f, BuildTimeline.flipBrightness(b, 840f), 0f)
        assertEquals("–", BuildTimeline.words(b, 150f).big)
    }

    @Test
    fun theBuildBuzzesAsThePageDoes() {
        val repair = dial("build").build!!
        assertArrayEquals(longArrayOf(7), BuildBuzz.between(repair, 500f, 530f))
        assertArrayEquals(longArrayOf(12), BuildBuzz.between(repair, 750f, 770f))
        assertNull(BuildBuzz.between(repair, 530f, 750f))
        val full = dial("replan").build!!
        assertArrayEquals(longArrayOf(10, 40, 18), BuildBuzz.between(full, 2000f, 2040f))
    }

    @Test
    fun keyframesRunStraightBetweenOffsets() {
        assertEquals(0f, BuildTimeline.keyframes(0f, 0f to 0f, .5f to 1f, 1f to 0f), 0f)
        assertEquals(.5f, BuildTimeline.keyframes(.25f, 0f to 0f, .5f to 1f, 1f to 0f), 0f)
        assertEquals(.5f, BuildTimeline.keyframes(.75f, 0f to 0f, .5f to 1f, 1f to 0f), 0f)
        assertEquals(0f, BuildTimeline.keyframes(2f, 0f to 0f, .5f to 1f, 1f to 0f), 0f)
        assertNull(BuildTimeline.active(-1f, 0f, 10f))
        assertEquals(1f, BuildTimeline.held(50f, 0f, 10f), 0f)
    }

    @Test
    fun aHandleIsKeptOnTheStopWithItsMinute() {
        val d = dial()
        val h: DialHandle = d.handle("Work ends")
        val own = DragGeometry.stopOf(h, d.stops)
        assertEquals(1020, d.stops[own].m)
        assertEquals(h.a, d.stops[own].a, .001f)
    }
}
