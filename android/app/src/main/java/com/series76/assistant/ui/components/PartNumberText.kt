package com.series76.assistant.ui.components

// A word joiner forbids a line break on either side of it and draws nothing.
private const val WORD_JOINER = '⁠'
private val PART_NUMBER = Regex("""(?i)\b76[1-7][0-9a-z]{3}-[0-9a-z]{8}/[a-z]\b""")

/**
 * The text with every part number unbreakable, for display only. Android breaks lines after a dash and a slash, which
 * left "761A00-11300000/" on one line and "A" on the next; a code read in two pieces is easy to misread or mistype.
 */
fun keepPartNumbersWhole(text: String): String =
    PART_NUMBER.replace(text) { match ->
        match.value.replace("-", "-$WORD_JOINER").replace("/", "$WORD_JOINER/$WORD_JOINER")
    }
