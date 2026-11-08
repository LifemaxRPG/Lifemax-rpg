package com.lifemax.rpg.core.save

/**
 * The three-way merge that writes native's changes into the stored save without touching anything
 * else. Three trees take part:
 * - `stored`: the save as it was read, with every field native does not model;
 * - `before`: what native's model wrote for that save when it was read (only the modelled fields);
 * - `after`: what the model writes now.
 *
 * Where `before` and `after` agree, the stored value is kept exactly (same text, same keys, same
 * odd types the page tolerates). Where they differ, the stored value is changed only there:
 * - objects merge key by key; a key the model does not write is never touched;
 * - a key the model wrote before and no longer writes is removed (a field the economy deletes);
 * - arrays are aligned element by element ([alignment]) so a changed record keeps the keys native
 *   does not model;
 * - anything else takes the new value; a key the stored save lacks is added only when the model has
 *   a value for it ([UNSET] stays missing).
 *
 * `before` must have been written from a decoding of `stored`, so that `before`'s arrays have the
 * same length and order as the stored ones.
 */
internal class JsonMerge {
    /**
     * Paths where the model introduced a NaN or infinite number ([JsonWriter] would write null).
     */
    val nonFinite = mutableListOf<String>()

    fun merge(stored: JsonValue?, before: JsonValue?, after: JsonValue?, path: String): JsonValue? {
        if (after == null) return if (before == null) stored else null
        if (before != null && before == after) return stored
        return when {
            stored is JsonObject && before is JsonObject && after is JsonObject ->
                mergeObject(stored, before, after, path)
            stored is JsonArray && before is JsonArray && after is JsonArray ->
                mergeArray(stored, before, after, path)
            stored == null -> freshMember(after, path)
            else -> fresh(after, path)
        }
    }

    private fun mergeObject(
        stored: JsonObject,
        before: JsonObject,
        after: JsonObject,
        path: String,
    ): JsonObject {
        val out = LinkedHashMap<String, JsonValue>()
        for ((key, value) in stored.members) {
            if (key !in before.members && key !in after.members) {
                out[key] = value
                continue
            }
            merge(value, before[key], after[key], "$path.$key")?.let { out[key] = it }
        }
        for ((key, value) in after.members) {
            if (key in stored.members) continue
            // The stored save lacks the key: keep it lacking unless the model changed it.
            merge(null, before[key], value, "$path.$key")?.let { out[key] = it }
        }
        return JsonObject(out)
    }

    private fun mergeArray(
        stored: JsonArray,
        before: JsonArray,
        after: JsonArray,
        path: String,
    ): JsonValue {
        // Cannot happen when `before` was written from `stored`; if it ever did, nothing is
        // aligned.
        if (stored.items.size != before.items.size) return fresh(after, path)
        val match = alignment(before.items, after.items)
        return JsonArray(
            after.items.mapIndexed { j, item ->
                val i = match[j]
                if (i == null) fresh(item, "$path[$j]")
                else merge(stored.items[i], before.items[i], item, "$path[$j]")!!
            }
        )
    }

    /**
     * A value only the model wrote. An [UNSET] object member is left out, so it reads back as the
     * same "not set"; any other NaN or infinity (an [UNSET] included, where a stored value was) is
     * recorded in [nonFinite]: the page would store it as null.
     */
    private fun fresh(value: JsonValue, path: String): JsonValue =
        when (value) {
            is JsonNumber -> value.also { if (!it.value.isFinite()) nonFinite += path }
            is JsonObject ->
                JsonObject(
                    LinkedHashMap<String, JsonValue>().apply {
                        value.members.forEach { (k, v) ->
                            freshMember(v, "$path.$k")?.let { put(k, it) }
                        }
                    }
                )
            is JsonArray -> JsonArray(value.items.mapIndexed { i, v -> fresh(v, "$path[$i]") })
            else -> value
        }

    /** A new object member: [UNSET] is left out, anything else as [fresh]. */
    private fun freshMember(value: JsonValue, path: String): JsonValue? =
        if (value === UNSET) null else fresh(value, path)

    companion object {
        /**
         * What the model writes for a number it holds as NaN because the key was missing (NUM-3):
         * "not set". Told apart from a computed NaN by identity, never by value.
         */
        val UNSET = JsonNumber(Double.NaN)

        /**
         * For each element of [after], the index of the element of [before] it continues, or null
         * for a new element. Chosen in this order:
         * 1. when every element on both sides is an object with a string `id` (activities, quests,
         *    money transactions): the n-th element with an id continues the n-th element before
         *    with that id, so a removal, an insertion or an edit anywhere is followed;
         * 2. else an element equal to one before continues the first unused equal one, in order
         *    (decay log, granted permissions, owned perks);
         * 3. and when nothing matched that way and the lengths agree, elements pair by position:
         *    the list was edited in place (an attribute split re-weighted). An element left
         *    unmatched is new and written whole.
         */
        fun alignment(before: List<JsonValue>, after: List<JsonValue>): List<Int?> {
            val beforeIds = before.map(::idOf)
            val afterIds = after.map(::idOf)
            if (beforeIds.all { it != null } && afterIds.all { it != null }) {
                return pair(beforeIds, afterIds)
            }
            val byValue = pair(before, after)
            if (byValue.all { it == null } && before.size == after.size)
                return after.indices.toList()
            return byValue
        }

        private fun idOf(value: JsonValue): String? =
            ((value as? JsonObject)?.get("id") as? JsonString)?.value

        /** The n-th occurrence of a key on the right continues the n-th occurrence on the left. */
        private fun <K> pair(before: List<K>, after: List<K>): List<Int?> {
            val pool = LinkedHashMap<K, ArrayDeque<Int>>()
            before.forEachIndexed { i, key -> pool.getOrPut(key) { ArrayDeque() }.addLast(i) }
            return after.map { key -> pool[key]?.removeFirstOrNull() }
        }
    }
}
