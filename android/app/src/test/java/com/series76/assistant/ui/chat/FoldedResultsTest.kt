package com.series76.assistant.ui.chat

import com.series76.assistant.data.remote.ActuatorData
import org.junit.Assert.assertEquals
import org.junit.Test

private fun model(partNumber: String) =
    ActuatorData(
        partNumber = partNumber,
        enclosure = "weatherproof",
        powerSupply = "110v_single_phase",
        service = "on_off_and_modulating",
        notAvailable = false,
        notCsaCertified = false,
        cyclesPerHour = null,
        startsPerHour = null,
        motorWatts = null,
        ratings = emptyList(),
        electricals = emptyList(),
    )

private fun answer(id: String, vararg partNumbers: String) =
    ChatMessage(id, fromUser = false, text = "", outcome = "recommendation", products = partNumbers.map(::model))

private fun lookup(id: String, vararg partNumbers: String) =
    ChatMessage(id, fromUser = false, text = "", outcome = "answered", products = partNumbers.map(::model))

class FoldedResultsTest {
    @Test
    fun `a follow-up on the same model folds its results, and the first answer keeps them open`() {
        val messages =
            listOf(
                answer("first", "761A00-11300000/A"),
                ChatMessage("q", fromUser = true, text = "and its speed?"),
                answer("follow-up", "761A00-11300000/A"),
            )
        assertEquals(setOf("follow-up"), foldedResults(messages))
    }

    @Test
    fun `an answer that adds a model it has not shown stays open`() {
        val messages = listOf(answer("a", "761A00-11300000/A"), answer("b", "761A00-11300000/A", "761B00-11300000/A"))
        assertEquals(emptySet<String>(), foldedResults(messages))
    }

    @Test
    fun `a subset of the models already shown folds, and answers without models never do`() {
        val messages =
            listOf(
                answer("a", "761A00-11300000/A", "761B00-11300000/A"),
                answer("b", "761B00-11300000/A"),
                answer("c"),
            )
        assertEquals(setOf("b"), foldedResults(messages))
    }

    @Test
    fun `a lookup of one model folds, since its text already answers`() {
        assertEquals(setOf("a"), foldedResults(listOf(lookup("a", "761A00-11300000/A"))))
    }

    @Test
    fun `a lookup of several models stays open as a comparison`() {
        assertEquals(emptySet<String>(), foldedResults(listOf(lookup("a", "761A00-11300000/A", "761B00-11300000/A"))))
    }

    @Test
    fun `a single match from a search stays open`() {
        val message = answer("a", "761A00-11300000/A").copy(matches = listOf("761A00-11300000/A"))
        assertEquals(emptySet<String>(), foldedResults(listOf(message)))
    }
}
