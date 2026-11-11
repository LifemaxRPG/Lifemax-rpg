package com.lifemax.rpg.core.lifedirector.readmodels

import com.lifemax.rpg.core.save.JsonArray
import com.lifemax.rpg.core.save.JsonBool
import com.lifemax.rpg.core.save.JsonObject
import com.lifemax.rpg.core.save.JsonString

/**
 * LifeMax as the page's host answered while its read models ran ([seen]: `{capabilities:
 * {connected, missing}, milestones}`, the members it was asked; the golden file's `out.host`, or
 * what a read shadow sends beside the page's sections). It notes which questions native asked, so
 * they are compared with the page's too. A question the page was not asked answers "not connected"
 * and no milestones.
 */
class RecordedHostView(private val seen: JsonObject) : DirectorHostView {
    val asked = LinkedHashSet<String>()

    override fun capabilities(): HostCapabilities {
        asked += "capabilities"
        val c = seen["capabilities"] as? JsonObject ?: return HostCapabilities(false, listOf("?"))
        return HostCapabilities(
            c["connected"] == JsonBool.TRUE,
            (c["missing"] as JsonArray).items.map { (it as JsonString).value },
        )
    }

    override fun milestones(): List<Milestone> {
        asked += "milestones"
        val m = seen["milestones"] as? JsonArray ?: return emptyList()
        return m.items.map {
            val o = it as JsonObject
            Milestone(
                (o["title"] as? JsonString)?.value,
                (o["desc"] as? JsonString)?.value,
                o["done"] == JsonBool.TRUE,
                o["locked"] == JsonBool.TRUE,
            )
        }
    }
}
