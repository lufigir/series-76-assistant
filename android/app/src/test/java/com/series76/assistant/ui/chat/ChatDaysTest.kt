package com.series76.assistant.ui.chat

import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatDaysTest {
    private val today = LocalDate.of(2026, 11, 4)

    private fun message(id: String, createdAt: String?) = ChatMessage(id = id, fromUser = true, text = id, createdAt = createdAt)

    private fun days(vararg messages: ChatMessage) = chatDays(messages.toList(), today, ZoneOffset.UTC)

    @Test
    fun `the latest question counts the day headers above it`() {
        val answer = ChatMessage(id = "r", fromUser = false, text = "r", createdAt = "2026-11-04T10:01:00+00:00")
        val result =
            days(
                message("a", "2026-11-03T09:00:00+00:00"),
                message("b", "2026-11-04T10:00:00+00:00"),
                answer,
            )
        // Yesterday, a, Today, b, r.
        assertEquals(3, latestQuestionIndex(result))
    }

    @Test
    fun `without a question there is nothing to pin`() {
        assertEquals(null, latestQuestionIndex(emptyList()))
    }

    @Test
    fun `consecutive messages of one day share a header`() {
        val result =
            days(
                message("a", "2026-11-03T09:00:00+00:00"),
                message("b", "2026-11-03T09:01:00+00:00"),
                message("c", "2026-11-04T10:00:00+00:00"),
            )
        assertEquals(listOf("Yesterday", "Today"), result.map { it.label })
        assertEquals(listOf("a", "b"), result[0].messages.map { it.id })
    }

    @Test
    fun `a message being sent joins the day before it`() {
        val result = days(message("a", "2026-11-03T09:00:00+00:00"), message("b", null))
        assertEquals(listOf("Yesterday"), result.map { it.label })
        assertEquals(listOf("a", "b"), result.single().messages.map { it.id })
    }

    @Test
    fun `a conversation started now is today`() {
        assertEquals(listOf("Today"), days(message("a", null)).map { it.label })
    }

    @Test
    fun `an older day reads as a date`() {
        assertEquals(1, days(message("a", "2026-10-20T09:00:00+00:00")).size)
        assertEquals(false, days(message("a", "2026-10-20T09:00:00+00:00")).single().label in setOf("Today", "Yesterday"))
    }
}
