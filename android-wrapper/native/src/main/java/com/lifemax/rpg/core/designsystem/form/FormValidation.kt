package com.lifemax.rpg.core.designsystem.form

/*
 * What the screen checks before it sends a form, in the page's words only. The page refuses a form in two ways
 * (saveRule, saveSleep and saveGoal in logic.js): a field left empty, which it names in its own words ("Pick at
 * least one day", "Give the goal a name"), and a number outside its box's limits, which it never names but quietly
 * keeps within them (Math.max/Math.min on travel, unwind, days on and off). So the schema carries the words for the
 * first ([FormField.required]) and the screen does the second by clamping. A field hidden by a check is not
 * checked and not sent, as the page skips the day chips of a rotating shift.
 */
object FormValidation {
    /**
     * The keys of the fields shown with [values]: a check's [FormField.reveals] and
     * [FormField.hides] applied.
     */
    fun visibleKeys(schema: FormSchema, values: Map<String, FieldValue>): Set<String> {
        val fields = schema.allFields
        val hidden = mutableSetOf<String>()
        for (f in fields) {
            if (f.kind != FieldKind.CHECK) continue
            val on = (values[f.key] as? FieldValue.Check ?: f.value as FieldValue.Check).on
            hidden += if (on) f.hides else f.reveals
        }
        return fields.map { it.key }.filterNot { it in hidden }.toSet()
    }

    /**
     * The page's words for each shown field left empty, by key; empty when the form may be sent. A
     * required check must be ticked; a time always has a value.
     */
    fun errors(schema: FormSchema, values: Map<String, FieldValue>): Map<String, String> {
        val shown = visibleKeys(schema, values)
        return schema.allFields
            .filter { it.key in shown && it.required != null }
            .filter { isEmpty(values[it.key] ?: it.value) }
            .associate { it.key to it.required!! }
    }

    /** The value [field] is sent with: a number kept within its limits, text cut to its length. */
    fun clamp(field: FormField, value: FieldValue): FieldValue =
        when (value) {
            is FieldValue.Number ->
                FieldValue.Number(
                    value.number?.let {
                        it.coerceAtLeast(field.min ?: it).coerceAtMost(field.max ?: it)
                    }
                )
            is FieldValue.Text ->
                field.maxLength?.let { FieldValue.Text(value.text.take(it)) } ?: value
            else -> value
        }

    /** The shown fields' values, clamped, in the page's order: what a form sends. */
    fun sendable(schema: FormSchema, values: Map<String, FieldValue>): Map<String, FieldValue> {
        val shown = visibleKeys(schema, values)
        return schema.allFields
            .filter { it.key in shown }
            .associate { it.key to clamp(it, values[it.key] ?: it.value) }
    }

    private fun isEmpty(v: FieldValue): Boolean =
        when (v) {
            is FieldValue.Text -> v.text.isBlank()
            is FieldValue.Number -> v.number == null
            is FieldValue.Date -> v.date.isEmpty()
            is FieldValue.Choice -> v.value.isEmpty()
            is FieldValue.Check -> !v.on
            is FieldValue.Days -> v.days.isEmpty()
            is FieldValue.Time -> false
        }
}
