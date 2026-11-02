package com.lifemax.rpg.feature.library.domain

/*
 * The Media sheets (plan JOURNAL_AND_LIBRARY_SHEETS §3.4, phase 3): the page's add/edit form and its two confirms, drawn by the
 * screen when the page says so (edit "native"), and the commands that write through the page's own owners. Every word is the
 * page's; the screen decides nothing about what may be stored beyond the page's own "required" words for the title.
 */

/** A text box of the page's form: its label, placeholder and length, and its words when empty. */
data class MediaTextField(
    val label: String,
    val placeholder: String,
    val maxLength: Int,
    /** The page's words for the box left empty; null when it may be. */
    val required: String? = null,
)

/** A select of the page's form: its label and options, the values the page stores. */
data class MediaChoiceField(val label: String, val options: List<FilterOption>)

/** A confirm: the page's question, with {title} for the item's title, and its confirming button. */
data class MediaAsk(val text: String, val confirm: String) {
    /** The question about [title], as the page's confirm asks it. */
    fun about(title: String): String = text.replace("{title}", title)
}

/** Which of the page's confirms. */
enum class MediaAskKind {
    REMOVE,
    REMOVE_AUDIO,
}

/**
 * The page's add/edit form and its confirms. [links] holds the stored link of an item whose link
 * the rows do not show (not http or https), by id, so an edit starts from what the page's form
 * shows instead of dropping it.
 */
data class MediaForm(
    val addHeading: String,
    val editHeading: String,
    val save: String,
    val cancel: String,
    val title: MediaTextField,
    val type: MediaChoiceField,
    val creator: MediaTextField,
    val status: MediaChoiceField,
    val link: MediaTextField,
    val notes: MediaTextField,
    /** What a new item starts as, as the page's Add fills its form. */
    val defaultType: String,
    val defaultStatus: String,
    val links: Map<String, String>,
    val remove: MediaAsk,
    val removeAudio: MediaAsk,
    val askCancel: String,
) {
    fun ask(kind: MediaAskKind): MediaAsk =
        when (kind) {
            MediaAskKind.REMOVE -> remove
            MediaAskKind.REMOVE_AUDIO -> removeAudio
        }

    /** The form filled for [item], as the page's Edit fills it. */
    fun draftOf(item: MediaItem): MediaFields =
        MediaFields(
            item.title,
            item.type,
            item.creator,
            item.status,
            links[item.id] ?: item.link,
            item.notes,
        )

    /** The empty form, as the page's Add fills it. */
    fun emptyDraft(): MediaFields = MediaFields("", defaultType, "", defaultStatus, "", "")
}

/** The form's six values, the page's names for them. */
data class MediaFields(
    val title: String,
    val type: String,
    val creator: String,
    val status: String,
    val link: String,
    val notes: String,
) {
    fun with(field: MediaField, value: String): MediaFields =
        when (field) {
            MediaField.TITLE -> copy(title = value)
            MediaField.TYPE -> copy(type = value)
            MediaField.CREATOR -> copy(creator = value)
            MediaField.STATUS -> copy(status = value)
            MediaField.LINK -> copy(link = value)
            MediaField.NOTES -> copy(notes = value)
        }

    operator fun get(field: MediaField): String =
        when (field) {
            MediaField.TITLE -> title
            MediaField.TYPE -> type
            MediaField.CREATOR -> creator
            MediaField.STATUS -> status
            MediaField.LINK -> link
            MediaField.NOTES -> notes
        }
}

/** A field of the form; [wire] is the page's name for it, as a refusal names it. */
enum class MediaField(val wire: String) {
    TITLE("title"),
    TYPE("type"),
    CREATOR("creator"),
    STATUS("status"),
    LINK("link"),
    NOTES("notes");

    companion object {
        fun of(wire: String): MediaField? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * The page's answer to a Media command. [field] names the form field a refusal is about ("" when
 * none); [id] is the item an upsert stored.
 */
data class MediaReply(
    val command: String,
    val requestId: String,
    val ok: Boolean,
    val changed: Boolean,
    val message: String,
    val id: String = "",
    val field: String = "",
)

/**
 * The Media sheets' writes, sent to the page as commands that run its own owners without closing
 * the screen. Each returns the request id the page's reply will carry.
 */
interface MediaCommands {
    /** An add ([id] null) with its operation id [op], or an edit of item [id]. */
    fun upsert(op: String, id: String?, fields: MediaFields): String

    fun setStatus(id: String, status: String): String

    fun remove(id: String): String

    fun removeAudio(id: String): String

    /** No page to write to (previews, screenshots). */
    object None : MediaCommands {
        override fun upsert(op: String, id: String?, fields: MediaFields) = ""

        override fun setStatus(id: String, status: String) = ""

        override fun remove(id: String) = ""

        override fun removeAudio(id: String) = ""
    }
}

/** The page's status words a tick sends: finished, or back to in progress (as the page's tick). */
object MediaStatus {
    const val FINISHED = "finished"
    const val PROGRESS = "progress"
}
