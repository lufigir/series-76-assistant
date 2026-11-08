package com.series76.assistant.ui.chat

/**
 * Answers whose results open folded, one tap from the full sheet, because their text already answers the question:
 * - A lookup of one model, such as "torque of 761A00-11300000/A?". The text gives the figure; the sheet stays as its
 *   source. A search keeps its results open, because choosing a model is reading them.
 * - An answer whose models were all shown by an earlier answer in the loaded history, such as "and its speed at 50 Hz?",
 *   where repeating the whole sheet would only bury the answer.
 */
fun foldedResults(messages: List<ChatMessage>): Set<String> {
    val shown = mutableSetOf<String>()
    val folded = mutableSetOf<String>()
    messages.filterNot { it.fromUser }.forEach { message ->
        val partNumbers = message.products.map { it.partNumber }
        val singleLookup = message.outcome == ANSWERED && partNumbers.size == 1 && message.matches.isEmpty()
        if (singleLookup || (partNumbers.isNotEmpty() && shown.containsAll(partNumbers))) folded += message.id
        shown += partNumbers
    }
    return folded
}

private const val ANSWERED = "answered"
