package com.lifemax.rpg.feature.plan.domain

/** Where the page's guided workout is (the session's phase, gwrCurrent().phase). */
enum class RunnerPhase(val wire: String) {
    INTRO("intro"),
    READY("ready"),
    ACTIVE("active"),
    TIMING("timing"),
    TIMER_DONE("timer-done"),
    REST("rest"),
    COMPLETE("complete");

    companion object {
        fun of(wire: String): RunnerPhase =
            entries.firstOrNull { it.wire == wire } ?: error("Unknown guided workout phase $wire")
    }
}

/** How a set's pip reads: done, the one being done, or still to come. */
enum class PipState {
    DONE,
    CURRENT,
    TODO,
}

/** An exercise's segment in the route above the screen (gwrV60Lead): flex weight and how full. */
data class RunnerSegment(val state: String, val weight: Int, val fill: Int)

/** A row of the itinerary the grid button shows (gwrQuickReview). */
data class RunnerRow(val number: Int, val name: String, val status: String, val current: Boolean)

data class RunnerItinerary(val title: String, val rows: List<RunnerRow>)

data class RunnerDay(val date: String, val label: String, val selected: Boolean)

data class RunnerLogRow(
    val number: Int,
    val name: String,
    val optional: Boolean,
    val prescription: String,
    val done: Boolean,
)

/** The briefing before the first set. */
data class RunnerIntro(
    val step: String,
    val title: String,
    val exercises: Int,
    val sets: Int,
    val xp: Int,
    val days: List<RunnerDay>,
    val log: List<RunnerLogRow>,
)

/** What the number box asks for when a timed exercise has no prescribed length (gwrGo's prompt). */
data class DurationAsk(val text: String, val start: Int, val max: Int)

/** The weight box of a set, and how it steps (gwrNudge). */
data class WeightField(val suggest: String, val step: Double, val min: Double, val max: Double)

/** The set being done: everything the ready, active, timing, review and rest screens say of it. */
data class RunnerStep(
    val key: String,
    val number: Int,
    val count: Int,
    /** "Exercise 2 of 10 · optional" and "Set 1 of 3", as the page words them. */
    val heading: String,
    val setText: String,
    val optional: Boolean,
    val name: String,
    val prescription: String,
    val set: Int,
    val sets: Int,
    val pips: List<PipState>,
    /** reps, seconds, minutes or check (gwrNumTarget). */
    val kind: String,
    val unit: String,
    val label: String,
    val suggest: String,
    val timed: Boolean,
    val askDuration: Boolean,
    val durationAsk: DurationAsk,
    val amountStep: Double,
    val amountMin: Double,
    val weight: WeightField?,
    val lastReady: String,
    val lastActive: String,
    val cue: String,
    val skipAsk: String,
    val swapAsk: String,
    val swapFrom: String,
) {
    val isCheck: Boolean
        get() = kind == "check"
}

data class RunnerTimer(val remainingMs: Long, val totalMs: Long)

/** A finished or stopped timed interval, waiting to be logged. */
data class RunnerReview(val time: String, val full: Boolean)

data class RunnerComplete(
    val full: Boolean,
    val xp: Int,
    val sets: Int,
    val time: String,
    val volume: String,
)

/**
 * The page's fixed words for the runner (GWR_WORDS): every label the screen draws comes from here.
 */
class RunnerWords(private val words: Map<String, String>) {
    operator fun get(key: String): String = words.getValue(key)

    companion object {
        /**
         * Every word the screens use; a projection without one is refused rather than drawn short.
         */
        val REQUIRED =
            listOf(
                "pause",
                "close",
                "itinerary",
                "itineraryTitle",
                "itineraryKicker",
                "itineraryReturn",
                "skipped",
                "introDay",
                "exercises",
                "sets",
                "xp",
                "start",
                "introNote",
                "checkHero",
                "noWeight",
                "startTimer",
                "startSet",
                "swap",
                "skip",
                "coach",
                "optional",
                "timerLive",
                "ringWork",
                "ringRest",
                "timerStop",
                "timerCancel",
                "intervalDone",
                "stoppedEarly",
                "log",
                "restart",
                "restTag",
                "restLess",
                "restLessShort",
                "restMore",
                "restMoreShort",
                "upNext",
                "nextSet",
                "undo",
                "live",
                "repsDone",
                "weight",
                "weightHint",
                "logSet",
                "back",
                "finishThenLog",
                "completeFull",
                "completePartial",
                "completeNoteFull",
                "completeNotePartial",
                "time",
                "volume",
                "backToPlan",
                "finishRemaining",
            )
    }
}

/**
 * The guided workout as the page shows it, sent while the screen hosts it (plan.runner.v1). The
 * page keeps the session and every rule; the screen draws these and sends acts back.
 */
data class PlanRunner(
    val phase: RunnerPhase,
    val date: String,
    val title: String,
    /** The session clock at the projection, and whether it is running. */
    val elapsedMs: Long,
    val running: Boolean,
    val progressDone: Int,
    val progressTotal: Int,
    val route: List<RunnerSegment>,
    val itinerary: RunnerItinerary,
    val intro: RunnerIntro?,
    val step: RunnerStep?,
    val timer: RunnerTimer?,
    val review: RunnerReview?,
    val rest: RunnerTimer?,
    val complete: RunnerComplete?,
    val words: RunnerWords,
)

/**
 * Which of the page's hand-overs the page lets the screen take over (the page asks the handshake).
 */
data class PlanFeatures(
    val runner: Boolean = false,
    val fields: Boolean = false,
    val leave: Boolean = false,
    val offers: Boolean = false,
)

data class PlanOfferRow(val whenText: String, val what: String)

/** The plan the page offers while there is none (planOfferFor), with its sign-up button. */
data class PlanOffer(
    val eyebrow: String,
    val title: String,
    val blurb: String,
    val rows: List<PlanOfferRow>,
    val note: String,
    val startLabel: String,
    /** The start date to begin with: today, as the page's date box does. */
    val start: String,
    val button: String,
)

/** The projection while there is no plan: only the offer, and the frame every projection has. */
data class PlanOfferProjection(
    val session: String,
    val sequence: Long,
    val offer: PlanOffer,
    val features: PlanFeatures,
    val reducedMotion: Boolean,
    val celebrationTarget: PagePoint,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
