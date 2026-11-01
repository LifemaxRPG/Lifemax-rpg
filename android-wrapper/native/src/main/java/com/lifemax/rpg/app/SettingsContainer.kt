package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.settings.data.WebSettingsRepository
import com.lifemax.rpg.feature.settings.domain.SettingsCommands
import com.lifemax.rpg.feature.settings.presentation.SettingsViewModel

/**
 * Composition root for Settings, scoped to its plugin. [commands] is how the screen reaches the
 * page while it stays.
 */
class SettingsContainer(commands: SettingsCommands) {
    val repository = WebSettingsRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == SettingsViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return SettingsViewModel(repository, commands) as T
            }
        }
}
