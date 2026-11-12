package com.lifemax.rpg.feature.pet.data

import com.lifemax.rpg.feature.pet.domain.CatteryCommand
import org.json.JSONObject

/**
 * The `cattery` command's payload as 50-native-pet-cattery.js takes it: its `kind`, and exactly the
 * keys that kind has (the page refuses any other shape). [op] rides on the commands that change
 * things only.
 */
internal fun catteryPayload(op: String, c: CatteryCommand): JSONObject {
    val j = JSONObject()
    fun kind(name: String) = j.put("kind", name)
    when (c) {
        is CatteryCommand.View -> kind("view").put("view", c.screen?.wire ?: "")
        is CatteryCommand.Goal -> kind("goal").put("value", c.value)
        is CatteryCommand.Pick -> kind("pick").put("who", c.who).put("key", c.key)
        is CatteryCommand.Breed -> kind("breed").put("mom", c.mom).put("dad", c.dad)
        is CatteryCommand.Keep -> kind("keep").put("at", c.at).put("i", c.index)
        is CatteryCommand.Drop -> kind("drop").put("at", c.at).put("i", c.index)
        is CatteryCommand.KeepAll -> kind("keepAll").put("at", c.at)
        is CatteryCommand.DropAll -> kind("dropAll").put("at", c.at)
        is CatteryCommand.Release -> kind("release").put("id", c.id)
        is CatteryCommand.Rename -> kind("rename").put("id", c.id).put("name", c.name)
        is CatteryCommand.Home -> kind("home").put("id", c.id)
        CatteryCommand.Stray -> kind("stray")
        CatteryCommand.NewLitter -> kind("newLitter")
        is CatteryCommand.PickKit -> kind("pickKit").put("i", c.index)
        is CatteryCommand.Adopt ->
            kind("adopt").put("mode", c.mode).put("kit", c.kit).put("name", c.name)
        CatteryCommand.Reopen -> kind("reopen")
    }
    if (c.changes) j.put("op", op)
    return j
}
