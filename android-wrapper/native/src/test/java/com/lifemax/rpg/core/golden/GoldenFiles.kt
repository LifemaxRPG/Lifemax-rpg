package com.lifemax.rpg.core.golden

import java.io.File
import org.json.JSONArray

/**
 * Golden cases, read in place from lifemax_modular_v73/tests/fixtures. The file is never copied:
 * the web test keeps only the original true. Gradle runs unit tests from
 * android-wrapper/android/app, so the search walks up.
 */
object GoldenFiles {
    /** The file's text, one case per line. */
    fun text(name: String): String {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val file =
            generateSequence(start) { it.parentFile }
                .map { File(it, "lifemax_modular_v73/tests/fixtures/$name") }
                .firstOrNull { it.isFile } ?: error("$name not found above $start")
        return file.readText()
    }

    fun read(name: String): JSONArray = JSONArray(text(name))
}
