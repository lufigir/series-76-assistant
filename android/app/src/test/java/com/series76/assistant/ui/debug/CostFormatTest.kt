package com.series76.assistant.ui.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class CostFormatTest {
    @Test
    fun `a fraction of a cent reads as a plain decimal, not scientific notation`() {
        assertEquals("USD 0.00021", formatCost(2.05804E-4))
    }

    @Test
    fun `a larger cost keeps two significant digits`() {
        assertEquals("USD 0.0014", formatCost(0.0013906))
    }

    @Test
    fun `a missing cost says so`() {
        assertEquals("not reported", formatCost(null))
    }
}
