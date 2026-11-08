package com.series76.assistant.ui.chat

import com.series76.assistant.data.remote.CatalogEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class PartNumberHelpTest {
    private val catalog =
        listOf(
            CatalogEntry("763A00-11300000/A", "weatherproof", "110v_single_phase", 300),
            CatalogEntry("763A00-11320000/A", "weatherproof", "220v_single_phase", 300),
            CatalogEntry("763B00-11320000/A", "weatherproof", "220v_single_phase", 500),
            CatalogEntry("763X30-11300000/A", "explosionproof", "110v_single_phase", 300),
            CatalogEntry("764A00-11320000/A", "weatherproof", "220v_single_phase", 750),
        )

    @Test
    fun `the start of a part number lists what it could be`() {
        assertEquals(
            listOf("763A00-11300000/A", "763A00-11320000/A", "763B00-11320000/A", "763X30-11300000/A"),
            completions("torque of 763", catalog),
        )
    }

    @Test
    fun `spaces, dashes and case do not matter`() {
        assertEquals(listOf("763A00-11320000/A"), completions("what about 763a00 1132", catalog))
    }

    @Test
    fun `a number already complete needs no suggestion`() {
        assertEquals(emptyList<String>(), completions("763A00-11320000/A", catalog))
        assertEquals(emptyList<String>(), completions("763a00 11320000", catalog))
    }

    @Test
    fun `text without a part number at its end suggests nothing`() {
        assertEquals(emptyList<String>(), completions("explosionproof at 110 V", catalog))
        assertEquals(emptyList<String>(), completions("763A00-11320000/A and its speed", catalog))
    }

    @Test
    fun `picking a completion replaces only what was being typed`() {
        assertEquals("torque of 763A00-11320000/A ", complete("torque of 763a00 113", "763A00-11320000/A"))
    }

    @Test
    fun `each builder step offers only what the earlier ones leave`() {
        assertEquals(listOf("763", "764"), sizeOptions(catalog))
        val size = PartNumberChoice(size = "763")
        assertEquals(listOf("explosionproof", "weatherproof"), enclosureOptions(catalog, size))
        val enclosure = size.copy(enclosure = "weatherproof")
        assertEquals(listOf("110v_single_phase", "220v_single_phase"), powerSupplyOptions(catalog, enclosure))
        assertEquals(
            listOf("763A00-11320000/A", "763B00-11320000/A"),
            builtPartNumbers(catalog, enclosure.copy(powerSupply = "220v_single_phase")).map { it.partNumber },
        )
    }

    @Test
    fun `the last builder step tells siblings apart by torque, and an N-A row by its missing ratings`() {
        assertEquals("Up to 500 Nm", torqueHint(catalog[2]))
        assertEquals("N/A in the tables", torqueHint(CatalogEntry("761A00-11330C00/A", "weatherproof", "220v_three_phase", null)))
    }
}
