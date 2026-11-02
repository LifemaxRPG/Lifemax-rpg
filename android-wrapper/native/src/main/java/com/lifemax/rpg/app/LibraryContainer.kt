package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.library.data.WebMediaRepository
import com.lifemax.rpg.feature.library.domain.MediaCommands
import com.lifemax.rpg.feature.library.presentation.MediaViewModel

/**
 * Composition root for the Library's Media pane, scoped to its plugin; [commands] sends the Media
 * sheets' writes to the page.
 */
class LibraryContainer(private val commands: MediaCommands = MediaCommands.None) {
    val mediaRepository = WebMediaRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == MediaViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return MediaViewModel(mediaRepository, commands) as T
            }
        }
}
