package com.lifemax.rpg.feature.overview.presentation.editor

/*
 * The editor's reorder rules as plain maths, the page's own (ovEditWire in 31-overview-edit.js), so
 * they are tested without a screen.
 */

/**
 * The slot a lifted row has reached: [mids] are the rows' centres when the drag began, [from] the
 * lifted row's index and [y] its centre now. Upwards it takes the first row whose centre it has
 * passed; downwards the last one, as the page's loop does.
 */
internal fun slotFor(mids: List<Float>, from: Int, y: Float): Int {
    var to = from
    for (i in mids.indices) {
        if (i < from && y < mids[i]) return i
        if (i > from && y > mids[i]) to = i
    }
    return to
}

/**
 * How far row [i] stands aside, in steps (the lifted row's height plus the gap), while the row at
 * [from] hovers over slot [to]: the rows it has passed move one step towards where it came from.
 */
internal fun shiftFor(i: Int, from: Int, to: Int): Int =
    when {
        i == from -> 0
        from < to && i > from && i <= to -> -1
        from > to && i < from && i >= to -> 1
        else -> 0
    }

/** [order] with the item at [from] moved to [to]. */
internal fun <T> moved(order: List<T>, from: Int, to: Int): List<T> {
    if (from == to || from !in order.indices || to !in order.indices) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
}

/** The tops of the rows of [order], given each one's [height] and the [gap] between them. */
internal fun tops(order: List<String>, height: (String) -> Float, gap: Float): Map<String, Float> {
    var y = 0f
    return order.associateWith { id -> y.also { y += height(id) + gap } }
}

/**
 * How far the list scrolls on a move of a lifted row whose finger is at [y] in a window [height]
 * tall, all in px with [density] px a dp: 8 dp up within 90 dp of the top, 8 dp down within 110 dp
 * of the bottom (the page's window.scrollBy), nothing in between.
 */
internal fun autoScrollStep(y: Float, height: Float, density: Float): Float =
    when {
        y > height - 110 * density -> 8 * density
        y < 90 * density -> -8 * density
        else -> 0f
    }

/**
 * The TalkBack moves (the page's arrow keys on a grip, and the top and bottom the plan adds): each
 * gives the new order, or null when the row cannot go that way.
 */
enum class EditorMove {
    Up,
    Down,
    Top,
    Bottom;

    fun apply(order: List<String>, id: String): List<String>? {
        val from = order.indexOf(id)
        if (from < 0) return null
        val to =
            when (this) {
                Up -> from - 1
                Down -> from + 1
                Top -> 0
                Bottom -> order.lastIndex
            }
        if (to == from || to !in order.indices) return null
        return moved(order, from, to)
    }
}
