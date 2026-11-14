package com.lifemax.rpg.feature.habits

import com.lifemax.rpg.core.data.PAGE_TOKENS
import com.lifemax.rpg.feature.habits.data.LocalHabitsRepository
import com.lifemax.rpg.feature.habits.domain.HabitsLoad
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.HabitsRules
import com.lifemax.rpg.feature.habits.domain.HabitsSave
import com.lifemax.rpg.feature.habits.domain.HabitsStorage
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.json.JSONObject

/**
 * The habits file's stand-in for tests: it keeps the save in memory, and can be told to fail a
 * write.
 */
class MemoryHabitsStorage(
    var stored: HabitsSave = HabitsSave(),
    private val opened: HabitsLoad? = null,
) : HabitsStorage {
    var failing = false
    var writes = 0

    override fun load(): HabitsLoad = opened ?: HabitsLoad.Ready(stored)

    override fun save(save: HabitsSave): Boolean {
        if (failing) return false
        stored = save
        writes++
        return true
    }
}

/** A clock a test sets: 2026-03-02 14:00 UTC until moved. */
class TestClock(
    var now: ZonedDateTime = ZonedDateTime.of(2026, 3, 2, 14, 0, 0, 0, ZoneOffset.UTC)
) : () -> ZonedDateTime {
    override fun invoke(): ZonedDateTime = now

    val today: String
        get() = now.toLocalDate().toString()

    fun tomorrow() {
        now = now.plusDays(1)
    }
}

/**
 * The states the screen is tested and photographed in, built by the rules themselves on a fixed day
 * (2026-03-02):
 * - `city`: three foundations. After coffee (reward: One episode): Read 10 pages (gold, 71 reps,
 *   laid today at its stretch size, paired), Journal 5 min (stone, laid today after a missed day:
 *   the gold seam), Stretch 10 min (clay, one missed day: a crack), Meditate (glass, two missed
 *   days: a wobble). At lights out: Phone in bed (being broken, 9 of 14 floors left), Late snacks
 *   (cleared: a tree), Lay out clothes (clay). After lunch: empty.
 * - `done`: the first stack finished (its reward can be claimed); `claimed`: and claimed.
 * - `rough`: the city on a rough day. `tall`: one foundation with nine habits of every material.
 */
object HabitsScenarios {
    const val TODAY = "2026-03-02"

    /** The page's surroundings, as 50-native-habits.js sends them. */
    fun surroundings(
        sequence: Long = 1,
        light: Boolean = false,
        tokens: JSONObject? = null,
        reducedMotion: Boolean = false,
    ): JSONObject =
        JSONObject()
            .put("protocol", 1)
            .put("webSessionId", "habits-test")
            .put("sequence", sequence)
            .put("v", 1)
            .put("reducedMotion", reducedMotion)
            .put("haptics", JSONObject.NULL)
            .put("className", "Scholar")
            .put(
                "tokens",
                tokens ?: JSONObject().also { t -> PAGE_TOKENS.forEach { t.put(it, 0xFF000000L) } },
            )
            .put("light", light)

    /**
     * [reps] laid on the days before today, the last [gap] of them left out (missed); born [born]
     * days ago.
     */
    private fun HabitsSave.past(id: String, reps: Int, gap: Int, born: Int): HabitsSave {
        var log = log
        for (i in 1..reps) {
            val day = HabitsRules.addDays(TODAY, -(i + gap))
            log = log + (day to (log[day].orEmpty() + (id to 2)))
        }
        return copy(
            log = log,
            items =
                items.map {
                    if (it.id == id) it.copy(born = HabitsRules.addDays(TODAY, -born)) else it
                },
        )
    }

    private fun HabitsSave.stack(cue: String) = HabitsRules.addStack(this, cue)!!

    private fun HabitsSave.habit(
        stack: String,
        name: String,
        small: String = "",
        fade: Boolean = false,
        floors: Int = 0,
        size: Int = 1,
    ) =
        HabitsRules.addHabit(this, TODAY, stack, name, small, fade = fade, floors = floors)!!.let {
            (s, id) ->
            (if (fade) s else HabitsRules.resize(s, id, size)!!) to id
        }

    fun city(): HabitsSave {
        var (s, coffee) = HabitsSave().stack("After coffee")
        val (s2, night) = s.stack("At lights out")
        s = s2.stack("After lunch").first
        val read =
            s.habit(coffee, "Read 10 pages", "Read 1 page", size = 2).also { s = it.first }.second
        val journal = s.habit(coffee, "Journal 5 min", "One line").also { s = it.first }.second
        val stretch = s.habit(coffee, "Stretch 10 min", "Touch toes").also { s = it.first }.second
        val sit =
            s.habit(coffee, "Meditate", "Three breaths", size = 0).also { s = it.first }.second
        val phone =
            s.habit(night, "Phone in bed", fade = true, floors = 14).also { s = it.first }.second
        val snacks =
            s.habit(night, "Late snacks", fade = true, floors = 3).also { s = it.first }.second
        val clothes = s.habit(night, "Lay out clothes", "Pick a shirt").also { s = it.first }.second
        s = HabitsRules.editStack(s, coffee, treat = "One episode")!!
        s = HabitsRules.rename(s, read, listOf("Read 1 page", "Read 10 pages", "Read 30 min"))!!
        s = HabitsRules.pair(s, read, "Morning coffee")!!
        s = HabitsRules.pair(s, phone, "A book on the pillow")!!
        s =
            s.past(read, 70, 0, 84)
                .past(journal, 35, 1, 60)
                .past(stretch, 12, 1, 30)
                .past(sit, 3, 2, 12)
                .past(phone, 5, 0, 20)
                .past(snacks, 3, 0, 9)
                .past(clothes, 20, 0, 40)
        s = HabitsRules.lay(s, TODAY, read)!!
        return HabitsRules.lay(s, TODAY, journal)!!
    }

    fun done(): HabitsSave =
        city().let { s ->
            s.items
                .filter { it.stack == s.stacks[0].id }
                .fold(s) { acc, it -> HabitsRules.lay(acc, TODAY, it.id) ?: acc }
        }

    fun claimed(): HabitsSave = done().let { HabitsRules.claim(it, TODAY, it.stacks[0].id)!! }

    fun rough(): HabitsSave = HabitsRules.rough(city(), TODAY, true)!!

    fun tall(): HabitsSave {
        var (s, wake) = HabitsSave(who = "an athlete who reads").stack("After waking up")
        listOf(
                Triple("Make the bed", 90 to 0, 2),
                Triple("Glass of water", 70 to 0, 1),
                Triple("Ten push-ups", 44 to 0, 1),
                Triple("Cold shower", 31 to 1, 2),
                Triple("Plan the day", 18 to 0, 1),
                Triple("Read 10 pages", 11 to 1, 0),
                Triple("Vitamins", 6 to 0, 1),
                Triple("Journal", 2 to 2, 1),
                Triple("Language app", 0 to 0, 2),
            )
            .forEachIndexed { i, (name, past, size) ->
                val (next, id) = s.habit(wake, name, size = size)
                s = next.past(id, past.first, past.second, past.first + past.second + 2)
                if (i < 3) s = HabitsRules.lay(s, TODAY, id)!!
            }
        return HabitsRules.editStack(s, wake, treat = "Good coffee")!!
    }

    fun save(name: String): HabitsSave =
        when (name) {
            "empty" -> HabitsSave()
            "city" -> city()
            "done" -> done()
            "claimed" -> claimed()
            "rough" -> rough()
            "tall" -> tall()
            else -> error("No scenario $name")
        }

    /** A repository over [storage] at [clock], with the page's surroundings in. */
    fun repository(
        storage: HabitsStorage,
        clock: TestClock = TestClock(),
        surroundings: JSONObject = surroundings(),
    ): LocalHabitsRepository =
        LocalHabitsRepository(storage, clock).apply { accept(surroundings, true) }

    /** What the screen draws for scenario [name]. */
    fun projection(
        name: String,
        clock: TestClock = TestClock(),
        surroundings: JSONObject = surroundings(),
    ): HabitsProjection =
        repository(MemoryHabitsStorage(save(name)), clock, surroundings).state.value!!
}
