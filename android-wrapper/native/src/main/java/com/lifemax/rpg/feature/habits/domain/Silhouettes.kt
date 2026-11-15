package com.lifemax.rpg.feature.habits.domain

import kotlin.math.floor

/*
 * Who is in the lit windows and what they are doing: the activity of a habit by the words in its name (and in the
 * catalogue entry it came from), the cast of a floor (who, which of the looks, which room, which curtains, in
 * which window), the frame an activity shows at a moment, and how much detail a building gets at a zoom. Pure data
 * and arithmetic: the rigs themselves are in PeopleRig and PeopleActivities, the pixels in the scene's sprites.
 */

/** How much of a person is drawn at a zoom. */
enum class PeopleLod {
    /** The whole rig in its room. */
    FULL,

    /** A simple warm shape with a head and shoulders, bobbing. */
    BLOB,

    /** Only a moving dot in the glow. */
    DOT,

    /** Only the lit window. */
    NONE,
}

/**
 * Who is in one lit window of a floor. [activity] is null for an empty room (its light on, nobody
 * in), [room] what the window looks into, [look] which of the [Looks], [flip] which way they face,
 * [seed] their own number, [curtain] 0 for none, 1 a drape on the left, 2 on the right, 3 blinds
 * half down, 4 both drapes, [sill] 0 for nothing, 1 a pot plant, 2 a cat, and [commuter] whether
 * someone walking along the floor passes this window.
 */
class WindowCast(
    val activity: Activity?,
    val room: RoomKind,
    val look: Int,
    val flip: Boolean,
    val seed: Int,
    val curtain: Int,
    val curtainTone: Int,
    val sill: Int,
)

object Silhouettes {
    /** The zoom (dp to a unit) from which each level of detail is drawn. */
    const val FULL_FROM = .55f
    const val BLOB_FROM = .28f
    const val DOT_FROM = .12f

    fun lod(scale: Float): PeopleLod =
        when {
            scale >= FULL_FROM -> PeopleLod.FULL
            scale >= BLOB_FROM -> PeopleLod.BLOB
            scale >= DOT_FROM -> PeopleLod.DOT
            else -> PeopleLod.NONE
        }

    /** What the habit [name] has its people doing, by the words in it; null when nothing fits. */
    fun activityOf(name: String): Activity? = activitiesOf(name).firstOrNull()

    /**
     * Everything the habit [name] could have its people doing, the likeliest first: the words of
     * its name and of the catalogue entry it matches (its other names and keys) are looked for in
     * the keyword table.
     */
    fun activitiesOf(name: String): List<Activity> {
        val text = StringBuilder(name.lowercase())
        val idea =
            HabitCatalog.ALL.firstOrNull {
                it.name.equals(name, true) ||
                    it.small.equals(name, true) ||
                    it.stretch.equals(name, true)
            }
        if (idea != null) for (k in idea.keys) text.append(' ').append(k)
        val t = text.toString()
        for ((acts, words) in KEYWORDS) if (words.any { t.contains(it) }) return acts
        return emptyList()
    }

    private fun a(vararg x: Activity) = x.toList()

    private val KEYWORDS: List<Pair<List<Activity>, List<String>>> =
        listOf(
            a(Activity.WATERING) to
                listOf("water the", "water plant", "plants", "garden", "flower", "watering"),
            a(Activity.MAKING_BED, Activity.SWEEPING) to
                listOf("make the bed", "make your bed", "make bed", "bed made", "make my bed"),
            a(Activity.PUSHUPS, Activity.LIFTING) to
                listOf(
                    "push-up",
                    "pushup",
                    "push up",
                    "press-up",
                    "press up",
                    "plank",
                    "burpee",
                    "sit-up",
                    "situp",
                ),
            a(Activity.SQUATTING, Activity.LIFTING) to listOf("squat", "lunge", "legs", "glute"),
            a(Activity.JOGGING, Activity.WALKING) to
                listOf(
                    "run",
                    "jog",
                    "cardio",
                    "treadmill",
                    "sprint",
                    "cycling",
                    "bike",
                    "hiit",
                    "swim",
                ),
            a(Activity.LIFTING, Activity.SQUATTING, Activity.PUSHUPS, Activity.JOGGING) to
                listOf(
                    "lift",
                    "gym",
                    "weight",
                    "dumbbell",
                    "kettlebell",
                    "strength",
                    "pull-up",
                    "pullup",
                    "curl",
                    "workout",
                    "work out",
                    "exercise",
                    "train",
                    "chest",
                ),
            a(Activity.STRETCHING) to
                listOf(
                    "stretch",
                    "yoga",
                    "mobility",
                    "pilates",
                    "posture",
                    "toes",
                    "tai chi",
                    "foam roll",
                    "warm up",
                    "warm-up",
                    "flexib",
                ),
            a(Activity.MEDITATING) to
                listOf(
                    "medita",
                    "mindful",
                    "breath",
                    "relax",
                    "calm",
                    "pray",
                    "stillness",
                    "unwind",
                    "gratitude pause",
                ),
            a(Activity.BRUSHING, Activity.DRINKING) to
                listOf(
                    "teeth",
                    "floss",
                    "shower",
                    "skincare",
                    "skin care",
                    "wash face",
                    "wash your face",
                    "brush",
                    "moistur",
                    "sunscreen",
                    "shave",
                    "hygiene",
                    "mouthwash",
                ),
            a(Activity.COOKING, Activity.DRINKING) to
                listOf(
                    "cook",
                    "meal",
                    "chop",
                    "breakfast",
                    "lunch",
                    "dinner",
                    "salad",
                    "vegetable",
                    "veg",
                    "fruit",
                    "protein",
                    "healthy",
                    "snack",
                    "prep",
                ),
            a(Activity.DRINKING, Activity.COOKING, Activity.LOOKING_OUT) to
                listOf(
                    "water",
                    "drink",
                    "tea",
                    "coffee",
                    "hydrat",
                    "glass of",
                    "juice",
                    "smoothie",
                    "vitamin",
                    "supplement",
                    "pill",
                    "medicine",
                    "meds",
                ),
            a(Activity.SWEEPING) to
                listOf(
                    "tidy",
                    "clean",
                    "dish",
                    "laundry",
                    "declutter",
                    "sweep",
                    "vacuum",
                    "chore",
                    "pick up",
                    "organi",
                    "mop",
                    "dust",
                    "clothes",
                    "lay out",
                    "trash",
                    "bins",
                ),
            a(Activity.PLANNING) to
                listOf(
                    "plan",
                    "schedule",
                    "goal",
                    "to-do",
                    "todo",
                    "priorit",
                    "agenda",
                    "calendar",
                    "top 3",
                    "top three",
                    "review the day",
                    "weekly review",
                ),
            a(Activity.WRITING, Activity.READING, Activity.TYPING) to
                listOf(
                    "write",
                    "journal",
                    "diary",
                    "note",
                    "gratitude",
                    "draft",
                    "blog",
                    "essay",
                    "letter",
                    "handwrit",
                    "reflect",
                ),
            a(Activity.READING) to
                listOf(
                    "read",
                    "book",
                    "page",
                    "study",
                    "learn",
                    "language",
                    "vocab",
                    "flashcard",
                    "course",
                    "lesson",
                    "podcast",
                    "audiobook",
                    "article",
                ),
            a(Activity.TYPING, Activity.WRITING, Activity.PHONE_CALL) to
                listOf(
                    "email",
                    "inbox",
                    "code",
                    "type",
                    "budget",
                    "work",
                    "focus",
                    "deep",
                    "finance",
                    "invoice",
                    "spreadsheet",
                    "admin",
                    "bills",
                    "screen",
                    "computer",
                    "laptop",
                    "task",
                ),
            a(Activity.PHONE_CALL) to
                listOf(
                    "call",
                    "phone",
                    "text",
                    "message",
                    "friend",
                    "family",
                    "mom",
                    "dad",
                    "parent",
                    "network",
                    "reach out",
                    "check in",
                    "catch up",
                    "kids",
                    "partner",
                ),
            a(Activity.GUITAR) to listOf("guitar", "ukulele", "instrument", "bass", "violin"),
            a(Activity.PIANO) to listOf("piano", "keyboard", "scales", "music", "practice"),
            a(Activity.DANCING) to listOf("dance", "dancing", "sing", "karaoke", "move to"),
            a(Activity.WALKING) to
                listOf(
                    "walk",
                    "step",
                    "stroll",
                    "commute",
                    "dog",
                    "errand",
                    "outside",
                    "fresh air",
                    "hike",
                    "outdoors",
                    "sunlight",
                ),
            a(Activity.LOOKING_OUT) to
                listOf(
                    "window",
                    "look out",
                    "gaze",
                    "sunrise",
                    "sunset",
                    "sky",
                    "daydream",
                    "take a break",
                ),
            a(Activity.WAVING) to
                listOf("wave", "smile", "greet", "hello", "thank", "compliment", "hug", "kind"),
        )

    /**
     * What anyone does in an office: the people who are not doing the habit, and the people on a
     * floor nothing fits.
     */
    private val OFFICE =
        arrayOf(
            Activity.TYPING,
            Activity.READING,
            Activity.PHONE_CALL,
            Activity.DRINKING,
            Activity.LOOKING_OUT,
            Activity.WRITING,
            Activity.PLANNING,
            Activity.WALKING,
            Activity.TYPING,
            Activity.WAVING,
        )

    /**
     * What the person in the [nth] occupied window of a floor called [name] does, deterministic in
     * the floor's [seed]: the first does the habit; of the others most do it too (or something like
     * it) and some are colleagues doing office things, one walking past now and then. A name
     * nothing fits fills its windows with everyday office life, different in each window.
     */
    fun activityFor(name: String, nth: Int, seed: Int = name.hashCode()): Activity {
        val own = activitiesOf(name)
        if (own.isEmpty())
            return OFFICE[((Ambient.hash(seed, nth, 31) * OFFICE.size).toInt() + nth) % OFFICE.size]
        if (nth == 0)
            return own[(Ambient.hash(seed, 0, 32) * own.size).toInt().coerceAtMost(own.size - 1)]
        return if (Ambient.hash(seed, nth, 33) < .62f)
            own[(nth + (Ambient.hash(seed, 1, 34) * own.size).toInt()) % own.size]
        else
            OFFICE[
                (Ambient.hash(seed, nth, 35) * OFFICE.size).toInt().coerceAtMost(OFFICE.size - 1)]
    }

    private val EMPTY_ROOMS =
        arrayOf(
            RoomKind.OFFICE,
            RoomKind.LIVING,
            RoomKind.HALL,
            RoomKind.KITCHEN,
            RoomKind.STUDY,
            RoomKind.PLANTS,
            RoomKind.DESK,
            RoomKind.BEDROOM,
            RoomKind.BOARD,
        )

    /**
     * Who is in each of a floor's first [lit] windows (in the order they light): the first [people]
     * have someone in them doing something, the rest an empty room with its lights on. Every window
     * of a floor has its own look, room, curtains and sill; all of it is a function of the floor's
     * [key] and [title] and [lit] alone.
     */
    fun cast(title: String, key: String, lit: Int, people: Int): Array<WindowCast> {
        val seed = key.hashCode()
        return Array(lit) { k ->
            val s = (Ambient.hash(seed, k, 5) * 100000).toInt()
            val occupant = k < people
            val act = if (occupant) activityFor(title, k, seed) else null
            val room =
                if (act != null) PeopleActivities.of(act).room
                else
                    EMPTY_ROOMS[
                        (Ambient.hash(seed, k, 41) * EMPTY_ROOMS.size)
                            .toInt()
                            .coerceAtMost(EMPTY_ROOMS.size - 1)]
            val look = (((Ambient.hash(seed, 0, 42) * Looks.COUNT).toInt()) + k * 5) % Looks.COUNT
            val c = Ambient.hash(seed, k, 43)
            val curtain =
                if (c < .3f) 1 + (Ambient.hash(seed, k, 44) * 4).toInt().coerceAtMost(3) else 0
            val sillRoll = Ambient.hash(seed, k, 45)
            val sill = if (!occupant && sillRoll < .3f) (if (sillRoll < .14f) 1 else 2) else 0
            WindowCast(
                act,
                room,
                look,
                Ambient.hash(seed, k, 46) < .5f,
                s,
                curtain,
                (Ambient.hash(seed, k, 47) * 3).toInt().coerceAtMost(2),
                sill,
            )
        }
    }

    /**
     * Which of a done-today floor's dark windows has a dim hallway light (-1 for none): one floor
     * in three has one.
     */
    fun hallLight(key: String, count: Int): Int {
        val h = Ambient.hash(key.hashCode(), 0, 51)
        return if (count > 0 && h < .34f) (h * 100f).toInt() % count else -1
    }

    // ------------------------------------------------------------------ time

    /**
     * The frame of [spec] showing at [seconds] for a person with their own [seed]; frame 0 while
     * time stands still.
     */
    fun frame(spec: ActivitySpec, seconds: Float, seed: Int): Int {
        if (seconds <= 0f) return 0
        val t = seconds * spec.fps + Ambient.hash(seed, 0, 61) * spec.frames
        val i = floor(t).toInt() % spec.frames
        return if (i < 0) i + spec.frames else i
    }

    /**
     * How far into the next frame [seconds] is (0 to 1) for a slow activity that blends its frames;
     * 0 otherwise.
     */
    fun blend(spec: ActivitySpec, seconds: Float, seed: Int): Float {
        if (!spec.blend || seconds <= 0f) return 0f
        val t = seconds * spec.fps + Ambient.hash(seed, 0, 61) * spec.frames
        return t - floor(t)
    }

    /**
     * How much someone is in their window at [seconds]: they step out for a few seconds now and
     * then (a cycle of about two minutes, its own for [seed]) and come back; 1 while time stands
     * still.
     */
    fun here(seed: Int, seconds: Float): Float {
        if (seconds <= 0f) return 1f
        val cycle = 70f + Ambient.hash(seed, 0, 71) * 70f
        val t = (seconds + Ambient.hash(seed, 0, 72) * cycle) % cycle
        val away = 7f
        val edge = 1f
        return when {
            t >= cycle - away -> 0f
            t >= cycle - away - edge -> (cycle - away - t) / edge
            t < edge -> t / edge
            else -> 1f
        }.coerceIn(0f, 1f)
    }

    /**
     * How lit an empty room's lamp is at [seconds]: off for a few seconds every minute or so, then
     * on again.
     */
    fun lamp(seed: Int, seconds: Float): Float {
        if (seconds <= 0f) return 1f
        val cycle = 45f + Ambient.hash(seed, 0, 73) * 50f
        val t = (seconds + Ambient.hash(seed, 0, 74) * cycle) % cycle
        return if (t > cycle - 9f) 0f else 1f
    }

    /**
     * Where someone walking past stands across a window [width] wide at [seconds], from its left
     * edge, or NaN while they are out of it: they cross in about four seconds, then the window is
     * empty for a while. Standing still (0 seconds), they stand in the middle.
     */
    fun walkX(seconds: Float, seed: Int, width: Float): Float {
        if (seconds <= 0f) return width / 2f
        val cycle = 6.5f
        val t = ((seconds / cycle + Ambient.hash(seed, 0, 90)) % 1f + 1f) % 1f
        if (t >= .7f) return Float.NaN
        val u = t / .7f
        val x = -6f + (width + 12f) * u
        return if (Ambient.hash(seed, 0, 91) < .5f) x else width - x
    }

    /** Which way someone walking past faces: 1 right, -1 left. */
    fun walkDir(seed: Int): Float = if (Ambient.hash(seed, 0, 91) < .5f) 1f else -1f

    /**
     * Where someone walking along a whole floor [width] wide is at [seconds], from its left edge,
     * or NaN while no one is: they cross in about eleven seconds, every half minute or so; each
     * floor has its own pace. They pass behind the wall between the windows.
     */
    fun commuterX(seconds: Float, seed: Int, width: Float): Float {
        if (seconds <= 0f) return Float.NaN
        val cycle = 26f + Ambient.hash(seed, 0, 81) * 20f
        val t = ((seconds / cycle + Ambient.hash(seed, 0, 82)) % 1f + 1f) % 1f
        val span = 11f / cycle
        if (t >= span) return Float.NaN
        val u = t / span
        val x = -10f + (width + 20f) * u
        return if (Ambient.hash(seed, 0, 83) < .5f) x else width - x
    }

    fun commuterDir(seed: Int): Float = if (Ambient.hash(seed, 0, 83) < .5f) 1f else -1f

    /**
     * The visitor in the lobby: how far from the door's side of the window [width] wide they are at
     * [seconds], or NaN while they are inside. They walk out, stand and look at the street for a
     * while, and walk back in. Standing still (0 seconds), they stand at the far end.
     */
    fun lobbyVisitor(seconds: Float, width: Float): Float {
        val far = width * .55f
        if (seconds <= 0f) return far
        val t = (seconds / 20f) % 1f
        return when {
            t < .3f -> far * t / .3f
            t < .62f -> far
            t < .92f -> far * (1f - (t - .62f) / .3f)
            else -> Float.NaN
        }
    }

    /** Whether the lobby's visitor is on the move at [seconds]. */
    fun lobbyWalking(seconds: Float): Boolean {
        if (seconds <= 0f) return false
        val t = (seconds / 20f) % 1f
        return t < .3f || (t >= .62f && t < .92f)
    }

    /** Whether the floor with [seed] has someone who walks along it. */
    fun hasCommuter(seed: Int, lit: Int): Boolean = lit >= 3 && Ambient.hash(seed, 0, 84) < .75f
}
