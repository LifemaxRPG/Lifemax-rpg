package com.lifemax.rpg.feature.habits.data

import com.lifemax.rpg.core.data.pageTokens
import com.lifemax.rpg.core.designsystem.feedback.HapticScale
import com.lifemax.rpg.feature.habits.domain.HabitsCommands
import com.lifemax.rpg.feature.habits.domain.HabitsEngine
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.HabitsReply
import com.lifemax.rpg.feature.habits.domain.HabitsRepository
import com.lifemax.rpg.feature.habits.domain.HabitsStorage
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * What the page sends a Habits screen (50-native-habits.js): nothing of Habits, only its
 * surroundings. The palette and theme, the app's reduce-motion setting and haptic strength, the
 * character's class name.
 */
data class HabitsSurroundings(
    val session: String,
    val sequence: Long,
    val reducedMotion: Boolean,
    val haptics: HapticScale?,
    val className: String,
    val tokens: Map<String, Long>,
    val light: Boolean,
)

/**
 * Strict: a missing key or a value of the wrong kind rejects the surroundings, and the last good
 * ones stay.
 */
fun JSONObject.toHabitsSurroundings(): HabitsSurroundings {
    fun JSONObject.bool(key: String) =
        get(key) as? Boolean ?: throw IllegalArgumentException("$key is not yes or no")
    fun JSONObject.number(key: String) =
        get(key) as? Number ?: throw IllegalArgumentException("$key is not a number")
    require(number("protocol").toInt() == 1 && number("v").toInt() == 1) {
        "Unsupported habits protocol"
    }
    val session = get("webSessionId") as? String ?: throw IllegalArgumentException("No session")
    val sequence = number("sequence").toLong()
    require(session.isNotBlank() && sequence > 0)
    require(has("haptics")) { "Missing haptics" }
    val haptics =
        if (isNull("haptics")) null
        else
            getJSONObject("haptics").let { h ->
                HapticScale(
                        h.number("dm").toDouble(),
                        h.number("add").toDouble(),
                        h.number("amp").toInt(),
                        h.number("min").toLong(),
                        h.number("max").toLong(),
                    )
                    .also {
                        require(it.amp in 1..255 && it.max in 1..1000 && it.min in 0..it.max) {
                            "The haptic strength is out of range"
                        }
                    }
            }
    return HabitsSurroundings(
        session,
        sequence,
        bool("reducedMotion"),
        haptics,
        get("className") as? String ?: throw IllegalArgumentException("className is not words"),
        getJSONObject("tokens").pageTokens(),
        bool("light"),
    )
}

/**
 * Habits' data for its screen: the engine over the habits file, the phone's clock ([clock]: its
 * local date and time, which a test sets), and the page's surroundings. Every command runs here, on
 * the phone, and the screen's state follows at once. Nothing is published before the first
 * surroundings arrive (the screen cannot be drawn without a palette). [accept] also re-reads the
 * clock, so the day and the sky follow the page's refreshes.
 */
class LocalHabitsRepository(
    storage: HabitsStorage,
    private val clock: () -> ZonedDateTime = ZonedDateTime::now,
) : HabitsRepository, HabitsCommands {
    private val engine = HabitsEngine(storage, ::today)
    private val mutable = MutableStateFlow<HabitsProjection?>(null)
    override val state = mutable.asStateFlow()
    private var around: HabitsSurroundings? = null

    /**
     * The phone's local day: read afresh each time, so a clock or zone change is followed at the
     * next reading.
     */
    private fun today(): String = clock().toLocalDate().toString()

    /**
     * The page's surroundings: an update older than the one held is ignored, another page session
     * only on an open.
     */
    fun accept(j: JSONObject, establishSession: Boolean = false) {
        val next = j.toHabitsSurroundings()
        val old = around
        if (old != null && old.session != next.session && !establishSession) return
        if (old?.session == next.session && next.sequence <= old.sequence) return
        around = next
        publish()
    }

    private fun publish() {
        val a = around ?: return
        val time = clock()
        mutable.value =
            engine.project(
                time.hour + time.minute / 60f,
                a.className,
                a.reducedMotion,
                a.haptics,
                a.tokens,
                a.light,
            )
    }

    private fun run(reply: HabitsReply): HabitsReply = reply.also { publish() }

    override fun lay(id: String) = run(engine.lay(id))

    override fun undo(id: String) = run(engine.undo(id))

    override fun claim(stackId: String) = run(engine.claim(stackId))

    override fun rough(on: Boolean) = run(engine.rough(on))

    override fun move(id: String, dir: Int) = run(engine.move(id, dir))

    override fun size(id: String, size: Int) = run(engine.size(id, size))

    override fun pair(id: String, text: String) = run(engine.pair(id, text))

    override fun names(id: String, names: List<String>) = run(engine.names(id, names))

    override fun restack(id: String, stackId: String) = run(engine.restack(id, stackId))

    override fun retire(id: String) = run(engine.retire(id))

    override fun who(text: String) = run(engine.who(text))

    override fun addStack(cue: String) = run(engine.addStack(cue))

    override fun editStack(id: String, cue: String, treat: String) =
        run(engine.editStack(id, cue, treat))

    override fun removeStack(id: String) = run(engine.removeStack(id))

    override fun addHabit(
        stackId: String,
        fade: Boolean,
        name: String,
        small: String,
        floors: Int,
        big: String,
        pair: String,
    ) = run(engine.addHabit(stackId, fade, name, small, floors, big, pair))
}
