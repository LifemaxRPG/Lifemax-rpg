package com.lifemax.rpg.app.shell.audio

/**
 * What the native file picker asks the system for. `.m4b` is often reported as one of the last two
 * (or as nothing the system knows), so the list is wide and [AudioAccept] decides from the name.
 */
val AUDIO_PICKER_TYPES =
    arrayOf(
        "audio/*",
        "audio/mp4",
        "audio/x-m4a",
        "audio/x-m4b",
        "audio/mpeg",
        "application/octet-stream",
    )

/**
 * The page's accept list for audio files (the `accept` of its audio file input, which the page
 * hands over; nothing here repeats it): entries such as ".m4b", "audio/mp4" or a whole family
 * ("audio/" and a star). A file is allowed as a browser's file input allows it: by its name's
 * ending, or by its type. A type the system could not name (octet-stream, none) is allowed by its
 * name alone.
 */
class AudioAccept(entries: List<String>) {
    private val endings = entries.filter { it.startsWith(".") }.map { it.lowercase() }
    private val types = entries.filter { it.contains('/') }.map { it.lowercase() }

    fun allows(name: String, mime: String?): Boolean {
        val lower = name.lowercase()
        if (endings.any { lower.endsWith(it) }) return true
        val type = mime?.lowercase().orEmpty()
        if (type.isEmpty() || type == "application/octet-stream") return false
        return types.any { it == type || (it.endsWith("/*") && type.startsWith(it.dropLast(1))) }
    }

    companion object {
        /** From the attribute's text, as the page writes it: entries separated by commas. */
        fun of(attribute: String) =
            AudioAccept(attribute.split(',').map { it.trim() }.filter { it.isNotEmpty() })
    }
}

/**
 * The address under which the page can read a content document: Capacitor's local server opens
 * `/_capacitor_content_/<authority>/<path>` as the document at `content://<authority>/<path>`
 * (WebViewLocalServer). [origin] is the page's own, such as `http://localhost`.
 */
fun capacitorContentUrl(origin: String, contentUri: String): String {
    require(contentUri.startsWith(CONTENT)) { "Not a content address" }
    return origin.trimEnd('/') + "/_capacitor_content_/" + contentUri.removePrefix(CONTENT)
}

private const val CONTENT = "content://"
