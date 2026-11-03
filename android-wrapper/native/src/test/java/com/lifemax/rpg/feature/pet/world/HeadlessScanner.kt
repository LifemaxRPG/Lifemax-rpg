package com.lifemax.rpg.feature.pet.world

import java.io.File

/**
 * The headless guarantee (plan 1.2): the cat brain and the world contract run on the JVM, a widget
 * or a wallpaper, so they and every package of this app they reach (by import, transitively, a
 * package at a time) must not name an Android or AndroidX type: no `import android...` and no fully
 * qualified use in code. Comments and strings are ignored. [scan] answers the violations as
 * "<file>: <reference>", empty when the guarantee holds.
 */
object HeadlessScanner {
    private val IMPORT = Regex("^\\s*import\\s+([A-Za-z0-9_.]+)", RegexOption.MULTILINE)
    private val ANDROID = Regex("\\b(androidx?)\\.[A-Za-z_]")
    private val COMMENT_OR_STRING =
        Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/|\"\"\"[\\s\\S]*?\"\"\"|\"(\\\\.|[^\"\\\\\\n])*\"")

    /**
     * Scans the packages [roots] (dotted names under [sourceRoot]) and every package of [appPrefix]
     * they import, transitively.
     */
    fun scan(sourceRoot: File, roots: List<String>, appPrefix: String): List<String> {
        val seen = LinkedHashSet<String>()
        val queue = ArrayDeque(roots)
        val violations = ArrayList<String>()
        while (queue.isNotEmpty()) {
            val pkg = queue.removeFirst()
            if (!seen.add(pkg)) continue
            val dir = File(sourceRoot, pkg.replace('.', '/'))
            val files =
                dir.listFiles { f -> f.isFile && f.name.endsWith(".kt") }
                    .orEmpty()
                    .sortedBy { it.name }
            if (files.isEmpty() && pkg in roots)
                violations += "$pkg: no sources (the check would prove nothing)"
            for (f in files) {
                val text = f.readText()
                val code = COMMENT_OR_STRING.replace(text, " ")
                ANDROID.findAll(code).forEach { violations += "${f.name}: ${it.value}..." }
                for (m in IMPORT.findAll(code)) {
                    val name = m.groupValues[1]
                    if (name.startsWith("$appPrefix."))
                        packageOf(name)?.let { if (it !in seen) queue += it }
                }
            }
        }
        return violations
    }

    /** The package of an imported name: the segments before the first capitalised one. */
    private fun packageOf(name: String): String? {
        val parts = name.split('.')
        val i = parts.indexOfFirst { it.firstOrNull()?.isUpperCase() == true }
        return if (i <= 0) null else parts.take(i).joinToString(".")
    }

    /** The app's authored Kotlin sources, found from wherever Gradle runs the tests. */
    fun mainSources(): File {
        val start = File(System.getProperty("user.dir")).absoluteFile
        return generateSequence(start) { it.parentFile }
            .map { File(it, "android-wrapper/native/src/main/java") }
            .firstOrNull { it.isDirectory } ?: error("native sources not found above $start")
    }
}
