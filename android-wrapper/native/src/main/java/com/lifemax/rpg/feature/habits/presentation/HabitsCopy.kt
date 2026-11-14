package com.lifemax.rpg.feature.habits.presentation

import com.lifemax.rpg.feature.habits.domain.HabitBlock
import com.lifemax.rpg.feature.habits.domain.HabitMaterial
import com.lifemax.rpg.feature.habits.domain.HabitStack
import com.lifemax.rpg.feature.habits.domain.HabitsProjection
import com.lifemax.rpg.feature.habits.domain.PrimaryAction

/**
 * The screen's words. Habits has no page version, so they live here, in one place; what the page
 * says (a refusal, a save that failed, a block that turned to stone) is shown as the page words it.
 */
internal object HabitsCopy {
    const val STACK_HERE = "+ Add a floor"
    const val ENJOYED = "Enjoyed"
    const val BECOMING = "BECOMING"
    const val WHO_UNSAID = "Tap to say who"
    const val TODAY = "today"
    const val STREET = "Street"
    const val BUILDING = "Building"
    const val STREET_LABEL = "Zoom out to the whole street"
    const val BUILDING_LABEL = "Back to one building"
    const val NEW_FOUNDATION = "+ Foundation"
    const val ROUGH_OFF = "Rough day?"
    const val ROUGH_OFF_HINT = "Shrink today's floors to two minutes"
    const val ROUGH_ON = "Rough day is on"
    const val ROUGH_ON_HINT = "Floors are two minutes today"
    const val LAPSE = "Time-lapse"
    const val LAPSE_TODAY = "Today"
    const val SCENE = "Your habit street"
    const val CANCEL = "Cancel"
    const val BACK = "Back"
    const val CLOSE = "Close"
    const val SAVE = "Save"
    const val EMPTY_HINT =
        "A foundation is something you already do every day. Habits stack on top of it."
    val SIZES = listOf("2 minutes", "Normal", "Stretch")

    fun weeksAgo(weeks: Int, max: Int): String =
        when (weeks) {
            0 -> LAPSE_TODAY
            1 -> "1 week ago"
            else -> "$weeks weeks ago" + if (weeks == max) " (as far as it goes)" else ""
        }

    /**
     * Under a block's name: how many times it has been done, or how a habit being broken stands.
     */
    fun count(b: HabitBlock): String =
        if (b.fade) "${b.reps} ${if (b.reps == 1) "day" else "days"} clean · ${b.left} to go"
        else "${b.reps} ${if (b.reps == 1) "rep" else "reps"}"

    fun material(m: HabitMaterial): String =
        when (m) {
            HabitMaterial.GLASS -> "Glass · brand new"
            HabitMaterial.CLAY -> "Clay · taking shape"
            HabitMaterial.STONE -> "Stone · nearly automatic"
            HabitMaterial.GOLD -> "Gold · runs itself"
            HabitMaterial.FADE -> "Coming down"
        }

    /** What a block's dials say about its state, if anything needs saying. */
    fun coach(b: HabitBlock): String =
        when {
            b.cleared -> "This one is gone. A tree stands where it was."
            b.done -> if (b.scar) "You missed this once and came back. The gold seam stays." else ""
            b.miss >= 2 ->
                "Missed twice. It is probably too big for a tired day. Turn the size down and keep the rep."
            b.miss == 1 -> "Missed yesterday. One crack is nothing. Lay the small version today."
            else -> ""
        }

    /** The big button: its small line, its main line. */
    fun primary(action: PrimaryAction, combo: Int): Pair<String, String> =
        when (action) {
            PrimaryAction.FirstFoundation -> "Start here" to "Lay your first foundation"
            is PrimaryAction.FirstHabit ->
                "Empty foundation" to "Add a floor to ${action.stack.cue.lowercase()}"
            is PrimaryAction.Lay ->
                ("Light up the next floor" + if (combo > 0) " · combo ×${combo + 1}" else "") to
                    action.block.name
            is PrimaryAction.Knock ->
                "Stayed clean today" to "Take a floor down: ${action.block.name}"
            is PrimaryAction.Claim ->
                "Building done · rooftop unlocked" to "Claim: ${action.stack.treat}"
            is PrimaryAction.GoTo ->
                "This building is done" to "Go to ${action.stack.cue.lowercase()}"
            PrimaryAction.Complete -> "Street complete" to "Every floor is lit today"
        }

    /** What TalkBack says of the scene: the building in front, and where it stands today. */
    fun describe(p: HabitsProjection, stack: HabitStack?, city: Boolean, weeks: Int): String {
        if (p.stacks.isEmpty()) return "$SCENE. Nothing built yet."
        if (city)
            return "$SCENE, whole street, ${weeksAgo(weeks, p.rules.weeks).lowercase()}: " +
                p.stacks.joinToString(", ") { it.cue }
        val s = stack ?: return SCENE
        val laid = s.blocks.count { it.done }
        val next = s.nextBlock?.let { ". Next: ${it.name}" } ?: ". Done for today"
        return "${s.cue}: $laid of ${s.blocks.count { !it.cleared }} floors lit today$next. Building ${p.stacks.indexOf(s) + 1} of ${p.stacks.size}."
    }

    /** A sheet's words: title, lead, the labels and hints of its fields, its buttons. */
    class SheetWords(
        val title: String,
        val lead: String = "",
        val labels: List<String> = emptyList(),
        val hints: List<String> = emptyList(),
        val go: String = SAVE,
        val danger: String = "",
    )

    fun sheet(sheet: HabitsSheet, p: HabitsProjection): SheetWords {
        val block = p.block(sheet.target)
        val stack = p.stack(sheet.target)
        val fade = block?.fade == true
        return when (sheet.kind) {
            SheetKind.WHO ->
                SheetWords(
                    "Who are you becoming?",
                    "Every floor you light is a vote for this person.",
                    listOf("I am becoming"),
                    listOf("a reader, an athlete"),
                )
            SheetKind.NEW_STACK ->
                SheetWords(
                    "New foundation",
                    "Something you already do every day. Habits stack on top of it.",
                    listOf("When"),
                    listOf("After coffee"),
                    "Lay it",
                )
            SheetKind.EDIT_STACK ->
                SheetWords(
                    "Foundation",
                    "Something you already do every day. Habits stack on top of it.",
                    listOf("When", "Rooftop reward (unlocks when the stack is done)"),
                    listOf("After coffee", "One episode"),
                    danger = if (stack?.removable == true) "Remove" else "",
                )
            SheetKind.NEW_HABIT ->
                if (sheet.fade)
                    SheetWords(
                        "A habit to tear down",
                        "${stack?.cue.orEmpty()}, I will not…",
                        listOf(
                            "The habit you are breaking",
                            "Clean days to bring it down",
                            "What you will do instead",
                        ),
                        listOf("Phone in bed", p.limits.floors.toString(), "A book on the pillow"),
                        "Stack it",
                    )
                else
                    SheetWords(
                        "New habit",
                        "${stack?.cue.orEmpty()}, I will…",
                        listOf("The habit", "Its two-minute version", "Its stretch version"),
                        listOf("Read 10 pages", "Read 1 page", "Read 30 minutes"),
                        "Stack it",
                    )
            SheetKind.NAMES ->
                SheetWords(
                    "Name each size",
                    "The same habit at three sizes. On a hard day the two-minute one still counts.",
                    listOf("Two minutes", "Normal", "Stretch"),
                    listOf("", "", ""),
                )
            SheetKind.PAIR ->
                if (fade)
                    SheetWords(
                        "What will you do instead?",
                        "A replacement makes the old habit easier to drop.",
                        listOf("Instead"),
                        listOf("A book on the pillow"),
                    )
                else
                    SheetWords(
                        "Pair it with something you enjoy",
                        "Do the thing you want only while you do the thing you need.",
                        listOf("Paired with"),
                        listOf("My favourite playlist"),
                    )
            SheetKind.RESTACK -> SheetWords("Move this floor", "Choose the building it goes on.")
            SheetKind.RETIRE ->
                SheetWords(
                    if (fade) "Stop tracking this?" else "Retire this habit?",
                    block
                        ?.let {
                            "\"${it.title}\" and its ${it.reps} ${if (fade) "clean days" else "reps"} will be removed."
                        }
                        .orEmpty(),
                    danger = "Remove it",
                )
            SheetKind.TUNE -> SheetWords(block?.name.orEmpty())
        }
    }

    /** How long each field of [sheet] may be, by the page's limits. */
    fun limits(sheet: HabitsSheet, p: HabitsProjection): List<Int> =
        when (sheet.kind) {
            SheetKind.WHO -> listOf(p.limits.who)
            SheetKind.NEW_STACK -> listOf(p.limits.cue)
            SheetKind.EDIT_STACK -> listOf(p.limits.cue, p.limits.treat)
            SheetKind.NEW_HABIT ->
                if (sheet.fade) listOf(p.limits.name, 2, p.limits.pair)
                else listOf(p.limits.name, p.limits.name, p.limits.name)
            SheetKind.NAMES -> listOf(p.limits.name, p.limits.name, p.limits.name)
            SheetKind.PAIR -> listOf(p.limits.pair)
            else -> emptyList()
        }
}
