package com.lifemax.rpg.feature.overview.domain

/**
 * One row of the Overview editor, in the layout's order: the tile's [id] ("plan", "hero", ...), its
 * [name], its [sub] (or the page's note on why it may be empty, when it has one), its pixel [icon],
 * its colour as 0xAARRGGBB and whether it is [hidden]. Made from the projection by [editorRows].
 */
data class EditorRow(
    val id: String,
    val name: String,
    val sub: String,
    val icon: String,
    val color: Long,
    val hidden: Boolean = false,
)

/**
 * The rows the page's editor lists (ovEditRowsHtml): every tile of [layout]'s order that [info]
 * names, its words "note or sub" as the page has them, hidden or not.
 */
fun editorRows(layout: OverviewLayout, info: List<TileInfo>): List<EditorRow> {
    val byId = info.associateBy { it.id }
    return layout.order.mapNotNull { id ->
        byId[id]?.let {
            EditorRow(
                it.id,
                it.name,
                it.note.ifEmpty { it.sub },
                it.icon,
                it.color,
                id in layout.hidden,
            )
        }
    }
}

/**
 * The editor's words, the page's own (OV_EDIT_WORDS in 31-overview-edit.js, projected as `editor`):
 * its head, Done, Reset, a hidden row's line, the eye's "Hide "/"Show " before the tile's name, and
 * the TalkBack moves.
 */
data class EditorWords(
    val title: String,
    val hint: String,
    val done: String,
    val reset: String,
    val hiddenSub: String,
    val hide: String,
    val show: String,
    val moveUp: String,
    val moveDown: String,
    val moveTop: String,
    val moveBottom: String,
)

/**
 * The editor's buzzes, at the page's moments and lengths (haptic() in 31-overview-edit.js), on and
 * off in milliseconds starting on: the screen plays them through PageBuzz at the owner's strength.
 * Open, Lift and Cross play at once; Drop, Toggle, Reset and Move only once the page has stored the
 * change, as the page's own editor buzzes after its save.
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
