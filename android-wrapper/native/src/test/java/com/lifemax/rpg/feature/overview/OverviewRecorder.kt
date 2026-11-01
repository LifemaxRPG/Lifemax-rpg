package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.domain.OverviewCommands

/** The commands a view model sends, as (command, arguments), each answered with its own id. */
internal class OverviewRecorder : OverviewCommands {
    val sent = mutableListOf<Pair<String, List<String>>>()

    private fun record(command: String, vararg args: String): String {
        sent += command to args.toList()
        return "$command-${sent.size}"
    }

    override fun plan(act: String, id: String) = record("plan", act, id)

    override fun attr(id: String, tile: String) = record("attr", id, tile)

    override fun luck(op: String) = record("luck", op)

    override fun shop() = record("shop")

    override fun health() = record("health")

    override fun appLaunch(pkg: String) = record("appLaunch", pkg)

    override fun appAdd() = record("appAdd")

    override fun appsEdit() = record("appsEdit")

    override fun tip(op: String) = record("tip", op)

    override fun edit() = record("edit")
}
