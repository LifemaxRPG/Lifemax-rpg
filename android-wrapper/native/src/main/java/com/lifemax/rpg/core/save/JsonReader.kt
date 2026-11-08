package com.lifemax.rpg.core.save

/** What reading a JSON text gave: the value, or where and why the text is not JSON. */
sealed interface JsonRead {
    /** The text is JSON; [value] is what `JSON.parse` would make of it. */
    data class Parsed(val value: JsonValue) : JsonRead

    /**
     * The text is not JSON that `JSON.parse` accepts, or nests deeper than [JsonReader.MAX_DEPTH].
     * [offset] is the UTF-16 index of the problem; [reason] names it without quoting the text.
     */
    data class Malformed(val offset: Int, val reason: String) : JsonRead
}

/**
 * Reads JSON text exactly as `JSON.parse` does (ECMA-404): the four whitespace characters only, no
 * byte order mark, no comments, no trailing commas, no `NaN` or `Infinity`, numbers without leading
 * zeros, every `\u` escape kept as the UTF-16 unit it names (lone surrogates included), and a
 * repeated object key keeping its first place with its last value.
 *
 * Numbers become the nearest double, as in JavaScript (`1e400` is Infinity, `-0` is minus zero),
 * and keep their text so that writing them back is byte for byte.
 *
 * Never throws: every failure is a [JsonRead.Malformed].
 */
object JsonReader {
    /**
     * The deepest nesting accepted. A stored save nests about six levels; the limit keeps a hostile
     * text from exhausting the stack, and such a text is refused rather than misread.
     */
    const val MAX_DEPTH = 512

    fun read(text: String): JsonRead =
        try {
            val cursor = Cursor(text)
            cursor.skipSpace()
            val value = cursor.value(0)
            cursor.skipSpace()
            if (cursor.index != text.length) cursor.fail("unexpected text after the value")
            JsonRead.Parsed(value)
        } catch (failure: MalformedText) {
            JsonRead.Malformed(failure.offset, failure.reason)
        }

    private class MalformedText(val offset: Int, val reason: String) :
        RuntimeException(reason, null, false, false)

    private class Cursor(private val text: String) {
        var index = 0

        fun fail(reason: String): Nothing = throw MalformedText(index, reason)

        fun skipSpace() {
            while (index < text.length) {
                when (text[index]) {
                    ' ',
                    '\t',
                    '\n',
                    '\r' -> index++
                    else -> return
                }
            }
        }

        private fun peek(): Char = if (index < text.length) text[index] else fail("text ends early")

        private fun expect(word: String) {
            if (!text.startsWith(word, index)) fail("unknown literal")
            index += word.length
        }

        fun value(depth: Int): JsonValue {
            if (depth > MAX_DEPTH) fail("nested deeper than $MAX_DEPTH levels")
            return when (peek()) {
                '{' -> objectValue(depth)
                '[' -> arrayValue(depth)
                '"' -> JsonString(string())
                't' -> JsonBool.TRUE.also { expect("true") }
                'f' -> JsonBool.FALSE.also { expect("false") }
                'n' -> JsonNull.also { expect("null") }
                else -> number()
            }
        }

        private fun objectValue(depth: Int): JsonObject {
            index++
            val members = LinkedHashMap<String, JsonValue>()
            skipSpace()
            if (peek() == '}') {
                index++
                return JsonObject(members)
            }
            while (true) {
                skipSpace()
                if (peek() != '"') fail("expected a key")
                val key = string()
                skipSpace()
                if (peek() != ':') fail("expected ':'")
                index++
                skipSpace()
                members[key] = value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> index++
                    '}' -> {
                        index++
                        return JsonObject(members)
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun arrayValue(depth: Int): JsonArray {
            index++
            val items = ArrayList<JsonValue>()
            skipSpace()
            if (peek() == ']') {
                index++
                return JsonArray(items)
            }
            while (true) {
                skipSpace()
                items += value(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> index++
                    ']' -> {
                        index++
                        return JsonArray(items)
                    }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun string(): String {
            index++
            val out = StringBuilder()
            while (true) {
                val c = peek()
                when {
                    c == '"' -> {
                        index++
                        return out.toString()
                    }
                    c == '\\' -> {
                        index++
                        out.append(escape())
                    }
                    c < ' ' -> fail("control character in a string")
                    else -> {
                        out.append(c)
                        index++
                    }
                }
            }
        }

        private fun escape(): Char {
            val c = peek()
            index++
            return when (c) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (index + 4 > text.length) fail("short \\u escape")
                    var unit = 0
                    repeat(4) {
                        val digit =
                            when (val h = text[index]) {
                                in '0'..'9' -> h - '0'
                                in 'a'..'f' -> h - 'a' + 10
                                in 'A'..'F' -> h - 'A' + 10
                                else -> fail("bad \\u escape")
                            }
                        unit = unit * 16 + digit
                        index++
                    }
                    unit.toChar()
                }
                else -> {
                    index--
                    fail("unknown escape")
                }
            }
        }

        /** `-? (0 | [1-9][0-9]*) (. [0-9]+)? ([eE] [+-]? [0-9]+)?`, checked before converting. */
        private fun number(): JsonNumber {
            val start = index
            if (index < text.length && text[index] == '-') index++
            when {
                index < text.length && text[index] == '0' -> index++
                index < text.length && text[index] in '1'..'9' -> digits()
                else -> fail("expected a value")
            }
            if (index < text.length && text[index] == '.') {
                index++
                if (digits() == 0) fail("expected digits after '.'")
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                if (digits() == 0) fail("expected exponent digits")
            }
            val lexeme = text.substring(start, index)
            return JsonNumber(lexeme.toDouble(), lexeme)
        }

        private fun digits(): Int {
            val start = index
            while (index < text.length && text[index] in '0'..'9') index++
            return index - start
        }
    }
}
