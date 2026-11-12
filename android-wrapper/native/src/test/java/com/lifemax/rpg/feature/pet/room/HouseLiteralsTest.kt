package com.lifemax.rpg.feature.pet.room

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The house is data (`pet/room/house.json`, [HouseMap]): the size of the old three-room strip must
 * not creep back into the engine as a literal (docs/specs/CAT_HOUSE_EXPANSION_PLAN.md risk 2). The
 * code of the body, brain, room, host and world packages names no `192` or `576` outside the one
 * room-art width, and none of the old room cut names.
 */
class HouseLiteralsTest {
    private fun petSources(): List<File> {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val root =
            generateSequence(start) { it.parentFile }
                .map {
                    File(it, "android-wrapper/native/src/main/java/com/lifemax/rpg/feature/pet")
                }
                .firstOrNull { it.isDirectory }
                ?: error("the pet sources were not found above $start")
        return listOf("body", "brain", "room", "host", "world").flatMap {
            File(root, it).walkTopDown().filter { f -> f.extension == "kt" }.toList()
        }
    }

    private fun code(file: File): List<Pair<Int, String>> =
        file
            .readLines()
            .mapIndexed { i, line -> i + 1 to line.trim() }
            .filter { (_, line) ->
                line.isNotEmpty() &&
                    !line.startsWith("*") &&
                    !line.startsWith("/*") &&
                    !line.startsWith("//")
            }

    @Test
    fun noRoomCountOrStripWidthIsWrittenInTheEngine() {
        val literal = Regex("""(?<![\w.])(192|576)(?![\w.])""")
        val names = Regex("""\b(LIVING_END|KITCHEN_START|ROOM_LO|ROOM_HI)\b""")
        val found = ArrayList<String>()
        for (file in petSources()) for ((n, line) in code(file)) {
            val text = line.substringBefore(" //")
            if (file.name == "RoomArt.kt" && text.startsWith("const val BACKDROP_WIDTH")) continue
            if (literal.containsMatchIn(text) || names.containsMatchIn(text)) {
                found += "${file.name}:$n: $line"
            }
        }
        assertTrue(
            "the house is data; these lines write it again:\n" + found.joinToString("\n"),
            found.isEmpty(),
        )
    }
}
