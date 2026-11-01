package com.lifemax.rpg.feature.overview.domain

// align at merge: these are the editor's own minimal types until the projection's decoder
// (OverviewCards.kt / OverviewProjection.kt) lands. A row is one `tileInfo[]` entry joined with
// `layout {order, hidden}`; the words are 31-overview-edit.js's until the page projects them.

/**
 * One row of the Overview editor, in the layout's order: the tile's [id] ("plan", "hero", ...), its
 * [name], its [sub] (or the page's note on why it may be empty, `tileInfo[].note`, when it has
 * one), its pixel [icon], its colour as 0xAARRGGBB and whether it is [hidden].
 */
data class EditorRow(
    val id: String,
    val name: String,
    val sub: String,
    val icon: String,
    val color: Long,
    val hidden: Boolean = false,
)

/** The editor's words, the page's own (31-overview-edit.js: ovEditOpen, ovEditRowsHtml). */
data class EditorWords(
    val title: String = "Edit Overview",
    val hint: String = "Drag to reorder. Tap the eye to hide a tile.",
    val done: String = "Done",
    val reset: String = "Reset to default",
    val hiddenSub: String = "Hidden",
    val hide: String = "Hide ",
    val show: String = "Show ",
    val moveUp: String = "Move up",
    val moveDown: String = "Move down",
    val moveTop: String = "Move to top",
    val moveBottom: String = "Move to bottom",
)

/**
 * The editor's buzzes, at the page's moments and lengths (haptic() in 31-overview-edit.js), on and
 * off in milliseconds starting on: the screen plays them through PageBuzz at the owner's strength.
 */
enum class EditorBuzz(vararg val timings: Long) {
    Open(8),
    Lift(10),
    Cross(4),
    Drop(8),
    Toggle(6),
    Reset(8, 30, 8),
    Move(5),
}
