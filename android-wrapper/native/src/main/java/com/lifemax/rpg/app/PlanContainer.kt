package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.plan.data.WebPlanRepository
import com.lifemax.rpg.feature.plan.domain.PlanCommands
import com.lifemax.rpg.feature.plan.presentation.PlanViewModel

/**
 * Composition root for the Workout plan, scoped to its plugin. [commands] is how the screen reaches
 * the page.
 */
class PlanContainer(commands: PlanCommands) {
    val repository = WebPlanRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == PlanViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return PlanViewModel(repository, commands) as T
            }
        }
}
