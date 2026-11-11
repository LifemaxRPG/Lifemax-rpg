package com.lifemax.rpg.core.directorshadow

import com.lifemax.rpg.core.save.JsonString
import com.lifemax.rpg.core.save.JsonWriter

/**
 * One change the page sends (SH-6): at [path] (object member names and list indexes), [kind] is `o`
 * (an object split into members, [keys] in the page's order), `a` (a list split into items,
 * [length] long) or `t` (a value as the page's exact JSON [text]).
 */
data class TextOp(
    val kind: Char,
    val path: List<Any>,
    val keys: List<String> = emptyList(),
    val length: Int = 0,
    val text: String = "",
)

/**
 * Native's copy of the page's Life Director state, as the page's own JSON texts (SH-5 to SH-8): the
 * state is split where it grows (the plan's sessions and history, the ledger's events and order,
 * the change log), every other value is the page's `JSON.stringify` text of it, and [stitch] puts
 * the parts together in the page's order, giving exactly the text the page's store writes for the
 * same state. Memory only; never written anywhere.
 */
class TextMirror {
    private sealed interface Node

    private class Leaf(val text: String) : Node

    private class Obj(var keys: List<String>, val kids: MutableMap<String, Node>) : Node

    private class Arr(val items: MutableList<Node?>) : Node

    private var root: Node? = null

    /** The sequence number of the envelope (or base) the copy is at; 0 when there is none. */
    var seq: Long = 0
        private set

    val empty: Boolean
        get() = root == null

    fun discard() {
        root = null
        seq = 0
    }

    /**
     * Applies [ops] in order (a base starts from nothing); false, with the copy dropped, when one
     * names a place the copy does not have (SH-7).
     */
    fun apply(ops: List<TextOp>, seq: Long, base: Boolean): Boolean {
        if (base) root = null
        for (op in ops) {
            if (!applyOne(op)) {
                discard()
                return false
            }
        }
        this.seq = seq
        return true
    }

    private fun applyOne(op: TextOp): Boolean {
        val made: (Node?) -> Node =
            when (op.kind) {
                'o' -> { old ->
                        if (old is Obj) {
                            old.kids.keys.retainAll(op.keys.toSet())
                            old.keys = op.keys
                            old
                        } else Obj(op.keys, HashMap())
                    }
                'a' -> { old ->
                        if (old is Arr) {
                            while (old.items.size > op.length) old.items.removeAt(
                                old.items.size - 1
                            )
                            while (old.items.size < op.length) old.items.add(null)
                            old
                        } else Arr(MutableList(op.length) { null })
                    }
                't' -> { _ -> Leaf(op.text) }
                else -> return false
            }
        if (op.path.isEmpty()) {
            if (op.kind == 't') return false // the state itself is always split into its members
            root = made(root)
            return true
        }
        var node: Node = root ?: return false
        for (step in op.path.dropLast(1)) node = child(node, step) ?: return false
        val last = op.path.last()
        return when (node) {
            is Obj -> {
                if (last !is String || last !in node.keys) return false
                node.kids[last] = made(node.kids[last])
                true
            }
            is Arr -> {
                if (last !is Int || last < 0 || last >= node.items.size) return false
                node.items[last] = made(node.items[last])
                true
            }
            is Leaf -> false
        }
    }

    private fun child(node: Node, step: Any): Node? =
        when (node) {
            is Obj -> (step as? String)?.let { node.kids[it] }
            is Arr -> (step as? Int)?.let { node.items.getOrNull(it) }
            is Leaf -> null
        }

    /**
     * The whole state's text, as the page's `JSON.stringify` writes it, or null while a part is
     * missing (a change named a member or item and no text followed) or past [limit] characters.
     */
    fun stitch(limit: Int = DirectorShadowLimits.MAX_STATE_CHARS): String? {
        val out = StringBuilder()
        return if (write(root ?: return null, out, limit)) out.toString() else null
    }

    private fun write(node: Node, out: StringBuilder, limit: Int): Boolean {
        when (node) {
            is Leaf -> out.append(node.text)
            is Obj -> {
                out.append('{')
                node.keys.forEachIndexed { i, k ->
                    if (i > 0) out.append(',')
                    out.append(JsonWriter.write(JsonString(k))).append(':')
                    if (!write(node.kids[k] ?: return false, out, limit)) return false
                }
                out.append('}')
            }
            is Arr -> {
                out.append('[')
                node.items.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    if (!write(item ?: return false, out, limit)) return false
                }
                out.append(']')
            }
        }
        return out.length <= limit
    }
}
