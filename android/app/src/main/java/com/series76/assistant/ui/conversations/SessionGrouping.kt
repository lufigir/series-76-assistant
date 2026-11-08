package com.series76.assistant.ui.conversations

import com.series76.assistant.data.remote.SessionSummary
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

data class SessionGroup(val label: String, val sessions: List<SessionRow>)

data class SessionRow(
    val id: String,
    val title: String,
    val time: String,
    val preview: String? = null,
)

// English like every other word in the app; the device locale gave "vie" beside "Previous 7 days".
private val TIME = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.US)
private val DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.US)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE", Locale.US)
private const val WEEK_DAYS = 7L
private val WHITESPACE = Regex("\\s+")

private const val TODAY = "Today"
private const val YESTERDAY = "Yesterday"
private const val PAST_WEEK = "Previous 7 days"
private const val EARLIER = "Earlier"

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
                    day == null -> EARLIER
                    day == today -> TODAY
                    day == today.minusDays(1) -> YESTERDAY
                    day.isAfter(today.minusDays(WEEK_DAYS)) -> PAST_WEEK
                    else -> EARLIER
                }
            val time =
                when (label) {
                    TODAY, YESTERDAY -> moment?.format(TIME).orEmpty()
                    PAST_WEEK -> moment?.format(WEEKDAY).orEmpty()
                    else -> moment?.format(DATE).orEmpty()
                }
            label to
                SessionRow(
                    id = session.id,
                    title = session.title,
                    time = time,
                    preview = session.preview?.replace(WHITESPACE, " ")?.trim()?.ifEmpty { null },
                )
        }
    return rows
        .groupBy({ it.first }, { it.second })
        .map { (label, grouped) -> SessionGroup(label, grouped) }
}
