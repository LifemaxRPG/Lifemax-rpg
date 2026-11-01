package com.lifemax.rpg.feature.lifedirector

import com.lifemax.rpg.feature.lifedirector.domain.DialPlace
import com.lifemax.rpg.feature.lifedirector.domain.LifeDirectorCommands

/** The commands a view model sends, each answered with its own request id. */
internal class DirectorRecorder : LifeDirectorCommands {
    val days = mutableListOf<Int>()
    val sections = mutableListOf<String>()
    val seen = mutableListOf<Pair<String, Boolean>>()
    val changes = mutableListOf<Pair<String, Map<String, Any>>>()
    /** the dial's commands: (command, handle, place) */
    val sent = mutableListOf<Triple<String, String, DialPlace>>()
    val sheets = mutableListOf<Pair<String, String>>()
    val todays = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun showDay(index: Int): String {
        days += index
        return "day-${days.size}"
    }

    override fun showSection(id: String): String {
        sections += id
        return "section-${sections.size}"
    }

    override fun gotoDay(index: Int): String {
        sections += "today@$index"
        return "section-${sections.size}"
    }

    override fun change(name: String, args: Map<String, Any>): String {
        changes += name to args
        return "change-${changes.size}"
    }

    override fun tourSeen(key: String, finished: Boolean): String {
        seen += key to finished
        return "seen-${seen.size}"
    }

    override fun showSheet(kind: String, id: String): String {
        sheets += kind to id
        return "sheet-${sheets.size}"
    }

    override fun today(name: String, args: Map<String, Any>): String {
        todays += name to args
        return "today-${todays.size}"
    }

    override fun dialDrop(key: String, place: DialPlace) = dial("dialDrop", key, place)

    override fun dialApply(key: String, place: DialPlace) = dial("dialApply", key, place)

    private fun dial(command: String, key: String, place: DialPlace): String {
        sent += Triple(command, key, place)
        return "director-${sent.size}"
    }
}
