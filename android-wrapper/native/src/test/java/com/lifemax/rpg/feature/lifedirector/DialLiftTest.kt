package com.lifemax.rpg.feature.lifedirector

import androidx.compose.ui.geometry.Offset
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.data.directorReply
import com.lifemax.rpg.feature.lifedirector.domain.ArcStyle
import com.lifemax.rpg.feature.lifedirector.domain.DialArc
import com.lifemax.rpg.feature.lifedirector.domain.DragModel
import com.lifemax.rpg.feature.lifedirector.domain.DragPoint
import com.lifemax.rpg.feature.lifedirector.domain.DragSource
import com.lifemax.rpg.feature.lifedirector.domain.OrderRow
import com.lifemax.rpg.feature.lifedirector.domain.Tap
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel
import com.lifemax.rpg.feature.lifedirector.presentation.dial.DialGeometry
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DialTouch
import com.lifemax.rpg.feature.lifedirector.presentation.drag.DragPhase
import com.lifemax.rpg.feature.lifedirector.presentation.drag.HoldEvent
import com.lifemax.rpg.feature.lifedirector.presentation.drag.HoldVerdict
import com.lifemax.rpg.feature.lifedirector.presentation.drag.PendingHold
import com.lifemax.rpg.feature.lifedirector.presentation.drag.Press
import com.lifemax.rpg.feature.lifedirector.presentation.drag.Start
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifting a session off the day from its segment on the dial (director.remove.v1): who a finger on
 * the dial belongs to (the handle, a hold on a session, a plain touch), what a hold turns into,
 * which segment is under the finger, and the shared controller lifting from the dial as it does
 * from a row. Nothing here draws or touches a screen.
 */
class DialLiftTest {
    private fun fixture(name: String) =
        JSONObject(javaClass.getResource("/fixtures/director-$name.json")!!.readText())

    private class Screen(val repo: WebLifeDirectorRepository, val commands: DirectorRecorder) {
        val vm = LifeDirectorViewModel(repo, commands).apply { opened() }
        val drag = vm.removal

        init {
            // a 1000 by 2000 px screen with 200 px of bars, 2.5 px to the dp
            drag.layout(1000f, 2000f, 150f, 200f, 2.5f)
        }

        private val today
            get() = repo.state.value!!.today!!

        val dial
            get() = today.dial

        fun arc(title: String) = dial.arcs.first { it.title == title }

        fun session(title: String): OrderRow =
            (today.ahead + today.earlier?.rows.orEmpty()).first { it.title == title }

        val onTrash
            get() = drag.target.value!!.centre

        val sent
            get() = commands.todays
    }

    private fun screen() =
        Screen(
            WebLifeDirectorRepository().apply { accept(fixture("remove"), true) },
            DirectorRecorder(),
        )

    private val slop = 8f
    private val down = DragPoint(100f, 100f)
    private val row = screen().session("Resistance Training")

    private fun hold(press: Press = Press.Lift(row)) = PendingHold(down, slop) { press }

    // Who a finger on the dial belongs to

    @Test
    fun aFingerOnAHandleDragsTheHandleWhateverIsUnderIt() {
        assertEquals(Start.Handle, DialTouch.start(onHandle = true, press = Press.Lift(row)))
        assertEquals(Start.Handle, DialTouch.start(onHandle = true, press = Press.Ignore))
        assertEquals(Start.Handle, DialTouch.start(onHandle = true, press = Press.Refuse("no")))
    }

    @Test
    fun aFingerOnASessionTheScreenCanAnswerForWaitsToLiftItOrRefuseIt() {
        assertEquals(Start.Hold, DialTouch.start(false, Press.Lift(row)))
        assertEquals(Start.Hold, DialTouch.start(false, Press.Refuse("It is already done.")))
    }

    @Test
    fun anyOtherFingerIsTheDialsPlainTouch() {
        assertEquals(Start.Plain, DialTouch.start(false, Press.Ignore))
    }

    // A hold in waiting

    @Test
    fun aFingerThatStaysPutLiftsTheSession() {
        val h = hold()
        // wobbling inside the slop is still staying put
        assertEquals(HoldVerdict.Waiting, h.on(HoldEvent.Moved(DragPoint(103f, 104f))))
        assertEquals(HoldVerdict.Waiting, h.on(HoldEvent.Moved(DragPoint(100f, 108f))))
        assertEquals(HoldVerdict.Lifts(row), h.on(HoldEvent.Elapsed))
    }

    @Test
    fun theHoldIsTheSameLengthAsARowsAndTheLiftTheSameBuzz() {
        assertEquals(350L, DragModel.LIFT_MS)
        assertTrue(DragModel.lifts(heldMs = 350L, movedPx = 0f, slopPx = slop))
        assertFalse(DragModel.lifts(heldMs = 349L, movedPx = 0f, slopPx = slop))
    }

    @Test
    fun aQuickTapIsStillATapOnTheSegment() {
        val h = hold()
        assertEquals(
            HoldVerdict.Tap(DragPoint(101f, 99f)),
            h.on(HoldEvent.Released(DragPoint(101f, 99f))),
        )
        // and nothing lifts afterwards
        assertEquals(HoldVerdict.Tap(DragPoint(101f, 99f)), h.on(HoldEvent.Elapsed))
    }

    @Test
    fun movingBeyondTheSlopBeforeTheHoldIsLongEnoughCancelsTheLift() {
        val h = hold()
        assertEquals(HoldVerdict.Yields, h.on(HoldEvent.Moved(DragPoint(100f, 109f))))
        // the lift stays cancelled, even if the finger comes back and the time then runs out
        assertEquals(HoldVerdict.Yields, h.on(HoldEvent.Moved(DragPoint(100f, 100f))))
        assertEquals(HoldVerdict.Yields, h.on(HoldEvent.Elapsed))
    }

    @Test
    fun theSlopIsMeasuredFromWhereTheFingerCameDownNotFromTheLastMove() {
        val h = hold()
        for (y in listOf(105f, 110f)) h.on(HoldEvent.Moved(DragPoint(100f, y)))
        assertEquals(HoldVerdict.Yields, h.on(HoldEvent.Elapsed))
    }

    @Test
    fun aSecondFingerOrATouchTheSystemTookCancelsTheLift() {
        val second = hold()
        assertEquals(HoldVerdict.Yields, second.on(HoldEvent.SecondFinger))
        assertEquals(HoldVerdict.Yields, second.on(HoldEvent.Elapsed))
        val taken = hold()
        assertEquals(HoldVerdict.Yields, taken.on(HoldEvent.Taken))
        assertEquals(HoldVerdict.Yields, taken.on(HoldEvent.Elapsed))
    }

    @Test
    fun aSessionThatCannotBeRemovedRefusesWithTheReasonOnlyOnceHeldLongEnough() {
        val h = hold(Press.Refuse("It is already done, so it stays."))
        assertEquals(HoldVerdict.Waiting, h.on(HoldEvent.Moved(DragPoint(101f, 101f))))
        assertEquals(
            HoldVerdict.Refuses("It is already done, so it stays."),
            h.on(HoldEvent.Elapsed),
        )
        // a quick tap on it is a tap: the sheet opens, as it always did
        assertTrue(hold(Press.Refuse("x")).on(HoldEvent.Released(down)) is HoldVerdict.Tap)
    }

    @Test
    fun whatTheHoldMeansIsAskedOnlyWhenItIsLongEnough() {
        var asked = 0
        val h =
            PendingHold(down, slop) {
                asked++
                Press.Ignore // the page changed its mind while the finger was held
            }
        h.on(HoldEvent.Moved(down))
        assertEquals(0, asked)
        assertEquals(HoldVerdict.Yields, h.on(HoldEvent.Elapsed))
        assertEquals(1, asked)
    }

    // The segment under the finger: the tap's own geometry

    private fun Screen.under(arc: DialArc, along: Float = .5f, radius: Float? = null): DialArc? {
        val r = radius ?: if (arc.outer) dial.radii.outer else dial.radii.inner
        val at = DialGeometry.point(r, arc.a0 + (arc.a1 - arc.a0) * along)
        return DialGeometry.arcAt(dial.arcs, dial.radii, at)
    }

    @Test
    fun theMiddleOfEachSessionSegmentFindsThatSession() {
        val s = screen()
        for (title in
            listOf(
                "No-scroll hour",
                "Resistance Training",
                "Full care day",
                "Focused Reading",
            )) assertEquals(title, s.under(s.arc(title))?.title)
    }

    @Test
    fun theEndsOfALongSegmentAndTheRingsEdgesStillFindIt() {
        val s = screen()
        val arc = s.arc("Resistance Training")
        assertEquals(arc, s.under(arc, along = .02f))
        assertEquals(arc, s.under(arc, along = .98f))
        // a touch a little to either side of the ring's middle is still on the segment
        assertEquals(arc, s.under(arc, radius = s.dial.radii.inner + 12f))
        assertEquals(arc, s.under(arc, radius = s.dial.radii.inner - 12f))
    }

    @Test
    fun aShortSegmentIsReachedFromJustBesideItAsATapReachesIt() {
        val s = screen()
        val short = s.arc("Full care day") // 2.4 degrees: about 4 units long
        val gap = DialGeometry.point(s.dial.radii.inner, short.a1 + .4f)
        assertEquals(short, DialGeometry.arcAt(s.dial.arcs, s.dial.radii, gap))
    }

    @Test
    fun touchesOffTheRingsOrBetweenSegmentsFindNothingToLift() {
        val s = screen()
        val centre = DialGeometry.point(30f, 10f)
        assertNull(DialGeometry.arcAt(s.dial.arcs, s.dial.radii, centre))
        val beyond = DialGeometry.point(s.dial.radii.outer + 60f, 200f)
        assertNull(DialGeometry.arcAt(s.dial.arcs, s.dial.radii, beyond))
        assertNull(DialGeometry.arcAt(s.dial.arcs, s.dial.radii, Offset(-20f, 500f)))
    }

    @Test
    fun theOuterRingAndTheMealsAndAppointmentsOnTheInnerRingAreFoundButNeverLift() {
        val s = screen()
        for (title in listOf("Work", "Sleep", "Commute", "Breakfast", "Dinner", "Dentist")) {
            val arc = s.arc(title)
            assertEquals(title, s.under(arc)?.title)
            assertTrue(arc.style != ArcStyle.SESSION)
            assertEquals(title, Press.Ignore, s.drag.press(arc))
        }
    }

    // What a hold on a segment means, from the page's own flags

    @Test
    fun aSessionSegmentLiftsWhenItsRowSaysItCanBeRemoved() {
        val s = screen()
        val press = s.drag.press(s.arc("Resistance Training"))
        assertEquals(s.session("Resistance Training"), (press as Press.Lift).row)
        assertTrue(s.drag.press(s.arc("Focused Reading")) is Press.Lift)
    }

    @Test
    fun aSegmentTheFlagsRefuseGivesTheSameReasonItsRowGives() {
        val s = screen()
        for (title in listOf("Full care day", "No-scroll hour")) {
            val why = s.session(title).removal!!.reason
            assertTrue(why.isNotBlank())
            assertEquals(title, Press.Refuse(why), s.drag.press(s.arc(title)))
            assertEquals(s.drag.press(s.session(title).key), s.drag.press(s.arc(title)))
        }
    }

    @Test
    fun aSegmentWithoutAnAnsweringRowDoesNotLift() {
        val s = screen()
        val arc = s.arc("Resistance Training")
        assertEquals(Press.Ignore, s.drag.press(arc.copy(tap = Tap("inst", "nobody"))))
        assertEquals(Press.Ignore, s.drag.press(arc.copy(tap = Tap("inst", ""))))
        assertEquals(Press.Ignore, s.drag.press(arc.copy(tap = null)))
    }

    @Test
    fun aPageThatDoesNotOfferRemovalLiftsNothingFromTheDial() {
        val s =
            Screen(
                WebLifeDirectorRepository().apply { accept(fixture("today"), true) },
                DirectorRecorder(),
            )
        assertTrue(
            s.dial.arcs
                .filter { it.style == ArcStyle.SESSION }
                .all { s.drag.press(it) == Press.Ignore }
        )
    }

    @Test
    fun nothingLiftsFromTheDialWhileAnotherThingHoldsTheScreen() {
        val s = screen()
        s.vm.openSession(s.session("Resistance Training").tap!!.id)
        assertEquals(Press.Ignore, s.drag.press(s.arc("Resistance Training")))
    }

    // The shared controller, lifting from the dial

    private fun Screen.lift(title: String = "Resistance Training") =
        drag.lift(session(title), DragPoint(300f, 600f), drag.dialGrab(), DragSource.DIAL)

    @Test
    fun theCardIsHeldByItsMiddleUnderTheFinger() {
        val s = screen()
        // the card is the list's width less its 12 dp sides: 1000 - 2 * 30 px
        assertEquals(470f, s.drag.dialGrab().x, 0f)
        assertTrue(s.drag.dialGrab().y > 0f)
    }

    @Test
    fun aSessionLiftedFromTheDialIsHeldLikeOneLiftedFromARow() {
        val s = screen()
        s.lift()
        val held = s.drag.held.value!!
        assertEquals(DragPhase.HELD, held.phase)
        assertEquals(DragSource.DIAL, held.source)
        assertEquals(s.session("Resistance Training"), held.row)
        assertTrue("arming is reported once", s.drag.move(s.onTrash))
        assertTrue(s.drag.armed.value)
        assertTrue("leaving is reported once", s.drag.move(DragPoint(100f, 300f)))
        // one controller: nothing else lifts while this is held
        assertEquals(Press.Ignore, s.drag.press(s.arc("Focused Reading")))
        assertEquals(Press.Ignore, s.drag.press(s.session("Focused Reading").key))
    }

    @Test
    fun aSessionLiftedFromARowStillSaysItCameFromARow() {
        val s = screen()
        s.drag.lift(s.session("Resistance Training"), DragPoint(300f, 900f), DragPoint(40f, 30f))
        assertEquals(DragSource.ROW, s.drag.held.value!!.source)
    }

    @Test
    fun theListDoesNotScrollForACardLiftedFromTheDial() {
        val s = screen()
        s.lift()
        s.drag.move(DragPoint(300f, 160f))
        assertEquals(0f, s.drag.scrollSpeed(), 0f)
        s.drag.move(DragPoint(300f, 1400f))
        assertEquals(0f, s.drag.scrollSpeed(), 0f)
    }

    @Test
    fun droppingADialSessionOnTheTrashSendsTheSameSkipWithAnOperationId() {
        val s = screen()
        s.lift()
        s.drag.move(s.onTrash)
        s.drag.release()
        assertEquals(DragPhase.DROPPING, s.drag.held.value!!.phase)
        val (name, args) = s.sent.single()
        assertEquals("skip", name)
        assertEquals(s.session("Resistance Training").tap!!.id, args["id"])
        assertTrue((args["op"] as String).isNotEmpty())
    }

    @Test
    fun theRemovalOfADialSessionOffersUndoAsARowsDoes() {
        val s = screen()
        s.lift()
        s.drag.move(s.onTrash)
        s.drag.release()
        val reply =
            directorReply(
                JSONObject(fixture("replies-remove").getJSONObject("skip").toString())
                    .put("command", "today")
                    .put("requestId", "today-1")
            )
        s.vm.onReply(reply)
        assertNull(s.drag.held.value)
        assertEquals("Skipped · 1 re-placed", s.vm.ui.value.undo!!.text)
        s.drag.undo()
        assertEquals("unskip", s.sent.last().first)
    }

    @Test
    fun letGoAwayFromTheTrashSendsNothingAndTheCardGoesBackToItsSegment() {
        val s = screen()
        s.lift()
        s.drag.move(DragPoint(200f, 700f))
        s.drag.release()
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
        assertEquals(DragSource.DIAL, s.drag.held.value!!.source)
        assertTrue(s.sent.isEmpty())
        s.drag.settled()
        assertNull(s.drag.held.value)
        assertTrue(s.drag.press(s.arc("Resistance Training")) is Press.Lift)
    }

    @Test
    fun cancellingADialLiftOverTheTrashRemovesNothing() {
        val s = screen()
        s.lift()
        s.drag.move(s.onTrash)
        s.drag.cancel() // a second finger, Back, or the touch taken away
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
        assertFalse(s.drag.armed.value)
        s.drag.release()
        assertTrue(s.sent.isEmpty())
    }

    @Test
    fun theSegmentsSessionLeavingThePlanWhileHeldPutsTheCardBack() {
        val s = screen()
        s.lift()
        val gone = fixture("remove")
        val ahead = gone.getJSONObject("today").getJSONObject("rows").getJSONArray("ahead")
        val kept = org.json.JSONArray()
        for (i in 0 until ahead.length()) if (
            ahead.getJSONObject(i).getString("title") != "Resistance Training"
        )
            kept.put(ahead.getJSONObject(i))
        gone.getJSONObject("today").getJSONObject("rows").put("ahead", kept)
        gone.put("sequence", gone.getLong("sequence") + 1)
        s.repo.accept(gone)
        s.vm.projected()
        assertEquals(DragPhase.RETURNING, s.drag.held.value!!.phase)
    }

    // TalkBack's action on a segment

    @Test
    fun removeFromTodayOnASegmentRemovesThatSessionWithNoDrag() {
        val s = screen()
        s.drag.remove(s.arc("Resistance Training"))
        val (name, args) = s.sent.single()
        assertEquals("skip", name)
        assertEquals(s.session("Resistance Training").tap!!.id, args["id"])
    }

    @Test
    fun removeFromTodayOnASegmentTheFlagsRefuseOrThatIsNotASessionDoesNothing() {
        val s = screen()
        s.drag.remove(s.arc("Full care day"))
        s.drag.remove(s.arc("No-scroll hour"))
        s.drag.remove(s.arc("Dinner"))
        s.drag.remove(s.arc("Work"))
        assertTrue(s.sent.isEmpty())
    }
}
