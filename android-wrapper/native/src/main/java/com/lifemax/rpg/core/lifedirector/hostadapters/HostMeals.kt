package com.lifemax.rpg.core.lifedirector.hostadapters

import com.lifemax.rpg.core.economy.jsRound
import com.lifemax.rpg.core.lifedirector.CalendarMath
import com.lifemax.rpg.core.lifedirector.EcmaTime
import com.lifemax.rpg.core.lifedirector.ZoneNames
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

/**
 * Life Director's link to Smart Diet (HA-22, HA-23): a meal block names its calendar date and meal
 * slot, never a recipe, and the recipe is looked up again on every read (50-smartdiet.js:211-237,
 * `sdDirectorDay`, `sdDirectorSlot`, `sdDirectorMeal`; logic.js:1076-1081 for the block's
 * reference). The device zone dates a reference without a day.
 */
internal object HostMeals {
    private val DAY = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
    private val SPACES = Regex("[${HostTexts.SPACE}-]+")
    private val NOON: LocalTime = LocalTime.NOON

    /** `sdDirectorSlot`'s table. */
    val SLOTS: Map<String, String> =
        mapOf(
            "breakfast" to "breakfast",
            "lunch" to "lunch",
            "dinner" to "dinner",
            "snack" to "snack",
            "snacks" to "snack",
            "before_training" to "pre_workout",
            "pre_workout" to "pre_workout",
            "after_training" to "post_workout",
            "post_workout" to "post_workout",
        )

    /** `sdDirectorDay(ref)`: the calendar date a reference names, or null. */
    fun day(ref: MealRef, device: ZoneId): String? {
        val day = ref.day
        if (!day.isNullOrEmpty()) {
            if (!DAY.matches(day)) return null
            // new Date(day + 'T12:00:00') rolls an impossible date over; the fields then differ
            val date =
                runCatching {
                        LocalDate.of(
                            day.substring(0, 4).toInt(),
                            day.substring(5, 7).toInt(),
                            day.substring(8, 10).toInt(),
                        )
                    }
                    .getOrNull() ?: return null
            val local = LocalDateTime.of(date, NOON)
            return if (shiftedDate(local, device) == date) day else null
        }
        val epoch = ref.startEpoch
        if (!epoch.isFinite() || epoch <= 0) return null
        val ms = EcmaTime.timeClip(epoch * 1000.0)
        if (ms.isNaN()) return null
        val local = Instant.ofEpochMilli(ms.toLong()).atZone(device).toLocalDate()
        return local.year.toString() +
            "-" +
            local.monthValue.toString().padStart(2, '0') +
            "-" +
            local.dayOfMonth.toString().padStart(2, '0')
    }

    /** The device's date at a local time, moved past a clock change that skips it. */
    private fun shiftedDate(local: LocalDateTime, device: ZoneId): LocalDate {
        val rules = device.rules
        if (rules.getValidOffsets(local).isNotEmpty()) return local.toLocalDate()
        val gap = rules.getTransition(local) ?: return local.toLocalDate()
        return local.plus(gap.duration).toLocalDate()
    }

    /** `sdDirectorSlot(ref)`: the slot named by the reference's slot, else its title. */
    fun slot(ref: MealRef): String? {
        val name = ref.slot?.takeIf { it.isNotEmpty() } ?: ref.title.orEmpty()
        val key = SPACES.replace(HostTexts.trim(name).lowercase(Locale.ROOT), "_")
        return SLOTS[key]
    }

    /** `sdDirectorMeal(ref)`. */
    fun meal(ref: MealRef?, device: ZoneId, meals: MealDays): ScheduledMeal? {
        ref ?: return null
        val day = day(ref, device) ?: return null
        val slot = slot(ref) ?: return null
        val d = meals.day(day) ?: return null
        val i = d.items.indexOfFirst { it.slot == slot }
        if (i < 0) return null
        val it = d.items[i]
        return ScheduledMeal(day, i, slot, it.name, jsRound(it.kcal), it.logged, it.skipped)
    }

    /** logic.js:1076-1078 `scheduledMealRef(b)`: dated by the plan zone at the meal's start. */
    fun refOf(block: MealBlock, zoneName: String): HostAnswer<MealRef> {
        val zone =
            ZoneNames.resolve(zoneName)
                ?: return HostAnswer.Refused(HostRefusal.UNKNOWN_ZONE, "zone")
        val start = block.mealStartEpoch?.takeIf { it != 0.0 && !it.isNaN() } ?: block.startEpoch
        val p =
            QuestReading.parts(start, zone)
                ?: return HostAnswer.Refused(HostRefusal.INVALID_TIME, "start")
        return HostAnswer.Answer(MealRef(block.title, null, CalendarMath.key(p.date), start))
    }
}
