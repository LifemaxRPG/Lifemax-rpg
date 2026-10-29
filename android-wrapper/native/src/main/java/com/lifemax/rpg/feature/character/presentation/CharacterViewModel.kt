package com.lifemax.rpg.feature.character.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.character.domain.CharacterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CharacterViewModel(repository:CharacterRepository):ViewModel() {
    val projection=repository.state
    private val mutable=MutableStateFlow(CharacterSelection())
    val selection=mutable.asStateFlow()
    fun select(id:String){mutable.value=mutable.value.copy(selected=id)}
    fun compare(on:Boolean){mutable.value=mutable.value.copy(compare=on)}
    fun sort(mode:String){require(mode in setOf("level","attention","week"));mutable.value=mutable.value.copy(sort=mode)}
    fun skills(){mutable.value=mutable.value.copy(allSkills=!mutable.value.allSkills)}
}
data class CharacterSelection(val selected:String?=null,val compare:Boolean=false,val sort:String="level",val allSkills:Boolean=false)
