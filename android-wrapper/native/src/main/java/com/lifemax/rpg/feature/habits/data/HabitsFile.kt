package com.lifemax.rpg.feature.habits.data

import com.lifemax.rpg.core.data.objects
import com.lifemax.rpg.feature.habits.domain.HabitItem
import com.lifemax.rpg.feature.habits.domain.HabitKind
import com.lifemax.rpg.feature.habits.domain.HabitsLoad
import com.lifemax.rpg.feature.habits.domain.HabitsSave
import com.lifemax.rpg.feature.habits.domain.HabitsStorage
import com.lifemax.rpg.feature.habits.domain.StackItem
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

/**
 * The habits file's format, schema 1: one JSON document, `{v, who, n, stacks:[{id,cue,treat}],
 * items:[{id,stack,kind:"build"|"fade",names:[3],size,born,pair,scar,floors}],
 * log:{"YYYY-MM-DD":{habitId:size+1}}, rough:"YYYY-MM-DD"|"", claimed:{"YYYY-MM-DD":[stackId]}}`.
 * Reading is strict: a missing key, a value of the wrong kind or facts that contradict each other
 * (a habit on a foundation that is not there) make the whole file unreadable, never half read.
 */
object HabitsCodec {
    const val SCHEMA = 1
    private val DAY = Regex("""\d{4}-\d{2}-\d{2}""")

    fun encode(s: HabitsSave): String =
        JSONObject()
            .put("v", SCHEMA)
            .put("who", s.who)
            .put("n", s.n)
            .put(
                "stacks",
                JSONArray(
                    s.stacks.map {
                        JSONObject().put("id", it.id).put("cue", it.cue).put("treat", it.treat)
                    }
                ),
            )
            .put(
                "items",
                JSONArray(
                    s.items.map {
                        JSONObject()
                            .put("id", it.id)
                            .put("stack", it.stack)
                            .put("kind", if (it.kind == HabitKind.FADE) "fade" else "build")
                            .put("names", JSONArray(it.names))
                            .put("size", it.size)
                            .put("born", it.born)
                            .put("pair", it.pair)
                            .put("scar", it.scar)
                            .put("floors", it.floors)
                    }
                ),
            )
            .put(
                "log",
                JSONObject().also { log ->
                    s.log.forEach { (day, laid) ->
                        log.put(
                            day,
                            JSONObject().also { o -> laid.forEach { (id, v) -> o.put(id, v) } },
                        )
                    }
                },
            )
            .put("rough", s.rough)
            .put(
                "claimed",
                JSONObject().also { c ->
                    s.claimed.forEach { (day, ids) -> c.put(day, JSONArray(ids)) }
                },
            )
            .toString()

    /** The schema a document says it is, or null when it is not a habits document at all. */
    fun schemaOf(text: String): Int? = runCatching { JSONObject(text).get("v") as? Int }.getOrNull()

    /** The save in [text]; throws when it is not a readable schema 1 document. */
    fun decode(text: String): HabitsSave {
        val j = JSONObject(text)
        require(j.int("v") == SCHEMA) { "Not schema $SCHEMA" }
        val stacks =
            j.getJSONArray("stacks").objects().map {
                StackItem(it.str("id"), it.str("cue"), it.str("treat"))
            }
        val stackIds = stacks.map { it.id }
        require(
            stackIds.none(String::isBlank) &&
                stackIds.size == stackIds.toSet().size &&
                stacks.none { it.cue.isBlank() }
        ) {
            "Bad foundations"
        }
        val items =
            j.getJSONArray("items").objects().map {
                val names =
                    it.getJSONArray("names").let { a ->
                        List(a.length()) { i ->
                            a.get(i) as? String
                                ?: throw IllegalArgumentException("A name is not words")
                        }
                    }
                val kind =
                    when (it.str("kind")) {
                        "build" -> HabitKind.BUILD
                        "fade" -> HabitKind.FADE
                        else -> throw IllegalArgumentException("No such kind of habit")
                    }
                HabitItem(
                    it.str("id"),
                    it.str("stack"),
                    kind,
                    names,
                    it.int("size"),
                    it.str("born"),
                    it.str("pair"),
                    it.bool("scar"),
                    it.int("floors"),
                )
            }
        val ids = items.map { it.id }
        require(ids.none(String::isBlank) && ids.size == ids.toSet().size) {
            "Each habit needs an id of its own"
        }
        require(
            items.all {
                it.stack in stackIds &&
                    it.names.size == 3 &&
                    it.names[1].isNotBlank() &&
                    it.size in 0..2 &&
                    it.floors >= 0 &&
                    DAY.matches(it.born)
            }
        ) {
            "A habit makes no sense"
        }
        val log =
            j.getJSONObject("log").let { l ->
                l.keys().asSequence().associateWith { day ->
                    val laid = l.getJSONObject(day)
                    laid.keys().asSequence().associateWith { id -> laid.int(id) }
                }
            }
        require(log.all { (day, laid) -> DAY.matches(day) && laid.values.all { it in 1..3 } }) {
            "The log makes no sense"
        }
        val rough = j.str("rough")
        require(rough.isEmpty() || DAY.matches(rough)) { "The rough day is not a day" }
        val claimed =
            j.getJSONObject("claimed").let { c ->
                c.keys().asSequence().associateWith { day ->
                    c.getJSONArray(day).let { a ->
                        List(a.length()) { i ->
                            a.get(i) as? String
                                ?: throw IllegalArgumentException("A claim is not an id")
                        }
                    }
                }
            }
        require(claimed.keys.all(DAY::matches)) { "A claim's day is not a day" }
        val n = j.int("n")
        require(n >= 0) { "The id counter is below zero" }
        return HabitsSave(j.str("who"), n, stacks, items, log, rough, claimed)
    }

    private fun JSONObject.str(key: String): String =
        get(key) as? String ?: throw IllegalArgumentException("$key is not words")

    private fun JSONObject.bool(key: String): Boolean =
        get(key) as? Boolean ?: throw IllegalArgumentException("$key is not yes or no")

    private fun JSONObject.int(key: String): Int =
        get(key) as? Int ?: throw IllegalArgumentException("$key is not a whole number")
}

/**
 * The habits file: `habits.json` in [dir] (the app's private files, under `habits/`), with the file
 * it replaced kept beside it as `habits.prev.json`, a rolling safety copy.
 *
 * Writing is atomic: the new document goes to a temporary file, is forced to the disk, the current
 * file becomes the safety copy and the temporary one takes its name, so a crash at any moment
 * leaves either the old file or the new one, never half of one. A write that fails changes nothing
 * and answers false.
 *
 * Reading never overwrites what it cannot read. A file made by a newer app is left exactly where it
 * is and Habits is blocked until the app is updated. A file that is not a habits document is moved
 * aside (`habits.unreadable-<time>.json`) and the safety copy is loaded in its place, or Habits
 * starts empty; either way the owner is told in plain words.
 */
class HabitsFile(private val dir: File, private val now: () -> Long = System::currentTimeMillis) :
    HabitsStorage {
    private val main = File(dir, NAME)
    private val previous = File(dir, PREVIOUS)

    private sealed interface Read {
        data class Good(val save: HabitsSave) : Read

        data object Newer : Read

        data object Bad : Read
    }

    private fun read(file: File): Read {
        val text =
            runCatching { file.readText() }
                .getOrElse {
                    return Read.Bad
                }
        val schema = HabitsCodec.schemaOf(text)
        if (schema != null && schema > HabitsCodec.SCHEMA) return Read.Newer
        return runCatching { Read.Good(HabitsCodec.decode(text)) }.getOrDefault(Read.Bad)
    }

    override fun load(): HabitsLoad {
        if (!main.exists()) {
            // nothing yet, or the moment between the two renames of a write: the safety copy is the
            // save
            val copy = if (previous.exists()) read(previous) else null
            return HabitsLoad.Ready((copy as? Read.Good)?.save ?: HabitsSave())
        }
        return when (val r = read(main)) {
            is Read.Good -> HabitsLoad.Ready(r.save)
            Read.Newer -> HabitsLoad.Blocked(NEWER)
            Read.Bad -> {
                val aside = File(dir, "habits.unreadable-${now()}.json")
                if (!main.renameTo(aside)) return HabitsLoad.Blocked(STUCK)
                val copy = if (previous.exists()) read(previous) else null
                if (copy is Read.Good)
                    HabitsLoad.Ready(
                        copy.save,
                        "Your habits file could not be read, so the copy from just before your last change was loaded. The unreadable file was kept as ${aside.name}.",
                    )
                else
                    HabitsLoad.Ready(
                        HabitsSave(),
                        "Your habits file could not be read. It was kept as ${aside.name}, and Habits starts empty.",
                    )
            }
        }
    }

    override fun save(save: HabitsSave): Boolean =
        try {
            if (!dir.isDirectory && !dir.mkdirs()) false
            else {
                val temp = File(dir, "$NAME.tmp")
                FileOutputStream(temp).use { out ->
                    out.write(HabitsCodec.encode(save).toByteArray(Charsets.UTF_8))
                    out.fd.sync()
                }
                // the file being replaced becomes the safety copy, then the new one takes its place
                if (main.exists() && !main.renameTo(previous)) {
                    temp.delete()
                    false
                } else if (!temp.renameTo(main)) {
                    previous.renameTo(main)
                    temp.delete()
                    false
                } else true
            }
        } catch (e: Exception) {
            false
        }

    companion object {
        const val NAME = "habits.json"
        const val PREVIOUS = "habits.prev.json"
        const val NEWER =
            "Your habits were saved by a newer version of LifeMax. Update the app to see and change them. Nothing has been touched."
        const val STUCK =
            "Your habits file could not be read, and could not be set aside either. Nothing has been touched."
    }
}
