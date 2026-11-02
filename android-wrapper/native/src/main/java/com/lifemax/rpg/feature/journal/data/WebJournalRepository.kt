package com.lifemax.rpg.feature.journal.data

import com.lifemax.rpg.feature.journal.domain.JournalFrame
import com.lifemax.rpg.feature.journal.domain.JournalRepository
import com.lifemax.rpg.feature.journal.domain.LockStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * The Journal's frames as the screen may hold them (CONTRACT A35, plan §3.2.4). On the main thread.
 * - **Order.** A frame from another page session is taken only on an explicit open; one no newer
 *   than the frame held is ignored (A23). Malformed data throws and the last good frame is kept.
 * - **Withheld.** The page's withheld frame becomes [JournalFrame.Locked], which has no field for
 *   journal data, so the open frame it replaces is gone.
 * - **At rest** ([rest]), whatever the lock: a screen that stops being drawn keeps only the chrome.
 * - **On stop** ([stop]), a showing frame with the lock on is locked here at once, with no round
 *   trip, and that page session's open frames are refused until its withheld frame (the page's own
 *   relock) has arrived. A refused open frame still shows the card, made from that frame's chrome,
 *   so a screen that rested meanwhile is never left blank. The gate is never set while the lock is
 *   off, and a new page session (a reload) clears it, so a reload cannot leave the card stuck.
 * - **The strip.** An open frame whose list has the same rev as the one held keeps the held list
 *   object, so the strip is not rebuilt on a refresh.
 */
class WebJournalRepository : JournalRepository {
    private val mutable = MutableStateFlow<JournalFrame?>(null)
    override val state = mutable.asStateFlow()

    /** The page session whose open frames wait for its relock, set on stop with the lock on. */
    var relockFor: String? = null
        private set

    /** The newest frame taken, open or withheld, which orders the next. */
    private var newest: Pair<String, Long>? = null

    fun accept(j: JSONObject, establishSession: Boolean = false) {
        val frame = decodeFrame(j)
        val chrome = frame.chrome
        val held = newest
        if (held != null && held.first != chrome.session && !establishSession) return
        if (held?.first == chrome.session && chrome.sequence <= held.second) return
        newest = chrome.session to chrome.sequence
        if (relockFor != null && relockFor != chrome.session) relockFor = null
        mutable.value =
            when (frame) {
                is DecodedFrame.Withheld -> {
                    relockFor = null
                    JournalFrame.Locked(chrome, frame.status, frame.notice)
                }
                is DecodedFrame.Open ->
                    if (relockFor == chrome.session)
                        JournalFrame.Locked(chrome, LockStatus.Idle, frame.notice)
                    else JournalFrame.Open(chrome, reuseList(frame), frame.notice)
            }
    }

    /** The screen stopped being drawn: keep only the chrome. */
    fun rest() {
        val now = mutable.value ?: return
        if (now !is JournalFrame.Resting) mutable.value = JournalFrame.Resting(now.chrome)
    }

    /**
     * The app stopped while the screen shows. With the lock on, the open Journal turns into its
     * card now, and the session's open frames wait for its relock. Answers whether it locked.
     */
    fun stop(): Boolean {
        val now = mutable.value as? JournalFrame.Open ?: return false
        if (!now.chrome.lock.on) return false
        relockFor = now.chrome.session
        mutable.value = JournalFrame.Locked(now.chrome, LockStatus.Idle, null)
        return true
    }

    private fun reuseList(frame: DecodedFrame.Open) =
        (mutable.value as? JournalFrame.Open)
            ?.journal
            ?.list
            ?.takeIf {
                it.rev == frame.journal.list.rev &&
                    mutable.value?.chrome?.session == frame.chrome.session
            }
            ?.let { frame.journal.copy(list = it) } ?: frame.journal
}
