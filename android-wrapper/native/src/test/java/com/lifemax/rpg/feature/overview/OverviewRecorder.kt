package com.lifemax.rpg.feature.overview

import com.lifemax.rpg.feature.overview.data.WebOverviewRepository
import com.lifemax.rpg.feature.overview.domain.EditorWords
import com.lifemax.rpg.feature.overview.domain.OverviewCards
import com.lifemax.rpg.feature.overview.domain.OverviewCommands
import org.json.JSONObject

/** The page's projection in fixture [name] (overview-cards-[name].json), decoded. */
internal fun overviewFixture(name: String = "established"): OverviewCards {
    val text = OverviewRecorder::class.java.getResource("/fixtures/overview-cards-$name.json")!!
    return WebOverviewRepository().apply { accept(JSONObject(text.readText()), true) }.state.value!!
}

/** The page's editor words (OV_EDIT_WORDS), as the fixtures carry them. */
internal fun pageEditorWords(): EditorWords = overviewFixture().editor

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

    override fun layout(order: List<String>, hidden: List<String>) =
        record("layout", order.joinToString(","), hidden.joinToString(","))

    override fun layoutReset() = record("layoutReset")
}
