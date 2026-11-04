package com.series76.assistant.ui.conversations

import com.series76.assistant.data.remote.SessionSummary
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGroupingTest {
    private val today = LocalDate.of(2026, 11, 4)

    private fun group(vararg dates: String) =
        groupSessions(dates.mapIndexed { index, date -> SessionSummary("$index", "Title", date) }, today, ZoneOffset.UTC)

    @Test
    fun `sessions fall under today, yesterday and earlier, keeping their order`() {
        val groups =
            group("2026-11-04T10:00:00+00:00", "2026-11-03T23:00:00+00:00", "2026-10-20T08:00:00+00:00")
        assertEquals(listOf("Today", "Yesterday", "Earlier"), groups.map { it.label })
        assertEquals(listOf("0"), groups[0].sessions.map { it.id })
    }

    @Test
    fun `the day is the local one, not the one in the timestamp`() {
        assertEquals("Today", group("2026-11-03T23:30:00-05:00").single().label)
    }

    @Test
    fun `a date that does not parse still lists the conversation`() {
        assertEquals(listOf("Earlier"), group("not a date").map { it.label })
    }
}
