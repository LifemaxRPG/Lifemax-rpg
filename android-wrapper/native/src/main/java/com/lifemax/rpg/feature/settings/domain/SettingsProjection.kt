package com.lifemax.rpg.feature.settings.domain

/**
 * The page's own Settings as 50-native-settings.js reads it, card by card and row by row, worded by
 * the page. The page owns every setting: the screen draws this and asks the page to change a
 * setting (a [SettingsCommand]) or to show a control itself ([SettingsPageAction.Reveal]).
 */
data class SettingsProjection(
    val session: String,
    val sequence: Long,
    val tokens: Map<String, Long>,
    val light: Boolean,
    val head: SettingsHead,
    /** The page's category chips, in its order. */
    val categories: List<SettingsCategory>,
    /** The chip the page has selected. */
    val category: String,
    /** The screen's own way back to the page, always shown. */
    val classic: ClassicRow,
    /** Every card in the page's order; [cardsIn] says which a category shows. */
    val cards: List<SettingsCard>,
    /** The page's toast while it shows; [PageNotice.id] rises with each toast. */
    val notice: PageNotice?,
) {
    /** The cards the page shows on [category]: its own, and those of no category. */
    fun cardsIn(category: String): List<SettingsCard> =
        cards.filter { it.category == category || it.category.isEmpty() }
}

data class SettingsHead(val eyebrow: String, val title: String, val blurb: String)

data class SettingsCategory(val id: String, val name: String)

data class ClassicRow(val label: String, val hint: String)

/** The page's toast: [id] tells a new message from the same words again. */
data class PageNotice(val id: Long, val text: String)

/** A card: shown when its [category] is the selected one, or on every category when empty. */
data class SettingsCard(
    val id: String,
    val title: String,
    /** The muted line under the title; empty when there is none. */
    val intro: String,
    val category: String,
    val rows: List<SettingsRow>,
)

/** A row of a card, by the page's kinds of row. Text keeps the page's line breaks ("\n"). */
sealed interface SettingsRow {
    /** A .settingsline: [title] (may be empty) and [hint] on the left, [controls] on the right. */
    data class Line(
        val title: String,
        val hint: String,
        /** The title in the bad colour (Delete all local data). */
        val danger: Boolean,
        val controls: List<SettingsControl>,
    ) : SettingsRow

    /** A .note box, [warn] with the warning border; its [controls] are buttons. */
    data class Note(
        val text: String,
        val warn: Boolean,
        val controls: List<SettingsControl.Button>,
    ) : SettingsRow

    /** A muted paragraph. */
    data class Text(val text: String) : SettingsRow

    /** Controls standing on their own: buttons, or the haptic strength. */
    data class Controls(val controls: List<SettingsControl>) : SettingsRow

    /** A form field: [label] above its fields or select. */
    data class Field(val label: String, val controls: List<SettingsControl>) : SettingsRow

    /** A bottom-bar row: the tab, its arrows (none for a tab not in the bar) and its box. */
    data class Tab(
        val view: String,
        val label: String,
        /** A PixelIcon name, or empty. */
        val icon: String,
        val visible: Boolean,
        val earlier: SettingsControl.Button?,
        val later: SettingsControl.Button?,
        val show: SettingsControl.Switch,
    ) : SettingsRow
}

/** How a control is operated: a command the page runs while the screen stays, or a reveal. */
enum class ControlOp {
    Toggle,
    Choose,
    Press,
    /** The screen closes and the page shows the control, focused, for the owner to use. */
    Reveal,
}

/**
 * A control of the page. [id] is opaque: it is only ever sent back to the page. [label] is what
 * TalkBack calls it.
 */
sealed interface SettingsControl {
    val id: String
    val enabled: Boolean
    val op: ControlOp

    /** The page's tick box (.check). */
    data class Switch(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val on: Boolean,
    ) : SettingsControl

    /** A select: [value] is one of [options] as the page holds it. */
    data class Select(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val value: String,
        val options: List<SettingsOption>,
    ) : SettingsControl

    /** The haptic strength: a small button per option, the chosen one primary. */
    data class Choice(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val value: String,
        val options: List<SettingsOption>,
    ) : SettingsControl

    /** A button: [label] is its text, [description] what TalkBack says when not empty. */
    data class Button(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val description: String,
        val style: ButtonStyle,
        val small: Boolean,
    ) : SettingsControl

    /** A text, number or time field, typed into on the page. */
    data class Field(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val value: String,
        val placeholder: String,
        val input: FieldInput,
    ) : SettingsControl

    /** The profile picture: [image] is a data URL or empty, [initial] shows without one. */
    data class Avatar(
        override val id: String,
        override val enabled: Boolean,
        override val op: ControlOp,
        val label: String,
        val image: String,
        val initial: String,
    ) : SettingsControl
}

data class SettingsOption(val value: String, val label: String)

enum class ButtonStyle {
    Primary,
    Secondary,
    Danger,
}

enum class FieldInput {
    Text,
    Number,
    Time,
}
