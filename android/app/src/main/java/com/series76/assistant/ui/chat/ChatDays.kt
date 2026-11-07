package com.series76.assistant.ui.chat

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

data class ChatDay(val label: String, val messages: List<ChatMessage>)

private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

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
