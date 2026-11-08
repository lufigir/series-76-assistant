package com.series76.assistant.ui.chat

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

data class ChatDay(val label: String, val messages: List<ChatMessage>)

// English like the rest of the screen, whatever the device language.
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)

/**
 * Consecutive messages of the same local day, in order. A message without a readable time, such as one still being
 * sent, joins the day before it, so a turn never lands under a header of its own.
 */
fun chatDays(
    messages: List<ChatMessage>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<ChatDay> {
    val days = mutableListOf<ChatDay>()
    messages.forEach { message ->
        val label = message.createdAt?.let { dayLabel(it, today, zone) } ?: days.lastOrNull()?.label ?: "Today"
        val last = days.lastOrNull()
        if (last != null && last.label == label) {
            days[days.lastIndex] = last.copy(messages = last.messages + message)
        } else {
            days += ChatDay(label, listOf(message))
        }
    }
    return days
}

/**
 * The list index of the latest question, counting each day's header before its messages, or null when nobody has asked
 * anything yet. The screen pins it to the top, so a short reply and a long table both read as question, then answer.
 */
fun latestQuestionIndex(days: List<ChatDay>): Int? {
    var index = 0
    var latest: Int? = null
    days.forEach { day ->
        index++
        day.messages.forEach { message ->
            if (message.fromUser) latest = index
            index++
        }
    }
    return latest
}

private fun dayLabel(createdAt: String, today: LocalDate, zone: ZoneId): String? {
    val day =
        try {
            OffsetDateTime.parse(createdAt).atZoneSameInstant(zone).toLocalDate()
        } catch (_: DateTimeParseException) {
            return null
        }
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(DATE)
    }
}
