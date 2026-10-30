package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.lifedirector.data.WebLifeDirectorRepository
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands
import com.lifemax.rpg.feature.lifedirector.presentation.LifeDirectorViewModel

/**
 * Composition root for Life Director, scoped to its plugin. [commands] is how the screen reaches
 * the page.
 */
class LifeDirectorContainer(commands: LifeDirectorCommands) {
    val repository = WebLifeDirectorRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == LifeDirectorViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return LifeDirectorViewModel(repository, commands) as T
            }
        }
}
