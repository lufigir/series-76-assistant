package com.series76.assistant.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PartNumberTextTest {
    @Test
    fun `a part number gets a word joiner at each place a line could break`() {
        assertEquals(
            "What is the torque of 761A00-⁠11300000⁠/⁠A?",
            keepPartNumbersWhole("What is the torque of 761A00-11300000/A?"),
        )
    }

    @Test
    fun `other dashes and slashes still break`() {
        assertEquals("on/off and 24-volt", keepPartNumbersWhole("on/off and 24-volt"))
    }

    @Test
    fun `every part number in the text is kept whole, whatever its case`() {
        val text = keepPartNumbersWhole("763a00-11320000/a or 764X30-11320000/A")
        assertEquals("763a00-11320000/a or 764X30-11320000/A", text.replace("⁠", ""))
        assertEquals(6, text.count { it == '⁠' })
    }
}
