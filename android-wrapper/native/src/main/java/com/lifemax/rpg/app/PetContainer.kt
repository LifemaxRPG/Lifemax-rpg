package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.pet.data.WebPetRepository
import com.lifemax.rpg.feature.pet.domain.NoPetCommands
import com.lifemax.rpg.feature.pet.domain.PetCommands
import com.lifemax.rpg.feature.pet.presentation.PetViewModel

/** Explicit composition root for the Cat tab; scoped to the owning bridge lifecycle. */
class PetContainer(private val commands: PetCommands = NoPetCommands) {
    val repository = WebPetRepository()
    val factory =
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == PetViewModel::class.java)
                @Suppress("UNCHECKED_CAST")
                return PetViewModel(repository, commands) as T
            }
        }
}
