package com.lifemax.rpg.feature.library.domain

/** One side of the Library's Media/Journal switch. */
data class LibraryPane(val id: String, val label: String, val current: Boolean)

/** The page head of a Library pane, and the words of its add button. */
data class LibraryHeader(
    val eyebrow: String,
    val title: String,
    val blurb: String,
    val add: String,
)

/** The page's feature tip for a pane (FEATURE_TIPS); [dismiss] names its ✕ for TalkBack. */
data class FeatureTip(val icon: String, val title: String, val body: String, val dismiss: String)

/**
 * A stat card. [value] is written as the page writes it ("17%"); [foot] is empty where the card has
 * none; [bar] is the percent the Completion card's bar shows, null on the others.
 */
data class MediaStat(val label: String, val value: String, val foot: String, val bar: Int?)

data class FilterOption(val id: String, val label: String)

/** A select of the filter: its label, and its options with the values the filter compares. */
data class FilterControl(val label: String, val options: List<FilterOption>) {
    fun label(id: String): String = options.first { it.id == id }.label

    operator fun contains(id: String): Boolean = options.any { it.id == id }
}

/** How far into its audio an item is: the bar's percent, the page's words, position / duration. */
data class AudioProgress(val pct: Double, val label: String, val time: String)

/** An item's audio. [file] is empty while the item has progress but no file (Attach shows). */
data class MediaAudio(val file: String, val meta: String, val progress: AudioProgress)

/**
 * A row of the list. [type] and [status] are as stored, so the filter compares what the page's
 * does; [subtitle] and [statusLabel] are the page's words for them. [link] is empty unless it is
 * http or https.
 */
data class MediaItem(
    val id: String,
    val title: String,
    val type: String,
    val creator: String,
    val subtitle: String,
    val status: String,
    val statusLabel: String,
    val finished: Boolean,
    val notes: String,
    val link: String,
    val audio: MediaAudio?,
)

/** What the card shows in place of rows; [add] is empty when it has no button. */
data class ListMessage(val icon: String, val title: String, val body: String, val add: String)

/**
 * The page's message while its toast shows (the toast is under the screen, so the screen draws it).
 * [id] counts the page's toasts, so the same words twice are two messages.
 */
data class PageNotice(val id: Long, val text: String)

/** The words of a row's buttons. */
data class MediaButtons(
    val edit: String,
    val remove: String,
    val play: String,
    val replace: String,
    val removeAudio: String,
    val attach: String,
    val link: String,
)

/**
 * The Media pane's search and filters. [matching] is the page's mediaMatching: an item shows when
 * its stored status and type are the ones chosen (or "all") and the search, trimmed and in lower
 * case, is a piece of its title, creator, notes or type; the list keeps its order.
 */
data class MediaFilter(val search: String, val status: String, val type: String) {
    private val query = search.trim(Char::isJsSpace).lowercase()

    fun matches(item: MediaItem): Boolean =
        (status == ALL || item.status == status) &&
            (type == ALL || item.type == type) &&
            listOf(item.title, item.creator, item.notes, item.type).any {
                it.lowercase().contains(query)
            }

    fun matching(items: List<MediaItem>): List<MediaItem> = items.filter(::matches)

    companion object {
        /** The option of either select that lets everything through. */
        const val ALL = "all"
    }
}

/** What JavaScript's trim removes: its white space and line terminators. */
private fun Char.isJsSpace(): Boolean =
    this in "\t\n\u000B\u000C\r\u2028\u2029\uFEFF" ||
        Character.getType(this) == Character.SPACE_SEPARATOR.toInt()

data class MediaProjection(
    val session: String,
    val sequence: Long,
    val panes: List<LibraryPane>,
    val header: LibraryHeader,
    val tip: FeatureTip?,
    val stats: List<MediaStat>,
    val searchLabel: String,
    val searchPlaceholder: String,
    val statusFilter: FilterControl,
    val typeFilter: FilterControl,
    /** The page's filter; [filterRevision] rises whenever the page sets it. */
    val filter: MediaFilter,
    val filterRevision: Long,
    /** In stored order, which is the list's order. */
    val items: List<MediaItem>,
    val empty: ListMessage,
    val noMatch: ListMessage,
    val buttons: MediaButtons,
    val notice: PageNotice?,
    val tokens: Map<String, Long>,
    val light: Boolean,
)
