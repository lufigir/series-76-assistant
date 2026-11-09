package com.series76.assistant.ui.components

// A word joiner forbids a line break on either side of it and draws nothing.
private const val WORD_JOINER = '⁠'
private val PART_NUMBER = Regex("""(?i)\b76[1-7][0-9a-z]{3}-[0-9a-z]{8}/[a-z]\b""")

/**
 * The text with every part number unbreakable, for display only. Android breaks lines after a dash and a slash, which
 * left "761A00-11300000/" on one line and "A" on the next; a code read in two pieces is easy to misread or mistype.
 *
 * [breakAtDash] keeps one break, after the dash, for a narrow line that cannot hold the whole code: without it, Android
 * forces a break anywhere in the digits, and "761A00-1130000" over "0/A" reads as another model.
 */
fun keepPartNumbersWhole(text: String, breakAtDash: Boolean = false): String =
    PART_NUMBER.replace(text) { match ->
        val dash = if (breakAtDash) "-" else "-$WORD_JOINER"
        match.value.replace("-", dash).replace("/", "$WORD_JOINER/$WORD_JOINER")
    }
