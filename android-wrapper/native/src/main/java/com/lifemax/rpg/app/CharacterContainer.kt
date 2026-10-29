package com.lifemax.rpg.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lifemax.rpg.feature.character.data.WebCharacterRepository
import com.lifemax.rpg.feature.character.presentation.CharacterViewModel

/** Explicit composition root for this slice; scoped to the owning bridge lifecycle. */
class CharacterContainer {
    val repository=WebCharacterRepository()
    val factory=object:ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            require(modelClass==CharacterViewModel::class.java)
            @Suppress("UNCHECKED_CAST") return CharacterViewModel(repository) as T
        }
    }
}
