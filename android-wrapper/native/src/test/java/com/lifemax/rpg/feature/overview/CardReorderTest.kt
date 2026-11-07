package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.presentation.editor.mergeCardOrder
import org.junit.Assert.*
import org.junit.Test

class CardReorderTest {
    @Test
    fun draggingVisibleCardsPreservesHiddenAndUnavailableSlots() {
        val full = listOf("cat", "hidden", "plan", "unavailable", "week")
        assertEquals(
            listOf("week", "hidden", "cat", "unavailable", "plan"),
            mergeCardOrder(full, listOf("week", "cat", "plan")),
        )
    }

    @Test
    fun visibleReorderKeepsEverySavedCardExactlyOnce() {
        val full = listOf("a", "hidden", "b", "c")
        for (visible in
            listOf(listOf("a", "b", "c"), listOf("c", "b", "a"), listOf("b", "a", "c"))) {
            val result = mergeCardOrder(full, visible)
            assertEquals(full.toSet(), result.toSet())
            assertEquals(full.size, result.size)
            assertEquals("hidden", result[1])
        }
    }

    @Test
    fun invalidDuplicateOrUnknownCardsCannotOverwriteLayout() {
        assertThrows(IllegalArgumentException::class.java) {
            mergeCardOrder(listOf("a", "b"), listOf("a", "a"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            mergeCardOrder(listOf("a", "b"), listOf("unknown"))
        }
        assertEquals(listOf("a", "b"), mergeCardOrder(listOf("a", "b"), emptyList()))
    }
}
