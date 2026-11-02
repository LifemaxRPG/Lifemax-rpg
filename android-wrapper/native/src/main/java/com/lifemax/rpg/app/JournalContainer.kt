package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.journal.data.WebJournalRepository
import com.lifemax.rpg.feature.journal.domain.JournalCommands
import com.lifemax.rpg.feature.journal.presentation.JournalViewModel

/**
 * Composition root for the Library's Journal pane, scoped to its plugin. [commands] is how the
 * screen reaches the page while it stays.
 */
class JournalContainer(commands: JournalCommands) {
    val repository = WebJournalRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == JournalViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return JournalViewModel(repository, commands) as T
            }
        }
}
