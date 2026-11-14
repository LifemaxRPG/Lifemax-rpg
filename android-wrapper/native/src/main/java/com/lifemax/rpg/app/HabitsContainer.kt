package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.habits.data.HabitsFile
import com.lifemax.rpg.feature.habits.data.LocalHabitsRepository
import com.lifemax.rpg.feature.habits.domain.HabitsStorage
import com.lifemax.rpg.feature.habits.presentation.HabitsViewModel
import java.io.File

/**
 * Composition root for Habits, scoped to its plugin: the habits file in the app's private files
 * (`files/habits/habits.json`, which Android's device backup copies with the rest of the app's
 * files), the repository over it and the screen's view model.
 */
class HabitsContainer(storage: HabitsStorage) {
    constructor(filesDir: File) : this(HabitsFile(File(filesDir, DIRECTORY)))

    val repository = LocalHabitsRepository(storage)
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HabitsViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return HabitsViewModel(repository, repository) as T
            }
        }

    companion object {
        const val DIRECTORY = "habits"
    }
}
