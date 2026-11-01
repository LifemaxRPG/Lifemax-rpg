package com.lifemax.rpg.app

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.core.designsystem.feedback.PageBuzz
import com.lifemax.rpg.core.designsystem.feedback.VibratorPageBuzz
import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import com.lifemax.rpg.feature.overview.presentation.OverviewViewModel

/**
 * Composition root for the Overview cards, scoped to their plugin. [commands] is how the cards
 * reach the page; [buzz] is the phone's vibrator the cards buzz through (the week bar's pick), made
 * once the plugin has a context.
 */
class OverviewContainer(commands: OverviewCommands) {
    val repository = WebOverviewRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == OverviewViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return OverviewViewModel(repository, commands) as T
            }
        }

    private var vibrator: PageBuzz? = null

    fun buzz(context: Context): PageBuzz =
        vibrator ?: VibratorPageBuzz(context).also { vibrator = it }
}
