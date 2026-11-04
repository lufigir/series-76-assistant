package com.series76.assistant.ui.conversations

import com.series76.assistant.data.remote.SessionSummary
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle

data class SessionGroup(val label: String, val sessions: List<SessionRow>)

data class SessionRow(val id: String, val title: String, val time: String)

private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** Groups by day in the order received, which the backend already sorts by most recent. */
fun groupSessions(
    sessions: List<SessionSummary>,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<SessionGroup> {
    val rows =
        sessions.map { session ->
            val moment =
                try {
                    OffsetDateTime.parse(session.date).atZoneSameInstant(zone)
                } catch (_: DateTimeParseException) {
                    null
                }
            val day = moment?.toLocalDate()
            val label =
                when {
                    day == null -> "Earlier"
                    day == today -> "Today"
                    day == today.minusDays(1) -> "Yesterday"
                    else -> "Earlier"
                }
            val time =
                when {
                    moment == null -> ""
                    label == "Earlier" -> moment.format(DATE)
                    else -> moment.format(TIME)
                }
            label to SessionRow(session.id, session.title, time)
        }
    return rows
        .groupBy({ it.first }, { it.second })
        .map { (label, grouped) -> SessionGroup(label, grouped) }
}
