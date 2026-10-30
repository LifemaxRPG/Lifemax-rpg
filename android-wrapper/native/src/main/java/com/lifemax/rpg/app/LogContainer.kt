package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.log.data.WebLogRepository
import com.lifemax.rpg.feature.log.domain.LogCommands
import com.lifemax.rpg.feature.log.presentation.LogViewModel

/**
 * Composition root for Log, scoped to its plugin. [commands] is how the screen reaches the page.
 */
class LogContainer(commands: LogCommands) {
    val repository = WebLogRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == LogViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return LogViewModel(repository, commands) as T
            }
        }
}
