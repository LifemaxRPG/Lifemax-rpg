package com.lifemax.rpg.core.designsystem.form

/*
 * A form the page describes as data (plan §3.4, "form schema"): the fields of a sheet in the page's order, with the
 * page's labels, limits, defaults and words. The screen draws it (SchemaForm) and keeps what the person types as a
 * map of values by key; it decides nothing the page has not said. The decoder that builds it from the projection
 * is feature/lifedirector/data/FormSchemaJson.kt, strict as every projection decoder is.
 */

/**
 * What a field holds and how it is drawn. The wire names are the plan's: text, number, time, date,
 * select, check, days.
 */
enum class FieldKind {
    TEXT,
    NUMBER,
    TIME,
    DATE,
    CHOICE,
    CHECK,
    DAYS,
}

/** One choice of a select or one day of day chips: what is sent and what is shown. */
data class FormOption(val value: String, val label: String)

/** A field's value, of the kind its field holds. */
sealed interface FieldValue {
    data class Text(val text: String) : FieldValue

    /** A number; null for a box the page leaves empty (it shows a placeholder there). */
    data class Number(val number: Int?) : FieldValue

    /** Minutes after local midnight, 0..1439 (A18). */
    data class Time(val minutes: Int) : FieldValue

    /** "YYYY-MM-DD", or "" for a date not given (A18). */
    data class Date(val date: String) : FieldValue

    data class Choice(val value: String) : FieldValue

    data class Check(val on: Boolean) : FieldValue

    /** The days picked, as the page numbers them (0 is Sunday, DOW in logic.js). */
    data class Days(val days: Set<Int>) : FieldValue
}

/**
 * One field. [min], [max] and [step] bound a number; [maxLength] and [placeholder] a text or number
 * box; [options] are a select's or day chips' choices. A check [reveals] the fields it shows while
 * ticked and [hides] those it hides while ticked (the rule sheet's rotating shift, the weekend
 * sleep times). Fields with the same [group] next to each other share a row, as the page's ld-grid2
 * and ld-grid3 do. [required] is the page's own words for a field left empty ("Pick at least one
 * day"); null when it may be empty. [note] is the muted line under it.
 */
data class FormField(
    val key: String,
    val kind: FieldKind,
    val label: String,
    val value: FieldValue,
    val min: Int? = null,
    val max: Int? = null,
    val step: Int? = null,
    val maxLength: Int? = null,
    val placeholder: String? = null,
    val options: List<FormOption> = emptyList(),
    val reveals: List<String> = emptyList(),
    val hides: List<String> = emptyList(),
    val group: String? = null,
    val required: String? = null,
    val note: String? = null,
) {
    /** A number drawn as the page's free box (it has a placeholder) rather than as a stepper. */
    val typedNumber: Boolean
        get() = kind == FieldKind.NUMBER && placeholder != null
}

/**
 * The page's folded part of a form (the goal form's optional target, a details element): its
 * summary and fields.
 */
data class FormFold(val label: String, val fields: List<FormField>)

data class FormSchema(val fields: List<FormField>, val fold: FormFold? = null) {
    /** Every field, the fold's last. */
    val allFields: List<FormField>
        get() = fields + fold?.fields.orEmpty()

    /** The values the page filled in, by key: a new draft. */
    fun initialValues(): Map<String, FieldValue> = allFields.associate { it.key to it.value }
}
