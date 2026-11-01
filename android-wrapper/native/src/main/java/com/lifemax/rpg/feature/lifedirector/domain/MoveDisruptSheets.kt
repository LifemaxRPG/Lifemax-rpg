package com.lifemax.rpg.feature.lifedirector.domain

/** The disrupt sheet's kind; Move's is [SheetKinds.MOVE]. */
const val DISRUPT_SHEET = "disrupt"

/** An open time a session can move to: the page's label ("Sat 9:30 AM") and its start (epoch s). */
data class MoveTime(val label: String, val start: Long)

/**
 * The page's Move sheet (moveModel): up to 48 open times that fit the session, or [none] when there
 * are none. Picking one is Today's `moveTo` with its start.
 */
data class MoveSheetModel(
    override val id: String,
    val title: String,
    val note: String,
    val times: List<MoveTime>,
    val none: String,
) : SheetModel {
    override val kind
        get() = SheetKinds.MOVE
}

/** One field of a disrupt form, in the page's words; [group] is its row of two (0 or 1). */
sealed interface DisruptInput {
    val key: String
    val label: String
    val group: Int
}

/** A date field; [value] is "YYYY-MM-DD". */
data class DisruptDate(
    override val key: String,
    override val label: String,
    val value: String,
    override val group: Int,
) : DisruptInput

/** A number field with the page's [min] and [step] (either may be absent). */
data class DisruptNumber(
    override val key: String,
    override val label: String,
    val value: Int,
    val min: Int?,
    val step: Int?,
    override val group: Int,
) : DisruptInput

/** A time field: [value] in minutes after midnight, [text] as the page's clock writes it. */
data class DisruptTime(
    override val key: String,
    override val label: String,
    val value: Int,
    val text: String,
    override val group: Int,
) : DisruptInput

/** A choice of a section: its [value] goes back as it came (a whole number or a word). */
data class DisruptOption(val value: Any, val label: String)

/**
 * One fold of "Something came up" (disruptParts): the Today [command] it sends ("disrupt" with the
 * section's [key] as its kind, or "energy"), and what it asks for: a [form] with its [button], a
 * row of [options] whose value goes as [param], or a lone [button].
 */
data class DisruptSection(
    val key: String,
    val title: String,
    val open: Boolean,
    val command: String,
    val param: String?,
    val form: List<DisruptInput>,
    val options: List<DisruptOption>,
    val button: String?,
    val note: String,
) {
    /**
     * Today's payload for this section: its kind for a disrupt, the form's values (as [values]
     * holds them, keyed by field) and the chosen [option]'s value under [param].
     */
    fun payload(values: Map<String, Any> = emptyMap(), option: DisruptOption? = null) =
        buildMap<String, Any> {
            if (command == DISRUPT_SHEET) put("kind", key)
            form.forEach { f -> values[f.key]?.let { put(f.key, it) } }
            if (option != null && param != null) put(param, option.value)
        }
}

/** The page's "Something came up" sheet: its title, note and folds in the page's order. */
data class DisruptSheetModel(
    val title: String,
    val note: String,
    val sections: List<DisruptSection>,
) : SheetModel {
    override val kind
        get() = DISRUPT_SHEET

    override val id
        get() = ""
}
