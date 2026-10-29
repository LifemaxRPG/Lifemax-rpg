package com.lifemax.rpg.feature.character.domain
import kotlinx.coroutines.flow.StateFlow
interface CharacterRepository { val state:StateFlow<CharacterProjection?> }
