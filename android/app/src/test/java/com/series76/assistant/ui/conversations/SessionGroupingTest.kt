package com.series76.assistant.ui.conversations

import com.series76.assistant.data.remote.SessionSummary
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionGroupingTest {
    private val today = LocalDate.of(2026, 11, 4)

    private fun group(vararg dates: String) =
        groupSessions(dates.mapIndexed { index, date -> SessionSummary("$index", "Title", date) }, today, ZoneOffset.UTC)

    private fun row(session: SessionSummary) = groupSessions(listOf(session), today, ZoneOffset.UTC).single().sessions.single()

    @Test
    fun `sessions fall under today, yesterday, the past week and earlier, keeping their order`() {
        val groups =
            group(
                "2026-11-04T10:00:00+00:00",
                "2026-11-03T23:00:00+00:00",
                "2026-10-30T08:00:00+00:00",
                "2026-10-20T08:00:00+00:00",
            )
        assertEquals(listOf("Today", "Yesterday", "Previous 7 days", "Earlier"), groups.map { it.label })
        assertEquals(listOf("0"), groups[0].sessions.map { it.id })
    }

    @Test
    fun `dates stay in English on a phone set to another language`() {
        val before = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-CO"))
        try {
            assertEquals("Fri", group("2026-10-30T08:00:00+00:00").single().sessions.single().time)
            assertEquals("Oct 20, 2026", group("2026-10-20T08:00:00+00:00").single().sessions.single().time)
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun `the day is the local one, not the one in the timestamp`() {
        assertEquals("Today", group("2026-11-03T23:30:00-05:00").single().label)
    }

    @Test
    fun `a date that does not parse still lists the conversation`() {
        assertEquals(listOf("Earlier"), group("not a date").map { it.label })
    }

    @Test
    fun `the preview folds the answer onto one paragraph`() {
        val session = SessionSummary("1", "Title", "2026-11-04T10:00:00+00:00", preview = "First line.\n\nSecond line.")
        assertEquals("First line. Second line.", row(session).preview)
        assertNull(row(SessionSummary("1", "Title", "2026-11-04T10:00:00+00:00", preview = "  ")).preview)
    }
}
