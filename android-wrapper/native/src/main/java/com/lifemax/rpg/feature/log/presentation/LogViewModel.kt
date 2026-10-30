package com.lifemax.rpg.feature.log.presentation

import androidx.lifecycle.ViewModel
import com.lifemax.rpg.feature.log.domain.LogCommands
import com.lifemax.rpg.feature.log.domain.LogDraft
import com.lifemax.rpg.feature.log.domain.LogProjection
import com.lifemax.rpg.feature.log.domain.LogRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The draft being edited lives here; the award and the save stay with the page. A change asks the
 * page for a fresh preview. The page's draft replaces the local one only when the page says it
 * changed it (a new session or a new draftRevision: after a save, or a prefill from a link). One
 * operation id is kept per draft, so pressing Save twice records one activity.
 */
class LogViewModel(
    repository: LogRepository,
    private val commands: LogCommands,
    private val newOperationId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    val projection = repository.state
    private val mutableDraft = MutableStateFlow<LogDraft?>(null)
    val draft = mutableDraft.asStateFlow()
    private val mutableStatus = MutableStateFlow(LogStatus())
    val status = mutableStatus.asStateFlow()
    private var adopted: Pair<String, Long>? = null
    private var operationId: String? = null

    /** Take the page's draft if the page replaced it since the last time. */
    fun adopt(p: LogProjection) {
        val key = p.session to p.draftRevision
        if (key == adopted) return
        adopted = key
        operationId = null
        mutableDraft.value = p.draft
        mutableStatus.value = LogStatus()
    }

    /** The page's answer to a command. Only a save changes what the screen shows. */
    fun onReply(command: String, ok: Boolean, message: String) {
        if (command != "submit") return
        mutableStatus.value =
            LogStatus(
                saving = false,
                error = if (ok) null else message.ifBlank { "Could not save" },
            )
    }

    fun choose(type: String) {
        val preset = projection.value?.preset(type) ?: return
        edit {
            it.copy(
                type = preset.id,
                skill = preset.skill,
                primary = preset.primary,
                secondary = preset.secondary,
            )
        }
    }

    /** Kept within the page's limits: under its minimum nothing would be paid. */
    fun setDuration(minutes: Int) {
        val p = projection.value ?: return
        edit { it.copy(duration = minutes.coerceIn(p.minMinutes, p.maxMinutes)) }
    }

    fun setQuality(value: String) = edit { it.copy(quality = value) }

    fun setCustomName(name: String) = edit { it.copy(customName = name.take(80)) }

    fun setSkill(skill: String) = edit { it.copy(skill = skill.take(60)) }

    fun setPrimary(id: String) = edit { it.copy(primary = id) }

    fun setSecondary(id: String) = edit { it.copy(secondary = id) }

    fun setNotes(notes: String) = edit { it.copy(notes = notes.take(2000)) }

    fun submit() {
        val current = mutableDraft.value ?: return
        if (mutableStatus.value.saving) return
        val id = operationId ?: newOperationId().also { operationId = it }
        mutableStatus.value = LogStatus(saving = true)
        commands.submit(current, id)
    }

    private fun edit(change: (LogDraft) -> LogDraft) {
        val current = mutableDraft.value ?: return
        val next = change(current)
        if (next == current) return
        mutableDraft.value = next
        operationId = null
        commands.preview(next)
    }
}

/** Whether a save is on its way to the page, and the page's message if the last one failed. */
data class LogStatus(val saving: Boolean = false, val error: String? = null)
