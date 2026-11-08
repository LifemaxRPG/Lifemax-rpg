package com.lifemax.rpg.core.golden

import java.io.File
import java.util.zip.GZIPInputStream
import org.json.JSONArray

/**
 * Golden cases, read in place from lifemax_modular_v73/tests/fixtures. The file is never copied:
 * the web test keeps only the original true. Gradle runs unit tests from
 * android-wrapper/android/app, so the search walks up. A name ending in `.gz` is unzipped as it is
 * read (the large proof corpora are kept zipped).
 */
object GoldenFiles {
    /** The file's text, one case per line. */
    fun text(name: String): String {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val file =
            generateSequence(start) { it.parentFile }
                .map { File(it, "lifemax_modular_v73/tests/fixtures/$name") }
                .firstOrNull { it.isFile } ?: error("$name not found above $start")
        return textOf(file)
    }

    /** A file's text, unzipped when its name ends in `.gz`. */
    fun textOf(file: File): String =
        if (file.name.endsWith(".gz"))
            GZIPInputStream(file.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }
        else file.readText()

    fun read(name: String): JSONArray = JSONArray(text(name))
}
