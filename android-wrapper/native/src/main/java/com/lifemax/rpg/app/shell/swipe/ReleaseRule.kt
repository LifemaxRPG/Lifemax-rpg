package com.lifemax.rpg.app.shell.swipe

/** How a release that commits got there; sent to the page as `via`. */
enum class Via(val wire: String) {
    /** The finger carried the screen over the catch. */
    Drag("drag"),
    /** A hard flick before the catch. */
    Fling("fling"),
}

/** What a release does. [via] is null for a cancel. */
enum class Release(val via: Via?) {
    CommitDrag(Via.Drag),
    CommitFling(Via.Fling),
    Cancel(null);

    val commits: Boolean
        get() = via != null
}

/**
 * The release rule (plan §2), in order:
 * 1. a wall cancels;
 * 2. a back-fling (v ≤ −backFlingDp) cancels, even when latched;
 * 3. latched commits;
 * 4. a fling (v ≥ flingDp) after at least flingMin of travel commits;
 * 5. anything else cancels. A casual 600–1000 dp/s flick springs back: that is the "really swipe".
 */
object ReleaseRule {
    /**
     * [u] is the travel toward the neighbour as a fraction of the width; [velocityDp] is the
     * finger's velocity toward the neighbour in dp/s (VelocityTracker over the last 100 ms, in raw
     * coordinates).
     */
    fun decide(
        wall: Boolean,
        latched: Boolean,
        u: Float,
        velocityDp: Float,
        tune: SwipeTune,
    ): Release =
        when {
            wall -> Release.Cancel
            velocityDp <= -tune.backFlingDp -> Release.Cancel
            latched -> Release.CommitDrag
            velocityDp >= tune.flingDp && u >= tune.flingMin -> Release.CommitFling
            else -> Release.Cancel
        }
}
